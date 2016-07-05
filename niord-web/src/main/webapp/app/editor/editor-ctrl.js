
/**
 * The Message Editor controller
 */
angular.module('niord.editor')

    /**
     * Main message editor controller
     */
    .controller('EditorCtrl', ['$scope', '$rootScope', '$stateParams', '$state', '$http', '$timeout', '$uibModal', 'growl',
            'MessageService', 'LangService', 'MapService', 'UploadFileService', 'DateIntervalService',
        function ($scope, $rootScope, $stateParams, $state, $http, $timeout, $uibModal, growl,
                  MessageService, LangService, MapService, UploadFileService, DateIntervalService) {
            'use strict';

            $scope.message = undefined;
            $scope.initId = $stateParams.id || '';
            $scope.referenceType = $stateParams.referenceType || '';

            $scope.editMode = {
                type: false,
                orig_info: false,
                id: false,
                title: false,
                references: false,
                time: false,
                areas: false,
                categories: false,
                positions: false,
                charts: false,
                subject: false,
                description: false,
                attachments: false
            };

            $scope.messageSeries = [];

            $scope.attachmentUploadUrl = undefined;

            // Record if the Edit page was entered from a message list URL
            $scope.backToListUrl = ($rootScope.lastUrl && $rootScope.lastUrl.indexOf('/messages/') != -1)
                        ? $rootScope.lastUrl : undefined;

            // This will be set when the "Save message" button is clicked and will
            // disable the button, to avoid double-clicks
            $scope.messageSaving = false;


            /*****************************/
            /** Initialize the editor   **/
            /*****************************/

            // Used to ensure that description entities have various field
            function initMessageDescField(desc) {
                desc.title = '';
                desc.description = '';
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

                // Ensure that the list of date intervals is defined
                if (!msg.dateIntervals) {
                    msg.dateIntervals = [];
                }

                // Ensure that localized desc fields are defined for all languages
                LangService.checkDescs(msg, initMessageDescField, undefined, $rootScope.modelLanguages);

                // Instantiate the feature collection from the message geometry
                if (!msg.geometry) {
                    msg.geometry = {
                        type: 'FeatureCollection',
                        features: []
                    }
                } else if (!msg.geometry.features) {
                    msg.geometry.features = [];
                }
                $scope.serializeCoordinates();

                // Determine the message series for the current domain and message mainType
                $scope.messageSeries.length = 0;
                if ($rootScope.domain && $rootScope.domain.messageSeries) {
                    angular.forEach($rootScope.domain.messageSeries, function (series) {
                        if (series.mainType == msg.mainType) {
                            if (msg.messageSeries && msg.messageSeries.seriesId == series.seriesId) {
                                $scope.messageSeries.push(msg.messageSeries);
                            } else {
                                $scope.messageSeries.push(series);
                            }
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
                $scope.attachmentUploadUrl = '/rest/messages/attachments/' + msg.editRepoPath + '/attachments';
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

                if ($scope.editType == 'edit' && $scope.initId) {
                    // Case 1) The message ID may be specified
                    MessageService.editableDetails($scope.initId)
                        .success(function (message) {
                            $scope.message = message;
                            $scope.initMessage(true);
                        })
                        .error(function (data, status) {
                            growl.error("Error loading message (code: " + status + ")", {ttl: 5000})
                        });

                } else if ($scope.editType == 'copy' && $scope.initId) {
                    // Case 2) Create a copy of a message
                    MessageService.copyMessageTemplate($scope.initId, $scope.referenceType)
                        .success(function (message) {
                            $scope.message = message;
                            $scope.initMessage();
                        })
                        .error(function (data, status) {
                            growl.error("Error copying message (code: " + status + ")", {ttl: 5000})
                        });

                } else if ($scope.editType == 'template') {
                    // Case 3) The editor may be based on a template message, say, from the AtoN selection page
                    $scope.createMessage($rootScope.templateMessage.mainType, $rootScope.templateMessage);
                }
            };


            /*****************************/
            /** Editor functionality    **/
            /*****************************/


            /** Set the form as pristine **/
            $scope.setPristine = function () {
                // $scope.editForm does not work (form tied to sub-scope of this controller?)
                try {
                    angular.element($("#editForm")).scope().editForm.$setPristine();
                } catch (err) {
                }
            };


            /** Set the form as dirty **/
            $scope.setDirty = function () {
                // $scope.editForm does not work (form tied to sub-scope of this controller?)
                try {
                    angular.element($("#editForm")).scope().editForm.$setDirty();
                } catch (err) {
                }
            };


            /** Returns if the message is editable **/
            $scope.editable = function () {
                var msg = $scope.message;
                return msg && msg.status &&
                    (msg.status == 'DRAFT' || msg.status == 'VERIFIED' || msg.status == 'IMPORTED'
                     || $rootScope.hasRole('sysadmin'));
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


            /** Returns if the given message field (group) is valid **/
            $scope.fieldValid = function(fieldId) {
                var msg = $scope.message;
                switch (fieldId) {
                    case 'type':
                        return msg.mainType && msg.type;
                    case 'id':
                        return msg.messageSeries !== undefined;
                    case 'areas':
                        return msg.areas && msg.areas.length > 0;
                }
                return true;
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
            };


            /** Deletes the given date interval from the list of message date intervals **/
            $scope.deleteDateInterval = function (dateInterval) {
                if ($.inArray(dateInterval, $scope.message.dateIntervals) > -1) {
                    $scope.message.dateIntervals.splice( $.inArray(dateInterval, $scope.message.dateIntervals), 1 );
                    $scope.setDirty();
                }
            };


            /** Adds a new date interval to the list of message date intervals */
            $scope.addDateInterval = function () {
                $scope.message.dateIntervals.push({ allDay: false, fromDate: undefined, toDate: undefined });
            };


            /** Adds a copy of the given date interval to the list with the given date offset */
            $scope.copyDateInterval = function (dateInterval, offset) {
                var di = angular.copy(dateInterval);
                if (offset && offset > 0) {
                    if (di.fromDate) {
                        di.fromDate = moment(di.fromDate).add(1, "days").valueOf();
                    }
                    if (di.toDate) {
                        di.toDate = moment(di.toDate).add(1, "days").valueOf();
                    }
                }
                $scope.message.dateIntervals.push(di);
            };


            /** Generates textual versions of the current date intervals **/
            $scope.generateTimeDesc = function () {
                angular.forEach($scope.message.descs, function (desc) {
                   desc.time = '';
                    if ($scope.message.dateIntervals.length == 0) {
                        desc.time = DateIntervalService.translateDateInterval(desc.lang, null);
                    } else {
                        angular.forEach($scope.message.dateIntervals, function (di) {
                            desc.time += DateIntervalService.translateDateInterval(desc.lang, di) + '\n';
                        });
                    }
                });
            };


            // Use for charts selection
            $scope.charts = [];
            $scope.refreshCharts = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/charts/search?name=' + encodeURIComponent(name) + '&lang=' + $rootScope.language + '&limit=10'
                ).then(function(response) {
                    $scope.charts = response.data;
                });
            };


            /** Computes the charts intersecting with the current message geometry **/
            $scope.computeCharts = function () {
                if ($scope.message.geometry.features.length > 0) {
                    MessageService.intersectingCharts($scope.message.geometry)
                        .success(function (charts) {
                            // Prune the returned chart data
                            $scope.message.charts = charts.map(function (chart) {
                                return {
                                    chartNumber: chart.chartNumber,
                                    internationalNumber: chart.internationalNumber
                                }
                            });
                            $scope.setDirty();
                        });
                }
            };


            // Use for area selection
            $scope.areas = [];
            $scope.refreshAreas = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/areas/search?name=' + encodeURIComponent(name) +
                    '&domain=true'+
                    '&lang=' + $rootScope.language +
                    '&limit=10'
                ).then(function(response) {
                    $scope.areas = response.data;
                });
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
                                    msg.geometry.features.push(feature);
                                    $scope.geometrySaved(msg.geometry);
                                }
                            });
                        });
                }
            };

            // Use for category selection
            $scope.categories = [];
            $scope.refreshCategories = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/categories/search?name=' + encodeURIComponent(name) +
                    '&domain=true' +
                    '&lang=' + $rootScope.language +
                    '&limit=10'
                ).then(function(response) {
                    $scope.categories = response.data;
                });
            };


            // Recursively formats the names of the parent lineage for areas and categories
            $scope.formatParents = function(child) {
                var txt = undefined;
                if (child) {
                    txt = (child.descs && child.descs.length > 0) ? child.descs[0].name : 'N/A';
                    if (child.parent) {
                        txt = $scope.formatParents(child.parent) + " - " + txt;
                    }
                }
                return txt;
            };


            $scope.showCoordinates = false;
            $scope.toggleShowCoordinates = function () {
                $scope.showCoordinates = !$scope.showCoordinates;
            };


            /** Serializes the message coordinates **/
            $scope.featureCoordinates = [];
            $scope.serializeCoordinates = function () {
                // Compute on-demand
                var msg = $scope.message;
                $scope.featureCoordinates.length = 0;
                if (msg.geometry.features.length > 0) {
                    var index = 1;
                    angular.forEach(msg.geometry.features, function (feature) {
                        var coords = [];
                        MapService.serializeCoordinates(feature, coords);
                        if (coords.length > 0) {
                            var name = feature.properties ? feature.properties['name:' + $rootScope.language] : undefined;
                            $scope.featureCoordinates.push({
                                coords: coords,
                                startIndex: index,
                                name: name
                            });
                            index += coords.length;
                        }
                    });
                }
                return $scope.featureCoordinates;
            };

            /** called when the message geometry has been changed **/
            $scope.geometrySaved = function () {
                $scope.serializeCoordinates();
                $scope.setDirty();
            };

            
            /** Returns if the current page matches the given page url **/
            $scope.onPage = function (page) {
                return $state.includes(page);
            };


            /** Called when relevant attributes have been changed that may affect the auto-generated title lines */
            $scope.updateTitleLine = function () {
                if ($scope.message.autoTitle) {
                    // Construct a  message template that contains attributes affecting title line
                    var msg = {
                        areas: $scope.message.areas,
                        descs: $scope.message.descs.map(function (desc) {
                           return { lang: desc.lang, subject: desc.subject, vicinity: desc.vicinity }
                        })
                    };
                    MessageService.computeTitleLine(msg)
                        .success(function (message) {
                            angular.forEach(message.descs, function (desc) {
                                var d = LangService.descForLanguage($scope.message, desc.lang);
                                if (d) {
                                    d.title = desc.title;
                                }
                            })
                        })
                }
            };


            /*****************************/
            /** TinyMCE functions       **/
            /*****************************/

            /** File callback function - called from the TinyMCE image and link dialogs **/
            $scope.fileBrowserCallback = function(field_name, url, type, win) {
                $(".mce-window").hide();
                $("#mce-modal-block").hide();
                $scope.$apply(function() {
                    $scope.attachmentDialog('md').result
                        .then(function (result) {
                            $("#mce-modal-block").show();
                            $(".mce-window").show();
                            var file = "/rest/repo/file/" + $scope.message.editRepoPath + "/attachments/"
                                + encodeURIComponent(result.attachment.fileName);
                            win.document.getElementById(field_name).value = file;
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
            $scope.locationsDialog = function (lang) {
                return $uibModal.open({
                    templateUrl: '/app/editor/format-locations-dialog.html',
                    controller: 'FormatMessageLocationsDialogCtrl',
                    size: 'lg',
                    resolve: {
                        featureCollection: function () { return $scope.message.geometry; },
                        lang: function () { return lang; }
                    }
                });
            };


            /** Allows the user to select which location should be inserted and how it should be formatted **/
            $scope.formatLocations = function (editor) {

                // The ID of the parent div has the format "tinymce-<<lang>>"
                var parentDivId = editor.getElement().parentElement.id;
                var lang = parentDivId.split("-")[1];

                $scope.$apply(function() {
                    $scope.locationsDialog(lang).result
                        .then(function (result) {
                            editor.insertContent(result);
                        });
                });
            };


            // Configuration of the TinyMCE editors
            $scope.tinymceOptions = {
                resize: false,
                valid_elements : '*[*]', // NB: This allows insertion of all html elements, including javascript
                theme: "modern",
                skin: 'light',
                statusbar : false,
                menubar: false,
                plugins: [ "autolink lists link image anchor", "code textcolor", "media table contextmenu paste" ],
                contextmenu: "link image inserttable | cell row column deletetable",
                toolbar: "styleselect | bold italic | forecolor backcolor | alignleft aligncenter alignright alignjustify | "
                         + "bullist numlist  | outdent indent | link image table | code | niordlocations",
                file_browser_callback: $scope.fileBrowserCallback,
                setup : function ( editor ) {
                    editor.addButton( 'niordlocations', {
                        title: 'Insert Locations',
                        icon: 'map-marker',
                        onclick : function () { $scope.formatLocations(editor); }
                    });
                }
            };


            /*****************************/
            /** Action menu functions   **/
            /*****************************/


            /** Expands or collapses all field editors **/
            $scope.expandCollapseFields = function (expand) {
                angular.forEach($scope.editMode, function (value, key) {
                    if (value !== expand) {
                        $scope.editMode[key] = expand;
                    }
                });
            };


            /** Opens the message print dialog */
            $scope.pdf = function () {
                if ($scope.message.id) {
                    MessageService.printMessage($scope.message.id);
                }
            };


            /** Opens a dialog that allows the editor to compare this message with another message **/
            $scope.compareMessages = function () {
                $uibModal.open({
                    controller: "MessageComparisonDialogCtrl",
                    templateUrl: "/app/editor/message-comparison-dialog.html",
                    size: 'lg',
                    resolve: {
                        message: function () { return $scope.message; }
                    }
                });
            };


            /*****************************/
            /** Thumbnails handling     **/
            /*****************************/

            $scope.imgCacheBreaker = new Date().getTime();


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
                    if (image && $scope.message.editRepoPath) {
                        MessageService.changeMessageMapImage($scope.message.editRepoPath, image)
                            .success(function () {
                                $scope.thumbnailUpdated();
                                growl.info("Updated message thumbnail", { ttl: 3000 });
                            });
                    }
                });
            };


            /** Opens the upload-charts dialog **/
            $scope.uploadMessageThumbnailDialog = function () {
                if ($scope.message.editRepoPath) {
                    UploadFileService.showUploadFileDialog(
                        'Upload thumbnail image',
                        '/rest/message-map-image/' + $scope.message.editRepoPath,
                        'png,jpg,jpeg,gif').result
                        .then($scope.thumbnailUpdated);
                }
            };


            /** Clears the current message thumbnail **/
            $scope.clearMessageThumbnail = function () {
                if ($scope.message.editRepoPath) {
                    MessageService.deleteMessageMapImage($scope.message.editRepoPath)
                        .success(function () {
                            $scope.thumbnailUpdated();
                            growl.info("Deleted message thumbnail", { ttl: 3000 })
                        });
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
                if ($.inArray(attachment, $scope.message.attachments) > -1) {
                    var filePath = MessageService.attachmentRepoPath($scope.message, attachment);
                    MessageService.deleteAttachmentFile(filePath)
                        .success(function () {
                            $scope.message.attachments.splice( $.inArray(attachment, $scope.message.attachments), 1 );
                            $scope.setDirty();
                        });
                }
            };
            

            /** Returns if the attachment can be displayed inline **/
            $scope.canDisplayAttachment = function (att) {
                return att.type && (att.type.startsWith('image/') || att.type.startsWith('video/'));
            };


            /** Called when the display property is updated **/
            $scope.attachmentDisplayUpdated = function (att) {
                if (att.display == '') {
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
    .controller('EditorStatusCtrl', ['$scope', '$rootScope', '$state', 'growl', 'MessageService', 'DialogService',
        function ($scope, $rootScope, $state, growl, MessageService, DialogService) {
            'use strict';

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
                if (!msg.areas || msg.areas.length == 0) {
                    error += '<li>Areas</li>';
                }
                if (!descFieldDefined('subject')) {
                    error += '<li>Subject</li>';
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
                if ($scope.message.status != 'DRAFT') {
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
                if ($scope.message.status != 'VERIFIED') {
                    growl.error("Only verified draft messages can be published", {ttl: 5000});
                    return;
                }

                // Validate that relevant message fields are defined
                if (!canPublish()) {
                    return;
                }

                DialogService.showConfirmDialog(
                    "Publish Message?", "Publish Message?")
                    .then(function() {
                        MessageService.updateMessageStatus($scope.message, 'PUBLISHED')
                            .success(function() { $scope.reloadMessage("Message published"); })
                            .error(function(err) {
                                growl.error("Publishing failed\n" + err, {ttl: 5000});
                            });
                    });
            };


            /** Delete the draft message **/
            $scope.delete = function () {
                if ($scope.message.status != 'DRAFT' && $scope.message.status != 'VERIFIED') {
                    growl.error("Only draft and verified messages can be deleted", {ttl: 5000});
                    return;
                }

                DialogService.showConfirmDialog(
                    "Delete draft?", "Delete draft?")
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
                if ($scope.message.status != 'PUBLISHED') {
                    growl.error("Only published messages can be cancelled", {ttl: 5000});
                    return;
                }

                var modalOptions = {
                    closeButtonText: 'Cancel',
                    actionButtonText: 'Confirm Cancellation',
                    headerText: 'Cancel Message',
                    cancelOptions: { createCancelMessage: true },
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
            }

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
    .controller('MessageComparisonDialogCtrl', ['$scope', 'MessageService', 'message',
        function ($scope, MessageService, message) {
            'use strict';

            $scope.message = message;
            $scope.compareMessage = undefined;
            $scope.selectedHistory = [ ];

            /** Initialize the list of messages to compare **/
            $scope.init = function () {
                $scope.compareMessage = undefined;
                $scope.selectedHistory.length = 0;
                $scope.selectedHistory.push({ snapshot: angular.toJson(message) });
            };
            $scope.init();

            $scope.reference = {
                messageId : undefined
            };
            $scope.$watch("reference.messageId", function (messageId) {
                // Reset the message history
                $scope.init();

                // Fetch the message to compare with
                if (messageId && messageId.length > 0) {
                    MessageService.editableDetails(messageId)
                        .success(function (compareMessage) {
                            $scope.compareMessage = compareMessage;
                            if (compareMessage) {
                                // Add on position 0
                                $scope.selectedHistory.unshift({ snapshot: angular.toJson(compareMessage) });
                            }
                        })
                }
            }, true);

        }])



    /*******************************************************************
     * Controller that allows the user to format selected features as text
     *******************************************************************/
    .controller('FormatMessageLocationsDialogCtrl', ['$scope', '$rootScope', '$window', 'growl',
            'MessageService', 'featureCollection', 'lang',
        function ($scope, $rootScope, $window, growl,
              MessageService, featureCollection, lang) {
            'use strict';

            $scope.wmsLayerEnabled = $rootScope.wmsLayerEnabled;
            $scope.featureCollection = angular.copy(featureCollection);
            $scope.lang = lang;
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


            // Initial selection
            $scope.selectFeatures($scope.featureCollection.features.length == 1);
        }])

;

