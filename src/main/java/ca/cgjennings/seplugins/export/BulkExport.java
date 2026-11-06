package ca.cgjennings.seplugins.export;

import ca.cgjennings.apps.arkham.plugins.AbstractPlugin;
import ca.cgjennings.apps.arkham.plugins.PluginContext;
import ca.cgjennings.apps.arkham.project.Actions;
import ca.cgjennings.apps.arkham.project.TaskAction;
import resources.Language;
import static resources.Language.string;

/**
 * Plug-in that adds project support for bulk exporting files.
 *
 * @author Chris Jennings <https://cgjennings.ca/contact>
 */
public class BulkExport extends AbstractPlugin {

    @Override
    public String getPluginName() {
        return string("bx-name");
    }

    @Override
    public String getPluginDescription() {
        return string("bx-desc");
    }

    @Override
    public float getPluginVersion() {
        return 1.3f;
    }

    @Override
    public int getPluginType() {
        return INJECTED;
    }

    @Override
    public boolean initializePlugin(PluginContext context) {
        if (!addedStrings) {
            // Language will also check if we already added but this is faster
            addedStrings = true;
            Language.getInterface().addStrings("cgj/export/strings");
        }
        return true;
    }
    private static boolean addedStrings;

    @Override
    public void showPlugin(PluginContext context, boolean show) {
        registered = new BulkExportAction();
        Actions.register(registered, Actions.PRIORITY_IMPORT_EXPORT);
    }

    @Override
    public void unloadPlugin() {
        if (registered != null) {
            Actions.unregister(registered);
            registered = null;
        }
    }

    private TaskAction registered;
}
