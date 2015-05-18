/*
 * Copyright 2014 CIRDLES.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cirdles.topsoil.app;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.cirdles.javafx.CustomVBox;
import org.cirdles.topsoil.app.chart.ChartWindow;
import org.cirdles.topsoil.app.chart.VariableBindingDialog;
import org.cirdles.topsoil.app.utils.DatasetReader;
import org.cirdles.topsoil.app.utils.DatasetWriter;
import org.cirdles.topsoil.app.utils.GetApplicationDirectoryOperation;
import org.cirdles.topsoil.app.utils.TSVDatasetManager;
import org.cirdles.topsoil.app.utils.TSVDatasetReader;
import org.cirdles.topsoil.app.utils.TSVDatasetWriter;
import org.cirdles.topsoil.chart.Chart;
import org.cirdles.topsoil.chart.JavaScriptChart;
import org.cirdles.topsoil.data.Dataset;
import org.cirdles.topsoil.data.DatasetManager;

/**
 * FXML Controller class
 *
 * @author John Zeringue
 */
public class TopsoilMainWindow extends CustomVBox implements Initializable {

    private static final Logger LOGGER
            = Logger.getLogger(TopsoilMainWindow.class.getName());

    private final static Path APPLICATION_DIRECTORY
            = new GetApplicationDirectoryOperation().perform("Topsoil");

    private final static Path DATASETS_DIRECTORY
            = APPLICATION_DIRECTORY.resolve("Data Sets");

    @FXML
    Menu chartsMenu;
    @FXML
    Menu datasetsMenu;
    @FXML
    TabPane dataTableTabPane;

    private DatasetManager datasetManager;

    /**
     * Initializes the controller class.
     *
     * @param url
     * @param resources
     */
    @Override
    public void initialize(URL url, ResourceBundle resources) {
        datasetManager = new TSVDatasetManager(DATASETS_DIRECTORY);
        datasetManager.getDatasets().stream()
                .filter(datasetManager::isOpen)
                .forEach(this::loadDataset);

        reloadDatasetMenu();

        // set the window title to something like "Topsoil [0.3.4]"
        String applicationName = resources.getString("applicationName");
        String applicationVersion = resources.getString("applicationVersion");
        setWindowTitle(
                String.format("%s [%s]", applicationName, applicationVersion));
    }

    Optional<TSVTable> getCurrentTable() {
        return getCurrentTab().map(tab -> (TSVTable) tab.getContent());
    }

    @FXML
    void createDataTable() {
        createTab();
    }

    Tab createTab() {
        Tab dataTableTab = new Tab("Untitled Data");
        dataTableTab.setOnClosed(event -> {
            if (dataTableTab.getContent() instanceof TSVTable) {
                TSVTable table = (TSVTable) dataTableTab.getContent();
            }
        });

        TSVTable dataTable = new TSVTable();
        dataTable.setPlaceholder(new EmptyTablePlaceholder(dataTable));
        dataTableTab.setContent(dataTable);

        dataTableTabPane.getTabs().add(dataTableTab);
        
        // focus on new tab
        SelectionModel<Tab> selectionModel = dataTableTabPane.getSelectionModel();
        selectionModel.select(dataTableTab);

        return dataTableTab;
    }

    @FXML
    void saveDataTable() {
        TextInputDialog textInputDialog = new TextInputDialog();

        textInputDialog.setContentText("Data set name:");
        textInputDialog.showAndWait().ifPresent(datasetName -> {
            Path datasetPath = DATASETS_DIRECTORY
                    .resolve("open")
                    .resolve(datasetName + ".tsv");

            getCurrentTable().ifPresent(table -> table.saveToPath(datasetPath));
            getCurrentTab().ifPresent(tab -> tab.setText(datasetName));
        });

        // reload
        reloadDatasetMenu();
    }

    void createDatasetMenuItem(Dataset dataset) {
        MenuItem datasetMenuItem = dataset.getName()
                .map(MenuItem::new)
                .orElseGet(MenuItem::new);

        datasetMenuItem.setOnAction(event -> {
            TopsoilMainWindow.this.loadDataset(dataset);
        });

        datasetsMenu.getItems().add(datasetMenuItem);
    }

