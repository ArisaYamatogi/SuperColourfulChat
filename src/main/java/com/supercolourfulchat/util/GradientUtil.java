package com.supercolourfulchat.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class GradientUtil {

    private static final Pattern MANUAL_CODES = Pattern.compile("[&§](?:[0-9a-fk-orA-FK-OR]|x|#[0-9a-fA-F]{6})|<#[0-9a-fA-F]{6}>");
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final long MIN_DISTANCE_SQ = 128L * 128L;
    private static final int MAX_END_REROLLS = 8;

    private GradientUtil() {
    }

    public static boolean hasManualCodes(String message) {
        return message != null && MANUAL_CODES.matcher(message).find();
    }

    public static int randomColor() {
        return ThreadLocalRandom.current().nextInt(0x1000000);
    }

    public static int randomEndColor(int startRgb) {
        int end = randomColor();
        for (int i = 0; i < MAX_END_REROLLS && distanceSq(startRgb, end) < MIN_DISTANCE_SQ; i++) {
            end = randomColor();
        }
        return end;
    }

    public static String gradient(String message) {
        int start = randomColor();
        return gradient(message, start, randomEndColor(start));
    }

    public static String gradient(String message, int startRgb, int endRgb) {
        if (message == null || message.isEmpty()) {
            return message == null ? "" : message;
        }
        int n = message.length();
        int denom = Math.max(1, n - 1);
        int sr = (startRgb >> 16) & 0xFF;
        int sg = (startRgb >> 8) & 0xFF;
        int sb = startRgb & 0xFF;
        int dr = ((endRgb >> 16) & 0xFF) - sr;
        int dg = ((endRgb >> 8) & 0xFF) - sg;
        int db = (endRgb & 0xFF) - sb;
        StringBuilder out = new StringBuilder(n * 15);
        for (int i = 0; i < n; i++) {
            double t = (double) i / denom;
            int r = sr + (int) Math.round(dr * t);
            int g = sg + (int) Math.round(dg * t);
            int b = sb + (int) Math.round(db * t);
            appendHexColor(out, (r << 16) | (g << 8) | b);
            out.append(message.charAt(i));
        }
        return out.toString();
    }

    public static char nearestLegacyCode(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int c = LEGACY_RGB[i];
            long dist = distanceSqChannels(r, g, b, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return HEX[best];
    }

    public static String downgrade(String message, int firstRgb) {
        return "§" + nearestLegacyCode(firstRgb) + (message == null ? "" : message);
    }

    private static void appendHexColor(StringBuilder out, int rgb) {
        out.append('§').append('x');
        for (int shift = 20; shift >= 0; shift -= 4) {
            out.append('§').append(HEX[(rgb >> shift) & 0xF]);
        }
    }

    private static long distanceSq(int a, int b) {
        return distanceSqChannels((a >> 16) & 0xFF, (a >> 8) & 0xFF, a & 0xFF,
                (b >> 16) & 0xFF, (b >> 8) & 0xFF, b & 0xFF);
    }

    private static long distanceSqChannels(int r1, int g1, int b1, int r2, int g2, int b2) {
        long dr = r1 - r2;
        long dg = g1 - g2;
        long db = b1 - b2;
        return dr * dr + dg * dg + db * db;
    }
}