package io.github.photep.hideplugins;

import bitwig.obf.AQw; // an on/off value a widget can show and set
import bitwig.obf.VGL; // a value change: the value, the old and the new
import bitwig.obf.dJV; // makes a widget for a model object
import bitwig.obf.dSK; // a scroll area: a scroll view with Bitwig's scrollbars
import bitwig.obf.dWb; // a vertical or horizontal group of widgets
import bitwig.obf.dWo; // a list of widgets made from a model, kept up to date with it
import bitwig.obf.dXp; // a line (the rule after a section heading)
import bitwig.obf.dnF; // margins around one widget
import bitwig.obf.dpN; // Bitwig's application (its theme)
import bitwig.obf.dut; // a text label (titles and headings)
import bitwig.obf.duw; // a label's text style
import bitwig.obf.duY; // a one-line text label
import bitwig.obf.dve; // a widget
import bitwig.obf.dzv; // a checkbox
import bitwig.obf.ecE; // a search field's filter maker: called with the text as it is typed
import bitwig.obf.ecp; // a wrapping text
import bitwig.obf.ecz; // Bitwig's search field
import bitwig.obf.eff; // the theme's colours
import bitwig.obf.ejE; // a font, as a label takes it
import bitwig.obf.ejl; // a search field's listener: told the filter for the text typed
import bitwig.obf.ejt; // a colour, as a widget takes it
import bitwig.obf.foz; // a listener
import bitwig.obf.pCi; // a filter a search field makes from its text
import bitwig.obf.r3s; // Bitwig's observable list
import bml.panels.ViewContext;
import com.bitwig.base.geom.Dgc; // an alignment

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The Hidden Plug-ins panel, laid out as Bitwig lays out its Project panel: a bold title, a search field,
 * then, scrolling: the switch for all hiding, the rules (whole vendors and formats) and a section per
 * format (CLAP, VST3, VST2), each a heading with a rule and rows of name, vendor and checkbox, ticked
 * while hidden. Unticking shows a plug-in in the browser again; its row stays (unticked) until Bitwig
 * restarts, so it can be ticked again. A section's own checkbox hides or shows every row it lists, which
 * with the search field handles many at once. Lists are Bitwig's own list widget on Bitwig's observable
 * list, so every open panel follows the changes. UI thread.
 */
final class HiddenPanel {
    private static final String TITLE = "Hidden Plug-ins";
    private static final String SWITCH = "Hide listed plug-ins";
    private static final String HINT = "Untick a plug-in or a rule to show it in the browser again.";
    private static final String EMPTY = "Nothing hidden. Right-click a plug-in in the browser, then Hide from Browser.";
    private static final String RULES = "rules"; // the rules section's key, beside the formats'
    /** The plug-in sections, in order: format key → heading. */
    private static final Map<String, String> FORMATS = new LinkedHashMap<>();

    static {
        FORMATS.put("clap", "CLAP");
        FORMATS.put("vst3", "VST3");
        FORMATS.put("vst2", "VST2");
    }

    /** Every row of this session by section (plug-in keys, by name; rules): hidden at start, then all hidden since. */
    private static Map<String, List<String>> all;
    /** The rows shown now by section: {@link #all} narrowed by the search text. The lists the widgets follow. */
    private static Map<String, r3s> shown;
    /** The search text, lower case; "" shows everything. Shared by every window's panel. */
    private static String search = "";

    private static final Set<Toggle> TOGGLES = Collections.newSetFromMap(new WeakHashMap<>());
    /** Widgets shown only while a section (its key) or the whole panel ("") has rows. */
    private static final Map<dve, String> SHOWN_WITH_ROWS = new WeakHashMap<>();
    private static final Set<dve> SHOWN_WHEN_EMPTY = Collections.newSetFromMap(new WeakHashMap<>());

    private HiddenPanel() {
    }

    /** The panel's content, for one window. */
    static Object create(ViewContext context) {
        try {
            return content();
        } catch (Throwable t) {
            HiddenList.MOD.log("the Hidden Plug-ins panel failed", t);
            return duY.MIO("Hidden Plug-ins could not be shown: see Bitwig's log"); // a label
        }
    }

