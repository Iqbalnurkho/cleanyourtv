package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parses synced lyrics in LRC format: "[mm:ss.xx] text" lines. */
final class LrcParser {
    final long[] times;
    final String[] lines;

    private LrcParser(long[] times, String[] lines) {
        this.times = times;
        this.lines = lines;
    }

    private static final class Entry {
        final long t;
        final String s;
        Entry(long t, String s) { this.t = t; this.s = s; }
    }

    static LrcParser parse(String lrc) {
        if (lrc == null || lrc.isEmpty()) return null;
        List<Entry> out = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            String line = raw.trim();
            List<Long> stamps = new ArrayList<>();
            while (line.startsWith("[")) {
                int end = line.indexOf(']');
                if (end < 0) break;
                long t = stamp(line.substring(1, end));
                if (t < 0) { // metadata tag such as [ar:...]
                    stamps.clear();
                    line = "";
                    break;
                }
                stamps.add(t);
                line = line.substring(end + 1).trim();
            }
            for (Long t : stamps) out.add(new Entry(t, line));
        }
        if (out.isEmpty()) return null;
        Collections.sort(out, (a, b) -> Long.compare(a.t, b.t));
        long[] times = new long[out.size()];
        String[] lines = new String[out.size()];
        for (int i = 0; i < times.length; i++) {
            times[i] = out.get(i).t;
            lines[i] = out.get(i).s;
        }
        return new LrcParser(times, lines);
    }

    private static long stamp(String s) {
        int c = s.indexOf(':');
        if (c <= 0) return -1;
        try {
            long min = Long.parseLong(s.substring(0, c).trim());
            double sec = Double.parseDouble(s.substring(c + 1).trim());
            return Math.round(min * 60000.0 + sec * 1000.0);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Index of the last line whose time is <= ms, or -1 before the first line. */
    int indexAt(long ms) {
        int lo = 0, hi = times.length - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= ms) { ans = mid; lo = mid + 1; } else { hi = mid - 1; }
        }
        return ans;
    }
}
