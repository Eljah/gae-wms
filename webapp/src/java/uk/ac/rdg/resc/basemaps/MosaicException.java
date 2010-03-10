/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

/**
 * An exception that is thrown when an {@link ImageFileMosaic} is invalid.
 * @author Jon
 */
public class MosaicException extends Exception {
    public MosaicException(String message) {
        super(message);
    }
}
