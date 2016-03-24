/**
 * Common filters.
 */
angular.module('niord.common')

    /** Formats a data using moment() **/
    .filter('formatDate', [function () {
        return function(input, format) {
            format = format || 'lll';
            return input ? moment(input).format(format) : '';
        };
    }])


    /** Formats a number using numeral() **/
    .filter('numeral', [function () {
        return function(input, format) {
            format = format || '0,0';
            return input ? numeral(input).format(format) : '';
        };
    }]);


