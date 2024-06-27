<?xml version="1.0" encoding="UTF-8"?>

<#assign htmlToText = "org.niord.core.script.directive.HtmlToTextDirective"?new()>
<#assign id='DK.' + msg.shortId!msg.id/>
<#assign mrn='urn:mrn:iho:' + msg.mainType?lower_case + ':dk:' + (msg.shortId!msg.id)?lower_case/>
<#assign geomId=0>
<#setting time_zone="UTC">

<S124:DataSet xmlns:S124="http://www.iho.int/S124/gml/1.0"
              xsi:schemaLocation="http://www.iho.int/S124/gml/1.0 S124.xsd"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xmlns:gml="http://www.opengis.net/gml/3.2"
              xmlns:S100="http://www.iho.int/s100gml/1.0"
              xmlns:xlink="http://www.w3.org/1999/xlink"
              gml:id="${id}">

    <#if bbox??>
        <gml:boundedBy>
            <gml:Envelope srsName="EPSG:4326">
                <gml:lowerCorner>${bbox[1]} ${bbox[0]}</gml:lowerCorner>
                <gml:upperCorner>${bbox[3]} ${bbox[2]}</gml:upperCorner>
            </gml:Envelope>
        </gml:boundedBy>
    </#if>

    <imember>
        <@generatePreamble msg=msg></@generatePreamble>
    </imember>

    <#assign partNo = 0/>
    <#if msg.parts?has_content>
        <#assign partNo = msg.parts?size/>
        <#list msg.parts as part>
            <#if part.geometry?? && part.geometry.features?has_content>
                <member>
                    <S124:S124_NavigationalWarningPart gml:id="${id}.${part?index + 1}">
                        <@generateNavWarnPart part=part index=part?index></@generateNavWarnPart>
                    </S124:S124_NavigationalWarningPart>
                </member>
            <#else>
                <imember>
                    <S124:S124_InformationNoticePart gml:id="${id}.${part?index + 1}">
                        <@generateNavWarnPart part=part index=part?index></@generateNavWarnPart>
                    </S124:S124_InformationNoticePart>
                </imember>
            </#if>
        </#list>
    </#if>

    <#if references?has_content>
        <#list references as ref>
            <imember>
                <@generateReference ref=ref index=ref?index + partNo></@generateReference>
            </imember>
        </#list>
    </#if>

</S124:DataSet>


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


<#function lang lang=language!'en'>
    <#switch lang>
        <#case "da">
            <#return 'dan' />
            <#break>
        <#default>
            <#return 'eng' />
            <#break>
    </#switch>
</#function>


<#macro generateMessageSeries msg>
    <#switch msg.type>
        <#case "LOCAL_WARNING">
            <NameOfSeries>Danish Nav Warn</NameOfSeries>
            <typeOfWarning>local</typeOfWarning>
            <#break>
        <#case "COASTAL_WARNING">
            <NameOfSeries>Danish Nav Warn</NameOfSeries>
            <typeOfWarning>coastal</typeOfWarning>
            <#break>
        <#case "SUBAREA_WARNING">
            <NameOfSeries>Danish Nav Warn</NameOfSeries>
            <typeOfWarning>sub-area</typeOfWarning>
            <#break>
        <#case "NAVAREA_WARNING">
            <NameOfSeries>Danish Nav Warn</NameOfSeries>
            <typeOfWarning>NAVAREA</typeOfWarning>
            <#break>
    </#switch>
    <warningNumber>${msg.number!-1}</warningNumber>
    <year>${(msg.year % 100)?string['00']}</year>
    <productionAgency>
        <#switch language!'en'>
            <#case "da">
                <language>dan</language>
                <text>SØFARTSSTYRELSEN</text>
                <#break>
            <#default>
                <language>eng</language>
                <text>DANISH MARITIME AUTHORITY</text>
                <#break>
        </#switch>
    </productionAgency>
    <country>DK</country>
</#macro>


