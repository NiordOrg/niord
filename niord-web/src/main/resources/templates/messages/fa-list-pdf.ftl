<#include "message-support.ftl"/>

<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.fa")}</title>

    <@pageSizeStyle />
    <link rel="stylesheet" type="text/css" href="/css/templates/pdf.css">
    <link rel="stylesheet" type="text/css" href="/css/templates/common.css">

    <style type="text/css" media="all">
        h1 {
            margin-top: 2cm;
            text-align: center;
            font-size: 36px;
        }
        .intro {
            margin-top: 2cm;
            font-size: 12px;
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
    </style>

</head>
<body>

<@renderDefaultHeaderAndFooter headerText="Generated ${.now?datetime}"/>

<h1>${text("pdf.fa")}</h1>

<div class="intro">
  <#include "fa-list-pdf-intro.ftl">
</div>

<div class="page-break">&nbsp;</div>

<@renderMessageList messages=messages areaHeadings=areaHeadings/>

</body>
</html>
