<#include "message-support.ftl"/>

<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.list.title")}</title>

    <@pageSizeStyle />
    <link rel="stylesheet" type="text/css" href="/css/templates/pdf.css">
    <link rel="stylesheet" type="text/css" href="/css/templates/common.css">

</head>
<body>

<@renderDefaultHeaderAndFooter headerText="Generated ${.now?datetime}"/>

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

<@renderMessageList messages=messages />

</body>
</html>
