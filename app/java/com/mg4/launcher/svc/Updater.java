package com.mg4.launcher.svc;

import com.mg4.launcher.R;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Aggiornamenti dal repository GitHub: legge l'ultima release, scarica l'APK allegato e lo installa
 * con PackageInstaller (il launcher è firmato con la chiave platform, quindi non serve conferma).
 */
public final class Updater {
    /** Repository dedicato alla SWI69: ramo main, cartella releases/, Release con tag "vX.Y.Z" (o "swi69-vX.Y.Z"). */
    static final String BRANCH = "main";
    static final String CHANNEL_DIR = "releases/";

    private static final String ACTION_RESULT = "com.mg4.launcher.INSTALL_RESULT";

    public static final class Release {
        public String tag = "";
        public String name = "";
        public String notes = "";
        public String apkUrl;       // URL API dell'allegato
        public String apkName = "";
        public long apkSize;
        public boolean newer;
        boolean raw;                // file del repository (API contents) invece di allegato di release
        String repo = "", path = "";  // per il download di riserva da raw.githubusercontent.com
    }

    public interface Listener {
        /** Messaggio di stato da mostrare (sul thread principale). */
        void onUpdateStatus(String text, Release r, boolean busy);
    }

    private final Context ctx;
    private final HandlerThread thread = new HandlerThread("mg4-update");
    private final Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private volatile boolean busy;

