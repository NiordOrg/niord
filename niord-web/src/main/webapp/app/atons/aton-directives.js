/**
 * Assorted Aid-to-Navigation directives.
 */
angular.module('niord.atons')

    /**
     * Renders the AtoN details
     */
    .directive('atonListDetails', ['$rootScope', 'MapService', 'AtonService',
        function ($rootScope, MapService, AtonService) {
            return {
                restrict: 'E',
                replace: false,
                templateUrl: '/app/atons/aton-list-details.html',
                scope: {
                    aton: '=',
                    selection: '=',
                    zoomBtn: '@',
                    editable: '@'
                },
                link: function(scope) {

                    scope.showZoomBtn = scope.zoomBtn == 'true';
                    scope.showDndBtn = scope.editable == 'true';
                    scope.showEditBtn = scope.editable == 'true';

                    // Returns if the given AtoN is selected or not
                    scope.isSelected = function (aton) {
                        return scope.selection.get(aton.tags['seamark_x:aton_uid']) !== undefined;
                    };

                    // Toggle the selection state of the AtoN
                    scope.toggleSelectAton = function (aton) {
                        if (scope.isSelected(aton)) {
                            scope.unselectAton(aton);
                        } else {
                            scope.selectAton(aton);
                        }
                    };

                    // Selects the given AtoN
                    scope.selectAton = function (aton) {
                        if (!scope.isSelected(aton)) {
                            // NB: We add a copy that can be modified on the selection page
                            scope.selection.put(aton.tags['seamark_x:aton_uid'], {
                                aton: angular.copy(aton),
                                orig: aton
                            });
                        }
                    };

                    // De-selects the given AtoN
                    scope.unselectAton = function (aton) {
                        if (scope.isSelected(aton)) {
                            scope.selection.remove(aton.tags['seamark_x:aton_uid']);
                        }
                    };

                    // Zooms in on the map
                    scope.zoomToAton = function (aton) {
                        $rootScope.$broadcast('zoom-to-aton', aton);
                    };

                    // Edits the attributes of the AtoN
                    scope.editAton = function (aton) {
                        var atonCtx = scope.selection.get(scope.aton.tags['seamark_x:aton_uid']);
                        if (atonCtx != null) {
                            $rootScope.$broadcast('edit-aton-details', atonCtx);
                        }
                    };

                    // Returns if the given attribute is changed compared with the original
                    scope.changed = function (attr) {
                        var atonCtx = scope.selection.get(scope.aton.tags['seamark_x:aton_uid']);
                        if (attr) {
                            return atonCtx != null && !angular.equals(atonCtx.orig[attr], atonCtx.aton[attr]);
                        }
                        // If no attribute is specified, check if there are any changes
                        return atonCtx != null && !angular.equals(atonCtx.orig, atonCtx.aton);
                    };

                    // Returns if the icon has changed
                    scope.iconChanged = function () {
                        var atonCtx = scope.selection.get(scope.aton.tags['seamark_x:aton_uid']);
                        return atonCtx != null && atonCtx.orig.iconUrl != atonCtx.aton.iconUrl;
                    };

                    // Returns if the position has changed
                    scope.posChanged = function () {
                        var atonCtx = scope.selection.get(scope.aton.tags['seamark_x:aton_uid']);
                        return atonCtx != null && (
                            (atonCtx.orig.lat != scope.aton.lat) ||
                            (atonCtx.orig.lon != scope.aton.lon));
                    };

                }
            };
        }]);