<#macro generatePreamble msg>

    <#assign msgDesc=descForLang(msg)!>

    <S124:S124_NWPreamble gml:id="PR.${id}">
        <id>${mrn}</id>

        <messageSeriesIdentifier>
            <@generateMessageSeries msg=msg></@generateMessageSeries>
        </messageSeriesIdentifier>

        <#if msg.publishDateFrom??>
            <sourceDate>${msg.publishDateFrom?string["yyyy-MM-dd"]}</sourceDate>
        </#if>

        <#if msg.categories?has_content>
            <@generateCategory category=msg.categories[0]></@generateCategory>
        </#if>

        <#if msg.areas?has_content>
            <@generateArea msgArea=msg.areas[0] area=msg.areas[0]></@generateArea>
        </#if>
        <#if msgDesc?? && msgDesc.vicinity?has_content>
            <locality>
                <language>${lang(msgDesc.lang)}</language>
                <text>${msgDesc.vicinity}</text>
            </locality>
        </#if>

        <#if msgDesc?? && msgDesc.title?has_content>
            <title>
                <language>${lang(msgDesc.lang)}</language>
                <text>${msgDesc.title}</text>
            </title>
        </#if>

        <#if msg.charts?has_content>
            <#list msg.charts as chart>
                <affectedCharts>
                    <chartAffected>${chart.chartNumber}</chartAffected>
                    <#if chart.internationalNumber??>
                        <internationalChartAffected>${chart.internationalNumber?c}</internationalChartAffected>
                    </#if>
                </affectedCharts>
            </#list>
        </#if>

        <#assign partNo = 0/>
        <#if msg.parts?has_content>
            <#assign partNo = msg.parts?size/>
            <#list msg.parts as part>
                <theWarningPart xlink:href="#${id}.${part?index + 1}"></theWarningPart>
            </#list>
        </#if>

        <#if references?has_content>
            <#list references as ref>
                <theWarningPart xlink:href="#${id}.${ref?index + partNo + 1}"></theWarningPart>
            </#list>
        </#if>

    </S124:S124_NWPreamble>
</#macro>


<#macro generateCategory category>
    <#assign enCategoryDesc=descForLang(category, 'en')!>
    <#if enCategoryDesc??>
        <#switch enCategoryDesc.name>
            <#case "Light">
            <#case "Light buoy">
            <#case "Buoy">
            <#case "Beacon">
                <generalCategory>aids to navigation</generalCategory>
                <#break>
            <#case "Wreck">
                <generalCategory>dangerous wreck</generalCategory>
                <#break>
            <#case "Drifting object">
                <generalCategory>drifting hazard</generalCategory>
                <#break>
            <#case "Underwater survey">
                <generalCategory>underwater operations</generalCategory>
                <#break>
            <#case "Cable operations">
                <generalCategory>pipe or cable laying operations</generalCategory>
                <#break>
            <#case "Radio navigation">
                <generalCategory>radio navigation services</generalCategory>
                <#break>
            <#case "Firing Exercises">
                <generalCategory>military exersices</generalCategory>
                <#break>
            <#default>
                <#if category.parent??>
                    <@generateCategory category=category.parent></@generateCategory>
                </#if>
                <#break>
        </#switch>
    </#if>
</#macro>


<#macro generateLocality area rootArea>
    <#if area.id != rootArea.id>
        <#assign areaDesc=descForLang(area, language)!>
        <#if areaDesc?? && areaDesc.name?has_content>
            <locality>
                <language>${lang(areaDesc.lang)}</language>
                <text>${areaDesc.name}</text>
            </locality>
        </#if>
        <#if area.parent??>
            <@generateLocality area=area.parent rootArea=rootArea></@generateLocality>
        </#if>
    </#if>
</#macro>


<#macro generateArea msgArea area>
    <#assign enAreaDesc=descForLang(area, 'en')!>
    <#if enAreaDesc??>
        <#switch enAreaDesc.name>
            <#case "The Baltic Sea">
                <generalArea>Baltic sea</generalArea>
                <@generateLocality area=msgArea rootArea=area></@generateLocality>
                <#break>
            <#case "Skagerrak">
                <generalArea>Skagerrak</generalArea>
                <@generateLocality area=msgArea rootArea=area></@generateLocality>
                <#break>
            <#case "Kattegat">
                <generalArea>Kattegat</generalArea>
                <@generateLocality area=msgArea rootArea=area></@generateLocality>
                <#break>
            <#case "The Sound">
                <generalArea>The Sound</generalArea>
                <@generateLocality area=msgArea rootArea=area></@generateLocality>
                <#break>
            <#case "The Great Belt">
            <#case "The Little Belt">
                <generalArea>The Belts</generalArea>
                <@generateLocality area=msgArea rootArea=area></@generateLocality>
                <#break>
            <#default>
                <#if area.parent??>
                    <@generateArea msgArea=msgArea area=area.parent></@generateArea>
                <#else>
                    <@generateLocality area=msgArea rootArea=area></@generateLocality>
                </#if>
                <#break>
        </#switch>
    </#if>
</#macro>


