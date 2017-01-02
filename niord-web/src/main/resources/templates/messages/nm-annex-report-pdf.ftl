<#include "message-support.ftl"/>

<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.list.title")}</title>

    <@pageSizeStyle frontPage=true/>
    <link rel="stylesheet" type="text/css" href="/css/templates/pdf.css">
    <link rel="stylesheet" type="text/css" href="/css/templates/common.css">

</head>
<body>


<#assign msg=messages[0] />

<!-- Construct the title -->
<#assign title="" />
<#if msg.descs?has_content && msg.descs[0].title?has_content>
    <#assign title=msg.descs[0].title />
</#if>
<#if msg.parts?has_content>
    <#list msg.parts as part>
        <#if part.type == 'DETAILS' && part.descs?has_content && part.descs[0].subject?has_content>
            <#assign title=part.descs[0].subject />
        </#if>
    </#list>
</#if>
<#assign title=title?remove_ending(".") />

<@renderDefaultHeaderAndFooter headerText="${text('pdf.nm_annex.nm_ref', .vars['NtM-ref']!'')}" frontPage=true/>

<h1 style="margin-top: 5mm">${title}</h1>

<table class="first-page-info-line" style="margin: 1cm 0">
    <tr>
        <td>
            ${.now?string['dd. MMMM yyyy']}
            <#if edition?? && edition != '1'> - Version ${edition}</#if>
        </td>
        <td align="right">
            ${text("pdf.nm_annex.nm_ref", .vars['NtM-ref']!"")}
        </td>
    </tr>
</table>

<@renderMessageList messages=messages areaHeadings=false/>

</body>
</html>
