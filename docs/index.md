---
layout: default
title: JLib Inspector
---

<section class="hero">
	<h1>Runtime Library Intelligence<br/>for Production JVMs</h1>
	<p class="tagline">Stop guessing which dependencies your services *really* use. JLib Inspector captures a precise, low‑overhead inventory of loaded JARs & classes so you can shrink images, prioritize CVE fixes, and eliminate dependency drift.</p>
	<div class="cta-row">
		<a class="btn accent" href="#quick-start">Get Started</a>
		<a class="btn" href="https://github.com/{{ site.repository }}">GitHub</a>
	</div>
</section>

## Why It Matters {#why}

<div class="feature-grid">
	<div class="feature"><h3>Trim Bloat</h3><p>Identify JARs never loaded in production to reduce image size and attack surface, and speed up startup.</p></div>
	<div class="feature"><h3>Prioritize CVEs</h3><p>Focus remediation on libraries actually resident in memory, not just declared.</p></div>
	<div class="feature"><h3>SBOM Reality Check</h3><p>Compare runtime inventory with build‑time SBOM to catch drift & shading surprises.</p></div>
	<div class="feature"><h3>Audit Evidence</h3><p>Produce timestamped runtime snapshots for compliance & forensic review.</p></div>
</div>

## Architecture (High Level) {#architecture}

<div class="diagram">[ JVM + Agent ] -- snapshots --> [ Inspector Server ] -- REST --> [ UI / Integrations ]
			|                              |
			+-- load events (coalesced) -- +
Hashing (optional) · Version heuristics · Timestamping
</div>

Key principles:

- Passive & low overhead: no bytecode weaving required.
- Leverages existing, official APIs in Java SE (Instrumentation).
- Extensible: future exporters (CycloneDX, OpenTelemetry events, etc.).

## Data Captured

| Dimension         | Notes                                                    |
| ----------------- | -------------------------------------------------------- |
| JARs in Classpath | Shows JARs that may have not been loaded                 |
| Loaded JARs       | List of JARs actually loaded by classloaders             | 
| JAR Path          | Full path on disk (if available)                         |
| Nested JARs       | Supports Spring Boot, One-JAR, etc.                      |
| JAR Manifest      | Extracts content from the file MANIFEST.mf in each JAR.  |
| JAR Hash          | SHA-256 for integrity / SBOM correlation.                |

## Quick Start (Docker Desktop) {#quick-start}

You can get everything (server, frontend, sample app with agent) running with a single command using the provided Compose setup.

<div>
<strong>1. Clone & Launch</strong>
<pre><code>
git clone https://github.com/{{ site.repository }}.git
cd jlib-inspector/docker
./start-docker.sh</code></pre>

This builds the Java modules, starts the Inspector server (port 8080), the frontend UI (port 3000), WebSocket (3001), and a launches a sample Spring app instrumented with the agent to push data to the server.

</div>
<div>

<strong>2. Explore</strong>

<pre><code>
# Open the UI
http://localhost:3000

# Check server health
curl -s http://localhost:8080/health

# List registered apps
curl -s http://localhost:8080/api/apps | jq
</code></pre>

</div>
<div>
<strong>3. Add Your App</strong>
<br/>
Run your JVM process pointing the agent at the running server:
<pre><code>java 
  -javaagent:/absolute/path/to/agent/ \
  jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar=server:8080 \
  -jar your-app.jar</code></pre>
It will appear in the UI within seconds when classes start loading.
</div>

### Optional: Rebuild After Code Changes

Inside the repo root:

```
docker compose down
docker compose up --build
```

## UI Screenshots

<div class="gallery" data-gallery>
	<figure>
		<img src="{{ site.baseurl }}/assets/images/screenshot1.png" alt="Inventory of Applications" data-full="{{ site.baseurl }}/assets/images/screenshot1.png"/>
		<figcaption>Applications</figcaption>
	</figure>
	<figure>
		<img src="{{ site.baseurl }}/assets/images/screenshot2.png" alt="Inventory of JARs" data-full="{{ site.baseurl }}/assets/images/screenshot2.png"/>
		<figcaption>JARs</figcaption>
	</figure>
</div>

## Roadmap (Snapshot)

- OpenTelemetry exporter (spans / events for classload anomalies)
- CycloneDX runtime delta export
- Historical diff view across deploys
- CLI summarizer for CI gating (fail on growth of unused libs)

## Contributing

Issues & PRs welcome. Try to reproduce with the sample app first; include JVM version & environment details for runtime discrepancies.

---

<p class="small">Toggle theme with the moon icon in the nav. Screenshots open in a lightbox; press ESC or click × to dismiss.</p>
