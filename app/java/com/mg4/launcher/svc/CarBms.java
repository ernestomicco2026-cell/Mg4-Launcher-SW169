package com.mg4.launcher.svc;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.lang.reflect.Method;

/**
 * Proprietà batteria SAIC lette dal CarService di Android (manager "bms", come l'app Energia SW169).
 * La libreria android.car è nel classpath di sistema: la si usa per riflessione, senza includerla nell'APK.
 * ID presi da android.car.hardware.CarBMSManager del firmware SWI69.
 */
public final class CarBms {
    public static final int CHRG_TRGT_SOC = 557904908;      // int, codice 1..7 (40%..100%)
    public static final int PACK_SOC_DSP = 560002052;       // float, % mostrata
    public static final int PACK_VOL = 560002054;           // float, V
    public static final int PACK_CRNT = 560002055;          // float, A
    public static final int CRNT_AVG_ELEC_CSUMP = 560002075;// float, kWh/100 km (media corrente)
    public static final int ELEC_CSUMP_PERKM = 560002077;   // float, consumo dell'ultimo km
    public static final int VEH_ACTU_ELEC_CSUMP = 560002127;// float (solo diagnostica, se presente)
    public static final int BAT_ELEC_ENRG_AVG_RATE = 560002081; // float (solo diagnostica)
    public static final int ESTD_ELEC_RNG = 557904918;      // int, km
    public static final int CHRG_STS = 557904905;           // int, stato ricarica BMS

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Object car;
    private volatile Object bms;
    private volatile Method getProperty;
    private boolean started;

    public CarBms(Context c) {
        ctx = c.getApplicationContext();
    }

    public void start() {
        if (started) return;
        started = true;
        main.post(this::connect);
    }

    public boolean isReady() {
        return bms != null && getProperty != null;
    }

    private void connect() {
        try {
            Class<?> carCls = Class.forName("android.car.Car");
            Method create = carCls.getMethod("createCar", Context.class, ServiceConnection.class);
            car = create.invoke(null, ctx, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    try {
                        bms = car.getClass().getMethod("getCarManager", String.class).invoke(car, "bms");
                        if (bms != null) {
                            getProperty = bms.getClass().getMethod("getProperty", Class.class, int.class, int.class);
                        }
                        Diag.put("bms.stato", bms != null ? "collegato" : "manager bms assente");
                    } catch (Throwable e) {
                        Diag.error("bms.manager", e.toString());
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    bms = null;
                    Diag.put("bms.stato", "scollegato");
                }
            });
            carCls.getMethod("connect").invoke(car);
            Diag.put("bms.stato", "connessione…");
        } catch (Throwable e) {
            Diag.error("bms.car", e.toString());
            main.postDelayed(this::connect, 15000);
        }
    }

    /** Valore float della proprietà, o NaN se non leggibile. */
    public float getFloat(int id) {
        Object v = get(Float.class, id);
        return v instanceof Float ? (Float) v : Float.NaN;
    }

    /** Valore int della proprietà, o Integer.MIN_VALUE se non leggibile. */
    public int getInt(int id) {
        Object v = get(Integer.class, id);
        return v instanceof Integer ? (Integer) v : Integer.MIN_VALUE;
    }

    private Object get(Class<?> type, int id) {
        Object m = bms;
        Method g = getProperty;
        if (m == null || g == null) return null;
        try {
            return g.invoke(m, type, id, 0);
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            Diag.error("bms.0x" + Integer.toHexString(id), c.toString());
            return null;
        }
    }
}
