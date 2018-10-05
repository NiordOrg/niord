<?xml version="1.0" encoding="UTF-8"?>
<!-- ============================================================================================= -->
<!-- Schematron rules for ISO 19115+Cor.1 / ISO 19139 metadata for individual dataset files        -->
<!-- ============================================================================================= -->

<!--
	© Copyright 2015 ... (Draft - Copyright statement tbd)
	Prepared under NOAA contract/sub-contract.

  Certain parts of the text of this document refer to or are based on the standards of the International
  Organization for Standardization (ISO). The ISO standards can be obtained from any ISO member and from the
  Web site of the ISO Central Secretariat at www.iso.org.

	Certain parts of this work are derived from or were originally prepared as works for the UK Location Programme
  (UKLP) and GEMINI project and are (C) Crown copyright (UK). These parts are included under and subject to the
  terms of the Open Government license.

  Permission to copy and distribute this work is hereby granted provided that this notice and the notice for the
  GEMINI and UKLP programs included below are retained on all copies, and that IHO & NOAA are credited when the
  material is redistributed or used in part or whole in derivative works.
  Redistributions in binary form must reproduce this notice and the notice for the GEMINI/UKLP program in the
  documentation and/or other materials provided with the distribution.

	Disclaimer	(Draft)
  This work is provided by the copyright holders and contributors "as is" and any express or implied warranties,
  including, but not limited to, the implied warranties of merchantability and fitness for a particular purpose
  are disclaimed. In no event shall the copyright owner or contributors be liable for any direct, indirect,
  incidental, special, exemplary, or consequential damages (including, but not limited to, procurement of substitute
  goods or services; loss of use, data, or profits; or business interruption) however caused and on any theory of
  liability, whether in contract, strict liability, or tort (including negligence or otherwise) arising in any way
  out of the use of this software, even if advised of the possibility of such damage.
	
	Document history
	Version 1.0	2015-08-18	Raphael Malyankar (Jeppesen) for NOAA / IIC Technologies / IHO
  Version 1.1 2015-08-21  Updated copyright/license/disclaimer notice.
-->
<!--
    Notes:
    (1) These rules are intended for a standalone dataset discovery metadata file and would have to be adapted
        if discovery metadata is embedded in the exchange catalogue.
    (2) Written for xslt1 as least common denominator; discuss if use of XSLT 2 is acceptable.
    (3) Allows for derivations of MD_Metadata (with the isoType attribute) but not derivations of its sub-elements,
        this will have to be updated if derivations of sub-elements are used.
