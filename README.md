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
    method-injector      Copy a simple static method  into the target. This
                         injector only operates  on  a  class  (not a JAR).
                         The target  class  will  be  modified  to  run the
                         implanted  method  when   loaded.   This  injector
                         offers only a limited set  of  features but can be
                         more difficult to detect.
    class-injector       Inject a  class  implant  into  a  JAR  containing
                         regular classes. This  will  modify  *all* classes
                         in the JAR to  call  the implant's 'init()' method
                         when loaded.
    spring-injector      Inject a  Spring  component  into  a  JAR-packaged
                         Spring application. The  component  will be loaded
                         and included in the Spring context.

named arguments:
  -h, --help             show this help message and exit

for more options, see command help pages:
  $ java -jar jarplant.jar method-injector -h
  $ java -jar jarplant.jar class-injector -h
  $ java -jar jarplant.jar spring-injector -h

example usage:
  $ java -jar jarplant.jar class-injector \
    --target path/to/target.jar --output spiked-target.jar
```

## Injectors

### MethodInjector

The first PoC.
This injector copies a method into the target class.

This injector only requires a class file (not a JAR) but is limited in its language capabilities.
For instance, the implant method cannot call any other methods (in the same class) yet.

Since no additional class files are added, it may be more difficult to detect this implant.

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

