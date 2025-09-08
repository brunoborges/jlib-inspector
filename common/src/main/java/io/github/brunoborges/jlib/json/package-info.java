/**
 * Minimal JSON parsing and response building utilities.
 *
 * <p>This package provides a light abstraction over JSON handling via
 * {@link io.github.brunoborges.jlib.json.JsonParserInterface} with two
 * implementations:
 * <ul>
 *   <li>{@link io.github.brunoborges.jlib.json.JsonParser} — built-in, no dependencies</li>
 *   <li>{@link io.github.brunoborges.jlib.json.OrgJsonParser} — backed by {@code org.json}</li>
 * </ul>
 * The {@link io.github.brunoborges.jlib.json.JsonParserFactory} selects an
 * implementation at runtime, preferring {@code org.json} when available.
 */
package io.github.brunoborges.jlib.json;
