/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Logger;

/**
 * <p>An {@link Image} whose pixels are stored as 4-byte argb integers in a
 * headerless file as a plain array, with the i dimension varying fastest.</p>
 * <p>This class is internally synchronized and therefore thread-safe.</p>
 * @author Jon
 */
public final class ImageFile extends AbstractImage {

    private static final Logger log = Logger.getLogger(ImageFile.class.getName());

    private final File file;
    private final RandomAccessFile raf;

    /** Creates an ImageFile, opening the underlying file */
    public ImageFile(File file, int width, int height) throws FileNotFoundException {
        super(width, height);
        this.file = file;
        this.raf = new RandomAccessFile(file, "r");
        log.fine("Created ImageFile from " + file.getName());
    }

    /** Reads a single pixel from the underlying file */
    @Override
    public int getPixel(int i, int j) throws IOException {
        // Check for out-of-bound indices
        this.checkIndices(i, j);
        int pos = (j * this.getWidth() + i) * 4;
        synchronized(this.raf) {
            this.raf.seek(pos);
            return this.raf.readInt();
        }
    }

    /** Reads all the pixels from the underlying file */
    @Override
    public int[] getPixels() throws IOException {
        int numPixels = this.getWidth() * this.getHeight();
        byte[] bytes = new byte[numPixels * 4];
        synchronized(this.raf) {
            this.raf.seek(0);
            this.raf.read(bytes);
        }
        // Convert the array of bytes to integer pixels
        int[] pixels = new int[numPixels];
        for (int i = 0; i < pixels.length; i++) {
            byte b1 = bytes[i*4];
            byte b2 = bytes[i*4 + 1];
            byte b3 = bytes[i*4 + 2];
            byte b4 = bytes[i*4 + 3];
            pixels[i] = (b1 << 24) | (b2 << 16) + (b3 << 8) + b4;
        }
        return pixels;
    }

    /** Closes the underlying file */
    @Override
    public void dispose() {
        try {
            log.fine("Closing " + this.file.getPath());
            synchronized(this.raf) {
                this.raf.close();
            }
        } catch (IOException ioe) {
            // TODO: log this error
        }
    }

}
