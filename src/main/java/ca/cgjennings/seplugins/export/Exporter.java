package ca.cgjennings.seplugins.export;

import ca.cgjennings.apps.arkham.BusyDialog;
import ca.cgjennings.apps.arkham.StrangeEons;
import ca.cgjennings.apps.arkham.component.ComponentMetadata;
import ca.cgjennings.apps.arkham.component.GameComponent;
import ca.cgjennings.apps.arkham.plugins.ScriptMonkey;
import ca.cgjennings.apps.arkham.sheet.Sheet;
import ca.cgjennings.apps.arkham.sheet.UndecoratedCardBack;
import ca.cgjennings.graphics.ImageUtilities;
import ca.cgjennings.imageio.SimpleImageWriter;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import static resources.Language.string;
import resources.ResourceKit;

/**
 * Export the faces of a single file.
 */
public final class Exporter {

    private String format = "png";
    private boolean progressive = false;
    private double quality = 1;
    private double ppi = 300f;
    private int dimensionLimit = 0;
    private boolean excludeSimpleFaces = false;
    private boolean bleedMargin = false;
    private String postprocessingCode = "";
    private String postprocessingCodeFile = null;

    public static class Results {

        private Results() {
            outputFiles = new LinkedList<>();
            errors = new LinkedList<>();
        }

        private void add(File output) {
            outputFiles.add(output);
        }

        private void add(String error) {
            errors.add(error);
        }
        public List<File> outputFiles;
        public List<String> errors;
    }

    public Exporter() {
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        switch (Objects.requireNonNull(format, "file format")) {
            case SimpleImageWriter.FORMAT_BMP:
            case SimpleImageWriter.FORMAT_GIF:
            case SimpleImageWriter.FORMAT_JPEG:
            case SimpleImageWriter.FORMAT_JPEG2000:
            case SimpleImageWriter.FORMAT_PNG:
                this.format = format;
                break;
            default:
                throw new IllegalArgumentException("unsupported format: " + format);
        }
    }

    public boolean isProgressive() {
        return progressive;
    }

    public void setProgressive(boolean progressive) {
        this.progressive = progressive;
    }

    public double getQuality() {
        return quality;
    }

    public void setQuality(double quality) {
        if (quality < 0d || quality > 1d) {
            throw new IllegalArgumentException("invalid quality: " + quality);
        }
        this.quality = quality;
    }

    public double getResolution() {
        return ppi;
    }

    public void setResolution(double ppi) {
        if (ppi < 1d || ppi > 9999d) {
            throw new IllegalArgumentException("invalid resolution: " + ppi);
        }
        this.ppi = ppi;
    }

    public int getDimensionLimit() {
        return dimensionLimit;
    }

    public void setDimensionLimit(int dimensionLimit) {
        if (dimensionLimit < 0) {
            dimensionLimit = 0;
        }
        this.dimensionLimit = dimensionLimit;
    }

    public boolean isExcludeSimpleFaces() {
        return excludeSimpleFaces;
    }

    public void setExcludeSimpleFaces(boolean excludeSimpleFaces) {
        this.excludeSimpleFaces = excludeSimpleFaces;
    }

    public boolean isBleedMarginEnabled() {
        return bleedMargin;
    }

    public void setBleedMarginEnabled(boolean synthesizeBleedMargin) {
        this.bleedMargin = synthesizeBleedMargin;
    }

    public String getPostprocessingCode() {
        return postprocessingCode;
    }

    public void setPostprocessingCode(String file) {
        this.postprocessingCode = file;
    }

    public String getPostprocessingCodeFile() {
        return postprocessingCodeFile;
    }

    /**
     * Sets the file path to report for the postprocessing script file in error
     * messages. Setting a file is optional.
     *
     * @param file the file path to report for the script, or null
     */
    public void setPostprocessingCodeFile(String file) {
        if (file != null) {
            try {
                file = new File(file).toURI().toURL().toString();
            } catch (MalformedURLException ex) {
                StrangeEons.log.log(Level.WARNING, "could not convert script file to URL: {0}", file);
                file = null;
            }
        }
        this.postprocessingCodeFile = file;
    }