    /** After anything hidden changed: run the browsers' searches again and bring every panel up to date. */
    static void afterChange() {
        HideHooks.searchAgain();
        try {
            if (all == null) return; // no panel built yet: it starts from the list as it is then
            HiddenList.Snapshot now = HiddenList.snapshot();
            for (String key : now.keys()) {
                List<String> section = all.get(format(key));
                if (section == null || section.contains(key)) continue;
                int at = 0; // a new row, in its place by name
                while (at < section.size() && name(section.get(at)).compareToIgnoreCase(name(key)) <= 0) at++;
                section.add(at, key);
            }
            for (String rule : now.rules()) {
                if (!all.get(RULES).contains(rule)) all.get(RULES).add(rule);
            }
            refresh();
        } catch (Throwable t) {
            HiddenList.MOD.log("updating the Hidden Plug-ins panel failed", t);
        }
    }

    /** Brings the shown rows, the checkboxes and what is visible in line with the list and the search text. */
    @SuppressWarnings("unchecked")
    private static void refresh() {
        for (Map.Entry<String, List<String>> section : all.entrySet()) {
            List<String> want = new ArrayList<>();
            for (String row : section.getValue()) if (matches(section.getKey(), row)) want.add(row);
            r3s list = shown.get(section.getKey());
            if (!want.equals(new ArrayList<Object>(list))) {
                list.clear();
                for (String row : want) list.add(row);
            }
        }
        List<Toggle> toggles;
        synchronized (TOGGLES) {
            toggles = new ArrayList<>(TOGGLES);
        }
        for (Toggle t : toggles) t.refresh();
        updateVisibility();
    }

    private static boolean matches(String section, String row) {
        if (search.isEmpty()) return true;
        String text = RULES.equals(section) ? HiddenList.ruleLabel(row)
                                            : name(row) + " " + HiddenList.vendor(row) + " " + bundleOf(row);
        return text.toLowerCase(Locale.ROOT).contains(search);
    }

    private static String format(String key) {
        int tab = key.indexOf('\t');
        return tab < 0 ? "" : key.substring(0, tab);
    }

    private static String name(String key) {
        int tab = key.lastIndexOf('\t');
        return tab < 0 ? key : key.substring(tab + 1);
    }

    private static String bundleOf(String key) {
        String[] parts = key.split("\t", 3);
        return parts.length < 3 ? "" : parts[1];
    }

    private static void model() {
        if (all != null) return;
        Map<String, List<String>> rows = new LinkedHashMap<>();
        Map<String, r3s> lists = new LinkedHashMap<>();
        rows.put(RULES, new ArrayList<>(HiddenList.snapshot().rules()));
        lists.put(RULES, new r3s());
        for (String format : FORMATS.keySet()) {
            rows.put(format, new ArrayList<>());
            lists.put(format, new r3s());
        }
        List<String> keys = new ArrayList<>(HiddenList.snapshot().keys());
        keys.sort((a, b) -> name(a).compareToIgnoreCase(name(b)));
        for (String key : keys) {
            List<String> section = rows.get(format(key));
            if (section != null) section.add(key);
        }
        shown = lists;
        all = rows;
        refresh();
    }

    private static eff theme() {
        return dpN.TVZ().Pus().BAa(); // the application's theme colours
    }

    /** The whole panel: title, search field, the sections scrolling below, on the panel background. */
    private static dve content() {
        model();
        eff colours = theme();

        dut title = dut.UNj(TITLE);                       // a text label
        title.Hnt((ejt) colours.x3t());                   // the panel text colour
        title.cfM((ejE) dpN.TVZ().Pus().Ai().wGc());      // the theme's bold font
        title.cfM(Dgc.lYc);                               // centred

        dWb panel = dWb.UNj(4);                           // vertical, as the Project panel
        panel.Hnt(12L);                                   // a gap above the title
        panel.HL(title);
        panel.Hnt(4L);                                    // a gap below it
        panel.HL(dnF.cfM(searchField(), 12, 4, 12, 4));   // the search field, inside the panel's margins
        panel.HL(scroll(body(colours)));
        panel.UNj(colours.PD3());                         // the panel background colour
        return panel;
    }

    /** Bitwig's search field, wired as the Commander wires its own: it narrows the rows as text is typed. */
    private static dve searchField() {
        ecz field = ecz.LzO();                            // a search field
        field.fJR(new Searched());                        // told each new filter: the text changed
        field.cfM(new MakesTyped());                      // makes the filter for the text typed
        return field;
    }

    /**
     * The search field's listener. A named class, not a lambda: BML translates the names of methods a
     * mod's class overrides (cfM is another name on other Bitwig builds), but not a lambda's.
     */
    private static final class Searched implements ejl {
        @Override
        public void cfM(pCi filter) {
            try {
                String text = filter instanceof Typed typed ? typed.text : "";
                if (text.equals(search)) return;
                search = text;
                if (all != null) refresh();
            } catch (Throwable t) {
                HiddenList.MOD.log("searching the Hidden Plug-ins panel failed", t);
            }
        }
    }

