<#include "common.ftl"/>

<@defaultSubjectFieldTemplates/>

<field-template field="part.getDesc('en').details" format="html">
    <#list params.positions as pos>
        <@renderAtonType atonParams=pos defaultName="A buoy" format="long" lang="en"/>
        has been established <@renderPositionList geomParam=pos lang="en"/>.<br>
    </#list>
</field-template>

<#if promulgate('navtex')>
    <field-template field="message.promulgation('navtex').text" update="append">
        <#list params.positions as pos>
            <@line format="navtex">
                <@renderAtonType atonParams=pos defaultName="A buoy" format="short" lang="en"/>
                ESTABLISHED <@renderPositionList geomParam=pos format="navtex" lang="en"/>.
            </@line>
        </#list>
    </field-template>
</#if>

<#if promulgate('safetynet')>
    <field-template field="message.promulgation('safetynet').text" update="append">
        <#list params.positions as pos>
            <@line format="navtex">
                <@renderAtonType atonParams=pos defaultName="A buoy" format="short" lang="en"/>
                ESTABLISHED <@renderPositionList geomParam=pos format="navtex" lang="en"/>.
            </@line>
        </#list>
    </field-template>
</#if>
