
<#include "publish-navwarn-common.ftl"/>

<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/templates/mail.css">

    <style>
        .message-details p {
            padding: 0;
            margin: 0;
        }
    </style>
</head>
<body>

    <div>
        Navigational Warning <#if message.shortId?has_content>no. ${message.shortId}</#if>
    </div>
    <@renderNavWarnDetails msg=message lang=language></@renderNavWarnDetails>

</body>
</html>
