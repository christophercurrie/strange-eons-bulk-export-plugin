package ca.cgjennings.seplugins.export;

import ca.cgjennings.apps.arkham.sheet.RenderTarget;
import ca.cgjennings.apps.arkham.sheet.Sheet;
import java.awt.image.BufferedImage;

/**
 * Implements {@link SheetRenderer} for Strange Eons 3.3+.
 * @author Chris Jennings <https://cgjennings.ca/contact>
 */
public class SE33Renderer implements SheetRenderer {
    @Override
    public BufferedImage render(Sheet<?> sheet, double ppi, boolean bleedMargin) {
        sheet.setUserBleedMargin(bleedMargin ? 9d : 0d);
        return sheet.paint(RenderTarget.EXPORT, ppi);
    }
}
