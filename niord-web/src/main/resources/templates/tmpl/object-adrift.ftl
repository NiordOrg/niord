<#include "common.ftl"/>
<#include "markings.ftl"/>

<#macro renderObject defaultName format='long' lang='en' capFirst=false>
    <#assign result=defaultName!''/>
    <#if params.object?has_content>
        <#assign desc=descForLang(params.object, lang)!>
        <#if desc?? && format == 'long'>
            <#assign result=desc.longValue/>
        <#elseif desc??>
            <#assign result=desc.value/>
        </#if>
    </#if>
    <#if capFirst>
        <#assign result=result?cap_first/>
    </#if>
    ${result}
</#macro>


<field-template field="part.getDesc('en').subject" format="text">
    <@renderObject defaultName="Object" format="short" lang="en" capFirst=true/> adrift
</field-template>

<field-template field="part.getDesc('en').details" format="html">
    <@renderObject defaultName="an object" format="long" lang="en" capFirst=true/>
    has been observed adrift <@renderPositionList geomParam=part lang="en"/>
    <#if params.date??><@renderDate date=params.date lang="en" tz="UTC"/></#if>.
    <#if params.cancelDate??>
        <p>Cancel this warning <@renderDate date=params.cancelDate lang="en" tz="UTC"/>.</p>
    </#if>
</field-template>

<#if promulgate('navtex')>
    <field-template field="message.promulgation('navtex').text" update="append">
        <@line format="navtex">
            <@renderObject defaultName="an object" format="short" lang="en" capFirst=true/>
            ADRIFT <@renderPositionList geomParam=part format="navtex" lang="en"/>
            <#if params.date??><@renderDate date=params.date lang="da" format="navtex"/></#if>.
        </@line>
    </field-template>
</#if>

<#if promulgate('safetynet')>
    <field-template field="message.promulgation('safetynet').text" update="append">
        <@line format="navtex">
            <@renderObject defaultName="an object" format="short" lang="en" capFirst=true/>
            ADRIFT <@renderPositionList geomParam=part format="navtex" lang="en"/>
            <#if params.date??><@renderDate date=params.date lang="da" format="navtex"/></#if>.
        </@line>
    </field-template>
</#if>
