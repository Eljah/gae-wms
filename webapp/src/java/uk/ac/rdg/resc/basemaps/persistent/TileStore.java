/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps.persistent;

import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * A persistent store of image tiles: the tiles are &lt;1MB-sized chunks of a
 * larger image, in which each pixel is an argb integer.  The chunks must be
 * &lt;1MB in size as this is a restriction of both GAE's persistent store and
 * its memcache.
 * @author Jon
 */
public final class TileStore
{
    private static final Logger log = Logger.getLogger(TileStore.class.getName());

    private static final DatastoreService DATASTORE
            = DatastoreServiceFactory.getDatastoreService();

    /** FilenameFilter to find all the tile files within a directory */
    private static final FilenameFilter FILENAME_FILTER = new FilenameFilter() {
        @Override public boolean accept(File dir, String name) {
            return name.endsWith(".tile");
        }
    };

    private static final TileStore INSTANCE = new TileStore();

    /** Used in the persistent store to identify the kind of entity */
    private static final String KIND = "tile";

    /** Key for the pixels property of an entity (will contain a Blob) */
    private static final String PIXELS_PROPERTY_KEY = "pixels";

    /** Prevents direct instantiation */
    private TileStore() {}

    public static TileStore getInstance() { return INSTANCE; }

    /**
     * Loads the tiles from the given location (usually within WEB-INF)
     * @param directoryPath
     */
    public void loadTiles(String directoryPath) throws IOException {
        File[] imageFiles = findTileFiles(directoryPath);
        Arrays.sort(imageFiles);
        for (int i = imageFiles.length - 1; i >= 0; i--) {
            File file = imageFiles[i];
            log.fine("Loading image from " + file);
            // Create a new entity.  We use the file stem to create the unique id
            String tileId = getFileStem(file.getName());
            Entity entity = new Entity(KIND, tileId);
            byte[] fileBytes = readFileContents(file);
            log.fine("Storing " + fileBytes.length + " bytes");
            entity.setProperty(PIXELS_PROPERTY_KEY, new Blob(fileBytes));
            //pm.makePersistent(tile);
            //pm.flush();
            com.google.appengine.api.datastore.Key key = DATASTORE.put(entity);
            log.fine("Got key from put: " + key.getKind() + ", " + key.getName() + ", " + key.getId());
            log.fine("Stored image from " + file + " in persistent store");
        }
    }

    private static File[] findTileFiles(String directoryPath) throws FileNotFoundException {
        File directory = new File(directoryPath);
        File[] imageFiles = directory.listFiles(FILENAME_FILTER);
        if (imageFiles == null || imageFiles.length == 0) {
            throw new FileNotFoundException(directory + " contains no valid image files");
        }
        return imageFiles;
    }

    private static String getFileStem(String filename) {
        int lastPeriodIndex = filename.lastIndexOf(".");
        return filename.substring(0, lastPeriodIndex);
    }

    private static byte[] readFileContents(File file)
            throws FileNotFoundException, IOException {
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException(file + " is not a regular file");
        }

        // Create a ByteBuffer that's large enough to read the entire file
        int fileSizeBytes = (int)file.length();
        ByteBuffer bb = ByteBuffer.allocate(fileSizeBytes);
        FileChannel fc = new FileInputStream(file).getChannel();
        fc.read(bb);
        fc.close();
        return bb.array();
    }

    /** Retrieves the array of pixels from the store, using the given key */
    public int[] getPixels(Key key) {
        // Create a unique string ID for this tile
        String tileId = key.toString();
        try {
            // Search the datastore
            Entity entity = DATASTORE.get(KeyFactory.createKey(KIND, tileId));
            Blob pixelsBlob = (Blob)entity.getProperty(PIXELS_PROPERTY_KEY);
            byte[] pixelBytes = pixelsBlob.getBytes();
            // Convert to an array of ints
            ByteBuffer bb = ByteBuffer.wrap(pixelBytes);
            IntBuffer ib = bb.asIntBuffer();
            int[] pixels = new int[pixelBytes.length / 4];
            ib.get(pixels);
            return pixels;
        } catch (EntityNotFoundException ex) {
            return null;
        }
    }

    /**
     * A key that uniquely identifies a tile.
     */
    public static final class Key {
        /** The name of the parent image */
        private String parentImageName;
        /** The width of the parent image */
        private int parentImageWidth;
        /** The height of the parent image */
        private int parentImageHeight;
        /** The x coordinate of the top-left corner of the tile */
        private int topLeftTileX;
        /** The x coordinate of the top-left corner of the tile */
        private int topLeftTileY;
        /** The width of the tile */
        private int tileWidth;
        /** The height of the tile */
        private int tileHeight;

        public Key(String parentImageName, int parentImageWidth, int parentImageHeight,
                int topLeftTileX, int topLeftTileY, int tileWidth, int tileHeight) {
            this.parentImageName = parentImageName;
            this.parentImageWidth = parentImageWidth;
            this.parentImageHeight = parentImageHeight;
            this.topLeftTileX = topLeftTileX;
            this.topLeftTileY = topLeftTileY;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
        }

        /**
         * Parses a string representing this key (the inverse of Key.toString())
         * @throws IllegalArgumentExcpetion if keyStr is not valid
         */
        public static Key decodeKey(String keyStr) {
            if (keyStr == null) throw new NullPointerException("keyStr cannot be null");
            String[] els = keyStr.split("_");
            if (els.length != 7) throw new IllegalArgumentException("Invalid key string format");
            try {
                return new Key(
                    els[0],
                    Integer.parseInt(els[1]),
                    Integer.parseInt(els[2]),
                    Integer.parseInt(els[3]),
                    Integer.parseInt(els[4]),
                    Integer.parseInt(els[5]),
                    Integer.parseInt(els[6])
                );
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Invalid key string format", nfe);
            }
        }

        /**
         * String representation, can be used as a key in itself (can be decoded
         * using decodeKey()
         */
        @Override
        public String toString() {
            return String.format("%s_%d_%d_%d_%d_%d_%d",
                this.parentImageName,
                this.parentImageWidth,
                this.parentImageHeight,
                this.topLeftTileX,
                this.topLeftTileY,
                this.tileWidth,
                this.tileHeight
            );
        }

    }

}
