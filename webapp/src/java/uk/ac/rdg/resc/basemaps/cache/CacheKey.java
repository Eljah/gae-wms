/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps.cache;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Key for the cache of images
 * @author Jon
 */
public final class CacheKey implements Serializable {

    private final int width;
    private final int height;
    private final double[] bbox;
    private final String layer;
    private final String mimeType;
    private final String crsCode;

    public CacheKey(String layer, String mimeType, String crsCode, double[] bbox, int width, int height) {
        this.layer = layer;
        this.mimeType = mimeType;
        if (crsCode.equals("EPSG:4326")) {
            this.crsCode = "CRS:84";
        } else {
            this.crsCode = crsCode;
        }
        this.bbox = bbox;
        this.width = width;
        this.height = height;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + this.width;
        result = 31 * result + this.height;
        result = 31 * result + Arrays.hashCode(this.bbox);
        result = 31 * result + this.layer.hashCode();
        result = 31 * result + this.mimeType.hashCode();
        result = 31 * result + this.crsCode.hashCode();
        return result;
    }

    @Override public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof CacheKey)) return false;
        CacheKey other = (CacheKey)obj;

        return this.width == other.width &&
               this.height == other.height &&
               Arrays.equals(this.bbox, other.bbox) &&
               this.layer.equals(other.layer) &&
               this.mimeType.equals(other.mimeType) &&
               this.crsCode.equals(other.crsCode);
    }

}
