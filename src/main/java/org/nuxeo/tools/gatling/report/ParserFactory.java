/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Kris Geusebroek
 */
package org.nuxeo.tools.gatling.report;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class ParserFactory {

    public static SimulationParser getParser(File file, Float apdexT) throws IOException {
        return getVersionSpecificParser(file, apdexT);
    }

    public static SimulationParser getParser(File file) throws IOException {
        return getVersionSpecificParser(file, null);
    }

    protected static SimulationParser getVersionSpecificParser(File file, Float apdexT) throws IOException {
        List<String> header = getHeaderLine(file);
        if (header.size() == 6) {
            String version = header.get(5);
            if (version.startsWith("2.")) {
                return new SimulationParserV2(file, apdexT);
            }
            if (version.equals("3.0") || version.startsWith("3.0.")) {
                return new SimulationParserV3(file, apdexT);
            }
            if ("3.2".equals(version) || version.startsWith("3.2.")
                    || ("3.3".equals(version) || version.startsWith("3.3."))) {
                return new SimulationParserV32(file, apdexT);
            }
            if (version.equals("3.4") || version.startsWith("3.4.")) {
                return new SimulationParserV34(file, apdexT);
            }
            // 3.5 and above
            if (version.startsWith("3.5") || version.startsWith("3.6") || version.startsWith("3.7")
                    || version.startsWith("3.8") || version.startsWith("3.9")) {
                return new SimulationParserV35(file, apdexT);
            }

            if (version.startsWith("3.13")) {
                return new SimulationParserV313(file, apdexT);
            }
        } else if (header.size() == 7) {
            String version = header.get(6);
            if (version.startsWith("2.")) {
                return new SimulationParserV23(file, apdexT);
            }
        }
        throw new IllegalArgumentException("Unknown Gatling simulation version: " + header);
    }

    protected static List<String> getHeaderLine(File file) throws IOException {
        // First try reading as text format using SimulationReader
        try (SimulationReader reader = new SimulationReader(file)) {
            List<String> header = reader.readNext();

            // If we got a valid header, return it
            if (header != null && !header.isEmpty() && header.size() > 1) {
                return header;
            }
        } catch (Exception e) {
            // Failed to read as text format, might be binary
        }

        // Check if this is a binary format file (Gatling 3.13+)
        String binaryVersion = extractBinaryVersion(file);
        if (binaryVersion != null) {
            List<String> binaryHeader = new ArrayList<>();
            // Create a header that mimics the text format but indicates it's binary
            // The version will be the actual version extracted from the file
            binaryHeader.add("BINARY_FORMAT");
            binaryHeader.add("SIMULATION");
            binaryHeader.add("UNKNOWN"); // Simulation name (will be read by the parser)
            binaryHeader.add("0"); // Start timestamp (will be read by the parser)
            binaryHeader.add("RUN");
            binaryHeader.add(binaryVersion); // Actual version number
            return binaryHeader;
        }
        // If we reach here, we couldn't identify the file format
        throw new IOException("Unable to determine format of simulation log: " + file.getAbsolutePath());
    }

    /**
     * Extracts the Gatling version from a binary format file.
     * Returns null if the file is not in binary format.
     */
    private static String extractBinaryVersion(File file) throws IOException {
        if (file.length() < 5) { // Minimum size for a binary file with version
            return null;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // Check if the first byte is 0 (RUN_RECORD in binary format)
            byte recordType = raf.readByte();
            if (recordType != 0) {
                return null; // Not a binary format file
            }

            // Read the Gatling version string
            // First 4 bytes after record type contain the string length
            int stringLength = raf.readInt();

            // Sanity check for string length
            if (stringLength <= 0 || stringLength > 100) {
                return null; // Invalid string length
            }

            // Read the version string bytes
            byte[] versionBytes = new byte[stringLength];
            int bytesRead = raf.read(versionBytes);

            if (bytesRead != stringLength) {
                return null; // Couldn't read full version string
            }

            // Convert bytes to string (UTF-8)
            String version = new String(versionBytes, java.nio.charset.StandardCharsets.UTF_8);

            // Skip the coder byte
            raf.readByte();

            // If it starts with a number and contains periods, it's likely a version
            if (version.matches("^\\d.*\\.\\d.*")) {
                return version;
            }

            return null;
        } catch (IOException e) {
            return null;
        }
    }
}
