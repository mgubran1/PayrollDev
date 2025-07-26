package com.company.payroll.drivergrid;

import com.company.payroll.loads.Load;
import java.util.EnumMap;
import java.util.Map;
import javafx.scene.paint.Color;

/**
 * Utility for mapping {@link Load.Status} values to display attributes.
 */
public final class LoadStatusUtil {
    private static final Map<Load.Status, String> STATUS_COLORS = new EnumMap<>(Load.Status.class);
    private static final Map<Load.Status, String> STATUS_ICONS = new EnumMap<>(Load.Status.class);

    static {
        STATUS_COLORS.put(Load.Status.BOOKED, "#2563eb");
        STATUS_COLORS.put(Load.Status.ASSIGNED, "#f59e0b");
        STATUS_COLORS.put(Load.Status.IN_TRANSIT, "#10b981");
        STATUS_COLORS.put(Load.Status.DELIVERED, "#059669");
        STATUS_COLORS.put(Load.Status.PAID, "#7c3aed");
        STATUS_COLORS.put(Load.Status.CANCELLED, "#ef4444");
        STATUS_COLORS.put(Load.Status.PICKUP_LATE, "#ff9999");
        STATUS_COLORS.put(Load.Status.DELIVERY_LATE, "#ff6666");

        STATUS_ICONS.put(Load.Status.BOOKED, "📘");
        STATUS_ICONS.put(Load.Status.ASSIGNED, "📋");
        STATUS_ICONS.put(Load.Status.IN_TRANSIT, "🚚");
        STATUS_ICONS.put(Load.Status.DELIVERED, "✅");
        STATUS_ICONS.put(Load.Status.PAID, "💰");
        STATUS_ICONS.put(Load.Status.CANCELLED, "❌");
        STATUS_ICONS.put(Load.Status.PICKUP_LATE, "⚠️");
        STATUS_ICONS.put(Load.Status.DELIVERY_LATE, "🚨");
    }

    private LoadStatusUtil() {}

    /**
     * Returns the color hex string for the given status.
     */
    public static String colorFor(Load.Status status) {
        return STATUS_COLORS.getOrDefault(status, "#9ca3af");
    }

    /**
     * Returns the icon string for the given status.
     */
    public static String iconFor(Load.Status status) {
        return STATUS_ICONS.getOrDefault(status, "");
    }

    /**
     * Convenience method that returns a {@link Color} instance for the given status.
     * This is useful when nodes need a {@link Color} object instead of a hex string.
     */
    public static Color fxColorFor(Load.Status status) {
        return Color.web(colorFor(status));
    }
}
