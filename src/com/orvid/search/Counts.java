package com.orvid.search;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Counts {

    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:[.,]\\d+)*)\\s*([\\p{L}]*)");
    private static final Pattern AGE = Pattern.compile("(\\d+)\\s*(\\p{L}+)");

    private Counts() {
    }

    public static long views(String text, String language) {
        if (text == null) return -1;
        Matcher m = NUMBER.matcher(text.replace(' ', ' '));
        if (!m.find()) return -1;
        String digits = m.group(1);
        String suffix = m.group(2).toLowerCase(Locale.ROOT);
        long mult = multiplier(suffix, language);
        if (mult == 1) return Long.parseLong(digits.replaceAll("[.,]", ""));
        double value = Double.parseDouble(digits.replace(',', '.').replaceAll("\\.(?=.*\\.)", ""));
        return (long) (value * mult);
    }

    private static long multiplier(String s, String language) {
        if (s.isEmpty()) return 1;
        boolean tr = "tr".equals(language);
        if (s.equals("mr") || s.startsWith("milyar") || !tr && (s.equals("b") || s.startsWith("billion"))) return 1_000_000_000L;
        if (s.equals("mn") || s.equals("m") || s.startsWith("milyon") || s.startsWith("million")) return 1_000_000L;
        if (s.equals("k") || s.equals("bn") && !tr || tr && (s.equals("b") || s.equals("bin")) || s.startsWith("thousand")) return 1_000L;
        return 1;
    }

    public static long ageSeconds(String text) {
        if (text == null) return -1;
        Matcher m = AGE.matcher(text.toLowerCase(new Locale("tr")));
        if (!m.find()) return -1;
        long n = Long.parseLong(m.group(1));
        String u = m.group(2);
        long unit;
        if (u.startsWith("san") || u.startsWith("sec")) unit = 1;
        else if (u.startsWith("dak") || u.startsWith("min")) unit = 60;
        else if (u.startsWith("saat") || u.startsWith("hour")) unit = 3600;
        else if (u.startsWith("gün") || u.startsWith("day")) unit = 86400;
        else if (u.startsWith("hafta") || u.startsWith("week")) unit = 604800;
        else if (u.startsWith("ay") || u.startsWith("month")) unit = 2592000;
        else if (u.startsWith("yıl") || u.startsWith("year")) unit = 31536000;
        else return -1;
        return n * unit;
    }
}
