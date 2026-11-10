package ca.cgjennings.seplugins.export;

import ca.cgjennings.apps.arkham.project.ProjectFolderDialog;
import ca.cgjennings.ui.JFileField;
import java.io.File;
import javax.swing.SwingUtilities;

/**
 * File field that chooses a folder.
 */
@SuppressWarnings("serial")
final class FolderField extends JFileField {

    @Override
    public String showFileDialog(int modifiers) {
        ProjectFolderDialog d = new ProjectFolderDialog(SwingUtilities.windowForComponent(this), ProjectFolderDialog.Mode.SELECT_FOLDER);
        d.setSelectedFolder(getFile());
        File sel = d.showDialog();
        return sel == null ? null : sel.getAbsolutePath();
    }
}
