
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
            'menu.action.pdf' : 'Generate PDF',
            'menu.action.select_all' : 'Select all',
            'menu.action.clear_selection' : 'Clear selection',
            'menu.action.tags' : 'Tags...',

            'msg.field.reference' : 'Reference',
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

