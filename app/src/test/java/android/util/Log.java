package android.util;

/** JVM adapter for the local-tone mapper's debug diagnostic only.
 * Other Android logging methods remain unavailable rather than silently returning defaults.
 */
public final class Log {
    private Log() {}
    public static int d(String tag, String message) {
        if (tag == null || message == null) throw new IllegalArgumentException("Null diagnostic");
        return 0;
    }
}
