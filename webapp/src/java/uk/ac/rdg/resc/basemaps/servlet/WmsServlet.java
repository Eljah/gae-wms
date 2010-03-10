package uk.ac.rdg.resc.basemaps.servlet;

import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.Transform;
import com.google.appengine.repackaged.com.google.common.collect.Sets;
import com.keypoint.PngEncoder;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.cache.CacheException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import uk.ac.rdg.resc.basemaps.cache.CacheKey;
import uk.ac.rdg.resc.basemaps.GeoreferencedImage;
import uk.ac.rdg.resc.basemaps.ArrayBackedImage;
import uk.ac.rdg.resc.basemaps.CachingImageMosaic;
import uk.ac.rdg.resc.basemaps.FileBackedImageMosaic;
import uk.ac.rdg.resc.basemaps.Image;
import uk.ac.rdg.resc.basemaps.Projection2;
import uk.ac.rdg.resc.basemaps.cache.ImageCache;
import uk.ac.rdg.resc.basemaps.persistent.TileStore;
import uk.ac.rdg.resc.ncwms.controller.GetMapDataRequest;
import uk.ac.rdg.resc.ncwms.controller.GetMapRequest;
import uk.ac.rdg.resc.ncwms.controller.RequestParams;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidCrsException;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidFormatException;
import uk.ac.rdg.resc.ncwms.exceptions.LayerNotDefinedException;
import uk.ac.rdg.resc.ncwms.exceptions.OperationNotSupportedException;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;

/**
 * Entry point for the Web Map Service.
 * @author Jon
 */