    public Updater(Context c) {
        ctx = c.getApplicationContext();
        thread.start();
        bg = new Handler(thread.getLooper());
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isBusy() {
        return busy;
    }

    /** Testo tradotto (lingua del launcher). */
    private String tr(int id, Object... args) {
        return args.length == 0 ? ctx.getString(id) : ctx.getString(id, args);
    }

    public String currentVersion() {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void status(String s, Release r, boolean b) {
        busy = b;
        Diag.put("aggiornamenti", s);
        main.post(() -> {
            if (listener != null) listener.onUpdateStatus(s, r, b);
        });
    }

    /** Controlla l'ultima release del repository. */
    public void check(String repo, String token) {
        if (busy) return;
        status(tr(R.string.upd_checking), null, true);
        bg.post(() -> {
            try {
                Release r = latest(repo, token);
                if (r.apkUrl == null) {
                    status(tr(R.string.upd_no_apk, r.tag), r, false);
                } else if (r.newer) {
                    status(tr(R.string.upd_new, r.tag), r, false);
                } else {
                    status(tr(R.string.upd_uptodate, currentVersion(), r.tag), r, false);
                }
            } catch (HttpError e) {
                status(e.getMessage(), null, false);
            } catch (Exception e) {
                status(tr(R.string.upd_no_conn, e.getClass().getSimpleName()), null, false);
                Diag.error("aggiornamenti", e.toString());
            }
        });
    }

    /**
     * Scarica l'APK della release e lo installa. L'APK resta in memoria e va direttamente nella sessione
     * di installazione: nessun file temporaneo da salvare (era il punto che non riusciva sull'auto).
     */
    /** Risultato del controllo silenzioso (avviso di nuova versione nella home). */
    public interface QuietResult {
        void onRelease(Release r);
    }

    /**
     * Controllo in background, senza messaggi di stato: usato per l'avviso discreto di nuova versione.
     * Usa raw.githubusercontent.com (nessun limite di richieste); non fa nulla se è già in corso un'operazione.
     */
    public void checkQuiet(String repo, QuietResult cb) {
        if (busy) return;
        bg.post(() -> {
            try {
                Release r = latest(repo, "");
                Diag.put("aggiornamenti.controllo", "ultima " + r.tag + (r.newer ? " (nuova)" : ""));
                main.post(() -> cb.onRelease(r));
            } catch (Exception e) {
                Diag.put("aggiornamenti.controllo", "non riuscito: " + describe(e));
            }
        });
    }

    public void install(Release r, String token) {
        if (busy || r == null || r.apkUrl == null) return;
        status(tr(R.string.upd_downloading, r.apkName), r, true);
        bg.post(() -> {
            byte[] apk;
            try {
                apk = downloadWithFallback(r, token);
            } catch (HttpError e) {
                status(e.getMessage(), r, false);
                Diag.error("aggiornamenti.download", e.getMessage());
                return;
            } catch (Exception e) {
                status(tr(R.string.upd_dl_failed, describe(e)), r, false);
                Diag.error("aggiornamenti.download", Diag.trace(e));
                return;
            }
            if (apk.length < 1024 || apk[0] != 'P' || apk[1] != 'K') {
                status(tr(R.string.upd_invalid, apk.length), r, false);
                Diag.error("aggiornamenti.download", "file non valido, " + apk.length + " byte");
                return;
            }
            String version = checkPackage(apk);
            if (version == null) {
                status(tr(R.string.upd_wrong_pkg), r, false);
                return;
            }
            status(tr(R.string.upd_installing, version), r, true);
            try {
                installApk(apk);
            } catch (Exception e) {
                status(tr(R.string.upd_install_failed, describe(e)), r, false);
                Diag.error("aggiornamenti.installazione", Diag.trace(e));
            }
        });
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        return e.getClass().getSimpleName() + (m == null || m.isEmpty() ? "" : " – " + m);
    }

    /** Prima l'API di GitHub, poi (per i file del repository) raw.githubusercontent.com. */
    /** File del repository: prima raw.githubusercontent.com (senza limiti), poi l'API di GitHub. */
    private byte[] downloadWithFallback(Release r, String token) throws Exception {
        if (!r.raw || r.repo.isEmpty()) {
            return download(r.apkUrl, r, token, "application/octet-stream");
        }
        try {
            return download("https://raw.githubusercontent.com/" + r.repo + "/" + BRANCH + "/" + r.path, r, "", "application/octet-stream");
        } catch (Exception first) {
            Diag.error("aggiornamenti.download", "raw.githubusercontent.com: " + describe(first) + " → provo l'API di GitHub");
            return download(r.apkUrl, r, token, "application/vnd.github.raw");
        }
    }

    /**
     * Controlla nome del pacchetto e versione dell'APK scaricato. Serve un file per leggerlo: se non si
     * riesce a scriverlo il controllo si salta (la sessione accetta comunque solo questo pacchetto).
     */
    private String checkPackage(byte[] apk) {
        File[] dirs = {ctx.getCacheDir(), ctx.getFilesDir(), ctx.getExternalCacheDir()};
        for (File dir : dirs) {
            if (dir == null) continue;
            File f = new File(dir, "update-check.apk");
            try {
                if (!dir.exists()) dir.mkdirs();
                try (OutputStream os = new FileOutputStream(f)) {
                    os.write(apk);
                }
                PackageInfo pi = ctx.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
                f.delete();
                if (pi == null) return null;
                if (!ctx.getPackageName().equals(pi.packageName)) return null;
                return pi.versionName;
            } catch (Exception e) {
                Diag.error("aggiornamenti.controllo", dir + ": " + describe(e));
                f.delete();
            }
        }
        return tr(R.string.upd_new_version);
    }

    // ------------------------------------------------------------------------------------------

    private static final class HttpError extends IOException {
        HttpError(String m) {
            super(m);
        }
    }

    /**
     * Prima la cartella releases/ del repository (latest.json + APK, caricati con un normale push),
     * poi le Release di GitHub.
     */
    private Release latest(String repo, String token) throws Exception {
        // 1) raw.githubusercontent.com: nessun limite di richieste (le API di GitHub senza token ne hanno 60 all'ora)
        try {
            Release raw = latestFromRaw(repo);
            if (raw != null) return raw;
        } catch (Exception e) {
            Diag.error("aggiornamenti.raw", describe(e));
        }
        // 2) API di GitHub: cartella releases/ e Release con APK allegato
        Release fromRepo = null;
        HttpError err = null;
        try {
            fromRepo = latestFromRepo(repo, token);
        } catch (HttpError e) {
            err = e;
        }
        Release fromReleases = null;
        try {
            fromReleases = latestRelease(repo, token);
        } catch (HttpError e) {
            if (err == null || fromRepo != null) err = e;
        }
        if (fromRepo != null && (fromReleases == null || fromReleases.apkUrl == null
                || compare(fromRepo.tag, fromReleases.tag) >= 0)) return fromRepo;
        if (fromReleases != null) return fromReleases;
        throw err != null ? err : new HttpError(tr(R.string.upd_not_found_repo, repo));
    }

    /** latest.json letto da raw.githubusercontent.com (CDN di GitHub, senza limite di richieste). */
    private Release latestFromRaw(String repo) throws Exception {
        String base = "https://raw.githubusercontent.com/" + repo + "/" + BRANCH + "/";
        HttpURLConnection c = open(base + CHANNEL_DIR + "latest.json?t=" + System.currentTimeMillis(), "", "application/json");
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            Diag.put("aggiornamenti.raw", "HTTP " + code);
            return null;
        }
        JSONObject o = new JSONObject(readAll(c.getInputStream()));
        c.disconnect();
        Release r = new Release();
        r.tag = o.optString("version", "");
        r.name = "MG4 Launcher " + r.tag;
        r.notes = o.optString("notes", "");
        String apk = o.optString("apk", "");
        if (!apk.isEmpty()) {
            r.apkUrl = "https://api.github.com/repos/" + repo + "/contents/" + apk + "?ref=" + BRANCH;
            r.apkName = apk.substring(apk.lastIndexOf('/') + 1);
            r.apkSize = o.optLong("size");
            r.raw = true;
            r.repo = repo;
            r.path = apk;
        }
        r.newer = compare(r.tag, currentVersion()) > 0;
        Diag.put("aggiornamenti.raw", "ok, ultima " + r.tag);
        return r;
    }

    private Release latestFromRepo(String repo, String token) throws Exception {
        String base = "https://api.github.com/repos/" + repo + "/contents/";
        HttpURLConnection c = open(base + CHANNEL_DIR + "latest.json?ref=" + BRANCH, token, "application/vnd.github.raw");
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw httpError(code, token, repo);
        }
        JSONObject o = new JSONObject(readAll(c.getInputStream()));
        c.disconnect();
        Release r = new Release();
        r.tag = o.optString("version", "");
        r.name = "MG4 Launcher " + r.tag;
        r.notes = o.optString("notes", "");
        String apk = o.optString("apk", "");
        if (!apk.isEmpty()) {
            r.apkUrl = base + apk + "?ref=" + BRANCH;
            r.apkName = apk.substring(apk.lastIndexOf('/') + 1);
            r.apkSize = o.optLong("size");
            r.raw = true;
            r.repo = repo;
            r.path = apk;
        }
        r.newer = compare(r.tag, currentVersion()) > 0;
        return r;
    }

