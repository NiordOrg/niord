
<#include "../messages/message-support.ftl"/>

<style>
    .safetynetTable td, .safetynetTable th {
        border: 1px solid lightgray;
        padding: 5px;
    }
</style>

<#assign safetynet=promulgation(message, 'safetynet')!>
<#assign areaType = params.area.class.name/>

<#macro renderAreas area lang=language>
    <#if area??>
        <#if area.parent??>
            <@renderAreas area=area.parent lang=lang/> -
        </#if>
        <#assign areaDesc=descForLang(area, lang)!>
        <#if areaDesc?? && areaDesc.name?has_content>
            ${areaDesc.name}
        </#if>
    </#if>
</#macro>

<#include "publish-safetynet-intro.ftl">

<#if safetynet??>
<table class="safetynetTable">
    <tr>
        <th>1</th>
        <th>MSI Type</th>
        <td>Navigational</td>
    </tr>
    <#if message.type == 'COASTAL_WARNING'>
        <tr>
            <th>2</th>
            <th>Service Code</th>
            <td>13 - Coastal Warnings</td>
        </tr>
    <#elseif message.type == 'NAVAREA_WARNING'>
        <tr>
            <th>2</th>
            <th>Service Code</th>
            <td>51 - NAVAREA Warnings</td>
        </tr>
    </#if>
    <tr>
        <th>3</th>
        <th>Priority</th>
        <td>
            <#if safetynet.priority?has_content>
                ${safetynet.priority?capitalize}
            </#if>
        </td>
    </tr>
    <#if params.area?? && areaType?ends_with('SafetyNetCircularAreaVo')>
        <tr>
            <th rowspan="3">4</th>
            <th>Area Type</th>
            <td>Circular</td>
        </tr>
        <tr>
            <th>Center</th>
            <td>${params.area.center!''}</td>
        </tr>
        <tr>
            <th>Radius (NM)</th>
            <td>${params.area.radius!''}</td>
        </tr>
    <#elseif params.area?? && areaType?ends_with('SafetyNetRectangularAreaVo')>
        <tr>
            <th rowspan="4">4</th>
            <th>Area Type</th>
            <td>Rectangular</td>
        </tr>
        <tr>
            <th>SW Corner</th>
            <td>${params.area.swCorner!''}</td>
        </tr>
        <tr>
            <th>Northings</th>
            <td>${params.area.northings!''}</td>
        </tr>
        <tr>
            <th>Eastings</th>
            <td>${params.area.eastings!''}</td>
        </tr>
    <#elseif params.area?? && areaType?ends_with('SafetyNetCoastalAreaVo')>
        <tr>
            <th rowspan="4">4</th>
            <th>Area Type</th>
            <td>Other</td>
        </tr>
        <tr>
            <th>Area Definition</th>
            <td>Coastal Area</td>
        </tr>
        <tr>
            <th>Area</th>
            <td>${params.area.navareaDescription!''} - Area ${params.area.coastalArea!''}</td>
        </tr>
        <tr>
            <th>Subject</th>
            <td>A - Navigational warnings</td>
        </tr>
    <#elseif params.area?? && areaType?ends_with('SafetyNetNavareaAreaVo')>
        <tr>
            <th rowspan="3">4</th>
            <th>Area Type</th>
            <td>Other</td>
        </tr>
        <tr>
            <th>Area Definition</th>
            <td>NAVAREA</td>
        </tr>
        <tr>
            <th>Area</th>
            <td>${params.area.navareaDescription!''}</td>
        </tr>
    <#else>
        <tr>
            <th rowspan="1">4</th>
            <th>Area Type</th>
            <td></td>
        </tr>
    </#if>
    <tr>
        <th rowspan="2">5</th>
        <th>Start Date</th>
        <td></td>
    </tr>
    <tr>
        <th>Repetition Code</th>
        <td>
            <#if safetynet.repetition??>
                ${safetynet.repetitionDescription}
            </#if>
        </td>
    </tr>
    <tr>
        <th>6</th>
        <th>MSI Contents</th>
        <td>
            <#if message.areas?has_content>
                <div>
                    <@line case="upper">
                        <@renderAreas area=message.areas[0] lang='en'></@renderAreas>
                        <#assign msgDesc=descForLang(message, lang)!>
                        <#if msgDesc?? && msgDesc.vicinity?has_content>
                            - ${msgDesc.vicinity}
                        </#if>
                    </@line>
                </div>
            </#if>
            <div>
                <@txtToHtml text=safetynet.text/>
            </div>
        </td>
    </tr>
</table>

</#if>
