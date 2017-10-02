<#include "common.ftl"/>


<#-- ***************************************  -->
<#-- Generates the SafetyNET footer lines     -->
<#-- ***************************************  -->
<#macro safetynetFooter>
    <#if message.publishDateTo?has_content>
        <@line format="navtex">
            <#assign cancelDate><@navtexDateFormat date=message.publishDateTo /></#assign>
            ${text('cancellation.this_message',cancelDate)}.
        </@line>
    </#if>
</#macro>


<#if promulgation.text?has_content>
    <#setting locale='en'>
    <field-template field="promulgation.text">

${promulgation.text}

<@safetynetFooter/>
    </field-template>
</#if>
