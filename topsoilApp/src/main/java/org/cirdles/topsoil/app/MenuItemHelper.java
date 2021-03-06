package org.cirdles.topsoil.app;

import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.cirdles.topsoil.app.browse.DesktopWebBrowser;
import org.cirdles.topsoil.app.control.AboutView;
import org.cirdles.topsoil.app.control.dialog.DataImportDialog;
import org.cirdles.topsoil.app.control.dialog.DataTableOptionsDialog;
import org.cirdles.topsoil.app.control.dialog.TopsoilNotification;
import org.cirdles.topsoil.app.control.dialog.wizards.MultipleImportWizard;
import org.cirdles.topsoil.app.data.FXDataTable;
import org.cirdles.topsoil.app.data.TopsoilProject;
import org.cirdles.topsoil.app.file.TopsoilFileUtils;
import org.cirdles.topsoil.app.file.serialization.ProjectSerializer;
import org.cirdles.topsoil.app.file.RecentFiles;
import org.cirdles.topsoil.app.file.FileChoosers;
import org.cirdles.topsoil.app.file.Delimiter;
import org.cirdles.topsoil.app.help.IssueCreator;
import org.cirdles.topsoil.app.help.StandardGitHubIssueCreator;
import org.cirdles.topsoil.app.metadata.TopsoilMetadata;
import org.cirdles.topsoil.app.util.TopsoilException;
import org.cirdles.topsoil.data.DataTable;
import org.cirdles.topsoil.data.DataTemplate;
import org.cirdles.topsoil.data.ExampleData;
import org.cirdles.topsoil.file.parser.DataParser;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class MenuItemHelper {

    private MenuItemHelper() {
        // Prevents instantiation by default constructor
    }

    //**********************************************//
    //                PUBLIC METHODS                //
    //**********************************************//

    /**
     * Handles the opening of a .topsoil file and returns the deserialized {@code TopsoilProject}.
     */
    static void openProject() {
        //Garrett Brenner: add a try-catch to try and open the folder of the most recently used project. If no recent
        //projects exist, open to the root folder
        Path path;
        FileChooser chooser = new FileChoosers().topsoilProjectFileChooser();

        //set to most recent path (no recently used path available is accounted for in
        //RecentFiles.findMRUProjectFolder()
        chooser.setInitialDirectory(RecentFiles.findMRUProjectFolder().toFile());

        path = Paths.get(chooser.showOpenDialog(Topsoil.getPrimaryStage()).toURI());
        openProject(path);
    }

    /**
     * Handles the opening of a .topsoil file and returns the deserialized {@code TopsoilProject}.
     *
     * @param path  project Path
     */
    static void openProject(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Cannot open project at path \"null\".");
        }

        // Check if there is a project already open
        if (ProjectManager.getProject() != null) {
            if (path.equals(ProjectManager.getProjectPath())) {
                TopsoilNotification.info("Already Open", "The project is already open.");
                return;    // this project is already open
            }
            if (! handleOverwriteSaveAndClose()) {
                return;    // overwrite process was cancelled
            }
        }

        openProjectPrivate(path);
    }

    /**
     * Returns a {@code DataTable} for the provided {@code ExampleData}.
     *
     * @param data  ExampleData
     */
    static void openExampleData(ExampleData data) {
        TopsoilProject project = ProjectManager.getProject();
        FXDataTable table = new FXDataTable(data.getDataTable());
        if (project != null) {
            project.addDataTable(table);
        } else {
            project = new TopsoilProject(table);
            ProjectManager.setProject(project);
        }
    }

    /**
     * Attempts to save the project that is currently open. If no project is loaded, it delegates to {@code
     * saveProjectAs()}.
     *
     * @param project   TopsoilProject to save
     * @return          true if successful
     */
    static boolean saveProject(TopsoilProject project) {
        boolean completed = false;
        Path path = ProjectManager.getProjectPath();
        if (path == null) {
            completed = saveProjectAs(project);
        } else {
            try {
                completed = ProjectSerializer.serialize(path, project);
            } catch (IOException e) {
                e.printStackTrace();
                TopsoilNotification.error(
                        "Error",
                        "Unable to save project: " + path.toString()
                );
            }
        }
        return completed;
    }

    /**
     * Saves the provided {@code TopsoilProject} as a .topsoil file. Handles the path selection of the new file and
     * serialization of the project.
     *
     * @param project   TopsoilProject to save
     * @return          true if successful
     */
    static boolean saveProjectAs(TopsoilProject project) {
        FileChooser chooser = FileChoosers.saveTopsoilFile();
        chooser.setInitialDirectory(RecentFiles.findMRUProjectFolder().toFile());
        File file = chooser.showSaveDialog(Topsoil.getPrimaryStage());
        if (file == null) {
            return false;
        }

        boolean completed = false;
        try {
            Path path = file.toPath();
            completed = ProjectSerializer.serialize(path, project);
            ProjectManager.setProjectPath(path);
            RecentFiles.addProjectPath(path);
        } catch (IOException e) {
            e.printStackTrace();
            TopsoilNotification.error(
                    "Error",
                    "Unable to save project: " + file.getName()
            );
        }
        return completed;
    }

    /**
     * Closes the current project, and resets the main content to the initial screen.
     */
    static void closeProject() {
        TopsoilProject project = ProjectManager.getProject();
        if (project == null) {
            ProjectManager.closeProject();
            return;
        }

        ButtonType saveVerification = TopsoilNotification.yesNo(
                "Save Changes",
                "Would you like to save your work?"
        ).orElse(null);
        if (saveVerification == null || saveVerification.equals(ButtonType.CANCEL)) {
            return;   // Dialog cancelled; don't exit
        }

        if (saveVerification == ButtonType.YES) {
            // If a project path is already defined
            if (ProjectManager.getProjectPath() != null) {
                saveProject(project);
                ProjectManager.closeProject();
            } else {
                File file = FileChoosers.saveTopsoilFile().showSaveDialog(Topsoil.getPrimaryStage());
                if (file != null) {
                    try {
                        ProjectSerializer.serialize(file.toPath(), project);
                        ProjectManager.closeProject();
                    } catch (IOException e) {
                        e.printStackTrace();
                        TopsoilNotification.error("Error", "Unable to save project: " + file.getName());
                    }
                }
            }
        } else {
            ProjectManager.closeProject();
        }
    }

    /**
     * Handles the parsing of a {@code DataTable} from some file.
     *
     * @throws IOException  if unable to read the file
     */
    static void importTableFromFile() throws IOException, TopsoilException {
        File file = FileChoosers.openTableFile().showOpenDialog(Topsoil.getPrimaryStage());
        if (file == null) {
            return; // Dialog cancelled
        }

        Path path = Paths.get(file.toURI());
        String fileName = (path.getFileName() != null) ? path.getFileName().toString() : path.toString();
        Delimiter delimiter = TopsoilFileUtils.guessDelimiter(path);
        Map<DataImportDialog.Key, Object> settings =
                new DataImportDialog(fileName, delimiter, Topsoil.getPrimaryStage()).showAndWait().orElse(null);
        if (settings == null) {
            return; // Dialog cancelled
        }

        delimiter = (Delimiter) settings.get(DataImportDialog.Key.DELIMITER);
        DataTemplate template = (DataTemplate) settings.get(DataImportDialog.Key.TEMPLATE);
        if (delimiter == null || template == null) {
            // This shouldn't happen
            throw new TopsoilException("Null setting from DataImportDialog");
        }

        // Check if parsing for the template is supported
        DataParser parser = template.getParser();
        if (! template.isParsingSupported()) {
            TopsoilNotification.info("Unsupported Operation",
                    template + " data parsing is not currently supported.");
            return;
        } else if (parser == null) {
            // This shouldn't happen
            throw new TopsoilException("DataParser instance for template \"" + template + "\" is null, but parsing is supported.");
        }

        FXDataTable table = new FXDataTable(parser.parseDataTable(path, delimiter.asString(), fileName));
        Map<DataTableOptionsDialog.Key, Object> tableOptions = DataTableOptionsDialog.showDialog(table, Topsoil.getPrimaryStage());
        if (tableOptions == null) {
            return; // Dialog cancelled
        }

        // Apply table options
        DataTableOptionsDialog.applySettings(table, tableOptions);

        // Add table to existing project, or create a new project
        if (ProjectManager.getProject() == null) {
            ProjectManager.setProject(new TopsoilProject(table));
        } else {
            ProjectManager.getProject().addDataTable(table);
        }
    }

    /**
     * Handles the parsing of a {@code DataTable} from some {@code String} value.
     *
     * @throws TopsoilException     in cases of application error
     */
    static void importTableFromClipboard() throws TopsoilException {
        String content = Clipboard.getSystemClipboard().getString();
        Delimiter delimiter = TopsoilFileUtils.guessDelimiter(content);

        // Get necessary information from user
        Map<DataImportDialog.Key, Object> settings =
                new DataImportDialog("Clipboard", delimiter, Topsoil.getPrimaryStage()).showAndWait().orElse(null);
        if (settings == null) {
            return; // Dialog cancelled
        }

        // Extract information from dialog settings
        delimiter = (Delimiter) settings.get(DataImportDialog.Key.DELIMITER);   // Overwrite guessed delimiter
        DataTemplate template = (DataTemplate) settings.get(DataImportDialog.Key.TEMPLATE);
        if (delimiter == null || template == null) {
            // This shouldn't happen
            throw new TopsoilException("Null setting from DataImportDialog");
        }

        // Check if parsing for the template is supported
        DataParser parser = template.getParser();
        if (! template.isParsingSupported()) {
            TopsoilNotification.info("Unsupported Operation",
                    template + " data parsing is not currently supported.");
            return;
        } else if (parser == null) {
            // This shouldn't happen
            throw new TopsoilException("DataParser instance for template \"" + template + "\" is null, but parsing is supported.");
        }

        // Check if the String content is able to be parsed into a data table
        if (! parser.isParseableString(content, delimiter.asString())) {
            TopsoilNotification.error("Unreadable Data",
                    "Topsoil could not read table data from the clipboard.");
            return;
        }

        // Parse table; get table options from user
        FXDataTable table = new FXDataTable(parser.parseDataTable(content, delimiter.asString(), "clipboard-content"));
        Map<DataTableOptionsDialog.Key, Object> tableOptions = DataTableOptionsDialog.showDialog(table, Topsoil.getPrimaryStage());
        if (tableOptions == null) {
            return; // Dialog cancelled
        }

        // Apply table options
        DataTableOptionsDialog.applySettings(table, tableOptions);

        // Add table to existing project, or create a new project
        if (ProjectManager.getProject() == null) {
            ProjectManager.setProject(new TopsoilProject(table));
        } else {
            ProjectManager.getProject().addDataTable(table);
        }
    }

    /**
     * Handles the parsing of multiple files into tables.
     */
    static void importMultipleFiles() {
        Map<String, Object> settings = MultipleImportWizard.startWizard();
        if (settings != null) {
            List<FXDataTable> tables = (List<FXDataTable>) settings.get(MultipleImportWizard.Key.TABLES);
            TopsoilProject project = ProjectManager.getProject();
            if (project != null) {
                project.addDataTables(tables.toArray(new FXDataTable[]{}));
            } else {
                ProjectManager.setProject(new TopsoilProject(tables.toArray(new FXDataTable[]{})));
            }
        }
    }

    /**
     * Exports the provided {@code DataTable} to some file.
     *
     * @param table DataTable
     */
    static void exportTableAs(FXDataTable table) {
        DataTemplate template = table.getTemplate();

        if (template.isWritingSupported()) {
            FileChooser chooser = FileChoosers.exportTableFile();
            chooser.setInitialDirectory(RecentFiles.findMRUExportFolder().toFile());
            File file = chooser.showSaveDialog(Topsoil.getPrimaryStage());

            //File file = FileChoosers.exportTableFile().showSaveDialog(Topsoil.getPrimaryStage());
            if (file != null) {
                exportTableAs(file.toPath(), table);
            }
        } else {
            TopsoilNotification.info(
                    "Unsupported Operation",
                    template + " table exporting is currently unsupported.");
        }
    }

    static void editTableOptions() {
        FXDataTable table = MenuUtils.getCurrentDataTable();
        if (table != null) {
            Map<DataTableOptionsDialog.Key, Object> tableOptions = DataTableOptionsDialog.showDialog(table, Topsoil.getPrimaryStage());
            if (tableOptions != null) {
                DataTableOptionsDialog.applySettings(table, tableOptions);
            }
        }
    }

    /**
     * Opens the system default browser to the Topsoil help page.
     */
    static void openOnlineHelp() {
        String TOPSOIL_URL = "http://cirdles.org/projects/topsoil/";
        new DesktopWebBrowser(Desktop.getDesktop()).browse(TOPSOIL_URL);
    }

    /**
     * Opens the system default browser to the "New Issue" form for Topsoil on GitHub, and loads relevant information
     * into the issue body.
     */
    static void openIssueReporter() {
        IssueCreator issueCreator = new StandardGitHubIssueCreator(
                new TopsoilMetadata(),
                System.getProperties(),
                new DesktopWebBrowser(Desktop.getDesktop()),
                new StringBuilder()
        );
        issueCreator.create();
    }

    /**
     * Opens a floating stage containing About information.
     *
     * @param owner Stage
     */
    static void openAboutScreen(Stage owner) {
        AboutView.show(owner);
    }

    //**********************************************//
    //                PRIVATE METHODS               //
    //**********************************************//

    /**
     * Asks the user whether or not to save the current project, saves the project if appropriate, then closes the
     * project.
     */
    private static boolean handleOverwriteSaveAndClose() {
        // Check if there is a project to overwrite
        TopsoilProject project = ProjectManager.getProject();
        if (project == null) {
            return true; // No need to overwrite; already complete
        }

        // Ask the user whether or not to save their changes
        ButtonType buttonType = TopsoilNotification.yesNo(
                "Overwrite",
                "This will overwrite your current data. Save?"
        ).orElse(null);
        if (buttonType == null || buttonType.equals(ButtonType.CANCEL)) {
            return false; // Dialog cancelled; don't overwrite
        }

        if (buttonType.equals(ButtonType.YES)) {
            boolean saveCompleted;
            if (ProjectManager.getProjectPath() == null) {
                saveCompleted = saveProjectAs(project);
            } else {
                saveCompleted = saveProject(project);
            }
            if (! saveCompleted) {
                return false;   // Save cancelled; don't overwrite
            }
        }
        ProjectManager.closeProject();
        return true;
    }

    private static void openProjectPrivate(Path projectPath) {
        try {
            TopsoilProject project = ProjectSerializer.deserialize(projectPath);
            if (project != null) {
                ProjectManager.setProject(project);
                ProjectManager.setProjectPath(projectPath);
                RecentFiles.addProjectPath(projectPath);
            }
        } catch (IOException e) {
            e.printStackTrace();
            TopsoilNotification.error(
                    "Error",
                    "Could not open project file: " + projectPath.toString()
            );
        }
    }

    private static void exportTableAs(Path path, DataTable table) {
        try {
            table.getTemplate().getWriter().writeTableToFile(path, table);
            RecentFiles.addExportPath(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
