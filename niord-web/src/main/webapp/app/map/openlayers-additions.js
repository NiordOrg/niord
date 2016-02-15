/**
 * From: http://openlayers.org/en/v3.11.2/examples/custom-interactions.html
 *
 * Define a namespace for the application.
 */
var niord_ol = {};

/** ***************  Utility methods  ************ **/

/**
 * Based on the current mouse event, return a matching feature.
 * If source is defined, check that the feature belongs to the source.
 * If handler is defined, call the handler for the first matching feature and layer.
 * @param evt
 * @param source
 * @param handler callback function that is called for the first matching feature and layer
 */
function handleFeature(evt, source, handler) {

    var matchingFeature = undefined;
    var matchingLayer = undefined;

    evt.map.forEachFeatureAtPixel(evt.pixel,
        function(feature, layer) {
            if (matchingFeature) {
                return;
            }
            if (source) {
                source.forEachFeature(function (f) {
                    if (f === feature) {
                        matchingFeature = feature;
                        matchingLayer = layer;
                    }
                })
            } else {
                matchingFeature = feature;
                matchingLayer = layer;
            }
        });

    if (matchingFeature && matchingLayer && handler) {
        handler(matchingFeature, matchingLayer);
    }

    return matchingFeature;
}

/** ***************  Drag Interaction  **************** **/

/**
 * @constructor
 * @extends {ol.interaction.Pointer}
 */
niord_ol.Drag = function(options) {

    ol.interaction.Pointer.call(this, {
        handleDownEvent: niord_ol.Drag.prototype.handleDownEvent,
        handleDragEvent: niord_ol.Drag.prototype.handleDragEvent,
        handleMoveEvent: niord_ol.Drag.prototype.handleMoveEvent,
        handleUpEvent: niord_ol.Drag.prototype.handleUpEvent
    });

    /**
     * @type {ol.Pixel}
     * @private
     */
    this.coordinate_ = null;

    /**
     * @type {string|undefined}
     * @private
     */
    this.cursor_ = 'pointer';

    /**
     * @type {ol.Feature}
     * @private
     */
    this.feature_ = null;

    /**
     * @type {boolean}
     * @private
     */
    this.dragged_ = false;

    /**
     * @type {string|undefined}
     * @private
     */
    this.previousCursor_ = undefined;

    /**
     * @type {ol.source.Vector}
     * @private
     */
    this.source_ = options ? options.source : undefined;
};
ol.inherits(niord_ol.Drag, ol.interaction.Pointer);


/**
 * @param {ol.MapBrowserEvent} evt Map browser event.
 * @return {boolean} `true` to start the drag sequence.
 */
niord_ol.Drag.prototype.handleDownEvent = function(evt) {
    var map = evt.map;

    var feature = handleFeature(evt, this.source_);

    if (feature) {
        this.coordinate_ = evt.coordinate;
        this.feature_ = feature;
        this.dragged_ = false;
    }

    return !!feature;
};


/**
 * @param {ol.MapBrowserEvent} evt Map browser event.
 */
niord_ol.Drag.prototype.handleDragEvent = function(evt) {

    var deltaX = evt.coordinate[0] - this.coordinate_[0];
    var deltaY = evt.coordinate[1] - this.coordinate_[1];

    if (Math.abs(deltaX) > 0 || Math.abs(deltaY) > 0) {
        this.dragged_ = true;

        var geometry = /** @type {ol.geom.SimpleGeometry} */
            (this.feature_.getGeometry());
        geometry.translate(deltaX, deltaY);

        this.coordinate_[0] = evt.coordinate[0];
        this.coordinate_[1] = evt.coordinate[1];
    }
};


/**
 * @param {ol.MapBrowserEvent} evt Event.
 */
niord_ol.Drag.prototype.handleMoveEvent = function(evt) {
    if (this.cursor_) {
        var feature = handleFeature(evt, this.source_);
        var element = evt.map.getTargetElement();
        if (feature) {
            if (element.style.cursor != this.cursor_) {
                this.previousCursor_ = element.style.cursor;
                element.style.cursor = this.cursor_;
            }
        } else if (this.previousCursor_ !== undefined) {
            element.style.cursor = this.previousCursor_;
            this.previousCursor_ = undefined;
        }
    }
};


