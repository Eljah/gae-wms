/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import java.io.IOException;
import java.util.List;

/**
 * A 24-bit image with ARGB pixels.  (We can't use
 * any of the standard Java SDK image classes in Google App Engine.)
 * @author Jon
 */
public interface Image {

    /** Returns the width of the image */
    public int getWidth();

    /** Returns the height of the image */
    public int getHeight();

    /**
     * Gets the pixel at the given i,j index in the image. [0,0] is the top-left
     * corner of the image
     * @return the ARGB pixel at the given point
     * @throws IOException if there was an error reading from the underlying image
     * @throws IndexOutOfBoundsException if the point is outside the bounds of
     * the image
     */
    public int getPixel(int i, int j) throws IOException;

    /**
     * Returns the array of width*height pixels. Each pixel represents an ARGB
     * value.  The first pixel in the array is the top-left corner of the image.
     * Note that this method may be slow for large images that are backed by
     * disk arrays.
     * @throws IOException if there was an error reading from the underlying image
     */
    public int[] getPixels() throws IOException;

    /**
     * Gets all the pixels at the [i,j] coordinates given in the provided List.
     * Coordinate arrays in the list may be null (signifying out-of-bounds
     * coordinates).
     * @param coordsList a List of two-element [i,j] coordinate arrays
     * @return an array of pixel values, one for each coordinate pair in coordsList
     * @throws IOException if there was an error reading from the underlying image
     * @throws NullPointerException if the List is null
     * @throws IllegalArgumentException if the any of the coordinate arrays
     * does not have 2 elements
     * @throws IndexOutOfBoundsException if any of the coordinate values is
     * out of range for this image.
     */
    public int[] getPixels(List<int[]> coordsList) throws IOException;

    /**
     * Sets the given pixel to the given ARGB colour.
     * @throws IndexOutOfBoundsException if the point is outside the bounds of
     * the image
     */
    public void setPixel(int i, int j, int colour);

    /** Frees all resources associated with this image */
    public void dispose();

}
