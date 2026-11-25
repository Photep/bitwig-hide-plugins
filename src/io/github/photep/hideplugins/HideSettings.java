package io.github.photep.hideplugins;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** Settings > Mods > Hide Plug-ins: the switch for the whole list. */
public final class HideSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods"); // the shared page: same ID and label in every mod
        Section mine = preferences.section(mods, "browser_hide_plugins", "Hide Plug-ins");
        preferences.checkbox("enabled", "Hide listed plug-ins")
                   .in(mine)
                   .value(HiddenList::enabled, on -> {
                       HiddenList.setEnabled(on);
                       HiddenPanel.afterChange();
                   });
    }
}
