package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Main view for dispatcher operations
 */
public class DispatcherView {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherView.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    private final DispatcherManager manager;
    private final ObservableList<DriverAvailability> driverList = FXCollections.observableArrayList();
    
    private BorderPane root;
    private TableView<DriverAvailability> driverTable;
    private Label statsLabel;
    private TextField searchField;
    private ComboBox<DriverAvailability.AvailabilityStatus> statusFilter;
    
    public DispatcherView() {
        this.manager = new DispatcherManager();
        initializeView();
        refreshData();
    }
    
    private void initializeView() {
        root = new BorderPane();
        root.setPadding(new Insets(10));
        
        // Header
        VBox header = createHeader();
        root.setTop(header);
        
        // Center - Driver table
        VBox centerContent = createCenterContent();
        root.setCenter(centerContent);
        
        // Bottom - Status bar
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(0, 0, 10, 0));
        
        // Title
        Label titleLabel = new Label("Dispatcher Console");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        // Toolbar
        HBox toolbar = createToolbar();
        
        // Filters
        HBox filters = createFilters();
        
        header.getChildren().addAll(titleLabel, toolbar, filters);
        return header;
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshData());
        
        Button updateStatusBtn = new Button("Update Status");
        updateStatusBtn.setOnAction(e -> showUpdateStatusDialog());
        
        Button assignLoadBtn = new Button("Assign Load");
        assignLoadBtn.setOnAction(e -> showAssignLoadDialog());
        
        Button viewHistoryBtn = new Button("View History");
        viewHistoryBtn.setOnAction(e -> showDriverHistory());
        
        Button settingsBtn = new Button("Settings");
        settingsBtn.setOnAction(e -> showSettingsDialog());
        
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);
        
        Button generateReportBtn = new Button("Generate Report");
        generateReportBtn.setOnAction(e -> generateDispatcherReport());
        
        toolbar.getChildren().addAll(
            refreshBtn, updateStatusBtn, assignLoadBtn, viewHistoryBtn, 
            separator, settingsBtn, generateReportBtn
        );
        
        return toolbar;
    }
    
    private HBox createFilters() {
        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);
        
        Label searchLabel = new Label("Search:");
        searchField = new TextField();
        searchField.setPromptText("Driver name or truck...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterDrivers());
        
        Label statusLabel = new Label("Status:");
        statusFilter = new ComboBox<>();
        statusFilter.getItems().add(null); // All
        statusFilter.getItems().addAll(DriverAvailability.AvailabilityStatus.values());
        statusFilter.setPromptText("All Statuses");
        statusFilter.setOnAction(e -> filterDrivers());
        
        filters.getChildren().addAll(searchLabel, searchField, statusLabel, statusFilter);
        return filters;
    }
    
    private VBox createCenterContent() {
        VBox content = new VBox(10);
        
        // Statistics panel
        statsLabel = new Label();
        statsLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        updateStatistics();
        
        // Driver table
        driverTable = createDriverTable();
        VBox.setVgrow(driverTable, Priority.ALWAYS);
        
        content.getChildren().addAll(statsLabel, driverTable);
        return content;
    }
    
    private TableView<DriverAvailability> createDriverTable() {
        TableView<DriverAvailability> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Driver column
        TableColumn<DriverAvailability, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(data.getValue().getDriverName()));
        driverCol.setMinWidth(150);
        
        // Truck column
        TableColumn<DriverAvailability, String> truckCol = new TableColumn<>("Truck");
        truckCol.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(data.getValue().getTruckUnit()));
        driverCol.setMinWidth(80);
        
        // Status column with color
        TableColumn<DriverAvailability, DriverAvailability.AvailabilityStatus> statusCol = 
            new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getStatus()));
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(DriverAvailability.AvailabilityStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status.getDisplayName());
                    setStyle("-fx-background-color: " + status.getColor() + "; -fx-font-weight: bold;");
                }
            }
        });
        statusCol.setMinWidth(100);
        
        // Location column
        TableColumn<DriverAvailability, String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(data.getValue().getCurrentLocation()));
        locationCol.setMinWidth(150);
        
        // Return date column
        TableColumn<DriverAvailability, String> returnCol = new TableColumn<>("Expected Return");
        returnCol.setCellValueFactory(data -> {
            LocalDate date = data.getValue().getExpectedReturnDate();
            return new javafx.beans.property.SimpleStringProperty(
                date != null ? date.format(DATE_FORMATTER) : "");
        });
        returnCol.setMinWidth(100);
        
        // Notes column
        TableColumn<DriverAvailability, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(data.getValue().getNotes()));
        
        table.getColumns().addAll(driverCol, truckCol, statusCol, locationCol, returnCol, notesCol);
        table.setItems(driverList);
        
        // Double-click handler
        table.setRowFactory(tv -> {
            TableRow<DriverAvailability> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showDriverDetails(row.getItem());
                }
            });
            return row;
        });
        
        return table;
    }
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        
        Label statusLabel = new Label("Ready");
        Label timeLabel = new Label();
        
        // Update time every second
        Platform.runLater(() -> {
            javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                    timeLabel.setText(LocalDate.now().format(DATE_FORMATTER) + " " + 
                                    java.time.LocalTime.now().format(
                                        java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                })
            );
            timeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
            timeline.play();
        });
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        statusBar.getChildren().addAll(statusLabel, spacer, timeLabel);
        return statusBar;
    }
    
    private void refreshData() {
        logger.info("Refreshing dispatcher data");
        manager.initializeDriverAvailability();
        List<DriverAvailability> drivers = manager.getAllDriverAvailabilities();
        driverList.setAll(drivers);
        updateStatistics();
    }
    
    private void filterDrivers() {
        // Implementation for filtering
        String searchText = searchField.getText().toLowerCase();
        DriverAvailability.AvailabilityStatus selectedStatus = statusFilter.getValue();
        
        List<DriverAvailability> allDrivers = manager.getAllDriverAvailabilities();
        List<DriverAvailability> filtered = allDrivers.stream()
            .filter(d -> {
                boolean matchesSearch = searchText.isEmpty() ||
                    d.getDriverName().toLowerCase().contains(searchText) ||
                    d.getTruckUnit().toLowerCase().contains(searchText);
                boolean matchesStatus = selectedStatus == null || d.getStatus() == selectedStatus;
                return matchesSearch && matchesStatus;
            })
            .collect(java.util.stream.Collectors.toList());
        
        driverList.setAll(filtered);
    }
    
    private void updateStatistics() {
        DispatcherManager.DispatcherStatistics stats = manager.getStatistics();
        String statsText = String.format(
            "Total Drivers: %d | Available: %d (%.1f%%) | On Road: %d | Off Duty: %d",
            stats.getTotalDrivers(),
            stats.getAvailableDrivers(),
            stats.getAvailabilityPercentage(),
            stats.getOnRoadDrivers(),
            stats.getOffDutyDrivers()
        );
        statsLabel.setText(statsText);
    }
    
    private void showUpdateStatusDialog() {
        DriverAvailability selected = driverTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select a driver to update status.");
            return;
        }
        
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Update Driver Status");
        dialog.setHeaderText("Update status for: " + selected.getDriverName());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        ComboBox<DriverAvailability.AvailabilityStatus> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll(DriverAvailability.AvailabilityStatus.values());
        statusCombo.setValue(selected.getStatus());
        
        TextField locationField = new TextField(selected.getCurrentLocation());
        DatePicker returnPicker = new DatePicker(selected.getExpectedReturnDate());
        TextArea notesArea = new TextArea(selected.getNotes());
        notesArea.setPrefRowCount(3);
        
        grid.add(new Label("Status:"), 0, 0);
        grid.add(statusCombo, 1, 0);
        grid.add(new Label("Location:"), 0, 1);
        grid.add(locationField, 1, 1);
        grid.add(new Label("Expected Return:"), 0, 2);
        grid.add(returnPicker, 1, 2);
        grid.add(new Label("Notes:"), 0, 3);
        grid.add(notesArea, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            manager.updateDriverStatus(
                selected.getDriverId(),
                statusCombo.getValue(),
                locationField.getText(),
                returnPicker.getValue(),
                notesArea.getText()
            );
            refreshData();
        }
    }
    
    private void showAssignLoadDialog() {
        // Implementation for load assignment dialog
        showAlert("Assign Load", "Load assignment functionality to be implemented.");
    }
    
    private void showDriverHistory() {
        DriverAvailability selected = driverTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select a driver to view history.");
            return;
        }
        showAlert("Driver History", "History for " + selected.getDriverName() + " to be implemented.");
    }
    
    private void showDriverDetails(DriverAvailability driver) {
        // Implementation for driver details view
        showAlert("Driver Details", "Details for " + driver.getDriverName() + " to be implemented.");
    }
    
    private void showSettingsDialog() {
        DispatcherSettingsDialog dialog = new DispatcherSettingsDialog();
        dialog.showAndWait();
    }
    
    private void generateDispatcherReport() {
        // Implementation for report generation
        showAlert("Generate Report", "Report generation to be implemented.");
    }
    
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    public BorderPane getRoot() {
        return root;
    }
    
    /**
     * Settings dialog for dispatcher configuration
     */
    private class DispatcherSettingsDialog extends Dialog<ButtonType> {
        public DispatcherSettingsDialog() {
            setTitle("Dispatcher Settings");
            setHeaderText("Configure dispatcher information");
            
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20));
            
            TextField nameField = new TextField(DispatcherSettings.getDispatcherName());
            TextField phoneField = new TextField(DispatcherSettings.getDispatcherPhone());
            TextField emailField = new TextField(DispatcherSettings.getDispatcherEmail());
            TextField faxField = new TextField(DispatcherSettings.getDispatcherFax());
            TextField woField = new TextField(DispatcherSettings.getWONumber());
            TextField companyField = new TextField(DispatcherSettings.getCompanyName());
            
            int row = 0;
            grid.add(new Label("Dispatcher Name:"), 0, row);
            grid.add(nameField, 1, row++);
            grid.add(new Label("Phone:"), 0, row);
            grid.add(phoneField, 1, row++);
            grid.add(new Label("Email:"), 0, row);
            grid.add(emailField, 1, row++);
            grid.add(new Label("Fax:"), 0, row);
            grid.add(faxField, 1, row++);
            grid.add(new Label("WO Number:"), 0, row);
            grid.add(woField, 1, row++);
            grid.add(new Label("Company Name:"), 0, row);
            grid.add(companyField, 1, row++);
            
            getDialogPane().setContent(grid);
            getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            setResultConverter(buttonType -> {
                if (buttonType == ButtonType.OK) {
                    DispatcherSettings.setDispatcherName(nameField.getText());
                    DispatcherSettings.setDispatcherPhone(phoneField.getText());
                    DispatcherSettings.setDispatcherEmail(emailField.getText());
                    DispatcherSettings.setDispatcherFax(faxField.getText());
                    DispatcherSettings.setWONumber(woField.getText());
                    DispatcherSettings.setCompanyName(companyField.getText());
                }
                return buttonType;
            });
        }
    }
}