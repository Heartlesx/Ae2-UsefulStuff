package com.extrarawstyle.ae2us.alarm;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 阈值文本解析：纯数字或 数字+K/M/B/G/T/P 简写（大小写均可、支持小数），结果最小 1、上限 long。 */
public final class ThresholdText {
    private ThresholdText() { }

    /** 返回合法阈值；空串或非法格式返回 null（调用方保留原值）。0 一律自动抬到 1。 */
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
            return Math.max(1L, value);
        } catch (final ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
