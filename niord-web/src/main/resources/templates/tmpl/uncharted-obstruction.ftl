<#include "common.ftl"/>
<#include "markings.ftl"/>

<#macro renderObstructionType defaultValue='obstruction' format='normal' lang='en' capFirst=false>
    <#assign obstructionType=(params.obstruction_type??)?then(getListValue(params.obstruction_type, defaultValue, format, lang), defaultValue) />
    <#if capFirst>
        <#assign obstructionType=obstructionType?cap_first/>
    </#if>
    ${obstructionType}
</#macro>

<#macro renderObstructionReport defaultValue='reported' lang='en'>
    <#if params.obstruction_report??>
        <@renderListValue value=params.obstruction_report defaultValue=defaultValue format="normal" lang=lang />
    <#else>
        ${defaultValue}
    </#if>
</#macro>


<field-template field="part.getDesc('en').subject" format="text">
    Uncharted <@renderObstructionType defaultValue="Obstruction" lang="en" capFirst=true/>
</field-template>

<field-template field="part.getDesc('en').details" format="html">
    An uncharted <@renderObstructionType defaultValue="Obstruction" lang="en"/>
    is <@renderObstructionReport defaultValue="reported" lang="en" />
    <@renderPositionList geomParam=part lang="en"/>.
    <#if params.obstruction_visible!false>
        <@renderObstructionType defaultValue="The obstruction" format="long" lang="en" capFirst=true/> is visible above the sea surface.
    <#else>
        The depth above <@renderObstructionType defaultValue="the obstruction" format="long" lang="en"/> is
        <#if params.obstruction_depth??>${params.obstruction_depth}m.<#else>unknown.</#if>
    </#if>
    <@renderObstructionType defaultValue="Obstruction" format="long" lang="en" capFirst=true/>
    is <@renderMarkings markings=params.markings! markingType=params.markingType! lang="en" format="details" unmarkedText="unmarked"/><br>
    Mariners are advised to keep well clear.
</field-template>

<#if promulgate('navtex')>
    <field-template field="message.promulgation('navtex').text" update="append">
        <@line format="navtex">
            UNCHARTED <@renderObstructionType defaultValue="OBSTRUCTION" lang="en"/>
            <@renderObstructionReport defaultValue="REPORTED" lang="en" />
            <@renderPositionList geomParam=part format="navtex" lang="en"/>.
        </@line>
        <@line format="navtex">
            <#if params.obstruction_visible!false>
                <@renderObstructionType defaultValue="OBSTRUCTION" lang="en" /> VISIBLE ABOVE SEA SURFACE.
            <#else>
                DEPTH ABOVE <@renderObstructionType defaultValue="OBSTRUCTION" lang="en"/>
                <#if params.obstruction_depth??>${params.obstruction_depth}M.<#else>UNKNOWN.</#if>
            </#if>
        </@line>
        <@line format="navtex">
            <@renderMarkings markings=params.markings! markingType=params.markingType! lang="en" format="navtex"  unmarkedText="UNMARKED"/>
        </@line>
        <@line format="navtex">
            MARINERS ADVISED TO KEEP CLEAR.
        </@line>
    </field-template>
</#if>
