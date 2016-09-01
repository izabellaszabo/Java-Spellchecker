package controllers;

import com.dropbox.core.*;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import views.AppGUI;

import javafx.stage.Stage;

import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/**
 * @author Izabella Szabo
 * Created on 24/12/2015
 *
 * Note on Error Messages possibly given to the user:
 * WTEL001
 * WTEL002
 * WTEL003
 * These errors are in the writeToErrorLog method and occur during
 * file creation
 * IOException during file creation
 * IOException while writing to the file
 * respectively.
 */
public class AppController {
    /**
     * Statuses an exception can have.
     */
    public enum Status {
        NOT_REVIEWED("Not Reviewed"),
        DELETED("Deleted"),
        CORRECTED("Corrected"),
        ADDED_PROJ_DIC("Added to the dictionary");

        private String statusText;
        Status(String value){
            statusText = value;
        }

        private String getText(){
            return statusText;
        }
    }

    /**
     * Reason why the word was flagged as an exception.
     */
    private enum ReasonCode {
        BLANK_SPACE("Extra blank space"),
        BRACKETS_ODD("Extra or missing bracket () [] {} <>"),
        QUOTES_ODD("Extra or missing quote \""),
        DUPLICATE("Duplicate word"),
        NOT_CAPITAL("Should be capitalised"),
        NOT_IN_DICT("Not found in dictionaries");

        private String reasonCode;
        ReasonCode(String value){
            reasonCode = value;
        }

        private String getText(){
            return reasonCode;
        }
    }

    /**
     * This is the type or extension of the file being processed.
     */
    private enum FileType {
        PDF, IDML, WORD, OTHER
    }

    private AppGUI gui;

    private Map<String, String> users;
    private Map<String, String> userPermissions;

    private Set<String> availableProjects;      // List of projects available

    private Set<String> genDic;  // The Generic Dictionary file to spellcheck against
    private Map<String, Map<String, String>> bookDic; // The Book/Project-specific Dictionary to spellcheck against

    private String fileSeparator = File.separator;
    private String rootDir = System.getProperty("user.dir") + fileSeparator;
    private boolean isFirstLine;
    private String userName;

    private Map<String,Boolean> spellingChecks;
    private Map<Integer, Map<String, String>> exceptionsList;
    private Map<Integer, Map<String, String>> lastExceptionsChanged;    // Used in undo apply change
    private boolean viewOnlyNotReviewedException;
    private String[] contents;
    private String[] previousContentsForUndo;
    private FileType fileType;
    private String sourceFileLocation;
    private boolean convertedFile;
    private int nextEntryNo;
    private String projectName;
    private String chapterName;
    private String saveRemainingExceptionsTo;
    private boolean newChapter;

    private DbxRequestConfig config;
    private DbxClient client;
    private String DbxWorkingDir;
    private final String NO_CONN_ERR_MSG = "An error occurred during execution. The application requires an active " +
            "connection to the internet and yours appears to be down. \nPlease reset your connection " +
            "and try again.";

    /**
     * Set up the application and initiate the login process.
     */
    public void start(Stage stage) {
        // Empty out temp folder, just in case.
        try {
            FileUtils.cleanDirectory(new File(rootDir + "temp"));
        } catch (IOException ioe) {
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return;
        }

        // Initialise DropBox connection.
        config = new DbxRequestConfig("FolensSpellchecker/1.0", Locale.getDefault().toString());

        gui = new AppGUI(stage);
        gui.setAppController(this);

        if(!initDropBox() || client == null) {
            gui.displayErrorMessage("An error occurred while connecting to DropBox. The application will close.");
            stage.close();
            return;
        }

        if(!populateAvailableProjectList() || !importUsers()) {
            stage.close();
            return;
        }

        gui.drawLoginWindow();
    }

    /**
     * There is a process when connecting to dropBox where the user has to get an authorisation code,
     * which will be used continually to connect to dropBox. Note this process only has to be done once,
     * (or if the user uninstalls and re-installs the application) for the lifetime of the software.
     * The very first time this spellchecker is run, the user is required to do this process, this method
     * then saves that authorisation code and stores it on the local computer. After this every time
     * the spellchecker software is run, the code is read in from the file.
     * @return DBXClient object used to communicate with DropBox
     */
    private boolean initDropBox() {
        final String DROPBOX_APP_KEY = "6b7fjarup3twl4x";
        final String DROPBOX_APP_SECRET = "99sr2wvp66cu2de";
        String DROPBOX_AUTH_CODE;

        DbxAppInfo dbxAppInfo;
        DbxWebAuthNoRedirect dbxWebAuthNoRedirect;
        DbxAuthFinish authFinish;

        File dBAuthCodeFile = new File(rootDir + "dropBox" + fileSeparator + "authCode.txt");

        // Creating a new DropBox Authorisation code if the local file which should contain it doesn't exist.
        if(!dBAuthCodeFile.exists()) {
            dbxAppInfo = new DbxAppInfo(DROPBOX_APP_KEY, DROPBOX_APP_SECRET);
            dbxWebAuthNoRedirect = new DbxWebAuthNoRedirect(
                    config, dbxAppInfo);
            // Start the authorisation process
            String authorizeUrl = dbxWebAuthNoRedirect.start();
            System.out.println("1. Authorize: Go to URL and click Allow : "
                    + authorizeUrl);
            Scanner in = new Scanner(System.in);
            String dropBoxAuthCode = in.nextLine().trim();

            // Create local file containing Authorisation Code.
            try {
                if (!dBAuthCodeFile.createNewFile()) {
                    writeToErrorLog("Error in 'myFile.createNewFile()' while creating DropBox Authorisation Code." +
                                    " FilePath: " + dBAuthCodeFile.getAbsolutePath() +
                                    " . \nOperating System: " + System.getProperty("os.name"),
                            Thread.currentThread().getStackTrace());
                    return false;
                }
            } catch (IOException ioe) {
                writeToErrorLog(ioe.toString(), ioe.getStackTrace());
                return false;
            }

            // Finalise and save the Authorisation Code
            try(FileWriter fWriter = new FileWriter(dBAuthCodeFile)) {
                authFinish = dbxWebAuthNoRedirect.finish(dropBoxAuthCode);
                DROPBOX_AUTH_CODE = authFinish.accessToken;

                fWriter.write("DropBox Authorisation Code:" + DROPBOX_AUTH_CODE);
            } catch (IOException | DbxException e) {
                writeToErrorLog(e.toString(), e.getStackTrace());
                return false;
            }
        } else {
            // Authorisation Code has already been finalised and saved to local computer.
            try(BufferedReader buffReader = new BufferedReader(new FileReader(dBAuthCodeFile))) {
                // Written in the format: "DropBox Authorisation Code:1234567aB"
                DROPBOX_AUTH_CODE = buffReader.readLine().split(":")[1];
            } catch (IOException ioe) {
                writeToErrorLog(ioe.toString(), ioe.getStackTrace());
                return false;
            }
        }

        client = new DbxClient(config, DROPBOX_AUTH_CODE);
        return true;
    }

