package com.company.payroll.payroll;

import com.company.payroll.loads.Load;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;

/**
 * ListCell implementation that displays a selectable checkbox next to the item.
 */
public class CheckBoxListCell<T> extends ListCell<T> {
    private final CheckBox checkBox = new CheckBox();
    private final java.util.function.Function<Integer, Boolean> isSelected;
    private final java.util.function.Consumer<Integer> onSelect;
    private final java.util.function.Consumer<Integer> onDeselect;

    public CheckBoxListCell(java.util.function.Function<Integer, Boolean> isSelected,
                            java.util.function.Consumer<Integer> onSelect,
                            java.util.function.Consumer<Integer> onDeselect) {
        this.isSelected = isSelected;
        this.onSelect = onSelect;
        this.onDeselect = onDeselect;
        setContentDisplay(ContentDisplay.LEFT);
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            checkBox.setSelected(isSelected.apply(getIndex()));
            checkBox.setOnAction(e -> {
                if (checkBox.isSelected()) {
                    onSelect.accept(getIndex());
                } else {
                    onDeselect.accept(getIndex());
                }
            });

            if (item instanceof Load) {
                Load load = (Load) item;
                setText(String.format("Load #%s - %s to %s ($%.2f)",
                        load.getLoadNumber(),
                        load.getPickupCity() + ", " + load.getPickupState(),
                        load.getDeliveryCity() + ", " + load.getDeliveryState(),
                        load.getDriverRate()));
            } else {
                setText(item.toString());
            }

            setGraphic(checkBox);
        }
    }
}
