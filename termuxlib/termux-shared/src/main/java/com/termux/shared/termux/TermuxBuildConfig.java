package com.termux.shared.termux;

/**
 * Compile-time constants injected by the Operit host build.
 */
public final class TermuxBuildConfig {

    private TermuxBuildConfig() {}

    /** applicationId of the hosting app. */
    public static final String HOST_PACKAGE_NAME = "com.ai.assistance.operit";

    /** Human readable app name. */
    public static final String HOST_APP_NAME = "Operit Termux";

    /** Absolute path of the private data dir; empty means resolve at runtime. */
    public static final String HOST_DATA_DIR = "";
}
