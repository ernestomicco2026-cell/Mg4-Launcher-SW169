package com.mg4.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.mg4.launcher.apps.AppCatalog;
import com.mg4.launcher.apps.MgApps;
import com.mg4.launcher.apps.IconStyler;
import com.mg4.launcher.svc.Diag;
import com.mg4.launcher.svc.MediaClient;
import com.mg4.launcher.svc.NavClient;
import com.mg4.launcher.svc.Updater;
import com.mg4.launcher.svc.VehicleClient;
import com.mg4.launcher.ui.AppsView;
import com.mg4.launcher.ui.HomeView;
import com.mg4.launcher.ui.Lang;
import com.mg4.launcher.ui.Pager;
import com.mg4.launcher.ui.Prefs;
import com.mg4.launcher.ui.SettingsView;
import com.mg4.launcher.ui.Theme;
import com.mg4.launcher.ui.Ui;
import com.mg4.launcher.ui.UpdateBanner;

/** Home del launcher: costruisce l'interfaccia e collega i servizi dell'auto. */
public final class MainActivity extends Activity implements HomeView.Host, AppsView.Host, SettingsView.Host {

    /** Schermo intero: nasconde barra di stato e dock MG (la SystemUI SAIC segue i flag standard). */
    private static final int IMMERSIVE = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

    private Prefs prefs;
    private AppCatalog catalog;
    private VehicleClient vehicle;
    private MediaClient media;
    private NavClient nav;
    private Updater updater;
    private com.mg4.launcher.svc.LogUploader logs;
    private UpdateBanner banner;
    private Updater.Release pendingUpdate;
    private static final long UPDATE_FIRST_MS = 2 * 60_000, UPDATE_PERIOD_MS = 6 * 3_600_000L, SNOOZE_MS = 24 * 3_600_000L;

    private FrameLayout root;
    private Theme theme;
    private HomeView home;
    private AppsView apps;
    private Pager pager;
    private SettingsView settings;
    private int lastUiMode;
    private java.util.Locale appliedLocale;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        appliedLocale = Lang.apply(this, prefs.language());
        catalog = new AppCatalog(this);
        vehicle = new VehicleClient(this);
        vehicle.setReadInsideTemp(prefs.showInsideTemp());
        media = new MediaClient(this);
        nav = new NavClient(this);
        updater = new Updater(this);
        logs = new com.mg4.launcher.svc.LogUploader(this);
        lastUiMode = getResources().getConfiguration().uiMode;

