<#include "message-support.ftl"/>

<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.list.title")}</title>

    <@pageSizeStyle />
    <link rel="stylesheet" type="text/css" href="/css/templates/pdf.css">
    <link rel="stylesheet" type="text/css" href="/css/templates/common.css">

    <style type="text/css" media="all">
        table.contact-info {
            margin-bottom: 1cm;
        }
        table.contact-info tr td {
            padding: 3mm;
        }
    </style>

</head>
<body>

<@renderDefaultHeaderAndFooter headerText="${text('pdf.nm_annex.title')} - ${text('pdf.nm_annex.subtitle')}"/>

<h1 style="margin-top: 5mm">${text("pdf.nm_annex.title")}</h1>

<table class="first-page-info-line" style="margin-top: 1cm">
    <tr>
        <td>${.now?string['dd. MMMM yyyy']}</td>
        <td align="right">${text("pdf.nm_annex.subtitle")}</td>
    </tr>
</table>

<div>
  <#include "nm-annex-report-pdf-intro.ftl">
</div>

<div>
    <h2>${text("pdf.toc")}</h2>
    <#list messages as msg>
        <#if msg.descs?has_content && msg.descs[0].title?has_content>
            <div class="toc"><a href='#msg_${msg.id}'>${msg.shortId} -  ${msg.descs[0].title}</a></div>
        </#if>
    </#list>
</div>


<div class="page-break">&nbsp;</div>

<@renderMessageList messages=messages areaHeadings=areaHeadings/>

</body>
</html>
