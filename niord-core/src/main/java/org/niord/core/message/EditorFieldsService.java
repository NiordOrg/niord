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

package org.niord.core.message;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang.StringUtils;
import org.niord.core.area.Area;
import org.niord.core.area.AreaType;
import org.niord.core.category.Category;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.service.BaseService;
import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;
import org.niord.core.util.JsonUtils;
import org.niord.model.message.MainType;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A wrapper around editor fields settings.
 * <p>
 * Editor fields for a message are computed by overlaying the following sets of editor fields:
 * <ol>
 *     <li>Base editor fields: A default set of editor fields. Defined in settings.</li>
 *     <li>Main-type editor fields: NW or NM specific editor fields. Defined in settings.</li>
 *     <li>Area editor fields: Each message area (or parent areas) may define editor fields.<br>
 *                     Additionally, all firing areas will be assigned the "prohibition" and "signals" fields.</li>
 *     <li>Category editor fields: Each message category (or parent categories) may define editor fields.</li>
 *     <li>Message series editor fields: Each associated message series may define editor fields.</li>
 *     <li>User attribute editor fields: NW or NM specific editor fields. Defined in user Keycloak "other claims" attributes.</li>
 * </ol>
 */
@Stateless
public class EditorFieldsService extends BaseService {

    /** These settings are all defined in niord.json **/
    private static final String KEY_EDITOR_FIELDS_BASE = "editorFieldsBase";
    private static final String KEY_EDITOR_FIELDS_NW   = "editorFieldsNw";
    private static final String KEY_EDITOR_FIELDS_NM   = "editorFieldsNm";

    private static final Setting EDITOR_FIELDS_BASE = new Setting(KEY_EDITOR_FIELDS_BASE);
    private static final Setting EDITOR_FIELDS_NW   = new Setting(KEY_EDITOR_FIELDS_NW);
    private static final Setting EDITOR_FIELDS_NM   = new Setting(KEY_EDITOR_FIELDS_NM);

    private final static String[] EDITOR_FIELDS_FIRING_EXERCISE = { "prohibition", "signals" };

    @Inject
    SettingsService settingsService;

    @Inject
    MessageSeriesService messageSeriesService;


    /**
     * Computes and updates the editor fields of the editable message value object
     * @param messageVo the message to update
     * @param userAttributes the Keycloak attributes associated with the current user
     */
    public void computeEditorFields(SystemMessageVo messageVo, Map<String, Object> userAttributes) {
        Map<String, Boolean> result = computeEditorFields(new Message(messageVo), userAttributes);
        messageVo.setEditorFields(result);
    }


    /**
     * Computes the editor fields to display for a given message
     * @param message the message to compute the editor fields for
     */
    public Map<String, Boolean> computeEditorFields(Message message, Map<String, Object> userAttributes) {

        Map<String, Boolean> result = new HashMap<>();

        // Add base editor fields
        addEditorFields(result, (Map) settingsService.get(EDITOR_FIELDS_BASE));

        // Add main-type specific editor fields
        if (message.getMainType() == MainType.NW) {
            addEditorFields(result, (Map) settingsService.get(EDITOR_FIELDS_NW));
        } else if (message.getMainType() == MainType.NM) {
            addEditorFields(result, (Map) settingsService.get(EDITOR_FIELDS_NM));
        }

        // Add fields relating to the associated areas and their parent areas (in root-most order)
        message.setAreas(persistedList(Area.class, message.getAreas()));
        message.getAreas().forEach(area -> {
            List<Area> lineage = area.lineageAsList();
            Collections.reverse(lineage); // Root-most order
            lineage.forEach(a -> {
                addEditorFields(result, a.getEditorFields());
                // A firing area will always be assigned the "prohibition" and "signals" fields
                if (a.getType() == AreaType.FIRING_AREA) {
                    addEditorFields(result, Arrays.asList(EDITOR_FIELDS_FIRING_EXERCISE));
                }
            });
        });

        // Add fields relating to the associated categories and their parent categories (in root-most order)
        message.setCategories(persistedList(Category.class, message.getCategories()));
        message.getCategories().forEach(cat -> {
            List<Category> lineage = cat.lineageAsList();
            Collections.reverse(lineage); // Root-most order
            lineage.forEach(c -> addEditorFields(result, c.getEditorFields()));
        });

        // Add fields relating to the associated message series
        if (message.getMessageSeries() != null) {
            message.setMessageSeries(messageSeriesService.findBySeriesId(message.getMessageSeries().getSeriesId()));
            addEditorFields(result, message.getMessageSeries().getEditorFields());
        }

        // Add editor fields from the user attributes (Keycloak "other claims")
        if (userAttributes != null) {
            if (message.getMainType() == MainType.NW) {
                addUserEditorFields(result, userAttributes.get(KEY_EDITOR_FIELDS_NW));
            } else if (message.getMainType() == MainType.NM) {
                addUserEditorFields(result, userAttributes.get(KEY_EDITOR_FIELDS_NM));
            }
        }

        return result;
    }


    /** Adds the editor fields to the result, overwriting existing fields settings **/
    private void addUserEditorFields(Map<String, Boolean> result, Object userFields) {

        // The user editor fields should be a string representation of a JSON object.
        // Example: '{ "publication": false }'

        if (userFields != null && userFields instanceof String && StringUtils.isNotBlank((String)userFields)) {
            try {
                Map<String, Boolean> userFieldMap = JsonUtils.fromJson(
                        (String)userFields,
                        new TypeReference<Map<String, Boolean>>(){});
                addEditorFields(result, userFieldMap);
            } catch (Exception ignored) {
            }
        }
    }


    /** Adds the editor fields to the result, overwriting existing fields settings **/
    @SuppressWarnings("unchecked")
    private void addEditorFields(Map<String, Boolean> result, Map editorFields) {
        editorFields.keySet().forEach(k -> {
            Object v = editorFields.get(k);
            result.put(k.toString(), v.equals(Boolean.TRUE));
        });
    }


    /** Adds the editor fields to the result, overwriting existing fields settings **/
    @SuppressWarnings("unchecked")
    private void addEditorFields(Map<String, Boolean> result, List<String> editorFields) {
        editorFields.forEach(k -> result.put(k, Boolean.TRUE));
    }
}
