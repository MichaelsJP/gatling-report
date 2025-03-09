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
 *     Benoit Delbosc
 */
package org.nuxeo.tools.gatling.report;

import static java.lang.Math.max;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SimulationContext {
    public static final String ALL_REQUESTS = "_all";

    protected final Float apdexT;

    protected final String filePath;

    protected final RequestStat simStat;

    protected final Map<String, RequestStat> reqStats = new HashMap<>();

    protected final Map<String, CountMax> users = new HashMap<>();

    protected String simulationName;

    protected String scenarioName;

    protected List<String> scripts = new ArrayList<>();

    protected int maxUsers;

    protected long start;

    public SimulationContext(String filePath, Float apdexT) {
        this.filePath = filePath;
        this.simStat = new RequestStat(ALL_REQUESTS, ALL_REQUESTS, ALL_REQUESTS, 0, apdexT);
        this.apdexT = apdexT;
    }

    public String getSimulationName() {
        return simulationName;
    }

    public void setSimulationName(String name) {
        this.simulationName = name;
        simStat.setSimulationName(name);
    }

    public RequestStat getSimStat() {
        return simStat;
    }

    public List<RequestStat> getRequests() {
        List<RequestStat> ret = new ArrayList<>(reqStats.values());
        ret.sort((a, b) -> (int) (1000 * (a.avg - b.avg)));
        return ret;
    }

    public void addRequest(String scenario, String requestName, long start, long end, boolean success) {
        RequestStat request = reqStats.computeIfAbsent(requestName,
                n -> new RequestStat(simulationName, scenario, n, this.start, apdexT));
        request.add(start, end, success);
        simStat.add(start, end, success);
    }

    public void computeStat() {
        maxUsers = users.values().stream().mapToInt(CountMax::getMax).sum();
        simStat.computeStat(maxUsers);
        reqStats.values()
                .forEach(request -> {
                    CountMax userCount = users.get(request.scenario);
                    int maxUsersForScenario = (userCount != null) ? userCount.maximum : 1;
                    request.computeStat(simStat.duration, maxUsersForScenario);
                });
    }

    public void setScenarioName(String name) {
        this.scenarioName = name;
        simStat.setScenario(name);
    }

    public void setStart(long start) {
        this.start = start;
        simStat.setStart(start);
    }

    public SimulationContext setScripts(List<String> scripts) {
        this.scripts = scripts;
        return this;
    }

    @Override
    public String toString() {
        return simStat.toString() + "\n"
                + getRequests().stream().map(RequestStat::toString).collect(Collectors.joining("\n"));
    }

    public SimulationContext setMaxUsers(int maxUsers) {
        this.maxUsers = maxUsers;
        return this;
    }

    public void addUser(String scenario) {
        CountMax count = users.computeIfAbsent(scenario, k -> new CountMax());
        count.incr();
    }

    public void endUser(String scenario) {
        CountMax count = users.get(scenario);
        if (count != null) {
            count.decr();
        }
    }

    class CountMax {
        int current = 0, maximum = 0;

        public void incr() {
            current += 1;
            maximum = max(current, maximum);
        }

        public void decr() {
            current -= 1;
        }

        public int getMax() {
            return maximum;
        }
    }

    /**
     * Print a summary of the simulation statistics to System.out
     */
    public void printStats() {
        System.out.println("\n========== SIMULATION STATISTICS ==========");
        System.out.println("File: " + filePath);
        System.out.println("Simulation: " + simulationName);
        System.out.println("Scenario: " + scenarioName);

        // Format start time as a readable date
        String startDate = simStat.startDate != null ? simStat.startDate : "Unknown";
        System.out.println("Start time: " + startDate);
        System.out.println("Duration: " + (simStat.duration / 1000.0) + " seconds");

        // User statistics
        System.out.println("\n----- User Statistics -----");
        System.out.println("Maximum concurrent users: " + maxUsers);

        if (!users.isEmpty()) {
            System.out.println("Users per scenario:");
            users.entrySet().forEach(entry -> System.out
                    .println("  - " + entry.getKey() + ": " + entry.getValue().getMax() + " max concurrent"));
        }

        // Request statistics
        System.out.println("\n----- Request Statistics -----");
        System.out.println("Total requests: " + simStat.count);
        System.out.println("Success rate: " + String.format("%.2f%%", (simStat.successCount * 100.0 / simStat.count)));
        System.out.println("Failed requests: " + simStat.errorCount);
        System.out.println("Average response time: " + String.format("%.2f ms", simStat.avg));
        System.out.println("Min/Max response time: " + simStat.min + "/" + simStat.max + " ms");
        System.out.println("Standard deviation: " + String.format("%.2f ms", (double) simStat.stddev));
        System.out.println("95th percentile: " + String.format("%.2f ms", (double) simStat.p95));
        System.out.println("99th percentile: " + String.format("%.2f ms", (double) simStat.p99));

        // Throughput
        double rps = simStat.count / simStat.duration; // Requests per second
        System.out.println("Throughput: " + String.format("%.2f requests/second", rps));

        // Individual request statistics
        if (!reqStats.isEmpty()) {
            System.out.println("\n----- Individual Requests -----");
            System.out.println(String.format("%-40s %10s %10s %10s %8s %8s %8s",
                    "Request", "Count", "Success %", "Avg (ms)", "Min", "Max", "Std Dev"));
            System.out.println(String.format("%-40s %10s %10s %10s %8s %8s %8s",
                    "-------", "-----", "--------", "-------", "---", "---", "-------"));

            // Sort requests by average response time for better readability
            List<RequestStat> sortedRequests = new ArrayList<>(reqStats.values());
            sortedRequests.sort((a, b) -> (int) (1000 * (b.avg - a.avg))); // Sort by average time (descending)

            for (RequestStat stat : sortedRequests) {
                double successRate = stat.successCount * 100.0 / stat.count;
                System.out.println(String.format("%-40s %10d %9.2f%% %10.2f %8d %8d %8.2f",
                        truncate(stat.request, 40),
                        stat.count,
                        successRate,
                        stat.avg,
                        stat.min,
                        stat.max,
                        (double) stat.stddev));
            }
        }

        System.out.println("\n===========================================");
    }

    /**
     * Helper method to truncate long strings
     */
    private String truncate(String str, int length) {
        if (str.length() <= length) {
            return str;
        }
        return str.substring(0, length - 3) + "...";
    }

}
