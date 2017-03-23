
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
        <#case "Iso">isophase ${flash}<#break>
        <#case "Oc">occulting<#break>
        <#case "Al">alternating ${flash}<#break>
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
    <#assign multiple=false />
    <#if lightGroup.composite!false>
        <#assign multiple=true />
        composite groups of
    <#elseif lightGroup.grouped!false>
        <#assign multiple=true />
        groups of
    </#if>

    <#if lightGroup.groupSpec?has_content>
        <#list lightGroup.groupSpec as blinks>
            ${blinks} <#if blinks_has_next> + </#if>
        </#list>
    </#if>

    <@formatLightCharacterPhase phase=lightGroup.phase multiple=multiple />

    <#if lightGroup.colors?has_content>
        in
        <#list lightGroup.colors as col>
            <@formatLightCharacterColor col=col /><#if col_has_next>, </#if>
        </#list>
    </#if>

    <#if lightGroup.phase == "Mo">
        ${lightGroup.morseCode}
    </#if>

</#macro>


<#macro formatlightCharacter lightModel>

    <#if lightModel.lightGroups??>
        <#list lightModel.lightGroups as lightGroup>
            <@formatlightGroup lightGroup=lightGroup /><#if lightGroup_has_next> followed by </#if>
        </#list>

        <#if lightModel.period??>
            , repeated every ${lightModel.period}. seconds
        </#if>

        <#if lightModel.elevation??>
            , the light is ${lightModel.elevation} meters above the chart datum
        </#if>

        <#if lightModel.range??>
            and is visible for ${lightModel.range} nautical miles
        </#if>

    </#if>

</#macro>

<@formatlightCharacter lightModel=lightModel/>
