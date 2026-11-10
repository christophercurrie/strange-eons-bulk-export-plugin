package ca.cgjennings.seplugins.export;

import java.io.File;

/**
 * Computes the names of exported image files.
 */
final class ExportPaths {

    private ExportPaths() {
    }

    /**
     * Returns the file that a face of a component is exported to. The name is
     * the source file name up to its first dot, a dash, the one-based face
     * number, and the format extension.
     *
     * @param source the component file
     * @param index the zero-based face index
     * @param format the image format extension
     * @param folder the output folder, or null for the source file's folder
     * @return the destination file
     */
    static File exportFile(File source, int index, String format, File folder) {
        String name = source.getName();
        final int dot = name.indexOf('.');
        if (dot >= 0) {
            name = name.substring(0, dot);
        }
        name += "-" + (index + 1) + '.' + format;
        return new File(folder == null ? source.getAbsoluteFile().getParentFile() : folder, name);
    }
}
