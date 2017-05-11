
<#include "../messages/message-support.ftl"/>

<#if message??>
    CANCEL NAV WARN
    <@renderNumberYearId message=message defaultValue=message.shortId!''></@renderNumberYearId>
</#if>
