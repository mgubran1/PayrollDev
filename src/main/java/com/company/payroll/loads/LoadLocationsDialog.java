package com.company.payroll.loads;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class LoadLocationsDialog extends Dialog<List<LoadLocation>> {
    private static final Logger logger = LoggerFactory.getLogger(LoadLocationsDialog.class);
    
    private final Load load;
    private final LoadDAO loadDAO;
    private final ObservableList<LoadLocation> pickupLocations = FXCollections.observableArrayList();
    private final ObservableList<LoadLocation> dropLocations = FXCollections.observableArrayList();
    private TableView<LoadLocation> pickupTable;
    private TableView<LoadLocation> dropTable;
    
    // UI fields for add/edit
    private ComboBox<LoadLocation.LocationType> typeBox;
    private ComboBox<String> customerBox;
    private ComboBox<String> addressBox;
    private ComboBox<String> cityBox;
    private ComboBox<String> stateBox;
    private DatePicker datePicker;
    private Spinner<LocalTime> timeSpinner;
    private TextArea notesArea;
    private Button addBtn;
    private Button clearBtn;
    private Button saveBtn;
    
    // For suggestions from unified address book
    private Map<String, List<String>> savedPickups = new HashMap<>();
    private Map<String, List<String>> savedDrops = new HashMap<>();
    private List<CustomerLocation> customerPickups = new ArrayList<>();
    private List<CustomerLocation> customerDrops = new ArrayList<>();
    private List<CustomerAddress> customerAddressBook = new ArrayList<>();
    
    public LoadLocationsDialog(Load load, LoadDAO loadDAO) {
        this.load = load;
        this.loadDAO = loadDAO;
        
        setTitle("Manage Load Locations");
        setHeaderText("Configure pickup and drop locations for Load #" + load.getLoadNumber());
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED); // Changed from UTILITY to DECORATED for better window controls
        setResizable(true); // Make dialog resizable
        
        // Set dialog size
        getDialogPane().setPrefSize(1400, 700);
        getDialogPane().setMinSize(1200, 600);
        
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, cancelButtonType);
        
        getDialogPane().setContent(createContent());
        loadExistingLocations();
        loadSavedLocations();
        
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return getAllLocations();
            }
            return null;
        });
    }
    
    private Node createContent() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setMinWidth(1100); // Set minimum width
        root.setPrefWidth(1300); // Increased preferred width
        root.setMinHeight(600); // Set minimum height
        
        // Add/Edit form with ScrollPane for horizontal scrolling if needed
        ScrollPane formScroll = new ScrollPane();
        formScroll.setContent(createAddEditForm());
        formScroll.setFitToHeight(true);
        formScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        formScroll.setStyle("-fx-background-color: transparent; -fx-padding: 5;");
        
        // Create table sections with scroll panes
        VBox pickupSection = createTableSection("Pickups", pickupLocations, true);
        VBox dropSection = createTableSection("Drops", dropLocations, false);
        
        // Wrap each table section in a ScrollPane
        ScrollPane pickupScroll = new ScrollPane(pickupSection);
        pickupScroll.setFitToWidth(false); // Allow horizontal scrolling
        pickupScroll.setFitToHeight(true);
        pickupScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pickupScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pickupScroll.setMinWidth(550);
        
        ScrollPane dropScroll = new ScrollPane(dropSection);
        dropScroll.setFitToWidth(false); // Allow horizontal scrolling
        dropScroll.setFitToHeight(true);
        dropScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        dropScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        dropScroll.setMinWidth(550);
        
        // Tables in HBox
        HBox tables = new HBox(20, pickupScroll, dropScroll);
        tables.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(tables, Priority.ALWAYS); // Allow tables to grow
        HBox.setHgrow(pickupScroll, Priority.ALWAYS);
        HBox.setHgrow(dropScroll, Priority.ALWAYS);
        
        root.getChildren().addAll(formScroll, new Separator(), tables);
        
        // Set the dialog pane to expand
        getDialogPane().setMinWidth(1200);
        getDialogPane().setPrefWidth(1400);
        getDialogPane().setMaxWidth(Double.MAX_VALUE);
        
        return root;
    }
    
    private HBox createAddEditForm() {
        HBox form = new HBox(8); // Reduced spacing for better fit
        form.setAlignment(Pos.CENTER_LEFT);
        form.setPadding(new Insets(10, 10, 10, 10)); // Added horizontal padding
        
        typeBox = new ComboBox<>(FXCollections.observableArrayList(LoadLocation.LocationType.PICKUP, LoadLocation.LocationType.DROP));
        typeBox.setValue(LoadLocation.LocationType.PICKUP);
        typeBox.setPrefWidth(100); // Reduced to make room for customer field
        
        customerBox = new ComboBox<>();
        customerBox.setEditable(true);
        customerBox.setPromptText("Customer");
        customerBox.setPrefWidth(150);
        // Pre-populate with load's customer
        if (load.getCustomer() != null && !load.getCustomer().isEmpty()) {
            customerBox.getItems().add(load.getCustomer());
            customerBox.setValue(load.getCustomer());
        }
        
        addressBox = new ComboBox<>();
        addressBox.setEditable(true);
        addressBox.setPromptText("Address");
        addressBox.setPrefWidth(180); // Reduced to make room
        
        cityBox = new ComboBox<>();
        cityBox.setEditable(true);
        cityBox.setPromptText("City");
        cityBox.setPrefWidth(140); // Increased from 120
        
        stateBox = new ComboBox<>();
        stateBox.setEditable(true);
        stateBox.setPromptText("State");
        stateBox.setPrefWidth(100); // Increased from 80
        
        datePicker = new DatePicker();
        datePicker.setPromptText("Date");
        datePicker.setPrefWidth(110);
        
        timeSpinner = createTimeSpinner();
        timeSpinner.setPrefWidth(90);
        
        notesArea = new TextArea();
        notesArea.setPromptText("Notes");
        notesArea.setPrefRowCount(1);
        notesArea.setPrefWidth(140);
        
        addBtn = new Button("Add Location");
        addBtn.setOnAction(e -> addLocation());
        addBtn.setDefaultButton(true);
        addBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        addBtn.setTooltip(new Tooltip("Add location to the selected type (Pickup/Drop)"));
        
        clearBtn = new Button("Clear Form");
        clearBtn.setOnAction(e -> clearForm());
        clearBtn.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;"); // Changed to grey color
        clearBtn.setTooltip(new Tooltip("Clear all form fields"));
        
        // Quick fill button to populate from saved customer locations
        Button quickFillBtn = new Button("Quick Fill");
        quickFillBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        quickFillBtn.setTooltip(new Tooltip("Select from saved customer locations"));
        quickFillBtn.setOnAction(e -> showQuickFillDialog());
        
        // Manage Customer Addresses button
        Button manageAddressesBtn = new Button("Manage Addresses");
        manageAddressesBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        manageAddressesBtn.setTooltip(new Tooltip("Manage customer address book"));
        manageAddressesBtn.setOnAction(e -> showManageAddressesDialog());
        
        // Suggestions update on type change
        typeBox.valueProperty().addListener((obs, oldVal, newVal) -> updateSuggestions());
        updateSuggestions();
        
        // Create labels with consistent styling
        Label typeLabel = new Label("Type:");
        typeLabel.setMinWidth(40);
        typeLabel.setStyle("-fx-font-weight: bold;");
        
        // Add all components to form
        form.getChildren().addAll(
            typeLabel, typeBox, 
            new Separator(Orientation.VERTICAL),
            customerBox, addressBox, cityBox, stateBox, 
            new Separator(Orientation.VERTICAL),
            datePicker, timeSpinner, 
            new Separator(Orientation.VERTICAL),
            notesArea, 
            new Separator(Orientation.VERTICAL),
            addBtn, clearBtn, quickFillBtn, manageAddressesBtn
        );
        
        return form;
    }
    
    private VBox createTableSection(String title, ObservableList<LoadLocation> locations, boolean isPickup) {
        VBox section = new VBox(8);
        section.setPadding(new Insets(5));
        section.setFillWidth(true);
        
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 15;");
        TableView<LoadLocation> table = createLocationTable(locations, isPickup);
        
        // Store reference to tables for refresh
        if (isPickup) {
            pickupTable = table;
        } else {
            dropTable = table;
        }
        
        // Add Remove Location button below the table
        Button removeBtn = new Button("Remove Location");
        removeBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
        removeBtn.setTooltip(new Tooltip("Remove selected location from the table"));
        removeBtn.setDisable(true); // Initially disabled
        
        // Enable/disable button based on selection
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            removeBtn.setDisable(newSelection == null);
        });
        
        // Handle remove action
        removeBtn.setOnAction(e -> {
            LoadLocation selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                deleteLocation(selected, locations);
            }
        });
        
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().add(removeBtn);
        
        section.getChildren().addAll(label, table, buttonBox);
        VBox.setVgrow(table, Priority.ALWAYS);
        return section;
    }
    
    private TableView<LoadLocation> createLocationTable(ObservableList<LoadLocation> locations, boolean isPickup) {
        TableView<LoadLocation> table = new TableView<>(locations);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY); // Allow columns to size naturally
        table.setPrefHeight(350); // Increased height
        table.setMinHeight(250); // Set minimum height
        table.setMinWidth(800); // Set minimum width to ensure all columns are visible
        
        TableColumn<LoadLocation, Integer> seqCol = new TableColumn<>("#");
        seqCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getSequence()));
        seqCol.setMaxWidth(40);
        
        TableColumn<LoadLocation, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCustomer()));
        customerCol.setMinWidth(120);
        
        TableColumn<LoadLocation, String> addressCol = new TableColumn<>("Address");
        addressCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAddress()));
        addressCol.setMinWidth(140); // Reduced to make room for customer
        
        TableColumn<LoadLocation, String> cityCol = new TableColumn<>("City");
        cityCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCity()));
        cityCol.setMinWidth(100); // Increased from 80
        
        TableColumn<LoadLocation, String> stateCol = new TableColumn<>("State");
        stateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getState()));
        stateCol.setMinWidth(50); // Changed from setMaxWidth to setMinWidth
        stateCol.setMaxWidth(80); // Added max width constraint
        
        TableColumn<LoadLocation, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getDate()));
        dateCol.setMinWidth(90);
        
        TableColumn<LoadLocation, String> timeCol = new TableColumn<>("Time");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");
        timeCol.setCellValueFactory(data -> {
            LocalTime time = data.getValue().getTime();
            return new SimpleStringProperty(time != null ? time.format(timeFormatter) : "");
        });
        timeCol.setMinWidth(80);
        
        TableColumn<LoadLocation, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNotes()));
        notesCol.setMinWidth(100);
        
        TableColumn<LoadLocation, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setMinWidth(180); // Adjusted width
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✏"); // Using pencil icon
            private final Button deleteBtn = new Button("🗑"); // Using trash icon
            private final Button upBtn = new Button("↑");
            private final Button downBtn = new Button("↓");
            private final HBox box = new HBox(3); // Reduced spacing
            {
                // Apply compact styling to buttons
                String buttonStyle = "-fx-padding: 4 8 4 8; -fx-font-size: 14px; -fx-background-radius: 3;";
                String editStyle = buttonStyle + " -fx-background-color: #2196F3; -fx-text-fill: white;";
                String deleteStyle = buttonStyle + " -fx-background-color: #f44336; -fx-text-fill: white;";
                String moveStyle = buttonStyle + " -fx-background-color: #9E9E9E; -fx-text-fill: white;";
                
                editBtn.setStyle(editStyle);
                deleteBtn.setStyle(deleteStyle);
                upBtn.setStyle(moveStyle);
                downBtn.setStyle(moveStyle);
                
                // Set tooltips for clarity
                editBtn.setTooltip(new Tooltip("Edit location"));
                deleteBtn.setTooltip(new Tooltip("Delete location"));
                upBtn.setTooltip(new Tooltip("Move up"));
                downBtn.setTooltip(new Tooltip("Move down"));
                
                editBtn.setOnAction(e -> editLocation(getTableView().getItems().get(getIndex()), locations));
                deleteBtn.setOnAction(e -> deleteLocation(getTableView().getItems().get(getIndex()), locations));
                upBtn.setOnAction(e -> moveLocation(getIndex(), -1, locations));
                downBtn.setOnAction(e -> moveLocation(getIndex(), 1, locations));
                
                box.getChildren().addAll(editBtn, deleteBtn, upBtn, downBtn);
                box.setAlignment(Pos.CENTER);
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        
        @SuppressWarnings("unchecked")
        TableColumn<LoadLocation, ?>[] columns = new TableColumn[] {
            seqCol, customerCol, addressCol, cityCol, stateCol, dateCol, timeCol, notesCol, actionsCol
        };
        table.getColumns().addAll(columns);
        table.setRowFactory(tv -> {
            TableRow<LoadLocation> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    editLocation(row.getItem(), locations);
                }
            });
            return row;
        });
        return table;
    }
    
    private Spinner<LocalTime> createTimeSpinner() {
        Spinner<LocalTime> spinner = new Spinner<>();
        spinner.setEditable(true);
        spinner.setValueFactory(new SpinnerValueFactory<LocalTime>() {
            {
                setValue(LocalTime.of(8, 0));
            }
            @Override
            public void decrement(int steps) {
                LocalTime current = getValue();
                setValue(current.minusMinutes(15 * steps));
            }
            @Override
            public void increment(int steps) {
                LocalTime current = getValue();
                setValue(current.plusMinutes(15 * steps));
            }
        });
        // Use a TextFormatter for the editor
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
        spinner.getEditor().setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty()) return change;
            try {
                LocalTime.parse(newText.toUpperCase(), formatter);
                return change;
            } catch (Exception e) {
                return null;
            }
        }));
        spinner.getEditor().setOnAction(e -> {
            try {
                LocalTime t = LocalTime.parse(spinner.getEditor().getText().toUpperCase(), formatter);
                spinner.getValueFactory().setValue(t);
            } catch (Exception ignored) {}
        });
        spinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) spinner.getEditor().setText(newV.format(formatter));
        });
        spinner.getValueFactory().setValue(LocalTime.of(8, 0));
        return spinner;
    }
    
    private void addLocation() {
        LoadLocation.LocationType type = typeBox.getValue();
        String customer = customerBox.getEditor().getText().trim();
        String address = addressBox.getEditor().getText().trim();
        String city = cityBox.getEditor().getText().trim();
        String state = stateBox.getEditor().getText().trim();
        LocalDate date = datePicker.getValue();
        LocalTime time = timeSpinner.getValue();
        String notes = notesArea.getText().trim();
        if (address.isEmpty() && city.isEmpty() && state.isEmpty()) {
            showError("Please enter at least an address, city, or state.");
            return;
        }
        
        // Auto-save to Customer Address Book if customer is specified
        if (!customer.isEmpty()) {
            String fullAddress = buildFullAddress(address, city, state);
            boolean isPickup = (type == LoadLocation.LocationType.PICKUP);
            loadDAO.autoSaveAddressToCustomerBook(customer, fullAddress, isPickup);
            
            // Refresh the customer address book for updated suggestions
            loadSavedLocations();
        }
        
        ObservableList<LoadLocation> list = (type == LoadLocation.LocationType.PICKUP) ? pickupLocations : dropLocations;
        int sequence = list.size() + 1;
        LoadLocation loc = new LoadLocation(type, customer, address, city, state, date, time, notes, sequence);
        list.add(loc);
        renumber(list);
        clearForm();
        refreshTables();
    }
    
    /**
     * Builds a full address string from components for auto-saving
     */
    private String buildFullAddress(String address, String city, String state) {
        StringBuilder fullAddr = new StringBuilder();
        if (address != null && !address.trim().isEmpty()) {
            fullAddr.append(address.trim());
        }
        if (city != null && !city.trim().isEmpty()) {
            if (fullAddr.length() > 0) fullAddr.append(", ");
            fullAddr.append(city.trim());
        }
        if (state != null && !state.trim().isEmpty()) {
            if (fullAddr.length() > 0) fullAddr.append(", ");
            fullAddr.append(state.trim());
        }
        return fullAddr.toString();
    }
    
    private void editLocation(LoadLocation location, ObservableList<LoadLocation> list) {
        Dialog<LoadLocation> dialog = new Dialog<>();
        dialog.setTitle("Edit Location");
        dialog.setHeaderText("Edit " + location.getType() + " Location #" + location.getSequence());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.DECORATED);
        dialog.setResizable(true);
        ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);
        
        TextField customerEdit = new TextField(location.getCustomer());
        customerEdit.setPromptText("Customer");
        ComboBox<String> addressEdit = new ComboBox<>(getSuggestions(location.getType(), "address"));
        addressEdit.setEditable(true);
        addressEdit.setValue(location.getAddress());
        ComboBox<String> cityEdit = new ComboBox<>(getSuggestions(location.getType(), "city"));
        cityEdit.setEditable(true);
        cityEdit.setValue(location.getCity());
        ComboBox<String> stateEdit = new ComboBox<>(getSuggestions(location.getType(), "state"));
        stateEdit.setEditable(true);
        stateEdit.setValue(location.getState());
        DatePicker dateEdit = new DatePicker(location.getDate());
        Spinner<LocalTime> timeEdit = createTimeSpinner();
        timeEdit.getValueFactory().setValue(location.getTime() != null ? location.getTime() : LocalTime.of(8, 0));
        TextArea notesEdit = new TextArea(location.getNotes());
        notesEdit.setPrefRowCount(1);
        notesEdit.setPrefWidth(140);
        
        GridPane grid = new GridPane();
        grid.setVgap(8);
        grid.setHgap(10);
        grid.setPadding(new Insets(15));
        int r = 0;
        grid.add(new Label("Customer:"), 0, r); grid.add(customerEdit, 1, r++);
        grid.add(new Label("Address:"), 0, r); grid.add(addressEdit, 1, r++);
        grid.add(new Label("City:"), 0, r); grid.add(cityEdit, 1, r++);
        grid.add(new Label("State:"), 0, r); grid.add(stateEdit, 1, r++);
        grid.add(new Label("Date:"), 0, r); grid.add(dateEdit, 1, r++);
        grid.add(new Label("Time:"), 0, r); grid.add(timeEdit, 1, r++);
        grid.add(new Label("Notes:"), 0, r); grid.add(notesEdit, 1, r++);
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(btn -> {
            if (btn == saveBtnType) {
                location.setCustomer(customerEdit.getText().trim());
                location.setAddress(addressEdit.getEditor().getText().trim());
                location.setCity(cityEdit.getEditor().getText().trim());
                location.setState(stateEdit.getEditor().getText().trim());
                location.setDate(dateEdit.getValue());
                location.setTime(timeEdit.getValue());
                location.setNotes(notesEdit.getText().trim());
                return location;
            }
            return null;
        });
        dialog.showAndWait();
        renumber(list);
        refreshTables();
    }
    
    private void deleteLocation(LoadLocation location, ObservableList<LoadLocation> list) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete this location?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Confirm Delete");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                list.remove(location);
                renumber(list);
                refreshTables();
            }
        });
    }
    
    private void moveLocation(int index, int delta, ObservableList<LoadLocation> list) {
        int newIndex = index + delta;
        if (newIndex < 0 || newIndex >= list.size()) return;
        Collections.swap(list, index, newIndex);
        renumber(list);
        refreshTables();
    }
    
    private void renumber(ObservableList<LoadLocation> list) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSequence(i + 1);
        }
    }
    
    private void clearForm() {
        customerBox.getEditor().clear();
        addressBox.getEditor().clear();
        cityBox.getEditor().clear();
        stateBox.getEditor().clear();
        datePicker.setValue(null);
        timeSpinner.getValueFactory().setValue(LocalTime.of(8, 0));
        notesArea.clear();
        // Reset customer to load's customer
        if (load.getCustomer() != null && !load.getCustomer().isEmpty()) {
            customerBox.setValue(load.getCustomer());
        }
    }
    
    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText("Error");
        alert.showAndWait();
    }
    
    private void loadExistingLocations() {
        pickupLocations.clear();
        dropLocations.clear();
        for (LoadLocation location : load.getLocations()) {
            if (location.getType() == LoadLocation.LocationType.PICKUP) {
                pickupLocations.add(location);
            } else {
                dropLocations.add(location);
            }
        }
        renumber(pickupLocations);
        renumber(dropLocations);
    }
    
    private void loadSavedLocations() {
        savedPickups.clear();
        savedDrops.clear();
        customerPickups.clear();
        customerDrops.clear();
        customerAddressBook.clear();
        
        String customer = load.getCustomer();
        if (customer != null && !customer.isEmpty()) {
            // Load unified address book
            customerAddressBook = loadDAO.getCustomerAddressBook(customer);
            
            // Extract suggestions from unified address book for both pickup and drop
            List<String> allAddresses = new ArrayList<>();
            List<String> allCities = new ArrayList<>();
            List<String> allStates = new ArrayList<>();
            
            for (CustomerAddress addr : customerAddressBook) {
                if (addr.getAddress() != null && !addr.getAddress().trim().isEmpty()) {
                    allAddresses.add(addr.getAddress());
                }
                if (addr.getCity() != null && !addr.getCity().trim().isEmpty()) {
                    allCities.add(addr.getCity());
                }
                if (addr.getState() != null && !addr.getState().trim().isEmpty()) {
                    allStates.add(addr.getState());
                }
            }
            
            // Make suggestions available for both pickup and drop (unified approach)
            savedPickups.put("address", new ArrayList<>(allAddresses));
            savedDrops.put("address", new ArrayList<>(allAddresses));
            savedPickups.put("city", new ArrayList<>(allCities));
            savedDrops.put("city", new ArrayList<>(allCities));
            savedPickups.put("state", new ArrayList<>(allStates));
            savedDrops.put("state", new ArrayList<>(allStates));
            
            // Also load legacy format for backward compatibility
            Map<String, List<CustomerLocation>> fullLocations = loadDAO.getAllCustomerLocationsFull(customer);
            customerPickups = fullLocations.getOrDefault("PICKUP", new ArrayList<>());
            customerDrops = fullLocations.getOrDefault("DROP", new ArrayList<>());
            
            // Extract suggestions from legacy locations
            List<String> legacyPickupAddresses = extractAddresses(customerPickups);
            List<String> legacyDropAddresses = extractAddresses(customerDrops);
            
            // Add legacy addresses if not already present
            for (String addr : legacyPickupAddresses) {
                if (!savedPickups.get("address").contains(addr)) {
                    savedPickups.get("address").add(addr);
                    savedDrops.get("address").add(addr); // Also add to drops for unified approach
                }
            }
            for (String addr : legacyDropAddresses) {
                if (!savedDrops.get("address").contains(addr)) {
                    savedDrops.get("address").add(addr);
                    savedPickups.get("address").add(addr); // Also add to pickups for unified approach
                }
            }
            
            // Add legacy cities and states
            List<String> legacyPickupCities = extractCitiesFromLocations(customerPickups);
            List<String> legacyDropCities = extractCitiesFromLocations(customerDrops);
            List<String> legacyPickupStates = extractStatesFromLocations(customerPickups);
            List<String> legacyDropStates = extractStatesFromLocations(customerDrops);
            
            for (String city : legacyPickupCities) {
                if (!savedPickups.get("city").contains(city)) {
                    savedPickups.get("city").add(city);
                    savedDrops.get("city").add(city);
                }
            }
            for (String city : legacyDropCities) {
                if (!savedDrops.get("city").contains(city)) {
                    savedDrops.get("city").add(city);
                    savedPickups.get("city").add(city);
                }
            }
            for (String state : legacyPickupStates) {
                if (!savedPickups.get("state").contains(state)) {
                    savedPickups.get("state").add(state);
                    savedDrops.get("state").add(state);
                }
            }
            for (String state : legacyDropStates) {
                if (!savedDrops.get("state").contains(state)) {
                    savedDrops.get("state").add(state);
                    savedPickups.get("state").add(state);
                }
            }
        }
    }
    
    private List<String> extractAddresses(List<CustomerLocation> locations) {
        List<String> addresses = new ArrayList<>();
        for (CustomerLocation loc : locations) {
            if (loc.getAddress() != null && !loc.getAddress().isEmpty()) {
                addresses.add(loc.getAddress());
            }
        }
        return addresses;
    }
    
    private List<String> extractCitiesFromLocations(List<CustomerLocation> locations) {
        Set<String> cities = new HashSet<>();
        for (CustomerLocation loc : locations) {
            if (loc.getCity() != null && !loc.getCity().isEmpty()) {
                cities.add(loc.getCity());
            }
        }
        return new ArrayList<>(cities);
    }
    
    private List<String> extractStatesFromLocations(List<CustomerLocation> locations) {
        Set<String> states = new HashSet<>();
        for (CustomerLocation loc : locations) {
            if (loc.getState() != null && !loc.getState().isEmpty()) {
                states.add(loc.getState());
            }
        }
        return new ArrayList<>(states);
    }

    private List<String> extractCities(List<String> addresses) {
        Set<String> cities = new HashSet<>();
        for (String addr : addresses) {
            String[] parts = addr.split(",");
            if (parts.length > 1) {
                cities.add(parts[1].trim());
            }
        }
        return new ArrayList<>(cities);
    }

    private List<String> extractStates(List<String> addresses) {
        Set<String> states = new HashSet<>();
        for (String addr : addresses) {
            String[] parts = addr.split(",");
            if (parts.length > 2) {
                states.add(parts[2].trim());
            } else if (parts.length > 1) {
                String[] stateParts = parts[1].trim().split(" ");
                if (stateParts.length > 1) states.add(stateParts[1].trim());
            }
        }
        return new ArrayList<>(states);
    }

    private void updateSuggestions() {
        LoadLocation.LocationType type = typeBox.getValue();
        addressBox.setItems(getSuggestions(type, "address"));
        cityBox.setItems(getSuggestions(type, "city"));
        stateBox.setItems(getSuggestions(type, "state"));
    }

    private ObservableList<String> getSuggestions(LoadLocation.LocationType type, String field) {
        if (type == LoadLocation.LocationType.PICKUP) {
            return FXCollections.observableArrayList(savedPickups.getOrDefault(field, Collections.emptyList()));
        } else {
            return FXCollections.observableArrayList(savedDrops.getOrDefault(field, Collections.emptyList()));
        }
    }

    private List<LoadLocation> getAllLocations() {
        List<LoadLocation> allLocations = new ArrayList<>();
        allLocations.addAll(pickupLocations);
        allLocations.addAll(dropLocations);
        return allLocations;
    }
    
    private void showQuickFillDialog() {
        LoadLocation.LocationType type = typeBox.getValue();
        if (type == null) {
            showError("Please select a location type first.");
            return;
        }
        
        // Use unified address book instead of separate pickup/drop lists
        if (customerAddressBook.isEmpty()) {
            showError("No saved addresses found for this customer.");
            return;
        }
        
        Dialog<CustomerAddress> dialog = new Dialog<>();
        dialog.setTitle("Select Customer Address");
        dialog.setHeaderText("Choose a saved address to auto-fill the form");
        dialog.initModality(Modality.APPLICATION_MODAL);
        
        ListView<CustomerAddress> listView = new ListView<>();
        listView.setCellFactory(lv -> new ListCell<CustomerAddress>() {
            @Override
            protected void updateItem(CustomerAddress item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    String displayText = item.getDisplayText();
                    // Show default indicators
                    if (type == LoadLocation.LocationType.PICKUP && item.isDefaultPickup()) {
                        displayText += " (Default Pickup)";
                        setStyle("-fx-font-weight: bold; -fx-background-color: #E8F5E8;");
                    } else if (type == LoadLocation.LocationType.DROP && item.isDefaultDrop()) {
                        displayText += " (Default Drop)";
                        setStyle("-fx-font-weight: bold; -fx-background-color: #E8F5E8;");
                    } else {
                        setStyle("");
                    }
                    setText(displayText);
                }
            }
        });
        listView.setItems(FXCollections.observableArrayList(customerAddressBook));
        listView.setPrefHeight(200);
        
        // Select default address if exists
        customerAddressBook.stream()
            .filter(addr -> (type == LoadLocation.LocationType.PICKUP && addr.isDefaultPickup()) ||
                           (type == LoadLocation.LocationType.DROP && addr.isDefaultDrop()))
            .findFirst()
            .ifPresent(listView.getSelectionModel()::select);
        
        VBox content = new VBox(10);
        content.getChildren().addAll(new Label("Select an address:"), listView);
        dialog.getDialogPane().setContent(content);
        
        ButtonType selectType = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectType, ButtonType.CANCEL);
        
        Node selectButton = dialog.getDialogPane().lookupButton(selectType);
        selectButton.setDisable(true);
        
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectButton.setDisable(newVal == null);
        });
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == selectType) {
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(address -> {
            // Fill form with selected address
            addressBox.getEditor().setText(address.getAddress() != null ? address.getAddress() : "");
            cityBox.getEditor().setText(address.getCity() != null ? address.getCity() : "");
            stateBox.getEditor().setText(address.getState() != null ? address.getState() : "");
            // Optionally set current date/time
            if (datePicker.getValue() == null) {
                datePicker.setValue(LocalDate.now());
            }
        });
    }
    
    private void refreshTables() {
        // Force refresh of both tables
        if (pickupTable != null) {
            pickupTable.refresh();
        }
        if (dropTable != null) {
            dropTable.refresh();
        }
    }
    
    /**
     * Shows a dialog for managing customer addresses in the address book
     */
    private void showManageAddressesDialog() {
        String customer = customerBox.getEditor().getText().trim();
        if (customer.isEmpty()) {
            showError("Please select or enter a customer name first.");
            return;
        }
        
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Manage Customer Addresses");
        dialog.setHeaderText("Customer Address Book for: " + customer);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(true);
        
        // Create table to show existing addresses
        TableView<CustomerAddress> addressTable = new TableView<>();
        addressTable.setPrefHeight(300);
        addressTable.setPrefWidth(600);
        
        TableColumn<CustomerAddress, String> nameCol = new TableColumn<>("Location Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLocationName()));
        nameCol.setPrefWidth(120);
        
        TableColumn<CustomerAddress, String> addressCol = new TableColumn<>("Address");
        addressCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAddress()));
        addressCol.setPrefWidth(150);
        
        TableColumn<CustomerAddress, String> cityCol = new TableColumn<>("City");
        cityCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCity()));
        cityCol.setPrefWidth(100);
        
        TableColumn<CustomerAddress, String> stateCol = new TableColumn<>("State");
        stateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getState()));
        stateCol.setPrefWidth(60);
        
        TableColumn<CustomerAddress, String> defaultsCol = new TableColumn<>("Default For");
        defaultsCol.setCellValueFactory(data -> {
            CustomerAddress addr = data.getValue();
            String defaults = "";
            if (addr.isDefaultPickup() && addr.isDefaultDrop()) {
                defaults = "Pickup & Drop";
            } else if (addr.isDefaultPickup()) {
                defaults = "Pickup";
            } else if (addr.isDefaultDrop()) {
                defaults = "Drop";
            }
            return new SimpleStringProperty(defaults);
        });
        defaultsCol.setPrefWidth(100);
        
        TableColumn<CustomerAddress, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(130);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(5, editBtn, deleteBtn);
            {
                editBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 2 8 2 8;");
                deleteBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-padding: 2 8 2 8;");
                
                editBtn.setOnAction(e -> {
                    CustomerAddress addr = getTableView().getItems().get(getIndex());
                    editCustomerAddress(addr, customer, addressTable);
                });
                
                deleteBtn.setOnAction(e -> {
                    CustomerAddress addr = getTableView().getItems().get(getIndex());
                    deleteCustomerAddress(addr, addressTable);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        
        @SuppressWarnings("unchecked")
        TableColumn<CustomerAddress, ?>[] columns = new TableColumn[] {
            nameCol, addressCol, cityCol, stateCol, defaultsCol, actionsCol
        };
        addressTable.getColumns().addAll(columns);
        
        // Load addresses
        ObservableList<CustomerAddress> addresses = FXCollections.observableArrayList(
            loadDAO.getCustomerAddressBook(customer)
        );
        addressTable.setItems(addresses);
        
        // Add/Edit buttons
        Button addAddressBtn = new Button("Add New Address");
        addAddressBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addAddressBtn.setOnAction(e -> addNewCustomerAddress(customer, addressTable));
        
        VBox content = new VBox(10);
        content.getChildren().addAll(
            new Label("Existing addresses for " + customer + ":"),
            addressTable,
            addAddressBtn
        );
        content.setPadding(new Insets(15));
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                // Refresh the suggestions when dialog closes
                loadSavedLocations();
                return true;
            }
            return false;
        });
        
        dialog.showAndWait();
    }
    
    private void addNewCustomerAddress(String customer, TableView<CustomerAddress> table) {
        CustomerAddressDialog dialog = new CustomerAddressDialog(null);
        dialog.showAndWait().ifPresent(address -> {
            address.setCustomerId(0); // Will be set by DAO
            int id = loadDAO.addCustomerAddress(customer, address.getLocationName(),
                address.getAddress(), address.getCity(), address.getState());
            if (id > 0) {
                address.setId(id);
                table.getItems().add(address);
                logger.info("Added new customer address: {}", address.getDisplayText());
            }
        });
    }
    
    private void editCustomerAddress(CustomerAddress address, String customer, TableView<CustomerAddress> table) {
        CustomerAddressDialog dialog = new CustomerAddressDialog(address);
        dialog.showAndWait().ifPresent(updatedAddress -> {
            loadDAO.updateCustomerAddress(updatedAddress);
            table.refresh();
            logger.info("Updated customer address: {}", updatedAddress.getDisplayText());
        });
    }
    
    private void deleteCustomerAddress(CustomerAddress address, TableView<CustomerAddress> table) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Address");
        alert.setHeaderText("Delete this address?");
        alert.setContentText(address.getDisplayText());
        
        alert.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                loadDAO.deleteCustomerAddress(address.getId());
                table.getItems().remove(address);
                logger.info("Deleted customer address: {}", address.getDisplayText());
            }
        });
    }
} 