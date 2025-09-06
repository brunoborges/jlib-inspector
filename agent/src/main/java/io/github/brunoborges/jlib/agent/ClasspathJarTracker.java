package io.github.brunoborges.jlib.agent;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Captures JARs present on the startup classpath (java.class.path & URLClassLoaders) even if
 * they have not (yet) defined any classes. This helps reveal unused or not-yet-loaded libraries.
 */
public final class ClasspathJarTracker {

    private static final Logger LOG = Logger.getLogger(ClasspathJarTracker.class.getName());

    private final Set<String> declaredClasspathJars = Collections.synchronizedSet(new LinkedHashSet<>()); // top-level declared
    private final Set<String> declaredNestedJars   = Collections.synchronizedSet(new LinkedHashSet<>()); // nested declared
    private final JarInventory inventory;

    public ClasspathJarTracker(Instrumentation inst, JarInventory inventory) {
        this.inventory = inventory;
        snapshotLoaded(inst);      // examine already loaded classes & their CodeSources
        scanDeclaredClasspath();   // parse java.class.path
        scanUrlClassLoaders();     // harvest URLs from system/app loaders
        scanExecutableJarsForNested(); // enumerate nested jars generically
        reportUnloaded();          // aggregate logging
    }

    private void snapshotLoaded(Instrumentation inst) {
        for (Class<?> c : inst.getAllLoadedClasses()) {
            CodeSource cs = c.getProtectionDomain() == null ? null : c.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                String loc = cs.getLocation().toString();
                if (loc.contains(".jar")) {
                    // Extract all jar layers from a possibly nested jar URL chain
                    for (String jarLayer : extractJarLayers(loc)) {
                        if (jarLayer != null) {
                            inventory.markLoaded(jarLayer);
                        }
                    }
                }
            }
        }
    }

    private void scanDeclaredClasspath() {
        String cp = System.getProperty("java.class.path", "");
        if (cp.isEmpty()) return;
        String[] parts = cp.split(File.pathSeparator);
        for (String p : parts) {
            if (p.endsWith(".jar")) {
                String norm = normalize(new File(p).toURI().toString());
                declaredClasspathJars.add(norm);
                registerDeclared(norm);
            }
        }
    }

    private void scanUrlClassLoaders() {
        // Walk up system/application classloader chain and harvest URLs
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        while (cl != null) {
            if (cl instanceof URLClassLoader ucl) {
                for (URL u : ucl.getURLs()) {
                    String s = u.toString();
                    if (s.endsWith(".jar")) {
                        String norm = normalize(s);
                        declaredClasspathJars.add(norm);
                        registerDeclared(norm);
                    }
                }
            }
            cl = cl.getParent();
        }
    }

    private void reportUnloaded() {
        if (!LOG.isLoggable(Level.INFO)) return;

    LOG.info(() -> "Classpath scan summary: topLevelDeclared=" + declaredClasspathJars.size() +
        ", nestedDeclared=" + declaredNestedJars.size() + ", inventorySize=" + inventory.snapshot().size());
    }

    private String normalize(String loc) {
        // Remove trailing !/ for nested jars and ensure uniform file: prefix
        int bang = loc.indexOf('!');
        if (bang > 0) loc = loc.substring(0, bang);
        return loc;
    }

    /** Extract all jar layers from a potentially nested jar URL chain. */
    private Set<String> extractJarLayers(String loc) {
        LinkedHashSet<String> layers = new LinkedHashSet<>();
        String work = loc;
        // Strip leading repeated "jar:" prefixes
        while (work.startsWith("jar:")) {
            work = work.substring(4);
        }
        int searchFrom = 0;
        while (true) {
            int idx = work.indexOf(".jar", searchFrom);
            if (idx < 0) break;
            int end = idx + 4; // include .jar
            String jarPath = work.substring(0, end);
            layers.add(normalizeJarLayer(jarPath));
            // Move past possible !/ delimiter
            if (end + 2 <= work.length() && work.startsWith("!/", end)) {
                searchFrom = end + 2;
            } else {
                break;
            }
        }
        return layers;
    }

    private String normalizeJarLayer(String raw) {
        // Ensure consistent file: prefix usage for top-level; nested path kept with outer!inner form
        return raw;
    }


    private void scanExecutableJarsForNested() {
        for (String jarUri : declaredClasspathJars) {
            // jarUri like file:/.../app.jar
            File f = toFileIfPossible(jarUri);
            if (f == null || !f.isFile()) continue;
            // Heuristic: scan if size < 300MB to avoid huge archives & if contains potential nested libs
            if (f.length() > 300L * 1024 * 1024) continue;
            try (JarFile jf = new JarFile(f)) {
                boolean hasNested = false;
                for (JarEntry e : Collections.list(jf.entries())) {
                    String name = e.getName();
                    if (name.endsWith(".jar")) {
                        hasNested = true;
                        String nestedId = f.toURI().toString() + "!/" + name;
                        declaredNestedJars.add(nestedId);
                        long size = e.getSize();
                        JarInventory.HashSupplier hs = JarInventory.nestedJarHashSupplier(jf, e);
                        inventory.registerDeclared(nestedId, size, hs);
                    }
                }
                if (hasNested && LOG.isLoggable(Level.FINE)) {
                    LOG.fine(() -> "Discovered nested jars inside " + f.getName() + ": " + declaredNestedJars.stream().filter(s -> s.startsWith(f.toURI().toString())).count());
                }
            } catch (IOException ioe) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Could not scan jar for nested entries: " + jarUri + " : " + ioe.getMessage());
                }
            }
        }
    }

    private File toFileIfPossible(String uri) {
        if (uri.startsWith("file:")) {
            try {
                return new File(new java.net.URI(uri));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public Set<String> getDeclaredClasspathJars() { return declaredClasspathJars; }
    public Set<String> getDeclaredNestedJars() { return declaredNestedJars; }

    private void registerDeclared(String norm) {
        File f = toFileIfPossible(norm);
        if (f != null && f.isFile()) {
            inventory.registerDeclared(norm, f.length(), JarInventory.fileHashSupplier(f));
        } else {
            inventory.registerDeclared(norm, -1L, null);
        }
    }
}
