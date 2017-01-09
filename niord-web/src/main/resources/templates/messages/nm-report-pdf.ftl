<#include "message-support.ftl"/>

<#assign nmMessages = [] />
<#assign miscMessages = [] />
<#assign minMessageNo = 9999999 />
<#assign maxMessageNo = -9999999 />
<#list messages as msg>
    <#if msg.type != 'MISCELLANEOUS_NOTICE'>
        <#assign nmMessages = nmMessages + [msg] />
    <#elseif msg.type == 'MISCELLANEOUS_NOTICE'>
        <#assign miscMessages = miscMessages + [msg] />
    </#if>

    <#if msg.number?? && msg.number lt minMessageNo >
        <#assign minMessageNo = msg.number />
    <#elseif msg.number?? && msg.number gt maxMessageNo >
        <#assign maxMessageNo = msg.number />
    </#if>
</#list>


<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.list.title")}</title>

    <@pageSizeStyle />
    <link rel="stylesheet" type="text/css" href="/css/templates/pdf.css">
    <link rel="stylesheet" type="text/css" href="/css/templates/common.css">

    <style type="text/css" media="all">
        .nm-toc {
            margin-top: 1cm;
        }
        .toc {
            font-size: 12px;
        }
        .intro {
            margin-top: 2cm;
            font-size: 12px;
            page-break-after:always;
        }
        .intro table {
            margin: 0 auto;
            font-size: 12px;
            text-align: left;
        }
        .intro table th {
            width: 2cm;
            font-weight: normal;
        }
        .week-year-header {
            font-size: 24px;
            text-align: right;
            margin-top: 1cm;
            margin-bottom: 1mm;
        }
    </style>

</head>
<body>

<!-- Headers and footers -->
<@renderDefaultHeaderAndFooter headerText="${text('pdf.nm')} - ${text('pdf.week')} ${week!''}, ${year!''}"/>


<!-- First page -->
<h1>${text("pdf.nm")}</h1>

<div class="week-year-header">
    ${text("pdf.week")} ${week!""}, ${year!""}
</div>
<table class="first-page-info-line">
    <tr>
        <td width="25%" align="left">${.now?string["dd. MMMM yyyy"]}</td>
        <td width="25%" align="center">ISSN ${ISSN!""}</td>
        <td width="25%" align="center">${text('pdf.volume', volume!"133")}</td>
        <td width="25%" align="right">
            <#if minMessageNo != 9999999 && maxMessageNo != -9999999>
                ${minMessageNo?c} - ${maxMessageNo?c}
            </#if>
        </td>
    </tr>
</table>

<div class="nm-toc">
    <h2>${text("pdf.toc")}</h2>
    <ol class='toc'>

        <#assign tocAreaHeadingId=-9999999 />
        <#list nmMessages as msg>
            <#if msg.areas?has_content>
                <#assign area=areaHeading(msg.areas[0]) />
                <#if area?? && area.id != tocAreaHeadingId>
                    <#assign tocAreaHeadingId=area.id />
                    <li><a href='#nm${tocAreaHeadingId?c}'><@areaLineage area=areaHeading(area) /></a></li>
                </#if>
            <#elseif  tocAreaHeadingId == -9999999>
                <li><a href='#nm${tocAreaHeadingId?c}'>${text("msg.area.general")}</a></li>
                <#assign tocAreaHeadingId=-9999998 />
            </#if>
        </#list>

        <#if miscMessages?has_content>
            <li>
                <div class="toc"><a href='#misc'>${text('pdf.misc_nm')}</a></div>
            </li>
        </#if>
    </ol>
</div>


<div class="intro">
    <#include "nm-report-pdf-intro.ftl">
</div>


<!-- Permanent and T&P messages -->
<#if nmMessages?has_content>
    <@renderMessageList messages=nmMessages areaHeadings=areaHeadings prefix="nm"/>
</#if>

<!-- Add page break -->
<#if nmMessages?has_content && miscMessages?has_content>
    <div class="page-break"></div>
</#if>

<!-- Misc messages -->
<#if miscMessages?has_content>
    <table width="100%">
        <tr>
            <td class="table-header"><h4 id="misc">${text('pdf.misc_nm')}</h4></td>
        </tr>
    </table>
    <@renderMessageList messages=miscMessages areaHeadings=false/>
</#if>

</body>
</html>
