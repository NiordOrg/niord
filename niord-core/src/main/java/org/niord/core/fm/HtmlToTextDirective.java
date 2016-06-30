package org.niord.core.fm;

import freemarker.core.Environment;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.niord.core.util.TextUtils;

import java.io.IOException;
import java.util.Map;

/**
 * Freemarker directive. Converts HTML to plain text
 */
@SuppressWarnings("unused")
public class HtmlToTextDirective implements TemplateDirectiveModel {

    private static final String PARAM_HTML = "html";

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Environment env,
                        Map params,
                        TemplateModel[] loopVars,
                        TemplateDirectiveBody body)
            throws TemplateException, IOException {


        SimpleScalar htmlModel = (SimpleScalar)params.get(PARAM_HTML);
        if (htmlModel == null) {
            throw new TemplateModelException("The 'html' parameter must be specified");
        }

        try {
            String html = htmlModel.getAsString();

            if (html != null) {
                env.getOut().write(TextUtils.html2txt(html));
            }
        } catch (Exception e) {
            // Prefer robustness over correctness
        }
    }

}
