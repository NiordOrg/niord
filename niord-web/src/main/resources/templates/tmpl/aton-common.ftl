<#include "common.ftl"/>

<#macro aton defaultSubjectFields=true enDefaultName="" enDetails="" enNavtex="">

    <#if defaultSubjectFields>
        <@defaultSubjectFieldTemplates/>
    </#if>

    <field-template field="part.getDesc('en').details" format="html">
        <#list params.positions![] as pos>
            <@renderAtonType atonParams=pos defaultName="${enDefaultName}" format="long" lang="en"/>
            <@renderPositionList geomParam=pos lang="en"/> ${enDetails}.<br>
        </#list>
    </field-template>

    <#if promulgate('navtex')>
        <field-template field="message.promulgation('navtex').text" update="append">
            <#list params.positions![] as pos>
                <@line format="navtex">
                    <@renderAtonType atonParams=pos defaultName="${enDefaultName}" format="short" lang="en"/>
                    <@renderPositionList geomParam=pos format="navtex" lang="en"/> ${enNavtex}.
                </@line>
            </#list>
        </field-template>
    </#if>

    <#if promulgate('safetynet')>
        <field-template field="message.promulgation('safetynet').text" update="append">
            <#list params.positions![] as pos>
                <@line format="navtex">
                    <@renderAtonType atonParams=pos defaultName="${enDefaultName}" format="short" lang="en"/>
                    <@renderPositionList geomParam=pos format="navtex" lang="en"/> ${enNavtex}.
                </@line>
            </#list>
        </field-template>
    </#if>

</#macro>
