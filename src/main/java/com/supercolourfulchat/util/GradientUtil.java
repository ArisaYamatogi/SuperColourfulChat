package com.supercolourfulchat.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class GradientUtil {

    // 裸 §x（后随不足 6 个 §hex）是 EssentialsChat 剥色残留，不算手动码；完整 §x hex 才算。
    private static final Pattern MANUAL_CODES = Pattern.compile("[&§](?:[0-9a-fk-orA-FK-OR]|x(?=(?:§[0-9a-fA-F]){6})|#[0-9a-fA-F]{6})|<#[0-9a-fA-F]{6}>");
    private static final Pattern DANGLING_HEX = Pattern.compile("§x(?!(?:§[0-9a-fA-F]){6})");
    private static final Pattern HEX_SEQUENCE = Pattern.compile("§x(?:§[0-9a-fA-F]){6}");
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

    public static String stripDanglingHex(String message) {
        return message == null ? "" : DANGLING_HEX.matcher(message).replaceAll("");
    }

    public static boolean containsHex(String message) {
        return message != null && HEX_SEQUENCE.matcher(message).find();
    }

    public static String plainProjection(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return "";
        }
        StringBuilder plain = new StringBuilder(legacy.length());
        buildPlainIndex(legacy, plain);
        return plain.toString();
    }

    // 只检查渲染输出中"消息正文"部分是否已有完整 §x hex，避免前缀/名字里的颜色造成误判。
    public static boolean renderedMessageHasHex(String rendered, String plainMessage) {
        if (rendered == null || rendered.isEmpty()) {
            return false;
        }
        if (plainMessage == null || plainMessage.isEmpty()) {
            return containsHex(rendered);
        }
        StringBuilder plain = new StringBuilder(rendered.length());
        int[] map = buildPlainIndex(rendered, plain);
        int pos = plain.lastIndexOf(plainMessage);
        if (pos < 0) {
            return containsHex(rendered);
        }
        return containsHex(rendered.substring(map[pos]));
    }

    // 把渲染输出中的消息正文替换为新渐变，保留格式前缀/后缀；找不到正文时返回 null。
    public static String recolorMessagePortion(String rendered, String plainMessage, int startRgb, int endRgb) {
        if (rendered == null || plainMessage == null || plainMessage.isEmpty()) {
            return null;
        }
        StringBuilder plain = new StringBuilder(rendered.length());
        int[] map = buildPlainIndex(rendered, plain);
        int pos = plain.lastIndexOf(plainMessage);
        if (pos < 0) {
            return null;
        }
        int from = map[pos];
        int to = map[pos + plainMessage.length()];
        return rendered.substring(0, from) + gradient(plainMessage, startRgb, endRgb) + rendered.substring(to);
    }

    // 逐字符扫描 legacy 串：完整 §x hex 跳 14 字符，其余 §c 跳 2 字符；map[k] = 第 k 个纯文本字符在 legacy 串中的起点。
    private static int[] buildPlainIndex(String legacy, StringBuilder plainOut) {
        int[] map = new int[legacy.length() + 1];
        int i = 0;
        while (i < legacy.length()) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                if (legacy.charAt(i + 1) == 'x' && isFullHexAt(legacy, i)) {
                    i += 14;
                } else {
                    i += 2;
                }
                continue;
            }
            map[plainOut.length()] = i;
            plainOut.append(c);
            i++;
        }
        map[plainOut.length()] = legacy.length();
        return map;
    }

    private static boolean isFullHexAt(String s, int i) {
        if (i + 13 >= s.length()) {
            return false;
        }
        for (int k = 0; k < 6; k++) {
            int base = i + 2 + k * 2;
            if (s.charAt(base) != '§') {
                return false;
            }
            char h = s.charAt(base + 1);
            if (!((h >= '0' && h <= '9') || (h >= 'a' && h <= 'f') || (h >= 'A' && h <= 'F'))) {
                return false;
            }
        }
        return true;
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
        int n = message.codePointCount(0, message.length());
        int denom = Math.max(1, n - 1);
        int sr = (startRgb >> 16) & 0xFF;
        int sg = (startRgb >> 8) & 0xFF;
        int sb = startRgb & 0xFF;
        int dr = ((endRgb >> 16) & 0xFF) - sr;
        int dg = ((endRgb >> 8) & 0xFF) - sg;
        int db = (endRgb & 0xFF) - sb;
        StringBuilder out = new StringBuilder(message.length() * 15);
        int i = 0;
        for (int idx = 0; idx < message.length(); ) {
            int codePoint = message.codePointAt(idx);
            double t = (double) i / denom;
            int r = sr + (int) Math.round(dr * t);
            int g = sg + (int) Math.round(dg * t);
            int b = sb + (int) Math.round(db * t);
            appendHexColor(out, (r << 16) | (g << 8) | b);
            out.appendCodePoint(codePoint);
            idx += Character.charCount(codePoint);
            i++;
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