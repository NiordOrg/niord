/**
 * Message editor directives.
 */
angular.module('niord.editor')

    /********************************
     * Renders the JSON diff structure
     ********************************/
    .directive('historyJsonDiff', [ '$document', function ($document) {
        'use strict';

        return {
            restrict: 'A',
            scope: {
                historyJsonDiff: "="
            },
            link: function(scope, element) {

                $document.on('click', function (e) {
                    e = e || window.event;
                    if (e.target.nodeName.toUpperCase() === "UL") {
                        if (e.target.getAttribute("closed") === "yes") {
                            e.target.setAttribute("closed", "no");
                        } else {
                            e.target.setAttribute("closed", "yes");
                        }
                    }
                });

                scope.$watchCollection(function () {
                        return scope.historyJsonDiff;
                    },
                    function (newValue) {
                        element.empty();

                        if (scope.historyJsonDiff.length > 0) {

                            try {
                                var hist1 = JSON.parse(scope.historyJsonDiff[0].snapshot);
                                var hist2 = (scope.historyJsonDiff.length > 1)
                                    ? JSON.parse(scope.historyJsonDiff[1].snapshot)
                                    : hist1;
                                jsond.compare(hist1, hist2, "Message", element[0]);
                            } catch (e) {
                                console.error("Error " + e);
                            }
                        }

                    });
            }
        };
    }]);



