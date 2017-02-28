/*
 * Copyright 2017 Danish Maritime Authority.
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
 * The admin controllers.
 */
angular.module('niord.admin')

    /**
     * ********************************************************************************
     * TemplatesAdminCtrl
     * ********************************************************************************
     * Message Templates Admin Controller
     * Controller for the Admin Message Templates page
     */
    .controller('TemplatesAdminCtrl', ['$scope', '$timeout', '$uibModal', 'growl',
                'AdminTemplateService', 'AdminCategoryService', 'LangService', 'DialogService', 'UploadFileService',
        function ($scope, $timeout, $uibModal, growl,
                  AdminTemplateService, AdminCategoryService, LangService, DialogService, UploadFileService) {
            'use strict';

            $scope.template = undefined; // The template being edited
            $scope.editMode = 'add';

            $scope.params = {
                name: '',
                category: undefined,
                domain: undefined
            };
            $scope.pageData = {
                page: 1,
                maxSize: 10
            };
            $scope.searchResult = {
                data: [],
                size: 0,
                total: 0
            };


            /** Loads the message templates from the back-end */
            $scope.loadTemplates = function() {
                $scope.template = undefined;
                AdminTemplateService
                    .searchTemplates($scope.params, $scope.pageData)
                    .success(function (searchResult) {
                        $scope.searchResult = searchResult;
                    });
            };

            // Monitor params and page
            $scope.$watch("params", function () {
                $scope.pageData.page = 1;
                $scope.loadTemplates();
            }, true);
            $scope.$watch("pageData", $scope.loadTemplates, true);


            // Used to ensure that description entities have a "name" field
            function ensureNameField(desc) {
                desc.name = '';
            }

            /** Adds a new message template **/
            $scope.addTemplate = function () {
                $scope.editMode = 'add';
                $scope.template = {
                    id: undefined,
                    category: undefined,
                    domains: [],
                    descs: [],
                    templatePath: undefined
                };
                LangService.checkDescs($scope.template, ensureNameField);
            };


            /** Edits a message template **/
            $scope.editTemplate = function (template) {
                AdminTemplateService.getTemplate(template.id)
                    .success(function (tmpl) {
                        $scope.editMode = 'edit';
                        $scope.template = tmpl;
                        LangService.checkDescs($scope.template, ensureNameField);
                    });
            };


            /** Copies a message template **/
            $scope.copyTemplate = function (template) {
                AdminTemplateService.getTemplate(template.id)
                    .success(function (tmpl) {
                        $scope.editMode = 'add';
                        $scope.template = tmpl;
                        $scope.template.id = undefined;
                        LangService.checkDescs($scope.template, ensureNameField);
                    });
            };


            /** Called when the category selection has changed. Potentially update template name and template path **/
            $scope.categoryChanged = function () {
                // If the template is defined, update name and compose it from the English category name
                if ($scope.template && $scope.template.category) {
                    AdminCategoryService.getCategory($scope.template.category)
                        .success(function (category) {
                            angular.forEach(category.descs, function (desc) {
                                var tmplDesc = LangService.descForLanguage($scope.template, desc.lang);
                                if (tmplDesc && !tmplDesc.name) {
                                    tmplDesc.name = desc.name;
                                }
                                if (desc.lang == 'en' && !$scope.template.templatePath){
                                    var name = desc.name.toLowerCase().replace(/ /g, '-');
                                    $scope.template.templatePath = 'templates/message/' + name + '.ftl';
                                }
                            })
                        })
                }
            };

            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving message template", { ttl: 5000 });
            };


            /** Saves the current message template being edited */
            $scope.saveTemplate = function () {

                if ($scope.template && $scope.editMode == 'add') {
                    AdminTemplateService
                        .createTemplate($scope.template)
                        .success($scope.loadTemplates)
                        .error($scope.displayError);
                } else if ($scope.template && $scope.editMode == 'edit') {
                    AdminTemplateService
                        .updateTemplate($scope.template)
                        .success($scope.loadTemplates)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given message template */
            $scope.deleteTemplate = function (template) {
                DialogService.showConfirmDialog(
                    "Delete message Template?", "Delete message template?")
                    .then(function() {
                        AdminTemplateService
                            .deleteTemplate(template)
                            .success($scope.loadTemplates)
                            .error($scope.displayError);
                    });
            };


            /** Generate an export file */
            $scope.exportTemplates = function () {
                AdminTemplateService
                    .exportTicket('admin')
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.href = '/rest/templates/all?ticket=' + ticket;
                        link.click();
                    });
            };


            /** Opens the upload-charts dialog **/
            $scope.uploadTemplatesDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Message Templates File',
                    '/rest/templates/upload-templates',
                    'json');
            };


            $scope.testMessageData = {
                messageId: undefined
            };


            /** Tests the current template with the specified message ID */
            $scope.executeTemplate = function (template) {
                if (template && $scope.testMessageData.messageId) {
                    AdminTemplateService
                        .executeTemplate(template, $scope.testMessageData.messageId)
                        .success(function (message) {
                            $uibModal.open({
                                controller: "TemplateResultDialogCtrl",
                                templateUrl: "templateResultDialog.html",
                                size: 'md',
                                resolve: {
                                    message: function () { return message; }
                                }
                            }).result.then($scope.loadMessageSeries)
                        })
                        .error(function (data, status) {
                            growl.error("Error executing template (code: " + status + ")", {ttl: 5000})
                        });
                }
            };

        }])


    /*******************************************************************
     * Displays the result of executing a template on a message
     *******************************************************************/
    .controller('TemplateResultDialogCtrl', ['$scope', '$rootScope', 'LangService', 'message',
        function ($scope, $rootScope, LangService, message) {
            'use strict';

            $scope.message = message;
            $scope.previewLang = $rootScope.language;

            /** Create a preview message, i.e. a message sorted to the currently selected language **/
            $scope.createPreviewMessage = function () {
                $scope.previewMessage = undefined;
                if ($scope.message) {
                    $scope.previewMessage = angular.copy($scope.message);
                    LangService.sortMessageDescs($scope.previewMessage, $scope.previewLang);
                }
            };
            $scope.$watch("message", $scope.createPreviewMessage, true);


            /** Set the preview language **/
            $scope.previewLanguage = function (lang) {
                $scope.previewLang = lang;
                $scope.createPreviewMessage();
            };
        }]);
