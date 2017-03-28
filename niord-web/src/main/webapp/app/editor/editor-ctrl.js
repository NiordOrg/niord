
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
 * The Message Editor controller and sub-controllers
 */
angular.module('niord.editor')

    /**
     * Main message editor controller
     */
    .controller('EditorCtrl', ['$scope', '$rootScope', '$window', '$stateParams', '$state', '$http', '$timeout', '$uibModal', 'growl',
            'MessageService', 'LangService', 'MapService', 'UploadFileService', 'AtonService',
        function ($scope, $rootScope, $window, $stateParams, $state, $http, $timeout, $uibModal, growl,
                  MessageService, LangService, MapService, UploadFileService, AtonService) {
            'use strict';

            $scope.message = undefined;
            $scope.initId = $stateParams.id || '';
            $scope.referenceType = $stateParams.referenceType || '';
            $scope.isEditor = $rootScope.hasRole('editor');

            // Records if a field is expanded or collapsed.
            // Flags packaged in arrays in order to handle message part fields
            $scope.editMode = {
                type:           [ false ],
                orig_info:      [ false ],
                id:             [ false ],
                title:          [ false ],
                references:     [ false ],
                publish_date:   [ false ],
                areas:          [ false ],
                categories:     [ false ],
                charts:         [ false ],
                event_dates:    [],         // indexed by message part
                positions:      [],         // indexed by message part
                subject:        [],         // indexed by message part
                description:    [],         // indexed by message part
                attachments:    [ false ],
                promulgation:   [ false ],
                note:           [ false ],
                publication:    [ false ],
                source:         [ false ],
                prohibition:    [ false ],
                signals:        [ false ],
                layout:         [ false ]
            };

            // From backend settings, determines which editor fields to display
            $scope.editorFields = $rootScope.editorFieldsBase;
            // Fields not displayed by default - can be manually enabled from menu
            $scope.unusedEditorFields = {};

            $scope.messageSeries = [];
            $scope.messageTags = [];

            $scope.attachmentUploadUrl = undefined;

            // Record if the Edit page was entered from a message list URL
            $scope.backToListUrl = $rootScope.lastMessageSearchUrl;

            // This will be set when the "Save message" button is clicked and will
            // disable the button, to avoid double-clicks
            $scope.messageSaving = false;


            $scope.validTypes = {
                NW: { LOCAL_WARNING: false, COASTAL_WARNING: false, SUBAREA_WARNING: false, NAVAREA_WARNING: false },
                NM: { TEMPORARY_NOTICE: false, PRELIMINARY_NOTICE: false, PERMANENT_NOTICE: false, MISCELLANEOUS_NOTICE: false }
            };


            /*****************************/
            /** Initialize the editor   **/
            /*****************************/

            // Used to ensure that description entities have various field
            function initMessageDescField(desc) {
                desc.title = '';
            }

            // Used to ensure that message part description entities have various field
            function initMessagePartDescField(desc) {
                desc.details = '';
            }

            // Used to ensure that attachment description entities have various field
            function initAttachmentDescField(desc) {
                desc.caption = '';
            }

            // Used to ensure that reference description entities have various field
            function initReferenceDescField(desc) {
                desc.description = '';
            }

            /** Called during initialization when the message has been loaded or instantiated */
            $scope.initMessage = function (pristine) {
                var msg = $scope.message;

                // Sanity check
                if (!msg) {
                    return;
                }

                $scope.setEditorFields(msg.editorFields, true);

                // Ensure that localized desc fields are defined for all languages
                LangService.checkDescs(msg, initMessageDescField, undefined, $rootScope.modelLanguages);

                // Ensure that the message has at lease one message part
                $scope.initMessageParts();

                // Reset the valid types
                Object.keys($scope.validTypes[msg.mainType]).forEach(function (key) {
                    $scope.validTypes[msg.mainType][key] = false;
                });

                // Determine the message series for the current domain and message mainType
                $scope.messageSeries.length = 0;
                if ($rootScope.domain && $rootScope.domain.messageSeries) {
                    angular.forEach($rootScope.domain.messageSeries, function (series) {
                        if (series.mainType === msg.mainType) {

                            $scope.messageSeries.push(series);

                            if (msg.messageSeries && msg.messageSeries.seriesId === series.seriesId) {
                                // The message may have been loaded with a "pruned" version of the series. Replace it:
                                msg.messageSeries = series;
                            }

                            // Compute the valid types for the message
                            Object.keys($scope.validTypes[msg.mainType]).forEach(function (key) {
                                $scope.validTypes[msg.mainType][key] |= series.types === undefined
                                    || series.types.length === 0 || ($.inArray(key, series.types) !== -1);
                            });
                        }
                    });
                }

                // Ensure that localized reference desc fields are defined for all languages
                if (!msg.references) {
                    msg.references = [];
                }
                angular.forEach(msg.references, function (ref) {
                    LangService.checkDescs(ref, initReferenceDescField, undefined, $rootScope.modelLanguages);
                });

                // Update the attachment upload url
                $scope.attachmentUploadUrl =  MessageService.attachmentUploadRepoPath(msg);
                // Ensure that localized attachment desc fields are defined for all languages
                angular.forEach(msg.attachments, function (att) {
                    LangService.checkDescs(att, initAttachmentDescField, undefined, $rootScope.modelLanguages);
                });

                // Mark the form as pristine
                if (pristine) {
                    $scope.setPristine();
                } else {
                    $scope.setDirty();
                }

                // Load the message tags
                $scope.loadTags();

                // Remove lock on save button
                $scope.messageSaving = false;
            };


            /** Initialize the message editor */
            $scope.init = function () {

                $scope.initId = $stateParams.id || '';
                $scope.referenceType = $stateParams.referenceType || '';
                $scope.editType = 'edit';
                if ($state.includes('editor.template')) {
                    $scope.editType = 'template';
                } else if ($state.includes('editor.copy')) {
                    $scope.editType = 'copy';
                }

                if ($scope.editType === 'edit' && $scope.initId) {
                    // Case 1) The message ID may be specified
                    MessageService.editableDetails($scope.initId)
                        .success(function (message) {
                            $scope.message = message;
                            $scope.initMessage(true);
                        })
                        .error(function (data, status) {
                            growl.error("Error loading message (code: " + status + ")", {ttl: 5000})
                        });

                } else if ($scope.editType === 'copy' && $scope.initId) {
                    // Case 2) Create a copy of a message
                    MessageService.copyMessageTemplate($scope.initId, $scope.referenceType)
                        .success(function (message) {
                            $scope.message = message;
                            $scope.initMessage();
                        })
                        .error(function (data, status) {
                            growl.error("Error copying message (code: " + status + ")", {ttl: 5000})
                        });

                } else if ($scope.editType === 'template') {
                    // Case 3) The editor may be based on a template message, say, from the AtoN selection page
                    $scope.createMessage($rootScope.templateMessage.mainType, $rootScope.templateMessage);
                }
            };


            /** Called when the message has been updated by executing a category template **/
            $scope.templateExecuted = function (message) {
                $scope.message = message;
                $scope.initMessage(false);
            };


            /*****************************/
            /** Editor functionality    **/
            /*****************************/

            // The usual $scope.editForm does not work (form tied to sub-scope of this controller?)
            // So, we defined it ourselves
            $scope.editForm = undefined;
            $scope.resolveEditForm = function () {
                if (!$scope.editForm) {
                    try { $scope.editForm = angular.element($("#editForm")).scope().editForm; } catch (err) {}
                }
                return $scope.editForm;
            };


            /** Set the form as pristine **/
            $scope.setPristine = function () {
                if ($scope.resolveEditForm()) {
                    $scope.editForm.$setPristine();
                }
                $scope.canSaveMessage = false;
            };


            /** Set the form as dirty **/
            $scope.setDirty = function () {
                if ($scope.resolveEditForm()) {
                    $scope.editForm.$setDirty();
                }
                $scope.canSaveMessage = true;
            };


            // Monitor the dirty state
            // The usual technique of monitoring form.$dirty, form.$pristine and form.$invalid does not work
            // properly, because form fields are added and removed when field editors are activated
            $scope.canSaveMessage = false;
            $scope.$watch(function () {
                return ($scope.resolveEditForm()) ? $scope.editForm.$dirty : false;
            }, function (dirty) {
                $scope.canSaveMessage = $scope.canSaveMessage || dirty;
            }, true);


            /** Place focus in the editor **/
            $scope.focusEditor = function () {
                $timeout(function () {
                    $('.editor-field-label').first().focus();
                });

                // Reset edit-form every time the edit page is displayed
                $scope.editForm = undefined;
            };


            /** The TinyMCE editor actually hides the textarea and constructs an iframe. Fix tab-indexing **/
            $scope.fixTinyMCETabIndex = function (editor) {
                var id = editor.getElement().parentElement.id;
                var elm = $('#' + id);
                var textarea = elm.find('textarea');
                var tabindex = textarea.attr('tabindex');
                if (tabindex) {
                    textarea.attr('tabindex', null);
                    elm.find('iframe').attr('tabindex', tabindex);
                }
            };


            /** Returns if the message is editable **/
            $scope.editable = function () {
                var msg = $scope.message;
                return msg && msg.status &&
                    (msg.status === 'DRAFT' || msg.status === 'VERIFIED' || $rootScope.hasRole('admin'));
            };


            /** Returns if the current user in the current domain can create messages of the given mainType **/
            $scope.canCreateMessage = function (mainType) {
                return $rootScope.supportsMainType(mainType) && $rootScope.hasRole('editor');
            };


            /** Create a message of the given mainType and AtoN selection **/
            $scope.createMessage = function (mainType, templateMessage) {
                if (!$scope.canCreateMessage(mainType)) {
                    return;
                }

                MessageService.newMessageTemplate(mainType)
                    .success(function (message) {
                        $scope.message = message;
                        if (templateMessage) {
                            angular.forEach(templateMessage, function (value, key) {
                                $scope.message[key] = value;
                            });
                        }
                        $scope.initMessage();
                    })
                    .error(function() {
                        growl.error("Error creating new message template", { ttl: 5000 })
                    });
            };


            /** Save the current message **/
            $scope.saveMessage = function () {
                // Perform a couple of validations
                var msg = $scope.message;
                if (!msg.mainType || !msg.type) {
                    growl.error("Please specify message type before saving", { ttl: 5000 });
                    return;
                } else if (!msg.messageSeries) {
                    growl.error("Please specify message series before saving", {ttl: 5000});
                    return;
                } else if (msg.publishDateFrom && msg.publishDateTo && msg.publishDateFrom > msg.publishDateTo) {
                    growl.error("Invalid publish date interval", {ttl: 5000});
                    return;
                }

                // Prevent double-submissions
                $scope.messageSaving = true;

                MessageService.saveMessage($scope.message)
                    .success(function(message) {
                        growl.info("Message saved", { ttl: 3000 });
                        $state.go(
                            'editor.edit',
                            { id: message.id },
                            { reload: true }
                        );
                    })
                    .error(function() {
                        $scope.messageSaving = false;
                        growl.error("Error saving message", { ttl: 5000 })
                    });
            };


            /** Clears the current message **/
            $scope.clearMessage = function () {
                $window.location = '/#/editor/edit/';
                $window.location.reload();
            };


            /** Returns if the given message field (group) is valid **/
            $scope.fieldValid = function(fieldId) {
                var msg = $scope.message;
                switch (fieldId) {
                    case 'type':
                        return msg.mainType && msg.type;
                    case 'id':
                        return msg.messageSeries !== undefined;
                }
                return true;
            };


            /** Returns if the given message field (group) should be displayed **/
            $scope.showField = function (fieldId) {
                return $scope.editorFields[fieldId] || $scope.unusedEditorFields[fieldId];
            };


            /** Sets the current editor fields **/
            $scope.setEditorFields = function (editorFields, reset) {
                $scope.editorFields = editorFields || $rootScope.editorFieldsBase;

                // Compute the list of unused editor fields, which may manually be selected
                if (reset) {
                    $scope.unusedEditorFields = {};
                }
                angular.forEach($scope.editorFields, function (enabled, field) {
                    if (enabled) {
                        // Not unused
                        delete $scope.unusedEditorFields[field];
                    } else if (reset || $scope.unusedEditorFields[field] === undefined) {
                        // Unused - don't show
                        $scope.unusedEditorFields[field] = false;
                    }
                })
            };


            /** Toggles whether or not to show the given editor field **/
            $scope.toggleUseEditorField = function (fieldId) {
                $scope.unusedEditorFields[fieldId] = !$scope.unusedEditorFields[fieldId];
            };


            /** Returns the number sequence type of the message series **/
            $scope.numberSequenceType = function () {
                var series = $scope.message.messageSeries;
                return (series) ? series.numberSequenceType : null;
            };


            /** Returns if the short ID is editable **/
            $scope.shortIdEditable = function () {
                var series = $scope.message.messageSeries;
                return series && series.numberSequenceType === 'MANUAL' && !series.shortFormat;
            };


            /** Returns if the message number is editable **/
            $scope.messageNumberEditable = function () {
                var series = $scope.message.messageSeries;
                return series && series.numberSequenceType === 'MANUAL' && series.shortFormat;
            };


            /** Called when the message type is updated. See if we can deduce the message series from the type **/
            $scope.typeSelected = function () {
                var type = $scope.message.type;

                // Find matching message series
                var series = $.grep($scope.messageSeries, function (s) {
                    return s.types === undefined || s.types.length === 0 || ($.inArray(type, s.types) !== -1);
                });
                if (series.length === 1) {
                    $scope.message.messageSeries = series[0];
                }

                // Lastly, call adjustEditableMessage() to update list of promulgations
                $scope.adjustEditableMessage(true);
            };


            /** Reference DnD configuration **/
            $scope.referencesSortableCfg = {
                group: 'reference',
                handle: '.move-btn',
                onEnd: $scope.setDirty
            };


            /** Called when a message reference is clicked **/
            $scope.referenceClicked = function(messageId) {
                MessageService.detailsDialog(messageId);
            };


            /** Deletes the given reference from the list of message references **/
            $scope.deleteReference = function (ref) {
                if ($.inArray(ref, $scope.message.references) > -1) {
                    $scope.message.references.splice( $.inArray(ref, $scope.message.references), 1 );
                    $scope.setDirty();
                }
            };


            /** Adds the new reference to the list of message references **/
            $scope.addReference = function () {
                var ref = {
                    messageId: undefined,
                    type: 'REFERENCE',
                    descs: []
                };
                LangService.checkDescs(ref, initReferenceDescField, undefined, $rootScope.modelLanguages);
                $scope.message.references.push(ref);
                $timeout(function () {
                    $('.ref-field:last').find('input').focus();
                }, 100)
            };


            /** Allows the user to edit or insert publications **/
            $scope.editPublications = function (editor) {
                // The ID of the parent div has the format "internal/external-publication-<<lang>>"
                var parentDivId = editor.getElement().parentElement.id;
                var id = parentDivId.split("-");
                var messagePublication = id[0].toUpperCase();
                var lang = id[2];

                // Check if the cursor is within a publication span
                var publicationId = null;
                var selNode = editor.selection.getNode();
                while (selNode !== null  && selNode.getAttribute && !publicationId) {
                    publicationId = selNode.getAttribute('publication');
                    selNode = selNode.parentNode;
                }

                $scope.$apply(function() {
                    MessageService.messagePublicationsDialog($scope.message, messagePublication, publicationId, lang)
                        .result.then($scope.setDirty);
                });
            };


            // Configuration of the Publication TinyMCE editors
            $scope.publicationTinymceOptions = {
                resize: false,
                forced_root_block : '',  // Avoid content placed in <p> tag
                valid_elements : '*[*]', // NB: This allows insertion of all html elements, including javascript
                statusbar : false,
                menubar: false,
                content_css : '/css/messages.css',
                body_class : 'message-publication',
                plugins: "link anchor code fullscreen",
                toolbar: " link image table | code fullscreen | niordpublications",
                setup : function ( editor ) {
                    editor.addButton( 'niordpublications', {
                        title: 'Insert Publications',
                        icon: 'publication',
                        onclick : function () { $scope.editPublications(editor); }
                    });
                },
                init_instance_callback : $scope.fixTinyMCETabIndex
            };


            /** Called in order to add a source to the message **/
            $scope.addSource = function () {
                MessageService.messageSourceDialog()
                    .result.then(function (result) {
                        angular.forEach(result, function (sourceText, lang) {
                            var desc = LangService.descForLanguage($scope.message, lang);
                            if (desc && sourceText.length > 0) {
                                var result = desc.source || '';
                                result = result.trim();
                                result += result.length > 0 ? ', ' : '';
                                result += sourceText;
                                desc.source = result;
                            }
                        });
                    });
            };


            /**
             * Checks if any of the message parts defined a geometry
             */
            $scope.definesGeometry = function () {
                return MessageService.getMessageFeatures($scope.message).length > 0;
            };


            /** Computes the areas intersecting with the current message geometry **/
            $scope.computeAreas = function () {
                // Create a combined geometry for all message parts
                var geometry = {
                    type: 'FeatureCollection',
                    features: MessageService.getMessageFeatures($scope.message)
                };
                if (geometry.features.length > 0) {
                    MessageService.intersectingAreas(geometry)
                        .success(function (areas) {
                            // Prune the returned area data
                            $scope.message.areas = areas;
                            $scope.adjustEditableMessage();
                            $scope.setDirty();
                        });
                }
            };


            /** Computes the charts intersecting with the current message geometry **/
            $scope.computeCharts = function () {
                // Create a combined geometry for all message parts
                var geometry = {
                    type: 'FeatureCollection',
                    features: MessageService.getMessageFeatures($scope.message)
                };
                if (geometry.features.length > 0) {
                    MessageService.intersectingCharts(geometry)
                        .success(function (charts) {
                            // Prune the returned chart data
                            $scope.message.charts = charts.map(function (chart) {
                                return {
                                    chartNumber: chart.chartNumber,
                                    internationalNumber: chart.internationalNumber,
                                    active: chart.active,
                                    scale: chart.scale
                                }
                            });
                            $scope.setDirty();
                        });
                }
            };


            /** Sorts the current selection of charts **/
            $scope.sortCharts = function () {
                if ($scope.message.charts) {
                    $scope.message.charts.sort(function (c1, c2) {
                        var scale1 = c1.scale || 10000000;
                        var scale2 = c2.scale || 10000000;
                        return scale1 - scale2;
                    });
                }
            };


            /** Copies the locations from the selected area to the message **/
            $scope.copyAreaLocations = function() {
                var msg = $scope.message;
                if (msg.areas) {
                    var areaIds = msg.areas.map(function (a) { return a.id;  }).join(",");
                    $http.get('/rest/areas/search/' + areaIds)
                        .success(function (areas) {
                            angular.forEach(areas, function (area) {
                                if (area.geometry) {
                                    var feature = {
                                        type: 'Feature',
                                        geometry: area.geometry,
                                        properties: { areaId: area.id }
                                    };
                                    angular.forEach(area.descs, function (desc) {
                                        feature.properties['name:' + desc.lang] = desc.name;
                                    });
                                    if (msg.parts && msg.parts.length > 0 && msg.parts[0].geometry) {
                                        msg.parts[0].geometry.features.push(feature);
                                    }
                                    $scope.geometrySaved();
                                }
                            });
                        });
                }
            };


            /** called when the message geometry has been changed **/
            $scope.geometrySaved = function () {
                $scope.initMessageParts();
                $scope.setDirty();
            };

            
            /** Returns if the current page matches the given page url **/
            $scope.onPage = function (page) {
                return $state.includes(page);
            };


            /**
             * Called when relevant attributes have been changed that may affect the auto-generated message fields
             */
            $scope.adjustEditableMessage = function (inclPromulgations) {
                var msg = $scope.message;
                MessageService.adjustEditableMessage(msg)
                    .success(function (message) {

                        $scope.setEditorFields(message.editorFields);

                        if (msg.autoTitle) {
                            angular.forEach(message.descs, function (desc) {
                                var d = LangService.descForLanguage($scope.message, desc.lang);
                                if (d && msg.autoTitle) {
                                    d.title = desc.title;
                                }
                                if (d && msg.autoSource) {
                                    d.source = desc.source;
                                }
                            })
                        }
                        if ($scope.messageNumberEditable()) {
                            msg.shortId = message.shortId;
                        }

                        if (inclPromulgations) {
                            msg.promulgations = message.promulgations;
                        }
                    })
            };


            /*****************************/
            /** Message Part functions  **/
            /*****************************/


            /** Initialize the message parts **/
            $scope.initMessageParts = function () {
                if (!$scope.message.parts) {
                    $scope.message.parts = [];
                }
                if ($scope.message.parts.length === 0) {
                    $scope.addMessagePart(0);
                }
                var startCoordIndex = 1;
                angular.forEach($scope.message.parts, function (part, index) {
                    part.index = index;
                    part.type = part.type || 'DETAILS';
                    LangService.checkDescs(part, initMessagePartDescField, undefined, $rootScope.modelLanguages);
                    part.showTime = false;

                    if (!part.eventDates) {
                        part.eventDates = [];
                    }

                    // Instantiate the message part geometry
                    if (!part.geometry) {
                        part.geometry = {
                            type: 'FeatureCollection',
                            features: []
                        }
                    } else if (!part.geometry.features) {
                        part.geometry.features = [];
                    }
                    startCoordIndex = $scope.serializeCoordinates(part, startCoordIndex);
                });
            };


            /** Adds a new message part after the given index **/
            $scope.addMessagePart = function (index) {
                if (!$scope.isEditor) {
                    return;
                }

                var parts = $scope.message.parts;
                index = Math.min(parts.length, index + 1);
                parts.splice(index, 0, {
                    type: 'DETAILS',
                    eventDates: [],
                    descs: [],
                    hideSubject: undefined
                });
                // Keep edit mode flags in sync
                $scope.editMode['event_dates'].splice(index, 0, false);
                $scope.editMode['positions'].splice(index, 0, false);
                $scope.editMode['subject'].splice(index, 0, false);
                $scope.editMode['description'].splice(index, 0, false);
                $scope.initMessageParts();
                $scope.setDirty();
            };


            /** Removes the message part at the given index **/
            $scope.removeMessagePart = function (index) {
                if (!$scope.isEditor) {
                    return;
                }

                var parts = $scope.message.parts;
                if (index < parts.length) {
                    parts.splice(index, 1);
                    // Keep edit mode flags in sync
                    $scope.editMode['event_dates'].splice(index, 1);
                    $scope.editMode['positions'].splice(index, 1);
                    $scope.editMode['subject'].splice(index, 1);
                    $scope.editMode['description'].splice(index, 1);
                }
                if (parts.length === 0) {
                    $scope.addMessagePart(0);
                }
                $scope.initMessageParts();
                $scope.adjustEditableMessage();
                $scope.setDirty();
            };


            /** Moves the given message part up or down **/
            $scope.moveMessagePart = function (index, moveUp) {
                if (!$scope.isEditor) {
                    return;
                }

                var parts = $scope.message.parts;
                var toIndex = moveUp ? index - 1 : index + 1;
                if (toIndex >= 0 && toIndex < parts.length) {
                    // Swap message part order
                    var tmp = parts[index];
                    parts[index] = parts[toIndex];
                    parts[toIndex] = tmp;

                    // Reassign part indexes
                    $scope.initMessageParts();
                    // Update message title, etc.
                    $scope.adjustEditableMessage();
                    $scope.setDirty();
                }
            };


            /** Sets the type of the message part **/
            $scope.setPartType = function (part, type) {
                if ($scope.isEditor) {
                    part.type = type;
                    $scope.setDirty();
                }
            };


            /** Returns if the given message part type selector (group) should be displayed **/
            $scope.showPartType = function (part, fieldId) {
                return $scope.showField(fieldId) || part.type.toLowerCase() === fieldId;
            };


            /** Returns if the given message part positions field should be displayed **/
            $scope.showPartPositionField = function (part) {
                return part.type === 'DETAILS' || part.type === 'POSITIONS' || part.type === 'SIGNALS' ||
                    (part.geometry && part.geometry.features.length > 0);
            };


            /** Returns if the given message part event dates field should be displayed **/
            $scope.showPartEventDatesField = function (part) {
                return part.type === 'DETAILS' || part.type === 'TIME' || (part.eventDates && part.eventDates.length > 0);
            };


            /** Adds a new date interval to the list of message date intervals */
            $scope.addPartEventDate = function (part) {
                part.eventDates.push({ allDay: true, fromDate: undefined, toDate: undefined });
                $timeout(function () {
                    $('.evt-date:last').find('input:first').focus();
                }, 100)

            };


            /** Deletes the given date interval from the list of message date intervals **/
            $scope.deletePartEventDate = function (part, dateInterval) {
                if ($.inArray(dateInterval, part.eventDates) > -1) {
                    part.eventDates.splice( $.inArray(dateInterval, part.eventDates), 1 );
                    $scope.setDirty();
                }
            };


            /** Adds a copy of the given date interval to the list with the given date offset */
            $scope.copyPartEventDate = function (part, dateInterval, offset) {
                var di = angular.copy(dateInterval);
                if (offset && offset > 0) {
                    if (di.fromDate) {
                        di.fromDate = moment(di.fromDate).add(1, "days").valueOf();
                    }
                    if (di.toDate) {
                        di.toDate = moment(di.toDate).add(1, "days").valueOf();
                    }
                }
                part.eventDates.push(di);
            };



            /** Show/hide message part time **/
            $scope.toggleShowTime = function (part) {
                part.showTime = !part.showTime;
            };


            /** Show/hide message part geometry coordinates **/
            $scope.toggleShowCoordinates = function (part) {
                part.showCoordinates = !part.showCoordinates;
            };


            /** Serializes the message part coordinates **/
            $scope.serializeCoordinates = function (part, startCoordIndex) {
                part.showCoordinates = false;
                part.serializedCoordinates = [];
                if (part.geometry.features.length > 0) {
                    angular.forEach(part.geometry.features, function (feature) {
                        var coords = [];
                        MapService.serializeReadableCoordinates(feature, coords);
                        if (coords.length > 0) {
                            feature.properties['startCoordIndex'] = startCoordIndex;
                            var name = feature.properties ? feature.properties['name:' + $rootScope.language] : undefined;
                            part.serializedCoordinates.push({
                                coords: coords,
                                startIndex: startCoordIndex,
                                name: name
                            });
                            startCoordIndex += coords.length;
                        }
                    });
                }
                return startCoordIndex;
            };


            /*********************************************/
            /** Message Details TinyMCE functions       **/
            /*********************************************/

            /** File callback function - called from the TinyMCE image and link dialogs **/
            $scope.fileBrowserCallback = function(field_name, url, type, win) {
                $(".mce-window").hide();
                $("#mce-modal-block").hide();
                $scope.$apply(function() {
                    $scope.attachmentDialog('md').result
                        .then(function (result) {
                            $("#mce-modal-block").show();
                            $(".mce-window").show();
                            win.document.getElementById(field_name).value = "/rest/repo/file/" + result.attachment.path;
                        });
                });
            };

            // TinyMCE file_browser_callback implementation
            $scope.attachmentDialog = function (size) {
                return $uibModal.open({
                    templateUrl: '/app/editor/message-file-dialog.html',
                    controller: function ($scope, $modalInstance, message) {
                        $scope.message = message;
                        $scope.cancel = function () { $modalInstance.dismiss('cancel'); };
                        $scope.attachmentClicked = function (att) {
                            $modalInstance.close(att);
                        }
                    },
                    size: size,
                    windowClass: 'on-top',
                    resolve: {
                        message: function () { return $scope.message; }
                    }
                });
            };


            /** Dialog that facilitates formatting selected message features as text **/
            $scope.locationsDialog = function (partIndex, lang) {
                return $uibModal.open({
                    templateUrl: '/app/editor/format-locations-dialog.html',
                    controller: 'FormatMessageLocationsDialogCtrl',
                    size: 'lg',
                    resolve: {
                        message: function () { return $scope.message },
                        partIndex: function () { return partIndex },
                        lang: function () { return lang; }
                    }
                });
            };


            /** Allows the user to select which location should be inserted and how it should be formatted **/
            $scope.formatLocations = function (editor) {

                // The ID of the parent div has the format "tinymce-<<part-index>>-<<lang>>"
                var parentDivId = editor.getElement().parentElement.id;
                var id = parentDivId.split("-");
                var partIndex = parseInt(id[1]);
                var lang = id[2];

                $scope.$apply(function() {
                    $scope.locationsDialog(partIndex, lang).result
                        .then(function (result) {
                            editor.insertContent(result);
                        });
                });
            };


            /** Dialog that facilitates formatting selected time intervals as text **/
            $scope.timeDialog = function (partIndex, lang) {
                return $uibModal.open({
                    templateUrl: '/app/editor/format-time-dialog.html',
                    controller: 'FormatMessageTimeDialogCtrl',
                    size: 'md',
                    resolve: {
                        message: function () { return $scope.message },
                        partIndex: function () { return partIndex },
                        lang: function () { return lang; }
                    }
                });
            };


            /** Allows the user to select which time intervals should be inserted and how they should be formatted **/
            $scope.formatTimeDialog = function (editor) {

                // The ID of the parent div has the format "tinymce-<<part-index>>-<<lang>>"
                var parentDivId = editor.getElement().parentElement.id;
                var id = parentDivId.split("-");
                var partIndex = parseInt(id[1]);
                var lang = id[2];

                $scope.$apply(function() {
                    $scope.timeDialog(partIndex, lang).result
                        .then(function (result) {
                            editor.insertContent(result);
                        });
                });
            };


            /** Inserts an AtoN SVG in the editor **/
            $scope.insertAtonDialog = function (editor) {
                var aton = {
                    lon: undefined,
                    lat: undefined,
                    tags: {}
                };
                AtonService.atonEditorDialog(aton, angular.copy(aton)).result
                    .then(function(editedAton) {
                        var params = 'width=80&height=80&scale=0.20';
                        AtonService.getAtonSvg(editedAton, params)
                            .success(function (svg) {
                                // The SVG is formatted in such a way that it cannot be displayed
                                // until it is cleaned up
                                svg = svg.replace(/(\r\n|\n|\r)\s+/gm,"");
                                editor.insertContent(svg);
                            });
                    });
            };


            // Configuration of the TinyMCE editors
            $scope.tinymceOptions = {
                resize: false,
                object_resizing : ":not(table)", // NB: Prevent TinyMCE from constantly updating cell/table sizes
                valid_elements : '*[*]', // NB: This allows insertion of all html elements, including javascript
                statusbar : false,
                menubar: false,
                content_css : '/css/messages.css',
                body_class : 'message-description',
                plugins: "autolink lists link image anchor code fullscreen textcolor media table contextmenu paste",
                contextmenu: "link image inserttable | cell row column deletetable",
                toolbar: "styleselect | bold italic | forecolor backcolor | alignleft aligncenter alignright alignjustify | "
                         + "bullist numlist  | outdent indent | link image table | code fullscreen | niordlocations niordtime niordaton",
                table_class_list: [
                    { title: 'None', value: ''},
                    { title: 'No border', value: 'no-border'},
                    { title: 'Condensed', value: 'condensed'},
                    { title: 'No border + condensed', value: 'no-border condensed'},
                    { title: 'Positions', value: 'positions'},
                    { title: 'Position Table', value: 'positions-table'}
                ],
                table_cell_class_list: [
                    {title: 'None', value: ''},
                    {title: 'Underline', value: 'underline'},
                    {title: 'Position', value: 'pos-col'},
                    {title: 'Pos. Index', value: 'pos-index'}
                ],
                table_row_class_list: [
                    {title: 'None', value: ''},
                    {title: 'Underline', value: 'underline'}
                ],
                style_formats: [
                    {title: 'No-wrap', inline: 'span', styles: {'white-space': 'nowrap'}}
                ],
                style_formats_merge: true,
                file_browser_callback: $scope.fileBrowserCallback,
                setup : function ( editor ) {
                    editor.addButton( 'niordlocations', {
                        title: 'Insert Locations',
                        icon: 'map-marker',
                        onclick : function () { $scope.formatLocations(editor); }
                    });
                    editor.addButton( 'niordtime', {
                        title: 'Insert Time',
                        icon: 'time',
                        onclick : function () { $scope.formatTimeDialog(editor); }
                    });
                    /** Disabled for now
                    editor.addButton( 'niordaton', {
                        title: 'Insert AtoN (TEST TEST TEST)',
                        icon: 'aton',
                        onclick : function () { $scope.insertAtonDialog(editor); }
                    });
                    **/
                },
                init_instance_callback : $scope.fixTinyMCETabIndex
            };


            /*****************************/
            /** Message tag handling    **/
            /*****************************/


            /** Loads the tags associated with the current message **/
            $scope.loadTags = function() {
                if ($scope.message && $scope.message.created) {
                    var tag = MessageService.getLastMessageTagSelection();
                    $scope.lastSelectedMessageTag = (tag) ? tag.name : undefined;

                    MessageService.tagsForMessage($scope.message.id, false)
                        .success(function (messageTags) {
                            $scope.messageTags = messageTags;

                            // If the current message is already associated with the last selected tag,
                            // Do not provide a link to add it again.
                            for (var x = 0; x < messageTags.length; x++) {
                                if (tag && tag.tagId === messageTags[x].tagId) {
                                    $scope.lastSelectedMessageTag = undefined;
                                }
                            }
                        })
                }
            };


            /** Adds the current message to the given tag **/
            $scope.addToTag = function (tag) {
                if (tag) {
                    MessageService.addMessagesToTag(tag, [ $scope.message.id ])
                        .success(function () {
                            growl.info("Added message to " + tag.name, { ttl: 3000 });
                            $scope.loadTags();
                        })
                }
            };


            /** Adds the current message to the tag selected via the Message Tag dialog */
            $scope.addToTagDialog = function () {
                MessageService.messageTagsDialog(false).result
                    .then($scope.addToTag);
            };


            /** Adds the current message to the last selected tag */
            $scope.addToLastSelectedTag = function () {
                $scope.addToTag(MessageService.getLastMessageTagSelection());
            };


            /** Removes the current message from the given tag */
            $scope.removeFromTag = function (tag) {
                MessageService.removeMessagesFromTag(tag, [ $scope.message.id ])
                    .success(function () {
                        growl.info("Removed message from " + tag.name, { ttl: 3000 });
                        $scope.loadTags();
                    })
            };


            /*****************************/
            /** Promulgation            **/
            /*****************************/


            // Called with a promulgation-specific endpoint to generate a promulgation
            $scope.generatePromulgation = function(promulgation) {

                MessageService.generatePromulgation(promulgation.type.typeId, $scope.message)
                    .success(function (result) {
                        angular.forEach($scope.message.promulgations, function(p) {
                            if (p.type.typeId === promulgation.type.typeId) {
                                $.extend(true, p, result);
                                $scope.setDirty();
                            }
                        });
                    })
                    .error(function () {
                        console.error("Error generating promulgation for " + type);
                    });
            };


            /*****************************/
            /** Other action menu ops   **/
            /*****************************/


            /** Expands or collapses all field editors **/
            $scope.expandCollapseFields = function (expand) {
                angular.forEach($scope.editMode, function (value) {
                    for (var x = 0; x < value.length; x++) {
                        value[x] = expand;
                    }
                });
            };


            /** Opens the message print dialog */
            $scope.pdf = function () {
                if ($scope.message.created) {
                    MessageService.printMessage($scope.message.id);
                }
            };


            /** Opens a dialog that allows the editor to compare this message with another message **/
            $scope.compareMessages = function () {
                if ($scope.message.created) {
                    MessageService.compareMessagesDialog(undefined, $scope.message.id);
                }
            };


            /*****************************/
            /** Thumbnails handling     **/
            /*****************************/

            $scope.imgCacheBreaker = new Date().getTime();


            /** Returns the URL to the thumbnail **/
            $scope.thumbnailPath = function () {
                return ($scope.message.thumbnailPath)
                    ? '/rest/repo/file/' + $scope.message.thumbnailPath + '?cb=' + $scope.imgCacheBreaker
                    : '/img/map_image_placeholder.png';
            };


            /** Called when the thumbnail has changed **/
            $scope.thumbnailUpdated = function () {
                $scope.imgCacheBreaker = new Date().getTime();
                $scope.setDirty();
            };


            /** Opens the message thumbnail dialog */
            $scope.messageThumbnailDialog = function () {
                $uibModal.open({
                    controller: "MessageThumbnailDialogCtrl",
                    templateUrl: "/app/editor/message-thumbnail-dialog.html",
                    size: 'sm',
                    resolve: {
                        message: function () { return $scope.message; }
                    }
                }).result.then(function (image) {
                    if (image) {
                        var path = $scope.message.editRepoPath + '/' + $scope.message.revision;
                        MessageService.changeMessageMapImage(path, image)
                            .success(function (thumbnailPath) {
                                $scope.message.thumbnailPath = thumbnailPath;
                                $scope.thumbnailUpdated();
                                growl.info("Updated message thumbnail", { ttl: 3000 });
                            });
                    }
                });
            };


            /** Opens the upload-message thumbnail dialog **/
            $scope.uploadMessageThumbnailDialog = function () {
                var path = $scope.message.editRepoPath + '/' + $scope.message.revision;
                UploadFileService.showUploadFileDialog(
                    'Upload thumbnail image',
                    '/rest/message-map-image/' + path ,
                    'png,jpg,jpeg,gif',
                    true).result
                    .then(function (thumbnailPath) {
                        $scope.message.thumbnailPath = thumbnailPath;
                        $scope.thumbnailUpdated();
                        growl.info("Updated message thumbnail", { ttl: 3000 });
                    });
            };


            /** Clears the current custom message thumbnail **/
            $scope.clearMessageThumbnail = function () {

                var thumbnailPath = $scope.message.thumbnailPath;
                if (thumbnailPath) {
                    delete $scope.message.thumbnailPath;
                    $scope.thumbnailUpdated();
                    growl.info("Deleted message thumbnail", { ttl: 3000 });

                    // To preserve revision history, we only delete the actual thumbnail file from the
                    // repository if it is the latest (unsaved) revision.
                    if (thumbnailPath.indexOf($scope.message.editRepoPath + '/' + $scope.message.revision + '/') === 0) {
                        MessageService.deleteAttachmentFile(thumbnailPath)
                            .success(function () {
                                console.log("Deleted thumbnail file " + thumbnailPath);
                            })
                            .error(function () {
                                console.error("Error thumbnail file " + thumbnailPath);
                            });
                    }
                }
            };


            /*****************************/
            /** Attachments             **/
            /*****************************/


            /** Called when new attachments have successfully been uploaded **/
            $scope.attachmentsUploaded = function (result) {
                if (!$scope.message.attachments) {
                    $scope.message.attachments = [];
                }
                angular.forEach(result, function (attachment) {
                    LangService.checkDescs(attachment, initAttachmentDescField, undefined, $rootScope.modelLanguages);
                    $scope.message.attachments.push(attachment);
                });
                growl.info("Attachments uploaded", { ttl: 3000 });
                $scope.setDirty();
                $scope.$$phase || $scope.$apply();
            };


            /** Called when new attachments have failed to be uploaded **/
            $scope.attachmentsUploadError = function(status, statusText) {
                growl.info("Error uploading attachments: " + statusText, { ttl: 5000 });
                $scope.$$phase || $scope.$apply();
            };


            /** Deletes the given attachment **/
            $scope.deleteAttachment = function (attachment) {
                var index = $.inArray(attachment, $scope.message.attachments);
                if (index > -1) {
                    $scope.message.attachments.splice( index, 1 );
                    $scope.setDirty();

                    // To preserve revision history, we only delete the attachment file from the
                    // repository if it is the latest (unsaved) revision.
                    if (attachment.path.indexOf($scope.message.editRepoPath + '/' + $scope.message.revision + '/') === 0) {
                        var filePath = MessageService.attachmentEditRepoPath($scope.message, attachment);
                        MessageService.deleteAttachmentFile(filePath)
                            .success(function () {
                                console.log("Deleted attachment " + filePath);
                            })
                            .error(function () {
                                console.error("Error deleting attachment " + filePath);
                            });
                    }
                }
            };
            

            /** Returns if the attachment can be displayed inline **/
            $scope.canDisplayAttachment = function (att) {
                return att.type && (att.type.startsWith('image/') || att.type.startsWith('video/'));
            };


            /** Called when the display property is updated **/
            $scope.attachmentDisplayUpdated = function (att) {
                if (att.display === '') {
                    delete att.display;
                    delete att.width;
                    delete att.height;
                }
            };

            /*****************************/
            /** Bootstrap editor        **/
            /*****************************/

            $scope.init();
        }])



    /*******************************************************************
     * EditorCtrl sub-controller that handles message status changes.
     *******************************************************************/
    .controller('EditorStatusCtrl', ['$scope', '$rootScope', '$state', 'growl', 'MessageService', 'LangService', 'DialogService',
        function ($scope, $rootScope, $state, growl, MessageService, LangService, DialogService) {
            'use strict';

            $scope.previewLang = $rootScope.language;
            $scope.isEditor = $rootScope.hasRole('editor');

            /** Create a preview message, i.e. a message sorted to the currently selected language **/
            $scope.updatePreviewMessage = function () {
                $scope.previewMessage = undefined;
                if ($scope.message) {
                    $scope.previewMessage = angular.copy($scope.message);
                    LangService.sortMessageDescs($scope.previewMessage, $scope.previewLang);
                }
            };
            $scope.$watch("message", $scope.updatePreviewMessage, true);


            /** Set the preview language **/
            $scope.previewLanguage = function (lang) {
                $scope.previewLang = lang;
                $scope.updatePreviewMessage();
            };


            /** Reloads the message and optionally growls the parameter */
            $scope.reloadMessage = function (msg) {
                // Call parent controller init() method
                $scope.init();
                if (msg) {
                    growl.info(msg, {ttl: 3000});
                }
            };


            /** Return if the field (e.g. "title") is defined in any of the message descriptors */
            function descFieldDefined(field) {
                for (var x = 0; x < $scope.message.descs.length; x++) {
                    var desc = $scope.message.descs[x];
                    if (desc[field] && desc[field].length > 0) {
                        return true;
                    }
                }
                return false;
            }


            /**
             * Returns if the message is in a state where it could be published.
             * The "status" field is not validated, since this is left to the callee to verify this.
             */
            function canPublish() {
                var msg = $scope.message;
                var error = '';
                if (!msg.type) {
                    error += '<li>Type</li>';
                }
                if (!msg.messageSeries) {
                    error += '<li>Message Series</li>';
                }
                if (!descFieldDefined('title')) {
                    error += '<li>Title</li>';
                }
                if (error.length > 0) {
                    growl.error("Missing fields:\n<ul>" + error + "</ul>", {ttl: 5000});
                    return false;
                }
                return true;
            }


            /** Verify the draft message **/
            $scope.verify = function () {
                if ($scope.message.status !== 'DRAFT') {
                    growl.error("Only draft messages can be verified", {ttl: 5000});
                    return;
                }

                // Validate that relevant message fields are defined
                if (!canPublish()) {
                    return;
                }

                DialogService.showConfirmDialog(
                    "Verify draft?", "Verify draft?")
                    .then(function() {
                        MessageService.updateMessageStatus($scope.message, 'VERIFIED')
                            .success(function() { $scope.reloadMessage("Message verified"); })
                            .error(function(err) {
                                growl.error("Verification failed\n" + err, {ttl: 5000});
                            });
                    });
            };


            /** Publish the message **/
            $scope.publish = function () {

                // First check that the message is valid
                if ($scope.message.status !== 'VERIFIED') {
                    growl.error("Only verified draft messages can be published", {ttl: 5000});
                    return;
                }

                // Validate that relevant message fields are defined
                if (!canPublish()) {
                    return;
                }

                // Check if there are any referenced published messages that should be cancelled
                MessageService.referencedMessages(
                        $scope.message.id,
                        ['CANCELLATION', 'REPETITION', 'REPETITION_NEW_TIME', 'UPDATE'],
                        'PUBLISHED'
                    ).success(function (messages) {

                        var modalOptions = {
                            closeButtonText: 'Cancel',
                            actionButtonText: 'Publish Message',
                            headerText: 'Publish Message',
                            publishOptions: { cancelReferencedMessage: false, referencedMessages: messages },
                            templateUrl: "publishMessage.html"
                        };

                        DialogService.showDialog({}, modalOptions)
                            .then(function () {

                                var params = undefined;
                                if (modalOptions.publishOptions.cancelReferencedMessage) {
                                    params = 'cancelReferencedMessages=true'
                                }

                                MessageService.updateMessageStatus($scope.message, 'PUBLISHED', params)
                                    .success(function() { $scope.reloadMessage("Message published"); })
                                    .error(function(err) {
                                        growl.error("Publishing failed\n" + err, {ttl: 5000});
                                    });
                            });

                    });
            };


            /** Delete the draft message **/
            $scope.deleteDraft = function () {
                if ($scope.message.status !== 'DRAFT' && $scope.message.status !== 'VERIFIED') {
                    growl.error("Only draft and verified messages can be deleted", {ttl: 5000});
                    return;
                }

                DialogService.showConfirmDialog(
                    "Delete message?", "Delete message?")
                    .then(function() {
                        MessageService.updateMessageStatus($scope.message, 'DELETED')
                            .success(function() { $scope.reloadMessage("Message deleted"); })
                            .error(function(err) {
                                growl.error("Deletion failed\n" + err, {ttl: 5000});
                            });
                    });
            };


            /** Copy the message **/
            $scope.copy = function () {
                DialogService.showConfirmDialog(
                    "Copy Message?", "Copy Message?")
                    .then(function() {
                        // Navigate to the message editor page
                        $state.go(
                            'editor.copy',
                            { id: $scope.message.id,  referenceType : 'REFERENCE' },
                            { reload: true }
                        );
                    });
            };


            /** Cancel the message **/
            $scope.cancel = function () {
                if ($scope.message.status !== 'PUBLISHED') {
                    growl.error("Only published messages can be cancelled", {ttl: 5000});
                    return;
                }

                var modalOptions = {
                    closeButtonText: 'Cancel',
                    actionButtonText: 'Confirm Cancellation',
                    headerText: 'Cancel Message',
                    cancelOptions: { createCancelMessage: false },
                    templateUrl: "cancelMessage.html"
                };

                DialogService.showDialog({}, modalOptions)
                    .then(function () {
                        MessageService.updateMessageStatus($scope.message, 'CANCELLED')
                            .success(function() {
                                if (modalOptions.cancelOptions.createCancelMessage) {
                                    // Navigate to the message editor page
                                    $state.go(
                                        'editor.copy',
                                        { id: $scope.message.id,  referenceType : 'CANCELLATION' },
                                        { reload: true }
                                    );
                                } else {
                                    $scope.reloadMessage("Message cancelled");
                                }
                            })
                            .error(function(err) {
                                growl.error("Cancellation failed\n" + err, {ttl: 5000});
                            });
                    });
            };


        }])



    /*******************************************************************
     * EditorCtrl sub-controller that handles message history.
     *******************************************************************/
    .controller('EditorHistoryCtrl', ['$scope', '$rootScope', '$timeout', 'MessageService',
        function ($scope, $rootScope, $timeout, MessageService) {
            'use strict';

            $scope.messageHistory = [];
            $scope.selectedHistory = [];
            $scope.historyAttachments = undefined;

            /** Loads the message history **/
            $scope.loadHistory = function () {
                if ($scope.message && $scope.message.id) {
                    MessageService.messageHistory($scope.message.id)
                        .success(function (history) {
                            $scope.messageHistory.length = 0;
                            angular.forEach(history, function (hist) {
                                hist.selected = false;
                                $scope.messageHistory.push(hist);
                            })
                        });
                }
            };

            // Load the message history (after the message has been loaded)
            $timeout($scope.loadHistory, 200);


            /** updates the history selection **/
            $scope.updateSelection = function () {
                $scope.selectedHistory.length = 0;
                angular.forEach($scope.messageHistory, function (hist) {
                    if (hist.selected) {
                        $scope.selectedHistory.unshift(hist);
                    }
                });

                // If one message history is selected, extract its attachments
                $scope.historyAttachments = undefined;
                if ($scope.selectedHistory.length === 1) {
                    try {
                        var hist1 = JSON.parse($scope.selectedHistory[0].snapshot);
                        $scope.historyAttachments = hist1.attachments;
                    } catch (e) {
                    }
                }
            }

        }])


    /*******************************************************************
     * EditorCtrl sub-controller that handles message comments.
     *******************************************************************/
    .controller('EditorCommentsCtrl', ['$scope', '$rootScope', '$timeout', 'MessageService',
        function ($scope, $rootScope, $timeout, MessageService) {
            'use strict';

            $scope.comments = [];
            $scope.comment = undefined;

            // Configuration of the Comment TinyMCE editors
            $scope.commentTinymceOptions = {
                resize: false,
                valid_elements : '*[*]', // NB: This allows insertion of all html elements, including javascript
                statusbar : false,
                menubar: false,
                plugins: [ "autolink lists link image anchor", "code textcolor", "media table contextmenu paste" ],
                contextmenu: "link image inserttable | cell row column deletetable",
                toolbar: "styleselect | bold italic | forecolor backcolor | alignleft aligncenter alignright alignjustify | "
                + "bullist numlist  | outdent indent | link image table"
            };


            /** Loads the message history **/
            $scope.loadComments = function () {
                $scope.comment = undefined;
                if ($scope.message && $scope.message.id) {
                    MessageService.comments($scope.message.id)
                        .success(function (comments) {
                            $scope.comments = comments;

                            // Update the number of unacknowledged comments for the message
                            var unackComments = 0;
                            angular.forEach(comments, function (comment) {
                                if (!comment.acknowledgeDate) {
                                    unackComments++;
                                }
                                $scope.message.unackComments = unackComments;
                            })

                        });
                }
            };

            // Load the message comments (after the message has been loaded)
            $timeout($scope.loadComments, 200);


            /** Set the form as pristine **/
            $scope.setPristine = function () {
                // $scope.commentForm does not work (form tied to sub-scope of this controller?)
                try {
                    angular.element($("#commentForm")).scope().commentForm.$setPristine();
                } catch (err) {
                }
            };


            /** Selects a new comment for viewing/editing **/
            $scope.selectComment = function (comment) {
                $scope.comment = angular.copy(comment);
                $scope.setPristine();
            };


            /** Returns if the given comment is selected **/
            $scope.isSelected = function (comment) {
                return comment && $scope.comment && comment.id === $scope.comment.id;
            };


            /** Create a template for a new comment **/
            $scope.newComment = function () {
                $scope.comment = { comment: '' };
            };


            /** Returns if the comment can be saved (and optionally e-mailed) **/
            $scope.canSaveComment = function (sendEmail) {
                return $scope.comment &&
                    sendEmail === ($scope.comment.emailAddresses && $scope.comment.emailAddresses.length > 0)
            };


            /** Saves the current comment comment **/
            $scope.saveComment = function () {
                if ($scope.comment && $scope.comment.id) {
                    MessageService.updateComment($scope.message.id, $scope.comment)
                        .success($scope.loadComments);
                } else if ($scope.comment) {
                    MessageService.createComment($scope.message.id, $scope.comment)
                        .success($scope.loadComments);
                }
            };


            /** Acknowledges the current comment comment **/
            $scope.acknowledge = function () {
                if ($scope.comment && $scope.comment.id) {
                    MessageService.acknowledgeComment($scope.message.id, $scope.comment)
                        .success($scope.loadComments);
                }
            };


            /** Returns if the current comment can be edited **/
            $scope.canEditComment = function () {
                return $scope.comment &&
                    (!$scope.comment.id || $scope.comment.ownComment || $rootScope.hasRole('admin'));
            };


            /** Cancels editing a comment **/
            $scope.cancel = function () {
                $scope.comment = undefined;
            };
        }])



    /*******************************************************************
     * EditorCtrl sub-controller that displays recently edited messages.
     *******************************************************************/
    .controller('RecentlyEditedMessagesCtrl', ['$scope', '$rootScope', '$state', '$timeout', 'MessageService',
        function ($scope, $rootScope, $state, $timeout, MessageService) {
            'use strict';

            $scope.recentlyEditedMessages = [];


            // Load recently edited messages for the current user
            MessageService.recentlyEditedDrafts()
                .success(function (messages) {
                    $scope.recentlyEditedMessages = messages;
                });


            /** Edit the given message **/
            $scope.edit = function (msgId) {
                $state.go('editor.edit', {id: msgId});
                $timeout($scope.init, 100);
            }
        }]);

