#!/usr/bin/env bash
# Generate coverage report
./gradlew jacocoRootReport coveralls

# Publish Javadoc
git checkout --orphan gh-pages
git reset
cp -r build/docs/javadoc/ docs
git clean -fdxe docs/*
git add -A
git commit -m "Update Javadoc"
git push -f origin gh-pages
