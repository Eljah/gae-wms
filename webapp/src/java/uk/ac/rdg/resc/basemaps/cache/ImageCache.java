/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps.cache;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheFactory;
import javax.cache.CacheManager;
import javax.cache.CacheStatistics;

/**
 * A cache of byte arrays representing images.
 * @author Jon
 */
public class ImageCache {

    private static final Logger log = Logger.getLogger(ImageCache.class.getName());

    /** Singleton instance */
    public static final ImageCache INSTANCE;

    private final Cache cache;

    static {
        try {
            INSTANCE = new ImageCache();
        } catch (CacheException ce) {
            throw new ExceptionInInitializerError(ce);
        }
    }

    private ImageCache() throws CacheException {
        CacheFactory cacheFactory = CacheManager.getInstance().getCacheFactory();
        this.cache = cacheFactory.createCache(Collections.emptyMap());
    }

    /** Returns an image from the cache, or null if there is no image matching
     *  the given key. */
    public byte[] getImage(CacheKey cacheKey) {
        return (byte[])this.cache.get(cacheKey);
    }

    public void putImage(CacheKey cacheKey, byte[] image) {
        try {
            this.cache.put(cacheKey, image);
        } catch (Exception e) {
            // During stress testing, we often see errors in the put request:
            // http://groups.google.co.uk/group/google-appengine-java/browse_thread/thread/7491cb06d6708150?hl=en
            // Until we have a better solution, we simply log the exception
            log.log(Level.WARNING, "Error putting extracted image in memcache", e);
        }
    }

    public CacheStatistics getCacheStatistics() {
        return this.cache.getCacheStatistics();
    }

    /** Removes all entries from the cache */
    public void clear() {
        this.cache.clear();
    }

}
