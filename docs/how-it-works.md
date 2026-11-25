# How it works

Notes for anyone changing this mod or writing a similar one. Bitwig's internal names are obfuscated and
change with every build; the names below are **Bitwig Studio 6.1.4 on macOS**, the build the mod is
written against (`built_against` in `mod.json`). Patchbay's map packs translate them to other builds.
Some members are named differently in the Windows jar (the one Patchbay's guide and framework sources
use), so take names from the jar of the platform you build on.

## The filter

Bitwig builds each browser search's filter in `Jp.nfq()` (`Jp` is a browser search session, used by the
side browser and the pop-up browser). When the search includes plug-ins and **Should hide redundant
Plug-ins** is on, one of the filter's terms is the redundant plug-in term (`OO`), which makes an `OP`.

`OP.cfM(pAe)` is called once per index cursor, not once per plug-in: there is one cursor per plug-in
format (`QM` VST2, `QT` VST3, `NQ` CLAP; all `PF`). It returns a `BooleanSupplier`, which Bitwig then runs
as it moves the cursor over every record of the index.

- The supplier answers "is this record redundant?". **`true` hides the record.**
- For CLAP, Bitwig returns `null` (nothing is redundant).
- A record's details can only be read while the supplier runs, not when it is built.

Bitwig only adds that term when the search's kinds include plug-ins, the setting is on and at least one
"prefer" setting is on (`OO.Hnt` returns no term otherwise), and some views (the pop-up browser's All
Instruments, for one) run without it. So the mod doesn't rely on Bitwig's term. It adds one of its own to
every search: a `WRAP_RETURN` hook on `Jp.nfq` returns `rXv.cfM(bitwigFilter, ours)`, where `ours` is
`new OP(OO.cfM(), true, false, false, false)`: a filtering term with all three "prefer" switches off, a
combination Bitwig never makes, which is how the second hook recognises it (`OP`'s constructor and those
three fields are opened through the manifest). Being Bitwig's own class, the term needs none of the
filter plumbing written again.

The second hook (`HideHooks.redundantTest`, `WRAP_RETURN` with `allReturns` on `OP.cfM`) answers the
tests of the mod's term from the user's list, and leaves Bitwig's own terms untouched. Per record it reads:

| What | Call |
|---|---|
| path inside its plug-in folder | `pAe.IwV()` |
| plug-in name | `pAe.hUR().cfM()` |
| vendor | `PF.OzW()` |

A right-clicked browser result (`CH`) gives the same identity: its file `CH.Grk()`, name `CN.wGc()`,
vendor `vE.qoU()`, and its format by class (`LG` VST2, `LT` VST3, `Am` CLAP). The mod's key for a plug-in
is `format / bundle file name / name`.

Order of checks per record, cheapest first: hiding switched on, format rule, vendor rule (compared
without case), then the single plug-ins (by name first, then the full key). The hidden list is an
immutable snapshot read on Bitwig's library thread; every exception is caught and Bitwig's own answer used.

## Refreshing the browser

The hook on `Jp.nfq` also remembers each session (weakly). After a change, the mod calls each
session's own "run the search again", `Jp.HhX()` (private in Bitwig; opened through the manifest's
`opens`). It returns at once for a session that isn't active.

## The panel

Built from Bitwig's own widgets, following Bitwig's Project panel:

- `dWb` rows and columns, `dut`/`duY` labels, `ecp` wrapping text, `dXp` the rule after a heading,
  `dzv` checkboxes on an `AQw` value, colours from the theme (`dpN.TVZ().Pus().BAa()`).
- Lists are `dWo`, bound to Bitwig's observable list `r3s`; rows are made by a `dJV`. Changing the `r3s`
  updates every open panel.
- Scrolling is `dSK.MRY(widget)` with `iHb(false)`, `qoU(true)`, as the Commander sets up its results. A
  bare `dTA` scrolls but draws no scroll indicator.
- The search field is `ecz.LzO()`, wired as the Commander's: `fJR(listener)` (an `ejl`) and
  `cfM(factory)` (an `ecE`). On each change Bitwig calls `factory.createFilter(text)`, then
  `listener.cfM(filter)`.

## Pitfalls met on the way

- **Never implement an obfuscated Bitwig interface with a lambda or method reference.** Patchbay renames
  the methods a mod's *named class* overrides, but not a lambda's, so a lambda throws
  `AbstractMethodError` on any build where the method's name differs. The offline check doesn't catch it.
- The theme's secondary text colour (`eff.d3()`) is unreadable on the panel background. Bitwig's own hint
  texts set no colour.
- Overloads that differ only by an obfuscated parameter type can be ambiguous to `javac` or bind to a
  private overload; cast to the type Bitwig's own code passes.
- The offline check (`loader/prepare-mod.sh`) proves hook targets and linkage on a build, not behaviour.

## Storage

Patchbay keeps the mod's settings (`Bml.mod(...).settings()`):

| Key | Value |
|---|---|
| `enabled` | whether hiding is on |
| `hidden` | one line per plug-in: format, bundle file name, name and (where known) vendor, tab-separated |
| `rules` | one line per rule: `vendor` or `format`, a tab, the vendor's name or the format |

These, the mod ID and the action IDs (`browser_hide_plugins_…`) must not change between releases: users'
lists and shortcuts depend on them.
