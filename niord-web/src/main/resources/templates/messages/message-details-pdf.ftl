
<#include "message-support.ftl"/>

<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.details.title")}</title>

    <@pageSizeStyle />
    <link rel="stylesheet" type="text/css" href="/css/templates/pdf.css">
    <link rel="stylesheet" type="text/css" href="/css/templates/common.css">
</head>
<body>



<h1>
  ${text("pdf.details.title")}
  <#if messages[0].shortId?has_content>
      <strong>${messages[0].shortId}</strong>
  </#if>
</h1>

<@renderMessageList messages=messages />

</body>
</html>
