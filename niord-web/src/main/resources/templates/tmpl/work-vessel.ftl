
<!-- ***************************************  -->
<!-- Renders information about a work vessel  -->
<!-- ***************************************  -->

<#macro renderWorkVessel vessel lang="en" format="details">
    <#setting locale=lang>

    <#if vessel?has_content && (vessel.name?has_content || vessel.callSign?has_content)>

        <#assign csFormat=(format == 'audio')?then('verbose','normal')/>
        <#assign vesselName=(vessel.name?has_content)?then(vessel.name, '???')/>

        <#if lang == "en">

            <#if format == 'navtex'>
                By <@quote text=vesselName/>
            <#elseif format == 'audio'>
                Work is carried out by <@quote text=vesselName/>
            <#else>
                Work is carried out by <@quote text=vesselName format="angular"/>
            </#if>
            <#if vessel.callSign?has_content>call sign <@callSignFormat callSign=vessel.callSign format=csFormat/></#if>.
            <#if vessel.guardVessels!false>
                <#if format == 'navtex'>
                    Guard vessels in area.
                <#else>
                    Guard vessels will be in the area.
                </#if>
            </#if>
            <#if vessel.contact!false>
                <#if format == 'navtex'>
                    Listening VHF CH 16
                    <#if vessel.channel?has_content>and ${vessel.channel}</#if>.
                <#else>
                    The <#if vessel.guardVessels!false>vessels are<#else>vessel is</#if>
                    listening on VHF channel 16
                    <#if vessel.channel?has_content>and ${vessel.channel}</#if>.
                </#if>
            </#if>
        </#if>
    </#if>

    <#if format == 'navtex'>
        MARINERS REQUESTED TO PASS WITH CAUTION
        <#if vessel.minDist?has_content>
            KEEPING MINIMUM DISTANCE ${vessel.minDist}${(vessel.minDistType!'m')?upper_case}
        </#if>.
    <#else>
        Mariners are requested to pass with caution
        <#if vessel.minDist?has_content>
            and keep a minimum distance of ${vessel.minDist}${(vessel.minDistType!'m')}
        </#if>.
    </#if>
</#macro>
