# Introduction #

This describes how the tests were performed for the [COM.Geo 2010 paper](http://gae-wms.googlecode.com/files/Results%20for%20COM.Geo%202010%20paper.zip).

# Test plan #

We used Apache JMeter (http://jakarta.apache.org/jmeter/) to set up and run a number of test scripts, each of which involved a single client machine making repeated requests to the server for images.  Each test started with a single thread, ramping up linearly over at least 30 seconds to a maximum number of threads, which were then sustained for at least a further 30 seconds.  Each thread looped repeatedly through a set of 42 image requests in random order.  Each image was of size 256x256 pixels, representing three “zoom levels” in WGS:84 latitude-longitude coordinates (the first zoom level contains two images, one covering each hemisphere; the second level splits each of these images into four, and so forth).

We tested three different server configurations.  In the “fully-dynamic” configuration, all images were generated dynamically from the source data, with no caching taking place within the application.  In the “self-caching” configuration, the server was set up to hold all generated images in the GAE memcache system as JPEGs or PNGs.  Subsequent identical requests receive the image directly from the memcache (tiling clients will naturally produce a number of identical requests).  Finally, in the “static files” configuration, the client was set up to request static image files from the server, bypassing the WMS interface.  (This last configuration is similar to that of a TileCache server, http://tilecache.org/.)

In each of the three test suites, a number of test scripts were run, with different maximum numbers of threads.  The throughput (the number of images received by the client from the server per second) was calculated by calculating the mean and standard deviations of a large number of “instantaneous” throughputs, each of which was estimated by calculating the time required to receive a certain number of images (typically 10) in a sliding window over the dataset.  Care was taken to verify that the figures for throughput were representative and not affected by transients.  The average latency in each case (the time a client observes between a request being made and the first response) was calculated by averaging over the recorded latencies in the same time window as was used to calculate the throughput.

Unfortunately we only had time to test a single client accessing the service at any one time.  It would be very instructive (and a more realistic test of capability) to perform a distributed test, in which many clients are accessing the server.

# Data analysis #

JMeter produces output in XML format, which we converted to HTML tabular form using an XSLT transformation.  These were then into Excel for analysis.

The data can be downloaded from [here](http://gae-wms.googlecode.com/files/Results%20for%20COM.Geo%202010%20paper.zip).