
<#assign formatPos = "org.niord.core.fm.directive.LatLonDirective"?new()>
<#assign formatDateInterval = "org.niord.core.fm.directive.DateIntervalDirective"?new()>
<#assign txtToHtml = "org.niord.core.fm.directive.TextToHtmlDirective"?new()>

<!-- ***************************************  -->
<!-- Parametrized message styles             -->
<!-- ***************************************  -->
<#macro pageSizeStyle frontPage=true>
    <style type="text/css" media="all">

        @page {
            size: ${pageSize!"A4"} ${pageOrientation!"portrait"};
        }

    <#if frontPage>
        /** First page rules **/
        @page :first {
            margin: 1.5cm 1.0cm;
            @top-center {
                content: element(first-page-header);
            }
            @bottom-center {
                content: element(first-page-footer);
            }
        }
    </#if>

    </style>
</#macro>


<!-- ***************************************  -->
<!-- Renders the default headers and footers  -->
<!-- ***************************************  -->
<#macro renderDefaultHeaderAndFooter headerText frontPage=true>
    <#if frontPage>
        <div class="first-page-header">
        </div>
    </#if>
    <div class="header">
        <span style="float: left">${headerText}</span>
        <span style="float: right">${text("pdf.page")} <span id="pagenumber"></span></span>
        &nbsp;
    </div>

    <#include "message-list-pdf-footer.ftl">
</#macro>


<!-- ***************************************  -->
<!-- Renders the parent-first area lineage    -->
<!-- ***************************************  -->
<#macro areaLineage area>
    <#if area??>
        <#if area.parent??>
            <@areaLineage area=area.parent /> -
        </#if>
        <#if area.descs?has_content>${area.descs[0].name}</#if>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Returns the two root-most area headings  -->
<!-- ***************************************  -->
<#function areaHeading area="area" >
    <#if area?? && (!area.parent?? || (area.parent?? && !area.parent.parent??))>
        <#return area/>
    </#if>
    <#if area?? >
        <#return areaHeading(area.parent) />
    </#if>
    <#return NULL/>
</#function>


<!-- ***************************************  -->
<!-- Returns if the attachment has the given type  -->
<!-- ***************************************  -->
<#function includeAttachment att type >
    <#return att.display?has_content && att.display == type && att.type?has_content && att.type?starts_with('image')/>
</#function>


<!-- ***************************************  -->
<!-- Renders an attachment  -->
<!-- ***************************************  -->
<#macro renderAttachment att >
    <#assign imageStyle='max-width: 100%;' />
    <#if att.width?has_content && att.height?has_content>
        <#assign imageStyle="max-width: 100%; width: " + att.width + "; height: " + att.height />
    <#elseif att.width?has_content>
        <#assign imageStyle="max-width: 100%; width: " + att.width + "; " />
    <#elseif att.height?has_content>
        <#assign imageStyle="max-width: 100%; height: " + att.height + "; " />
    </#if>

    <div class="attachment">
        <div>
            <img src="/rest/repo/file/${att.path}" style="${imageStyle}">
        </div>
        <#if att.descs?has_content && att.descs[0].caption?has_content>
            <div style="margin: 1mm; font-style: italic">${att.descs[0].caption}</div>
        </#if>
    </div>
</#macro>


<!-- ***************************************  -->
<!-- Renders all nessage attachments of the given type    -->
<!-- ***************************************  -->
<#macro renderMessageAttachments msg type>
    <#if msg.attachments?has_content>
        <#list msg.attachments as att>
            <#if includeAttachment(att, type)>
                <@renderAttachment att=att />
            </#if>
        </#list>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Renders all separate-page attachments    -->
<!-- ***************************************  -->
<#macro renderSeparatePageAttachments msg>
    <#if msg.attachments?has_content>
        <#list msg.attachments as att>
            <#if includeAttachment(att, 'SEPARATE_PAGE')>
            <div class="separate-attachment-page">
                <#assign messageId=msg.id />
                <#if msg.shortId?has_content>
                    <#assign messageId=msg.shortId />
                </#if>
                <div style="margin: 1mm">
                    <h4 style="color: #8f2f7b; font-size: 16px;" id="${messageId}">${text("pdf.attachment.title", messageId)}</h4>
                </div>
                <@renderAttachment att=att />
            </div>
            </#if>
        </#list>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Renders the TOC for area-headings        -->
