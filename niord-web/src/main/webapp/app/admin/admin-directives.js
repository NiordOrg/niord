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
    .directive('adminPage', ['$state', function ($state) {
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

                scope.getParentPage = function () {
                    return $state.href(scope.parentPage);
                }
            }
        };
    }])


    /*************************************
     * Renders the status of a publication
     *************************************/
    .directive('publicationStatusField', [ function () {
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
    }])

        
    /*************************************
     * Recipient selector panel for mailing lists
     *************************************/
    .directive('mailingListRecipientSelector', [function () {
        return {
            restrict: 'EA',
            templateUrl: '/app/admin/mailing-list-recipient-selector.html',
            replace: true,
            scope: {
                recipientData:   "="
            },
            link: function(scope) {


                /** Returns a filtered array of recipients */
                function filterRecipients(recipients, filter) {
                    if (filter === undefined || filter.length === 0) {
                        return recipients;
                    }
                    filter = filter.toLowerCase();
                    var result = [];
                    for (var x = 0; x < recipients.length; x++) {
                        var r = recipients[x];
                        if ((r.email && r.email.toLowerCase().indexOf(filter) !== -1) ||
                            (r.firstName && r.firstName.toLowerCase().indexOf(filter) !== -1) ||
                            (r.lastName && r.lastName.toLowerCase().indexOf(filter) !== -1)) {
                            result.push(r);
                        }
                    }
                    return result;
                }


                function sortRecipients(recipients) {
                    recipients.sort(function(a,b){
                        return a.email.toLowerCase().localeCompare(b.email.toLowerCase());
                    })
                }


                scope.filter = { selectedFilter: '', availableFilter: '' };
                scope.filteredData = {
                    selectedRecipients: [],
                    availableRecipients: []
                };


                scope.filterRecipients = function () {
                    scope.filteredData.selectedRecipients = filterRecipients(
                        scope.recipientData.selectedRecipients,
                        scope.filter.selectedFilter
                    );
                    scope.filteredData.availableRecipients = filterRecipients(
                        scope.recipientData.availableRecipients,
                        scope.filter.availableFilter
                    );
                };


                /** Called when the recipient data has been updated **/
                scope.recipientDataUpdated = function () {
                    sortRecipients(scope.recipientData.availableRecipients);
                    sortRecipients(scope.recipientData.selectedRecipients);
                    scope.filterRecipients();
                };


                /** Initialize the recipient filter **/
                scope.initFilter = function () {
                    scope.filter.selectedFilter = '';
                    scope.filter.availableFilter = '';
                };


                /** Selects the given recipient **/
                scope.addRecipient = function (recipient) {
                    scope.recipientData.selectedRecipients.push(recipient);
                    var index = scope.recipientData.availableRecipients.indexOf(recipient);
                    scope.recipientData.availableRecipients.splice(index, 1);
                    scope.recipientDataUpdated();
                };


                /** Un-selects the given recipient **/
                scope.removeRecipient = function (recipient) {
                    var index = scope.recipientData.selectedRecipients.indexOf(recipient);
                    scope.recipientData.selectedRecipients.splice(index, 1);
                    scope.recipientData.availableRecipients.push(recipient);
                    scope.recipientDataUpdated();
                };


                /** Selects all available recipients **/
                scope.addAllRecipients = function () {
                    var r = scope.recipientData;
                    r.selectedRecipients = Array.prototype.concat.apply(r.selectedRecipients, r.availableRecipients);
                    scope.recipientData.availableRecipients.length = 0;
                    scope.recipientDataUpdated();
                };


                /** Un-selects all recipients **/
                scope.removeAllRecipients = function () {
                    var r = scope.recipientData;
                    r.availableRecipients = Array.prototype.concat.apply(r.selectedRecipients, r.availableRecipients);
                    scope.recipientData.selectedRecipients.length = 0;
                    scope.recipientDataUpdated();
                };


                scope.$watch("filter", scope.filterRecipients, true);
                scope.$watch("recipientData", scope.recipientDataUpdated);

            }
        };
    }]);


