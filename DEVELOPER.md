# Developer documentation

This document summarizes information relevant to Java tally contributors.

## Making code changes

### Prerequisites
In order to build this project, you must have:
- JDK-7 or later
- Apache Thrift 0.9.x -- only if you plan to make changes to Thrift files and recompile (regenerate) source files

### Building and testing
Gradle is used to build tally. Run this from the top-level directory to build the project:
```bash
./gradlew clean build
```

To run the project's tests:
```bash
./gradlew check
```

To run the JMH benchmark tests:
```bash
./gradlew runJmhTests
```

By default, the benchmark test results are written to `benchmark-tests.txt`.
Use the `output` input parameter to configure the output path.
E.g. the following command will run the benchmark tests for `tally-prometheus` sub-project and store 
the results to `custom/path/result.txt`.  
```bash
./gradlew :tally-prometheus:runJmhTests -Poutput="custom/path/result.txt"
``` 

By default, the build does *not* compile Thrift files to generate sources. If you make changes to Thrift files and need
regenerate sources, make sure you have thrift 0.9.x installed and build with the `genThrift` property set, e.g.
```bash
./gradlew clean :tally-m3:build -PgenThrift
``` 

### Making changes
The source code of tally is managed using GitHub, and as such, we use its features to, for example,
track [issues](https://help.github.com/articles/about-issues/) and create
[pull requests](https://help.github.com/articles/creating-a-pull-request/). 

If you have not contributed to the project before, please add your details to the `developers`
section in the top-level [build file](build.gradle).

### Encrypting for Travis
In order to pass secrets to Travis securely for authentication and signing, we need to encrypt them
first before checking in. The full documentation [here](https://docs.travis-ci.com/user/encryption-keys/)
for encrypting keys, and [here](https://docs.travis-ci.com/user/encrypting-files/) for encrypting files.

These are the secrets that need to be passed:
1. [OSSRH](http://central.sonatype.org/pages/ossrh-guide.html) **username** and **password**. These are
   the credentials used to upload artifacts to the Sonatype Nexus Repository, which is used to sync to Maven Central.
2. Signing **key ID**, **password**, and **secret key ring file**. These three are used to sign artifacts that get
   created, which is a requirement in order to upload to Maven Central.

In order to pass these along, first login to Travis:
```bash
travis login
```

To set environment variables, run:
```bash
travis encrypt SOMEVAR="secretvalue"
```
and place the output inside [.travis.yml](.travis.yml) under this section:
```yaml
env:
  global:
```
The Gradle build file can then retrieve these environment variables as per usual via `System.getenv`.

Keep in mind that the Gradle signing plugin expects the signing details to be specifically named as per
their [documentation here](https://docs.gradle.org/current/userguide/signing_plugin.html#sec:signatory_credentials). 

To pass an encrypted file, first encrypt the file:
```bash
travis encrypt-file super_secret.txt
```

Then have Travis run the output from the above command during the build process, which will make Travis
decrypt the file for your usage.
