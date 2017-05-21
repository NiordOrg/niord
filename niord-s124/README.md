# niord-s124

Important: This sub-project is currently at a proof-of-concept stage only.

It provides an interface for exporting navigational warnings in Niord as 
S-124 GML data.

The S-124 format used is based on the one used by the STM-project ("Area Exchange Format"):
http://stmvalidation.eu/schemas/

## API

Niord provides a REST endpoint for converting a navigational warning, say "NW-069-17", to
S-124 (assuming you are running at localhost):

    http://localhost:8080/rest/S-124/NW-069-17.gml?lang=en

The function can also be tested via Swagger: 

    http://localhost:8080/api.html#!/S-124/messageDetails


## Validation 

Validation of the produced GML can be done using _xmllint_:

    xmllint --noout \
        --schema http://localhost:8080/rest/S-124/S124.xsd \
        http://localhost:8080/rest/S-124/NW-069-17.gml
    
## Comments

A collection of comments about the current stage of the S-124 format:

* In general, too much focus on supporting NAVTEX-like warnings for ECDIS clients. 
  Consider promulgation via e.g. Web and Apps as first-tier clients too.
* Support for NM T&P. Due to the similarity between NW and NM T&P, the latter should 
  be properly supported in the model.
* For some of its fields, the S-124 spec makes use of S124_LocalizedTextType, which should
  be used more generally, and in a manner so that a localizable field, say _Subject_, can
  list all language variants, not just one.
* It is a great limitation that _generalArea_ is enumeration-based.
* Would prefer a more generic approach to modelling _generalArea_ and _locality_.
* It is a great limitation that _generalCategory_ is enumeration-based.
* If the model supported NM T&P, it should be possible to support rich-text (HTML)
  for the textual description, support for attachments, etc.
* Rather than splitting geometry fields into two, "geometry" and "areaAffected", a more 
  general solution would be to use a single "geometry" field, but extend the
  S100 geometry classes with a "restriction" flag ("affected", "prohibited", etc.)
* The reference model seems to imply that an ECDIS should update the list of in-force
  warnings based on "in-force" and "cancellation" references. This sounds somewhat 
  dangerous, if e.g. updates are missed. It would be more robust for the ECDIS to 
  periodically pull lists of in-force warnings.
