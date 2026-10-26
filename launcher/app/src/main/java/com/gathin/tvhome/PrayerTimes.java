package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Prayer times worked out on the TV, no network. Kemenag RI method: Fajr 20 degrees, Isha 18 degrees,
 * Asr shadow factor 1, plus a small safety margin (ihtiyat). Compare one day with bimasislam.kemenag.go.id
 * and nudge IHTIYAT if a time is a minute off.
 */
final class PrayerTimes {
    static final double LAT = -6.9175, LON = 107.6191, TZ = 7.0;   // Bandung, WIB
    static final double FAJR = 20, ISHA = 18, RISE_SET = 0.833;
    /** Minutes added to Subuh, Syuruk, Dzuhur, Ashar, Maghrib, Isya. */
    static final int[] IHTIYAT = {3, -2, 3, 2, 7, 3};

    private PrayerTimes() {
    }

    /** Subuh, Syuruk, Dzuhur, Ashar, Maghrib, Isya for one day. */
    static LocalTime[] on(LocalDate date) {
        double jd = julian(date.getYear(), date.getMonthValue(), date.getDayOfMonth()) - LON / 360.0;
        double[] t = {5, 6, 12, 13, 18, 18};
        t = compute(jd, t);
        t = compute(jd, t);   // a second pass settles the sun's position
        LocalTime[] out = new LocalTime[6];
        for (int i = 0; i < 6; i++) {
            double hours = t[i] + TZ - LON / 15;
            long minutes = Math.round(fixHour(hours) * 60) + IHTIYAT[i];
            out[i] = LocalTime.ofSecondOfDay(Math.floorMod(minutes, 1440L) * 60);
        }
        return out;
    }

    /** Index (0..5) of the next time after now, or -1 once Isya has passed. */
    static int next(LocalTime[] times, LocalTime now) {
        for (int i = 0; i < times.length; i++) if (now.isBefore(times[i])) return i;
        return -1;
    }

    private static double[] compute(double jd, double[] t) {
        return new double[] {
                angleTime(jd, FAJR, t[0] / 24, true),
                angleTime(jd, RISE_SET, t[1] / 24, true),
                12 - sunPosition(jd + t[2] / 24)[1],
                asrTime(jd, t[3] / 24),
                angleTime(jd, RISE_SET, t[4] / 24, false),
                angleTime(jd, ISHA, t[5] / 24, false)};
    }

    private static double angleTime(double jd, double angle, double day, boolean before) {
        double[] sun = sunPosition(jd + day);
        double noon = 12 - sun[1];
        double cosH = (-sin(angle) - sin(sun[0]) * sin(LAT)) / (cos(sun[0]) * cos(LAT));
        double h = Math.toDegrees(Math.acos(cosH)) / 15;
        return before ? noon - h : noon + h;
    }

    private static double asrTime(double jd, double day) {
        double decl = sunPosition(jd + day)[0];
        double altitude = Math.toDegrees(Math.atan(1.0 / (1 + Math.tan(Math.toRadians(Math.abs(LAT - decl))))));
        return angleTime(jd, -altitude, day, false);
    }

    /** {declination in degrees, equation of time in hours}. */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = fixHour(Math.toDegrees(Math.atan2(cos(e) * sin(l), cos(l))) / 15);
        double eqt = fixHour(q / 15 - ra + 12) - 12;
        double decl = Math.toDegrees(Math.asin(sin(e) * sin(l)));
        return new double[] {decl, eqt};
    }

    private static double julian(int y, int m, int d) {
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    private static double sin(double deg) {
        return Math.sin(Math.toRadians(deg));
    }

    private static double cos(double deg) {
        return Math.cos(Math.toRadians(deg));
    }

    private static double fixAngle(double a) {
        return a - 360 * Math.floor(a / 360);
    }

    private static double fixHour(double h) {
        return h - 24 * Math.floor(h / 24);
    }
}
