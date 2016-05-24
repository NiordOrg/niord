package org.niord.web.conf;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.niord.core.NiordApp;
import org.niord.core.dictionary.DictionaryService;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Loads the site-text.js file and injects relevant translations into it.
 *
 * The translations consists of all the dictionary entries of the "web" dictionary.
 */
@WebFilter(urlPatterns={"/app/conf/site-texts.js"})
public class SiteTextsServletFilter extends AbstractTextResourceServletFilter {

    final static int CACHE_SECONDS = 0; // No caching

    final static String[] WEB_DICTIONARIES    = { "web", "message" };
    final static String TRANSLATIONS_START  = "/** TRANSLATIONS START **/";
    final static String TRANSLATIONS_END    = "/** TRANSLATIONS END **/";

    @Inject
    Logger log;

    @Inject
    NiordApp app;

    @Inject
    DictionaryService dictionaryService;


    /** Constructor */
    public SiteTextsServletFilter() {
        super(CACHE_SECONDS);
    }


    /**
     * Returns the web translations as a javascript snippet that sets the $translateProvider translations.
     */
    private String getWebTranslations() {

        StringBuilder str = new StringBuilder("\n");
        for (String lang : app.getLanguages()) {

            str.append("$translateProvider.translations('")
                    .append(lang)
                    .append("', {\n");

            // Construct a property file with all language-specific values from all included dictionaries
            Properties langDict = new Properties();
            for (String name : WEB_DICTIONARIES) {
                langDict.putAll(dictionaryService.getCachedDictionary(name).toProperties(lang));
            }

            // Generate the javascript key-values
            langDict.stringPropertyNames().stream()
                    .sorted()
                    .forEach(key -> {
                        String value = langDict.getProperty(key);
                        str.append(String.format("'%s'", key))
                                .append(" : ")
                                .append(encodeValue(value))
                                .append(",\n");
                    });

            str.append("});\n");
        }

        str.append("$translateProvider.preferredLanguage('")
                .append(app.getDefaultLanguage())
                .append("');\n");

        return str.toString();
    }


    /** Encodes the value as a javascript string **/
    private String encodeValue(String value) {
        if (StringUtils.isBlank(value)) {
            return "''";
        }

        // Emit the escaped translation as a javascript string
        return Arrays.stream(value.split("\n"))
                .map(v -> String.format("'%s'", StringEscapeUtils.escapeEcmaScript(v)))
                .collect(Collectors.joining(" +\n"));
   }


    /**
     * Updates the response with relevant translations
     */
    @Override
    String updateResponse(HttpServletRequest request, String response) {
        int startIndex = response.indexOf(TRANSLATIONS_START);
        int endIndex = response.indexOf(TRANSLATIONS_END);

        if (startIndex != -1 && endIndex != -1) {
            endIndex += TRANSLATIONS_END.length();
            return response.substring(0, startIndex)
                    + getWebTranslations()
                    + response.substring(endIndex);
        }

        return response;
    }
}

