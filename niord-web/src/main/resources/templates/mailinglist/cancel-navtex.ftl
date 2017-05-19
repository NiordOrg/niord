
<#include "../messages/message-support.ftl"/>

<#if message??>
    ${text('mail.navtex.cancel')}
    <@renderNumberYearId message=message defaultValue=message.shortId!''></@renderNumberYearId>
</#if>