    /**
     * Download the users file from the DropBox account and then import the user file,
     * which includes the user names and passwords associated with the users.
     */
    private boolean importUsers(){
        this.users = new TreeMap<>();
        String userFilePath = rootDir + "users" + fileSeparator + "users.txt";
        File users = new File(userFilePath);

        // Download file from DropBox
        try(FileOutputStream outputStream = new FileOutputStream(userFilePath)) {
            client.getFile("/users/users.txt", null, outputStream);
        } catch(IOException | DbxException e) {
            writeToErrorLog(e.toString(), e.getStackTrace());
            return false;
        }

        // Read in local file
        String currentLine;
        try(BufferedReader br = new BufferedReader(new FileReader(users))) {
            br.readLine();  // Title Line
            while((currentLine = br.readLine()) != null){
                String[] user = currentLine.split(",");
                this.users.put(user[0], user[1]);       // i.e username, password
            }
        } catch(IOException ioe){
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }
        return true;
    }

    /**
     * Checking whether the user name/password combination exists in the application.
     * @param userName Name of the person who is attempting to login.
     * @param password Password that was entered.
     */
    public boolean validateLoginCredentials(String userName, String password){
        if(userName.equals("") || password.equals("")){
            gui.displayErrorMessage("The user name and password fields must not be blank.");
            return false;
        }

        if(users.containsKey(userName)) { this.userName = userName; }

        if (password.equals(users.get(userName))) {
            gui.closeLoginWindow();
            if(!importUserPermissions()) {
                return false;
            }
            gui.drawMainMenu();
        } else {
            this.userName = "";
            gui.displayErrorMessage("The user name and password you entered don't match.");
            gui.clearPasswordField();
        }
        return true;
    }

    /**
     * Populate the userPermissions map, for the user currently logged in. At this point the users file
     * has already been downloaded from DropBox and this method is reading in the details from the local copy.
     */
    private boolean importUserPermissions() {
        userPermissions = new TreeMap<>();
        File user = new File(rootDir + "users" + fileSeparator + "users.txt");

        String currentLine;
        boolean isFirstLine = true;
        String[] keys = new String[1];      // Dummy initialisation

        try(BufferedReader br = new BufferedReader(new FileReader(user))) {
            while((currentLine = br.readLine()) != null){
                if(isFirstLine) {
                    keys = currentLine.split(",");              // Get the permission names for the user
                    isFirstLine = false;
                }
                String[] userLine = currentLine.split(",");
                if(userLine[0].equals(userName)) {
                    for(int i = 1; i < keys.length; i++){
                        userPermissions.put(keys[i], userLine[i]);      // Place the values into the map.
                    }
                    break;
                }
            }
        } catch(IOException ioe){
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }
        return true;
    }

    /**
     * This list is used on the Main Menu form in the user interface, to identify which project they are now
     * working in. The list of current projects is retrieved from the DropBox account.
     */
    private boolean populateAvailableProjectList() {
        availableProjects = new HashSet<>();

        try {
            DbxEntry.WithChildren listing = client.getMetadataWithChildren("/projects");
            for (DbxEntry child : listing.children) {
                availableProjects.add(child.name);
            }
        } catch (DbxException dbxE) {
            writeToErrorLog(dbxE.toString(), dbxE.getStackTrace());
            return false;
        }
        return true;
    }

    /**
     * These are the spelling checks to perform if non have been specified explicitly.
     */
    private void populateDefaultSpellChecks() {
        spellingChecks = new TreeMap<>();
        spellingChecks.put("Brackets", true);
        spellingChecks.put("Quotes", true);
        spellingChecks.put("Blanks", true);
        spellingChecks.put("Auto remove Blanks", true);
    }

    /**
     * Set the spelling checks to perform.
     * @param checks A map containing the which checks to carry out.
     */
    public void setSpellingChecks(Map<String, Boolean> checks) {
        spellingChecks = checks;
    }

