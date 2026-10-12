package com.mg4.launcher.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * Lingua del launcher: "auto" segue la lingua di sistema dell'auto (e si aggiorna da sola quando cambia),
 * oppure una delle lingue tradotte scelta nelle impostazioni.
 */
public final class Lang {
    private Lang() {}

    public static final String AUTO = "auto";
    /** Codici delle lingue tradotte (res/values-xx), nell'ordine delle impostazioni. */
    public static final String[] CODES = {"it", "en", "es", "de", "pt", "fr", "tr"};
    /** Nome di ogni lingua scritto nella lingua stessa. */
    public static final String[] NAMES = {"Italiano", "English", "Español", "Deutsch", "Português", "Français", "Türkçe"};

    /** Lingua attuale del sistema (non quella forzata nel launcher). */
    public static Locale system() {
        return Resources.getSystem().getConfiguration().getLocales().get(0);
    }

    /** Nome della lingua di sistema, se tradotta; altrimenti English (la lingua di riserva). */
    public static String systemName() {
        String l = system().getLanguage();
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(l)) return NAMES[i];
        return "English";
    }

    public static Locale resolve(String code) {
        return code == null || AUTO.equals(code) ? system() : new Locale(code);
    }

    /**
     * Applica la lingua alle risorse dell'activity e dell'applicazione (usate anche dai servizi del launcher).
     * @return la lingua applicata
     */
    @SuppressWarnings("deprecation")
    public static Locale apply(Context c, String code) {
        Locale loc = resolve(code);
        Locale.setDefault(loc);
        Resources[] all = {c.getResources(), c.getApplicationContext().getResources()};
        for (Resources r : all) {
            Configuration cfg = new Configuration(r.getConfiguration());
            cfg.setLocale(loc);
            r.updateConfiguration(cfg, r.getDisplayMetrics());
        }
        return loc;
    }
}
