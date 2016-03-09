<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        version="1.0">

    <xsl:output omit-xml-declaration="yes" indent="yes"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="presets">
        <osm-defaults>
            <xsl:apply-templates/>
        </osm-defaults>
    </xsl:template>

    <xsl:template match="group[@name='Seamarks' or ancestor-or-self::group[@name='P: Lights' or @name='Q: Buoys, Beacons, Notices' or @name='R: Fog Signals' or @name='S: Electronic Position-Fixing Systems']]">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="chunk[@id='lightcolours' or @id='othercolours' or @id='rightlateralcolours' or @id='leftlateralcolours' or @id='cardinalcolours' or @id='lightchars' or @id='lightcats' or @id='lightexhibs' or @id='lightvisis' or @id='patterns']">
        <tag-values>
            <xsl:attribute name="id">
                <xsl:value-of select="@id"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </tag-values>
    </xsl:template>

    <xsl:template match="chunk[@id='lateraltops' or @id='cardinaltops' or @id='specialtops' or @id='topshapes' or @id='buoyshapes' or @id='beaconshapes' or @id='topmarks' or @id='ialas' or @id='cevnis' or @id='laterals' or @id='cardinals' or @id='specials']">
        <tag-values>
            <xsl:attribute name="id">
                <xsl:value-of select="@id"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </tag-values>
    </xsl:template>

    <xsl:template match="list_entry">
        <tag-value>
            <xsl:attribute name="v">
                <xsl:value-of select="@value"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </tag-value>
    </xsl:template>

    <xsl:template match="item[descendant::node()[contains(@key, ':2:') or contains(@key, ':3:') or contains(@key, ':4:') or contains(@key, ':5:') or contains(@key, ':6:')]]">
        <!-- skip if sub-node has indexed keys > 1 -->
    </xsl:template>

    <xsl:template match="item">
        <node-type>
            <xsl:attribute name="name">
                <xsl:value-of select="@name"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </node-type>
    </xsl:template>

    <xsl:template match="key">
        <tag>
            <xsl:attribute name="k">
                <xsl:value-of select="@key"/>
            </xsl:attribute>
            <xsl:attribute name="v">
                <xsl:value-of select="@value"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </tag>
    </xsl:template>

    <xsl:template match="text">
        <tag>
            <xsl:attribute name="k">
                <xsl:value-of select="@key"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </tag>
    </xsl:template>

    <xsl:template match="multiselect|combo">
        <tag>
            <xsl:attribute name="k">
                <xsl:value-of select="@key"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </tag>
    </xsl:template>

    <xsl:template match="reference">
        <tag-values>
            <xsl:attribute name="ref">
                <xsl:value-of select="@ref"/>
            </xsl:attribute>
            <xsl:apply-templates/>
        </tag-values>
    </xsl:template>

    <xsl:template match="label|space|link|check">
        <!-- discard these elements -->
    </xsl:template>

    <xsl:template match="chunk|group">
        <!-- discard all other chunks and groups -->
    </xsl:template>

    <xsl:template match="*">
        <xsl:copy-of select="."/>
    </xsl:template>

</xsl:stylesheet>