public class WmsServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(WmsServlet.class.getName());

    private static final String JPEG_FORMAT = "image/jpeg";
    private static final String PNG_FORMAT  = "image/png";

    private static final Set<String> SUPPORTED_IMAGE_FORMATS =
            Sets.newHashSet(JPEG_FORMAT, PNG_FORMAT);

    private static final Map<String, Projection2> SUPPORTED_PROJECTIONS
            = new HashMap<String, Projection2>();

    /** The maximum size of image (side length) that can be generated by this server */
    private static final int MAX_IMAGE_SIZE = 512;

    /** In-memory cache of source images.  The keys are the layer names, the values are the images */
    private final Map<String, GeoreferencedImage> images = new HashMap<String, GeoreferencedImage>();

    /** Cache of generated images.  */
    private ImageCache imageCache;

    /** Persistent store of source image tiles.  */
    private final TileStore tileStore = TileStore.getInstance();

    // We use this field to detect the first GetMap request of a new JVM instance
    private boolean firstTime = true;

    /** For logging: possible sources for a GetMap image */
    private enum ImageSource {
        /** Image was found in the memcache: no processing necessary */
        FOUND_IN_CACHE,
        /** PNG version of the same image was found in the cache then converted to JPEG */
        CONVERTED_FROM_CACHED_PNG,
        /** Cache not used at all: generated from scratch */
        NEWLY_GENERATED;
    }

    @Override
    public void init() throws ServletException {
        log.info("Initializing WmsServlet");

        try {
            this.imageCache = new ImageCache();
        } catch (CacheException ce) {
            throw new ServletException(ce);
        }

        // Populate the map of supported coordinate systems for images
        for (Projection2 proj : Projection2.values()) {
            for (String code : proj.getCodes()) {
                SUPPORTED_PROJECTIONS.put(code, proj);
            }
        }

        // Create an Image that wraps the files containing the image data
        log.fine("Creating mosaiced blue marble image");
        //Image mosaicedBlueMarble = new ImageFileMosaic(new File("WEB-INF/images/bluemarble_5400x2700"),
        //        5400, 2700);

        // Split this image up into <1MB chunks that can be memcached and stored
        // in the persistent store, then re-assemble as another Image.
        CachingImageMosaic cachedMosaicedBlueMarble = new CachingImageMosaic(
            "bluemarble",
            5400,
            2700,
            this.tileStore
        );

        FileBackedImageMosaic fileBackedBlueMarble = new FileBackedImageMosaic(
            "bluemarble_file", 5400, 2700, new File("WEB-INF/images/bluemarble"));

        // Close all the files associated with the file-backed image
        //mosaicedBlueMarble.dispose();

        // Wrap the image in a georeferencing wrapper and store in the Map
        this.images.put("bluemarble",      new GeoreferencedImage(cachedMosaicedBlueMarble));
        this.images.put("bluemarble_file", new GeoreferencedImage(fileBackedBlueMarble));
    }

    /*private void loadBitmap(String name) throws IOException, InvalidBitmapException {
        InputStream in = null;
        try {
            in = new FileInputStream("WEB-INF/images/" + name + ".bmp");
            // This image spans the whole world
            this.images.put(name, new GeoreferencedImage(BMPLoader.readBMPImage(in)));
        } finally {
            if (in != null) in.close();
        }
    }*/

    /** Entry point for WMS requests */
    @Override
    protected void doGet(HttpServletRequest request,
        HttpServletResponse response) throws ServletException, IOException {

        // Wrap the request parameters in an object that provides more convenient access
        RequestParams params = new RequestParams(request.getParameterMap());

        try {
            // Check the REQUEST parameter to see if we're producing a
            // capabilities document or a map image
            String operation = params.getMandatoryString("request");
            /*if (operation.equals("GetCapabilities")) {
                return getCapabilities(params, request);
            } else */ if (operation.equals("GetMap")) {
                getMap(params, response);
            } else {
                throw new OperationNotSupportedException(operation);
            }
        } catch (WmsException wmse) {
            throw new ServletException(wmse);
        }
    }

    /**
     * Executes the GetCapabilities operation, returning a ModelAndView for
     * display of the information as an XML document.  If the user has
     * requested VERSION=1.1.1 the information will be rendered using
     * <tt>web/WEB-INF/jsp/capabilities_xml_1_1_1.jsp</tt>.  If the user
     * specifies VERSION=1.3.0 (or does not specify a version) the information
     * will be rendered using <tt>web/WEB-INF/jsp/capabilities_xml.jsp</tt>.
     */
    protected void getCapabilities(RequestParams params,
            HttpServletRequest httpServletRequest) throws WmsException {
        // Check the SERVICE parameter
        String service = params.getMandatoryString("service");
        if (!service.equals("WMS")) {
            throw new WmsException("The value of the SERVICE parameter must be \"WMS\"");
        }

        // Check the VERSION parameter (not compulsory for GetCapabilities)
        String versionStr = params.getWmsVersion();

        // Check the FORMAT parameter
        String format = params.getString("format");
        // The WMS 1.3.0 spec says that we can respond with the default text/xml
        // format if the client has requested an unknown format.  Hence we do
        // nothing here.


        Map<String, Object> models = new HashMap<String, Object>();
        models.put("title", "WMS basemaps server on Google App Engine");
        models.put("abstract", "An experimental Web Map Service for common basemap images");
        models.put("lastUpdate", "2009-10-28T00:00:00Z");
        models.put("wmsBaseUrl", httpServletRequest.getRequestURL().toString());
        models.put("maxImageSize", MAX_IMAGE_SIZE);
        models.put("supportedCrsCodes", SUPPORTED_PROJECTIONS.keySet());
        models.put("supportedImageFormats", SUPPORTED_IMAGE_FORMATS);
        models.put("layerLimit", 1);

        // Do WMS version negotiation.  From the WMS 1.3.0 spec:
        // * If a version unknown to the server and higher than the lowest
        //   supported version is requested, the server shall send the highest
        //   version it supports that is less than the requested version.
        // * If a version lower than any of those known to the server is requested,
        //   then the server shall send the lowest version it supports.
        // We take the version to be 1.3.0 if not specified
        WmsVersion wmsVersion = versionStr == null
                ? WmsVersion.VERSION_1_3_0
                : new WmsVersion(versionStr);
        if (wmsVersion.compareTo(WmsVersion.VERSION_1_3_0) >= 0) {
            // version is >= 1.3.0. Send 1.3.0 Capabilities
            //return new ModelAndView("capabilities_xml", models);
        } else {
            // version is < 1.3.0. Send 1.1.1 Capabilities
            //return new ModelAndView("capabilities_xml_1_1_1", models);
        }
    }

    /** Implements the GetMap operation */
    private void getMap(RequestParams params, HttpServletResponse response)
        throws WmsException, IOException {
        
        long getMapStart = System.nanoTime();

        // Check to see if this is the first request of this JVM instance
        // We use the "double-check" idiom (Bloch 2nd Ed, item 71) to avoid the
        // cost of locking where possible
        boolean firstRequest = false;
        if (this.firstTime) {
            synchronized(this) {
                if (this.firstTime) {
                    this.firstTime = false;
                    firstRequest = true;
                }
            }
        }

        // Parse the URL parameters and check validity
        GetMapRequest getMapRequest = new GetMapRequest(params);
        GetMapDataRequest dr = getMapRequest.getDataRequest();

        // Check that we have a source image for the requested layer
        String[] layers = dr.getLayers();
        if (layers.length > 1) {
            throw new WmsException("You may only request a maximum of " +
                "1 layer simultaneously from this server");
        }
        GeoreferencedImage sourceImage = images.get(layers[0]);
        if (sourceImage == null) {
            throw new LayerNotDefinedException(layers[0]);
        }

        // Check that we can support the requested image file type
        String mimeType = getMapRequest.getStyleRequest().getImageFormat();
        if (!SUPPORTED_IMAGE_FORMATS.contains(mimeType)) {
            throw new InvalidFormatException("The image format " + mimeType +
                    " is not supported by this server");
        }

        // Check that the size of the requested image is within limits
        if (dr.getWidth() > MAX_IMAGE_SIZE || dr.getHeight() > MAX_IMAGE_SIZE) {
            throw new WmsException("Requested image is too large (exceeds "
                    + MAX_IMAGE_SIZE + "x" + MAX_IMAGE_SIZE + ")");
        }

        // See if the client has requested not to use the cache (debug only)
        boolean avoidCache = "true".equalsIgnoreCase(params.getString("no_cache"));

        // We assume the image has been newly generated unless we find otherwise
        final ImageSource imageSource;
        // We're interested to know about any space saving by JPEG compression
        // This will not be relevant if the image was found in the cache, or
        // if the user has requested a PNG
        Integer bytesSavedInJpegCompression = null;

        // Look to see if we have a matching image in the cache
        CacheKey cacheKey = new CacheKey(layers[0], mimeType, dr.getCrsCode(),
                dr.getBbox(), dr.getWidth(), dr.getHeight());
        byte[] imageBytes = readImageFromCache(cacheKey, avoidCache);

        if (imageBytes == null)
        {
            // No matching image in the cache
            if (mimeType.equals(JPEG_FORMAT))
            {
                // We haven't found a JPEG in the cache but we might be able to
                // find a PNG with the same information
                CacheKey pngCacheKey = new CacheKey(layers[0], PNG_FORMAT, dr.getCrsCode(),
                    dr.getBbox(), dr.getWidth(), dr.getHeight());
                imageBytes = readImageFromCache(pngCacheKey, avoidCache);

                if (imageBytes == null) {
                    // There was no PNG in the cache so we must create one
                    //((CachingImageMosaic)sourceImage.getImage()).testCache();
                    imageBytes = readPng(dr, sourceImage);
                    // We put it in the cache
                    this.imageCache.putImage(pngCacheKey, imageBytes);
                    imageSource = ImageSource.NEWLY_GENERATED;
                }
                else
                {
                    // We found a PNG in the cache
                    imageSource = ImageSource.CONVERTED_FROM_CACHED_PNG;
                }

                // We must convert the PNG to a JPEG
                int pngSize = imageBytes.length;
                imageBytes = convertPngToJpeg(imageBytes);
                bytesSavedInJpegCompression = pngSize - imageBytes.length;
            }
            else
            {
                // We were looking for a PNG and didn't find one
                imageBytes = readPng(dr, sourceImage);
                imageSource = ImageSource.NEWLY_GENERATED;
            }
            // We put the requested image in the cache
            this.imageCache.putImage(cacheKey, imageBytes);
        }
        else
        {
            imageSource = ImageSource.FOUND_IN_CACHE;
        }

        // Write the headers, which we use for debugging and monitoring what's
        // going on on the server
        if ("true".equalsIgnoreCase(params.getString("debug_headers"))) {
            response.setHeader("X-WmsBasemaps-firstRequest", "" + firstRequest);
            response.setHeader("X-WmsBasemaps-imageSource", imageSource.toString());
            response.setHeader("X-WmsBasemaps-getMapTime", "" + (System.nanoTime() - getMapStart));
            if (bytesSavedInJpegCompression != null) {
                response.setHeader("X-WmsBasemaps-bytesSavedInJpegCompression",
                        "" + bytesSavedInJpegCompression.intValue());
            }
        }

        // Write the image to the client
        response.setContentType(mimeType);
        OutputStream os = null;
        try {
            os = response.getOutputStream();
            os.write(imageBytes);
        } catch(IOException ioe) {
            // We swallow these exceptions.  They most commonly occur when the
            // client disconnects, which is very frequently when using OpenLayers.
            // If we were to throw these exceptions, the Controller would attempt
            // to write the exception as XML to the client, but the OutputStream
            // would have already been opened and hence in an illegal state.
            // We could log these messages, but there are likely to be lots of them...
        } finally {
            if (os != null) {
                // Exception swallowed for the same reason as above.
                try { os.close(); } catch(IOException ioe) {}
            }
        }
    }

    private byte[] readImageFromCache(CacheKey key, boolean avoidCache)
    {
        if (avoidCache) return null;
        else return this.imageCache.getImage(key);
    }

    private static byte[] readPng(GetMapDataRequest dr, GeoreferencedImage sourceImage)
        throws InvalidCrsException, IOException
    {
        // Get the requested projection of the image
        Projection2 proj = SUPPORTED_PROJECTIONS.get(dr.getCrsCode());
        if (proj == null) {
            throw new InvalidCrsException(dr.getCrsCode());
        }

        // The image is not in the cache.
        // Create the image we shall build up, pixel by pixel
        Image im = new ArrayBackedImage(dr.getWidth(), dr.getHeight());
        GeoreferencedImage targetImage = new GeoreferencedImage(im, dr.getBbox(), proj);

        // The line below reads all the pixels from the source image in one
        // operation, meaning that sourceImage can optimize the use of i/o
        int[] targetPixels = sourceImage.getNearestPixels(targetImage.getLonLatsList());
        int pixelIndex = 0;
        for (int j = 0; j < im.getHeight(); j++) {
            for (int i = 0; i < im.getWidth(); i++) {
                im.setPixel(i, j, targetPixels[pixelIndex]);
                pixelIndex++;
            }
        }

        // Create the image itself as a PNG.  I can't find any suitable and
        // compatible code that will encode directly as JPEG.
        PngEncoder pngEnc = new PngEncoder(
                targetImage.getImage(), // the image to encode
                false, // don't encode the alpha channel - these are background maps
                0, // don't use a filter (I don't even know what this means ;-)
                9 // Use maximum compression (or we might run out of free GAE bandwidth)
                  // This can reduces the size of the images considerably,
                  // but not by as much as JPEG compression
                  // Would be interesting to view some stats on that... (TODO)
        );

        return pngEnc.pngEncode();
    }

    /**
     * Uses Google App Engine's image service to convert the given PNG image
     * to JPEG.
     */
    private static byte[] convertPngToJpeg(byte[] pngBytes) {
        com.google.appengine.api.images.Image im = ImagesServiceFactory.makeImage(pngBytes);
        ImagesService imService = ImagesServiceFactory.getImagesService();
        // This transformation does nothing.
        Transform nullTransform = ImagesServiceFactory.makeRotate(0);
        imService.applyTransform(nullTransform, im, ImagesService.OutputEncoding.JPEG);
        return im.getImageData();
    }

    /** Cleans up any resources */
    @Override
    public void destroy() {
        log.info("disposing WmsController");
        for (GeoreferencedImage geoImage : this.images.values()) {
            geoImage.getImage().dispose();
        }
    }

    /**
     * Represents a WMS version number.
     */
    private static final class WmsVersion implements Comparable<WmsVersion> {

        private Integer value; // Numerical value of the version number,
        // used for comparisons
        private String str;
        private int hashCode;
        public static final WmsVersion VERSION_1_1_1 = new WmsVersion("1.1.1");
        public static final WmsVersion VERSION_1_3_0 = new WmsVersion("1.3.0");

        /**
         * Creates a new WmsVersion object based on the given String
         * (e.g. "1.3.0")
         * @throws IllegalArgumentException if the given String does not represent
         * a valid WMS version number
         */
        public WmsVersion(String versionStr) {
            String[] els = versionStr.split("\\.");  // regex: split on full stops
            if (els.length != 3) {
                throw new IllegalArgumentException(versionStr +
                        " is not a valid WMS version number");
            }
            int x, y, z;
            try {
                x = Integer.parseInt(els[0]);
                y = Integer.parseInt(els[1]);
                z = Integer.parseInt(els[2]);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(versionStr +
                        " is not a valid WMS version number");
            }
            if (y > 99 || z > 99) {
                throw new IllegalArgumentException(versionStr +
                        " is not a valid WMS version number");
            }
            // We can calculate all these values up-front as this object is
            // immutable
            this.str = x + "." + y + "." + z;
            this.value = (100 * 100 * x) + (100 * y) + z;
            this.hashCode = 7 + 79 * this.value.hashCode();
        }

        /**
         * Compares this WmsVersion with the specified Version for order.  Returns a
         * negative integer, zero, or a positive integer as this Version is less
         * than, equal to, or greater than the specified Version.
         */
        @Override
        public int compareTo(WmsVersion otherVersion) {
            return this.value.compareTo(otherVersion.value);
        }

        /**
         * @return String representation of this version, e.g. "1.3.0"
         */
        @Override
        public String toString() {
            return this.str;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj instanceof WmsVersion) {
                final WmsVersion other = (WmsVersion) obj;
                return this.value.equals(other.value);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }

}
