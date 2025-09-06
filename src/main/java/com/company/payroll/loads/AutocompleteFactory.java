package com.company.payroll.loads;

import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * ENTERPRISE AUTOCOMPLETE FACTORY
 * 
 * Professional factory class that creates properly configured UnifiedAutocompleteField
 * instances for specific data types. Replaces the need for 7 separate autocomplete classes.
 * 
 * This factory eliminates code duplication and ensures consistent configuration across
 * all autocomplete implementations in the loads system.
 */
public class AutocompleteFactory {
    private static final Logger logger = LoggerFactory.getLogger(AutocompleteFactory.class);
    
    private final LoadDAO loadDAO;
    private final EnterpriseDataCacheManager cacheManager;
    
    public AutocompleteFactory(LoadDAO loadDAO) {
        this.loadDAO = loadDAO;
        this.cacheManager = EnterpriseDataCacheManager.getInstance();
    }
    
    /**
     * Creates a customer autocomplete field with intelligent features
     * 
     * @param isPickupCustomer Whether this is for pickup (true) or drop (false) customer
     * @param onCustomerSelected Callback when customer is selected
     * @return Configured customer autocomplete field
     */
    public UnifiedAutocompleteField<String> createCustomerAutocomplete(
            boolean isPickupCustomer, 
            Consumer<String> onCustomerSelected) {
        
        String promptText = isPickupCustomer ? "Enter pickup customer..." : "Enter drop customer...";
        UnifiedAutocompleteField<String> field = new UnifiedAutocompleteField<>(promptText);
        
        // Configure for customer search
        field.setSearchProvider(query -> CompletableFuture.supplyAsync(() -> {
            List<String> customers = cacheManager.searchCustomers(query, 15);
            logger.debug("Found {} customers matching '{}'", customers.size(), query);
            return customers;
        }));
        
        field.setDisplayFunction(customer -> customer);
        field.setSearchFunction(customer -> customer);
        
        field.setOnSelectionHandler(customer -> {
            if (customer != null && !customer.trim().isEmpty()) {
                String normalizedCustomer = customer.trim().toUpperCase();
                
                // Save customer to database asynchronously
                CompletableFuture.runAsync(() -> {
                    try {
                        loadDAO.addCustomerIfNotExists(normalizedCustomer);
                        logger.debug("Customer '{}' added/verified in database", normalizedCustomer);
                    } catch (Exception e) {
                        logger.error("Error saving customer: " + normalizedCustomer, e);
                    }
                });
                
                if (onCustomerSelected != null) {
                    onCustomerSelected.accept(normalizedCustomer);
                }
            }
        });
        
        // Configure action button for adding new customers
        field.setActionButtonText("+");
        field.setActionButtonTooltip("Add new customer");
        field.setOnActionHandler(() -> showAddCustomerDialog(field, onCustomerSelected));
        
        // Performance settings for customer search
        field.setMaxSuggestions(15);
        field.setDebounceDelay(200);
        field.setMinSearchLength(1);
        field.setEnableCaching(true);
        
        return field;
    }
    
    /**
     * Creates an address autocomplete field with customer filtering
     * 
     * @param isPickupAddress Whether this is for pickup (true) or drop (false) address
     * @param customerSupplier Function to get current customer name for filtering
     * @param onAddressSelected Callback when address is selected
     * @return Configured address autocomplete field
     */
    public UnifiedAutocompleteField<CustomerAddress> createAddressAutocomplete(
            boolean isPickupAddress,
            java.util.function.Supplier<String> customerSupplier,
            Consumer<CustomerAddress> onAddressSelected) {
        
        String promptText = isPickupAddress ? "Enter pickup address..." : "Enter drop address...";
        UnifiedAutocompleteField<CustomerAddress> field = new UnifiedAutocompleteField<>(promptText);
        
        // Configure for address search
        field.setSearchProvider(query -> CompletableFuture.supplyAsync(() -> {
            String customer = customerSupplier != null ? customerSupplier.get() : null;
            List<CustomerAddress> addresses = loadDAO.searchAddresses(query, customer, 10);
            logger.debug("Found {} addresses matching '{}' for customer '{}'", 
                        addresses.size(), query, customer);
            return addresses;
        }));
        
        field.setDisplayFunction(address -> {
            if (address == null) return "";
            
            StringBuilder display = new StringBuilder();
            if (address.getLocationName() != null && !address.getLocationName().isEmpty()) {
                display.append(address.getLocationName()).append(": ");
            }
            
            display.append(address.getFullAddress());
            
            // Add default indicators
            if (isPickupAddress && address.isDefaultPickup()) {
                display.append(" ⭐ Default Pickup");
            } else if (!isPickupAddress && address.isDefaultDrop()) {
                display.append(" ⭐ Default Drop");
            }
            
            return display.toString();
        });
        
        field.setSearchFunction(address -> {
            if (address == null) return "";
            
            StringBuilder searchable = new StringBuilder();
            if (address.getLocationName() != null) {
                searchable.append(address.getLocationName()).append(" ");
            }
            searchable.append(address.getFullAddress());
            
            return searchable.toString();
        });
        
        field.setOnSelectionHandler(address -> {
            if (address != null && onAddressSelected != null) {
                onAddressSelected.accept(address);
            }
        });
        
        // Configure action button for address book
        field.setActionButtonText("📖");
        field.setActionButtonTooltip("Browse address book");
        field.setOnActionHandler(() -> showAddressBookDialog(customerSupplier, field, onAddressSelected));
        
        // Performance settings for address search
        field.setMaxSuggestions(10);
        field.setDebounceDelay(250);
        field.setMinSearchLength(2);
        field.setEnableCaching(true);
        
        return field;
    }
    
