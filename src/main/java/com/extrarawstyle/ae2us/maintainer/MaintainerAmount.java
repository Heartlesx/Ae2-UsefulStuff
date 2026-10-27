package com.extrarawstyle.ae2us.maintainer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 维持器数字文本解析：纯数字或 数字+K/M/B/G/T/P 简写（大小写均可、支持小数）。
 * 与报警器的 ThresholdText 不同：0、负数、超 long 一律判为非法并保留原值，不抬成 1、不截断成上限。
 */
public final class MaintainerAmount {
    private MaintainerAmount() { }

    /** 合法且大于 0 时返回数值；空串、0、负数、格式错误、溢出均返回 null。 */
    public static Long parse(final String text) {
        if (text == null) { return null; }
        String body = text.trim();
        if (body.isEmpty()) { return null; }
        final long multiplier;
        final char last = Character.toUpperCase(body.charAt(body.length() - 1));
        switch (last) {
            case 'K': multiplier = 1000L; break;
            case 'M': multiplier = 1000000L; break;
            case 'B': case 'G': multiplier = 1000000000L; break;
            case 'T': multiplier = 1000000000000L; break;
            case 'P': multiplier = 1000000000000000L; break;
            default: multiplier = 1;
        }
        if (multiplier != 1) {
            body = body.substring(0, body.length() - 1).trim();
            if (body.isEmpty()) { return null; }
        }
        if (!body.matches("\\d+(\\.\\d+)?")) { return null; }
        try {
            final long value = new BigDecimal(body).multiply(BigDecimal.valueOf(multiplier))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            return value > 0 ? value : null;
        } catch (final ArithmeticException notRepresentable) {
            return null;
        }
    }

    /** 界面上把已保存的数值写回输入框：整数字符串，不做 K/M 压缩。 */
    public static String format(final long value) {
        return Long.toString(value);
    }

    /** 列表里的紧凑显示：1K、1.5M、2G…；小于 1000 原样显示。 */
    public static String compact(final long value) {
        if (value < 1000) { return Long.toString(value); }
        final long unit;
        final String suffix;
        if (value >= 1000000000000000L) { unit = 1000000000000000L; suffix = "P"; }
        else if (value >= 1000000000000L) { unit = 1000000000000L; suffix = "T"; }
        else if (value >= 1000000000L) { unit = 1000000000L; suffix = "G"; }
        else if (value >= 1000000L) { unit = 1000000L; suffix = "M"; }
        else { unit = 1000L; suffix = "K"; }
        final long whole = value / unit;
        final long decimal = value % unit * 10 / unit;
        return whole + (whole < 100 && decimal > 0 ? "." + decimal : "") + suffix;
    }
}
