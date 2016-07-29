<#include "message-support.ftl"/>

<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <@messageStyles />
</head>
<body>

<h1>${text("mail.dear.user", mailTo)}</h1>

<p style="margin-top:20px">
    ${mailMessage}
</p>

<p style="margin-top:20px; margin-bottom: 40px">
    ${text("mail.greetings.best.regards", mailSender)}
</p>

<#assign areaHeadingId=-9999999 />

<table class="message-table">
    <!-- Layout row for fixed-layout table -->
    <tr><td width="140"></td><td width="*"></td></tr>

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
                <img src="/rest/message-map-image/${msg.id}.png" width="120" height="120"/>
            </td>
            <td class="table-item">
                <@renderMessage msg=msg />
            </td>
        </tr>
    </#list>
</table>

</body>
</html>
