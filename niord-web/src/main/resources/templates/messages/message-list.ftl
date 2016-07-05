<#include "message-support.ftl"/>

<html>
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${text("pdf.list.title")}</title>

    <@messageStyles />

</head>
<body>

<@renderDefaultHeaderAndFooter headerText="Generated ${.now?datetime}"/>

<#assign areaHeadingId=-9999999 />

<h1>${text("pdf.list.title")}</h1>

<div style="margin: 1cm 0">
    <table border="0">
        <tr>
            <th align="right" valign="top" nowrap>Generated: &nbsp;</th>
            <td>${.now?datetime}</td>
        </tr>
        <tr>
            <th align="right" valign="top" nowrap>Criteria: &nbsp;</th>
            <td><small>${searchCriteria}</small></td>
        </tr>
        <tr>
            <th align="right" valign="top" nowrap>Result: &nbsp;</th>
            <td>${messages?size} messages</td>
        </tr>
    </table>
</div>

<@renderTOC areaHeadings=areaHeadings />

<table class="message-table">
    <#list messages as msg>

        <#if msg.areas?has_content>
            <#assign area=areaHeading(msg.areas[0]) />
            <#if areaHeadings && area?? && area.id != areaHeadingId>
                <#assign areaHeadingId=area.id />
                <tr>
                    <td colspan="2"><h4 style="color: #8f2f7b; font-size: 16px;" id="${areaHeadingId?c}"><@areaLineage area=areaHeading(area) /></h4></td>
                </tr>
            </#if>
        </#if>
        <tr>
            <td class="table-image">
                <img src="/rest/message-map-image/${msg.id}.png" width="120" height="120"/>
            </td>
            <td class="table-item">

                <@renderMessage msg=msg />

            </td>
        </tr>
    </#list>
</table>

<#list messages as msg>
    <#if msg.attachments?has_content>
        <#list msg.attachments as att>
            <#if att.display?has_content && att.display == 'SEPARATE_PAGE' && att.type?has_content && att.type?starts_with('image')>
                <div class="separate-attachment-page">
                    <#assign imageStyle='' />
                    <#if att.width?has_content && att.height?has_content>
                        <#assign imageStyle="width: " + att.width + "; height: " + att.height />
                    <#elseif att.width?has_content>
                        <#assign imageStyle="width: " + att.width + "; " />
                    <#elseif att.height?has_content>
                        <#assign imageStyle="height: " + att.height + "; " />
                    </#if>
                    <div style="text-align: center; margin-top: 5mm">
                        <#if msg.shortId?has_content>
                            <div style="margin: 1mm">
                                Attachment for message ${msg.shortId}.
                            </div>
                        </#if>
                        <div>
                            <img src="/rest/repo/file/${msg.repoPath}/attachments/${att.fileName}" style="${imageStyle}">
                        </div>
                        <#if att.descs?has_content && att.descs[0].caption?has_content>
                            <div style="margin: 1mm">
                                ${att.descs[0].caption}
                            </div>
                        </#if>
                    </div>
                </div>
            </#if>
        </#list>
    </#if>
</#list>

</body>
</html>
