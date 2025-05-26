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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DiffContext {
    protected final SimulationContext ref;

    protected final SimulationContext challenger;

    protected final long max;

    protected List<String> scripts;

    // Constants to avoid duplication
    private static final String WIN = "win";
    private static final String LOOSE = "loose";
    private static final String FORMAT_PERCENT = "%+.2f";
    private static final Locale LOCALE = Locale.ENGLISH;

    public DiffContext(List<SimulationContext> stats) {
        ref = stats.get(0);
        challenger = stats.get(1);
        max = Math.max(ref.simStat.max, challenger.simStat.max);
    }

    public DiffContext setScripts(List<String> scripts) {
        this.scripts = scripts;
        return this;
    }

    public List<DiffRequestStat> getDiffRequests() {
        List<DiffRequestStat> ret = new ArrayList<>(ref.reqStats.size());
        for (RequestStat refStat : ref.getRequests()) {
            RequestStat challengerStat = challenger.reqStats.get(refStat.request);
            if (challengerStat == null) {
                challengerStat = new RequestStat(refStat.simulation, refStat.scenario, refStat.request, 0, null);
                challengerStat.add(0, 0, false);
                challengerStat.computeStat(0);
            }
            ret.add(new DiffRequestStat(refStat, challengerStat));
        }
        Collections.reverse(ret);
        return ret;
    }

    public String avgPercent() {
        return String.format(LOCALE, FORMAT_PERCENT, (challenger.simStat.avg * 100.0 / ref.simStat.avg) - 100.0);
    }

    public String avgClass() {
        if (challenger.simStat.avg < ref.simStat.avg) {
            return WIN;
        }
        return LOOSE;
    }

    public String rpsPercent() {
        return String.format(LOCALE, FORMAT_PERCENT, (challenger.simStat.rps * 100.0 / ref.simStat.rps) - 100.0);
    }

    public String rpsClass() {
        if (challenger.simStat.rps > ref.simStat.rps) {
            return WIN;
        }
        return LOOSE;
    }

    public String durationDiff() {
        double diff = challenger.simStat.duration - ref.simStat.duration;
        return String.format(LOCALE, FORMAT_PERCENT, diff);
    }

    public String durationClass() {
        if (challenger.simStat.duration < ref.simStat.duration) {
            return WIN;
        }
        return LOOSE;
    }

    public String requestCountDiff() {
        double percent = ((double) challenger.simStat.count / ref.simStat.count * 100.0) - 100.0;
        return String.format(LOCALE, FORMAT_PERCENT, percent);
    }

    public String requestCountClass() {
        return challenger.simStat.count >= ref.simStat.count ? WIN : LOOSE;
    }

    public String errorCountDiff() {
        return String.format(LOCALE, "%+d", challenger.simStat.errorCount - ref.simStat.errorCount);
    }

    public String errorCountClass() {
        if (challenger.simStat.errorCount < ref.simStat.errorCount) {
            return WIN;
        }
        return LOOSE;
    }

    public String maxUsersDiff() {
        double percent = ((double) challenger.simStat.maxUsers / ref.simStat.maxUsers * 100.0) - 100.0;
        return String.format(LOCALE, FORMAT_PERCENT, percent);
    }

    public String maxUsersClass() {
        // More concurrent users could be better or worse depending on perspective
        // Here we assume more users is better (higher load testing capability)
        return challenger.simStat.maxUsers >= ref.simStat.maxUsers ? WIN : LOOSE;
    }

}