    /** Makes the filter for the text typed. A named class, as {@link Searched} is. */
    private static final class MakesTyped implements ecE {
        @Override
        public pCi createFilter(String text) {
            return new Typed(text);
        }
    }

    /** What the search field hands back for its text: only the text matters here. */
    private static final class Typed implements pCi {
        final String text;

        Typed(String text) {
            this.text = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean accepts(Object item) {
            return true;
        }
    }

    /** The sections, with the Project panel's margins. */
    private static dve body(eff colours) {
        List<dve> parts = new ArrayList<>();

        // The switch for all hiding, as a row; unticked, every plug-in shows and the lists below are kept.
        parts.add(row(SWITCH, "", new Toggle(HiddenList::enabled, on -> {
            HiddenList.setEnabled(on);
            afterChange();
        })));

        ecp hint = ecp.Hnt(HINT);                         // a wrapping text, in the default text colour
        hint.MJ(0);
        hint.UZr(0);
        parts.add(hint);
        shownWithRows(hint, "");

        section(parts, RULES, "Rules", colours, false);
        for (Map.Entry<String, String> f : FORMATS.entrySet()) section(parts, f.getKey(), f.getValue(), colours, true);

        ecp empty = ecp.Hnt(EMPTY);
        empty.MJ(0);
        empty.UZr(0);
        synchronized (SHOWN_WHEN_EMPTY) {
            SHOWN_WHEN_EMPTY.add(empty);
        }
        parts.add(empty);
        updateVisibility();

        dWb column = dWb.MRY(12, parts.toArray(new dve[0])); // vertical, 12 between parts, as the Project panel's sections
        column.JJY();                                     // the rest of the height stays empty
        return dnF.cfM(column, 12, 8, 0, 12);             // the Project panel's margins
    }

    /** One section: its heading and the list of its rows, both shown only while it has rows. */
    private static void section(List<dve> parts, String key, String title, eff colours, boolean ofPlugins) {
        dve heading = heading(title, colours, ofPlugins ? sectionToggle(key) : null);
        shownWithRows(heading, key);
        parts.add(heading);

        dWo list = dWo.iAj();                             // a vertical list
        list.TMF(2);                                      // spacing between rows
        list.cfM(new Rows(ofPlugins));                    // how a row is made
        list.ucQ(shown.get(key));                         // bound to the section's rows: follows them as they change
        shownWithRows(list, key);
        parts.add(list);
    }

    /** A section's own checkbox: ticked while every row it lists is hidden; hides or shows them all. */
    @SuppressWarnings("unchecked")
    private static Toggle sectionToggle(String format) {
        return new Toggle(() -> {
            r3s rows = shown.get(format);
            if (rows.isEmpty()) return false;
            for (Object key : rows) if (!HiddenList.contains((String) key)) return false;
            return true;
        }, hide -> {
            Set<String> keys = new LinkedHashSet<>();
            for (Object key : shown.get(format)) keys.add((String) key);
            if (hide) HiddenList.add(keys);
            else HiddenList.remove(keys);
            afterChange();
        });
    }

    /** A section heading as the Project panel draws it: the text, a faint rule, and the section's checkbox. */
    private static dve heading(String text, eff colours, Toggle toggle) {
        dXp rule = dXp.LzO();                             // a line
        rule.E3(0.7F);                                    // drawn as the Project panel's
        rule.UNj(colours.Dah());                          // the rule colour
        dve label = dut.cfM(text, colours.x3t());         // the heading
        return toggle == null ? dWb.cfM(12, new dve[] { label, rule })
                              : dWb.cfM(12, new dve[] { label, rule, dzv.cfM(toggle) }); // horizontal
    }

    /** One row, as the Project panel's checkbox rows: the name, a stretch, the vendor if known, the checkbox. */
    private static dve row(String text, String vendor, Toggle toggle) {
        duY label = duY.MIO(text);
        label.Hnt((ejt) theme().x3t());                   // the panel text colour
        label.MRY(duw.FzB);                               // the text style Bitwig's checkbox rows use
        dWb row = dWb.IwV(4);                             // horizontal
        row.UNj(0.5F);                                    // centred vertically
        row.HL(label);
        row.JJY();                                        // stretch
        if (!vendor.isEmpty()) {
            duY by = duY.MIO(vendor);                     // the vendor, in the default text colour
            by.MRY(duw.FzB);
            row.HL(by);
            row.Hnt(8L);                                  // a gap before the checkbox
        }
        row.HL(dzv.cfM(toggle));                          // Bitwig's checkbox
        return row;
    }

