
<!-- ***************************************  -->
<!-- Global directives                        -->
<!-- ***************************************  -->
<#assign formatPos = "org.niord.core.script.directive.LatLonDirective"?new()>
<#assign line = 'org.niord.core.script.directive.LineDirective'?new()>


<!-- ***************************************  -->
<!-- Renders the parent-first area lineage    -->
<!-- ***************************************  -->
<#macro areaLineage area>
    <#if area??>
        <#if area.parent??>
            <@areaLineage area=area.parent /> -
        </#if>
        <#if area.descs?has_content>${area.descs[0].name}</#if>
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
<!-- Returns the desc for the given language  -->
<!-- ***************************************  -->
<#function descForLang entity lang=language >
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
<!-- Renders default title fields             -->
<!-- ***************************************  -->
<#macro defaultTitleFieldTemplates>
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
<!-- Serializes the message part geometry     -->
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
<!-- Renders the geometry of message part     -->
<!-- as a list of positions                   -->
<!-- ***************************************  -->
<#macro renderPositionList part format='dec'>
    <#assign positions=toPositions(part)/>
    <#if positions?size gt 1>between<#elseif format != 'navtex'>in</#if>
    <#if format == 'audio'>postion<#elseif format != 'navtex'>pos.</#if>
    <#list positions as pos>
        <@formatPos lat=pos.coordinates[1] lon=pos.coordinates[0] format=format />
        <#if pos_has_next> and </#if>
    </#list>
</#macro>

