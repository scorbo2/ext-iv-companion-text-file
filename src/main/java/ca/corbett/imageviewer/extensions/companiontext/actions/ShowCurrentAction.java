package ca.corbett.imageviewer.extensions.companiontext.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import org.apache.commons.io.FilenameUtils;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * An action to launch the companion text file viewer/editor for the currently selected
 * image. This action will create a new empty text file if none exists.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.0.0
 */
public class ShowCurrentAction extends EnhancedAction {

    public ShowCurrentAction(String name) {
        super(name);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
        if (currentImage.isEmpty()) {
            MainWindow.getInstance().showMessageDialog(NAME, "Nothing selected.");
            return;
        }

        // Figure out where the companion text file should live:
        // (it's no problem if it's not there, our ShowTextFileAction will handle that)
        File imageFile = currentImage.getImageFile();
        File testFile = new File(imageFile.getParentFile(), FilenameUtils.getBaseName(imageFile.getName()) + ".txt");

        // Now we can delegate to ShowTextFileAction with the createIfNotPresent option set to true:
        ShowTextFileAction action = new ShowTextFileAction(testFile);
        action.setCreateIfNotPresent(true);
        action.actionPerformed(e);
    }
}
