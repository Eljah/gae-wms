package uk.ac.rdg.resc.basemaps.servlet;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import uk.ac.rdg.resc.basemaps.cache.ImageCache;
import uk.ac.rdg.resc.basemaps.persistent.TileStore;

/**
 * Entry point for the administration web pages.  Only authenticated administrators
 * are allowed to access this, as set in web.xml
 * @author Jon
 */
public class AdminServlet  {

    private ImageCache imageCache; // Will be injected by Spring
    private TileStore tileStore;

    /** Displays the front page of the admin app */
    public void displayAdminPage(HttpServletRequest request,
        HttpServletResponse response) throws Exception {

        UserService userService = UserServiceFactory.getUserService();
        String thisURL = request.getRequestURI();

        if (request.getUserPrincipal() == null) {
            // Shouldn't happen: web.xml should ensure that an admin user is
            // logged in.
            throw new Exception("Internal error: no user logged in");
        }
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("logoutURL", userService.createLogoutURL(thisURL));
        model.put("cacheStats", this.imageCache.getCacheStatistics());
        //return new ModelAndView("admin", model);
    }

    /** Clears the image cache */
    public void clearImageCache(HttpServletRequest request,
        HttpServletResponse response) throws IOException {
        this.imageCache.clear();
        response.sendRedirect("./");
    }

    /** Populates the persistent store with image tiles */
    public void loadImageTiles(HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        // Find out which image we want to load
        String imageName = request.getParameter("imagename");
        if (imageName == null) {
            throw new Exception("Must specify an image name");
        }

        // Load the images from the source files
        this.tileStore.loadTiles("WEB-INF/images/" + imageName);

        response.sendRedirect("./");
    }

    /** Called by Spring to set the image cache */
    public void setImageCache(ImageCache imageCache) {
        this.imageCache = imageCache;
    }

    /** Called by Spring to set the tile store */
    public void setTileStore(TileStore tileStore) {
        this.tileStore = tileStore;
    }

}