    void reloadDatasetMenu() {
        // allows this method to be called multiple times in the same session
        datasetsMenu.getItems().clear();

        datasetManager.getDatasets().forEach(this::createDatasetMenuItem);
    }

    void loadDataset(Dataset dataset) {
        loadDataset(dataset, createTab());
    }

    void loadDataset(Dataset dataset, Tab tab) {
        Node content = tab.getContent();

        if (content instanceof TSVTable) {
            TSVTable table = (TSVTable) content;

            table.setDataset(dataset);
            datasetManager.open(dataset);

            dataset.getName().ifPresent(name -> {
                tab.setText(name);
            });
        }
    }

    Optional<Tab> getCurrentTab() {
        return Optional.ofNullable(
                dataTableTabPane.getSelectionModel().getSelectedItem());
    }

    void setWindowTitle(String title) {
        // while the code below is long and ugly, anonymous inner classes are
        // necessary (in Java 8) in order to allow the listeners to reference
        // and remove themselves
        // initially lambdas were used (see git history)
        // this keeps TestFX from causing errors

        // create self-removing window listener
        // runs second
        ChangeListener<Window> windowListener = new ChangeListener<Window>() {
            @Override
            public void changed(ObservableValue<? extends Window> observableWindow, Window oldWindow, Window newWindow) {
                if (newWindow instanceof Stage) { // make the cast safe
                    Stage stage = (Stage) newWindow;

                    // actually set the title
                    stage.setTitle(title);
                }

                getScene().windowProperty().removeListener(this);
            }
        };

        // create self-removing scene listener
        // runs first
        ChangeListener<Scene> sceneListener = new ChangeListener<Scene>() {
            @Override
            public void changed(ObservableValue<? extends Scene> observableScene, Scene oldScene, Scene newScene) {
                getScene().windowProperty().addListener(windowListener);
                sceneProperty().removeListener(this);
            }
        };

        sceneProperty().addListener(sceneListener);
    }

    public TSVTable createTable() {
        return (TSVTable) createTab().getContent();
    }

    void importFromFile(Path filePath) {
        TSVTable dataTable = createTable();

        Tools.yesNoPrompt("Does the selected file contain headers?", response -> {
            DatasetReader tableReader = new TSVDatasetReader(response);

            try {
                Dataset dataset = tableReader.read(filePath);
                dataTable.setDataset(dataset);
            } catch (IOException ex) {
                Logger.getLogger(Topsoil.class.getName()).log(Level.SEVERE, null, ex);
            }

            DatasetWriter tableWriter = new TSVDatasetWriter();
            try {
                tableWriter.write(dataTable.getDataset(), Topsoil.LAST_TABLE_PATH);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });
    }

    @FXML
    void importFromFile() {
        FileChooser tsvChooser = new FileChooser();
        tsvChooser.setInitialDirectory(Topsoil.USER_HOME.toFile());
        tsvChooser.setSelectedExtensionFilter(
                new FileChooser.ExtensionFilter("Table Files", "TSV"));

        Optional.ofNullable(tsvChooser.showOpenDialog(getScene().getWindow()))
                .map(File::toPath)
                .ifPresent(this::importFromFile);
    }

    void initializeAndShow(Chart chart, Dataset dataset) {
        new VariableBindingDialog(chart.getVariables(), dataset).showAndWait()
                .ifPresent(variableContext -> {
                    chart.setData(variableContext);

                    Parent chartWindow = new ChartWindow(chart);
                    Scene scene = new Scene(chartWindow, 1200, 800);

                    Stage chartStage = new Stage();
                    chartStage.setScene(scene);
                    chartStage.show();
                });
    }

    private void initializeAndShow(JavaScriptChart javaScriptChart) {
        getCurrentTable().map(TSVTable::getDataset).ifPresent(dataset -> {
            initializeAndShow(javaScriptChart, dataset);
        });
    }

    @FXML
    void createScatterplot() {
        initializeAndShow(new ScatterplotChart());
    }

    @FXML
    void createErrorEllipseChart() {
        initializeAndShow(new ErrorEllipseChart());
    }

    @FXML
    void pasteFromClipboard() {
        getCurrentTable().ifPresent(TSVTable::pasteFromClipboard);
    }

    @FXML
    void emptyTable() {
        getCurrentTable().ifPresent(TSVTable::clear);
    }

}
