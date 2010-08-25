/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * An Image that is made up of a number of sub-images.  Each sub-image has a
 * certain maximum size (sub-images at the right and bottom of the parent image
 * may be smaller).  Sub-images tile to fill the parent image from the top-left.
 * @author Jon
 */
public abstract class ImageMosaic extends AbstractImage {

    private static final Logger log = Logger.getLogger(ImageMosaic.class.getName());

    public ImageMosaic(int width, int height) {
        super(width, height);
    }

    /** Gets the maximum width of each sub-image. */
    public abstract int getMaxSubImageWidth();

    /** Gets the maximum height of each sub-image. */
    public abstract int getMaxSubImageHeight();

    /**
     * Returns the argb pixel value in the parent image at the given parent image
     * indices.
     * @param i The i index of the required pixel in the parent image
     * @param j The j index of the required pixel in the parent image
     * @return The argb value of the required pixel at the given indices
     * @throws IOException if there was an i/o error reading from the sub-image
     * @throws IndexOutOfBoundsException if the point is outside the bounds of
     * the image
     */
    @Override
    public int getPixel(int i, int j) throws IOException {
        log.fine("Requested individual pixel " + i + ", " + j);
        // Check for indices out of bounds
        this.checkIndices(i, j);

        // Get the indices of the sub image (i.e. [0,0] is the sub-image that
        // covers the top left of the parent image)
        int subImageIIndex = i / this.getMaxSubImageWidth();
        int subImageJIndex = j / this.getMaxSubImageHeight();

        // Get the indices of this pixel within the sub-image
        int iIndexInSubImage = i % this.getMaxSubImageWidth();
        int jIndexInSubImage = j % this.getMaxSubImageHeight();

        // Get the sub-image
        Image subImage = this.getSubImage(subImageIIndex, subImageJIndex);

        // Return the correct pixel in the sub-image
        return subImage.getPixel(iIndexInSubImage, jIndexInSubImage);
    }

    /**
     * Reads multiple pixels at once, minimizing the number of calls to
     * {@link #getSubImage(int, int)}.
     */
    @Override
    public int[] getPixels(List<int[]> coordsList) throws IOException {
        if (coordsList == null) throw new NullPointerException();

        log.fine("Requested " + coordsList.size() + " pixels from mosaic.");
        
        // Maps sub-image indices to lists of coordinates within each subimage
        Map<Coords, PixelDetails> pixelDetails = new HashMap<Coords, PixelDetails>();
        
        for (int i = 0; i < coordsList.size(); i++) {
            
            // Get and check the coordinates
            int[] coords = coordsList.get(i);

            // Skip any null coordinates (these represent out-of-range values)
            if (coords == null) continue;

            if (coords.length != 2) {
                throw new IllegalArgumentException("Illegal coordinates");
            }
            
            // Find out which sub image contains these coordinates
            int subImageIIndex = coords[0] / this.getMaxSubImageWidth();
            int subImageJIndex = coords[1] / this.getMaxSubImageHeight();
            int[] subImageIndices = new int[]{subImageIIndex, subImageJIndex};
            Coords subImageCoords = new Coords(subImageIndices);
            PixelDetails details = pixelDetails.get(subImageCoords);
            if (details == null) {
                log.fine("Added new PixelDetails entry for subimage " +
                        Arrays.toString(subImageIndices));
                details = new PixelDetails();
                pixelDetails.put(subImageCoords, details);
            }
            details.targetImageIndices.add(i);
            // Add the coordinates of this point within the subimage
            details.subImageIndices.add(new int[]{
                 coords[0] % this.getMaxSubImageWidth(),
                 coords[1] % this.getMaxSubImageHeight()
            });
        }
        
        // Now we can extract the data from each sub-image in turn
        int[] pixels = new int[coordsList.size()];
        for (Coords subImageCoords : pixelDetails.keySet()) {
            PixelDetails details = pixelDetails.get(subImageCoords);
            int[] subImageIndices = subImageCoords.getCoords();
            Image subImage = this.getSubImage(subImageIndices[0], subImageIndices[1]);
            int[] subImagePixels = subImage.getPixels(details.subImageIndices);
            // Now add these pixels to the target image
            for (int i = 0; i < subImagePixels.length; i++) {
                int indexInTargetImage = details.targetImageIndices.get(i);
                pixels[indexInTargetImage] = subImagePixels[i];
            }
        }
        
        return pixels;
        
        // We avoid the creation of an expensive object a la PixelMap by making
        // multiple passes through the list
        

        // This set records the subimages we've looked at already
//        Set<int[]> subImagesExamined = new HashSet<int[]>();
//        boolean done = false;
//        int[] pixels = new int[coordsList.size()];
//        while(!done) {
//            // Assume this is the last pass unless we find otherwise
//            done = true;
//            // Cycle through all the coordinates
//            for (int i = 0; i < coordsList.size(); i++) {
//                int[] coords = coordsList.get(i);
//                if (coords == null || coords.length != 2) {
//                    throw new IllegalArgumentException("Illegal coordinates");
//                }
//                // Find out which subimage contains these coords
//                int subImageIIndex = coords[0] / this.getMaxSubImageWidth();
//                int subImageJIndex = coords[1] / this.getMaxSubImageHeight();
//                int[] subImageIndices = new int[]{subImageIIndex, subImageJIndex};
//                // Look to see if we've examined this subimage on a previous pass
//                if (!subImagesExamined.contains(subImageIndices)) {
//                    // This is a new subimage
//                    done = false;
//                    subImagesExamined.add(subImageIndices);
//                    // Get the subimage in question
//                    Image subImage = this.getSubImage(subImageIIndex, subImageJIndex);
//                    // Read 
//                }
//            }
//        }
    }

