package ca.cgjennings.seplugins.export;

import ca.cgjennings.apps.arkham.sheet.Sheet;
import java.awt.image.BufferedImage;

/**
 * Abstracts sheet rendering to support multiple versions of Strange Eons.
 * @author Chris Jennings <https://cgjennings.ca/contact>
 */
public interface SheetRenderer {
    BufferedImage render(Sheet<?> sheet, double ppi, boolean bleedMargin);
}
