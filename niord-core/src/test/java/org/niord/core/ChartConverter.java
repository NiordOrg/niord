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

package org.niord.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.niord.core.chart.vo.SystemChartVo;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.model.geojson.PolygonVo;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Dummy utility class used for converts chart from CSV to a json array of SystemChartVo.
 */
public class ChartConverter {

    public static void main(String[] args) throws IOException {

        String csvPath = "/Users/carolus/Downloads/charts.csv";
        String resultPath = "/Users/carolus/Downloads/charts.json";

        List<SystemChartVo> charts = new ArrayList<>();

        Files.readAllLines(Paths.get(csvPath), Charset.forName("UTF-8")).forEach(line -> {
            String[] fields = line.split(";");

            SystemChartVo chart = new SystemChartVo();
            chart.setChartNumber(fields[0].split(" ")[1].trim());
            if (StringUtils.isNotBlank(fields[1]) && StringUtils.isNumeric(fields[1])) {
                chart.setInternationalNumber(Integer.valueOf(fields[1]));
            }

            chart.setName(StringUtils.defaultIfBlank(fields[3], ""));

            if (StringUtils.isNotBlank(fields[4]) && StringUtils.isNumeric(fields[4])) {
                chart.setScale(Integer.valueOf(fields[4]));
            }

            if (!"Ukendt / Unknown".equals(fields[5])) {
                chart.setHorizontalDatum(StringUtils.defaultIfBlank(fields[5], ""));
            }

            Double south = parsePos(fields[6]);
            Double west = -parsePos(fields[7]);
            Double north = parsePos(fields[8]);
            Double east = -parsePos(fields[9]);

            double[][] coords = new double[][] {
                    { east, north },
                    { east, south },
                    { west, south },
                    { west, north },
                    { east, north },
            };
            PolygonVo geometry = new PolygonVo();
            geometry.setCoordinates(new double[][][] { coords });
            GeoJsonUtils.roundCoordinates(geometry, 8);
            chart.setGeometry(geometry);

            charts.add(chart);
        });

        ObjectMapper mapper = new ObjectMapper();

        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(charts));

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(resultPath), StandardCharsets.UTF_8)) {
            //writer.write("\uFEFF");
            writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(charts));
        }
    }


    private static Double parsePos(String pos) {
        String[] parts = pos.split("-");
        Double degrees = Double.valueOf(parts[0]);
        Double mins = Double.valueOf(parts[1].replace(",", ".").substring(0, parts[1].length() - 1)) / 60.0;

        return degrees + mins;
    }

}
