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
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gatling 3.13+ simulation format parser
 * Handles the binary simulation.log file format introduced in Gatling 3.13
 */
public class SimulationParserV313 extends SimulationParser {

    private static final Logger log = LoggerFactory.getLogger(SimulationParserV313.class);

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

    // Counters for stats
    private int totalRecords = 0;
    private int userRecords = 0;
    private int requestRecords = 0;
    private int errorRecords = 0;
    private int groupRecords = 0;

    // Store scenario to user mapping
    protected final Map<String, String> userScenario = new HashMap<>();

    // Store simulation metadata
    protected String simulationClassName;
    protected long simulationStart;
    protected String[] scenarioNames;

    public SimulationParserV313(File file, Float apdexT) {
        super(file, apdexT);
        log.debug("Created parser for file: {} with apdexT={}", file.getAbsolutePath(), apdexT);
    }

    public SimulationParserV313(File file) {
        super(file);
        log.debug("Created parser for file: {}", file.getAbsolutePath());
    }

    boolean isBinaryFormat(ByteBuffer buffer) {
        // Check if first byte is RUN_RECORD (0) and next bytes match expected pattern
        return buffer.get(0) == RUN_RECORD;
    }

    boolean isValidRecord(byte recordType, ByteBuffer buffer) {
        // Check basic size requirements
        int minimumSize = switch (recordType) {
            case RUN_RECORD -> 16; // gatlingVersion(4+) + simulationClassName(4+) + start(8)
            case USER_RECORD -> 9; // scenarioIndex(4) + isStart(1) + timestamp(4)
            case REQUEST_RECORD -> 14; // groupCount(4) + name(4) + start(4) + end(4) + success(1) + message(1+)
            case GROUP_RECORD -> 17; // groupCount(4) + start(4) + end(4) + cumulated(4) + success(1)
            case ERROR_RECORD -> 8; // message(4) + timestamp(4)
            default -> -1;
        };

        if (buffer.remaining() < minimumSize) {
            return false;
        }

        // For additional validation, you could mark the current position and peek at
        // values
        int position = buffer.position();
        try {
            // Example: For USER_RECORD, validate scenarioIndex is within bounds
            if (recordType == USER_RECORD) {
                int scenarioIndex = buffer.getInt(position);
                return scenarioIndex >= 0 &&
                        scenarioNames != null &&
                        scenarioIndex < scenarioNames.length;
            }

            // Add other validation checks as needed

            return true;
        } finally {
            // Reset position so we don't disturb the buffer
            buffer.position(position);
        }
    }

    @Override
    public SimulationContext parse() throws IOException {
        log.info("Starting to parse binary simulation log: {}", file.getAbsolutePath());
        SimulationContext ret = new SimulationContext(file.getAbsolutePath(), apdexT);

        // Check if the file is gzipped
        boolean isGzipped = isGzippedFile(file);
        log.info("Detected {} file format", isGzipped ? "gzipped" : "standard");

        if (isGzipped) {
            // Handle gzipped file with InputStream
            parseGzippedFile(ret);
        } else {
            // Handle regular file with RandomAccessFile
            parseRegularFile(ret);
        }

        log.info("Completed parsing {} records: {} USER, {} REQUEST, {} ERROR, {} GROUP",
                totalRecords, userRecords, requestRecords, errorRecords, groupRecords);

        log.debug("Computing statistics");
        ret.computeStat();

        return ret;
    }

