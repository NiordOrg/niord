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

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                        e -> (String[])e.getValue().toArray(new String[e.getValue().size()])));
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
}
