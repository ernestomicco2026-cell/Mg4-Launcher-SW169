package com.mg4.launcher.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.mg4.launcher.R;
import com.mg4.launcher.svc.Diag;

/**
 * Pillola in alto a destra, prima delle schede: Bluetooth e Wi-Fi con 4 tacche (come la SW168 0.6.0).
 * Bluetooth: pieno = collegato a un telefono, sbiadito = acceso. Wi-Fi: solo se collegato, tacche = segnale.
 * Se nulla è acceso la pillola sparisce.
 */
public final class ConnIndicator extends LinearLayout {
    private static final long PERIOD_MS = 3000;
    /** Profili BT: 16 HFP client (chiamate), 11 A2DP sink (musica dal telefono), 1 cuffie, 2 A2DP. */
    private static final int[] BT_PROFILES = {16, 11, 1, 2};

    private final ImageView bt;
    private final WifiBars wifi;

    public ConnIndicator(Context c, Theme t) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(Ui.round(t.overlay, Ui.dp(c, 16)));
        setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
        bt = Ui.icon(c, R.drawable.ic_bluetooth, t.text, 22);
        addView(bt, new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        wifi = new WifiBars(c, t.text, t.track);
        addView(wifi, Ui.margins(new LayoutParams(Ui.dp(c, 26), Ui.dp(c, 22)), Ui.dp(c, 12), 0, 0, 0));
        setVisibility(GONE);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            postDelayed(this, PERIOD_MS);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(tick);
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    /** -1 = Wi-Fi non collegato, altrimenti 1..4 tacche. */
    private int wifiLevel() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm != null ? cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI) : null;
            if (ni == null || !ni.isConnected()) return -1;
            WifiManager wm = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm != null ? wm.getConnectionInfo() : null;
            if (wi == null) return 4;
            return Math.max(1, WifiManager.calculateSignalLevel(wi.getRssi(), 5));
        } catch (Throwable e) {
            return -1;
        }
    }

    /** 0 = spento, 1 = acceso non collegato, 2 = collegato. */
    private int btState() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || !a.isEnabled()) return 0;
            try {
                Object st = BluetoothAdapter.class.getMethod("getConnectionState").invoke(a);
                if (st instanceof Integer && (Integer) st == BluetoothAdapter.STATE_CONNECTED) return 2;
            } catch (Throwable ignored) {
            }
            for (int prof : BT_PROFILES) {
                try {
                    if (a.getProfileConnectionState(prof) == BluetoothProfile.STATE_CONNECTED) return 2;
                } catch (Throwable ignored) {
                }
            }
            return 1;
        } catch (Throwable e) {
            return 0;
        }
    }

    private int lastW = -2, lastB = -1;

    private void refresh() {
        int w = wifiLevel(), b = btState();
        if (w == lastW && b == lastB) return;
        lastW = w;
        lastB = b;
        bt.setVisibility(b == 0 ? GONE : VISIBLE);
        bt.setAlpha(b == 2 ? 1f : 0.4f);
        wifi.setVisibility(w < 0 ? GONE : VISIBLE);
        wifi.setLevel(w);
        LayoutParams lp = (LayoutParams) wifi.getLayoutParams();
        lp.leftMargin = b == 0 ? 0 : Ui.dp(getContext(), 12);
        wifi.setLayoutParams(lp);
        setVisibility(w < 0 && b == 0 ? GONE : VISIBLE);
        Diag.put("conn.bt/wifi", (b == 2 ? "BT collegato" : b == 1 ? "BT acceso" : "BT spento")
                + " · " + (w < 0 ? "Wi-Fi no" : "Wi-Fi " + w + "/4"));
    }

    /** Simbolo Wi-Fi a 4 tacche: punto + 3 archi, accesi in base al segnale. */
    static final class WifiBars extends View {
        private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int level = 4;

        WifiBars(Context c, int fg, int dim) {
            super(c);
            for (Paint p : new Paint[]{on, off}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
            }
            on.setColor(fg);
            off.setColor(dim);
        }

        void setLevel(int l) {
            if (l != level) {
                level = l;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float stroke = h * 0.12f;
            on.setStrokeWidth(stroke);
            off.setStrokeWidth(stroke);
            float cx = w / 2f, cy = h - stroke * 0.9f;
            // tacca 1: il punto
            Paint dot = level >= 1 ? on : off;
            dot.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, stroke * 0.95f, dot);
            dot.setStyle(Paint.Style.STROKE);
            // tacche 2..4: archi crescenti
            float maxR = Math.min(w / 2f, h) - stroke / 2f;
            for (int i = 1; i <= 3; i++) {
                float rad = maxR * (0.2f + 0.8f * i / 3f);
                r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                c.drawArc(r, 225, 90, false, level >= i + 1 ? on : off);
            }
        }
    }
}
