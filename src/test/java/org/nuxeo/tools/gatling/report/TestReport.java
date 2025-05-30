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
 *     bdelbosc
 */
package org.nuxeo.tools.gatling.report;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Test;

public class TestReport {

    protected static final String SIM_GZ = "simulation-1.log.gz";
    protected static final String SIM_GZ_313 = "simulation-v3.13.log.gz";

    protected static final List<String> SIMS_GZ = Arrays.asList("simulation.log.1.gz", "simulation.log.2.gz",
            "simulation.log.3.gz", "simulation.log.4.gz", "simulation-v3.13.log.gz");

    @Test
    public void generateSimulationReport() throws Exception {
        List<SimulationContext> stats = Collections.singletonList(ParserFactory.getParser(getResourceFile(SIM_GZ)).parse());
        Writer writer = new StringWriter();
        String reportPath = new Report(stats).setWriter(writer).create();
        Assert.assertTrue(reportPath.endsWith("index.html"));
        String string = writer.toString();
        Assert.assertTrue(writer.toString().contains("sim50bench"));
    }

    @Test
    public void generateSimulationReport313() throws Exception {

        List<SimulationContext> stats = Collections
                .singletonList(ParserFactory.getParser(getResourceFile(SIM_GZ_313)).parse());
        Writer writer = new StringWriter();
        String reportPath = new Report(stats).setWriter(writer).create();
        Assert.assertTrue(reportPath.endsWith("index.html"));
        Assert.assertTrue(writer.toString().contains("org.heigit.ors.benchmark.IsochronesLoadTest"));
    }

    @Test
    public void generateTrendReport() throws Exception {
        List<SimulationContext> stats = new ArrayList<>(SIMS_GZ.size());
        SIMS_GZ.forEach(file -> {
            try {
                stats.add(ParserFactory.getParser(getResourceFile(file)).parse());
            } catch (IOException e) {
                Assert.fail("Can not parse: " + file);
            }
        });
        Writer writer = new StringWriter();
        String reportPath = new Report(stats).setWriter(writer).create();
        Assert.assertTrue(reportPath.endsWith("index.html"));
        Assert.assertTrue(writer.toString().contains("Trend report"));
    }

    protected File getResourceFile(String filename) throws FileNotFoundException {
        ClassLoader classLoader = getClass().getClassLoader();
        if (classLoader.getResource(filename) == null) {
            throw new FileNotFoundException(filename);
        }
        return new File(Objects.requireNonNull(classLoader.getResource(filename)).getFile());
    }

}
