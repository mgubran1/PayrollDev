package com.company.payroll.loads;

import com.company.payroll.employees.EmployeesTab;
import com.company.payroll.trailers.TrailersTab;
import com.company.payroll.trailers.Trailer;
import javafx.scene.control.Tab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * LoadsTab is a wrapper Tab for displaying the LoadsPanel inside a TabPane.
 * It can be further extended to listen for employee changes and propagate them to the LoadsPanel if needed.
 */
public class LoadsTab extends Tab implements EmployeesTab.EmployeeDataChangeListener,
                                            LoadsPanel.LoadDataChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(LoadsTab.class);
    private final LoadsPanel loadsPanel;
    private Consumer<List<Load>> syncCallback;
    
    // Add interface for load data changes
    public interface LoadDataChangeListener {
        void onLoadDataChanged();
    }
    
    private final List<LoadDataChangeListener> loadDataChangeListeners = new ArrayList<>();

    public LoadsTab(EmployeesTab employeesTab, TrailersTab trailersTab) {
        super("Loads");
        logger.info("Initializing LoadsTab");
        this.loadsPanel = new LoadsPanel();
        setContent(loadsPanel);
        
        // Register for employee updates
        employeesTab.addEmployeeDataChangeListener(this);
        
        // Register for trailer updates
        trailersTab.addDataChangeListener(() ->
                onTrailerDataChanged(trailersTab.getCurrentTrailers()));
        
        // Register this tab as a listener for load data changes
        loadsPanel.addLoadDataChangeListener(this);
        
        // Pass the sync callback to the panel
        loadsPanel.setSyncToTriumphCallback(loads -> {
            if (syncCallback != null) {
                logger.info("Executing sync callback for {} loads", loads.size());
                syncCallback.accept(loads);
            } else {
                logger.warn("Sync callback is not set");
            }
        });
        logger.info("LoadsTab initialized successfully");
    }
    
    public void addLoadDataChangeListener(LoadDataChangeListener listener) {
        loadDataChangeListeners.add(listener);
        logger.debug("Added load data change listener. Total listeners: {}", loadDataChangeListeners.size());
    }
    
    public void removeLoadDataChangeListener(LoadDataChangeListener listener) {
        loadDataChangeListeners.remove(listener);
        logger.debug("Removed load data change listener. Total listeners: {}", loadDataChangeListeners.size());
    }

    @Override
    public void onEmployeeDataChanged(java.util.List<com.company.payroll.employees.Employee> currentList) {
        logger.debug("Employee data changed, updating LoadsPanel with {} employees", currentList.size());
        loadsPanel.onEmployeeDataChanged(currentList);
    }
    
    /**
     * Called when trailer data changes so the panel can update its trailer list.
     */
    public void onTrailerDataChanged(List<Trailer> currentList) {
        logger.debug("Trailer data changed, updating LoadsPanel with {} trailers", currentList.size());
        loadsPanel.onTrailerDataChanged(currentList);
    }
    
    @Override
    public void onLoadDataChanged() {
        logger.info("Load data changed, notifying {} listeners", loadDataChangeListeners.size());
        // Propagate the load data change to all registered listeners
        for (LoadDataChangeListener listener : loadDataChangeListeners) {
            listener.onLoadDataChanged();
        }
    }
    
    public void setSyncCallback(Consumer<List<Load>> callback) {
        logger.info("Setting sync callback");
        this.syncCallback = callback;
    }
}