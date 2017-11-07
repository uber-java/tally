#!/usr/bin/env bash
# Generate coverage report
./gradlew jacocoRootReport coveralls

# Tasks to do only on an actual release
if [ "$TRAVIS_JDK_VERSION" == "openjdk7" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_BRANCH" == "master" ]; then
    # Publish Javadoc
    git checkout --orphan gh-pages
    git reset
    cp -r build/docs/javadoc/ docs
    git clean -fdxe /docs
    git add -A
    git commit -m "Update Javadoc"
    git push -f https://${GITHUB_TOKEN}@github.com/uber-java/tally.git gh-pages

    # Upload artifacts and release to Sonatype to be synced to Maven Central
   ./gradlew uploadArchives closeAndReleaseRepository
fi
