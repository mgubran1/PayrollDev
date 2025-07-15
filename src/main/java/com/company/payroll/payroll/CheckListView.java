package com.company.payroll.payroll;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;

/**
 * ListView with a simple check model for selecting items.
 */
public class CheckListView<T> extends ListView<T> {
    private final CheckListViewModel checkModel;

    public CheckListView() {
        this.checkModel = new CheckListViewModel();
        setCellFactory(lv -> new CheckBoxListCell<>(
                index -> checkModel.isSelected(index),
                index -> checkModel.select(index),
                index -> checkModel.clearSelection(index)
        ));
    }

    public MultipleSelectionModel<T> getCheckModel() {
        return checkModel;
    }

    private class CheckListViewModel extends MultipleSelectionModel<T> {
        private final ObservableList<Integer> selectedIndices = FXCollections.observableArrayList();
        private final ObservableList<T> selectedItems = FXCollections.observableArrayList();

        @Override
        public ObservableList<Integer> getSelectedIndices() {
            return FXCollections.unmodifiableObservableList(selectedIndices);
        }

        @Override
        public ObservableList<T> getSelectedItems() {
            return FXCollections.unmodifiableObservableList(selectedItems);
        }

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
        public void selectFirst() {
            if (!getItems().isEmpty()) select(0);
        }

        @Override
        public void selectLast() {
            if (!getItems().isEmpty()) select(getItems().size() - 1);
        }

        @Override
        public void clearAndSelect(int index) {
            clearSelection();
            select(index);
        }

        @Override
        public void select(int index) {
            if (index >= 0 && index < getItems().size() && !selectedIndices.contains(index)) {
                selectedIndices.add(index);
                selectedItems.add(CheckListView.this.getItems().get(index));
            }
        }

        @Override
        public void select(T obj) {
            int index = getItems().indexOf(obj);
            if (index >= 0) select(index);
        }

        @Override
        public void clearSelection(int index) {
            Integer idx = Integer.valueOf(index);
            if (selectedIndices.contains(idx)) {
                selectedIndices.remove(idx);
                selectedItems.remove(getItems().get(index));
            }
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
        public boolean isEmpty() {
            return selectedIndices.isEmpty();
        }

        @Override
        public void selectPrevious() {
            if (!selectedIndices.isEmpty()) {
                int min = selectedIndices.stream().mapToInt(Integer::intValue).min().orElse(0);
                if (min > 0) select(min - 1);
            }
        }

        @Override
        public void selectNext() {
            if (!selectedIndices.isEmpty()) {
                int max = selectedIndices.stream().mapToInt(Integer::intValue).max().orElse(0);
                if (max < getItems().size() - 1) select(max + 1);
            }
        }
    }
}
