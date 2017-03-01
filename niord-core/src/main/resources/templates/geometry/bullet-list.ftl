
<#assign formatPos = "org.niord.core.script.directive.LatLonDirective"?new()>
<#assign trailingDot = "org.niord.core.script.directive.TrailingDotDirective"?new()>

<#if geometry?has_content>
    <ul class="positions">
        <#list geometry as feature>
            <#if feature.coordinates?has_content>
                <#list feature.coordinates as coord>
                    <li>
                        <@trailingDot>
                            <@formatPos lat=coord.coordinates[1] lon=coord.coordinates[0] format=format/><#if coord.name?has_content>, ${coord.name}</#if>
                        </@trailingDot>
                    </li>
                </#list>
            </#if>
        </#list>
    </ul>
</#if>