<!-- ***************************************  -->
<#macro renderTOC messages areaHeadings prefix="">
    <#if areaHeadings>
        <div>
            <h2>${text("pdf.toc")}</h2>
            <@renderTOCEntries messages=messages prefix=prefix />
        </div>
    </#if>
</#macro>


<#macro renderTOCEntries messages prefix="">
    <#assign tocAreaHeadingId=-9999999 />
    <ol class='toc'>
        <#list messages as msg>
            <#if msg.areas?has_content>
                <#assign area=areaHeading(msg.areas[0]) />
                <#if area?? && area.id != tocAreaHeadingId>
                    <#assign tocAreaHeadingId=area.id />
                    <li><a href='#${prefix}${tocAreaHeadingId?c}'><@areaLineage area=areaHeading(area) /></a></li>
                </#if>
            </#if>
        </#list>
    </ol>
</#macro>


<!-- ***************************************  -->
<!-- Returns the desc for the given language  -->
<!-- ***************************************  -->
<#function descForLang entity lang=language >
    <#if entity.descs?has_content>
        <#list entity.descs as desc>
            <#if desc.lang?? && desc.lang == lang>
                <#return desc />
            </#if>
        </#list>
    </#if>
    <#if entity.descs?has_content>
        <#return entity.descs[0] />
    </#if>
</#function>


<!-- ***************************************  -->
<!-- Renders a language flag if wrong lang    -->
<!-- ***************************************  -->
<#macro renderLangFlag desc lang=language  draft=draft!false >
    <#if draft == true && desc?? && desc.lang != lang>
        <img src="/img/flags/${desc.lang}.png" class="lang-flag"/>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Renders a message part                   -->
<!-- ***************************************  -->
<#macro renderMessagePart part lang=language draft=draft!false >
    <#assign partDesc=descForLang(part, lang)!>
    <#if partDesc?? && partDesc.subject??>
        <p>
            <strong>${partDesc.subject}</strong>
            <@renderLangFlag desc=partDesc lang=lang draft=draft/>
        </p>
    </#if>
    <#if partDesc?? && partDesc.details??>
        <div>${partDesc.details}</div>
    </#if>
</#macro>


<!-- ***************************************  -->
<!-- Renders a message                        -->
<!-- ***************************************  -->
<#macro renderMessage msg lang=language draft=draft!false >

    <#assign msgDesc=descForLang(msg, lang)!>

    <div style="width: 100%;">

    <!-- Attachments to display above message -->
    <@renderMessageAttachments msg=msg type="ABOVE" />


    <!-- Title line -->
    <#if msg.originalInformation?has_content && msg.originalInformation>
        <div class="avoid-break-after original-information">*</div>
    </#if>

    <#if msg.shortId?has_content>
        <div class="avoid-break-before-after">
            <span class="label-message-id">${msg.shortId}</span>
        </div>
    </#if>

    <#if msgDesc?has_content && msgDesc.title?has_content>
        <div class="avoid-break-before-after">
            <strong>
                <a href="${baseUri}/#/message/${msg.id}" target="_blank" id="msg_${msg.id}">
                    ${msgDesc.title}
                </a>
            </strong>
            <@renderLangFlag desc=msgDesc lang=lang />
        </div>
    </#if>


    <table class="field-table">

        <!-- Type line (drafts only) -->
        <#if draft == true>
            <tr>
                <td class="field-name">${text("msg.field.type")}</td>
                <td class="field-value">
                    ${text("msg.type." + msg.type)} ${msg.mainType}
                </td>
            </tr>
        </#if>

        <!-- Status (drafts only) -->
        <#if draft == true>
            <tr>
                <td class="field-name">${text("msg.field.status")}</td>
                <td class="field-value">
                    ${msg.status?lower_case}
                </td>
            </tr>
        </#if>

        <!-- Reference lines -->
        <#if msg.references?has_content>
            <tr>
                <td class="field-name">
                    ${text("msg.field.references")}
                </td>
                <td class="field-value">
                    <#list msg.references as ref>
                        <#assign refDesc=descForLang(ref, lang)!>
                        <div>
                            <a href="${baseUri}/#/message/${ref.messageId?url('ASCII')}" target="_blank">${ref.messageId}</a>

                            <#if ref.type == 'REPETITION'>
                                - ${text("msg.reference.repetition")}
                            <#elseif ref.type == 'CANCELLATION'>
                                - ${text("msg.reference.cancelled")}
                            <#elseif ref.type == 'UPDATE'>
                                - ${text("msg.reference.updated")}
                            </#if>

                            <#if refDesc?has_content && refDesc.description??>
                                - ${refDesc.description}
                            </#if>
                        </div>
                    </#list>
                </td>
            </tr>
        </#if>


        <!-- Details line -->
        <#if msg.parts?has_content>
            <#list msg.parts as part>
                <tr style="page-break-inside: auto;">
                    <td class="field-name">
                        <#if part?is_first || part.type != msg.parts[part?index - 1].type>
                            ${text("msg.field." + part.type?lower_case)}
                        </#if>
                    </td>
                    <td class="field-value message-description" style="page-break-inside: auto;">
                        <@renderMessagePart part=part lang=lang draft=draft/>
                    </td>
                </tr>
            </#list>
        </#if>


        <!-- Charts line -->
        <#if msg.charts?has_content>
            <tr>
                <td class="field-name">${text("msg.field.charts")}</td>
                <td class="field-value">
                    <#list msg.charts as chart>
                    ${chart.chartNumber}
                        <#if chart.internationalNumber?has_content>(INT ${chart.internationalNumber?c})</#if>
                        <#if chart_has_next>, </#if>
                    </#list>
                </td>
            </tr>
        </#if>

        <!-- Publication line -->
        <#if msgDesc?has_content && msgDesc.publication?has_content>
            <tr>
                <td class="field-name">${text("msg.field.publication")}</td>
                <td class="field-value">
                ${msgDesc.publication!""}
                </td>
            </tr>
        </#if>

        <!-- Source line -->
        <#if msgDesc?has_content && msgDesc.source?has_content>
            <tr>
                <td class="field-value" align="right" colspan="2">
                    (${msgDesc.source!""})
                </td>
            </tr>
        </#if>

    </table>

    <!-- Attachments to display below message -->
    <@renderMessageAttachments msg=msg type="BELOW" />

    </div>

