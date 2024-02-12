# Java Archive Implant Toolkit

Inject malicious payloads into JAR files.

## Quickstart

Installation:

```
git clone git@github.com:w1th4d/JarPlant.git
cd JarPlant
mvn package
mv jarplant-cli/target/jarplant-cli-<version>-jar-with-dependencies.jar jarplant.jar
```

Make sure to substitute `<version>` with the current version.

Usage:

```
usage: java -jar jarplant.jar [-h] command ...

       _____               ______  __                __   
     _|     |.---.-..----.|   __ \|  |.---.-..-----.|  |_ 
    |       ||  _  ||   _||    __/|  ||  _  ||     ||   _|
    |_______||___._||__|  |___|   |__||___._||__|__||____|
    Java archive implant toolkit   v0.1   by w1th4d & kugg

positional arguments:
  command
    class-injector       Inject a class implant  into  any JAR. The implant
                         will detonate whenever  any  class  in  the JAR is
                         used but  the  payload  will  only  run  once  (or
                         possibly twice in  some  very  fringe cases). This
                         is the most versatile implant  type and works with
                         any JAR (even ones  without  a main function, like
                         a library).
    spring-injector      Inject  a  Spring  component   implant  into  JAR-
                         packaged Spring  application.  The  component will
                         be loaded and included in  the Spring context. The
                         component could be  something  like  an extra REST
                         controller or scheduled task.

named arguments:
  -h, --help             show this help message and exit

for more options, see command help pages:
  $ java -jar jarplant.jar class-injector -h
  $ java -jar jarplant.jar spring-injector -h

example usage:
  $ java -jar jarplant.jar class-injector \
    --target path/to/target.jar --output spiked-target.jar
```

## Injectors

### ClassInjector

Operates upon any JAR file containing arbitrary classes.
The JAR does _not_ have to be executable or contain a `main()` function.
Any class will do. This is great for libraries and dependencies.

When the target JAR is run or used by another app, the implant will trigger.
Info exfil? Reverse shell? Full-fledged C2? Virtually anything goes with this implant type.

### SpringInjector

Specifically looks for a Spring configuration class and injects a Spring component in the same package namespace.
If _component scanning_ is not enabled by the target app, then the SpringInjector will also inject a `@Bean`-annotated
method in the configuration in order to reference the implanted component.

The accompanying SpringImplant will register a new HTTP request mapping for the app.
Requests going to that endpoint (`/implant` by default) will be handled by the implant.

## Maven modules

This project is divided into a set of Maven modules:

* **jarplant-cli** is where the executable main function is. It gives the user a Command Line Interface to use the
  JarPlant functionality in a user-friendly way.
* **jarplant-implants** is where the various example implants are located. A savvy user is encouraged to write custom
  implants as appropriate.
* **jarplant-lib** is where the main functionality is. This module is designed to contain only the essential
  functionality for portability.
* **target-app** is a very minimal Java app that can be used for test the implants.
* **target-app-spring-boot** is a Spring Boot application for testing implants. This one is particularly handy for
  testing the Spring implant(s).

