package com.company.payroll.payroll;

import java.awt.Desktop;
import javafx.collections.FXCollections;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import com.company.payroll.employees.Employee;
import com.company.payroll.employees.EmployeeDAO;
import com.company.payroll.fuel.FuelTransaction;
import com.company.payroll.fuel.FuelTransactionDAO;
import com.company.payroll.loads.Load;
import com.company.payroll.loads.LoadDAO;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.FontPosture;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/**
 * QuickActions - Centralized handler for all payroll quick action operations
 * Manages Print Preview, Save PDF, Export Excel, Copy Table, and History functionalities
 */
public class QuickActions {
    private static final Logger logger = LoggerFactory.getLogger(QuickActions.class);
    
    // Configuration constants
    private static final String CONFIG_FILE = "payroll_quick_actions.properties";
    private static final String PDF_PATH_KEY = "pdf.save.path";
    private static final String AUTO_ORGANIZE_KEY = "pdf.auto.organize";
    private static final String PDF_FORMAT_KEY = "pdf.filename.format";
    
    // Default settings
    private static final String DEFAULT_PDF_FORMAT = "PayStub_{DRIVER}_{WEEK}.pdf";
    
    // Dependencies
    private final PayrollTab payrollTab;
    private final LoadDAO loadDAO;
    private final FuelTransactionDAO fuelDAO;
    private final ExecutorService executorService;
    
    // Settings
    private String pdfSavePath;
    private boolean autoOrganizeByDriver;
    private String pdfFilenameFormat;
    
    // Cache for frequently accessed data
    private final Map<String, Employee> employeeCache = new HashMap<>();
    
    public QuickActions(PayrollTab payrollTab, LoadDAO loadDAO, FuelTransactionDAO fuelDAO, 
                       ExecutorService executorService) {
        this.payrollTab = payrollTab;
        this.loadDAO = loadDAO;
        this.fuelDAO = fuelDAO;
        this.executorService = executorService;
        
        loadSettings();
        logger.info("QuickActions initialized with PDF path: {}", pdfSavePath);
    }
    
