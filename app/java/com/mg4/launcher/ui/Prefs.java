package com.mg4.launcher.ui;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Impostazioni del launcher. */
public final class Prefs {
    public static final String[] CAR_COLORS = {
            "Arctic White", "Black Pearl", "Camden Grey", "Cosmic Silver",
            "Ealing Green", "Piccadilly Blue", "Dynamic Red", "Volcano Orange"};
    public static final int[] CAR_COLOR_VALUES = {
            0xFFE8E9E6, 0xFF202227, 0xFF5D6166, 0xFFA9ADB2,
            0xFF4C6A5A, 0xFF2E5597, 0xFFA5161E, 0xFFE1621F};
    public static final int[] ACCENTS = {0xFF3E8BFF, 0xFFFFFFFF, 0xFF2BD4A4, 0xFFF5A524};
    public static final String THEME_AUTO = "auto", THEME_NIGHT = "notte", THEME_DAY = "giorno";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
    }

    public int carColorIndex() {
        return Math.max(0, Math.min(CAR_COLORS.length - 1, sp.getInt("carColor", 5)));
    }

    public void setCarColorIndex(int i) {
        sp.edit().putInt("carColor", i).apply();
    }

    public boolean blackRoof() {
        return sp.getBoolean("blackRoof", true);
    }

    public void setBlackRoof(boolean b) {
        sp.edit().putBoolean("blackRoof", b).apply();
    }

    public int accent() {
        return sp.getInt("accent", ACCENTS[0]);
    }

    public void setAccent(int c) {
        sp.edit().putInt("accent", c).apply();
    }

    public String themeMode() {
        return sp.getString("theme", THEME_AUTO);
    }

    public void setThemeMode(String m) {
        sp.edit().putString("theme", m).apply();
    }

    // --- lingua: "auto" = come il sistema dell'auto, altrimenti codice (it, en, es, de, pt, fr, tr) ---

    public String language() {
        return sp.getString("lingua", Lang.AUTO);
    }

    public void setLanguage(String code) {
        sp.edit().putString("lingua", code).apply();
    }

    // --- avviso di nuova versione: "Più tardi" lo rimanda per quella versione ---

    public void snoozeUpdate(String tag, long until) {
        sp.edit().putString("upd_snooze_tag", tag).putLong("upd_snooze_until", until).apply();
    }

    public String snoozedUpdateTag() {
        return sp.getString("upd_snooze_tag", "");
    }

    public long snoozedUpdateUntil() {
        return sp.getLong("upd_snooze_until", 0);
    }

    // --- app installate nascoste dalla griglia ---

    public Set<String> hiddenApps() {
        return new HashSet<>(sp.getStringSet("hiddenApps", new HashSet<>()));
    }

    public void setAppHidden(String key, boolean hide) {
        Set<String> s = hiddenApps();
        if (hide) s.add(key);
        else s.remove(key);
        sp.edit().putStringSet("hiddenApps", s).apply();
    }

    // --- dimensione dei comandi toccabili: 0 Normale, 1 Grande, 2 Molto grande ---
    public static final float[] UI_SCALES = {1f, 1.15f, 1.3f};

    public int uiScale() {
        return Math.max(0, Math.min(UI_SCALES.length - 1, sp.getInt("dimensioneComandi", 0)));
    }

    public void setUiScale(int i) {
        sp.edit().putInt("dimensioneComandi", i).apply();
    }

    public float uiScaleFactor() {
        return UI_SCALES[uiScale()];
    }

    // --- posizione di guida: "auto" (dalla configurazione dell'auto), "left", "right" ---
    public static final String DRIVE_AUTO = "auto", DRIVE_LEFT = "left", DRIVE_RIGHT = "right";

    public String driveSide() {
        return sp.getString("posizioneGuida", DRIVE_AUTO);
    }

    public void setDriveSide(String v) {
        sp.edit().putString("posizioneGuida", v).apply();
    }

    // --- riquadro auto ---
    /** Percentuale della batteria con un decimale (es. 88,1%). */
    public boolean socDecimals() {
        return sp.getBoolean("socDecimali", false);
    }

    public void setSocDecimals(boolean on) {
        sp.edit().putBoolean("socDecimali", on).apply();
    }

    /** Temperatura interna sotto quella esterna (molte auto non la forniscono: spenta di default). */
    public boolean showInsideTemp() {
        return sp.getBoolean("mostraTempInterna", false);
    }

    public void setShowInsideTemp(boolean on) {
        sp.edit().putBoolean("mostraTempInterna", on).apply();
    }

    /** Pressione degli pneumatici (TPMS) sotto le ruote dell'auto disegnata. */
    public boolean showTpms() {
        return sp.getBoolean("mostraTpms", false);
    }

    public void setShowTpms(boolean on) {
        sp.edit().putBoolean("mostraTpms", on).apply();
    }

    // --- aggiornamenti da GitHub ---
    public static final String DEFAULT_REPO = "ernestomicco2026-cell/Mg4-Launcher-SWI69";

    /** Vecchio nome del repository (prima che la sigla del software diventasse SWI69). */
    private static final String OLD_REPO_NAME = "mg4-launcher-sw169";

    public String updateRepo() {
        String saved = sp.getString("ghRepo", DEFAULT_REPO);
        String fixed = normalizeRepo(saved);
        // salvato con il vecchio nome (Mg4-Launcher-SW169) → si passa da soli al nuovo
        if (!fixed.equals(saved)) sp.edit().putString("ghRepo", fixed).apply();
        return fixed;
    }

    public void setUpdateRepo(String r) {
        sp.edit().putString("ghRepo", normalizeRepo(r)).apply();
    }

    /** Vuoto o vecchio nome del repository → repository attuale (Mg4-Launcher-SWI69). */
    static String normalizeRepo(String r) {
        if (r == null || r.trim().isEmpty()) return DEFAULT_REPO;
        String t = r.trim();
        int slash = t.lastIndexOf('/');
        String name = slash >= 0 ? t.substring(slash + 1) : t;
        if (name.toLowerCase(java.util.Locale.ROOT).equals(OLD_REPO_NAME)) {
            return (slash >= 0 ? t.substring(0, slash + 1) : "ernestomicco2026-cell/") + "Mg4-Launcher-SWI69";
        }
        return t;
    }

    /** Token GitHub di sola lettura: serve perché il repository è privato. */
    public String githubToken() {
        return sp.getString("ghToken", "");
    }

    public void setGithubToken(String tok) {
        sp.edit().putString("ghToken", tok == null ? "" : tok.trim()).apply();
    }

    // --- navigatore predefinito: "auto" (telefono se collegato, altrimenti MG), "mg", "phone" o nome pacchetto ---
    public static final String NAV_AUTO = "auto", NAV_MG = "mg", NAV_PHONE = "phone";

    public String navApp() {
        return sp.getString("navApp", NAV_AUTO);
    }

    public void setNavApp(String v) {
        sp.edit().putString("navApp", v).apply();
    }
}
