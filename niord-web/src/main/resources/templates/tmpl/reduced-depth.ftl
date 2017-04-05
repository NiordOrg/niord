<#include "common.ftl"/>

<@defaultSubjectFieldTemplates/>

<field-template field="part.getDesc('en').details" format="html">
    <#setting locale="en">
    Water depths down to ${params.water_depth!0?c} m
    have been observed
    <@renderPositionList geomParam=part lang="en"/>
    <#if params.locality?has_content>in the entrance to ${params.locality}</#if>.<br>
    Mariners are advised to keep well clear.
</field-template>

<field-template field="message.promulgation('navtex').text" update="append">
    <#setting locale="en">
    <@line format="navtex">
        Water depths down to ${params.water_depth!0?c} M
        have been observed
        <@renderPositionList geomParam=part format="navtex" lang="en"/>
        <#if params.locality?has_content>IN ENTRANCE TO ${params.locality}</#if>.
    </@line>
    <@line format="navtex">
        MARINERS ADVISED TO KEEP CLEAR
    </@line>
</field-template>
