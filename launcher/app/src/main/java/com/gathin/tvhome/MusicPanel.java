package com.gathin.tvhome;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * Now-playing panel on Home: cover, title, artist and the current synced lyric line. Added to the root
 * from code, hidden unless a media session is playing or paused. Needs notification access (see
 * MusicListenerService); without it the panel simply stays hidden.
 */
final class MusicPanel implements Application.ActivityLifecycleCallbacks {
    /** Position on Home, dp from the top-left corner (the same slot as the race panel). */
    private static final int LEFT_DP = 60;
    private static final int TOP_DP = 158;
    private static final int WIDTH_DP = 380;
    private static final long TICK_MS = 300;
    private static final long LEAD_MS = 250;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout panel;
    private final ImageView cover;
    private final TextView title;
    private final TextView artist;
    private final TextView lineNow;
    private final TextView lineNext;
    private final Runnable ticker = this::tick;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::pick;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { refresh(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { refresh(); }
        @Override public void onSessionDestroyed() { bind(null); }
    };

    private MediaSessionManager sessions;
    private ComponentName listener;
    private MediaController controller;
    private Bitmap shownCover;
    private String trackKey = "";
    private LrcParser lrc;
    private int shownIndex = -2;
    private boolean started;

    /** Call once from MainActivity.onCreate, right after setContentView. */
    static void attach(Activity a) {
        View root = a.findViewById(R.id.root);
        if (!(root instanceof FrameLayout)) return;
        MusicPanel p = new MusicPanel(a, (FrameLayout) root);
        a.getApplication().registerActivityLifecycleCallbacks(p);
    }

    private MusicPanel(Activity a, FrameLayout root) {
        activity = a;
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        cover = new ImageView(a);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setClipToOutline(true);
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(8));
            }
        });
        cover.setVisibility(View.GONE);
        cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        title = text(20, 0xFFFFFFFF, medium, 1);
        artist = text(13, 0xB3FFFFFF, light, 1);
        LinearLayout names = new LinearLayout(a);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(title);
        names.addView(artist);

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        coverLp.setMarginEnd(dp(14));
        head.addView(cover, coverLp);
        head.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        lineNow = text(22, 0xFFFFFFFF, light, 2);
        lineNext = text(15, 0x80FFFFFF, light, 1);
        LinearLayout.LayoutParams nowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nowLp.topMargin = dp(14);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nextLp.topMargin = dp(4);

        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setFocusable(false);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.setVisibility(View.GONE);
        panel.addView(head);
        panel.addView(lineNow, nowLp);
        panel.addView(lineNext, nextLp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.setMargins(dp(LEFT_DP), dp(TOP_DP), 0, 0);
        root.addView(panel, lp);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                activity.getResources().getDisplayMetrics()));
    }

    private TextView text(float sp, int color, Typeface tf, int maxLines) {
        TextView t = new TextView(activity);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setMaxLines(maxLines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setFocusable(false);
        return t;
    }

    // ---- session handling ----

    private void start() {
        if (started) return;
        try {
            sessions = (MediaSessionManager) activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
            listener = new ComponentName(activity, MusicListenerService.class);
            sessions.addOnActiveSessionsChangedListener(sessionsListener, listener, handler);
            started = true;
            pick(sessions.getActiveSessions(listener));
        } catch (RuntimeException e) {
            // SecurityException until notification access is granted; try again on the next resume
            started = false;
        }
    }

    private void stop() {
        if (sessions != null) {
            try { sessions.removeOnActiveSessionsChangedListener(sessionsListener); } catch (RuntimeException ignored) { }
        }
        if (controller != null) {
            try { controller.unregisterCallback(callback); } catch (RuntimeException ignored) { }
            controller = null;
        }
        handler.removeCallbacksAndMessages(null);
        started = false;
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s == null) continue;
                if (s.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
                if (best == null && s.getState() == PlaybackState.STATE_PAUSED && c.getMetadata() != null) best = c;
            }
        }
        bind(best);
    }

    private void bind(MediaController c) {
        boolean same = c != null && controller != null
                && c.getSessionToken().equals(controller.getSessionToken());
        if (!same) {
            if (controller != null) {
                try { controller.unregisterCallback(callback); } catch (RuntimeException ignored) { }
            }
            controller = c;
            if (c != null) c.registerCallback(callback, handler);
        }
        refresh();
    }

    private void refresh() {
        if (controller == null) { hide(); return; }
        MediaMetadata m = controller.getMetadata();
        PlaybackState s = controller.getPlaybackState();
        if (m == null || s == null || s.getState() == PlaybackState.STATE_STOPPED
                || s.getState() == PlaybackState.STATE_NONE) { hide(); return; }
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(a)) a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (TextUtils.isEmpty(t)) { hide(); return; }
        if (a == null) a = "";
        title.setText(t);
        artist.setText(a);
        setCover(m);

        String key = a + "|" + t;
        if (!key.equals(trackKey)) {
            trackKey = key;
            lrc = null;
            shownIndex = -2;
            setLines("", "");
            if (!a.isEmpty()) {
                final String k = key;
                LyricsFetcher.fetch(activity, t, a, m.getLong(MediaMetadata.METADATA_KEY_DURATION), lyrics -> {
                    if (!k.equals(trackKey)) return; // the track changed while this was loading
                    lrc = LrcParser.parse(lyrics);
                    shownIndex = -2;
                    tick();
                });
            }
        }
        panel.setVisibility(View.VISIBLE);
        tick();
    }

    private void hide() {
        panel.setVisibility(View.GONE);
        handler.removeCallbacks(ticker);
        trackKey = "";
    }

    private void setCover(MediaMetadata m) {
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (b == shownCover) return;
        shownCover = b;
        cover.setImageBitmap(b);
        cover.setVisibility(b == null ? View.GONE : View.VISIBLE);
    }

    private void setLines(String now, String next) {
        lineNow.setText(now);
        lineNext.setText(next);
    }

    private void tick() {
        handler.removeCallbacks(ticker);
        if (controller == null || panel.getVisibility() != View.VISIBLE) return;
        PlaybackState s = controller.getPlaybackState();
        if (s == null || lrc == null) return;
        long pos = Math.max(0, s.getPosition());
        boolean playing = s.getState() == PlaybackState.STATE_PLAYING;
        if (playing) {
            long dt = SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime();
            pos += (long) (dt * s.getPlaybackSpeed());
        }
        int i = lrc.indexAt(pos + LEAD_MS);
        if (i != shownIndex) {
            shownIndex = i;
            String now = i < 0 ? "" : (lrc.lines[i].isEmpty() ? "\u266A" : lrc.lines[i]);
            String next = i + 1 < lrc.lines.length ? lrc.lines[i + 1] : "";
            setLines(now, next);
        }
        if (playing) handler.postDelayed(ticker, TICK_MS);
    }

    // ---- lifecycle: only listen while Home is on screen ----

    @Override public void onActivityResumed(Activity a) { if (a == activity) start(); }
    @Override public void onActivityPaused(Activity a) { if (a == activity) stop(); }
    @Override public void onActivityDestroyed(Activity a) {
        if (a != activity) return;
        stop();
        a.getApplication().unregisterActivityLifecycleCallbacks(this);
    }
    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
}
