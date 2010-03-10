/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

/*
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 */
package com.jhlabs.map.proj;

import com.jhlabs.map.*;

/**
* Transverse Mercator Projection algorithm is taken from the USGS PROJ package.
*/
public class TransverseMercatorProjection extends CylindricalProjection {
	
	private final static double FC1 = 1.0;
	private final static double FC2 = 0.5;
	private final static double FC3 = 0.16666666666666666666;
	private final static double FC4 = 0.08333333333333333333;
	private final static double FC5 = 0.05;
	private final static double FC6 = 0.03333333333333333333;
	private final static double FC7 = 0.02380952380952380952;
	private final static double FC8 = 0.01785714285714285714;

	private double esp;
	private double ml0;
	private double[] en;

	public TransverseMercatorProjection() {
		ellipsoid = Ellipsoid.GRS_1980;
		projectionLatitude = Math.toRadians(0);
		projectionLongitude = Math.toRadians(0);
		minLongitude = Math.toRadians(-90);
		maxLongitude = Math.toRadians(90);
		initialize();
	}
	
	/**
	* Set up a projection suitable for State Plane Coordinates.
	*/
	public TransverseMercatorProjection(Ellipsoid ellipsoid, double lon_0, double lat_0, double k, double x_0, double y_0) {
		setEllipsoid(ellipsoid);
		projectionLongitude = lon_0;
		projectionLatitude = lat_0;
		scaleFactor = k;
		falseEasting = x_0;
		falseNorthing = y_0;
		initialize();
	}
	
	public Object clone() {
		TransverseMercatorProjection p = (TransverseMercatorProjection)super.clone();
		if (en != null)
			p.en = (double[])en.clone();
		return p;
	}
	
	public boolean isRectilinear() {
		return false;
	}

	public void initialize() {
		super.initialize();
		if (spherical) {
			esp = scaleFactor;
			ml0 = .5 * esp;
		} else {
			en = MapMath.enfn(es);
			ml0 = MapMath.mlfn(projectionLatitude, Math.sin(projectionLatitude), Math.cos(projectionLatitude), en);
			esp = es / (1. - es);
		}
	}

	public int getRowFromNearestParallel(double latitude) {
		int degrees = (int)MapMath.radToDeg(MapMath.normalizeLatitude(latitude));
		if (degrees < -80 || degrees > 84)
			return 0;
		if (degrees > 80)
			return 24;
		return (degrees + 80) / 8 + 3;
	}
	
	public int getZoneFromNearestMeridian(double longitude) {
		int zone = (int)Math.floor((MapMath.normalizeLongitude(longitude) + Math.PI) * 30.0 / Math.PI) + 1;
		if (zone < 1)
			zone = 1;
		else if (zone > 60)
			zone = 60;
		return zone;
	}
	
	public void setUTMZone(int zone) {
		zone--;
		projectionLongitude = (zone + .5) * Math.PI / 30. -Math.PI;
		projectionLatitude = 0.0;
		scaleFactor = 0.9996;
        falseEasting = 500000;
		initialize();
	}

	@Override public double[] project(double lplam, double lpphi) {
                double x, y;
		if (spherical) {
			double cosphi = Math.cos(lpphi);
			double b = cosphi * Math.sin(lplam);

			x = ml0 * scaleFactor * Math.log((1. + b) / (1. - b));
			double ty = cosphi * Math.cos(lplam) / Math.sqrt(1. - b * b);
			ty = MapMath.acos(ty);
			if (lpphi < 0.0)
				ty = -ty;
			y = esp * (ty - projectionLatitude);
		} else {
			double al, als, n, t;
			double sinphi = Math.sin(lpphi);
			double cosphi = Math.cos(lpphi);
			t = Math.abs(cosphi) > 1e-10 ? sinphi/cosphi : 0.0;
			t *= t;
			al = cosphi * lplam;
			als = al * al;
			al /= Math.sqrt(1. - es * sinphi * sinphi);
			n = esp * cosphi * cosphi;
			x = scaleFactor * al * (FC1 +
				FC3 * als * (1. - t + n +
				FC5 * als * (5. + t * (t - 18.) + n * (14. - 58. * t)
				+ FC7 * als * (61. + t * ( t * (179. - t) - 479. ) )
			)));
			y = scaleFactor * (MapMath.mlfn(lpphi, sinphi, cosphi, en) - ml0 +
				sinphi * al * lplam * FC2 * ( 1. +
				FC4 * als * (5. - t + n * (9. + 4. * n) +
				FC6 * als * (61. + t * (t - 58.) + n * (270. - 330 * t)
				+ FC8 * als * (1385. + t * ( t * (543. - t) - 3111.) )
			))));
		}
		return new double[]{x,y};
	}

	@Override public double[] projectInverse(double x, double y) {
                double lon, lat;
		if (spherical) {
			double h = Math.exp(x / scaleFactor);
			double g = .5 * (h - 1. / h);
			h = Math.cos(projectionLatitude + y / scaleFactor);
			lat = MapMath.asin(Math.sqrt((1. - h*h) / (1. + g*g)));
			if (y < 0)
				lat = -lat;
			lon = Math.atan2(g, h);
		} else {
			double n, con, cosphi, d, ds, sinphi, t;

			lat = MapMath.inv_mlfn(ml0 + y/scaleFactor, es, en);
			if (Math.abs(y) >= MapMath.HALFPI) {
				lat = y < 0. ? -MapMath.HALFPI : MapMath.HALFPI;
				lon = 0.;
			} else {
				sinphi = Math.sin(lat);
				cosphi = Math.cos(lat);
				t = Math.abs(cosphi) > 1e-10 ? sinphi/cosphi : 0.;
				n = esp * cosphi * cosphi;
				d = x * Math.sqrt(con = 1. - es * sinphi * sinphi) / scaleFactor;
				con *= t;
				t *= t;
				ds = d * d;
				lat -= (con * ds / (1.-es)) * FC2 * (1. -
					ds * FC4 * (5. + t * (3. - 9. *  n) + n * (1. - 4 * n) -
					ds * FC6 * (61. + t * (90. - 252. * n +
						45. * t) + 46. * n
					- ds * FC8 * (1385. + t * (3633. + t * (4095. + 1574. * t)) )
				)));
				lon = d*(FC1 -
					ds*FC3*( 1. + 2.*t + n -
					ds*FC5*(5. + t*(28. + 24.*t + 8.*n) + 6.*n
					- ds * FC7 * (61. + t * (662. + t * (1320. + 720. * t)) )
				))) / cosphi;
			}
		}
		return new double[]{lon, lat};
	}

	public boolean hasInverse() {
		return true;
	}

	public String toString() {
		return "Transverse Mercator";
	}

}
