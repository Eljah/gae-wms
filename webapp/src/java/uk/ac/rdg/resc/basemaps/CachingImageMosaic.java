/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheFactory;
import javax.cache.CacheManager;
import uk.ac.rdg.resc.basemaps.persistent.TileStore;

/**
 * An ImageMosaic whose sub-images are cached using Google App Engine's
 * memcache and persistent store.
 * @todo Add internal in-memory cache too?
 * @author Jon
 */
public final class CachingImageMosaic extends ImageMosaic {
    
    private static final Logger log = Logger.getLogger(CachingImageMosaic.class.getName());

    /** The height and width of the sub-images.  (500x500 gives an image just
     * less than 1MB in size, which is the limit of what memcache and the
     * persistent store can hold for a single object.) */
    private static final int SUB_IMAGE_SIZE = 500;
    
    /** Caches sub-images in Google's memcache */
    private static final Cache MEMCACHE;

    /** Persistent store of sub-images */
    private final TileStore tileStore;
    
    /** An identifier for this image, unique on this server */
    private final String id;
    
    // Initializes the caches
    static {
        // Set up the memcache
        try {
            // TODO: repeats code in ImageCache: can we refactor?
            CacheFactory cacheFactory = CacheManager.getInstance().getCacheFactory();
            MEMCACHE = cacheFactory.createCache(Collections.emptyMap());
            log.info("Set up memcache in CachingImageMosaic");
        } catch (CacheException ce) {
            throw new ExceptionInInitializerError(ce);
        }
    }

    /**
     * Creates a CachingImageMosaic from the given Image, creating the necessary
     * sub-images
     * @param id String identifying the image, to distinguish it from others
     * on this server
     * @param image The image to process
     * @throws IOException if there was an error reading from the source image
     */
    public CachingImageMosaic(String id, int width, int height, TileStore tileStore) {
        super(width, height);
        this.id = id;
        this.tileStore = tileStore;
    }

    @Override
    public int getMaxSubImageWidth() { return SUB_IMAGE_SIZE; }

    @Override
    public int getMaxSubImageHeight() { return SUB_IMAGE_SIZE; }

    @Override
    protected Image getSubImage(int subImageIIndex, int subImageJIndex)
    {
        log.fine("Getting subimage " + subImageIIndex + ", " + subImageJIndex);

        // Get the width and height of the subimage
        int subImageWidth = this.getSubImageWidth(subImageIIndex);
        int subImageHeight = this.getSubImageHeight(subImageJIndex);
        
        // Get the top-left coordinates of the subimage in the parent image
        int topLeftTileX = subImageIIndex * this.getMaxSubImageWidth();
        int topLeftTileY = subImageJIndex * this.getMaxSubImageHeight();

        // Create a unique key for this subimage
        TileStore.Key key = new TileStore.Key(
            this.id, this.getWidth(), this.getHeight(),
            topLeftTileX, topLeftTileY,
            subImageWidth, subImageHeight
        );

        // First look for the tile in the memcache
        // We use the string representation of the key as the memcache key - 
        // TODO revisit this
        String memcacheKey = key.toString();
        int[] pixels = (int[])MEMCACHE.get(memcacheKey);
        if (pixels != null) {
            log.fine("Subimage " + subImageIIndex + ", " + subImageJIndex +
                    " found in memcache.");
            return new ArrayBackedImage(subImageWidth, subImageHeight, pixels);
        }

        // Then look for the tile in the persistent store
        pixels = this.tileStore.getPixels(key);
        if (pixels != null) {
            log.fine("Subimage " + subImageIIndex + ", " + subImageJIndex +
                    " found in persistent store.");
            // Put it in the memcache for later reference
            log.fine("Putting " + pixels.length + " into memcache (key length = "
                    + memcacheKey.length() + ")");
            try {
                MEMCACHE.put(memcacheKey, pixels);
            } catch(Exception e) {
                // During stress testing, we often see errors in the put request:
                // http://groups.google.co.uk/group/google-appengine-java/browse_thread/thread/7491cb06d6708150?hl=en
                // Until we have a better solution, we simply log the exception
                log.warning("Error putting pixels in memcache: " + e.toString());
            }
            return new ArrayBackedImage(subImageWidth, subImageHeight, pixels);
        }

        log.fine("Subimage " + subImageIIndex + ", " + subImageJIndex +
                " not found in persistent store.");
        return new EmptyImage(subImageWidth, subImageHeight);
    }

