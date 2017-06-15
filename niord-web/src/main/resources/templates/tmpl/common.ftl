
<!-- ***************************************  -->
<!-- Global directives                        -->
<!-- ***************************************  -->
<#assign formatPos = "org.niord.core.script.directive.LatLonDirective"?new()>
<#assign line = 'org.niord.core.script.directive.LineDirective'?new()>
<#assign quote = 'org.niord.core.script.directive.QuoteDirective'?new()>
<#assign trailingDot = "org.niord.core.script.directive.TrailingDotDirective"?new()>
<#assign debug = 'org.niord.core.script.directive.DebugDirective'?new()>
<#assign dateIntervalFormat = 'org.niord.core.script.directive.DateIntervalDirective'?new()>
<#assign navtexDateFormat = 'org.niord.core.script.directive.NavtexDateFormatDirective'?new()>
<#assign lightCharacterFormat = 'org.niord.core.script.directive.LightCharacterDirective'?new()>
<#assign callSignFormat = 'org.niord.core.script.directive.CallSignDirective'?new()>
<#assign posDecimals = (params.posDecimals!'1')?number> <#-- 1 decimals in position formatting -->

<!-- ***************************************  -->
<!-- Returns the desc for the given language  -->
<!-- ***************************************  -->
<#function descForLang entity lang >
    <#if entity.descs?has_content>
        <#list entity.descs as desc>
            <#if desc.lang?? && desc.lang == lang>
                <#return desc />
            </#if>
        </#list>
    </#if>
    <#if entity.descs?has_content>
        <#return entity.descs[0] />
    </#if>
</#function>


<!-- ***************************************  -->
<!-- Renders the parent-first area lineage    -->
<!-- ***************************************  -->
<#macro renderAreaLineage area lang="en">
    <#if area??>
        <#if area.parent??>
            <@renderAreaLineage area=area.parent lang=lang />
        </#if>
        <#assign areaDesc=descForLang(area, lang)!>
        <#if areaDesc?? && areaDesc.name?has_content >
            <@trailingDot>${areaDesc.name}</@trailingDot>
        </#if>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Renders the message title line           -->
<!-- ***************************************  -->
<#macro renderTitleLine message lang="en">
    <#if message??>
        <#if message.areas?has_content>
            <@renderAreaLineage area=message.areas[0] lang=lang />
        </#if>
        <#assign msgDesc=descForLang(message, lang)!>
        <#if msgDesc?? && msgDesc.vicinity?has_content>
            <@trailingDot>${msgDesc.vicinity}</@trailingDot>
        </#if>
        <#if message.parts?has_content>
            <#list message.parts as part>
                <#assign partDesc=descForLang(part, lang)!>
                <#if partDesc?? && partDesc.subject?has_content>
                    <@trailingDot>${partDesc.subject}</@trailingDot>
                </#if>
            </#list>
        </#if>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Returns the two root-most area headings  -->
<!-- ***************************************  -->
<#function areaHeading area="area" >
    <#if area?? && (!area.parent?? || (area.parent?? && !area.parent.parent??))>
        <#return area/>
    </#if>
    <#if area?? >
        <#return areaHeading(area.parent) />
    </#if>
    <#return NULL/>
</#function>


<!-- ***************************************  -->
<!-- Renders default suject fields of the     -->
<!-- first details message part as the name   -->
<!-- of the current template                  -->
<!-- ***************************************  -->
<#macro defaultMessageSubjectFieldTemplates>
    <#list languages as lang>
        <#assign desc=descForLang(template, lang)!>
        <#if desc?? && desc.name?has_content>
            <field-template field="message.part('DETAILS').getDesc('${lang}').subject" format="text">
                ${desc.name}
            </field-template>
        </#if>
    </#list>
</#macro>


<!-- ***************************************  -->
<!-- Renders default suject fields of the     -->
<!-- current message part as the name of the  -->
<!-- current template                         -->
<!-- ***************************************  -->
<#macro defaultSubjectFieldTemplates>
    <#list languages as lang>
        <#assign desc=descForLang(template, lang)!>
        <#if desc?? && desc.name?has_content>
            <field-template field="part.getDesc('${lang}').subject" format="text">
                ${desc.name}
            </field-template>
        </#if>
    </#list>
</#macro>


<!-- ***************************************  -->
<!-- Serializes the message geometry param    -->
<!-- into a list of positions                 -->
<!-- ***************************************  -->
<#function toPositions geomParam >
    <#assign positions = []/>
    <#if geomParam?? && geomParam.geometry?has_content>
        <#list geomParam.geometry as feature>
            <#if feature.coordinates?has_content>
                <#list feature.coordinates as coord>
                    <#assign positions = positions + [ coord ]/>
                </#list>
            </#if>
        </#list>
    </#if>
    <#return positions/>
