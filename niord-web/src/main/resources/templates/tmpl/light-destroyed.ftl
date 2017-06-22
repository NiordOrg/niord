<#include "common.ftl"/>
<#include "markings.ftl"/>

<#macro renderLightName lightParam defaultName="">
    <#if lightParam?? && lightParam.aton_name?has_content>
        ${lightParam.aton_name}
    <#else>
       ${defaultName}
    </#if>
</#macro>

<@defaultSubjectFieldTemplates/>

<field-template field="part.getDesc('en').details" format="html">
    <#list params.positions as pos>
        The light <@renderLightName lightParam=pos/> <@renderPositionList geomParam=part lang="en"/>
        has been destroyed and makes an obstruction.
    </#list>
    The light is <@renderMarkings markings=params.markings! markingType=params.markingType! lang="en" format="details" unmarkedText="unmarked"/><br>
    Mariners are advised to keep well clear.
</field-template>

<#if promulgate('navtex')>
    <field-template field="message.promulgation('navtex').text" update="append">
        <@line format="navtex">
            <#list params.positions as pos>
                LIGHT <@renderLightName lightParam=pos/> <@renderPositionList geomParam=part format="navtex" lang="en"/>
                DESTROYED.
            </#list>
            LIGHT <@renderMarkings markings=params.markings! markingType=params.markingType! lang="en" format="navtex"  unmarkedText="UNMARKED"/>
        </@line>
        <@line format="navtex">
            MARINERS ADVISED TO KEEP CLEAR.
        </@line>
    </field-template>
</#if>
