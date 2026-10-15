package com.mg4.launcher.apps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import com.mg4.launcher.R;
import com.mg4.launcher.svc.Diag;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Elenco delle app: quelle dell'auto (componenti SAIC noti, con icone disegnate) e quelle
 * installate dall'utente (lette dal sistema, aggiornate a ogni installazione/disinstallazione).
 */
public final class AppCatalog {

    public static final class App {
        public final String label;
        public final ComponentName component;   // null = usa launchIntent
        public final Intent launchIntent;
        public final int iconRes;               // 0 = icona ridisegnata dall'originale
        public final boolean highlight;
        public final String packageName;

        App(String label, ComponentName cn, Intent li, int iconRes, boolean highlight, String pkg) {
            this.label = label;
            this.component = cn;
            this.launchIntent = li;
            this.iconRes = iconRes;
            this.highlight = highlight;
            this.packageName = pkg;
        }

        public Intent intent() {
            Intent i;
            if (component != null) {
                i = new Intent(Intent.ACTION_MAIN).setComponent(component);
            } else {
                i = new Intent(launchIntent);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return i;
        }
    }

    /** App dell'auto SWI69 (EH32 Color Radio), nell'ordine del launcher MG; quelle assenti non compaiono. */
    private static final String[][] CAR_APPS = {
            {"@app_vehicle", MgApps.VEHICLE_PKG, MgApps.VEHICLE_CLS, "car"},
            {"@app_climate", MgApps.HVAC_PKG, MgApps.HVAC_CLS, "fan"},
            {"@app_energy", MgApps.ENERGY_PKG, MgApps.ENERGY_CLS, "bolt"},
            {"@app_phone", MgApps.PHONE_PKG, MgApps.PHONE_CLS, "phone"},
            {"@app_radio", MgApps.RADIO_PKG, MgApps.RADIO_CLS, "radio"},
            {"@app_music", MgApps.MEDIA_PKG, MgApps.MEDIA_CLS, "music"},
            {"@app_files", MgApps.FILES_PKG, MgApps.FILES_CLS, "video"},
            {"@app_settings", MgApps.SETTINGS_PKG, MgApps.SETTINGS_CLS, "gear"},
            {"@app_sos", MgApps.SOS_PKG, MgApps.SOS_CLS, "sos"},
    };

    /** Navigatori possibili (dall'originale: Telenav, iGo, SAIC). */
    private static final String[] NAV_PACKAGES = {"com.telenav.app.arp", "com.nng.igo.primong", "com.saicmotor.navigation"};

    /** Pacchetti di sistema da non mostrare tra le app installate. */
    private static final String[] HIDDEN_PREFIX = {"com.saicmotor.", "com.saic.", "com.allgo.", "com.android.", "android",
            "com.yfve.", "com.mediatek.", "com.mtk.", "com.google.android.car", "com.telenav.", "com.nng."};

    private final Context ctx;
    private final PackageManager pm;

    public AppCatalog(Context c) {
        ctx = c.getApplicationContext();
        pm = ctx.getPackageManager();
    }

    public static int iconFor(Context c, String name) {
        return c.getResources().getIdentifier("ic_" + name, "drawable", c.getPackageName());
    }

    /** Nome dell'app: "@chiave" = testo tradotto nelle risorse strings.xml. */
    private String label(String key) {
        if (!key.startsWith("@")) return key;
        int id = ctx.getResources().getIdentifier(key.substring(1), "string", ctx.getPackageName());
        return id != 0 ? ctx.getString(id) : key.substring(1);
    }

