
/*
 * Copyright 2016 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * The aid-to-navigation service
 */
angular.module('niord.atons')

    /**
     * Interface for calling the application server
     */
    .factory('AtonService', [ '$rootScope', '$http', '$uibModal',
        function($rootScope, $http, $uibModal) {
        'use strict';

        /** Adds a the given AtoN tag as a parameter if well-defined */
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


        /** Constructs a URL for the overview AtoN icon */
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

            /** Constructs a URL for the overview AtoN icon */
            getAtonIconUrl: function(aton) {
                return computeAtonIconUrl(aton);
            },


            /** Compute which icon to display for a given AtoN */
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


            /** Returns the selection icon to use with a selected AtoN */
            getAtonSelectionOLIcon: function() {
                return new ol.style.Icon({
                    anchor: [0.5, 0.5],
                    scale: 0.2,
                    opacity: 1.0,
                    src: '/img/aton/select.png'
                })
            },


            /** Compute the OL label to display for a given AtoN */
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


            /** Returns the UID for the given AtoN */
            getAtonUid: function(aton) {
                return aton ? aton.tags['seamark:ref'] : undefined;
            },



            /** Opens an AtoN details dialog **/
            atonDetailsDialog: function(aton, selectable) {
                return $uibModal.open({
                    controller: "EditAtonDetailsDialogCtrl",
                    templateUrl: "/app/atons/aton-details-editor-dialog.html",
                    size: 'md',
                    keyboard: true,
                    resolve: {
                        atonCtx: function () { return { aton: aton, orig: aton }; },
                        editable: function () { return false; },
                        selectable: function () { return selectable }
                    }
                });
            },


            /** Opens an AtoN editor dialog **/
            atonEditorDialog: function(aton, orig) {
                return $uibModal.open({
                    controller: "EditAtonDetailsDialogCtrl",
                    templateUrl: "/app/atons/aton-details-editor-dialog.html",
                    size: 'md',
                    keyboard: false,
                    resolve: {
                        atonCtx: function () { return { aton: aton, orig: orig }; },
                        editable: function () { return true; },
                        selectable: function () { return false }
                    }
                });
            },


            /** Returns an SVG representation of the AtoN */
            getAtonSvg: function(aton, params) {
                params = params || 'width=400&height=200&scale=0.4';
                return $http.post('/rest/aton-icon/svg?' + params, aton);
            },


            /** Search for AtoNs within the given extent */
            searchAtonsByExtent: function(extent, maxAtonNo) {
                var params = 'maxAtonNo=' + maxAtonNo + '&emptyOnOverflow=true';
                if (extent && extent.length == 4) {
                    params += '&minLon=' + extent[0] + '&minLat=' + extent[1]
                           +  '&maxLon=' + extent[2] + '&maxLat=' + extent[3];
                }

                return $http.get('/rest/atons/search?' + params);
            },


            /** Searches for AtoNs based on the given search parameters */
            searchAtonsByParams: function(params) {
                return $http.get('/rest/atons/search?' + params);
            },


            /** Returns the node type names matching the name param */
            getNodeTypeNames: function (name) {
                var param = (name) ? '?name=' + encodeURIComponent(name) : '';
                return $http.get('/rest/atons/defaults/node-types' + param);
            },


            /** Updates and returns an AtoN from the node types with the given names */
            mergeWithNodeTypes: function (aton, nodeTypeNames) {
                var param = { aton: aton, nodeTypeNames: nodeTypeNames };
                return $http.post('/rest/atons/defaults/merge-with-node-types', param);
            }
        };
    }]);