    /** Wraps an int array, providing proper hashCode() and equals() methods */
    private static final class Coords
    {
        private int[] coords;
        public Coords(int[] coords) { this.coords = coords; }
        public int[] getCoords() { return this.coords; }
        @Override public String toString() { return Arrays.toString(this.coords); }
        @Override public int hashCode() { return Arrays.hashCode(this.coords); }
        @Override public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof Coords)) return false;
            return Arrays.equals(this.coords, ((Coords)obj).getCoords());
        }
    }
    
    private static final class PixelDetails
    {
        private List<Integer> targetImageIndices = new ArrayList<Integer>();
        private List<int[]> subImageIndices = new ArrayList<int[]>();
    }

    /**
     * Gets the sub-image with the given indices, i.e. [0,0] returns the sub-image
     * in the top left of the parent image and [1,0] returns the next sub-image
     * along.
     */
    protected abstract Image getSubImage(int subImageIIndex, int subImageJIndex);

    /**
     * Gets the width in pixels of the sub-image with the given i index.  This
     * will be equal to {@link #getMaxSubImageWidth()} except for the last
     * subimage in the row.
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    public int getSubImageWidth(int subImageIIndex) {
        if (subImageIIndex < 0) throw new IndexOutOfBoundsException();
        // Find the first pixel in the subimage
        final int iStart = subImageIIndex * this.getMaxSubImageWidth();
        if (iStart >= this.getWidth()) throw new IndexOutOfBoundsException();
        final int subImageWidth = this.getWidth() - iStart;
        return Math.min(subImageWidth, this.getMaxSubImageWidth());
    }

    /**
     * Gets the height in pixels of the sub-image with the given j index.  This
     * will be equal to {@link #getMaxSubImageHeight()} except for the last
     * subimage in the column.
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    public int getSubImageHeight(int subImageJIndex) {
        if (subImageJIndex < 0) throw new IndexOutOfBoundsException();
        final int jStart = subImageJIndex * this.getMaxSubImageHeight();
        if (jStart >= this.getHeight()) throw new IndexOutOfBoundsException();
        final int subImageHeight = this.getHeight() - jStart;
        return Math.min(subImageHeight, this.getMaxSubImageHeight());
    }

}
