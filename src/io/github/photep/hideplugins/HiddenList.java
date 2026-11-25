package io.github.photep.hideplugins;

import bitwig.obf.Am; // a CLAP plug-in result in the browser
import bitwig.obf.CH; // a result in Bitwig's library (what the browser lists)
import bitwig.obf.CN; // a library item with a name
import bitwig.obf.LG; // a VST2 plug-in result in the browser
import bitwig.obf.LT; // a VST3 plug-in result in the browser
import bitwig.obf.vE; // a plug-in (its vendor)
import bml.api.Bml;
import bml.api.Settings;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the user has hidden, kept in the mod's settings: single plug-ins and rules.
 *
 * <p>A plug-in is identified by its format, its bundle's file name and its name
 * ({@code clap / FabFilter Pro-Q 2.clap / Pro-Q 2}): the same three things the browser's right-click
 * item and Bitwig's index record both give, and enough to tell the plug-ins of one bundle apart (shell
 * plug-ins) and one format from another. A rule hides a whole vendor ({@code vendor / FabFilter}) or a
 * whole format ({@code format / vst2}).
 */
final class HiddenList {
    static final Bml.Mod MOD = Bml.mod("browser-hide-plugins");

    private static final String KEY_LIST = "hidden";
    private static final String KEY_RULES = "rules";
    private static final String KEY_ENABLED = "enabled";
    private static final String RULE_VENDOR = "vendor";
    private static final String RULE_FORMAT = "format";

    /** An immutable view, swapped whole, so the library thread reads it without locks. */
    record Snapshot(boolean enabled, Set<String> keys, Set<String> names, Set<String> formats,
                    Set<String> rules, Set<String> ruleFormats, Set<String> ruleVendors) {
        /** Whether anything of this format could be hidden now: the cheap first check per record. */
        boolean hides(String format) {
            return enabled && (formats.contains(format) || ruleFormats.contains(format) || !ruleVendors.isEmpty());
        }
    }

    private static volatile Snapshot snapshot;
    /** Each hidden plug-in's vendor, where known (for the panel); kept with the list. */
    private static final Map<String, String> VENDORS = new ConcurrentHashMap<>();

    private HiddenList() {
    }