/**
 * @param {ol.MapBrowserEvent} evt Map browser event.
 * @return {boolean} `false` to stop the drag sequence.
 */
niord_ol.Drag.prototype.handleUpEvent = function(evt) {
    var that = this;
    if (this.dragged_) {
        this.dispatchEvent({
            type: "modifyend",
            features: {
                getArray: function () {
                    return [ that.feature_ ];
                }
            },
            target: this});
    }
    this.coordinate_ = null;
    this.feature_ = null;
    this.dragged_ = false;
    return false;
};


/** *****************  Remove Interaction  **************** **/

/**
 * @constructor
 * @extends {ol.interaction.Pointer}
 */
niord_ol.Remove = function(options) {

    ol.interaction.Pointer.call(this, {
        handleDownEvent: niord_ol.Remove.prototype.handleDownEvent
    });

    /**
     * @type {ol.source.Vector}
     * @private
     */
    this.source_ = options ? options.source : undefined;
};
ol.inherits(niord_ol.Remove, ol.interaction.Pointer);


/**
 * @param {ol.MapBrowserEvent} evt Map browser event.
 * @return {boolean} `true` if feature is deleted.
 */
niord_ol.Remove.prototype.handleDownEvent = function(evt) {

    var that = this;
    handleFeature(evt, this.source_, function (feature, layer) {
        layer.getSource().removeFeature(feature);
        that.dispatchEvent({
            type: "featureremove",
            features: {
                getArray: function () {
                    return [ feature ];
                }
            },
            target: that});
    });

    return false;
};


/** *****************  GeoJSON Circle support  **************** **/

/** Consider whether to support GeoJSON Circles
// Based on https://github.com/openlayers/ol3/pull/3434/files

ol.format.GeoJSON.readCircleGeometry_ = function(object) {
  goog.asserts.assert(object.type == 'Circle');
  return new ol.geom.Circle(object.center, object.radius);
};

ol.format.GeoJSON.writeCircleGeometry_ = function(geometry, opt_options) {
  goog.asserts.assertInstanceof(geometry, ol.geom.Circle,
      'geometry should be an ol.geom.Circle');
  return ({
    'type': 'Circle',
    'center': geometry.getCenter(),
    'radius': geometry.getRadius()
  });
};

ol.format.GeoJSON.GEOMETRY_READERS_ = {
    'Point': ol.format.GeoJSON.readPointGeometry_,
    'LineString': ol.format.GeoJSON.readLineStringGeometry_,
    'Polygon': ol.format.GeoJSON.readPolygonGeometry_,
    'MultiPoint': ol.format.GeoJSON.readMultiPointGeometry_,
    'MultiLineString': ol.format.GeoJSON.readMultiLineStringGeometry_,
    'MultiPolygon': ol.format.GeoJSON.readMultiPolygonGeometry_,
    'GeometryCollection': ol.format.GeoJSON.readGeometryCollectionGeometry_,
    'Circle': ol.format.GeoJSON.readCircleGeometry_
};

ol.format.GeoJSON.GEOMETRY_WRITERS_ = {
    'Point': ol.format.GeoJSON.writePointGeometry_,
    'LineString': ol.format.GeoJSON.writeLineStringGeometry_,
    'Polygon': ol.format.GeoJSON.writePolygonGeometry_,
    'MultiPoint': ol.format.GeoJSON.writeMultiPointGeometry_,
    'MultiLineString': ol.format.GeoJSON.writeMultiLineStringGeometry_,
    'MultiPolygon': ol.format.GeoJSON.writeMultiPolygonGeometry_,
    'GeometryCollection': ol.format.GeoJSON.writeGeometryCollectionGeometry_,
    'Circle': ol.format.GeoJSON.writeCircleGeometry_
};
**/
