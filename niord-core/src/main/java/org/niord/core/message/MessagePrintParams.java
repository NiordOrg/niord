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

package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.niord.core.util.WebUtils;
import org.niord.model.search.PagedSearchParamsVo;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Defines the message print parameters
 */
@SuppressWarnings("unused")
public class MessagePrintParams extends PagedSearchParamsVo {

    // Print parameters
    String report;
    String pageSize = "A4";
    String pageOrientation = "portrait";
    Boolean mapThumbnails = Boolean.FALSE;
    String fileName;
    Boolean debug = Boolean.FALSE;
    Map<String, Object> params = new HashMap<>();


    /**
     * Returns a MessagePrintParams initialized with parameter values from a request using "default" parameter names
     * @param req the servlet request
     * @return the MessageSearchParams initialized with parameter values
     */
    public static MessagePrintParams instantiate(HttpServletRequest req) {
        MessagePrintParams params = new MessagePrintParams();
        params.report(req.getParameter("report"))
                .pageSize(checkNull(req.getParameter("pageSize"), "A4", Function.identity()))
                .pageOrientation(checkNull(req.getParameter("pageOrientation"), "portrait", Function.identity()))
                .mapThumbnails(checkNull(req.getParameter("mapThumbnails"), false, Boolean::valueOf))
                .fileName(checkNull(req.getParameter("fileName"), null, Function.identity()))
                .debug(checkNull(req.getParameter("debug"), false, Boolean::valueOf))
                .readReportParams(req);

        return params;
    }

    /**
     * Returns a valid PDF file name "Content-Disposition" header
     * @param defaultName the default name to use if no file name has been set
     * @return a valid PDF file name "Content-Disposition" header
     */
    public String getFileNameHeader(String defaultName) {
        String name = StringUtils.defaultIfBlank(fileName, defaultName);
        if (!name.toLowerCase().endsWith(".pdf")) {
            name += ".pdf";
        }

        return "attachment; filename=\"" + WebUtils.encodeURIComponent(name) + "\"";
    }

    /**
     * Returns a string representation of the print params
     * @return a string representation of the print params
     */
    @Override
    public String toString() {
        List<String> desc = new ArrayList<>();
        if (StringUtils.isNotBlank(report)) { desc.add(String.format("Report: %s", report)); }
        if (StringUtils.isNotBlank(pageSize)) { desc.add(String.format("Page size: %s", pageSize)); }
        if (StringUtils.isNotBlank(pageOrientation)) { desc.add(String.format("Page orientation: %s", pageOrientation)); }

        return desc.stream().collect(Collectors.joining(", "));
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /***/


    public String getReport() {
        return report;
    }

    public MessagePrintParams report(String report) {
        this.report = report;
        return this;
    }

    public String getPageSize() {
        return pageSize;
    }

    public MessagePrintParams pageSize(String pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public String getPageOrientation() {
        return pageOrientation;
    }

    public MessagePrintParams pageOrientation(String pageOrientation) {
        this.pageOrientation = pageOrientation;
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public MessagePrintParams fileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public Boolean getDebug() {
        return debug;
    }

    public MessagePrintParams debug(Boolean debug) {
        this.debug = debug;
        return this;
    }


    public Boolean getMapThumbnails() {
        return mapThumbnails;
    }

    public MessagePrintParams mapThumbnails(Boolean mapThumbnails) {
        this.mapThumbnails = mapThumbnails;
        return this;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    private MessagePrintParams readReportParams(HttpServletRequest request) {
        request.getParameterMap().entrySet().stream()
                .filter(e -> e.getKey().startsWith("param:"))
                .forEach(e -> {
                    String key = e.getKey().substring("param:".length());
                    if (e.getValue() != null && e.getValue().length == 1) {
                        params.put(key, e.getValue()[0]);
                    } else if (e.getValue().length > 0) {
                        params.put(key, e.getValue());
                    }
                });
        return this;
    }


}
