package com.identitycrisis.shared.util;

/** Minimal tagged logger. Wraps System.out/err with [TAG] prefixes. */
public class Logger {

    private static final boolean DEBUG = false;

    private final String tag;

    public Logger(String tag) { this.tag = tag; }

    public void info(String msg) { }

    public void warn(String msg) { }

    public void error(String msg) { }

    public void error(String msg, Throwable t) { }

    /** Only prints if static DEBUG flag is true. */
    public void debug(String msg) { }
}
