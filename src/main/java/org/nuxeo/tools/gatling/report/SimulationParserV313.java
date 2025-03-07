/*
 * (C) Copyright 2015-2025 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Your Name
 */
package org.nuxeo.tools.gatling.report;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gatling 3.13+ simulation format parser
 * Handles the binary simulation.log file format introduced in Gatling 3.13
 */
public class SimulationParserV313 extends SimulationParser {

    // Binary record type headers
    private static final byte RUN_RECORD = 0;
    private static final byte REQUEST_RECORD = 1;
    private static final byte USER_RECORD = 2;
    private static final byte GROUP_RECORD = 3;
    private static final byte ERROR_RECORD = 4;

    // Buffer size for reading
    private static final int BUFFER_SIZE = 8192;

    // String cache for efficient reading
    private final Map<Integer, String> stringCache = new HashMap<>();
    
    // Store scenario to user mapping
    final protected Map<String, String> userScenario = new HashMap<>();
    
    // Store simulation metadata
    protected String simulationClassName;
    protected long simulationStart;
    protected String[] scenarioNames;

    public SimulationParserV313(File file, Float apdexT) {
        super(file, apdexT);
    }

    public SimulationParserV313(File file) {
        super(file);
    }

    @Override
    public SimulationContext parse() throws IOException {
        SimulationContext ret = new SimulationContext(file.getAbsolutePath(), apdexT);

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                FileChannel channel = randomAccessFile.getChannel()) {

            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

            // First, fill the buffer with enough data to process the RUN record
            int bytesRead = channel.read(buffer);
            if (bytesRead <= 0) {
                throw new IOException("Empty file or failed to read simulation log");
            }

            buffer.flip();

            // Check if first byte indicates a RUN record
            if (buffer.remaining() > 0 && buffer.get(0) == RUN_RECORD) {
                // Skip the record type byte
                buffer.get();

                try {
                    processRunRecord(buffer);

                    // Set simulation information in the context
                    ret.setSimulationName(simulationClassName);
                    ret.setStart(simulationStart);
                    if (scenarioNames.length > 0) {
                        ret.setScenarioName(scenarioNames[0]);
                    }
                } catch (IOException e) {
                    System.err.println("Error processing RUN record: " + e.getMessage());
                    // If we can't parse the RUN record, we likely can't parse the rest either
                    throw e;
                }
            } else {
                throw new IOException("File does not start with a RUN record or is not a valid binary simulation log");
            }

            buffer.compact();

            // Process the rest of the records
            while ((bytesRead = channel.read(buffer)) > 0 || buffer.position() > 0) {
                buffer.flip();

                while (buffer.remaining() > 0) {
                    // Make sure we have at least a byte for the record type
                    if (buffer.remaining() < 1) {
                        break;
                    }

                    byte recordType = buffer.get();

                    try {
                        switch (recordType) {
                            case USER_RECORD:
                                processUserRecord(buffer, ret);
                                break;
                            case REQUEST_RECORD:
                                processRequestRecord(buffer, ret);
                                break;
                            case ERROR_RECORD:
                                processErrorRecord(buffer);
                                break;
                            case GROUP_RECORD:
                                processGroupRecord(buffer);
                                break;
                            case RUN_RECORD:
                                // We already processed the RUN record at the beginning
                                throw new IOException("Unexpected additional RUN record found");
                            default:
                                System.err.println("Unknown record type: " + recordType);
                                // Skip to the next record as we can't determine the length
                                buffer.position(buffer.limit());
                                break;
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing record type " + recordType + ": " + e.getMessage());
                        // Clear buffer and try next chunk
                        buffer.position(buffer.limit());
                        break;
                    }
                }

                buffer.compact();
            }
        }

        ret.computeStat();
        return ret;
    }

    /**
     * Process the RUN record which contains simulation metadata
     */
    private void processRunRecord(ByteBuffer buffer) throws IOException {
        // Format based on RunMessageSerializer:
        // gatlingVersion, simulationClassName, start, runDescription, scenarioCount, [scenarioNames], assertionCount, [assertions]
        
        String gatlingVersion = readString(buffer);
        simulationClassName = readString(buffer);
        simulationStart = buffer.getLong();
        String runDescription = readString(buffer);
        
        int scenarioCount = buffer.getInt();
        scenarioNames = new String[scenarioCount];
        for (int i = 0; i < scenarioCount; i++) {
            scenarioNames[i] = readString(buffer);
        }
        
        // Skip assertions as they're not needed for our parser
        int assertionCount = buffer.getInt();
        // Just move the buffer position past the assertions
        for (int i = 0; i < assertionCount; i++) {
            int byteLength = buffer.getInt();
            buffer.position(buffer.position() + byteLength);
        }
    }

    /**
     * Process a USER record which contains user start/end events
     */
    private void processUserRecord(ByteBuffer buffer, SimulationContext context) {
        // Format based on UserMessageSerializer:
        // scenarioIndex, start/end, timestamp
        
        int scenarioIndex = buffer.getInt();
        boolean isStart = buffer.get() == 1;
        int relativeTimestamp = buffer.getInt();
        long timestamp = simulationStart + relativeTimestamp;
        
        String scenarioName = getScenarioName(scenarioIndex);
        String userId = "user-" + scenarioName + "-" + System.nanoTime(); // Generate unique user ID
        
        // Store scenario mapping
        userScenario.put(userId, scenarioName);
        
        // Update the context with user events
        if (isStart) {
            context.addUser(scenarioName);
        } else {
            context.endUser(scenarioName);
        }
    }

