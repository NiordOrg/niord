
<#include "../messages/message-support.ftl"/>

<#setting time_zone="GMT+0000">

<#macro renderAreas area lang=language>
    <#if area??>
        <#if area.parent??>
            <@renderAreas area=area.parent lang=lang/>
        </#if>
        <#assign areaDesc=descForLang(area, lang)!>
        <#if areaDesc?? && areaDesc.name?has_content>
            <@trailingDot>${areaDesc.name}</@trailingDot>
        </#if>
    </#if>
</#macro>

<#macro renderNavWarnDetails msg lang=language >

    <#assign msgDesc=descForLang(msg, lang)!>

    <#if msg.publishDateFrom??>
        <div>${msg.publishDateFrom?string['dd MM yyyy HH:mm']} UTC</div>
    </#if>

    <#if msg.areas?has_content>
        <div>
            <@renderAreas area=msg.areas[0] lang=lang></@renderAreas>
            <#if msgDesc?? && msgDesc.vicinity?has_content>
                <@trailingDot>${msgDesc.vicinity}</@trailingDot>
            </#if>
        </div>
    </#if>

    <#if msg.parts?has_content>
        <#list msg.parts as part>
            <#assign partDesc=descForLang(part, lang)!>
            <#if partDesc?? && partDesc.details?has_content>
                <div class="message-details">${partDesc.details}</div>
            </#if>
        </#list>
    </#if>

</#macro>
