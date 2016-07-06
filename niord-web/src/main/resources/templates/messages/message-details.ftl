
<#include "message-support.ftl"/>

<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.details.title")}</title>

    <@messageStyles />
</head>
<body>



<h1>${text("pdf.details.title")}</h1>
<table class="message-table">
    <!-- Layout row for fixed-layout table -->
    <tr><td width="140"></td><td width="*"></td></tr>

    <tr>
        <td class="table-image">
            <img src="/rest/message-map-image/${msg.id}.png" width="120" height="120"/>
        </td>
        <td class="table-item">

            <@renderMessage msg=msg />

        </td>
    </tr>
</table>

<@renderSeparatePageAttachments msg=msg />

</body>
</html>