    /**
     * Process a REQUEST record which contains request metrics
          * @throws IOException 
          */
         private void processRequestRecord(ByteBuffer buffer, SimulationContext context) throws IOException {
        // Format based on ResponseMessageSerializer:
        // groupCount, [groups], name, startTimestamp, endTimestamp, success, message
        
        // Read groups (not used in our case but need to skip)
        int groupCount = buffer.getInt();
        List<String> groups = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            groups.add(readCachedString(buffer));
        }
        
        String requestName = readCachedString(buffer);
        int relativeStartTime = buffer.getInt();
        int relativeEndTime = buffer.getInt();
        boolean success = buffer.get() == 1;
        String message = readCachedString(buffer);
        
        long startTimestamp = simulationStart + relativeStartTime;
        long endTimestamp = simulationStart + relativeEndTime;
        
        // Use first scenario if we can't determine the actual one
        String scenarioName = scenarioNames.length > 0 ? scenarioNames[0] : "default";
        
        // Add the request to the context
        context.addRequest(scenarioName, requestName, startTimestamp, endTimestamp, success);
    }

    /**
     * Process an ERROR record
          * @throws IOException 
          */
         private void processErrorRecord(ByteBuffer buffer) throws IOException {
        // Format based on ErrorMessageSerializer:
        // message, timestamp
        
        String message = readCachedString(buffer);
        int relativeTimestamp = buffer.getInt();
        // We don't need to do anything with errors for now
    }

    /**
     * Process a GROUP record
          * @throws IOException 
          */
        @SuppressWarnings("unused")
          private void processGroupRecord(ByteBuffer buffer) throws IOException {
        // Format based on GroupMessageSerializer:
        // groupCount, [groups], startTimestamp, endTimestamp, cumulatedResponseTime, success
        
        int groupCount = buffer.getInt();
        for (int i = 0; i < groupCount; i++) {
            readCachedString(buffer);
        }
        
        
        int startTimeStamp = buffer.getInt(); // startTimestamp
        int endTimeStamp = buffer.getInt(); // endTimestamp
        int cumulatedResponseTime = buffer.getInt(); // cumulatedResponseTime
        byte success = buffer.get();    // success
        System.out.println("Group Record: " + startTimeStamp + " " + endTimeStamp + " " + cumulatedResponseTime + " " + success);
        // We don't need to do anything with groups for now
    }

    /**
     * Read a cached string from the buffer
     * Handles the string caching mechanism used in Gatling's binary format
          * @throws IOException 
          */
         private String readCachedString(ByteBuffer buffer) throws IOException {
        int stringIndex = buffer.getInt();
        
        if (stringIndex >= 0) {
            // New string, read and cache it
            String str = readString(buffer);
            stringCache.put(stringIndex, str);
            return str;
        } else {
            // Cached string reference
            return stringCache.get(-stringIndex);
        }
    }

    /**
     * Read a raw string from the buffer
     */
    private String readString(ByteBuffer buffer) throws IOException {
        // Check if we have enough bytes to read the length
        if (buffer.remaining() < 4) {
            throw new IOException("Buffer underflow when reading string length");
        }

        int length = buffer.getInt();

        if (length == 0) {
            return "";
        }

        // Check if we have enough bytes for the string content plus coder byte
        if (buffer.remaining() < length + 1) {
            throw new IOException("Buffer underflow when reading string data of length " + length);
        }

        byte[] stringBytes = new byte[length];
        buffer.get(stringBytes);

        // Check coder byte (encoding type)
        byte coder = buffer.get();

        // Use UTF-8 encoding for all strings
        return new String(stringBytes, StandardCharsets.UTF_8);
    }

    /**
     * Get scenario name from its index
     */
    private String getScenarioName(int index) {
        if (index >= 0 && index < scenarioNames.length) {
            return scenarioNames[index];
        }
        return "unknown";
    }

    // Implementation of required abstract methods
    // These are mostly placeholders as the actual parsing happens in our custom parse() method

    @Override
    protected String getSimulationName(List<String> line) {
        return simulationClassName;
    }

    @Override
    protected String getSimulationStart(List<String> line) {
        return String.valueOf(simulationStart);
    }

    @Override
    protected String getScenario(List<String> line) {
        if (RUN.equals(line.get(0))) {
            return scenarioNames.length > 0 ? scenarioNames[0] : "";
        } else if (USER.equals(line.get(0))) {
            String user = line.get(2);
            if (START.equals(line.get(3))) {
                String ret = line.get(1);
                userScenario.put(user, ret);
            }
            return userScenario.get(user);
        } else {
            String user = line.get(1);
            return userScenario.get(user);
        }
    }

    @Override
    protected String getType(List<String> line) {
        return line.get(0);
    }

    @Override
    protected String getUserType(List<String> line) {
        return line.get(2);
    }

    @Override
    protected String getRequestName(List<String> line) {
        return line.get(2);
    }

    @Override
    protected Long getRequestStart(List<String> line) {
        return Long.parseLong(line.get(3));
    }

    @Override
    protected Long getRequestEnd(List<String> line) {
        return Long.parseLong(line.get(4));
    }

    @Override
    protected boolean getRequestSuccess(List<String> line) {
        return OK.equals(line.get(5));
    }
}