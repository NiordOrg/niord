[![Build Status](https://github.com/NiordOrg/niord/workflows/Java%20CI/badge.svg)](https://github.com/NiordOrg/niord/actions)
# Niord
The niord repository contains the common code-base for the NW + NM T&P editing and publishing system.

# Important Notice
The following concerns Niord 2.0 which is currently under development.
The latest stable release is tagged with 1.0.1 in this repository.

## Development Setup

To get started with developing Niord you need to check out the developer guide at 
http://docs.niord.org/dev-guide/guide.html.

## Country-specific Implementations

Country-specific implementations of the Niord system are easily created using a web-application overlay project.
Here additional code can be added and web resources (images, stylesheets, javascript files, etc) can be replaced with 
custom versions.

For an example, please refer to [niord-dk](https://github.com/NiordOrg/niord-dk) - a Danish implementation of Niord.

## Public API
A swagger definition of the public portion of the Rest API is published at https://niord.e-navigation.net/rest/swagger.json.

The swagger definition is generated from the jersey annotated methods in [ApiRestService.java](https://github.com/NiordOrg/niord/blob/master/niord-web/src/main/java/org/niord/web/api/ApiRestService.java) and [S124RestService.java](https://github.com/NiordOrg/niord/blob/master/niord-s124/src/main/java/org/niord/s124/S124RestService.java).

## Configuration

Sensitive or environment-specific settings should be placed in a "${niord.home}/niord.json" file. Example:

    [
      {
        "key"         : "baseUri",
        "description" : "The base application server URI",
        "value"       : "https://niord.mydomain.com",
        "web"         : false,
        "editable"    : true
      },
      {
        "key"         : "wmsLogin",
        "description" : "The WMS login",
        "value"       : "YOUR-SECRET-WMS-LOGIN",
        "web"         : false,
        "editable"    : true
      },
      {
        "key"         : "wmsPassword",
        "description" : "The WMS password",
        "value"       : "YOUR-SECRET-WMS-PASSWORD",
        "type"        : "Password",
        "web"         : false,
        "editable"    : true
      }
    ]



## Tips and Tricks

*IntelliJ set-up:*

Notice the following describes the setup for a previous version of Niord.

* First, check out and open the parent niord project in IntelliJ.
* In Run -> Edit configuration..., configure a new local JBoss server based on the [niord-appsrv](https://github.com/NiordOrg/niord-appsrv) project.
* Deploy "niord-web:war exploded" to the server.
* If working on a country-specific Niord implementation, e.g. [niord-dk](https://github.com/NiordOrg/niord-dk), 
  import this maven project via the "Maven Projects" tab. Deploy the imported project to Wildfly instead of "niord-web".
* If you have only updated web resources, there is no need to re-deploy the web application. Use the "Update resources" function instead.
* To get rid of superfluous IntelliJ code editor warnings, disable the "Declaration access can be weaker" 
  and "Dangling Javadoc comment" inspections.

