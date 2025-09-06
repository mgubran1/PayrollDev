package com.company.payroll.drivergrid;

import com.company.payroll.loads.Load;
import java.util.EnumMap;
import java.util.Map;

/**
 * Utility for mapping {@link Load.Status} values to display attributes.
 */
public final class LoadStatusUtil {
    private static final Map<Load.Status, String> STATUS_COLORS = new EnumMap<>(Load.Status.class);
    private static final Map<Load.Status, String> STATUS_ICONS = new EnumMap<>(Load.Status.class);

    static {
        // Updated with lighter color variants for better contrast with black text
        STATUS_COLORS.put(Load.Status.BOOKED, "#C7D9F4");     // Light blue - good contrast with black text
        STATUS_COLORS.put(Load.Status.ASSIGNED, "#FFE0B2");   // Light amber - good contrast with black text
        STATUS_COLORS.put(Load.Status.IN_TRANSIT, "#B2DFDB");  // Light teal - good contrast with black text
        STATUS_COLORS.put(Load.Status.DELIVERED, "#A5D6A7");   // Light green - good contrast with black text
        STATUS_COLORS.put(Load.Status.PAID, "#D4C4E9");        // Light purple - good contrast with black text
        STATUS_COLORS.put(Load.Status.CANCELLED, "#FFCDD2");   // Light red - good contrast with black text
        STATUS_COLORS.put(Load.Status.PICKUP_LATE, "#FFCCBC"); // Light orange - good contrast with black text
        STATUS_COLORS.put(Load.Status.DELIVERY_LATE, "#EF9A9A"); // Light red-orange - good contrast with black text

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
        return STATUS_COLORS.getOrDefault(status, "#e5e7eb");
    }

    /**
     * Returns the icon string for the given status.
     */
    public static String iconFor(Load.Status status) {
        return STATUS_ICONS.getOrDefault(status, "");
    }
}
