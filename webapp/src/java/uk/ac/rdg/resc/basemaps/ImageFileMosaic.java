/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An ImageMosaic made from a set of {@link ImageFile}s.
 * @author Jon
 */
public class ImageFileMosaic extends ImageMosaic {

    private static final FilenameFilter FILENAME_FILTER = new FilenameFilter() {
        @Override public boolean accept(File dir, String name) {
            return name.endsWith(".pixels");
        }
    };

    /** The individual files that make up this mosaic */
    private final List<ImageFile> subImages = new ArrayList<ImageFile>();
    
    /** The height of each sub-image except perhaps the last one */
    private int maxSubImageHeight;

    /**
     * Creates a new image file mosaic from the *.pixels files in the given directory.
     * @param directory The directory containing the image files
     * @param width The width of the whole image
     * @param height The height of the whole image
     * @throws MosaicException if there was an error constructing the mosaic
     * @throws FileNotFoundException if the directory does not contain any *.pixels file
     */
    public ImageFileMosaic(File directory, int width, int height)
            throws FileNotFoundException, MosaicException
    {
        super(width, height);

        File[] imageFiles = directory.listFiles(FILENAME_FILTER);
        if (imageFiles == null || imageFiles.length == 0) {
            throw new FileNotFoundException(directory + " contains no valid image files");
        }

        // Sort them into ascending lexicographic order
        Arrays.sort(imageFiles);

        int sumFileHeights = 0;
        for (int i = 0; i < imageFiles.length; i++) {
            int fileHeight = (int)(imageFiles[i].length() / (width * 4));
            // Do a consistency check
            if (imageFiles[i].length() != fileHeight * width * 4) {
                throw new MosaicException("Image file " + imageFiles[i].getName()
                        + " is not of the right length");
            }
            // Check that the file height matches previous values, if we have one
            if (i == 0) {
                this.maxSubImageHeight = fileHeight;
            } else if (i < imageFiles.length - 1) {
                // All but the last sub-image must be the same height
                if (fileHeight != this.maxSubImageHeight) {
                    throw new MosaicException("Image file " + imageFiles[i].getName()
                            + "is not the right height");
                }
            } else {
                // The last image may be smaller than previous ones, but not larger
                if (fileHeight > this.maxSubImageHeight) {
                    throw new MosaicException("Image file " + imageFiles[i].getName()
                            + "is too large");
                }
            }
            sumFileHeights += fileHeight;
            // Everything is OK, so create a sub-image
            this.subImages.add(new ImageFile(imageFiles[i], this.getWidth(), fileHeight));
        }
        // Check that the sum of the file heights adds up to the correct value
        if (sumFileHeights != height) {
            throw new MosaicException("Sum of individual file heights (" +
                    sumFileHeights + ") does not match specified total height ("
                    + height + ")");
        }
    }

    @Override
    public int getMaxSubImageWidth() { return this.getWidth(); }

    @Override
    public int getMaxSubImageHeight() { return this.maxSubImageHeight; }

    @Override
    protected ImageFile getSubImage(int subImageIIndex, int subImageJIndex) {
        if (subImageIIndex != 0) {
            throw new IndexOutOfBoundsException("subImageIIndex must be zero");
        }
        return this.subImages.get(subImageJIndex);
    }

    public int[] getPixels() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Frees all resources associated with this ImageMosaic.  Simply calls
     * dispose() on all sub-images
     */
    @Override
    public void dispose()
    {
        for (ImageFile imageFile : this.subImages) {
            imageFile.dispose();
        }
    }

}
