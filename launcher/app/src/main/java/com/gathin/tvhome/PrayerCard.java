package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Prayer card: flat dark card, five times in columns with the next one lit, days to the coming holidays. */
final class PrayerCard {
    private static final String[] NAMES = {"Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya"};
    /** Each column's place in PrayerTimes.on(): Subuh, Syuruk, Dzuhur, Ashar, Maghrib, Isya (Syuruk skipped). */
    private static final int[] SLOT = {0, 2, 3, 4, 5};
    private static final String[] EVENTS = {"Ramadan", "Idul Fitri", "Idul Adha"};
    /** KHGT Muhammadiyah calendar; Kemenag can differ by a day. Edit when the dates move. */
    private static final LocalDate[] DATES = {
            LocalDate.of(2027, 2, 8), LocalDate.of(2027, 3, 9), LocalDate.of(2027, 5, 16)};

    private PrayerCard() {
    }

    static void update(LinearLayout panel, LocalTime[] times, int next) {
        Context c = panel.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout row = panel.findViewWithTag("prayerRow");
        TextView events = panel.findViewWithTag("prayerEvents");
        if (row == null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x8C0F0F14);
            bg.setCornerRadius(16 * d);
            bg.setStroke(Math.max(1, Math.round(d)), 0x2EFFFFFF);
            panel.setBackground(bg);
            int h = Math.round(18 * d), v = Math.round(12 * d);
            panel.setPadding(h, v, h, v);
            View old = panel.findViewById(R.id.prayerTimes);
            if (old != null) old.setVisibility(View.GONE);
            Typeface face = Typeface.create(c.getString(R.string.font_medium), Typeface.NORMAL);
            row = new LinearLayout(c);
            row.setTag("prayerRow");
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.topMargin = Math.round(10 * d);
            panel.addView(row, 1, rp);
            for (int i = 0; i < NAMES.length; i++) {
                TextView cell = new TextView(c);
                cell.setGravity(Gravity.CENTER);
                cell.setTypeface(face);
                cell.setTextSize(11);
                cell.setMinWidth(Math.round(84 * d));
                int p = Math.round(6 * d);
                cell.setPadding(p, p, p, p);
                row.addView(cell);
            }
            events = new TextView(c);
            events.setTag("prayerEvents");
            events.setTypeface(face);
            events.setTextSize(11);
            events.setSingleLine(true);
            events.setTextColor(0x99FFFFFF);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ep.topMargin = Math.round(8 * d);
            panel.addView(events, 2, ep);
        }

        int lit = 0;
        if (next >= 0) {
            lit = SLOT.length - 1;
            for (int i = 0; i < SLOT.length; i++) {
                if (SLOT[i] >= next) {
                    lit = i;
                    break;
                }
            }
        }
        for (int i = 0; i < NAMES.length; i++) {
            TextView cell = (TextView) row.getChildAt(i);
            LocalTime t = times[SLOT[i]];
            SpannableStringBuilder sb = new SpannableStringBuilder(NAMES[i]).append('\n');
            int start = sb.length();
            sb.append(String.format(Locale.US, "%02d:%02d", t.getHour(), t.getMinute()));
            sb.setSpan(new RelativeSizeSpan(1.9f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            boolean passed = next >= 0 ? i < lit : i > 0;
            int color = i == lit ? 0xFFFFFFFF : (passed ? 0x59FFFFFF : 0xCCFFFFFF);
            sb.setSpan(new ForegroundColorSpan(color), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            cell.setText(sb);
            if (i == lit) {
                GradientDrawable on = new GradientDrawable();
                on.setColor(0x33FFFFFF);
                on.setCornerRadius(10 * d);
                cell.setBackground(on);
            } else {
                cell.setBackground(null);
            }
        }

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Jakarta"));
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < EVENTS.length; i++) {
            long days = ChronoUnit.DAYS.between(today, DATES[i]);
            if (days < 0) continue;
            if (line.length() > 0) line.append("   \u00B7   ");
            line.append(EVENTS[i]).append(' ').append(days == 0 ? "hari ini" : days + " hari lagi");
        }
        events.setText(line);
        events.setVisibility(line.length() == 0 ? View.GONE : View.VISIBLE);
    }
}
