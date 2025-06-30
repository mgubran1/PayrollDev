package com.company.payroll.driver;

import com.company.payroll.payroll.PayrollCalculator;
import com.company.payroll.payroll.PayrollTab;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Locale;

public class DriverIncomeTab extends BorderPane {
    private final ObservableList<PayrollCalculator.PayrollRow> rows;

    public DriverIncomeTab(PayrollTab payrollTab){
        rows = FXCollections.observableArrayList(payrollTab.getSummaryRows());
        TextField searchField = new TextField();
        searchField.setPromptText("Search driver...");
        HBox filterBox = new HBox(10,searchField);
        filterBox.setPadding(new Insets(10,10,0,10));
        filterBox.setAlignment(Pos.CENTER_LEFT);

        TableView<PayrollCalculator.PayrollRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<PayrollCalculator.PayrollRow,String> nameCol = new TableColumn<>("Driver");
        nameCol.setCellValueFactory(r-> new javafx.beans.property.SimpleStringProperty(r.getValue().driverName));
        TableColumn<PayrollCalculator.PayrollRow,String> netCol = new TableColumn<>("Net Pay");
        netCol.setCellValueFactory(r-> new javafx.beans.property.SimpleStringProperty(String.format("%.2f",r.getValue().netPay)));
        table.getColumns().addAll(nameCol, netCol);

        FilteredList<PayrollCalculator.PayrollRow> filtered = new FilteredList<>(rows,p->true);
        searchField.textProperty().addListener((o,ov,nv)->filtered.setPredicate(r->filterRow(r,nv)));
        SortedList<PayrollCalculator.PayrollRow> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        table.setRowFactory(tv->{
            TableRow<PayrollCalculator.PayrollRow> row = new TableRow<>();
            row.setOnMouseClicked(e->{ });
            return row;
        });

        setCenter(new VBox(filterBox, table));
    }

    private boolean filterRow(PayrollCalculator.PayrollRow r,String text){
        String lower = text==null?"":text.toLowerCase(Locale.ROOT);
        if(lower.isEmpty()) return true;
        return r.driverName!=null && r.driverName.toLowerCase(Locale.ROOT).contains(lower);
    }
}
