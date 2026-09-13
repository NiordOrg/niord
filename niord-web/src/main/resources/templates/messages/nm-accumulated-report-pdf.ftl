<#include "message-support.ftl"/>

<#--
    The accumulated notices: a year drawn as the weeks it was compiled from.

    The document is sectioned rather than flat, and the sections arrive in the
    model as "groups" -- each one a source issue, its numbers, its cut-off and the
    ids of the messages it printed, in the order that issue printed them. The ids
    partition the ordered "messages" list rather than duplicating it, so a section
    can never print something the list does not contain.

    A section whose id list is empty is a week that printed nothing, and it is
    still listed and still printed -- with its heading and one sentence in place
    of the tables. The sections are the record of what the year covered, and a
    year that quietly prints fifty-one of its fifty-two weeks is a document
    claiming a week was never covered.

    A model with no groups still renders: the flat list under one heading. That is
    what a preview of an unsourced issue, or any other report pointed at this
    template, produces.
-->

<#-- A cut-off is an instant, and which DAY it falls on is answered by the zone the
     publication's own desk keeps -- never by the renderer's, which on an unattended
     release is whatever zone the container happens to run in. -->

<#if timeZone?has_content>
    <#setting time_zone=timeZone>
</#if>

<#-- The messages of one section, picked out of the ordered list by id. -->
<#function messagesOfGroup group>
    <#local picked = [] />
    <#list messages as msg>
        <#if group.messageIds?seq_contains(msg.id)>
            <#local picked = picked + [msg] />
        </#if>
    </#list>
    <#return picked />
</#function>

<#-- The first and last printed number of a set of messages, where they have any. -->
<#macro numberRange msgs>
    <#local minNo = -1 />
    <#local maxNo = -1 />
    <#list msgs as msg>
        <#if msg.number??>
            <#if minNo == -1 || msg.number lt minNo><#local minNo = msg.number /></#if>
            <#if maxNo == -1 || msg.number gt maxNo><#local maxNo = msg.number /></#if>
        </#if>
    </#list>
    <#if minNo != -1>${minNo?c} - ${maxNo?c}</#if>
</#macro>

<#-- What a section is called: the source issue's own name, or what it is instead. -->
<#macro groupTitle group>
    <#compress>
        <#if group.manual>
            ${text('pdf.accumulated.added_by_hand')}
        <#elseif group.name?has_content>
            ${group.name}
        <#elseif group.week??>
            ${text('pdf.week')} ${group.week?c}
        <#else>
            ${text('pdf.nm')}
        </#if>
    </#compress>
</#macro>

<#--
    One section's messages, split the way the weekly splits its own: the notices
    proper with area headings, then the announcements under their own heading.
-->
<#macro renderGroupBody msgs prefix>
    <#local nmMsgs = [] />
    <#local miscMsgs = [] />
    <#list msgs as msg>
        <#if msg.type == 'MISCELLANEOUS_NOTICE'>
            <#local miscMsgs = miscMsgs + [msg] />
        <#else>
            <#local nmMsgs = nmMsgs + [msg] />
        </#if>
    </#list>

    <#if nmMsgs?has_content>
        <@renderMessageList messages=nmMsgs areaHeadings=areaHeadings prefix=prefix/>
    </#if>

    <#if miscMsgs?has_content>
        <table width="100%">
            <tr>
                <td class="table-header"><h4 id="${prefix}misc">${text('pdf.misc_nm')}</h4></td>
            </tr>
        </table>
        <@renderMessageList messages=miscMsgs areaHeadings=false prefix="${prefix}misc"/>
    </#if>
</#macro>


