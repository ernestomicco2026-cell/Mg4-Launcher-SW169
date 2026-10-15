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
 * Avvio rapido all'accensione e home MG aperta per nome (tasto Home rimappato o firmware). Android collega i servizi di accessibilità all'avvio del sistema, molto prima di
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
 *   <li>Fuori dalla finestra di avvio (0.2.7): con il launcher come home predefinita, la home MG si sostituisce ogni
 *       volta che compare; se ricompare più di 3 volte in 10 s ci si ferma per 2 minuti.</li>
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
        resolvedHome(this);
        new Thread(() -> reportKeyApps(getApplicationContext()), "mg4-tasti").start();
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

    // --- home MG aperta per nome (tasto Home rimappato, firmware) fuori dalla finestra di avvio ---
    private static final long LOOP_WINDOW_MS = 10_000, LOOP_PAUSE_MS = 2 * 60_000L, HOME_CHECK_MS = 20_000;
    private static final int LOOP_MAX = 3;
    private final java.util.ArrayDeque<Long> mgSeen = new java.util.ArrayDeque<>();
    private long pausedUntil = -1, homeCheckedAt = -1;
    private boolean ourHomeDefault;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        long now = SystemClock.elapsedRealtime();
        boolean ours = getPackageName().equals(pkg);
        // solo la schermata principale della home MG (non le guide o le altre pagine aperte dal sistema)
        CharSequence cls = e.getClassName();
        boolean mg = MgApps.HOME_PKG.equals(pkg) && (cls == null || !cls.toString().startsWith(MgApps.HOME_PKG)
                || MgApps.HOME_CLS.equals(cls.toString()));
        if (ours) BootTimes.ours(now);
        if (mg) BootTimes.mgHome(now);
        String prev = lastPkg;
        if (!pkg.equals(lastPkg)) {
            lastPkg = pkg;
            if (windowStart >= 0) Diag.putQuiet("avvio.ultimoPrimoPiano", pkg + " dopo " + BootTimes.sec(now));
        }
        if (windowStart >= 0 && now - windowStart > WINDOW_MS) {
            closeWindow(restarts > 0 ? "sì (tempo scaduto)" : "no (tempo scaduto)");
        }
        boolean inWindow = windowStart >= 0;
        if (ours) {
            if (inWindow) {
                if (oursSince < 0) {
                    oursSince = now;
                    scheduleStableCheck();
                }
                if (now - oursSince >= OURS_STABLE_MS) closeWindow(restarts > 0 ? "sì" : "no (già in primo piano)");
            }
            return;
        }
        if (!mg) return;   // un'altra app (retrocamera, CarPlay, chiamata, avvisi, barra di sistema): non si tocca
        oursSince = -1;
        if (inWindow) Diag.put("avvio.primoPiano", pkg + " dopo " + BootTimes.sec(now) + " (accessibilità)");
        if (BootWatcher.originalOpenedOnPurpose(this)) {
            if (inWindow) closeWindow("no (home MG aperta apposta)");
            Diag.putQuiet("home.tasto", "home MG lasciata (aperta apposta dal launcher)");
            return;
        }
        // si sostituisce solo se il nostro launcher è la home predefinita
        if (!isOurHomeDefault(now)) {
            if (inWindow) closeWindow("no (il launcher non è la home predefinita)");
            Diag.putQuiet("home.tasto", "home MG lasciata (il launcher non è la home predefinita)");
            return;
        }
        // anti-ciclo: più di 3 comparse in 10 s → pausa di 2 minuti
        if (pausedUntil > now) return;
        mgSeen.addLast(now);
        while (!mgSeen.isEmpty() && now - mgSeen.peekFirst() > LOOP_WINDOW_MS) mgSeen.removeFirst();
        if (mgSeen.size() > LOOP_MAX) {
            pausedUntil = now + LOOP_PAUSE_MS;
            mgSeen.clear();
            Diag.put("home.tasto", "sospeso per 2 minuti: la home MG è ricomparsa più di " + LOOP_MAX + " volte in 10 s");
            if (inWindow) closeWindow("no (sospeso: troppe ricomparse)");
            return;
        }
        String origin;
        if (inWindow) {
            if (restarts >= MAX_RESTARTS) return;
            restarts++;
            origin = "avvio";
        } else {
            // fuori dalla finestra di avvio: tasto Home (o un'app che rimappa i tasti); "altro" se l'ha aperta un'app MG
            origin = prev.startsWith("com.saicmotor.") || prev.startsWith("com.saic.") ? "altro (" + prev + ")" : "tasto";
        }
        BootWatcher.openOurs(this, inWindow ? restarts : 1, "accessibilità");
        Diag.put("home.tasto", "home MG sostituita (origine: " + origin + ")");
    }

    /** La home risolta per ACTION_MAIN + CATEGORY_HOME è il nostro launcher? (ricontrollata ogni 20 s) */
    private boolean isOurHomeDefault(long now) {
        if (homeCheckedAt >= 0 && now - homeCheckedAt < HOME_CHECK_MS) return ourHomeDefault;
        homeCheckedAt = now;
        String home = resolvedHome(this);
        ourHomeDefault = getPackageName().equals(home);
        return ourHomeDefault;
    }

    /** Pacchetto della home predefinita (scritto nei log come home.predefinita). */
    public static String resolvedHome(Context c) {
        String pkg;
        try {
            android.content.pm.ResolveInfo ri = c.getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            pkg = ri != null && ri.activityInfo != null ? ri.activityInfo.packageName : "nessuna";
        } catch (Throwable t) {
            pkg = "errore " + t.getClass().getSimpleName();
        }
        Diag.putQuiet("home.predefinita", pkg);
        return pkg;
    }

    /**
     * App che possono rimappare i tasti (es. MG4 Control): servizi di accessibilità che filtrano i tasti, più le app
     * il cui nome lo fa pensare. Scritto nei log come home.appTasti.
     */
    public static void reportKeyApps(Context c) {
        try {
            java.util.Set<String> found = new java.util.LinkedHashSet<>();
            String enabled = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                for (android.accessibilityservice.AccessibilityServiceInfo info : am.getInstalledAccessibilityServiceList()) {
                    if ((info.getCapabilities() & android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS) == 0) continue;
                    String p = info.getResolveInfo().serviceInfo.packageName;
                    if (p.equals(c.getPackageName())) continue;
                    boolean on = enabled != null && enabled.contains(p + "/");
                    found.add(p + (on ? " (attivo)" : ""));
                }
            }
            android.content.pm.PackageManager pm = c.getPackageManager();
            for (android.content.pm.ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 || ai.packageName.equals(c.getPackageName())) continue;
                String label = String.valueOf(pm.getApplicationLabel(ai)).toLowerCase(Locale.ROOT);
                String p = ai.packageName.toLowerCase(Locale.ROOT);
                if (label.contains("mg4 control") || p.contains("mg4control") || label.contains("key mapper") || label.contains("button mapper")
                        || p.contains("keymapper") || p.contains("buttonmapper") || label.contains("rimappa") || label.contains("tasti")) {
                    found.add(ai.packageName + " (" + pm.getApplicationLabel(ai) + ")");
                }
            }
            Diag.put("home.appTasti", found.isEmpty() ? "nessuna trovata" : TextUtils.join(", ", found));
        } catch (Throwable t) {
            Diag.put("home.appTasti", "errore " + t.getClass().getSimpleName());
        }
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
