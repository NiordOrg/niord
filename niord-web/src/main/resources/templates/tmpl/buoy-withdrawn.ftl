<#include "aton-common.ftl"/>

<#assign durationEn=(params.duration??)?then(getListValue(params.duration, '', 'normal', 'en'), '')/>

<@aton
    enDefaultName="The buoy"
    enDetails="has been ${durationEn} withdrawn"
    enNavtex="${durationEn} WITHDRAWN"
    />
