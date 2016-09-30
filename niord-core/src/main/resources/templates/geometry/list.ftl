
<#assign formatPos = "org.niord.core.fm.directive.LatLonDirective"?new()>

<#if geometry?has_content>
    <table class="positions">
        <#list geometry as feature>
            <#if feature.name?has_content>
                <tr><th colspan="3">${feature.name}</th></tr>
            </#if>
            <#if feature.coordinates?has_content>
                <#list feature.coordinates as coord>
                    <tr>
                        <td nowrap>${feature.startIndex + coord?index})</td>
                        <td nowrap><@formatPos lat=coord.coordinates[1] lon=coord.coordinates[0] format=format/></td>
                        <td><#if coord.name?has_content>${coord.name}</#if></td>
                    </tr>
                </#list>
            </#if>
        </#list>
    </table>
</#if>
