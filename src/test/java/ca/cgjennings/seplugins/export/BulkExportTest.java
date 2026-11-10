package ca.cgjennings.seplugins.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class BulkExportTest {

    private static InputStream text(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void reportsBuildVersion() throws IOException {
        Properties p = new Properties();
        try (InputStream in = BulkExport.class.getResourceAsStream("/resources/cgj/export/version.properties")) {
            assertThat(in).isNotNull();
            p.load(in);
        }
        float expected = Float.parseFloat(p.getProperty("se-version"));
        assertThat(expected).isGreaterThan(0f);
        assertThat(new BulkExport().getPluginVersion()).isEqualTo(expected);
    }

    @Test
    void readsSeVersion() {
        assertThat(BulkExport.readVersion(text("version=1.4.1\nse-version=1.4\n"))).isEqualTo(1.4f);
    }

    @Test
    void missingFileIsZero() {
        assertThat(BulkExport.readVersion(null)).isZero();
    }

    @Test
    void missingKeyIsZero() {
        assertThat(BulkExport.readVersion(text("version=1.4.1\n"))).isZero();
    }

    @Test
    void invalidValueIsZero() {
        assertThat(BulkExport.readVersion(text("se-version=one\n"))).isZero();
    }

    @Test
    void unreadableStreamIsZero() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("broken");
            }
        };
        assertThat(BulkExport.readVersion(broken)).isZero();
    }
}
