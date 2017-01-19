<#include "message-support.ftl"/>

<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>

    <link rel="stylesheet" type="text/css" href="/css/templates/mail.css">
    <link rel="stylesheet" type="text/css" href="/css/templates/common.css">
</head>
<body>

<h3>${text("mail.dear.user", mailTo)}</h3>

<p style="margin-top:20px">
    ${mailMessage}
</p>

<p style="margin-top:20px; margin-bottom: 40px">
    ${text("mail.greetings.best.regards", mailSender)}
</p>

<hr>

<@renderMessageList messages=messages areaHeadings=false />

</body>
</html>
