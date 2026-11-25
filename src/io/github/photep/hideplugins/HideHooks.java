package io.github.photep.hideplugins;

import bitwig.obf.Jp; // a browser search session (the side browser's, the pop-up browser's)
import bitwig.obf.NQ; // a CLAP plug-in record in the library index
import bitwig.obf.OP; // Bitwig's "hide redundant plug-ins" filter: builds a test per index cursor
import bitwig.obf.PF; // a plug-in record in the library index
import bitwig.obf.QM; // a VST2 plug-in record in the library index
import bitwig.obf.QT; // a VST3 plug-in record in the library index
import bitwig.obf.pAe; // a cursor over Bitwig's library index, moved from record to record
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
 * Hides listed plug-ins by marking them redundant in Bitwig's own "hide redundant plug-ins" filter.
 *
 * <p>Bitwig builds that filter's test once per index cursor (one per plug-in format) and runs it as the
 * cursor moves over every record; {@code true} means "redundant", and Bitwig then leaves the record out
 * of the results (and counts it in the hidden plug-in count). So this wraps the test: a listed plug-in is
 * redundant, everything else keeps Bitwig's answer. It works while Bitwig's Should hide redundant
 * Plug-ins setting is on.
 */
public final class HideHooks {
    private static final AtomicBoolean FAILED = new AtomicBoolean();

    /** Browser sessions that have searched, to search again after the list changes. */
    private static final Set<Jp> SESSIONS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private HideHooks() {
    }

    @Hook(id = "hide-listed-plugins", target = OP.class,
          method = "cfM(LpAe;)Ljava/util/function/BooleanSupplier;", at = At.WRAP_RETURN, allReturns = true)
    public static BooleanSupplier redundantTest(@Return BooleanSupplier bitwig, @Arg(0) pAe cursor) {
        try {
            if (!(cursor instanceof PF plugin)) return bitwig;
            String format = cursor instanceof NQ ? "clap" : cursor instanceof QT ? "vst3" : cursor instanceof QM ? "vst2" : null;
            if (format == null) return bitwig;
            // The list is read each time the test runs, never kept: a test Bitwig goes on using after the
            // list changed still hides the right plug-ins.
            return () -> hidden(plugin, format) || (bitwig != null && bitwig.getAsBoolean());
        } catch (Throwable t) {
            failed(t);
            return bitwig;
        }
    }

    /** Remembers each browser session as it builds a search, so {@link #searchAgain()} can reach it. */
    @Hook(id = "remember-session", target = Jp.class, method = "nfq", at = At.ENTRY)
    public static void buildingSearch(@This Jp session) {
        try {
            SESSIONS.add(session);
        } catch (Throwable t) {
            failed(t);
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
            if (name == null || !hidden.names().contains(name)) return false;
            String path = plugin.IwV();       // the record's path inside its plug-in folder
            String key = HiddenList.key(format, HiddenList.bundle(path), name);
            if (!hidden.keys().contains(key)) return false;
            if (!HiddenList.knowsVendor(key)) HiddenList.noteVendor(key, plugin.OzW()); // for the panel
            return true;
        } catch (Throwable t) {
            failed(t);
            return false;
        }
    }

    private static void failed(Throwable t) {
        if (FAILED.compareAndSet(false, true)) HiddenList.MOD.log("hiding failed (logged once); Bitwig's own results are used", t);
    }
}
