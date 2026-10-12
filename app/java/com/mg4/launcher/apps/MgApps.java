package com.mg4.launcher.apps;

/**
 * App MG del software SW169 (EH32 Color Radio): pacchetti e schermate principali,
 * presi dai manifest del firmware SWI69-29958-1100R45.
 */
public final class MgApps {
    private MgApps() {}

    public static final String HOME_PKG = "com.saicmotor.launcher";
    public static final String HOME_CLS = "com.saicmotor.launcher.view.MainActivity";

    public static final String RADIO_PKG = "com.saicmotor.radio";
    public static final String RADIO_CLS = "com.saicmotor.radio.view.RadioMainActivity";

    public static final String MEDIA_PKG = "com.saicmotor.media";
    public static final String MEDIA_CLS = "com.saicmotor.media.ui.activity.MainActivity";

    public static final String HVAC_PKG = "com.saicmotor.hvac";
    public static final String HVAC_CLS = "com.saicmotor.hvac.ui.activity.MainActivity";

    public static final String PHONE_PKG = "com.saicmotor.btphone";
    public static final String PHONE_CLS = "com.saicmotor.btphone.view.activity.BTPhoneMainActivity";

    public static final String SETTINGS_PKG = "com.saicmotor.settings";
    public static final String SETTINGS_CLS = "com.saicmotor.settings.ui.MainActivity";

    public static final String VEHICLE_PKG = "com.saicmotor.vehiclesetting";
    public static final String VEHICLE_CLS = "com.saicmotor.vehiclesetting.ui.VehicleSettingActivity";

    /** App Energia (ricarica, scarica V2L, consumi): non ha collegamenti diretti alle schede. */
    public static final String ENERGY_PKG = "com.saicmotor.saicev";
    public static final String ENERGY_CLS = "com.saicmotor.saicev.activity.MainActivity";

    /** Telecamere: la home originale avvia EvsService con "state" = 1 (0 = chiudi). */
    public static final String CAMERA_PKG = "com.saicmotor.evs";
    public static final String CAMERA_SERVICE = "com.saicmotor.evs.service.EvsService";
    public static final String CAMERA_CLS = "com.saicmotor.evs.view.EvsActivity";

    public static final String FILES_PKG = "com.saicmotor.folder";
    public static final String FILES_CLS = "com.saicmotor.folder.ui.activity.MainActivity";

    public static final String SOS_PKG = "com.saicmotor.ecall";
    public static final String SOS_CLS = "com.saicmotor.ecall.view.activity.MainActivity";
}
