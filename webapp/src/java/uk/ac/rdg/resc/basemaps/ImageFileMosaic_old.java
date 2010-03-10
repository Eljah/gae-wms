/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A high-resolution image that has been split into a number of smaller images,
 * to work around Google App Engine's limitation of 10MB per uploaded file.
 * The files are organized by latitude bands, with file0 holding the northernmost
 * latitudes.
 * @author Jon
 */
public final class ImageFileMosaic_old implements Image {

    private static final FilenameFilter FILENAME_FILTER = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.endsWith(".pixels");
        }
    };

    /** Least-recently used cache of open RAFs */
    private static final Map<File, RandomAccessFile> FILE_CACHE
            = new LinkedHashMap<File, RandomAccessFile>();

    private final File[] imageFiles; // The individual files that make up this mosaic
    private final int[] imageHeights; // The heights of the image in each file
    private final int width;
    private final int height;

    /**
     * Creates a new image file mosaic from the *.pixels files in the given directory.
     * @param directory The directory containing the image files
     * @param width The width of the whole image
     * @param height The height of the whole image
     * @throws MosaicException if there was an error constructing the mosaic
     * @throws FileNotFoundException if there was an 
     */
    public ImageFileMosaic_old(File directory, int width, int height)
            throws FileNotFoundException, MosaicException {

        this.imageFiles = directory.listFiles(FILENAME_FILTER);
        if (imageFiles == null || imageFiles.length == 0) {
            throw new FileNotFoundException(directory + " contains no valid image files");
        }

        // Sort them into ascending lexicographic order
        Arrays.sort(imageFiles);

        this.imageHeights = new int[imageFiles.length];
        int sumFileHeights = 0;
        for (int i = 0; i < this.imageFiles.length; i++) {
            System.out.println(imageFiles[i]);
            int fileHeight = (int)(imageFiles[i].length() / (width * 4));
            // Do a consistency check
            if (imageFiles[i].length() != fileHeight * width * 4) {
                throw new MosaicException("Image file " + imageFiles[i].getName()
                        + " is not of the right length");
            }
            this.imageHeights[i] = fileHeight;
            sumFileHeights += fileHeight;
        }
        // Check that the sum of the file heights adds up to the correct value
        if (sumFileHeights != height) {
            throw new MosaicException("Sum of individual file heights (" +
                    sumFileHeights + ") does not match specified total height ("
                    + height + ")");
        }
        this.width = width;
        this.height = height;
    }

    @Override public int getWidth() { return this.width; }

    @Override public int getHeight() { return this.height; }

    @Override public int getPixel(int i, int j) {
        if (i < 0 || i >= this.width ||
            j < 0 || j >= this.height) {
            throw new IndexOutOfBoundsException();
        }
        // Find out which file we need and the j index in the file
        int firstJIndex = 0;
        for (int index = 0; index < this.imageFiles.length; index++) {
            // Find the last j index in the file
            int lastJIndex = firstJIndex + this.imageHeights[index] - 1;
            if (j >= firstJIndex && j <= lastJIndex) {
                // We need to read from this file
                File file = this.imageFiles[index];
                // Calculate the j index within the file
                int jIndexInFile = j - firstJIndex;
                // Calculate the position of the pixel in the file
                long pos = (jIndexInFile * this.width + i) * 4;

                // Read the pixel value from the file
                // TODO: better error handling!
                RandomAccessFile raf = null;
                try {
                    synchronized(FILE_CACHE) {
                        raf = FILE_CACHE.get(file);
                        if (raf == null) {
                            raf = new RandomAccessFile(file, "r");
                            FILE_CACHE.put(file, raf);
                        }
                    }
                    int argb;
                    synchronized(raf) {
                        raf.seek(pos);
                        argb = raf.readInt();
                    }
                    return argb;
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    return 0;
                    // TODO: close the files at some point
                /*} finally {
                    if (raf != null) {
                        try { raf.close(); } catch(IOException ioe) {}
                    }*/
                }
            }
            firstJIndex = lastJIndex + 1;
        }
        // Shouldn't get here
        throw new AssertionError();
    }

    /**
     * Gets all the pixels at the [i,j] coordinates given in the provided List.
     * @param coordsList a List of two-element [i,j] coordinate arrays
     * @return an array of pixel values, one for each coordinate pair in coordsList
     * @throws NullPointerException if the List or any of its contained
     * coordinate arrays is null
     * @throws IllegalArgumentException if the any of the coordinate arrays
     * does not have 2 elements
     * @throws IndexOutOfBoundsException if any of the coordinate values is
     * out of range for this image.
     */
    @Override public int[] getPixels(List<int[]> coordsList) {
        if (coordsList == null) throw new NullPointerException();

        // This maps files to a list of coordinates that need to be read from each file
        Map<File, PixelMap> files = new HashMap<File, PixelMap>();

        // First we cycle through all the coordinates working out which
        // files contain them, and what the indices are in those files
        for (int i = 0; i < coordsList.size(); i++) {
            int[] coords = coordsList.get(i);
            // Check the validity of the coordinates
            this.checkCoords(coords);
            if (coords != null) {
                // Get the file corresponding to these coordinates, plus the coordinates
                // of the point within this file
                FileAndCoords fac = this.getFileAndCoords(coords[0], coords[1]);
                // Add this information to the map
                PixelMap pixelMap = files.get(fac.file);
                if (pixelMap == null) {
                    pixelMap = new PixelMap();
                    files.put(fac.file, pixelMap);
                }
                pixelMap.put(fac.i, fac.j, i);
            }
        }

        // We create the array of pixels to return
        int[] pixels = new int[coordsList.size()];

        // We go through each file and extract the relevant pixels from each
        for (File file : files.keySet()) {
            PixelMap pixelMap = files.get(file);

            FileChannel chan = null;
            try {
                // Open a file channel for fast reading from this file
                chan = new FileInputStream(file).getChannel();
                // Create a ByteBuffer for reading 4 bytes at a time
                ByteBuffer buf = ByteBuffer.allocateDirect(4);

                for (int j : pixelMap.getJIndices()) {
                    for (int i : pixelMap.getIIndices(j)) {
                        // Read the data point
                        chan.position((j * width + i) * 4);
                        chan.read(buf);
                        buf.rewind();
                        int argb = buf.asIntBuffer().get();
                        for (int pos : pixelMap.getPixelIndices(i, j)) {
                            pixels[pos] = argb;
                        }
                    }

                    /*
                    // Get the extremes of the scanline
                    int minI = pixelMap.getMinIIndexInRow(j);
                    int maxI = pixelMap.getMaxIIndexInRow(j);
                    
                    // Read the scanline in a single i/o operation
                    int[] scanline = new int[maxI - minI + 1];
                    chan.position((j * width + minI) * 4);
                    ByteBuffer buf = ByteBuffer.allocateDirect(scanline.length * 4);
                    chan.read(buf);
                    buf.rewind(); // set the buffer back to the start
                    // Copy the data to an array of ints
                    // TODO: could avoid this operation and convert bytes to
                    // argb pixels one at a time
                    buf.asIntBuffer().get(scanline);
                    
                    // Copy the data from the scanline into the array of pixels
                    for (int i : pixelMap.getIIndices(j)) {
                        int argb = scanline[i - minI];
                        for (int pos : pixelMap.getPixelIndices(i, j)) {
                            pixels[pos] = argb;
                        }
                    }
                    */
                }

            } catch (IOException ioe) {
                ioe.printStackTrace();
            } finally {
                if (chan != null) {
                    try { chan.close(); } catch(IOException ioe) {}
                }
            }
        }

        return pixels;
    }

    /**
     * Checks the validity of the given coordinates.
     * @param coords A two-element coordinate array, [i,j]
     * @throws NullPointerException if the coordinate array is null
     * @throws IllegalArgumentException if the coordinate array does not have
     * 2 elements
     * @throws IndexOutOfBoundsException if the values of the coordinates are
     * out of range for this image.
     */
    private void checkCoords(int[] coords) {
        if (coords == null) return;
        if (coords.length != 2) {
            throw new IllegalArgumentException("All coordinates must be 2-dimensional");
        }
        this.checkCoords(coords[0], coords[1]);
    }

    private void checkCoords(int i, int j) {
        if (i < 0 || i >= this.width ||
            j < 0 || j >= this.height) {
            throw new IndexOutOfBoundsException();
        }
    }

    /** Gets the file and the coordinates within that file that correspond with
     * the given coordinates in the whole image. */
    private FileAndCoords getFileAndCoords(int i, int j) {
        // Find out which file we need and the j index in the file
        int firstJIndex = 0;
        for (int index = 0; index < this.imageFiles.length; index++) {
            // Find the last j index in the file
            int lastJIndex = firstJIndex + this.imageHeights[index] - 1;
            if (j >= firstJIndex && j <= lastJIndex) {
                // We need to read from this file
                File file = this.imageFiles[index];
                // Calculate the j index within the file
                int jIndexInFile = j - firstJIndex;
                return new FileAndCoords(file, i, jIndexInFile);
            }
            firstJIndex = lastJIndex + 1;
        }
        // Shouldn't get here.
        throw new AssertionError();
    }

    /** Currently not supported */
    @Override public int[] getPixels() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** Not supported */
    @Override public void setPixel(int i, int j, int colour) {
        throw new UnsupportedOperationException("Not supported.");
    }

    /** Does nothing. */
    public void dispose() { }

    /** Holds a File, plus the coordinates of a point in the file */
    private static final class FileAndCoords implements Comparable<FileAndCoords> {
        private File file;
        private int i;
        private int j;

        public FileAndCoords(File file, int i, int j) {
            this.file = file;
            this.i = i;
            this.j = j;
        }

        /** Sorts by file, then j index, then i index */
        public int compareTo(FileAndCoords o) {
            int fileCompare = this.file.compareTo(o.file);
            if (fileCompare != 0) return fileCompare;
            int jCompare = new Integer(this.j).compareTo(o.j);
            if (jCompare != 0) return jCompare;
            return new Integer(this.i).compareTo(o.i);
        }
    }

}
