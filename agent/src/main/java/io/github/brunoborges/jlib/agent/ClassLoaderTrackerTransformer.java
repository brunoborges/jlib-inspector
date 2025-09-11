package io.github.brunoborges.jlib.agent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * A ClassFileTransformer that tracks class loading activity and identifies
 * which JAR files
 * are actually being used during application execution. This transformer
 * intercepts class
 * loading events and maps classes to their source JAR files, including support
 * for nested
 * JARs in Spring Boot fat JARs.
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Tracks ClassLoaders using weak references to avoid memory leaks</li>
 * <li>Handles Spring Boot nested JAR URLs (jar:nested: and file: schemes)</li>
 * <li>Normalizes JAR paths to match ClasspathJarTracker registration
 * format</li>
 * <li>Marks JARs as loaded in the central JarInventory when classes are loaded
 * from them</li>
 * <li>Provides detailed logging of class loading activity</li>
 * </ul>
 * 
 * <p>
 * This transformer does not modify bytecode - it always returns null to
 * preserve
 * the original class bytes.
 * 
 * @see JarInventory
 * @see ClasspathJarTracker
 */
public final class ClassLoaderTrackerTransformer implements ClassFileTransformer {

    private static final Logger LOG = Logger.getLogger(ClassLoaderTrackerTransformer.class.getName());

    /**
     * Track which code sources (jars/directories/modules) we've already announced
     * at INFO level
     */
    private static final Set<String> SEEN_SOURCES = ConcurrentHashMap.newKeySet();

    /** Weakly referenced ClassLoaders to avoid preventing garbage collection */
    private final Set<WeakReference<ClassLoader>> loaders;

    /** Central inventory for tracking JAR metadata and load states */
    private final JarInventory inventory;

    /**
     * Creates a new ClassLoaderTrackerTransformer.
     * 
     * @param loaders   Set to store weak references to encountered ClassLoaders
     * @param inventory Central JAR inventory for tracking load states and metadata
     */
    public ClassLoaderTrackerTransformer(Set<WeakReference<ClassLoader>> loaders, JarInventory inventory) {
        this.loaders = loaders;
        this.inventory = inventory;
    }

    /**
     * Intercepts class loading events to track JAR usage and ClassLoader instances.
     * 
     * <p>
     * For each class being loaded:
     * <ul>
     * <li>Records the ClassLoader (if non-null) using a weak reference</li>
     * <li>Identifies the source JAR/module using the ProtectionDomain</li>
     * <li>Logs the class loading event</li>
     * <li>Marks the source JAR as loaded in the inventory (first time only)</li>
     * <li>Cleans up cleared weak references</li>
     * </ul>
     * 
     * @param module              The module being loaded (Java 9+)
     * @param loader              The ClassLoader defining the class (may be null
     *                            for bootstrap)
     * @param className           The internal name of the class (e.g.,
     *                            "java/lang/String")
     * @param classBeingRedefined The class being redefined (null for new classes)
     * @param protectionDomain    Security context containing the code source
     *                            location
     * @param classfileBuffer     The original class bytecode
     * @return null (no bytecode transformation performed)
     * @throws IllegalClassFormatException Never thrown by this implementation
     */
    @Override
    public byte[] transform(Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) throws IllegalClassFormatException {

        // Track the ClassLoader using a weak reference to avoid memory leaks
        if (loader != null) {
            loaders.add(new WeakReference<>(loader));
        }

        final String source = sourceIdentifier(protectionDomain);

        // Mark source JAR as loaded and log first occurrence
        if (source != null && SEEN_SOURCES.add(source)) {
            inventory.markLoaded(source);
            LOG.info(() -> "Class Source " + source + " loaded");
            Map<String,String> manifest = readManifestAttributes(protectionDomain);
            if (manifest != null && !manifest.isEmpty()) {
                inventory.attachManifest(source, manifest);
            }
        } else if (source != null) {
            // Still mark as loaded in case JAR was registered after first class loading
            inventory.markLoaded(source);
        }

        cleanupCleared();
        return null; // No bytecode transformation
    }

    /**
     * Removes weak references to ClassLoaders that have been garbage collected.
     * This prevents memory leaks by cleaning up stale references periodically.
     */
    private void cleanupCleared() {
        Iterator<WeakReference<ClassLoader>> it = loaders.iterator();
        while (it.hasNext()) {
            if (it.next().get() == null) {
                it.remove();
            }
        }
    }

