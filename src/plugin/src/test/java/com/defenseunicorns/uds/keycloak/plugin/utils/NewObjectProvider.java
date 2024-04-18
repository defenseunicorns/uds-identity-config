package com.defenseunicorns.uds.keycloak.plugin.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * The sole purpose of this class is to allow for unit test coverage.
 * The Jaccoco test coverage report is not fully compatible with the PowerMock testing framework.
 * This class is used in @PrepareForTest so that the Jaccoco report detects test coverage for CommonConfig class.
 */
public final class NewObjectProvider {

    private NewObjectProvider() {
        // hide public constructor. No need to ever declare an instance. All methods are static.
    }

    /**
     * Get new java.io.File object.
     *
     * @param filePath a String
     * @return File
     */
    public static File getFile(final String filePath) {
        return new File(filePath);
    }

    /**
     * Get new java.io.FileInputStream object.
     *
     * @param file a File object
     * @return FileInputStream
     */
    public static FileInputStream getFileInputStream(final File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }

    /**
     * Get YAML content from a file and return the resulting map.
     *
     * @param filePath path to the YAML file
     * @return Map representing the parsed YAML content
     * @throws FileNotFoundException if the file is not found
     */
    public static YAMLConfig getYaml(String filePath) throws FileNotFoundException {
        return new YAMLConfig();
    }
}
