/**
 * Java instrumentation agent that inspects JAR usage at runtime.
 *
 * <p>Main components:
 * <ul>
 *   <li>{@link io.github.brunoborges.jlib.agent.InspectorAgent} — entrypoint (premain)</li>
 *   <li>{@link io.github.brunoborges.jlib.agent.ClassLoaderTrackerTransformer} — tracks class sources</li>
 *   <li>{@link io.github.brunoborges.jlib.agent.ClasspathJarTracker} — scans declared classpath and nested jars</li>
 *   <li>{@link io.github.brunoborges.jlib.agent.JarInventory} — central inventory of observed JARs</li>
 *   <li>{@link io.github.brunoborges.jlib.agent.JLibServerClient} — optional reporting to the backend</li>
 * </ul>
 */
package io.github.brunoborges.jlib.agent;
