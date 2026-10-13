package com.mg4.launcher.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mg4.launcher.R;

/**
 * Avviso discreto di nuova versione: piccola scheda in basso a destra, sopra la barra.
 * Non blocca nulla, sparisce da sola dopo qualche secondo; "Più tardi" la rimanda di un giorno.
 */
public final class UpdateBanner extends LinearLayout {
    public interface Actions {
        void onUpdate();

        void onLater();
    }

    private static final long AUTO_HIDE_MS = 25_000;
    private final TextView title;

    public UpdateBanner(Context c, Theme t, Actions actions) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(Ui.round(t.overlay, Ui.dp(c, 22)));
        setElevation(Ui.dp(c, 8));
        int p = Ui.dp(c, 14);
        setPadding(p, p, p, p);
        setClickable(true);   // i tocchi sulla scheda non passano alla home

        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Ui.round(t.accent, Ui.dp(c, 22)));
        badge.addView(Ui.icon(c, R.drawable.ic_download, t.onAccent, 24),
                new FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER));
        addView(badge, new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));

        LinearLayout txt = Ui.column(c);
        title = Ui.oneLine(Ui.text(c, "", 18, t.text, Ui.MEDIUM));
        txt.addView(title);
        txt.addView(Ui.oneLine(Ui.text(c, c.getString(R.string.upd_banner_sub), 14, t.sub, Ui.REGULAR)));
        addView(txt, Ui.margins(new LayoutParams(-2, -2), Ui.dp(c, 14), 0, Ui.dp(c, 18), 0));

        TextView later = Ui.text(c, c.getString(R.string.upd_banner_later), 16, t.sub, Ui.MEDIUM);
        later.setGravity(Gravity.CENTER);
        later.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        later.setBackground(Ui.pressable(c, 0, 14, t));
        later.setClickable(true);
        later.setOnClickListener(v -> {
            hide();
            actions.onLater();
        });
        addView(later, new LayoutParams(-2, Ui.dp(c, 48)));

        TextView update = Ui.text(c, c.getString(R.string.upd_banner_update), 16, t.onAccent, Ui.MEDIUM);
        update.setGravity(Gravity.CENTER);
        update.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), 0);
        update.setBackground(Ui.pressable(c, t.accent, 14, t));
        update.setClickable(true);
        update.setOnClickListener(v -> {
            hide();
            actions.onUpdate();
        });
        addView(update, Ui.margins(new LayoutParams(-2, Ui.dp(c, 48)), Ui.dp(c, 8), 0, 0, 0));
        setVisibility(GONE);
    }

    private final Runnable autoHide = this::hide;

    public void show(String version) {
        title.setText(getContext().getString(R.string.upd_banner_title, version));
        removeCallbacks(autoHide);
        setAlpha(0f);
        setTranslationY(Ui.dp(getContext(), 24));
        setVisibility(VISIBLE);
        animate().alpha(1f).translationY(0).setDuration(250).start();
        postDelayed(autoHide, AUTO_HIDE_MS);
    }

    public void hide() {
        removeCallbacks(autoHide);
        if (getVisibility() != VISIBLE) return;
        animate().alpha(0f).translationY(Ui.dp(getContext(), 24)).setDuration(200)
                .withEndAction(() -> setVisibility(GONE)).start();
    }
}
