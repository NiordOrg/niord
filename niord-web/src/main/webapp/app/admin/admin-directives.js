/**
 * Common directives.
 */
angular.module('niord.common')


    /**
     * Defines a view mode filter panel
     */
    .directive('adminPage', [function () {
        return {
            restrict: 'EA',
            templateUrl: '/app/admin/admin-page.html',
            replace: true,
            transclude: true,
            scope: {
                adminPageTitle: "@"
            },
            link: function(scope) {
            }
        };
    }]);





