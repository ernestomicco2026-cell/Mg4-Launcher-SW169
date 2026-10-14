package com.mg4.launcher.svc;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

/**
 * Configurazione dell'auto (stessa fonte delle app originali): valori come "0" / "1" che dicono quali funzioni
 * ha quella specifica auto (dipendono da modello e mercato).
 */
public final class CarFeatures {
    private CarFeatures() {}

    private static final String URI = "content://com.saicmotor.caradapter.featureprovider/feature/key/";
    private static final Map<String, String> cache = new HashMap<>();

    /** Valore della configurazione, o null se non leggibile. */
    public static synchronized String get(Context c, String key) {
        if (cache.containsKey(key)) return cache.get(key);
        String v = null;
        try (Cursor q = c.getContentResolver().query(Uri.parse(URI + key), null, null, null, null)) {
            if (q != null && q.moveToNext()) v = q.getString(0);
        } catch (Throwable e) {
            Diag.error("auto.config." + key, e.toString());
        }
        cache.put(key, v);
        return v;
    }

    /** Spia del retronebbia fissa nella barra di sistema, in alto a sinistra (sopra a qualsiasi app). */
    public static boolean hasSystemFogIndicator(Context c) {
        String[] keys = {"systemui_state_rearfog__", "systemui_state_rearfognew__",
                "systemui_state_frontfog__", "systemui_state_frontfognew__",
                "systemui_hiboard_RearFog__", "systemui_hiboard_Frontfog__"};
        StringBuilder sb = new StringBuilder();
        for (String k : keys) sb.append(k.replace("systemui_", "").replace("__", "")).append('=').append(get(c, k)).append(' ');
        Diag.put("auto.config.spiaLuci", sb.toString().trim());
        return "1".equals(get(c, "systemui_state_rearfog__")) || "1".equals(get(c, "systemui_state_rearfognew__"));
    }
}
