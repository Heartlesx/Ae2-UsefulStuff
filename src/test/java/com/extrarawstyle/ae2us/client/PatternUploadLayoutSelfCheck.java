package com.extrarawstyle.ae2us.client;

/**
 * 样板上传覆盖层的布局几何自检。
 *
 * <p>面板尺寸、行高、图标间距全是算出来的数，改一个数就可能让面板出屏、分页按钮压到行上、
 * 或三个操作图标互相重叠——这些在编译期和普通自检里都看不出来，进游戏才发现。这里直接拿
 * {@link PatternUploadClient#geometry} 的快照在所有常见 GUI 尺寸上验算。</p>
 */
public final class PatternUploadLayoutSelfCheck {

    private static final int MARGIN = 10;
    private static final int BUTTON = 20;
    private static final int MIN_REAL_WIDTH = 320;
    private static final int MIN_REAL_HEIGHT = 240;

    private PatternUploadLayoutSelfCheck() {
    }

    public static void main(final String[] args) {
        int narrowText = 0;
        for (int width = MIN_REAL_WIDTH; width <= 1920; width += 10) {
            for (int height = MIN_REAL_HEIGHT; height <= 1080; height += 10) {
                for (final boolean mapping : new boolean[]{false, true}) {
                    final int[] g = PatternUploadClient.geometry(width, height, mapping);
                    final int x = g[0];
                    final int y = g[1];
                    final int w = g[2];
                    final int h = g[3];
                    final int rows = g[4];
                    final int listY = g[5];
                    final int footerY = g[6];
                    final String where = width + "x" + height + (mapping ? " mapping" : " main");

                    check(x >= MARGIN && x + w <= width - MARGIN, "panel fits horizontally: " + where);
                    check(y >= MARGIN && y + h <= height - MARGIN, "panel fits vertically: " + where);
                    check(footerY + BUTTON <= y + h, "pager stays inside the panel: " + where);
                    check(listY + rows * 30 <= y + h - 20, "list clears the pager: " + where);

                    // 三个图标互不重叠，且末尾那个不越过行右缘。
                    check(g[8] > g[7] && g[9] > g[8], "row icons are ordered: " + where);
                    check(g[7] + BUTTON <= g[8] && g[8] + BUTTON <= g[9],
                            "row icons never overlap: " + where);
                    check(g[9] + BUTTON <= x + w - 6, "locate icon stays inside the row: " + where);

                    if (g[10] < 120) {
                        narrowText++;
                    }
                }
            }
        }
        check(narrowText == 0, "row text keeps a usable width on every supported GUI size");

        // 关键尺寸的具体值，防止"能跑但难看"的静默回归。
        final int[] normal = PatternUploadClient.geometry(427, 240, false);
        check(normal[2] == 320, "main panel is 320px wide on a normal GUI");
        check(normal[3] == 200, "main panel holds 4 rows at 240px tall");
        final int[] mapping = PatternUploadClient.geometry(427, 240, true);
        check(mapping[2] == 360, "mapping panel is 360px wide on a normal GUI");

        // 上界：高分辨率下不会无限加行。
        final int[] tall = PatternUploadClient.geometry(1920, 1080, false);
        check(tall[4] == 6, "row count is capped at 6");

        textCenteringChecks();

        System.out.println("Pattern upload layout checks passed "
                + "(panel bounds, pager clearance, row icon spacing, text width across 320x240..1920x1080)");
    }

    /**
     * 输入框文字必须垂直居中。
     *
     * <p>GuiTextField.drawTextBox 里那段是：开启自带背景才用 {@code x+4 / y+(height-8)/2} 居中，
     * 关掉背景（我们为了自绘凹槽）就直接退化成 {@code x / y}——文字贴顶贴边。绘制时必须临时把
     * 坐标偏到居中位置。这条以前真翻过车：文字顶在框上沿，下面空一大截。</p>
     */
    private static void textCenteringChecks() {
        final int height = 16;
        final int padX = PatternUploadClient.inputTextPadX();
        final int padY = PatternUploadClient.inputTextPadY(height);
        check(padX == 4, "文字左侧内缩 4px（与 GuiTextField 自身的一致）");
        check(padY > 0, "16px 高的输入框，文字上下都该有留白（0 就是又贴顶了）");
        check(height - 8 - padY == padY, "文字上下留白必须相等才算居中");
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