    /**
     * Extracts and normalizes the source identifier from a ProtectionDomain.
     * 
     * <p>
     * This method handles various URL schemes and formats to produce consistent
     * identifiers that match the format used by ClasspathJarTracker for
     * registration:
     * 
     * <ul>
     * <li><strong>jar:nested: URLs</strong> - Spring Boot nested JARs in fat
     * JARs</li>
     * <li><strong>file: URLs with /!</strong> - Alternative nested JAR format</li>
     * <li><strong>Regular JAR files</strong> - Standard file: URLs to .jar
     * files</li>
     * <li><strong>JRT modules</strong> - Java 9+ module system (jrt: scheme)</li>
     * <li><strong>Directories</strong> - Class folders with trailing slash
     * normalization</li>
     * </ul>
     * 
     * <h3>URL Format Examples:</h3>
     * 
     * <pre>
     * Input:  jar:nested:/path/app.jar/!BOOT-INF/lib/spring-boot.jar!/
     * Output: file:/path/app.jar!/BOOT-INF/lib/spring-boot.jar
     * 
     * Input:  file:/path/app.jar/!BOOT-INF/lib/spring-boot.jar!/
     * Output: file:/path/app.jar!/BOOT-INF/lib/spring-boot.jar
     * 
     * Input:  file:/path/library.jar!/com/example/
     * Output: file:/path/library.jar
     * 
     * Input:  jrt:/java.base
     * Output: jrt:/java.base
     * </pre>
     * 
     * @param pd The ProtectionDomain containing the CodeSource with location
     *           information
     * @return Normalized source identifier, or null if location cannot be
     *         determined
     */
    private String sourceIdentifier(ProtectionDomain pd) {
        if (pd == null)
            return null;
        CodeSource cs = pd.getCodeSource();
        if (cs == null)
            return null;
        URL loc = cs.getLocation();
        if (loc == null)
            return null;
        String url = loc.toString();

        // Handle jar:nested: URLs like
        // "jar:nested:/path/outer.jar/!BOOT-INF/lib/inner.jar!/"
        if (url.startsWith("jar:nested:")) {
            return normalizeJarNestedUrl(url);
        }

        // Handle Spring Boot nested jar file URLs
        // From: file:/path/outer.jar/!BOOT-INF/lib/inner.jar!/
        // To: file:/path/outer.jar!/BOOT-INF/lib/inner.jar
        if (url.startsWith("file:") && url.contains("/!") && url.endsWith("!/")) {
            return normalizeFileNestedUrl(url);
        }

        // Normalize other jar notation (strip !/...)
        int bang = url.indexOf('!');
        if (bang > 0) {
            url = url.substring(0, bang);
        }

        // For file: URLs, ensure consistent formatting
        if (url.startsWith("file:")) {
            if (url.endsWith(".jar")) {
                return url; // JAR files are returned as-is
            }
            if (!url.endsWith("/")) {
                url = url + "/"; // Ensure directories have trailing slash
            }
            return url;
        }

        if (url.startsWith("jrt:")) {
            return url; // Java module system paths
        }

        return url; // Fallback for other schemes (http, custom, etc.)
    }

    /**
     * Normalizes jar:nested: URLs to match ClasspathJarTracker registration format.
     * 
     * @param url The jar:nested: URL to normalize
     * @return Normalized file: URL in ClasspathJarTracker format
     */
    private String normalizeJarNestedUrl(String url) {
        String path = url.substring("jar:nested:".length());

        // Look for nested jar pattern: /path/outer.jar/!inner/path!/
        int bangIndex = path.indexOf("/!");
        if (bangIndex > 0) {
            // Extract outer jar and inner path
            String outerJar = path.substring(0, bangIndex);
            String innerPath = path.substring(bangIndex + 2); // Skip the "/!"

            // Remove trailing !/ if present
            if (innerPath.endsWith("!/")) {
                innerPath = innerPath.substring(0, innerPath.length() - 2);
            }

            // Ensure file: prefix for consistency with ClasspathJarTracker
            if (!outerJar.startsWith("file:")) {
                outerJar = "file:" + outerJar;
            }

            // Return in ClasspathJarTracker format: file:/outer.jar!/inner/path
            return outerJar + "!/" + innerPath;
        } else {
            // Just the outer jar, no nested path
            if (!path.startsWith("file:")) {
                path = "file:" + path;
            }
            return path;
        }
    }

