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
     * Templates Admin Controller
     * Controller for the Admin Templates page
     */
    .controller('TemplatesAdminCtrl', ['$scope', '$timeout', 'growl', 'AdminTemplateService', 'DialogService',
        function ($scope, $timeout, growl, AdminTemplateService, DialogService) {
            'use strict';

            $scope.template = undefined; // The template being edited
            $scope.editMode = 'add';
            $scope.templates = [];
            $scope.search = '';

            $scope.fmEditorOptions = {
                useWrapMode : false,
                showGutter: true,
                mode: 'ftl',
                onLoad: function(editor) {
                    $scope.editor = editor;
                }
            };


            /** Loads the templates from the back-end */
            $scope.loadTemplates = function() {
                $scope.template = undefined;
                AdminTemplateService
                    .getTemplates()
                    .success(function (templates) {
                        $scope.templates = templates;
                    });
            };


            /** Focuses the template path input field **/
            $scope.focusPath = function () {
                $timeout(function () {
                    $('#templatePath').focus();
                }, 100)
            };


            /** Focuses the template path input field **/
            $scope.focusTemplateEditor = function () {
                $timeout(function () {
                    try { $scope.editor.focus(); } catch (e) {}
                }, 100)
            };


            /** Adds a new template **/
            $scope.addTemplate = function () {
                $scope.editMode = 'add';
                $scope.template = {
                    path: '',
                    template: ''
                };
                $scope.focusPath();
            };


            /** Edits a template **/
            $scope.editTemplate = function (template) {
                $scope.editMode = 'edit';
                $scope.template = angular.copy(template);
                $scope.focusTemplateEditor();
            };


            /** Copies a template **/
            $scope.copyTemplate = function (template) {
                $scope.editMode = 'add';
                $scope.template = angular.copy(template);
                // Strip template name from path
                var x = $scope.template.path.lastIndexOf('/');
                if (x != -1) {
                    $scope.template.path = $scope.template.path.substring(0, x + 1);
                }
                $scope.focusPath();
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving template", { ttl: 5000 });
            };


            /** Saves the current template being edited */
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


            /** Deletes the given template */
            $scope.deleteTemplate = function (template) {
                DialogService.showConfirmDialog(
                    "Delete Template?", "Delete template '" + template.path + "'?")
                    .then(function() {
                        AdminTemplateService
                            .deleteTemplate(template)
                            .success($scope.loadTemplates)
                            .error($scope.displayError);
                    });
            };


            /** Reload the templates from the file system **/
            $scope.reloadTemplates = function () {
                DialogService.showConfirmDialog(
                    "Reload Templates?", "Reload templates from the file system?")
                    .then(function() {
                        AdminTemplateService
                            .reloadTemplates()
                            .success($scope.loadTemplates);
                    });
            };

        }]);
