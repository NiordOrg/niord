
<#include "../messages/message-support.ftl"/>

<#if message??>
    <div>
        ${text('mail.safetynet.cancel')}
        <#if message.shortId?has_content>${message.shortId}</#if>
    </div>

    <#assign safetynet=promulgation(message, 'safetynet')!>
    <#if safetynet?? && safetynet.safetyNetId?has_content>
        <div style="margin-top: 20px">
            SafetyNET ID: ${safetynet.safetyNetId}
        </div>
    </#if>
</#if>
