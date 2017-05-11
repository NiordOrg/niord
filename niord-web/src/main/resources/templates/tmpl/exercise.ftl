<#include "common.ftl"/>

<#macro renderSubject value lang='en'>
    <#if value?has_content>
        <#assign desc=descForLang(value, lang)!>
        <#if desc??>
            ${desc.value?cap_first}
        </#if>
    </#if>
</#macro>


<#if params.exercise_type??>
    <#list languages as lang>
        <field-template field="part.getDesc('${lang}').subject" format="text">
            <@renderSubject value=params.exercise_type lang=lang/>
        </field-template>
    </#list>
</#if>

<field-template field="part.getDesc('en').details" format="html">
    <@renderDateIntervals dateIntervals=part.eventDates lang="en" capFirst=true/>
    <#if params.exercise_type??>
        <@renderListValue value=params.exercise_type defaultValue="" lang="en"/>
    </#if>
    will take place in the area
    <@renderPositionList geomParam=part lang="en"/>.
    Guard vessels will be in the area listening on VHF CH 16.
    Mariners are requested to pass with caution.
</field-template>

<#if promulgate('navtex')>
    <field-template field="message.promulgation('navtex').text" update="append">
        <@line format="navtex">
            <@renderDateIntervals dateIntervals=part.eventDates lang="en" format="navtex"/>
            <#if params.exercise_type??>
                <@renderListValue value=params.exercise_type lang="en" defaultValue="" format="navtex"/>
            </#if>
            <@renderPositionList geomParam=part lang="en" format="navtex"/>.
            Guard vessels in area listening VHF CH 16.
            MARINERS REQUESTED TO PASS WITH CAUTION.
        </@line>
    </field-template>
</#if>
