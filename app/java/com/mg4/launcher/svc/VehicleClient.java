package com.mg4.launcher.svc;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Veicolo, versione SW169 (EH32 Color Radio).
 *
 * Il servizio com.saicmotor.caradapter è un "hub" (ICarAdapterService.queryClient(codice)) che restituisce i
 * binder dei sotto-servizi, come fa la home originale SW169 (libreria carapi). Da qui: batteria, autonomia,
 * stato di ricarica, V2L e climatizzatore. Limite di ricarica, corrente/tensione e consumo arrivano invece
 * dalle proprietà "bms" del CarService di Android (CarBms), come nell'app Energia.
 */
public final class VehicleClient {
    private static final String PKG = "com.saicmotor.caradapter";
    private static final String ACTION = "com.saicmotor.caradapter.CarAdapterService";

    private static final String HUB = "com.saicmotor.carapi.ICarAdapterService";
    private static final int HUB_QUERY_CLIENT = 1;
    private static final int BIND_EV = 4, BIND_HVAC = 7, BIND_CONFIG = 2, BIND_LAMP = 13;

    private static final String EV = "com.saicmotor.carapi.ev.ICarEvService";
    private static final int EV_PERCENT = 3;          // int  getChargePercentChange
    private static final int EV_STATUS = 4;           // int  getChargeStatus (stessi codici della SW168)
    private static final int EV_REMAIN_TIME = 6;      // int  getChargingRemainTime (minuti)
    private static final int EV_DISCHG_TIME = 7;      // int  getDisChargingRemainTime
    private static final int EV_UNITS = 11;           // int  getDistanceUnits (0 = km)
    private static final int EV_RANGE = 12;           // int  getCurrentRange
    private static final int EV_CHG_CTRL = 20;        // int  getChargeCtrlStatus
    private static final int EV_DISCHG_CTRL = 24;     // int  getDisChargeCtrl

    private static final String AC = "com.saicmotor.carapi.hvac.ICarHvacService";
    private static final int AC_SWITCH_POWER = 5;     // void switchHvacPowerStatus()
    private static final int AC_GET_POWER = 6;        // bool getHvacPowerStatus
    private static final int AC_SWITCH_AUTO = 9;      // void switchAutoStatus()
    private static final int AC_GET_AUTO = 10;        // bool getAutoStatus
    private static final int AC_SET_FAN = 19;         // void setFanSpeed(int)
    private static final int AC_GET_FAN = 20;         // int  getFanSpeed
    private static final int AC_SET_DRV_TEMP = 21;    // void setDriverTemperature(float)
    private static final int AC_GET_DRV_TEMP = 22;    // float getDriverTemperature
    private static final int AC_GET_OUT_TEMP = 25;    // float getOutSideTemperature
    private static final int AC_SWITCH_AC = 7, AC_GET_AC = 8;                 // switchACStatus / getACStatus
    private static final int AC_SWITCH_DEFROST = 27, AC_GET_DEFROST = 28;     // parabrezza

    // fendinebbia, come la schermata dell'auto originale: set(1) = "premuto" (commuta), get 1 = acceso
    private static final String LAMP = "com.saicmotor.carapi.lamp.ICarLampService";
    private static final int LAMP_SET_REAR_FOG = 30, LAMP_GET_REAR_FOG = 31, LAMP_SET_FRONT_FOG = 40, LAMP_GET_FRONT_FOG = 41;
    private static final String CONFIG = "com.saicmotor.carapi.config.ICarConfigService";
    private static final int CONFIG_FRONT_FOG = 60;   // getFrontFrogConfigData: 0 = fendinebbia anteriori presenti

    /** Ventola come la barra del launcher: 0 = clima spento, 1..11, 15 = automatico. */
    public static final int FAN_OFF = 0, FAN_AUTO = 15;
    private static final int FAN_MIN = 1, FAN_MAX = 11;
    /** Per quanto tempo il valore impostato dall'utente prevale sulle letture (che arrivano in ritardo). */
    private static final long HOLD_MS = 4000;

    /**
     * Temperatura nella barra: MG4 EU/UK su SW169 va da 16 a 30 °C, con LO = 15,5 e HI = 30,5
     * (CarModel.isTempRange16to30CarModel della home originale). Nel launcher: 15 = "LO", 31 = "HI".
     */
    public static final int TEMP_MIN = 15;   // "LO"
    public static final int TEMP_MAX = 31;   // "HI"
    private static final float CAR_LO = 15.5f, CAR_HI = 30.5f;

