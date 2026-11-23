package io.github.brunoborges.jlib.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.logging.Logger;

/**
 * Best-effort reader of package metadata for an archive identified by an
 * inventory id, either top-level ({@code file:/app/lib/x.jar}) or nested one
 * level deep ({@code file:/app/app.jar!/BOOT-INF/lib/x.jar}).
 *
 * <p>
 * Extracts Maven coordinates from {@code META-INF/maven/.../pom.properties},
 * the manifest main attributes and, on request, the SHA-256 digest of the
 * archive bytes. Any failure yields {@link #EMPTY}.
 */
final class ArchiveDetails {

    private static final Logger LOG = Logger.getLogger(ArchiveDetails.class.getName());
    private static final String POM_PREFIX = "META-INF/maven/";
    private static final String POM_SUFFIX = "/pom.properties";

    static final ArchiveDetails EMPTY = new ArchiveDetails(null, Map.of(), null);

    /** Maven pom.properties for this archive, or null if absent/ambiguous. */
    final Properties pom;
    /** Manifest main attributes; empty if unavailable. */
    final Map<String, String> manifest;
    /** Lowercase hex SHA-256 of the archive, or null if not computed. */
    final String sha256Hex;

    private ArchiveDetails(Properties pom, Map<String, String> manifest, String sha256Hex) {
        this.pom = pom;
        this.manifest = manifest;
        this.sha256Hex = sha256Hex;
    }

    static ArchiveDetails read(String jarId, String fileName, boolean computeDigest) {
        if (jarId == null) {
            return EMPTY;
        }
        try {
            int bang = jarId.indexOf("!/");
            if (bang < 0) {
                File file = toFile(jarId);
                return file == null ? EMPTY : readTopLevel(file, fileName, computeDigest);
            }
            String inner = jarId.substring(bang + 2);
            if (inner.contains("!/")) {
                return EMPTY;
            }
            File outer = toFile(jarId.substring(0, bang));
            return outer == null ? EMPTY : readNested(outer, inner, fileName, computeDigest);
        } catch (Exception e) {
            LOG.fine(() -> "Unable to read archive details for " + jarId + ": " + e.getMessage());
            return EMPTY;
        }
    }

    private static ArchiveDetails readTopLevel(File file, String fileName, boolean computeDigest)
            throws IOException, NoSuchAlgorithmException {
        Map<String, String> manifest;
        List<Properties> poms = new ArrayList<>();
        try (JarFile jar = new JarFile(file, false)) {
            manifest = mainAttributes(jar.getManifest());
            for (JarEntry entry : Collections.list(jar.entries())) {
                if (isPom(entry.getName())) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        poms.add(load(in));
                    }
                }
            }
        }
        String digest = null;
        if (computeDigest) {
            try (DigestInputStream in = new DigestInputStream(new FileInputStream(file), sha256())) {
                drain(in);
                digest = HexFormat.of().formatHex(in.getMessageDigest().digest());
            }
        }
        return new ArchiveDetails(selectPom(poms, fileName), manifest, digest);
    }

    private static ArchiveDetails readNested(File outerFile, String innerName, String fileName,
            boolean computeDigest) throws IOException, NoSuchAlgorithmException {
        try (JarFile outer = new JarFile(outerFile, false)) {
            JarEntry entry = outer.getJarEntry(innerName);
            if (entry == null) {
                return EMPTY;
            }
            List<Properties> poms = new ArrayList<>();
            Map<String, String> manifest;
            String digest = null;
            InputStream raw = outer.getInputStream(entry);
            DigestInputStream digestIn = computeDigest ? new DigestInputStream(raw, sha256()) : null;
            try (JarInputStream jar = new JarInputStream(digestIn != null ? digestIn : raw, false)) {
                manifest = mainAttributes(jar.getManifest());
                for (JarEntry e; (e = jar.getNextJarEntry()) != null;) {
                    if (isPom(e.getName())) {
                        poms.add(load(jar));
                    }
                }
                if (digestIn != null) {
                    // Consume trailing bytes (central directory) so the digest covers the whole entry
                    drain(digestIn);
                    digest = HexFormat.of().formatHex(digestIn.getMessageDigest().digest());
                }
            }
            return new ArchiveDetails(selectPom(poms, fileName), manifest, digest);
        }
    }

    /**
     * Picks the pom.properties describing this archive. Shaded archives may
     * embed several; in that case the one matching the file name wins.
     */
    private static Properties selectPom(List<Properties> poms, String fileName) {
        if (poms.size() == 1) {
            return poms.get(0);
        }
        if (fileName != null) {
            for (Properties pom : poms) {
                String artifactId = pom.getProperty("artifactId");
                String version = pom.getProperty("version");
                if (artifactId != null && version != null && fileName.startsWith(artifactId + "-" + version)) {
                    return pom;
                }
            }
        }
        return null;
    }

    private static boolean isPom(String name) {
        return name.startsWith(POM_PREFIX) && name.endsWith(POM_SUFFIX);
    }

    private static Properties load(InputStream in) throws IOException {
        Properties props = new Properties();
        props.load(in);
        return props;
    }

    private static Map<String, String> mainAttributes(Manifest manifest) {
        if (manifest == null) {
            return Map.of();
        }
        Map<String, String> attrs = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> e : manifest.getMainAttributes().entrySet()) {
            attrs.put(((Attributes.Name) e.getKey()).toString(), String.valueOf(e.getValue()));
        }
        return attrs;
    }

    private static File toFile(String id) {
        if (!id.startsWith("file:")) {
            return null;
        }
        try {
            File f = new File(URI.create(id));
            return f.isFile() ? f : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static MessageDigest sha256() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        while (in.read(buf) != -1) {
            // consume
        }
    }
}