</#function>


<!-- ***************************************  -->
<!-- Returns if the geometry param defines    -->
<!-- multiple positions                       -->
<!-- ***************************************  -->
<#function multiplePositions geomParam >
    <#assign positions = toPositions(geomParam) />
    <#return positions?size gt 1/>
</#function>


<!-- ***************************************  -->
<!-- Returns if the geometry represents       -->
<!-- an area, i.e. defined by a polygon       -->
<!-- ***************************************  -->
<#function isArea geomParam >
    <#if geomParam?? && geomParam.geometry?has_content>
        <#list geomParam.geometry as feature>
            <#if feature.geometryType?has_content && (feature.geometryType == 'Polygon' || feature.geometryType == 'MultiPolygon')>
                <#return true/>
            </#if>
        </#list>
    </#if>
    <#return false/>
</#function>


<!-- ***************************************  -->
<!-- Renders the geometry parameter           -->
<!-- as a list of positions                   -->
<!-- ***************************************  -->
<#macro renderPositionList geomParam format="dec" plural=false lang='en'>
    <#setting locale=lang>
    <#assign positions=toPositions(geomParam)/>
    <#assign area=isArea(geomParam)/>

    <#if positions?size gt 1>
        <#if plural>
            <#switch format>
                <#case 'audio'>${text('position.in_positions')}<#break>
                <#case 'navtex'><#break>
                <#default>${text('position.in_pos')}
            </#switch>
        <#else>
            <#if area>${text('position.in_area')}</#if>
            <#switch format>
                <#case 'audio'>${text('position.between_positions')}<#break>
                <#case 'navtex'><#break>
                <#default>${text('position.between_pos')}
            </#switch>
        </#if>
    <#else>
        <#switch format>
            <#case 'audio'>${text('position.in_position')}<#break>
            <#case 'navtex'><#break>
            <#default>${text('position.in_pos')}
        </#switch>
    </#if>

    <#list positions as pos>
        <@formatPos lat=pos.coordinates[1] lon=pos.coordinates[0] format=format decimals=posDecimals/>
        <#if pos_has_next> ${text('term.and')} </#if>
    </#list>
</#macro>


<!-- ***************************************  -->
<!-- Renders the date                         -->
<!-- ***************************************  -->
<#macro renderDate date format="html" lang="en" tz="">
    <#setting locale=lang>
    <@dateIntervalFormat date=date format=format tz=tz/>
</#macro>


<!-- ***************************************  -->
<!-- Renders the date interval                -->
<!-- ***************************************  -->
<#macro renderDateInterval dateInterval format="html" lang="en" tz="">
    <#setting locale=lang>
    <@dateIntervalFormat dateInterval=dateInterval format=format tz=tz/>
</#macro>


<!-- ***************************************  -->
<!-- Renders the date intervals                -->
<!-- ***************************************  -->
<#macro renderDateIntervals dateIntervals format="html" lang="en" tz="" capFirst=false>
    <#setting locale=lang>
    <#if dateIntervals?has_content>
        <@dateIntervalFormat dateIntervals=dateIntervals format=format tz=tz capFirst=capFirst/>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Renders the given list value             -->
<!-- ***************************************  -->
<#macro renderListValue value defaultValue format='long' lang='en'>
    <#if value?has_content>
        <#assign desc=descForLang(value, lang)!>
        <#if desc?? && format == 'long'>
            ${desc.longValue}
        <#elseif desc??>
            ${desc.value}
        <#elseif defaultValue>
            ${defaultValue}
        </#if>
    <#else>
        ${defaultValue}
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Renders the AtoN given by the aton_type  -->
<!-- parameter                                -->
<!-- ***************************************  -->
<#macro renderAtonType atonParams defaultName format='long' lang='en'>
    <#if atonParams?has_content && atonParams.aton_type?has_content>
        <#assign desc=descForLang(atonParams.aton_type, lang)!>
        <#if desc?? && format == 'long'>
            ${desc.longValue?cap_first}
        <#elseif desc??>
            ${desc.value?cap_first}
        <#else>
            ${defaultName}
        </#if>
    <#else>
        ${defaultName}
    </#if>
    <#if atonParams?has_content && atonParams.aton_name?has_content>
        ${atonParams.aton_name}
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Returns whether to promulgate the given  -->
<!-- promulgation type or not                 -->
<!-- ***************************************  -->
<#function promulgate type="" >
    <#if message.promulgations?has_content>
        <#list message.promulgations as p>
            <#if p.type?? && p.type.typeId == type>
                <#return p.promulgate/>
            </#if>
        </#list>
    </#if>
    <#return false/>
</#function>
