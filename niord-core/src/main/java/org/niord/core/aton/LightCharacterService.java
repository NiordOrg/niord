/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.aton;

import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.script.FmTemplateService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;

/**
 * Interface for translating light characters to text.
 * <p>
 * Example of "verbose" translation:
 * <pre>
 *     "Gp.L.Fl(2+1)G. 5s" -> "composite groups of 2 + 1 long flashes in green, repeated every 5. seconds"
 * </pre>
 */
@Stateless
public class LightCharacterService {

    public static final String LIGHT_CHARACTER_TEMPLATE = "/templates/aton/light-character.ftl";

    @Inject
    Logger log;

    @Inject
    FmTemplateService templateService;

    @Inject
    NiordApp app;

    /**
     * Translates light characters to text
     *
     * @param language the language
     * @param lightCharacter the light characters
     * @return the formatted textual representation
     */
    public String formatLightCharacter(
            String language,
            String lightCharacter) throws Exception {

        // Sanity check
        if (StringUtils.isBlank(lightCharacter)) {
            return lightCharacter;
        }

        // Parse the light character model
        LightCharacterModel lightCharacterModel = LightCharacterParser.getInstance().parse(lightCharacter);

        String lang = app.getLanguage(language);

        String result = templateService.newFmTemplateBuilder()
                .templatePath(LIGHT_CHARACTER_TEMPLATE)
                .data("lightModel", lightCharacterModel)
                .language(lang)
                .process();

        result = trimResult(result);

        log.info(String.format("Translate light character \"%s\" -> %s -> \"%s\"", lightCharacter, language, result));

        return result;
    }


    /**
     * Trims the whitespace from the translated light character
     * @param text the text to trim
     * @return the trimmed text
     */
    public static String trimResult(String text) {
        if (StringUtils.isNotBlank(text)) {
            text = text
                    .replaceAll("\\s+"," ")
                    .replace(" ;",";")
                    .replace(" ,",",")
                    .replace("- ","-")
                    .trim();
        }
        return text;
    }

}
