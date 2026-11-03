package io.github.gdacoff.killthefrizz.util;

import io.github.gdacoff.killthefrizz.config.KtfConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limited SLF4J wrapper with the {@code [KilllTheFrizz]} prefix.
 *
 * <p>Warnings and errors are de-duplicated: the same message key is not logged more
 * often than once per {@code logSpamLimitSeconds} (default 60s). This keeps the log
 * readable when a feature misbehaves every tick.</p>
 */
public final class KtfLogger {

    private static final String PREFIX = "[KilllTheFrizz] ";
    private static final Logger LOG = LoggerFactory.getLogger("KilllTheFrizz");

    /** Last time (epoch millis) a given message key was logged. */
    private static final ConcurrentHashMap<String, Long> LAST_LOGGED_MILLIS = new ConcurrentHashMap<>();

    private KtfLogger() {
    }

    public static void info(String message, Object... args) {
        LOG.info(PREFIX + message, args);
    }

    public static void debug(String message, Object... args) {
        if (KtfConfig.getEffectiveCommon().debugLogging()) {
            LOG.debug(PREFIX + message, args);
        }
    }

    public static void warn(String message, Object... args) {
        warnRateLimited(message, message, args);
    }

    public static void error(String message, Object... args) {
        errorRateLimited(message, message, args);
    }

    /**
     * Rate-limited warning. {@code key} identifies the message for de-duplication;
     * {@code message} is the actual format string.
     */
    public static void warnRateLimited(String key, String message, Object... args) {
        if (shouldLog(key)) {
            LOG.warn(PREFIX + message, args);
        }
    }

    public static void errorRateLimited(String key, String message, Object... args) {
        if (shouldLog(key)) {
            LOG.error(PREFIX + message, args);
        }
    }

    private static boolean shouldLog(String key) {
        long now = System.currentTimeMillis();
        long limitMs = Math.max(1, KtfConfig.getEffectiveCommon().logSpamLimitSeconds()) * 1000L;
        Long last = LAST_LOGGED_MILLIS.get(key);
        if (last != null && (now - last) < limitMs) {
            return false;
        }
        LAST_LOGGED_MILLIS.put(key, now);
        return true;
    }

    /** Clears the de-duplication cache (used on config reload). */
    public static void resetRateLimit() {
        LAST_LOGGED_MILLIS.clear();
    }
}
