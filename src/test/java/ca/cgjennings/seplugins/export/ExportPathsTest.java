package ca.cgjennings.seplugins.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Test;

class ExportPathsTest {

    @Test
    void writesBesideSourceWithoutFolder() {
        File source = new File("/cards/hero.eon");
        assertThat(ExportPaths.exportFile(source, 0, "png", null))
                .isEqualTo(new File("/cards/hero-1.png").getAbsoluteFile());
    }

    @Test
    void writesToFolderWhenSet() {
        File source = new File("/cards/hero.eon");
        assertThat(ExportPaths.exportFile(source, 1, "jpg", new File("/out")))
                .isEqualTo(new File("/out/hero-2.jpg"));
    }

    @Test
    void stripsFromFirstDot() {
        File source = new File("/cards/hero.v2.eon");
        assertThat(ExportPaths.exportFile(source, 0, "png", new File("/out")))
                .isEqualTo(new File("/out/hero-1.png"));
    }

    @Test
    void keepsNameWithoutDot() {
        File source = new File("/cards/hero");
        assertThat(ExportPaths.exportFile(source, 2, "png", new File("/out")))
                .isEqualTo(new File("/out/hero-3.png"));
    }
}