    /**
     * Load settings from configuration file
     */
    private void loadSettings() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                pdfSavePath = props.getProperty(PDF_PATH_KEY, System.getProperty("user.home"));
                autoOrganizeByDriver = Boolean.parseBoolean(props.getProperty(AUTO_ORGANIZE_KEY, "true"));
                pdfFilenameFormat = props.getProperty(PDF_FORMAT_KEY, DEFAULT_PDF_FORMAT);
            } catch (IOException e) {
                logger.error("Failed to load settings", e);
                setDefaultSettings();
            }
        } else {
            setDefaultSettings();
            saveSettings(); // Create default config file
        }
    }
    
    private void setDefaultSettings() {
        pdfSavePath = System.getProperty("user.home");
        autoOrganizeByDriver = true;
        pdfFilenameFormat = DEFAULT_PDF_FORMAT;
    }
    
    /**
     * Save settings to configuration file
     */
    private void saveSettings() {
        Properties props = new Properties();
        props.setProperty(PDF_PATH_KEY, pdfSavePath);
        props.setProperty(AUTO_ORGANIZE_KEY, String.valueOf(autoOrganizeByDriver));
        props.setProperty(PDF_FORMAT_KEY, pdfFilenameFormat);
        
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "QuickActions Configuration");
        } catch (IOException e) {
            logger.error("Failed to save settings", e);
        }
    }
    
    /**
     * Show settings dialog for configuring paths and options
     */
    public void showSettingsDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Quick Actions Settings");
        dialog.setHeaderText("Configure PDF Save Settings");
        dialog.initModality(Modality.APPLICATION_MODAL);
        
        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        // PDF Save Path
        Label pathLabel = new Label("PDF Save Path:");
        TextField pathField = new TextField(pdfSavePath);
        pathField.setPrefWidth(300);
        pathField.setEditable(false);
        
        Button browseBtn = ModernButtonStyles.createSecondaryButton("Browse...");
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select PDF Save Directory");
            chooser.setInitialDirectory(new File(pdfSavePath));
            File selectedDir = chooser.showDialog(dialog.getOwner());
            if (selectedDir != null) {
                pathField.setText(selectedDir.getAbsolutePath());
            }
        });
        
        // Auto-organize checkbox
        CheckBox autoOrganizeCheck = new CheckBox("Auto-organize PDFs by driver name");
        autoOrganizeCheck.setSelected(autoOrganizeByDriver);
        autoOrganizeCheck.setTooltip(new Tooltip("Creates a folder for each driver automatically"));
        
        // Filename format
        Label formatLabel = new Label("Filename Format:");
        TextField formatField = new TextField(pdfFilenameFormat);
        formatField.setPromptText("Use {DRIVER}, {WEEK}, {DATE} as placeholders");
        formatField.setPrefWidth(300);
        
        Label formatHelp = new Label("Available placeholders: {DRIVER}, {WEEK}, {YEAR}, {DATE}");
        formatHelp.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
        
        // Layout
        grid.add(pathLabel, 0, 0);
        grid.add(pathField, 1, 0);
        grid.add(browseBtn, 2, 0);
        
        grid.add(autoOrganizeCheck, 1, 1, 2, 1);
        
        grid.add(formatLabel, 0, 2);
        grid.add(formatField, 1, 2, 2, 1);
        grid.add(formatHelp, 1, 3, 2, 1);
        
        // Preview
        Separator sep = new Separator();
        grid.add(sep, 0, 4, 3, 1);
        GridPane.setMargin(sep, new Insets(10, 0, 10, 0));
        
        Label previewLabel = new Label("Preview:");
        Label previewPath = new Label();
        previewPath.setStyle("-fx-font-family: monospace; -fx-background-color: #f0f0f0; -fx-padding: 5;");
        
        // Update preview
        Runnable updatePreview = () -> {
            String preview = generatePreviewPath(
                pathField.getText(),
                formatField.getText(),
                autoOrganizeCheck.isSelected(),
                "John Doe",
                LocalDate.now()
            );
            previewPath.setText(preview);
        };
        
        pathField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        formatField.textProperty().addListener((obs, old, text) -> updatePreview.run());
        autoOrganizeCheck.selectedProperty().addListener((obs, old, selected) -> updatePreview.run());
        updatePreview.run();
        
        grid.add(previewLabel, 0, 5);
        grid.add(previewPath, 1, 5, 2, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Save on OK
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                pdfSavePath = pathField.getText();
                autoOrganizeByDriver = autoOrganizeCheck.isSelected();
                pdfFilenameFormat = formatField.getText();
                saveSettings();
                logger.info("Settings updated - Path: {}, Auto-organize: {}", pdfSavePath, autoOrganizeByDriver);
            }
            return dialogButton;
        });
        
        dialog.showAndWait();
    }
    
    private String generatePreviewPath(String basePath, String format, boolean autoOrganize, 
                                     String driverName, LocalDate date) {
        String filename = format
            .replace("{DRIVER}", driverName.replace(" ", "_"))
            .replace("{WEEK}", String.valueOf(date.get(WeekFields.ISO.weekOfWeekBasedYear())))
            .replace("{YEAR}", String.valueOf(date.getYear()))
            .replace("{DATE}", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        if (autoOrganize) {
            return basePath + File.separator + driverName + File.separator + filename;
        } else {
            return basePath + File.separator + filename;
        }
    }
    
    /**
     * Show print preview for a driver's pay stub
     */
    public void showPrintPreview(Employee driver, LocalDate weekStart, 
                                ObservableList<PayrollCalculator.PayrollRow> summaryRows) {
        if (driver == null) {
            showError("Please select a driver to preview pay stub");
            return;
        }
        
        PayrollCalculator.PayrollRow driverRow = summaryRows.stream()
            .filter(r -> r.driverName != null && r.driverName.equalsIgnoreCase(driver.getName()))
            .findFirst()
            .orElse(null);
            
        if (driverRow == null) {
            showError("No payroll data found for " + driver.getName());
            return;
        }
        
        Stage previewStage = new Stage();
        previewStage.setTitle("Pay Stub Preview - " + driver.getName());
        previewStage.initModality(Modality.APPLICATION_MODAL);
        previewStage.initStyle(StageStyle.DECORATED);
        
        VBox previewContent = createPayStubContent(driver, weekStart, driverRow);
        ScrollPane scrollPane = new ScrollPane(previewContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f5f5f5;");
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f5f5f5;");
        
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER);
        
        Button printBtn = ModernButtonStyles.createPrimaryButton("🖨️ Print");
        printBtn.setOnAction(e -> printPayStub(previewContent));
        
        Button saveBtn = ModernButtonStyles.createInfoButton("💾 Save as PDF");
        saveBtn.setOnAction(e -> {
            saveSinglePayStub(driver, weekStart, driverRow);
            previewStage.close();
        });
        
        Button settingsBtn = ModernButtonStyles.createSecondaryButton("⚙️ Settings");
        settingsBtn.setOnAction(e -> showSettingsDialog());
        
        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> previewStage.close());
        
        buttonBar.getChildren().addAll(printBtn, saveBtn, settingsBtn, closeBtn);
        
        root.getChildren().addAll(scrollPane, buttonBar);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        Scene scene = new Scene(root, 700, 900);
        previewStage.setScene(scene);
        previewStage.showAndWait();
    }
    
    /**
     * Export to PDF with automatic path management
     */
    public void exportToPDF(Employee driver, LocalDate weekStart, 
                          ObservableList<PayrollCalculator.PayrollRow> summaryRows,
                          List<Employee> allDrivers) {
        if (driver == null) {
            // Export all drivers
            exportAllPayStubsToPDF(weekStart, summaryRows, allDrivers);
        } else {
            // Export single driver
            PayrollCalculator.PayrollRow driverRow = summaryRows.stream()
                .filter(r -> r.driverName != null && r.driverName.equalsIgnoreCase(driver.getName()))
                .findFirst()
                .orElse(null);
                
            if (driverRow != null) {
                saveSinglePayStub(driver, weekStart, driverRow);
            } else {
                showError("No payroll data found for " + driver.getName());
            }
        }
    }
    
    private void saveSinglePayStub(Employee driver, LocalDate weekStart, PayrollCalculator.PayrollRow row) {
        try {
            // Generate file path
            Path filePath = generatePDFPath(driver.getName(), weekStart);
            
            // Create directories if needed
            Files.createDirectories(filePath.getParent());
            
            // Generate PDF with two pages for fuel details
            generatePDFWithSeparateFuelPage(filePath.toFile(), driver, weekStart, row);
            
            showInfo("Pay stub saved successfully to:\n" + filePath.toString());
            
            // Optionally open the file
            if (java.awt.Desktop.isDesktopSupported()) {
                Alert openAlert = new Alert(Alert.AlertType.CONFIRMATION);
                openAlert.setTitle("Open PDF");
                openAlert.setHeaderText("Pay stub saved successfully");
                openAlert.setContentText("Would you like to open the PDF?");
                
                if (openAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    java.awt.Desktop.getDesktop().open(file);
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to save pay stub", e);
            showError("Failed to save pay stub: " + e.getMessage());
        }
    }
    
    private Path generatePDFPath(String driverName, LocalDate weekStart) {
        int weekNumber = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear());
        
        String filename = pdfFilenameFormat
            .replace("{DRIVER}", driverName.replace(" ", "_"))
            .replace("{WEEK}", String.valueOf(weekNumber))
            .replace("{YEAR}", String.valueOf(weekStart.getYear()))
            .replace("{DATE}", weekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        if (!filename.endsWith(".pdf")) {
            filename += ".pdf";
        }
        
        Path basePath = Paths.get(pdfSavePath);
        
        if (autoOrganizeByDriver) {
            // Create driver-specific folder
            String folderName = driverName.replace(" ", "_");
            return basePath.resolve(folderName).resolve(filename);
        } else {
            return basePath.resolve(filename);
        }
    }
    
    private void exportAllPayStubsToPDF(LocalDate weekStart, 
                                      ObservableList<PayrollCalculator.PayrollRow> summaryRows,
                                      List<Employee> allDrivers) {
        Task<Integer> exportTask = new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {
                int processed = 0;
                int total = summaryRows.size();
                
                for (PayrollCalculator.PayrollRow row : summaryRows) {
                    if (isCancelled()) break;
                    
                    updateProgress(processed, total);
                    updateMessage("Processing " + row.driverName + "...");
                    
                    Employee emp = allDrivers.stream()
                        .filter(e -> e.getName().equals(row.driverName))
                        .findFirst()
                        .orElse(null);
                        
                    if (emp != null) {
                        try {
                            Path filePath = generatePDFPath(emp.getName(), weekStart);
                            Files.createDirectories(filePath.getParent());
                            generatePDFWithSeparateFuelPage(filePath.toFile(), emp, weekStart, row);
                            processed++;
                        } catch (Exception e) {
                            logger.error("Failed to generate PDF for {}", emp.getName(), e);
                        }
                    }
                }
                
                return processed;
            }
        };
        
        ProgressDialog<Integer> progressDialog = new ProgressDialog<>(exportTask);
        progressDialog.setTitle("Exporting Pay Stubs");
        progressDialog.setHeaderText("Generating PDF files for all drivers...");
        progressDialog.initModality(Modality.APPLICATION_MODAL);
        
        exportTask.setOnSucceeded(e -> {
            int count = exportTask.getValue();
            showInfo(String.format("Successfully exported %d pay stubs to:\n%s", count, pdfSavePath));
        });
        
        exportTask.setOnFailed(e -> {
            logger.error("Failed to export pay stubs", exportTask.getException());
            showError("Failed to export pay stubs: " + exportTask.getException().getMessage());
        });
        
        executorService.submit(exportTask);
        progressDialog.showAndWait();
    }
    
    /**
     * Generate PDF with fuel transactions on a separate page
     */
    private void generatePDFWithSeparateFuelPage(File file, Employee driver, LocalDate weekStart, 
                                                PayrollCalculator.PayrollRow payrollRow) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // First page - Main pay stub
            PDPage page1 = new PDPage(PDRectangle.A4);
            document.addPage(page1);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page1)) {
                float yPosition = page1.getMediaBox().getHeight() - 50;
                float pageWidth = page1.getMediaBox().getWidth();
                
                // Draw main pay stub content (without fuel details)
                yPosition = drawPayStubHeader(contentStream, yPosition, pageWidth);
                yPosition = drawEmployeeInfo(contentStream, yPosition, driver, weekStart);
                yPosition = drawEarningsSection(contentStream, yPosition, payrollRow);
                yPosition = drawLoadDetails(contentStream, yPosition, driver, weekStart);
                yPosition = drawDeductionsSummary(contentStream, yPosition, payrollRow);
                
                if (payrollRow.reimbursements > 0) {
                    yPosition = drawReimbursements(contentStream, yPosition, payrollRow);
                }
                
                if (payrollRow.advancesGiven > 0) {
                    yPosition = drawAdvances(contentStream, yPosition, payrollRow);
                }
                
                yPosition = drawNetPay(contentStream, yPosition, payrollRow);
                drawFooter(contentStream, page1, 1, 2); // Page 1 of 2
            }
            
            // Second page - Fuel transaction details (if fuel deductions exist)
            if (Math.abs(payrollRow.fuel) > 0) {
                PDPage page2 = new PDPage(PDRectangle.A4);
                document.addPage(page2);
                
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page2)) {
                    float yPosition = page2.getMediaBox().getHeight() - 50;
                    
                    // Header for page 2
                    contentStream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f);
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20);
                    contentStream.newLineAtOffset(50, yPosition);
                    contentStream.showText("FUEL TRANSACTION DETAILS");
                    contentStream.endText();
                    
                    yPosition -= 30;
                    
                    // Driver info
                    contentStream.setNonStrokingColor(0, 0, 0);
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(50, yPosition);
                    contentStream.showText("Driver: " + driver.getName());
                    contentStream.endText();
                    
                    yPosition -= 20;
                    
                    LocalDate weekEnd = weekStart.plusDays(6);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(50, yPosition);
                    contentStream.showText("Period: " + weekStart.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) + 
                                         " - " + weekEnd.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
                    contentStream.endText();
                    
                    yPosition -= 30;
                    
                    // Draw fuel transactions table
                    yPosition = drawFuelTransactionTable(contentStream, yPosition, driver, weekStart, weekEnd);
                    
                    drawFooter(contentStream, page2, 2, 2); // Page 2 of 2
                }
            }
            
            document.save(file);
            logger.info("Pay stub with separate fuel page saved to: {}", file.getAbsolutePath());
        }
    }
    
    // Helper methods for PDF generation
    private float drawPayStubHeader(PDPageContentStream stream, float yPosition, float pageWidth) throws IOException {
        String companyName = payrollTab.getCompanyName();
        
        stream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
        stream.newLineAtOffset(50, yPosition);
        stream.showText(companyName);
        stream.endText();
        
        yPosition -= 30;
        
        stream.setNonStrokingColor(66f/255f, 66f/255f, 66f/255f);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
        stream.newLineAtOffset(50, yPosition);
        stream.showText("EARNINGS STATEMENT");
        stream.endText();
        
        yPosition -= 25;
        
        // Draw separator line
        stream.setStrokingColor(200f/255f, 200f/255f, 200f/255f);
        stream.setLineWidth(1);
        stream.moveTo(50, yPosition);
        stream.lineTo(pageWidth - 50, yPosition);
        stream.stroke();
        
        return yPosition - 20;
    }
    
    private float drawEmployeeInfo(PDPageContentStream stream, float yPosition, 
                                 Employee driver, LocalDate weekStart) throws IOException {
        // Background box
        stream.setNonStrokingColor(245f/255f, 245f/255f, 245f/255f);
        stream.addRect(40, yPosition - 80, 515, 80);
        stream.fill();
        
        stream.setNonStrokingColor(0, 0, 0);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
        stream.newLineAtOffset(50, yPosition - 20);
        stream.showText("Employee Information");
        stream.endText();
        
        yPosition -= 35;
        
        LocalDate weekEnd = weekStart.plusDays(6);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        
        // Employee details
        String[][] info = {
            {"Employee:", driver.getName()},
            {"Employee ID:", String.valueOf(driver.getId())},
            {"Truck/Unit:", driver.getTruckUnit() != null ? driver.getTruckUnit() : "N/A"},
            {"Pay Period:", weekStart.format(formatter) + " - " + weekEnd.format(formatter)},
            {"Pay Date:", LocalDate.now().format(formatter)}
        };
        
        for (String[] row : info) {
            stream.beginText();
            stream.newLineAtOffset(50, yPosition);
            stream.showText(row[0]);
            stream.endText();
            
            stream.beginText();
            stream.newLineAtOffset(200, yPosition);
            stream.showText(row[1]);
            stream.endText();
            
            yPosition -= 15;
        }
        
        return yPosition - 20;
    }
    
    private float drawEarningsSection(PDPageContentStream stream, float yPosition, 
                                    PayrollCalculator.PayrollRow row) throws IOException {
        stream.setNonStrokingColor(46f/255f, 125f/255f, 50f/255f);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
        stream.newLineAtOffset(50, yPosition);
        stream.showText("EARNINGS");
        stream.endText();
        
        yPosition -= 20;
        
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        
        String[][] earnings = {
            {"Gross Pay:", String.format("$%,.2f", row.gross)},
            {"Load Count:", String.valueOf(row.loadCount)},
            {"Service Fee:", String.format("($%,.2f)", Math.abs(row.serviceFee))},
            {"Gross After Service Fee:", String.format("$%,.2f", row.grossAfterServiceFee)},
            {"Company Pay:", String.format("$%,.2f", row.companyPay)},
            {"Driver Pay:", String.format("$%,.2f", row.driverPay)}
        };
        
        for (String[] item : earnings) {
            stream.beginText();
            stream.newLineAtOffset(60, yPosition);
            stream.showText(item[0]);
            stream.endText();
            
            stream.beginText();
            stream.newLineAtOffset(400, yPosition);
            stream.showText(item[1]);
            stream.endText();
            
            yPosition -= 15;
        }
        
        return yPosition - 15;
    }
    
    private float drawLoadDetails(PDPageContentStream stream, float yPosition, 
                                Employee driver, LocalDate weekStart) throws IOException {
        stream.setNonStrokingColor(21f/255f, 101f/255f, 192f/255f);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
        stream.newLineAtOffset(50, yPosition);
        stream.showText("LOAD DETAILS");
        stream.endText();
        
        yPosition -= 20;
        
        // Get loads for the period
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Load> loads = loadDAO.getByDriverAndDateRangeForFinancials(driver.getId(), weekStart, weekEnd);
        
        if (loads.isEmpty()) {
            stream.setNonStrokingColor(100f/255f, 100f/255f, 100f/255f);
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
            stream.newLineAtOffset(60, yPosition);
            stream.showText("No loads found for this period");
            stream.endText();
            return yPosition - 20;
        }
        
        // Table headers
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
        
        stream.beginText();
        stream.newLineAtOffset(60, yPosition);
        stream.showText("Load #");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(140, yPosition);
        stream.showText("Pick Up");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(310, yPosition);
        stream.showText("Drop Off");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(480, yPosition);
        stream.showText("Gross");
        stream.endText();
        
        yPosition -= 15;
        
        // Load data
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
        double totalGross = 0;
        
        for (Load load : loads) {
            stream.beginText();
            stream.newLineAtOffset(60, yPosition);
            stream.showText(load.getLoadNumber() != null ? load.getLoadNumber() : "N/A");
            stream.endText();
            
            stream.beginText();
            stream.newLineAtOffset(140, yPosition);
            stream.showText(truncateLocation(load.getPrimaryPickupLocation()));
            stream.endText();
            
            stream.beginText();
            stream.newLineAtOffset(310, yPosition);
            stream.showText(truncateLocation(load.getPrimaryDropLocation()));
            stream.endText();
            
            stream.beginText();
            stream.newLineAtOffset(480, yPosition);
            stream.showText(String.format("$%.2f", load.getGrossAmount()));
            stream.endText();
            
            totalGross += load.getGrossAmount();
            yPosition -= 12;
            
            // Check if we're running out of space
            if (yPosition < 150) {
                // Truncate and show total
                stream.beginText();
                stream.newLineAtOffset(60, yPosition);
                stream.showText("... " + (loads.size() - loads.indexOf(load) - 1) + " more loads");
                stream.endText();
                yPosition -= 12;
                break;
            }
        }
        
        // Total line
        if (loads.size() > 1) {
            yPosition -= 5;
            stream.setStrokingColor(200f/255f, 200f/255f, 200f/255f);
            stream.setLineWidth(0.5f);
            stream.moveTo(310, yPosition);
            stream.lineTo(530, yPosition);
            stream.stroke();
            
            yPosition -= 12;
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
            stream.beginText();
            stream.newLineAtOffset(310, yPosition);
            stream.showText("Total:");
            stream.endText();
            
            stream.beginText();
            stream.newLineAtOffset(480, yPosition);
            stream.showText(String.format("$%.2f", totalGross));
            stream.endText();
        }
        
        return yPosition - 20;
    }
    
    private float drawDeductionsSummary(PDPageContentStream stream, float yPosition, 
                                      PayrollCalculator.PayrollRow row) throws IOException {
        stream.setNonStrokingColor(211f/255f, 47f/255f, 47f/255f);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
        stream.newLineAtOffset(50, yPosition);
        stream.showText("DEDUCTIONS SUMMARY");
        stream.endText();
        
        yPosition -= 20;
        
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        
        String[][] deductions = {
            {"Fuel (see page 2 for details):", String.format("$%,.2f", Math.abs(row.fuel))},
            {"Recurring Fees:", String.format("$%,.2f", Math.abs(row.recurringFees))},
            {"Advance Repayments:", String.format("$%,.2f", Math.abs(row.advanceRepayments))},
            {"Escrow Deposits:", String.format("$%,.2f", Math.abs(row.escrowDeposits))},
            {"Other Deductions:", String.format("$%,.2f", Math.abs(row.otherDeductions))}
        };
        
        for (String[] item : deductions) {
            stream.beginText();
            stream.newLineAtOffset(60, yPosition);
            stream.showText(item[0]);
            stream.endText();
            
            stream.beginText();
            stream.newLineAtOffset(400, yPosition);
            stream.showText(item[1]);
            stream.endText();
            
            yPosition -= 15;
        }
        
        // Total deductions
        double totalDeductions = Math.abs(row.fuel) + Math.abs(row.recurringFees) + 
                               Math.abs(row.advanceRepayments) + Math.abs(row.escrowDeposits) + 
                               Math.abs(row.otherDeductions);
        
        yPosition -= 5;
        stream.setStrokingColor(200f/255f, 200f/255f, 200f/255f);
        stream.moveTo(250, yPosition);
        stream.lineTo(500, yPosition);
        stream.stroke();
        
        yPosition -= 15;
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
        stream.beginText();
        stream.newLineAtOffset(250, yPosition);
        stream.showText("Total Deductions:");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(400, yPosition);
        stream.showText(String.format("$%,.2f", totalDeductions));
        stream.endText();
        
        return yPosition - 20;
    }
    
    private float drawReimbursements(PDPageContentStream stream, float yPosition, 
                                   PayrollCalculator.PayrollRow row) throws IOException {
        stream.setNonStrokingColor(25f/255f, 118f/255f, 210f/255f);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
        stream.newLineAtOffset(50, yPosition);
        stream.showText("REIMBURSEMENTS");
        stream.endText();
        
        yPosition -= 20;
        
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        stream.beginText();
        stream.newLineAtOffset(60, yPosition);
        stream.showText("Total Reimbursements:");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(400, yPosition);
        stream.showText(String.format("$%,.2f", row.reimbursements));
        stream.endText();
        
        return yPosition - 25;
    }
    
    private float drawAdvances(PDPageContentStream stream, float yPosition, 
                             PayrollCalculator.PayrollRow row) throws IOException {
        stream.setNonStrokingColor(255f/255f, 152f/255f, 0f/255f);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
        stream.newLineAtOffset(50, yPosition);
        stream.showText("ADVANCES");
        stream.endText();
        
        yPosition -= 20;
        
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        stream.beginText();
        stream.newLineAtOffset(60, yPosition);
        stream.showText("Advances Given This Week:");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(400, yPosition);
        stream.showText(String.format("$%,.2f", row.advancesGiven));
        stream.endText();
        
        return yPosition - 25;
    }
    
    private float drawNetPay(PDPageContentStream stream, float yPosition, 
                           PayrollCalculator.PayrollRow row) throws IOException {
        // Highlight box
        if (row.netPay >= 0) {
            stream.setNonStrokingColor(232f/255f, 245f/255f, 233f/255f);
        } else {
            stream.setNonStrokingColor(255f/255f, 235f/255f, 238f/255f);
        }
        stream.addRect(40, yPosition - 35, 515, 35);
        stream.fill();
        
        yPosition -= 25;
        
        if (row.netPay >= 0) {
            stream.setNonStrokingColor(27f/255f, 94f/255f, 32f/255f);
        } else {
            stream.setNonStrokingColor(211f/255f, 47f/255f, 47f/255f);
        }
        
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20);
        stream.newLineAtOffset(50, yPosition);
        stream.showText("NET PAY:");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(400, yPosition);
        stream.showText(String.format("$%,.2f", row.netPay));
        stream.endText();
        
        return yPosition - 30;
    }
    
    private float drawFuelTransactionTable(PDPageContentStream stream, float yPosition, 
                                         Employee driver, LocalDate weekStart, LocalDate weekEnd) throws IOException {
        // Get fuel transactions
        List<FuelTransaction> fuelTransactions = fuelDAO.getByDriverAndDateRange(driver.getName(), weekStart, weekEnd);
        
        if (fuelTransactions.isEmpty()) {
            stream.setNonStrokingColor(100f/255f, 100f/255f, 100f/255f);
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(50, yPosition);
            stream.showText("No fuel transactions found for this period");
            stream.endText();
            return yPosition - 30;
        }
        
        // Table header background
        stream.setNonStrokingColor(240f/255f, 240f/255f, 240f/255f);
        stream.addRect(40, yPosition - 20, 515, 20);
        stream.fill();
        
        // Table headers
        stream.setNonStrokingColor(0, 0, 0);
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
        
        String[] headers = {"Date", "Location", "Invoice", "Gallons", "Amount", "Fee", "Total"};
        float[] columnX = {50, 120, 250, 330, 380, 430, 480};
        
        for (int i = 0; i < headers.length; i++) {
            stream.beginText();
            stream.newLineAtOffset(columnX[i], yPosition - 15);
            stream.showText(headers[i]);
            stream.endText();
        }
        
        yPosition -= 25;
        
        // Table data
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd");
        
        double totalAmount = 0;
        double totalFees = 0;
        int rowCount = 0;
        
        for (FuelTransaction trans : fuelTransactions) {
            // Alternate row background
            if (rowCount % 2 == 1) {
                stream.setNonStrokingColor(250f/255f, 250f/255f, 250f/255f);
                stream.addRect(40, yPosition - 12, 515, 14);
                stream.fill();
            }
            
            stream.setNonStrokingColor(0, 0, 0);
            
            // Date
            String dateStr = "N/A";
            try {
                LocalDate tranDate = LocalDate.parse(trans.getTranDate(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                dateStr = tranDate.format(dateFormatter);
            } catch (Exception e) {
                dateStr = trans.getTranDate() != null ? trans.getTranDate() : "N/A";
            }
            
            stream.beginText();
            stream.newLineAtOffset(columnX[0], yPosition);
            stream.showText(dateStr);
            stream.endText();
            
            // Location
            stream.beginText();
            stream.newLineAtOffset(columnX[1], yPosition);
            String location = trans.getLocationName() != null ? truncateText(trans.getLocationName(), 20) : "N/A";
            stream.showText(location);
            stream.endText();
            
            // Invoice
            stream.beginText();
            stream.newLineAtOffset(columnX[2], yPosition);
            stream.showText(trans.getInvoice() != null ? trans.getInvoice() : "N/A");
            stream.endText();
            
            // Gallons
            stream.beginText();
            stream.newLineAtOffset(columnX[3], yPosition);
            stream.showText(String.format("%.1f", trans.getQty()));
            stream.endText();
            
            // Amount
            stream.beginText();
            stream.newLineAtOffset(columnX[4], yPosition);
            stream.showText(String.format("$%.2f", trans.getAmt()));
            stream.endText();
            
            // Fee
            stream.beginText();
            stream.newLineAtOffset(columnX[5], yPosition);
            stream.showText(String.format("$%.2f", trans.getFees()));
            stream.endText();
            
            // Total
            double total = trans.getAmt() + trans.getFees();
            stream.beginText();
            stream.newLineAtOffset(columnX[6], yPosition);
            stream.showText(String.format("$%.2f", total));
            stream.endText();
            
            totalAmount += trans.getAmt();
            totalFees += trans.getFees();
            yPosition -= 14;
            rowCount++;
            
            // Check if we need a new page
            if (yPosition < 100) {
                stream.beginText();
                stream.newLineAtOffset(50, yPosition);
                stream.showText("... continued on next page");
                stream.endText();
                break;
            }
        }
        
        // Totals
        yPosition -= 10;
        stream.setStrokingColor(0, 0, 0);
        stream.setLineWidth(1);
        stream.moveTo(330, yPosition);
        stream.lineTo(530, yPosition);
        stream.stroke();
        
        yPosition -= 15;
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
        
        stream.beginText();
        stream.newLineAtOffset(330, yPosition);
        stream.showText("Totals:");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(380, yPosition);
        stream.showText(String.format("$%.2f", totalAmount));
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(430, yPosition);
        stream.showText(String.format("$%.2f", totalFees));
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(480, yPosition);
        stream.showText(String.format("$%.2f", totalAmount + totalFees));
        stream.endText();
        
        return yPosition - 30;
    }
    
    private void drawFooter(PDPageContentStream stream, PDPage page, int pageNum, int totalPages) throws IOException {
        float yPosition = 30;
        
        stream.setNonStrokingColor(117f/255f, 117f/255f, 117f/255f);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
        stream.newLineAtOffset(50, yPosition);
        stream.showText("This is an electronic pay stub. Please retain for your records.");
        stream.endText();
        
        stream.beginText();
        stream.newLineAtOffset(50, yPosition - 10);
        stream.showText("Generated on: " + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a")));
        stream.endText();
        
        // Page number
        stream.beginText();
        stream.newLineAtOffset(page.getMediaBox().getWidth() - 100, yPosition);
        stream.showText("Page " + pageNum + " of " + totalPages);
        stream.endText();
    }
    
    /**
     * Export to Excel
     */
    public void exportToExcel(LocalDate weekStart, ObservableList<PayrollCalculator.PayrollRow> summaryRows,
                            List<Employee> allDrivers) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Payroll to Excel");
        fileChooser.setInitialFileName("payroll_" + weekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        
        File file = fileChooser.showSaveDialog(payrollTab.getScene().getWindow());
        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                
                // Write BOM for Excel UTF-8 recognition
                writer.write('\ufeff');
                
                // Write headers
                String headers = "Driver,Truck/Unit,Loads,Gross Pay,Service Fee,Gross After Fee," +
                               "Company Pay,Driver Pay,Fuel,After Fuel,Recurring,Advances Given," +
                               "Advance Repayments,Escrow,Other Deductions,Reimbursements,NET PAY";
                writer.write(headers);
                writer.newLine();
                
                // Write data
                for (PayrollCalculator.PayrollRow row : summaryRows) {
                    String truckUnit = "";
                    Employee emp = allDrivers.stream()
                        .filter(e -> e.getName().equals(row.driverName))
                        .findFirst()
                        .orElse(null);
                    if (emp != null && emp.getTruckUnit() != null) {
                        truckUnit = escapeCSV(emp.getTruckUnit());
                    }
                    
                    String line = String.format("%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f",
                        escapeCSV(row.driverName),
                        truckUnit,
                        row.loadCount,
                        row.gross,
                        row.serviceFee,
                        row.grossAfterServiceFee,
                        row.companyPay,
                        row.driverPay,
                        row.fuel,
                        row.grossAfterFuel,
                        row.recurringFees,
                        row.advancesGiven,
                        row.advanceRepayments,
                        row.escrowDeposits,
                        row.otherDeductions,
                        row.reimbursements,
                        row.netPay
                    );
                    writer.write(line);
                    writer.newLine();
                }
                
                showInfo("Payroll data exported successfully");
            } catch (IOException e) {
                logger.error("Failed to export to CSV", e);
                showError("Failed to export: " + e.getMessage());
            }
        }
    }
    
    /**
     * Copy table to clipboard
     */
    public void copyTableToClipboard(ObservableList<PayrollCalculator.PayrollRow> summaryRows,
                                   List<Employee> allDrivers) {
        StringBuilder sb = new StringBuilder();
        
        // Headers
        sb.append("Driver\tTruck/Unit\tLoads\tGross Pay\tService Fee\tGross After Fee\t");
        sb.append("Company Pay\tDriver Pay\tFuel\tAfter Fuel\tRecurring\tAdvances Given\t");
        sb.append("Advance Repayments\tEscrow\tOther Deductions\tReimbursements\tNET PAY\n");
        
        // Data
        for (PayrollCalculator.PayrollRow row : summaryRows) {
            String truckUnit = "";
            Employee emp = allDrivers.stream()
                .filter(e -> e.getName().equals(row.driverName))
                .findFirst()
                .orElse(null);
            if (emp != null && emp.getTruckUnit() != null) {
                truckUnit = emp.getTruckUnit();
            }
            
            sb.append(String.format("%s\t%s\t%d\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\t$%.2f\n",
                row.driverName,
                truckUnit,
                row.loadCount,
                row.gross,
                row.serviceFee,
                row.grossAfterServiceFee,
                row.companyPay,
                row.driverPay,
                row.fuel,
                row.grossAfterFuel,
                row.recurringFees,
                row.advancesGiven,
                row.advanceRepayments,
                row.escrowDeposits,
                row.otherDeductions,
                row.reimbursements,
                row.netPay
            ));
        }
        
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
        
        showInfo("Table copied to clipboard");
    }
    
    /**
     * Show payroll history
     */
    public void showPayrollHistory(List<Employee> allDrivers, Set<String> lockedWeeks,
                                 Runnable onHistoryChanged) {
        Stage historyStage = new Stage();
        historyStage.setTitle("Payroll History");
        historyStage.initModality(Modality.APPLICATION_MODAL);
        historyStage.initStyle(StageStyle.DECORATED);
        
        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: #f5f7fa;");
        
        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: linear-gradient(to right, #2196F3, #1976D2); " +
                       "-fx-background-radius: 8px;");
        
        Label title = new Label("Payroll History");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        title.setTextFill(Color.WHITE);
        
        header.getChildren().add(title);
        
        // Filter controls
        HBox filterBox = new HBox(15);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        filterBox.setPadding(new Insets(10));
        filterBox.setStyle("-fx-background-color: white; -fx-background-radius: 8px;");
        
        DatePicker fromDate = new DatePicker(LocalDate.now().minusMonths(3));
        DatePicker toDate = new DatePicker(LocalDate.now());
        ComboBox<Employee> driverFilter = new ComboBox<>();
        driverFilter.getItems().add(null);
        driverFilter.getItems().addAll(allDrivers);
        driverFilter.setPromptText("All Drivers");
        driverFilter.setPrefWidth(200);
        
        Button searchBtn = ModernButtonStyles.createPrimaryButton("Search");
        Button exportHistoryBtn = ModernButtonStyles.createSecondaryButton("Export");
        Button settingsBtn = ModernButtonStyles.createSecondaryButton("⚙️ Settings");
        settingsBtn.setOnAction(e -> showSettingsDialog());
        
        filterBox.getChildren().addAll(
            new Label("From:"), fromDate,
            new Label("To:"), toDate,
            new Label("Driver:"), driverFilter,
            searchBtn,
            exportHistoryBtn,
            settingsBtn
        );
        
        // History table
        TableView<PayrollHistoryEntry> historyTable = createHistoryTable();
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        
        // Load initial data
        ObservableList<PayrollHistoryEntry> historyData = FXCollections.observableArrayList();
        loadHistoryData(historyData, fromDate.getValue(), toDate.getValue(), null, allDrivers, lockedWeeks);
        historyTable.setItems(historyData);
        
        // Search action
        searchBtn.setOnAction(e -> {
            loadHistoryData(historyData, fromDate.getValue(), toDate.getValue(), 
                          driverFilter.getValue(), allDrivers, lockedWeeks);
        });
        
        // Export action
        exportHistoryBtn.setOnAction(e -> exportHistory(historyData));
        
        // Summary footer
        HBox summaryBox = createHistorySummaryBox(historyData);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> historyStage.close());
        
        buttonBox.getChildren().add(closeBtn);
        
        root.getChildren().addAll(header, filterBox, historyTable, summaryBox, buttonBox);
        
        Scene scene = new Scene(root, 1200, 700);
        historyStage.setScene(scene);
        historyStage.showAndWait();
    }
    
    private TableView<PayrollHistoryEntry> createHistoryTable() {
        TableView<PayrollHistoryEntry> table = new TableView<>();
        table.setStyle("-fx-background-color: white;");
        
        TableColumn<PayrollHistoryEntry, String> dateCol = new TableColumn<>("Week Starting");
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))));
        dateCol.setPrefWidth(120);
        
        TableColumn<PayrollHistoryEntry, String> driverCol = new TableColumn<>("Driver");
        driverCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().driverName));
        driverCol.setPrefWidth(150);
        
        TableColumn<PayrollHistoryEntry, String> truckCol = new TableColumn<>("Truck/Unit");
        truckCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().truckUnit));
        truckCol.setPrefWidth(100);
        
        TableColumn<PayrollHistoryEntry, Number> loadsCol = new TableColumn<>("Loads");
        loadsCol.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().loadCount));
        loadsCol.setPrefWidth(80);
        loadsCol.setStyle("-fx-alignment: CENTER;");
        
        TableColumn<PayrollHistoryEntry, Number> grossCol = new TableColumn<>("Gross");
        grossCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().gross));
        grossCol.setCellFactory(col -> createCurrencyCell());
        grossCol.setPrefWidth(100);
        
        TableColumn<PayrollHistoryEntry, Number> deductionsCol = new TableColumn<>("Deductions");
        deductionsCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().totalDeductions));
        deductionsCol.setCellFactory(col -> createCurrencyCell());
        deductionsCol.setPrefWidth(100);
        
        TableColumn<PayrollHistoryEntry, Number> netCol = new TableColumn<>("Net Pay");
        netCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().netPay));
        netCol.setCellFactory(col -> createNetPayCell());
        netCol.setPrefWidth(120);
        
        TableColumn<PayrollHistoryEntry, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().locked ? "🔒 Locked" : "🔓 Open"));
        statusCol.setPrefWidth(100);
        statusCol.setStyle("-fx-alignment: CENTER;");
        
        table.getColumns().setAll(List.of(
                dateCol, driverCol, truckCol, loadsCol, grossCol,
                deductionsCol, netCol, statusCol));
        
        // Enable sorting
        table.getSortOrder().add(dateCol);
        dateCol.setSortType(TableColumn.SortType.DESCENDING);
        
        return table;
    }
    
    private TableCell<PayrollHistoryEntry, Number> createCurrencyCell() {
        return new TableCell<PayrollHistoryEntry, Number>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", value.doubleValue()));
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        };
    }
    
    private TableCell<PayrollHistoryEntry, Number> createNetPayCell() {
        return new TableCell<PayrollHistoryEntry, Number>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                } else {
                    double amount = value.doubleValue();
                    setText(String.format("$%,.2f", amount));					
                    setAlignment(Pos.CENTER_RIGHT);
                    setStyle("-fx-font-weight: bold;");
                    setTextFill(amount >= 0 ? Color.web("#2e7d32") : Color.web("#d32f2f"));
                }
            }
        };
    }
    
    private HBox createHistorySummaryBox(ObservableList<PayrollHistoryEntry> historyData) {
        HBox summaryBox = new HBox(20);
        summaryBox.setPadding(new Insets(10));
        summaryBox.setAlignment(Pos.CENTER);
        summaryBox.setStyle("-fx-background-color: white; -fx-background-radius: 8px;");
        
        Label recordCount = new Label("Records: " + historyData.size());
        Label totalGross = new Label();
        Label totalNet = new Label();
        
        historyData.addListener((ListChangeListener<PayrollHistoryEntry>) c -> {
            recordCount.setText("Records: " + historyData.size());
            double gross = historyData.stream().mapToDouble(e -> e.gross).sum();
            double net = historyData.stream().mapToDouble(e -> e.netPay).sum();
            totalGross.setText(String.format("Total Gross: $%,.2f", gross));
            totalNet.setText(String.format("Total Net: $%,.2f", net));
        });
        
        summaryBox.getChildren().addAll(recordCount, 
            new Separator(Orientation.VERTICAL),
            totalGross, 
            new Separator(Orientation.VERTICAL),
            totalNet);
        
        return summaryBox;
    }
    
    private void loadHistoryData(ObservableList<PayrollHistoryEntry> data, 
                               LocalDate from, LocalDate to, Employee driver,
                               List<Employee> allDrivers, Set<String> lockedWeeks) {
        data.clear();
        
        Task<List<PayrollHistoryEntry>> loadTask = new Task<List<PayrollHistoryEntry>>() {
            @Override
            protected List<PayrollHistoryEntry> call() throws Exception {
                List<PayrollHistoryEntry> entries = new ArrayList<>();
                PayrollHistoryDAO historyDAO = new PayrollHistoryDAO();
                
                // Load from database
                List<PayrollHistoryEntry> savedEntries = historyDAO.getPayrollHistoryRange(from, to);
                
                if (driver != null) {
                    // Filter by driver
                    savedEntries = savedEntries.stream()
                        .filter(e -> e.driverName.equalsIgnoreCase(driver.getName()))
                        .collect(Collectors.toList());
                }
                
                entries.addAll(savedEntries);
                return entries;
            }
        };
        
        loadTask.setOnSucceeded(e -> data.addAll(loadTask.getValue()));
        loadTask.setOnFailed(e -> {
            logger.error("Failed to load history", loadTask.getException());
            showError("Failed to load history: " + loadTask.getException().getMessage());
        });
        
        executorService.submit(loadTask);
    }
    
    private void exportHistory(ObservableList<PayrollHistoryEntry> data) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Payroll History");
        fileChooser.setInitialFileName("payroll_history_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        
        File file = fileChooser.showSaveDialog(payrollTab.getScene().getWindow());
        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                
                writer.write('\ufeff'); // BOM for Excel
                writer.write("Week Starting,Driver,Truck/Unit,Loads,Gross,Deductions,Net Pay,Status");
                writer.newLine();
                
                for (PayrollHistoryEntry entry : data) {
                    writer.write(String.format("%s,%s,%s,%d,%.2f,%.2f,%.2f,%s",
                        entry.date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
                        escapeCSV(entry.driverName),
                        escapeCSV(entry.truckUnit),
                        entry.loadCount,
                        entry.gross,
                        entry.totalDeductions,
                        entry.netPay,
                        entry.locked ? "Locked" : "Open"
                    ));
                    writer.newLine();
                }
                
                showInfo("History exported successfully");
            } catch (IOException e) {
                logger.error("Failed to export history", e);
                showError("Failed to export: " + e.getMessage());
            }
        }
    }
    
    /**
     * Create pay stub content for display
     */
    private VBox createPayStubContent(Employee driver, LocalDate weekStart, 
                                    PayrollCalculator.PayrollRow row) {
        VBox content = new VBox(8);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: white;");
        content.setPrefWidth(650);
        content.setMaxWidth(650);
        
        // Header
        VBox header = new VBox(2);
        header.setAlignment(Pos.CENTER);
        
        Label companyName = new Label(payrollTab.getCompanyName());
        companyName.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        companyName.setTextFill(Color.web("#1976D2"));
        
        Label payStubTitle = new Label("EARNINGS STATEMENT");
        payStubTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        payStubTitle.setTextFill(Color.web("#424242"));
        
        header.getChildren().addAll(companyName, payStubTitle);
        
        Separator headerSep = new Separator();
        headerSep.setPadding(new Insets(5, 0, 5, 0));
        
        // Employee info
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(20);
        infoGrid.setVgap(4);
        infoGrid.setPadding(new Insets(5));
        infoGrid.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 5px;");
        
        LocalDate weekEnd = weekStart.plusDays(6);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        
        addStyledPayStubRow(infoGrid, 0, "Employee:", driver.getName(), true);
        addStyledPayStubRow(infoGrid, 1, "Employee ID:", String.valueOf(driver.getId()), false);
        addStyledPayStubRow(infoGrid, 2, "Truck/Unit:", driver.getTruckUnit() != null ? driver.getTruckUnit() : "N/A", false);
        addStyledPayStubRow(infoGrid, 3, "Pay Period:", weekStart.format(formatter) + " - " + weekEnd.format(formatter), false);
        addStyledPayStubRow(infoGrid, 4, "Pay Date:", LocalDate.now().format(formatter), false);
        
        // Add percentage information
        addStyledPayStubRow(infoGrid, 5, "Rate Split:", 
            String.format("Driver: %.0f%% | Company: %.0f%% | Service Fee: %.0f%%", 
                row.driverPercent, row.companyPercent, row.serviceFeePercent), false);
        
        // Earnings section
        VBox earningsSection = createPayStubSection("EARNINGS", new String[][] {
            {"Gross Pay", String.format("$%,.2f", row.gross)},
            {"Load Count", String.valueOf(row.loadCount)},
            {"Service Fee", String.format("($%,.2f)", Math.abs(row.serviceFee))},
            {"Gross After Service Fee", String.format("$%,.2f", row.grossAfterServiceFee)},
            {"Company Pay", String.format("$%,.2f", row.companyPay)},
            {"Driver Pay", String.format("$%,.2f", row.driverPay)}
        }, Color.web("#2E7D32"));
        
        // Deductions section
        VBox deductionsSection = createPayStubSection("DEDUCTIONS", new String[][] {
            {"Fuel", String.format("$%,.2f", Math.abs(row.fuel))},
            {"Recurring Fees", String.format("$%,.2f", Math.abs(row.recurringFees))},
            {"Advance Repayments", String.format("$%,.2f", Math.abs(row.advanceRepayments))},
            {"Escrow Deposits", String.format("$%,.2f", Math.abs(row.escrowDeposits))},
            {"Other Deductions", String.format("$%,.2f", Math.abs(row.otherDeductions))},
            {"Total Deductions", String.format("$%,.2f", 
                Math.abs(row.fuel) + Math.abs(row.recurringFees) + 
                Math.abs(row.advanceRepayments) + Math.abs(row.escrowDeposits) + 
                Math.abs(row.otherDeductions))}
        }, Color.web("#D32F2F"));
        
        // Reimbursements section (if any)
        VBox reimbSection = null;
        if (row.reimbursements > 0) {
            reimbSection = createPayStubSection("REIMBURSEMENTS", new String[][] {
                {"Total Reimbursements", String.format("$%,.2f", row.reimbursements)}
            }, Color.web("#1976D2"));
        }
        
        // Advances Given section (if any)
        VBox advancesSection = null;
        if (row.advancesGiven > 0) {
            advancesSection = createPayStubSection("ADVANCES", new String[][] {
                {"Advances Given This Week", String.format("$%,.2f", row.advancesGiven)}
            }, Color.web("#FF9800"));
        }
        
        // Net pay section
        HBox netPayBox = new HBox();
        netPayBox.setPadding(new Insets(10));
        netPayBox.setAlignment(Pos.CENTER);
        netPayBox.setStyle("-fx-background-color: linear-gradient(to right, #E8F5E9, #C8E6C9); " +
                         "-fx-background-radius: 5px;");
        
        Label netPayLbl = new Label("NET PAY: ");
        netPayLbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        netPayLbl.setTextFill(Color.web("#1B5E20"));
        
        Label netPayAmt = new Label(String.format("$%,.2f", row.netPay));
        netPayAmt.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        netPayAmt.setTextFill(row.netPay >= 0 ? Color.web("#1B5E20") : Color.web("#D32F2F"));
        
        netPayBox.getChildren().addAll(netPayLbl, netPayAmt);
        
        // Add all sections
        content.getChildren().addAll(
            header, headerSep, infoGrid,
            new Separator(), earningsSection,
            new Separator(), deductionsSection
        );
        
        if (reimbSection != null) {
            content.getChildren().addAll(new Separator(), reimbSection);
        }
        
        if (advancesSection != null) {
            content.getChildren().addAll(new Separator(), advancesSection);
        }
        
        content.getChildren().addAll(new Separator(), netPayBox);
        
        // Footer
        VBox footer = new VBox(2);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(10, 0, 0, 0));
        
        Label footerText = new Label("This is an electronic pay stub. Please retain for your records.");
        footerText.setStyle("-fx-font-style: italic; -fx-text-fill: #757575; -fx-font-size: 9px;");
        
        Label dateGenerated = new Label("Generated on: " + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a")));
        dateGenerated.setStyle("-fx-font-size: 8px; -fx-text-fill: #757575;");
        
        footer.getChildren().addAll(footerText, dateGenerated);
        content.getChildren().add(footer);
        
        return content;
    }
    
    private VBox createPayStubSection(String title, String[][] items, Color accentColor) {
        VBox section = new VBox(4);
        section.setPadding(new Insets(5));
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        titleLabel.setTextFill(accentColor);
        
        GridPane grid = new GridPane();
        grid.setHgap(150);
        grid.setVgap(2);
        grid.setPadding(new Insets(2, 0, 0, 10));
        
        for (int i = 0; i < items.length; i++) {
            Label label = new Label(items[i][0] + ":");
            label.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
            
            Label value = new Label(items[i][1]);
            value.setFont(Font.font("Arial", 
                i == items.length - 1 ? FontWeight.BOLD : FontWeight.NORMAL, 10));
            
            if (i == items.length - 1 && items.length > 1) {
                Separator sep = new Separator();
                GridPane.setColumnSpan(sep, 2);
                grid.add(sep, 0, i * 2 - 1);
            }
            
            grid.add(label, 0, i * 2);
            grid.add(value, 1, i * 2);
        }
        
        section.getChildren().addAll(titleLabel, grid);
        return section;
    }
    
    private void addStyledPayStubRow(GridPane grid, int row, String label, String value, boolean bold) {
        Label lblNode = new Label(label);
        lblNode.setFont(Font.font("Arial", bold ? FontWeight.BOLD : FontWeight.NORMAL, 10));
        lblNode.setTextFill(Color.web("#424242"));
        
        Label valNode = new Label(value);
        valNode.setFont(Font.font("Arial", bold ? FontWeight.BOLD : FontWeight.NORMAL, 10));
        valNode.setTextFill(Color.web("#212121"));
        
        grid.add(lblNode, 0, row);
        grid.add(valNode, 1, row);
    }
    
    private void printPayStub(Node content) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(payrollTab.getScene().getWindow())) {
            PageLayout pageLayout = job.getPrinter().createPageLayout(
                Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);
            job.getJobSettings().setPageLayout(pageLayout);
            
            boolean printed = job.printPage(content);
            if (printed) {
                job.endJob();
                showInfo("Pay stub sent to printer");
            } else {
                showError("Failed to print pay stub");
            }
        }
    }
    
    // Utility methods
    private String truncateLocation(String location) {
        if (location == null) return "N/A";
        String[] parts = location.split(",");
        if (parts.length >= 2) {
            return parts[0].trim() + ", " + parts[1].trim();
        }
        if (location.length() > 20) {
            return location.substring(0, 17) + "...";
        }
        return location;
    }
    
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
    
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.initStyle(StageStyle.DECORATED);
            alert.showAndWait();
        });
    }
    
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("An error occurred");
            alert.setContentText(message);
            alert.initStyle(StageStyle.DECORATED);
            alert.showAndWait();
        });
    }
    
    /**
     * Merge documents functionality
     */
    public void showMergeDocumentsDialog(Employee driver, LocalDate weekStart,
                                       ObservableList<Load> loadsRows) {
        if (driver == null) {
            showError("Please select a driver to merge documents");
            return;
        }
        
        List<Load> driverLoads = loadsRows.stream()
            .filter(l -> l.getDriver() != null && l.getDriver().getId() == driver.getId())
            .collect(Collectors.toList());
            
        if (driverLoads.isEmpty()) {
            showError("No loads found for the selected driver in this period");
            return;
        }
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Merge Load Documents");
        dialog.setHeaderText("Merge documents for " + driver.getName() + " - Week of " + 
                           weekStart.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setMinWidth(500);
        
        // Load selection
        Label selectLabel = new Label("Select loads to include documents from:");
        selectLabel.setStyle("-fx-font-weight: bold;");
        
        CheckListView<Load> loadCheckList = new CheckListView<>();
        loadCheckList.getItems().addAll(driverLoads);
        loadCheckList.setPrefHeight(300);
        loadCheckList.setStyle("-fx-border-color: #ddd; -fx-border-radius: 5;");
        
        // Select all/none buttons
        HBox selectionButtons = new HBox(10);
        Button selectAllBtn = new Button("Select All");
        Button selectNoneBtn = new Button("Select None");
        selectAllBtn.setOnAction(e -> loadCheckList.getCheckModel().selectAll());
        selectNoneBtn.setOnAction(e -> loadCheckList.getCheckModel().clearSelection());
        selectionButtons.getChildren().addAll(selectAllBtn, selectNoneBtn);
        
        // Options
        Separator optionsSep = new Separator();
        optionsSep.setPadding(new Insets(10, 0, 10, 0));
        
        Label optionsLabel = new Label("Options:");
        optionsLabel.setStyle("-fx-font-weight: bold;");
        
        CheckBox includePayStub = new CheckBox("Include Pay Stub as Cover Page");
        includePayStub.setSelected(true);
        
        CheckBox openAfterMerge = new CheckBox("Open PDF after merging");
        openAfterMerge.setSelected(true);
        
        content.getChildren().addAll(
            selectLabel,
            loadCheckList,
            selectionButtons,
            optionsSep,
            optionsLabel,
            includePayStub,
            openAfterMerge
        );
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Style the dialog
        dialog.getDialogPane().setStyle("-fx-font-size: 14px;");
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ModernButtonStyles.applyPrimaryStyle(okButton);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                List<Load> selectedLoads = new ArrayList<>(loadCheckList.getCheckModel().getSelectedItems());
                if (selectedLoads.isEmpty()) {
                    showError("Please select at least one load");
                    return null;
                }
                
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Merged PDF");
                fileChooser.setInitialFileName(String.format("PayrollDocs_%s_%s.pdf",
                    driver.getName().replace(" ", "_"),
                    weekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
                
                File outputFile = fileChooser.showSaveDialog(dialog.getOwner());
                if (outputFile != null) {
                    PayrollCalculator.PayrollRow driverRow = payrollTab.getSummaryRows().stream()
                        .filter(r -> r.driverName.equalsIgnoreCase(driver.getName()))
                        .findFirst()
                        .orElse(null);
                        
                    mergeDocuments(driver, weekStart, selectedLoads, driverRow,
                                 includePayStub.isSelected(), outputFile, 
                                 openAfterMerge.isSelected());
                }
            }
            return null;
        });
        
        dialog.showAndWait();
    }
    
    private void mergeDocuments(Employee driver, LocalDate weekStart, List<Load> loads,
                               PayrollCalculator.PayrollRow payrollRow,
                               boolean includePayStub, File outputFile, boolean openAfter) {
        Task<Void> mergeTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Preparing documents...");
                PDFMergerUtility merger = new PDFMergerUtility();
                List<File> tempFiles = new ArrayList<>();
                
                try {
                    // Create pay stub if requested
                    if (includePayStub && payrollRow != null) {
                        updateMessage("Generating pay stub...");
                        File payStubFile = File.createTempFile("paystub", ".pdf");
                        tempFiles.add(payStubFile);
                        generatePDFWithSeparateFuelPage(payStubFile, driver, weekStart, payrollRow);
                        merger.addSource(payStubFile);
                    }
                    
                    // Add documents from each load
                    int processedDocs = 0;
                    for (Load load : loads) {
                        updateMessage("Processing load " + load.getLoadNumber() + "...");
                        List<Load.LoadDocument> docs = loadDAO.getDocumentsByLoadId(load.getId());
                        for (Load.LoadDocument doc : docs) {
                            File docFile = new File(doc.getFilePath());
                            if (docFile.exists()) {
                                if (doc.getFilePath().toLowerCase().endsWith(".pdf")) {
                                    merger.addSource(docFile);
                                    processedDocs++;
                                } else if (doc.getFilePath().toLowerCase().matches(".*\\.(jpg|jpeg|png)$")) {
                                    File tempPdf = convertImageToPDF(docFile);
                                    tempFiles.add(tempPdf);
                                    merger.addSource(tempPdf);
                                    processedDocs++;
                                }
                            }
                        }
                    }
                    
                    if (processedDocs == 0 && !includePayStub) {
                        throw new Exception("No PDF or image documents found in selected loads");
                    }
                    
                    updateMessage("Merging documents...");
                    merger.setDestinationFileName(outputFile.getAbsolutePath());
                    merger.mergeDocuments(null);
                    updateMessage("Documents merged successfully!");
                    return null;
                } finally {
                    // Clean up temp files
                    tempFiles.forEach(File::delete);
                }
            }
            
            @Override
            protected void succeeded() {
                showInfo("Documents merged successfully!");
                if (openAfter && outputFile.exists()) {
                    try {
                        java.awt.Desktop.getDesktop().open(outputFile);
                    } catch (IOException e) {
                        logger.error("Failed to open PDF", e);
                    }
                }
            }
            
            @Override
            protected void failed() {
                Throwable ex = getException();
                logger.error("Failed to merge documents", ex);
                showError("Failed to merge documents: " + ex.getMessage());
            }
        };
        
        // Progress dialog
        ProgressDialog<Void> progressDialog = new ProgressDialog<>(mergeTask);
        progressDialog.setTitle("Merging Documents");
        progressDialog.setHeaderText("Please wait while documents are being merged...");
        progressDialog.initModality(Modality.APPLICATION_MODAL);
        
        executorService.submit(mergeTask);
        progressDialog.showAndWait();
    }
    
    private File convertImageToPDF(File imageFile) throws IOException {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(imageFile);
        float width = image.getWidth();
        float height = image.getHeight();
        PDPage page = new PDPage(new PDRectangle(width, height));
        document.addPage(page);
        PDImageXObject pdImage = PDImageXObject.createFromFileByContent(imageFile, document);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.drawImage(pdImage, 0, 0);
        }
        File pdfFile = File.createTempFile("image", ".pdf");
        document.save(pdfFile);
        document.close();
        return pdfFile;
    }
}