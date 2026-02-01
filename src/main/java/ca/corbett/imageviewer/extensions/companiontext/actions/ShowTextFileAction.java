package ca.corbett.imageviewer.extensions.companiontext.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.PopupTextDialog;
import ca.corbett.extras.io.FileSystemUtil;
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
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.0.0
 */
public class ShowTextFileAction extends EnhancedAction {

    private static final Logger log = Logger.getLogger(ShowTextFileAction.class.getName());
    private MessageUtil messageUtil;
    private File textFile;

    public ShowTextFileAction(File textFile) {
        super("View/Edit companion text file");
        this.textFile = textFile;
    }

    public File getTextFile() {
        return textFile;
    }

    /**
     * If the associated image file is renamed or moved, you can call this
     * method to update the text file reference.
     *
     * @param textFile The new location of our text file.
     */
    public void setTextFile(File textFile) {
        this.textFile = textFile;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (textFile == null || !textFile.exists() || !textFile.isFile()) {
            log.warning("CompanionTextFile: No text file to show.");
            return;
        }

        String text;
        String title = "Text for " + textFile.getName();
        try {
            text = FileSystemUtil.readFileToString(textFile);
        }
        catch (IOException ioe) {
            text = ioe.getMessage();
            log.log(Level.SEVERE, "Error reading companion text file: " + textFile.getAbsolutePath(), ioe);
        }
        PopupTextDialog dialog = new PopupTextDialog(MainWindow.getInstance(), title, text, true);
        dialog.setReadOnly(false);
        dialog.setVisible(true);
        if (dialog.wasOkayed() && !text.equals(dialog.getText())) {
            try {
                FileSystemUtil.writeStringToFile(dialog.getText(), textFile);
            }
            catch (IOException ioe) {
                getMessageUtil().error("Problem saving companion text file: " + ioe.getMessage());
            }
        }
    }

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(MainWindow.getInstance(), log);
        }
        return messageUtil;
    }
}
