package org.niord.web.conf;

import org.niord.core.util.WebUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Implementations of this class should be used to load and modify textual web resources,
 * typically used for AngularJS JavaScript configuration files.
 */
public abstract class AbstractTextResourceServletFilter implements Filter {

    final int cacheTTL;

    /**
     * Constructor
     * @param cacheTTL the cache TTL in seconds
     */
    public AbstractTextResourceServletFilter(int cacheTTL) {
        this.cacheTTL = cacheTTL;
    }

    /** {@inheritDoc} */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
    }

    /**
     * Main filter method
     * @param req the request
     * @param res the response
     * @param chain the filter chain
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        PrintWriter out = res.getWriter();

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Wrap the response to collect the returned text
        CharResponseWrapper wrapper = new CharResponseWrapper(response, "UTF-8");

        // Load the designated resource
        chain.doFilter(request, wrapper);
        String txt = wrapper.toString();

        // Let implementing class manipulate the textual response
        txt = updateResponse(request, txt);

        // Write the result back to the response
        if (cacheTTL > 0) {
            WebUtils.cache(response, cacheTTL);
        } else {
            WebUtils.nocache(response);
        }

        response.setContentLength(txt.length());
        out.write(txt);
        out.flush();
        out.close();
    }

    /**
     * Implementing class must override to update the response
     */
    abstract String updateResponse(HttpServletRequest request, String response);
}

/** Wraps the response for post-processing */
class CharResponseWrapper extends HttpServletResponseWrapper {
    private CharArrayWriter output;
    private String encoding;

    /** Constructor */
    public CharResponseWrapper(HttpServletResponse response, String encoding) {
        super(response);
        this.encoding = encoding;
        output = new CharArrayWriter();
    }

    @Override
    public String getCharacterEncoding() {
        return encoding;
    }


    @Override
    public String toString() {
        return output.toString();
    }

    @Override
    public PrintWriter getWriter(){
        return new PrintWriter(output);
    }
}