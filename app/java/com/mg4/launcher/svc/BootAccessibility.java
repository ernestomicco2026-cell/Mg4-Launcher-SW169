package com.mg4.launcher.svc;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.mg4.launcher.apps.MgApps;

import java.util.Locale;

/**
 * Avvio rapido all'accensione. Android collega i servizi di accessibilità all'avvio del sistema, molto prima di
 * BOOT_COMPLETED (che sull'auto arriva 30 s circa dopo l'accensione): appena la home MG compare in primo piano
 * nella finestra di avvio, il launcher torna davanti.
 * <p>
 * Riceve solo TYPE_WINDOW_STATE_CHANGED e ne usa solo il nome del pacchetto: nessun contenuto delle finestre
 * (canRetrieveWindowContent = false).
 * <ul>
 *   <li>Finestra di avvio: dall'accensione (o dal risveglio dello schermo) fino a 5 s dopo che il nostro launcher
 *       è in primo piano, al massimo 60 s.</li>
 *   <li>Al massimo 3 tentativi; nessun intervento se la home MG è stata aperta apposta dal launcher o se in primo
 *       piano c'è un'altra app (retrocamera, CarPlay, Android Auto, chiamata, avvisi): si interviene solo quando
 *       compare proprio la home MG.</li>
 * </ul>
 */
public final class BootAccessibility extends AccessibilityService {

    private static final long WINDOW_MS = 60_000, OURS_STABLE_MS = 5000, BOOT_WINDOW_MS = 120_000;
    private static final int MAX_RESTARTS = 3;

    /** true mentre il servizio è collegato (il controllo periodico della 0.2.7 resta solo come riserva). */
    public static volatile boolean connected;

