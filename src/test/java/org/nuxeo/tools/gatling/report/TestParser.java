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
import java.util.Objects;

import org.junit.Assert;
import org.junit.Test;

public class TestParser {
    protected static final String SIM_SMALL_V2_1 = "simulation-small.log";

    protected static final String SIM_V2_3 = "simulation-v2.3.log";

    protected static final String SIM_SMALL_V3 = "simulation-small-v3.log";

    protected static final String SIM_SMALL_V3_2 = "simulation-small-v3.2.log";

    protected static final String SIM_SMALL_V3_3 = "simulation-small-v3.3.log";

    protected static final String SIM_V3_2_GZ = "simulation-v3.2.log.gz";

    protected static final String SIM_GZ = "simulation-1.log.gz";

    protected static final String SIM_SMALL_V3_4 = "simulation-small-v3.4.log";

    protected static final String SIM_V3_5_GZ = "simulation-v3.5.log.gz";

    protected static final String SIM_V3_10_GZ = "simulation-v3.10.log.gz";

    protected static final String SIM_V3_13 = "simulation-v3.13.log";

    protected static final String SIM_V3_13_GZ = "simulation-v3.13.log.gz";

    @Test
    public void parseSimpleSimulationVersion21() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_SMALL_V2_1)).parse();
        Assert.assertEquals("sim80reindexall", ret.getSimulationName());
        Assert.assertEquals(2, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseCompressedSimulation() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_GZ)).parse();
        Assert.assertEquals("sim50bench", ret.getSimulationName());
        Assert.assertEquals(2464, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseSimulationVersion23() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_V2_3)).parse();
        Assert.assertEquals("sim20createdocuments", ret.getSimulationName());
        Assert.assertEquals(1000, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseSimpleSimulationVersion3() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_SMALL_V3)).parse();
        Assert.assertEquals("sim80reindexall", ret.getSimulationName());
        Assert.assertEquals(2, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseSimpleSimulationVersion32Small() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_SMALL_V3_2)).parse();
        Assert.assertEquals("sim80reindexall", ret.getSimulationName());
        Assert.assertEquals(2, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseSimpleSimulationVersion33Small() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_SMALL_V3_3)).parse();
        Assert.assertEquals("sim80reindexall", ret.getSimulationName());
        Assert.assertEquals(2, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseSimpleSimulationVersion32() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_V3_2_GZ)).parse();
        Assert.assertEquals("sim50bench", ret.getSimulationName());
        Assert.assertEquals(16095, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseSimpleSimulationVersion34Small() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_SMALL_V3_4)).parse();
        Assert.assertEquals("testsimulationspec", ret.getSimulationName());
        Assert.assertEquals(31, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseSimpleSimulationVersion35() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_V3_5_GZ)).parse();
        Assert.assertEquals("sim50bench", ret.getSimulationName());
        Assert.assertEquals(16095, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseSimpleSimulationVersion310() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_V3_10_GZ)).parse();
        Assert.assertEquals("sim50bench", ret.getSimulationName());
        Assert.assertEquals(16029, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseSimulationVersion313() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_V3_13)).parse();
        Assert.assertEquals("org.heigit.ors.benchmark.IsochronesLoadTest", ret.getSimulationName());
        Assert.assertEquals(3, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    @Test
    public void parseSimpleSimulationVersion313GZ() throws Exception {
        SimulationContext ret = ParserFactory.getParser(getResourceFile(SIM_V3_13_GZ)).parse();
        Assert.assertEquals("org.heigit.ors.benchmark.IsochronesLoadTest", ret.getSimulationName());
        Assert.assertEquals(3, ret.getSimStat().getCount());
        Assert.assertTrue(ret.toString().contains("_all"));
    }

    protected File getResourceFile(String filename) throws FileNotFoundException {
        ClassLoader classLoader = getClass().getClassLoader();
        if (classLoader.getResource(filename) == null) {
            throw new FileNotFoundException(filename);
        }
        return new File(Objects.requireNonNull(classLoader.getResource(filename)).getFile());
    }

}