    /**
     * Creates a location autocomplete field for general location search
     * 
     * @param promptText Placeholder text for the field
     * @param onLocationSelected Callback when location is selected
     * @return Configured location autocomplete field
     */
    public UnifiedAutocompleteField<String> createLocationAutocomplete(
            String promptText,
            Consumer<String> onLocationSelected) {
        
        UnifiedAutocompleteField<String> field = new UnifiedAutocompleteField<>(promptText);
        
        // Configure for location search (city, state combinations)
        field.setSearchProvider(query -> CompletableFuture.supplyAsync(() -> {
            // Search both customer addresses and general locations
            List<CustomerAddress> addresses = loadDAO.searchAddresses(query, null, 20);
            return addresses.stream()
                .map(addr -> {
                    if (addr.getCity() != null && addr.getState() != null) {
                        return addr.getCity() + ", " + addr.getState();
                    }
                    return addr.getFullAddress();
                })
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        }));
        
        field.setDisplayFunction(location -> location);
        field.setSearchFunction(location -> location);
        
        field.setOnSelectionHandler(location -> {
            if (location != null && onLocationSelected != null) {
                onLocationSelected.accept(location);
            }
        });
        
        // Configure action button for manual entry
        field.setActionButtonText("✏");
        field.setActionButtonTooltip("Enter location manually");
        field.setOnActionHandler(() -> showManualLocationDialog(field, onLocationSelected));
        
        // Performance settings for location search
        field.setMaxSuggestions(15);
        field.setDebounceDelay(300);
        field.setMinSearchLength(2);
        field.setEnableCaching(true);
        
        return field;
    }
    
    private void showAddCustomerDialog(UnifiedAutocompleteField<String> field, Consumer<String> onCustomerSelected) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Customer");
        dialog.setHeaderText("Enter customer name:");
        dialog.setContentText("Customer:");
        
        // Pre-fill with current text if any
        String currentText = field.getSearchField().getText();
        if (currentText != null && !currentText.trim().isEmpty()) {
            dialog.getEditor().setText(currentText.trim().toUpperCase());
        }
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(customerName -> {
            if (!customerName.trim().isEmpty()) {
                String normalized = customerName.trim().toUpperCase();
                field.setText(normalized);
                
                if (onCustomerSelected != null) {
                    onCustomerSelected.accept(normalized);
                }
                
                // Log success instead of showing blocking popup
                logger.info("Customer '{}' added successfully", normalized);
            }
        });
    }
    
    private void showAddressBookDialog(
            java.util.function.Supplier<String> customerSupplier,
            UnifiedAutocompleteField<CustomerAddress> field,
            Consumer<CustomerAddress> onAddressSelected) {
        
        String customer = customerSupplier != null ? customerSupplier.get() : null;
        if (customer == null || customer.trim().isEmpty()) {
            logger.debug("Address book requested but no customer selected");
            return;
        }
        
        // This would open a comprehensive address book dialog
        // For now, just log the request without blocking popup
        logger.info("Address book requested for customer: {}", customer);
        
        // TODO: Implement full address book dialog
        // This would show all customer addresses in a dialog with add/edit/delete options
    }
    
    private void showManualLocationDialog(UnifiedAutocompleteField<String> field, Consumer<String> onLocationSelected) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Enter Location");
        dialog.setHeaderText("Enter location manually:");
        dialog.setContentText("Location:");
        
        // Pre-fill with current text if any
        String currentText = field.getSearchField().getText();
        if (currentText != null && !currentText.trim().isEmpty()) {
            dialog.getEditor().setText(currentText.trim());
        }
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(location -> {
            if (!location.trim().isEmpty()) {
                field.setText(location.trim());
                
                if (onLocationSelected != null) {
                    onLocationSelected.accept(location.trim());
                }
            }
        });
    }
    
    // REMOVED: showNotification method that was causing UI freezes
    // All notifications now use logging instead of blocking popups
    
    /**
     * Create an enhanced customer field with both customer and address selection
     * This replaces EnhancedCustomerFieldWithClear functionality
     */
    public EnhancedCustomerAddressField createEnhancedCustomerField(boolean isPickupCustomer) {
        return new EnhancedCustomerAddressField(this, isPickupCustomer);
    }
}
