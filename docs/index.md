---
layout: default
title: JLib Inspector
---

<section class="hero">
	<h1>Runtime Library Intelligence<br/>for Production JVMs</h1>
	<p class="tagline">Stop guessing which dependencies your services *really* use. JLib Inspector captures a precise, low‑overhead inventory of loaded JARs & classes so you can shrink images, prioritize CVE fixes, and eliminate dependency drift.</p>
	<div class="cta-row">
		<a class="btn accent" href="#quick-start">Get Started ▸</a>
		<a class="btn" href="{{ site.baseurl }}/javadoc/">API Javadoc</a>
		<a class="btn" href="https://github.com/{{ site.repository }}">GitHub</a>
	</div>
</section>

## Why It Matters {#why}

<div class="feature-grid">
	<div class="feature"><h3>Trim Bloat</h3><p>Identify JARs never loaded under production traffic to reduce image size & attack surface.</p></div>
	<div class="feature"><h3>Prioritize CVEs</h3><p>Focus remediation on libraries actually resident in memory, not just declared.</p></div>
	<div class="feature"><h3>Resolve Conflicts</h3><p>See which versions the class loader picks when multiple appear on the path.</p></div>
	<div class="feature"><h3>SBOM Reality Check</h3><p>Compare runtime inventory with build‑time SBOM to catch drift & shading surprises.</p></div>
	<div class="feature"><h3>Audit Evidence</h3><p>Produce timestamped runtime snapshots for compliance & forensic review.</p></div>
	<div class="feature"><h3>Cost & Cold Starts</h3><p>Remove dead dependencies to speed startup & reduce memory usage.</p></div>
</div>

## Architecture (High Level) {#architecture}

<div class="diagram">[ JVM + Agent ] -- snapshots --> [ Inspector Server ] -- REST --> [ UI / Integrations ]
			|                              |
			+-- load events (coalesced) -- +
Hashing (optional) · Version heuristics · Timestamping
</div>

Key principles:
* Passive & low overhead: no bytecode weaving required.
* Data minimization: snapshots & diffing to keep payloads small.
* Extensible: future exporters (CycloneDX, OpenTelemetry events, etc.).

## Data Captured

| Dimension | Notes |
|-----------|-------|
| Artifact (GAV) | Derivation via MANIFEST / path heuristics |
| JAR Hash | SHA-256 for integrity / SBOM correlation |
| Classes Loaded | Set + (optional) load counts |
| First / Last Seen | Wall clock timestamps |
| Source Path | Original JAR / shaded container |

## Quick Start {#quick-start}

<div class="two-col">
<div>
<strong>1. Build & Start Server</strong>
<pre><code>git clone https://github.com/{{ site.repository }}.git
cd jlib-inspector
./mvnw -q -DskipTests install
java -jar server/target/jlib-inspector-server-1.0-SNAPSHOT.jar 8080</code></pre>
</div>
<div>
<strong>2. Launch Your App With Agent</strong>
<pre><code>java -javaagent:agent/target/\
jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar=localhost:8080 \
-jar your-app.jar</code></pre>
</div>
<div>
<strong>3. (Optional) UI Frontend</strong>
<pre><code>cd frontend
npm install
npm start   # http://localhost:3000</code></pre>
</div>
<div>
<strong>4. Query REST API</strong>
<pre><code>curl -s http://localhost:8080/api/apps | jq
curl -s http://localhost:8080/api/apps/yourAppId | jq</code></pre>
</div>
</div>

## Javadoc & Integration

Browse the <a href="{{ site.baseurl }}/javadoc/">API docs</a>. Javadoc is rebuilt automatically from `main` on each push.

## Roadmap (Snapshot)

- OpenTelemetry exporter (spans / events for classload anomalies)
- CycloneDX runtime delta export
- Historical diff view across deploys
- CLI summarizer for CI gating (fail on growth of unused libs)

## Contributing

Issues & PRs welcome. Try to reproduce with the sample app first; include JVM version & environment details for runtime discrepancies.

---

<p class="small">Toggle theme with the moon icon in the nav. All static assets are served via GitHub Pages.</p>
