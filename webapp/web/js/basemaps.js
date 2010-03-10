var map;
var polarMaxExtent = new OpenLayers.Bounds(-10700000, -10700000, 14700000, 14700000);
var lonLatMaxExtent = new OpenLayers.Bounds(-180, -90, 180, 90);

var halfSideLength = (polarMaxExtent.top - polarMaxExtent.bottom) / (4 * 2);
var centre = ((polarMaxExtent.top - polarMaxExtent.bottom) / 2) + polarMaxExtent.bottom;
var low = centre - halfSideLength;
var high = centre + halfSideLength;
var polarMaxResolution = (high - low) / 256;
var windowLow = centre - 2 * halfSideLength;
var windowHigh = centre + 2 * halfSideLength;
var polarWindow = new OpenLayers.Bounds(windowLow, windowLow, windowHigh, windowHigh);

window.onload = function()
{
    // Make sure Plate Carree is selected: sometimes browsers don't respect
    // the "selected" attribute when reloading pages
    document.getElementById("projection").selectedIndex=0;

    map = new OpenLayers.Map('map');

    // We set the buffer to 1 to avoid a large halo of tiles around the viewport.
    // This is only a demo so this won't matter - it's more important to ensure
    // that the demo doesn't place too much load on the server.
    var bluemarble = new OpenLayers.Layer.WMS("NASA Blue Marble",
        "wms", {layers: 'bluemarble_file', format: 'image/jpeg'}, {buffer : 1});

    map.addLayers([bluemarble]);
    map.addControl(new OpenLayers.Control.LayerSwitcher());
    map.addControl(new OpenLayers.Control.PanZoomBar());
    map.addControl(new OpenLayers.Control.MousePosition());

    map.zoomToMaxExtent();
}

// Called when the base layer projection is changed
function projectionChanged(crsCode)
{
    // Do nothing if the projection has not changed
    if (crsCode == map.projection) return;

    var maxExtent;
    var maxResolution;
    var units;

    if (crsCode == 'EPSG:4326') {
        units = 'degrees';
        maxExtent = lonLatMaxExtent;
        maxResolution = 360.0 / 256;
    }
    else if (crsCode == 'EPSG:32661' || crsCode == 'EPSG:32761') {
        // North or South polar stereographic
        units = 'm';
        maxExtent = polarWindow;
        maxResolution = polarMaxResolution;
    } else {
        alert('Unrecognized map projection ' + crsCode);
        return;
    }

    // We must recalculate the resolutions (really, OpenLayers should be able to do this itself)
    var resolutions = [maxResolution];
    for (var i = 1; i < map.numZoomLevels; i++) {
        var divisor = Math.pow(2, i);
        resolutions.push(maxResolution / divisor);
    }

    map.baseLayer.addOptions({
       projection: new OpenLayers.Projection(crsCode),
       maxExtent: maxExtent,
       maxResolution: maxResolution,
       resolutions: resolutions,
       units: units
    });

    map.setOptions({
       projection: crsCode,
       maxExtent: maxExtent,
       maxResolution: maxResolution,
       resolutions: resolutions,
       units: units
    });

    map.baseLayer.mergeNewParams({srs: crsCode});

    // We seem to need to do this when changing the extent of the map
    map.zoomToMaxExtent();
}
