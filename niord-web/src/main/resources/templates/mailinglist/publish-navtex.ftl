
<#include "../messages/message-support.ftl"/>

<#assign navtex=promulgation(message, 'navtex')!>

<#if navtex??>
    <@txtToHtml text=navtex.preamble/><br>
    <@txtToHtml text=navtex.text/>
    <#if navtex.priority?? && navtex.priority != 'NONE'>
      <p>&nbsp;</p>
      <p>
        PRIORITY<br>
        ${navtex.priority}
      </p>
    </#if>
</#if>
