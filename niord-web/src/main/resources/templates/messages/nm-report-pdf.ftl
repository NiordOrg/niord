<#include "message-support.ftl"/>

<#assign permMessages = [] />
<#assign tpMessages = [] />
<#assign miscMessages = [] />
<#assign minMessageNo = 9999999 />
<#assign maxMessageNo = -9999999 />
<#list messages as msg>
    <#if msg.type == 'PRELIMINARY_NOTICE' || msg.type == 'TEMPORARY_NOTICE'>
        <#assign tpMessages = tpMessages + [msg] />
    <#elseif msg.type == 'PERMANENT_NOTICE'>
        <#assign permMessages = permMessages + [msg] />
    <#elseif msg.type == 'MISCELLANEOUS_NOTICE'>
        <#assign miscMessages = miscMessages + [msg] />
    </#if>

    <#if msg.number?? && msg.number lt minMessageNo >
        <#assign minMessageNo = msg.number />
    <#elseif msg.number?? && msg.number gt maxMessageNo >
        <#assign maxMessageNo = msg.number />
    </#if>
</#list>


<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.list.title")}</title>

    <@pageSizeStyle />
    <link rel="stylesheet" type="text/css" href="/css/templates/pdf.css">
    <link rel="stylesheet" type="text/css" href="/css/templates/common.css">

    <style type="text/css" media="all">
        .nm-section {
            page-break-before:always;
        }
        h1 {
            margin-top: 2cm;
            text-align: center;
            font-size: 36px;
            color: #334;
        }
        /** TODO: The string-set does not work - Flying Saucer problem? **/
        h3 {
            string-set: sectiontitle content();
        }
        #section-title:before {
            content: string(sectiontitle);
        }
        .nm-toc {
            margin-top: 1cm;
        }
        .toc {
            font-size: 12px;
        }
        .intro {
            margin-top: 2cm;
            font-size: 12px;
        }
        .intro table {
            margin: 0 auto;
            font-size: 12px;
            text-align: left;
        }
        .intro table th {
            width: 2cm;
            font-weight: normal;
        }
    </style>

</head>
<body>

<!-- Headers and footers -->
<div class="first-page-header">
</div>
<div class="header">
    <span style="float: left"><span id="section-title"></span></span>
    <span style="float: right">${text("pdf.page")} <span id="pagenumber"></span></span>
    &nbsp;
</div>
<#include "message-list-pdf-footer.ftl">


<!-- First page -->
<h1>${text("pdf.nm")}</h1>

<div style="font-size: 24px; text-align: right; margin-top: 1cm; margin-bottom: 1mm">
    ${text("pdf.week")} ${week!""}, ${year!""}
</div>
<table style="background-color: #334; color: white; width: 100%">
    <tr>
        <td width="30%" align="left">${.now?string["dd. MMMM yyyy"]}</td>
        <td width="40%" align="center">ISSN ${ISSN!""}</td>
        <td width="30%" align="right">
            <#if minMessageNo != 9999999 && maxMessageNo != -9999999>
                ${minMessageNo?c} - ${maxMessageNo?c}
            </#if>
        </td>
    </tr>
</table>

<div class="nm-toc">
    <h2>${text("pdf.toc")}</h2>
    <ol>
    <#if permMessages?has_content>
        <li>
            <div class="toc"><a href='#perm'>Chart Updates</a></div>
            <@renderTOCEntries messages=permMessages prefix="perm" />
        </li>
    </#if>
    <#if tpMessages?has_content>
        <li>
            <div class="toc"><a href='#tp'>T & P Messages</a></div>
            <@renderTOCEntries messages=tpMessages prefix="tp" />
        </li>
    </#if>
    <#if miscMessages?has_content>
        <li>
            <div class="toc"><a href='#misc'>Announcements...</a></div>
        </li>
    </#if>
    </ol>
</div>


<div class="intro">
    <#include "nm-report-pdf-intro.ftl">
</div>


<!-- Permanent messages -->
<#if permMessages?has_content>
    <div class="nm-section">
        <h3 style="color: #333; font-size: 20px;" id="perm">Chart Updates</h3>
        <@renderMessageList messages=permMessages areaHeadings=areaHeadings prefix="perm"/>
    </div>
</#if>

<!-- T&P messages -->
<#if tpMessages?has_content>
    <div class="nm-section">
        <h3 style="color: #333; font-size: 20px;" id="tp">T & P Messages</h3>
        <@renderMessageList messages=tpMessages areaHeadings=areaHeadings prefix="tp"/>
    </div>
</#if>

<!-- Misc messages -->
<#if miscMessages?has_content>
    <div class="nm-section">
        <h3 style="color: #333; font-size: 20px;" id="misc">Announcements...</h3>
        <@renderMessageList messages=miscMessages areaHeadings=false/>
    </div>
</#if>

</body>
</html>
