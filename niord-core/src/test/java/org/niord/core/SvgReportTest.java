package org.niord.core;

import org.junit.Test;
import org.niord.core.fm.pdf.HtmlToPdfRenderer;

import java.io.File;

/**
 * Test creating PDF reports containing embedded SVG
 */
public class SvgReportTest {

    @Test
    public void testSvgReport() throws Exception {

        File file = File.createTempFile( "svg-test-", ".pdf");
        file.deleteOnExit();
        //File file = new File("/Users/Peder/Desktop/svg-test.pdf");

        HtmlToPdfRenderer.newBuilder()
                .html(getClass().getResourceAsStream("/svg.html"))
                .pdf(file)
                .build()
                .render();
        System.out.println("Created PDF file including SVG at " + file);
    }
}
