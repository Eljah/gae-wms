/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.basemaps.utils;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a set of HTTP request samplers for JMeter, which contain only
 * the bounding boxes (in WGS84 lat-lon) of a tile pyramid.  We start with
 * two tiles representing the eastern and western hemisphere, then recursively
 * break these down into four square tiles.
 *
 * The downloadTile function can be used to download the images in the pyramid.
 * @author Jon
 */
public final class TileBoundsGenerator {

    /** XML that can be pasted into a JMeter test script */
    private static final String XML_TEMPLATE = "<HTTPSampler2 guiclass=\"HttpTestSampleGui2\" testclass=\"HTTPSampler2\" testname=\"${label}\" enabled=\"true\">\n" +
            "<elementProp name=\"HTTPsampler.Arguments\" elementType=\"Arguments\" guiclass=\"HTTPArgumentsPanel\" testclass=\"Arguments\" testname=\"User Defined Variables\" enabled=\"true\">\n" +
              "<collectionProp name=\"Arguments.arguments\">\n" +
                "<elementProp name=\"BBOX\" elementType=\"HTTPArgument\">\n" +
                  "<boolProp name=\"HTTPArgument.always_encode\">false</boolProp>\n" +
                  "<stringProp name=\"Argument.value\">${bbox}</stringProp>\n" +
                  "<stringProp name=\"Argument.metadata\">=</stringProp>\n" +
                  "<boolProp name=\"HTTPArgument.use_equals\">true</boolProp>\n" +
                  "<stringProp name=\"Argument.name\">BBOX</stringProp>\n" +
                "</elementProp>\n" +
              "</collectionProp>\n" +
            "</elementProp>\n" +
            "<stringProp name=\"HTTPSampler.domain\"></stringProp>\n" +
            "<stringProp name=\"HTTPSampler.port\"></stringProp>\n" +
            "<stringProp name=\"HTTPSampler.connect_timeout\"></stringProp>\n" +
            "<stringProp name=\"HTTPSampler.response_timeout\"></stringProp>\n" +
            "<stringProp name=\"HTTPSampler.protocol\"></stringProp>\n" +
            "<stringProp name=\"HTTPSampler.contentEncoding\"></stringProp>\n" +
            "<stringProp name=\"HTTPSampler.path\"></stringProp>\n" +
            "<stringProp name=\"HTTPSampler.method\">GET</stringProp>\n" +
            "<boolProp name=\"HTTPSampler.follow_redirects\">false</boolProp>\n" +
            "<boolProp name=\"HTTPSampler.auto_redirects\">true</boolProp>\n" +
            "<boolProp name=\"HTTPSampler.use_keepalive\">true</boolProp>\n" +
            "<boolProp name=\"HTTPSampler.DO_MULTIPART_POST\">false</boolProp>\n" +
            "<stringProp name=\"HTTPSampler.FILE_NAME\"></stringProp>\n" +
            "<stringProp name=\"HTTPSampler.FILE_FIELD\"></stringProp>\n" +
            "<stringProp name=\"HTTPSampler.mimetype\"></stringProp>\n" +
            "<boolProp name=\"HTTPSampler.monitor\">false</boolProp>\n" +
            "<stringProp name=\"HTTPSampler.embedded_url_re\"></stringProp>\n" +
          "</HTTPSampler2>\n";

    /** The URL from which to download images, minus the BBOX value */
    private static final String DOWNLOAD_URL =
            "INSERT BASE URL" +
            "wms?" +
            "&LAYERS=bluemarble" +
            "&FORMAT=image%2Fjpeg" +
            "&SERVICE=WMS" +
            "&VERSION=1.1.1" +
            "&REQUEST=GetMap" +
            "&STYLES=" +
            "&EXCEPTIONS=application%2Fvnd.ogc.se_inimage"+
            "&SRS=EPSG%3A4326" +
            "&WIDTH=256" +
            "&HEIGHT=256" +
            "&BBOX=";

