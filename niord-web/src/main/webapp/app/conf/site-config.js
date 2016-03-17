
/**
 * Common settings.
 * The settings are either defined as constants or set on the root scope
 */
angular.module('niord.conf')

    .run(['$rootScope', '$window', '$translate', 'LangService', 'DomainService',
        function ($rootScope, $window, $translate, LangService, DomainService) {

        $rootScope.adminIntegrationPageEnabled = false;

        // Map settings
        $rootScope.mapDefaultZoomLevel = 6;
        $rootScope.mapDefaultLatitude  = 56;
        $rootScope.mapDefaultLongitude = 11;

        // Layer settings
        $rootScope.wmsLayerEnabled = true;

        // Language settings
        $rootScope.modelLanguages = [ 'en' ];
        $rootScope.editorLanguages = $rootScope.modelLanguages;
        $rootScope.siteLanguages = [ 'en' ];
        // Define which numeral language to use. If language is not supported, create the definition, as per http://numeraljs.com
        $rootScope.numeralLauguages = { 'en': 'en' };
        LangService.initLanguage();

        // Update the application domains
        DomainService.initDomain();

    }]);
