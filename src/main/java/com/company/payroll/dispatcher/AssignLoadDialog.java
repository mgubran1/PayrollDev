package com.company.payroll.dispatcher;

import com.company.payroll.loads.Load;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Dialog for assigning loads to drivers
 */
public class AssignLoadDialog extends Dialog<Boolean> {
    private static final Logger logger = LoggerFactory.getLogger(AssignLoadDialog.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    
    private final DispatcherController controller;
    private ComboBox<Load> loadCombo;
    private ComboBox<DispatcherDriverStatus> driverCombo;
    private TextArea notesArea;
    
    public AssignLoadDialog(DispatcherController controller) {
        this.controller = controller;
        
        setTitle("Assign Load to Driver");
        setHeaderText("Select an unassigned load and available driver");
        initModality(Modality.APPLICATION_MODAL);
        
        initializeUI();
        
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return performAssignment();
            }
            return false;
        });
    }
    
    private void initializeUI() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        // Load selection
        Label loadLabel = new Label("Select Load:");
        loadCombo = new ComboBox<>();
        loadCombo.setPrefWidth(400);
        
        List<Load> unassignedLoads = controller.getUnassignedLoads();
        loadCombo.getItems().addAll(unassignedLoads);
        
        loadCombo.setCellFactory(lv -> new ListCell<Load>() {
            @Override
            protected void updateItem(Load load, boolean empty) {
                super.updateItem(load, empty);
                if (empty || load == null) {
                    setText(null);
                } else {
                    setText(formatLoadDisplay(load));
                }
            }
        });
        
        loadCombo.setButtonCell(new ListCell<Load>() {
            @Override
            protected void updateItem(Load load, boolean empty) {
                super.updateItem(load, empty);
                if (empty || load == null) {
                    setText(null);
                } else {
                    setText(formatLoadDisplay(load));
                }
            }
        });
        
        // Driver selection
        Label driverLabel = new Label("Select Driver:");
        driverCombo = new ComboBox<>();
        driverCombo.setPrefWidth(400);
        
        List<DispatcherDriverStatus> availableDrivers = controller.getAvailableDrivers();
        driverCombo.getItems().addAll(availableDrivers);
        
        driverCombo.setCellFactory(lv -> new ListCell<DispatcherDriverStatus>() {
            @Override
            protected void updateItem(DispatcherDriverStatus driver, boolean empty) {
                super.updateItem(driver, empty);
                if (empty || driver == null) {
                    setText(null);
                } else {
                    setText(formatDriverDisplay(driver));
                }
            }
        });
        
        driverCombo.setButtonCell(new ListCell<DispatcherDriverStatus>() {
            @Override
            protected void updateItem(DispatcherDriverStatus driver, boolean empty) {
                super.updateItem(driver, empty);
                if (empty || driver == null) {
                    setText(null);
                } else {
                    setText(formatDriverDisplay(driver));
                }
            }
        });
        
        // Load details
        VBox loadDetailsBox = new VBox(5);
        loadDetailsBox.setPadding(new Insets(10));
        loadDetailsBox.setStyle("-fx-border-color: #ddd; -fx-border-radius: 3; -fx-background-radius: 3;");
        Label loadDetailsLabel = new Label("Load Details:");
        loadDetailsLabel.setStyle("-fx-font-weight: bold;");
        Label loadDetailsContent = new Label("Select a load to view details");
        loadDetailsContent.setWrapText(true);
        loadDetailsBox.getChildren().addAll(loadDetailsLabel, loadDetailsContent);
        
        // Driver details
        VBox driverDetailsBox = new VBox(5);
        driverDetailsBox.setPadding(new Insets(10));
        driverDetailsBox.setStyle("-fx-border-color: #ddd; -fx-border-radius: 3; -fx-background-radius: 3;");
        Label driverDetailsLabel = new Label("Driver Details:");
        driverDetailsLabel.setStyle("-fx-font-weight: bold;");
        Label driverDetailsContent = new Label("Select a driver to view details");
        driverDetailsContent.setWrapText(true);
        driverDetailsBox.getChildren().addAll(driverDetailsLabel, driverDetailsContent);
        
        // Update details on selection
        loadCombo.setOnAction(e -> {
            Load selected = loadCombo.getValue();
            if (selected != null) {
                loadDetailsContent.setText(formatLoadDetails(selected));
            }
        });
        
        driverCombo.setOnAction(e -> {
            DispatcherDriverStatus selected = driverCombo.getValue();
            if (selected != null) {
                driverDetailsContent.setText(formatDriverDetails(selected));
            }
        });
        
        // Notes
        Label notesLabel = new Label("Assignment Notes:");
        notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        notesArea.setPromptText("Enter any special instructions or notes...");
        
        // Layout
        int row = 0;
        grid.add(loadLabel, 0, row++, 2, 1);
        grid.add(loadCombo, 0, row++, 2, 1);
        grid.add(loadDetailsBox, 0, row++, 2, 1);
        
        grid.add(new Separator(), 0, row++, 2, 1);
        
        grid.add(driverLabel, 0, row++, 2, 1);
        grid.add(driverCombo, 0, row++, 2, 1);
        grid.add(driverDetailsBox, 0, row++, 2, 1);
        
        grid.add(new Separator(), 0, row++, 2, 1);
        
        grid.add(notesLabel, 0, row++, 2, 1);
        grid.add(notesArea, 0, row++, 2, 1);
        
        getDialogPane().setContent(grid);
        getDialogPane().setPrefSize(500, 600);
    }
    
    private String formatLoadDisplay(Load load) {
        return String.format("%s - %s (%s → %s) - Pickup: %s",
            load.getLoadNumber(),
            load.getCustomer(),
            getCityName(load.getPickUpLocation()),
            getCityName(load.getDropLocation()),
            load.getPickUpDate() != null ? load.getPickUpDate().toString() : "TBD"
        );
    }
    
    private String formatLoadDetails(Load load) {
        StringBuilder sb = new StringBuilder();
        sb.append("Load #: ").append(load.getLoadNumber()).append("\n");
        sb.append("PO: ").append(load.getPONumber()).append("\n");
        sb.append("Customer: ").append(load.getCustomer()).append("\n");
        sb.append("Pickup: ").append(load.getPickUpLocation()).append("\n");
        if (load.getPickUpDate() != null) {
            sb.append("Pickup Date: ").append(load.getPickUpDate());
            if (load.getPickUpTime() != null) {
                sb.append(" @ ").append(load.getPickUpTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            sb.append("\n");
        }
        sb.append("Delivery: ").append(load.getDropLocation()).append("\n");
        if (load.getDeliveryDate() != null) {
            sb.append("Delivery Date: ").append(load.getDeliveryDate());
            if (load.getDeliveryTime() != null) {
                sb.append(" @ ").append(load.getDeliveryTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            sb.append("\n");
        }
        sb.append("Gross Amount: $").append(String.format("%.2f", load.getGrossAmount()));
        
        return sb.toString();
    }
    
    private String formatDriverDisplay(DispatcherDriverStatus driver) {
        return String.format("%s (%s) - %s - %s",
            driver.getDriverName(),
            driver.getTruckUnit(),
            driver.getStatus().getDisplayName(),
            driver.getCurrentLocation()
        );
    }
    
    private String formatDriverDetails(DispatcherDriverStatus driver) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(driver.getDriverName()).append("\n");
        sb.append("Truck: ").append(driver.getTruckUnit()).append("\n");
        sb.append("Trailer: ").append(driver.getTrailerNumber()).append("\n");
        sb.append("Status: ").append(driver.getStatus().getDisplayName()).append("\n");
        sb.append("Location: ").append(driver.getCurrentLocation()).append("\n");
        sb.append("Hours Today: ").append(String.format("%.1f", driver.getHoursWorkedToday())).append("\n");
        sb.append("Hours This Week: ").append(String.format("%.1f", driver.getHoursWorkedWeek()));
        
        if (driver.getNextLoad() != null) {
            sb.append("\n\nNext Load: ").append(driver.getNextLoad().getLoadNumber());
        }
        
        return sb.toString();
    }
    
    private String getCityName(String location) {
        if (location == null || location.isEmpty()) return "";
        String[] parts = location.split(",");
        return parts[0].trim();
    }
    
    private boolean performAssignment() {
        Load selectedLoad = loadCombo.getValue();
        DispatcherDriverStatus selectedDriver = driverCombo.getValue();
        
        if (selectedLoad == null || selectedDriver == null) {
            showError("Please select both a load and a driver.");
            return false;
        }
        
        try {
            // TODO: Implement actual assignment logic
            logger.info("Assigning load {} to driver {}", 
                selectedLoad.getLoadNumber(), selectedDriver.getDriverName());
            
            // Show success
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Assignment Successful");
            alert.setHeaderText(null);
            alert.setContentText(String.format("Load %s has been assigned to %s",
                selectedLoad.getLoadNumber(), selectedDriver.getDriverName()));
            alert.showAndWait();
            
            return true;
        } catch (Exception e) {
            logger.error("Failed to assign load", e);
            showError("Failed to assign load: " + e.getMessage());
            return false;
        }
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Assignment Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}