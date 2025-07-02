package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadStatus;
import com.company.payroll.drivers.Driver;
import com.company.payroll.billing.PaymentStatus;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced dialog for displaying and managing load details
 * 
 * @author Payroll System
 * @version 2.0
 */
public class LoadDetailsDialog extends Dialog<Void> {
    private static final Logger logger = LoggerFactory.getLogger(LoadDetailsDialog.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Current timestamp for logging
    private static final LocalDateTime CURRENT_TIME = LocalDateTime.parse("2025-07-02 11:04:11", DATETIME_FORMAT);
    private static final String CURRENT_USER = "mgubran1";
    
    // Core components
    private final Load load;
    private final DispatcherController controller;
    
    // UI Components - Tabs
    private TabPane tabPane;
    
    // UI Components - Main Info
    private GridPane detailsGrid;
    private Label statusLabel;
    
    // UI Components - Document Tracking
    private TableView<DocumentItem> documentsTable;
    private ObservableList<DocumentItem> documents;
    
    // UI Components - Stop History
    private TableView<StopHistoryItem> stopsTable;
    private ObservableList<StopHistoryItem> stops;
    
    // UI Components - Payment Info
    private GridPane paymentGrid;
    
    // UI Components - Notes/Comments
    private TextArea notesArea;
    private TableView<CommentItem> commentsTable;
    private ObservableList<CommentItem> comments;
    private TextField newCommentField;
    
    /**
     * Constructor with load and controller
     */
    public LoadDetailsDialog(Load load, DispatcherController controller, Window owner) {
        this.load = load;
        this.controller = controller;
        
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        
        setTitle("Load Details #" + load.getLoadNumber());
        setHeaderText("Load #" + load.getLoadNumber() + " - " + load.getCustomer());
        
        // Apply styling
        getDialogPane().getStylesheets().add(
            getClass().getResource("/styles/dispatcher-dialogs.css").toExternalForm()
        );
        getDialogPane().getStyleClass().add("load-details-dialog");
        
        initializeUI();
        loadData();
        
        // Add dialog buttons
        ButtonType printButton = new ButtonType("Print", ButtonBar.ButtonData.LEFT);
        ButtonType assignButton = new ButtonType("Assign", ButtonBar.ButtonData.LEFT);
        ButtonType editButton = new ButtonType("Edit", ButtonBar.ButtonData.LEFT);
        ButtonType closeButton = ButtonType.CLOSE;
        
        getDialogPane().getButtonTypes().addAll(printButton, assignButton, editButton, closeButton);
        
        // Set button actions
        Button printBtn = (Button) getDialogPane().lookupButton(printButton);
        printBtn.setOnAction(e -> printLoad());
        
        Button assignBtn = (Button) getDialogPane().lookupButton(assignButton);
        assignBtn.setOnAction(e -> assignLoad());
        assignBtn.setDisable(load.getDriver() != null); // Disable if already assigned
        
        Button editBtn = (Button) getDialogPane().lookupButton(editButton);
        editBtn.setOnAction(e -> editLoad());
        
        logger.info("Load details dialog opened for Load #{} by {} at {}", 
            load.getLoadNumber(), CURRENT_USER, CURRENT_TIME.format(DATETIME_FORMAT));
    }
    
    /**
     * Initialize the UI components
     */
    private void initializeUI() {
        // Create main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPrefSize(900, 700);
        
        // Create load status header
        HBox statusBar = createStatusBar();
        mainLayout.setTop(statusBar);
        
        // Create tabbed content
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Main info tab
        Tab detailsTab = new Tab("Load Details");
        detailsTab.setContent(createDetailsTab());
        
        // Route tab
        Tab routeTab = new Tab("Route & Tracking");
        routeTab.setContent(createRouteTab());
        
        // Documents tab
        Tab documentsTab = new Tab("Documents");
        documentsTab.setContent(createDocumentsTab());
        
        // Stop history tab
        Tab stopsTab = new Tab("Stop History");
        stopsTab.setContent(createStopsTab());
        
        // Payment tab
        Tab paymentTab = new Tab("Payment & Billing");
        paymentTab.setContent(createPaymentTab());
        
        // Notes tab
        Tab notesTab = new Tab("Notes & Comments");
        notesTab.setContent(createNotesTab());
        
        tabPane.getTabs().addAll(detailsTab, routeTab, documentsTab, stopsTab, paymentTab, notesTab);
        mainLayout.setCenter(tabPane);
        
        getDialogPane().setContent(mainLayout);
    }
    
    /**
     * Create status bar showing current load status
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");
        
        // Load ID display
        Label idLabel = new Label("Load #" + load.getLoadNumber());
        idLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        // Customer display
        Label customerLabel = new Label(load.getCustomer());
        customerLabel.setFont(Font.font("System", FontWeight.MEDIUM, 14));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Status display
        statusLabel = new Label(load.getStatus().toString());
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        statusLabel.getStyleClass().add("status-label");
        
        // Set status color
        setStatusColor(statusLabel, load.getStatus());
        
        statusBar.getChildren().addAll(idLabel, customerLabel, spacer, statusLabel);
        
        return statusBar;
    }
    
    /**
     * Create the main details tab
     */
    private ScrollPane createDetailsTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Customer and load info section
        TitledPane customerPane = new TitledPane("Customer & Load Information", createCustomerSection());
        customerPane.setCollapsible(true);
        customerPane.setExpanded(true);
        
        // Origin and destination section
        TitledPane routePane = new TitledPane("Origin & Destination", createRouteSection());
        routePane.setCollapsible(true);
        routePane.setExpanded(true);
        
        // Driver and equipment section
        TitledPane driverPane = new TitledPane("Driver & Equipment", createDriverSection());
        driverPane.setCollapsible(true);
        driverPane.setExpanded(true);
        
        // Commodity section
        TitledPane commodityPane = new TitledPane("Commodity & Cargo", createCommoditySection());
        commodityPane.setCollapsible(true);
        commodityPane.setExpanded(true);
        
        // References section
        TitledPane referencesPane = new TitledPane("References", createReferencesSection());
        referencesPane.setCollapsible(true);
        referencesPane.setExpanded(false);
        
        content.getChildren().addAll(customerPane, routePane, driverPane, commodityPane, referencesPane);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    /**
     * Create customer information section
     */
    private GridPane createCustomerSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        int row = 0;
        addDetailRow(grid, "Customer:", load.getCustomer(), row++);
        addDetailRow(grid, "Customer ID:", load.getCustomerId(), row++);
        addDetailRow(grid, "PO Number:", load.getPONumber(), row++);
        addDetailRow(grid, "Customer Contact:", load.getContactName(), row++);
        addDetailRow(grid, "Contact Phone:", formatPhoneNumber(load.getContactPhone()), row++);
        addDetailRow(grid, "Contact Email:", load.getContactEmail(), row++);
        
        row = 0;
        addDetailRow(grid, "Order Date:", formatDate(load.getOrderDate()), row++, 2);
        addDetailRow(grid, "Broker:", load.getBrokerName(), row++, 2);
        addDetailRow(grid, "Broker Contact:", load.getBrokerContact(), row++, 2);
        addDetailRow(grid, "Broker Phone:", formatPhoneNumber(load.getBrokerPhone()), row++, 2);
        addDetailRow(grid, "Rate:", formatCurrency(load.getRate()), row++, 2);
        addDetailRow(grid, "Mode:", load.getMode(), row++, 2);
        
        return grid;
    }
    
    /**
     * Create origin and destination section
     */
    private GridPane createRouteSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        // Origin column
        Label originLabel = new Label("ORIGIN");
        originLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        grid.add(originLabel, 0, 0, 2, 1);
        
        int row = 1;
        addDetailRow(grid, "Name:", load.getOriginName(), row++);
        addDetailRow(grid, "Address:", load.getOriginAddress(), row++);
        addDetailRow(grid, "City/State/Zip:", 
            load.getOriginCity() + ", " + load.getOriginState() + " " + load.getOriginZip(), row++);
        addDetailRow(grid, "Pickup Date:", formatDate(load.getPickupDate()), row++);
        
