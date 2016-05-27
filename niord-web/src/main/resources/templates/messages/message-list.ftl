<#include "message-support.ftl"/>

<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.list.title")}</title>

    <@messageStyles />

</head>
<body>

<@renderDefaultHeaderAndFooter headerText="Generated ${.now?datetime}"/>

<#assign areaHeadingId=-9999999 />

<h1>${text("pdf.list.title")}</h1>

<div style="margin: 1cm 0">
    <table border="0">
        <tr>
            <th align="right" valign="top" nowrap>Generated: &nbsp;</th>
            <td>${.now?datetime}</td>
        </tr>
        <tr>
            <th align="right" valign="top" nowrap>Criteria: &nbsp;</th>
            <td><small>${searchCriteria}</small></td>
        </tr>
        <tr>
            <th align="right" valign="top" nowrap>Result: &nbsp;</th>
            <td>${messages?size} messages</td>
        </tr>
    </table>
</div>

<@renderTOC areaHeadings=areaHeadings />

<table class="message-table">
    <#list messages as msg>

        <#if msg.areas?has_content>
            <#assign area=areaHeading(msg.areas[0]) />
            <#if areaHeadings && area?? && area.id != areaHeadingId>
                <#assign areaHeadingId=area.id />
                <tr>
                    <td colspan="2"><h4 style="color: #8f2f7b; font-size: 16px;" id="${areaHeadingId?c}"><@areaLineage area=areaHeading(area) /></h4></td>
                </tr>
            </#if>
        </#if>
        <tr>
            <td class="table-image">
                <img src="/rest/message-map-image/${msg.id?c}.png" width="120" height="120"/>
            </td>
            <td class="table-item">

                <@renderMessage msg=msg />

            </td>
        </tr>
    </#list>
</table>

</body>
</html>