</#macro>


<!-- ***************************************  -->
<!-- Renders a list of messages               -->
<!-- ***************************************  -->
<#macro renderMessageList messages areaHeadings=true prefix="" draft=draft!false>

    <#assign areaHeadingId=-9999999 />

    <table class="message-table">
        <!-- Layout row for fixed-layout table -->
        <tr>
            <#if mapThumbnails!true>
                <#assign colspan=2 />
                <td width="140"></td>
                <td width="*"></td>
            <#else>
                <#assign colspan=1 />
                <td width="100%"></td>
            </#if>
        </tr>

        <#list messages as msg>

            <#if msg.areas?has_content>
                <#assign area=areaHeading(msg.areas[0]) />
                <#if areaHeadings && area?? && area.id != areaHeadingId>
                    <#assign areaHeadingId=area.id />
                    <tr class="table-header-row">
                        <td colspan="${colspan}" class="table-header"><h4 id="${prefix}${areaHeadingId?c}"><@areaLineage area=areaHeading(area) /></h4></td>
                    </tr>
                </#if>
            </#if>
            <#if separatePageIds?? && separatePageIds?seq_contains(msg.id)>
                <#assign msgClass="break-before" />
            <#else>
                <#assign msgClass="" />
            </#if>
            <tr class="${msgClass}">
                <#if mapThumbnails!true>
                    <td class="table-image">
                        <img src="/rest/message-map-image/${msg.id}.png" width="120" height="120"/>
                    </td>
                </#if>
                <td class="table-item">
                    <#if draft>
                        <#list languages as lang>
                            <#if lang?is_first>
                                <div class="lang-header-first">
                                    <img src="/img/flags/${lang}.png" height="12"/>&nbsp;${text('lang.' + lang)} ${text('pdf.translation')}
                                </div>
                            <#else>
                                <div class="lang-header">
                                    <img src="/img/flags/${lang}.png" height="12"/>&nbsp;${text('lang.' + lang)} ${text('pdf.translation')}
                                </div>
                            </#if>
                            <@renderMessage msg=msg lang=lang draft=draft/>
                        </#list>
                    <#else>
                        <@renderMessage msg=msg lang=language draft=draft/>
                    </#if>
                </td>
            </tr>
        </#list>
    </table>

    <#list messages as msg>
        <@renderSeparatePageAttachments msg=msg />
    </#list>

</#macro>