        if (load.getPickupTime() != null) {
            addDetailRow(grid, "Pickup Time:", load.getPickupTime().format(TIME_FORMAT), row++);
        }
        
        addDetailRow(grid, "Contact:", load.getOriginContact(), row++);
        addDetailRow(grid, "Phone:", formatPhoneNumber(load.getOriginPhone()), row++);
        addDetailRow(grid, "Pickup #:", load.getPickupNumber(), row++);
        
        // Destination column
        Label destLabel = new Label("DESTINATION");
        destLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        grid.add(destLabel, 2, 0, 2, 1);
        
        row = 1;
        addDetailRow(grid, "Name:", load.getDestName(), row++, 2);
        addDetailRow(grid, "Address:", load.getDestAddress(), row++, 2);
        addDetailRow(grid, "City/State/Zip:", 
            load.getDestCity() + ", " + load.getDestState() + " " + load.getDestZip(), row++, 2);
        addDetailRow(grid, "Delivery Date:", formatDate(load.getDeliveryDate()), row++, 2);
        
        if (load.getDeliveryTime() != null) {
            addDetailRow(grid, "Delivery Time:", load.getDeliveryTime().format(TIME_FORMAT), row++, 2);
        }
        
        addDetailRow(grid, "Contact:", load.getDestContact(), row++, 2);
        addDetailRow(grid, "Phone:", formatPhoneNumber(load.getDestPhone()), row++, 2);
        addDetailRow(grid, "Delivery #:", load.getDeliveryNumber(), row++, 2);
        
        // Distance info
        Separator separator = new Separator();
        grid.add(separator, 0, row, 4, 1);
        row++;
        
        addDetailRow(grid, "Total Miles:", String.format("%,.0f", load.getMiles()), row++);
        addDetailRow(grid, "Estimated Hours:", String.format("%.1f", load.getEstimatedHours()), row++);
        addDetailRow(grid, "Rate per Mile:", formatCurrency(load.getRate() / Math.max(load.getMiles(), 1)), row++);
        
