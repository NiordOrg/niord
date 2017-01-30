/*
 * Copyright 2017 Danish Maritime Authority.
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility methods for compressing and de-compressing strings using GZIP
 */
public class GzipUtils {

    /**
     * Don't instantiate this class
     */
    private GzipUtils() {
    }


    /** GZIP compresses the data **/
    public static byte[] compressString(String data) throws IOException {
        if (data != null) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length())) {
                GZIPOutputStream gzip = new GZIPOutputStream(bos);
                gzip.write(data.getBytes("UTF-8"));
                gzip.close();
                return bos.toByteArray();
            }
        }
        return null;
    }


    /** GZIP compresses the data. Returns null in case of an error **/
    public static byte[] compressStringIgnoreError(String data) {
        try {
            return compressString(data);
        } catch (IOException e) {
            return null;
        }
    }


    /** GZIP de-compresses the data **/
    public static String decompressString(byte[] compressed) throws IOException {
        if (compressed != null) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
                 GZIPInputStream gis = new GZIPInputStream(bis);
                 BufferedReader br = new BufferedReader(new InputStreamReader(gis, "UTF-8"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        }
        return null;
    }


    /** GZIP de-compresses the data. Returns null in case of an error **/
    public static String decompressStringIgnoreError(byte[] compressed) {
        try {
            return decompressString(compressed);
        } catch (IOException e) {
            return null;
        }
    }

}
