package org.niord.web.aton;

import org.niord.core.repo.RepositoryService;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.slf4j.Logger;

import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates and caches AtoN symbols
 */
@javax.ws.rs.Path("/aton-symbol")
@Startup
@Singleton
public class AtonSymbolRestService {

    static final String SYMBOL_REPO_FOLDER = "aton_symbols";

    @Inject
    Logger log;

    @Inject
    RepositoryService repositoryService;

    // TODO: From system property
    String baseUri = "http://localhost:8080";

    /**
    @Inject
    @TextResource("/aton-symbols.defs")
    String atonSymbols;

    Map<String, String> atonSymbolDocs = new ConcurrentHashMap<>();


    @PostConstruct
    void init() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource is = new InputSource();
            is.setCharacterStream(new StringReader(atonSymbols));
            Document doc = db.parse(is);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            NodeList symbols = doc.getElementsByTagName("symbol");
            for (int i = 0; i < symbols.getLength(); i++) {
                Element symbol = (Element) symbols.item(i);
                StreamResult result = new StreamResult(new StringWriter());
                DOMSource source = new DOMSource(symbol);
                transformer.transform(source, result);
                String svg = String.format("<svg\n" +
                        "   xmlns=\"http://www.w3.org/2000/svg\"\n" +
                        "   version=\"1.0\"\n" +
                        "   width=\"%s\"\n" +
                        "   height=\"%s\">\n" +
                        "   %s" +
                        "</svg>",
                        symbol.getAttribute("width"),
                        symbol.getAttribute("height"),
                        result.getWriter().toString());

                atonSymbolDocs.put(symbol.getAttribute("id"), svg);
            }

        } catch (Exception e) {
            log.error("Error parsing AtoN symbols", e);
            e.printStackTrace();
        }
    }
    **/

    /**
     * Streams the given tile
     */
    @GET
    @javax.ws.rs.Path("/{symbols}")
    public Response test(@PathParam("symbols") String symbols) {

        long t0 = System.currentTimeMillis();
        try {
            String[] svgSymbols = symbols.split("\\+");

            Path file = svgToPng(svgSymbols[0]);
            log.info(String.format("Generated AtoN PNG %s in %d ms", file.getFileName(), System.currentTimeMillis() - t0));

            return Response
                    .ok(file.toFile(), "image/png")
                    .build();


        } catch (Exception e) {
            log.error("Error generating AtoN symbol", e);
            throw new WebApplicationException(500);
        }
    }

    /** Looks up or creates a PNG for the given SVG name **/
    private Path svgToPng(String baseSvgName) throws Exception {

        Path pngFile = repositoryService.getRepoRoot()
                .resolve(SYMBOL_REPO_FOLDER)
                .resolve(baseSvgName + ".png");
        // Check if it already exists
        if (Files.exists(pngFile)) {
            return pngFile;
        }

        /*
        System.out.println("SVG: \n" + atonSymbolDocs.get(baseSvgName));
        TranscoderInput svgIn = new TranscoderInput(new StringReader(atonSymbolDocs.get(baseSvgName)));

        checkCreateParentDirs(pngFile);

        OutputStream pngOut = new FileOutputStream(pngFile.toFile());

        TranscoderOutput transcoder = new TranscoderOutput(pngOut);

        PNGTranscoder pngTranscoder = new PNGTranscoder();

        pngTranscoder.transcode(svgIn, transcoder);
        pngOut.flush();
        pngOut.close();
        */

        // Resolve the URL of the SVG file
        String svgUrl = String.format("%s/img/aton/svg/%s.svg", baseUri, baseSvgName);

        URLConnection con = new URL(svgUrl).openConnection();
        con.setConnectTimeout(3000);
        con.setReadTimeout(3000);

        try (InputStream in = con.getInputStream()) {
            TranscoderInput svgIn = new TranscoderInput(in);

            checkCreateParentDirs(pngFile);

            OutputStream pngOut = new FileOutputStream(pngFile.toFile());

            TranscoderOutput transcoder = new TranscoderOutput(pngOut);

            PNGTranscoder pngTranscoder = new PNGTranscoder();

            pngTranscoder.transcode(svgIn, transcoder);
            pngOut.flush();
            pngOut.close();
        }

        return pngFile;
    }

    /**
     * Ensures that parent directories are created
     * @param file the file whose parent directories will be created
     */
    private void checkCreateParentDirs(Path file) throws IOException {
        if (!Files.exists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }
    }

}

