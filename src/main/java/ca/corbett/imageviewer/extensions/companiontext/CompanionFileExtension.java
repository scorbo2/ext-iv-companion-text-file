package ca.corbett.imageviewer.extensions.companiontext;

import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.ImageOperation;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.ui.ThumbPanel;
import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.IntegerProperty;
import ca.corbett.extras.RedispatchingMouseAdapter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents an extension to ImageViewer that allows you to put a txt file alongside
 * an image, and have that file be displayable by adding a hyperlink to the top of the
 * thumbnail panel. If the image is renamed, moved, copied, symlinked, or deleted, this extension
 * will make sure the same operation happens to the companion file as needed.
 * <p>
 *     <b>Example:</b> given an image named abcd.jpg, you can create a text file in the
 *     same directory with the name abcd.txt - this extension will detect the existence of
 *     that text file the next time you browse to that directory. A hyperlink will be added
 *     to the top of the image thumbnail panel. Clicking the hyperlink will open a
 *     text view/edit dialog with the contents of the text file.
 * </p>
 * <p>
 *     <b>Configuration</b> - the font size for the generated hyperlinks is configurable.
 *     You can find it on the "Thumbnails" tab of the properties dialog after enabling
 *     this extension.
 * </p>
 *
 * @author scorbo2
 */
public class CompanionFileExtension extends ImageViewerExtension {

    private static final Logger logger = Logger.getLogger(CompanionFileExtension.class.getName());

    private static final String EXT_INFO = "/ca/corbett/imageviewer/extensions/companiontext/extInfo.json";
    private static final String fontSizePropName = "Thumbnails.Companion text file.linkFontSize";

    private final AppExtensionInfo extInfo;