    /**
     * Normalizes file: URLs with nested jar indicators to standard format.
     * 
     * @param url The file: URL with /! indicators to normalize
     * @return Normalized file: URL with proper !/ separators
     */
    private String normalizeFileNestedUrl(String url) {
        // Remove trailing !/ and convert /! to !/
        String result = url.substring(0, url.length() - 2); // Remove trailing !/
        result = result.replace("/!", "!/"); // Change /! to !/
        return result;
    }


    /**
     * Reads MANIFEST.MF using the original ProtectionDomain's CodeSource location
     * rather than the normalized source identifier.
     *
     * <p>Supports:
     * <ul>
     *   <li>Regular JARs (file:...jar)</li>
     *   <li>Nested JAR style locations (file:outer.jar!/BOOT-INF/lib/inner.jar)</li>
     *   <li>Exploded directories (file:/path/to/classes/)</li>
     * </ul>
     * Falls back silently (FINE log) if the manifest cannot be read.
     */
    private Map<String,String> readManifestAttributes(ProtectionDomain pd) {
        String stage = "start";
        boolean manifestRead = false;
        String locStr = null;
        Map<String,String> attrs = new HashMap<>();
        try {
            if (pd == null) { LOG.fine("Manifest scan aborted (stage=" + stage + "): pd null"); return attrs; }
            stage = "codesource";
            CodeSource cs = pd.getCodeSource();
            if (cs == null) { LOG.fine("Manifest scan aborted (stage=" + stage + "): CodeSource null"); return attrs; }
            stage = "location";
            URL loc = cs.getLocation();
            if (loc == null) { LOG.fine("Manifest scan aborted (stage=" + stage + "): location null"); return attrs; }
            locStr = loc.toString();
            stage = "scheme-eval";
            LOG.fine("Attempting MANIFEST read; stage=" + stage + ", location=" + locStr);

            URL manifestUrl = null;

            // NEW: Handle nested jars with jar:nested: scheme
            if (locStr.startsWith("jar:nested:")) {
                stage = "jar-nested-primary";
                // Primary attempt: leverage the custom protocol handler directly
                String base = locStr;
                if (!base.endsWith("!/")) {
                    if (base.endsWith("!")) {
                        base = base + "/";
                    } else if (!base.endsWith("/")) {
                        // location should end with "!/" already, but be defensive
                        base = base + (base.contains("!") ? "/" : "!/");
                    }
                }
                try {
                    manifestUrl = URI.create(base + "META-INF/MANIFEST.MF").toURL();
                    LOG.fine("jar:nested primary manifest URL: " + manifestUrl);
                } catch (Exception e) {
                    LOG.fine("Primary jar:nested manifest URL build failed: " + e.getMessage());
                }
                // Fallback: normalize to file:/outer.jar!/inner.jar then build a standard jar: URL
                if (manifestUrl == null) {
                    stage = "jar-nested-fallback-normalize";
                    String normalized = normalizeJarNestedUrl(locStr); // e.g. file:/outer.jar!/BOOT-INF/lib/inner.jar
                    // Build standard jar: URL
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
                    String base = locStr;
                    if (base.endsWith("!/")) {
                        base = base.substring(0, base.length() - 2);
                    }
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

            // MANIFEST_PARSER
            stage = "open-stream";
            LOG.fine("Resolved MANIFEST URL (stage=" + stage + "): " + manifestUrl);
            InputStream is = manifestUrl.openStream();
            LOG.fine("Opened MANIFEST stream (stage=" + stage + "): " + manifestUrl);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                stage = "parse";
                String line;
                String previousKey = null;
                while ((line = br.readLine()) != null) {
                    // Previously we stopped parsing on the first empty line (end of main section).
                    // We now continue so that additional sections (per-entry attributes following
                    // blank separators) are also captured. Reset previousKey so continuation lines
                    // do not attach across section boundaries.
                    if (line.isEmpty()) {
                        previousKey = null;
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
            LOG.fine("Exception during manifest read (stage=" + stage + ", location=" + locStr + "): " + e.getMessage());
        } finally {
            if (!manifestRead) {
                String finalLoc = (locStr == null ? "<unknown>" : locStr);
                LOG.fine("Manifest not read; stopped at stage=" + stage + ", location=" + finalLoc);
            }
        }
        return attrs;
    }
}
