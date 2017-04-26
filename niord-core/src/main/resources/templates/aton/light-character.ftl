
<#macro formatLightCharacterPhase phase multiple=false >
    <#assign flash=multiple?then('flashes', 'flash') >
    <#switch phase>
        <#case "F">fixed light<#break>
        <#case "Fl">${flash}<#break>
        <#case "FFl">fixed light with ${flash}<#break>
        <#case "LFl">long ${flash}<#break>
        <#case "Q">quick ${flash}<#break>
        <#case "VQ">very quick ${flash}<#break>
        <#case "IQ">interrupted quick ${flash}<#break>
        <#case "IVQ">interrupted very quick ${flash}<#break>
        <#case "UQ">ultra quick ${flash}<#break>
        <#case "IUQ">interrupted ultra quick ${flash}<#break>
        <#case "Iso">isophase light<#break>
        <#case "Oc">${multiple?then('occultings', 'occulting')}<#break>
        <#case "Al">alternating<#break>
        <#case "Mo">morse code<#break>
    </#switch>
</#macro>


<#macro formatLightCharacterColor col>
    <#switch col>
        <#case "W">white<#break>
        <#case "G">green<#break>
        <#case "R">red<#break>
        <#case "Y">yellow<#break>
        <#case "B">blue<#break>
        <#case "Am">amber<#break>
    </#switch>
</#macro>


<#macro formatlightGroup lightGroup>
    <#assign multiple=lightGroup.grouped />

    <#if lightGroup.phase == 'Mo'>
        morse code ${lightGroup.morseCode}

    <#elseif lightGroup.phase == 'Al'>
        alternating
        <#if lightGroup.groupSpec?has_content>
            <#list lightGroup.groupSpec as blinks>
                ${blinks} <#if blinks_has_next> + </#if>
            </#list>
        </#if>

        <#if lightGroup.colors?has_content>
            <#list lightGroup.colors as col>
                <#if !col?is_first && col?is_last> and <#elseif !col?is_first>, </#if>
                <@formatLightCharacterColor col=col/>
            </#list>
        </#if>

    <#elseif lightGroup.phase == 'Oc'>

        <#if lightGroup.colors?has_content>
            <#list lightGroup.colors as col>
                <#if !col?is_first && col?is_last> and <#elseif !col?is_first>, </#if>
                <@formatLightCharacterColor col=col/>
            </#list> light with
        </#if>

        occulting
        <#if lightGroup.groupSpec?has_content>
            <#list lightGroup.groupSpec as blinks>
            ${blinks} <#if blinks_has_next> + </#if>
            </#list>
        </#if>

    <#else>
        <#if lightGroup.groupSpec?has_content>
            <#list lightGroup.groupSpec as blinks>
                ${blinks} <#if blinks_has_next> + </#if>
            </#list>
        </#if>

        <#if lightGroup.colors?has_content>
            <#list lightGroup.colors as col>
                <#if !col?is_first && col?is_last> and <#elseif !col?is_first>, </#if>
                <@formatLightCharacterColor col=col/>
            </#list>
        </#if>

        <@formatLightCharacterPhase phase=lightGroup.phase multiple=multiple />
    </#if>
</#macro>


<#macro formatlightCharacter lightModel>

    <#if lightModel.lightGroups??>
        <#list lightModel.lightGroups as lightGroup>
            <@formatlightGroup lightGroup=lightGroup /><#if lightGroup_has_next> followed by </#if>
        </#list>

        <#if lightModel.period??>
             every ${lightModel.period}. seconds
        </#if>

        <#if lightModel.elevation??>
            , ${lightModel.elevation} meters
        </#if>

        <#if lightModel.range??>
            , ${lightModel.range} nautical miles
        </#if>

    </#if>

</#macro>

<@formatlightCharacter lightModel=lightModel/>
