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
package org.niord.core.publication;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.niord.core.message.vo.MessagePublicationVo;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.core.util.TextUtils;
import org.niord.model.message.MessageVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.publication.PublicationDescVo;

import java.util.Objects;

/**
 * Utility method for publications
 */
public class PublicationUtils {

    /**
     * Extracts the given message publication from the message
     *
     * @param message the message
     * @param publication the publication to extract
     * @param lang the language
     * @return the message publication or null if not found
     */
    public static MessagePublicationVo extractMessagePublication(MessageVo message, SystemPublicationVo publication, String lang) {
        // Sanity check
        if (message == null || publication == null || publication.getDesc(lang) == null || message.getDesc(lang) == null) {
            return null;
        }

        boolean internal = publication.getMessagePublication() == MessagePublication.INTERNAL;
        String pubHtml = internal ? message.getDesc(lang).getInternalPublication() : message.getDesc(lang).getPublication();
        if (StringUtils.isBlank(pubHtml)) {
            return null;
        }

        PublicationDescVo pubDesc = publication.getDesc(lang);

        Document doc = Jsoup.parseBodyFragment(pubHtml);

        String pubAttr = "[publication=" + publication.getPublicationId() + "]";
        Element e = doc.select("a" + pubAttr + ",span" + pubAttr).first();
        if (e != null) {
            MessagePublicationVo msgPub = new MessagePublicationVo();
            msgPub.setPublication(publication);
            String link = e.attr("href");
            if (StringUtils.isNotBlank(link) && !Objects.equals(link, pubDesc.getLink())) {
                msgPub.setLink(link);
            }
            String text = TextUtils.removeTrailingDot(e.html());

            // Internal publications have brackets around them
            if (internal && text.startsWith("[") && text.endsWith("]")) {
                text = text.substring(1, text.length() - 1);
            }

            String format = pubDesc.getFormat();
            if (StringUtils.isNotBlank(text) && StringUtils.isNotBlank(format) && format.contains("${parameters}")) {
                int index = format.indexOf("${parameters}");
                String prefix = format.substring(0, index);
                String suffix = format.substring(index + "${parameters}".length());
                if (text.startsWith(prefix) && text.endsWith(suffix)) {
                    String params = text.substring(prefix.length(), text.length() - suffix.length());
                    msgPub.setParameters(params);
                }
            }

            return msgPub;
        }
        return null;
    }

    /**
     * Updates the message publications from the publication, parameters and link
     *
     * @param message the message
     * @param publication the publication to extract
     * @param parameters the optional parameters
     * @param link the optional link
     * @param lang either a specific language or null for all languages
     * @return the message publication or null if not found
     */
    public static MessageVo updateMessagePublications(MessageVo message, SystemPublicationVo publication, String parameters, String link, String lang) {
        // Sanity check
        if (message == null || publication == null) {
            return null;
        }

        boolean internal = publication.getMessagePublication() == MessagePublication.INTERNAL;

        message.getDescs()
                .stream()
                .filter(msgDesc -> lang == null || lang.equals(msgDesc.getLang()))
                .forEach(msgDesc -> {

            PublicationDescVo pubDesc = publication.getDesc(msgDesc.getLang());

            String updatedPubHtml = computeMessagePublication(publication, parameters, link, pubDesc.getLang());

            String pubHtml = internal
                    ? msgDesc.getInternalPublication()
                    : msgDesc.getPublication();
            pubHtml = StringUtils.defaultIfBlank(pubHtml, "");

            Document doc = Jsoup.parseBodyFragment(pubHtml);
            String pubAttr = "[publication=" + publication.getPublicationId() + "]";
            Element e = doc.select("a" + pubAttr + ",span" + pubAttr).first();
            if (e != null) {
                // TODO: Is there a better way to replace an element?
                e.replaceWith(Jsoup.parse(updatedPubHtml).body().child(0));
                pubHtml = doc.body().html();
            } else {
                pubHtml += " " + updatedPubHtml;
            }
            // Lastly, clean up html for artifacts often added by TinyMCE
            if (StringUtils.isNotBlank(pubHtml)) {
                pubHtml = pubHtml.replace("<p>", "").replace("</p>", "").trim();
                if (internal) {
                    msgDesc.setInternalPublication(pubHtml);
                } else {
                    msgDesc.setPublication(pubHtml);
                }
            }
        });


        return message;
    }


    /**
     * Computes the message publication text
     * @param lang the language
     * @return the message publication text
     */
    public static String computeMessagePublication(SystemPublicationVo publication, String parameters, String link, String lang) {

        String result = null;
        PublicationDescVo desc = publication.getDesc(lang);
        if (desc != null && StringUtils.isNotBlank(desc.getFormat())) {
            String params = StringUtils.defaultIfBlank(parameters, "");
            result = desc.getFormat().replace("${parameters}", params);
            if (publication.getMessagePublication() == MessagePublication.INTERNAL) {
                result = "[" + result + "]";
            }
            result = TextUtils.trailingDot(result);

            String href = StringUtils.defaultIfBlank(link, desc.getLink());

            if (StringUtils.isNotBlank(href)) {
                result = String.format(
                        "<a publication=\"%s\" href=\"%s\" target=\"_blank\">%s</a>",
                        publication.getPublicationId(),
                        href,
                        result);
            } else {
                result = String.format(
                        "<span publication=\"%s\">%s</span>",
                        publication.getPublicationId(),
                        result);
            }
        }

        return result;
    }
}
