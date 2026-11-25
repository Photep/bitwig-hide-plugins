package io.github.photep.hideplugins;

import bml.panels.PanelProvider;
import bml.panels.Panels;

/** The Hidden Plug-ins panel, a tab on the browser side beside Browser, Project, Studio I/O and Mappings. */
public final class HidePanels implements PanelProvider {
    @Override
    public void declare(Panels panels) {
        panels.view("hidden", "Hidden Plug-ins")
              .icon("hidden_icon")              // Bitwig's own "hidden" icon (mod.json resources)
              .content(HiddenPanel::create)     // built for each window that shows it
              .side("browser");                 // a tab on the browser side
    }
}
