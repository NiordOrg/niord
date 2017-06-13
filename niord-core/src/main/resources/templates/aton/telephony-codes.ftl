
<#macro formatTelephonyCode code separator="-">
    <#assign result=""/>
    <#list 0..<code?length as i>
        <#if !(i?is_first)>
            <#assign result=result + separator/>
        </#if>
        <#assign result=result + formatTelephonyCharacter(code?substring(i, i+1)?upper_case)/>
    </#list>
    ${result}
</#macro>

<#function formatTelephonyCharacter code>
    <#assign result=""/>
    <#switch code>
        <#case "A"><#assign result="Alpha"/><#break>
        <#case "B"><#assign result="Bravo"/><#break>
        <#case "C"><#assign result="Charlie"/><#break>
        <#case "D"><#assign result="Delta"/><#break>
        <#case "E"><#assign result="Echo"/><#break>
        <#case "F"><#assign result="Foxtrot"/><#break>
        <#case "G"><#assign result="Golf"/><#break>
        <#case "H"><#assign result="Hotel"/><#break>
        <#case "I"><#assign result="India"/><#break>
        <#case "J"><#assign result="Juliet"/><#break>
        <#case "K"><#assign result="Kilo"/><#break>
        <#case "L"><#assign result="Lima"/><#break>
        <#case "M"><#assign result="Mike"/><#break>
        <#case "N"><#assign result="November"/><#break>
        <#case "O"><#assign result="Oscar"/><#break>
        <#case "P"><#assign result="Papa"/><#break>
        <#case "Q"><#assign result="Quebec"/><#break>
        <#case "R"><#assign result="Romeo"/><#break>
        <#case "S"><#assign result="Sierra"/><#break>
        <#case "T"><#assign result="Tango"/><#break>
        <#case "U"><#assign result="Uniform"/><#break>
        <#case "V"><#assign result="Victor"/><#break>
        <#case "W"><#assign result="Whiskey"/><#break>
        <#case "X"><#assign result="Xray"/><#break>
        <#case "Y"><#assign result="Yankee"/><#break>
        <#case "Z"><#assign result="Zulu"/><#break>
        <#case "1"><#assign result="One"/><#break>
        <#case "2"><#assign result="Two"/><#break>
        <#case "3"><#assign result="Three"/><#break>
        <#case "4"><#assign result="Four"/><#break>
        <#case "5"><#assign result="Five"/><#break>
        <#case "6"><#assign result="Six"/><#break>
        <#case "7"><#assign result="Seven"/><#break>
        <#case "8"><#assign result="Eight"/><#break>
        <#case "9"><#assign result="Nine"/><#break>
        <#case "0"><#assign result="Zero"/><#break>
    </#switch>
    <#return result />
</#function>

<@formatTelephonyCode code=code!""/>
