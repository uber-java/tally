# Developer documentation

This document summarizes information relevant to Java tally contributors.

## Making code changes

### Prerequisites
In order to build this project, you must have:
- JDK-7 or later
- Gradle 3.3 or later

### Building and testing
Gradle is used to build tally. Run this from the top-level directory to build the project:
```sh
./gradlew clean build
```

To run the project's tests:
```sh
./gradlew check
```

### Making changes
The source code of tally is managed using GitHub, and as such, we use its features to, for example,
track [issues](https://help.github.com/articles/about-issues/) and create
[pull requests](https://help.github.com/articles/creating-a-pull-request/). 

If you have not contributed to the project before, please add your details to the `developers`
section in the top-level [build file](build.gradle).
