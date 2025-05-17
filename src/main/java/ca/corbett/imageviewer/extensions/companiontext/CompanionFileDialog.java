package ca.corbett.imageviewer.extensions.companiontext;

import ca.corbett.extras.MessageUtil;
import ca.corbett.imageviewer.ui.MainWindow;
import org.apache.commons.io.FileUtils;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Very quick dialog for just showing the contents of a text file.
 * No checking is done to make sure the file actually contains text
 * and not binary gibberish.
 *
 * @author scorbo2
 */
public class CompanionFileDialog extends JDialog {

    private static final Logger log = Logger.getLogger(CompanionFileDialog.class.getName());
    private JTextArea textArea;
    private final File textFile;
    private MessageUtil messageUtil;

    /**
     * Creates a simple dialog for showing the contents of the given File, which is
     * assumed to be a text file.
     *
     * @param textFile The file to show.
     */
    public CompanionFileDialog(File textFile) {
        super(MainWindow.getInstance(), "Text for " + textFile.getName(), true);
        this.textFile = textFile;
        setSize(580, 250);
        setResizable(true);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        initComponents();
    }

    private void initComponents() {
        textArea = new JTextArea();
        textArea.setLineWrap(true);
        try {
            textArea.setText(FileUtils.readFileToString(textFile, (String)null));
        }
        catch (IOException ioe) {
            textArea.setText(ioe.getMessage());
        }
        setLayout(new BorderLayout());
        add(new JScrollPane(textArea), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.RIGHT));

        JButton button = new JButton("OK");
        button.setPreferredSize(new Dimension(90,24));
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    FileUtils.writeStringToFile(textFile, textArea.getText(), (String)null);
                    dispose();
                }
                catch (IOException ioe) {
                    getMessageUtil().error("Unable to save text file!", ioe);
                }
            }
        });
        panel.add(button);

        button = new JButton("Cancel");
        button.setPreferredSize(new Dimension(90,24));
        button.addActionListener(actionEvent -> dispose());
        panel.add(button);

        return panel;
    }

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, log);
        }
        return messageUtil;
    }
}
