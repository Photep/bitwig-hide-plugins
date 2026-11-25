package io.github.photep.hideplugins;

import bml.actions.ActionContext;
import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;
import com.bitwig.flt.app.CHR; // the application window controller (shows notifications)

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * On browser results: Hide from Browser, Show in Browser, Hide All from This Vendor, Hide All VST2 /
 * VST3 / CLAP Plug-ins. In the Commander: Toggle Plug-in Hiding and Unhide All Plug-ins. Each change updates the
 * browsers and the Hidden Plug-ins panel.
 */
public final class HideActions implements ActionProvider {
    @Override
    public void declare(Actions actions) {
        actions.action("browser_hide_plugins_hide", "Hide from Browser")
               .in(Menu.BROWSER_ITEM)
               .menuOnly()
               .visibleWhen(context -> any(context, false))
               .onRun(context -> change(context, true));

        actions.action("browser_hide_plugins_show", "Show in Browser")
               .in(Menu.BROWSER_ITEM)
               .menuOnly()
               .visibleWhen(context -> any(context, true))
               .onRun(context -> change(context, false));

        actions.action("browser_hide_plugins_hide_vendor", "Hide All from This Vendor")
               .in(Menu.BROWSER_ITEM)
               .menuOnly()
               .visibleWhen(context -> {
                   String vendor = vendor(context);
                   return !vendor.isEmpty() && !HiddenList.hasRule(HiddenList.vendorRule(vendor));
               })
               .onRun(context -> {
                   String vendor = vendor(context);
                   if (vendor.isEmpty()) return;
                   HiddenList.addRule(HiddenList.vendorRule(vendor));
                   HiddenPanel.afterChange();
                   notify(hiddenText("every plug-in from " + vendor));
               });

        // One per format, each shown only on plug-ins of its format, so the menu names the format.
        for (String format : new String[] { "clap", "vst3", "vst2" }) {
            String label = HiddenList.formatLabel(format);
            actions.action("browser_hide_plugins_hide_" + format, "Hide All " + label + " Plug-ins")
                   .in(Menu.BROWSER_ITEM)
                   .menuOnly()
                   .visibleWhen(context -> format.equals(HiddenList.formatOf(context.item()))
                           && !HiddenList.hasRule(HiddenList.formatRule(format)))
                   .onRun(context -> {
                       HiddenList.addRule(HiddenList.formatRule(format));
                       HiddenPanel.afterChange();
                       notify(hiddenText("every " + label + " plug-in"));
                   });
        }

        actions.action("browser_hide_plugins_toggle", "Toggle Plug-in Hiding")
               .onRun(context -> {
                   boolean on = !HiddenList.enabled();
                   HiddenList.setEnabled(on);
                   HiddenPanel.afterChange();
                   notify(on ? "Plug-in hiding is on" : "Plug-in hiding is off: every plug-in shows in the browser");
               });

        actions.action("browser_hide_plugins_show_all", "Unhide All Plug-ins")
               .onRun(context -> {
                   HiddenList.Snapshot before = HiddenList.snapshot();
                   int count = before.keys().size() + before.rules().size();
                   HiddenList.clear();
                   HiddenPanel.afterChange();
                   notify(count == 0 ? "Nothing was hidden" : "Everything shows in the browser again");
               });
    }

    /** The right-clicked plug-in's vendor, or "". */
    private static String vendor(ActionContext context) {
        Object item = context.item();
        return HiddenList.formatOf(item) == null ? "" : HiddenList.vendorOf(item);
    }

    /** The right-clicked results that are plug-ins, key to vendor: every selected one, else the one clicked. */
    private static Map<String, String> plugins(ActionContext context) {
        Map<String, String> plugins = new LinkedHashMap<>();
        List<Object> items = context.items();
        if (items != null) for (Object item : items) add(plugins, item);
        if (plugins.isEmpty()) add(plugins, context.item());
        return plugins;
    }

    private static void add(Map<String, String> plugins, Object item) {
        String key = item == null ? null : HiddenList.keyOf(item);
        if (key != null) plugins.putIfAbsent(key, HiddenList.vendorOf(item));
    }

    /** Whether any right-clicked plug-in is on the list ({@code hidden}) or not. */
    private static boolean any(ActionContext context, boolean hidden) {
        for (String key : plugins(context).keySet()) if (HiddenList.contains(key) == hidden) return true;
        return false;
    }

    private static void change(ActionContext context, boolean hide) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<String, String> plugin : plugins(context).entrySet()) {
            if (HiddenList.contains(plugin.getKey()) == hide) continue;
            keys.add(plugin.getKey());
            HiddenList.noteVendor(plugin.getKey(), plugin.getValue());
        }
        if (keys.isEmpty()) return;
        if (hide) HiddenList.add(keys);
        else HiddenList.remove(keys);
        HiddenPanel.afterChange();
        String what = keys.size() == 1 ? HiddenList.label(keys.iterator().next()) : keys.size() + " plug-ins";
        notify(hide ? hiddenText(what) : "Shown in the browser again: " + what);
    }

    private static String hiddenText(String what) {
        return HiddenList.enabled() ? "Hidden from the browser: " + what
                                    : "Added to the hidden list (hiding is off): " + what;
    }

    private static void notify(String text) {
        try {
            CHR.Grk().hUR(text); // Bitwig's on-screen notification
        } catch (Throwable t) {
            HiddenList.MOD.log(text);
        }
    }
}
