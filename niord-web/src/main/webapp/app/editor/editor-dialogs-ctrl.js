
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
 * The Message Editor dialogs controllers
 */
angular.module('niord.editor')

    /*******************************************************************
     * Controller that handles the message publications dialog
     *******************************************************************/
    .controller('MessagePublicationsDialogCtrl', ['$scope', '$timeout', 'MessageService', 'LangService',
                'message', 'type', 'publicationId', 'lang',
        function ($scope, $timeout, MessageService, LangService,
                message, type, publicationId, lang) {
            'use strict';

            $scope.message = message;
            // Create pruned version of the message
            $scope.messageTemplate = {
                descs: message.descs.map(function (desc) {
                    return { lang: desc.lang, publication: desc.publication, internalPublication: desc.internalPublication }
                })
            };

            $scope.type = type;
            $scope.publicationId = publicationId;
            $scope.pub = {
                parameters: undefined,
                link: undefined,
                publication: undefined
            };

            $timeout(function () {
                $('.pub-field').find('input').focus();
            }, 100)

            /** Returns whether or not to display a parameter field for the message publication **/
            $scope.hasPublicationParams = function (pub) {
                if (pub && pub.publication && pub.publication.descs) {
                    for (var x = 0; x < pub.publication.descs.length; x++) {
                        var desc = pub.publication.descs[x];
                        if (desc && desc.format && desc.format.indexOf('${parameters}') !== -1) {
                            return true;
                        }
                    }
                }
                return false;
            };


            // Called when the publication selection changes
            $scope.$watch("pub.publication", function () {
                delete $scope.pub.parameters;
                delete $scope.pub.link;
                if ($scope.pub.publication) {
                    MessageService.extractMessagePublication($scope.messageTemplate, $scope.pub.publication.publicationId, lang)
                        .success(function (msgPub) {
                           if (msgPub) {
                               $scope.pub.parameters = msgPub.parameters;
                               $scope.pub.link = msgPub.link;
                           }
                        });

                    if ($scope.hasPublicationParams($scope.pub)) {
                        $timeout(function () {
                            $('#pub-param').focus();
                        }, 100)
                    }
                }
            });


            /** Called when the publication should be updated for the message **/
            $scope.updatePublication = function () {
                if ($scope.pub.publication) {
                    MessageService.updateMessagePublications($scope.messageTemplate, $scope.pub.publication.publicationId, $scope.pub.parameters, $scope.pub.link)
                        .success(function (msg) {
                            if (msg) {
                                angular.forEach(msg.descs, function (desc) {
                                    var origDesc = LangService.descForLanguage($scope.message, desc.lang);
                                    if (origDesc) {
                                        origDesc.publication = desc.publication;
                                        origDesc.internalPublication = desc.internalPublication;
                                    }
                                })
                            }
                            $scope.$close("Updated");
                        });
                } else {
                    $scope.$close("Not updated");
                }
            };
        }])


    /*******************************************************************
     * Controller that handles the message source dialog
     *******************************************************************/
    .controller('MessageSourceDialogCtrl', ['$scope', '$rootScope', '$timeout', 'MessageService', 'LangService',
        function ($scope, $rootScope, $timeout, MessageService, LangService) {
            'use strict';

            $scope.sources = [];
            $scope.selection = [];
            $scope.search = '';

            $timeout(function () {
                $('#filter').focus();
            }, 100);


            /** Filters messages by name **/
            $scope.searchFilter = function (source) {
                return source.descs[0].name.toLowerCase().indexOf($scope.search.toLowerCase()) !== -1 ||
                    source.descs[0].abbreviation.toLowerCase().indexOf($scope.search.toLowerCase()) !== -1;
            };


            // Load all sources
            MessageService.getSources()
                .success(function (sources) {
                    $scope.sources = sources;
                });


            /** Called when the selection has been updated **/
            $scope.selectionUpdated = function () {
                $scope.selection.length = 0;
                angular.forEach($scope.sources, function (source) {
                    if (source.selected) {
                        $scope.selection.push(source);
                    }
                });
            };
            $scope.$watch("sources", $scope.selectionUpdated, true);


            /** If all sources of the selection share the same year, return this, return null otherwise **/
            $scope.getCommonYear = function () {
                var commonYear = null;
                for (var x = 0; x < $scope.selection.length; x++) {
                    if (!$scope.selection[x].date) {
                        return null;
                    }
                    var date = moment($scope.selection[x].date);
                    var year = date.format('YYYY');
                    if (commonYear != null && year != commonYear) {
                        return null;
                    }
                    commonYear = year;
                }
                return commonYear;
            };


            /** Composes the source text from the current selection **/
            $scope.composeSourceText = function () {
                var result = {};
                angular.forEach($rootScope.modelLanguages, function (lang) {
                    var text = '';
                    var commonYear = $scope.getCommonYear();
                    var dateFormat = (commonYear) ? 'Do MMMM' : 'Do MMMM YYYY';
                    for (var x = 0; x < $scope.selection.length; x++) {
                        var source = $scope.selection[x];
                        var desc = LangService.descForLanguage(source, lang);
                        if (desc) {
                            var delim = ($scope.selection.length > 1 && x == $scope.selection.length - 1)
                                ? ' ' + LangService.translate('term.and', null, lang) + ' '
                                : ', ';
                            text += (text.length > 0) ? delim : '';
                            text += desc.abbreviation || desc.name;
                            if (source.date) {
                                var date = moment(source.date).locale(lang);
                                text += ' ' + date.format(dateFormat);
                            }
                        }
                    }
                    if (commonYear) {
                        text += ' ' + commonYear;
                    }
                    result[lang] = text;
                });
                $scope.$close(result);
            };


            /** Called when the source selection should be added to the message **/
            $scope.addSelection = function () {

                // Get the details of the selected sources to get all language variants
                MessageService.getSourceDetails($scope.selection)
                    .success(function (sources) {
                        var detailsMap = {};
                        angular.forEach(sources, function (s) {
                           detailsMap[s.id] = s;
                        });
                        angular.forEach($scope.selection, function (source) {
                           if (detailsMap[source.id]) {
                               source.descs = detailsMap[source.id].descs;
                           }
                        });

                        $scope.composeSourceText();
                    });
            };
        }])


    /*******************************************************************
     * Controller that handles the message Thumbnail dialog
     *******************************************************************/
    .controller('MessageThumbnailDialogCtrl', ['$scope', '$rootScope', 'message',
        function ($scope, $rootScope, message) {
            'use strict';

            $scope.messageList = [ message ];
            $scope.switcherLayers = [];
            $scope.showStdOSMLayer = $rootScope.osmSourceUrl && $rootScope.osmSourceUrl.length > 0;
            $scope.mapImageSize = $rootScope.mapImageSize || 256;

            /** Takes a thumbnail */
            $scope.thumbnail = function () {
                // Broadcast a take-thumbnail event to the message-thumbnail-layer.
                $scope.$broadcast('take-thumbnail', {});
            };


            /** Called when the thumbnail has been generated **/
            $scope.thumbnailGenerated = function (image) {
                $scope.$close(image);
            };


            $scope.updateVisibility = function (l) {
                l.layer.setVisible(l.visible)
            };

        }])


    /*******************************************************************
     * Controller that handles the message Thumbnail dialog
     *******************************************************************/
    .controller('MessageComparisonDialogCtrl', ['$scope', '$timeout', 'growl', 'MessageService', 'messageId1', 'messageId2',
        function ($scope, $timeout, growl, MessageService, messageId1, messageId2) {
            'use strict';

            $scope.message1 = undefined;
            $scope.message2 = undefined;

            // Contains a visual diff of the two message html representations
            $scope.messageDiff = '';

            // Contains the JSON data of message1 and message2 used for data comparison
            $scope.messageData = [ ];

            /** Initialize the list of messages to compare **/
            $scope.initData = function () {
                $scope.messageData.length = 0;
                if ($scope.message2) {
                    $scope.messageData.push({ snapshot: angular.toJson($scope.message2) });
                }
                if ($scope.message1) {
                    $scope.messageData.push({ snapshot: angular.toJson($scope.message1) });
                }

                $scope.messageDiff = '';
                if ($scope.message1 != undefined && $scope.message2 != undefined) {
                    $timeout(function () {
                        var msg1 = $('#message1').html();
                        var msg2 = $('#message2').html();
                        $scope.messageDiff = htmldiff(msg1, msg2);
                        $timeout(function () {
                            // Disable all links
                            $('#message-diff').find('*').removeAttr('href ng-click');
                        });
                    }, 300);
                }
            };

            $scope.reference1 = {
                messageId : messageId1
            };
            $scope.reference2 = {
                messageId : messageId2
            };

            $scope.$watch("reference1.messageId", function (messageId) {
                // Reset the message history
                $scope.message1 = undefined;
                $scope.initData();

                // Fetch the message to compare with
                if (messageId && messageId.length > 0) {
                    MessageService.details(messageId)
                        .success(function (message) {
                            $scope.message1 = message;
                            $scope.initData();
                        })
                        .error(function (data, status) {
                            growl.error("Error loading message (code: " + status + ")", {ttl: 5000})
                        })
                }
            }, true);

            $scope.$watch("reference2.messageId", function (messageId) {
                // Reset the message history
                $scope.message2 = undefined;
                $scope.initData();

                // Fetch the message to compare with
                if (messageId && messageId.length > 0) {
                    MessageService.details(messageId)
                        .success(function (message) {
                            $scope.message2 = message;
                            $scope.initData();
                        })
                        .error(function (data, status) {
                            growl.error("Error loading message (code: " + status + ")", {ttl: 5000})
                        })
                }
            }, true);

        }])



    /*******************************************************************
     * Controller that allows the user to format selected features as text
     *******************************************************************/
    .controller('FormatMessageLocationsDialogCtrl', ['$scope', '$rootScope', '$window', 'growl',
            'MessageService', 'message', 'partIndex', 'lang',
        function ($scope, $rootScope, $window, growl,
              MessageService, message, partIndex, lang) {
            'use strict';

            $scope.wmsLayerEnabled = $rootScope.wmsLayerEnabled;
            $scope.openSeaMapLayerEnabled = $rootScope.openSeaMapLayerEnabled;

            $scope.message = message;
            $scope.partIndexData = { partIndex: partIndex };
            $scope.lang = lang;

            $scope.featureCollection = {
                type: 'FeatureCollection',
                features: []
            };
            $scope.selectedFeatures = {
                type: 'FeatureCollection',
                features: [ ]
            };
            $scope.data = {
                result: ''
            };
            $scope.params = {
                template: 'list',
                format: 'dec'
            };
            $scope.formats = [
                { name : 'Decimal', value: 'dec' },
                { name : 'Seconds', value: 'sec' },
                { name : 'Navtex', value: 'navtex' }
            ];


            // Restore previous parameter settings
            if ($window.localStorage['formatLocationSettings']) {
                try {
                    angular.copy(angular.fromJson($window.localStorage['formatLocationSettings']), $scope.params);
                } catch (error) {
                }
            }


            /** Called whenever the message part selection changes **/
            $scope.messagePartSelectionChanged = function (updateSelection) {
                var fc = message.parts[$scope.partIndexData.partIndex].geometry;
                $scope.featureCollection.features = angular.copy(fc.features);

                // Remove all buffered features
                $scope.featureCollection.features = $scope.featureCollection.features.filter(function (feature) {
                    return feature.properties['parentFeatureIds'] === undefined;
                });

                // Initial selection
                if ($scope.featureCollection.features.length == 1) {
                    $scope.featureCollection.features[0].selected = true;
                }

                // Update the current feature selection
                if (updateSelection) {
                    $scope.featureSelectionChanged();
                }
            };
            $scope.messagePartSelectionChanged(false);


            /** Called when the feature selection changes */
            $scope.featureSelectionChanged = function () {
                $scope.selectedFeatures.features = $.grep($scope.featureCollection.features, function (feature) {
                    return feature.selected;
                });

                // Compute the result
                $scope.data.result = '';
                if ($scope.selectedFeatures.features.length > 0) {
                    MessageService.formatMessageGeometry($scope.selectedFeatures, $scope.lang, $scope.params.template, $scope.params.format)
                        .success(function (result) {
                            $scope.data.result = result;
                        })
                        .error (function (data, status) {
                            growl.error("Error formatting locations (code: " + status + ")", { ttl: 5000 })
                        });
                }
            };


            /** Called to select all or none of the features */
            $scope.selectFeatures = function (select) {
                angular.forEach($scope.featureCollection.features, function (feature) {
                    feature.selected = select;
                });
                $scope.featureSelectionChanged();
            };
            $scope.$watch("params", $scope.featureSelectionChanged, true);


            /** Called when Insert is clicked **/
            $scope.insert = function () {
                $window.localStorage['formatLocationSettings'] = angular.toJson($scope.params);
                $scope.$close($scope.data.result);
            };
        }])


    /*******************************************************************
     * Controller that allows the user to format selected time intervals as text
     *******************************************************************/
    .controller('FormatMessageTimeDialogCtrl', ['$scope', '$rootScope', '$window', 'growl',
        'DateIntervalService', 'message', 'partIndex', 'lang',
        function ($scope, $rootScope, $window, growl,
                  DateIntervalService, message, partIndex, lang) {
            'use strict';

            $scope.message = message;
            $scope.partIndexData = { partIndex: partIndex };
            $scope.dateIntervals = [];
            $scope.lang = lang;
            $scope.data = {
                result: ''
            };
            $scope.params = {
                tz: true
            };


            // Restore previous parameter settings
            if ($window.localStorage['formatTimeSettings']) {
                try {
                    angular.copy(angular.fromJson($window.localStorage['formatTimeSettings']), $scope.params);
                } catch (error) {
                }
            }


            /** Called whenever the message part selection changes **/
            $scope.messagePartSelectionChanged = function (updateSelection) {

                var dateIntervals = message.parts[$scope.partIndexData.partIndex].eventDates || [];
                $scope.dateIntervals = angular.copy(dateIntervals);

                // Initial selection
                if ($scope.dateIntervals.length == 1) {
                    $scope.dateIntervals[0].selected = true;
                }

                // Update the current time selection
                if (updateSelection) {
                    $scope.timeSelectionChanged();
                }
            };
            $scope.messagePartSelectionChanged(false);


            /** Called when the time selection changes */
            $scope.timeSelectionChanged = function () {

                // Compute the result
                $scope.data.result = '';

                if ($scope.dateIntervals.length == 0) {
                    $scope.data.result = DateIntervalService.translateDateInterval($scope.lang, null, $scope.params.tz);
                } else {
                    angular.forEach($scope.dateIntervals, function (di) {
                        if (di.selected) {
                            $scope.data.result +=
                                DateIntervalService.translateDateInterval($scope.lang, di, $scope.params.tz) + '<br>';
                        }
                    });
                }
            };


            /** Called to select all or none of the date intervals */
            $scope.selectTimeIntervals = function (select) {
                angular.forEach($scope.dateIntervals, function (di) {
                    di.selected = select;
                });
                $scope.timeSelectionChanged();
            };


            /** Called when Insert is clicked **/
            $scope.insert = function () {
                $window.localStorage['formatTimeSettings'] = angular.toJson($scope.params);
                $scope.$close($scope.data.result);
            };


            // Initial selection
            $scope.selectTimeIntervals($scope.dateIntervals.length == 1);
        }])
;

