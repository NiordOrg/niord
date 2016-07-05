
<#assign formatPos = "org.niord.core.fm.LatLonDirective"?new()>
<#assign formatDateInterval = "org.niord.core.fm.DateIntervalDirective"?new()>
<#assign txtToHtml = "org.niord.core.fm.TextToHtmlDirective"?new()>

<!-- ***************************************  -->
<!-- Commonly used message styles             -->
<!-- ***************************************  -->
<#macro messageStyles>
    <style type="text/css" media="all">

        /** General page rules **/
        @page {
            size: ${pageSize!"A4"} ${pageOrientation!"portrait"};
            margin: 2cm 1.5cm;
            padding: 0;

            @top-center {
                content: element(header);
                vertical-align: bottom;
            }

            @bottom-center {
                content: element(footer);
                vertical-align: top;
            }
        }

        /** First page rules **/
        @page :first {
            margin: 1.5cm;
            @top-center {
                content: element(first-page-header);
            }
            @bottom-center {
                content: element(first-page-footer);
            }
        }

        div.header {
            display: block;
            position: running(header);
            border-bottom: 0.05em solid gray;
            padding-bottom: 5px;
            color: darkgray;
        }

        div.footer {
            margin-top: 0.5cm;
            display: block;
            position: running(footer);
            padding-top: 5px;
            color: darkgray;
        }

        div.first-page-header {
            display: block;
            position: running(first-page-header);
        }

        div.first-page-footer {
            display: block;
            position: running(first-page-footer);
        }

        #pagenumber:before {
            content: counter(page);
        }

        #pagecount:before {
            content: counter(pages);
        }

        body{
            font-size:10px;
            font-family: Helvetica;
            margin: 0;
            padding:0;
        }

        a {
            color: #000000;
        }

        .page-break  {
            clear: left;
            display:block;
            page-break-after:always;
            margin: 0;
            padding: 0;
            height: 0;
        }

        ol.toc a::after {
            content: leader('.') target-counter(attr(href), page);
        }

        table.message-table tr, table.message-table tr {
            page-break-inside: avoid;
        }

        .table-image {
            vertical-align: top;
            padding: 10px;
            border-top: 1px solid lightgray;
        }

        .table-item {
            vertical-align: top;
            width: 100%;
            padding: 10px 10px 10px 40px;
            border-top: 1px solid lightgray;
        }

        .field-name {
            white-space: nowrap;
            vertical-align: top;
            font-style: italic;
            padding-right: 10px;
            text-align: right;
        }

        .field-value {
            vertical-align: top;
            width: 100%;
        }

        .field-value ol {
            padding-left: 0;
        }

        .feature-name {
            margin-top: 5px;
            margin-bottom: 5px;
            text-decoration: underline;
        }

        ol.feature-coordinates {
            margin-left: 20px;
            margin-bottom: 0;
            margin-top: 0;
        }

        ol.feature-coordinates li {
            color: darkgray;
        }
        ol.feature-coordinates li span {
            color: black;
        }

        .separate-attachment-page {
            clear: left;
            display:block;
            page-break-before:always;
            margin: 0;
            padding: 0;
            height: 0;
        }
    </style>
</#macro>


<!-- ***************************************  -->
<!-- Renders the default headers and footers  -->
<!-- ***************************************  -->
<#macro renderDefaultHeaderAndFooter headerText>
    <div class="first-page-header">
    </div>
    <div class="header">
        <span style="float: left">${headerText}</span>
        <span style="float: right">${text("pdf.page")} <span id="pagenumber"></span></span>
        &nbsp;
    </div>

    <div class="first-page-footer">
    </div>
    <div class="footer">
        <div style="text-align: center"><img src="/img/company-logo.png" style="height: 1cm"></div>
    </div>
</#macro>


<!-- ***************************************  -->
<!-- Renders the parent-first area lineage    -->
<!-- ***************************************  -->
<#macro areaLineage area>
    <#if area??>
        <#if area.parent??>
            <@areaLineage area=area.parent /> -
        </#if>
        <#if area.descs?has_content>${area.descs[0].name}</#if>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Returns the two root-most area headings  -->
<!-- ***************************************  -->
<#function areaHeading area="area" >
    <#if area?? && (!area.parent?? || (area.parent?? && !area.parent.parent??))>
        <#return area/>
    </#if>
    <#if area?? >
        <#return areaHeading(area.parent) />
    </#if>
    <#return NULL/>
</#function>


