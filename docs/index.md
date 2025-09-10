---
layout: default
title: JLib Inspector
---

# JLib Inspector

> **⚠️ EXPERIMENTAL - NOT PRODUCTION READY**  
> This project is currently in experimental/development phase and is **not suitable for production environments**. Use for development, testing, and evaluation purposes only.

<!-- BADGES -->
[![CI/CD Pipeline](https://github.com/brunoborges/jlib-inspector/actions/workflows/ci.yml/badge.svg)](https://github.com/brunoborges/jlib-inspector/actions/workflows/ci.yml)
[![Security & Maintenance](https://github.com/brunoborges/jlib-inspector/actions/workflows/security.yml/badge.svg)](https://github.com/brunoborges/jlib-inspector/actions/workflows/security.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.java.net/)
[![Node.js](https://img.shields.io/badge/Node.js-18%2B-green.svg)](https://nodejs.org/)
<!-- /BADGES -->

A comprehensive Java application monitoring dashboard that tracks JAR dependencies loaded during application runtime. The system consists of a Java agent, a standalone data collection server, and a modern React-based web dashboard to exemplify data visualization.

## 🎯 Why JLib Inspector?

When security vulnerabilities like **Log4Shell** strike, organizations face a critical challenge: **identifying which production systems are affected**. During the Log4j vulnerability crisis, many teams struggled with fundamental questions:

- *"Which of our production applications use Log4j?"*
- *"What version are they running?"*
- *"Are there transitive dependencies we don't know about?"*
- *"How can we quickly assess our exposure across hundreds of services?"*

**JLib Inspector Enables Proactive Dependency Visibility:**
- 🚀 **Real-time monitoring** of actual JAR files loaded by running JVMs
- 🎯 **Complete visibility** including transitive dependencies
- 📊 **Centralized dashboard** showing all applications and their dependencies
- 🔄 **Continuous tracking** of what's actually running vs. what's deployed
- ⚡ **Instant response** capability when new vulnerabilities are disclosed
- 🛡️ **Proactive security posture** instead of reactive emergency responses

**Production Reality Check:**
Unlike static analysis of build files, JLib Inspector shows you **exactly what JARs are loaded at runtime** - capturing the full picture including:
- JARs loaded dynamically through plugins or extensions
- Nested JARs within fat/uber JARs  
- Platform-specific dependencies loaded conditionally
- The actual classpath used by the running JVM

When the next security vulnerability emerges, you'll have immediate answers instead of emergency archaeology.

## 🏗️ Architecture

- **Java Agent**: Instruments Java applications to track JAR loading and usage (shaded agent JAR)
- **JLib Server**: Standalone server that collects and aggregates data from instrumented applications (Port 8080, shaded server JAR)
- **Web Dashboard**: React-based frontend with real-time updates (Ports 3000 for http and 3001 for websocket)

## 🎯 Key Features

### For Developers
- **Real-time JAR tracking** - See which dependencies are actually used
- **Performance insights** - Identify unused JARs for optimization
- **Dependency analysis** - Complete visibility into application dependencies
- **Command line monitoring** - Full startup command with arguments

### For Operations
- **Live monitoring** - Real-time view of all Java applications
- **Health checking** - Monitor application and server status
- **Resource tracking** - JAR file sizes and checksums
- **Historical data** - First seen and last updated timestamps

## 📁 Project Structure

```
jlib-inspector/
├── agent/                # Java Agent module (produces shaded agent JAR)
├── common/               # Shared code used by agent and server
├── server/               # Standalone JLib Server module (produces shaded server JAR)
├── sample-spring-app/    # Sample Spring Boot application
├── frontend/             # React dashboard (Express-based)
├── docker/               # Dockerfiles, compose, helper script
└── site/                 # GitHub Pages documentation (this site)
```

## 📋 Prerequisites

- **Java 21+** (JDK)
- **Maven 4+** (use the included wrapper `./mvnw`)
- **Node.js 18+** and **npm**

## 🚀 Quick Start

Ready to get started? Follow our [Getting Started Guide](getting-started.html) for step-by-step instructions.

## 📖 Documentation

- [Getting Started](getting-started.html) - Build, run, and test the project
- [Agent Documentation](agent.html) - How to use the Java agent
- [Server Documentation](server.html) - JLib Server setup and configuration
- [Screenshots](screenshots.html) - Visual overview of the dashboard
- [Javadoc](javadoc/index.html) - Complete API documentation

## 🤝 Contributing

1. Build the project: `./mvnw verify`
2. Run tests: `./mvnw test`
3. Start development environment following the Getting Started guide
4. Make changes and test with sample applications

## 📄 License

MIT License - see [LICENSE]({{ site.github.repository_url }}/blob/main/LICENSE) file for details.

## 🔗 Links

- [Source Code]({{ site.github.repository_url }})
- [Issues]({{ site.github.repository_url }}/issues)
- [Releases]({{ site.github.repository_url }}/releases)
- [CI/CD Pipeline]({{ site.github.repository_url }}/actions)