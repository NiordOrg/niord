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

import org.junit.Test;
import org.niord.core.chart.Chart;

import java.util.Arrays;
import java.util.regex.Matcher;

/**
 * Chart test
 */
public class ChartTest {

    String[] CHARTS = {
            "A",
            "120",
            "190 (INT 1301)"
    };

    @Test
    public void testChartNumberParsing() {
        Arrays.stream(CHARTS).forEach(c -> {
            Matcher m = Chart.CHART_FORMAT.matcher(c);
            System.out.println("===== " + c + " =====");
            if (m.find()) {
                try {
                    if (m.group("chartNumber") != null) System.out.println("\tchartNumber " + m.group("chartNumber"));
                    if (m.group("intNumber") != null) System.out.println("\tintNumber " + m.group("intNumber"));
                } catch (Exception e) {
                    System.out.println("\t-> " + e.getMessage());
                }
            }
        });

    }

}
