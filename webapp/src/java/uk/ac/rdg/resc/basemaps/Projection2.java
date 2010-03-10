/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps;

import com.google.appengine.repackaged.com.google.common.collect.Sets;
import com.jhlabs.map.proj.Projection;
import com.jhlabs.map.proj.ProjectionFactory;
import java.util.Set;

/**
 * Enumeration of projections that are supported by this server.  Projections
 * contain methods to convert between lon/lat and projection coordinates.
 * @author Jon
 */
public enum Projection2 {

    /** Longitude, latitude coordinate system */
    LONLAT {
        @Override public double[] lonLatToProj(double lon, double lat) {
            return new double[]{lon, lat};
        }
        @Override public double[] projToLonLat(double x, double y) {
            return new double[]{x, y};
        }
        @Override public Set<String> getCodes() {
            return Sets.newHashSet("CRS:84", "EPSG:4326");
        }
    },

    /** North polar stereographic projection */
    NORTH_POLAR_STEREOGRAPHIC {
        // For some reason, directly instantiating a StereographicAzimuthalProjection doesn't work
        Projection proj = ProjectionFactory.fromPROJ4Specification(new String[]{
            "+proj=stere",
            "+lat_0=90",
            "+lon_0=0",
            "+lat_ts=90",
            "+k=0.994",
            "+x_0=2000000",
            "+y_0=2000000",
            "+ellps=WGS84",
            "+datum=WGS84",
            "+units=m"
        });

        @Override public double[] lonLatToProj(double lon, double lat) {
            return proj.transform(lon, lat);
        }
        @Override public double[] projToLonLat(double x, double y) {
            return proj.inverseTransform(x, y);
        }
        @Override public Set<String> getCodes() {
            return Sets.newHashSet("EPSG:32661");
        }
    },

    /** South polar stereographic projection */
    SOUTH_POLAR_STEREOGRAPHIC {
        Projection proj = ProjectionFactory.fromPROJ4Specification(new String[]{
            "+proj=stere",
            "+lat_0=-90",
            "+lon_0=0",
            "+lat_ts=-90",
            "+k=0.994",
            "+x_0=2000000",
            "+y_0=2000000",
            "+ellps=WGS84",
            "+datum=WGS84",
            "+units=m"
        });

        @Override public double[] lonLatToProj(double lon, double lat) {
            return proj.transform(lon, lat);
        }
        @Override public double[] projToLonLat(double x, double y) {
            return proj.inverseTransform(x, y);
        }
        @Override public Set<String> getCodes() {
            return Sets.newHashSet("EPSG:32761");
        }
    };

    /** Converts a longitude-latitude point to [x,y] coordinates in this projection */
    public abstract double[] lonLatToProj(double lon, double lat);

    /** Converts an x,y point in this projection to [lon,lat] */
    public abstract double[] projToLonLat(double x, double y);

    /** Gets the codes that can be used to identify this projection */
    public abstract Set<String> getCodes();

}