    public List<App> carApps(String navPackageHint) {
        List<App> out = new ArrayList<>();
        for (String[] a : CAR_APPS) {
            int icon = iconFor(ctx, a[3]);
            String label = label(a[0]);
            if ("@nav".equals(a[1])) {
                Intent nav = navIntent(navPackageHint);
                if (nav != null) {
                    String pkg = nav.getComponent() != null ? nav.getComponent().getPackageName() : String.valueOf(nav.getPackage());
                    out.add(new App(label, null, nav, icon, false, pkg));
                }
                continue;
            }
            ComponentName cn = new ComponentName(a[1], a[2]);
            if (exists(cn)) {
                out.add(new App(label, cn, null, icon, false, a[1]));
            } else {
                Intent li = pm.getLaunchIntentForPackage(a[1]);
                if (li != null) {
                    out.add(new App(label, null, li, icon, false, a[1]));
                }
            }
        }
        addAndroidSystemApps(out);
        return out;
    }

    /** Gestori file di Android da cercare sull'auto, in quest'ordine. */
    private static final String[] FILE_MANAGERS = {"com.android.documentsui", "com.mediatek.filemanager", "com.android.fileexplorer"};

    /**
     * In fondo alle app dell'auto: "Impostazioni Android" e "File Android", ciascuna solo se l'attività esiste davvero.
     * HIDDEN_PREFIX resta com'è (i pacchetti com.android. non compaiono tra le app installate).
     */
    private void addAndroidSystemApps(List<App> out) {
        String settingsDiag = "assente", filesDiag = "assente";
        try {
            // solo le impostazioni di Android (com.android.settings), non quelle MG che sono già tra le app dell'auto
            Intent li = new Intent(android.provider.Settings.ACTION_SETTINGS).setPackage("com.android.settings");
            if (pm.resolveActivity(li, 0) == null) li = pm.getLaunchIntentForPackage("com.android.settings");
            if (li == null) {
                ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.Settings");
                if (exists(cn)) li = new Intent(Intent.ACTION_MAIN).setComponent(cn);
            }
            if (li != null) {
                out.add(new App(label("@app_android_settings"), null, li, iconFor(ctx, "android_settings"), false, "com.android.settings"));
                settingsDiag = "ok";
            }
        } catch (Exception e) {
            settingsDiag = "errore " + e.getClass().getSimpleName();
        }
        try {
            Intent files = fileManagerIntent();
            if (files != null) {
                String pkg = files.getComponent() != null ? files.getComponent().getPackageName() : String.valueOf(files.getPackage());
                out.add(new App(label("@app_android_files"), null, files, iconFor(ctx, "folder"), false, pkg));
                filesDiag = pkg;
            }
        } catch (Exception e) {
            filesDiag = "errore " + e.getClass().getSimpleName();
        }
        Diag.put("app.sistema", "impostazioni " + settingsDiag + ", file " + filesDiag);
    }

    /** Gestore file di Android: prima i pacchetti noti, poi qualsiasi attività che apre documenti. */
    private Intent fileManagerIntent() {
        for (String p : FILE_MANAGERS) {
            Intent li = pm.getLaunchIntentForPackage(p);
            if (li != null) return li;
            if ("com.android.documentsui".equals(p)) {
                // DocumentsUI senza icona nel launcher: si apre sulla memoria interna in modalità esplorazione
                Intent view = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(android.net.Uri.parse("content://com.android.externalstorage.documents/root/primary"),
                                "vnd.android.document/root")
                        .setPackage(p);
                if (pm.resolveActivity(view, 0) != null) return view;
                ComponentName la = new ComponentName(p, "com.android.documentsui.LauncherActivity");
                if (exists(la)) return new Intent(Intent.ACTION_MAIN).setComponent(la);
            }
        }
        for (String action : new String[]{Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT}) {
            Intent pick = new Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            ResolveInfo ri = pm.resolveActivity(pick, 0);
            if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) {
                return pick.setPackage(ri.activityInfo.packageName);
            }
        }
        return null;
    }

