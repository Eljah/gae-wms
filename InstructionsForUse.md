# Introduction #

The software comes in two parts.  The "webapp" is the code that is uploaded to Google App Engine.  The "utils" are standalone command-line Java apps for preparing data for upload.

These instructions are not very detailed, but should be enough for developers who are familiar with developing Java web apps for Google App Engine.  More detailed instructions could be provided if someone makes a request in the comments.

# Preparing data #

Download a high-resolution image of any format that can be read by Java ImageIO.  In the COM.Geo study we used a 5400x2700 NASA [Blue Marble Next Generation](http://earthobservatory.nasa.gov/Features/BlueMarble/) JPEG image.  Run the ImageMosaicGenerator class from the utils package on this image to generate a set of files containing 500x500 pixel arrays (called `*`.tile).

# Preparing and uploading the web application #

Check out the web application code from http://gae-wms.googlecode.com/svn/trunk/webapp.  (The code was built in NetBeans 6.8, but there are no NetBeans project files in the checkout, therefore you will have to import the code into a new project.)  Copy the tiles generated in the previous section to `web/WEB-INF/myimagename/`.

You will need to edit WmsServlet.java, adding the path to your image to the call to `new FileBackedImageMosaic()` in the `init()` method.

Create an application on Google App Engine, then add its name to the `WEB-INF/appengine-web.xml` config file.

You'll need commons-logging-1.1.jar, joda-time-1.6.jar as well as the GAE dependencies.

You should now be able to deploy the web application.