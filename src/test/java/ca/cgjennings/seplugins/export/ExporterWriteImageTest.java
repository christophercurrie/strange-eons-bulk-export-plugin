package ca.cgjennings.seplugins.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Node;

class ExporterWriteImageTest {

    private static final int W = 600, H = 400;

    @TempDir
    Path dir;

    private static BufferedImage gradient() {
        BufferedImage bi = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; ++y) {
            for (int x = 0; x < W; ++x) {
                bi.setRGB(x, y, 0xff000000 | (x % 256) << 16 | (y % 256) << 8 | ((x + y) % 256));
            }
        }
        return bi;
    }

    private static int pixelsPerMetre(File png) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(png)) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            ImageReader r = it.next();
            r.setInput(in);
            IIOMetadata md = r.getImageMetadata(0);
            Node root = md.getAsTree("javax_imageio_png_1.0");
            for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                if ("pHYs".equals(n.getNodeName())) {
                    return Integer.parseInt(n.getAttributes().getNamedItem("pixelsPerUnitXAxis").getNodeValue());
                }
            }
            return -1;
        }
    }

    @Test
    void compressesPng() throws IOException {
        File out = dir.resolve("a.png").toFile();
        File written = Exporter.writeImage(gradient(), out, "png", 0.75d, false, 300d);
        assertThat(written).isEqualTo(out);
        // uncompressed ARGB data is W * H * 4 bytes
        assertThat(out.length()).isLessThan((long) W * H * 4 / 2);
        assertThat(ImageIO.read(out).getRGB(10, 20)).isEqualTo(gradient().getRGB(10, 20));
    }

    @Test
    void appliesPngCompression() throws IOException {
        File stored = dir.resolve("stored.png").toFile();
        File smallest = dir.resolve("smallest.png").toFile();
        Exporter.writeImage(gradient(), stored, "png", 1d, false, 300d);
        Exporter.writeImage(gradient(), smallest, "png", 0d, false, 300d);
        // uncompressed ARGB data is W * H * 4 bytes
        assertThat(stored.length()).isGreaterThanOrEqualTo((long) W * H * 4);
        assertThat(smallest.length()).isLessThan(stored.length() / 2);
        assertThat(ImageIO.read(smallest).getRGB(10, 20)).isEqualTo(ImageIO.read(stored).getRGB(10, 20));
    }

    @Test
    void recordsResolutionWhenGiven() throws IOException {
        File with = dir.resolve("with.png").toFile();
        File without = dir.resolve("without.png").toFile();
        Exporter.writeImage(gradient(), with, "png", 1d, false, 300d);
        Exporter.writeImage(gradient(), without, "png", 1d, false, 0d);
        assertThat(pixelsPerMetre(with)).isEqualTo(11811);
        assertThat(pixelsPerMetre(without)).isEqualTo(2835);
    }

    @Test
    void appliesJpegQuality() throws IOException {
        BufferedImage rgb = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(gradient(), 0, 0, null);
        File low = dir.resolve("low.jpg").toFile();
        File high = dir.resolve("high.jpg").toFile();
        Exporter.writeImage(rgb, low, "jpg", 0.1d, false, 300d);
        Exporter.writeImage(rgb, high, "jpg", 0.95d, false, 300d);
        assertThat(low.length()).isLessThan(high.length());
    }
}
