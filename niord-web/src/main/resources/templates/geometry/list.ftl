
<#assign formatPos = "org.niord.core.fm.LatLonDirective"?new()>

<#if geometry?has_content>
    <#list geometry as feature>
        <#if feature.name?has_content>
            <div class="feature-name">${feature.name}</div>
        </#if>
        <#if feature.coordinates?has_content>
            <ol class="feature-coordinates" start="${feature.startIndex}">
                <#list feature.coordinates as coord>
                    <li>
                        <span>
                            <@formatPos lat=coord.coordinates[1] lon=coord.coordinates[0] format=format /><#if coord.name?has_content>,&nbsp;${coord.name}</#if>
                        </span>
                    </li>
                </#list>
            </ol>
        </#if>
    </#list>
</#if>
