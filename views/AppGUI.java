package views;

import controllers.AppController;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.*;

import java.io.File;
import java.util.*;

/**
 * @author Izabella Szabo
 * Created on 24/12/2015
 */
public class AppGUI{
    private Stage primaryStage;
    private Stage progressStage;

    private AppController appController;

    private PasswordField pwBox;

    private String appFontType = "Arial";
    private String appBgColor = "#fff";
    private String appColor = "#036b03";
    private String appStyle = "-fx-background-color: " + appBgColor + "; -fx-font-family: " + appFontType + ";" +
            "-fx-font-color: #000;";
    private String menuBarStyle = "-fx-border-color: transparent transparent " + appColor +
            " transparent; -fx-border-width: 2px; -fx-padding: 0px 0px 2px 0px;";
    private String menuStyle = "-fx-margin-left: 0px; -fx-margin-top: 0px; -fx-margin-bottom:0px;";
    //private String menuItemStyle;

    /**
     * Class constructor.
     * @param stage is the main thread of JavaFX application. This stage needs to
     *              stay in play(stay opened) for the application to continue execution.
     */
    public AppGUI(Stage stage){
        primaryStage = stage;
    }

    /**
     * Making sure only one instance of an App Controller class is used.
     * @param appController an instance of App Controller class to be used.
     */
    public void setAppController(AppController appController){
        this.appController = appController;
    }

