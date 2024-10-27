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

public class NewThreadController implements Initializable {

    private Model model;


    @FXML
    private TextArea runnableTaskTextArea;

    @FXML
    private TextField threadNameTextField;

    @FXML
    private Button startThreadButton;

    @FXML
    private CheckBox newThreadDaemonCheckBox;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        model = new Model();
        // new thread button
        startThreadButton.setOnAction(event -> {

            String threadName = threadNameTextField.getText();
            String task = runnableTaskTextArea.getText();
            boolean isDaemon = newThreadDaemonCheckBox.isSelected();
            model.startNewThread(threadName, task, isDaemon);
        });
    }
}
