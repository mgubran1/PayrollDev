package com.company.payroll.services;

import com.company.payroll.drivers.Driver;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadStatus;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/** Minimal data access facade used for compilation. */
public class DataService {
    private static final DataService INSTANCE = new DataService();
    public static DataService getInstance() { return INSTANCE; }

    public List<Driver> getAllDrivers() { return Collections.emptyList(); }
    public List<Load> getActiveLoads() { return Collections.emptyList(); }
    public List<Load> getLoadsForDriver(Driver driver, LocalDate start, LocalDate end) { return Collections.emptyList(); }
    public void updateDriverStatus(Driver driver, LoadStatus status) { }
}
