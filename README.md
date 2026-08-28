[![Build Status](https://github.com/NiordOrg/niord/workflows/Java%20CI/badge.svg)](https://github.com/NiordOrg/niord/actions)
# Niord
The niord repository contains the common code-base for the NW + NM T&P editing and publishing system.

## Building

The build is pinned to **Java 21 (Temurin)**. Locally it is driven through the checked-in Maven
wrapper, so it does not depend on a Maven installed on PATH or bundled with an IDE:

```bash
JAVA_HOME=<path to a Temurin 21 JDK>
./mvnw -pl niord-core -am test
./mvnw -DskipTests -Dmaven.source.skip=true install
```

`niord-core` alone runs a few hundred tests. Rather more than half of them need the MySQL
container described below and skip themselves silently without it, so a run that finishes
suspiciously fast is a run that tested a fraction of what it looks like it did -- start the
container first and compare the totals if a number matters to you.

**CI does not use the wrapper.** Both pipelines provide their own Maven -- the Azure pipeline
unpacks a pinned distribution, the GitHub workflow uses the runner's. So the version in
`.mvn/wrapper/maven-wrapper.properties` governs local builds only, and a Maven-version difference
between a developer's machine and CI is possible rather than excluded. It has not caused a problem;
it is written down here so that "but the wrapper pins it" is not relied on when one appears.

Two notes on those flags, so they are not copied around without reason:

- `-Dmaven.source.skip=true` is needed only for **offline** builds. `maven-source-plugin` runs
  `attach-sources` on every `install`, and its own dependencies are typically not in a warm local
  repository.
- **Java 21 specifically.** Above 21, Quarkus augmentation in `niord-dk-web` fails when Byte Buddy
  refuses the newer class file version while enhancing entities. `-Dnet.bytebuddy.experimental=true`
  silences it, but that flag disables a safety check in the bytecode enhancer, which is the machinery
  behind lazy loading and dirty tracking. Pinning the JDK is the cheaper and safer of the two.

## Running the tests against MySQL

Tests that need a database talk to an externally-managed MySQL container -- not Dev Services, not
H2. The shared `hibernate_sequence` behaviour, the native `ENUM` columns and the spatial types are
exactly what those tests exist to exercise, and an in-memory substitute would quietly exercise none
of them. Keeping the container outside the build also means it stays warm between runs and its
schema survives for inspection after a failure.

```bash
docker run -d --name niord-test-db -p 13306:3306 \
  -e MYSQL_ROOT_PASSWORD=mysql -e MYSQL_DATABASE=niord \
  -e MYSQL_USER=niord -e MYSQL_PASSWORD=niord mysql:8.0.35
```

Seed it once with the committed baseline. Flyway then adopts that database at version 0 and
applies the migrations on top, which is exactly what happens on a deployed environment -- so the
tests exercise the real delivery path rather than a Hibernate-generated approximation of it:

```bash
docker exec -i niord-test-db mysql -uroot -pmysql niord \
  < niord-core/src/test/resources/schema/baseline-MaDaMe.sql
```

Hibernate schema generation is off in the tests (`generation=none`) for that reason: with both
active, whichever ran last would win and the migration would never be exercised.

Docker Desktop does not start at boot, so launch it first. `CoreQuarkusBootstrapTest` asserts the
server really reports MySQL 8.0.x, so it fails rather than passes if the container is down.

## Development Setup

To get started with developing Niord you need to check out the developer guide at 
http://docs.niord.org/dev-guide/guide.html.

## Country-specific Implementations

Country-specific implementations of the Niord system are easily created using a web-application overlay project.
Here additional code can be added and web resources (images, stylesheets, javascript files, etc) can be replaced with 
custom versions.

For an example, please refer to [niord-dk](https://github.com/NiordOrg/niord-dk) - a Danish implementation of Niord.

## Public API
A swagger definition of the public portion of the Rest API is published at https://niord.dma.dk/swagger/swagger-ui.html/.

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

