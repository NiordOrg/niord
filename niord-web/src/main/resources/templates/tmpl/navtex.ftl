<#include "common.ftl"/>

<#assign areaLineage = 'org.niord.core.script.directive.AreaLineageDirective'?new()>


<#-- ***************************************  -->
<#-- Generates the NAVTEX header line         -->
<#-- ***************************************  -->
<#macro navtexHeader lang="en">
    <@areaLineage message=message lang=lang format="navtex"/>
</#macro>


<#-- ***************************************  -->
<#-- Generates the NAVTEX footer lines        -->
<#-- ***************************************  -->
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
</#macro>


<#if promulgation.text?has_content>
    <#setting locale='en'>
    <field-template field="promulgation.text">
<@navtexHeader/>

${promulgation.text}

<@navtexFooter/>
    </field-template>
</#if>