    /**
     * Import basic information about the project into memory.
     * @param projectName The project directory to work in.
     * @return chapters - A list of chapters within the project.
     */
    public String[] importProject(String projectName) {
        this.projectName = projectName;
        DbxWorkingDir = "/projects/" + projectName + "/";

        Set<String> projectChapters = new HashSet<>();

        // Retrieving Chapters from DropBox
        try {
            DbxEntry.WithChildren listing = client.getMetadataWithChildren(DbxWorkingDir + "chapters");
            for (DbxEntry child : listing.children) {
                // Display file name removing the extension
                projectChapters.add(child.name);
            }
        } catch (DbxException dbxE) {
            if(dbxE.getCause().getClass() == UnknownHostException.class ||
                    dbxE.getCause().getClass() == NoRouteToHostException.class) {
                gui.displayErrorMessage(NO_CONN_ERR_MSG);
                gui.blankMainMenuScene(); // Reset the screen
                return null;
            } else {
                writeToErrorLog(dbxE.toString(), dbxE.getStackTrace());
                gui.blankMainMenuScene(); // Reset the screen
                return null;
            }
        }

        // If size == 0, then there are no chapters in the project yet.
        return projectChapters.size() == 0 ? null : projectChapters.toArray(new String[projectChapters.size()]);
    }

    /**
     * Processes the Project File by calling methods which import the file, convert it to a generic
     * file type, spellcheck it and then export the misspelled words to an exception file.
     * @param newChapter Indicates whether or not this is a new project, thus a new Project-specific dictionary
     *                   should be created.
     * @param chapterName The name of the chapter is the name of the text file to spellcheck.
     * @param saveRemExceptionsTo The location of the exception file to be produced with the
     *                              spelling errors, if any.
     * @return boolean Returns false if any errors occurred while processing the file.
     */
    public synchronized boolean processFile(boolean newChapter, String chapterName, String saveRemExceptionsTo) {
        // Since importing the generic file is started on another thread, before this method is called,
        // This method needs to wait until the generic file has been imported.
        while(!convertedFile) {
            try {
                this.wait();
            } catch(InterruptedException ie) {
                writeToErrorLog(ie.toString(), ie.getStackTrace());
                return false;
            }
        }

        this.isFirstLine = true;
        this.newChapter = newChapter;
        this.chapterName = chapterName;
        this.saveRemainingExceptionsTo = saveRemExceptionsTo;

        // Default spelling checks
        if(spellingChecks == null) { populateDefaultSpellChecks(); }

        // Import both generic and project dictionaries.
        if(!importGenericDictionary() || !importProjectDictionary()) { return false; }

        // Actual spellchecking performed here.
        if(!spellCheckFile()) { return false; }

        // Resetting, exception processing begins at the first index.
        isFirstLine = true;
        nextEntryNo = 0;

        return true;
    }

    /**
     * Download from DropBox and read the project dictionary file into memory.
     * @return returns whether or not the dictionary was imported without errors.
     */
    private boolean importProjectDictionary() {
        // TODO should start importing with convert to generic
        // Project Dictionary will be deleted only after the program has finished as some entries may still need to be written to it.
        String dicPath = rootDir + "temp" + fileSeparator + "projectDic.txt";
        File dictionaryFile = new File(dicPath);
        bookDic = new HashMap<>();
        Map<String, String> bookDicDetails;
        String[] currentLineDetails;
        String currentLine;

        // Downloading Project Dictionary from DropBox.
        try {
            // Create temp project dictionary file.
            if (!dictionaryFile.createNewFile()) {
                writeToErrorLog("Error in 'myFile.createNewFile()' while creating temporary project dictionary file." +
                                " FilePath: " + dicPath +
                                " . \nOperating System: " + System.getProperty("os.name"),
                        Thread.currentThread().getStackTrace());
                return false;
            }
        } catch (IOException ioe) {
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }

        try (FileOutputStream outputStream = new FileOutputStream(dictionaryFile, false)) {    // false for boolean append.
        // Read project dictionary file from DropBox
        client.getFile(DbxWorkingDir + "projectDictionary.txt", null, outputStream);
        } catch(IOException | DbxException e) {
            if (e.getCause().getClass() == UnknownHostException.class ||
                    e.getCause().getClass() == NoRouteToHostException.class) {
                gui.displayErrorMessage(NO_CONN_ERR_MSG);
            } else {
                writeToErrorLog(e.toString(), e.getStackTrace());
            }
            return false;
        }

        // Reading in local copy of project dictionary
        try(BufferedReader br = new BufferedReader(new FileReader(dicPath))) {
            while((currentLine = br.readLine()) != null){
                // Title line
                if(isFirstLine){
                    isFirstLine = false;
                    continue;
                }
                currentLineDetails = currentLine.split(",");
                bookDicDetails = new HashMap<>();
                bookDicDetails.put("Added By", currentLineDetails[1]);
                bookDicDetails.put("Last Modified", currentLineDetails[2]);
                bookDic.put(currentLineDetails[0], bookDicDetails);
            }
        } catch(IOException ioe) {
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }
        return true;
    }

