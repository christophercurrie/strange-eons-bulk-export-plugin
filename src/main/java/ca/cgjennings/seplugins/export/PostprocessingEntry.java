package ca.cgjennings.seplugins.export;

import ca.cgjennings.apps.arkham.StrangeEons;
import ca.cgjennings.apps.arkham.component.GameComponent;
import ca.cgjennings.apps.arkham.sheet.Sheet;
import ca.cgjennings.apps.arkham.sheet.UndecoratedCardBack;
import ca.cgjennings.imageio.SimpleImageWriter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.logging.Level;

/**
 * Object that describes a sheet image to be processed by an end user script
 * file before the final version is written.
 *
 * @author Chris Jennings <https://cgjennings.ca/contact>
 */
public final class PostprocessingEntry {
    private static final SheetRenderer renderer;
    static {
        SheetRenderer theRenderer = null;
        String className = "SE33Renderer";
        try {
            Sheet.class.getMethod("getUserBleedMargin");
        } catch (NoSuchMethodException nsm) {
            className = "SE32Renderer";
        }
        StrangeEons.log.log(Level.INFO, "creating bulk export renderer {0}", className);
        className = PostprocessingEntry.class.getPackage().getName() + '.' + className;
        try {
            theRenderer = (SheetRenderer) Class.forName(className).newInstance();
        } catch(ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
            StrangeEons.log.log(Level.SEVERE, "unable to create renderer", ex);
        }
        renderer = theRenderer;
    }

    PostprocessingEntry(Exporter ex, File sourcePath, GameComponent gc, Sheet<? extends GameComponent> sheet, int index) {
        this.sourcePath = sourcePath.getAbsolutePath();
        this.gc = gc;
        this.sheet = sheet;
        this.index = index;
        ppi = ex.getResolution();
        ppcm = ppi / 2.54d;
        simpleBackFace = sheet instanceof UndecoratedCardBack;
        
        bleedMargin = synthesizeBleedMargin = ex.isBleedMarginEnabled();

        image = renderer.render(sheet, ppi, bleedMargin);

        format = ex.getFormat();
        changeExportPathExtension(null);
        quality = ex.getQuality();
        progressive = ex.isProgressive();
    }

    public void changeExportPathExtension(String extension) {
        if (extension == null) {
            extension = format;
        }

        final File source = new File(sourcePath);
        String name = source.getName();
        final int dot = name.indexOf('.');
        if (dot >= 0) {
            name = name.substring(0, dot);
        }
        name += '-' + String.valueOf(index + 1) + '.' + format;

        final File dest = new File(source.getParentFile(), name);
        exportPath = dest.getAbsolutePath();
    }

    /**
     * String that gives the full path to the file being exported.
     */
    public final String sourcePath;
    /**
     * The GameComponent object being exported.
     */
    public final GameComponent gc;
    /**
     * The Sheet object of the specific face being exported.
     */
    public final Sheet<? extends GameComponent> sheet;
    /**
     * The index of the face (0=front, 1=back, and so on).
     */
    public final int index;
    /**
     * The export resolution in pixels per cm.
     */
    public final double ppcm;
    /**
     * The export resolution in pixels per inch.
     */
    public final double ppi;
    /**
     * True if this is a simple back face.
     */
    public final boolean simpleBackFace;
    /**
     * True if a bleed margin was requested, else false.
     * @since 3.3
     */
    public final boolean bleedMargin;
    /**
     * True if the option to synthesize bleed margins was selected, else false.
     * 
     * @deprecated When running in Strange Eons 3.3 or newer, this is equivalent
     * to the {@link #bleedMargin} property.
     */
    @Deprecated
    public final boolean synthesizeBleedMargin;

    /**
     * String that gives the full path to image file to be written.
     */
    public String exportPath;
    /**
     * The image data that will be written.
     */
    public BufferedImage image;
    /**
     * The file format, as supported by {@link SimpleImageWriter}.
     */
    public String format;
    /**
     * The image quality, from 0.0 to 1.0.
     */
    public double quality;
    /**
     * Whether or not to save with progressive scan if supported.
     */
    public boolean progressive;
}
