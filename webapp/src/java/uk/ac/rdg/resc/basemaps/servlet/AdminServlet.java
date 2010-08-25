package uk.ac.rdg.resc.basemaps.servlet;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.cache.CacheStatistics;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import uk.ac.rdg.resc.basemaps.cache.ImageCache;

/**
 * Entry point for the administration web pages.  Only authenticated administrators
 * are allowed to access this, as set in web.xml
 * @author Jon
 */
public class AdminServlet extends HttpServlet {

    private final ImageCache imageCache = ImageCache.INSTANCE;
    //private TileStore tileStore;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        if ("clearImageCache".equals(request.getParameter("op"))) {
            this.imageCache.clear();
            response.sendRedirect("admin");
            return;
        }

        UserService userService = UserServiceFactory.getUserService();
        String thisURL = request.getRequestURI();
        CacheStatistics cacheStats = this.imageCache.getCacheStatistics();

        if (request.getUserPrincipal() == null) {
            // Shouldn't happen: web.xml should ensure that an admin user is
            // logged in.
            throw new ServletException("Internal error: no user logged in");
        }

        PrintWriter writer = response.getWriter();
        writer.println("<html><head><title>Admin page</title</head>");
        writer.println("<body>");

        writer.printf("<h1>Welcome %s!</h1>%n", request.getUserPrincipal().getName());
        writer.printf("<p>You can <a href=\"%s\">Logout</a></p>%n", userService.createLogoutURL(thisURL));

        writer.println("<h1>Cache statistics</h1>");
        writer.printf("<p><b>Cache hits:</b> %s</p>%n", cacheStats.getCacheHits());
        writer.printf("<p><b>Cache misses:</b> %s</p>%n", cacheStats.getCacheMisses());
        writer.printf("<p><b>Cache size:</b> %s</p>%n", cacheStats.getObjectCount());

        writer.println("<form action=\"admin\" method=\"get\">");
        writer.println("<input type=\"hidden\" name=\"op\" value=\"clearImageCache\"/>");
        writer.println("<button type=\"submit\" onclick=\"return confirm('Are you sure you want to clear the cache?');\">Clear cache</button>");
        writer.println("</form>");

        writer.println("</body></html>");
    }

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
    /*public void loadImageTiles(HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        // Find out which image we want to load
        String imageName = request.getParameter("imagename");
        if (imageName == null) {
            throw new Exception("Must specify an image name");
        }

        // Load the images from the source files
        this.tileStore.loadTiles("WEB-INF/images/" + imageName);

        response.sendRedirect("./");
    }*/

}
