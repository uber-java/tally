#!/usr/bin/env bash

if [ $TRAVIS_JDK_VERSION == "openjdk7" ]; then
    # Compile using JDK8, but continue the rest of the build using JDK7
    # due to openjdk7 issue:
    # https://github.com/gradle/gradle/issues/2421
    ./jdk_switcher.sh use openjdk8
    ./gradlew assemble
    ./jdk_switcher.sh use openjdk7
else
    ./gradlew assemble
fi
