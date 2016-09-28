
<#assign formatPos = "org.niord.core.fm.directive.LatLonDirective"?new()>

<#if geometry?has_content>
    <#list geometry as feature>
        <#if feature.coordinates?has_content>
            <#list feature.coordinates as coord>
                <@formatPos lat=coord.coordinates[1] lon=coord.coordinates[0] format=format /><#if coord?has_next>,&nbsp;</#if>
            </#list>
        </#if>
        <#if feature?has_next>,&nbsp;</#if>
    </#list>
</#if>
