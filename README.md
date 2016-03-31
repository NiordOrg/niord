# niord

The niord project contains the common code-base for the NW + NM T&P editing and publishing system.

## Prerequisites

* Java 8
* Maven 3
* MySQL 5.7.10+ (NB: proper spatial support is a requirement)
* JBoss Wildfly 10+
* JBoss Keycloak 1.9+

The [niord-appsrv](https://github.com/NiordOrg/niord-appsrv) project contains scripts for installing and configuring 
Wildfly + Keycloak for a development set-up.


## Deployment

Once Wildfly has been set up and configured, 


## Country-specific Implementations

Country-specific implementations of the Niord system are easily created using a web-application overlay project.
Here additional code can be added and web resources (images, stylesheets, javascript files, etc) can be replaced with 
custom versions.

For an example, please refer to [niord-dk](https://github.com/NiordOrg/niord-dk) - a Danish implementation of Niord.

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
        "key"         : "authServerAdminUser",
        "description" : "The Keycloak user to use for creating Keycloak clients",
        "value"       : "KEYCLOAK-ADMIN-USER",
        "web"         : false,
        "editable"    : true
      },
      {
        "key"         : "authServerAdminPassword",
        "description" : "The Keycloak password to use for creating Keycloak clients",
        "value"       : "KEYCLOAK-ADMIN-PASSWORD",
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
        "web"         : false,
        "editable"    : true
      }
    ]



## Tips and Tricks

*IntelliJ set-up:*

* First, check out and open the parent niord project in IntelliJ.
* In Run -> Edit configuration..., configure a new local JBoss server based on the [niord-appsrv](https://github.com/NiordOrg/niord-appsrv) project.
* Deploy "niord-web:war exploded" to the server.
* If working on a country-specific Niord implementation, e.g. [niord-dk](https://github.com/NiordOrg/niord-dk), 
  import this maven project via the "Maven Projects" tab. Deploy the imported project to Wildfly instead of "niord-web".
* If you have only updated web resources, there is no need to re-deploy the web application. Use the "Update resources" function instead.

