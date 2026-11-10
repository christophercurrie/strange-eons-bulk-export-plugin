package ca.cgjennings.seplugins.export;

import ca.cgjennings.apps.arkham.StrangeEons;
import ca.cgjennings.apps.arkham.plugins.AbstractPlugin;
import ca.cgjennings.apps.arkham.plugins.PluginContext;
import ca.cgjennings.apps.arkham.project.Actions;
import ca.cgjennings.apps.arkham.project.TaskAction;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
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
        return VERSION;
    }

    private static final float VERSION = readVersion(
            BulkExport.class.getResourceAsStream("/resources/cgj/export/version.properties"));

    /**
     * Reads the {@code se-version} from a version properties file, closing
     * the stream. Returns 0 if the stream is null or the value is missing or
     * not a number.
     */
    static float readVersion(InputStream in) {
        if (in == null) {
            StrangeEons.log.warning("missing bulk export version properties");
            return 0f;
        }
        try (InputStream s = in) {
            Properties p = new Properties();
            p.load(s);
            return Float.parseFloat(p.getProperty("se-version", ""));
        } catch (IOException | NumberFormatException ex) {
            StrangeEons.log.log(Level.WARNING, "invalid bulk export version properties", ex);
            return 0f;
        }
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
