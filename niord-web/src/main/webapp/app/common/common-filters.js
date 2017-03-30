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
 * Common filters.
 */
angular.module('niord.common')

    /** Capitalize the string **/
    .filter('capitalize', function() {
        return function(input) {
            return (!!input) ? input.charAt(0).toUpperCase() + input.substr(1).toLowerCase() : '';
        }
    })


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
            return input !== undefined ? numeral(input).format(format) : '';
        };
    }])


    /** Truncates the text to at most one line and the given  number of charts */
    .filter('truncate', function () {
        return function (text, chars) {
            var truncated = false;
            text = (text || "") + "";
            // only first line
            if (text.indexOf('\n') !== -1) {
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
    })


    .filter('toTrusted', ['$sce', function ($sce) {
        return function (value) {
            return $sce.trustAsHtml(value);
        };
    }])


    /** Improve HTML rendering of plain text by e.g. replacing new-lines + other characters **/
    .filter('plain2html', function () {
        return function (text) {
            return ((text || "") + "")  // make sure it's a string;
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/\t/g, "    ")
                .replace(/ /g, "&#8203;&nbsp;&#8203;")
                .replace(/\r\n|\r|\n/g, "<br />");
        };
    });





