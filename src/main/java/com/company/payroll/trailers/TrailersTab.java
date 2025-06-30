package com.company.payroll.trailers;

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

public class TrailersTab extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(TrailersTab.class);

    private final ObservableList<Trailer> trailers = FXCollections.observableArrayList();
    private final TrailerDAO dao = new TrailerDAO();

    public interface TrailerDataChangeListener {
        void onTrailerDataChanged(List<Trailer> currentList);
    }
    private final List<TrailerDataChangeListener> listeners = new ArrayList<>();

    public TrailersTab() {
        logger.info("Initializing TrailersTab");
        trailers.setAll(dao.getAll());

        TextField searchField = new TextField();
        searchField.setPromptText("Search number, vin, type...");

        HBox filterBox = new HBox(10, searchField);
        filterBox.setPadding(new Insets(10,10,0,10));
        filterBox.setAlignment(Pos.CENTER_LEFT);

        TableView<Trailer> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Trailer,String> numCol = new TableColumn<>("Number");
        numCol.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getNumber()));

        TableColumn<Trailer,String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getType()));

        TableColumn<Trailer,String> yearCol = new TableColumn<>("Year");
        yearCol.setCellValueFactory(t -> new SimpleStringProperty(String.valueOf(t.getValue().getYear())));

        TableColumn<Trailer,LocalDate> licExpCol = new TableColumn<>("License Expiry");
        licExpCol.setCellValueFactory(new PropertyValueFactory<>("licenseExpiry"));
        licExpCol.setCellFactory(getExpiryCellFactory());

        TableColumn<Trailer,LocalDate> inspExpCol = new TableColumn<>("Inspection Expiry");
        inspExpCol.setCellValueFactory(new PropertyValueFactory<>("inspectionExpiry"));
        inspExpCol.setCellFactory(getExpiryCellFactory());

        TableColumn<Trailer,String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getStatus()!=null?t.getValue().getStatus().name():""));

        table.getColumns().addAll(numCol, typeCol, yearCol, licExpCol, inspExpCol, statusCol);

        FilteredList<Trailer> filtered = new FilteredList<>(trailers, p->true);
        searchField.textProperty().addListener((obs,o,n)-> filtered.setPredicate(tr -> filterTrailer(tr,n)));
        SortedList<Trailer> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        table.setRowFactory(tv->{
            TableRow<Trailer> row = new TableRow<>();
            row.setOnMouseClicked(e->{
                if(e.getClickCount()==2 && !row.isEmpty()) showTrailerDialog(row.getItem(),false);
            });
            return row;
        });

        Button addBtn = new Button("Add");
        Button editBtn = new Button("Edit");
        Button delBtn = new Button("Delete");
        Button refreshBtn = new Button("Refresh");

        addBtn.setOnAction(e-> showTrailerDialog(null,true));
        editBtn.setOnAction(e->{
            Trailer t = table.getSelectionModel().getSelectedItem();
            if(t!=null) showTrailerDialog(t,false);
        });
        delBtn.setOnAction(e->{
            Trailer t = table.getSelectionModel().getSelectedItem();
            if(t!=null){
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete trailer \""+t.getNumber()+"\"?",
                        ButtonType.YES,ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp->{
                    if(resp==ButtonType.YES){
                        dao.delete(t.getId());
                        trailers.setAll(dao.getAll());
                        notifyTrailerDataChanged();
                    }
                });
            }
        });
        refreshBtn.setOnAction(e->{
            trailers.setAll(dao.getAll());
            notifyTrailerDataChanged();
        });

        HBox btnBox = new HBox(10, addBtn, editBtn, delBtn, refreshBtn);
        btnBox.setPadding(new Insets(12));
        btnBox.setAlignment(Pos.CENTER_LEFT);

        setCenter(new VBox(filterBox, table));
        setBottom(btnBox);
        setPadding(new Insets(10));
    }

    private boolean filterTrailer(Trailer t,String text){
        String lower = text==null?"":text.toLowerCase(Locale.ROOT);
        if(lower.isEmpty()) return true;
        return (t.getNumber()!=null && t.getNumber().toLowerCase(Locale.ROOT).contains(lower)) ||
                (t.getVin()!=null && t.getVin().toLowerCase(Locale.ROOT).contains(lower)) ||
                (t.getType()!=null && t.getType().toLowerCase(Locale.ROOT).contains(lower));
    }

    private void showTrailerDialog(Trailer trailer, boolean isAdd){
        Dialog<Trailer> dialog = new Dialog<>();
        dialog.setTitle(isAdd?"Add Trailer":"Edit Trailer");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField numField = new TextField();
        TextField typeField = new TextField();
        TextField yearField = new TextField();
        TextField vinField = new TextField();
        TextField plateField = new TextField();
        DatePicker licExpPicker = new DatePicker();
        DatePicker inspExpPicker = new DatePicker();
        ComboBox<Trailer.Status> statusBox = new ComboBox<>(FXCollections.observableArrayList(Trailer.Status.values()));

        if(trailer!=null){
            numField.setText(trailer.getNumber());
            typeField.setText(trailer.getType());
            yearField.setText(trailer.getYear()>0?String.valueOf(trailer.getYear()):"");
            vinField.setText(trailer.getVin());
            plateField.setText(trailer.getLicensePlate());
            licExpPicker.setValue(trailer.getLicenseExpiry());
            inspExpPicker.setValue(trailer.getInspectionExpiry());
            statusBox.setValue(trailer.getStatus());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(7);
        grid.setPadding(new Insets(15));
        int r=0;
        grid.add(new Label("Number*"),0,r); grid.add(numField,1,r++);
        grid.add(new Label("Type"),0,r); grid.add(typeField,1,r++);
        grid.add(new Label("Year"),0,r); grid.add(yearField,1,r++);
        grid.add(new Label("VIN"),0,r); grid.add(vinField,1,r++);
        grid.add(new Label("Plate"),0,r); grid.add(plateField,1,r++);
        grid.add(new Label("License Expiry"),0,r); grid.add(licExpPicker,1,r++);
        grid.add(new Label("Inspection Expiry"),0,r); grid.add(inspExpPicker,1,r++);
        grid.add(new Label("Status"),0,r); grid.add(statusBox,1,r++);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);

        Runnable validate = ()->{
            boolean valid = !numField.getText().trim().isEmpty();
            okBtn.setDisable(!valid);
        };
        numField.textProperty().addListener((o,ov,nv)->validate.run());
        validate.run();

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn->{
            if(btn==ButtonType.OK){
                String num = numField.getText().trim();
                String type = typeField.getText().trim();
                int year = yearField.getText().trim().isEmpty()?0:Integer.parseInt(yearField.getText().trim());
                String vin = vinField.getText().trim();
                String plate = plateField.getText().trim();
                LocalDate licExp = licExpPicker.getValue();
                LocalDate inspExp = inspExpPicker.getValue();
                Trailer.Status status = statusBox.getValue();
                if(isAdd){
                    Trailer t = new Trailer(0,num,type,year,vin,plate,licExp,inspExp,status);
                    int id = dao.add(t);
                    t.setId(id);
                    trailers.setAll(dao.getAll());
                    notifyTrailerDataChanged();
                    return t;
                }else{
                    trailer.setNumber(num);
                    trailer.setType(type);
                    trailer.setYear(year);
                    trailer.setVin(vin);
                    trailer.setLicensePlate(plate);
                    trailer.setLicenseExpiry(licExp);
                    trailer.setInspectionExpiry(inspExp);
                    trailer.setStatus(status);
                    dao.update(trailer);
                    trailers.setAll(dao.getAll());
                    notifyTrailerDataChanged();
                    return trailer;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private Callback<TableColumn<Trailer, LocalDate>, TableCell<Trailer, LocalDate>> getExpiryCellFactory(){
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

    public void addTrailerDataChangeListener(TrailerDataChangeListener l){ listeners.add(l); }
    public void removeTrailerDataChangeListener(TrailerDataChangeListener l){ listeners.remove(l); }
    private void notifyTrailerDataChanged(){
        for(TrailerDataChangeListener l:listeners){
            l.onTrailerDataChanged(new ArrayList<>(trailers));
        }
    }

    public List<Trailer> getCurrentTrailers(){ return new ArrayList<>(trailers); }
}