<#macro generateNavWarnPart part index>
    <#assign partDesc=descForLang(part, language)!>

    <id>${mrn}.${index + 1}</id>

    <#if part.geometry?? && part.geometry.features?has_content>
        <#list part.geometry.features as feature>
            <@generateGeometry g=feature.geometry></@generateGeometry>
        </#list>
    </#if>

    <#if partDesc?? && partDesc.details?has_content>
        <Subject>
            <language>${lang(partDesc.lang)}</language>
            <text><@htmlToText html=partDesc.details></@htmlToText></text>
        </Subject>
    </#if>

    <#if part.eventDates?? && part.eventDates?has_content>
        <#list part.eventDates as date>
            <#assign allDay=date.allDay?? && date.allDay == true />
            <fixedDateRange>
                <#if date.fromDate?? && !allDay>
                    <timeOfDayStart>${date.fromDate?string["HH:mm:ss"]}Z</timeOfDayStart>
                </#if>
                <#if date.toDate?? && !allDay>
                    <timeOfDayEnd>${date.toDate?string["HH:mm:ss"]}Z</timeOfDayEnd>
                </#if>
                <#if date.fromDate??>
                    <dateStart>
                        <date>${date.fromDate?string["yyyy-MM-dd"]}</date>
                    </dateStart>
                </#if>
                <#if date.toDate??>
                    <dateEnd>
                        <date>${date.toDate?string["yyyy-MM-dd"]}</date>
                    </dateEnd>
                </#if>
            </fixedDateRange>
        </#list>
    </#if>

    <header xlink:href="#PR.${id}"></header>
</#macro>


<#macro generateReference ref index>
    <S124:S124_References gml:id="${id}.${index + 1}">
        <id>${mrn}.${index + 1}</id>
        <#switch ref.type>
            <#case "CANCELLATION">
                <referenceType>cancellation</referenceType>
                <#break>
            <#default>
                <referenceType>source reference</referenceType>
                <#break>
        </#switch>
        <messageReference>
            <@generateMessageSeries msg=ref.msg></@generateMessageSeries>
        </messageReference>
        <header xlink:href="#PR.${id}"></header>
    </S124:S124_References>
</#macro>


<#macro generateGeometry g>

    <#switch g.type!''>
        <#case "Point">
            <@generatePoint coords=g.coordinates></@generatePoint>
            <#break>
        <#case "MultiPoint">
            <#list g.coordinates as coords>
                <@generatePoint coords=coords></@generatePoint>
            </#list>
            <#break>
        <#case "LineString">
            <@generateCurve coords=g.coordinates></@generateCurve>
            <#break>
        <#case "MultiLineString">
            <#list g.coordinates as coords>
                <@generateCurve coords=coords></@generateCurve>
            </#list>
            <#break>
        <#case "Polygon">
            <@generateSurface coords=g.coordinates></@generateSurface>
            <#break>
        <#case "MultiPolygon">
            <#list g.coordinates as coords>
                <@generateSurface coords=coords></@generateSurface>
            </#list>
            <#break>
        <#case "GeometryCollection">
            <#list g.geometries as geom>
                <@generateGeometry g=geom></@generateGeometry>
            </#list>
            <#break>
    </#switch>
</#macro>


<#macro generatePoint coords>
    <#if coords?? && coords?size gt 1>
        <geometry>
            <S100:pointProperty>
                <S100:Point gml:id="${nextGeomId()}" srsName="EPSG:4326">
                    <gml:pos><@generateCoordinates coords=[coords]></@generateCoordinates></gml:pos>
                </S100:Point>
            </S100:pointProperty>
        </geometry>
    </#if>
</#macro>


<#macro generateCurve coords>
    <#if coords?? && coords?size gt 1>
        <geometry>
            <S100:curveProperty>
                <S100:Curve gml:id="${nextGeomId()}" srsName="EPSG:4326">
                    <gml:segments>
                        <gml:LineStringSegment>
                            <gml:posList><@generateCoordinates coords=coords></@generateCoordinates></gml:posList>
                        </gml:LineStringSegment>
                    </gml:segments>
                </S100:Curve>
            </S100:curveProperty>
        </geometry>
    </#if>
</#macro>


<#macro generateSurface coords>
    <#if coords?? && coords?size gt 0>
        <geometry>
            <S100:surfaceProperty>
                <S100:Surface gml:id="${nextGeomId()}" srsName="EPSG:4326">
                    <gml:patches>
                        <gml:PolygonPatch>
                            <#list coords as linearRing>
                                <#if linearRing?is_first>
                                    <gml:exterior>
                                        <gml:LinearRing>
                                            <gml:posList><@generateCoordinates coords=linearRing></@generateCoordinates></gml:posList>
                                        </gml:LinearRing>
                                    </gml:exterior>
                                <#else>
                                    <gml:interior>
                                        <gml:LinearRing>
                                            <gml:posList><@generateCoordinates coords=linearRing></@generateCoordinates></gml:posList>
                                        </gml:LinearRing>
                                    </gml:interior>
                                </#if>
                            </#list>
                        </gml:PolygonPatch>
                    </gml:patches>
                </S100:Surface>
            </S100:surfaceProperty>
        </geometry>
    </#if>
</#macro>


<#macro generateCoordinates coords>
    <#list coords as lonLat>${lonLat[1]} ${lonLat[0]} </#list>
</#macro>


<#function nextGeomId>
    <#assign geomId=geomId + 1>
    <#return 'G.${id}.${geomId?c}' />
</#function>