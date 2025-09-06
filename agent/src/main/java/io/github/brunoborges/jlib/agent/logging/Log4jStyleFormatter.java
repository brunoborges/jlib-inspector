package io.github.brunoborges.jlib.agent.logging;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Simple Log4j-style formatter for JUL output.
 * Pattern (fixed) : yyyy-MM-dd HH:mm:ss.SSS LEVEL [thread] logger - message\n(stacktrace)
 * Not fully configurable (kept tiny for agent footprint). Add system property
 * "jlib.format.tz" to override timezone (default: system default).
 */
public class Log4jStyleFormatter extends Formatter {

    private static final DateTimeFormatter TS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.of(System.getProperty("jlib.format.tz", ZoneId.systemDefault().getId())));

    @Override
    public String format(LogRecord r) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(TS.format(Instant.ofEpochMilli(r.getMillis()))).append(' ')
          .append(padLevel(r.getLevel())).append(' ')
          .append('[').append(threadName(r)).append("] ")
          .append(loggerSimple(r.getLoggerName())).append(" - ")
          .append(formatMessage(r)).append('\n');
        if (r.getThrown() != null) {
            appendStackTrace(sb, r.getThrown());
        }
        return sb.toString();
    }

    private String padLevel(Level lvl) {
        String n = lvl.getName();
        // Common levels to fixed width 5 for alignment
        if (n.length() >= 5) return n;
        return ("     " + n).substring(n.length());
    }

    private String loggerSimple(String logger) {
        if (logger == null) return "";
        int lastDot = logger.lastIndexOf('.');
        return lastDot >= 0 ? logger.substring(lastDot + 1) : logger;
    }

    // Best-effort thread name retrieval (JUL only gives thread ID).
    @SuppressWarnings("deprecation")
    private String threadName(LogRecord r) {
        // getThreadID is deprecated but retained here for a stable lightweight identifier.
        return String.valueOf(r.getThreadID());
    }

    private void appendStackTrace(StringBuilder sb, Throwable t) {
        sb.append(t).append('\n');
        for (StackTraceElement ste : t.getStackTrace()) {
            sb.append("\tat ").append(ste).append('\n');
        }
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            sb.append("Caused by: ");
            appendStackTrace(sb, cause);
        }
    }
}