    /**
     * Download from DropBox and read the generic dictionary file into memory.
     * @return returns whether or not the dictionary was imported without errors.
     */
    private boolean importGenericDictionary() {
        String genDicLanguage;
        String[] currentLineDetails;
        String currentLine;

        // Get the GenDic associated with the project, by downloading and then reading in the setup file.
        File setupFile = new File(rootDir + "temp" + fileSeparator + "setup.txt");
        // Download setup file from DropBox
        try {
            if(!setupFile.createNewFile()) {
                writeToErrorLog("Error in 'myFile.createNewFile()' while creating temporary project setup file." +
                                " FilePath: " + setupFile.getAbsolutePath() +
                                " . \nOperating System: " + System.getProperty("os.name"),
                        Thread.currentThread().getStackTrace());
                return false;
            }
        } catch(IOException ioe) {
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }

        // Download setup file from DropBox
        try (FileOutputStream outputStream = new FileOutputStream(setupFile)) {
            client.getFile(DbxWorkingDir + "setup.txt", null, outputStream);
        } catch(IOException | DbxException e) {
            if (e.getCause().getClass() == UnknownHostException.class ||
                    e.getCause().getClass() == NoRouteToHostException.class) {
                gui.displayErrorMessage(NO_CONN_ERR_MSG);
            } else {
                writeToErrorLog(e.toString(), e.getStackTrace());
            }
            return false;
        }

        // Read in setup file.
        try(BufferedReader br = new BufferedReader(new FileReader(setupFile))){
            // First Line example - GenDic:English(GB)
            genDicLanguage = br.readLine().split(":")[1];
        } catch(IOException ioe) {
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }

        String dicPath = rootDir + "generalDictionaries" + fileSeparator + genDicLanguage +
                fileSeparator + "Dictionary.txt";
        genDic = new HashSet<>();
        File dictionaryFile = new File(dicPath);
        // Create generic dictionary file if it does not already exist
        if(!dictionaryFile.exists()) {
            try {
                if(!dictionaryFile.createNewFile()) {
                    writeToErrorLog("Error in 'myFile.createNewFile()' while creating" + genDicLanguage +
                                    "generic dictionary file." +
                                    " FilePath: " + dictionaryFile.getAbsolutePath() +
                                    " . \nOperating System: " + System.getProperty("os.name"),
                            Thread.currentThread().getStackTrace());
                    return false;
                }
            } catch(IOException ioe) {
                writeToErrorLog(ioe.toString(), ioe.getStackTrace());
                return false;
            }
        }

        // Downloading generic dictionary from DropBox
        try(FileOutputStream outputStream = new FileOutputStream(dictionaryFile)) {
            client.getFile("/generalDictionaries/" + genDicLanguage + "/Dictionary.txt", null, outputStream);
        } catch(IOException | DbxException e) {
            if (e.getCause().getClass() == UnknownHostException.class ||
                    e.getCause().getClass() == NoRouteToHostException.class) {
                gui.displayErrorMessage(NO_CONN_ERR_MSG);
            } else {
                writeToErrorLog(e.toString(), e.getStackTrace());
            }
            return false;
        }

        // Reading in the Generic Dictionary
        try(BufferedReader br = new BufferedReader(new FileReader(dicPath))) {
            while((currentLine = br.readLine()) != null){
                if(isFirstLine){
                    isFirstLine = false;
                    continue;
                }
                currentLineDetails = currentLine.split(",");
                genDic.add(currentLineDetails[0]);
            }
        } catch(IOException ioe){
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }

        if(!setupFile.delete()) {
            writeToErrorLog("Error in 'myFile.delete()' while deleting temporary project setup file." +
                            " Setup FilePath: " + setupFile.getAbsolutePath() +
                            " . \nOperating System: " + System.getProperty("os.name"),
                    Thread.currentThread().getStackTrace());
            return false;
        }
        return true;
    }

    /**
     * Covert the Word/PDF/InDesign file to a generic file with .txt extension.
     * @param sourceFileLocation Location of the file to be converted.
     * @return converted Returns whether or not the file was converted to a generic file
     * without errors.
     */
    public synchronized boolean convertToGenericTextFile(String sourceFileLocation) {
        this.sourceFileLocation = sourceFileLocation;
        if(spellingChecks == null) {
            // Default values
            populateDefaultSpellChecks();
        }

        File sourceFile = new File(sourceFileLocation);
        switch(sourceFile.getName().substring(sourceFile.getName().lastIndexOf("."))) {
            case ".doc":
            case ".docx":
                fileType = FileType.WORD;
                break;
            case ".pdf":
                fileType = FileType.PDF;
                break;
            case ".idml":
                fileType = FileType.IDML;
                break;
            default:
                fileType = FileType.OTHER;
        }

        BodyContentHandler contentHandler = new BodyContentHandler(-1); // -1 for unlimited text length
        Metadata metadata = new Metadata();
        ParseContext pContext = new ParseContext();
        //parsing the document using a parser which automatically detects the file format
        Parser parser = new AutoDetectParser();

        // Temp file
        File tempProjectChapter = new File(rootDir + "temp" + fileSeparator + "tempChapter.txt");
        try {
            if (!(tempProjectChapter.exists() && tempProjectChapter.delete()) & !tempProjectChapter.createNewFile()) {
                writeToErrorLog("Error in 'myFile.createNewFile()' while creating temporary chapter file." +
                                " FilePath: " + tempProjectChapter.getAbsolutePath() +
                                " . \nOperating System: " + System.getProperty("os.name"),
                        Thread.currentThread().getStackTrace());
                return false;
            }
        } catch (IOException ioe) {
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }

        // Reading in the file.
        try (FileInputStream inputStream = new FileInputStream(sourceFile);
            FileWriter fStream = new FileWriter(tempProjectChapter, false);
            BufferedWriter out = new BufferedWriter(fStream)) {
            parser.parse(inputStream, contentHandler, metadata, pContext);
            contents = contentHandler.toString().split("\\r\\n|[\\r\\n]");  // An array element per line.

            // Writing to generic temp text file
            out.newLine();
            for(String s : contents) {
                out.newLine();
                out.write(s);
            }
        } catch (IOException | TikaException | SAXException e) {
            writeToErrorLog(e.toString(), e.getStackTrace());
            return false;
        }

        convertedFile = true;
        this.notifyAll(); // Spellchecking can commence

        return true;
    }

