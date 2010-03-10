/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

/**
 * Exception that is thrown when the {@link BMPLoader} encounters a bitmap file
 * that is either badly-formatted or contains features that it cannot read
 * (e.g. compression)
 * @author Jon Blower
 */
public class InvalidBitmapException extends Exception {
    public InvalidBitmapException(String message) {
        super(message);
    }
}
