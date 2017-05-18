<#include "common.ftl"/>

<#macro renderStatus format='long' lang='en'>
    <#if params.dgps_status?has_content>
        <#assign desc=descForLang(params.dgps_status, lang)!>
        <#if desc?? && format == 'long'>
        ${desc.longValue}
        <#elseif desc??>
        ${desc.value}
        </#if>
    </#if>
</#macro>

<#macro renderStationName format='long' lang='en'>
    <#if params.dgps_station?has_content>
        <#assign desc=descForLang(params.dgps_station, lang)!>
        <#if desc??>
        ${desc.value}
        </#if>
    </#if>
</#macro>

<#if params.dgps_station?? && params.dgps_station.key??>
    <#assign key=params.dgps_station.key>

<field-template field="part.getDesc('en').subject">
    DGPS <@renderStatus format="normal" lang="en"/>
</field-template>

<field-template field="part.getDesc('en').details" format="html">
    <#setting locale='en'>
    <@renderStationName format="long" lang="en"/> DGPS-station
    in pos. <@formatPos lat=params.station.coordinates[1] lon=params.station.coordinates[0] format='dec-1' />
    and frequency ${params.station.frequency?string["0.0"]} kHz
    <@renderStatus format="long" lang="en"/>
    <#if part.eventDates?has_content && part.eventDates[0].fromDate??>
        <@renderDateIntervals dateIntervals=part.eventDates lang="en"/>
    </#if>.
</field-template>

<#if promulgate('navtex')>
    <field-template field="message.promulgation('navtex').text" update="append">
        <#setting locale='en'>
        <@line format="navtex">
            DGPS-station <@renderStationName format="long" lang="en"/>
            ${params.station.frequency?string["0.0"]} kHz
            <@formatPos lat=params.station.coordinates[1] lon=params.station.coordinates[0] format='navtex' />
            <@renderStatus format="normal" lang="en"/>
            <#if part.eventDates?has_content && part.eventDates[0].fromDate??>
                <@renderDateIntervals dateIntervals=part.eventDates format="navtex" lang="en"/>
            </#if>.
        </@line>
    </field-template>
</#if>

</#if>
