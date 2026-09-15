package com.repopilot.cli;

import picocli.CommandLine.IVersionProvider;

import java.io.InputStream;
import java.util.Properties;

/** Reads the version from the build-generated properties file, falling back to a dev marker. */
public class VersionProvider implements IVersionProvider {

    public static String version() {
        try (InputStream in = VersionProvider.class.getResourceAsStream("/repo-pilot.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String v = p.getProperty("version");
                if (v != null && !v.isBlank() && !v.startsWith("${")) {
                    return v;
                }
            }
        } catch (Exception ignored) {
            // fall through to development version
        }
        return "0.0.0-dev";
    }

    @Override
    public String[] getVersion() {
        return new String[]{"repo-pilot " + version()};
    }
}