<!-- ***************************************  -->
<!-- Renders the TOC for area-headings        -->
<!-- ***************************************  -->
<#macro renderTOC areaHeadings>
    <#if areaHeadings>

        <#assign tocAreaHeadingId=-9999999 />

        <div>
            <h2>${text("pdf.toc")}</h2>
            <ol class='toc'>
                <#list messages as msg>
                    <#if msg.areas?has_content>
                        <#assign area=areaHeading(msg.areas[0]) />
                        <#if area?? && area.id != tocAreaHeadingId>
                            <#assign tocAreaHeadingId=area.id />
                            <li><a href='#${tocAreaHeadingId?c}'><@areaLineage area=areaHeading(area) /></a></li>
                        </#if>
                    </#if>
                </#list>
            </ol>
        </div>
        <div class="page-break">&nbsp;</div>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Renders a message                        -->
<!-- ***************************************  -->
<#macro renderMessage msg>
    <!-- Title line -->
    <#if msg.originalInformation?has_content && msg.originalInformation>
        <div>*</div>
    </#if>

    <#if msg.shortId?has_content>
        <div>
            <strong>${msg.shortId}.</strong>
        </div>
    </#if>

    <#if msg.descs?has_content && msg.descs[0].title?has_content>
        <div>
            <strong>
                <a href="${baseUri}/#/message/${msg.id}" target="_blank">
                    ${msg.descs[0].title}
                </a>
            </strong>
        </div>
    </#if>


    <table>

        <!-- Reference lines -->
        <#if msg.references?has_content>
            <tr>
                <td class="field-name">
                    ${text("msg.field.references")}
                </td>
                <td class="field-value">
                    <#list msg.references as ref>
                        <div>
                            <a href="${baseUri}/#/message/${ref.messageId}" target="_blank">${ref.messageId}</a>

                            <#if ref.type == 'REPETITION'>
                                ${text("msg.reference.repetition")}
                            <#elseif ref.type == 'CANCELLATION'>
                                ${text("msg.reference.cancelled")}
                            <#elseif ref.type == 'UPDATE'>
                                ${text("msg.reference.updated")}
                            </#if>

                            <#if ref.description?has_content>
                                ${ref.description}
                            </#if>
                        </div>
                    </#list>
                </td>
            </tr>
        </#if>


        <!-- Time line -->
        <tr>
            <td class="field-name">${text("msg.field.time")}</td>
            <td class="field-value">
                <#if msg.descs?has_content && msg.descs[0].time?has_content>
                    <div><@txtToHtml text=msg.descs[0].time /></div>
                <#elseif msg.dateIntervals?has_content>
                    <#list msg.dateIntervals as dateInterval>
                        <div>
                            <@formatDateInterval dateInterval=dateInterval />
                        </div>
                    </#list>
                <#else>
                    <@formatDateInterval />
                </#if>
            </td>
        </tr>


        <!-- Geometry line -->
        <#if msg.geometry?has_content>
            <tr>
                <td class="field-name">${text("msg.field.positions")}</td>
                <td class="field-value">
                    <#list msg.geometry as feature>
                        <#if feature.name?has_content>
                            <div class="feature-name">${feature.name}</div>
                        </#if>
                        <#if feature.coordinates?has_content>
                            <ol class="feature-coordinates" start="${feature.startIndex}">
                            <#list feature.coordinates as coord>
                                <li>
                                    <span>
                                        <@formatPos lat=coord.coordinates[1] lon=coord.coordinates[0] /><#if coord.name?has_content>,&nbsp;${coord.name}</#if>
                                    </span>
                                </li>
                            </#list>
                            </ol>
                        </#if>
                    </#list>
                </td>
            </tr>
        </#if>


        <!-- Details line -->
        <#if msg.descs?has_content && msg.descs[0].description?has_content>
            <tr>
                <td class="field-name">${text("msg.field.details")}</td>
                <td class="field-value">
                ${msg.descs[0].description}
                </td>
            </tr>
        </#if>

        <!-- Note line -->
        <#if msg.descs?has_content && msg.descs[0].note?has_content>
            <tr>
                <td class="field-name">${text("msg.field.note")}</td>
                <td class="field-value">
                ${msg.descs[0].note}
                </td>
            </tr>
        </#if>

        <!-- Charts line -->
        <#if msg.charts?has_content>
            <tr>
                <td class="field-name">${text("msg.field.charts")}</td>
                <td class="field-value">
                    <#list msg.charts as chart>
                    ${chart.chartNumber}
                        <#if chart.internationalNumber?has_content>(INT ${chart.internationalNumber?c})</#if>
                        <#if chart_has_next>, </#if>
                    </#list>
                </td>
            </tr>
        </#if>

        <!-- Publication line -->
        <#if msg.descs?has_content && msg.descs[0].publication?has_content>
            <tr>
                <td class="field-name">${text("msg.field.publication")}</td>
                <td class="field-value">
                ${msg.descs[0].publication}
                </td>
            </tr>
        </#if>

        <!-- Source line -->
        <#if msg.descs?has_content && msg.descs[0].source?has_content>
            <tr>
                <td class="field-name" colspan="2">
                    (${msg.descs[0].source})
                </td>
            </tr>
        </#if>

    </table>
</#macro>