    /** Istantanea dello stato del veicolo letta dai servizi. */
    public static final class State {
        public boolean connected;
        public float soc = -1;            // %
        public int rangeKm = -1;
        public int chargingStatus = -1;   // 0 scollegata, 1 AC, 2 completata, 10/11 DC, 13 V2L…
        public int predictMinutes = -1;
        public float chargeKw = 0;
        public int chargeLimit = -1;      // % (convertito dal codice SAIC)
        public float consumption = -1;    // kWh/100km
        public int dischargeStatus = -1;
        public int acTemp = -1;           // 15 = LO, 16..30, 31 = HI
        public int hvacPower = -1;
        public int acOn = -1, autoOn = -1, defrostOn = -1;   // 1 acceso, 0 spento, -1 sconosciuto
        public int rearFog = -1, frontFog = -1;           // 1 acceso, 0 spento, -1 non disponibile
        public boolean hasFrontFog;
        public int fanLevel = -1;         // 0 spento, 1..max, 15 auto
        public int fanMin = FAN_MIN, fanMax = FAN_MAX;
        public float outsideTemp = -10000f;   // °C
    }

    public interface Listener {
        void onVehicleState(State s);
    }

    private final ServiceLink hub;
    private final CarBms bms;
    private final Map<Integer, IBinder> subs = new HashMap<>();
    private final HandlerThread thread = new HandlerThread("mg4-vehicle");
    private Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private volatile State last = new State();
    private volatile int pendingTemp = -1, pendingFan = -1;
    private volatile int heldTemp = -1, heldFan = -1;
    private volatile long tempHoldUntil, fanHoldUntil;
    private volatile boolean lastPower, lastAuto;
    private boolean running;

