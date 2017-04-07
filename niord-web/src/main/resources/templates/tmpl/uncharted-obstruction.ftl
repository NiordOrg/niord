<#include "common.ftl"/>
<#include "markings.ftl"/>

<@defaultSubjectFieldTemplates/>

<field-template field="part.getDesc('en').details" format="html">
    An uncharted obstruction is reported <@renderPositionList geomParam=part lang="en"/>.
    The obstruction is <@renderMarkings markings=params.markings! lang="en" format="details" unmarkedText="unmarked"/><br>
    Mariners are advised to keep well clear.
</field-template>

<#if promulgate('navtex')>
    <field-template field="message.promulgation('navtex').text" update="append">
        <@line format="navtex">
            UNCHARTED OBSTRUCTION REPORTED <@renderPositionList geomParam=part format="navtex" lang="en"/>.
            OBSTRUCTION <@renderMarkings markings=params.markings! lang="en" format="navtex"  unmarkedText="UNMARKED"/>
        </@line>
        <@line format="navtex">
            MARINERS ADVISED TO KEEP CLEAR.
        </@line>
    </field-template>
</#if>
