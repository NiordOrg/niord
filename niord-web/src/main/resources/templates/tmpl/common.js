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

/**
 * Adjusts various message fields, such as the title field if auto-title is turned on
 */
function adjustMessage() {
    // Update auto-title fields, etc.
    messageService.adjustMessage(message, AdjustmentType.TITLE);

    // If there is only one DETAILS message part, hide the subject
    var detailParts = message.parts(MessagePartType.DETAILS);
    if (detailParts.length == 1) {
        detailParts[0].hideSubject = true;
    }
}


/**
 * Adjusts the areas of the message from the associated geometry
 */
function adjustAreas() {
    // If message areas are undefined, compute them from the message geometry.
    messageService.adjustMessage(message, AdjustmentType.AREAS);
}


/**
 * Updates the enabled-state of the NAVTEX promulgation transmitters
 */
function updateNavtexTransmitters() {
    var navtex = CdiUtils.getBean(org.niord.core.promulgation.NavtexPromulgationService.class);
    navtex.computeNavtexTransmitterStatuses(message);
}


/**
 * Updates the promulgate-state of the message promulgations
 */
function updatePromulgations() {
    if (message.promulgations) {
        for (var p = 0; p < message.promulgations.length; p++) {
            var promulgation = message.promulgations[p];
            promulgation.promulgate = promulgation.promulgationDataDefined();
        }
    }
}


/**
 * Resets all Geometry IDs. Can be used if, say, the parts of a message is
 * copied to a new message. Subsequently reset the IDs of the copied geometry
 */
function resetGeometryIds(msg) {
    if (msg && msg.parts) {
        for (var x = 0; x < msg.parts.length; x++) {
            var part = msg.parts[x];
            if (part.geometry) {
                part.geometry.id = null;
                if (part.geometry.features) {
                    for (var y = 0; y < part.geometry.features.length; y++) {
                        part.geometry.features[y].id = null;
                    }
                }
            }
        }
    }
    return msg;
}


/**
 * Returns the description entity from the list with the given language.
 * Returns null if not found;
 */
function descForLang(descs, lang) {
    if (descs !== undefined && lang !== undefined) {
        for (var x = 0; x < descs.length; x++) {
            if (descs[x].lang == lang) {
                return descs[x];
            }
        }
    }
    return null;
}


/**
 * Copies the specified field from one list of description entities
 * to another
 */
function copyDescField(fromDescs, toDescs, field) {
    // Copy "vicinity" back to the message
    if (fromDescs !== undefined && toDescs !== undefined && field !== undefined) {
        for (var x = 0; x < fromDescs.length; x++) {
            var fromDesc = fromDescs[x];
            var toDesc = descForLang(toDescs, fromDesc.lang);
            if (toDesc !== undefined) {
                toDesc[field] = fromDesc[field];
            }
        }
    }
}
