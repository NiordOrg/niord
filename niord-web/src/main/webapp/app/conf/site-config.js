
/**
 * Common settings.
 * The settings are either defined as constants or set on the root scope
 */
angular.module('niord.conf')

    .run(['$rootScope', '$window', '$translate', 'LangService', 'DomainService',
        function ($rootScope, $window, $translate, LangService, DomainService) {

        /** SETTINGS START **/
                
        /**
         * Everything between the SETTINGS START and END comments is substituted 
         * with database settings by the SiteConfigServletFilter.
         * Example settings:
         */
        
        $rootScope.adminIntegrationPageEnabled = true;
        $rootScope.mapDefaultLatitude = 56;
        $rootScope.mapDefaultLongitude = 11;
        $rootScope.mapDefaultZoomLevel = 6;
        $rootScope.modelLanguages = ["da","en"];
        $rootScope.editorLanguages = ["da","en","gl"];
        $rootScope.siteLanguages = ["da","en"];
        $rootScope.numeralLauguages = {"da":"da-dk","en":"en"};
        $rootScope.wmsLayerEnabled = true;
        $rootScope.nwMrnPrefix = "urn:mrn:iho:nw:";
        $rootScope.nmMrnPrefix = "urn:mrn:iho:nm:";

        $rootScope.domains = [{
                "clientId" : "niord-client-nw",
                "name" : "NW"
              }, {
                "clientId" : "niord-client-nm",
                "name" : "NM"
              }];

        /** SETTINGS END **/

        // Initialize the language support
        LangService.initLanguage();

        // Update the application domains
        DomainService.initDomain();

    }]);
