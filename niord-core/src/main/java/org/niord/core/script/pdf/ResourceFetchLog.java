/*
 * Copyright 2026 Danish Emergency Management Agency.
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

package org.niord.core.script.pdf;

import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;
import org.xhtmlrenderer.resource.CSSResource;
import org.xhtmlrenderer.resource.ImageResource;
import org.xhtmlrenderer.resource.XMLResource;

/**
 * How much of a render was spent fetching what the document points at.
 *
 * A rendered document is not self-contained. Every stylesheet it links and every
 * image it shows is a URL the layout engine opens for itself, one at a time, on
 * the rendering thread, and for a message list most of those URLs are this
 * application's own REST endpoints -- one map thumbnail per message, each
 * answered with a redirect to the repository, so a thousand-notice document is
 * several thousand HTTP round trips the application makes to itself while a user
 * waits.
 *
 * That cost is invisible from outside because it is charged to the layout: a
 * slow render looks the same whether the engine was paginating or waiting on a
 * socket, and the two have nothing in common as problems. So the fetches are
 * counted and timed apart, and the caller prints both.
 *
 * Counting only. Nothing here changes which resources are fetched or what comes
 * back, so a document rendered with this installed is the document rendered
 * without it.
 */
public class ResourceFetchLog {

    private int count;
    private long millis;

    /** How many resources the document sent the engine off to fetch. */
    public int getCount() {
        return count;
    }

    /** How long those fetches took, in total. */
    public long getMillis() {
        return millis;
    }

    private void record(long startedAtNanos) {
        count++;
        millis += (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    /**
     * Puts a counting user agent in front of the renderer's own.
     *
     * A subclass rather than a wrapper, because the PDF renderer's agent is not
     * interchangeable with any other: it turns an image into the output device's
     * own image type at the document's resolution, and an agent that did not
     * would silently produce a document with differently scaled pictures.
     */
    static void install(ITextRenderer renderer, ResourceFetchLog log) {
        CountingUserAgent agent = new CountingUserAgent(renderer.getOutputDevice(), log);
        agent.setSharedContext(renderer.getSharedContext());
        renderer.getSharedContext().setUserAgentCallback(agent);
    }

    /** The renderer's own agent, with a stopwatch around each fetch. */
    private static class CountingUserAgent extends ITextUserAgent {

        private final ResourceFetchLog log;

        CountingUserAgent(org.xhtmlrenderer.pdf.ITextOutputDevice outputDevice, ResourceFetchLog log) {
            super(outputDevice);
            this.log = log;
        }

        @Override
        public ImageResource getImageResource(String uri) {
            long t0 = System.nanoTime();
            try {
                return super.getImageResource(uri);
            } finally {
                log.record(t0);
            }
        }

        @Override
        public CSSResource getCSSResource(String uri) {
            long t0 = System.nanoTime();
            try {
                return super.getCSSResource(uri);
            } finally {
                log.record(t0);
            }
        }

        @Override
        public XMLResource getXMLResource(String uri) {
            long t0 = System.nanoTime();
            try {
                return super.getXMLResource(uri);
            } finally {
                log.record(t0);
            }
        }

        @Override
        public byte[] getBinaryResource(String uri) {
            long t0 = System.nanoTime();
            try {
                return super.getBinaryResource(uri);
            } finally {
                log.record(t0);
            }
        }
    }
}
