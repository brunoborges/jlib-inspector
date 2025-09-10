---
layout: default
title: JLib Inspector
---

> Minimal project site. Source code lives on the `main` branch.

## Overview

JLib Inspector provides a Java agent and backend that let you inspect which JARs and classes are *actually loaded* at runtime so you can trim unused dependencies, diagnose conflicts, and improve supply‑chain visibility.

## Links

* Project Repo: <https://github.com/brunoborges/jlib-inspector>
* API Javadoc: [Browse here]({{ site.baseurl }}/javadoc/)

## Quick Start (from main branch)

```
git checkout main
./mvnw -DskipTests install
java -jar server/target/jlib-inspector-server-1.0-SNAPSHOT.jar 8080 &
java -javaagent:agent/target/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar=localhost:8080 -jar sample-spring-app/target/sample-spring-app-1.0-SNAPSHOT.jar
```

Open the frontend (see README on main branch) to explore collected data.

## Javadoc

The workflow regenerates Javadoc on each push to `main` or `site`.
