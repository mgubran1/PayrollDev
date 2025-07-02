package com.company.payroll.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Utility helpers for date/time formatting. */
public class DateTimeUtils {
    public static String format(LocalDateTime time, String pattern) {
        return time != null ? time.format(DateTimeFormatter.ofPattern(pattern)) : "";
    }
}
