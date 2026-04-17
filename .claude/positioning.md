# Positioning

## Project Overview

`hazelcast-toolkit` is a modern, annotation-driven open-source Java 17 library for
Hazelcast client integration in Spring Boot applications.

Core value proposition:
Provide the most developer-friendly way to work with Hazelcast compact serialization,
IMap listeners, and JCache / Hibernate L2 cache with minimal boilerplate.

## Target Audience

Backend developers who want useful Hazelcast features without writing a large amount
of manual `ClientConfig`, listener registration, or Hibernate cache plumbing.

## Positioning Statement

> The most developer-friendly annotation-driven Hazelcast client toolkit for Spring Boot applications.

## Main Competitors

| Library / Project | Type | Strengths | Weaknesses vs this project |
|---|---|---|---|
| Spring Boot official Hazelcast support | Official | Simple, official, familiar | No `@HzCompact`, no `@HzIMapListener`, weaker client-focused ergonomics |
| `all-things-dev/hazelcast-spring-boot-starters` | Community starter | Clean property-based config | No annotation-first compact/listener story |
| Hazelcast official Spring integration | Official | Mature, broad integration | More XML-heavy and less convenient for modern client usage |
| Hazelcast Hibernate integration | Official | Good L2 support | Narrower scope, less client/developer-experience focus |

## Unique Strengths

- Annotation-first developer experience with `@HzCompact` and `@HzIMapListener`
- Support for both reflective compact registration and explicit `CompactSerializer`
- Automatic listener registration with Spring proxy awareness
- Focus on real production pain points, especially Hibernate L2 integration
- Clean multi-module architecture
- Strong release and testing discipline for a community library

## Current Weaknesses

- Limited property-driven configuration compared with more configuration-heavy starters
- No small example application yet
- Boot 4 support still incomplete
- Observability capabilities are not yet a strong outward-facing selling point

## Messaging Guidance

When describing the project publicly, emphasize:

- zero-boilerplate compact serialization registration
- auto-wired IMap listeners on Spring beans
- pragmatic Hibernate L2 integration with conservative defaults
- modern client-focused Hazelcast usage rather than embedded-server defaults

Avoid over-claiming:

- do not present Boot 4 support as fully complete yet
- do not imply the toolkit replaces every Hazelcast integration style
- do not frame internal documentation improvements as product features
