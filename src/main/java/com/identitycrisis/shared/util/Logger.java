package com.identitycrisis.shared.util;

/** Minimal tagged logger. Wraps System.out/err with [TAG] prefixes. */
public class Logger {

    private static final boolean DEBUG = false;

    private final String tag;

    public Logger(String tag) { this.tag = tag; }

    public void info(String msg)  { System.out.println("[INFO][" + tag + "] " + msg); }

    public void warn(String msg)  { System.out.println("[WARN][" + tag + "] " + msg); }

    public void error(String msg) { System.err.println("[ERROR][" + tag + "] " + msg); }

    public void error(String msg, Throwable t) {
        System.err.println("[ERROR][" + tag + "] " + msg);
        t.printStackTrace(System.err);
    }

    /** Only prints if static DEBUG flag is true. */
    public void debug(String msg) { if (DEBUG) System.out.println("[DEBUG][" + tag + "] " + msg); }
}
