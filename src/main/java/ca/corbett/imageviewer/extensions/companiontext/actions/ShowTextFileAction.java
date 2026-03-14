package ca.corbett.imageviewer.extensions.companiontext.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.PopupTextDialog;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.io.TextFileDetector;
import ca.corbett.imageviewer.ui.MainWindow;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An action to show/edit a companion text file associated with an image.
 * The text file to be shown/edited is specified when constructing the action,
 * but can be modified later via setTextFile() if the text file is renamed or moved.
 * Alternatively, you can set the createIfNotPresent option (default false) to
 * have the action create a new empty text file if it does not already exist.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.0.0
 */
public class ShowTextFileAction extends EnhancedAction {

    private static final Logger log = Logger.getLogger(ShowTextFileAction.class.getName());
    private MessageUtil messageUtil;
    private File textFile;
    private boolean createIfNotPresent;
    private boolean fileWasCreated;

    public ShowTextFileAction(File textFile) {
        super("View/Edit companion text file");
        this.textFile = textFile;
        this.createIfNotPresent = false;
        this.fileWasCreated = false;
    }

    public boolean isCreateIfNotPresent() {
        return createIfNotPresent;
    }

    public ShowTextFileAction setCreateIfNotPresent(boolean createIfNotPresent) {
        this.createIfNotPresent = createIfNotPresent;
        return this;
    }

    public File getTextFile() {
        return textFile;
    }

    /**
     * If the associated image file is renamed or moved, you can call this
     * method to update the text file reference.
     */
    public ShowTextFileAction setTextFile(File textFile) {
        this.textFile = textFile;
        return this;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        fileWasCreated = false;
        if (!validateFile()) {
            return; // error already logged/shown
        }

        String text = getFileContents();
        String title = "Text for " + textFile.getName();
        PopupTextDialog dialog = new PopupTextDialog(MainWindow.getInstance(), title, text, true);
        dialog.setReadOnly(false);
        dialog.setVisible(true);

        // Save the results if the user okayed the dialog:
        if (dialog.wasOkayed()) {
            try {
                FileSystemUtil.writeStringToFile(dialog.getText(), textFile);

                // Force a refresh if this is a new file:
                // (this will allow our hyperlink label to get added to the thumb panel)
                // (I'd rather just surgically do that here, because we know only one thumb panel
                //  needs updating, but there's no avenue in the parent application to get to it from here, so...)
                if (fileWasCreated) {
                    MainWindow.getInstance().reload();
                }
            }
            catch (IOException ioe) {
                getMessageUtil().error("Save Error", "Problem saving companion text file: " + ioe.getMessage(), ioe);
            }
        }
    }

    /**
     * Retrieves the file's contents as a string.
     */
    private String getFileContents() {
        try {
            // It's possible the file doesn't exist yet (if createIfNotPresent is true):
            return textFile.exists() ? FileSystemUtil.readFileToString(textFile) : "";
        }
        catch (IOException ioe) {
            log.log(Level.SEVERE, "Error reading companion text file: " + textFile.getAbsolutePath(), ioe);
            return ioe.getMessage() == null ? "" : ioe.getMessage();
        }
    }

    /**
     * Invoked internally to check our given textFile for validity.
     *
     * @return true if we're good, false if some warning or error has been logged.
     */
    private boolean validateFile() {
        // If we were given garbage input, just log a warning and bail:
        if (textFile == null) {
            log.warning("CompanionTextFile: No text file to show.");
            return false;
        }

        // If the file exists: it has to be readable and a valid text file:
        if (textFile.exists()) {
            if (!textFile.isFile() || !textFile.canRead()) {
                log.warning("CompanionTextFile: Invalid or unreadable text file: " + textFile.getAbsolutePath());
                return false;
            }

            try {
                if (!TextFileDetector.isTextFile(textFile)) {
                    getMessageUtil().warning("Not a text file",
                                             "The specified companion text file does not appear to be a text file:\n" +
                                                 textFile.getAbsolutePath());
                    return false;
                }
            }
            catch (IOException ioe) {
                getMessageUtil().error("File error",
                                       "Problem checking companion text file type: " + ioe.getMessage(), ioe);
                return false;
            }
        }

        // If the file doesn't exist, that may or may not be a problem:
        else {
            // If we were not explicitly told to create it, just inform the user and bail:
            if (!createIfNotPresent) {
                getMessageUtil().info("File not found",
                                      "No companion text file found at:\n" + textFile.getAbsolutePath());
                return false;
            }

            // Otherwise, just let it fall through and return true.
            // The file will get created if/when the user okays the dialog.
            //
            // We will, however, note that the file is being created, so we can
            // force a refresh later:
            fileWasCreated = true;
        }

        return true;
    }

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(MainWindow.getInstance(), log);
        }
        return messageUtil;
    }
}
