package com.company.payroll.maintenance;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;
import com.company.payroll.trucks.TrucksTab;
import com.company.payroll.trailers.TrailersTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MaintenanceTab extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(MaintenanceTab.class);

    private final ObservableList<MaintenanceRecord> records = FXCollections.observableArrayList();
    private final MaintenanceDAO dao = new MaintenanceDAO();
    private final TrucksTab trucksTab;
    private final TrailersTab trailersTab;

    public interface MaintenanceDataChangeListener {
        void onMaintenanceDataChanged(List<MaintenanceRecord> currentList);
    }
    private final List<MaintenanceDataChangeListener> listeners = new ArrayList<>();

    public MaintenanceTab(TrucksTab trucksTab, TrailersTab trailersTab) {
        logger.info("Initializing MaintenanceTab");
        this.trucksTab = trucksTab;
        this.trailersTab = trailersTab;
        records.setAll(dao.getAll());

        TextField searchField = new TextField();
        searchField.setPromptText("Search description...");

        HBox filterBox = new HBox(10, searchField);
        filterBox.setPadding(new Insets(10,10,0,10));
        filterBox.setAlignment(Pos.CENTER_LEFT);

        TableView<MaintenanceRecord> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<MaintenanceRecord,String> typeCol = new TableColumn<>("Vehicle");
        typeCol.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().getVehicleType()!=null?r.getValue().getVehicleType().name():""));

        TableColumn<MaintenanceRecord,String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(r -> new SimpleStringProperty(String.valueOf(r.getValue().getVehicleId())));

        TableColumn<MaintenanceRecord, LocalDate> dateCol = new TableColumn<>("Service Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("serviceDate"));

        TableColumn<MaintenanceRecord,String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().getDescription()));

        TableColumn<MaintenanceRecord,String> costCol = new TableColumn<>("Cost");
        costCol.setCellValueFactory(r -> new SimpleStringProperty(String.format("%.2f", r.getValue().getCost())));

        TableColumn<MaintenanceRecord, LocalDate> nextCol = new TableColumn<>("Next Due");
        nextCol.setCellValueFactory(new PropertyValueFactory<>("nextDue"));
        nextCol.setCellFactory(getExpiryCellFactory());

        table.getColumns().addAll(typeCol,idCol,dateCol,descCol,costCol,nextCol);

        FilteredList<MaintenanceRecord> filtered = new FilteredList<>(records,p->true);
        searchField.textProperty().addListener((obs,o,n)->filtered.setPredicate(r->filterRecord(r,n)));
        SortedList<MaintenanceRecord> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        table.setRowFactory(tv->{
            TableRow<MaintenanceRecord> row = new TableRow<>();
            row.setOnMouseClicked(e->{
                if(e.getClickCount()==2 && !row.isEmpty()) showRecordDialog(row.getItem(),false);
            });
            return row;
        });

        Button addBtn = new Button("Add");
        Button editBtn = new Button("Edit");
        Button delBtn = new Button("Delete");
        Button refreshBtn = new Button("Refresh");

        addBtn.setOnAction(e->showRecordDialog(null,true));
        editBtn.setOnAction(e->{
            MaintenanceRecord r = table.getSelectionModel().getSelectedItem();
            if(r!=null) showRecordDialog(r,false);
        });
        delBtn.setOnAction(e->{
            MaintenanceRecord r = table.getSelectionModel().getSelectedItem();
            if(r!=null){
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete record?", ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp->{
                    if(resp==ButtonType.YES){
                        dao.delete(r.getId());
                        records.setAll(dao.getAll());
                        notifyMaintenanceDataChanged();
                    }
                });
            }
        });
        refreshBtn.setOnAction(e->{
            records.setAll(dao.getAll());
            notifyMaintenanceDataChanged();
        });

        HBox btnBox = new HBox(10, addBtn, editBtn, delBtn, refreshBtn);
        btnBox.setPadding(new Insets(12));
        btnBox.setAlignment(Pos.CENTER_LEFT);

        setCenter(new VBox(filterBox, table));
        setBottom(btnBox);
        setPadding(new Insets(10));
    }

    private boolean filterRecord(MaintenanceRecord r, String text){
        String lower = text==null?"":text.toLowerCase(Locale.ROOT);
        if(lower.isEmpty()) return true;
        return r.getDescription()!=null && r.getDescription().toLowerCase(Locale.ROOT).contains(lower);
    }

    private void showRecordDialog(MaintenanceRecord record, boolean isAdd){
        Dialog<MaintenanceRecord> dialog = new Dialog<>();
        dialog.setTitle(isAdd?"Add Maintenance":"Edit Maintenance");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<MaintenanceRecord.VehicleType> typeBox = new ComboBox<>(FXCollections.observableArrayList(MaintenanceRecord.VehicleType.values()));
        ComboBox<String> idBox = new ComboBox<>();
        DatePicker serviceDatePicker = new DatePicker();
        TextField descField = new TextField();
        TextField costField = new TextField();
        DatePicker nextDuePicker = new DatePicker();
        TextField receiptNumField = new TextField();
        TextField receiptPathField = new TextField();
        receiptPathField.setEditable(false);
        Button browseBtn = new Button("Browse...");
        browseBtn.setOnAction(e->{
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Receipt");
            File file = chooser.showOpenDialog(getScene().getWindow());
            if(file!=null){
                try{
                    File destDir = new File("receipts");
                    destDir.mkdirs();
                    File dest = new File(destDir,file.getName());
                    Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    receiptPathField.setText(dest.getAbsolutePath());
                }catch(Exception ex){
                    logger.error("Failed to copy receipt",ex);
                }
            }
        });
        Button previewBtn = new Button("Preview");
        previewBtn.setOnAction(e->{
            String path = receiptPathField.getText();
            if(path!=null && !path.isEmpty()){
                try{ java.awt.Desktop.getDesktop().open(new File(path)); }catch(Exception ex){ logger.error("Failed to open receipt",ex); }
            }
        });

        typeBox.valueProperty().addListener((o,ov,nv)->{
            idBox.getItems().clear();
            if(nv==MaintenanceRecord.VehicleType.TRUCK && trucksTab!=null){
                trucksTab.getCurrentTrucks().forEach(t->idBox.getItems().add(t.getUnit()));
            }else if(nv==MaintenanceRecord.VehicleType.TRAILER && trailersTab!=null){
                trailersTab.getCurrentTrailers().forEach(tr->idBox.getItems().add(tr.getNumber()));
            }
        });

        if(record!=null){
            typeBox.setValue(record.getVehicleType());
            if(record.getVehicleType()==MaintenanceRecord.VehicleType.TRUCK){
                trucksTab.getCurrentTrucks().forEach(t->idBox.getItems().add(t.getUnit()));
            }else if(record.getVehicleType()==MaintenanceRecord.VehicleType.TRAILER){
                trailersTab.getCurrentTrailers().forEach(tr->idBox.getItems().add(tr.getNumber()));
            }
            idBox.setValue(String.valueOf(record.getVehicleId()));
            serviceDatePicker.setValue(record.getServiceDate());
            descField.setText(record.getDescription());
            costField.setText(String.valueOf(record.getCost()));
            nextDuePicker.setValue(record.getNextDue());
            receiptNumField.setText(record.getReceiptNumber());
            receiptPathField.setText(record.getReceiptPath());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(7);
        grid.setPadding(new Insets(15));
        int r=0;
        grid.add(new Label("Vehicle Type"),0,r); grid.add(typeBox,1,r++);
        grid.add(new Label("Vehicle ID"),0,r); grid.add(idBox,1,r++);
        grid.add(new Label("Service Date"),0,r); grid.add(serviceDatePicker,1,r++);
        grid.add(new Label("Description"),0,r); grid.add(descField,1,r++);
        grid.add(new Label("Cost"),0,r); grid.add(costField,1,r++);
        grid.add(new Label("Next Due"),0,r); grid.add(nextDuePicker,1,r++);
        grid.add(new Label("Receipt #"),0,r); grid.add(receiptNumField,1,r++);
        HBox recBox = new HBox(5, receiptPathField, browseBtn, previewBtn);
        grid.add(recBox,1,r++);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);

        Runnable validate = ()->{
            boolean valid = typeBox.getValue()!=null && idBox.getValue()!=null && !idBox.getValue().trim().isEmpty();
            okBtn.setDisable(!valid);
        };
        typeBox.valueProperty().addListener((o,ov,nv)->validate.run());
        idBox.valueProperty().addListener((o,ov,nv)->validate.run());
        validate.run();

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn->{
            if(btn==ButtonType.OK){
                MaintenanceRecord.VehicleType type = typeBox.getValue();
                String idVal = idBox.getValue();
                int vehicleId = idVal==null?0:Integer.parseInt(idVal);
                LocalDate serviceDate = serviceDatePicker.getValue();
                String description = descField.getText().trim();
                double cost = costField.getText().trim().isEmpty()?0.0:Double.parseDouble(costField.getText().trim());
                LocalDate nextDue = nextDuePicker.getValue();
                String recNum = receiptNumField.getText().trim();
                String recPath = receiptPathField.getText();
                if(isAdd){
                    MaintenanceRecord rec = new MaintenanceRecord(0,type,vehicleId,serviceDate,description,cost,nextDue,recNum,recPath);
                    int id = dao.add(rec);
                    rec.setId(id);
                    records.setAll(dao.getAll());
                    notifyMaintenanceDataChanged();
                    return rec;
                }else{
                    record.setVehicleType(type);
                    record.setVehicleId(vehicleId);
                    record.setServiceDate(serviceDate);
                    record.setDescription(description);
                    record.setCost(cost);
                    record.setNextDue(nextDue);
                    record.setReceiptNumber(recNum);
                    record.setReceiptPath(recPath);
                    dao.update(record);
                    records.setAll(dao.getAll());
                    notifyMaintenanceDataChanged();
                    return record;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private Callback<TableColumn<MaintenanceRecord, LocalDate>, TableCell<MaintenanceRecord, LocalDate>> getExpiryCellFactory(){
        return col -> new TableCell<>(){
            @Override
            protected void updateItem(LocalDate date, boolean empty){
                super.updateItem(date, empty);
                setText(date==null?"":date.toString());
                if(!empty && date!=null){
                    LocalDate now = LocalDate.now();
                    if(date.isBefore(now)){
                        setStyle("-fx-background-color: #ffcccc; -fx-font-weight: bold;");
                    }else if(date.isBefore(now.plusMonths(2))){
                        setStyle("-fx-background-color: #fff3cd; -fx-font-weight: bold;");
                    }else{
                        setStyle("");
                    }
                }else{
                    setStyle("");
                }
            }
        };
    }

    public void addMaintenanceDataChangeListener(MaintenanceDataChangeListener l){ listeners.add(l); }
    public void removeMaintenanceDataChangeListener(MaintenanceDataChangeListener l){ listeners.remove(l); }
    private void notifyMaintenanceDataChanged(){
        for(MaintenanceDataChangeListener l:listeners){
            l.onMaintenanceDataChanged(new ArrayList<>(records));
        }
    }

    public List<MaintenanceRecord> getCurrentRecords(){ return new ArrayList<>(records); }
}