    /** Bitwig's scroll area with its thin scroll indicator, set up as the Commander's result list is. */
    private static dve scroll(dve body) {
        dSK scroll = dSK.MRY(body);                       // a scroll area around the content
        scroll.iHb(false);                                // no horizontal scrollbar
        scroll.qoU(true);                                 // a vertical scrollbar
        return scroll;
    }

    private static void shownWithRows(dve widget, String section) {
        synchronized (SHOWN_WITH_ROWS) {
            SHOWN_WITH_ROWS.put(widget, section);
        }
    }

    private static void updateVisibility() {
        boolean anyRows = false, anyShown = false;
        for (List<String> section : all.values()) anyRows |= !section.isEmpty();
        for (r3s section : shown.values()) anyShown |= !section.isEmpty();
        Map<dve, String> withRows;
        synchronized (SHOWN_WITH_ROWS) {
            withRows = new LinkedHashMap<>(SHOWN_WITH_ROWS);
        }
        for (Map.Entry<dve, String> w : withRows.entrySet()) {
            r3s section = shown.get(w.getValue());
            w.getKey().t_(w.getValue().isEmpty() ? anyShown : section != null && !section.isEmpty()); // shown or hidden
        }
        List<dve> empties;
        synchronized (SHOWN_WHEN_EMPTY) {
            empties = new ArrayList<>(SHOWN_WHEN_EMPTY);
        }
        for (dve e : empties) e.t_(!anyRows);
    }

    /** Makes the rows of one list: plug-ins (name, vendor, checkbox) or rules (label, checkbox). */
    private static final class Rows implements dJV {
        private final boolean ofPlugins;

        Rows(boolean ofPlugins) {
            this.ofPlugins = ofPlugins;
        }

        @Override
        public dve createWidget(Object item) {
            try {
                if (item instanceof String row) {
                    if (ofPlugins) {
                        return row(name(row), HiddenList.vendor(row), new Toggle(() -> HiddenList.contains(row), hide -> {
                            if (hide) HiddenList.add(Set.of(row));
                            else HiddenList.remove(Set.of(row));
                            afterChange();
                        }));
                    }
                    return row(HiddenList.ruleLabel(row), "", new Toggle(() -> HiddenList.hasRule(row), hide -> {
                        if (hide) HiddenList.addRule(row);
                        else HiddenList.removeRule(row);
                        afterChange();
                    }));
                }
            } catch (Throwable t) {
                HiddenList.MOD.log("a Hidden Plug-ins row failed", t);
            }
            return duY.wIw(); // an empty label: the list stays intact
        }
    }

    /** An on/off value as a checkbox shows and sets it; {@link #refresh} tells the checkbox of changes made elsewhere. */
    private static final class Toggle implements AQw {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private final CopyOnWriteArrayList<foz> listeners = new CopyOnWriteArrayList<>();
        private boolean told;

        Toggle(BooleanSupplier get, Consumer<Boolean> set) {
            this.get = get;
            this.set = set;
            this.told = get.getAsBoolean();
            synchronized (TOGGLES) {
                TOGGLES.add(this);
            }
        }

        @Override
        public boolean getBoolValue() {
            return get.getAsBoolean();
        }

        @Override
        public void setBoolValue(boolean on) {
            if (on == getBoolValue()) return;
            try {
                set.accept(on);
            } catch (Throwable t) {
                HiddenList.MOD.log("a Hidden Plug-ins checkbox failed", t);
            }
        }

        void refresh() {
            boolean now = getBoolValue();
            if (now == told) return;
            boolean before = told;
            told = now;
            VGL event = new VGL(this, before, now); // a change event: the value, old, new
            for (foz l : listeners) {
                try {
                    l.eventOccurred(event);
                } catch (Throwable t) {
                    HiddenList.MOD.log("a Hidden Plug-ins checkbox failed to update", t);
                }
            }
        }

        @Override
        public Object getValue() {
            return getBoolValue();
        }

        @Override
        public void setValue(Object value) {
            if (value instanceof Boolean b) setBoolValue(b);
        }

        @Override
        public boolean isSettable() {
            return true;
        }

        @Override
        public boolean isObservable() {
            return true;
        }

        @Override
        public void addListener(foz listener) {
            if (listener != null) listeners.addIfAbsent(listener);
        }

        @Override
        public void removeListener(foz listener) {
            listeners.remove(listener);
        }
    }
}
