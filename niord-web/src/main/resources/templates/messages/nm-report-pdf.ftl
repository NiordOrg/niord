<#include "message-support.ftl"/>

<#--
    Split by ?filter rather than by appending to a list one message at a time.
    Appending builds a sequence out of a chain of concatenations, one link per
    message, and reading the n'th element walks the chain -- so printing the list
    afterwards costs the square of its length.
-->
<#assign nmMessages = messages?filter(msg -> msg.type != 'MISCELLANEOUS_NOTICE') />
<#assign miscMessages = messages?filter(msg -> msg.type == 'MISCELLANEOUS_NOTICE') />
<#assign minMessageNo = 9999999 />
<#assign maxMessageNo = -9999999 />
<#list messages as msg>
    <#if msg.number?? && msg.number lt minMessageNo >
        <#assign minMessageNo = msg.number />
    </#if>
    <#if msg.number?? && msg.number gt maxMessageNo >
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

<#-- Headers and footers -->
<@renderDefaultHeaderAndFooter headerText="${text('pdf.nm')} - ${text('pdf.week')} ${week!''}, ${year!''}"/>


<#-- First page -->
<h1>${text("pdf.nm")}</h1>

<div class="week-year-header">
    ${text("pdf.week")} ${week!""}, ${year!""}
</div>
<table class="first-page-info-line">
    <tr>
        <td width="25%" align="left">
            ${.now?string["dd. MMMM yyyy"]}
            <#if edition?? && edition != '1'> - Version ${edition}</#if>
        </td>
        <td width="25%" align="center">ISSN ${ISSN!""}</td>
        <#-- The volume is counted from ${yearNumber}, the year the numbering
             derived, and not from the clock: a December issue amended in January,
             and any re-render years later, would otherwise print the volume of the
             year it was printed in rather than of the year it is the issue for.
             The legacy print dialog renders this same template with no issue
             behind it, so where no year was derived the clock still answers. -->
        <td width="25%" align="center"><#if yearNumber??>${text('pdf.volume', (yearNumber - 1884))}<#else>${text('pdf.volume', (.now?string('yyyy')?number - 1884))}</#if></td>
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


<#-- Permanent and T&P messages -->
<#if nmMessages?has_content>
    <@renderMessageList messages=nmMessages areaHeadings=areaHeadings prefix="nm"/>
</#if>

<#-- Add page break -->
<#if nmMessages?has_content && miscMessages?has_content>
    <div class="page-break"></div>
</#if>

<#-- Misc messages -->
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
