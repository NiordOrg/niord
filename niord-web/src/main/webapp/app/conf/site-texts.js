
/**
 * Translations.
 * Specific site implementations should add their own translations
 */
angular.module('niord.conf')

    .config(['$translateProvider', function ($translateProvider) {

        $translateProvider.useSanitizeValueStrategy('sanitize');

        $translateProvider.translations('en', {
            'LANG_EN' : 'English',

            'MENU_BRAND': 'Niord',
            'MENU_MESSAGES': 'Messages',
            'MENU_ATONS': 'AtoNs',
            'MENU_EDITOR': 'Editor',
            'MENU_ADMIN': 'Admin',
            'MENU_LOGIN': 'Login',
            'MENU_LOGOUT': 'Logout',

            'FOOTER_COPYRIGHT': '&copy; 2015 EfficienSea 2',
            'FOOTER_LINK': 'http://efficiensea2.org',
            'FOOTER_DISCLAIMER': 'Disclaimer',
            'FOOTER_COOKIES': 'Cookies',

            'FRONT_PAGE_WELCOME': 'Welcome to the',
            'FRONT_PAGE_BRAND': 'Nautical Information Directory',

            'VIEW_MODE_GRID': 'Grid',
            'VIEW_MODE_DETAILS': 'Details',
            'VIEW_MODE_TABLE': 'Table',
            'VIEW_MODE_MAP': 'Map',
            'VIEW_MODE_SELECTION': 'Selection',
            'MENU_FILTER' : 'Filter',
            'MENU_FILTER_TEXT' : 'Text',
            'MENU_FILTER_TYPE' : 'Type',
            'MENU_FILTER_STATUS' : 'Status',
            'MENU_FILTER_CATEGORY' : 'Category',
            'MENU_FILTER_TAG' : 'Tag',
            'MENU_FILTER_ATON' : 'AtoN',
            'MENU_FILTER_CHART' : 'Chart',
            'MENU_FILTER_AREA' : 'Area',
            'MENU_FILTER_DATE' : 'Date',
            'MENU_FILTER_SAVE' : 'Save Filter...',
            'MENU_FILTER_CLEAR' : 'Clear Filter',
            'MENU_ACTION' : 'Action',
            'MENU_ACTION_PDF' : 'Generate PDF',
            'MENU_ACTION_SELECT_ALL' : 'Select all',
            'MENU_ACTION_CLEAR_SELECTION' : 'Clear selection',

            'FIELD_REFERENCE' : 'Reference',
            'FIELD_TIME' : 'Time',
            'FIELD_LOCATION' : 'Location',
            'FIELD_DETAILS' : 'Details',
            'FIELD_ATTACHMENTS' : 'Attachments',
            'FIELD_NOTE' : 'Note',
            'FIELD_CHARTS' : 'Charts',
            'FIELD_PUBLICATION' : 'Publication',
            'REF_REPETITION' : '(repetition)',
            'REF_CANCELLED' : '(cancelled)',
            'REF_UPDATED' : '(updated)',
            'SHOW_POS' : 'Show positions',
            'HIDE_POS' : 'Hide positions',
            'GENERAL_MSGS' : 'General Notifications'

        });

        $translateProvider.preferredLanguage('en');

    }]);

