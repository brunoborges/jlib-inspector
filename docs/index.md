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
		<a class="btn" href="{{ site.baseurl }}/latest.html">Latest Release {{ site.latest_release }}</a>
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

<figure class="architecture" aria-labelledby="architecture">
	<ol role="list">
		<li>
			<svg aria-hidden="true" viewBox="0 0 24 24">
				<rect x="5" y="5" width="14" height="14" rx="2"/>
				<path d="M9 2v3m6-3v3M9 19v3m6-3v3M2 9h3m-3 6h3m14-6h3m-3 6h3M8 12h2l1-3 2 6 1-3h2"/>
			</svg>
			<h3>JVM + Agent</h3>
			<p>The agent sends inventory snapshots and coalesced load events to the server.</p>
		</li>
		<li>
			<svg aria-hidden="true" viewBox="0 0 24 24">
				<rect x="3" y="3" width="18" height="7" rx="2"/>
				<rect x="3" y="14" width="18" height="7" rx="2"/>
				<path d="M7 6.5h1m-1 11h1m5-11h4m-4 11h4"/>
			</svg>
			<h3>Inspector Server</h3>
			<p>The server makes each application's JAR inventory available through its REST API.</p>
		</li>
		<li>
			<svg aria-hidden="true" viewBox="0 0 24 24">
				<rect x="2" y="3" width="20" height="15" rx="2"/>
				<path d="M2 8h20M9 22h6m-3-4v4"/>
			</svg>
			<h3>UI &amp; Integrations</h3>
			<p>Explore loaded JARs in the dashboard or query the same inventory from your own tools.</p>
		</li>
	</ol>
	<figcaption><strong>Inventory metadata</strong> Timestamps, version heuristics, and optional JAR hashes.</figcaption>
</figure>

Key principles:

- Passive & low overhead: no bytecode weaving required.
- Leverages existing, official APIs in Java SE (Instrumentation).
- Extensible: optional OpenTelemetry event export, with more exporters (e.g. CycloneDX) planned.

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
Download <code>jlib-inspector-agent-{{ site.latest_release }}-shaded.jar</code> from the <a href="https://github.com/{{ site.repository }}/releases/tag/v{{ site.latest_release }}">latest release</a>, then run your JVM process pointing the agent at the running server:
<pre><code>java \
  -javaagent:/absolute/path/to/jlib-inspector-agent-{{ site.latest_release }}-shaded.jar=server:8080,interval:30s \
  -jar your-app.jar</code></pre>
With <code>interval:30s</code>, it appears in the UI within 30 seconds and stays up to date while it runs. Without it, the agent reports once, when the JVM exits.
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

## OpenTelemetry Export {#opentelemetry}

Already use OpenTelemetry? Add the `otel` option and the agent sends its JAR inventory to your OTLP/HTTP collector when the JVM shuts down. Add `interval:5m` (or any `s`/`m`/`h` duration) to also export while the app runs; after the first export, only new archives and archives that became loaded are sent. It sends one `package.info` event per archive, with the same attributes (`package.name`, `package.version`, `package.checksum`, …) as the OpenTelemetry Java agent's experimental package emitter. It also adds JARs that were declared but never loaded, and JARs nested in fat JARs.

<pre><code>java \
  -javaagent:/absolute/path/to/jlib-inspector-agent-{{ site.latest_release }}-shaded.jar=server:8080,otel:http://collector:4318,interval:5m \
  -jar your-app.jar</code></pre>

Use `otel` with no endpoint to rely on `OTEL_EXPORTER_OTLP_ENDPOINT` (default `http://localhost:4318`). `OTEL_SERVICE_NAME`, `OTEL_RESOURCE_ATTRIBUTES` and `OTEL_EXPORTER_OTLP_HEADERS` are also honored. See the [README](https://github.com/{{ site.repository }}#opentelemetry-export-optional) for the full attribute list.

## Roadmap (Snapshot)

- OpenTelemetry spans / events for classload anomalies
- CycloneDX runtime delta export
- Historical diff view across deploys
- CLI summarizer for CI gating (fail on growth of unused libs)

## Contributing

Issues & PRs welcome. Try to reproduce with the sample app first; include JVM version & environment details for runtime discrepancies.

---

<p class="small">Toggle theme with the moon icon in the nav. Screenshots open in a lightbox; press ESC or click × to dismiss.</p>