        root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener(this::applyInsets);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.requestApplyInsets();
        });
        setContentView(root);
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(vis -> {
            // se il sistema rimostra le barre (es. dopo un popup) le rinasconde
            if ((vis & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) root.postDelayed(this::goFullscreen, 2500);
        });
        goFullscreen();
        build();

        vehicle.setListener(s -> {
            if (home != null) home.onVehicle(s);
        });
        media.setListener(s -> {
            if (home != null) home.onMedia(s);
        });
        nav.setListener(s -> {
            if (home != null) home.onNav(s);
        });
        vehicle.start();
        media.start();
        logs.start();
        root.postDelayed(updateCheck, UPDATE_FIRST_MS);
        // SWI69: nessun navigatore MG, il servizio di navigazione non si usa (resta nav.last() vuoto)

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        // all'avvio dell'auto le app utente compaiono solo dopo lo sblocco dell'utente Android
        registerReceiver(packages, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        root.postDelayed(saveDiag, 15000);
        // riconferma che il launcher è la home predefinita (un aggiornamento di sistema può averla azzerata)
        new Thread(() -> {
            com.mg4.launcher.svc.HomeDefault.ensure(getApplicationContext());
            // servizio di accessibilità per l'avvio rapido: attivato una volta e poi resta attivo
            com.mg4.launcher.svc.BootAccessibility.ensureEnabled(getApplicationContext());
            com.mg4.launcher.svc.BootAccessibility.resolvedHome(getApplicationContext());
            com.mg4.launcher.svc.BootAccessibility.reportKeyApps(getApplicationContext());
        }, "mg4-home").start();
    }

    /** Ogni 15 s salva la diagnostica su file: adb pull /sdcard/Android/data/com.mg4.launcher/files/diagnostica.txt */
    private final Runnable saveDiag = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> Diag.save(MainActivity.this), "mg4-diag").start();
            root.postDelayed(this, 15000);
        }
    };

    /**
     * Schermo intero: il contenuto occupa sempre tutti i 1920×720 px. Le barre di sistema, se ricompaiono
     * per un attimo (trascinando dal bordo), si sovrappongono senza spostare il layout.
     */
    private WindowInsets applyInsets(View v, WindowInsets in) {
        v.setPadding(0, 0, 0, 0);
        Diag.put("finestra", v.getWidth() + "×" + v.getHeight() + " · margini sistema " + in.getSystemWindowInsetLeft() + ","
                + in.getSystemWindowInsetTop() + "," + in.getSystemWindowInsetRight() + "," + in.getSystemWindowInsetBottom());
        return in.consumeSystemWindowInsets();
    }

    private void goFullscreen() {
        View d = getWindow().getDecorView();
        if (d.getSystemUiVisibility() != IMMERSIVE) d.setSystemUiVisibility(IMMERSIVE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        goFullscreen();
        com.mg4.launcher.svc.BootAccessibility.BootTimes.ours(android.os.SystemClock.elapsedRealtime());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goFullscreen();
    }

    private void build() {
        theme = Theme.resolve(this, prefs);
        Ui.K = prefs.uiScaleFactor();   // dimensione dei comandi toccabili
        root.setBackgroundColor(theme.bg);
        root.removeAllViews();
        int keepPage = pager != null ? pager.page() : 0;
        pager = new Pager(this);
        home = new HomeView(this, theme, prefs, this, vehicle, media, nav);
        apps = new AppsView(this, theme, catalog, prefs, this);
        pager.addView(home);
        pager.addView(apps);
        pager.setListener(p -> {
            if (p == 1) apps.reload();
        });
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        settings = null;
        pager.show(keepPage, false);
        home.onVehicle(vehicle.last());
        home.onMedia(media.last());
        home.onNav(nav.last());
        // avviso discreto di nuova versione, in basso a destra sopra la barra
        banner = new UpdateBanner(this, theme, new UpdateBanner.Actions() {
            @Override
            public void onUpdate() {
                Updater.Release r = pendingUpdate;
                openSettings();
                if (r != null) updater.install(r, prefs.githubToken());
            }

            @Override
            public void onLater() {
                if (pendingUpdate != null) prefs.snoozeUpdate(pendingUpdate.tag, System.currentTimeMillis() + SNOOZE_MS);
            }
        });
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM | android.view.Gravity.END);
        bl.setMargins(0, 0, Ui.dp(this, 40), Ui.dp(this, 132));
        root.addView(banner, bl);
        getWindow().setStatusBarColor(theme.bg);
        root.requestApplyInsets();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // il collegamento ai servizi resta attivo; qui si riprendono solo le letture periodiche
        vehicle.resume();
        media.resume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        vehicle.pause();
        media.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        root.removeCallbacks(saveDiag);
        root.removeCallbacks(updateCheck);
        logs.stop();
        vehicle.stop();
        media.stop();
        nav.stop();
        try {
            unregisterReceiver(packages);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // tasto Home: torna sempre alla schermata principale
        closeSettings();
        closeApps();
    }

    @Override
    public void onBackPressed() {
        if (settings != null) closeSettings();
        else if (pager != null && pager.page() != 0) closeApps();
        // altrimenti il launcher resta dov'è
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        // lingua: il sistema ha appena riscritto le risorse con la sua lingua; si riapplica quella scelta
        // e, se è cambiata (es. "Come l'auto" e lingua dell'auto modificata), si ricostruisce l'interfaccia
        java.util.Locale loc = Lang.apply(this, prefs.language());
        if (!loc.equals(appliedLocale)) {
            appliedLocale = loc;
            IconStyler.clearCache();
            build();
            return;
        }
        if (cfg.uiMode != lastUiMode) {
            lastUiMode = cfg.uiMode;
            if (Prefs.THEME_AUTO.equals(prefs.themeMode())) {
                IconStyler.clearCache();
                build();
            }
        }
    }

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            IconStyler.clearCache();
            if (apps != null) apps.reload();
        }
    };

    // --- HomeView.Host ------------------------------------------------------------------------

    /** Controllo silenzioso degli aggiornamenti: all'avvio (dopo 2 minuti) e poi ogni 6 ore. */
    private final Runnable updateCheck = new Runnable() {
        @Override
        public void run() {
            updater.checkQuiet(prefs.updateRepo(), r -> {
                if (r == null || !r.newer || r.apkUrl == null) return;
                pendingUpdate = r;
                boolean snoozed = r.tag.equals(prefs.snoozedUpdateTag()) && System.currentTimeMillis() < prefs.snoozedUpdateUntil();
                if (!snoozed && settings == null && banner != null) banner.show(r.tag);
            });
            root.postDelayed(this, UPDATE_PERIOD_MS);
        }
    };

    @Override
    public void openApps() {
        if (pager != null) pager.show(1, true);
    }

    @Override
    public void openSettings() {
        if (banner != null) banner.hide();
        if (settings == null) {
            settings = new SettingsView(this, theme, prefs, this);
            root.addView(settings, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    @Override
    public void launch(String pkg, String cls) {
        catalog.launchComponent(pkg, cls);
    }

    /** App Energia SWI69 (ricarica, scarica V2L, consumi): non ha schede apribili dall'esterno. */
    @Override
    public void openEnergy(String module) {
        if (!catalog.launchComponent(MgApps.ENERGY_PKG, MgApps.ENERGY_CLS)) {
            com.mg4.launcher.svc.Diag.error("energia " + module, "app Energia non trovata");
        }
    }

    @Override
    public void launchNavigator() {
        launchNavigator(prefs.navApp());
    }

    /** Apre il navigatore scelto: MG, quello del telefono (CarPlay/Android Auto) o un'app installata. */
    @Override
    public void launchNavigator(String which) {
        // SWI69: il navigatore è quello del telefono (CarPlay / Android Auto) o un'app installata sull'auto
        boolean phone = media.last().deviceType != 0;
        if (!Prefs.NAV_AUTO.equals(which) && !Prefs.NAV_PHONE.equals(which) && !Prefs.NAV_MG.equals(which)) {
            Intent li = getPackageManager().getLaunchIntentForPackage(which);
            if (li != null) {
                try {
                    startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (Exception e) {
                    Diag.error("navigatore.app", e.toString());
                }
            }
        }
        if (phone) {
            media.openProjection();
            media.openRemoteNavigation();
            return;
        }
        java.util.List<AppCatalog.App> others = otherNavApps();
        if (!others.isEmpty()) {
            try {
                startActivity(others.get(0).intent());
                return;
            } catch (Exception e) {
                Diag.error("navigatore.altro", e.toString());
            }
        }
        android.widget.Toast.makeText(this, getString(R.string.nav_sub_nophone),
                android.widget.Toast.LENGTH_LONG).show();
    }

    @Override
    public java.util.List<AppCatalog.App> otherNavApps() {
        return catalog.otherNavApps(nav.last().mapPackage);
    }

    // --- AppsView.Host ------------------------------------------------------------------------

    @Override
    public void closeApps() {
        if (pager != null) pager.show(0, true);
    }

    @Override
    public String navPackageHint() {
        return nav.last().mapPackage;
    }

    // --- SettingsView.Host --------------------------------------------------------------------

    @Override
    public void closeSettings() {
        if (settings != null) {
            root.removeView(settings);
            settings = null;
        }
    }

    @Override
    public void settingsChanged(boolean rebuild) {
        if (rebuild) {
            appliedLocale = Lang.apply(this, prefs.language());
            IconStyler.clearCache();
            build();
            openSettings();
        } else if (home != null) {
            home.applyCarLook();
            home.refreshNav();
            vehicle.setReadInsideTemp(prefs.showInsideTemp());
            home.onVehicle(vehicle.last());   // decimali della batteria e pressioni subito aggiornati
        }
    }

    @Override
    public com.mg4.launcher.svc.LogUploader logs() {
        return logs;
    }

    @Override
    public Updater updater() {
        return updater;
    }

    @Override
    public void openOriginalHome() {
        // aperta apposta: per un po' il controllo all'accensione non riporta in primo piano il launcher
        com.mg4.launcher.svc.BootWatcher.userOpenedOriginal(this);
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName(MgApps.HOME_PKG, MgApps.HOME_CLS))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Diag.error("home originale", e.toString());
        }
    }
}