    private HttpError httpError(int code, String token, String repo) {
        if (code == 404) return new HttpError(token.isEmpty() ? tr(R.string.upd_not_found) : tr(R.string.upd_not_found_repo, repo));
        if (code == 401) return new HttpError(tr(R.string.upd_token));
        if (code == 403) return new HttpError(tr(R.string.upd_refused));
        return new HttpError(tr(R.string.upd_unexpected, code));
    }

    /** Release di GitHub più recente (per versione) che ha un APK allegato. */
    private Release latestRelease(String repo, String token) throws Exception {
        HttpURLConnection c = open("https://api.github.com/repos/" + repo + "/releases?per_page=30", token, "application/vnd.github+json");
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw httpError(code, token, repo);
        }
        JSONArray list = new JSONArray(readAll(c.getInputStream()));
        c.disconnect();
        JSONObject o = null;
        for (int i = 0; i < list.length(); i++) {
            JSONObject x = list.getJSONObject(i);
            if (!x.optBoolean("draft") && hasApk(x)) {
                if (o == null || compare(x.optString("tag_name"), o.optString("tag_name")) > 0) o = x;
            }
        }
        if (o == null) throw new HttpError(tr(R.string.upd_no_release_apk, repo));

        Release r = new Release();
        r.tag = o.optString("tag_name", "");
        r.name = o.optString("name", r.tag);
        r.notes = o.optString("body", "");
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").toLowerCase().endsWith(".apk")) {
                    r.apkUrl = a.optString("url");
                    r.apkName = a.optString("name");
                    r.apkSize = a.optLong("size");
                    break;
                }
            }
        }
        r.newer = compare(r.tag, currentVersion()) > 0;
        return r;
    }

    private byte[] download(String url, Release r, String token, String accept) throws Exception {
        // l'allegato risponde con un redirect verso lo storage di GitHub: lo si segue senza il token
        HttpURLConnection c = open(url, token, accept);
        c.setInstanceFollowRedirects(false);
        int code = c.getResponseCode();
        for (int hops = 0; code / 100 == 3 && hops < 5; hops++) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            c = open(loc, "", "application/octet-stream");
            c.setInstanceFollowRedirects(false);
            code = c.getResponseCode();
        }
        if (code != 200) {
            c.disconnect();
            throw new HttpError(tr(R.string.upd_dl_http, code));
        }
        long total = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : r.apkSize;
        ByteArrayOutputStream bo = new ByteArrayOutputStream(total > 0 ? (int) total : 512 * 1024);
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[64 * 1024];
            long done = 0, lastShown = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                done += n;
                if (total > 0 && done - lastShown > total / 20) {
                    lastShown = done;
                    status(tr(R.string.upd_progress, (int) Math.min(100, done * 100 / total)), r, true);
                }
            }
        } finally {
            c.disconnect();
        }
        Diag.put("aggiornamenti.scaricati", bo.size() + " byte");
        return bo.toByteArray();
    }

    private void installApk(byte[] apk) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(ctx.getPackageName());
        p.setSize(apk.length);
        // memoria interna esplicita: il firmware MG blocca le app non di sistema sull'archivio interno "automatico"
        p.setInstallLocation(PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY);
        // come "adb install -r -f": INSTALL_REPLACE_EXISTING (0x2) + INSTALL_INTERNAL (0x10)
        try {
            java.lang.reflect.Field f = PackageInstaller.SessionParams.class.getField("installFlags");
            f.setInt(p, f.getInt(p) | 0x2 | 0x10);
        } catch (Throwable t) {
            Diag.error("aggiornamenti.flag", t.toString());
        }
        int id = pi.createSession(p);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream os = s.openWrite("base.apk", 0, apk.length)) {
                os.write(apk);
                s.fsync(os);
            }
            try {
                ctx.registerReceiver(result, new IntentFilter(ACTION_RESULT));
            } catch (Exception ignored) {
            }
            PendingIntent cb = PendingIntent.getBroadcast(ctx, id,
                    new Intent(ACTION_RESULT).setPackage(ctx.getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(cb.getIntentSender());
        } catch (Exception e) {
            try {
                pi.abandonSession(id);
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private final BroadcastReceiver result = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                // senza il permesso di sistema Android chiede conferma: si mostra la schermata standard
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                status(tr(R.string.upd_confirm), null, true);
                return;
            }
            try {
                ctx.unregisterReceiver(this);
            } catch (Exception ignored) {
            }
            if (st == PackageInstaller.STATUS_SUCCESS) {
                status(tr(R.string.upd_installed), null, false);
            } else {
                String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Diag.error("aggiornamenti.installazione", "stato " + st + ": " + msg);
                status(tr(R.string.upd_install_failed, msg == null ? tr(R.string.upd_err_code, st) : msg), null, false);
            }
        }
    };

    private static HttpURLConnection open(String url, String token, String accept) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("User-Agent", "MG4Launcher");
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "token " + token);
        return c;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /** Confronta versioni tipo "v0.3.0" e "0.2.0" numero per numero. */
    private static boolean hasApk(JSONObject release) {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return false;
        for (int i = 0; i < assets.length(); i++) {
            if (assets.optJSONObject(i) != null && assets.optJSONObject(i).optString("name").toLowerCase().endsWith(".apk")) return true;
        }
        return false;
    }

    static int compare(String a, String b) {
        int[] x = nums(a), y = nums(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static int[] nums(String v) {
        // "swi69-v0.1.0" (o il vecchio "sw169-…") → 0.1.0: il nome del software non fa parte della versione
        String[] parts = v.replaceAll("(?i)sw\\s*-?[i1]69", "").replaceAll("[^0-9.]", "").replaceAll("^\\.+", "").split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** true se il launcher ha il permesso di installare senza conferma (firmato con la chiave platform). */
    public boolean canInstallSilently() {
        return ctx.checkSelfPermission("android.permission.INSTALL_PACKAGES") == PackageManager.PERMISSION_GRANTED;
    }
}