    /*public void testCache() {
        int numSubImagesIDirection = this.getWidth() / this.getMaxSubImageWidth();
        if (this.getWidth() > numSubImagesIDirection * this.getMaxSubImageWidth()) {
            numSubImagesIDirection++;
        }
        int numSubImagesJDirection = this.getHeight() / this.getMaxSubImageHeight();
        if (this.getHeight() > numSubImagesJDirection * this.getMaxSubImageHeight()) {
            numSubImagesJDirection++;
        }
        int hits = 0;
        int misses = 0;
        for (int j = 0; j < numSubImagesJDirection; j++) {
            for (int i = 0; i < numSubImagesIDirection; i++) {
                Image subImage = this.getSubImage(i, j);
                if (subImage == null) misses++;
                else hits++;
            }
        }
        System.out.println("Hits: " + hits + ", misses: " + misses);
    }*/

    public int[] getPixels() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private SubImageKey createKey(int subImageIIndex, int subImageJIndex) {
        return new SubImageKey(this.id, subImageIIndex, subImageJIndex,
                SUB_IMAGE_SIZE, SUB_IMAGE_SIZE);
    }
    
    /**
     * An identifier for a sub-image, used to retrieve instances from a
     * memcache or persistent store
     */
    public static final class SubImageKey implements Serializable
    {
        /** The id of the parent CachingImageMosaic */
        private final String imageId;
        /** The i index of the sub-image in the parent image */
        private final int subImageIIndex;
        /** The j index of the sub-image in the parent image */
        private final int subImageJIndex;
        /** The maximum width of the sub-images */
        private final int maxSubImageWidth;
        /** The maximum height of the sub-images */
        private final int maxSubImageHeight;

        public SubImageKey(String imageId, int subImageIIndex, int subImageJIndex,
                int maxSubImageWidth, int maxSubImageHeight) {
            if (imageId == null) throw new NullPointerException();
            this.imageId = imageId;
            this.subImageIIndex = subImageIIndex;
            this.subImageJIndex = subImageJIndex;
            this.maxSubImageWidth = maxSubImageWidth;
            this.maxSubImageHeight = maxSubImageHeight;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof SubImageKey)) return false;
            final SubImageKey other = (SubImageKey)obj;
            
            // Do the cheap comparisons that are most likely to differ first
            return this.subImageIIndex == other.subImageIIndex &&
                   this.subImageJIndex == other.subImageJIndex &&
                   this.imageId.equals(other.imageId) &&
                   this.maxSubImageWidth == other.maxSubImageWidth &&
                   this.maxSubImageHeight == other.maxSubImageHeight;
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash = 31 * hash + this.imageId.hashCode();
            hash = 31 * hash + this.maxSubImageHeight;
            hash = 31 * hash + this.maxSubImageWidth;
            hash = 31 * hash + this.subImageIIndex;
            hash = 31 * hash + this.subImageJIndex;
            return hash;
        }

        @Override
        public String toString() {
            return String.format("%s: [%d,%d] (max: %d,%d)", this.imageId,
                    this.subImageIIndex, this.subImageJIndex,
                    this.maxSubImageWidth, this.maxSubImageHeight);
        }
    }

    /** Image with all-black pixels */
    public static final class EmptyImage extends AbstractImage {

        public EmptyImage(int width, int height) {
            super(width, height);
        }

        public int getPixel(int i, int j) throws IOException {
            return 0;
        }

        public int[] getPixels() throws IOException {
            return new int[this.getWidth() * this.getHeight()];
        }

    }

    public static void main(String[] args)
    {
        Map<SubImageKey, String> testMap = new HashMap<SubImageKey, String>();

        SubImageKey key1 = new SubImageKey("bluemarble", 0, 0, SUB_IMAGE_SIZE, SUB_IMAGE_SIZE);
        SubImageKey key2 = new SubImageKey("bluemarble", 0, 0, SUB_IMAGE_SIZE, SUB_IMAGE_SIZE);

        testMap.put(key1, "key1");
        testMap.put(key2, "key2");

        System.out.println(testMap.get(key1));
        System.out.println(testMap.get(key2));

        System.out.println("key1 hashCode: " + key1.hashCode());
        System.out.println("key2 hashCode: " + key2.hashCode());
        System.out.println("key1 == key2?: " + key1.equals(key2));
        System.out.println("key2 == key1?: " + key2.equals(key1));
    }

}