    /** Navigatori di terze parti installati sull'auto (Waze, Google Maps, OsmAnd, ...). */
    public List<App> otherNavApps(String mgNavPackage) {
        Set<String> pkgs = new java.util.LinkedHashSet<>();
        Collections.addAll(pkgs, "com.waze", "com.google.android.apps.maps", "com.google.android.apps.maps.auto",
                "net.osmand", "net.osmand.plus", "com.sygic.aura", "com.here.app.maps", "com.tomtom.gplay.navapp",
                "com.mapfactor.navigator", "cz.seznam.mapy");
        try {
            Intent geo = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=roma"));
            for (ResolveInfo ri : pm.queryIntentActivities(geo, 0)) pkgs.add(ri.activityInfo.packageName);
        } catch (Exception ignored) {
        }
        List<App> out = new ArrayList<>();
        for (String p : pkgs) {
            if (p.equals(mgNavPackage) || p.startsWith("com.saicmotor") || p.startsWith("com.telenav")
                    || p.startsWith("com.nng") || p.startsWith("com.tomtom.navigation")) continue;
            Intent li = pm.getLaunchIntentForPackage(p);
            if (li == null) continue;
            String label = p;
            try {
                label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(p, 0)));
            } catch (Exception ignored) {
            }
            out.add(new App(label, null, li, 0, false, p));
        }
        return out;
    }

    public Intent navIntent(String hint) {
        List<String> candidates = new ArrayList<>();
        if (hint != null && !hint.isEmpty()) candidates.add(hint);
        Collections.addAll(candidates, NAV_PACKAGES);
        for (String p : candidates) {
            Intent i = pm.getLaunchIntentForPackage(p);
            if (i != null) return i;
        }
        return null;
    }

    private boolean exists(ComponentName cn) {
        try {
            ActivityInfo ai = pm.getActivityInfo(cn, 0);
            return ai != null && ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** App installate dall'utente (DiPlay, Task Manager, ...), in ordine alfabetico. */
    public List<App> userApps() {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
        List<App> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden(pkg)) continue;
            String key = pkg + "/" + ri.activityInfo.name;
            if (!seen.add(key)) continue;
            String label = String.valueOf(ri.loadLabel(pm));
            boolean mine = pkg.toLowerCase(Locale.ROOT).contains("diplay") || label.toLowerCase(Locale.ROOT).contains("diplay")
                    || label.toLowerCase(Locale.ROOT).contains("task manager") || pkg.toLowerCase(Locale.ROOT).contains("taskmanager");
            out.add(new App(label, new ComponentName(pkg, ri.activityInfo.name), null, 0, mine, pkg));
        }
        final Collator col = Collator.getInstance(Locale.getDefault());
        Collections.sort(out, (x, y) -> {
            return col.compare(x.label, y.label);
        });
        Diag.put("app.installate", out.size());
        return out;
    }

    private static boolean hidden(String pkg) {
        for (String p : HIDDEN_PREFIX) {
            if (pkg.startsWith(p)) return true;
        }
        return false;
    }

    public Drawable originalIcon(App a) {
        try {
            if (a.component != null) return pm.getActivityIcon(a.component);
            return pm.getApplicationIcon(a.packageName);
        } catch (Exception e) {
            return ctx.getDrawable(R.drawable.ic_app);
        }
    }

    public App findByPackageHint(String... needles) {
        for (App a : userApps()) {
            String p = a.packageName.toLowerCase(Locale.ROOT), l = a.label.toLowerCase(Locale.ROOT);
            for (String n : needles) {
                if (p.contains(n) || l.contains(n)) return a;
            }
        }
        return null;
    }

    public boolean launch(App a) {
        try {
            ctx.startActivity(a.intent());
            return true;
        } catch (Exception e) {
            Diag.error("avvio " + a.label, e.toString());
            return false;
        }
    }

    public boolean launchComponent(String pkg, String cls) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg, cls))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            Intent li = pm.getLaunchIntentForPackage(pkg);
            if (li == null) {
                Diag.error("avvio " + pkg, e.toString());
                return false;
            }
            try {
                ctx.startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (Exception e2) {
                Diag.error("avvio " + pkg, e2.toString());
                return false;
            }
        }
    }
}
