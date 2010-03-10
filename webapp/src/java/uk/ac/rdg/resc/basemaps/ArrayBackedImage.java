/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import java.io.Serializable;
import java.util.Arrays;

/**
 * A simple class representing a 24-bit image, held in memory.
 * @author Jon
 */
public final class ArrayBackedImage extends AbstractImage implements Serializable {

    private final int[] pixels;

    /**
     * Creates a new Image
     * @param width The width of the image in pixels
     * @param height The height of the image in pixels
     * @param pixels Array of width*height pixels
     * @throws NullPointerException if pixels == null
     * @throws IllegalArgumentException if (pixels.length != width * height) or
     * if width or height are less than 0
     */
    public ArrayBackedImage(int width, int height, int[] pixels) {
        super(width, height);
        if (pixels == null) throw new NullPointerException();
        if (pixels.length != width * height) throw new IllegalArgumentException(
                "width and height do not match size of pixel array");
        this.pixels = pixels; // TODO should take defensive copy?
    }

    /**
     * Creates a new Image with all-black pixels
     * @param width The width of the image in pixels
     * @param height The height of the image in pixels
     * @throws IllegalArgumentException if width or height are less than 0
     */
    public ArrayBackedImage(int width, int height) {
        this(width, height, new int[width * height]);
    }

    /**
     * Returns the array of width*height pixels. Each pixel represents an ARGB
     * value.  The first pixel in the array is the top-left corner of the image
     * @todo: should return defensive copy?
     */
    @Override public int[] getPixels() { return this.pixels; }

    /**
     * Gets the pixel at the given i,j index in the image. [0,0] is the top-left
     * corner of the image
     * @return the ARGB pixel at the given point
     * @throws IndexOutOfBoundsException if the point is outside the bounds of
     * the image
     */
    @Override public int getPixel(int i, int j) {
        return this.pixels[getIndex(i, j)];
    }

    /**
     * Sets the given pixel to the given ARGB colour.
     * @throws IndexOutOfBoundsException if the point is outside the bounds of
     * the image
     */
    @Override public void setPixel(int i, int j, int colour) {
        this.pixels[getIndex(i, j)] = colour;
    }

    /**
     * Gets the index of the given point in the array of pixels
     * @throws IndexOutOfBoundsException if the point is outside the bounds of
     * the image
     */
    private int getIndex(int i, int j) {
        if (i < 0 || i >= this.getWidth() ||
            j < 0 || j >= this.getHeight()) {
            throw new IndexOutOfBoundsException();
        }
        return i + (j * this.getWidth());
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + this.getWidth();
        result = 31 * result + this.getHeight();
        result = 31 * result + Arrays.hashCode(this.pixels);
        return result;
    }

    @Override public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof ArrayBackedImage)) return false;
        ArrayBackedImage other = (ArrayBackedImage)obj;
        return this.getWidth() == other.getWidth() &&
               this.getHeight() == other.getHeight() &&
               Arrays.equals(this.pixels, other.pixels);
    }

    @Override public String toString() {
        return "Image width = " + this.getWidth() + ", height = " + this.getHeight();
    }

}
