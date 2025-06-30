package com.company.payroll.expenses;

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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CompanyExpensesTab extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(CompanyExpensesTab.class);

    private final ObservableList<CompanyExpense> expenses = FXCollections.observableArrayList();
    private final CompanyExpenseDAO dao = new CompanyExpenseDAO();

    public interface ExpenseDataChangeListener {
        void onExpenseDataChanged(List<CompanyExpense> currentList);
    }
    private final List<ExpenseDataChangeListener> listeners = new ArrayList<>();

    public CompanyExpensesTab() {
        logger.info("Initializing CompanyExpensesTab");
        expenses.setAll(dao.getAll());

        TextField searchField = new TextField();
        searchField.setPromptText("Search description...");

        HBox filterBox = new HBox(10, searchField);
        filterBox.setPadding(new Insets(10,10,0,10));
        filterBox.setAlignment(Pos.CENTER_LEFT);

        TableView<CompanyExpense> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<CompanyExpense, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("expenseDate"));

        TableColumn<CompanyExpense,String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));

        TableColumn<CompanyExpense,String> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().getAmount())));

        TableColumn<CompanyExpense,String> recCol = new TableColumn<>("Receipt #");
        recCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReceiptNumber()));

        table.getColumns().addAll(dateCol, descCol, amtCol, recCol);

        FilteredList<CompanyExpense> filtered = new FilteredList<>(expenses,p->true);
        searchField.textProperty().addListener((obs,o,n)->filtered.setPredicate(r->filterExpense(r,n)));
        SortedList<CompanyExpense> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        table.setRowFactory(tv->{
            TableRow<CompanyExpense> row = new TableRow<>();
            row.setOnMouseClicked(e->{
                if(e.getClickCount()==2 && !row.isEmpty()) showExpenseDialog(row.getItem(),false);
            });
            return row;
        });

        Button addBtn = new Button("Add");
        Button editBtn = new Button("Edit");
        Button delBtn = new Button("Delete");
        Button refreshBtn = new Button("Refresh");

        addBtn.setOnAction(e->showExpenseDialog(null,true));
        editBtn.setOnAction(e->{
            CompanyExpense r = table.getSelectionModel().getSelectedItem();
            if(r!=null) showExpenseDialog(r,false);
        });
        delBtn.setOnAction(e->{
            CompanyExpense r = table.getSelectionModel().getSelectedItem();
            if(r!=null){
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete expense?", ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Confirm Delete");
                confirm.showAndWait().ifPresent(resp->{
                    if(resp==ButtonType.YES){
                        dao.delete(r.getId());
                        expenses.setAll(dao.getAll());
                        notifyExpenseDataChanged();
                    }
                });
            }
        });
        refreshBtn.setOnAction(e->{
            expenses.setAll(dao.getAll());
            notifyExpenseDataChanged();
        });

        HBox btnBox = new HBox(10, addBtn, editBtn, delBtn, refreshBtn);
        btnBox.setPadding(new Insets(12));
        btnBox.setAlignment(Pos.CENTER_LEFT);

        setCenter(new VBox(filterBox, table));
        setBottom(btnBox);
        setPadding(new Insets(10));
    }

    private boolean filterExpense(CompanyExpense r, String text){
        String lower = text==null?"":text.toLowerCase(Locale.ROOT);
        if(lower.isEmpty()) return true;
        return r.getDescription()!=null && r.getDescription().toLowerCase(Locale.ROOT).contains(lower);
    }

    private void showExpenseDialog(CompanyExpense record, boolean isAdd){
        Dialog<CompanyExpense> dialog = new Dialog<>();
        dialog.setTitle(isAdd?"Add Expense":"Edit Expense");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        DatePicker datePicker = new DatePicker();
        TextField descField = new TextField();
        TextField amountField = new TextField();
        TextField receiptNumField = new TextField();
        TextField receiptPathField = new TextField();
        receiptPathField.setEditable(false);
        Button browseBtn = new Button("Browse...");

        browseBtn.setOnAction(e->{
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Receipt File");
            File file = chooser.showOpenDialog(getScene().getWindow());
            if(file!=null){
                try{
                    File destDir = new File("receipts");
                    destDir.mkdirs();
                    File dest = new File(destDir, file.getName());
                    Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    receiptPathField.setText(dest.getAbsolutePath());
                }catch(Exception ex){
                    logger.error("Failed copying receipt", ex);
                }
            }
        });

        Button previewBtn = new Button("Preview");
        previewBtn.setOnAction(e->{
            String path = receiptPathField.getText();
            if(path!=null && !path.isEmpty()){
                try{
                    java.awt.Desktop.getDesktop().open(new File(path));
                }catch(Exception ex){
                    logger.error("Failed to open receipt", ex);
                }
            }
        });

        if(record!=null){
            datePicker.setValue(record.getExpenseDate());
            descField.setText(record.getDescription());
            amountField.setText(String.valueOf(record.getAmount()));
            receiptNumField.setText(record.getReceiptNumber());
            receiptPathField.setText(record.getReceiptPath());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(7);
        grid.setPadding(new Insets(15));
        int r=0;
        grid.add(new Label("Date"),0,r); grid.add(datePicker,1,r++);
        grid.add(new Label("Description"),0,r); grid.add(descField,1,r++);
        grid.add(new Label("Amount"),0,r); grid.add(amountField,1,r++);
        grid.add(new Label("Receipt #"),0,r); grid.add(receiptNumField,1,r++);
        HBox receiptBox = new HBox(5, receiptPathField, browseBtn, previewBtn);
        grid.add(receiptBox,1,r++);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);

        Runnable validate = ()->{
            boolean valid = !descField.getText().trim().isEmpty();
            okBtn.setDisable(!valid);
        };
        descField.textProperty().addListener((o,ov,nv)->validate.run());
        validate.run();

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn->{
            if(btn==ButtonType.OK){
                LocalDate date = datePicker.getValue();
                String desc = descField.getText().trim();
                double amt = amountField.getText().trim().isEmpty()?0.0:Double.parseDouble(amountField.getText().trim());
                String recNum = receiptNumField.getText().trim();
                String recPath = receiptPathField.getText();
                if(isAdd){
                    CompanyExpense exp = new CompanyExpense(0,date,desc,amt,recNum,recPath);
                    int id = dao.add(exp);
                    exp.setId(id);
                    expenses.setAll(dao.getAll());
                    notifyExpenseDataChanged();
                    return exp;
                }else{
                    record.setExpenseDate(date);
                    record.setDescription(desc);
                    record.setAmount(amt);
                    record.setReceiptNumber(recNum);
                    record.setReceiptPath(recPath);
                    dao.update(record);
                    expenses.setAll(dao.getAll());
                    notifyExpenseDataChanged();
                    return record;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    public void addExpenseDataChangeListener(ExpenseDataChangeListener l){ listeners.add(l); }
    public void removeExpenseDataChangeListener(ExpenseDataChangeListener l){ listeners.remove(l); }
    private void notifyExpenseDataChanged(){
        for(ExpenseDataChangeListener l:listeners){
            l.onExpenseDataChanged(new ArrayList<>(expenses));
        }
    }

    public List<CompanyExpense> getCurrentExpenses(){ return new ArrayList<>(expenses); }
}
