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
import org.niord.core.publication.vo.PublicationDescVo;
import org.niord.core.publication.vo.PublicationVo;
import org.niord.core.util.TextUtils;
import org.niord.model.message.MessageDescVo;
import org.niord.model.message.MessageVo;

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
    public static MessagePublicationVo extractMessagePublication(MessageVo message, PublicationVo publication, String lang) {
        // Sanity check
        if (message == null || publication == null || publication.getDesc(lang) == null ||
                message.getDesc(lang) == null || StringUtils.isBlank(message.getDesc(lang).getPublication())) {
            return null;
        }

        String pubHtml = message.getDesc(lang).getPublication();
        PublicationDescVo pubDesc = publication.getDesc(lang);

        Document doc = Jsoup.parseBodyFragment(pubHtml);

        String pubAttr = "[publication=" + publication.getId() + "]";
        Element e = doc.select("a" + pubAttr + ",span" + pubAttr).first();
        if (e != null) {
            MessagePublicationVo msgPub = new MessagePublicationVo();
            msgPub.setPublication(publication);
            String link = e.attr("href");
            if (StringUtils.isNotBlank(link) && !Objects.equals(link, pubDesc.getLink())) {
                msgPub.setLink(link);
            }
            String text = TextUtils.removeTrailingDot(e.html());

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
     * @return the message publication or null if not found
     */
    public static MessageVo updateMessagePublications(MessageVo message, PublicationVo publication, String parameters, String link) {
        // Sanity check
        if (message == null || publication == null) {
            return null;
        }

        publication.getDescs().forEach(pubDesc -> {

            String updatedPubHtml = computeMessagePublication(publication, parameters, link, pubDesc.getLang());

            MessageDescVo msgDesc = message.checkCreateDesc(pubDesc.getLang());
            String pubHtml = msgDesc.getPublication();
            pubHtml = StringUtils.defaultIfBlank(pubHtml, "");

            Document doc = Jsoup.parseBodyFragment(pubHtml);
            String pubAttr = "[publication=" + publication.getId() + "]";
            Element e = doc.select("a" + pubAttr + ",span" + pubAttr).first();
            if (e != null) {
                // TODO: Is there a better way to replace an element?
                e.replaceWith(Jsoup.parse(updatedPubHtml).body().child(0));
                msgDesc.setPublication(doc.body().html());
            } else {
                pubHtml += " " + updatedPubHtml;
                msgDesc.setPublication(pubHtml.trim());
            }
            // Lastly, clean up html for artifacts often added by TinyMCE
            if (StringUtils.isNotBlank(msgDesc.getPublication())) {
                msgDesc.setPublication(msgDesc.getPublication().replace("<p>", "").replace("</p>", ""));
            }
        });


        return message;
    }


    /**
     * Computes the message publication text
     * @param lang the language
     * @return the message publication text
     */
    public static String computeMessagePublication(PublicationVo publication, String parameters, String link, String lang) {

        String result = null;
        PublicationDescVo desc = publication.getDesc(lang);
        if (desc != null && StringUtils.isNotBlank(desc.getFormat())) {
            String params = StringUtils.defaultIfBlank(parameters, "");
            result = desc.getFormat().replace("${parameters}", params);
            if (publication.isInternal()) {
                result = "[" + result + "]";
            }
            result = TextUtils.trailingDot(result);

            String href = StringUtils.defaultIfBlank(link, desc.getLink());

            if (StringUtils.isNotBlank(href)) {
                result = String.format(
                        "<a publication=\"%s\" href=\"%s\" target=\"_blank\">%s</a>",
                        publication.getId(),
                        href,
                        result);
            } else {
                result = String.format(
                        "<span publication=\"%s\">%s</span>",
                        publication.getId(),
                        result);
            }
        }

        return result;
    }


    /** Testing **/
    public static void main(String[] args) {
        MessageVo msg = new MessageVo();
        msg.checkCreateDesc("da").setPublication("<a publication=\"475\" href=\"http://www.danskehavnelods.dk\" target=\"_blank\">www.danskehavnelods.dk.</a> <a publication=\"499\" href=\"http://www.soefartsstyrelsen.dk/SikkerhedTilSoes/Sejladsinformation/EfS/ThisYear2/Skydebilag%202016%20DK.pdf\" target=\"_blank\">Oversigt over forsvarets skydepladser 2016, punkt 17, 18.</a>");
        msg.checkCreateDesc("en").setPublication("<a publication=\"475\" href=\"http://www.danskehavnelods.dk\" target=\"_blank\">www.danskehavnelods.dk.</a> <a publication=\"499\" href=\"http://www.soefartsstyrelsen.dk/SikkerhedTilSoes/Sejladsinformation/EfS/ThisYear2/Skydebilag%202016%20UK.pdf\" target=\"_blank\">Danish List of Firing Practice Areas, section 17, 18.</a>");

        PublicationVo pub = new PublicationVo();
        pub.setId(475);
        pub.checkCreateDesc("da").setLink("http://www.danskehavnelods.dk");
        pub.getDesc("da").setFormat("www.danskehavnelods.dk");
        pub.checkCreateDesc("en").setLink("http://www.danskehavnelods.dk");
        pub.getDesc("en").setFormat("www.danskehavnelods.dk");
        System.out.println("Msg Pub: " + extractMessagePublication(msg, pub, "da"));

        pub = new PublicationVo();
        pub.setId(499);
        pub.checkCreateDesc("da").setLink("http://www.soefartsstyrelsen.dk/SikkerhedTilSoes/Sejladsinformation/EfS/ThisYear2/Skydebilag%202016%20DK.pdf");
        pub.getDesc("da").setFormat("Oversigt over forsvarets skydepladser 2016, punkt ${parameters}");
        pub.checkCreateDesc("en").setLink("http://www.soefartsstyrelsen.dk/SikkerhedTilSoes/Sejladsinformation/EfS/ThisYear2/Skydebilag%202016%20UK.pdf");
        pub.getDesc("en").setFormat("Danish List of Firing Practice Areas, section ${parameters}");
        System.out.println("Msg Pub: " + extractMessagePublication(msg, pub, "da"));


        updateMessagePublications(msg, pub, "17, 18, 19", null);
        msg.getDescs().forEach(d -> System.out.println("MsgPub[" + d.getLang() + "]: " + d.getPublication()));

        pub = new PublicationVo();
        pub.setId(480);
        pub.checkCreateDesc("da").setFormat("J.nr. ${parameters}");
        pub.checkCreateDesc("en").setFormat("J.no ${parameters}");
        updateMessagePublications(msg, pub, "235434242", "http://www.google.dk");
        msg.getDescs().forEach(d -> System.out.println("MsgPub[" + d.getLang() + "]: " + d.getPublication()));
    }
}
