package com.company.payroll;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.company.payroll.database.DatabaseConfig;
import com.company.payroll.loads.EnhancedAutocompleteField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String APP_TITLE = "Payroll Desktop v0.1.0";
    private MainController mainController;
    
    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("=== Starting Payroll Desktop Application ===");
            logger.info("JavaFX Version: {}", System.getProperty("javafx.version"));
            logger.info("Java Version: {}", System.getProperty("java.version"));
            logger.info("OS: {} {} {}", System.getProperty("os.name"), 
                       System.getProperty("os.version"), System.getProperty("os.arch"));
            logger.info("User: {}", System.getProperty("user.name"));
            
            // Initialize directories
            initializeDirectories();
            
            // Create the main controller
            mainController = new MainController();
            
            logger.debug("Creating scene with dimensions: 1500x750");
            // Use the mainController directly as the root since it extends BorderPane
            Scene scene = new Scene(mainController, 1500, 750);
            
            logger.debug("Loading stylesheet");
            String stylesheetPath = getClass().getResource("/styles.css").toExternalForm();
            scene.getStylesheets().add(stylesheetPath);
            logger.debug("Stylesheet loaded from: {}", stylesheetPath);

            primaryStage.setTitle(APP_TITLE);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(700);
            primaryStage.setMaximized(true);

            // Register stage with controller for window-aware features
            mainController.setStage(primaryStage);
            
            // Try to load application icon
            try {
                String iconPath = "/icon.png";
                if (getClass().getResource(iconPath) != null) {
                    primaryStage.getIcons().add(new Image(getClass().getResourceAsStream(iconPath)));
                    logger.debug("Application icon loaded");
                }
            } catch (Exception e) {
                logger.warn("Could not load application icon: {}", e.getMessage());
            }
            
            // Log window events
            primaryStage.setOnShown(e -> logger.info("Application window shown"));
            primaryStage.setOnCloseRequest(e -> {
                logger.info("Application close requested");
                // Consume the event to handle custom shutdown process
                e.consume();
                if (mainController != null) {
                    mainController.handleApplicationExit(primaryStage);
                }
            });
            primaryStage.setOnHidden(e -> logger.info("Application window hidden"));
            
            logger.debug("Showing primary stage");
            primaryStage.show();
            
            logger.info("=== Application started successfully ===");
            logger.info("Window dimensions: {}x{}", primaryStage.getWidth(), primaryStage.getHeight());
        } catch (Exception e) {
            logger.error("Failed to start application: {}", e.getMessage(), e);
            showErrorDialog("Application Error", "Failed to start the application:\n" + e.getMessage());
            throw e;
        }
    }
    
    private void initializeDirectories() {
        try {
            // Create necessary directories
            String[] directories = {
                "logs",
                "load_documents",
                "exports",
                "backups"
            };
            
            for (String dir : directories) {
                Path path = Paths.get(dir);
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                    logger.info("Created directory: {}", dir);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to initialize directories", e);
        }
    }
    
    private void showErrorDialog(String title, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("An error occurred");
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    @Override
    public void init() throws Exception {
        logger.info("Application init() called");
        super.init();
    }
    
    @Override
    public void stop() throws Exception {
        logger.info("Application stop() called - shutting down");
        
        // Shutdown main controller
        if (mainController != null) {
            mainController.shutdown();
        }
        
        // Shutdown database connection pool
        try {
            DatabaseConfig.shutdown();
            logger.info("Database connection pool shut down successfully");
        } catch (Exception e) {
            logger.error("Error shutting down database connection pool", e);
        }
        
        // Shutdown autocomplete field executor
        try {
            EnhancedAutocompleteField.shutdown();
            logger.info("Autocomplete field executor shut down successfully");
        } catch (Exception e) {
            logger.error("Error shutting down autocomplete field executor", e);
        }
        
        super.stop();
        logger.info("Application shutdown complete");
    }

    public static void main(String[] args) {
        logger.info("Main method started with {} arguments", args.length);
        if (args.length > 0) {
            logger.info("Arguments: {}", String.join(", ", args));
        }
        
        // Set system properties before launching
        logger.debug("Setting system properties for better rendering");
        System.setProperty("prism.lcdtext", "false"); // Better text rendering
        System.setProperty("prism.text", "t2k"); // Better font rendering
        System.setProperty("file.encoding", "UTF-8"); // Ensure UTF-8 encoding
        
        // Set default locale
        Locale.setDefault(Locale.US);
        
        logger.debug("System properties set - prism.lcdtext=false, prism.text=t2k, file.encoding=UTF-8");
        
        logger.info("Launching JavaFX application");
        launch(args);
    }
}