    private long windowStart = -1, oursSince = -1;
    private int restarts;
    private String lastPkg = "";

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            openWindow("risveglio schermo");
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        BootTimes.accessibility();
        Diag.put("avvio.accessibilita", "attivo (collegato dopo " + BootTimes.sec(SystemClock.elapsedRealtime()) + ")");
        // collegato poco dopo l'accensione: finestra di avvio aperta da subito
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) openWindow("accensione");
        try {
            registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        } catch (Exception e) {
            Diag.error("avvio.accessibilita", e.toString());
        }
    }

    private void openWindow(String why) {
        windowStart = SystemClock.elapsedRealtime();
        oursSince = -1;
        restarts = 0;
        Diag.putQuiet("avvio.finestra", why + " a " + BootTimes.sec(windowStart));
        final long ws = windowStart;
        getMainHandlerCompat().postDelayed(() -> {
            if (windowStart == ws) closeWindow(restarts > 0 ? "sì (tempo scaduto)" : "no (tempo scaduto)");
        }, WINDOW_MS + 500);
    }

    /** Chiude la finestra se il nostro launcher è rimasto in primo piano per 5 s (anche senza altri eventi). */
    private void scheduleStableCheck() {
        getMainHandlerCompat().postDelayed(() -> {
            if (windowStart >= 0 && oursSince >= 0 && SystemClock.elapsedRealtime() - oursSince >= OURS_STABLE_MS)
                closeWindow(restarts > 0 ? "sì" : "no (già in primo piano)");
        }, OURS_STABLE_MS + 300);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        long now = SystemClock.elapsedRealtime();
        boolean ours = getPackageName().equals(pkg);
        boolean mg = MgApps.HOME_PKG.equals(pkg);
        if (ours) BootTimes.ours(now);
        if (mg) BootTimes.mgHome(now);
        if (!pkg.equals(lastPkg)) {
            lastPkg = pkg;
            if (windowStart >= 0) Diag.putQuiet("avvio.ultimoPrimoPiano", pkg + " dopo " + BootTimes.sec(now));
        }
        if (windowStart < 0) return;
        if (now - windowStart > WINDOW_MS) {
            closeWindow(restarts > 0 ? "sì (tempo scaduto)" : "no (tempo scaduto)");
            return;
        }
        if (ours) {
            if (oursSince < 0) {
                oursSince = now;
                scheduleStableCheck();
            }
            if (now - oursSince >= OURS_STABLE_MS) closeWindow(restarts > 0 ? "sì" : "no (già in primo piano)");
            return;
        }
        if (!mg) return;   // un'altra app (retrocamera, CarPlay, chiamata, avvisi, barra di sistema): non si tocca
        oursSince = -1;
        Diag.put("avvio.primoPiano", pkg + " dopo " + BootTimes.sec(now) + " (accessibilità)");
        if (BootWatcher.originalOpenedOnPurpose(this)) {
            closeWindow("no (home MG aperta apposta)");
            return;
        }
        if (restarts >= MAX_RESTARTS) return;
        restarts++;
        BootWatcher.openOurs(this, restarts, "accessibilità");
    }

    private android.os.Handler handler;

    private android.os.Handler getMainHandlerCompat() {
        if (handler == null) handler = new android.os.Handler(android.os.Looper.getMainLooper());
        return handler;
    }

    private void closeWindow(String result) {
        if (windowStart < 0) return;
        Diag.put("avvio.homeRipresa", result + ", tentativi " + restarts + " (accessibilità)");
        windowStart = -1;
        BootTimes.report();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        try {
            unregisterReceiver(screenOn);
        } catch (Exception ignored) {
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        connected = false;
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------------------
    // Attivazione automatica (WRITE_SECURE_SETTINGS, concesso dalla firma platform)
    // ---------------------------------------------------------------------------------------------

    /** Attiva il servizio in modo permanente, senza togliere gli altri servizi di accessibilità già attivi. */
    public static void ensureEnabled(Context c) {
        try {
            String me = new ComponentName(c, BootAccessibility.class).flattenToString();
            String shortMe = new ComponentName(c, BootAccessibility.class).flattenToShortString();
            android.content.ContentResolver cr = c.getContentResolver();
            String list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean present = false;
            if (!TextUtils.isEmpty(list)) {
                for (String s : list.split(":")) {
                    if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(shortMe)) present = true;
                }
            }
            boolean on = Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;
            if (present && on) {
                Diag.put("avvio.accessibilita", connected ? "attivo" : "attivo (in attesa di collegamento)");
                return;
            }
            if (!present) {
                String nl = TextUtils.isEmpty(list) ? me : list + ":" + me;
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nl);
            }
            if (!on) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Diag.put("avvio.accessibilita", "attivato ora");
        } catch (Throwable e) {
            Diag.put("avvio.accessibilita", "errore: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tempi dell'avvio (SystemClock.elapsedRealtime = dall'accensione)
    // ---------------------------------------------------------------------------------------------

    public static final class BootTimes {
        private static long accessibility = -1, boot = -1, mg = -1, ours = -1;

        private BootTimes() {}

        static synchronized void accessibility() {
            if (accessibility < 0) accessibility = SystemClock.elapsedRealtime();
            report();
        }

        public static synchronized void boot() {
            if (boot < 0) boot = SystemClock.elapsedRealtime();
            report();
        }

        static synchronized void mgHome(long t) {
            if (mg < 0) {
                mg = t;
                report();
            }
        }

        /** Primo momento in cui il nostro launcher è in primo piano (anche da MainActivity.onResume). */
        public static synchronized void ours(long t) {
            if (ours < 0) {
                ours = t;
                report();
            }
        }

        static String sec(long ms) {
            return ms < 0 ? "—" : String.format(Locale.ITALY, "%.1fs", ms / 1000f);
        }

        static synchronized void report() {
            long proc = -1;
            try {
                proc = android.os.Process.getStartElapsedRealtime();
            } catch (Throwable ignored) {
            }
            Diag.put("avvio.tempi", "processo " + sec(proc) + " · accessibilità " + sec(accessibility) + " · boot " + sec(boot)
                    + " · home MG " + sec(mg) + " · nostro launcher " + sec(ours));
        }
    }
}