    private TileBoundsGenerator() { throw new AssertionError(); }

    public static void main(String[] args) throws Exception {

        int numZoomLevels = 3;

        List<Map<String, double[]>> tilesByLevel = new ArrayList<Map<String, double[]>>();
        tilesByLevel.add(getTopLevelTiles());

        for (int i = 0; i < numZoomLevels - 1; i++) {
            tilesByLevel.add(getNextLevelTiles(tilesByLevel.get(i)));
        }

        // Print out results
        int n = 0;
        for (Map<String, double[]> tiles : tilesByLevel) {
            for (Map.Entry<String, double[]> tile : tiles.entrySet()) {
                //System.out.println(tileToXml(tile.getKey(), tile.getValue()) + "<hashTree/>");
                downloadTile(tile.getKey(), tile.getValue());
                n++;
            }
        }

        System.out.println(n);

    }

    private static Map<String, double[]> getTopLevelTiles() {
        Map<String, double[]> topLevelTiles = new LinkedHashMap<String, double[]>();
        // Western hemisphere
        topLevelTiles.put("W", new double[]{-180.0, -90.0,   0.0, 90.0});
        // Eastern hemisphere
        topLevelTiles.put("E", new double[]{   0.0, -90.0, 180.0, 90.0});
        return topLevelTiles;
    }

    private static Map<String, double[]> getNextLevelTiles(Map<String, double[]> parentTiles) {
        Map<String, double[]> nextLevelTiles = new LinkedHashMap<String, double[]>();
        for (Map.Entry<String, double[]> tile : parentTiles.entrySet()) {
            nextLevelTiles.putAll(getTiles(tile.getKey(), tile.getValue()));
        }
        return nextLevelTiles;
    }

    private static Map<String, double[]> getTiles(String parentTileLabel, double[] parentTileBbox) {
        double midPointX = (parentTileBbox[0] + parentTileBbox[2]) / 2.0;
        double midPointY = (parentTileBbox[1] + parentTileBbox[3]) / 2.0;
        Map<String, double[]> map = new LinkedHashMap<String, double[]>();

        // Bottom left tile
        map.put(parentTileLabel + "-SW", new double[]{
            parentTileBbox[0],
            parentTileBbox[1],
            midPointX,
            midPointY
        });

        // Bottom right tile
        map.put(parentTileLabel + "-SE", new double[]{
            midPointX,
            parentTileBbox[1],
            parentTileBbox[2],
            midPointY
        });

        // Top left tile
        map.put(parentTileLabel + "-NW", new double[]{
            parentTileBbox[0],
            midPointY,
            midPointX,
            parentTileBbox[3]
        });

        // Top right tile
        map.put(parentTileLabel + "-NE", new double[]{
            midPointX,
            midPointY,
            parentTileBbox[2],
            parentTileBbox[3]
        });

        return map;
    }

    private static String tileToString(String label, double[] bbox) {
        return String.format("%s: %s", label, Arrays.toString(bbox));
    }

    private static String tileToXml(String label, double[] bbox) {
        return XML_TEMPLATE
            .replace("${label}", label)
            .replace("${bbox}", String.format("%s,%s,%s,%s", bbox[0], bbox[1], bbox[2], bbox[3]));
    }

    /** Downloads the tile from the GAE server */
    private static void downloadTile(String label, double[] bbox) throws Exception {
        String url = String.format("%s%s,%s,%s,%s", DOWNLOAD_URL,  bbox[0], bbox[1], bbox[2], bbox[3]);
        System.out.println(url);
        InputStream in = new URL(url).openStream();
        FileOutputStream out = new FileOutputStream("c:\\documents and settings\\jon\\desktop\\tiles\\" + label + ".jpeg");
        byte data[] = new byte[1024];
        int count = 0;
        while (count >= 0) {
            count = in.read(data);
            if (count >= 0) {
                out.write(data, 0, count);
            }
        }
        out.close();
        in.close();
    }

}
