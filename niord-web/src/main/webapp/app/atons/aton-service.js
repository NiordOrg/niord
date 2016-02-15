
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
            'FYRLST'        : { anchor: [0.5, 0.5],  icon : 'Lighthouse_Major.png' },
            'SPEC m/top'    : { anchor: [0.4, 0.95], icon : 'Special_Purpose.png' },
            'SPEC,med top'  : { anchor: [0.4, 0.95], icon : 'Special_Purpose.png' },
            'SPEC u/top'    : { anchor: [0.4, 0.95], icon : 'Special_Purpose.png' },
            'STAR m/top'    : { anchor: [0.4, 0.95], icon : 'Lateral_Green.png' },
            'PORT m/top'    : { anchor: [0.4, 0.95], icon : 'Lateral_Red.png' },
            'STAR u/top'    : { anchor: [0.4, 0.95], icon : 'Lateral_Green.png' },
            'PORT u/top'    : { anchor: [0.4, 0.95], icon : 'Lateral_Red.png' },
            'N-CAR m/top'   : { anchor: [0.4, 0.95], icon : 'Cardinal_North.png' },
            'S-CAR m/top'   : { anchor: [0.4, 0.95], icon : 'Cardinal_South.png' },
            'E-CAR m/top'   : { anchor: [0.4, 0.95], icon : 'Cardinal_East.png' },
            'W-CAR m/top'   : { anchor: [0.4, 0.95], icon : 'Cardinal_West.png' },
            'W-SAFE m/top'  : { anchor: [0.4, 0.95], icon : 'Lateral_SafeWater.png' },
            'SAFE m/top'    : { anchor: [0.4, 0.95], icon : 'Lateral_SafeWater.png' },
            'IS D m/top'    : { anchor: [0.4, 0.95], icon : 'Cardinal_Single.png' }
        };


        return {

            getAtonIconUrl: function(aton) {
                var icon = atonIcons[aton.code];
                if (icon) {
                    return '/img/aton/' + icon.icon;
                }
                return '/img/aton/aton' + aton.type + '.png';
            },

            // Compute which icon to display for a given AtoN
            getAtonOLIcon: function(aton, zoom) {
                var icon = atonIcons[aton.code];
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
                    src: '/img/aton/aton' + aton.type + '.png'
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
                    text: aton.atonUid,
                    fill: new ol.style.Fill({color: 'red'}),
                    stroke: new ol.style.Stroke({color: 'white', width: 2.0}),
                    offsetX: 0,
                    offsetY: 15
                })
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

