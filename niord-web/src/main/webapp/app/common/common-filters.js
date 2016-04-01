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
    }])


    /** Truncates the text to at most one line and the given  number of charts */
    .filter('truncate', function () {
        return function (text, chars) {
            var truncated = false;
            text = (text || "") + "";
            // only first line
            if (text.indexOf('\n') != -1) {
                text = text.substr(0, text.indexOf('\n'));
                truncated = true;
            }

            // Limit chars
            if (chars && text.length > chars) {
                text = text.substr(0, chars);
                truncated = true;
            }

            if (truncated) {
                text = text + "\u2026";
            }
            return text;
        };
    });



