/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps.utils;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * <p>Takes a large image and splits it into sub-images of &lt;1MB, so that they
 * can be stored in Google App Engine's persistent store and memcache.  In practice
 * this means that the images must be no more than ~500x500 if they are 4 bytes
 * per pixel (500*500*4 = 1 million bytes).</p>
 * <p>The names of the tile files are a kind of primary key:
 * "<i>parentImageName</i>_<i>parentImageWidth</i>_<i>parentImageHeight</i>_<i>topLeftTileX</i>_<i>topLeftTileY</i>_<i>tileWidth</i>_<i>tileHeight</i>.pixels".</p>
 * <p>This code cannot be run on Google App Engine itself: it must be run
 * separately, then the files uploaded.</p>
 * @author Jon
 */
public final class ImageMosaicGenerator {

    private static final int MAX_IMAGE_SIZE = 500;

    public static void main(String[] args) {
        // Check the arguments
        if (args.length != 1) {
            System.err.println("Usage: ImageMosaicGenerator2 <filename>");
            System.exit(-1);
        }

        // Find the input image file
        File inFile = new File(args[0]);
        if (!inFile.exists()) {
            System.err.println(args[0] + " does not exist.");
            System.exit(-1);
        }
        // Strip the file extension
        String fileStem = getFileStem(inFile.getName());

        ImageInputStream imageIn = null;
        try {
            // Create an ImageInputStream and find an appropriate image reader
            imageIn = ImageIO.createImageInputStream(inFile);
            Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageIn);
            if (!imageReaders.hasNext()) {
                System.err.println("No ImageReader can be found for " + inFile);
                System.exit(-1);
            }
            ImageReader imageReader = imageReaders.next();
            imageReader.setInput(imageIn);
            // Create a default set of parameters for reading from the images
            ImageReadParam imageReadParam = imageReader.getDefaultReadParam();

            // Create the directory for the output files
            File outFileDir = new File("outputs", fileStem);
            outFileDir.mkdirs();

            // Read the size of the image
            int width = imageReader.getWidth(0);
            int height = imageReader.getHeight(0);
            System.out.println(inFile + ": " + width + "x" + height);

            // The indices of the tiles (i in the horizontal, j in the vertical)
            int i = 0;
            int j = 0;
            boolean done = false;

            while (!done) {
                // Assume we're not in the last row or column unless we find otherwise
                boolean lastRow = false;
                boolean lastCol = false;

                // Find the top-left corner of this tile
                final int topLeftX = i * MAX_IMAGE_SIZE;
                final int topLeftY = j * MAX_IMAGE_SIZE;

                // Find the width and height of this tile
                final int tileWidth;
                final int tileHeight;
                if (topLeftX + MAX_IMAGE_SIZE >= width) {
                    // We are in the last column of tiles
                    lastCol = true;
                    tileWidth = width - topLeftX;
                } else {
                    tileWidth = MAX_IMAGE_SIZE;
                }
                if (topLeftY + MAX_IMAGE_SIZE >= height) {
                    // We are in the last row of tiles
                    lastRow = true;
                    tileHeight = height - topLeftY;
                } else {
                    tileHeight = MAX_IMAGE_SIZE;
                }

                // Extract the tile from the source image
                // Set the source rectangle
                Rectangle sourceRect = new Rectangle(topLeftX, topLeftY, tileWidth, tileHeight);
                imageReadParam.setSourceRegion(sourceRect);

                // Read the required region of the source data
                BufferedImage im = imageReader.read(0, imageReadParam);
                int[] pixels = new int[im.getWidth() * im.getHeight()];
                int n = 0;
                for (int y = 0; y < im.getHeight(); y++) {
                    for (int x = 0; x < im.getWidth(); x++) {
                        pixels[n] = im.getRGB(x, y);
                        n++;
                    }
                }
                
                // Create a filename.  The name is a parseable unique identifier
                String filename = String.format("%s_%d_%d_%d_%d_%d_%d.tile",
                     fileStem, width, height, topLeftX, topLeftY, tileWidth, tileHeight);

                // Output the pixels as raw ARGB integers
                File outFile = new File(outFileDir, filename);
                ByteBuffer bb = ByteBuffer.allocateDirect(pixels.length*4);
                IntBuffer ib = bb.asIntBuffer();
                ib.put(pixels);
                FileChannel fc = new FileOutputStream(outFile).getChannel();
                fc.write(bb);
                fc.close();
                System.out.println(outFile + " written.");

                if (lastCol) {
                    if (lastRow) {
                        // We've done the last tile.  Time to stop.
                        done = true;
                    } else {
                        // Move to the next row
                        i = 0;
                        j++;
                    }
                } else {
                    // Move to the next column
                    i++;
                }
            }
        } catch(IOException ioe) {
            ioe.printStackTrace();
            System.exit(-1);
        } finally {
            if (imageIn != null) {
                try { imageIn.close(); } catch(IOException ioe) { }
            }
        }

        System.out.println("Success.");
    }

    private static String getFileStem(String filename) {
        int lastPeriodIndex = filename.lastIndexOf(".");
        return filename.substring(0, lastPeriodIndex);
    }

}
