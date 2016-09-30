
<#assign formatPos = "org.niord.core.fm.directive.LatLonDirective"?new()>

<#if geometry?has_content>
    <#list geometry as feature>
        <table>
            <#if feature.coordinates?has_content>
                <#list feature.coordinates as coord>
                    <tr>
                        <td nowrap align="right" style="width: 0.8cm">${feature.startIndex + coord?index})</td>
                        <td nowrap align="right" style="width: 2.3cm"><@formatPos lat=coord.coordinates[1] format=format /></td>
                        <td nowrap align="right" style="width: 2.3cm"><@formatPos lon=coord.coordinates[0] format=format /></td>
                        <td><#if coord.name?has_content>${coord.name}</#if></td>
                    </tr>
                </#list>
            </#if>
        </table>
    </#list>
</#if>