    static Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (HiddenList.class) {
                if (snapshot == null) snapshot = build(settings().getBoolean(KEY_ENABLED, true), loadKeys(), loadRules());
                s = snapshot;
            }
        }
        return s;
    }

    // ---- plug-ins ----

    static String key(String format, String bundle, String name) {
        return format + "\t" + bundle + "\t" + name;
    }

    /** The last part of a path, with either separator. */
    static String bundle(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /** A browser item's format ("clap", "vst3", "vst2"), or null when it isn't a plug-in. */
    static String formatOf(Object item) {
        return item instanceof Am ? "clap" : item instanceof LT ? "vst3" : item instanceof LG ? "vst2" : null;
    }

    /** The key of a browser item, or null when it isn't a plug-in. */
    static String keyOf(Object item) {
        String format = formatOf(item);
        if (format == null || !(item instanceof CH result)) return null;
        File file = result.Grk();               // the plug-in's bundle
        String name = ((CN) result).wGc();      // the plug-in's name
        if (file == null || name == null) return null;
        return key(format, file.getName(), name);
    }

    /** A browser item's vendor, or "" when it has none. */
    static String vendorOf(Object item) {
        try {
            if (item instanceof vE plugin) {
                String vendor = plugin.qoU();   // the plug-in's vendor
                return vendor == null ? "" : vendor.trim();
            }
        } catch (Throwable t) {
            // no vendor
        }
        return "";
    }

    static String formatLabel(String format) {
        return format == null ? "" : format.toUpperCase(Locale.ROOT);
    }

    /** "Pro-Q 2 (CLAP)", for notifications. */
    static String label(String key) {
        String[] parts = key.split("\t", 3);
        if (parts.length < 3) return key;
        return parts[2] + " (" + formatLabel(parts[0]) + ")";
    }

    static boolean contains(String key) {
        return snapshot().keys().contains(key);
    }

    /** The vendor of a hidden plug-in, or "" when it isn't known yet. */
    static String vendor(String key) {
        return VENDORS.getOrDefault(key, "");
    }

    /** Notes a plug-in's vendor (from the browser item, or from the index as the filter meets it). Any thread. */
    static void noteVendor(String key, String vendor) {
        if (vendor != null && !vendor.isBlank()) VENDORS.putIfAbsent(key, vendor.trim());
    }

    static boolean knowsVendor(String key) {
        return VENDORS.containsKey(key);
    }

    static synchronized void add(Set<String> keys) {
        Set<String> all = new LinkedHashSet<>(snapshot().keys());
        if (all.addAll(keys)) save(all, snapshot().rules());
    }

    static synchronized void remove(Set<String> keys) {
        Set<String> all = new LinkedHashSet<>(snapshot().keys());
        if (all.removeAll(keys)) save(all, snapshot().rules());
    }

    // ---- rules ----

    static String vendorRule(String vendor) {
        return RULE_VENDOR + "\t" + vendor;
    }

    static String formatRule(String format) {
        return RULE_FORMAT + "\t" + format;
    }

    /** "Vendor: FabFilter" or "All VST2 plug-ins". */
    static String ruleLabel(String rule) {
        String[] parts = rule.split("\t", 2);
        if (parts.length < 2) return rule;
        return RULE_FORMAT.equals(parts[0]) ? "All " + formatLabel(parts[1]) + " plug-ins" : "Vendor: " + parts[1];
    }

    static boolean hasRule(String rule) {
        return snapshot().rules().contains(rule);
    }

    static synchronized void addRule(String rule) {
        Set<String> all = new LinkedHashSet<>(snapshot().rules());
        if (all.add(rule)) save(snapshot().keys(), all);
    }

    static synchronized void removeRule(String rule) {
        Set<String> all = new LinkedHashSet<>(snapshot().rules());
        if (all.remove(rule)) save(snapshot().keys(), all);
    }

    // ---- everything ----

    static synchronized void clear() {
        save(new LinkedHashSet<>(), new LinkedHashSet<>());
    }

    static boolean enabled() {
        return snapshot().enabled();
    }

    static synchronized void setEnabled(boolean on) {
        settings().putBoolean(KEY_ENABLED, on);
        snapshot = build(on, snapshot().keys(), snapshot().rules());
    }

    private static void save(Set<String> keys, Set<String> rules) {
        StringBuilder list = new StringBuilder();
        for (String key : keys) {
            if (list.length() > 0) list.append('\n');
            list.append(key);
            String vendor = VENDORS.get(key);
            if (vendor != null) list.append('\t').append(vendor.replace('\t', ' ').replace('\n', ' '));
        }
        settings().put(KEY_LIST, list.toString());
        settings().put(KEY_RULES, String.join("\n", rules));
        snapshot = build(snapshot().enabled(), keys, rules);
    }

    /** Each line: format, bundle, name and, where known, the vendor. */
    private static Set<String> loadKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : settings().get(KEY_LIST, "").split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 3 || parts.length > 4) continue;
            String key = key(parts[0], parts[1], parts[2]);
            keys.add(key);
            if (parts.length == 4) noteVendor(key, parts[3]);
        }
        return keys;
    }

    private static Set<String> loadRules() {
        Set<String> rules = new LinkedHashSet<>();
        for (String line : settings().get(KEY_RULES, "").split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length == 2 && !parts[1].isBlank() && (RULE_VENDOR.equals(parts[0]) || RULE_FORMAT.equals(parts[0]))) {
                rules.add(line);
            }
        }
        return rules;
    }

    private static Snapshot build(boolean enabled, Set<String> keys, Set<String> rules) {
        Set<String> names = new LinkedHashSet<>(), formats = new LinkedHashSet<>();
        for (String key : keys) {
            String[] parts = key.split("\t", 3);
            formats.add(parts[0]);
            names.add(parts[2]);
        }
        Set<String> ruleFormats = new LinkedHashSet<>(), ruleVendors = new LinkedHashSet<>();
        for (String rule : rules) {
            String[] parts = rule.split("\t", 2);
            if (RULE_FORMAT.equals(parts[0])) ruleFormats.add(parts[1]);
            else ruleVendors.add(parts[1].toLowerCase(Locale.ROOT)); // vendors compare without case
        }
        return new Snapshot(enabled, Collections.unmodifiableSet(new LinkedHashSet<>(keys)),
                Collections.unmodifiableSet(names), Collections.unmodifiableSet(formats),
                Collections.unmodifiableSet(new LinkedHashSet<>(rules)),
                Collections.unmodifiableSet(ruleFormats), Collections.unmodifiableSet(ruleVendors));
    }

    private static Settings settings() {
        return MOD.settings();
    }
}
