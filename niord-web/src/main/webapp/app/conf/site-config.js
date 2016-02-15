
/**
 * Common settings.
 * The settings are either defined as constants or set on the root scope
 */
angular.module('niord.conf')

    .run(['$rootScope', '$window', '$translate', 'LangService', 'AppSpaceService',
        function ($rootScope, $window, $translate, LangService, AppSpaceService) {

        // Map settings
        $rootScope.mapDefaultZoomLevel = 6;
        $rootScope.mapDefaultLatitude  = 56;
        $rootScope.mapDefaultLongitude = 11;

        // Language settings
        $rootScope.modelLanguages = [ 'en' ];
        $rootScope.editorLanguages = $rootScope.modelLanguages;
        $rootScope.siteLanguages = [ 'en' ];
        // Define which numeral language to use. If language is not supported, create the definition, as per http://numeraljs.com
        $rootScope.numeralLauguages = { 'en': 'en' };
        LangService.initLanguage();

        // Update the application spaces
        $rootScope.spaces = [
            {
                id: "1",
                name: "All"
            },
            {
                id: "2",
                name: "NW"
            },
            {
                id: "3",
                name: "NM"
            }
        ];
        AppSpaceService.initAppSpace();

    }]);
