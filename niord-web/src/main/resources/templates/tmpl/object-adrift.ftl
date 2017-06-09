<#include "common.ftl"/>
<#include "markings.ftl"/>

<#macro renderObject defaultName format='long' lang='en'>
    <#if params.object?has_content>
        <#assign desc=descForLang(params.object, lang)!>
        <#if desc?? && format == 'long'>
        ${desc.longValue?cap_first}
        <#elseif desc??>
        ${desc.value?cap_first}
        <#else>
        ${defaultName?cap_first}
        </#if>
    <#else>
    ${defaultName?cap_first}
    </#if>
</#macro>


<@defaultSubjectFieldTemplates/>

<field-template field="part.getDesc('en').details" format="html">
    <@renderObject defaultName="an object" format="long" lang="en"/>
    has been reported adrift <@renderPositionList geomParam=part lang="en"/>
    <#if params.date??><@renderDate date=params.date lang="en" tz="UTC"/></#if>.
    <#if params.cancelDate??>
        <p>Cancel this warning <@renderDate date=params.cancelDate lang="en" tz="UTC"/>.</p>
    </#if>
</field-template>

<#if promulgate('navtex')>
    <field-template field="message.promulgation('navtex').text" update="append">
        <@line format="navtex">
            <@renderObject defaultName="an object" format="short" lang="en"/>
            ADRIFT <@renderPositionList geomParam=part format="navtex" lang="en"/>
            <#if params.date??><@renderDate date=params.date lang="da" format="navtex"/></#if>.
        </@line>
    </field-template>
</#if>
