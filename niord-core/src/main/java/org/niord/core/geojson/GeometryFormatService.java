package org.niord.core.geojson;

import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.script.FmTemplateService;
import org.niord.core.message.Message;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.message.MessageVo;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Arrays;

/**
 * Uses the Freemarker template system to format GeoJSON geometries as HTML
 */
@Stateless
@SuppressWarnings("unused")
public class GeometryFormatService {

    @Inject
    FmTemplateService templateService;

    @Inject
    NiordApp app;

    /**
     * Formats the feature collection according to the given template
     *
     * @param geometry the feature collection to format
     * @param language the language of the returned data
     * @param template the template to use for formatting the geometry
     * @param format the position format, either "dec", "sec" or "navtex"
     * @return the formatted geometry as HTML
     */
    public String formatGeometryAsHtml(
            String language,
            String template,
            String format,
            FeatureCollectionVo geometry) throws Exception {

        // Sanity check
        if (geometry == null) {
            return "";
        }

        String lang = app.getLanguage(language);
        template = StringUtils.defaultIfBlank(template, "list");
        format = StringUtils.defaultIfBlank(format, "dec");

        Arrays.stream(geometry.getFeatures())
                .forEach(f -> f.getProperties().put("language", lang));

        String templatePath = String.format("/templates/geometry/%s.ftl", template);
        return templateService.newFmTemplateBuilder()
                .templatePath(templatePath)
                .data("geometry", geometry)
                .data("format", format)
                .language(lang)
                .process();
    }


    /**
     * Appends the geometry of a message to the message details
     * @param message the message to update
     * @return the updated message
     */
    public <M extends MessageVo> M appendGeometryToDetails(M message) {
        if (message != null && message.getParts() != null) {
            message.getParts().stream()
                    .filter(p -> p.getDescs() != null && p.getGeometry() != null)
                    .forEach(p -> p.getDescs().forEach(desc -> desc.setDetails(
                            appendGeometryToDetails(desc.getLang(), desc.getDetails(), p.getGeometry()))));
        }
        return message;
    }


    /**
     * Appends the geometry of a message to the message details
     * @param message the message to update
     * @return the updated message
     */
    public Message appendGeometryToDetails(Message message) {
        if (message != null) {
            message.getParts().stream()
                    .filter(p -> p.getDescs() != null && p.getGeometry() != null)
                    .forEach(p -> {
                        FeatureCollectionVo geometry = p.getGeometry().toGeoJson();
                        p.getDescs().forEach(desc -> desc.setDetails(
                                appendGeometryToDetails(desc.getLang(), desc.getDetails(), geometry)));
                    });
        }
        return message;
    }


    /** Appends the geometry to the details */
    public String appendGeometryToDetails(String language, String details, FeatureCollectionVo geometry) {
        try {
            String positions = formatGeometryAsHtml(language, "list", "dec", geometry);
            StringBuilder result = new StringBuilder();
            if (StringUtils.isNotBlank(details)) {
                result.append(details).append("<br>");
            }
            if (StringUtils.isNotBlank(positions)) {
                result.append(positions);
            }
            if (result.length() > 0) {
                return result.toString();
            }
        } catch (Exception ignored) {
            // In case of an error, we just append nothing
        }
        return details;
    }
}
