
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
 * Translations.
 * Specific site implementations should add their own translations
 */
angular.module('niord.conf')

    .config(['$translateProvider', function ($translateProvider) {

        $translateProvider.useSanitizeValueStrategy('sanitizeParameters');

        /** TRANSLATIONS START **/

        /**
         * Everything between the TRANSLATIONS START and END comments is substituted
         * with database translations by the SiteTextsServletFilter.
         * Example translations:
         */

        $translateProvider.translations('en', {
            'LANG_EN' : 'English',

            'menu.brand': 'Niord',
            'menu.messages': 'Messages',
            'menu.atons': 'AtoNs',
            'menu.editor': 'Editor',
            'menu.admin': 'Admin',
            'menu.login': 'Login',
            'menu.logout': 'Logout',

            'footer.copyright': '&copy; 2016 EfficienSea 2',
            'footer.link': 'http://efficiensea2.org',
            'footer.disclaimer': 'Disclaimer',
            'footer.cookies': 'Cookies',
            'footer.accessibility': 'Accessibility',
            'footer.accessibility_link': 'https://www.was.digst.dk/nautiskinformation-soefartsstyrelsen-dk',

            'front_page.welcome': 'Welcome to the',
            'front_page.brand': 'Nautical Information Directory',

            'view_mode.grid': 'Grid',
            'view_mode.details': 'Details',
            'view_mode.table': 'Table',
            'view_mode.map': 'Map',
            'view_mode.selection': 'Selection',
            'menu.filter' : 'Filter',
            'menu.filter.domain' : 'Domain',
            'menu.filter.messageSeries' : 'Message Series',
            'menu.filter.text' : 'Text',
            'menu.filter.type' : 'Type',
            'menu.filter.status' : 'Status',
            'menu.filter.category' : 'Category',
            'menu.filter.tag' : 'Tag',
            'menu.filter.aton' : 'AtoN',
            'menu.filter.chart' : 'Chart',
            'menu.filter.area' : 'Area',
            'menu.filter.date' : 'Date',
            'menu.filter.save' : 'Save Filter...',
            'menu.filter.clear' : 'Clear Filter',
            'menu.action' : 'Action',
            'menu.action.print' : 'Print...',
            'menu.action.select.all' : 'Select all',
            'menu.action.select.none' : 'Clear selection',
            'menu.action.tags' : 'Tags...',

            'msg.field.references' : 'References',
            'msg.field.time' : 'Time',
            'msg.field.positions' : 'Location',
            'msg.field.details' : 'Details',
            'msg.field.attachments' : 'Attachments',
            'msg.field.note' : 'Note',
            'msg.field.charts' : 'Charts',
            'msg.field.publication' : 'Publication',
            'msg.reference.repetition' : '(repetition)',
            'msg.reference.cancelled' : '(cancelled)',
            'msg.reference.updated' : '(updated)',
            'msg.show_positions' : 'Show positions',
            'msg.hide_positions' : 'Hide positions',
            'msg.general_messages' : 'General Notifications'

        });

        $translateProvider.preferredLanguage('en');

        /** TRANSLATIONS END **/

    }]);

