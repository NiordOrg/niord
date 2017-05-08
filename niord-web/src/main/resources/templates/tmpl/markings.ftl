
<!-- ***************************************  -->
<!-- Renders the list of markings             -->
<!-- ***************************************  -->

<#macro renderMarkings markings lang="en" format="details" unmarkedText="">
    <#assign lightFormat=(format == 'audio')?then('verbose','normal')/>
    <#assign valueFormat=(format == 'navtex')?then('normal','long')/>
    <#if markings?has_content>
        marked with
        <#list markings as marking>
            <@renderListValue value=marking.type defaultValue="" format=valueFormat lang=lang/>
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
