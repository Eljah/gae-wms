# Introduction #
The Google App Engine environment places some strong limits on software, particularly GIS software.  This page expands upon the discussion of the software challenges in the "Development" section of [this paper](http://code.google.com/p/gae-wms/downloads/detail?name=Blower%20WMS%20on%20GAE%20camera-ready.doc).

# Main restrictions of GAE #

  * No files may be written to the server’s local filesystem.  All data must be written to Google’s persistent store, or to the memory cache (“memcache”), both of which are distributed over many servers to provide high capacity.
  * Many important classes in the standard Java Development Kit are not available, including those in the javax.imageio package and most of the contents of java.awt.  These packages contain much of the code that would usually be used for image manipulation.
  * Applications may not spawn background threads.  This means that the present versions of many Java libraries, including Geotoolkit, which handles transformations between hundreds of coordinate systems, cannot be run.
  * The amount of random-access memory available to each service instance is low.  Precise figures are not available, but tests suggest that the current limit is around 100MB.  Applications must therefore make very sparing use of RAM.

These restrictions aside, GAE provides an environment close to that of a standard servlet container.  Capacity is restricted by daily and per-minute quotas.  We wish to stay within these free quotas as much as possible, and have designed the software appropriately.

# Software notes #

## Reading image data ##

A Web Map Service receives requests for images with a certain geographic bounding box, coordinate reference system, width and height (in pixels).  Using this information we can work out the geographic coordinates of each individual pixel in the image.  We can then work out the nearest pixels in the source image.  We must therefore implement an efficient means to read collections of pixels from the source image, which are then assembled to produce the requested image.

We could not find an open-source Java library for reading JPEG images that runs on Google App Engine (please let me know at j.d.blower@reading.ac.uk if you know of one!).  Therefore we uncompressed our source image into raw arrays of pixels (4-byte ARGB integers).  The uncompressed image is too large (58.3MB) to upload to GAE as a single file so we split it into 500x500 chunks of 1MB each (which we called an "image mosaic").

We initially tried storing these chunks in GAE's distributed persistent data store, layering a memcache on top of it for speed.  However, we found that using this method we would very easily exceed the free per-minute quotas for reading from these data stores (if the user requests an image covering the whole planet, all 58.3MB are read from the persistent store and doing this several times simultaneously with multiple client threads exceeds the quotas).

An image pyramid would have been one solution to this, but a pyramid would have to be generated for each coordinate reference system supported by the app.  This is not a large problem at the moment, but we may wish to support a large number of CRSs in future.  Images generated from a pyramid are also less accurate than those generated from full-resolution data.

So our final solution was to store the image mosaic in the local filesystem.  This seems to work well, but unfortunately the free filesystem quota is not very large, and only two such source images could be stored.

A better solution might be to store the mosaic in the distributed store, but request increases to the free per-minute quotas.

## Writing images ##

Having extracted the necessary pixels from the source image, an output image must be written.  We located an [open-source PNG-writing library](http://catcode.com/pngencoder/), which we adapted to run on GAE (the adaptation consisted of removing references to banned classes such as java.awt.`*`).  This worked well, but PNGs are large compared with JPEGs (about 10 times larger), meaning that 10 times fewer images could be served within the free quotas (and more client bandwidth is used too).

We were unable to locate a suitable JPEG-writing library so we used GAE's in-built image service to convert from PNG to JPEG.  The disadvantage of this approach is that we found that we could easily exceed the per-minute quota for this service.  However, the images can be stored in memcache for future use, meaning that there is no need to perform the same conversion many times.

## Coordinate transformations ##

Requests for images from clients can be associated with a number of coordinate reference systems.  In order to map from the requested image's coordinate system to that of the source image (WGS:84 latitude-longitude in this case), a coordinate transformation library is needed.  Usually we use [Geotoolkit](http://www.geotoolkit.org) for this purpose but this cannot run on GAE because it spawns background threads.

We located a [Java projection library](http://www.jhlabs.com/java/maps/proj/index.html) based on PROJ.4 that we adapted for our needs.  We only implemented WGS:84 lat/lon, north and south polar stereographic projections, but more could be implemented.  Also, future versions of Geotoolkit might be adapted to work on GAE to provide support for the full EPSG coordinate system database.

## Other points ##

New virtual machines may be started up at any time, so it is advantageous to make applications as lightweight as possible.  Hence we avoided the use of Spring, which we employed in earlier versions of the software.

# Acknowledgments #

Many thanks to Jerry Huxtable for making his projection library available, and to J. David Eisenberg for his PNG library.