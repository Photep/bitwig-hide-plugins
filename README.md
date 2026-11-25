# Browser: Hide Plug-ins

A mod for [Patchbay Mod Loader](https://github.com/shaboogen-dev/Patchbay/releases) that hides plug-ins
you never want to add from Bitwig Studio's browser.

**Not affiliated with Bitwig Studio or Bitwig GmbH, or with Patchbay's author.**

Typical uses:

- older versions you keep installed only so old projects still open (Pro-Q 2 and Pro-Q 3 when you only
  use Pro-Q 4);
- a vendor's plug-ins you own but never use;
- a whole format, such as every VST2.

Hidden plug-ins stay installed and scanned: projects that use them open and play as before. Only the
browser's results leave them out, in the side browser and the pop-up browser.

## Requirements

- Patchbay Mod Loader 0.36.0-dev.17 or newer, with its **Framework: Actions and Menus**,
  **Framework: Settings** and **Framework: Panels** (Patchbay ticks them with the mod).
- Bitwig Studio 6.x, as far as Patchbay supports it.

**Tested on:** macOS, Bitwig Studio 6.1.4 and 6.1.7. It passes Patchbay's offline check on macOS 6.1.1 and
6.1.3. Windows and Linux are untested: Patchbay translates the mod to them, but nobody has tried it there
yet. Reports are welcome.

## Install

1. Download `browser-hide-plugins-<version>.zip` from
   [Releases](https://github.com/Photep/bitwig-hide-plugins/releases). Don't unpack it.
2. In Patchbay, **Mods** → **Add Mod…** and pick the ZIP (or drag it onto the window).
3. Tick **Browser: Hide Plug-ins** and click **Load**.

## Use

- **Hide one:** right-click a plug-in in the browser's results → **Hide from Browser**. With several
  results selected, all the selected plug-ins are hidden. This is one format at a time: hiding the VST3
  copy of a plug-in leaves its CLAP copy.
- **Hide many:** right-click a plug-in → **Hide All from This Vendor** hides everything from its vendor,
  in every format; **Hide All VST2 Plug-ins** (or VST3, or CLAP, after the plug-in you clicked) hides
  every plug-in of that format. Each is a rule: plug-ins installed later are hidden too.
- **The Hidden Plug-ins panel** is a tab on the browser side beside Browser, Project, Studio I/O and
  Mappings (its button is in the bottom bar with theirs; **Show Hidden Plug-ins** in the Commander opens it):
  - **Search** at the top narrows the rows by name, vendor or file name.
  - **Hide listed plug-ins** switches all hiding off and on, keeping the lists.
  - **Rules** lists the hidden vendors and formats; untick one to remove it.
  - **CLAP / VST3 / VST2** list the hidden plug-ins with their vendor. Untick one and it's back in the
    browser at once; the row stays until Bitwig restarts, so you can tick it again. The checkbox on a
    section's heading hides or shows every row listed there, so with Search you can handle a group at once.
- **Peek:** the **Toggle Plug-in Hiding** action (Commander, or give it a shortcut) switches all hiding
  off and on, for the one time you need a hidden plug-in. Settings → Mods → Hide Plug-ins has the same switch.
- **Start over:** the **Unhide All Plug-ins** action (Commander) empties the lists and the rules.

## Notes and limits

- Hiding doesn't depend on any Bitwig setting: the mod adds its own filter to every browser search.
- For duplicate formats of one plug-in (VST2, VST3, CLAP), Bitwig's own settings do the job: **Should
  hide redundant Plug-ins** and **Prefer CLAP over VST (when available)**. They work alongside the mod.
- A plug-in hidden by a rule can't be shown on its own: untick the rule.
- A plug-in is identified by its format, its bundle's file name and its name. Two copies of the same
  bundle in different folders are hidden together.
- The lists are kept in Patchbay's settings folder (`mods/browser-hide-plugins/settings.properties`), so
  they survive Bitwig restarts and new versions of Patchbay.
- Turning the mod off in Patchbay brings every plug-in back; the lists are kept for when it's on again.

## Building from source

You need a Patchbay install (for its mod kit and frameworks), JDK 25, Python 3 and Bitwig Studio 6.1.4,
the version the mod's names are written in.

```sh
BML_HOME=/path/to/BML-0.36.0-dev.17-macos sh tools/build.sh
```

The installable ZIP is written to `out/`. `tools/build.sh` lists the other settings (`JDK`, `BITWIG_APP`,
`BITWIG_BIN`). [docs/how-it-works.md](docs/how-it-works.md) explains what the mod hooks in Bitwig and why.

## Licence

MIT: see [LICENSE](LICENSE). The mod contains none of Bitwig's files.
