
/**
 * The aid-to-navigation service
 */
angular.module('niord.atons')

    /**
     * Interface for calling the application server
     */
    .factory('AtonService', [ '$http', function($http) {
        'use strict';

        // TEST: Try use a couple of the icons from
        //       https://github.com/OpenSeaMap/online_chart
        var atonIcons = {
            'light'         : { anchor: [0.5, 0.5],  icon : 'Lighthouse_Major.png' },
            'light_major'   : { anchor: [0.5, 0.5],  icon : 'Lighthouse_Major.png' },
            'light_minor'   : { anchor: [0.5, 0.5],  icon : 'Lighthouse_Major.png' },
            'buoy_special_purpose'    : { anchor: [0.4, 0.95], icon : 'Special_Purpose.png' },
            'beacon_special_purpose'    : { anchor: [0.4, 0.95], icon : 'Special_Purpose.png' },
            'buoy_lateral'    : { anchor: [0.4, 0.95], icon : 'Lateral_Green.png' },
            'beacon_lateral'    : { anchor: [0.4, 0.95], icon : 'Lateral_Green.png' },
            'buoy_cardinal'   : { anchor: [0.4, 0.95], icon : 'Cardinal_North.png' },
            'beacon_cardinal'   : { anchor: [0.4, 0.95], icon : 'Cardinal_North.png' },
            'buoy_safe_water'  : { anchor: [0.4, 0.95], icon : 'Lateral_SafeWater.png' },
            'beacon_safe_water'  : { anchor: [0.4, 0.95], icon : 'Lateral_SafeWater.png' },
            'buoy_isolated_danger'    : { anchor: [0.4, 0.95], icon : 'Cardinal_Single.png' },
            'beacon_isolated_danger'    : { anchor: [0.4, 0.95], icon : 'Cardinal_Single.png' }
        };


        return {

            getAtonIconUrl: function(aton) {
                var icon = atonIcons[aton.tags['seamark:type']];
                if (icon) {
                    return '/img/aton/' + icon.icon;
                }
                return '/img/aton/aton1.png';
            },

            // Compute which icon to display for a given AtoN
            getAtonOLIcon: function(aton, zoom) {
                var icon = atonIcons[aton.tags['seamark:type']];
                if (icon) {
                    var scale = Math.min(1.0, Math.max(0.1, zoom / 50.0));
                    return new ol.style.Icon({
                        anchor: icon.anchor,
                        scale: scale,
                        opacity: 1.0,
                        src: '/img/aton/' + icon.icon
                    })
                }
                return new ol.style.Icon({
                    anchor: [0.5, 0.5],
                    scale: 0.08,
                    opacity: 1.0,
                    src: '/img/aton/aton1.png'
                })
            },


            // Returns the selection icon to use with a selected AtoN
            getAtonSelectionOLIcon: function() {
                return new ol.style.Icon({
                    anchor: [0.5, 0.5],
                    scale: 0.2,
                    opacity: 1.0,
                    src: '/img/aton/select.png'
                })
            },

            // Compute the label to display for a given AtoN
            getAtonLabel: function(aton, zoom) {
                // it easily becomes cluttered, so, only show labels when you have zoomed in a lot...
                if (zoom <= 15) {
                    return undefined;
                }
                return new ol.style.Text({
                    textAlign: 'center',
                    font: 'Arial',
                    text: aton.tags['seamark:ref'],
                    fill: new ol.style.Fill({color: 'red'}),
                    stroke: new ol.style.Stroke({color: 'white', width: 2.0}),
                    offsetX: 0,
                    offsetY: 15
                })
            },


            getAtonUid: function(aton) {
                return aton ? aton.tags['seamark:ref'] : undefined;
            },


            getAton: function(atonUid, success, error) {
                $http.get('/rest/atons/' + encodeURI(atonUid))
                    .success(success)
                    .error(error);
            },


            searchAtonsByExtent: function(extent, maxAtonNo, success, error) {
                var params = 'maxAtonNo=' + maxAtonNo + '&emptyOnOverflow=true';
                if (extent && extent.length == 4) {
                    params += '&minLon=' + extent[0] + '&minLat=' + extent[1]
                           +  '&maxLon=' + extent[2] + '&maxLat=' + extent[3];
                }

                $http.get('/rest/atons/search?' + params)
                    .success(success)
                    .error(error);
            },

            searchAtonsByParams: function(params, success, error) {
                $http.get('/rest/atons/search?' + params)
                    .success(success)
                    .error(error);
            }

        };
    }]);