    /**
     * This method is the central method in the entire application. It is the
     * function which actually spellchecks the file.
     *
     * It reads the file paragraph at a time. Then looping through each word it
     * checks the following and writes the word to the exception file if any of
     * these conditions are met:
     * o More than one blank space between words
     * o Even number of brackets and quotes
     * o Duplicate word
     * o The first letter of a sentence is not capitalised
     * o The word cannot be found in either the General Dictionary nor the Project Specific Dictionary
     * @return boolean Returns whether or not the file was successfully spellchecked without errors.
     */
    private boolean spellCheckFile() {
        if(genDic == null || bookDic == null){ return false; }
        Map<String, Integer> occurrences = new HashMap<>();
        boolean bracketsFlag = false;
        boolean quotesFlag = false;
        isFirstLine = true;

        if(spellingChecks.get("Auto remove Blanks")) {
            for(int i = 0; i < contents.length; i ++) {
                contents[i] = contents[i].replaceAll("[ \\t]+", " ");
            }
        }

        for(String s : contents) {
            String[] words = s.split(" ?((?<!\\G)((?<=[^\\p{Punct}])(?=\\p{Punct})|\\b))|\\s+ ?", 0);

            for (int i = 0; i < words.length; i++) {
                String previousWord = "";
                if (i != 0)
                    previousWord = words[i - 1];
                String currentWord = words[i];

                if (!occurrences.containsKey(currentWord)) {
                    occurrences.put(currentWord, 1);
                } else {
                    int previousCount = occurrences.get(currentWord);
                    occurrences.replace(currentWord, previousCount, ++previousCount);
                }

                int occurrence = occurrences.get(currentWord);

                // Brackets {} [] () <>
                if (spellingChecks.get("Brackets") && Pattern.matches("<|>|\\{|}|[|]|\\(|\\)", currentWord))
                    bracketsFlag = !bracketsFlag;

                // Quotes "
                if (spellingChecks.get("Quotes") && Pattern.matches("\"", currentWord))
                    quotesFlag = !quotesFlag;

                // Ignore punctuation (including: '--' '."') and digits
                if (Pattern.matches("\\p{Punct}+|\\d+|'", currentWord))
                    continue;

                // Extra blank space
                if (currentWord.equals(" ") && spellingChecks.get("Blanks") && !spellingChecks.get("Auto remove Blanks")) {
                    addToExceptionList(" ", ReasonCode.BLANK_SPACE, occurrence);
                    continue;
                }

                // Duplicate word
                if (currentWord.equals(previousWord)) {
                    addToExceptionList(currentWord, ReasonCode.DUPLICATE, occurrence);
                    continue;
                }

                // If first letter of sentence, InitCap is expected
                String firstLetter = currentWord.substring(0, 1);
                String capitalFirstLetter = firstLetter.toUpperCase();
                if ((Pattern.matches("\\.|\\?|!", previousWord) || previousWord.equals(""))
                        && !firstLetter.equals(capitalFirstLetter)) {
                    addToExceptionList(currentWord, ReasonCode.NOT_CAPITAL, occurrence);
                    continue;
                }

                // Misspelled word
                if (!genDic.contains(currentWord) && !bookDic.containsKey(currentWord)) {
                    if (!Pattern.matches("\\.|\\?|!|\"", previousWord) &&
                            !(genDic.contains(currentWord.toLowerCase()) ||
                                    bookDic.containsKey(currentWord.toLowerCase()))) {
                        addToExceptionList(currentWord, ReasonCode.NOT_IN_DICT, occurrence);
                    }
                }
            }
        }

        if(spellingChecks.get("Brackets") && bracketsFlag)
            addToExceptionList("", ReasonCode.BRACKETS_ODD, 0);

        if(spellingChecks.get("Quotes") && quotesFlag)
            addToExceptionList("", ReasonCode.QUOTES_ODD, 0);

        return true;
    }

    /**
     * Add an entry to the Exceptions List.
     * @param misspelledWord A word which was not found in either the General Dictionary or
     *                      the Project Dictionary.
     * @param reasonCode The reason why the word is flagged as incorrect.
     * @param occurrence The occurrence of the misspelled word in that paragraph.
     */
    private void addToExceptionList(String misspelledWord, ReasonCode reasonCode, int occurrence){
        Map<String, String> exceptionDetails = new TreeMap<>();

        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());

        if(isFirstLine){
            exceptionsList = new TreeMap<>();
            lastExceptionsChanged = new TreeMap<>();
            isFirstLine = false;
        }

        exceptionDetails.put("Incorrect Word", misspelledWord);
        exceptionDetails.put("Reason", reasonCode.getText());
        exceptionDetails.put("Occurrence", "" + occurrence);
        exceptionDetails.put("Created Date", date);
        exceptionDetails.put("Created By", userName);
        exceptionDetails.put("Status", Status.NOT_REVIEWED.getText());
        exceptionDetails.put("Replacement", " ");
        exceptionDetails.put("Replacement Authorised By", " ");
        exceptionDetails.put("Last Modified", date);

