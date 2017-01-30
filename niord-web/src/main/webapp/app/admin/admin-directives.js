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
 * Common directives.
 */
angular.module('niord.common')


    /*************************************
     * Defines a view mode filter panel
     *************************************/
    .directive('adminPage', [function () {
        return {
            restrict: 'EA',
            templateUrl: '/app/admin/admin-page.html',
            replace: true,
            transclude: true,
            scope: {
                adminPageTitle:     "@",
                parentPage:         "@",
                parentPageTitle:    "@"
            },
            link: function(scope) {
                scope.parentPage = scope.parentPage || 'admin';
                scope.parentPageTitle = scope.parentPageTitle || 'Admin';
                console.log("ADMIN PAGE " + scope.parentPage);
            }
        };
    }])


    /*************************************
     * Renders the status of a publication
     *************************************/
    .directive('publicationStatusField', [ 'LangService', function (LangService) {
        'use strict';

        return {
            restrict: 'E',
            template: '<span class="label label-publication-status" ng-class="statusClass">{{statusName}}</span>',
            scope: {
                status:  "="
            },
            link: function(scope) {

                /** Updates the status label **/
                function updateStatusLabel() {
                    scope.statusClass = 'publication-status-' + scope.status;
                    scope.statusName = scope.status.toLowerCase();
                }

                scope.$watch('status', updateStatusLabel, true);

            }
        }
    }]);





