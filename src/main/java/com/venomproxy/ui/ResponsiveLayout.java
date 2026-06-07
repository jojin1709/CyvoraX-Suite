package com.venomproxy.ui;

public final class ResponsiveLayout {
    private ResponsiveLayout() {
    }

    public static int cardColumns(double width) {
        if (width >= 2400) {
            return 6;
        }
        if (width >= 1800) {
            return 5;
        }
        if (width >= 1280) {
            return 4;
        }
        if (width >= 980) {
            return 3;
        }
        return 2;
    }

    public static int panelColumns(double width) {
        if (width >= 1800) {
            return 3;
        }
        if (width >= 1180) {
            return 2;
        }
        return 1;
    }

    public static double tileWidth(double containerWidth, int columns, double gap, double minWidth) {
        int safeColumns = Math.max(1, columns);
        double available = Math.max(minWidth, containerWidth - (gap * (safeColumns - 1)));
        return Math.max(minWidth, Math.floor(available / safeColumns));
    }
}
