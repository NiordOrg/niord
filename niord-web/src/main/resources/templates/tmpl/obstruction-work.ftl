<#include "common.ftl"/>
<#include "work-vessel.ftl"/>

<#macro obstructionWork enDetails="" enNavtex="">

    <@defaultSubjectFieldTemplates/>

    <field-template field="part.getDesc('en').details" format="html">
        <@renderDateInterval dateInterval=part.eventDates[0] lang="en"/>
        ${enDetails}
        <@renderPositionList geomParam=part lang="en"/>.
        <@renderWorkVessel vessel=params.vessel! lang="en" format="details"/>
    </field-template>

    <field-template field="message.promulgation('navtex').text" update="append">
        <@line format="navtex">
            <@renderDateInterval dateInterval=part.eventDates[0] lang="en" format="navtex"/>
            ${enNavtex}
            <@renderPositionList geomParam=part lang="en" format="navtex"/>.
            <@renderWorkVessel vessel=params.vessel! lang="en" format="navtex"/>
        </@line>
    </field-template>

</#macro>