    public CompanionFileExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(), EXT_INFO);
        if (extInfo == null) {
            throw new RuntimeException("FullScreenExtension: can't parse extInfo.json!");
        }
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    public List<AbstractProperty> getConfigProperties() {
        List<AbstractProperty> list = new ArrayList<>();
        list.add(new IntegerProperty(fontSizePropName, "Hyperlink font size", 10, 8, 16, 1));
        return list;
    }

    /**
     * Invoked when a new ThumbPanel is created. We respond to this by examining the source
     * image file that it represents, and looking for a companion text file.
     * If found, a hyperlink is added to the thumbpanel, and a few extra properties
     * are set in the thumbpanel so that we can respond to events later.
     *
     * @param thumbPanel The newly created ThumbPanel
     */
    @Override
    public void thumbPanelCreated(ThumbPanel thumbPanel) {
        File srcFile = thumbPanel.getFile();
        if (srcFile == null) {
            return;
        }
        File textFile = new File(srcFile.getParentFile(), FilenameUtils.getBaseName(srcFile.getName()) + ".txt");
        if (textFile.exists()) {

            // Assuming there will be other CompanionFileExtensions for different companion file
            // types. It's therefore possible that one of the others has already created the wrapper
            // panel, and in that case we can just use it. If not, we will create it.
            JPanel wrapperPanel = (JPanel)thumbPanel.getExtraProperty("companionFileWrapperPanel");
            if (wrapperPanel == null) {
                wrapperPanel = new JPanel();
                wrapperPanel.addMouseListener(new RedispatchingMouseAdapter());
                wrapperPanel.setBackground(thumbPanel.getBackground());
                thumbPanel.setExtraProperty("companionFileWrapperPanel", wrapperPanel);
                wrapperPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
            }

            if (textFile.exists()) {
                JLabel textFileLabel = createLabel("[text]");
                CompanionFileMouseListener listener = new CompanionFileMouseListener(textFile);
                textFileLabel.addMouseListener(listener);
                thumbPanel.setExtraProperty("companionTextFileLabel", textFileLabel);
                thumbPanel.setExtraProperty("companionTextFileLabelListener", listener);
                wrapperPanel.add(textFileLabel);
            }

            thumbPanel.add(wrapperPanel, BorderLayout.NORTH);
        }
    }

    /**
     * Invoked when a ThumbPanel is selected or deselected. We respond to that by changing
     * colours as needed to indicate the selection state.
     *
     * @param thumbPanel The ThumbPanel in question
     * @param isSelected true if this thumb panel is selected.
     */
    @Override
    public void thumbPanelSelectionChanged(ThumbPanel thumbPanel, boolean isSelected) {
        JLabel textFileLabel = (JLabel)thumbPanel.getExtraProperty("companionTextFileLabel");
        if (textFileLabel != null) {
            if (isSelected) {
                textFileLabel.setForeground(LookAndFeelManager.getLafColor("textHighlightText", Color.BLUE));
            }
            else {
                textFileLabel.setForeground(LookAndFeelManager.getLafColor("Component.linkColor", Color.BLUE));
            }
        }
        JPanel wrapperPanel = (JPanel)thumbPanel.getExtraProperty("companionFileWrapperPanel");
        if (wrapperPanel != null) {
            wrapperPanel.setBackground(thumbPanel.getBackground());
        }
    }

    /**
     * Invoked when the image that this thumb panel represents has been renamed. We respond
     * to this by updating the hyperlink to point to the new companion file.
     * Note that we don't move the companion files here! File operations are handled
     * in preImageOperation() instead of here. This is the final step, invoked after the file
     * has been renamed, and we just need to update the stale hyperlinks to point to the
     * new files. This method does nothing if there is no companion file.
     *
     * @param thumbPanel The ThumbPanel in question.
     * @param newFile    A File object representing the new name.
     */
    @Override
    public void thumbPanelRenamed(ThumbPanel thumbPanel, File newFile) {
        File textFile = new File(newFile.getParentFile(), FilenameUtils.getBaseName(newFile.getName()) + ".txt");
        CompanionFileMouseListener textLabelListener = (CompanionFileMouseListener)thumbPanel.getExtraProperty(
            "companionTextFileLabelListener");
        if (textLabelListener != null) {
            textLabelListener.setFile(textFile);
        }
    }

    /**
     * Invoked before an ImageOperation is performed - this can be a copy, a delete, a symlink,
     * or a move. We respond to this by doing the equivalent action on the companion files,
     * if they exist.
     *
     * @param opType      The type of operation that's happening.
     * @param srcFile     The image file being operated on.
     * @param destination For non-delete operations, this is the destination of the operation.
     */
    @Override
    public void preImageOperation(ImageOperation.Type opType, File srcFile, File destination) {

        // Special case: if we are being notified about an operation that's happening to one
        // of our companion files, just ignore it:
        if (srcFile.getName().toLowerCase().endsWith(".txt") || srcFile.getName().toLowerCase().endsWith(".json")) {
            return;
        }

        String opName = "moveSingleFile";
        switch (opType) {
            case COPY:
                opName = "copySingleFile";
                break;
            case SYMLINK:
                opName = "linkSingleFile";
                break;
            case DELETE:
                opName = "delete";
                break;
        }
        File companionTextFileSrc = new File(srcFile.getParentFile(),
                                             FilenameUtils.getBaseName(srcFile.getName()) + ".txt");
        File companionTextFileDest = null;
        if (destination != null) {
            companionTextFileDest = new File(destination.getParentFile(),
                                             FilenameUtils.getBaseName(destination.getName()) + ".txt");
            if (companionTextFileSrc.exists()) {
                logger.log(Level.INFO, "{0}: {1} -> {2}",
                           new Object[]{opName, companionTextFileSrc.getAbsolutePath(), companionTextFileDest.getAbsolutePath()});
                if (companionTextFileDest.exists()) {
                    companionTextFileDest.delete();
                }
            }
        }
        try {
            switch (opType) {
                case COPY:
                    if (companionTextFileSrc.exists() && companionTextFileDest != null) {
                        FileUtils.copyFile(companionTextFileSrc, companionTextFileDest);
                    }
                    break;

                case SYMLINK:
                    if (companionTextFileSrc.exists() && companionTextFileDest != null) {
                        Path target = FileSystems.getDefault().getPath(companionTextFileSrc.getAbsolutePath());
                        Path link = FileSystems.getDefault().getPath(companionTextFileDest.getAbsolutePath());
                        java.nio.file.Files.createSymbolicLink(link, target);
                    }
                    break;

                case MOVE:
                    if (companionTextFileSrc.exists() && companionTextFileDest != null) {
                        FileUtils.moveFile(companionTextFileSrc, companionTextFileDest);
                    }
                    break;

                case DELETE:
                    if (companionTextFileSrc.exists()) {
                        logger.log(Level.INFO, "delete: {0}", companionTextFileSrc.getAbsolutePath());
                        companionTextFileSrc.delete();
                    }
                    break;
            }
        }
        catch (IOException ioe) {
            // Extensions can't stop processing, so just log it:
            logger.log(Level.INFO, "{0}: Caught exception while processing companion file: " + ioe.getMessage(), ioe);
        }
    }

    /**
     * We want to prevent our companion files from being flagged as aliens, so we hook into
     * this extension point and return false here if the alien in question is actually
     * one of our guys.
     *
     * @param alienFile The suspected alien file.
     * @return false if the file is a companion file, true otherwise.
     */
    @Override
    public boolean isFileAlien(File alienFile) {
        // First make sure it's a file that we would work with:
        String name = alienFile.getName().toLowerCase();
        if (!name.endsWith(".txt")) {
            return true;
        }

        // Now make sure there's an image file with a matching name.
        // I hate that this code is case-sensitive...
        String[] imageExtensions = new String[]{"gif", "GIF", "jpg", "JPG", "jpeg", "JPEG", "png", "PNG", "tiff", "bmp"};
        File dir = alienFile.getParentFile();
        String basename = FilenameUtils.getBaseName(alienFile.getName());
        boolean matchingImageFound = false;
        for (String ext : imageExtensions) {
            File test = new File(dir, basename + "." + ext);
            if (test.exists()) {
                matchingImageFound = true;
                break;
            }
        }
        return !matchingImageFound;
    }

    /**
     * Invoked internally to create the hyperlink label to launch the viewer dialog.
     * The font size for the label is taken from our config property.
     *
     * @param text The text for the label
     * @return A JLabel
     */
    private JLabel createLabel(final String text) {
        IntegerProperty fontSizeProp = (IntegerProperty)AppConfig.getInstance().getPropertiesManager().getProperty(fontSizePropName);
        if (fontSizeProp == null) {
            logger.log(Level.SEVERE, "CompanionTextFile: can't find our config property!");
            fontSizeProp = new IntegerProperty(fontSizePropName, "Hyperlink font size", 10, 8, 16, 1);
        }
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setFont(label.getFont().deriveFont((float)fontSizeProp.getValue()));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setForeground(LookAndFeelManager.getLafColor("Component.linkColor", Color.BLUE));
        return label;
    }
}
