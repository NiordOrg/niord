
<#assign formatPos = "org.niord.core.fm.directive.LatLonDirective"?new()>

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
    <table>
        <#list geometry as feature>
            <#if feature.coordinates?has_content>
                <#list feature.coordinates as coord>
                    <tr>
                        <td nowrap align="right" style="width: 0.8cm">${coord.index})</td>
                        <td nowrap align="right" style="width: 2.3cm"><@formatPos lat=coord.coordinates[1] format=format /></td>
                        <td nowrap align="right" style="width: 2.3cm"><@formatPos lon=coord.coordinates[0] format=format /></td>
                        <#if hasName>
                            <td><#if coord.name?has_content>${coord.name}</#if></td>
                        </#if>
                    </tr>
                </#list>
            </#if>
        </#list>
    </table>
</#if>
