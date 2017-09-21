<#include "message-support.ftl"/>

<#assign tpMessages = [] />
<#list messages as msg>
    <#if msg.type == 'PRELIMINARY_NOTICE' || msg.type == 'TEMPORARY_NOTICE'>
        <#assign tpMessages = tpMessages + [msg] />
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
        td.table-item {
            vertical-align: top;
            width: 100%;
            padding: 5px 0;
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

<@renderDefaultHeaderAndFooter headerText="${text('pdf.nm_tp')} - ${text('pdf.week')} ${week!''}, ${year!''}"/>

<#-- First page -->
<h1>${text("pdf.nm_tp")}</h1>

<div class="week-year-header">
    ${text("pdf.week")} ${week!""}, ${year!""}
</div>

<table class="first-page-info-line" style="margin-bottom: 1cm">
    <tr>
        <td width="50%" align="left">${.now?string["dd. MMMM yyyy"]}</td>
        <td width="50%" align="right">
            ${text("pdf.nm_abbrev")}  ${week!""} ${year!""}
        </td>
    </tr>
</table>


<#-- T&P messages -->
<#if tpMessages?has_content>

    <#assign areaHeadingId=-9999999 />

     <table class="message-table">

         <#-- Define column widths (fixed table layout) -->
         <tr>
             <td style="width: 2.5cm"></td>
             <td style="width: 0.5cm"></td>
             <td width="*"></td>
         </tr>

        <#list tpMessages as msg>

            <#if msg.areas?has_content>
                <#assign area=areaHeading(msg.areas[0]) />
                <#if areaHeadings && area?? && area.id != areaHeadingId>
                    <#assign areaHeadingId=area.id />
                    <tr class="table-header-row">
                        <td colspan="3" class="table-item">
                            <h4 id="${areaHeadingId?c}"><@areaLineage area=areaHeading(area) /></h4>
                        </td>
                    </tr>
                </#if>
            <#elseif  areaHeadings && areaHeadingId == -9999999>
                <tr class="table-header-row">
                    <td colspan="3" class="table-header"><h4 id="${areaHeadingId?c}">${text("msg.area.general")}</h4></td>
                </tr>
                <#assign areaHeadingId=-9999998 />
            </#if>
            <tr>
                <td class="table-item">
                    <#if msg.shortId?has_content>
                        ${msg.shortId}
                    </#if>
                </td>
                <td class="table-item">
                    <#if msg.type == 'PRELIMINARY_NOTICE'>
                        (P)
                    <#elseif msg.type == 'TEMPORARY_NOTICE'>
                        (T)
                    </#if>
                </td>
                <td class="table-item">
                    <#if msg.descs?has_content && msg.descs[0].title?has_content>
                        ${msg.descs[0].title}
                    </#if>
                </td>
            </tr>
        </#list>
    </table>

</#if>


</body>
</html>
