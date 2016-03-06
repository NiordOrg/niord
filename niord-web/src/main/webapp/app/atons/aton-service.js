
/**
 * The aid-to-navigation service
 */
angular.module('niord.atons')

    /**
     * Interface for calling the application server
     */
    .factory('AtonService', [ '$http', function($http) {
        'use strict';

        // Adds a the given AtoN tag as a parameter if well-defined
        function addParam(url, aton, k) {
            var v = aton.tags[k];
            if (v && v.length > 0) {
                if (url.length > 0) {
                    url = url + '&'
                }
                url += encodeURIComponent(k) + '=' + encodeURIComponent(v);
            }
            return url;
        }

        // Constructs a URL for the overview AtoN icon
        function computeAtonIconUrl(aton) {

            var type = aton.tags['seamark:type'];
            if (!type) {
                return '/img/aton/aton.png';
            }

            var url = addParam('', aton, 'seamark:type');
            url = addParam(url, aton, 'seamark:' + type + ':category');
            url = addParam(url, aton, 'seamark:' + type + ':shape');
            url = addParam(url, aton, 'seamark:' + type + ':colour');
            url = addParam(url, aton, 'seamark:' + type + ':colour_pattern');
            url = addParam(url, aton, 'seamark:topmark:shape');
            url = addParam(url, aton, 'seamark:topmark:colour');
            url = addParam(url, aton, 'seamark:light:character');
            url = addParam(url, aton, 'seamark:light:colour');

            return '/rest/aton-icon/overview?' + url;
        }

        return {

            // Constructs a URL for the overview AtoN icon
            getAtonIconUrl: function(aton) {
                return computeAtonIconUrl(aton);
            },


            // Compute which icon to display for a given AtoN
            getAtonOLIcon: function(aton, zoom) {
                var iconUrl = computeAtonIconUrl(aton);
                var scale = Math.min(1.0, Math.max(0.7, zoom / 20.0));
                return new ol.style.Icon({
                    anchor: [ 0.33333333, 0.666666667 ],
                    scale: scale,
                    opacity: 1.0,
                    src: iconUrl
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


            getAtonSvg: function(aton) {
                return $http.post('/rest/aton-icon/svg/100', aton);
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

