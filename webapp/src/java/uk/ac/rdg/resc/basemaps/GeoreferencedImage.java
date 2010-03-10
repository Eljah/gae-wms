/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * An image whose boundaries are mapped in lat-lon space
 * @author Jon
 */
public class GeoreferencedImage {

    private final Image image;

    // The bounds of the image in projection coordinates
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;

    private final double dx;
    private final double dy;
    private final double startX;
    private final double startY;

    private final Projection2 projection;

    public GeoreferencedImage(Image image, double[] bbox, Projection2 projection) {
        if (bbox.length != 4) {
            throw new IllegalArgumentException("Malformed bounding box");
        }
        this.image = image;
        this.minX = bbox[0];
        this.minY = bbox[1];
        this.maxX = bbox[2];
        this.maxY = bbox[3];
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Malformed bounding box");
        }
        // Calculate the distance between adjacent pixels
        this.dx = (this.maxX - this.minX) / this.image.getWidth();
        this.dy = (this.maxY - this.minY) / this.image.getHeight();

        // Calculate the lat and lon of the first pixel
        this.startX = this.minX + this.dx / 2.0;
        this.startY = this.minY + this.dy / 2.0;

        this.projection = projection;
    }

    public GeoreferencedImage(Image image, double[] bbox) {
        this(image, bbox, Projection2.LONLAT);
    }

    public GeoreferencedImage(Image image, double minLon, double minLat,
            double maxLon, double maxLat) {
        this(image, new double[]{minLon, minLat, maxLon, maxLat});
    }

    /**
     * Creates a georeferenced image in lat-lon coordinates that spans the world
     * @param image
     */
    public GeoreferencedImage(Image image) {
        this(image, -180.0, -90.0, 180.0, 90.0);
    }

    /**
     * Gets the longitude and latitude at the centre of the given pixel.
     * [0,0] represents the top left-hand corner of the image
     * @return [lon,lat]
     * @throws IndexOutOfBoundsException if [i,j] are outside the bounds of the image
     */
    public double[] getLonLat(int i, int j) {
        if (i < 0 || i >= this.image.getWidth() ||
            j < 0 || j >= this.image.getHeight()) {
            throw new IndexOutOfBoundsException();
        }
        double x = this.startX + (i * this.dx);
        // We must flip the j axis
        j = this.image.getHeight() - 1 - j;
        double y = this.startY + (j * this.dy);
        return this.projection.projToLonLat(x, y);
    }

    /**
     * Gets the nearest [i,j] index to the given longitude-latitude point, or
     * null if the point is outside the bounds of the image.
     * [0,0] is the top-left corner of the image
     */
    public int[] getNearestIndices(double lon, double lat) {
        double[] xy = this.projection.lonLatToProj(lon, lat);
        double x = xy[0];
        double y = xy[1];
        if (x < this.minX || x > this.maxX ||
            y < this.minY || y > this.maxY) {
            return null;
        }
        double nx = (x - this.startX) / this.dx;
        double ny = (y - this.startY) / this.dy;
        // We flip the j axis
        return new int[] {
            (int)Math.round(nx),
            this.image.getHeight() - 1 - (int)Math.round(ny)
        };
    }

    /**
     * Gets the nearest ARGB pixel to the given longitude-latitude point, or
     * null if the point is outside the bounds of the image
     */
    public Integer getNearestPixel(double lon, double lat) throws IOException {
        int[] nearestIndices = this.getNearestIndices(lon, lat);
        if (nearestIndices == null) return null;
        return this.image.getPixel(nearestIndices[0], nearestIndices[1]);
    }

    /**
     * Gets the nearest ARGB pixel to each of the given longitude-latitude points
     * in this image, or null if the point is outside the bounds of the image
     */
    public int[] getNearestPixels(List<double[]> lonLats) throws IOException {
        List<int[]> coordsList = new ArrayList<int[]>(lonLats.size());
        for (double[] lonLat : lonLats) {
            if (lonLat == null) throw new NullPointerException();
            if (lonLat.length != 2) throw new IllegalArgumentException("Invalid lon-lat");
            coordsList.add(this.getNearestIndices(lonLat[0], lonLat[1]));
        }
        return this.image.getPixels(coordsList);
    }

    /** Does nothing if lon,lat is outside the bounds of this image */
    public void setNearestPixel(double lon, double lat, int pixel) {
        int[] nearestIndices = this.getNearestIndices(lon, lat);
        if (nearestIndices == null) return;
        this.image.setPixel(nearestIndices[0], nearestIndices[1], pixel);
    }

    public Image getImage() {
        return this.image;
    }

    /**
     * Returns a new unmodifiable {@link List} of longitude-latitude pairs, one for each pixel
     * in the image.  The first item in the list is the lowest-valued lon-lat,
     * i.e. the bottom left-hand corner of the image. (TODO: is this last
     * statement true for non-lon-lat projections?)
     * @return
     */
    public List<double[]> getLonLatsList() {
        return new LonLatList();
    }

    private final class LonLatList extends AbstractList<double[]> {

        @Override
        public double[] get(int index) {
            int i = index % image.getWidth();
            int j = index / image.getWidth();
            return GeoreferencedImage.this.getLonLat(i, j);
        }

        @Override
        public int size() {
            return image.getWidth() * image.getHeight();
        }

    }

}
