
<#include "publish-navwarn-common.ftl"/>

<div>
    Navigational Warning no. <#if message.shortId?has_content>${message.shortId}</#if>
</div>
<@renderNavWarnDetails msg=message lang=language></@renderNavWarnDetails>
