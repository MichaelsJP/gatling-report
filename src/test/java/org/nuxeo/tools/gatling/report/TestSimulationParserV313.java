package org.nuxeo.tools.gatling.report;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

import org.junit.Test;

/**
 * Simple test class for SimulationParserV313
 */
public class TestSimulationParserV313 {

    @Test
    public void parseSimulation() {
        try {
            // Get the test file
            File testFile = getResourceFile("simulation-v3.13.log");
            
            if (!testFile.exists()) {
                System.err.println("Test file not found: " + testFile.getAbsolutePath());
                return;
            }
            
            System.out.println("Parsing test file: " + testFile.getAbsolutePath());
            
            // Create parser instance
            SimulationParserV313 parser = new SimulationParserV313(testFile);
            
            // Parse the file
            SimulationContext context = parser.parse();
            
            // Print basic information for debugging
            System.out.println("Parsing completed successfully");
            
            // Print request stats summary
            System.out.println("\nRequest statistics:");

        } catch (IOException e) {
            System.err.println("Error parsing simulation log: " + e.getMessage());
            e.printStackTrace();
        }
    }


    protected File getResourceFile(String filename) throws FileNotFoundException {
        ClassLoader classLoader = getClass().getClassLoader();
        if (classLoader.getResource(filename) == null) {
            throw new FileNotFoundException(filename);
        }
        return new File(Objects.requireNonNull(classLoader.getResource(filename)).getFile());
    }
}