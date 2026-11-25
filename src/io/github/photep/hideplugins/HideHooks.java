package io.github.photep.hideplugins;

import bitwig.obf.Jp; // a browser search session (the side browser's, the pop-up browser's)
import bitwig.obf.M22; // a search filter, or one term of it
import bitwig.obf.NQ; // a CLAP plug-in record in the library index
import bitwig.obf.OO; // Bitwig's redundant plug-in filter: makes the terms
import bitwig.obf.OP; // Bitwig's "hide redundant plug-ins" filter: builds a test per index cursor
import bitwig.obf.PF; // a plug-in record in the library index
import bitwig.obf.QM; // a VST2 plug-in record in the library index
import bitwig.obf.QT; // a VST3 plug-in record in the library index
import bitwig.obf.pAe; // a cursor over Bitwig's library index, moved from record to record
import bitwig.obf.rXv; // a filter that is several terms together
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Hides the user's plug-ins by adding a filter term of the mod's own to every browser search.
 *
 * <p>The term is one of Bitwig's "redundant plug-in" terms ({@code OP}). Bitwig asks such a term for a
 * test once per index cursor (one per plug-in format) and runs the test as the cursor moves over every
 * record; {@code true} leaves the record out of the results. Bitwig only adds its own term in some
 * searches and with some settings, so the mod adds one itself, told apart by its switches, and answers
 * its tests from the user's list. Bitwig's own terms are left exactly as they are.
 */
public final class HideHooks {
    private static final AtomicBoolean FAILED = new AtomicBoolean();
    private static final AtomicBoolean SEEN = new AtomicBoolean();
    private static final Set<String> NEAR_MISSES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Browser sessions that have searched, to search again after the list changes. */
    private static final Set<Jp> SESSIONS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private HideHooks() {
    }

    /**
     * The mod's own filter term: one of Bitwig's redundant plug-in terms with every "prefer" switch off,
     * a combination Bitwig never makes itself (it makes none then). On its own it hides nothing; the
     * hook below makes it hide what the user listed. Being Bitwig's own class, Bitwig knows how to run,
     * compare and pass it around.
     */
    private static volatile OP ours;

    private static OP ours() {
        OP term = ours;
        if (term == null) ours = term = new OP(OO.cfM(), true, false, false, false); // filtering; prefer VST3, CLAP, native: all off
        return term;
    }

    private static boolean isOurs(OP term) {
        return !term.Hnt && !term.IwV && !term.UNj; // the three "prefer" switches
    }

    /**
     * Adds the mod's term to every browser search's filter, and remembers the session so
     * {@link #searchAgain()} can reach it. Bitwig's filter is all of its terms together.
     */
    @Hook(id = "add-filter-term", target = Jp.class, method = "nfq", at = At.WRAP_RETURN, allReturns = true)
    public static M22 searchFilter(@Return M22 bitwig, @This Jp session) {
        try {
            SESSIONS.add(session);
            if (bitwig == null) return null;
            return rXv.cfM(new M22[] { bitwig, ours() }); // both must match
        } catch (Throwable t) {
            failed(t);
            return bitwig;
        }
    }

    /**
     * The test a redundant plug-in term makes for one index cursor; true hides the record. For the mod's
     * own term: whether the user hid it. For Bitwig's terms: Bitwig's answer, untouched.
     */
    @Hook(id = "hide-listed-plugins", target = OP.class,
          method = "cfM(LpAe;)Ljava/util/function/BooleanSupplier;", at = At.WRAP_RETURN, allReturns = true)
    public static BooleanSupplier redundantTest(@Return BooleanSupplier bitwig, @This OP term, @Arg(0) pAe cursor) {
        try {
            if (!isOurs(term) || !(cursor instanceof PF plugin)) return bitwig;
            String format = cursor instanceof NQ ? "clap" : cursor instanceof QT ? "vst3" : cursor instanceof QM ? "vst2" : null;
            if (format == null) return bitwig;
            if (SEEN.compareAndSet(false, true)) HiddenList.MOD.log("the filter is in use (first plug-in search of this session)");
            // The list is read each time the test runs, never kept: a test Bitwig goes on using after the
            // list changed still hides the right plug-ins.
            return () -> hidden(plugin, format);
        } catch (Throwable t) {
            failed(t);
            return bitwig;
        }
    }

    /** Runs every open browser's search again, so a change to the list shows at once. UI thread. */
    static void searchAgain() {
        List<Jp> sessions;
        synchronized (SESSIONS) {
            sessions = new ArrayList<>(SESSIONS);
        }
        for (Jp session : sessions) {
            try {
                session.HhX(); // the session's own "run the search again" (returns at once when it isn't active)
            } catch (Throwable t) {
                HiddenList.MOD.log("searching again failed; the change shows at the next search", t);
            }
        }
    }

    /**
     * Whether the record the cursor is on now is hidden: by a format rule, a vendor rule, or as a listed
     * plug-in. Library thread, once per record, so the cheap checks come first.
     */
    private static boolean hidden(PF plugin, String format) {
        try {
            HiddenList.Snapshot hidden = HiddenList.snapshot();
            if (!hidden.hides(format)) return false; // hiding is off, or nothing could match this format
            if (hidden.ruleFormats().contains(format)) return true;
            if (!hidden.ruleVendors().isEmpty()) {
                String vendor = plugin.OzW(); // the record's vendor
                if (vendor != null && hidden.ruleVendors().contains(vendor.trim().toLowerCase(Locale.ROOT))) return true;
            }
            String name = plugin.hUR().cfM(); // the record's plug-in name
            if (name == null) return false;
            if (!hidden.names().contains(name)) {
                if (hidden.looseNames().contains(loose(name))) nearMiss(format, plugin.IwV(), name, hidden);
                return false;
            }
            String path = plugin.IwV();       // the record's path inside its plug-in folder
            String key = HiddenList.key(format, HiddenList.bundle(path), name);
            if (!hidden.keys().contains(key)) {
                nearMiss(format, path, name, hidden);
                return false;
            }
            if (!HiddenList.knowsVendor(key)) HiddenList.noteVendor(key, plugin.OzW()); // for the panel
            return true;
        } catch (Throwable t) {
            failed(t);
            return false;
        }
    }

    static String loose(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Logs, once each, a record whose name is on the list (exactly, or but for case and spaces) while its
     * full identity isn't: the way to see why a plug-in that was hidden still shows.
     */
    private static void nearMiss(String format, String path, String name, HiddenList.Snapshot hidden) {
        String seen = format + "|" + path + "|" + name;
        if (NEAR_MISSES.size() >= 100 || !NEAR_MISSES.add(seen)) return;
        StringBuilder listed = new StringBuilder();
        for (String key : hidden.keys()) {
            String[] parts = key.split("\t", 3);
            if (parts.length == 3 && loose(parts[2]).equals(loose(name))) listed.append(" [").append(key.replace('\t', '|')).append(']');
        }
        HiddenList.MOD.log("not hidden: " + format + " record path='" + path + "' name='" + name + "' differs from the listed" + listed);
    }

    private static void failed(Throwable t) {
        if (FAILED.compareAndSet(false, true)) HiddenList.MOD.log("hiding failed (logged once); Bitwig's own results are used", t);
    }
}
