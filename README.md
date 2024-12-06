# :heavy_check_mark: tally [![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov] [![Maven Central][maven-img]][maven]

Fast, buffered, hierarchical stats collection in Java. [Go here](https://github.com/uber-go/tally) for the Go client.

## Abstract

Tally provides a common interface for emitting metrics, while letting you not worry about the velocity of metrics
emission.

By default, it buffers counters, gauges and histograms at a specified interval but does not buffer timer values. This is
primarily so timer values can have all their values sampled if desired and if not they can be sampled as summaries or
histograms independently by a reporter.

## Structure

- **Scope**: Keeps track of metrics, and their common metadata.
- **Metrics**: Counters, Gauges, Timers and Histograms.
- **Reporter**: Implemented by you. Accepts aggregated values from the scope. Forwards the aggregated values to your metrics ingestion pipeline.

### Create a `Scope`

```java
// Implement as you will
StatsReporter reporter = new MyStatsReporter();

Map<String, String> tags = new HashMap<>(2, 1);
tags.put("dc", "east-1");
tags.put("type","leader");

Scope scope = new RootScopeBuilder()
    .reporter(reporter)
    .tags(tags)
    .reportEvery(Duration.ofSeconds(1));
```

### Get/Create a metric; use it
```java
Counter reqCounter = scope.counter("requests");
reqCounter.inc(1);

Gauge queueGauge = scope.gauge("queue_length");
queueGauge.update(42);
```

### Report your metrics

Use one of the inbuilt reporters or implement your own using the `StatsReporter` interface.

## Usage examples

Run the example by running:
```bash
$ ./gradlew run
```

This runs the `PrintStatsReporterExample` class in the `tally-example` project.

## Artifacts publishing

All artifacts are published under the group `com.uber.m3`.

1. `tally-m3`: The tally M3 reporter.
1. `tally-statsd`: The tally StatsD reporter.
1. `tally-core`: The tally core functionality that includes interfaces and utilities to report metrics to M3.
1. `tally-example`: Usage examples with different reporters.
1. `tally-prometheus`: The tally Prometheus reporter (experimental; see prometheus/README.md).

## Versioning

We follow semantic versioning outlined [here](http://semver.org/spec/v2.0.0.html). In summary, given a version of
MAJOR.MINOR.PATCH (e.g. 1.2.0):

- MAJOR version changes are breaking changes to the public API
- MINOR version changes are backwards-compatible changes that include new functionality
- PATCH version changes are backwards-compatible bug fixes
<hr>

Released under the [MIT License](LICENSE.md).

[ci-img]: https://travis-ci.org/uber-java/tally.svg?branch=master
[ci]: https://travis-ci.org/uber-java/tally
[cov-img]: https://coveralls.io/repos/github/uber-java/tally/badge.svg?branch=master
[cov]: https://coveralls.io/github/uber-java/tally?branch=master
[maven-img]: https://maven-badges.herokuapp.com/maven-central/com.uber.m3/tally-m3/badge.svg
[maven]: https://maven-badges.herokuapp.com/maven-central/com.uber.m3/tally-m3
