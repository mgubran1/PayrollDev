package com.company.payroll.trailers;

import javafx.beans.property.*;
import java.time.LocalDate;

/**
 * Simple model representing an assignment of a trailer to a truck or driver.
 * Formerly defined inside {@link TrailersTab}.
 */
public class TrailerAssignment {
    private final StringProperty assignmentId = new SimpleStringProperty();
    private final StringProperty trailerNumber = new SimpleStringProperty();
    private final StringProperty driverName = new SimpleStringProperty();
    private final StringProperty truckUnit = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> startDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> endDate = new SimpleObjectProperty<>();

    public String getAssignmentId() { return assignmentId.get(); }
    public void setAssignmentId(String v) { assignmentId.set(v); }
    public StringProperty assignmentIdProperty() { return assignmentId; }

    public String getTrailerNumber() { return trailerNumber.get(); }
    public void setTrailerNumber(String v) { trailerNumber.set(v); }
    public StringProperty trailerNumberProperty() { return trailerNumber; }

    public String getDriverName() { return driverName.get(); }
    public void setDriverName(String v) { driverName.set(v); }
    public StringProperty driverNameProperty() { return driverName; }

    public String getTruckUnit() { return truckUnit.get(); }
    public void setTruckUnit(String v) { truckUnit.set(v); }
    public StringProperty truckUnitProperty() { return truckUnit; }

    public LocalDate getStartDate() { return startDate.get(); }
    public void setStartDate(LocalDate d) { startDate.set(d); }
    public ObjectProperty<LocalDate> startDateProperty() { return startDate; }

    public LocalDate getEndDate() { return endDate.get(); }
    public void setEndDate(LocalDate d) { endDate.set(d); }
    public ObjectProperty<LocalDate> endDateProperty() { return endDate; }
}
