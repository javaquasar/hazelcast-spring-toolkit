# Performance Notes

This document captures local performance characterization results for the
Hibernate L2 cache integration in `hazelcast-toolkit`.

These numbers are intended as a practical engineering reference, not as a
portable benchmark or SLA.

## Scope

The measurements below come from the integration-style test:

- `io.github.javaquasar.hazelcast.toolkit.boot2.Boot2HibernateL2PerformanceComparisonTest`

That test compares:

- `JCACHE`
- `HAZELCAST_LOCAL`
- both with and without client near-cache enabled

Each scenario measures:

- one cold read
- several warm reads
- observed Hibernate L2 hit counts during the measured phase

## Local Snapshot (April 17, 2026)

Five local runs produced the following averages:

| Mode | Near-cache | Avg cold read | Avg warm read | Avg L2 hits |
|---|---:|---:|---:|---:|
| `JCACHE` | `false` | `74.65 ms` | `1.878 ms` | `8` |
| `JCACHE` | `true` | `15.25 ms` | `1.722 ms` | `8` |
| `HAZELCAST_LOCAL` | `false` | `7.66 ms` | `0.733 ms` | `8` |
| `HAZELCAST_LOCAL` | `true` | `1.86 ms` | `0.646 ms` | `8` |

## Interpretation

- In this local Boot 2 / H2 setup, `HAZELCAST_LOCAL` was faster than `JCACHE`
  across the observed scenarios.
- Enabling client near-cache significantly improved cold reads for both modes.
- Warm reads stayed below `2 ms` on average in all four measured scenarios.

## Method Notes

- The measurements were taken from repeated local test runs on one machine.
- The results are sensitive to JVM warmup, host load, storage speed, network
  conditions, entity size, serialization cost, and cluster topology.
- Production behavior may differ substantially from these local figures.

## Reproducing

Run the characterization test directly:

```bash
./gradlew :toolkit-spring-boot2:test --tests "io.github.javaquasar.hazelcast.toolkit.boot2.Boot2HibernateL2PerformanceComparisonTest"
```

For a more stable local picture, rerun it multiple times and aggregate the
reported `PERF_RESULT` lines.
