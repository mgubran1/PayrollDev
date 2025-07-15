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
        dialog.setWidth(850);
        dialog.setHeight(700);
        
        createContent();
    }
    
    private void createContent() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        
        // Create toolbar
        ToolBar toolbar = createToolbar();
        
        // Create preview content
        ScrollPane scrollPane = new ScrollPane();
        previewContent = createPreviewContent();
        scrollPane.setContent(previewContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f0f0f0;");
        
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        root.getChildren().addAll(toolbar, scrollPane);
        
        Scene scene = new Scene(root);
        dialog.setScene(scene);
    }
    
    private ToolBar createToolbar() {
        Button printBtn = new Button("Print");
        printBtn.setOnAction(e -> handlePrint());
        
        Button saveAsPdfBtn = new Button("Save as PDF");
        saveAsPdfBtn.setOnAction(e -> handleSaveAsPdf());
        
        Button emailBtn = new Button("Email");
        emailBtn.setDisable(true); // Will be implemented later
        
        Button configBtn = new Button("Configuration");
        configBtn.setOnAction(e -> {
            LoadConfirmationConfigDialog configDialog = new LoadConfirmationConfigDialog();
            configDialog.showAndWait();
            // Refresh preview after configuration changes
            previewContent = createPreviewContent();
            ScrollPane scrollPane = (ScrollPane) ((VBox) dialog.getScene().getRoot()).getChildren().get(1);
            scrollPane.setContent(previewContent);
        });
        
        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());
        
        return new ToolBar(printBtn, saveAsPdfBtn, emailBtn, new Separator(), configBtn, new Separator(), closeBtn);
    }
    
    private VBox createPreviewContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);
        content.setStyle("-fx-background-color: white; -fx-border-color: #cccccc; -fx-border-width: 1;");
        content.setMaxWidth(750);
        
        // Add company logo if available
        addCompanyLogo(content);
        
        // Add header with driver info
        addHeader(content);
        
        // Add separator
        content.getChildren().add(new Separator());
        
        // Add pickup and drop information
        addPickupSection(content);
        content.getChildren().add(new Separator());
        addDropSection(content);
        
        // Add notes if present
        if (load.getNotes() != null && !load.getNotes().trim().isEmpty()) {
            addNotesSection(content);
        }
        
        // Add pickup and delivery policy
        addPolicySection(content);
        
        // Add dispatcher information
        addDispatcherSection(content);
        
        return content;
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
                    imageView.setFitHeight(80);
                    
                    HBox logoBox = new HBox(imageView);
                    logoBox.setAlignment(Pos.CENTER);
                    content.getChildren().add(logoBox);
                } catch (Exception e) {
                    logger.error("Error loading logo: {}", e.getMessage());
                }
            }
        }
    }
    
    private void addHeader(VBox content) {
        // Get company name
        String companyName = getCompanyNameFromConfig();
        
        // Company name
        Label companyLabel = new Label(companyName);
        companyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        companyLabel.setAlignment(Pos.CENTER);
        
        HBox companyBox = new HBox(companyLabel);
        companyBox.setAlignment(Pos.CENTER);
        content.getChildren().add(companyBox);
        
        // Title
        Label title = new Label("LOAD CONFIRMATION");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        title.setAlignment(Pos.CENTER);
        
        HBox titleBox = new HBox(title);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(5, 0, 0, 0));
        content.getChildren().add(titleBox);
        
        // Header with load info on left and driver info on right
        HBox headerBox = new HBox();
        headerBox.setPadding(new Insets(10, 0, 0, 0));
        
        // Left side - Load info
        VBox leftInfo = new VBox(5);
        Label loadNumLabel = new Label("Load #: " + load.getLoadNumber());
        loadNumLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        Label pickupDateLabel = new Label("Pickup: " + 
            (load.getPickUpDate() != null ? load.getPickUpDate().format(dateFormatter) : "TBD"));
        pickupDateLabel.setFont(Font.font("Arial", 11));
        
        Label deliveryDateLabel = new Label("Delivery: " + 
            (load.getDeliveryDate() != null ? load.getDeliveryDate().format(dateFormatter) : "TBD"));
        deliveryDateLabel.setFont(Font.font("Arial", 11));
        
        leftInfo.getChildren().addAll(loadNumLabel, pickupDateLabel, deliveryDateLabel);
        
        // Right side - Driver info
        VBox rightInfo = new VBox(5);
        rightInfo.setAlignment(Pos.TOP_RIGHT);
        
        Employee driver = load.getDriver();
        if (driver != null) {
            Label driverLabel = new Label("Driver: " + driver.getName());
            driverLabel.setFont(Font.font("Arial", 11));
            
            String truckUnit = load.getTruckUnitSnapshot() != null ? load.getTruckUnitSnapshot() : 
                             (driver.getTruckUnit() != null ? driver.getTruckUnit() : "N/A");
            Label truckLabel = new Label("Truck: " + truckUnit);
            truckLabel.setFont(Font.font("Arial", 11));
            
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
            Label trailerLabel = new Label("Trailer: " + trailerInfo);
            trailerLabel.setFont(Font.font("Arial", 11));
            
            rightInfo.getChildren().addAll(driverLabel, truckLabel, trailerLabel);
            
            // Add mobile and email if available
            if (driver.getPhone() != null && !driver.getPhone().isEmpty()) {
                Label mobileLabel = new Label("Mobile #: " + driver.getPhone());
                mobileLabel.setFont(Font.font("Arial", 11));
                leftInfo.getChildren().add(mobileLabel);
            }
            
            if (driver.getEmail() != null && !driver.getEmail().isEmpty()) {
                Label emailLabel = new Label("Email: " + driver.getEmail());
                emailLabel.setFont(Font.font("Arial", 11));
                rightInfo.getChildren().add(emailLabel);
            }
        }
        
        // Add spacer to push driver info to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        headerBox.getChildren().addAll(leftInfo, spacer, rightInfo);
        content.getChildren().add(headerBox);
    }
    
    
    private void addPickupSection(VBox content) {
        VBox section = createSection("PICKUP INFORMATION");
        
        // Always show the original pickup location first
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(5);
        grid.setPadding(new Insets(0, 0, 0, 20));
        
        int row = 0;
        addInfoRow(grid, row++, "Customer:", load.getCustomer());
        addInfoRow(grid, row++, "Address:", load.getPickUpLocation());
        
        String pickupDateTime = formatDateTime(load.getPickUpDate(), load.getPickUpTime());
        addInfoRow(grid, row++, "Date/Time:", pickupDateTime);
        
        if (load.getPONumber() != null && !load.getPONumber().isEmpty()) {
            addInfoRow(grid, row++, "PO #:", load.getPONumber());
        }
        
        section.getChildren().add(grid);
        
        // Check if we have additional pickup locations from Manage Load Locations
        List<LoadLocation> additionalPickups = load.getLocations().stream()
            .filter(loc -> loc.getType() == LoadLocation.LocationType.PICKUP)
            .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collect(java.util.stream.Collectors.toList());
        
        // If we have additional pickups, show them
        if (!additionalPickups.isEmpty()) {
            Label additionalLabel = new Label("Additional Pickup Locations:");
            additionalLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            additionalLabel.setPadding(new Insets(10, 0, 5, 20));
            section.getChildren().add(additionalLabel);
            
            VBox locationsList = new VBox(3);
            locationsList.setPadding(new Insets(0, 0, 0, 30));
            
            for (int i = 0; i < additionalPickups.size(); i++) {
                LoadLocation loc = additionalPickups.get(i);
                
                // Format: Customer - Address - Time
                String customer = loc.getCustomer() != null && !loc.getCustomer().isEmpty() ? 
                    loc.getCustomer() : "";
                String address = formatLocationAddress(loc);
                String time = loc.getTime() != null ? loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "";
                
                Label locationLine = new Label(String.format("%d. %s - %s - %s", 
                    i + 1, customer, address, time));
                locationLine.setFont(Font.font("Arial", 11));
                locationsList.getChildren().add(locationLine);
                
                // Add notes if present
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    Label notesLabel = new Label("   Notes: " + loc.getNotes());
                    notesLabel.setFont(Font.font("Arial", 10));
                    notesLabel.setTextFill(Color.GRAY);
                    locationsList.getChildren().add(notesLabel);
                }
            }
            
            section.getChildren().add(locationsList);
        }
        
        content.getChildren().add(section);
    }
    
    private void addDropSection(VBox content) {
        VBox section = createSection("DROP INFORMATION");
        
        // Always show the original drop location first
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(5);
        grid.setPadding(new Insets(0, 0, 0, 20));
        
        int row = 0;
        addInfoRow(grid, row++, "Customer:", load.getCustomer2() != null ? load.getCustomer2() : load.getCustomer());
        addInfoRow(grid, row++, "Address:", load.getDropLocation());
        
        String dropDateTime = formatDateTime(load.getDeliveryDate(), load.getDeliveryTime());
        addInfoRow(grid, row++, "Date/Time:", dropDateTime);
        
        section.getChildren().add(grid);
        
        // Check if we have additional drop locations from Manage Load Locations
        List<LoadLocation> additionalDrops = load.getLocations().stream()
            .filter(loc -> loc.getType() == LoadLocation.LocationType.DROP)
            .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collect(java.util.stream.Collectors.toList());
        
        // If we have additional drops, show them
        if (!additionalDrops.isEmpty()) {
            Label additionalLabel = new Label("Additional Drop Locations:");
            additionalLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            additionalLabel.setPadding(new Insets(10, 0, 5, 20));
            section.getChildren().add(additionalLabel);
            
            VBox locationsList = new VBox(3);
            locationsList.setPadding(new Insets(0, 0, 0, 30));
            
            for (int i = 0; i < additionalDrops.size(); i++) {
                LoadLocation loc = additionalDrops.get(i);
                
                // Format: Customer - Address - Time
                String customer = loc.getCustomer() != null && !loc.getCustomer().isEmpty() ? 
                    loc.getCustomer() : "";
                String address = formatLocationAddress(loc);
                String time = loc.getTime() != null ? loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "";
                
                Label locationLine = new Label(String.format("%d. %s - %s - %s", 
                    i + 1, customer, address, time));
                locationLine.setFont(Font.font("Arial", 11));
                locationsList.getChildren().add(locationLine);
                
                // Add notes if present
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    Label notesLabel = new Label("   Notes: " + loc.getNotes());
                    notesLabel.setFont(Font.font("Arial", 10));
                    notesLabel.setTextFill(Color.GRAY);
                    locationsList.getChildren().add(notesLabel);
                }
            }
            
            section.getChildren().add(locationsList);
        }
        
        content.getChildren().add(section);
    }
    
    private void addNotesSection(VBox content) {
        VBox section = createSection("NOTES");
        
        Text notesText = new Text(load.getNotes());
        notesText.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        notesText.setFill(Color.RED);
        notesText.setWrappingWidth(680);
        
        VBox notesBox = new VBox(notesText);
        notesBox.setPadding(new Insets(0, 0, 0, 20));
        
        section.getChildren().add(notesBox);
        content.getChildren().add(section);
    }
    
    private void addPolicySection(VBox content) {
        String policy = config.getPickupDeliveryPolicy();
        if (policy != null && !policy.isEmpty()) {
            VBox section = createSection("PICKUP AND DELIVERY POLICY");
            
            Text policyText = new Text(policy);
            policyText.setFont(Font.font("Arial", 8));
            policyText.setWrappingWidth(680);
            
            VBox policyBox = new VBox(policyText);
            policyBox.setPadding(new Insets(0, 0, 0, 20));
            
            section.getChildren().add(policyBox);
            content.getChildren().add(section);
        }
    }
    
    private void addDispatcherSection(VBox content) {
        HBox dispatcherBox = new HBox(10);
        dispatcherBox.setPadding(new Insets(20, 0, 0, 0));
        dispatcherBox.setAlignment(Pos.CENTER);
        
        Label title = new Label("DISPATCHER INFORMATION:");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        
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
        infoLabel.setFont(Font.font("Arial", 8));
        
        dispatcherBox.getChildren().addAll(title, infoLabel);
        content.getChildren().add(dispatcherBox);
    }
    
    private VBox createSection(String title) {
        VBox section = new VBox(8);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        titleLabel.setTextFill(Color.DARKBLUE);
        
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
    
    private void handlePrint() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(dialog)) {
            PageLayout pageLayout = job.getPrinter().createPageLayout(
                Paper.NA_LETTER, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT
            );
            job.getJobSettings().setPageLayout(pageLayout);
            
            boolean success = job.printPage(previewContent);
            if (success) {
                job.endJob();
            }
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
        HBox locationBox = new HBox(20);
        locationBox.setPadding(new Insets(10, 0, 10, 0));
        
        // Pickup section on the left with additional locations
        VBox pickupContainer = new VBox(10);
        pickupContainer.setMinWidth(350);
        pickupContainer.setMaxWidth(350);
        
        // Main pickup section
        VBox pickupSection = new VBox(5);
        
        Label pickupTitle = new Label("PICKUP INFORMATION");
        pickupTitle.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        pickupTitle.setTextFill(Color.DARKBLUE);
        
        GridPane pickupGrid = new GridPane();
        pickupGrid.setHgap(10);
        pickupGrid.setVgap(3);
        pickupGrid.setPadding(new Insets(5, 0, 0, 15));
        
        int pRow = 0;
        addInfoRow(pickupGrid, pRow++, "Customer:", load.getCustomer(), 9);
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
            VBox addPickupSection = new VBox(3);
            
            Label addPickupTitle = new Label("Additional Pickup Locations:");
            addPickupTitle.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            addPickupTitle.setPadding(new Insets(0, 0, 3, 15));
            
            VBox pickupList = new VBox(2);
            pickupList.setPadding(new Insets(0, 0, 0, 25));
            
            for (int i = 0; i < additionalPickups.size(); i++) {
                LoadLocation loc = additionalPickups.get(i);
                String info = String.format("%d. %s - %s - %s", 
                    i + 1, 
                    loc.getCustomer() != null ? loc.getCustomer() : "",
                    formatLocationAddress(loc),
                    loc.getTime() != null ? loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "");
                Label locLabel = new Label(info);
                locLabel.setFont(Font.font("Arial", 9));
                pickupList.getChildren().add(locLabel);
                
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    Label notesLabel = new Label("   Notes: " + loc.getNotes());
                    notesLabel.setFont(Font.font("Arial", 8));
                    notesLabel.setTextFill(Color.GRAY);
                    pickupList.getChildren().add(notesLabel);
                }
            }
            
            addPickupSection.getChildren().addAll(addPickupTitle, pickupList);
            pickupContainer.getChildren().add(addPickupSection);
        }
        
        // Drop section on the right with additional locations
        VBox dropContainer = new VBox(10);
        dropContainer.setMinWidth(350);
        dropContainer.setMaxWidth(350);
        
        // Main drop section
        VBox dropSection = new VBox(5);
        
        Label dropTitle = new Label("DROP INFORMATION");
        dropTitle.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        dropTitle.setTextFill(Color.DARKBLUE);
        
        GridPane dropGrid = new GridPane();
        dropGrid.setHgap(10);
        dropGrid.setVgap(3);
        dropGrid.setPadding(new Insets(5, 0, 0, 15));
        
        int dRow = 0;
        addInfoRow(dropGrid, dRow++, "Customer:", load.getCustomer2() != null ? load.getCustomer2() : load.getCustomer(), 9);
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
            VBox addDropSection = new VBox(3);
            
            Label addDropTitle = new Label("Additional Drop Locations:");
            addDropTitle.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            addDropTitle.setPadding(new Insets(0, 0, 3, 15));
            
            VBox dropList = new VBox(2);
            dropList.setPadding(new Insets(0, 0, 0, 25));
            
            for (int i = 0; i < additionalDrops.size(); i++) {
                LoadLocation loc = additionalDrops.get(i);
                String info = String.format("%d. %s - %s - %s", 
                    i + 1, 
                    loc.getCustomer() != null ? loc.getCustomer() : "",
                    formatLocationAddress(loc),
                    loc.getTime() != null ? loc.getTime().format(DateTimeFormatter.ofPattern("h:mm a")) : "");
                Label locLabel = new Label(info);
                locLabel.setFont(Font.font("Arial", 9));
                dropList.getChildren().add(locLabel);
                
                if (loc.getNotes() != null && !loc.getNotes().isEmpty()) {
                    Label notesLabel = new Label("   Notes: " + loc.getNotes());
                    notesLabel.setFont(Font.font("Arial", 8));
                    notesLabel.setTextFill(Color.GRAY);
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
        
        Label valueNode = new Label(value != null ? value : "");
        valueNode.setFont(Font.font("Arial", fontSize));
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
                logger.warn("Could not load company name from config: " + e.getMessage());
            }
        }
        
        return "Your Company Name";
    }
}