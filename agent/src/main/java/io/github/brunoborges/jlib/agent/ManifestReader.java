package io.github.brunoborges.jlib.agent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Utility for reading and parsing MANIFEST.MF attributes from a class'
 * {@link ProtectionDomain}.
 * <p>
 * Supports:
 * <ul>
 * <li>Regular JARs (file:...jar)</li>
 * <li>Nested JAR style locations (file:outer.jar!/BOOT-INF/lib/inner.jar)</li>
 * <li>"jar:nested:" custom Spring Boot loader style URLs</li>
 * <li>Exploded directories (file:/path/to/classes/)</li>
 * </ul>
 * Returns an empty map if the manifest cannot be read. All logging is at
 * FINE/INFO
 * to avoid noisy output while still providing traceability when enabled.
 */
final class ManifestReader {

    private static final Logger LOG = Logger.getLogger(ManifestReader.class.getName());

    private ManifestReader() {
    }

    /**
     * Reads and parses manifest attributes from the given ProtectionDomain.
     * The returned map is mutable (caller may copy or wrap if immutability is
     * desired).
     *
     * @param pd protection domain
     * @return map of manifest main section (and any subsequent sections)
     *         attributes; empty if not available
     */
    static Map<String, String> read(ProtectionDomain pd) {
        String stage = "start";
        boolean manifestRead = false;
        String locStr = null;
        Map<String, String> attrs = new HashMap<>();
        try {
            if (pd == null) {
                LOG.fine(msg(stage, "pd null"));
                return attrs;
            }
            stage = "codesource";
            CodeSource cs = pd.getCodeSource();
            if (cs == null) {
                LOG.fine(msg(stage, "CodeSource null"));
                return attrs;
            }
            stage = "location";
            URL loc = cs.getLocation();
            if (loc == null) {
                LOG.fine(msg(stage, "location null"));
                return attrs;
            }
            locStr = loc.toString();
            stage = "scheme-eval";
            LOG.fine("Attempting MANIFEST read; stage=" + stage + ", location=" + locStr);

            URL manifestUrl = null;

            if (locStr.startsWith("jar:nested:")) {
                stage = "jar-nested-primary";
                String base = ensureEndsWithBangSlash(locStr);
                try {
                    manifestUrl = URI.create(base + "META-INF/MANIFEST.MF").toURL();
                    LOG.fine("jar:nested primary manifest URL: " + manifestUrl);
                } catch (Exception e) {
                    LOG.fine("Primary jar:nested manifest URL build failed: " + e.getMessage());
                }
                if (manifestUrl == null) {
                    stage = "jar-nested-fallback-normalize";
                    String normalized = normalizeJarNestedUrl(locStr); // file:/outer.jar!/BOOT-INF/lib/inner.jar
                    try {
                        manifestUrl = URI.create("jar:" + normalized + "!/META-INF/MANIFEST.MF").toURL();
                        LOG.fine("jar:nested fallback manifest URL: " + manifestUrl);
                    } catch (Exception e2) {
                        LOG.fine("Fallback jar:nested manifest URL build failed: " + e2.getMessage());
                    }
                }
            }

            if (manifestUrl == null) {
                if (locStr.startsWith("file:") && (locStr.endsWith(".jar") || locStr.contains("!/"))) {
                    stage = "jar-url-build";
                    String base = locStr.endsWith("!/") ? locStr.substring(0, locStr.length() - 2) : locStr;
                    manifestUrl = URI.create("jar:" + base + "!/META-INF/MANIFEST.MF").toURL();
                } else if (locStr.startsWith("file:") && locStr.endsWith("/")) {
                    stage = "dir-url-build";
                    manifestUrl = loc.toURI().resolve("META-INF/MANIFEST.MF").toURL();
                } else {
                    stage = "unsupported-scheme";
                    LOG.fine("Skipping MANIFEST read (stage=" + stage + ") for location=" + locStr);
                    return attrs;
                }
            }

            stage = "open-stream";
            LOG.fine("Resolved MANIFEST URL (stage=" + stage + "): " + manifestUrl);
            InputStream is = manifestUrl.openStream();
            LOG.fine("Opened MANIFEST stream (stage=" + stage + "): " + manifestUrl);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                stage = "parse";
                String line;
                String previousKey = null;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty()) { // Section boundary; continue scanning subsequent sections
                        previousKey = null;
                        attrs.put("---", "---"); // Marker for section boundary (empty key)
                        continue;
                    }
                    if (line.startsWith(" ") && previousKey != null) {
                        attrs.put(previousKey, attrs.get(previousKey) + line.substring(1));
                        continue;
                    }
                    int colon = line.indexOf(':');
                    if (colon > 0 && colon + 1 < line.length()) {
                        String key = line.substring(0, colon).trim();
                        String value = line.substring(colon + 1).trim();
                        attrs.put(key, value);
                        previousKey = key;
                    }
                }
                stage = "log";
                LOG.fine("Parsed " + attrs.size() + " manifest attributes for " + locStr + " (stage=" + stage + ")");
                manifestRead = true;
            }
        } catch (Exception e) {
            LOG.fine(
                    "Exception during manifest read (stage=" + stage + ", location=" + locStr + "): " + e.getMessage());
        } finally {
            if (!manifestRead) {
                String finalLoc = (locStr == null ? "<unknown>" : locStr);
                LOG.fine("Manifest not read; stopped at stage=" + stage + ", location=" + finalLoc);
            }
        }
        return attrs;
    }

    private static String ensureEndsWithBangSlash(String base) {
        if (!base.endsWith("!/")) {
            if (base.endsWith("!")) {
                base = base + "/";
            } else if (!base.endsWith("/")) {
                base = base + (base.contains("!") ? "/" : "!/");
            }
        }
        return base;
    }

    /**
     * Duplicate of logic in
     * {@code ClassLoaderTrackerTransformer#normalizeJarNestedUrl} to avoid tight
     * coupling.
     */
    private static String normalizeJarNestedUrl(String url) {
        String path = url.substring("jar:nested:".length());
        int bangIndex = path.indexOf("/!");
        if (bangIndex > 0) {
            String outerJar = path.substring(0, bangIndex);
            String innerPath = path.substring(bangIndex + 2);
            if (innerPath.endsWith("!/")) {
                innerPath = innerPath.substring(0, innerPath.length() - 2);
            }
            if (!outerJar.startsWith("file:")) {
                outerJar = "file:" + outerJar;
            }
            return outerJar + "!/" + innerPath;
        } else {
            if (!path.startsWith("file:")) {
                path = "file:" + path;
            }
            return path;
        }
    }

    private static String msg(String stage, String detail) {
        return "Manifest scan aborted (stage=" + stage + "): " + detail;
    }
}
