<#include "common.ftl"/>

<!-- ***************************************  -->
<!-- Generates the NAVTEX header line         -->
<!-- ***************************************  -->
<#macro navtexHeader lang="en">
    <#if message.areas?has_content>
        <@line format="navtex">
            <@renderAreaLineage area=message.areas[0] />
            <#assign msgDesc=descForLang(message, lang)!>
            <#if msgDesc?? && msgDesc.vicinity?has_content >
                <@trailingDot>${msgDesc.vicinity}</@trailingDot>
            </#if>
        </@line>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Generates the NAVTEX footer lines        -->
<!-- ***************************************  -->
<#macro navtexFooter>
    <#if message.references?has_content>
        <#list message.references as ref>
            <#if ref.type == 'CANCELLATION'>
                <@line format="navtex">
                ${text('cancellation.message',ref.messageId)}.
                </@line>
            </#if>
        </#list>
    </#if>

    <#if message.publishDateTo?has_content>
        <@line format="navtex">
            <#assign cancelDate><@navtexDateFormat date=message.publishDateTo /></#assign>
        ${text('cancellation.this_message',cancelDate)}.
        </@line>
    </#if>

    <#if promulgation.priority?has_content && promulgation.priority != 'NONE'>
        PRIORITY: ${promulgation.priority}
    </#if>
</#macro>


<#if promulgation.text?has_content>
    <#setting locale='en'>
    <field-template field="promulgation.text">
<@navtexHeader/>

${promulgation.text}

<@navtexFooter/>
    </field-template>
</#if>
