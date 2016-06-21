
<#assign formatPos = "org.niord.core.fm.LatLonDirective"?new()>

<#if geometry?has_content>
    <table style="width: 80%">
        <#list geometry as feature>
            <#if feature.coordinates?has_content>
                <#list feature.coordinates as coord>
                    <tr>
                        <td><#if coord.name?has_content>${coord.name}</#if></td>
                        <td align="right"><@formatPos lat=coord.coordinates[1] format=format /></td>
                        <td align="right"><@formatPos lon=coord.coordinates[0] format=format /></td>
                    </tr>
                </#list>
            </#if>
        </#list>
    </table>
</#if>
