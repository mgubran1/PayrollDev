package com.company.payroll.payroll;

import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;

/**
 * Modern button styling utility for Payroll components
 * Provides consistent, professional button designs with hover effects
 */
public class ModernButtonStyles {
    
    // Base style constants
    private static final String BASE_BUTTON_STYLE = 
        "-fx-font-weight: bold; " +
        "-fx-cursor: hand; " +
        "-fx-background-radius: 8; " +
        "-fx-border-radius: 8; " +
        "-fx-padding: 10 16 10 16; " +
        "-fx-font-size: 13px; " +
        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 4, 0, 0, 2);";
    
    // Color scheme constants
    public static class Colors {
        public static final String PRIMARY = "#2563eb";         // Blue
        public static final String PRIMARY_HOVER = "#1d4ed8";
        public static final String SUCCESS = "#059669";         // Green  
        public static final String SUCCESS_HOVER = "#047857";
        public static final String WARNING = "#d97706";         // Orange
        public static final String WARNING_HOVER = "#b45309";
        public static final String DANGER = "#dc2626";          // Red
        public static final String DANGER_HOVER = "#b91c1c";
        public static final String SECONDARY = "#6b7280";       // Gray
        public static final String SECONDARY_HOVER = "#4b5563";
        public static final String INFO = "#0891b2";           // Cyan
        public static final String INFO_HOVER = "#0e7490";
    }
    
    /**
     * Create a modern primary button (blue theme)
     */
    public static Button createPrimaryButton(String text) {
        Button button = new Button(text);
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.PRIMARY + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        // Hover effects
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.PRIMARY_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
        return button;
    }
    
    /**
     * Create a modern success button (green theme)
     */
    public static Button createSuccessButton(String text) {
        Button button = new Button(text);
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.SUCCESS + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.SUCCESS_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
        return button;
    }
    
    /**
     * Create a modern warning button (orange theme)
     */
    public static Button createWarningButton(String text) {
        Button button = new Button(text);
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.WARNING + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.WARNING_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
        return button;
    }
    
    /**
     * Create a modern danger button (red theme)
     */
    public static Button createDangerButton(String text) {
        Button button = new Button(text);
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.DANGER + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.DANGER_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
        return button;
    }
    
    /**
     * Create a modern secondary button (gray theme)
     */
    public static Button createSecondaryButton(String text) {
        Button button = new Button(text);
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.SECONDARY + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.SECONDARY_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
        return button;
    }
    
    /**
     * Create a modern info button (cyan theme)
     */
    public static Button createInfoButton(String text) {
        Button button = new Button(text);
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.INFO + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.INFO_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
        return button;
    }
    
    /**
     * Create a modern outline button (white background with colored border)
     */
    public static Button createOutlineButton(String text, String color, String hoverColor) {
        Button button = new Button(text);
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: white; " +
            "-fx-text-fill: " + color + "; " +
            "-fx-border-color: " + color + "; " +
            "-fx-border-width: 2;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + color + "; " +
                "-fx-text-fill: white; " +
                "-fx-border-color: " + hoverColor + "; " +
                "-fx-border-width: 2; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
        return button;
    }
    
    /**
     * Apply modern styling to an existing button
     */
    public static void applyPrimaryStyle(Button button) {
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.PRIMARY + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.PRIMARY_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
    }
    
    /**
     * Apply modern secondary styling to an existing button
     */
    public static void applySecondaryStyle(Button button) {
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.SECONDARY + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.SECONDARY_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
    }
    
    /**
     * Apply modern success styling to an existing button
     */
    public static void applySuccessStyle(Button button) {
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.SUCCESS + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.SUCCESS_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
    }
    
    /**
     * Apply modern danger styling to an existing button
     */
    public static void applyDangerStyle(Button button) {
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.DANGER + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.DANGER_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
    }
    
    /**
     * Apply modern warning styling to an existing button
     */
    public static void applyWarningStyle(Button button) {
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.WARNING + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.WARNING_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
    }
    
    /**
     * Apply modern info styling to an existing button
     */
    public static void applyInfoStyle(Button button) {
        String style = BASE_BUTTON_STYLE + 
            "-fx-background-color: " + Colors.INFO + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = BASE_BUTTON_STYLE + 
                "-fx-background-color: " + Colors.INFO_HOVER + "; " +
                "-fx-text-fill: white; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 3);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
    }
    
    /**
     * Create a compact button for table actions
     */
    public static Button createCompactButton(String text, String color, String hoverColor) {
        Button button = new Button(text);
        String style = 
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand; " +
            "-fx-background-radius: 6; " +
            "-fx-border-radius: 6; " +
            "-fx-padding: 6 12 6 12; " +
            "-fx-font-size: 11px; " +
            "-fx-background-color: " + color + "; " +
            "-fx-text-fill: white;";
        button.setStyle(style);
        
        button.setOnMouseEntered(e -> {
            String hoverStyle = style.replace(color, hoverColor) +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);";
            button.setStyle(hoverStyle);
        });
        
        button.setOnMouseExited(e -> button.setStyle(style));
        return button;
    }
}