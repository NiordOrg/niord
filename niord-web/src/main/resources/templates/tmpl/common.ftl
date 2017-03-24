
<!-- ***************************************  -->
<!-- Global directives                        -->
<!-- ***************************************  -->
<#assign formatPos = "org.niord.core.script.directive.LatLonDirective"?new()>
<#assign line = 'org.niord.core.script.directive.LineDirective'?new()>
<#assign trailingDot = "org.niord.core.script.directive.TrailingDotDirective"?new()>
<#assign debug = 'org.niord.core.script.directive.DebugDirective'?new()>
<#assign navtexDateFormat = 'org.niord.core.script.directive.NavtexDateFormatDirective'?new()>
<#assign lightCharacterFormat = 'org.niord.core.script.directive.LightCharacterDirective'?new()>
<#assign callSignFormat = 'org.niord.core.script.directive.CallSignDirective'?new()>
<#assign posFormat = 'dec-3'> <#-- 3 decimals in message details -->

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
<!-- Renders default suject fields as the     -->
<!-- name of the current template             -->
<!-- ***************************************  -->
<#macro defaultSubjectFieldTemplates>
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
<!-- Serializes the message part geomtry      -->
<!-- into a list of positions                 -->
<!-- ***************************************  -->
<#function toPositions part >
    <#assign positions = []/>
    <#if part.geometry?has_content>
        <#list part.geometry as feature>
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
<!-- Returns if the message part defines      -->
<!-- multiple positions                       -->
<!-- ***************************************  -->
<#function multiplePositions part >
    <#assign positions = toPositions(part) />
    <#return positions?size gt 1/>
</#function>


<!-- ***************************************  -->
<!-- Renders the geometry of message part     -->
<!-- as a list of positions                   -->
<!-- ***************************************  -->
<#macro renderPositionList part format=posFormat lang='en'>
    <#setting locale=lang>
    <#assign positions=toPositions(part)/>
    <#if positions?size gt 1>${text('position.between')}<#elseif format != 'navtex'>${text('position.in')}</#if>
    <#if format == 'audio'>${text('position.position')}<#elseif format != 'navtex'>${text('position.pos')}</#if>
    <#list positions as pos>
        <@formatPos lat=pos.coordinates[1] lon=pos.coordinates[0] format=format />
        <#if pos_has_next> ${text('term.and')} </#if>
    </#list>
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
<#macro renderAton defaultName format='long' lang='en'>
    <#if params?has_content && params?has_content && params.aton_type?has_content>
        <#assign desc=descForLang(params.aton_type, lang)!>
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
    <#if params?has_content && params?has_content && params.aton_name?has_content>
        ${params.aton_name}
    </#if>
</#macro>
