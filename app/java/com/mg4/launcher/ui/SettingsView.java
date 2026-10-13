package com.mg4.launcher.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.mg4.launcher.R;
import com.mg4.launcher.svc.Diag;
import com.mg4.launcher.svc.Updater;

/** Impostazioni del launcher (si aprono tenendo premuta l'auto o dalla griglia app). */
public final class SettingsView extends FrameLayout {

    public interface Host {
        void closeSettings();

        void settingsChanged(boolean rebuild);

        void openOriginalHome();

        Updater updater();

        com.mg4.launcher.svc.LogUploader logs();
    }

    private final Theme t;
    private final Prefs prefs;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView diag;
    private final LinearLayout colorRow, themeRow, navRow;
    private LinearLayout langRow;
    private TextView logUpload;
    private final TextView colorName;
    private TextView updStatus, updInstall, updCheck, logBtn, logStatus;
    private Updater.Release found;

    public SettingsView(Context c, Theme t, Prefs prefs, Host host) {
        super(c);
        this.t = t;
        this.prefs = prefs;
        this.host = host;
        setBackgroundColor(t.day ? 0x66000000 : 0xB3000000);
        setClickable(true);
        setOnClickListener(v -> host.closeSettings());

        LinearLayout card = Ui.row(c);
        card.setGravity(Gravity.TOP);
        card.setBackground(Ui.round(t.panel, dp(28)));
        card.setPadding(dp(36), dp(28), dp(28), dp(28));
        card.setClickable(true);
        LayoutParams lp = new LayoutParams(dp(1500), dp(580), Gravity.CENTER);
        addView(card, lp);

        // --- colonna impostazioni ---
        ScrollView left = new ScrollView(c);
        LinearLayout col = Ui.column(c);
        left.addView(col);
        card.addView(left, Ui.lpWeight(0, -1, 1.15f));

        LinearLayout titleRow = Ui.row(c);
        titleRow.addView(Ui.text(c, c.getString(R.string.set_title), 30, t.text, Ui.MEDIUM));
        col.addView(titleRow);

        col.addView(label(c, c.getString(R.string.set_car_color)), top(22));
        colorRow = Ui.row(c);
        col.addView(colorRow, top(10));
        colorName = Ui.text(c, "", 18, t.sub, Ui.REGULAR);
        col.addView(colorName, top(8));

        LinearLayout roof = Ui.row(c);
        roof.addView(Ui.text(c, c.getString(R.string.set_black_roof), 20, t.text, Ui.REGULAR), Ui.lpWeight(0, -2, 1));
        Switch sw = new Switch(c);
        sw.setChecked(prefs.blackRoof());
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.setBlackRoof(on);
            host.settingsChanged(false);
        });
        roof.addView(sw);
        col.addView(roof, top(18));

        col.addView(label(c, c.getString(R.string.set_theme)), top(24));
        themeRow = Ui.row(c);
        col.addView(themeRow, top(10));

        // lingua: "Come l'auto" segue la lingua di sistema, oppure una delle lingue tradotte
        col.addView(label(c, c.getString(R.string.set_language)), top(24));
        android.widget.HorizontalScrollView ls = new android.widget.HorizontalScrollView(c);
        ls.setHorizontalScrollBarEnabled(false);
        langRow = Ui.row(c);
        ls.addView(langRow);
        col.addView(ls, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, dp(10), 0, 0));

        // SW169: niente navigatore MG, si usa sempre quello del telefono (CarPlay / Android Auto):
        // la scelta del navigatore predefinito non serve e non si mostra
        navRow = Ui.row(c);

        // invio automatico dei log nel repository privato dei log
        col.addView(label(c, c.getString(R.string.log_upload_label)), top(24));
        LinearLayout lr = Ui.row(c);
        lr.addView(Ui.text(c, c.getString(R.string.log_upload_on), 20, t.text, Ui.REGULAR), Ui.lpWeight(0, -2, 1));
        Switch ls2 = new Switch(c);
        ls2.setChecked(host.logs().enabled());
        ls2.setEnabled(com.mg4.launcher.svc.LogUploader.configured());
        ls2.setOnCheckedChangeListener((b, on) -> host.logs().setEnabled(on));
        lr.addView(ls2);
        col.addView(lr, top(10));
        logUpload = Ui.text(c, "", 15, t.sub, Ui.REGULAR);
        col.addView(logUpload, top(6));

        TextView original = Ui.text(c, c.getString(R.string.set_open_original), 19, t.text, Ui.MEDIUM);
        original.setGravity(Gravity.CENTER_VERTICAL);
        original.setPadding(dp(22), 0, dp(22), 0);
        original.setMinHeight(dp(56));
        original.setBackground(Ui.pressable(c, t.chip, 16, t));
        original.setClickable(true);
        original.setOnClickListener(v -> host.openOriginalHome());
        col.addView(original, top(28));

        buildUpdates(c, col);

        // --- colonna diagnostica ---
        LinearLayout right = Ui.column(c);
        LinearLayout rh = Ui.row(c);
        rh.addView(label(c, c.getString(R.string.set_diag)), Ui.lpWeight(0, -2, 1));
        logBtn = button(c, c.getString(R.string.save_log), false, v -> saveLog());
        logBtn.setMinHeight(dp(48));
        rh.addView(logBtn, Ui.margins(Ui.lp(-2, dp(48)), 0, 0, dp(10), 0));
        rh.addView(Ui.iconButton(c, t, R.drawable.ic_close, t.text, t.chip, 56, 26, 28, c.getString(R.string.close), v -> host.closeSettings()));
        right.addView(rh);
        ScrollView ds = new ScrollView(c);
        ds.setBackground(Ui.round(t.bg, dp(16)));
        diag = Ui.text(c, "", 13, t.sub, Typeface.MONOSPACE);
        diag.setPadding(dp(16), dp(14), dp(16), dp(14));
        diag.setTextIsSelectable(true);
        ds.addView(diag);
        right.addView(ds, Ui.margins(Ui.lpWeight(-1, 0, 1), 0, dp(12), 0, 0));
        logStatus = Ui.text(c, c.getString(R.string.log_hint), 14, t.sub, Ui.REGULAR);
        right.addView(logStatus, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, dp(10), 0, 0));
        card.addView(right, Ui.margins(Ui.lpWeight(0, -1, 1f), dp(32), 0, 0, 0));

        buildSwatches();
    }

    // ---- aggiornamenti da GitHub ----------------------------------------------------------------

    private void buildUpdates(Context c, LinearLayout col) {
        Updater up = host.updater();
        col.addView(label(c, c.getString(R.string.set_updates)), top(30));
        col.addView(Ui.text(c, "MG4 Launcher " + up.currentVersion() + " · " + prefs.updateRepo(), 17, t.text, Ui.REGULAR), top(10));

        LinearLayout btns = Ui.row(c);
        updCheck = button(c, c.getString(R.string.upd_check_btn), false, v -> up.check(prefs.updateRepo(), prefs.githubToken()));
        btns.addView(updCheck, Ui.margins(Ui.lp(-2, dp(56)), 0, 0, dp(12), 0));
        updInstall = button(c, c.getString(R.string.upd_install_btn), true, v -> up.install(found, prefs.githubToken()));
        updInstall.setVisibility(GONE);
        btns.addView(updInstall, Ui.lp(-2, dp(56)));
        col.addView(btns, top(12));

        updStatus = Ui.text(c, "", 16, t.sub, Ui.REGULAR);
        col.addView(updStatus, top(10));

        up.setListener((text, r, busy) -> {
            if (r != null) found = r;
            updStatus.setText(text);
            updCheck.setEnabled(!busy);
            updCheck.setAlpha(busy ? 0.5f : 1f);
            boolean canInstall = !busy && found != null && found.newer && found.apkUrl != null;
            updInstall.setVisibility(canInstall ? VISIBLE : GONE);
            if (canInstall) updInstall.setText(getContext().getString(R.string.upd_install_ver, found.tag));
        });
        updStatus.setText(c.getString(up.isBusy() ? R.string.upd_busy : R.string.upd_idle));
    }

    private TextView button(Context c, String text, boolean primary, View.OnClickListener l) {
        TextView b = Ui.text(c, text, 18, primary ? t.onAccent : t.text, Ui.MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(22), 0, dp(22), 0);
        b.setMinHeight(dp(56));
        b.setBackground(Ui.pressable(c, primary ? t.accent : t.chip, 16, t));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** Salva il log completo (in background) e mostra dove è finito. */
    private void saveLog() {
        logBtn.setEnabled(false);
        logBtn.setAlpha(0.5f);
        logStatus.setText(getContext().getString(R.string.log_saving));
        Context app = getContext().getApplicationContext();
        new Thread(() -> {
            Diag.Saved r = Diag.saveLog(app);
            main.post(() -> {
                logBtn.setEnabled(true);
                logBtn.setAlpha(1f);
                if (r.paths.isEmpty()) {
                    logStatus.setText(app.getString(R.string.log_failed));
                    return;
                }
                StringBuilder sb = new StringBuilder(app.getString(r.onUsb ? R.string.log_saved_usb : R.string.log_saved)).append('\n');
                for (String path : r.paths) sb.append(path).append('\n');
                if (!r.onUsb) sb.append(app.getString(R.string.log_usb_hint));
                logStatus.setText(sb.toString().trim());
            });
        }, "mg4-log").start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        main.removeCallbacks(refreshDiag);
        refreshDiag.run();
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }

    private LinearLayout.LayoutParams top(int m) {
        return Ui.margins(new LinearLayout.LayoutParams(-2, -2), 0, dp(m), 0, 0);
    }

    private TextView label(Context c, String s) {
        TextView tv = Ui.text(c, s, 14, t.sub, Ui.MEDIUM);
        tv.setLetterSpacing(0.08f);
        return tv;
    }

    private void buildSwatches() {
        Context c = getContext();
        colorRow.removeAllViews();
        int sel = prefs.carColorIndex();
        for (int i = 0; i < Prefs.CAR_COLORS.length; i++) {
            final int idx = i;
            colorRow.addView(swatch(c, Prefs.CAR_COLOR_VALUES[i], i == sel, Prefs.CAR_COLORS[i], v -> {
                prefs.setCarColorIndex(idx);
                buildSwatches();
                host.settingsChanged(false);
            }), Ui.margins(Ui.lp(dp(56), dp(56)), 0, 0, dp(12), 0));
        }
        colorName.setText(Prefs.CAR_COLORS[sel]);

        navRow.removeAllViews();
        String nav = prefs.navApp();
        String[][] navs = {{Prefs.NAV_AUTO, c.getString(R.string.theme_auto)}, {Prefs.NAV_PHONE, c.getString(R.string.phone)}};
        for (String[] m : navs) {
            boolean on = m[0].equals(nav);
            TextView b = Ui.text(c, m[1], 18, on ? t.panel : t.text, Ui.MEDIUM);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(22), 0, dp(22), 0);
            b.setMinHeight(dp(52));
            b.setBackground(on ? Ui.round(t.text, dp(14)) : Ui.pressable(c, t.chip, 14, t));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                prefs.setNavApp(m[0]);
                buildSwatches();
                host.settingsChanged(false);
            });
            navRow.addView(b, Ui.margins(Ui.lp(-2, dp(52)), 0, 0, dp(10), 0));
        }

        themeRow.removeAllViews();
        String mode = prefs.themeMode();
        String[][] modes = {{Prefs.THEME_AUTO, c.getString(R.string.theme_auto)}, {Prefs.THEME_NIGHT, c.getString(R.string.theme_night)}, {Prefs.THEME_DAY, c.getString(R.string.theme_day)}};
        for (String[] m : modes) {
            boolean on = m[0].equals(mode);
            TextView b = Ui.text(c, m[1], 18, on ? t.panel : t.text, Ui.MEDIUM);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(22), 0, dp(22), 0);
            b.setMinHeight(dp(52));
            b.setBackground(on ? Ui.round(t.text, dp(14)) : Ui.pressable(c, t.chip, 14, t));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                prefs.setThemeMode(m[0]);
                host.settingsChanged(true);
            });
            themeRow.addView(b, Ui.margins(Ui.lp(-2, dp(52)), 0, 0, dp(10), 0));
        }
        buildLanguages(c);
    }

    /** Lingua: "Come l'auto (lingua attuale)" + le lingue tradotte, ognuna scritta nella propria lingua. */
    private void buildLanguages(Context c) {
        langRow.removeAllViews();
        String cur = prefs.language();
        java.util.List<String[]> opts = new java.util.ArrayList<>();
        opts.add(new String[]{Lang.AUTO, c.getString(R.string.lang_auto) + " · " + Lang.systemName()});
        for (int i = 0; i < Lang.CODES.length; i++) opts.add(new String[]{Lang.CODES[i], Lang.NAMES[i]});
        for (String[] m : opts) {
            boolean on = m[0].equals(cur);
            TextView b = Ui.text(c, m[1], 18, on ? t.panel : t.text, Ui.MEDIUM);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(20), 0, dp(20), 0);
            b.setMinHeight(dp(52));
            b.setBackground(on ? Ui.round(t.text, dp(14)) : Ui.pressable(c, t.chip, 14, t));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                if (m[0].equals(prefs.language())) return;
                prefs.setLanguage(m[0]);
                host.settingsChanged(true);   // riapplica la lingua e ricostruisce l'interfaccia
            });
            langRow.addView(b, Ui.margins(Ui.lp(-2, dp(52)), 0, 0, dp(10), 0));
        }
    }

    private View swatch(Context c, int color, boolean selected, String desc, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(dp(selected ? 4 : 1), selected ? t.text : t.track);
        f.setBackground(g);
        f.setContentDescription(desc);
        f.setClickable(true);
        f.setOnClickListener(l);
        return f;
    }

    /** Stato dell'invio dei log, nella lingua del launcher. */
    private String logUploadText() {
        Context c = getContext();
        if (!com.mg4.launcher.svc.LogUploader.configured()) return c.getString(R.string.log_upload_nokey);
        if (!host.logs().enabled()) return c.getString(R.string.log_upload_off);
        String st = com.mg4.launcher.svc.LogUploader.status();
        if (st.startsWith("ok ")) return c.getString(R.string.log_upload_last, st.substring(3));
        if (st.startsWith("errore ")) return c.getString(R.string.log_upload_error, st.substring(7));
        return c.getString(R.string.log_upload_never);
    }

    private final Runnable refreshDiag = new Runnable() {
        @Override
        public void run() {
            String d = Diag.dump();
            diag.setText(d.isEmpty() ? getContext().getString(R.string.diag_waiting) : d);
            logUpload.setText(logUploadText());
            if (isAttachedToWindow()) main.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        main.removeCallbacks(refreshDiag);
        host.updater().setListener(null);
    }
}
