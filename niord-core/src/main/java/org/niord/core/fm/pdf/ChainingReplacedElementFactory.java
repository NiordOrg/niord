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
package org.niord.core.fm.pdf;

import org.w3c.dom.Element;
import org.xhtmlrenderer.extend.ReplacedElement;
import org.xhtmlrenderer.extend.ReplacedElementFactory;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.simple.extend.FormSubmissionListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Used for producing SVG in Flying Saucer PDF reports.
 * Kindly borrowed from:
 * http://www.samuelrossille.com/posts/2013-08-13-render-html-with-svg-to-pdf-with-flying-saucer.html
 */
public class ChainingReplacedElementFactory implements ReplacedElementFactory {

    private List<ReplacedElementFactory> replacedElementFactories = new ArrayList<>();

    public void addReplacedElementFactory(ReplacedElementFactory replacedElementFactory) {
        replacedElementFactories.add(0, replacedElementFactory);
    }

    /** {@inheritDoc} **/
    @Override
    public ReplacedElement createReplacedElement(LayoutContext c, BlockBox box,
                                                 UserAgentCallback uac, int cssWidth, int cssHeight) {

        for (ReplacedElementFactory replacedElementFactory : replacedElementFactories) {
            ReplacedElement element = replacedElementFactory.createReplacedElement(c, box, uac, cssWidth, cssHeight);
            if(element != null) {
                return element;
            }
        }
        return null;
    }


    /** {@inheritDoc} **/
    @Override
    public void reset() {
        replacedElementFactories.forEach(ReplacedElementFactory::reset);
    }


    /** {@inheritDoc} **/
    @Override
    public void remove(Element e) {
        replacedElementFactories.forEach(ref -> ref.remove(e));
    }


    /** {@inheritDoc} **/
    @Override
    public void setFormSubmissionListener(FormSubmissionListener listener) {
        replacedElementFactories.forEach(ref -> ref.setFormSubmissionListener(listener));
    }
}
