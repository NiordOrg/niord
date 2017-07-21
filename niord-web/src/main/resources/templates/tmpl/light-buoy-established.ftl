<#include "common.ftl"/>

<@defaultSubjectFieldTemplates/>

<field-template field="part.getDesc('en').details" format="html">
    <#list params.positions as pos>
        <@renderAtonType atonParams=pos defaultName="A light buoy" format="long" lang="en"/>
        <#if pos.aton_light?has_content>
            showing
            <@lightCharacterFormat light=pos.aton_light format="verbose"/>
        </#if>
        has been established <@renderPositionList geomParam=pos lang="en"/>.<br>
    </#list>
</field-template>

<#if promulgate('navtex')>
    <field-template field="message.promulgation('navtex').text" update="append">
        <#list params.positions as pos>
            <@line format="navtex">
                <@renderAtonType atonParams=pos defaultName="A light buoy" format="short" lang="en"/>
                <#if pos.aton_light?has_content>
                    SHOWING
                    <@lightCharacterFormat light=pos.aton_light format="normal"/>
                </#if>
                ESTABLISHED <@renderPositionList geomParam=pos format="navtex" lang="en"/>.
            </@line>
        </#list>
    </field-template>
</#if>
