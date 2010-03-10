/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;
import uk.ac.rdg.resc.basemaps.persistent.TileStore;

/**
 * An image mosaic that is backed by a set of files on the local disk.
 * @author Jon
 */
public class FileBackedImageMosaic extends ImageMosaic {
    
    private static final Logger log = Logger.getLogger(FileBackedImageMosaic.class.getName());

    /** The height and width of the sub-images.  (500x500 gives an image just
     * less than 1MB in size, which is the limit of what memcache and the
     * persistent store can hold for a single object.) */
    private static final int SUB_IMAGE_SIZE = 500;
    
    /** An identifier for this image, unique on this server */
    private final String id;
    
    /** The directory in which the files are stored */
    private final File directory;
    
    public FileBackedImageMosaic(String id, int width, int height, File directory) {
        super(width, height);
        this.id = id;
        this.directory = directory;
    }

    @Override
    protected Image getSubImage(int subImageIIndex, int subImageJIndex) {
        log.fine("Getting subimage " + subImageIIndex + ", " + subImageJIndex);

        // Get the width and height of the subimage
        int subImageWidth = this.getSubImageWidth(subImageIIndex);
        int subImageHeight = this.getSubImageHeight(subImageJIndex);
        
        // Get the top-left coordinates of the subimage in the parent image
        int topLeftTileX = subImageIIndex * this.getMaxSubImageWidth();
        int topLeftTileY = subImageJIndex * this.getMaxSubImageHeight();

        // Create a unique key for this subimage.  This is the filename
        TileStore.Key key = new TileStore.Key(
            // TODO: replace "bluemarble" with "this.id"
            "bluemarble", this.getWidth(), this.getHeight(),
            topLeftTileX, topLeftTileY,
            subImageWidth, subImageHeight
        );
        String filename = key.toString() + ".tile";
        try {
            return getImage(filename, subImageWidth, subImageHeight);
        } catch (IOException ioe) {
            log.warning(ioe.toString());
            // return a blank image
            return new CachingImageMosaic.EmptyImage(subImageWidth, subImageHeight);
        }
    }
    
    private Image getImage(String filename, int subImageWidth, int subImageHeight) throws IOException {
        File imageFile = new File(this.directory, filename);
        int length = (int)imageFile.length();
        if (length == 0) throw new FileNotFoundException(imageFile.getPath());
        if (length != subImageWidth * subImageHeight * 4) {
            throw new IllegalStateException("Unexpected file length");
        }
        log.fine("Reading image data from " + filename);
        ByteBuffer buf = ByteBuffer.allocateDirect(length);
        FileChannel chan = new FileInputStream(imageFile).getChannel();
        chan.read(buf);
        chan.close();
        return new ByteBufferBackedImage(subImageWidth, subImageHeight, buf);
    }
    
    /** Image that's backed by a byte buffer */
    private static final class ByteBufferBackedImage extends AbstractImage {
        private final ByteBuffer buf;
        
        public ByteBufferBackedImage(int width, int height, ByteBuffer buf) {
            super(width, height);
            this.buf = buf;
        }

        public int getPixel(int i, int j) {
            int pos = 4 * ((j * this.getWidth()) + i);
            // We read the bytes one at a time to avoid the need to set the
            // position of the byte buffer, which would require synchronization
            // between threads
            byte b1 = this.buf.get(pos);
            byte b2 = this.buf.get(pos+1);
            byte b3 = this.buf.get(pos+2);
            byte b4 = this.buf.get(pos+3);
            
            // Convert these bytes to an int and return
            return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
        }

        public int[] getPixels() throws IOException {
            throw new UnsupportedOperationException("Not supported.");
        }
    }

    @Override
    public int getMaxSubImageWidth() {
        return SUB_IMAGE_SIZE;
    }

    @Override
    public int getMaxSubImageHeight() {
        return SUB_IMAGE_SIZE;
    }

    public int[] getPixels() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
