package com.company.payroll.loads;

import com.company.payroll.employees.Employee;
import com.company.payroll.trailers.Trailer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class LoadConfirmationPreviewDialog {
    private static final Logger logger = LoggerFactory.getLogger(LoadConfirmationPreviewDialog.class);
    
    private final Load load;
    private final Stage dialog;
    private final LoadConfirmationConfig config;
    private final LoadConfirmationGenerator generator;
    private VBox previewContent;
    
    public LoadConfirmationPreviewDialog(Load load) {
        this.load = load;
        this.config = LoadConfirmationConfig.getInstance();
        this.generator = new LoadConfirmationGenerator();
        
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Load Confirmation Preview - Load #" + load.getLoadNumber());
        dialog.setWidth(1000);
        dialog.setHeight(900);
        
        createContent();
    }
    
    private void createContent() {
        VBox root = new VBox(8);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f8f9fa;");
        
        // Create toolbar
        ToolBar toolbar = createToolbar();
        
        // Create preview content
        ScrollPane scrollPane = new ScrollPane();
        previewContent = createPreviewContent();
        scrollPane.setContent(previewContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dee2e6; -fx-border-width: 1;");
        scrollPane.setPrefViewportWidth(850);
        scrollPane.setPrefViewportHeight(700);
        
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        root.getChildren().addAll(toolbar, scrollPane);
        
        Scene scene = new Scene(root);
        dialog.setScene(scene);
    }
    
    private ToolBar createToolbar() {
        ToolBar toolbar = new ToolBar();
        toolbar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");
        
        Button printPdfBtn = new Button("Print PDF");
        printPdfBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16;");
        printPdfBtn.setOnAction(e -> handlePrintPdf());
        
        Button saveAsPdfBtn = new Button("Save as PDF");
        saveAsPdfBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16;");
        saveAsPdfBtn.setOnAction(e -> handleSaveAsPdf());
        
        Button emailBtn = new Button("Email");
        emailBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-padding: 8 16;");
        emailBtn.setDisable(true); // Will be implemented later
        
        Button configBtn = new Button("Configuration");
        configBtn.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white; -fx-padding: 8 16;");
        configBtn.setOnAction(e -> {
            LoadConfirmationConfigDialog configDialog = new LoadConfirmationConfigDialog();
            configDialog.showAndWait();
            // Refresh preview after configuration changes
            previewContent = createPreviewContent();
            ScrollPane scrollPane = (ScrollPane) ((VBox) dialog.getScene().getRoot()).getChildren().get(1);
            scrollPane.setContent(previewContent);
        });
        
        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-padding: 8 16;");
        closeBtn.setOnAction(e -> dialog.close());
        
        // Add spacing between button groups
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        
        toolbar.getItems().addAll(printPdfBtn, saveAsPdfBtn, emailBtn, spacer1, configBtn, spacer2, closeBtn);
        
        return toolbar;
    }
    
    private VBox createPreviewContent() {
        // Letter size paper dimensions: 8.5" x 11" = 612 x 792 points (72 DPI)
        // Convert to pixels for screen display (assuming 96 DPI screen)
        double letterWidthPixels = 612 * (96.0 / 72.0); // ~816 pixels
        double letterHeightPixels = 792 * (96.0 / 72.0); // ~1056 pixels
        
        // Standard margins (1 inch = 72 points = 96 pixels at 96 DPI)
        double marginPixels = 72;
        double contentWidth = letterWidthPixels - (2 * marginPixels); // ~672 pixels
        
        // Main container that represents the page
        VBox pageContainer = new VBox();
        pageContainer.setAlignment(Pos.TOP_CENTER);
        pageContainer.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 4;");
        pageContainer.setMaxWidth(letterWidthPixels);
        pageContainer.setMinWidth(letterWidthPixels);
        pageContainer.setPrefWidth(letterWidthPixels);
        pageContainer.setMinHeight(letterHeightPixels);
        pageContainer.setPrefHeight(letterHeightPixels);
        
        // Content container with margins
        VBox content = new VBox(10);
        content.setPadding(new Insets(marginPixels));
        content.setAlignment(Pos.TOP_LEFT);
        content.setMaxWidth(contentWidth);
        content.setMinWidth(contentWidth);
        content.setPrefWidth(contentWidth);
        
        // Header
        addHeader(content);
        
        // Separator
        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: #dee2e6;");
        VBox.setMargin(separator, new Insets(10, 0, 10, 0));
        content.getChildren().add(separator);
        
        // Main content in a single vertical flow
        addMainContent(content);
        
        // Notes section if available
        if (load.getNotes() != null && !load.getNotes().isEmpty()) {
            addNotesSection(content);
        }
        
        // Policy section
        addPolicySection(content);
        
        // Spacer to push dispatcher info to bottom
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        content.getChildren().add(spacer);
        
        // Dispatcher section
        addDispatcherSection(content);
        
        pageContainer.getChildren().add(content);
        return pageContainer;
    }
    
    private void addHeader(VBox content) {
        // Company name
        String companyName = getCompanyNameFromConfig();
        Label companyLabel = new Label(companyName);
        companyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        companyLabel.setAlignment(Pos.CENTER);
        companyLabel.setStyle("-fx-text-fill: #2c3e50;");
        
        HBox companyBox = new HBox(companyLabel);
        companyBox.setAlignment(Pos.CENTER);
        companyBox.setPadding(new Insets(0, 0, 2, 0));
        content.getChildren().add(companyBox);
        
        // Title
        Label title = new Label("LOAD CONFIRMATION");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setAlignment(Pos.CENTER);
        title.setStyle("-fx-text-fill: #34495e;");
        
        HBox titleBox = new HBox(title);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(0, 0, 8, 0));
        content.getChildren().add(titleBox);
    }
    
    private void addMainContent(VBox content) {
        // Load and Driver Info Section
        VBox loadInfoSection = new VBox(8);
        
        // Two-column layout for basic info
        GridPane basicInfoGrid = new GridPane();
        basicInfoGrid.setHgap(40);
        basicInfoGrid.setVgap(6);
        
        // Configure columns - 60% for left, 40% for right
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(60);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(40);
        basicInfoGrid.getColumnConstraints().addAll(col1, col2);
        
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        Employee driver = load.getDriver();
        
        // Left column
        int leftRow = 0;
        addLabelValuePair(basicInfoGrid, leftRow++, 0, "Load #:", load.getLoadNumber(), true);
        
        String pickupDate = load.getPickUpDate() != null ? load.getPickUpDate().format(dateFormatter) : "TBD";
        addLabelValuePair(basicInfoGrid, leftRow++, 0, "Pickup Date:", pickupDate, false);
        
        String deliveryDate = load.getDeliveryDate() != null ? load.getDeliveryDate().format(dateFormatter) : "TBD";
        addLabelValuePair(basicInfoGrid, leftRow++, 0, "Delivery Date:", deliveryDate, false);
        
        if (config.isShowGrossAmount() && load.getGrossAmount() > 0.0) {
            addLabelValuePair(basicInfoGrid, leftRow++, 0, "Gross Amount:", "$" + String.format("%.2f", load.getGrossAmount()), true);
        }
        
        // Right column
        int rightRow = 0;
        if (driver != null) {
            addLabelValuePair(basicInfoGrid, rightRow++, 1, "Driver:", driver.getName(), true);
            
            String truckUnit = load.getTruckUnitSnapshot() != null ? load.getTruckUnitSnapshot() : 
                             (driver.getTruckUnit() != null ? driver.getTruckUnit() : "N/A");
            addLabelValuePair(basicInfoGrid, rightRow++, 1, "Truck:", truckUnit, false);
            
            Trailer trailer = load.getTrailer();
            String trailerInfo = "N/A";
            if (trailer != null) {
                trailerInfo = trailer.getTrailerNumber();
                if (trailer.getType() != null && !trailer.getType().isEmpty()) {
                    trailerInfo += " (" + trailer.getType() + ")";
                }
            } else if (load.getTrailerNumber() != null && !load.getTrailerNumber().isEmpty()) {
                trailerInfo = load.getTrailerNumber();
            }
            addLabelValuePair(basicInfoGrid, rightRow++, 1, "Trailer:", trailerInfo, false);
            
            if (driver.getPhone() != null && !driver.getPhone().isEmpty()) {
                addLabelValuePair(basicInfoGrid, rightRow++, 1, "Mobile:", driver.getPhone(), false);
            }
        }
        
        loadInfoSection.getChildren().add(basicInfoGrid);
        content.getChildren().add(loadInfoSection);
        
        // Separator
        Separator separator1 = new Separator();
        separator1.setStyle("-fx-background-color: #e0e0e0;");
        VBox.setMargin(separator1, new Insets(10, 0, 10, 0));
        content.getChildren().add(separator1);
        
        // Pickup Information Section
        VBox pickupSection = createLocationSection("PICKUP INFORMATION", 
            load.getCustomer(), 
            load.getPickUpLocation(),
            formatDateTime(load.getPickUpDate(), load.getPickUpTime()),
            load.getPONumber());
        content.getChildren().add(pickupSection);
        
        // Additional pickup locations
        List<LoadLocation> additionalPickups = getAdditionalLocations(LoadLocation.LocationType.PICKUP);
        if (!additionalPickups.isEmpty()) {
            VBox addPickupSection = createAdditionalLocationsSection("Additional Pickup Locations", additionalPickups);
            VBox.setMargin(addPickupSection, new Insets(5, 0, 0, 20));
            content.getChildren().add(addPickupSection);
        }
        
        // Separator
        Separator separator2 = new Separator();
        separator2.setStyle("-fx-background-color: #e0e0e0;");
        VBox.setMargin(separator2, new Insets(10, 0, 10, 0));
        content.getChildren().add(separator2);
        
        // Drop Information Section
        VBox dropSection = createLocationSection("DROP INFORMATION",
            load.getCustomer2() != null ? load.getCustomer2() : load.getCustomer(),
            load.getDropLocation(),
            formatDateTime(load.getDeliveryDate(), load.getDeliveryTime()),
            null);
        content.getChildren().add(dropSection);
        
        // Additional drop locations
        List<LoadLocation> additionalDrops = getAdditionalLocations(LoadLocation.LocationType.DROP);
        if (!additionalDrops.isEmpty()) {
            VBox addDropSection = createAdditionalLocationsSection("Additional Drop Locations", additionalDrops);
            VBox.setMargin(addDropSection, new Insets(5, 0, 0, 20));
            content.getChildren().add(addDropSection);
        }
    }
    
    private VBox createLocationSection(String title, String customer, String address, String dateTime, String poNumber) {
        VBox section = new VBox(5);
        
        // Create header with gray background
        HBox headerBox = new HBox();
        headerBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 3 10;");
        headerBox.setPrefWidth(Region.USE_COMPUTED_SIZE);
        headerBox.setMaxWidth(Double.MAX_VALUE);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        titleLabel.setStyle("-fx-text-fill: black;");
        headerBox.getChildren().add(titleLabel);
        
        section.getChildren().add(headerBox);
        
        // Add double spacing after header
        Region spacer = new Region();
        spacer.setPrefHeight(10);
        section.getChildren().add(spacer);
        
        VBox detailsBox = new VBox(3);
        detailsBox.setPadding(new Insets(0, 0, 0, 20));
        
        if (customer != null && !customer.isEmpty()) {
            HBox customerBox = createDetailRow("Customer:", customer, false);
            detailsBox.getChildren().add(customerBox);
        }
        
        if (address != null && !address.isEmpty()) {
            HBox addressBox = createDetailRow("Address:", address, false);
            detailsBox.getChildren().add(addressBox);
        }
        
        if (dateTime != null && !dateTime.isEmpty()) {
            HBox dateTimeBox = createDetailRow("Date/Time:", dateTime, false);
            detailsBox.getChildren().add(dateTimeBox);
        }
        
        if (poNumber != null && !poNumber.isEmpty()) {
            HBox poBox = createDetailRow("PO #:", poNumber, false);
            detailsBox.getChildren().add(poBox);
        }
        
        section.getChildren().add(detailsBox);
        return section;
    }
    
    private HBox createDetailRow(String label, String value, boolean bold) {
        HBox row = new HBox(5);
        
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        labelNode.setStyle("-fx-text-fill: #2c3e50;");
        labelNode.setMinWidth(80);
        
        Label valueNode = new Label(value != null ? value : "");
        if (bold) {
            valueNode.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            valueNode.setStyle("-fx-text-fill: #27ae60;");
        } else {
            valueNode.setFont(Font.font("Arial", 10));
            valueNode.setStyle("-fx-text-fill: #34495e;");
        }
        valueNode.setWrapText(true);
        valueNode.setMaxWidth(500);
        
        row.getChildren().addAll(labelNode, valueNode);
        return row;
    }
    
    private VBox createAdditionalLocationsSection(String title, List<LoadLocation> locations) {
        VBox section = new VBox(3);
        
        Label titleLabel = new Label(title + ":");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        titleLabel.setStyle("-fx-text-fill: #2c3e50;");
        section.getChildren().add(titleLabel);
        
        VBox locationsList = new VBox(2);
        locationsList.setPadding(new Insets(0, 0, 0, 15));
        
        for (int i = 0; i < locations.size(); i++) {
            LoadLocation loc = locations.get(i);
            String info = String.format("%d. %s - %s", 
                i + 1, 
                loc.getCustomer() != null ? loc.getCustomer() : "",
                formatLocationAddress(loc));
            
            if (loc.getTime() != null) {
                info += " @ " + loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a"));
            }
            
            Label locLabel = new Label(info);
            locLabel.setFont(Font.font("Arial", 9));
            locLabel.setStyle("-fx-text-fill: #34495e;");
            locLabel.setWrapText(true);
            locationsList.getChildren().add(locLabel);
            
            if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                Label notesLabel = new Label("   Notes: " + loc.getNotes());
                notesLabel.setFont(Font.font("Arial", 8));
                notesLabel.setStyle("-fx-text-fill: #7f8c8d;");
                notesLabel.setWrapText(true);
                locationsList.getChildren().add(notesLabel);
            }
        }
        
        section.getChildren().add(locationsList);
        return section;
    }
    
    private List<LoadLocation> getAdditionalLocations(LoadLocation.LocationType type) {
        if (load.getLocations() == null) {
            return new ArrayList<>();
        }
        
        return load.getLocations().stream()
            .filter(loc -> loc.getType() == type)
            .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    private void addLabelValuePair(GridPane grid, int row, int col, String label, String value, boolean bold) {
        HBox pairBox = createDetailRow(label, value, bold);
        grid.add(pairBox, col, row);
    }
    
    private void addGridCell(GridPane grid, int row, int col, String label, String value, boolean bold) {
        if (label.isEmpty()) {
            // Just value
            Label valueNode = new Label(value != null ? value : "");
            if (bold) {
                valueNode.setFont(Font.font("Arial", FontWeight.BOLD, 9));
                valueNode.setStyle("-fx-text-fill: #27ae60;");
            } else {
                valueNode.setFont(Font.font("Arial", 9));
                valueNode.setStyle("-fx-text-fill: #34495e;");
            }
            grid.add(valueNode, col, row);
        } else {
            // Label and value
            Label labelNode = new Label(label);
            labelNode.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            labelNode.setStyle("-fx-text-fill: #2c3e50;");
            
            Label valueNode = new Label(value != null ? value : "");
            if (bold) {
                valueNode.setFont(Font.font("Arial", FontWeight.BOLD, 9));
                valueNode.setStyle("-fx-text-fill: #27ae60;");
            } else {
                valueNode.setFont(Font.font("Arial", 9));
                valueNode.setStyle("-fx-text-fill: #34495e;");
            }
            
            grid.add(labelNode, col, row);
            grid.add(valueNode, col + 1, row);
        }
    }
    
    private void addCompanyLogo(VBox content) {
        String logoPath = config.getCompanyLogoPath();
        if (logoPath != null && !logoPath.isEmpty()) {
            File logoFile = new File(logoPath);
            if (logoFile.exists()) {
                try {
                    Image image = new Image(logoFile.toURI().toString());
                    ImageView imageView = new ImageView(image);
                    imageView.setPreserveRatio(true);
                    imageView.setFitHeight(50); // Reduced logo size
                    
                    HBox logoBox = new HBox(imageView);
                    logoBox.setAlignment(Pos.CENTER);
                    logoBox.setPadding(new Insets(0, 0, 5, 0)); // Reduced padding
                    content.getChildren().add(logoBox);
                } catch (Exception e) {
                    logger.error("Error loading logo: {}", e.getMessage());
                }
            }
        }
    }
    
    private void addNotesSection(VBox content) {
        VBox section = new VBox(5);
        VBox.setMargin(section, new Insets(10, 0, 10, 0));
        
        // Create header with gray background
        HBox headerBox = new HBox();
        headerBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 3 10;");
        headerBox.setPrefWidth(Region.USE_COMPUTED_SIZE);
        headerBox.setMaxWidth(Double.MAX_VALUE);
        
        Label title = new Label("NOTES");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        title.setStyle("-fx-text-fill: black;");
        headerBox.getChildren().add(title);
        
        // Add double spacing after header
        Region spacer = new Region();
        spacer.setPrefHeight(10);
        
        Label notesLabel = new Label(load.getNotes());
        notesLabel.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        notesLabel.setStyle("-fx-text-fill: #e74c3c;");
        notesLabel.setWrapText(true);
        notesLabel.setMaxWidth(600);
        
        VBox notesBox = new VBox(notesLabel);
        notesBox.setPadding(new Insets(0, 0, 0, 20));
        
        section.getChildren().addAll(headerBox, spacer, notesBox);
        content.getChildren().add(section);
    }
    
    private void addPolicySection(VBox content) {
        String policy = config.getPickupDeliveryPolicy();
        if (policy != null && !policy.isEmpty()) {
            VBox section = new VBox(5);
            VBox.setMargin(section, new Insets(15, 0, 10, 0));
            
            // Create header with gray background
            HBox headerBox = new HBox();
            headerBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 3 10;");
            headerBox.setPrefWidth(Region.USE_COMPUTED_SIZE);
            headerBox.setMaxWidth(Double.MAX_VALUE);
            
            Label title = new Label("PICKUP AND DELIVERY POLICY");
            title.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            title.setStyle("-fx-text-fill: black;");
            headerBox.getChildren().add(title);
            
            // Add double spacing after header
            Region spacer = new Region();
            spacer.setPrefHeight(10);
            
            Label policyLabel = new Label(policy);
            policyLabel.setFont(Font.font("Arial", 8));
            policyLabel.setStyle("-fx-text-fill: #34495e;");
            policyLabel.setWrapText(true);
            policyLabel.setMaxWidth(600);
            
            VBox policyBox = new VBox(policyLabel);
            policyBox.setPadding(new Insets(0, 0, 0, 20));
            
            section.getChildren().addAll(headerBox, spacer, policyBox);
            content.getChildren().add(section);
        }
    }
    
    private void addDispatcherSection(VBox content) {
        VBox dispatcherSection = new VBox(3);
        dispatcherSection.setAlignment(Pos.CENTER);
        dispatcherSection.setPadding(new Insets(10, 0, 0, 0));
        
        Label title = new Label("DISPATCHER INFORMATION");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 8));
        title.setStyle("-fx-text-fill: #2c3e50;");
        
        StringBuilder info = new StringBuilder();
        if (!config.getDispatcherName().isEmpty()) {
            info.append("Name: ").append(config.getDispatcherName());
        }
        if (!config.getDispatcherPhone().isEmpty()) {
            if (info.length() > 0) info.append(" | ");
            info.append("Phone: ").append(config.getDispatcherPhone());
        }
        if (!config.getDispatcherEmail().isEmpty()) {
            if (info.length() > 0) info.append(" | ");
            info.append("Email: ").append(config.getDispatcherEmail());
        }
        if (!config.getDispatcherFax().isEmpty()) {
            if (info.length() > 0) info.append(" | ");
            info.append("Fax: ").append(config.getDispatcherFax());
        }
        
        Label infoLabel = new Label(info.toString());
        infoLabel.setFont(Font.font("Arial", 7));
        infoLabel.setStyle("-fx-text-fill: #34495e;");
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(600);
        
        dispatcherSection.getChildren().addAll(title, infoLabel);
        content.getChildren().add(dispatcherSection);
    }
    
    private VBox createSection(String title) {
        VBox section = new VBox(6);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        titleLabel.setStyle("-fx-text-fill: #2c3e50; -fx-background-color: #ecf0f1; -fx-padding: 5 10; -fx-background-radius: 3;");
        
        section.getChildren().add(titleLabel);
        return section;
    }
    
    private void addInfoRow(GridPane grid, int row, String label, String value) {
        addInfoRow(grid, row, label, value, 10);
    }
    
    private String formatDateTime(LocalDate date, LocalTime time) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");
        
        String result = "";
        if (date != null) {
            result = date.format(dateFormatter);
            if (time != null) {
                result += " @ " + time.format(timeFormatter);
            }
        } else {
            result = "TBD";
        }
        
        return result;
    }
    
    private void handlePrintPdf() {
        try {
            // Generate PDF in memory
            PDDocument document = generator.generateLoadConfirmation(load);
            
            // Create temporary file with proper extension
            File tempFile = File.createTempFile("LoadConfirmation_" + load.getLoadNumber() + "_", ".pdf");
            document.save(tempFile);
            document.close();
            
            // Verify the file was created successfully
            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new IOException("Failed to create temporary PDF file");
            }
            
            // Try multiple printing approaches
            boolean printSuccess = false;
            
            // Method 1: Try direct printing
            if (java.awt.Desktop.isDesktopSupported()) {
                try {
                    java.awt.Desktop.getDesktop().print(tempFile);
                    printSuccess = true;
                    logger.info("Direct printing successful");
                } catch (IOException printException) {
                    logger.warn("Direct printing failed: {}", printException.getMessage());
                }
            }
            
            // Method 2: If direct printing failed, try opening with default app
            if (!printSuccess) {
                try {
                    // Try to open with default PDF viewer
                    java.awt.Desktop.getDesktop().open(tempFile);
                    printSuccess = true;
                    logger.info("Opened PDF in default viewer");
                } catch (IOException openException) {
                    logger.warn("Failed to open PDF: {}", openException.getMessage());
                }
            }
            
            // Method 3: If both failed, try using system print command
            if (!printSuccess) {
                try {
                    printSuccess = printUsingSystemCommand(tempFile);
                    if (printSuccess) {
                        logger.info("System print command successful");
                    }
                } catch (Exception cmdException) {
                    logger.warn("System print command failed: {}", cmdException.getMessage());
                }
            }
            
            if (printSuccess) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Print PDF");
                alert.setHeaderText(null);
                alert.setContentText("Load confirmation PDF sent to printer successfully!");
                alert.showAndWait();
            } else {
                // Final fallback: Save to desktop and inform user
                File desktopFile = new File(System.getProperty("user.home") + "/Desktop/LoadConfirmation_" + load.getLoadNumber() + ".pdf");
                document = generator.generateLoadConfirmation(load);
                document.save(desktopFile);
                document.close();
                
                Alert fallbackAlert = new Alert(Alert.AlertType.INFORMATION);
                fallbackAlert.setTitle("PDF Saved");
                fallbackAlert.setHeaderText("Printing not available");
                fallbackAlert.setContentText("The PDF has been saved to your Desktop as 'LoadConfirmation_" + load.getLoadNumber() + ".pdf'. You can open it and print manually.");
                fallbackAlert.showAndWait();
            }
            
            // Clean up temp file after a delay
            new Thread(() -> {
                try {
                    Thread.sleep(15000); // Wait 15 seconds to ensure printing is complete
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
        } catch (IOException e) {
            logger.error("Error generating PDF for printing: {}", e.getMessage(), e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Print Error");
            alert.setHeaderText("Failed to generate PDF");
            alert.setContentText("Error: " + e.getMessage());
            alert.showAndWait();
        }
    }
    
    private boolean printUsingSystemCommand(File pdfFile) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            
            if (os.contains("win")) {
                // Windows: Try using the print command
                pb = new ProcessBuilder("cmd", "/c", "print", pdfFile.getAbsolutePath());
            } else if (os.contains("mac")) {
                // macOS: Use lpr command
                pb = new ProcessBuilder("lpr", pdfFile.getAbsolutePath());
            } else {
                // Linux: Use lpr command
                pb = new ProcessBuilder("lpr", pdfFile.getAbsolutePath());
            }
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            logger.warn("System print command failed: {}", e.getMessage());
            return false;
        }
    }
    
    private void handleSaveAsPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Load Confirmation PDF");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("LoadConfirmation_" + load.getLoadNumber() + ".pdf");
        
        File file = fileChooser.showSaveDialog(dialog);
        if (file != null) {
            try {
                PDDocument document = generator.generateLoadConfirmation(load);
                document.save(file);
                document.close();
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Success");
                alert.setHeaderText(null);
                alert.setContentText("Load confirmation saved successfully!");
                alert.showAndWait();
                
                // Ask if user wants to open the PDF
                Alert openAlert = new Alert(Alert.AlertType.CONFIRMATION);
                openAlert.setTitle("Open PDF");
                openAlert.setHeaderText(null);
                openAlert.setContentText("Would you like to open the PDF?");
                openAlert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        try {
                            java.awt.Desktop.getDesktop().open(file);
                        } catch (IOException ex) {
                            logger.error("Error opening PDF: {}", ex.getMessage());
                        }
                    }
                });
            } catch (IOException e) {
                logger.error("Error saving PDF: {}", e.getMessage(), e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Failed to save PDF");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }
    
    private String formatLocationAddress(LoadLocation location) {
        StringBuilder sb = new StringBuilder();
        if (location.getAddress() != null && !location.getAddress().isEmpty()) {
            sb.append(location.getAddress());
        }
        if (location.getCity() != null && !location.getCity().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(location.getCity());
        }
        if (location.getState() != null && !location.getState().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(location.getState());
        }
        return sb.toString();
    }
    
    private void addPickupAndDropSideBySide(VBox content) {
        HBox locationBox = new HBox(15); // Reduced gap
        locationBox.setPadding(new Insets(5, 0, 5, 0)); // Reduced padding
        locationBox.setAlignment(Pos.TOP_CENTER);
        
        // Pickup section on the left
        VBox pickupContainer = new VBox(3); // Reduced spacing
        pickupContainer.setMinWidth(350);
        pickupContainer.setMaxWidth(350);
        
        // Main pickup section
        VBox pickupSection = new VBox(3); // Reduced spacing
        
        Label pickupTitle = new Label("PICKUP INFORMATION");
        pickupTitle.setFont(Font.font("Arial", FontWeight.BOLD, 10)); // Reduced font size
        pickupTitle.setStyle("-fx-text-fill: #2c3e50; -fx-background-color: #ecf0f1; -fx-padding: 3 8; -fx-background-radius: 2;");
        
        GridPane pickupGrid = new GridPane();
        pickupGrid.setHgap(8); // Reduced gap
        pickupGrid.setVgap(2); // Reduced gap
        pickupGrid.setPadding(new Insets(3, 0, 0, 10)); // Reduced padding
        
        int pRow = 0;
        addInfoRow(pickupGrid, pRow++, "Customer:", load.getCustomer(), 9); // Reduced font size
        addInfoRow(pickupGrid, pRow++, "Address:", load.getPickUpLocation(), 9);
        String pickupDateTime = formatDateTime(load.getPickUpDate(), load.getPickUpTime());
        addInfoRow(pickupGrid, pRow++, "Date/Time:", pickupDateTime, 9);
        if (load.getPONumber() != null && !load.getPONumber().isEmpty()) {
            addInfoRow(pickupGrid, pRow++, "PO #:", load.getPONumber(), 9);
        }
        
        pickupSection.getChildren().addAll(pickupTitle, pickupGrid);
        pickupContainer.getChildren().add(pickupSection);
        
        // Additional pickup locations
        List<LoadLocation> additionalPickups = load.getLocations().stream()
            .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP)
            .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collect(java.util.stream.Collectors.toList());
            
        if (!additionalPickups.isEmpty()) {
            VBox addPickupSection = new VBox(2); // Reduced spacing
            
            Label addPickupTitle = new Label("Additional Pickup Locations:");
            addPickupTitle.setFont(Font.font("Arial", FontWeight.BOLD, 8)); // Reduced font size
            addPickupTitle.setPadding(new Insets(0, 0, 2, 10)); // Reduced padding
            
            VBox pickupList = new VBox(1); // Reduced spacing
            pickupList.setPadding(new Insets(0, 0, 0, 15)); // Reduced padding
            
            for (int i = 0; i < additionalPickups.size(); i++) {
                LoadLocation loc = additionalPickups.get(i);
                String info = String.format("%d. %s - %s - %s", 
                    i + 1, 
                    loc.getCustomer() != null ? loc.getCustomer() : "",
                    formatLocationAddress(loc),
                    loc.getTime() != null ? loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "");
                Label locLabel = new Label(info);
                locLabel.setFont(Font.font("Arial", 8)); // Reduced font size
                pickupList.getChildren().add(locLabel);
                
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    Label notesLabel = new Label("   Notes: " + loc.getNotes());
                    notesLabel.setFont(Font.font("Arial", 7)); // Reduced font size
                    notesLabel.setStyle("-fx-text-fill: #7f8c8d;");
                    pickupList.getChildren().add(notesLabel);
                }
            }
            
            addPickupSection.getChildren().addAll(addPickupTitle, pickupList);
            pickupContainer.getChildren().add(addPickupSection);
        }
        
        // Drop section on the right
        VBox dropContainer = new VBox(3); // Reduced spacing
        dropContainer.setMinWidth(350);
        dropContainer.setMaxWidth(350);
        
        // Main drop section
        VBox dropSection = new VBox(3); // Reduced spacing
        
        Label dropTitle = new Label("DROP INFORMATION");
        dropTitle.setFont(Font.font("Arial", FontWeight.BOLD, 10)); // Reduced font size
        dropTitle.setStyle("-fx-text-fill: #2c3e50; -fx-background-color: #ecf0f1; -fx-padding: 3 8; -fx-background-radius: 2;");
        
        GridPane dropGrid = new GridPane();
        dropGrid.setHgap(8); // Reduced gap
        dropGrid.setVgap(2); // Reduced gap
        dropGrid.setPadding(new Insets(3, 0, 0, 10)); // Reduced padding
        
        int dRow = 0;
        addInfoRow(dropGrid, dRow++, "Customer:", load.getCustomer2() != null ? load.getCustomer2() : load.getCustomer(), 9); // Reduced font size
        addInfoRow(dropGrid, dRow++, "Address:", load.getDropLocation(), 9);
        String dropDateTime = formatDateTime(load.getDeliveryDate(), load.getDeliveryTime());
        addInfoRow(dropGrid, dRow++, "Date/Time:", dropDateTime, 9);
        
        dropSection.getChildren().addAll(dropTitle, dropGrid);
        dropContainer.getChildren().add(dropSection);
        
        // Additional drop locations
        List<LoadLocation> additionalDrops = load.getLocations().stream()
            .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP)
            .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collect(java.util.stream.Collectors.toList());
            
        if (!additionalDrops.isEmpty()) {
            VBox addDropSection = new VBox(2); // Reduced spacing
            
            Label addDropTitle = new Label("Additional Drop Locations:");
            addDropTitle.setFont(Font.font("Arial", FontWeight.BOLD, 8)); // Reduced font size
            addDropTitle.setPadding(new Insets(0, 0, 2, 10)); // Reduced padding
            
            VBox dropList = new VBox(1); // Reduced spacing
            dropList.setPadding(new Insets(0, 0, 0, 15)); // Reduced padding
            
            for (int i = 0; i < additionalDrops.size(); i++) {
                LoadLocation loc = additionalDrops.get(i);
                String info = String.format("%d. %s - %s - %s", 
                    i + 1, 
                    loc.getCustomer() != null ? loc.getCustomer() : "",
                    formatLocationAddress(loc),
                    loc.getTime() != null ? loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "");
                Label locLabel = new Label(info);
                locLabel.setFont(Font.font("Arial", 8)); // Reduced font size
                dropList.getChildren().add(locLabel);
                
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    Label notesLabel = new Label("   Notes: " + loc.getNotes());
                    notesLabel.setFont(Font.font("Arial", 7)); // Reduced font size
                    notesLabel.setStyle("-fx-text-fill: #7f8c8d;");
                    dropList.getChildren().add(notesLabel);
                }
            }
            
            addDropSection.getChildren().addAll(addDropTitle, dropList);
            dropContainer.getChildren().add(addDropSection);
        }
        
        locationBox.getChildren().addAll(pickupContainer, dropContainer);
        content.getChildren().add(locationBox);
    }
    
    private void addInfoRow(GridPane grid, int row, String label, String value, int fontSize) {
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
        labelNode.setStyle("-fx-text-fill: #2c3e50;");
        
        Label valueNode = new Label(value != null ? value : "");
        valueNode.setFont(Font.font("Arial", fontSize));
        valueNode.setStyle("-fx-text-fill: #34495e;");
        valueNode.setWrapText(true);
        valueNode.setMaxWidth(250);
        
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
    
    public void show() {
        dialog.show();
    }
    
    private String getCompanyNameFromConfig() {
        Properties props = new Properties();
        File configFile = new File("payroll_config.properties");
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                String name = props.getProperty("company.name");
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            } catch (Exception e) {
                logger.warn("Could not load company name from config: {}", e.getMessage());
            }
        }
        
        return "Your Company Name";
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}