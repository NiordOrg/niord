
<#assign formatPos = "org.niord.core.fm.directive.LatLonDirective"?new()>

<#function nameColumn coordinates>
    <#list coordinates as coord>
        <#if coord.name?has_content>
            <#return true/>
        </#if>
    </#list>
    <#return false/>
</#function>

<#function coordinates geometry>
    <#assign coords = []>
    <#list geometry as feature>
        <#list feature.coordinates as coord>
            <#assign coords = coords + [coord]>
        </#list>
    </#list>
    <#return coords/>
</#function>


<#if geometry?has_content>
    <#assign coords = coordinates(geometry)>
    <#if coords?has_content && coords?size % 2 == 0>

        <#assign fromCoords = coords?chunk(coords?size / 2)[0]>
        <#assign toCoords = coords?chunk(coords?size / 2)[1]>
        <#assign fromName = nameColumn(fromCoords)>
        <#assign toName = nameColumn(toCoords)>
        <table class="position-table">
            <#list fromCoords as fromCoord>
                <tr>
                    <!-- From position -->
                    <td class="pos-index">${fromCoord.index})</td>
                    <td class="pos-col"><@formatPos lat=fromCoord.coordinates[1] lon=fromCoord.coordinates[0] format=format /></td>
                    <#if fromName>
                        <td><#if fromCoord.name?has_content>${fromCoord.name}</#if></td>
                    </#if>

                    <!-- To position -->
                    <#assign toCoord = toCoords[fromCoord?index]!>
                    <td class="pos-index"><#if toCoord?has_content>${toCoord.index})</#if></td>
                    <td class="pos-col"><#if toCoord?has_content><@formatPos lon=toCoord.coordinates[0] lat=toCoord.coordinates[1] format=format /></#if></td>
                    <#if toName>
                        <td><#if toCoord?has_content && toCoord.name?has_content>${toCoord.name}</#if></td>
                    </#if>
                </tr>
            </#list>
        </table>
    </#if>
</#if>