<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.accumulated.title")}</title>

    <@pageSizeStyle />
    <link rel="stylesheet" type="text/css" href="/css/templates/pdf.css">
    <link rel="stylesheet" type="text/css" href="/css/templates/common.css">

    <style type="text/css" media="all">
        .nm-toc {
            margin-top: 1cm;
        }
        .toc {
            font-size: 12px;
        }
        .intro {
            margin-top: 2cm;
            font-size: 12px;
            page-break-after:always;
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
        .year-header {
            font-size: 24px;
            text-align: right;
            margin-top: 1cm;
            margin-bottom: 1mm;
        }
        .group-header {
            margin-top: 8mm;
            page-break-after: avoid;
        }
        .group-header h3 {
            margin-bottom: 1mm;
        }
        .group-info-line {
            font-size: 11px;
            border-bottom: 1px solid #999999;
            padding-bottom: 1mm;
        }
        /* The sentence a section with nothing in it carries: the intro's text
           size, and none of its cover-page spacing or page break. */
        .group-empty {
            font-size: 12px;
            margin-top: 4mm;
        }
    </style>

</head>
<body>

<#-- Headers and footers -->
<@renderDefaultHeaderAndFooter headerText="${text('pdf.accumulated.title')} ${year!''}"/>


<#--
    First page. ${year} is the year LABEL and prints exactly as it was typed:
    free text, in letters as readily as in digits. The volume is counted from
    ${yearNumber}, the year the numbering derived -- never from the label, which
    is not arithmetic to do, and never from the clock, which would stamp the year
    of printing on a document of a year that has been over for months. An issue
    whose numbering derived no year prints no volume line at all.
-->
<h1>${text("pdf.accumulated.title")}</h1>

<div class="year-header">
    ${year!""}
</div>
<table class="first-page-info-line">
    <tr>
        <#-- The weekly prints the day it was made here. This one cannot: an annual
             is routinely rendered again years later, and a printing date would be
             the only part of the document that was not about its own year. -->
        <td width="25%" align="left">
            <#if edition?? && edition != '1'>Version ${edition}</#if>
        </td>
        <td width="25%" align="center">ISSN ${ISSN!""}</td>
        <td width="25%" align="center">
            <#if yearNumber??>${text('pdf.volume', (yearNumber - 1884))}</#if>
        </td>
        <td width="25%" align="right"><@numberRange msgs=messages /></td>
    </tr>
</table>

<#if groups?has_content>
    <div class="nm-toc">
        <h2>${text("pdf.toc")}</h2>
        <ol class='toc'>
            <#list groups as group>
                <#-- Every section, the empty ones included: the contents list what
                     the year covered, and a week missing from it reads as a week
                     that was never covered at all. -->
                <li><a href='#group_${group?index}'><@groupTitle group=group /></a></li>
            </#list>
        </ol>
    </div>
</#if>


<div class="intro">
    <#include "nm-accumulated-report-pdf-intro.ftl">
</div>


<#if groups?has_content>

    <#list groups as group>
        <#assign groupMessages = messagesOfGroup(group) />

        <#-- EVERY section is printed, including one nothing was filed under. The
             sections are the record of what the year covered, so a week that
             printed nothing still gets its heading and says so in a sentence; a
             year that quietly prints fifty-one of its fifty-two weeks is a
             document claiming a week was never covered. -->

        <#if !group?is_first>
            <div class="page-break"></div>
        </#if>

        <div class="group-header">
            <h3 id="group_${group?index}"><@groupTitle group=group /></h3>
            <table class="group-info-line" width="100%">
                <tr>
                    <td width="40%" align="left">
                        <#if !group.manual && group.week??>
                            ${text('pdf.week')} ${group.week?c}<#if group.weekTo?? && group.weekTo != group.week> - ${group.weekTo?c}</#if><#if group.year??>, ${group.year?c}</#if>
                        </#if>
                    </td>
                    <td width="30%" align="center">
                        <#if group.cutoff??>
                            <#-- ?datetime because a Date arrives without a declared kind -->
                            ${text('pdf.accumulated.cutoff')}: ${group.cutoff?datetime?string["dd. MMMM yyyy"]}
                        </#if>
                    </td>
                    <#-- No range where there are no numbers to range over. -->
                    <td width="30%" align="right"><#if groupMessages?has_content><@numberRange msgs=groupMessages /></#if></td>
                </tr>
            </table>
        </div>

        <#if groupMessages?has_content>
            <@renderGroupBody msgs=groupMessages prefix="g${group?index}"/>
        <#else>
            <p class="group-empty">${text('pdf.accumulated.no_messages')}</p>
        </#if>
    </#list>

<#elseif messages?has_content>

    <#-- No sections: the ordered list, whole, exactly as the weekly prints one. -->
    <div class="group-header">
        <h3>${text('pdf.nm')}</h3>
    </div>
    <@renderGroupBody msgs=messages prefix="nm"/>

</#if>

</body>
</html>
