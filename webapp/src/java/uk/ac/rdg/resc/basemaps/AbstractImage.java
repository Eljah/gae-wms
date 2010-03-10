/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import java.io.IOException;
import java.util.List;

/**
 * Abstract class containing helper methods for creating Image objects
 * @author Jon
 */
public abstract class AbstractImage implements Image {

    private final int width;
    private final int height;

    /**
     * @throws IllegalArgumentException if width or height are less than zero
     */
    protected AbstractImage(int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException(
                "width and height must not be less than zero");
        this.width = width;
        this.height = height;
    }

    /** Checks the given indices, throwing an IndexOutOfBoundsException if
     * either index is out of range for this image.  Intended for use in
     * {@link #getPixel(int, int)} and associated methods. */
    protected void checkIndices(int i, int j) {
        if (i < 0 || i >= this.getWidth() ||
            j < 0 || j >= this.getHeight()) {
            throw new IndexOutOfBoundsException();
        }
    }
    
    @Override public int getWidth() { return this.width; }
    
    @Override public int getHeight() { return this.height; }

    /**
     * {@inheritDoc}
     *
     * This implementation simply calls {@link #getPixel(int, int)} for each
     * i-j pair in the coords list.
     * @throws IOException if one of the calls to {@link #getPixel(int, int)}
     * throws an IOException
     */
    @Override
    public int[] getPixels(List<int[]> coordsList) throws IOException {
        if (coordsList == null) throw new NullPointerException("coordsList");
        int[] pixels = new int[coordsList.size()];
        for (int i = 0; i < coordsList.size(); i++) {
            int[] coords = coordsList.get(i);
            if (coords.length != 2) {
                throw new IllegalArgumentException("coords.length must be 2");
            }
            pixels[i] = this.getPixel(coords[0], coords[1]);
        }
        return pixels;
    }

    /**
     * {@inheritDoc}
     *
     * This implementation simply throws UnsupportedOperationException.
     */
    public void setPixel(int i, int j, int colour) {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * {@inheritDoc}
     *
     * This implementation does nothing.
     */
    @Override
    public void dispose() {}

}
