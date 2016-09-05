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

import org.niord.core.area.Area;
import org.niord.core.message.vo.EditableMessageVo;
import org.niord.core.service.BaseService;
import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;
import org.niord.model.message.AreaType;
import org.niord.model.message.MainType;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper around editor fields settings.
 * <p>
 * Editor fields for a message are computed by overlaying the following sets of editor fields:
 * <ol>
 *     <li>Base editor fields: A default set of editor fields. Defined in settings.</li>
 *     <li>Main-type editor fields: NW or NM specific editor fields. Defined in settings.</li>
 *     <li>Area editor fields: Each message area (or parent areas) may define editor fields.</li>
 *     <li>Category editor fields: Each message category (or parent categories) may define editor fields.</li>
 *     <li>Message series editor fields: Each associated message series may define editor fields.</li>
 * </ol>
 *
 * TODO: Currently, the area, category and message series editor fields have not been implemented.
 * For now, hardcoded firing area editor fields are added.
 *
 */
@Stateless
public class EditorFieldsService extends BaseService {

    /** These settings are all defined in niord.json **/
    private static final Setting EDITOR_FIELDS_BASE = new Setting("editorFieldsBase");
    private static final Setting EDITOR_FIELDS_NW   = new Setting("editorFieldsNw");
    private static final Setting EDITOR_FIELDS_NM   = new Setting("editorFieldsNm");


    @Inject
    SettingsService settingsService;


    /**
     * Computes and updates the editor fields of the editable message value object
     * @param messageVo the editable message to update
     */
    public void computeEditorFields(EditableMessageVo messageVo) {
        Map<String, Boolean> result = computeEditorFields(new Message(messageVo));
        messageVo.setEditorFields(result);
    }


    /**
     * Computes the editor fields to display for a given message
     * @param message the message to compute the editor fields for
     */
    public Map<String, Boolean> computeEditorFields(Message message) {

        Map<String, Boolean> result = new HashMap<>();

        // Add base editor fields
        addEditorFields(result, (Map) settingsService.get(EDITOR_FIELDS_BASE));

        // Add main-type specific editor fields
        if (message.getMainType() == MainType.NW) {
            addEditorFields(result, (Map) settingsService.get(EDITOR_FIELDS_NW));
        } else if (message.getMainType() == MainType.NM) {
            addEditorFields(result, (Map) settingsService.get(EDITOR_FIELDS_NM));
        }

        // Add firing-area editor fields if any of the message areas (or parent areas) is a firing area
        message.setAreas(persistedList(Area.class, message.getAreas()));
        boolean isFiringArea = false;
        for (Area area : message.getAreas()) {
            for (Area a = area; a != null; a = a.getParent()) {
                isFiringArea |= a.getType() == AreaType.FIRING_AREA;
            }
        }
        if (isFiringArea) {
            addEditorFields(result, getFiringExerciseEditorFields());
        }

        return result;
    }


    /**
     * Returns the editor fields for firing areas/exercises
     * @return the editor fields for firing areas/exercises
     */
    private Map<String, Boolean> getFiringExerciseEditorFields() {
        Map<String, Boolean> result = new HashMap<>();
        result.put("prohibition", Boolean.TRUE);
        result.put("signals", Boolean.TRUE);
        return result;
    }


    /** Adds teh editor fields to the result, overwriting existing fields settings **/
    @SuppressWarnings("unchecked")
    private void addEditorFields(Map<String, Boolean> result, Map editorFields) {
        editorFields.keySet().forEach(k -> {
            Object v = editorFields.get(k);
            result.put(k.toString(), v.equals(Boolean.TRUE));
        });
    }

}
