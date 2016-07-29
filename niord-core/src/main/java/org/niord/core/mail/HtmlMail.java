/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.mail;

import org.jsoup.Jsoup;
import org.jsoup.examples.HtmlToPlainText;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates a mail from an HTML source.
 * <p>
 * The HTML linked sources, such as stylesheets and images, will be inlined.
 * Links will be absolutized (converted from relative links to absolute links)
 * and the linked stylesheets will be converted to linked inline mail parts
 */
@SuppressWarnings("unused")
public class HtmlMail extends Mail {

    private int cidIndex = 0;
    private Map<String, InlineMailPart> inlinePartsLookup = new HashMap<>();

	/**
	 * Constructor
	 * @param doc the HTML document
     * @param includePlainText whether to include a plain text version or not
	 */
	private HtmlMail(Document doc, boolean includePlainText) throws IOException {
        processHtml(doc, includePlainText);
	}
	
	/**
	 * Returns a new HTML mail from a URL
	 * 
	 * @param url the URL of the HTML document
     * @param includePlainText whether to include a plain text version or not
	 * @return the HTML mail
	 */
	public static HtmlMail fromUrl(String url, boolean includePlainText) throws IOException {
		Document doc = Jsoup.connect(url).get();
		return new HtmlMail(doc, includePlainText);
	}
	
	/**
	 * Returns a new HTML mail from the HTML content
	 * 
	 * @param html the actual HTML
	 * @param baseUri the base URI of the document
     * @param includePlainText whether to include a plain text version or not
	 * @return the HTML mail
	 */
	public static HtmlMail fromHtml(String html, String baseUri, boolean includePlainText) throws IOException {
		Document doc = Jsoup.parse(html, baseUri);
		return new HtmlMail(doc, includePlainText);
	}
	
	/**
	 * Transforms the document to be "mailable" and build up a list of
	 * inline mail parts for images and stylesheets.
	 * 
	 * @param doc the DOM of the HTML document
	 */
	protected void processHtml(Document doc, boolean includePlainText) throws IOException {
		
		// Clean up a bit
		removeElements(doc, "script", "meta", "base", "iframe");

        // Inline stylesheets
        inlineStyleSheets(doc);

		// Inline images
		inlineImages(doc);
		
		// Convert links to be absolute
		absolutizeLinks(doc, "a", "href");
		absolutizeLinks(doc, "form", "action");
		
		// Get the result
		setHtmlText(doc.toString());
        setInlineParts(new ArrayList<>(inlinePartsLookup.values()));
        if (includePlainText) {
            setPlainText(new HtmlToPlainText().getPlainText(doc.body()));
        }
	}
	
	/**
	 * Removes the given html elements from the document
	 * 
	 * @param doc the HTML document
	 * @param elements the elements to remove
	 */
	protected void removeElements(Document doc, String... elements) {
        doc.select(Arrays.stream(elements).collect(Collectors.joining(", ")))
                .forEach(Node::remove);
	}

    /**
	 * Inlines the linked stylesheets, and remove all other link elements
	 * 
	 * @param doc the HTML document
	 */
	protected void inlineStyleSheets(Document doc) {
		Elements elms = doc.select("link");
		for (Element e : elms) {
			
			// Handle style sheets
			if (e.attr("type").equalsIgnoreCase("text/css") || e.attr("rel").equals("stylesheet")) {
				String url = e.absUrl("href");
				if (url.length() == 0) {
					e.remove();
					continue;
				}
				// Change the href to be the inline content id
				InlineMailPart mp = createInlineMailPart(url, "css");
				e.attr("href", "cid:" + mp.getContentId());
				
			} else  {
				// Remove all non-stylesheet links
				e.remove();
			}
		}
	}
	
	/**
	 * Inlines the images
	 * 
	 * @param doc the HTML document
	 */
	protected void inlineImages(Document doc) {
		Elements elms = doc.select("img[src]");
		for (Element e : elms) {
			
			String url = e.absUrl("src");
			if (url.length() == 0) {
				// Weird result...
				e.remove();
				continue;
			}
			// Change the src to be the inline content id
			InlineMailPart mp = createInlineMailPart(url, "img");
			e.attr("src", "cid:" + mp.getContentId());
		}
	}
	
	/**
	 * Make sure that links are absolute. 
	 * Used for a.href and form.action links
	 * 
	 * @param doc the HTML document
	 */
	protected void absolutizeLinks(Document doc, String tag, String attr) {
		Elements elms = doc.select(tag + "[" + attr + "]");
		for (Element e : elms) {
			
			String url = e.absUrl(attr);
			if (url.length() == 0) {
				// Disable link
				e.attr(attr, "#");
				continue;
			}
			// Update the link to be the absolute link
			e.attr(attr, url);
		}
	}
	
	/**
	 * Create or look up an existing inline mail part for the given url.
	 * Hmm, we make the assumption that e.g. a stylesheet and an image 
	 * cannot have the same url...
	 * 
	 * @param url the url of the resource
	 * @param name the cid name prefix
	 * @return the associated inline mail part
	 */
	protected InlineMailPart createInlineMailPart(String url, String name) {
		// Check if this is a known url
		InlineMailPart mp = inlinePartsLookup.get(url);
		if (mp == null) {
			String cid = name + (cidIndex++);
			mp = new InlineMailPart(cid, url);
            inlinePartsLookup.put(url, mp);
		}
		return mp;
	}
}
