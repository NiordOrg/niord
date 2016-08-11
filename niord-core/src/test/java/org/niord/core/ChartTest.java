/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
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