    /**
     * Process a regular non-compressed file
     */
    private void parseRegularFile(SimulationContext context) throws IOException {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                FileChannel channel = randomAccessFile.getChannel()) {

            log.debug("File size: {} bytes", channel.size());
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

            // Read and process the RUN record first
            processRunRecord(channel, buffer, context);

            // Process all remaining records
            processRemainingRecords(channel, buffer, context);
        }
    }

    /**
     * Process a gzipped file
     */
    private void parseGzippedFile(SimulationContext context) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis);
                GZIPInputStream gzis = new GZIPInputStream(bis);
                ReadableByteChannel channel = Channels.newChannel(gzis)) {

            log.debug("Processing gzipped file");
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

            // Process the RUN record first
            processRunRecord(channel, buffer, context);

            // Process all remaining records
            processRemainingRecords(channel, buffer, context);
        }
    }

    /**
     * Process the RUN record at the beginning of the file
     * Modified to accept ReadableByteChannel instead of only FileChannel
     */
    private void processRunRecord(ReadableByteChannel channel, ByteBuffer buffer, SimulationContext context)
            throws IOException {
        int bytesRead = channel.read(buffer);
        if (bytesRead <= 0) {
            throw new IOException("Empty file or failed to read simulation log");
        }
        log.debug("Initial read: {} bytes", bytesRead);

        buffer.flip();

        // Check if first byte indicates a RUN record
        if (buffer.remaining() > 0 && buffer.get(0) == RUN_RECORD) {
            // Skip the record type byte
            buffer.get();
            log.debug("Processing RUN record at start of file");

            try {
                processRunRecord(buffer);
                totalRecords++;

                // Set simulation information in the context
                context.setSimulationName(simulationClassName);
                context.setStart(simulationStart);
                if (scenarioNames.length > 0) {
                    context.setScenarioName(scenarioNames[0]);
                }

                log.info("Parsed simulation: {}, scenarios: {}, start: {}",
                        simulationClassName, scenarioNames.length, simulationStart);
                for (int i = 0; i < scenarioNames.length; i++) {
                    log.debug("Scenario {}: {}", i, scenarioNames[i]);
                }
            } catch (IOException e) {
                throw new IOException("Failed to process RUN record in simulation log: " + e.getMessage(), e);
            }
        } else {
            log.error("Invalid file format: first byte is {} (expected {})",
                    buffer.remaining() > 0 ? buffer.get(0) : "none", RUN_RECORD);
            throw new IOException("File does not start with a RUN record or is not a valid binary simulation log");
        }

        buffer.compact();
    }

    /**
     * Process all records after the initial RUN record
     * Modified to accept ReadableByteChannel instead of only FileChannel
     */
    private void processRemainingRecords(ReadableByteChannel channel, ByteBuffer buffer, SimulationContext context)
            throws IOException {
        log.debug("Processing remaining records");

        while (readAndProcessBuffer(channel, buffer, context)) {
            // Continue reading and processing until no more data
        }
    }

    private boolean readAndProcessBuffer(ReadableByteChannel channel, ByteBuffer buffer, SimulationContext context)
            throws IOException {
        // Read more data into the buffer
        int bytesRead = channel.read(buffer);

        if (bytesRead > 0) {
            log.trace("Read {} bytes from file", bytesRead);
        }

        // If we have no more data to read and buffer is empty, we're done
        if (bytesRead <= 0 && buffer.position() == 0) {
            return false;
        }

        buffer.flip();
        processBuffer(buffer, context);
        buffer.compact();

        return true;
    }

    /**
     * Process as many complete records as possible from the buffer
     */
    private void processBuffer(ByteBuffer buffer, SimulationContext context) throws IOException {
        int initialPosition = buffer.position();
        int lastSafePosition = initialPosition;

        while (buffer.remaining() > 0) {
            int recordStart = buffer.position();
            ProcessResult result = tryProcessRecord(buffer, context, recordStart);

            if (result.processed) {
                lastSafePosition = buffer.position();
            } else {
                buffer.position(recordStart);
                break;
            }
        }

        // Reset to last safe position to retain any partial records
        buffer.position(lastSafePosition);
    }

    private record ProcessResult(boolean processed) {
    }

    private ProcessResult tryProcessRecord(ByteBuffer buffer, SimulationContext context, int recordStart)
            throws IOException {
        if (buffer.remaining() < 1) {
            return new ProcessResult(false);
        }
        return new ProcessResult(processSingleRecord(buffer, context, recordStart));
    }

    /**
     * Process a single record from the current buffer position.
     * 
     * @param buffer      The buffer containing record data
     * @param context     The simulation context to update
     * @param recordStart The starting position of this record in the buffer
     * @return True if a record was successfully processed, false if more data is
     *         needed
     */
    private boolean processSingleRecord(ByteBuffer buffer, SimulationContext context, int recordStart)
            throws IOException {
        // Read record type without advancing position
        byte recordType = buffer.get(recordStart);

        // Check if we have a complete record
        if (!isCompleteRecord(recordType, buffer)) {
            return false;
        }

        // Advance past the record type byte
        buffer.get();

        try {
            // Process the record
            dispatchRecordByType(recordType, buffer, context);
            totalRecords++;
            return true;
        } catch (BufferUnderflowException e) {
            // If we hit an underflow, the record is incomplete
            log.debug("Incomplete record of type {} detected, waiting for more data", recordType);
            buffer.position(recordStart);
            return false;
        } catch (Exception e) {
            // Handle error but continue processing
            handleRecordProcessingError(recordType, buffer, e);
            return true;
        }
    }

    /**
     * Checks if a complete record of the given type can be read from the buffer
     * without modifying buffer position.
     */
    private boolean isCompleteRecord(byte recordType, ByteBuffer buffer) {
        int position = buffer.position();

        try {
            // Skip past the record type byte that was already read
            position++;

            // Check if we have enough bytes for this record type's metadata
            switch (recordType) {
                case RUN_RECORD:
                    // Need at least the string length for gatlingVersion
                    if (buffer.remaining() < 5)
                        return false; // 1 byte already read + 4 for int
                    int versionLength = buffer.getInt(position);
                    position += 4 + versionLength + 1; // int + string bytes + coder byte

                    // Check for simulationClassName
                    if (position + 4 > buffer.limit())
                        return false;
                    int classNameLength = buffer.getInt(position);
                    position += 4 + classNameLength + 1; // int + string bytes + coder byte

                    // Check for start timestamp (8 bytes)
                    return position + 8 <= buffer.limit();

                case USER_RECORD:
                    // scenarioIndex(4) + isStart(1) + timestamp(4)
                    return buffer.remaining() >= 10; // 1 byte already read + 9

                case REQUEST_RECORD:
                    // Start with groupCount
                    if (buffer.remaining() < 5)
                        return false; // 1 byte already read + 4 for groupCount
                    int groupCount = buffer.getInt(position);

                    // We can't fully validate without reading all the dynamic content
                    // So this is a best-effort check for the static parts
                    return buffer.remaining() >= 10 + groupCount * 4; // Basic structure minimum size

                case GROUP_RECORD:
                    return buffer.remaining() >= 18; // 1 byte already read + 17

                case ERROR_RECORD:
                    return buffer.remaining() >= 9; // 1 byte already read + 8

                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
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
     * Handle errors that occur while processing a record
     */
    private void handleRecordProcessingError(byte recordType, ByteBuffer buffer, Exception e) throws IOException {
        if (e instanceof IOException ioException) {
            throw ioException;
        }

        log.error("Error processing record type {}: {}", recordType, e.getMessage());
        if (log.isDebugEnabled()) {
            log.debug("Stack trace:", e);
        }

        // Clear buffer and try next chunk
        buffer.position(buffer.limit());

    }


    /**
     * Dispatch record processing based on record type
     */
    private void dispatchRecordByType(byte recordType, ByteBuffer buffer, SimulationContext context)
            throws IOException {
        switch (recordType) {
            case USER_RECORD:
                userRecords++;
                log.trace("Processing USER record #{}", userRecords);
                processUserRecord(buffer, context);
                break;
            case REQUEST_RECORD:
                requestRecords++;
                log.trace("Processing REQUEST record #{}", requestRecords);
                processRequestRecord(buffer, context);
                break;
            case ERROR_RECORD:
                errorRecords++;
                log.trace("Processing ERROR record #{}", errorRecords);
                processErrorRecord(buffer);
                break;
            case GROUP_RECORD:
                groupRecords++;
                log.trace("Processing GROUP record #{}", groupRecords);
                processGroupRecord(buffer);
                break;
            case RUN_RECORD:
                // We already processed the RUN record at the beginning
                log.warn("Unexpected additional RUN record found");
                throw new IOException("Unexpected additional RUN record found");
            default:
                // Data misalignment detected
                handleMisalignedData(recordType, buffer);
                break;
        }
    }

    /**
     * Handle misaligned data in the buffer
     */
    private void handleMisalignedData(byte recordType, ByteBuffer buffer) {
        if (recordType < 0) {
            // Most likely a cached string reference, buffer is misaligned
            log.warn("Buffer misalignment: Found string cache reference {} instead of record type", recordType);
        } else {
            log.warn("Unknown record type: {}, likely buffer misalignment", recordType);
        }

        // Try to recover by skipping to the next likely record boundary
        buffer.position(buffer.limit());
    }

    /**
     * Process the RUN record which contains simulation metadata
     */
    private void processRunRecord(ByteBuffer buffer) throws IOException {
        // Format based on RunMessageSerializer:
        // gatlingVersion, simulationClassName, start, runDescription, scenarioCount,
        // [scenarioNames], assertionCount, [assertions]

        String gatlingVersion = readString(buffer);
        log.debug("Gatling version: {}", gatlingVersion);

        simulationClassName = readString(buffer);
        simulationStart = buffer.getLong();
        String runDescription = readString(buffer);
        log.debug("Simulation: {}, start: {}, description: {}",
                simulationClassName, simulationStart, runDescription);

        int scenarioCount = buffer.getInt();
        log.debug("Number of scenarios: {}", scenarioCount);

        scenarioNames = new String[scenarioCount];
        for (int i = 0; i < scenarioCount; i++) {
            scenarioNames[i] = readString(buffer);
            log.debug("Scenario {}: {}", i, scenarioNames[i]);
        }

        // Skip assertions as they're not needed for our parser
        int assertionCount = buffer.getInt();
        log.debug("Number of assertions: {}", assertionCount);

        // Just move the buffer position past the assertions
        for (int i = 0; i < assertionCount; i++) {
            int byteLength = buffer.getInt();
            log.trace("Skipping assertion {} of size {} bytes", i, byteLength);
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

        log.trace("USER event: scenario={}, isStart={}, timestamp={}",
                scenarioName, isStart, timestamp);

        // Store scenario mapping
        userScenario.put(userId, scenarioName);

        // Update the context with user events
        if (isStart) {
            context.addUser(scenarioName);
            log.trace("Added user to scenario: {}", scenarioName);
        } else {
            context.endUser(scenarioName);
            log.trace("Ended user in scenario: {}", scenarioName);
        }
    }

    /**
     * Process a REQUEST record which contains request metrics
     * 
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
        long duration = endTimestamp - startTimestamp;

        log.trace("REQUEST: name={}, start={}, end={}, duration={}ms, success={}, message={}",
                requestName, startTimestamp, endTimestamp, duration, success,
                message.isEmpty() ? "[none]" : message);

        // Use first scenario if we can't determine the actual one
        String scenarioName = scenarioNames.length > 0 ? scenarioNames[0] : "default";

        // Add the request to the context
        context.addRequest(scenarioName, requestName, startTimestamp, endTimestamp, success);
    }

    /**
     * Process an ERROR record
     * 
     * @throws IOException
     */
    private void processErrorRecord(ByteBuffer buffer) throws IOException {
        // Format based on ErrorMessageSerializer:
        // message, timestamp

        String message = readCachedString(buffer);
        int relativeTimestamp = buffer.getInt();
        long timestamp = simulationStart + relativeTimestamp;

        log.debug("ERROR at {}: {}", timestamp, message);
        // We don't need to do anything with errors for now
    }

    /**
     * Process a GROUP record
     * 
     * @throws IOException
     */
    private void processGroupRecord(ByteBuffer buffer) throws IOException {
        // Format based on GroupMessageSerializer:
        // groupCount, [groups], startTimestamp, endTimestamp, cumulatedResponseTime,
        // success

        int groupCount = buffer.getInt();
        List<String> groupNames = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            groupNames.add(readCachedString(buffer));
        }

        int startTimeStamp = buffer.getInt(); // startTimestamp
        int endTimeStamp = buffer.getInt(); // endTimestamp
        int cumulatedResponseTime = buffer.getInt(); // cumulatedResponseTime
        byte success = buffer.get(); // success

        if (log.isTraceEnabled()) {
            log.trace("GROUP: names={}, start={}, end={}, cumulated={}, success={}",
                    String.join(">", groupNames),
                    startTimeStamp,
                    endTimeStamp,
                    cumulatedResponseTime,
                    success == 1);
        }
        // We don't need to do anything with groups for now
    }

    /**
     * Read a cached string from the buffer
     * Handles the string caching mechanism used in Gatling's binary format
     * 
     * @throws IOException
     */
    private String readCachedString(ByteBuffer buffer) throws IOException {
        int stringIndex = buffer.getInt();

        if (stringIndex >= 0) {
            // New string, read and cache it
            String str = readString(buffer);
            stringCache.put(stringIndex, str);
            log.trace("Cached new string at index {}: '{}'", stringIndex, str);
            return str;
        } else {
            // Cached string reference
            String cachedString = stringCache.get(-stringIndex);
            if (cachedString == null) {
                log.warn("Cache miss for string index: {}", -stringIndex);
                return "";
            }
            log.trace("Using cached string at index {}: '{}'", -stringIndex, cachedString);
            return cachedString;
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
        log.trace("Reading string of length: {}", length);

        if (length == 0) {
            return "";
        }

        // Check if we have enough bytes for the string content plus coder byte
        if (buffer.remaining() < length + 1) {
            throw new IOException("Buffer underflow when reading string data of length " + length +
                    " (only " + buffer.remaining() + " bytes available)");
        }

        byte[] stringBytes = new byte[length];
        buffer.get(stringBytes);

        // Check coder byte (encoding type)
        byte coder = buffer.get();
        log.trace("String coder: {}", coder);

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
        log.warn("Invalid scenario index: {}, using 'unknown'", index);
        return "unknown";
    }

    // Implementation of required abstract methods
    // These are mostly placeholders as the actual parsing happens in our custom
    // parse() method

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