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
    implant-list         List all bundled implants.
    implant-info         See more details  about  a  specific implant. This
                         includes reading its  class  to  see all available
                         configuration properties and  their  data types. A
                         class file path can be  specified to read a custom
                         implant.

named arguments:
  -h, --help             show this help message and exit

for more options, see command help pages:
  $ java -jar jarplant.jar class-injector -h
  $ java -jar jarplant.jar spring-injector -h
    ...

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
* **test-app-pojo** is a very minimal plain Java app that can be used for test the class implants.
* **test-app-spring-simple** is a simple Spring Boot application for testing the Spring implants.
  It's just a `@SpringBootApplication` with one configuration and controller.
* **test-app-spring-complex** tries to simulate a more intricate Spring app with several `@Configuration` classes in
  different sub-packages. It includes configurations without component scanning enabled, several configurations in
  the same package and other exotic cases worth testing for.

## Test suite

Testing JAR and bytecode manipulation can be a bit tricky. Please try to include any bug or corner case into its own
Junit test. Don't be afraid of adding to the test apps and test implants, just don't break any other tests in the
process. Add a new submodule with a test app/implant that narrows in on the test case if necessary. *Don't* check in
a blob like a JAR file or anything. Any test apps needs to be provided by source and pom. Try to keep it to the point.

### Test automation

Most tests reside in `jarplant-lib/src/test` that uses a combination of dummy classes and "live samples" from the other
Maven submodules. These submodules are set up to build a proper JAR file and then copy it into the resource folder of
the tests. See their `pom.xml` files. It's a bit out of the ordinary and may generate some warnings in Maven. Just make
sure to run `mvn package`
in the project root before running any tests in isolation. If you change anything in the test apps or test implants,
then make sure to run `mvn clean` before `mvn package` (or just `mvn clean package` in one go).

The tests in `jarplant-lib` are a mix of unit tests and end-to-end tests.

### Manual testing and troubleshooting

When developing JarPlant or its implants, it's been very useful to use the `javap` tool provided with the JVM.

Example:

```
mkdir /tmp/jarplant-debug
cd /tmp/jarplant-debug
unzip ../path/to/jarfile/my-app.jar

javap -c -v org/example/target/Main.class | less
```

Replace directories and paths as appropriate. The key here is the `javap` command. It's great for disassembling and
peaking into the JVM bytecode. Do this before and after a JarPlant run and investigate the diffs.

Consider to use the manual testing procedure as a tool to narrow down on a bug and then express that bug as a Junit
test. That way, it's easy for someone to fix the bug by satisfying the test. Alternatively, just fix the bug and create
a PL. You can always just create a GitHub Issue to explain the problem if you're unable to express it as a test or fix
it yourself. A reported Issue is better than nothing.
