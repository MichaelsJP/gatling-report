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
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class ParserFactory {

    public static SimulationParser getParser(File file, Float apdexT) throws IOException {
        return getVersionSpecificParser(file, apdexT);
    }

    public static SimulationParser getParser(File file) throws IOException {
        return getVersionSpecificParser(file, null);
    }

    protected static SimulationParser getVersionSpecificParser(File file, Float apdexT) throws IOException {
        List<String> header = getHeaderLine(file);

        // Handle binary format
        if (isBinaryFormat(header)) {
            return createBinaryFormatParser(file, apdexT, header.get(5));
        }

        // Handle text format based on header size
        if (header.size() == 7) {
            return createTextFormat7Parser(file, apdexT, header.get(6));
        } else if (header.size() == 6) {
            return createTextFormat6Parser(file, apdexT, header.get(5));
        }

        throw new IllegalArgumentException("Unknown Gatling simulation header format: " + header);
    }

    private static boolean isBinaryFormat(List<String> header) {
        return !header.isEmpty() && "BINARY_FORMAT".equals(header.get(0));
    }

    private static SimulationParser createBinaryFormatParser(File file, Float apdexT, String version) {
        if (version.startsWith("3.13")) {
            return new SimulationParserV313(file, apdexT);
        }
        throw new IllegalArgumentException("Unsupported binary format Gatling version: " + version);
    }

    private static SimulationParser createTextFormat7Parser(File file, Float apdexT, String version) {
        if (version.startsWith("2.")) {
            return new SimulationParserV23(file, apdexT);
        }
        throw new IllegalArgumentException("Unknown Gatling 7-column format version: " + version);
    }

    private static SimulationParser createTextFormat6Parser(File file, Float apdexT, String version) {
        // Version 2.x
        if (version.startsWith("2.")) {
            return new SimulationParserV2(file, apdexT);
        }

        // Version 3.x
        if (matchesVersion(version, "3.0")) {
            return new SimulationParserV3(file, apdexT);
        }

        if (matchesVersion(version, "3.2", "3.3")) {
            return new SimulationParserV32(file, apdexT);
        }

        if (matchesVersion(version, "3.4")) {
            return new SimulationParserV34(file, apdexT);
        }

        if (matchesVersion(version, "3.5", "3.6", "3.7", "3.8", "3.9", "3.10")) {
            return new SimulationParserV35(file, apdexT);
        }

        throw new IllegalArgumentException("Unknown Gatling simulation version: " + version);
    }

    private static boolean matchesVersion(String version, String... prefixes) {
        for (String prefix : prefixes) {
            if (version.equals(prefix) || version.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
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
     * Works with both regular and gzip compressed files.
     * Returns null if the file is not in binary format.
     */
    private static String extractBinaryVersion(File file) throws IOException {
        if (file.length() < 5) { // Minimum size for a binary file with version
            return null;
        }

        // Check if file is gzip compressed
        boolean isGzipped = isGzippedFile(file);

        if (isGzipped) {
            // Handle gzipped file
            try (InputStream fis = new FileInputStream(file);
                    BufferedInputStream bis = new BufferedInputStream(fis);
                    GZIPInputStream gzis = new GZIPInputStream(bis)) {

                return readBinaryVersionFromStream(gzis);
            } catch (IOException e) {
                return null;
            }
        } else {
            // Handle regular file
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                return readBinaryVersionFromRandomAccessFile(raf);
            } catch (IOException e) {
                return null;
            }
        }
    }

    /**
     * Determines if a file is gzipped by checking its magic number.
     */
    private static boolean isGzippedFile(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 2) {
                return false;
            }

            // Check for gzip magic number (0x1F 0x8B)
            int byte1 = raf.read();
            int byte2 = raf.read();
            return byte1 == 0x1F && byte2 == 0x8B;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Reads the binary version from a RandomAccessFile.
     */
    private static String readBinaryVersionFromRandomAccessFile(RandomAccessFile raf) throws IOException {
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
    }

    /**
     * Reads the binary version from an InputStream.
     */
    private static String readBinaryVersionFromStream(InputStream is) throws IOException {
        // Check if the first byte is 0 (RUN_RECORD in binary format)
        int recordTypeByte = is.read();
        if (recordTypeByte != 0) {
            return null; // Not a binary format file
        }

        // Read the Gatling version string length (4 bytes)
        byte[] lengthBytes = new byte[4];
        int bytesRead = is.read(lengthBytes);
        if (bytesRead != 4) {
            return null;
        }

        // Convert byte array to int (big-endian)
        int stringLength = ((lengthBytes[0] & 0xFF) << 24) |
                ((lengthBytes[1] & 0xFF) << 16) |
                ((lengthBytes[2] & 0xFF) << 8) |
                (lengthBytes[3] & 0xFF);

        // Sanity check for string length
        if (stringLength <= 0 || stringLength > 100) {
            return null; // Invalid string length
        }

        // Read the version string bytes
        byte[] versionBytes = new byte[stringLength];
        bytesRead = is.read(versionBytes);

        if (bytesRead != stringLength) {
            return null; // Couldn't read full version string
        }

        // Convert bytes to string (UTF-8)
        String version = new String(versionBytes, java.nio.charset.StandardCharsets.UTF_8);

        // Skip the coder byte
        is.read();

        // If it starts with a number and contains periods, it's likely a version
        if (version.matches("^\\d.*\\.\\d.*")) {
            return version;
        }

        return null;
    }
}