    /**
     * Drawing the initial login window.
     */
    public void drawLoginWindow(){
        primaryStage.setTitle("Login to Spellchecker");

        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20, 20, 20, 20));
        gridPane.setStyle(appStyle);

        Text sceneTitle = new Text("Login to Folens Spellchecker");
        sceneTitle.setFont(Font.font(appFontType, 18));
        gridPane.add(sceneTitle, 0, 0, 2, 1);

        Label userName = new Label("User Name:");
        gridPane.add(userName, 0, 1);

        TextField userNameText = new TextField();
        gridPane.add(userNameText, 1, 1);

        Label pwLbl = new Label("Password:");
        gridPane.add(pwLbl, 0, 2);

        pwBox = new PasswordField();
        gridPane.add(pwBox, 1, 2);

        Button loginBtn = new Button("Login");
        loginBtn.setDefaultButton(true);
        loginBtn.setOnAction(e -> {
        if(!appController.validateLoginCredentials(userNameText.getText(), pwBox.getText())) {
            displayErrorMessage("An error occurred while validating your login details. Make sure " +
                    "you've entered both a user name and password, then try again.");
        }});
        HBox hBox = new HBox(10);
        hBox.setAlignment(Pos.BOTTOM_RIGHT);
        hBox.getChildren().add(loginBtn);
        gridPane.add(hBox, 1, 3);

        Scene loginScene = new Scene(gridPane, 350, 300);
        primaryStage.setScene(loginScene);
        primaryStage.show();
    }

    /**
     * Close the login window after successfully logging in.
     */
    public void closeLoginWindow(){
        primaryStage.hide();
    }

    /**
     * Confirm message template which is used multiple times within the application.
     * @param message The message to be displayed.
     * @return confirmed.
     */
    private boolean displayConfirmMessage(String message){
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm");
        alert.setHeaderText(message);

        ButtonType buttonTypeYes = new ButtonType("Yes");
        ButtonType buttonTypeCancel = new ButtonType("No");

        alert.getButtonTypes().setAll(buttonTypeYes, buttonTypeCancel);

        Optional<ButtonType> result = alert.showAndWait();
        if(result.isPresent() && result.get() == buttonTypeYes) {
            alert.close();
            return true;
        } else {
            alert.close();
            return false;
        }
    }

    /**
     * The Main Menu is where the project, file path, chapter name is specified.
     */
    public void drawMainMenu() {
        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(30, 10, 10, 10));
        gridPane.setStyle(appStyle);

        Text sceneTitle = new Text("Spellcheck Project");
        sceneTitle.setFont(Font.font(appFontType, 18));
        gridPane.add(sceneTitle, 0, 0, 2, 1);

        // Project Name dropdown menu
        Label genDicLbl = new Label("Project Name:");
        gridPane.add(genDicLbl, 0, 1);

        String[] projectItems = appController.getProjectItems();
        ObservableList<String> projectItemOptions = FXCollections.observableArrayList();
        projectItemOptions.addAll(projectItems);
        ComboBox<String> projectChoiceCmbBox = new ComboBox<>(projectItemOptions);
        projectChoiceCmbBox.setEditable(true);
        projectChoiceCmbBox.setVisibleRowCount(7);
        projectChoiceCmbBox.getEditor().textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                handleSearchByKey(projectChoiceCmbBox, projectItemOptions, oldValue, newValue);
                projectChoiceCmbBox.show();
            }
        });
        gridPane.add(projectChoiceCmbBox, 1, 1);

        // Project Chapter file picker
        Label chapterFileLbl = new Label("Chapter File:");
        gridPane.add(chapterFileLbl, 0, 2);

        final TextField chapterFilePath = new TextField();
        gridPane.add(chapterFilePath, 1, 2);

        final FileChooser fileChooser = new FileChooser();

        // Read in the file here to improve performance
        Button browseChapterFileBtn = new Button("Browse...");
        browseChapterFileBtn.setOnAction(e -> {
            Stage fileChooserStage = new Stage();
             File file = fileChooser.showOpenDialog(fileChooserStage);
            if (file != null) {
                chapterFilePath.setText(file.getAbsolutePath());
            }

            // Start importing the project file in a separate thread now to improve performance.
            Task<String> importConvertToGeneric = new Task<String>() {
                @Override
                public String call() throws InterruptedException {
                    if(file != null) {
                        return "" + appController.convertToGenericTextFile(file.getAbsolutePath());
                    }
                    return "";
                }
            };

            importConvertToGeneric.setOnSucceeded(event -> {
                if(importConvertToGeneric.getValue().equals("false")){
                    displayErrorMessage("An error occurred while importing and converting the Chapter File. \nPlease check the error log.");
                }
            });

            Thread thread = new Thread(importConvertToGeneric);
            thread.setDaemon(true);
            thread.start();
        });
        gridPane.add(browseChapterFileBtn, 2, 2);

        // Project(Book) Chapters
        Label chapterNameLbl = new Label("Chapter Name:");
        gridPane.add(chapterNameLbl, 0, 3);

        ObservableList<String> chapterOptions = FXCollections.observableArrayList();
        ComboBox<String> chapterChoiceCmbBox = new ComboBox<>(chapterOptions);
        chapterChoiceCmbBox.setVisible(false);
        gridPane.add(chapterChoiceCmbBox, 0, 4, 2, 1);

        Label newChapterNameLbl = new Label("Title: ");
        newChapterNameLbl.setVisible(false);
        HBox hBox = new HBox(10);
        hBox.setAlignment(Pos.CENTER_RIGHT);
        hBox.getChildren().add(newChapterNameLbl);
        gridPane.add(hBox, 0, 4);

        TextField newChapterName = new TextField();
        newChapterName.setVisible(false);
        gridPane.add(newChapterName, 1, 4);

        final ToggleGroup bookDicRadioGroup = new ToggleGroup();
        GridPane chapterRadioBtnGrid = new GridPane();
        chapterRadioBtnGrid.setHgap(30);

        RadioButton newChapterRadioBtn = new RadioButton("New");
        newChapterRadioBtn.setToggleGroup(bookDicRadioGroup);
        newChapterRadioBtn.setSelected(false);
        newChapterRadioBtn.setOnAction(e -> {
            chapterChoiceCmbBox.setVisible(false);
            newChapterNameLbl.setVisible(true);
            newChapterName.setVisible(true);
            newChapterName.requestFocus();
        });
        chapterRadioBtnGrid.add(newChapterRadioBtn, 0, 0);

        RadioButton selectChapterRadioBtn = new RadioButton("Select");
        selectChapterRadioBtn.setToggleGroup(bookDicRadioGroup);
        selectChapterRadioBtn.setOnAction(e -> {
            chapterChoiceCmbBox.setVisible(true);
            chapterChoiceCmbBox.requestFocus();
            newChapterNameLbl.setVisible(false);
            newChapterName.setVisible(false);
        });
        chapterRadioBtnGrid.add(selectChapterRadioBtn, 1, 0);

        gridPane.add(chapterRadioBtnGrid, 1, 3);

        // Setting a listener on the ProjectName to start importing the project when it is chosen.
        // Importing the project means displaying the list of available projects.
        projectChoiceCmbBox.getEditor().focusedProperty().addListener((observable, oldValue, newValue) -> {
            if(!projectChoiceCmbBox.isFocused() && !projectChoiceCmbBox.getEditor().getText().equals("")){
                if(!projectChoiceCmbBox.getItems().contains(projectChoiceCmbBox.getEditor().getText())) {
                    displayErrorMessage("Invalid Project Name value. Please choose from the available options.");
                } else {
                    String[] options = appController.importProject(
                            projectChoiceCmbBox.getSelectionModel().getSelectedItem());
                    if(options == null) {
                        // No chapters in this project yet
                        selectChapterRadioBtn.setDisable(true);
                    } else {
                        chapterOptions.setAll(options);
                        chapterChoiceCmbBox.setItems(chapterOptions);
                        chapterChoiceCmbBox.setValue(options[0]);
                    }
                }
            }
        });

        // Exception File save location
        Label projectDirLoc = new Label("Save Project Directory To:");
        gridPane.add(projectDirLoc, 0, 5);

        final TextField projectDirPath = new TextField();
        gridPane.add(projectDirPath, 1, 5);

        final DirectoryChooser dirChooser = new DirectoryChooser();

        Button browseProjectDirLoc = new Button("Browse...");
        browseProjectDirLoc.setOnAction(e -> {
            Stage dirChooserStage = new Stage();
            final File selectedDirectory =
                    dirChooser.showDialog(dirChooserStage);
            if (selectedDirectory != null) {
                projectDirPath.setText(selectedDirectory.getAbsolutePath());
            }
        });
        gridPane.add(browseProjectDirLoc, 2, 5);

        // Spellcheck Button
        Button spellCheckBtn = new Button("Spellcheck File");
        spellCheckBtn.setOnAction(e -> {
            if(projectChoiceCmbBox.getValue() == null){
                displayErrorMessage("The Project Name cannot be blank.");
                return;
            }

            if(chapterFilePath.getText().equals("")){
                displayErrorMessage("The Chapter File Path cannot be blank.");
                return;
            } else {
                File chapterFile = new File(chapterFilePath.getText());
                if(!chapterFile.exists()){
                    displayErrorMessage("The Chapter File entered is not a valid file.");
                    return;
                }
            }

            String chapterNameText;
            if(newChapterRadioBtn.isSelected()){
                chapterNameText = newChapterName.getText();
            } else if (selectChapterRadioBtn.isSelected()) {
                chapterNameText = chapterChoiceCmbBox.getValue();
            } else {
                // Neither selected
                displayErrorMessage("You must select a Chapter Name option.");
                return;
            }

            if(chapterNameText.equals("")){
                displayErrorMessage("The Chapter Name must not be blank.");
                return;
            }

            if(projectDirPath.getText().equals("")) {
                displayErrorMessage("The Save to Directory location cannot be blank.");
                return;
            } else {
                File projectDirFile = new File(projectDirPath.getText());
                if(!projectDirFile.isDirectory()){
                    displayErrorMessage("The Save to Directory location must be an existing directory.");
                    return;
                }
            }

            openProgressBar("Spellchecking file...");

            // Processing has to be run on a separate thread, else the GUI will become unresponsive
            Task<String> task = new Task<String>() {
                @Override
                public String call() throws InterruptedException {
                    return "" + appController.processFile(newChapterRadioBtn.isSelected(),
                            chapterNameText, projectDirPath.getText());
                }
            };

            task.setOnSucceeded(event -> {
                closeProgressBar();
                if(task.getValue().equals("false")){
                    displayErrorMessage("An error occurred while spellchecking the file. " +
                            "\nPlease check the error log or try again.");
                    blankMainMenuScene();
                } else {
                    drawMainWindow();
                }
            });

            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        });

        HBox hBox1 = new HBox(10);
        hBox1.setAlignment(Pos.BOTTOM_RIGHT);
        hBox1.getChildren().add(spellCheckBtn);
        gridPane.add(hBox1, 1, 7);

        VBox mainMenuContainerVBox = new VBox();
        mainMenuContainerVBox.setStyle(appStyle);
        Scene mainMenuScene = new Scene(mainMenuContainerVBox, 500, 350);

        MenuBar menuBar = new MenuBar();
        menuBar.setStyle(appStyle + menuBarStyle);

        Menu menuFile = new Menu("File");
        menuFile.setStyle(menuStyle);
        Menu menuOptions = new Menu("Options");
        menuOptions.setStyle(menuStyle);

        MenuItem menuItemLogout = new MenuItem("Logout");
        menuItemLogout.setOnAction(e -> {
            if(displayConfirmMessage("Are you sure you want to logout and exit?")) {
                primaryStage.close();
            }
        });
        menuFile.getItems().add(menuItemLogout);

        MenuItem menuItemSpellingChecks = new MenuItem("Checks to Perform");
        menuItemSpellingChecks.setOnAction(e -> appController.setSpellingChecks(displaySpellingChecks()));
        menuOptions.getItems().add(menuItemSpellingChecks);

        menuBar.getMenus().addAll(menuFile, menuOptions);

        ((VBox) mainMenuScene.getRoot()).getChildren().addAll(menuBar, gridPane);

        primaryStage = new Stage();
        primaryStage.setTitle("Main Menu");
        primaryStage.setScene(mainMenuScene);
        primaryStage.show();
    }

    /**
     * This function is used to assist the user in searching for a Book Dictionary.
     * When the user presses enter after typing something into the combo box containing
     * the values for the Book Dictionary, this method will return which entry to highlight
     * in the combo box.
     * @param list The ComboBox containing the list
     * @param entries The list of all available items for Book Dictionary.
     * @param oldVal Old value which was previously typed into the textField.
     * @param newVal New value which has just been typed into the textField
     */
    private void handleSearchByKey(ComboBox<String> list, ObservableList<String> entries, String oldVal, String newVal) {
        // If the number of characters in the text box is less than last time
        // it must be because the user pressed delete
        // TODO or newVal.length == 0
        if (oldVal != null && (newVal.length() < oldVal.length())) {
            // Restore the lists original set of entries
            // and start from the beginning
            list.setItems(entries);
        }

        // Change to upper case so that case is not an issue
        newVal = newVal.toUpperCase();

        // Filter out the entries that don't contain the entered text
        ObservableList<String> subEntries = FXCollections.observableArrayList();
        for ( Object entry: list.getItems() ) {
            String entryText = (String)entry;
            if ( entryText.toUpperCase().contains(newVal) ) {
                subEntries.add(entryText);
            }
        }
        list.setItems(subEntries);
    }

    /**
     * The spell checks to be made are specified here.
     * @return a map containing which checks to make.
     */
    private Map<String, Boolean> displaySpellingChecks() {
        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setHgap(30);
        gridPane.setVgap(20);
        gridPane.setPadding(new Insets(10, 10, 10, 10));
        gridPane.setStyle(appStyle);

        Label headingLbl = new Label("Perform the following checks:");
        headingLbl.setFont(Font.font(appFontType, FontWeight.BOLD, 12));
        gridPane.add(headingLbl, 0, 0);

        Label bracketsLbl = new Label("Brackets are closed: {} [] () <>");
        gridPane.add(bracketsLbl, 0, 1);
        CheckBox bracketsChkBox = new CheckBox();
        bracketsChkBox.setSelected(true);
        gridPane.add(bracketsChkBox, 1, 1);

        Label quotesLbl = new Label("Quotes are closed: \" \" ");
        gridPane.add(quotesLbl, 0, 2);
        CheckBox quotesChkBox = new CheckBox();
        quotesChkBox.setSelected(true);
        gridPane.add(quotesChkBox, 1, 2);

        Label blanksLbl = new Label("Extra blank spaces:");
        gridPane.add(blanksLbl, 0, 3);
        CheckBox blanksChkBox = new CheckBox();
        blanksChkBox.setSelected(true);
        gridPane.add(blanksChkBox, 1, 3);

        Label autoRemoveBlankSpaceLbl = new Label("Automatically remove blank spaces:");
        gridPane.add(autoRemoveBlankSpaceLbl, 0, 4);
        CheckBox autoRemoveBlanksChkBox = new CheckBox();
        autoRemoveBlanksChkBox.setSelected(true);
        gridPane.add(autoRemoveBlanksChkBox, 1, 4);

        BorderPane containerBdrPne = new BorderPane(gridPane);
        containerBdrPne.setPadding(new Insets(20, 20, 20, 20));
        containerBdrPne.setStyle(appStyle);

        Scene spellingChecksScene = new Scene(containerBdrPne, 300, 250);

        Stage stage = new Stage();
        stage.setTitle("Optional Checks");
        stage.setScene(spellingChecksScene);
        stage.setResizable(false);
        stage.show();

        Map<String, Boolean> checks = new HashMap<>();
        stage.setOnCloseRequest(e -> {
            checks.put("Brackets", bracketsChkBox.isSelected());
            checks.put("Quotes", quotesChkBox.isSelected());
            checks.put("Blanks", blanksChkBox.isSelected());
            checks.put("Auto remove Blanks", autoRemoveBlanksChkBox.isSelected());
        });
        return checks;
    }

    /**
     * The progress spinning wheel is displayed while the file is being spellchecked.
     */
    private void openProgressBar(String message){
        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setHgap(10);
        gridPane.setVgap(20);
        gridPane.setPadding(new Insets(10, 10, 10, 10));
        gridPane.setStyle(appStyle);

        Label progressMsgLbl = new Label(message);
        gridPane.add(progressMsgLbl, 0, 0);

        ProgressIndicator pinWheel = new ProgressIndicator();
        gridPane.add(pinWheel, 0, 1);

        Scene progressScene = new Scene(gridPane, 300, 150);

        progressStage = new Stage(StageStyle.DECORATED);
        progressStage.setResizable(false);
        progressStage.setTitle("Working...");
        progressStage.setScene(progressScene);
        progressStage.show();
    }

    /**
     * Close the progress bar.
     */
    private void closeProgressBar(){
        progressStage.close();
    }

    /**
     * The main window is where the exceptions, or incorrect words, are processed by the user.
     * The text of the file is displayed on the left, with details, options, and processing on
     * the right.
     */
    private void drawMainWindow(){
        Stage mainWindowStage = new Stage();
        BorderPane mainContainerBdrPne = new BorderPane();

        // Menu Bar
        MenuBar menuBar = new MenuBar();
        menuBar.setStyle(appStyle + menuBarStyle);

        Menu menuFile = new Menu("File");
        menuFile.setStyle(menuStyle);
        Menu menuOptions = new Menu("Options");
        menuOptions.setStyle(menuStyle);

        MenuItem finishProcessingMenuItem = new MenuItem("Finish Spellchecking");
        finishProcessingMenuItem.setOnAction(e -> {
            if(displayConfirmMessage("Are you sure you want to save this project and exit?")) {
                mainWindowStage.hide();
                openProgressBar("Finishing up.");
                // Processing has to be run on a separate thread, else the GUI will become unresponsive
                Task<String> task = new Task<String>() {
                    @Override
                    public String call() throws InterruptedException {
                        return "" + appController.finishProcessing();
                    }
                };

                task.setOnSucceeded(event -> {
                    closeProgressBar();
                    if(task.getValue().equals("false")){
                        displayErrorMessage("An error occurred while saving the files. \nPlease check the error log.");
                    } else {
                        drawMainMenu();
                    }
                });

                Thread thread = new Thread(task);
                thread.setDaemon(true);
                thread.start();
            }
        });

        menuFile.getItems().add(finishProcessingMenuItem);

        // Options
        CheckMenuItem viewOnlyNotReviewedChkMI = new CheckMenuItem("View only not reviewed entries");
        viewOnlyNotReviewedChkMI.selectedProperty().addListener(e -> {
            appController.setViewOnlyNotReviewedException(viewOnlyNotReviewedChkMI.isSelected());
        });
        menuOptions.getItems().add(viewOnlyNotReviewedChkMI);

        MenuItem viewProjectDicEntriesMenuItem = new MenuItem("View Project Dictionary");
        viewProjectDicEntriesMenuItem.setOnAction(e -> showProjectDicEntries());
        menuFile.getItems().add(viewProjectDicEntriesMenuItem);

        menuBar.getMenus().addAll(menuFile, menuOptions);

        mainContainerBdrPne.setTop(menuBar);

        SplitPane splitPane = new SplitPane();
        splitPane.setStyle(appStyle);
        splitPane.setDividerPositions(0.90);

        Map<String, String> userPermissions = appController.getUserPermissions();

        // Left side of the split pane, the chapter content
        BorderPane leftSideBdrPne = new BorderPane();
        leftSideBdrPne.setStyle(appStyle);

        TextArea contentTxtArea = new TextArea(appController.getContents());
        contentTxtArea.setStyle(appStyle + "-fx-focus-color: transparent;");
        contentTxtArea.setEditable(false);
        contentTxtArea.setWrapText(true);

        leftSideBdrPne.setCenter(contentTxtArea);

        GridPane statsGrid = new GridPane();
        statsGrid.setStyle(appStyle);
        statsGrid.setPadding(new Insets(5, 20, 5, 20));
        statsGrid.setHgap(3);
        statsGrid.setVgap(3);
        ColumnConstraints statsCol15 = new ColumnConstraints();
        statsCol15.setPercentWidth(15);
        ColumnConstraints statsCol17 = new ColumnConstraints();
        statsCol15.setPercentWidth(17);
        statsGrid.getColumnConstraints().addAll(statsCol17, statsCol15, statsCol17, statsCol15, statsCol17, statsCol15);

        Label totalErrorsLbl = new Label("Total Errors: ");
        GridPane.setHalignment(totalErrorsLbl, HPos.RIGHT);
        statsGrid.add(totalErrorsLbl, 0, 0);

        Label totalErrorsCntLbl = new Label("" + appController.getExceptionErrorCount());
        GridPane.setHalignment(totalErrorsCntLbl, HPos.LEFT);
        statsGrid.add(totalErrorsCntLbl, 1, 0);

        Label currentErrorLbl = new Label("Currently at error: ");
        GridPane.setHalignment(currentErrorLbl, HPos.RIGHT);
        statsGrid.add(currentErrorLbl, 2, 0);

        Label currentErrorNoLbl = new Label(appController.getCurrentErrorNo());
        GridPane.setHalignment(currentErrorNoLbl, HPos.LEFT);
        statsGrid.add(currentErrorNoLbl, 3, 0);

        Label similarErrorsLbl = new Label("Times this error occurs: ");
        GridPane.setHalignment(similarErrorsLbl, HPos.RIGHT);
        statsGrid.add(similarErrorsLbl, 4, 0);

        Label similarErrorsNoLbl = new Label(appController.getSimilarErrorCount());
        GridPane.setHalignment(similarErrorsNoLbl, HPos.LEFT);
        statsGrid.add(similarErrorsNoLbl, 5, 0);

        leftSideBdrPne.setBottom(statsGrid);

        // Right side of the split pane, all the controls to process the exceptions file
        BorderPane rightSideBdrPne = new BorderPane();
        rightSideBdrPne.setStyle(appStyle);
        rightSideBdrPne.setPadding(new Insets(20, 10, 20, 10));

        // Top Section
        GridPane topGridPane = new GridPane();
        topGridPane.setStyle(appStyle);
        topGridPane.setPadding(new Insets(0, 5, 5, 5));
        topGridPane.setVgap(10);
        ColumnConstraints col100 = new ColumnConstraints();
        col100.setPercentWidth(100);
        topGridPane.getColumnConstraints().add(col100);

        // Undo Message
        GridPane undoMessageGridPaneContainer = new GridPane();
        undoMessageGridPaneContainer.setStyle(appStyle);
        undoMessageGridPaneContainer.setPadding(new Insets(0, 5, 10, 5));
        undoMessageGridPaneContainer.setVisible(false);
        ColumnConstraints col80 = new ColumnConstraints();
        col80.setPercentWidth(80);
        ColumnConstraints col20 = new ColumnConstraints();
        col20.setPercentWidth(20);
        undoMessageGridPaneContainer.getColumnConstraints().addAll(col80, col20);

        Label undoMessageLbl = new Label();
        GridPane.setHalignment(undoMessageLbl, HPos.LEFT);
        GridPane.setValignment(undoMessageLbl, VPos.CENTER);
        undoMessageGridPaneContainer.add(undoMessageLbl, 0, 0);

        Hyperlink undoHyperLink = new Hyperlink("UNDO");
        GridPane.setHalignment(undoHyperLink, HPos.RIGHT);
        GridPane.setValignment(undoHyperLink, VPos.CENTER);
        undoHyperLink.setOnAction(e -> {
            appController.unApplyLastChange();
            contentTxtArea.setText(appController.getContents());
            displaySuccessMessage("Un-applied last change.");
        });
        undoMessageGridPaneContainer.add(undoHyperLink, 1, 0);

        topGridPane.add(undoMessageGridPaneContainer, 0, 0);

        Button deleteWordBtn = new Button("Delete word");
        deleteWordBtn.setTextAlignment(TextAlignment.CENTER);
        deleteWordBtn.setMinWidth(100);
        deleteWordBtn.setMinHeight(50);
        GridPane.setHalignment(deleteWordBtn, HPos.CENTER);
        topGridPane.add(deleteWordBtn, 0, 1);

        Button applyChangesBtn = new Button("Apply changes");
        applyChangesBtn.setTextAlignment(TextAlignment.CENTER);
        applyChangesBtn.setMinWidth(100);
        applyChangesBtn.setMinHeight(50);
        GridPane.setHalignment(applyChangesBtn, HPos.CENTER);
        topGridPane.add(applyChangesBtn, 0, 2);


        Button addToBookDicBtn = new Button("Add to \nProject Dictionary");
        addToBookDicBtn.setTextAlignment(TextAlignment.CENTER);
        addToBookDicBtn.setMinWidth(100);
        addToBookDicBtn.setMinHeight(50);
        GridPane.setHalignment(addToBookDicBtn, HPos.CENTER);
        addToBookDicBtn.setVisible(userPermissions.get("Create Project Dic Entry").equals("true"));
        topGridPane.add(addToBookDicBtn, 0, 3);

        rightSideBdrPne.setTop(topGridPane);

        // Middle Section
        Map<String, String> exceptionDetails = appController.getExceptionDetails(true);

        GridPane middlePane = new GridPane();
        middlePane.setVgap(10);
        middlePane.setHgap(10);
        middlePane.setStyle(appStyle + " -fx-font-size: 12");
        middlePane.setAlignment(Pos.CENTER);

        TextField incorrectWord = new TextField(exceptionDetails.get("Incorrect Word"));
        GridPane.setHalignment(incorrectWord, HPos.CENTER);
        incorrectWord.requestFocus();
        middlePane.add(incorrectWord, 0, 0, 2, 1);

        // Exception details
        Label reasonLbl = new Label("Error type:");
        middlePane.add(reasonLbl, 0, 3);

        Label exceptionReasonLbl = new Label(exceptionDetails.get("Reason"));
        middlePane.add(exceptionReasonLbl, 1, 3);

        Label statusLbl = new Label("Status:");
        middlePane.add(statusLbl, 0, 4);

        Label exceptionStatusLbl = new Label(exceptionDetails.get("Status"));
        middlePane.add(exceptionStatusLbl, 1, 4);

        rightSideBdrPne.setCenter(middlePane);

        // Bottom Section
        GridPane bottomGridPane = new GridPane();
        bottomGridPane.setStyle(appStyle);
        bottomGridPane.setHgap(5);
        ColumnConstraints columnConstraint50 = new ColumnConstraints();
        columnConstraint50.setPercentWidth(50);
        bottomGridPane.getColumnConstraints().addAll(columnConstraint50,columnConstraint50);

        Button previousBtn = new Button("Previous");
        previousBtn.setMinWidth(100);
        GridPane.setHalignment(previousBtn, HPos.RIGHT);
        bottomGridPane.add(previousBtn, 0, 0);

        Button finishButton = new Button("Finish");
        finishButton.setVisible(false);
        finishButton.setMinWidth(100);
        GridPane.setHalignment(finishButton, HPos.LEFT);
        bottomGridPane.add(finishButton, 1, 0);

        Button nextBtn = new Button("Next");
        nextBtn.setOnAction(e -> {
            contentTxtArea.setText(appController.getContents());
            Map<String, String> exception = appController.getExceptionDetails(true);
            incorrectWord.setText(exception.get("Incorrect Word"));
            exceptionReasonLbl.setText(exception.get("Reason"));
            exceptionStatusLbl.setText(exception.get("Status"));
            if(!exceptionStatusLbl.getText().equals("Not Reviewed")) {
                exceptionStatusLbl.setStyle("-fx-fill: #24a51f");
            }
            currentErrorNoLbl.setText(appController.getCurrentErrorNo());
            if(currentErrorNoLbl.getText().equals(totalErrorsCntLbl.getText())) {
                nextBtn.setVisible(false);
                finishButton.setVisible(true);
                return;
            }
            similarErrorsNoLbl.setText(appController.getSimilarErrorCount());
            int i = 1;
            int position = 0;
            int occurrence = Integer.parseInt(exception.get("Occurrence"));
            // The outer loop is needed to get an accurate 'position' value.
            // The inner loop's split is the split regex used when spellchecking. This loop is needed
            // to get the correct word and occurrence to be highlighted.
            for(String s : contentTxtArea.getText().split(" ")) {
                position += s.length() + 1;
                for(String innerString : s.split(" ?((?<!\\G)((?<=[^\\p{Punct}])(?=\\p{Punct})|\\b))|\\s+ ?", 0)) {
                    if (innerString.equals(incorrectWord.getText())) {
                        if (occurrence == i) {
                            contentTxtArea.selectRange(position - s.length() - 1, position - 1);
                        }
                        i++;
                    }
                }
            }
            if(Integer.parseInt(currentErrorNoLbl.getText()) == Integer.parseInt(totalErrorsCntLbl.getText())) {
                nextBtn.setVisible(false);
                finishButton.setVisible(true);
            }
        });
        nextBtn.setMinWidth(100);
        GridPane.setHalignment(nextBtn, HPos.LEFT);
        bottomGridPane.add(nextBtn, 1, 0);

        previousBtn.setOnAction(e -> {
            contentTxtArea.setText(appController.getContents());
            Map<String, String> exception = appController.getExceptionDetails(false);
            incorrectWord.setText(exception.get("Incorrect Word"));
            exceptionReasonLbl.setText(exception.get("Reason"));
            exceptionStatusLbl.setText(exception.get("Status"));
            currentErrorNoLbl.setText(appController.getCurrentErrorNo());
            similarErrorsNoLbl.setText(appController.getSimilarErrorCount());
            int i = 1;
            int position = 0;
            int occurrence = Integer.parseInt(exception.get("Occurrence"));
            // The outer loop is needed to get an accurate 'position' value.
            // The inner loop's split is the split regex used when spellchecking. This loop is needed
            // to get the correct word and occurrence to be highlighted.
            for(String s : contentTxtArea.getText().split(" ")) {
                position += s.length() + 1;
                for(String innerString : s.split(" ?((?<!\\G)((?<=[^\\p{Punct}])(?=\\p{Punct})|\\b))|\\s+ ?", 0)) {
                    if (innerString.equals(incorrectWord.getText())) {
                        if (occurrence == i) {
                            contentTxtArea.selectRange(position - s.length() - 1, position - 1);
                        }
                        i++;
                    }
                }
            }
            if(Integer.parseInt(currentErrorNoLbl.getText()) < Integer.parseInt(totalErrorsCntLbl.getText())) {
                nextBtn.setVisible(true);
                finishButton.setVisible(false);
            }
        });

        // Setting OnActions of word correction buttons at top of right side.
        deleteWordBtn.setOnAction(e -> {
            if(appController.applyChangeToException("", AppController.Status.DELETED)) {     // Replacing a word with blank essentially deletes it.
                undoMessageLbl.setText("Deleted phrase.");
                undoMessageGridPaneContainer.setVisible(true);
                nextBtn.fire();
            } else {
                displayErrorMessage("An error occurred while deleting.");
            }
        });

        applyChangesBtn.setOnAction(e -> {
            if(appController.applyChangeToException(incorrectWord.getText(), AppController.Status.CORRECTED)) {
                undoMessageLbl.setText("Successfully updated the spelling.");
                undoMessageGridPaneContainer.setVisible(true);
                nextBtn.fire();
            } else {
                displayErrorMessage("An error occurred while updating the source file with the corrected phrase.");
            }
        });

        addToBookDicBtn.setOnAction(e -> {
            if(!displayConfirmMessage("Are you sure you want to add \"" + incorrectWord.getText() + "\" to the Project Dictionary?")) {
                return;
            }
            if(appController.applyChangeToException(incorrectWord.getText(), AppController.Status.ADDED_PROJ_DIC)) {
                undoMessageLbl.setText("Successfully added to Project Dictionary.");
                undoMessageGridPaneContainer.setVisible(true);
                nextBtn.fire();
            } else {
                displayErrorMessage("The word " + incorrectWord.getText() + " already exists in the project dictionary.");
            }
        });

        rightSideBdrPne.setBottom(bottomGridPane);

        splitPane.getItems().addAll(leftSideBdrPne, rightSideBdrPne);
        mainContainerBdrPne.setCenter(splitPane);

        // Setting up the stage
        mainWindowStage.setMinWidth(600);
        mainWindowStage.setMinHeight(400);
        mainWindowStage.setMaximized(true);

        mainWindowStage.setOnCloseRequest(e -> {
            if(displayConfirmMessage("Are you sure you want to exit? All changes since last save will be lost.")) {
                mainWindowStage.close();
            }
        });

        finishButton.setOnAction(e -> {
            mainWindowStage.hide();
            openProgressBar("Finishing up.");
            // Processing has to be run on a separate thread, else the GUI will become unresponsive
            Task<String> task = new Task<String>() {
                @Override
                public String call() throws InterruptedException {
                    return "" + appController.finishProcessing();
                }
            };

            task.setOnSucceeded(event -> {
                closeProgressBar();
                if(task.getValue().equals("false")){
                    displayErrorMessage("An error occurred while saving the files. \nPlease check the error log.");
                } else {
                    drawMainMenu();
                }
            });

            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        });

        Scene mainWindowScene = new Scene(mainContainerBdrPne, mainWindowStage.getWidth(), mainWindowStage.getHeight());

        mainWindowStage.setScene(mainWindowScene);

        primaryStage.hide();
        mainWindowStage.show();
    }

    private void showProjectDicEntries() {
        Map<String, Map<String, String>> projectDicEntries = appController.getProjectDicEntries();
        Set<String> projectDicKeys = projectDicEntries.keySet();
        Set<String> numberedKeys = new TreeSet<>();
        ListView<String> rowsListView = new ListView<>();
        ObservableList<String> rowsList = FXCollections.observableArrayList();

        int i = 1;
        for(String key : projectDicKeys) {
            numberedKeys.add(i++ + ". " + key);
        }

        rowsList.addAll(numberedKeys);
        rowsListView.setItems(rowsList);
        rowsListView.setCellFactory(p -> {
            Label leadLbl = new Label();
            Tooltip tooltip = new Tooltip();
            return new ListCell<String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    if(item != null) {
                        String details = "Added By: " +
                                projectDicEntries.get(item.substring(item.indexOf('.') + 2)).get("Added By") +
                                ", Last Modified Date: " +
                                projectDicEntries.get(item.substring(item.indexOf('.') + 2)).get("Last Modified DateTime");
                        leadLbl.setText(details);
                        setText(item);
                        tooltip.setText(details);
                        setTooltip(tooltip);
                    }
                }
            };
        });

        StackPane root = new StackPane();
        root.getChildren().add(rowsListView);
        Stage projectDicStage = new Stage();
        projectDicStage.setTitle("View Project Dictionary Entries");
        projectDicStage.setScene(new Scene(root, 400, 450));
        projectDicStage.show();
    }

    /**
     * Error message template used multiple times throughout the application.
     * @param message The message to be displayed.
     */
    public void displayErrorMessage(String message) {
        Alert popupError = new Alert(Alert.AlertType.ERROR);
        popupError.setTitle("Error");
        popupError.setHeaderText(message);
        popupError.initStyle(StageStyle.DECORATED);
        popupError.setResizable(false);
        popupError.getDialogPane().setPrefSize(450, 150);
        popupError.showAndWait();
    }

    /**
     * Success message template used multiple times throughout the application.
     * @param message The message to be displayed.
     */
    private void displaySuccessMessage(String message) {
        Alert popupSuccess = new Alert(Alert.AlertType.INFORMATION);
        popupSuccess.setTitle("Success");
        popupSuccess.setHeaderText(message);
        popupSuccess.initStyle(StageStyle.DECORATED);
        popupSuccess.setResizable(false);
        popupSuccess.getDialogPane().setPrefSize(400, 80);
        popupSuccess.showAndWait();
    }

    /**
     * When logging in, the method blanks the password field to make it easier for the
     * user to re-enter their password.
     */
    public void clearPasswordField(){
        pwBox.setText("");
    }

    // Reset the fields
    public void blankMainMenuScene() {
        primaryStage.hide();
        drawMainMenu();
    }
}
