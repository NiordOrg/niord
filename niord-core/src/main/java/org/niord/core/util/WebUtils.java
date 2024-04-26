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
package org.niord.core.util;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * Web-related utility functions
 */
@SuppressWarnings("unused")
public class WebUtils {

    private WebUtils() {
    }

    /**
     * Returns the base URL of the request
     * @param request the request
     * @return the base URL
     */
    public static String getWebAppUrl(HttpServletRequest request, String... appends) {
        StringBuilder result = new StringBuilder(String.format(
                "%s://%s%s%s",
                request.getScheme(),
                request.getServerName(),
                request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort(),
                request.getContextPath()));
        for (String a : appends) {
            result.append(a);
        }
        return result.toString();
    }

    /**
     * Returns the base URL of the request
     * @param request the request
     * @return the base URL
     */
    public static String getServletUrl(HttpServletRequest request, String... appends) {
        String[] args = (String[])ArrayUtils.addAll(new String[] { request.getServletPath() }, appends);
        return getWebAppUrl(request, args);
    }

    /**
     * Returns the cookie with the given name or null if not found
     * @param request the request
     * @param name the name
     * @return the cookie with the given name or null if not found
     */
    public static Cookie getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : request.getCookies()) {
                if (c.getName().equals(name)) {
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * Returns the value of the cookie with the given name or null if not found
     * @param request the request
     * @param name the name
     * @return the value of the cookie with the given name or null if not found
     */
    public static String getCookieValue(HttpServletRequest request, String name) {
        Cookie c = getCookie(request, name);
        return (c == null) ? null : c.getValue();
    }


    /**
     * Returns a single parameter value from the request parameter map
     * @param reqParams the request parameter map
     * @param name the name of the parameter
     * @return the value or null if not defined
     */
    public static String getParameterValues(Map<String, String[]> reqParams, String name) {
        if (reqParams != null && name != null) {
            String[] values = reqParams.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }
        return null;
    }


    /**
     * Parses the URL (or part of the URL) to extract a request parameter map.
     * @param url the URL to parse
     * @return the parsed request parameter map
     */
    public static Map<String, String[]> parseParameterMap(String url) {
        Map<String, List<String>> params = new HashMap<>();

        if (StringUtils.isNotBlank(url)) {
            int index = url.indexOf("?");
            if (index > -1) {
                url = url.substring(index + 1);
            }
        }
        if (StringUtils.isNotBlank(url)) {
            for (String kv : url.split("&")) {
                String key, value = "";
                int x = kv.indexOf("=");
                if (x == -1) {
                    key = urlDecode(kv);
                } else {
                    key = urlDecode(kv.substring(0, x));
                    value = urlDecode(kv.substring(x + 1));
                }
                params.computeIfAbsent(key, v -> new ArrayList<>())
                        .add(value);
            }
        }
        return params.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().toArray(new String[e.getValue().size()])));
    }


    /**
     * Reads the body of a posted request
     * @param request the request
     * @return the body
     */
    public static String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder result = new StringBuilder();

        String line;
        BufferedReader reader = request.getReader();
        while ((line = reader.readLine()) != null) {
            result.append(line).append("\n");
        }

        return result.toString();
    }


    /**
     * Add headers to the response to ensure no caching takes place
     * @param response the response
     * @return the response
     */
    public static HttpServletResponse nocache(HttpServletResponse response) {
        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Cache-Control","no-store");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);
        return response;
    }

    /**
     * Add headers to the response to ensure caching in the given duration
     * @param response the response
     * @param seconds the number of seconds to cache the response
     * @return the response
     */
    public static HttpServletResponse cache(HttpServletResponse response, int seconds) {
        long now = System.currentTimeMillis();
        response.addHeader("Cache-Control", "max-age=" + seconds);
        response.setDateHeader("Last-Modified", now);
        response.setDateHeader("Expires", now + seconds * 1000L);
        return response;
    }

    /**
     * Encode identically to the javascript encodeURIComponent() method
     * @param s the string to encode
     * @return the encoded string
     */
    public static String encodeURIComponent(String s) {
        String result;

        try {
            result = URLEncoder.encode(s, "UTF-8")
                    .replaceAll("\\+", "%20")
                    .replaceAll("\\%21", "!")
                    .replaceAll("\\%27", "'")
                    .replaceAll("\\%28", "(")
                    .replaceAll("\\%29", ")")
                    .replaceAll("\\%7E", "~");
        } catch (UnsupportedEncodingException e) {
            result = s;
        }

        return result;
    }

    /**
     * Encode identically to the javascript encodeURI() method
     * @param s the string to encode
     * @return the encoded string
     */
    public static String encodeURI(String s) {
        return encodeURIComponent(s)
                    .replaceAll("\\%3A", ":")
                    .replaceAll("\\%2F", "/")
                    .replaceAll("\\%3B", ";")
                    .replaceAll("\\%3F", "?");
    }


    /**
     * Wraps a call to URLDecoder.decode(value, "UTF-8") to prevent the exception signature
     * @param value the value to decode
     * @return the decoded value or the original value in case of an exception
     */
    public static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * A helper function that retrieves all the form parameters on the multi-part
     * request and returns them as a map of objects based on their number, i.e.
     * <ul>
     *     <li>For zero entries the value is null</li>
     *     <li>For one entries the value is a single string</li>
     *     <li>For multiple entries the value is list of strings</li>
     * </ul>
     *
     * @param input the multi-part form input request
     * @return the extracted parameters' map
     */
    public static Map<String, Object> getMultipartInputFormParams(MultipartFormDataInput input) throws IOException {
        // Initialise the lists of parameters
        final Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
        final Map<String, Object> formParams = new HashMap<>();

        // Now iterate for all input parts
        for(Map.Entry<String, List<InputPart>> paramEntry : uploadForm.entrySet()) {
            if(paramEntry.getValue().stream().allMatch(not(InputPart::isContentTypeFromMessage))) {
                switch(paramEntry.getValue().size()) {
                    case 0:
                        formParams.put(paramEntry.getKey(), null);
                        break;
                    case 1:
                        formParams.put(paramEntry.getKey(), paramEntry.getValue().get(0).getBodyAsString());
                        break;
                    default:
                        final List<String> paramEntryList = new ArrayList<>();
                        for(InputPart inputPart : paramEntry.getValue()) {
                            paramEntryList.add(inputPart.getBodyAsString());
                        }
                        formParams.put(paramEntry.getKey(), paramEntryList);
                        break;
                }
            }
        }

        // And return the populated parameters' map
        return formParams;
    }

    /**
     * A helper function that retrieves all the uploaded files from the multi-part
     * request and returns them as a map based on their provided file names.
     *
     * @param input the multi-part input request
     * @return the uploaded files map
     */
    public static Map<String, InputStream> getMultipartInputFiles(MultipartInput input) throws IOException {
        // Initialise the lists of parameters
        final List<InputPart> uploadParts = input.getParts();
        final Map<String, InputStream> inputFiles = new HashMap<>();

        // For all input parts
        for(InputPart inputPart : input.getParts()) {
            // If this looks like a file, i.e. has the same content type as the input
            if(inputPart.isContentTypeFromMessage()) {
                // Try to read the file and add it to the map
                inputFiles.put(
                        WebUtils.getFileName(inputPart.getHeaders()),
                        inputPart.getBody(InputStream.class, null)
                );
            }
        }

        // And return the populated parameters' map
        return inputFiles;
    }

    /**
     * Retrieves a file name from the request headers from the "filename" field
     * of the request headers.
     *
     * @param header the multivalued map of the request headers
     * @return the name of the file as specified in the filename header field
     */
    protected static String getFileName(MultivaluedMap<String, String> header) {
        // Initialise the content disposition array
        final String[] contentDisposition = header.getFirst("Content-Disposition").split(";");
        // Now iterate for all provided filenames
        for (String filename : contentDisposition) {
            if ((filename.trim().startsWith("filename"))) {
                String[] name = filename.split("=");
                String finalFileName = name[1].trim().replaceAll("\"", "");
                return finalFileName;
            }
        }
        return "unknown";
    }

}