    public Results export(File file) {
        Results results = new Results();

        // ignore deck files
        ComponentMetadata md = new ComponentMetadata(file);
        if (md.getMetadataVersion() >= 1 && !md.isDeckLayoutSupported()) {
            return results;
        }

        // try to read the GC, returning an error on failure
        GameComponent gc = ResourceKit.getGameComponentFromFile(file, false);
        if (gc == null) {
            results.add(string("app-err-open", file.getName()));
            return results;
        }

        // render and save each sheet in turn
        Sheet[] sheets = gc.createDefaultSheets();
        if (sheets == null || sheets.length == 0) {
            results.add("no sheets to render: " + file.getName());
            return results;
        }

        for (int i = 0; i < sheets.length; ++i) {
            if (excludeSimpleFaces && (sheets[i] instanceof UndecoratedCardBack)) {
                sheets[i] = null;
                continue;
            }

            PostprocessingEntry entry = new PostprocessingEntry(this, file, gc, sheets[i], i);

            // remember original size; don't write PPI metadata if it changes
            final int biWidth = entry.image.getWidth();
            final int biHeight = entry.image.getHeight();

            applyDimensionLimits(entry);
            postprocess(entry);
            try {
                if (entry.image != null) {
                    final boolean resized = (entry.image.getWidth() != biWidth) || (entry.image.getHeight() != biHeight);
                    writeImage(entry, resized, results);
                }
            } catch (Exception ex) {
                results.add(ex.getLocalizedMessage());
            } finally {
                // allow GC of image buffers
                sheets[i] = null;
            }

            // check if we are running under a busy dialog, and if we are and
            // it is cancelled, break out early
            if (BusyDialog.getCurrentDialog() != null && BusyDialog.getCurrentDialog().isCancelled()) {
                break;
            }
        }
        return results;
    }

    public Results export(String file) {
        return export(new File(file));
    }

    private void applyDimensionLimits(PostprocessingEntry entry) {
        final BufferedImage bi = entry.image;
        if (dimensionLimit > 0 && (bi.getWidth() > dimensionLimit || bi.getHeight() > dimensionLimit)) {
            float scale = ImageUtilities.idealBoundingScaleForImage(dimensionLimit, dimensionLimit, bi.getWidth(), bi.getHeight());
            entry.image = ImageUtilities.resample(
                    bi, scale, true,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
        }
    }

    private void postprocess(PostprocessingEntry entry) {
        if (postprocessingCode == null || postprocessingCode.isEmpty()) {
            return;
        }

        ScriptMonkey sm = new ScriptMonkey("Postprocessor");
        if (postprocessingCodeFile != null) {
            sm.setInternalFileName(postprocessingCodeFile);
        }
        sm.bind("bulkItem", entry);
        sm.bind("Component", entry.gc);
        sm.eval("useLibrary('imageutils');");
        sm.eval("importClass(arkham.sheet.RenderTarget);");
        sm.eval("importClass(ca.cgjennings.graphics.ImageUtilities);");
        sm.eval("importPackage(ca.cgjennings.graphics.filters);");

        sm.eval(postprocessingCode);
    }

    private void writeImage(PostprocessingEntry entry, boolean replaced, Results results) throws IOException {
        if (entry.exportPath == null || entry.exportPath.isEmpty()) {
            return;
        }

        SimpleImageWriter wr = new SimpleImageWriter(entry.format);
        try {
            wr.setProgressiveScan(progressive);
            wr.setCompressionQuality(wr.isLossless() ? 1f : (float) entry.quality);
            if (!replaced) {
                wr.setPixelsPerInch((float) ppi);
            }
            final File output = new File(entry.exportPath);
            wr.write(entry.image, output);
            results.add(output);
        } finally {
            wr.dispose();
        }
    }

    @Override
    public String toString() {
        return "Exporter{" + "format=" + format + ", progressive=" + progressive + ", quality=" + quality + ", ppi=" + ppi + ", dimensionLimit=" + dimensionLimit + ", excludeSimpleFaces=" + excludeSimpleFaces + ", synthesizeBleedMargin=" + bleedMargin + ", postprocessingCode=" + postprocessingCode + '}';
    }
}
