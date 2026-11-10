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
    private File outputFolder = null;

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

    public File getOutputFolder() {
        return outputFolder;
    }

    /**
     * Sets the folder that images are written to. If null, each image is
     * written to the folder that contains its source file.
     *
     * @param folder the output folder, or null
     */
    public void setOutputFolder(File folder) {
        this.outputFolder = folder;
    }

    public Results export(File file) {
        try {
            return export(file, null);
        } catch (InterruptedException ex) {
            throw new AssertionError(ex);
        }
    }

    /**
     * Exports the faces of a file. If a queue is given, images are written by
     * the queue and its written files and errors are not included in the
     * results.
     *
     * @throws InterruptedException if interrupted while waiting for the queue
     */
    Results export(File file, ImageWriteQueue queue) throws InterruptedException {
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
            // allow GC of image buffers
            sheets[i] = null;
            if (entry.image != null && entry.exportPath != null && !entry.exportPath.isEmpty()) {
                final boolean resized = (entry.image.getWidth() != biWidth) || (entry.image.getHeight() != biHeight);
                final BufferedImage image = entry.image;
                final File output = new File(entry.exportPath);
                final String imageFormat = entry.format;
                final double imageQuality = entry.quality;
                final double imagePpi = resized ? 0d : ppi;
                if (queue == null) {
                    try {
                        results.add(writeImage(image, output, imageFormat, imageQuality, progressive, imagePpi));
                    } catch (Exception ex) {
                        results.add(ex.getLocalizedMessage());
                    }
                } else {
                    final long bytes = 4L * image.getWidth() * image.getHeight();
                    queue.submit(bytes, () -> writeImage(image, output, imageFormat, imageQuality, progressive, imagePpi));
                }
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

    /**
     * Writes an image file. Quality is ignored for lossless formats, which use
     * the writer's default compression level.
     *
     * @param ppi the resolution to record in the file, or 0 for the writer default
     * @return the file written
     */
    static File writeImage(BufferedImage image, File output, String format, double quality, boolean progressive, double ppi) throws IOException {
        SimpleImageWriter wr = new SimpleImageWriter(format);
        try {
            wr.setProgressiveScan(progressive);
            if (!wr.isLossless()) {
                wr.setCompressionQuality((float) quality);
            }
            if (ppi > 0d) {
                wr.setPixelsPerInch((float) ppi);
            }
            wr.write(image, output);
            return output;
        } finally {
            wr.dispose();
        }
    }

    @Override
    public String toString() {
        return "Exporter{" + "format=" + format + ", progressive=" + progressive + ", quality=" + quality + ", ppi=" + ppi + ", dimensionLimit=" + dimensionLimit + ", excludeSimpleFaces=" + excludeSimpleFaces + ", synthesizeBleedMargin=" + bleedMargin + ", outputFolder=" + outputFolder + ", postprocessingCode=" + postprocessingCode + '}';
    }
}