        exceptionsList.put(nextEntryNo++, exceptionDetails);
    }

    /**
     * Returns a list of all available Projects.
     * @return String[] containing the names of the available Projects.
     */
    public String[] getProjectItems() {
        ArrayList<String> projectItems = new ArrayList<>(availableProjects);
        Collections.sort(projectItems);
        return projectItems.toArray(new String[projectItems.size()]);
    }

    /**
     * Write any errors that occur during execution of the program for future debugging.
     * @param errorMessage Text representation of the error which occurred.
     * @param stackTrace The stack trace of the error, to aid debugging.
     */
    private boolean writeToErrorLog(String errorMessage, StackTraceElement[] stackTrace) {
        File errorFile = new File(rootDir + "temp" + fileSeparator + "error_log.txt");
        if(!errorFile.exists()){
            try {
                if(!errorFile.createNewFile()){
                    gui.displayErrorMessage("Fatal Error WTEL001 - The error log could not be created. " +
                            "\n\nIf the error persists, please contact the developer.");
                    return false;
                }
            } catch(IOException ioe){
                gui.displayErrorMessage("Fatal Error WTEL002 - Exception occurred during error log file creation. " +
                        "\n" + ioe.getMessage() +
                        "\n\nIf the error persists, please contact the developer.");
                return false;
            }
        }

        try(FileWriter fStream = new FileWriter(errorFile, true);
            BufferedWriter out = new BufferedWriter(fStream);
            FileOutputStream fOStream = new FileOutputStream(errorFile)){
            // Only download file from DropBox if it exists
            String dBoxFileName = "/projects/" + projectName + "/errorLogs/error_log_" +
                    new SimpleDateFormat("ddMMMyyyy").format(new Date()) + ".txt";
            if(client.getMetadata(dBoxFileName) != null) {
                client.getFile(dBoxFileName, null, fOStream);
            }
            // Write to local copy.
            out.write("Date: " + new SimpleDateFormat("ddMMMyyyy HH:mm:ss").format(new Date()));
            out.newLine();
            out.write(errorMessage + " \nDetails: ");
            out.write("User: " + userName + "\n");
            for(StackTraceElement stackTraceElement:stackTrace){
                out.write(stackTraceElement.toString());
                out.newLine();
            }
            out.write("--------------- END ---------------");
            out.newLine();
            out.newLine();
        } catch (IOException | DbxException e) {
            if(e.getCause().getClass() == UnknownHostException.class ||
                    e.getCause().getClass() == NoRouteToHostException.class) {
                gui.displayErrorMessage(NO_CONN_ERR_MSG);
            } else {
                gui.displayErrorMessage("Fatal Error WTEL003 - Writing to the error log failed. " +
                        "\n" + e.getMessage() +
                        "\n\nIf the error persists, please contact the developer.");
            }
            return false;
        }
        return true;
    }

    /**
     * Return the userPermissions map detailing what actions the user can perform.
     * @return map containing user permissions.
     */
    public Map<String, String> getUserPermissions() {
        return userPermissions;
    }

    /**
     * Apply the corrections (delete, corrected spelling, added to project dictionary) to the
     * exception list.
     * @param correctWord The word to be substituted.
     * @return Whether or not the word was successfully replaced in the source file.
     */
    public boolean applyChangeToException(String correctWord, Status status) {
        // Add to project dictionary
        if(status == Status.ADDED_PROJ_DIC) {
            if (bookDic.containsKey(correctWord)) {
                return false;
            }

            Map<String, String> projectDicEntryDetails = new HashMap<>();
            projectDicEntryDetails.put("Added By", userName);
            projectDicEntryDetails.put("Last Modified DateTime",
                    new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));

            bookDic.put(correctWord, projectDicEntryDetails);
        }

        // Apply change to every other exception of the same type.
        String currentWord = exceptionsList.get(nextEntryNo).get("Incorrect Word");
        String exceptionReason = exceptionsList.get(nextEntryNo).get("Reason");

        for(int i = 0; i < exceptionsList.size(); i++) {
            Map<String, String> details = exceptionsList.get(i);
            if(details.get("Incorrect Word").equals(currentWord) &&
                    details.get("Reason").equals(exceptionReason)) {
                // Saving the original entry, in case of an undo.
                lastExceptionsChanged.put(i, details);
                // Finding similar occurrences
                details.put("Incorrect Word", correctWord);
                details.put("Status", status.getText());
            }
        }

        previousContentsForUndo = contents;

        // Update contents
        for(int i = 0; i < contents.length; i++) {
            if (exceptionReason.equals(ReasonCode.BLANK_SPACE.getText())) {
                contents[i] = contents[i].replaceAll("[ \\t]+", " ");
            } else if (exceptionReason.equals(ReasonCode.DUPLICATE.getText())) {
                contents[i] = contents[i].replaceAll("\\s" + currentWord + "\\s" + currentWord + "\\s", " " + currentWord + " ");
            } else if (!correctWord.equals("")) {
                // replace word
                contents[i] = contents[i].replaceAll("\\s" + currentWord + "\\s", " " + correctWord + " ");
            } else {
                // delete word
                contents[i] = contents[i].replaceAll("\\s" + currentWord + "\\s", " ");
            }
        }

        return true;
    }

    /**
     * Undo the last change made by overwriting the new changed entries with the original entries.
     */
    public void unApplyLastChange() {
        exceptionsList.putAll(lastExceptionsChanged);
        contents = previousContentsForUndo;
    }

    /**
     * Return the contents of the project file being processed. To be displayed in the TextArea on the GUI.
     * @return string - the contents of the project file.
     */
    public String getContents() {
        StringBuilder concatContentsString = new StringBuilder();
        int noOfParagraphs = contents.length;
        for(int i = 0; i < noOfParagraphs; i++) {
            concatContentsString.append(contents[i]);
            if(i + 1 != noOfParagraphs) {
                concatContentsString.append("\n");
            }
        }
        return concatContentsString.toString();
    }

    /**
     * Returns the total number of exceptions found in the document.
     * @return total number of exceptions.
     */
    public int getExceptionErrorCount() {
        return exceptionsList.size();
    }

    /**
     * Return the entry no. of the current error which is being processed.
     * @return current error no.
     */
    public String getCurrentErrorNo() {
        return "" + (nextEntryNo + 1);        // The nextEntryNo var is zero based by default
    }

    /**
     * Returns the number of times the incorrect word is in the exceptionsList.
     * @return number of occurrences of the incorrect word.
     */
    public String getSimilarErrorCount() {
        Collection<Map<String, String>> allExceptionDetails = exceptionsList.values();
        String currentWord = exceptionsList.get(nextEntryNo).get("Incorrect Word");
        String currentErrorDesc = exceptionsList.get(nextEntryNo).get("Reason");
        int count = 0;

        for(Map<String, String> details : allExceptionDetails) {
            if(details.get("Incorrect Word").equals(currentWord) &&
                    details.get("Reason").equals(currentErrorDesc)) {
                count ++;
            }
        }
        return ""  + count;
    }

    /**
     * Sets whether to view all exceptions or only those for which no corrective measure has been taken.
     */
    public void setViewOnlyNotReviewedException(boolean b) {
        viewOnlyNotReviewedException = b;
    }

    /**
     * Return the next/previous exception to be processed.
     * @param next - whether to return the next exception or the previous.
     * @return a map which contains the details of the exception.
     */
    public Map<String, String> getExceptionDetails(boolean next) {
        if(next) {
            nextEntryNo++;
            if(isFirstLine) {
                isFirstLine = false;
                nextEntryNo = 0;
            }
        } else {
            nextEntryNo--;
            if(nextEntryNo < 0) {
                nextEntryNo = 0;
            }
        }

        if(viewOnlyNotReviewedException && !exceptionsList.get(nextEntryNo).get("Status").equals(Status.NOT_REVIEWED.getText())) {
            return getExceptionDetails(next);
        } else {
            return exceptionsList.get(nextEntryNo);
        }
    }

    /**
     * Returns a list of the entries in the project dictionary.
     * @return a map containing the list of project dictionary entries.
     */
    public Map<String, Map<String,String>> getProjectDicEntries() {
        return bookDic;
    }

    /**
     * Called when the user is completely finished processing the current project chapter.
     * This method ties all loose ends. It writes all changes back to the web. It also
     * produces an exception file of not-reviewed exceptions for the user.
     * @return successfully saved all files.
     */
    public boolean finishProcessing() {
        // Upload original chapter file for historical reference.
        String tempFilesPath = rootDir + "temp" + fileSeparator + "tempChapter.txt";
        // Write this original temp file to DropBox for historical analysis
        try(FileInputStream fileInputStream = new FileInputStream(tempFilesPath)) {
            // Upload chapter to DropBox
            if(newChapter) {
                // Create folder per chapter
                client.createFolder(DbxWorkingDir + "Chapters/" + chapterName);
            }

            // Filename is the date - for historical analysis each chapter is written to its chapter name folder.
            client.uploadFile(DbxWorkingDir + "Chapters/" + chapterName + "/" +
                    new SimpleDateFormat("ddMMMyyyy HH:mm:ss").format(new Date()) + ".txt", DbxWriteMode.add(),
                    new File(tempFilesPath).length(), fileInputStream);
        } catch(IOException | DbxException e) {
            if(e.getCause().getClass() == UnknownHostException.class ||
                    e.getCause().getClass() == NoRouteToHostException.class) {
                gui.displayErrorMessage(NO_CONN_ERR_MSG);
            }
            writeToErrorLog(e.toString(), e.getStackTrace());
            return false;
        }

        // Delete temp chapter file
        if(!new File(tempFilesPath).delete()){
            writeToErrorLog("Error in 'myFile.delete()' while deleting temporary chapter file." +
                            " Setup FilePath: " + tempFilesPath +
                            " . \nOperating System: " + System.getProperty("os.name"),
                    Thread.currentThread().getStackTrace());
            return false;
        }

        // Write changes back to original file.
        writeChangesToOriginalFile();

        // Finalising exceptions and writing remaining ones to a file, which the user can print.
        tempFilesPath = rootDir + "temp" + fileSeparator + "exceptions.txt";
        File tempFile = new File(tempFilesPath);
        // Create temp exceptions file
        try {
            if(!tempFile.createNewFile()){
                writeToErrorLog("Error in 'myFile.createNewFile()' while creating temporary Exceptions file." +
                                " FilePath: " + tempFilesPath +
                                " . \nOperating System: " + System.getProperty("os.name"),
                        Thread.currentThread().getStackTrace());
                return false;
            }
        } catch(IOException ioe) {
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }

        // Write exceptions to temp file.
        try(FileWriter fStream = new FileWriter(tempFilesPath);
            BufferedWriter out = new BufferedWriter(fStream)) {
            out.write(projectName + ", " + chapterName + ", Created: " + userName + ", " +
                    new SimpleDateFormat("dd MMM yyyy HH:mm:ss").format(new Date()));
            out.newLine();
            out.write("Incorrect Word, Reason Code, Occurrences");
            out.newLine();
            // Looping through exceptions, only writing those which have not yet been reviewed.
            for(int i = 0; i < exceptionsList.size(); i++) {
                Map<String, String> details = exceptionsList.get(i);
                if(details.get("Status").equals(Status.NOT_REVIEWED.getText())) {
                    // Need this in the call to getSimilarErrorCount
                    nextEntryNo = i;
                    out.write(details.get("Incorrect Word") + ", " + details.get("Reason") + ", "
                            + getSimilarErrorCount());
                    out.newLine();
                }
            }
        } catch(IOException ioe){
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }

        // Copy temp exceptions file to location specified by the user in the Main Menu.
        try {
            Files.copy(tempFile.toPath(), new File(saveRemainingExceptionsTo + fileSeparator +
                    chapterName + " Exceptions.txt").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ioe) {
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }

        // Delete temp remaining Exceptions file.
        if(!new File(tempFilesPath).delete()){
            writeToErrorLog("Error in 'myFile.delete()' while deleting temporary remaining exceptions file." +
                            " Setup FilePath: " + tempFilesPath +
                            " . \nOperating System: " + System.getProperty("os.name"),
                    Thread.currentThread().getStackTrace());
            return false;
        }

        // Writing changes to project Dictionary.
        tempFilesPath = rootDir + "temp" + fileSeparator + "projectDic.txt";
        try(FileWriter fStream = new FileWriter(tempFilesPath);
            BufferedWriter out = new BufferedWriter(fStream)) {
            out.write("Correct Word, Added By, Added At");
            out.newLine();
            // Looping through ProjectDic object writing words to projectDic file.
            for(String key : bookDic.keySet()) {
                Map<String, String> details = bookDic.get(key);
                out.write(key + ", " + details.get("Added By") + ", " + details.get("Last Modified"));
                out.newLine();
            }
        } catch(IOException ioe){
            writeToErrorLog(ioe.toString(), ioe.getStackTrace());
            return false;
        }

        // Upload project dictionary file to dropBox.
        try(FileInputStream fileInputStream = new FileInputStream(tempFilesPath)) {
            // Upload project Dictionary to DropBox
            client.uploadFile(DbxWorkingDir + "projectDictionary.txt", DbxWriteMode.force(),        // Test
                    new File(tempFilesPath).length(), fileInputStream);
        } catch(IOException | DbxException e) {
            if(e.getCause().getClass() == UnknownHostException.class ||
                    e.getCause().getClass() == NoRouteToHostException.class) {
                gui.displayErrorMessage(NO_CONN_ERR_MSG);
            }
            writeToErrorLog(e.toString(), e.getStackTrace());
            return false;
        }

        // Delete project dictionary file
        if(!new File(tempFilesPath).delete()){
            writeToErrorLog("Error in 'myFile.delete()' while deleting temporary project dictionary file." +
                            " Setup FilePath: " + tempFilesPath +
                            " . \nOperating System: " + System.getProperty("os.name"),
                    Thread.currentThread().getStackTrace());
            return false;
        }

        // Uploading errorLog if one exist.
        tempFilesPath = rootDir + "temp" + fileSeparator + "error_log.txt";
        if(new File(tempFilesPath).exists()) {
            try(FileInputStream fileInputStream = new FileInputStream(tempFilesPath)) {
                // Upload errorLog to DropBox
                client.uploadFile(DbxWorkingDir + "errorLogs/error_log_" + new SimpleDateFormat("ddMMMyyyy") + ".txt",
                        DbxWriteMode.add(), new File(tempFilesPath).length(), fileInputStream);
            } catch(IOException | DbxException e) {
                if(e.getCause().getClass() == UnknownHostException.class ||
                        e.getCause().getClass() == NoRouteToHostException.class) {
                    gui.displayErrorMessage(NO_CONN_ERR_MSG);
                }
                writeToErrorLog(e.toString(), e.getStackTrace());
                return false;
            }

            // Delete errorLog file
            if(!new File(tempFilesPath).delete()){
                writeToErrorLog("Error in 'myFile.delete()' while deleting temporary error log file." +
                                " Setup FilePath: " + tempFilesPath +
                                " . \nOperating System: " + System.getProperty("os.name"),
                        Thread.currentThread().getStackTrace());
                return false;
            }
        }
        return true;
    }

    private void writeChangesToOriginalFile() {
        // Put in original folder.
        // Create new document.
        // Delete old.
        switch (fileType) {
            case IDML:
                writeToIDML();
                break;
            case PDF:
                writeToPDF();
                break;
            case WORD:
            case OTHER:
                writeToWord();
                break;
        }

    }

    private void writeToIDML() {
        // Waiting in IDMLLib Library.
    }

    /**
     * Create a new PDF Document and write the contents of the file being spellchecked into this the PDF file.
     * Then save it, replacing the old version.
     */
    private void writeToPDF() {
        try {
            PDDocument doc = new PDDocument();
            PDPage page = new PDPage();

            doc.addPage(page);

            PDPageContentStream content = new PDPageContentStream(doc, page);

            content.beginText();
            for(String s : contents) {
                content.showText(s);
                content.newLine();
                content.endText();
            }

            content.close();
            doc.save(sourceFileLocation);
            doc.close();
        } catch(IOException | COSVisitorException e) {
            writeToErrorLog(e.toString(), e.getStackTrace());
        }
    }

    /**
     * Create a new Word Document and write the contents of the file being spellchecked into this the Word Doc.
     * Then save it, replacing the old version.
     */
    private void writeToWord() {
        XWPFDocument document = new XWPFDocument();

        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();

        int maxParagraphs = contents.length;

        for(int i = 0; i < maxParagraphs; i++) {
            run.setText(contents[i]);
            if((i + 1) != maxParagraphs) {
                run.addBreak();
            }
        }

        try(FileOutputStream output = new FileOutputStream(sourceFileLocation)) {
            document.write(output);
        } catch (Exception e) {
            writeToErrorLog(e.toString(), e.getStackTrace());
        }
    }
}