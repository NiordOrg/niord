# niord-s124

Important: This sub-project is currently at a demo stage only.

It provides an interface for exporting navigational warnings in Niord as 
S-124 GML data.

The S-124 format used is based on the one used by the STM-project ("Area Exchange Format"):
http://stmvalidation.eu/schemas/

## API

Niord provides a REST endpoint for converting a navigational warning, say "NW-069-17", to
S-124 (assuming you are running at localhost):

    http://localhost:8080/rest/S-124/NW-069-17.gml?lang=en

The function can also be tested at the Swagger page: 

    http://localhost:8080/api.html#!/S-124/messageDetails


## Validation 

Validation of the produced GML can be done using _xmllint_:

    xmllint --noout \
        --schema http://localhost:8080/rest/S-124/S124.xsd \
        http://localhost:8080/rest/S-124/NW-069-17.gml
    