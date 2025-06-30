package com.company.payroll.trucks;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrucksTab extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(TrucksTab.class);

    private final ObservableList<Truck> trucks = FXCollections.observableArrayList();
    private final TruckDAO dao = new TruckDAO();

    public interface TruckDataChangeListener {
        void onTruckDataChanged(List<Truck> currentList);
    }
    private final List<TruckDataChangeListener> listeners = new ArrayList<>();

    public TrucksTab() {
        logger.info("Initializing TrucksTab");
        trucks.setAll(dao.getAll());

        TextField searchField = new TextField();
        searchField.setPromptText("Search unit, vin, make...");

        HBox filterBox = new HBox(10, searchField);
        filterBox.setPadding(new Insets(10,10,0,10));
        filterBox.setAlignment(Pos.CENTER_LEFT);

        TableView<Truck> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Truck,String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getUnit()));

        TableColumn<Truck,String> makeCol = new TableColumn<>("Make");
        makeCol.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getMake()));

        TableColumn<Truck,String> modelCol = new TableColumn<>("Model");
        modelCol.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getModel()));

        TableColumn<Truck,String> yearCol = new TableColumn<>("Year");
        yearCol.setCellValueFactory(t -> new SimpleStringProperty(String.valueOf(t.getValue().getYear())));

        TableColumn<Truck,LocalDate> licExpCol = new TableColumn<>("License Expiry");
        licExpCol.setCellValueFactory(new PropertyValueFactory<>("licenseExpiry"));
        licExpCol.setCellFactory(getExpiryCellFactory());

        TableColumn<Truck,LocalDate> inspExpCol = new TableColumn<>("Inspection Expiry");
        inspExpCol.setCellValueFactory(new PropertyValueFactory<>("inspectionExpiry"));
        inspExpCol.setCellFactory(getExpiryCellFactory());

        TableColumn<Truck,LocalDate> iftaExpCol = new TableColumn<>("IFTA Expiry");
        iftaExpCol.setCellValueFactory(new PropertyValueFactory<>("iftaExpiry"));
        iftaExpCol.setCellFactory(getExpiryCellFactory());

        TableColumn<Truck,String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getStatus()!=null?t.getValue().getStatus().name():""));

        table.getColumns().addAll(unitCol, makeCol, modelCol, yearCol, licExpCol, inspExpCol, iftaExpCol, statusCol);

        FilteredList<Truck> filtered = new FilteredList<>(trucks, p->true);
        searchField.textProperty().addListener((obs,o,n)->{
            filtered.setPredicate(tr -> filterTruck(tr,n));
        });
        SortedList<Truck> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        table.setRowFactory(tv -> {
            TableRow<Truck> row = new TableRow<>();
            row.setOnMouseClicked(e->{
                if(e.getClickCount()==2 && !row.isEmpty()) {
                    showTruckDialog(row.getItem(), false);
                }
            });
            return row;
        });

        Button addBtn = new Button("Add");
        Button editBtn = new Button("Edit");
        Button delBtn = new Button("Delete");
        Button refreshBtn = new Button("Refresh");

        addBtn.setOnAction(e-> showTruckDialog(null,true));
        editBtn.setOnAction(e->{
            Truck t = table.getSelectionModel().getSelectedItem();
            if(t!=null) showTruckDialog(t,false);
        });
        delBtn.setOnAction(e->{
            Truck t = table.getSelectionModel().getSelectedItem();
            if(t!=null){
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete truck unit \""+t.getUnit()+"\"?",
                        ButtonType.YES,ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp->{
                    if(resp==ButtonType.YES){
                        dao.delete(t.getId());
                        trucks.setAll(dao.getAll());
                        notifyTruckDataChanged();
                    }
                });
            }
        });
        refreshBtn.setOnAction(e->{
            trucks.setAll(dao.getAll());
            notifyTruckDataChanged();
        });

        HBox btnBox = new HBox(10, addBtn, editBtn, delBtn, refreshBtn);
        btnBox.setPadding(new Insets(12));
        btnBox.setAlignment(Pos.CENTER_LEFT);

        setCenter(new VBox(filterBox, table));
        setBottom(btnBox);
        setPadding(new Insets(10));
    }

    private boolean filterTruck(Truck t,String text){
        String lower = text==null?"":text.toLowerCase(Locale.ROOT);
        if(lower.isEmpty()) return true;
        return (t.getUnit()!=null && t.getUnit().toLowerCase(Locale.ROOT).contains(lower)) ||
                (t.getVin()!=null && t.getVin().toLowerCase(Locale.ROOT).contains(lower)) ||
                (t.getMake()!=null && t.getMake().toLowerCase(Locale.ROOT).contains(lower)) ||
                (t.getModel()!=null && t.getModel().toLowerCase(Locale.ROOT).contains(lower));
    }

    private void showTruckDialog(Truck truck, boolean isAdd){
        Dialog<Truck> dialog = new Dialog<>();
        dialog.setTitle(isAdd?"Add Truck":"Edit Truck");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField unitField = new TextField();
        TextField makeField = new TextField();
        TextField modelField = new TextField();
        TextField yearField = new TextField();
        TextField vinField = new TextField();
        TextField plateField = new TextField();
        DatePicker licExpPicker = new DatePicker();
        DatePicker inspExpPicker = new DatePicker();
        DatePicker iftaExpPicker = new DatePicker();
        ComboBox<Truck.Status> statusBox = new ComboBox<>(FXCollections.observableArrayList(Truck.Status.values()));

        if(truck!=null){
            unitField.setText(truck.getUnit());
            makeField.setText(truck.getMake());
            modelField.setText(truck.getModel());
            yearField.setText(truck.getYear()>0?String.valueOf(truck.getYear()):"");
            vinField.setText(truck.getVin());
            plateField.setText(truck.getLicensePlate());
            licExpPicker.setValue(truck.getLicenseExpiry());
            inspExpPicker.setValue(truck.getInspectionExpiry());
            iftaExpPicker.setValue(truck.getIftaExpiry());
            statusBox.setValue(truck.getStatus());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(7);
        grid.setPadding(new Insets(15));
        int r=0;
        grid.add(new Label("Unit*"),0,r); grid.add(unitField,1,r++);
        grid.add(new Label("Make"),0,r); grid.add(makeField,1,r++);
        grid.add(new Label("Model"),0,r); grid.add(modelField,1,r++);
        grid.add(new Label("Year"),0,r); grid.add(yearField,1,r++);
        grid.add(new Label("VIN"),0,r); grid.add(vinField,1,r++);
        grid.add(new Label("Plate"),0,r); grid.add(plateField,1,r++);
        grid.add(new Label("License Expiry"),0,r); grid.add(licExpPicker,1,r++);
        grid.add(new Label("Inspection Expiry"),0,r); grid.add(inspExpPicker,1,r++);
        grid.add(new Label("IFTA Expiry"),0,r); grid.add(iftaExpPicker,1,r++);
        grid.add(new Label("Status"),0,r); grid.add(statusBox,1,r++);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);

        Runnable validate = ()->{
            boolean valid = !unitField.getText().trim().isEmpty();
            okBtn.setDisable(!valid);
        };
        unitField.textProperty().addListener((o,ov,nv)->validate.run());
        validate.run();

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn->{
            if(btn==ButtonType.OK){
                String unit = unitField.getText().trim();
                String make = makeField.getText().trim();
                String model = modelField.getText().trim();
                int year = yearField.getText().trim().isEmpty()?0:Integer.parseInt(yearField.getText().trim());
                String vin = vinField.getText().trim();
                String plate = plateField.getText().trim();
                LocalDate licExp = licExpPicker.getValue();
                LocalDate inspExp = inspExpPicker.getValue();
                LocalDate iftaExp = iftaExpPicker.getValue();
                Truck.Status status = statusBox.getValue();
                if(isAdd){
                    Truck t = new Truck(0, unit, make, model, year, vin, plate, licExp, inspExp, iftaExp, status);
                    int id = dao.add(t);
                    t.setId(id);
                    trucks.setAll(dao.getAll());
                    notifyTruckDataChanged();
                    return t;
                }else{
                    truck.setUnit(unit);
                    truck.setMake(make);
                    truck.setModel(model);
                    truck.setYear(year);
                    truck.setVin(vin);
                    truck.setLicensePlate(plate);
                    truck.setLicenseExpiry(licExp);
                    truck.setInspectionExpiry(inspExp);
                    truck.setIftaExpiry(iftaExp);
                    truck.setStatus(status);
                    dao.update(truck);
                    trucks.setAll(dao.getAll());
                    notifyTruckDataChanged();
                    return truck;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private Callback<TableColumn<Truck, LocalDate>, TableCell<Truck, LocalDate>> getExpiryCellFactory(){
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

    public void addTruckDataChangeListener(TruckDataChangeListener l){ listeners.add(l); }
    public void removeTruckDataChangeListener(TruckDataChangeListener l){ listeners.remove(l); }
    private void notifyTruckDataChanged(){
        for(TruckDataChangeListener l:listeners){
            l.onTruckDataChanged(new ArrayList<>(trucks));
        }
    }

    public List<Truck> getCurrentTrucks(){ return new ArrayList<>(trucks); }
}
