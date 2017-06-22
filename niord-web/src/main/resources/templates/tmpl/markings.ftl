
<!-- ***************************************  -->
<!-- Renders the list of markings             -->
<!-- ***************************************  -->

<#macro renderMarkingType marking format='long' lang='en'>
    <#assign markingType=(marking.type??)?then(getListValue(marking.type, '', format, lang), '')/>
    <#assign markingColor=(marking.color??)?then(getListValue(marking.color, '', 'normal', lang), '')/>
    ${markingType?replace('{{color}}', markingColor)}
</#macro>


<#macro renderMarkings markings lang="en" format="details" unmarkedText="">
    <#assign lightFormat=(format == 'navtex')?then('normal','verbose')/>
    <#assign valueFormat=(format == 'navtex')?then('normal','long')/>
    <#if markings?has_content>
        ${(markingType == 'buoy')?then('buoyed', 'marked')} with
        <#list markings as marking>
            <#if format == 'navtex'>${(marking.color??)?then(getListValue(marking.color, '', 'normal', lang), '')}</#if>
            <@renderMarkingType marking=marking format=valueFormat lang=lang/>
            <#if marking.lightCharacter?has_content>
                showing
                <@lightCharacterFormat light=marking.lightCharacter format=lightFormat/>
            </#if>
            <#if marking.distance??>approx. ${marking.distance}m.</#if>
            <#if marking.bearing??>
                <@renderListValue value=marking.bearing defaultValue="" format=valueFormat lang=lang/>
            </#if>
            <#if marking.distance?? || marking.bearing??>of the position</#if>
            <#if marking?has_next> and <#else>.</#if>
        </#list>
    <#else>
        ${unmarkedText}.
    </#if>
</#macro>