-->
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
  <sch:ns prefix="fn" uri="http://www.w3.org/2005/xpath-functions"/>
  <sch:ns prefix="gco" uri="http://www.isotc211.org/2005/gco"/>
  <sch:ns prefix="gmd" uri="http://www.isotc211.org/2005/gmd"/>
  <sch:ns prefix="gml" uri="http://www.opengis.net/gml/3.2"/>
  <sch:ns prefix="gmx" uri="http://www.isotc211.org/2005/gmx"/>

  <!-- Uncomment the following line if imagery and gridded metadata extensions are needed -->
  <!-- <sch:ns uri="http://www.isotc211.org/2005/gmi" prefix="gmi"/> -->

  <sch:title>IHO S-100 additional Schematron validation rules</sch:title>

  <sch:p>Supplements Version 1.4 of Schematron validation rules for ISO 19139 in ISOTS19139A1Constraints_v1.4.sch</sch:p>

  <!-- Rule to test that fileIdentifier is present and encoded as a characterString of length > 0 -->
  <sch:pattern fpi="S100_2.0.0_Table4a-2_R1">
    <sch:title>Rule for mandatory MD_Metadata.fileIdentifier</sch:title>
    <sch:rule context="/gmd:MD_Metadata | /*[gco:isoType='gmd:MD_Metadata']">
      <sch:assert test="gmd:fileIdentifier and (count(gmd:fileIdentifier/@gco:nilReason) = 0)">
        A fileIdentifier element is mandatory in S-100 and cannot be nilled.
      </sch:assert>
    </sch:rule>
    <sch:rule context="/gmd:MD_Metadata/gmd:fileIdentifier | /*[gco:isoType='gmd:MD_Metadata']/gmd:fileIdentifier">
      <sch:assert test="string-length(normalize-space()) > 0">File identifier element <sch:name/> must not be empty.</sch:assert>
    </sch:rule>
  </sch:pattern>

  <!-- The rule for language permits either a gco:CharacterString or a gmd:LanguageCode entry -->
  <sch:pattern fpi="S100_MD_language">
    <sch:title>Rule to check that MD_Metadata.language is not empty</sch:title>
    <sch:rule context="/gmd:MD_Metadata//gmd:language | /*[gco:isoType='gmd:MD_Metadata']//gmd:language">
      <sch:assert test="gmd:LanguageCode[(string-length(normalize-space(@codeList)) > 0) and (string-length(@codeListValue) > 0)]
                       | gco:CharacterString[string-length(normalize-space()) > 0]">
        If the element gmd:language is present its content must be a non-empty gco:CharacterString or a LanguageCode from the ISO 19139 codelists.
      </sch:assert>
    </sch:rule>
  </sch:pattern>

  <!-- rule to test that specified optional elements must have characterstring values if they are present.
      Note that this does not permit nilled elements, i.e., gco:nilReason="missing" will trigger a validation error;
      since these are optional elements, they are expected to be omitted if the value is missing or unknown.
  -->
  <sch:pattern fpi="S100_MD_cString_generic">
    <sch:title>Rule to check types of parentIdentifier and other optional elements</sch:title>
      <sch:rule context="gmd:parentIdentifier|gmd:hierarchyLevelName|gmd:metadataStandardName|gmd:metadataStandardVersion|gmd:dataset">
        <sch:assert test="gco:CharacterString and (string-length(normalize-space(gco:CharacterString)) > 0)"><sch:name/> must contain a gco:CharacterString with non-blank content</sch:assert>
        <sch:assert test="count(@gco:nilReason) = 0"><sch:name/> is not nillable. If the value is not available, the element must be omitted.</sch:assert>
      </sch:rule>
  </sch:pattern>

  <!-- The rule for datasetURI in S-100 metadata is an expanded form of the rule for other string-type elements.
        It includes a rudimentary check that the value is a local file name and not a network location or mailto: URL
  -->
  <sch:pattern fpi="S100_MD_cString_datasetURI">
    <sch:title>Rule to check type of datasetURI optional element</sch:title>
      <sch:rule context="gmd:dataSetURI">
        <sch:assert test="count(@gco:nilreason) = 0"><sch:name/> is not nillable.</sch:assert>
        <sch:assert test="gco:CharacterString|gmx:FileName"><sch:name/> must contain a gco:CharacterString or gmx:FileName</sch:assert>
      </sch:rule>

      <sch:p>Implementor note: The tests below for the dataset being a local file and not a network location are neither official S-100 requirements nor comprehensive.</sch:p>
      
      <sch:rule context="gmd:dataSetURI/gco:CharacterString">
        <sch:let name="fileLocation" value="normalize-space()"/>
       <sch:assert test="not(contains($fileLocation, ':') or starts-with($fileLocation, '//')
                              or starts-with($fileLocation, '\\') or contains($fileLocation, '@'))">
          Dataset location (<sch:value-of select="$fileLocation"/>) must be a local file name/path</sch:assert>
      </sch:rule>
    <sch:rule context="gmd:dataSetURI/gmx:FileName">
      <sch:let name="fileLocation" value="normalize-space(@src)"/>
      <sch:assert test="not(contains($fileLocation, ':') or starts-with($fileLocation, '//')
        or starts-with($fileLocation, '\\') or contains($fileLocation, '@'))">
        Dataset location in "src" attribute (<sch:value-of select="$fileLocation"/>) must be a local file name/path</sch:assert>
    </sch:rule>
  </sch:pattern>

  <!-- Rule to check that MD_Metadata.contact is not nilled and has at least one of organisation name, individual
        name, or positionName elements. The ISOTS19139A1Constraints_v1.4 Schematron file checks for the presence
        of at least one of these elements, but not whether they have actual (non-blank) content.
        NOTE: This rule checks only MD_Metadata.contact, not "contact" elements under other elements like resourceSpecificUsage.
        S-100 does not state that this requirement applies in other possible locations of "contact". 
  -->
  <sch:pattern fpi="S100_2.0.0_Table4a-2_R5">
    <sch:title>Contact information presence test</sch:title>
    <sch:rule context="/gmd:MD_Metadata/gmd:contact |/*[gco:isoType='gmd:MD_Metadata']/gmd:contact">
      <sch:assert test="count(@gco:nilReason) = 0">Contact is not nillable.</sch:assert>
      <!--<sch:assert test="count(gmd:CI_ResponsibleParty/gmd:organisationName|gmd:CI_ResponsibleParty/gmd:positionName|gmd:CI_ResponsibleParty/gmd:individualName) > 0">
        At least one of CI_ResponsibleParty.{individualName, organizationName, positionName} must be provided.
      </sch:assert>-->
      <sch:assert test="0 &lt; (string-length(normalize-space(gmd:CI_ResponsibleParty/gmd:organisationName)) +
                             string-length(normalize-space(gmd:CI_ResponsibleParty/gmd:positionName)) +
                             string-length(normalize-space(gmd:CI_ResponsibleParty/gmd:individualName)))">
        contact.CI_ResponsibleParty {organisationName, positionName, individualName} cannot all be missing or blank.
      </sch:assert>
    </sch:rule>
  </sch:pattern>

  <!-- rule to check for presence of title, abstract, and date in identificationInfo elements -->
  <sch:pattern fpi="S100_2.0.0_Table4a-2_R9-11">
    <sch:title>Dataset title, reference date, and abstract presence test</sch:title>
    <sch:p>This pattern checks the presence of title, date and abstract even if a citation element is (wrongly) nilled.</sch:p>
    <sch:p>Absence of identificationInfo is permitted</sch:p>
    <sch:rule context="/gmd:MD_Metadata | /*[gco:isoType='gmd:MD_Metadata']">
      <sch:assert test="gmd:identificationInfo/gmd:MD_DataIdentification/gmd:citation/gmd:CI_Citation/gmd:title">
        MD_Metadata/MD_identificationInfo/MD_DataIdentification.citation/CI_Citation.title is required
      </sch:assert>
      <sch:assert test="gmd:identificationInfo/gmd:MD_DataIdentification/gmd:citation/gmd:CI_Citation/gmd:date">
        MD_Metadata/MD_identificationInfo/MD_DataIdentification.citation/CI_Citation.date is required
      </sch:assert>
      <sch:assert test="gmd:identificationInfo/gmd:MD_DataIdentification/gmd:abstract">
        MD_Metadata / MD_identificationInfo / MD_DataIdentification / abstract is required
      </sch:assert>
    </sch:rule>
    <sch:rule context="/gmd:MD_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:citation/gmd:CI_Citation/gmd:date
      | /*[gco:isoType='gmd:MD_Metadata']/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:citation/gmd:CI_Citation/gmd:date">
      <sch:assert test="count(@gco:nilReason) = 0">
        MD_identificationInfo / ... / reference date is not nillable
      </sch:assert>
    </sch:rule>
    <sch:rule context="/gmd:MD_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:abstract
      | /*[gco:isoType='gmd:MD_Metadata']/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:abstract">
      <sch:assert test="count(@gco:nilReason) = 0">
        MD_identificationInfo / MD_DataIdentification / abstract is not nillable
      </sch:assert>
    </sch:rule>
  </sch:pattern>
  
  <!-- Rules to test validity of bounding box coordinates (if any) in gmd:extent elements. Assumes lat/lon in decimal degrees, ranges +/-90.0 and +/-180.0. -->
  <sch:pattern is-a="S100_ValidBBoxPattern">
    <sch:title>Validity of bounding box corners</sch:title>
    <sch:param name="bbox" value="//gmd:extent/gmd:EX_Extent/gmd:geographicElement/gmd:EX_GeographicBoundingBox"></sch:param>
  </sch:pattern>
  
  <sch:pattern id="S100_ValidBBoxPattern" abstract="true" fpi="S100_BBox_LLDD_MinMax">
    <sch:title>Check the values of the bounding box min/max. Assumes values are latitude and longitude in decimal degrees in +/-90 or +/-180 range respectively.</sch:title>
    <sch:rule context="$bbox">
      <sch:assert test="gmd:westBoundLongitude and (string-length(gmd:westBoundLongitude) > 0) and (gmd:westBoundLongitude >= -180.0) and (gmd:westBoundLongitude &lt;= 180.0)" flag="badWB">
        westBoundLongitude must be present and in the range [-180.0, 180.0].
      </sch:assert>
      <sch:assert test="gmd:eastBoundLongitude and (string-length(gmd:eastBoundLongitude) > 0) and (gmd:eastBoundLongitude >= -180.0) and (gmd:eastBoundLongitude &lt;= 180.0)" flag="badEB">
        eastBoundLongitude must be present and in the range [-180.0, 180.0].
      </sch:assert>
      <sch:assert test="gmd:southBoundLatitude and (string-length(gmd:southBoundLatitude) > 0) and (gmd:southBoundLatitude >= -90.0) and (gmd:southBoundLatitude &lt;= 90.0)" flag="badSB">
        southBoundLatitude must be present and in the range [-90.0, 90.0].
      </sch:assert>
      <sch:assert test="gmd:northBoundLatitude and (string-length(gmd:northBoundLatitude) > 0) and (gmd:northBoundLatitude >= -90.0) and (gmd:northBoundLatitude &lt;= 90.0)" flag="badNB">
        northBoundLatitude must be present and in the range [-90.0, 90.0].
      </sch:assert>
      <sch:assert test="not(badEB or badWB) and (gmd:westBoundLongitude &lt; gmd:eastBoundLongitude)">
        westBoundLongitude (<sch:value-of select="gmd:westBoundLongitude"/>) must be less than eastBoundLongitude (<sch:value-of select="gmd:eastBoundLongitude"/>)
      </sch:assert>
      <sch:assert test="not(badNB or badSB) and (gmd:southBoundLatitude &lt; gmd:northBoundLatitude)">
        northBoundLatitude (<sch:value-of select="gmd:northBoundLatitude"/>) must be greater than southBoundLatitude (<sch:value-of select="gmd:southBoundLatitude"/>)
      </sch:assert>
    </sch:rule>
  </sch:pattern>

  <!-- ========================================================================================== -->
  <!-- The following rule is from the Schematron Schema for the UK GEMINI Standard Version 2.1 and
        is included under the licence terms below:
        (C) Crown copyright, 2011
        You may use and re-use the information in this publication (not including logos) free of charge
        in any format or medium, under the terms of the Open Government Licence.
  -->
  <!-- ========================================================================================== -->
  <sch:pattern fpi="Gemini2-at5">
    <sch:title>Creation date type</sch:title>
    <sch:p>Constrain citation date type = creation to one occurrence.</sch:p>
    <sch:rule context="//gmd:CI_Citation | //*[@gco:isoType='gmd:CI_Citation'][1]">
      <sch:assert test="count(gmd:date/*[1]/gmd:dateType/*[1][@codeListValue='creation']) &lt;= 1">
        There shall not be more than one creation date.
      </sch:assert>
    </sch:rule>
  </sch:pattern>

  <!-- End of extract from UK GEMINI Standard Version 2.1  -->
</sch:schema>