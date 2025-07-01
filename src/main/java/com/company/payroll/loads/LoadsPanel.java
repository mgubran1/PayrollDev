package com.company.payroll.loads;

// Java standard library imports (grouped alphabetically)
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// Third-party imports
import javax.imageio.ImageIO;

// JavaFX imports
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

// Apache PDFBox imports
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Application-specific imports
import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.trailers.Trailer;
import com.company.payroll.trailers.TrailerDAO;

public class LoadsPanel extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(LoadsPanel.class);
    
    private final LoadDAO loadDAO = new LoadDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final TrailerDAO trailerDAO = new TrailerDAO();
    private final ObservableList<Load> allLoads = FXCollections.observableArrayList();
    private final ObservableList<Employee> allDrivers = FXCollections.observableArrayList();
    private final ObservableList<Trailer> allTrailers = FXCollections.observableArrayList();
    private final ObservableList<String> allCustomers = FXCollections.observableArrayList();

    private final List<StatusTab> statusTabs = new ArrayList<>();
    private Consumer<List<Load>> syncToTriumphCallback;
    
    // Add listener interface and list
    public interface LoadDataChangeListener {
        void onLoadDataChanged();
    }
    
    private final List<LoadDataChangeListener> loadDataChangeListeners = new ArrayList<>();

    // Document storage directory
    private static final String DOCUMENT_STORAGE_PATH = "load_documents";

    public LoadsPanel() {
        logger.info("Initializing LoadsPanel");
        
        // Create document storage directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(DOCUMENT_STORAGE_PATH));
        } catch (IOException e) {
            logger.error("Failed to create document storage directory", e);
        }
        
        reloadAll();

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        statusTabs.add(makeActiveTab());
        statusTabs.add(makeStatusTab("Cancelled", Load.Status.CANCELLED));
        statusTabs.add(makeStatusTab("All Loads", null));
        statusTabs.add(makeCustomerSettingsTab());

        for (StatusTab sTab : statusTabs) {
            tabs.getTabs().add(sTab.tab);
        }

        setCenter(tabs);
        logger.info("LoadsPanel initialization complete");
    }
    
    public void addLoadDataChangeListener(LoadDataChangeListener listener) {
        loadDataChangeListeners.add(listener);
    }

    public void removeLoadDataChangeListener(LoadDataChangeListener listener) {
        loadDataChangeListeners.remove(listener);
    }

    private void notifyLoadDataChanged() {
        for (LoadDataChangeListener listener : loadDataChangeListeners) {
            listener.onLoadDataChanged();
        }
    }

    public void setSyncToTriumphCallback(Consumer<List<Load>> callback) {
        logger.debug("Setting sync to Triumph callback");
        this.syncToTriumphCallback = callback;
    }

    private static class StatusTab {
        Tab tab;
        TableView<Load> table;
        FilteredList<Load> filteredList;
    }

    private StatusTab makeActiveTab() {
        logger.debug("Creating Active Loads tab");
        StatusTab tab = new StatusTab();
        tab.filteredList = new FilteredList<>(allLoads, l -> l.getStatus() != Load.Status.CANCELLED);

        TableView<Load> table = makeTableView(tab.filteredList, true);

        // Enhanced search controls
        TextField loadNumField = new TextField();
        loadNumField.setPromptText("Load #");
        loadNumField.setPrefWidth(100);
        
        TextField truckUnitField = new TextField();
        truckUnitField.setPromptText("Truck/Unit");
        truckUnitField.setPrefWidth(100);
        
        TextField trailerNumberField = new TextField();
        trailerNumberField.setPromptText("Trailer #");
        trailerNumberField.setPrefWidth(100);
        
        ComboBox<Employee> driverBox = new ComboBox<>(allDrivers);
        driverBox.setPromptText("Driver");
        driverBox.setPrefWidth(150);
        driverBox.setCellFactory(cb -> new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                setText((e == null || empty) ? "" : e.getName() + (e.getTruckUnit() != null ? " (" + e.getTruckUnit() + ")" : ""));
            }
        });
        driverBox.setButtonCell(new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                setText((e == null || empty) ? "" : e.getName() + (e.getTruckUnit() != null ? " (" + e.getTruckUnit() + ")" : ""));
            }
        });
        
        ComboBox<Trailer> trailerBox = new ComboBox<>(allTrailers);
        trailerBox.setPromptText("Trailer");
        trailerBox.setPrefWidth(150);
        trailerBox.setCellFactory(cb -> new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                setText((t == null || empty) ? "" : t.getTrailerNumber() + " - " + t.getType());
            }
        });
        trailerBox.setButtonCell(new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                setText((t == null || empty) ? "" : t.getTrailerNumber() + " - " + t.getType());
            }
        });
        
        ComboBox<String> customerBox = new ComboBox<>(allCustomers);
        customerBox.setPromptText("Customer");
        customerBox.setPrefWidth(150);
        
        DatePicker startDatePicker = new DatePicker();
        startDatePicker.setPromptText("Start Date");
        startDatePicker.setPrefWidth(120);
        
        DatePicker endDatePicker = new DatePicker();
        endDatePicker.setPromptText("End Date");
        endDatePicker.setPrefWidth(120);
        
        Button clearSearchBtn = new Button("Clear Search");

        HBox searchBox1 = new HBox(8, new Label("Search:"), loadNumField, truckUnitField, trailerNumberField, driverBox);
        HBox searchBox2 = new HBox(8, customerBox, new Label("Date Range:"), startDatePicker, new Label("to"), endDatePicker, clearSearchBtn);
        VBox searchContainer = new VBox(5, searchBox1, searchBox2);
        searchContainer.setPadding(new Insets(10));
        searchContainer.setStyle("-fx-background-color:#f7f9ff;");

        Button addBtn = new Button("Add");
        Button editBtn = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        Button exportBtn = new Button("Export CSV");
        Button refreshBtn = new Button("Refresh");
        Button syncToTriumphBtn = new Button("Sync to MyTriumph");

        HBox buttonBox = new HBox(10, addBtn, editBtn, deleteBtn, syncToTriumphBtn, exportBtn, refreshBtn);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(4, 10, 8, 10));

        table.setRowFactory(tv -> {
            TableRow<Load> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    logger.info("Double-click edit for load: {}", row.getItem().getLoadNumber());
                    showLoadDialog(row.getItem(), false);
                }
            });
            return row;
        });

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Enhanced filtering
        Runnable refilter = () -> {
            logger.debug("Applying filters to Active Loads");
            Predicate<Load> pred = l -> l.getStatus() != Load.Status.CANCELLED;

            String loadNum = loadNumField.getText().trim().toLowerCase();
            if (!loadNum.isEmpty()) {
                pred = pred.and(l -> l.getLoadNumber() != null && l.getLoadNumber().toLowerCase().contains(loadNum));
            }
            
            String truckUnit = truckUnitField.getText().trim().toLowerCase();
            if (!truckUnit.isEmpty()) {
                pred = pred.and(l -> l.getTruckUnitSnapshot() != null && l.getTruckUnitSnapshot().toLowerCase().contains(truckUnit));
            }
            
            String trailerNumber = trailerNumberField.getText().trim().toLowerCase();
            if (!trailerNumber.isEmpty()) {
                pred = pred.and(l -> l.getTrailerNumber() != null && l.getTrailerNumber().toLowerCase().contains(trailerNumber));
            }
            
            Employee driver = driverBox.getValue();
            if (driver != null) {
                pred = pred.and(l -> l.getDriver() != null && l.getDriver().getId() == driver.getId());
            }
            
            Trailer trailer = trailerBox.getValue();
            if (trailer != null) {
                pred = pred.and(l -> l.getTrailer() != null && l.getTrailer().getId() == trailer.getId());
            }
            
            String customer = customerBox.getValue();
            if (customer != null && !customer.trim().isEmpty()) {
                pred = pred.and(l -> customer.equalsIgnoreCase(l.getCustomer()));
            }
            
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            if (startDate != null) {
                pred = pred.and(l -> l.getDeliveryDate() != null && !l.getDeliveryDate().isBefore(startDate));
            }
            if (endDate != null) {
                pred = pred.and(l -> l.getDeliveryDate() != null && !l.getDeliveryDate().isAfter(endDate));
            }
            
            tab.filteredList.setPredicate(pred);
        };

        // Add listeners
        loadNumField.textProperty().addListener((obs, o, n) -> refilter.run());
        truckUnitField.textProperty().addListener((obs, o, n) -> refilter.run());
        trailerNumberField.textProperty().addListener((obs, o, n) -> refilter.run());
        driverBox.valueProperty().addListener((obs, o, n) -> refilter.run());
        trailerBox.valueProperty().addListener((obs, o, n) -> refilter.run());
        customerBox.valueProperty().addListener((obs, o, n) -> refilter.run());
        startDatePicker.valueProperty().addListener((obs, o, n) -> refilter.run());
        endDatePicker.valueProperty().addListener((obs, o, n) -> refilter.run());
        
        clearSearchBtn.setOnAction(e -> {
            logger.info("Clearing search filters");
            loadNumField.clear();
            truckUnitField.clear();
            trailerNumberField.clear();
            driverBox.setValue(null);
            trailerBox.setValue(null);
            customerBox.setValue(null);
            startDatePicker.setValue(null);
            endDatePicker.setValue(null);
            refilter.run();
        });

        // Button actions
        addBtn.setOnAction(e -> {
            logger.info("Add load button clicked");
            showLoadDialog(null, true);
        });
        editBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Edit load button clicked for: {}", selected.getLoadNumber());
                showLoadDialog(selected, false);
            }
        });
        deleteBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Delete load button clicked for: {}", selected.getLoadNumber());
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete load \"" + selected.getLoadNumber() + "\"?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp -> {
                    if (resp == ButtonType.YES) {
                        logger.info("User confirmed deletion of load: {}", selected.getLoadNumber());
                        loadDAO.delete(selected.getId());
                        reloadAll();
                        notifyLoadDataChanged(); // Notify listeners
                    }
                });
            }
        });
        exportBtn.setOnAction(e -> {
            logger.info("Export CSV button clicked");
            exportCSV(table);
        });
        refreshBtn.setOnAction(e -> {
            logger.info("Refresh button clicked");
            reloadAll();
            notifyLoadDataChanged(); // Notify listeners
        });

        syncToTriumphBtn.setOnAction(e -> {
            logger.info("Sync to MyTriumph button clicked");
            List<Load> toSync = allLoads.stream()
                .filter(l -> (l.getStatus() == Load.Status.DELIVERED || l.getStatus() == Load.Status.PAID))
                .filter(l -> l.getPONumber() != null && !l.getPONumber().isEmpty())
                .collect(Collectors.toList());
            
            if (toSync.isEmpty()) {
                logger.info("No delivered/paid loads with PO numbers to sync");
                showInfo("No delivered/paid loads with PO numbers to sync.");
                return;
            }
            
            if (syncToTriumphCallback != null) {
                logger.info("Syncing {} loads to MyTriumph audit", toSync.size());
                syncToTriumphCallback.accept(toSync);
                showInfo("Syncing " + toSync.size() + " loads to MyTriumph audit.");
            } else {
                logger.warn("MyTriumph sync callback not configured");
                showInfo("MyTriumph sync not configured.");
            }
        });

        VBox vbox = new VBox(searchContainer, table, buttonBox);
        tab.tab = new Tab("Active Loads", vbox);
        tab.table = table;
        refilter.run();
        return tab;
    }

    private StatusTab makeStatusTab(String title, Load.Status filterStatus) {
        logger.debug("Creating {} tab", title);
        StatusTab statusTab = new StatusTab();
        statusTab.filteredList = new FilteredList<>(allLoads, l -> filterStatus == null || l.getStatus() == filterStatus);
        TableView<Load> table = makeTableView(statusTab.filteredList, filterStatus == null);

        Button addBtn = new Button("Add");
        Button editBtn = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        Button exportBtn = new Button("Export CSV");
        Button refreshBtn = new Button("Refresh");

        HBox buttonBox = new HBox(10, addBtn, editBtn, deleteBtn, exportBtn, refreshBtn);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10, 10, 5, 10));

        addBtn.setOnAction(e -> {
            logger.info("Add load button clicked in {} tab", title);
            showLoadDialog(null, true);
        });
        editBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Edit load button clicked for: {} in {} tab", selected.getLoadNumber(), title);
                showLoadDialog(selected, false);
            }
        });
        deleteBtn.setOnAction(e -> {
            Load selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Delete load button clicked for: {} in {} tab", selected.getLoadNumber(), title);
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete load \"" + selected.getLoadNumber() + "\"?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp -> {
                    if (resp == ButtonType.YES) {
                        logger.info("User confirmed deletion of load: {}", selected.getLoadNumber());
                        loadDAO.delete(selected.getId());
                        reloadAll();
                        notifyLoadDataChanged(); // Notify listeners
                    }
                });
            }
        });
        exportBtn.setOnAction(e -> {
            logger.info("Export CSV button clicked in {} tab", title);
            exportCSV(table);
        });
        refreshBtn.setOnAction(e -> {
            logger.info("Refresh button clicked in {} tab", title);
            reloadAll();
            notifyLoadDataChanged(); // Notify listeners
        });

        table.setRowFactory(tv -> {
            TableRow<Load> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    logger.info("Double-click edit for load: {} in {} tab", row.getItem().getLoadNumber(), title);
                    showLoadDialog(row.getItem(), false);
                }
            });
            return row;
        });

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        VBox vbox = new VBox(table, buttonBox);
        statusTab.tab = new Tab(title, vbox);
        statusTab.table = table;
        return statusTab;
    }

    private StatusTab makeCustomerSettingsTab() {
        logger.debug("Creating Customer Settings tab");
        StatusTab statusTab = new StatusTab();
        
        // Main content split pane
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.3);
        
        // Left side - Customer list
        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(10));
        
        Label customerLabel = new Label("Customer List:");
        ListView<String> customerList = new ListView<>(allCustomers);
        customerList.setPrefHeight(300);
        
        TextField newCustomerField = new TextField();
        newCustomerField.setPromptText("Add new customer...");
        Button addCustomerBtn = new Button("Add");
        Button deleteCustomerBtn = new Button("Delete Selected");
        
        HBox customerInputBox = new HBox(10, newCustomerField, addCustomerBtn, deleteCustomerBtn);
        customerInputBox.setAlignment(Pos.CENTER_LEFT);
        
        leftPane.getChildren().addAll(customerLabel, customerList, customerInputBox);
        
        // Right side - Customer locations
        VBox rightPane = new VBox(10);
        rightPane.setPadding(new Insets(10));
        
        Label locationsLabel = new Label("Customer Locations:");
        
        TabPane locationTabs = new TabPane();
        locationTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Pickup locations tab
        Tab pickupTab = new Tab("Pick Up Locations");
        VBox pickupContent = new VBox(10);
        pickupContent.setPadding(new Insets(10));
        
        ListView<String> pickupList = new ListView<>();
        pickupList.setPrefHeight(200);
        
        TextField newPickupField = new TextField();
        newPickupField.setPromptText("Add new pickup location...");
        Button addPickupBtn = new Button("Add");
        Button deletePickupBtn = new Button("Delete Selected");
        
        HBox pickupInputBox = new HBox(10, newPickupField, addPickupBtn, deletePickupBtn);
        pickupContent.getChildren().addAll(pickupList, pickupInputBox);
        pickupTab.setContent(pickupContent);
        
        // Drop locations tab
        Tab dropTab = new Tab("Drop Locations");
        VBox dropContent = new VBox(10);
        dropContent.setPadding(new Insets(10));
        
        ListView<String> dropList = new ListView<>();
        dropList.setPrefHeight(200);
        
        TextField newDropField = new TextField();
        newDropField.setPromptText("Add new drop location...");
        Button addDropBtn = new Button("Add");
        Button deleteDropBtn = new Button("Delete Selected");
        
        HBox dropInputBox = new HBox(10, newDropField, addDropBtn, deleteDropBtn);
        dropContent.getChildren().addAll(dropList, dropInputBox);
        dropTab.setContent(dropContent);
        
        locationTabs.getTabs().addAll(pickupTab, dropTab);
        
        rightPane.getChildren().addAll(locationsLabel, locationTabs);
        rightPane.setDisable(true); // Initially disabled until customer selected
        
        splitPane.getItems().addAll(leftPane, rightPane);
        
        // Customer selection handler
        customerList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                rightPane.setDisable(false);
                // Load locations for selected customer
                Map<String, List<String>> locations = loadDAO.getAllCustomerLocations(newVal);
                pickupList.setItems(FXCollections.observableArrayList(locations.getOrDefault("PICKUP", new ArrayList<>())));
                dropList.setItems(FXCollections.observableArrayList(locations.getOrDefault("DROP", new ArrayList<>())));
            } else {
                rightPane.setDisable(true);
                pickupList.setItems(FXCollections.emptyObservableList());
                dropList.setItems(FXCollections.emptyObservableList());
            }
        });
        
        // Customer management handlers
        addCustomerBtn.setOnAction(e -> {
            String name = newCustomerField.getText().trim();
            if (name.isEmpty()) {
                logger.warn("Attempted to add empty customer name");
                showError("Customer name cannot be empty.");
                return;
            }
            boolean isDuplicate = allCustomers.stream().anyMatch(c -> c.equalsIgnoreCase(name));
            if (isDuplicate) {
                logger.warn("Attempted to add duplicate customer: {}", name);
                showError("Customer already exists.");
                return;
            }
            logger.info("Adding new customer: {}", name);
            loadDAO.addCustomerIfNotExists(name);
            reloadAll();
            newCustomerField.clear();
            for (String c : allCustomers) {
                if (c.equalsIgnoreCase(name)) {
                    customerList.getSelectionModel().select(c);
                    customerList.scrollTo(c);
                    break;
                }
            }
        });

        deleteCustomerBtn.setOnAction(e -> {
            String selected = customerList.getSelectionModel().getSelectedItem();
            if (selected == null || selected.trim().isEmpty()) {
                logger.warn("No customer selected for deletion");
                showError("No customer selected.");
                return;
            }
            logger.info("Delete customer button clicked for: {}", selected);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
                "Delete customer \"" + selected + "\"?\nThis will also delete all associated locations.", 
                ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText("Confirm Delete");
            confirm.showAndWait().ifPresent(resp -> {
                if (resp == ButtonType.YES) {
                    logger.info("User confirmed deletion of customer: {}", selected);
                    loadDAO.deleteCustomer(selected);
                    reloadAll();
                }
            });
        });
        
        // Location management handlers
        addPickupBtn.setOnAction(e -> {
            String customer = customerList.getSelectionModel().getSelectedItem();
            String location = newPickupField.getText().trim();
            if (customer == null || location.isEmpty()) {
                showError("Please select a customer and enter a location.");
                return;
            }
            loadDAO.addCustomerLocationIfNotExists(customer, "PICKUP", location);
            newPickupField.clear();
            // Refresh locations
            Map<String, List<String>> locations = loadDAO.getAllCustomerLocations(customer);
            pickupList.setItems(FXCollections.observableArrayList(locations.getOrDefault("PICKUP", new ArrayList<>())));
        });
        
        deletePickupBtn.setOnAction(e -> {
            String customer = customerList.getSelectionModel().getSelectedItem();
            String location = pickupList.getSelectionModel().getSelectedItem();
            if (customer == null || location == null) {
                showError("Please select a location to delete.");
                return;
            }
            loadDAO.deleteCustomerLocation(customer, "PICKUP", location);
            // Refresh locations
            Map<String, List<String>> locations = loadDAO.getAllCustomerLocations(customer);
            pickupList.setItems(FXCollections.observableArrayList(locations.getOrDefault("PICKUP", new ArrayList<>())));
        });
        
        addDropBtn.setOnAction(e -> {
            String customer = customerList.getSelectionModel().getSelectedItem();
            String location = newDropField.getText().trim();
            if (customer == null || location.isEmpty()) {
                showError("Please select a customer and enter a location.");
                return;
            }
            loadDAO.addCustomerLocationIfNotExists(customer, "DROP", location);
            newDropField.clear();
            // Refresh locations
            Map<String, List<String>> locations = loadDAO.getAllCustomerLocations(customer);
            dropList.setItems(FXCollections.observableArrayList(locations.getOrDefault("DROP", new ArrayList<>())));
        });
        
        deleteDropBtn.setOnAction(e -> {
            String customer = customerList.getSelectionModel().getSelectedItem();
            String location = dropList.getSelectionModel().getSelectedItem();
            if (customer == null || location == null) {
                showError("Please select a location to delete.");
                return;
            }
            loadDAO.deleteCustomerLocation(customer, "DROP", location);
            // Refresh locations
            Map<String, List<String>> locations = loadDAO.getAllCustomerLocations(customer);
            dropList.setItems(FXCollections.observableArrayList(locations.getOrDefault("DROP", new ArrayList<>())));
        });

        statusTab.tab = new Tab("Customer Settings", splitPane);
        return statusTab;
    }

    private TableView<Load> makeTableView(ObservableList<Load> list, boolean includeActionColumns) {
        TableView<Load> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setItems(list);

        TableColumn<Load, String> loadNumCol = new TableColumn<>("Load #");
        loadNumCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getLoadNumber()));
        loadNumCol.setPrefWidth(80);

        TableColumn<Load, String> poCol = new TableColumn<>("PO");
        poCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getPONumber()));
        poCol.setPrefWidth(80);

        TableColumn<Load, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getCustomer()));
        customerCol.setPrefWidth(120);

        TableColumn<Load, String> pickUpCol = new TableColumn<>("Pick Up Location");
        pickUpCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getPickUpLocation()));
        pickUpCol.setPrefWidth(150);

        TableColumn<Load, String> dropCol = new TableColumn<>("Drop Location");
        dropCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getDropLocation()));
        dropCol.setPrefWidth(150);

        TableColumn<Load, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getDriver() != null ? e.getValue().getDriver().getName() : ""
        ));
        driverCol.setPrefWidth(100);

        TableColumn<Load, String> truckUnitCol = new TableColumn<>("Truck/Unit");
        truckUnitCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getTruckUnitSnapshot()));
        truckUnitCol.setPrefWidth(80);
        
        TableColumn<Load, String> trailerCol = new TableColumn<>("Trailer");
        trailerCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getTrailerNumber()));
        trailerCol.setPrefWidth(80);

        TableColumn<Load, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getStatus().toString()));
        statusCol.setCellFactory(col -> new TableCell<Load, String>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(s);
                if (!empty && s != null) {
                    setStyle("-fx-background-color: " + getStatusColor(s) + "; -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });
        statusCol.setPrefWidth(80);

        TableColumn<Load, Number> grossCol = new TableColumn<>("Gross Amount");
        grossCol.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().getGrossAmount()));
        grossCol.setCellFactory(col -> new TableCell<Load, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", item.doubleValue()));
                }
            }
        });
        grossCol.setPrefWidth(100);

        TableColumn<Load, String> reminderCol = new TableColumn<>("Reminder");
        reminderCol.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getReminder()));
        reminderCol.setCellFactory(col -> new TableCell<Load, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item);
                    Load load = getTableRow().getItem();
                    if (load != null && load.isHasLumper()) {
                        if (load.isHasRevisedRateConfirmation()) {
                            setStyle("-fx-background-color: #b7f9b7; -fx-font-weight: bold;"); // Green
                        } else {
                            setStyle("-fx-background-color: #ffcccc; -fx-font-weight: bold;"); // Red
                        }
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        reminderCol.setPrefWidth(200);

        TableColumn<Load, String> pickupDateCol = new TableColumn<>("Pick Up Date");
        pickupDateCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getPickUpDate() != null ? e.getValue().getPickUpDate().toString() : ""
        ));
        pickupDateCol.setPrefWidth(100);

        TableColumn<Load, String> deliveryDateCol = new TableColumn<>("Delivery Date");
        deliveryDateCol.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getDeliveryDate() != null ? e.getValue().getDeliveryDate().toString() : ""
        ));
        deliveryDateCol.setPrefWidth(100);

        table.getColumns().addAll(
                Arrays.asList(loadNumCol, poCol, customerCol, pickUpCol, dropCol,
                        driverCol, truckUnitCol, trailerCol, statusCol, grossCol,
                        reminderCol, pickupDateCol, deliveryDateCol));

        // Add action columns if requested
        if (includeActionColumns) {
            TableColumn<Load, Void> lumperCol = new TableColumn<>("Lumper");
            lumperCol.setPrefWidth(150);
            lumperCol.setCellFactory(col -> new TableCell<Load, Void>() {
                private final Button addLumperBtn = new Button("Add");
                private final Button editLumperBtn = new Button("Edit");
                private final Button removeLumperBtn = new Button("Remove");
                private final HBox buttonBox = new HBox(5, addLumperBtn, editLumperBtn, removeLumperBtn);
                
                {
                    addLumperBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-size: 10;");
                    editLumperBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-size: 10;");
                    removeLumperBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-size: 10;");
                    buttonBox.setAlignment(Pos.CENTER);
                }
                
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        Load load = getTableRow().getItem();
                        if (load != null) {
                            addLumperBtn.setDisable(load.isHasLumper());
                            editLumperBtn.setDisable(!load.isHasLumper());
                            removeLumperBtn.setDisable(!load.isHasLumper());
                            
                            addLumperBtn.setOnAction(e -> handleLumperAction(load, "add"));
                            editLumperBtn.setOnAction(e -> handleLumperAction(load, "edit"));
                            removeLumperBtn.setOnAction(e -> handleLumperAction(load, "remove"));
                            
                            setGraphic(buttonBox);
                        }
                    }
                }
            });
            
            TableColumn<Load, Void> docsCol = new TableColumn<>("Documents");
            docsCol.setPrefWidth(100);
            docsCol.setCellFactory(col -> new TableCell<Load, Void>() {
                private final Button uploadBtn = new Button("Upload Docs");
                
                {
                    uploadBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white;");
                }
                
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        Load load = getTableRow().getItem();
                        if (load != null) {
                            uploadBtn.setOnAction(e -> showDocumentDialog(load));
                            setGraphic(uploadBtn);
                        }
                    }
                }
            });
            
            table.getColumns().addAll(Arrays.asList(lumperCol, docsCol));
        }

        return table;
    }

    private void handleLumperAction(Load load, String action) {
        logger.info("Lumper {} action for load: {}", action, load.getLoadNumber());
        
        if ("add".equals(action) || "edit".equals(action)) {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle(action.equals("add") ? "Add Lumper" : "Edit Lumper");
            dialog.setHeaderText("Enter lumper information for load " + load.getLoadNumber());
            
            TextArea lumperInfo = new TextArea();
            lumperInfo.setPromptText("Enter lumper details...");
            lumperInfo.setPrefRowCount(3);
            if ("edit".equals(action) && load.getReminder() != null) {
                lumperInfo.setText(load.getReminder());
            }
            
            CheckBox hasRevisedConfirmation = new CheckBox("Revised Rate Confirmation Available");
            hasRevisedConfirmation.setSelected(load.isHasRevisedRateConfirmation());
            
            VBox content = new VBox(10);
            content.getChildren().addAll(
                new Label("Lumper Information:"),
                lumperInfo,
                hasRevisedConfirmation,
                new Label("Note: If revised rate confirmation is not available,\nreminder will show: EMAIL BROKER / CHECK EMAIL for Revised Rate Confirmation")
            );
            
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    return lumperInfo.getText();
                }
                return null;
            });
            
            dialog.showAndWait().ifPresent(lumperText -> {
                load.setHasLumper(true);
                load.setHasRevisedRateConfirmation(hasRevisedConfirmation.isSelected());
                if (!hasRevisedConfirmation.isSelected()) {
                    load.setReminder("EMAIL BROKER / CHECK EMAIL for Revised Rate Confirmation - " + lumperText);
                } else {
                    load.setReminder("Lumper: " + lumperText);
                }
                loadDAO.update(load);
                reloadAll();
                notifyLoadDataChanged(); // Notify listeners
            });
        } else if ("remove".equals(action)) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Remove lumper from load " + load.getLoadNumber() + "?",
                    ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    load.setHasLumper(false);
                    load.setHasRevisedRateConfirmation(false);
                    load.setReminder("");
                    loadDAO.update(load);
                    reloadAll();
                    notifyLoadDataChanged(); // Notify listeners
                }
            });
        }
    }

    private void showDocumentDialog(Load load) {
        logger.info("Showing document dialog for load: {}", load.getLoadNumber());
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Document Management - Load " + load.getLoadNumber());
        dialog.setHeaderText("Upload and manage documents");
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        // Document list
        ListView<Load.LoadDocument> docList = new ListView<>();
        docList.setPrefHeight(200);
        docList.setCellFactory(lv -> new ListCell<Load.LoadDocument>() {
            @Override
            protected void updateItem(Load.LoadDocument doc, boolean empty) {
                super.updateItem(doc, empty);
                if (empty || doc == null) {
                    setText(null);
                } else {
                    setText(doc.getFileName() + " (" + doc.getType() + ") - " + doc.getUploadDate());
                }
            }
        });
        
        ObservableList<Load.LoadDocument> documents = FXCollections.observableArrayList(load.getDocuments());
        docList.setItems(documents);
        
        // Upload section
        ComboBox<Load.LoadDocument.DocumentType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Load.LoadDocument.DocumentType.values());
        typeCombo.setValue(Load.LoadDocument.DocumentType.RATE_CONFIRMATION);
        
        Button uploadBtn = new Button("Upload Document");
        Button deleteBtn = new Button("Delete Selected");
        Button mergeBtn = new Button("Merge & Print");
        
        HBox uploadBox = new HBox(10, new Label("Type:"), typeCombo, uploadBtn, deleteBtn);
        uploadBox.setAlignment(Pos.CENTER_LEFT);
        
        uploadBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Document");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.jpg", "*.jpeg", "*.png"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png")
            );
            
            List<File> files = fileChooser.showOpenMultipleDialog(dialog.getOwner());
            if (files != null && !files.isEmpty()) {
                for (File file : files) {
                    try {
                        // Copy file to document storage
                        String fileName = load.getLoadNumber() + "_" + System.currentTimeMillis() + "_" + file.getName();
                        Path targetPath = Paths.get(DOCUMENT_STORAGE_PATH, fileName);
                        Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                        
                        // Save document record
                        Load.LoadDocument doc = new Load.LoadDocument(
                            0,
                            load.getId(),
                            file.getName(),
                            targetPath.toString(),
                            typeCombo.getValue(),
                            LocalDate.now()
                        );
                        
                        int docId = loadDAO.addDocument(doc);
                        doc.setId(docId);
                        documents.add(doc);
                        load.getDocuments().add(doc);
                        
                        logger.info("Document uploaded: {}", file.getName());
                    } catch (IOException ex) {
                        logger.error("Failed to upload document: {}", file.getName(), ex);
                        showError("Failed to upload " + file.getName() + ": " + ex.getMessage());
                    }
                }
            }
        });
        
        deleteBtn.setOnAction(e -> {
            Load.LoadDocument selected = docList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                loadDAO.deleteDocument(selected.getId());
                documents.remove(selected);
                load.getDocuments().remove(selected);
                
                // Delete physical file
                try {
                    Files.deleteIfExists(Paths.get(selected.getFilePath()));
                } catch (IOException ex) {
                    logger.error("Failed to delete file: {}", selected.getFilePath(), ex);
                }
            }
        });
        
        mergeBtn.setOnAction(e -> handleMergeDocuments(load, documents));
        
        content.getChildren().addAll(
            new Label("Documents:"),
            docList,
            uploadBox,
            new Separator(),
            mergeBtn
        );
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(500, 400);
        
        dialog.showAndWait();
    }

    private void handleMergeDocuments(Load load, List<Load.LoadDocument> documents) {
		if (documents.isEmpty()) {
			showError("No documents to merge.");
			return;
		}
		
		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Merge Documents");
		dialog.setHeaderText("Select documents to merge and output filename");
		
		VBox content = new VBox(10);
		content.setPadding(new Insets(10));
		
		// Document selection with checkboxes
		VBox docSelectionBox = new VBox(5);
		List<CheckBox> checkBoxes = new ArrayList<>();
		for (Load.LoadDocument doc : documents) {
			CheckBox cb = new CheckBox(doc.getFileName() + " (" + doc.getType() + ")");
			cb.setUserData(doc);
			checkBoxes.add(cb);
			docSelectionBox.getChildren().add(cb);
		}
		ScrollPane scrollPane = new ScrollPane(docSelectionBox);
		scrollPane.setPrefHeight(150);
		
		// Output filename
		ComboBox<String> nameTypeCombo = new ComboBox<>();
		nameTypeCombo.getItems().addAll("Load Number", "PO Number", "Custom");
		nameTypeCombo.setValue("Load Number");
		
		TextField customNameField = new TextField();
		customNameField.setPromptText("Custom filename");
		customNameField.setDisable(true);
		
		nameTypeCombo.setOnAction(e -> {
			customNameField.setDisable(!"Custom".equals(nameTypeCombo.getValue()));
		});
		
		content.getChildren().addAll(
			new Label("Select documents to merge:"),
			scrollPane,
			new Separator(),
			new Label("Output filename:"),
			new HBox(10, nameTypeCombo, customNameField)
		);
		
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		
		dialog.setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				List<Load.LoadDocument> selectedDocs = new ArrayList<>();
				for (CheckBox cb : checkBoxes) {
					if (cb.isSelected()) {
						selectedDocs.add((Load.LoadDocument) cb.getUserData());
					}
				}
				
				if (selectedDocs.isEmpty()) {
					showError("Please select at least one document.");
					return null;
				}
				
				String fileName;
				switch (nameTypeCombo.getValue()) {
					case "Load Number":
						fileName = load.getLoadNumber();
						break;
					case "PO Number":
						fileName = load.getPONumber();
						break;
					default:
						fileName = customNameField.getText().trim();
						if (fileName.isEmpty()) {
							showError("Please enter a custom filename.");
							return null;
						}
				}
				
				DirectoryChooser dirChooser = new DirectoryChooser();
				dirChooser.setTitle("Select Output Directory");
				File outputDir = dirChooser.showDialog(dialog.getOwner());
				if (outputDir == null) return null;
				
				try {
					File outputFile = new File(outputDir, fileName + ".pdf");
					mergePDFs(load, selectedDocs, outputFile);
					
					// Ask if user wants to print
					Alert printAlert = new Alert(Alert.AlertType.CONFIRMATION,
							"PDF merged successfully. Do you want to print it now?",
							ButtonType.YES, ButtonType.NO);
					printAlert.showAndWait().ifPresent(response -> {
						if (response == ButtonType.YES) {
							printPDF(outputFile);
						}
					});
					
					return fileName;
				} catch (Exception ex) {
					logger.error("Failed to merge documents", ex);
					showError("Failed to merge documents: " + ex.getMessage());
					return null;
				}
			}
			return null;
		});
		
		dialog.showAndWait();
	}

    private void mergePDFs(Load load, List<Load.LoadDocument> documents, File outputFile) throws IOException {
		PDFMergerUtility merger = new PDFMergerUtility();
		PDDocument coverPage = createCoverPage(load);
		
		// Save cover page to temp file
		File tempCover = File.createTempFile("cover", ".pdf");
		coverPage.save(tempCover);
		coverPage.close();
		
		// Add cover page first
		merger.addSource(tempCover);
		
		// Add all selected documents
		for (Load.LoadDocument doc : documents) {
			File docFile = new File(doc.getFilePath());
			if (docFile.exists()) {
				if (doc.getFilePath().toLowerCase().endsWith(".pdf")) {
					merger.addSource(docFile);
				} else {
					// Convert image to PDF
					File tempPdf = convertImageToPDF(docFile);
					merger.addSource(tempPdf);
				}
			}
		}
		
		merger.setDestinationFileName(outputFile.getAbsolutePath());
		// Remove the MemoryUsageSetting parameter:
		merger.mergeDocuments(null);
		
		// Clean up temp files
		tempCover.delete();
		
		logger.info("Documents merged successfully to: {}", outputFile.getAbsolutePath());
	}

    private PDDocument createCoverPage(Load load) throws IOException {
		PDDocument document = new PDDocument();
		PDPage page = new PDPage(PDRectangle.LETTER);
		document.addPage(page);
		
		try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
			contentStream.beginText();
			// Correct way to use Standard14Fonts in PDFBox 3.0.2:
			contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
			contentStream.setLeading(30f);
			contentStream.newLineAtOffset(100, 700);
			
			contentStream.showText("LOAD DOCUMENTS");
			contentStream.newLine();
			contentStream.newLine();
			
			// Correct way to use Standard14Fonts in PDFBox 3.0.2:
			contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
			contentStream.showText("Driver: " + (load.getDriver() != null ? load.getDriver().getName() : "N/A"));
			contentStream.newLine();
			
			contentStream.showText("Truck/Unit: " + load.getTruckUnitSnapshot());
			contentStream.newLine();
			
			// Add trailer information to cover page
			String trailerInfo = load.getTrailerNumber() != null && !load.getTrailerNumber().isEmpty() ? 
				load.getTrailerNumber() : 
				(load.getDriver() != null ? load.getDriver().getTrailerNumber() : "N/A");
				
			contentStream.showText("Trailer #: " + trailerInfo);
			contentStream.newLine();
			
			contentStream.showText("Pick Up Date: " + (load.getPickUpDate() != null ? load.getPickUpDate().toString() : "N/A"));
			contentStream.newLine();
			
			contentStream.showText("Delivery Date: " + (load.getDeliveryDate() != null ? load.getDeliveryDate().toString() : "N/A"));
			contentStream.newLine();
			
			contentStream.showText("PO: " + load.getPONumber());
			contentStream.newLine();
			
			contentStream.endText();
		}
		
		return document;
	}

    private File convertImageToPDF(File imageFile) throws IOException {
        PDDocument document = new PDDocument();
        
        BufferedImage image = ImageIO.read(imageFile);
        float width = image.getWidth();
        float height = image.getHeight();
        
        PDPage page = new PDPage(new PDRectangle(width, height));
        document.addPage(page);
        
        PDImageXObject pdImage = PDImageXObject.createFromFileByContent(imageFile, document);
        
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.drawImage(pdImage, 0, 0);
        }
        
        File pdfFile = File.createTempFile("image", ".pdf");
        document.save(pdfFile);
        document.close();
        
        return pdfFile;
    }

    private void printPDF(File pdfFile) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().print(pdfFile);
            } else {
                showError("Printing is not supported on this system.");
            }
        } catch (IOException e) {
            logger.error("Failed to print PDF", e);
            showError("Failed to print PDF: " + e.getMessage());
        }
    }

    // CheckListView implementation (simplified version)
    private static class CheckListView<T> extends ListView<T> {
        private final MultipleSelectionModel<T> checkModel;
        
        public CheckListView() {
            this.checkModel = new MultipleSelectionModel<T>() {
                private final ObservableList<Integer> selectedIndices = FXCollections.observableArrayList();
                private final ObservableList<T> selectedItems = FXCollections.observableArrayList();
                
                @Override
                public ObservableList<Integer> getSelectedIndices() { return selectedIndices; }
                @Override
                public ObservableList<T> getSelectedItems() { return selectedItems; }
                @Override
                public void selectIndices(int index, int... indices) {
                    select(index);
                    for (int i : indices) select(i);
                }
                @Override
                public void selectAll() {
                    for (int i = 0; i < getItems().size(); i++) select(i);
                }
                @Override
                public void selectFirst() { if (!getItems().isEmpty()) select(0); }
                @Override
                public void selectLast() { if (!getItems().isEmpty()) select(getItems().size() - 1); }
                @Override
                public void clearAndSelect(int index) { clearSelection(); select(index); }
                @Override
                public void select(int index) {
                    if (index >= 0 && index < getItems().size()) {
                        if (!selectedIndices.contains(index)) {
                            selectedIndices.add(index);
                            selectedItems.add(getItems().get(index));
                        }
                    }
                }
                @Override
                public void select(T obj) {
                    int index = getItems().indexOf(obj);
                    if (index >= 0) select(index);
                }
                @Override
                public void clearSelection(int index) {
                    selectedIndices.remove(Integer.valueOf(index));
                    selectedItems.remove(getItems().get(index));
                }
                @Override
                public void clearSelection() {
                    selectedIndices.clear();
                    selectedItems.clear();
                }
                @Override
                public boolean isSelected(int index) {
                    return selectedIndices.contains(index);
                }
                @Override
                public boolean isEmpty() { return selectedIndices.isEmpty(); }
                @Override
                public void selectPrevious() {}
                @Override
                public void selectNext() {}
            };
            
            setCellFactory(lv -> new CheckBoxListCell<>(checkModel::isSelected, (index, selected) -> {
                if (selected) {
                    checkModel.select(index);
                } else {
                    checkModel.clearSelection(index);
                }
            }));
        }
        
        public MultipleSelectionModel<T> getCheckModel() { return checkModel; }
    }

    private static class CheckBoxListCell<T> extends ListCell<T> {
        private final CheckBox checkBox = new CheckBox();
        private final java.util.function.Function<Integer, Boolean> getSelectedProperty;
        private final java.util.function.BiConsumer<Integer, Boolean> onSelectedChanged;
        
        public CheckBoxListCell(java.util.function.Function<Integer, Boolean> getSelectedProperty,
                                java.util.function.BiConsumer<Integer, Boolean> onSelectedChanged) {
            this.getSelectedProperty = getSelectedProperty;
            this.onSelectedChanged = onSelectedChanged;
        }
        
        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                checkBox.setSelected(getSelectedProperty.apply(getIndex()));
                checkBox.setOnAction(e -> onSelectedChanged.accept(getIndex(), checkBox.isSelected()));
                setGraphic(checkBox);
                setText(item.toString());
            }
        }
    }

    private String getStatusColor(String s) {
        switch (s) {
            case "BOOKED":      return "#b6d4fe";
            case "IN_TRANSIT":  return "#ffe59b";
            case "DELIVERED":   return "#b7f9b7";
            case "PAID":        return "#c2c2d6";
            case "CANCELLED":   return "#ffc8c8";
            default:            return "#f7f7f7";
        }
    }

    private void showLoadDialog(Load load, boolean isAdd) {
        logger.debug("Showing load dialog - isAdd: {}", isAdd);
        Dialog<Load> dialog = new Dialog<>();
        dialog.setTitle(isAdd ? "Add Load" : "Edit Load");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField loadNumField = new TextField();
        TextField poField = new TextField();
        ComboBox<String> customerBox = new ComboBox<>(allCustomers);
        customerBox.setEditable(true);
        
        // Enhanced location fields with autocomplete
        ComboBox<String> pickUpField = new ComboBox<>();
        pickUpField.setEditable(true);
        pickUpField.setPrefWidth(300);
        
        ComboBox<String> dropField = new ComboBox<>();
        dropField.setEditable(true);
        dropField.setPrefWidth(300);
        
        // Update location lists when customer is selected
        customerBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                Map<String, List<String>> locations = loadDAO.getAllCustomerLocations(newVal);
                pickUpField.setItems(FXCollections.observableArrayList(locations.getOrDefault("PICKUP", new ArrayList<>())));
                dropField.setItems(FXCollections.observableArrayList(locations.getOrDefault("DROP", new ArrayList<>())));
            } else {
                pickUpField.setItems(FXCollections.emptyObservableList());
                dropField.setItems(FXCollections.emptyObservableList());
            }
        });
        
        // Enhanced driver/truck selection
        ComboBox<Employee> driverBox = new ComboBox<>(allDrivers);
        driverBox.setCellFactory(cb -> new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                setText((e == null || empty) ? "" : e.getName() + (e.getTruckUnit() != null ? " (" + e.getTruckUnit() + ")" : ""));
            }
        });
        driverBox.setButtonCell(new ListCell<Employee>() {
            @Override
            protected void updateItem(Employee e, boolean empty) {
                super.updateItem(e, empty);
                setText((e == null || empty) ? "" : e.getName() + (e.getTruckUnit() != null ? " (" + e.getTruckUnit() + ")" : ""));
            }
        });
        
        TextField truckUnitSearchField = new TextField();
        truckUnitSearchField.setPromptText("Search by Truck/Unit");
        
        // Link truck unit search to driver selection
        truckUnitSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                Employee found = employeeDAO.getByTruckUnit(newVal.trim());
                if (found != null) {
                    driverBox.setValue(found);
                }
            }
        });
        
        // Update truck search field when driver is selected
        driverBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getTruckUnit() != null) {
                truckUnitSearchField.setText(newVal.getTruckUnit());
            }
        });
        
        // Trailer selection - now automatically populated from driver's trailer
        ComboBox<Trailer> trailerBox = new ComboBox<>(allTrailers);
        trailerBox.setPromptText("Select Trailer");
        trailerBox.setCellFactory(cb -> new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                                setText((t == null || empty) ? "" : t.getTrailerNumber() + " - " + t.getType());
            }
        });
        trailerBox.setButtonCell(new ListCell<Trailer>() {
            @Override
            protected void updateItem(Trailer t, boolean empty) {
                super.updateItem(t, empty);
                setText((t == null || empty) ? "" : t.getTrailerNumber() + " - " + t.getType());
            }
        });
        
        TextField trailerSearchField = new TextField();
        trailerSearchField.setPromptText("Search by Trailer #");
        
        // Link trailer search to selection
        trailerSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                Trailer found = trailerDAO.findByTrailerNumber(newVal.trim());
                if (found != null) {
                    trailerBox.setValue(found);
                }
            }
        });
        
        // Update trailer search field when trailer is selected
        trailerBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                trailerSearchField.setText(newVal.getTrailerNumber());
            }
        });
        
        // Auto-populate trailer from driver's trailer number
        driverBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getTrailerNumber() != null && !newVal.getTrailerNumber().isEmpty()) {
                trailerSearchField.setText(newVal.getTrailerNumber());
                // Try to find and select the trailer
                Trailer driverTrailer = trailerDAO.findByTrailerNumber(newVal.getTrailerNumber());
                if (driverTrailer != null) {
                    trailerBox.setValue(driverTrailer);
                }
            }
        });
        
        ComboBox<Load.Status> statusBox = new ComboBox<>(FXCollections.observableArrayList(Load.Status.values()));
        TextField grossField = new TextField();
        TextArea notesField = new TextArea();
        DatePicker pickUpDatePicker = new DatePicker();  // NEW
        DatePicker deliveryDatePicker = new DatePicker();
        TextField reminderField = new TextField();
        CheckBox hasLumperCheck = new CheckBox("Has Lumper");
        CheckBox hasRevisedRateCheck = new CheckBox("Has Revised Rate Confirmation");
        
        notesField.setPrefRowCount(2);
        reminderField.setPromptText("Reminder notes...");

        if (load != null) {
            loadNumField.setText(load.getLoadNumber());
            poField.setText(load.getPONumber());
            customerBox.setValue(load.getCustomer());
            pickUpField.setValue(load.getPickUpLocation());
            dropField.setValue(load.getDropLocation());
            
            // Set driver
            Employee loadedDriver = load.getDriver();
            if (loadedDriver != null) {
                Employee matching = allDrivers.stream()
                        .filter(emp -> emp.getId() == loadedDriver.getId())
                        .findFirst()
                        .orElse(null);
                driverBox.setValue(matching);
            } else {
                driverBox.setValue(null);
            }
            
            // Set trailer
            Trailer loadedTrailer = load.getTrailer();
            if (loadedTrailer != null) {
                Trailer matching = allTrailers.stream()
                        .filter(t -> t.getId() == loadedTrailer.getId())
                        .findFirst()
                        .orElse(null);
                trailerBox.setValue(matching);
            } else if (load.getTrailerNumber() != null && !load.getTrailerNumber().isEmpty()) {
                trailerSearchField.setText(load.getTrailerNumber());
            }
            
            statusBox.setValue(load.getStatus());
            grossField.setText(String.valueOf(load.getGrossAmount()));
            notesField.setText(load.getNotes());
            if (load.getPickUpDate() != null) {
                pickUpDatePicker.setValue(load.getPickUpDate());
            }
            if (load.getDeliveryDate() != null) {
                deliveryDatePicker.setValue(load.getDeliveryDate());
            }
            reminderField.setText(load.getReminder());
            hasLumperCheck.setSelected(load.isHasLumper());
            hasRevisedRateCheck.setSelected(load.isHasRevisedRateConfirmation());
        }

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
        errorLabel.setVisible(false);

        GridPane grid = new GridPane();
        grid.setVgap(7);
        grid.setHgap(12);
        grid.setPadding(new Insets(15, 20, 10, 10));
        int r = 0;
        grid.add(new Label("Load #*:"), 0, r);      grid.add(loadNumField, 1, r++);
        grid.add(new Label("PO:"), 0, r);           grid.add(poField, 1, r++);
        grid.add(new Label("Customer:"), 0, r);     grid.add(customerBox, 1, r++);
        grid.add(new Label("Pick Up:"), 0, r);      grid.add(pickUpField, 1, r++);
        grid.add(new Label("Drop Location:"), 0, r);grid.add(dropField, 1, r++);
        grid.add(new Label("Driver:"), 0, r);       grid.add(driverBox, 1, r++);
        grid.add(new Label("Find by Truck:"), 0, r);grid.add(truckUnitSearchField, 1, r++);
        grid.add(new Label("Trailer:"), 0, r);      grid.add(trailerBox, 1, r++);
        grid.add(new Label("Find by Trailer #:"), 0, r);grid.add(trailerSearchField, 1, r++);
        grid.add(new Label("Status:"), 0, r);       grid.add(statusBox, 1, r++);
        grid.add(new Label("Gross Amount:"), 0, r); grid.add(grossField, 1, r++);
        grid.add(new Label("Notes:"), 0, r);        grid.add(notesField, 1, r++);
        grid.add(new Label("Pick Up Date:"), 0, r); grid.add(pickUpDatePicker, 1, r++);  // NEW
        grid.add(new Label("Delivery Date:"), 0, r);grid.add(deliveryDatePicker, 1, r++);
        grid.add(new Label("Reminder:"), 0, r);     grid.add(reminderField, 1, r++);
        grid.add(hasLumperCheck, 1, r++);
        grid.add(hasRevisedRateCheck, 1, r++);
        grid.add(errorLabel, 1, r++);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

        Runnable validate = () -> {
            boolean loadNumValid = !loadNumField.getText().trim().isEmpty();
            boolean grossValid = grossField.getText().trim().isEmpty() || isDouble(grossField.getText());
            boolean duplicate = checkDuplicateLoadNumber(loadNumField.getText().trim(), isAdd ? -1 : (load != null ? load.getId() : -1));
            boolean deliveryRequired = false;
            Load.Status statusValue = statusBox.getValue();
            if (statusValue == Load.Status.DELIVERED || statusValue == Load.Status.PAID) {
                deliveryRequired = true;
            }
            boolean deliveryValid = !deliveryRequired || deliveryDatePicker.getValue() != null;
            if (duplicate && loadNumValid) {
                errorLabel.setText("Load # already exists.");
                errorLabel.setVisible(true);
            } else if (!deliveryValid) {
                errorLabel.setText("Delivery date required for DELIVERED or PAID status.");
                errorLabel.setVisible(true);
            } else {
                errorLabel.setVisible(false);
            }
            okBtn.setDisable(!(loadNumValid && grossValid && deliveryValid) || duplicate);
        };
        loadNumField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        grossField.textProperty().addListener((obs, oldV, newV) -> validate.run());
        statusBox.valueProperty().addListener((obs, oldV, newV) -> validate.run());
        deliveryDatePicker.valueProperty().addListener((obs, oldV, newV) -> validate.run());
        validate.run();

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    String loadNum = loadNumField.getText().trim();
                    String poNum = poField.getText().trim();
                    String customer = customerBox.getValue() != null ? customerBox.getValue().trim() : "";
                    String pickUp = pickUpField.getValue() != null ? pickUpField.getValue() : "";
                    String drop = dropField.getValue() != null ? dropField.getValue() : "";
                    Employee driver = driverBox.getValue();
                    Trailer trailer = trailerBox.getValue();
                    Load.Status status = statusBox.getValue() != null ? statusBox.getValue() : Load.Status.BOOKED;
                    double gross = grossField.getText().isEmpty() ? 0 : Double.parseDouble(grossField.getText());
                    String notes = notesField.getText().trim();
                    LocalDate pickUpDate = pickUpDatePicker.getValue();  // NEW
                    LocalDate deliveryDate = deliveryDatePicker.getValue();
                    String reminder = reminderField.getText().trim();
                    boolean hasLumper = hasLumperCheck.isSelected();
                    boolean hasRevisedRate = hasRevisedRateCheck.isSelected();
                    
					// Capture truck unit at time of load creation/update
                    String truckUnitSnapshot = driver != null ? driver.getTruckUnit() : "";
                    
                    // Get trailer info
                    int trailerId = 0;
                    String trailerNumber = "";
                    if (trailer != null) {
                        trailerId = trailer.getId();
                        trailerNumber = trailer.getTrailerNumber();
                    }

                    if (isAdd) {
                        logger.info("Adding new load: {}", loadNum);
                        Load newLoad = new Load(0, loadNum, poNum, customer, pickUp, drop, driver, 
                                             truckUnitSnapshot, status, gross, notes, pickUpDate, deliveryDate, 
                                             reminder, hasLumper, hasRevisedRate);
                        
                        // Set trailer information
                        newLoad.setTrailerId(trailerId);
                        newLoad.setTrailerNumber(trailerNumber);
                        newLoad.setTrailer(trailer);
                        
                        int newId = loadDAO.add(newLoad);
                        newLoad.setId(newId);
                        logger.info("Load added successfully: {} (ID: {})", loadNum, newId);
                        return newLoad;
                    } else {
                        logger.info("Updating load: {} (ID: {})", loadNum, load.getId());
                        load.setLoadNumber(loadNum);
                        load.setPONumber(poNum);
                        load.setCustomer(customer);
                        load.setPickUpLocation(pickUp);
                        load.setDropLocation(drop);
                        load.setDriver(driver);
                        
                        // Don't update truck unit snapshot on edit - preserve historical data
                        if (load.getTruckUnitSnapshot() == null || load.getTruckUnitSnapshot().isEmpty()) {
                            load.setTruckUnitSnapshot(truckUnitSnapshot);
                        }
                        
                        // Update trailer information
                        load.setTrailerId(trailerId);
                        load.setTrailerNumber(trailerNumber);
                        load.setTrailer(trailer);
                        
                        load.setStatus(status);
                        load.setGrossAmount(gross);
                        load.setNotes(notes);
                        load.setPickUpDate(pickUpDate);  // NEW
                        load.setDeliveryDate(deliveryDate);
                        load.setReminder(reminder);
                        load.setHasLumper(hasLumper);
                        load.setHasRevisedRateConfirmation(hasRevisedRate);
                        loadDAO.update(load);
                        logger.info("Load updated successfully: {}", loadNum);
                        return load;
                    }
                } catch (Exception ex) {
                    logger.error("Error in load dialog: {}", ex.getMessage(), ex);
                    ex.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            reloadAll();
            notifyLoadDataChanged(); // Notify listeners after add/edit
        });
    }

    private boolean checkDuplicateLoadNumber(String loadNum, int excludeId) {
        String norm = loadNum.trim().toLowerCase(Locale.ROOT);
        for (Load l : allLoads) {
            if (l.getId() != excludeId && l.getLoadNumber() != null &&
                l.getLoadNumber().trim().toLowerCase(Locale.ROOT).equals(norm)) {
                logger.debug("Duplicate load number found: {}", loadNum);
                return true;
            }
        }
        return false;
    }

    private void reloadAll() {
        logger.debug("Reloading all data");
        allLoads.setAll(loadDAO.getAll());
        allDrivers.setAll(employeeDAO.getAll());
        allTrailers.setAll(trailerDAO.findAll());
        allCustomers.setAll(loadDAO.getAllCustomers());
        for (StatusTab tab : statusTabs) {
            if (tab.filteredList != null)
                tab.filteredList.setPredicate(tab.filteredList.getPredicate());
        }
        logger.info("Data reload complete - Loads: {}, Drivers: {}, Trailers: {}, Customers: {}", 
            allLoads.size(), allDrivers.size(), allTrailers.size(), allCustomers.size());
    }

    /**
     * Call this method from LoadsTab when employee list changes.
     */
    public void onEmployeeDataChanged(List<Employee> currentList) {
        logger.debug("Employee data changed, updating drivers list with {} employees", currentList.size());
        allDrivers.setAll(currentList);
    }
    
    /**
     * Call this method when trailer list changes.
     */
    public void onTrailerDataChanged(List<Trailer> currentList) {
        logger.debug("Trailer data changed, updating trailers list with {} trailers", currentList.size());
        allTrailers.setAll(currentList);
    }

    private boolean isDouble(String s) {
        try { Double.parseDouble(s); return true; }
        catch (Exception e) { return false; }
    }

    private void exportCSV(TableView<Load> table) {
        logger.info("Exporting loads to CSV");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Loads to CSV");
        fileChooser.setInitialFileName("loads-export.csv");
        File file = fileChooser.showSaveDialog(table.getScene().getWindow());
        if (file == null) {
            logger.info("CSV export cancelled");
            return;
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            String header = table.getColumns().stream().map(TableColumn::getText).collect(Collectors.joining(","));
            bw.write(header); bw.newLine();
            int count = 0;
            for (Load l : table.getItems()) {
                String row = String.join(",",
                        safe(l.getLoadNumber()),
                        safe(l.getPONumber()),
                        safe(l.getCustomer()),
                        safe(l.getPickUpLocation()),
                        safe(l.getDropLocation()),
                        safe(l.getDriver() != null ? l.getDriver().getName() : ""),
                        safe(l.getTruckUnitSnapshot()),
                        safe(l.getTrailerNumber()),
                        safe(l.getStatus().toString()),
                        String.valueOf(l.getGrossAmount()),
                        safe(l.getReminder()),
                        safe(l.getPickUpDate() != null ? l.getPickUpDate().toString() : ""),
                        safe(l.getDeliveryDate() != null ? l.getDeliveryDate().toString() : "")
                );
                bw.write(row); bw.newLine();
                count++;
            }
            logger.info("Successfully exported {} loads to CSV: {}", count, file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to export CSV: {}", e.getMessage(), e);
            new Alert(Alert.AlertType.ERROR, "Failed to write file: " + e.getMessage()).showAndWait();
        }
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace(",", " ");
    }

    private void showError(String msg) {
        logger.error("Showing error dialog: {}", msg);
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText("Error");
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        logger.info("Showing info dialog: {}", msg);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText("Info");
        alert.showAndWait();
    }
}