    public VehicleClient(Context ctx) {
        hub = new ServiceLink(ctx, "veicolo", ServiceLink.byAction(PKG, ACTION));
        bms = new CarBms(ctx);
        hub.setOnConnected(() -> {
            synchronized (subs) {
                subs.clear();
            }
            if (bg != null) bg.post(this::poll);
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public State last() {
        return last;
    }

    public void start() {
        if (running) return;
        running = true;
        if (bg == null) {
            thread.start();
            bg = new Handler(thread.getLooper());
        }
        hub.start();
        bms.start();
        bg.post(loop);
    }

    public void stop() {
        pause();
        hub.stop();
    }

    /** Sospende le letture (launcher non visibile) senza scollegarsi dai servizi. */
    public void pause() {
        running = false;
        if (bg != null) bg.removeCallbacks(loop);
    }

    public void resume() {
        if (bg == null || running) return;
        running = true;
        bg.post(loop);
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            poll();
            bg.postDelayed(this, 2000);
        }
    };

    private IBinder sub(int code) throws RemoteException {
        synchronized (subs) {
            IBinder b = subs.get(code);
            if (b != null && b.isBinderAlive()) return b;
        }
        IBinder h = hub.binder();
        if (h == null) return null;
        IBinder b = Rpc.callBinder(h, HUB, HUB_QUERY_CLIENT, p -> p.writeInt(code));
        if (b != null) {
            synchronized (subs) {
                subs.put(code, b);
            }
        }
        return b;
    }

    private void forget(int code) {
        synchronized (subs) {
            subs.remove(code);
        }
    }

    /**
     * Limite di ricarica: il CarService restituisce un codice, non una percentuale
     * (1 = 40% … 5 = 80% … 6 = 90%, altrimenti 100%, come l'app Energia SW169).
     */
    static int limitPercent(int code) {
        if (code >= 1 && code <= 7) return (code + 3) * 10;
        return -1;
    }

    /** Temperatura dell'auto (°C, a mezzi gradi) → valore della barra (15 = LO … 31 = HI). */
    static int tempFromCar(float f) {
        if (Float.isNaN(f) || f <= 0) return -1;
        if (f <= CAR_LO + 0.01f) return TEMP_MIN;
        if (f >= CAR_HI - 0.01f) return TEMP_MAX;
        return Math.max(TEMP_MIN + 1, Math.min(TEMP_MAX - 1, Math.round(f)));
    }

    /** Valore della barra → temperatura da inviare all'auto. */
    static float tempToCar(int v) {
        if (v <= TEMP_MIN) return CAR_LO;
        if (v >= TEMP_MAX) return CAR_HI;
        return v;
    }

    private static boolean ok(float v) {
        return !Float.isNaN(v);
    }

    private void poll() {
        State s = new State();
        s.connected = hub.isConnected();
        if (s.connected) {
            try {
                IBinder e = sub(BIND_EV);
                if (e != null) {
                    int percent = Rpc.callInt(e, EV, EV_PERCENT);
                    s.soc = percent;
                    s.rangeKm = Rpc.callInt(e, EV, EV_RANGE);
                    s.chargingStatus = Rpc.callInt(e, EV, EV_STATUS);
                    s.predictMinutes = Rpc.callInt(e, EV, EV_REMAIN_TIME);
                    s.dischargeStatus = Rpc.callInt(e, EV, EV_DISCHG_CTRL);
                    Diag.put("veicolo.soc(ev)", percent);
                    Diag.put("veicolo.autonomia", s.rangeKm + " (unità " + Rpc.callInt(e, EV, EV_UNITS) + ")");
                    Diag.put("veicolo.statoRicarica", s.chargingStatus);
                    Diag.put("veicolo.minutiRicarica", s.predictMinutes);
                    Diag.put("veicolo.minutiScarica", Rpc.callInt(e, EV, EV_DISCHG_TIME));
                    Diag.put("veicolo.comandoRicarica", Rpc.callInt(e, EV, EV_CHG_CTRL));
                    Diag.put("veicolo.statoScarica(V2L)", s.dischargeStatus);
                }
            } catch (Exception ex) {
                Diag.error("ricarica", ex.toString());
                forget(BIND_EV);
            }
            try {
                IBinder a = sub(BIND_HVAC);
                if (a != null) {
                    sendPending(a);
                    long now = android.os.SystemClock.uptimeMillis();
                    boolean power = Rpc.callBool(a, AC, AC_GET_POWER);
                    boolean auto = Rpc.callBool(a, AC, AC_GET_AUTO);
                    lastPower = power;
                    lastAuto = auto;
                    float drv = Rpc.callFloat(a, AC, AC_GET_DRV_TEMP);
                    int readTemp = tempFromCar(drv);
                    s.acTemp = (now < tempHoldUntil && heldTemp > 0) ? heldTemp : readTemp;
                    int speed = Rpc.callInt(a, AC, AC_GET_FAN);
                    int readFan = !power ? FAN_OFF : auto ? FAN_AUTO : speed;
                    s.fanLevel = (now < fanHoldUntil && heldFan >= 0) ? heldFan : readFan;
                    s.hvacPower = power ? 1 : 0;
                    s.outsideTemp = Rpc.callFloat(a, AC, AC_GET_OUT_TEMP);
                    Diag.put("clima.tempEsterna", s.outsideTemp);
                    Diag.put("clima.tempGuidatore", drv + (s.acTemp != readTemp ? " (richiesta " + s.acTemp + ")" : ""));
                    Diag.put("clima.ventola", speed + (auto ? " auto" : "") + (s.fanLevel != readFan ? " (richiesta " + s.fanLevel + ")" : ""));
                    Diag.put("clima.accensione", power);
                    s.acOn = held(AC_GET_AC, Rpc.callBool(a, AC, AC_GET_AC) ? 1 : 0, now);
                    s.autoOn = held(AC_GET_AUTO, auto ? 1 : 0, now);
                    s.defrostOn = held(AC_GET_DEFROST, Rpc.callBool(a, AC, AC_GET_DEFROST) ? 1 : 0, now);
                    Diag.put("clima.ac/auto/sbrinamento", s.acOn + " / " + s.autoOn + " / " + s.defrostOn);
                }
            } catch (Exception ex) {
                Diag.error("clima", ex.toString());
                forget(BIND_HVAC);
            }
        }
        readLamps(s);
        readBms(s);
        last = s;
        main.post(() -> {
            if (listener != null) listener.onVehicleState(s);
        });
    }

    /** Dati dell'app Energia: limite di ricarica, potenza, consumo, percentuale con decimali. */
    private void readBms(State s) {
        if (!bms.isReady()) {
            Diag.put("bms.letture", "non disponibile");
            return;
        }
        float socDsp = bms.getFloat(CarBms.PACK_SOC_DSP);
        if (ok(socDsp) && socDsp >= 0 && socDsp <= 100) s.soc = socDsp;
        int code = bms.getInt(CarBms.CHRG_TRGT_SOC);
        if (code != Integer.MIN_VALUE) {
            s.chargeLimit = limitPercent(code);
            if (s.chargeLimit < 0) s.chargeLimit = 100;
        }
        float amps = bms.getFloat(CarBms.PACK_CRNT);
        float volts = bms.getFloat(CarBms.PACK_VOL);
        if (ok(amps) && ok(volts)) s.chargeKw = Math.abs(amps * volts) / 1000f;
        float avg = bms.getFloat(CarBms.CRNT_AVG_ELEC_CSUMP);
        float lastKm = bms.getFloat(CarBms.ELEC_CSUMP_PERKM);
        s.consumption = ok(avg) && avg > 0 ? avg : (ok(lastKm) ? lastKm : -1);
        if (s.rangeKm <= 0) {
            int r = bms.getInt(CarBms.ESTD_ELEC_RNG);
            if (r > 0 && r < 2000) s.rangeKm = r;
        }
        Diag.put("bms.soc", socDsp);
        Diag.put("veicolo.limiteRicarica", "codice " + code + " = " + s.chargeLimit + "%");
        Diag.putQuiet("veicolo.correnteA/tensioneV", amps + " / " + volts);
        Diag.put("veicolo.consumo.media", ok(avg) ? String.format(Locale.ITALY, "%.1f", avg) : "nessun dato");
        Diag.putQuiet("veicolo.consumo.ultimoKm", lastKm);
        Diag.put("veicolo.consumo.altri", "reale " + bms.getFloat(CarBms.VEH_ACTU_ELEC_CSUMP)
                + " · mediaBatteria " + bms.getFloat(CarBms.BAT_ELEC_ENRG_AVG_RATE));
    }

    private void readLamps(State s) {
        try {
            if (frontFogConfig == Integer.MIN_VALUE) {
                IBinder cfg = sub(BIND_CONFIG);
                if (cfg != null) {
                    frontFogConfig = Rpc.callInt(cfg, CONFIG, CONFIG_FRONT_FOG);
                    Diag.put("luci.fendinebbiaAnterioriConfig", frontFogConfig);
                }
            }
        } catch (Exception e) {
            Diag.error("luci.config", e.toString());
            forget(BIND_CONFIG);
        }
        try {
            IBinder l = sub(BIND_LAMP);
            if (l != null) {
                long now = android.os.SystemClock.uptimeMillis();
                s.rearFog = held(LAMP_GET_REAR_FOG, Rpc.callInt(l, LAMP, LAMP_GET_REAR_FOG) == 1 ? 1 : 0, now);
                s.hasFrontFog = frontFogConfig == 0;
                if (s.hasFrontFog) s.frontFog = held(LAMP_GET_FRONT_FOG, Rpc.callInt(l, LAMP, LAMP_GET_FRONT_FOG) == 1 ? 1 : 0, now);
                Diag.put("luci.fendinebbia ant/post", s.frontFog + " / " + s.rearFog);
            }
        } catch (Exception e) {
            Diag.error("luci", e.toString());
            forget(BIND_LAMP);
        }
    }

    // ---- pulsanti a commutazione (A/C, AUTO, sbrinamento, fendinebbia) ------------------------

    private int frontFogConfig = Integer.MIN_VALUE;
    private final Map<Integer, long[]> holds = new HashMap<>();   // codice lettura -> {valore, scadenza}

    /** Dopo un tocco mostra subito il nuovo stato per qualche secondo, finché l'auto non lo conferma. */
    private int held(int getCode, int read, long now) {
        synchronized (holds) {
            long[] h = holds.get(getCode);
            if (h == null) return read;
            if (now < h[1] && read != h[0]) return (int) h[0];
            holds.remove(getCode);
            return read;
        }
    }

    private void hold(int getCode, int value) {
        synchronized (holds) {
            holds.put(getCode, new long[]{value, android.os.SystemClock.uptimeMillis() + HOLD_MS});
        }
    }

    /** Commuta: il servizio SW169 ha solo "cambia stato" (senza argomenti) per il clima, set(1) per le luci. */
    private void press(int bind, String desc, int setCode, boolean withOne, int getCode, int current, String what) {
        if (bg == null) return;
        if (current == 0 || current == 1) hold(getCode, 1 - current);
        bg.post(() -> {
            try {
                IBinder b = sub(bind);
                if (b != null) {
                    if (withOne) Rpc.call(b, desc, setCode, p -> p.writeInt(1));
                    else Rpc.call(b, desc, setCode, Rpc.NONE);
                }
            } catch (Exception e) {
                Diag.error(what, e.toString());
                forget(bind);
            }
            poll();
        });
    }

    public void toggleAc() {
        press(BIND_HVAC, AC, AC_SWITCH_AC, false, AC_GET_AC, last.acOn, "clima.ac");
    }

    public void toggleAuto() {
        press(BIND_HVAC, AC, AC_SWITCH_AUTO, false, AC_GET_AUTO, last.autoOn, "clima.auto");
    }

    public void toggleRearFog() {
        press(BIND_LAMP, LAMP, LAMP_SET_REAR_FOG, true, LAMP_GET_REAR_FOG, last.rearFog, "luci.retronebbia");
    }

    public void toggleFrontFog() {
        press(BIND_LAMP, LAMP, LAMP_SET_FRONT_FOG, true, LAMP_GET_FRONT_FOG, last.frontFog, "luci.fendinebbia");
    }

    /** Imposta la temperatura lato guida (15 = LO … 31 = HI). Il valore richiesto resta visibile per qualche secondo. */
    public void setDriverTemp(int celsius) {
        int t = Math.max(TEMP_MIN, Math.min(TEMP_MAX, celsius));
        heldTemp = t;
        tempHoldUntil = android.os.SystemClock.uptimeMillis() + HOLD_MS;
        pendingTemp = t;
        sendSoon();
    }

    /** Livello ventola: 0 = spegne il clima, 1..11, 15 = automatico. */
    public void setFanLevel(int level) {
        int l = level == FAN_AUTO || level == FAN_OFF ? level : Math.max(FAN_MIN, Math.min(FAN_MAX, level));
        heldFan = l;
        fanHoldUntil = android.os.SystemClock.uptimeMillis() + HOLD_MS;
        pendingFan = l;
        sendSoon();
    }

    private void sendPending(IBinder a) throws RemoteException {
        int t = pendingTemp;
        if (t > 0) {
            pendingTemp = -1;
            float f = tempToCar(t);
            Rpc.call(a, AC, AC_SET_DRV_TEMP, p -> p.writeFloat(f));
        }
        int fan = pendingFan;
        if (fan >= 0) {
            pendingFan = -1;
            boolean power = Rpc.callBool(a, AC, AC_GET_POWER);
            if (fan == FAN_OFF) {
                if (power) Rpc.call(a, AC, AC_SWITCH_POWER, Rpc.NONE);
            } else if (fan == FAN_AUTO) {
                if (!Rpc.callBool(a, AC, AC_GET_AUTO)) Rpc.call(a, AC, AC_SWITCH_AUTO, Rpc.NONE);
            } else {
                if (!power) Rpc.call(a, AC, AC_SWITCH_POWER, Rpc.NONE);
                Rpc.call(a, AC, AC_SET_FAN, p -> p.writeInt(fan));
            }
        }
    }

    /** Invia il comando subito, senza riscrivere la UI con una lettura immediata. */
    private void sendSoon() {
        if (bg == null) return;
        bg.post(() -> {
            try {
                IBinder a = sub(BIND_HVAC);
                if (a != null) sendPending(a);
            } catch (Exception e) {
                Diag.error("clima.comando", e.toString());
                forget(BIND_HVAC);
            }
        });
    }

    /** Accende o spegne il clima (il servizio SW169 ha solo il comando "cambia stato"). */
    public void setHvacPower(boolean on) {
        if (bg == null) return;
        bg.post(() -> {
            try {
                IBinder a = sub(BIND_HVAC);
                if (a != null && Rpc.callBool(a, AC, AC_GET_POWER) != on) {
                    Rpc.call(a, AC, AC_SWITCH_POWER, Rpc.NONE);
                }
            } catch (Exception e) {
                Diag.error("clima.accensione", e.toString());
            }
            poll();
        });
    }
}