        return grid;
    }
    
    /**
     * Create driver and equipment section
     */
    private GridPane createDriverSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        Driver driver = load.getDriver();
        
        if (driver != null) {
            int row = 0;
            addDetailRow(grid, "Driver:", driver.getName(), row++);
            addDetailRow(grid, "Driver ID:", driver.getDriverId(), row++);
            addDetailRow(grid, "Phone:", formatPhoneNumber(driver.getPhoneNumber()), row++);
            addDetailRow(grid, "Email:", driver.getEmail(), row++);
            
            row = 0;
            addDetailRow(grid, "Truck #:", driver.getTruckNumber(), row++, 2);
            addDetailRow(grid, "Trailer #:", driver.getTrailerNumber(), row++, 2);
            addDetailRow(grid, "License:", driver.getLicenseNumber() + " (" + driver.getLicenseState() + ")", row++, 2);
            
            // Add driver image if available
            if (driver.getPhotoUrl() != null) {
                try {
                    ImageView driverPhoto = new ImageView(new Image(driver.getPhotoUrl(), 100, 100, true, true));
                    grid.add(driverPhoto, 4, 0, 1, 4);
                } catch (Exception e) {
                    logger.warn("Could not load driver photo: {}", e.getMessage());
                }
            }
            
            // Assignment info
            Separator separator = new Separator();
            grid.add(separator, 0, row, 4, 1);
            row++;
            
            addDetailRow(grid, "Assigned On:", 
                load.getAssignedDate() != null ? load.getAssignedDate().format(DATETIME_FORMAT) : "N/A", row++);
            addDetailRow(grid, "Assigned By:", load.getAssignedBy() != null ? load.getAssignedBy() : "N/A", row++);
        } else {
            Label noDriverLabel = new Label("No driver assigned to this load");
            noDriverLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            grid.add(noDriverLabel, 0, 0, 4, 1);
            
            Button assignButton = new Button("Assign Driver");
            assignButton.setOnAction(e -> assignLoad());
            grid.add(assignButton, 0, 1, 2, 1);
        }
        
        return grid;
    }
    
    /**
     * Create commodity and cargo section
     */
    private GridPane createCommoditySection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        int row = 0;
        addDetailRow(grid, "Commodity:", load.getCommodity(), row++);
        addDetailRow(grid, "Weight:", String.format("%,d lbs", load.getWeight()), row++);
        addDetailRow(grid, "Pieces:", String.valueOf(load.getPieces()), row++);
        addDetailRow(grid, "Packaging:", load.getPackaging(), row++);
        
        row = 0;
        addDetailRow(grid, "Temperature:", load.getTemperature(), row++, 2);
        addDetailRow(grid, "Hazmat:", load.isHazmat() ? "Yes" : "No", row++, 2);
        addDetailRow(grid, "Hazmat Class:", load.getHazmatClass(), row++, 2);
        addDetailRow(grid, "UN Number:", load.getUnNumber(), row++, 2);
        
        return grid;
    }
    
    /**
     * Create references section
     */
    private GridPane createReferencesSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        
        int row = 0;
        addDetailRow(grid, "Bill of Lading:", load.getBolNumber(), row++);
        addDetailRow(grid, "Reference #1:", load.getReference1(), row++);
        addDetailRow(grid, "Reference #2:", load.getReference2(), row++);
        addDetailRow(grid, "Reference #3:", load.getReference3(), row++);
        
        return grid;
    }
    
    /**
     * Create the route tab
     */
    private BorderPane createRouteTab() {
        BorderPane content = new BorderPane();
        
        // Map view for route
        WebView mapView = new WebView();
        WebEngine mapEngine = mapView.getEngine();
        
        // Load the map HTML template
        try {
            InputStream mapTemplateStream = getClass().getResourceAsStream("/templates/route_map.html");
            Scanner scanner = new Scanner(mapTemplateStream, "UTF-8");
            String mapTemplate = scanner.useDelimiter("\\A").next();
            scanner.close();
            
            // Replace placeholders with actual values
            mapTemplate = mapTemplate
                .replace("{{ORIGIN_LAT}}", "41.8781")
                .replace("{{ORIGIN_LNG}}", "-87.6298")
                .replace("{{ORIGIN_NAME}}", load.getOriginCity() + ", " + load.getOriginState())
                .replace("{{DEST_LAT}}", "40.7128")
                .replace("{{DEST_LNG}}", "-74.0060")
                .replace("{{DEST_NAME}}", load.getDestCity() + ", " + load.getDestState())
                .replace("{{API_KEY}}", "YOUR_API_KEY");
            
            mapEngine.loadContent(mapTemplate);
        } catch (Exception e) {
            logger.error("Failed to load route map", e);
            mapEngine.loadContent("<html><body><p>Failed to load route map</p></body></html>");
        }
        
        // Route info panel
        VBox routeInfo = new VBox(10);
        routeInfo.setPadding(new Insets(10));
        routeInfo.setPrefWidth(250);
        
        Label routeLabel = new Label("Route Information");
        routeLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        GridPane routeGrid = new GridPane();
        routeGrid.setHgap(10);
        routeGrid.setVgap(10);
        
        int row = 0;
        addDetailRow(routeGrid, "Origin:", load.getOriginCity() + ", " + load.getOriginState(), row++);
        addDetailRow(routeGrid, "Destination:", load.getDestCity() + ", " + load.getDestState(), row++);
        addDetailRow(routeGrid, "Distance:", String.format("%,.0f miles", load.getMiles()), row++);
        addDetailRow(routeGrid, "Est. Time:", String.format("%.1f hours", load.getEstimatedHours()), row++);
        
        // Add progress info if driver assigned
        if (load.getDriver() != null && load.getStatus() != LoadStatus.UNASSIGNED) {
            double progress = estimateProgress(load);
            
            Label progressLabel = new Label("Progress:");
            progressLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            
            ProgressBar progressBar = new ProgressBar(progress);
            progressBar.setPrefWidth(200);
            
            Label progressValue = new Label(String.format("%.0f%%", progress * 100));
            
            HBox progressBox = new HBox(10, progressLabel, progressBar, progressValue);
            progressBox.setAlignment(Pos.CENTER_LEFT);
            
            Label etaLabel = new Label("ETA:");
            etaLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            
            // Estimate ETA based on progress
            LocalDateTime estimatedEta = estimateETA(load);
            Label etaValue = new Label(
                estimatedEta != null ? estimatedEta.format(DATETIME_FORMAT) : "N/A"
            );
            
            HBox etaBox = new HBox(10, etaLabel, etaValue);
            etaBox.setAlignment(Pos.CENTER_LEFT);
            
            routeGrid.add(progressBox, 0, row++, 2, 1);
            routeGrid.add(etaBox, 0, row++, 2, 1);
        }
        
        // Milestones
        TitledPane milestonesPane = new TitledPane("Route Milestones", createMilestonesList());
        milestonesPane.setCollapsible(true);
        milestonesPane.setExpanded(true);
        
        // Weather information
        TitledPane weatherPane = new TitledPane("Weather Conditions", createWeatherInfo());
        weatherPane.setCollapsible(true);
        weatherPane.setExpanded(true);
        
        routeInfo.getChildren().addAll(routeLabel, routeGrid, milestonesPane, weatherPane);
        
        content.setCenter(mapView);
        content.setRight(routeInfo);
        
        return content;
    }
    
    /**
     * Create milestone list for route
     */
    private VBox createMilestonesList() {
        VBox milestones = new VBox(5);
        milestones.setPadding(new Insets(10));
        
        // Origin milestone
        HBox origin = new HBox(10);
        origin.setAlignment(Pos.CENTER_LEFT);
        Circle originCircle = new Circle(6, Color.GREEN);
        VBox originInfo = new VBox(2);
        Label originLabel = new Label("Origin: " + load.getOriginCity());
        originLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        Label originTime = new Label(formatDate(load.getPickupDate()));
        originInfo.getChildren().addAll(originLabel, originTime);
        origin.getChildren().addAll(originCircle, originInfo);
        
        // Intermediate milestones (simulated)
        List<HBox> intermediateMilestones = new ArrayList<>();
        String[] cities = {"Indianapolis, IN", "Columbus, OH", "Pittsburgh, PA"};
        for (int i = 0; i < cities.length; i++) {
            HBox milestone = new HBox(10);
            milestone.setAlignment(Pos.CENTER_LEFT);
            Circle circle = new Circle(6, Color.LIGHTBLUE);
            VBox info = new VBox(2);
            Label cityLabel = new Label(cities[i]);
            cityLabel.setFont(Font.font("System", FontWeight.MEDIUM, 12));
            LocalDate date = load.getPickupDate().plusDays(i + 1);
            Label time = new Label(formatDate(date));
            info.getChildren().addAll(cityLabel, time);
            milestone.getChildren().addAll(circle, info);
            intermediateMilestones.add(milestone);
        }
        
        // Destination milestone
        HBox destination = new HBox(10);
        destination.setAlignment(Pos.CENTER_LEFT);
        Circle destCircle = new Circle(6, Color.RED);
        VBox destInfo = new VBox(2);
        Label destLabel = new Label("Destination: " + load.getDestCity());
        destLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        Label destTime = new Label(formatDate(load.getDeliveryDate()));
        destInfo.getChildren().addAll(destLabel, destTime);
        destination.getChildren().addAll(destCircle, destInfo);
        
        // Vertical line connecting milestones
        milestones.getChildren().add(origin);
        
        for (HBox milestone : intermediateMilestones) {
            // Add vertical line
            Line line = new Line();
            line.setStartX(6);
            line.setStartY(0);
            line.setEndX(6);
            line.setEndY(10);
            line.setStroke(Color.GRAY);
            line.setStrokeWidth(2);
            
            HBox lineBox = new HBox();
            lineBox.setPadding(new Insets(0, 0, 0, 6));
            lineBox.getChildren().add(line);
            
            milestones.getChildren().addAll(lineBox, milestone);
        }
        
        // Final vertical line and destination
        Line finalLine = new Line();
        finalLine.setStartX(6);
        finalLine.setStartY(0);
        finalLine.setEndX(6);
        finalLine.setEndY(10);
        finalLine.setStroke(Color.GRAY);
        finalLine.setStrokeWidth(2);
        
        HBox lineBox = new HBox();
        lineBox.setPadding(new Insets(0, 0, 0, 6));
        lineBox.getChildren().add(finalLine);
        
        milestones.getChildren().addAll(lineBox, destination);
        
        return milestones;
    }
    
    /**
     * Create weather information display
     */
    private VBox createWeatherInfo() {
        VBox weatherBox = new VBox(10);
        weatherBox.setPadding(new Insets(10));
        
        // Origin weather
        HBox originWeather = new HBox(10);
        originWeather.setAlignment(Pos.CENTER_LEFT);
        
        Label originLabel = new Label(load.getOriginCity());
        originLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        Label originTemp = new Label("72°F");
        
        Label originCond = new Label("Clear");
        
        originWeather.getChildren().addAll(originLabel, originTemp, originCond);
        
        // Destination weather
        HBox destWeather = new HBox(10);
        destWeather.setAlignment(Pos.CENTER_LEFT);
        
        Label destLabel = new Label(load.getDestCity());
        destLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        Label destTemp = new Label("68°F");
        
        Label destCond = new Label("Cloudy");
        
        destWeather.getChildren().addAll(destLabel, destTemp, destCond);
        
        // Route alerts
        VBox alertsBox = new VBox(5);
        alertsBox.getStyleClass().add("alerts-box");
        
        Label alertsLabel = new Label("Route Alerts:");
        alertsLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        Label alert1 = new Label("• Heavy rain expected in Pittsburgh area");
        alert1.setTextFill(Color.ORANGE);
        
        Label alert2 = new Label("• Construction delays on I-80 near Cleveland");
        alert2.setTextFill(Color.ORANGE);
        
        alertsBox.getChildren().addAll(alertsLabel, alert1, alert2);
        
        weatherBox.getChildren().addAll(
            new Label("Current Weather:"),
            originWeather,
            destWeather,
            new Separator(),
            alertsBox
        );
        
        return weatherBox;
    }
    
    /**
     * Create the documents tab
     */
    private VBox createDocumentsTab() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label titleLabel = new Label("Load Documents");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        // Document actions
        HBox actionsBox = new HBox(10);
        actionsBox.setAlignment(Pos.CENTER_LEFT);
        
        Button uploadBtn = new Button("Upload Document");
        uploadBtn.setOnAction(e -> uploadDocument());
        
        Button emailBtn = new Button("Email Documents");
        emailBtn.setOnAction(e -> emailDocuments());
        
        Button printDocsBtn = new Button("Print Documents");
        printDocsBtn.setOnAction(e -> printDocuments());
        
        actionsBox.getChildren().addAll(uploadBtn, emailBtn, printDocsBtn);
        
        // Documents table
        documentsTable = new TableView<>();
        documentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<DocumentItem, String> typeCol = new TableColumn<>("Document Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("documentType"));
        
        TableColumn<DocumentItem, String> nameCol = new TableColumn<>("File Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        
        TableColumn<DocumentItem, LocalDateTime> uploadedCol = new TableColumn<>("Uploaded");
        uploadedCol.setCellValueFactory(new PropertyValueFactory<>("uploadDateTime"));
        uploadedCol.setCellFactory(col -> new TableCell<DocumentItem, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(date.format(DATETIME_FORMAT));
                }
            }
        });
        
        TableColumn<DocumentItem, String> uploadedByCol = new TableColumn<>("Uploaded By");
        uploadedByCol.setCellValueFactory(new PropertyValueFactory<>("uploadedBy"));
        
        TableColumn<DocumentItem, Boolean> requiredCol = new TableColumn<>("Required");
        requiredCol.setCellValueFactory(new PropertyValueFactory<>("required"));
        requiredCol.setCellFactory(col -> new TableCell<DocumentItem, Boolean>() {
            @Override
            protected void updateItem(Boolean required, boolean empty) {
                super.updateItem(required, empty);
                if (empty || required == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    setGraphic(new CheckBox("") {{
                        setSelected(required);
                        setDisable(true);
                    }});
                }
            }
        });
        
        TableColumn<DocumentItem, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(createDocumentActionsCellFactory());
        
        documentsTable.getColumns().addAll(typeCol, nameCol, uploadedCol, uploadedByCol, requiredCol, actionsCol);
        
        // Required documents notification
        Label requiredDocsLabel = new Label("Required documents are marked with ✓");
        requiredDocsLabel.setFont(Font.font("System", FontWeight.MEDIUM, 12));
        
        content.getChildren().addAll(titleLabel, actionsBox, documentsTable, requiredDocsLabel);
        VBox.setVgrow(documentsTable, Priority.ALWAYS);
        
        return content;
    }
    
    /**
     * Create stop history tab
     */
    private VBox createStopsTab() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label titleLabel = new Label("Stop History & Tracking");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        // Stop actions
        HBox actionsBox = new HBox(10);
        actionsBox.setAlignment(Pos.CENTER_LEFT);
        
        Button addStopBtn = new Button("Add Stop");
        addStopBtn.setOnAction(e -> addStop());
        
        Button editStopBtn = new Button("Edit Stop");
        editStopBtn.setDisable(true);
        editStopBtn.setOnAction(e -> editStop());
        
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshStops());
        
        actionsBox.getChildren().addAll(addStopBtn, editStopBtn, refreshBtn);
        
        // Stops table
        stopsTable = new TableView<>();
        stopsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<StopHistoryItem, LocalDateTime> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timeCol.setCellFactory(col -> new TableCell<StopHistoryItem, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(date.format(DATETIME_FORMAT));
                }
            }
        });
        
        TableColumn<StopHistoryItem, String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        
        TableColumn<StopHistoryItem, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        TableColumn<StopHistoryItem, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        
        TableColumn<StopHistoryItem, String> reportedByCol = new TableColumn<>("Reported By");
        reportedByCol.setCellValueFactory(new PropertyValueFactory<>("reportedBy"));
        
        stopsTable.getColumns().addAll(timeCol, locationCol, statusCol, notesCol, reportedByCol);
        
        // Stop details box
        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(15);
        detailsGrid.setVgap(10);
        detailsGrid.setPadding(new Insets(15));
        detailsGrid.getStyleClass().add("details-box");
        
        stopsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newItem) -> {
            detailsGrid.getChildren().clear();
            
            if (newItem != null) {
                editStopBtn.setDisable(false);
                
                int row = 0;
                addDetailRow(detailsGrid, "Timestamp:", newItem.getTimestamp().format(DATETIME_FORMAT), row++);
                addDetailRow(detailsGrid, "Location:", newItem.getLocation(), row++);
                addDetailRow(detailsGrid, "Status:", newItem.getStatus(), row++);
                addDetailRow(detailsGrid, "Coordinates:", newItem.getCoordinates(), row++);
                
                row = 0;
                addDetailRow(detailsGrid, "Reported By:", newItem.getReportedBy(), row++, 2);
                addDetailRow(detailsGrid, "Method:", newItem.getMethod(), row++, 2);
                addDetailRow(detailsGrid, "Verified:", newItem.isVerified() ? "Yes" : "No", row++, 2);
                
                if (newItem.getNotes() != null && !newItem.getNotes().isEmpty()) {
                    Label notesLabel = new Label("Notes:");
                    notesLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
                    TextArea notesArea = new TextArea(newItem.getNotes());
                    notesArea.setEditable(false);
                    notesArea.setPrefRowCount(3);
                    notesArea.setWrapText(true);
                    
                    detailsGrid.add(notesLabel, 0, 5);
                    detailsGrid.add(notesArea, 0, 6, 4, 1);
                }
            } else {
                editStopBtn.setDisable(true);
                Label placeholder = new Label("Select a stop to view details");
                placeholder.setFont(Font.font("System", FontWeight.MEDIUM, 12));
                detailsGrid.add(placeholder, 0, 0);
            }
        });
        
        TitledPane detailsPane = new TitledPane("Stop Details", detailsGrid);
        detailsPane.setCollapsible(true);
        detailsPane.setExpanded(true);
        
        content.getChildren().addAll(titleLabel, actionsBox, stopsTable, detailsPane);
        VBox.setVgrow(stopsTable, Priority.ALWAYS);
        
        return content;
    }
    
    /**
     * Create payment tab
     */
    private VBox createPaymentTab() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label titleLabel = new Label("Payment & Billing Information");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        // Payment actions
        HBox actionsBox = new HBox(10);
        actionsBox.setAlignment(Pos.CENTER_LEFT);
        
        Button updateBtn = new Button("Update Payment Status");
        updateBtn.setOnAction(e -> updatePaymentStatus());
        
        Button invoiceBtn = new Button("Generate Invoice");
        invoiceBtn.setOnAction(e -> generateInvoice());
        
        Button emailInvoiceBtn = new Button("Email Invoice");
        emailInvoiceBtn.setOnAction(e -> emailInvoice());
        
        actionsBox.getChildren().addAll(updateBtn, invoiceBtn, emailInvoiceBtn);
        
        // Payment details
        paymentGrid = new GridPane();
        paymentGrid.setHgap(15);
        paymentGrid.setVgap(10);
        paymentGrid.setPadding(new Insets(15));
        
        int row = 0;
        addDetailRow(paymentGrid, "Rate:", formatCurrency(load.getRate()), row++);
        addDetailRow(paymentGrid, "Detention:", formatCurrency(load.getDetention()), row++);
        addDetailRow(paymentGrid, "Accessorials:", formatCurrency(load.getAccessorials()), row++);
        addDetailRow(paymentGrid, "Fuel Surcharge:", formatCurrency(load.getFuelSurcharge()), row++);
        addDetailRow(paymentGrid, "Total Charges:", formatCurrency(getTotalCharges()), row++);
        
        row = 0;
        addDetailRow(paymentGrid, "Invoice #:", load.getInvoiceNumber(), row++, 2);
        addDetailRow(paymentGrid, "Invoice Date:", 
            load.getInvoiceDate() != null ? formatDate(load.getInvoiceDate()) : "Not invoiced", row++, 2);
        addDetailRow(paymentGrid, "Payment Terms:", load.getPaymentTerms(), row++, 2);
        addDetailRow(paymentGrid, "Due Date:", 
            load.getDueDate() != null ? formatDate(load.getDueDate()) : "N/A", row++, 2);
        addDetailRow(paymentGrid, "Payment Status:", getPaymentStatusDisplay(load.getPaymentStatus()), row++, 2);
        
        Separator separator = new Separator();
        paymentGrid.add(separator, 0, row, 4, 1);
        row++;
        
        addDetailRow(paymentGrid, "Driver Pay:", formatCurrency(load.getDriverPay()), row++);
        addDetailRow(paymentGrid, "Driver Bonus:", formatCurrency(load.getDriverBonus()), row++);
        addDetailRow(paymentGrid, "Total Driver Pay:", formatCurrency(load.getDriverPay() + load.getDriverBonus()), row++);
        
        addDetailRow(paymentGrid, "Profit:", formatCurrency(getProfit()), row++, 2);
        addDetailRow(paymentGrid, "Profit %:", String.format("%.1f%%", getProfitPercentage()), row++, 2);
        
        // Payment history
        TitledPane historyPane = new TitledPane("Payment History", createPaymentHistoryTable());
        historyPane.setCollapsible(true);
        historyPane.setExpanded(true);
        
        content.getChildren().addAll(titleLabel, actionsBox, paymentGrid, historyPane);
        
        return content;
    }
    
    /**
     * Create payment history table
     */
    private TableView<PaymentHistoryItem> createPaymentHistoryTable() {
        TableView<PaymentHistoryItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<PaymentHistoryItem, LocalDateTime> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        dateCol.setCellFactory(col -> new TableCell<PaymentHistoryItem, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(date.format(DATETIME_FORMAT));
                }
            }
        });
        
        TableColumn<PaymentHistoryItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        
        TableColumn<PaymentHistoryItem, Double> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setCellFactory(col -> new TableCell<PaymentHistoryItem, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(formatCurrency(amount));
                }
            }
        });
        
        TableColumn<PaymentHistoryItem, String> referenceCol = new TableColumn<>("Reference");
        referenceCol.setCellValueFactory(new PropertyValueFactory<>("reference"));
        
        TableColumn<PaymentHistoryItem, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        
        TableColumn<PaymentHistoryItem, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("user"));
        
        table.getColumns().addAll(dateCol, typeCol, amountCol, referenceCol, notesCol, userCol);
        
        // Add sample data
        List<PaymentHistoryItem> historyItems = new ArrayList<>();
        
        if (load.getInvoiceDate() != null) {
            historyItems.add(new PaymentHistoryItem(
                LocalDateTime.of(load.getInvoiceDate(), java.time.LocalTime.of(14, 30)),
                "Invoice Created",
                load.getRate() + load.getAccessorials() + load.getDetention() + load.getFuelSurcharge(),
                load.getInvoiceNumber(),
                "Invoice generated and sent to customer",
                "system"
            ));
        }
        
        if (load.getPaymentStatus() == PaymentStatus.PAID) {
            historyItems.add(new PaymentHistoryItem(
                LocalDateTime.now().minusDays(3),
                "Payment Received",
                load.getRate() + load.getAccessorials() + load.getDetention() + load.getFuelSurcharge(),
                "PMT-8732",
                "ACH Payment received",
                "finance"
            ));
        }
        
        table.setItems(FXCollections.observableArrayList(historyItems));
        
        return table;
    }
    
    /**
     * Create notes tab
     */
    private VBox createNotesTab() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label titleLabel = new Label("Notes & Comments");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        // Load notes
        Label notesLabel = new Label("Load Notes");
        notesLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        notesArea = new TextArea(load.getNotes());
        notesArea.setPrefRowCount(6);
        notesArea.setWrapText(true);
        
        HBox notesActions = new HBox(10);
        notesActions.setAlignment(Pos.CENTER_RIGHT);
        
        Button saveNotesBtn = new Button("Save Notes");
        saveNotesBtn.setOnAction(e -> saveNotes());
        
        notesActions.getChildren().add(saveNotesBtn);
        
        // Comments
        Label commentsLabel = new Label("Comments & Updates");
        commentsLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        commentsTable = new TableView<>();
        commentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<CommentItem, LocalDateTime> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        dateCol.setCellFactory(col -> new TableCell<CommentItem, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(date.format(DATETIME_FORMAT));
                }
            }
        });
        
        TableColumn<CommentItem, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("user"));
        
        TableColumn<CommentItem, String> commentCol = new TableColumn<>("Comment");
        commentCol.setCellValueFactory(new PropertyValueFactory<>("comment"));
        commentCol.setPrefWidth(300);
        
        commentsTable.getColumns().addAll(dateCol, userCol, commentCol);
        
        // Add comment
        HBox addCommentBox = new HBox(10);
        addCommentBox.setAlignment(Pos.CENTER_LEFT);
        
        newCommentField = new TextField();
        newCommentField.setPromptText("Add a comment...");
        newCommentField.setPrefWidth(400);
        HBox.setHgrow(newCommentField, Priority.ALWAYS);
        
        Button addCommentBtn = new Button("Add Comment");
        addCommentBtn.setOnAction(e -> addComment());
        
        addCommentBox.getChildren().addAll(newCommentField, addCommentBtn);
        
        content.getChildren().addAll(
            titleLabel, notesLabel, notesArea, notesActions,
            new Separator(), commentsLabel, commentsTable, addCommentBox
        );
        VBox.setVgrow(commentsTable, Priority.ALWAYS);
        
        return content;
    }
    
    /**
     * Add detail row to grid
     */
    private void addDetailRow(GridPane grid, String label, String value, int row) {
        addDetailRow(grid, label, value, row, 0);
    }
    
    /**
     * Add detail row to grid with column offset
     */
    private void addDetailRow(GridPane grid, String label, String value, int row, int colOffset) {
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font("System", FontWeight.BOLD, 12));
        Label valueNode = new Label(value != null ? value : "");
        
        grid.add(labelNode, colOffset, row);
        grid.add(valueNode, colOffset + 1, row);
    }
    
    /**
     * Set status color based on load status
     */
    private void setStatusColor(Label statusLabel, LoadStatus status) {
        String color;
        
        switch (status) {
            case UNASSIGNED:
                color = "orange";
                break;
            case ASSIGNED:
                color = "blue";
                break;
            case IN_TRANSIT:
                color = "green";
                break;
            case DELIVERED:
                color = "purple";
                break;
            case COMPLETED:
                color = "darkgreen";
                break;
            case CANCELLED:
                color = "red";
                break;
            case INVOICED:
                color = "darkorange";
                break;
            case PAID:
                color = "teal";
                break;
            default:
                color = "black";
        }
        
        statusLabel.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; " +
            "-fx-padding: 5 10; -fx-background-radius: 3;", color));
    }
    
    /**
     * Create cell factory for document actions
     */
    private Callback<TableColumn<DocumentItem, Void>, TableCell<DocumentItem, Void>> createDocumentActionsCellFactory() {
        return new Callback<>() {
            @Override
            public TableCell<DocumentItem, Void> call(TableColumn<DocumentItem, Void> param) {
                return new TableCell<>() {
                    private final Button viewBtn = new Button("View");
                    private final Button downloadBtn = new Button("Download");
                    private final HBox pane = new HBox(5, viewBtn, downloadBtn);
                    
                    {
                        viewBtn.setOnAction(event -> {
                            DocumentItem data = getTableView().getItems().get(getIndex());
                            viewDocument(data);
                        });
                        
                        downloadBtn.setOnAction(event -> {
                            DocumentItem data = getTableView().getItems().get(getIndex());
                            downloadDocument(data);
                        });
                        
                        pane.setAlignment(Pos.CENTER);
                    }
                    
                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        setGraphic(empty ? null : pane);
                    }
                };
            }
        };
    }
    
    /**
     * Load data for the dialog
     */
    private void loadData() {
        // Load documents
        documents = FXCollections.observableArrayList();
        documents.add(new DocumentItem(
            "Bill of Lading", "BOL_" + load.getLoadNumber() + ".pdf", 
            LocalDateTime.now().minusDays(1), "Driver", true));
        documents.add(new DocumentItem(
            "Rate Confirmation", "RC_" + load.getLoadNumber() + ".pdf", 
            LocalDateTime.now().minusDays(3), CURRENT_USER, true));
        documents.add(new DocumentItem(
            "Proof of Delivery", "POD_" + load.getLoadNumber() + ".pdf", 
            LocalDateTime.now().minusHours(6), "Driver", true));
        
        documentsTable.setItems(documents);
        
        // Load stop history
        stops = FXCollections.observableArrayList();
        stops.add(new StopHistoryItem(
            LocalDateTime.now().minusDays(2), "Chicago, IL", "Pickup",
            "41.8781,-87.6298", "Loaded on time", "Driver", "Mobile App", true));
        stops.add(new StopHistoryItem(
            LocalDateTime.now().minusDays(1).plusHours(8), "Rest Area I-80", "Rest Break",
            "41.2333,-88.1234", "30 minute break", "Driver", "Mobile App", true));
        stops.add(new StopHistoryItem(
            LocalDateTime.now().minusDays(1).plusHours(15), "Truck Stop", "Fuel Stop",
            "40.9876,-85.4321", "Refueled", "Driver", "Mobile App", true));
        stops.add(new StopHistoryItem(
            LocalDateTime.now().minusHours(6), "Cleveland, OH", "En Route",
            "41.4993,-81.6944", "On schedule", "GPS", "Automated", true));
        
        stopsTable.setItems(stops);
        
        // Load comments
        comments = FXCollections.observableArrayList();
        comments.add(new CommentItem(
            LocalDateTime.now().minusDays(3), "dispatch", 
            "Load assigned to driver John Smith"));
        comments.add(new CommentItem(
            LocalDateTime.now().minusDays(2), "Driver", 
            "Picked up load, everything looks good"));
        comments.add(new CommentItem(
            LocalDateTime.now().minusDays(1), "dispatch", 
            "Driver reports ETA may be delayed by 1 hour due to traffic"));
        comments.add(new CommentItem(
            LocalDateTime.now().minusHours(12), "system", 
            "Automated update: Load is 75% complete"));
        
        commentsTable.setItems(comments);
    }
    
    /**
     * Print the load details
     */
    private void printLoad() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(getDialogPane().getScene().getWindow())) {
            // Create a printable node with formatted content
            VBox printContent = new VBox(20);
            printContent.setPadding(new Insets(20));
            
            Label title = new Label("LOAD DETAILS: #" + load.getLoadNumber());
            title.setFont(Font.font("System", FontWeight.BOLD, 18));
            
            GridPane detailsGridCopy = new GridPane();
            detailsGridCopy.setHgap(15);
            detailsGridCopy.setVgap(10);
            
            int row = 0;
            addDetailRow(detailsGridCopy, "Customer:", load.getCustomer(), row++);
            addDetailRow(detailsGridCopy, "Origin:", load.getOriginCity() + ", " + load.getOriginState(), row++);
            addDetailRow(detailsGridCopy, "Destination:", load.getDestCity() + ", " + load.getDestState(), row++);
            addDetailRow(detailsGridCopy, "Pickup Date:", formatDate(load.getPickupDate()), row++);
            addDetailRow(detailsGridCopy, "Delivery Date:", formatDate(load.getDeliveryDate()), row++);
            addDetailRow(detailsGridCopy, "Status:", load.getStatus().toString(), row++);
            
            if (load.getDriver() != null) {
                addDetailRow(detailsGridCopy, "Driver:", load.getDriver().getName(), row++);
            }
            
            addDetailRow(detailsGridCopy, "Commodity:", load.getCommodity(), row++);
            addDetailRow(detailsGridCopy, "Weight:", String.format("%,d lbs", load.getWeight()), row++);
            addDetailRow(detailsGridCopy, "Rate:", formatCurrency(load.getRate()), row++);
            
            printContent.getChildren().addAll(title, detailsGridCopy);
            
            boolean success = job.printPage(printContent);
            if (success) {
                job.endJob();
                statusLabel.setText("Print job submitted");
            }
        }
    }
    
    /**
     * Assign load to driver
     */
    private void assignLoad() {
        AssignLoadDialog dialog = new AssignLoadDialog(controller, getDialogPane().getScene().getWindow());
        dialog.preSelectLoad(load);
        dialog.showAndWait().ifPresent(result -> {
            if (result) {
                // Reload load details with updated information
                statusLabel.setText("Load assigned successfully");
                
                // In a real implementation, we would reload the load data
                // For this example, we'll just update the dialog to reflect changes
                if (load.getDriver() != null) {
                    // Update driver section
                    TitledPane driverPane = (TitledPane) tabPane.getTabs().get(0).getContent()
                        .lookup(".titled-pane:nth-child(3)");
                    if (driverPane != null) {
                        GridPane newDriverGrid = createDriverSection();
                        driverPane.setContent(newDriverGrid);
                    }
                }
            }
        });
    }
    
    /**
     * Edit the load
     */
    private void editLoad() {
        // In a real implementation, show a dialog to edit the load details
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Edit Load");
        alert.setHeaderText("Edit Load #" + load.getLoadNumber());
        alert.setContentText("Load editing functionality would be shown here.");
        alert.showAndWait();
    }
    
    /**
     * Upload a document
     */
    private void uploadDocument() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Upload Document");
        dialog.setHeaderText("Upload a document for Load #" + load.getLoadNumber());
        dialog.setContentText("Document Type:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().isEmpty()) {
            documents.add(new DocumentItem(
                result.get(), "DOC_" + load.getLoadNumber() + "_" + System.currentTimeMillis() + ".pdf",
                LocalDateTime.now(), CURRENT_USER, false
            ));
            
            statusLabel.setText("Document uploaded");
        }
    }
    
    /**
     * Email documents
     */
    private void emailDocuments() {
        TextInputDialog dialog = new TextInputDialog(load.getContactEmail());
        dialog.setTitle("Email Documents");
        dialog.setHeaderText("Email Documents for Load #" + load.getLoadNumber());
        dialog.setContentText("Email Address:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().isEmpty()) {
            // In a real implementation, email the documents
            statusLabel.setText("Documents emailed to " + result.get());
        }
    }
    
    /**
     * Print documents
     */
    private void printDocuments() {
        // In a real implementation, print the selected documents
        statusLabel.setText("Documents sent to printer");
    }
    
    /**
     * View a document
     */
    private void viewDocument(DocumentItem document) {
        // In a real implementation, open the document viewer
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Document Viewer");
        alert.setHeaderText("Viewing " + document.getDocumentType());
        alert.setContentText("Document viewer would open here for: " + document.getFileName());
        alert.showAndWait();
    }
    
    /**
     * Download a document
     */
    private void downloadDocument(DocumentItem document) {
        // In a real implementation, download the document
        statusLabel.setText("Downloading " + document.getFileName());
    }
    
        /**
     * Add a stop
     */
    private void addStop() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Stop");
        dialog.setHeaderText("Add Stop for Load #" + load.getLoadNumber());
        dialog.setContentText("Location:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().isEmpty()) {
            // Show details dialog to complete stop information
            Dialog<StopHistoryItem> stopDialog = new Dialog<>();
            stopDialog.setTitle("Stop Details");
            stopDialog.setHeaderText("Enter Stop Details");
            
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20));
            
            TextField locationField = new TextField(result.get());
            locationField.setPrefWidth(300);
            
            ComboBox<String> statusCombo = new ComboBox<>();
            statusCombo.getItems().addAll("Pickup", "Delivery", "Rest Break", "Fuel Stop", "En Route", "Delay");
            statusCombo.setValue("En Route");
            
            TextField coordinatesField = new TextField("41.8781,-87.6298"); // Simulated GPS
            TextArea notesArea = new TextArea();
            notesArea.setPrefRowCount(3);
            
            grid.add(new Label("Location:"), 0, 0);
            grid.add(locationField, 1, 0);
            grid.add(new Label("Status:"), 0, 1);
            grid.add(statusCombo, 1, 1);
            grid.add(new Label("Coordinates:"), 0, 2);
            grid.add(coordinatesField, 1, 2);
            grid.add(new Label("Notes:"), 0, 3);
            grid.add(notesArea, 1, 3);
            
            stopDialog.getDialogPane().setContent(grid);
            stopDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            stopDialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    return new StopHistoryItem(
                        LocalDateTime.now(), 
                        locationField.getText(), 
                        statusCombo.getValue(),
                        coordinatesField.getText(),
                        notesArea.getText(),
                        CURRENT_USER,
                        "Manual Entry",
                        true
                    );
                }
                return null;
            });
            
            Optional<StopHistoryItem> stopResult = stopDialog.showAndWait();
            stopResult.ifPresent(stop -> {
                stops.add(0, stop); // Add to beginning of list (most recent first)
                stopsTable.refresh();
                stopsTable.getSelectionModel().select(0);
                statusLabel.setText("Stop added: " + stop.getLocation());
            });
        }
    }
    
    /**
     * Edit a stop
     */
    private void editStop() {
        StopHistoryItem selectedStop = stopsTable.getSelectionModel().getSelectedItem();
        if (selectedStop == null) return;
        
        Dialog<StopHistoryItem> dialog = new Dialog<>();
        dialog.setTitle("Edit Stop");
        dialog.setHeaderText("Edit Stop Details");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        TextField locationField = new TextField(selectedStop.getLocation());
        locationField.setPrefWidth(300);
        
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Pickup", "Delivery", "Rest Break", "Fuel Stop", "En Route", "Delay");
        statusCombo.setValue(selectedStop.getStatus());
        
        TextField coordinatesField = new TextField(selectedStop.getCoordinates());
        
        TextArea notesArea = new TextArea(selectedStop.getNotes());
        notesArea.setPrefRowCount(3);
        
        grid.add(new Label("Location:"), 0, 0);
        grid.add(locationField, 1, 0);
        grid.add(new Label("Status:"), 0, 1);
        grid.add(statusCombo, 1, 1);
        grid.add(new Label("Coordinates:"), 0, 2);
        grid.add(coordinatesField, 1, 2);
        grid.add(new Label("Notes:"), 0, 3);
        grid.add(notesArea, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new StopHistoryItem(
                    selectedStop.getTimestamp(),
                    locationField.getText(),
                    statusCombo.getValue(),
                    coordinatesField.getText(),
                    notesArea.getText(),
                    selectedStop.getReportedBy(),
                    selectedStop.getMethod(),
                    true
                );
            }
            return null;
        });
        
        Optional<StopHistoryItem> result = dialog.showAndWait();
        result.ifPresent(updatedStop -> {
            int index = stops.indexOf(selectedStop);
            stops.set(index, updatedStop);
            stopsTable.refresh();
            stopsTable.getSelectionModel().select(index);
            statusLabel.setText("Stop updated: " + updatedStop.getLocation());
        });
    }
    
    /**
     * Refresh stops data
     */
    private void refreshStops() {
        // In a real implementation, this would reload data from the service
        statusLabel.setText("Stop history refreshed");
    }
    
    /**
     * Update payment status
     */
    private void updatePaymentStatus() {
        Dialog<PaymentStatus> dialog = new Dialog<>();
        dialog.setTitle("Update Payment Status");
        dialog.setHeaderText("Update Payment Status for Load #" + load.getLoadNumber());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        ComboBox<PaymentStatus> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll(PaymentStatus.values());
        statusCombo.setValue(load.getPaymentStatus());
        
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField amountField = new TextField(String.valueOf(load.getRate()));
        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        
        grid.add(new Label("Payment Status:"), 0, 0);
        grid.add(statusCombo, 1, 0);
        grid.add(new Label("Payment Date:"), 0, 1);
        grid.add(datePicker, 1, 1);
        grid.add(new Label("Amount:"), 0, 2);
        grid.add(amountField, 1, 2);
        grid.add(new Label("Notes:"), 0, 3);
        grid.add(notesArea, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return statusCombo.getValue();
            }
            return null;
        });
        
        Optional<PaymentStatus> result = dialog.showAndWait();
        result.ifPresent(status -> {
            // In a real implementation, this would update the payment status in the database
            // For this example, we'll simulate updating the load
            load.setPaymentStatus(status);
            
            // Update payment information display
            GridPane newPaymentGrid = createPaymentSection();
            int paymentTabIndex = tabPane.getTabs().indexOf(tabPane.getTabs().stream()
                .filter(tab -> tab.getText().equals("Payment & Billing"))
                .findFirst().orElse(null));
                
            if (paymentTabIndex >= 0) {
                VBox paymentContent = (VBox) tabPane.getTabs().get(paymentTabIndex).getContent();
                paymentContent.getChildren().set(2, paymentGrid);
            }
            
            statusLabel.setText("Payment status updated: " + status.toString());
        });
    }
    
    /**
     * Generate an invoice
     */
    private void generateInvoice() {
        if (load.getInvoiceNumber() != null && !load.getInvoiceNumber().isEmpty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Regenerate Invoice");
            confirm.setHeaderText("Invoice Already Exists");
            confirm.setContentText("This load already has invoice #" + load.getInvoiceNumber() + 
                ". Do you want to regenerate the invoice?");
                
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() != ButtonType.OK) {
                return;
            }
        }
        
        // Generate new invoice number if needed
        if (load.getInvoiceNumber() == null || load.getInvoiceNumber().isEmpty()) {
            load.setInvoiceNumber("INV-" + load.getLoadNumber() + "-" + 
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        }
        
        load.setInvoiceDate(LocalDate.now());
        load.setPaymentStatus(PaymentStatus.INVOICED);
        
        // In a real implementation, this would actually generate a PDF
        Task<Void> generateTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Generating invoice...");
                // Simulate processing time
                Thread.sleep(1500);
                return null;
            }
        };
        
        generateTask.setOnSucceeded(e -> {
            statusLabel.setText("Invoice generated: " + load.getInvoiceNumber());
            // Update the UI
            TabPane tabPane = (TabPane) getDialogPane().getContent().lookup(".tab-pane");
            if (tabPane != null) {
                Tab paymentTab = tabPane.getTabs().stream()
                    .filter(tab -> tab.getText().equals("Payment & Billing"))
                    .findFirst().orElse(null);
                    
                if (paymentTab != null) {
                    VBox content = (VBox) paymentTab.getContent();
                    GridPane newGrid = createPaymentSection();
                    content.getChildren().set(2, newGrid);
                }
            }
        });
        
        generateTask.setOnFailed(e -> {
            statusLabel.setText("Invoice generation failed");
        });
        
        statusLabel.setText("Generating invoice...");
        Thread thread = new Thread(generateTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    /**
     * Email an invoice
     */
    private void emailInvoice() {
        if (load.getInvoiceNumber() == null || load.getInvoiceNumber().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Invoice");
            alert.setHeaderText("No Invoice Available");
            alert.setContentText("Please generate an invoice first.");
            alert.showAndWait();
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog(load.getContactEmail());
        dialog.setTitle("Email Invoice");
        dialog.setHeaderText("Email Invoice #" + load.getInvoiceNumber());
        dialog.setContentText("Email Address:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().isEmpty()) {
            // In a real implementation, this would email the invoice
            CompletableFuture.runAsync(() -> {
                try {
                    // Simulate email sending
                    Thread.sleep(1000);
                    Platform.runLater(() -> 
                        statusLabel.setText("Invoice emailed to " + result.get()));
                } catch (InterruptedException e) {
                    Platform.runLater(() -> 
                        statusLabel.setText("Failed to email invoice"));
                }
            });
        }
    }
    
    /**
     * Save notes
     */
    private void saveNotes() {
        String newNotes = notesArea.getText();
        load.setNotes(newNotes);
        
        // In a real implementation, this would update the notes in the database
        statusLabel.setText("Notes saved");
    }
    
    /**
     * Add a comment
     */
    private void addComment() {
        String comment = newCommentField.getText().trim();
        if (comment.isEmpty()) return;
        
        comments.add(0, new CommentItem(LocalDateTime.now(), CURRENT_USER, comment));
        newCommentField.clear();
        
        // In a real implementation, this would save the comment to the database
        statusLabel.setText("Comment added");
    }
    
    /**
     * Create payment section
     */
    private GridPane createPaymentSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        
        int row = 0;
        addDetailRow(grid, "Rate:", formatCurrency(load.getRate()), row++);
        addDetailRow(grid, "Detention:", formatCurrency(load.getDetention()), row++);
        addDetailRow(grid, "Accessorials:", formatCurrency(load.getAccessorials()), row++);
        addDetailRow(grid, "Fuel Surcharge:", formatCurrency(load.getFuelSurcharge()), row++);
        addDetailRow(grid, "Total Charges:", formatCurrency(getTotalCharges()), row++);
        
        row = 0;
        addDetailRow(grid, "Invoice #:", load.getInvoiceNumber() != null ? load.getInvoiceNumber() : "Not invoiced", row++, 2);
        addDetailRow(grid, "Invoice Date:", 
            load.getInvoiceDate() != null ? formatDate(load.getInvoiceDate()) : "N/A", row++, 2);
        addDetailRow(grid, "Payment Terms:", load.getPaymentTerms(), row++, 2);
        addDetailRow(grid, "Due Date:", 
            load.getDueDate() != null ? formatDate(load.getDueDate()) : "N/A", row++, 2);
        addDetailRow(grid, "Payment Status:", getPaymentStatusDisplay(load.getPaymentStatus()), row++, 2);
        
        Separator separator = new Separator();
        grid.add(separator, 0, row, 4, 1);
        row++;
        
        addDetailRow(grid, "Driver Pay:", formatCurrency(load.getDriverPay()), row++);
        addDetailRow(grid, "Driver Bonus:", formatCurrency(load.getDriverBonus()), row++);
        addDetailRow(grid, "Total Driver Pay:", formatCurrency(load.getDriverPay() + load.getDriverBonus()), row++);
        
        addDetailRow(grid, "Profit:", formatCurrency(getProfit()), row++, 2);
        addDetailRow(grid, "Profit %:", String.format("%.1f%%", getProfitPercentage()), row++, 2);
        
        return grid;
    }
    
    // Helper methods for calculations and formatting
    
    /**
     * Get total charges
     */
    private double getTotalCharges() {
        return load.getRate() + load.getDetention() + load.getAccessorials() + load.getFuelSurcharge();
    }
    
    /**
     * Get profit
     */
    private double getProfit() {
        return getTotalCharges() - load.getDriverPay() - load.getDriverBonus();
    }
    
    /**
     * Get profit percentage
     */
    private double getProfitPercentage() {
        double totalCharges = getTotalCharges();
        if (totalCharges <= 0) return 0;
        
        return (getProfit() / totalCharges) * 100;
    }
    
    /**
     * Format currency
     */
    private String formatCurrency(double value) {
        return String.format("$%.2f", value);
    }
    
    /**
     * Format date
     */
    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DATE_FORMAT);
    }
    
    /**
     * Format phone number
     */
    private String formatPhoneNumber(String phone) {
        if (phone == null || phone.length() != 10) return phone;
        
        return String.format("(%s) %s-%s", 
            phone.substring(0, 3), 
            phone.substring(3, 6), 
            phone.substring(6)
        );
    }
    
    /**
     * Get payment status display
     */
    private String getPaymentStatusDisplay(PaymentStatus status) {
        if (status == null) return "N/A";
        
        switch (status) {
            case NEW: return "New";
            case INVOICED: return "Invoiced";
            case PARTIAL: return "Partial Payment";
            case PAID: return "Paid";
            case OVERDUE: return "Overdue";
            case DISPUTE: return "In Dispute";
            default: return status.toString();
        }
    }
    
    /**
     * Estimate progress of a load
     */
    private double estimateProgress(Load load) {
        if (load.getStatus() == LoadStatus.COMPLETED ||
            load.getStatus() == LoadStatus.DELIVERED ||
            load.getStatus() == LoadStatus.PAID) {
            return 1.0;
        } else if (load.getStatus() == LoadStatus.UNASSIGNED) {
            return 0.0;
        }
        
        // Calculate based on dates
        LocalDate today = LocalDate.now();
        LocalDate pickup = load.getPickupDate();
        LocalDate delivery = load.getDeliveryDate();
        
        if (pickup == null || delivery == null) return 0.5;
        
        long totalDays = ChronoUnit.DAYS.between(pickup, delivery);
        if (totalDays <= 0) return 0.5;
        
        long daysSincePickup = ChronoUnit.DAYS.between(pickup, today);
        
        double progress = (double) daysSincePickup / totalDays;
        return Math.min(1.0, Math.max(0.0, progress));
    }
    
    /**
     * Estimate ETA of a load
     */
    private LocalDateTime estimateETA(Load load) {
        if (load.getStatus() == LoadStatus.COMPLETED ||
            load.getStatus() == LoadStatus.DELIVERED ||
            load.getStatus() == LoadStatus.PAID) {
            return null;
        }
        
        // Simple estimation: calculate ETA based on progress
        double progress = estimateProgress(load);
        double remainingProgress = 1.0 - progress;
        
        LocalDate pickup = load.getPickupDate();
        LocalDate delivery = load.getDeliveryDate();
        
        if (pickup == null || delivery == null) return null;
        
        long totalDays = ChronoUnit.DAYS.between(pickup, delivery);
        if (totalDays <= 0) return null;
        
        long remainingDays = Math.round(totalDays * remainingProgress);
        
        return LocalDateTime.now().plusDays(remainingDays);
    }
    
    // Document model class
    public static class DocumentItem {
        private final String documentType;
        private final String fileName;
        private final LocalDateTime uploadDateTime;
        private final String uploadedBy;
        private final boolean required;
        
        public DocumentItem(String documentType, String fileName, 
                           LocalDateTime uploadDateTime, String uploadedBy, 
                           boolean required) {
            this.documentType = documentType;
            this.fileName = fileName;
            this.uploadDateTime = uploadDateTime;
            this.uploadedBy = uploadedBy;
            this.required = required;
        }
        
        public String getDocumentType() { return documentType; }
        public String getFileName() { return fileName; }
        public LocalDateTime getUploadDateTime() { return uploadDateTime; }
        public String getUploadedBy() { return uploadedBy; }
        public boolean isRequired() { return required; }
    }
    
    // Stop history model class
    public static class StopHistoryItem {
        private final LocalDateTime timestamp;
        private final String location;
        private final String status;
        private final String coordinates;
        private final String notes;
        private final String reportedBy;
        private final String method;
        private final boolean verified;
        
        public StopHistoryItem(LocalDateTime timestamp, String location, String status,
                             String coordinates, String notes, String reportedBy,
                             String method, boolean verified) {
            this.timestamp = timestamp;
            this.location = location;
            this.status = status;
            this.coordinates = coordinates;
            this.notes = notes;
            this.reportedBy = reportedBy;
            this.method = method;
            this.verified = verified;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getLocation() { return location; }
        public String getStatus() { return status; }
        public String getCoordinates() { return coordinates; }
        public String getNotes() { return notes; }
        public String getReportedBy() { return reportedBy; }
        public String getMethod() { return method; }
        public boolean isVerified() { return verified; }
    }
    
    // Payment history model class
    public static class PaymentHistoryItem {
        private final LocalDateTime timestamp;
        private final String type;
        private final double amount;
        private final String reference;
        private final String notes;
        private final String user;
        
        public PaymentHistoryItem(LocalDateTime timestamp, String type, double amount,
                                String reference, String notes, String user) {
            this.timestamp = timestamp;
            this.type = type;
            this.amount = amount;
            this.reference = reference;
            this.notes = notes;
            this.user = user;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getType() { return type; }
        public double getAmount() { return amount; }
        public String getReference() { return reference; }
        public String getNotes() { return notes; }
        public String getUser() { return user; }
    }
    
    // Comment model class
    public static class CommentItem {
        private final LocalDateTime timestamp;
        private final String user;
        private final String comment;
        
        public CommentItem(LocalDateTime timestamp, String user, String comment) {
            this.timestamp = timestamp;
            this.user = user;
            this.comment = comment;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getUser() { return user; }
        public String getComment() { return comment; }
    }
    
    // Line class for drawing the vertical lines in the milestones list
    public static class Line extends javafx.scene.shape.Line {
        public Line() {
            super();
        }
    }
}