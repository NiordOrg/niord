
<#assign formatPos = "org.niord.core.script.directive.LatLonDirective"?new()>
<#assign trailingDot = "org.niord.core.script.directive.TrailingDotDirective"?new()>

<#function nameColumn geometry>
    <#list geometry as feature>
        <#if feature.hasCoordinateName>
            <#return true/>
        </#if>
    </#list>
    <#return false/>
</#function>


<#if geometry?has_content>
    <#assign hasName = nameColumn(geometry)>
    <table class="positions">
        <#list geometry as feature>
            <#if feature.name?has_content>
                <tr><th colspan="${hasName?then(3,2)}">${feature.name}</th></tr>
            </#if>
            <#if feature.coordinates?has_content>
                <#list feature.coordinates as coord>
                    <tr>
                        <td nowrap>${coord.index})</td>
                        <td nowrap><@formatPos lat=coord.coordinates[1] lon=coord.coordinates[0] format=format/><#if !coord.name?has_content>.</#if></td>
                        <#if hasName>
                            <td><@trailingDot><#if coord.name?has_content>${coord.name}</#if></@trailingDot></td>
                        </#if>
                    </tr>
                </#list>
            </#if>
        </#list>
    </table>
</#if>
