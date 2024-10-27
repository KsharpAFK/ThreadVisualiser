package org.example.threadsvisualiser;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.lang.management.ThreadInfo;
import java.net.URL;
import java.util.*;

/**
 * The Controller class is responsible for handling the UI interactions and updating the view
 * based on the data from the Model.
 */
public class Controller implements Initializable {

    private Model model;

    @FXML
    private TextField filterIdTextField;

    @FXML
    private TextField filterNameTextField;

    @FXML
    private TableView<Thread> threadInfoTableView;

    @FXML
    private TableColumn<Thread, String> nameColumn;

    @FXML
    private TableColumn<Thread, Number> idColumn;

    @FXML
    private TableColumn<Thread, String> stateColumn;

    @FXML
    private TableColumn<Thread, Boolean> daemonColumn;

    @FXML
    private TableColumn<Thread, Number> priorityColumn;

    @FXML
    private Label liveThreadCountLabel;

    @FXML
    private Label daemonThreadCountLabel;

    @FXML
    private Label peakThreadCountLabel;

    @FXML
    private ComboBox<String> processListComboBox;

    @FXML
    private ComboBox<String> refreshRateComboBox;

    @FXML
    private TextArea consoleTextArea;

    @FXML
    private CheckBox showDaemonCheckBox;

    @FXML
    private Button findDeadlockButton;

    @FXML
    private Button resetButton;

    @FXML
    private Button processSelectButton;

    @FXML
    private Button interruptThreadButton;

    @FXML
    private Button addThreadButton;


    /**
     * Initializes the controller class. This method is automatically called after the fxml file has been loaded.
     *
     * @param url the location used to resolve relative paths for the root object, or null if the location is not known
     * @param resourceBundle the resources used to localize the root object, or null if the root object was not localized
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        model = new Model();
        model.startRefreshDataScheduler(50);

        initializeTable();
        initializeRefreshRateComboBox();
        bindThreadLabels();
        initializeFilters();
        initializeButtons();
    }

    /**
     * Initializes the table columns and sets up the cell factories for custom rendering.
     */
    public void initializeTable() {
        nameColumn.setCellValueFactory(thread -> new SimpleStringProperty(thread.getValue().getName()));
        idColumn.setCellValueFactory(thread -> new SimpleLongProperty(thread.getValue().threadId()));
        stateColumn.setCellValueFactory(thread -> new SimpleStringProperty(thread.getValue().getState().toString()));
        daemonColumn.setCellValueFactory(thread -> new SimpleBooleanProperty(thread.getValue().isDaemon()));
        priorityColumn.setCellValueFactory(thread -> new SimpleIntegerProperty(thread.getValue().getPriority()));

        stateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setBackground(null);
                } else {
                    setText(item);
                    if (item.equals("RUNNABLE")) {
                        setBackground(new Background(new BackgroundFill(Color.FORESTGREEN, CornerRadii.EMPTY, null)));
                    } else {
                        setBackground(null);
                    }
                }
            }
        });

        threadInfoTableView.setRowFactory(tableView -> new TableRow<>() {
            @Override
            protected void updateItem(Thread item, boolean empty) {
                super.updateItem(item, empty);
                setOnMouseClicked(event -> {
                    if (!isEmpty()) {
                        Thread thread = getTableView().getItems().get(getIndex());
                        model.setSelectedThreadId(thread.threadId());
                        consoleTextArea.setText(model.getDetailedThreadInfo(thread));
                        interruptThreadButton.setDisable(false);

                    }
                });
            }
        });

        threadInfoTableView.setItems(model.getObservableThreadInfoList());
    }

    /**
     * Initializes the refresh rate combo box with predefined values and sets up the action handler.
     */
    public void initializeRefreshRateComboBox() {
        refreshRateComboBox.getItems().addAll("50 ms", "100 ms", "200 ms", "500 ms", "1 s");
        refreshRateComboBox.setValue("50 ms");

        refreshRateComboBox.setOnAction(event -> {
            String rate = refreshRateComboBox.getValue();
            rate = rate.substring(0, rate.indexOf(" ")).trim();
            int rateInt = Integer.parseInt(rate);
            rateInt = rateInt == 1 ? 1000 : rateInt;

            try {
                model.startRefreshDataScheduler(rateInt);
            } catch (Exception e) {
                showErrorAlert(e.getMessage());
            }
        });
    }

    /**
     * Binds the thread count labels to the corresponding properties in the model.
     */
    public void bindThreadLabels() {
        liveThreadCountLabel.textProperty().bind(model.getLiveThreadCount());
        daemonThreadCountLabel.textProperty().bind(model.getDaemonThreadCount());
        peakThreadCountLabel.textProperty().bind(model.getPeakThreadCount());
    }

    /**
     * Initializes the filters for thread filtering based on name, ID, and daemon status.
     */
    public void initializeFilters() {
        filterNameTextField.textProperty().addListener((observable, oldValue, newValue) -> model.setNameFilter(newValue));
        filterIdTextField.textProperty().addListener((observable, oldValue, newValue) -> model.setIdFilter(newValue));
        showDaemonCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> model.setDaemonFilter(newValue));
    }

    /**
     * Initializes the buttons and sets up their action handlers.
     */
    public void initializeButtons() {

        // reset button
        resetButton.setOnAction(event -> {
            filterNameTextField.clear();
            filterIdTextField.clear();
            showDaemonCheckBox.setSelected(false);
        });

        //find deadlock button
        findDeadlockButton.setOnAction(event -> consoleTextArea.setText(model.getDeadlockedThreads()));

        // interrupt thread button
        interruptThreadButton.setDisable(true);

        interruptThreadButton.setOnAction(event -> {
            model.interruptThread();
            interruptThreadButton.setDisable(true);
        });

        // Add thread button
        addThreadButton.setOnAction(event -> {
            try {
                // Load the New Window layout from FXML
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("start_new_thread.fxml"));
                Scene scene = new Scene(fxmlLoader.load());

                // Create a new Stage for the New Window
                Stage newWindow = new Stage();
                newWindow.setScene(scene);

                newWindow.setMinWidth(650);
                newWindow.setMinHeight(450);

                newWindow.initModality(Modality.NONE); // Keeps the main window accessible
                newWindow.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    /**
     * Displays an error alert with the specified message.
     *
     * @param message the error message to display
     */
    public void showErrorAlert(String message) {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.setTitle("Error");
        errorAlert.setHeaderText("Failed to connect to JVM");
        errorAlert.setContentText(message);
        errorAlert.showAndWait();
    }

    /**
     * Shuts down the scheduler in the model.
     */
    public void shutdownScheduler() {
        model.shutdownScheduler();
    }
}