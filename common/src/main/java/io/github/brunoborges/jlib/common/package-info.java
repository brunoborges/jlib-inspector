/**
 * Shared data model and utilities used by both the agent and the server.
 *
 * <p>Core types include:
 * <ul>
 *   <li>{@link io.github.brunoborges.jlib.common.JarMetadata} — unified JAR metadata and tracking</li>
 *   <li>{@link io.github.brunoborges.jlib.common.JavaApplication} — in-memory app record and jar map</li>
 *   <li>{@link io.github.brunoborges.jlib.common.ApplicationIdUtil} — utility for computing stable application IDs</li>
 * </ul>
 *
 * <p>Thread-safety considerations:
 * <ul>
 *   <li>Jar loaded state is tracked via {@code AtomicBoolean} in {@code JarMetadata}</li>
 *   <li>Application jar collections use {@code ConcurrentHashMap}</li>
 * </ul>
 */
package io.github.brunoborges.jlib.common;
