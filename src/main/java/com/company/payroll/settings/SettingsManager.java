package com.company.payroll.settings;

import java.util.Properties;

/** Minimal application settings manager. */
public class SettingsManager {
    private static final SettingsManager INSTANCE = new SettingsManager();
    private final Properties props = new Properties();

    private SettingsManager() {}

    public static SettingsManager getInstance() { return INSTANCE; }

    public boolean getBooleanSetting(String key, boolean defaultValue) {
        String v = props.getProperty(key);
        return v != null ? Boolean.parseBoolean(v) : defaultValue;
    }

    public int getIntSetting(String key, int defaultValue) {
        String v = props.getProperty(key);
        if (v != null) {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
