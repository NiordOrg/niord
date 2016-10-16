
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
        $rootScope.siteLanguages = ["da","en"];
        $rootScope.numeralLanguages = {"da":"da-dk","en":"en"};
        $rootScope.wmsLayerEnabled = true;
        $rootScope.editorFieldsBase = {
            "type": true, "orig_info" : false, "id" : true, "title" : true, "references"  :true, "time" : true,
            "areas" : true, "categories" : true, "positions" : true, "charts" : true, "subject" : true,
            "description" : true, "attachments" : false, "note" : false, "publication" : false, "source" : false,
            "prohibition" : false, "signals" : false };

        $rootScope.domains = [{
                "domainId" : "niord-client-nw",
                "name" : "NW"
              }, {
                "domainId" : "niord-client-nm",
                "name" : "NM"
              }];

        /** SETTINGS END **/

        // Initialize the language support
        LangService.initLanguage();

        // Update the application domains
        DomainService.initDomain();

    }]);
