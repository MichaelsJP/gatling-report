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

import java.util.Locale;

public class DiffRequestStat {
    protected final RequestStat refR;

    protected final RequestStat challengerR;

    public DiffRequestStat(RequestStat refStat, RequestStat challengerStat) {
        refR = refStat;
        challengerR = challengerStat;
    }

    // Constants to avoid duplication
    private static final String ZERO_PERCENT = "+0.00";
    private static final String FORMAT_PERCENT = "%+.2f";
    private static final String WIN = "win";
    private static final String LOOSE = "loose";
    private static final Locale LOCALE = Locale.ENGLISH;

    public String minPercent() {
        if (refR.min == 0) {
            return ZERO_PERCENT;
        }
        return String.format(LOCALE, FORMAT_PERCENT, (challengerR.min * 100.0 / refR.min) - 100.0);
    }

    public String minClass() {
        if (challengerR.min <= refR.min) {
            return WIN;
        }
        return LOOSE;
    }

    public String avgRPercent() {
        if (refR.avg == 0) {
            return ZERO_PERCENT;
        }
        return String.format(LOCALE, FORMAT_PERCENT, (challengerR.avg * 100.0 / refR.avg) - 100.0);
    }

    public String avgRClass() {
        if (challengerR.avg <= refR.avg) {
            return WIN;
        }
        return LOOSE;
    }

    public String maxPercent() {
        if (refR.max == 0) {
            return ZERO_PERCENT;
        }
        return String.format(LOCALE, FORMAT_PERCENT, (challengerR.max * 100.0 / refR.max) - 100.0);
    }

    public String maxClass() {
        if (challengerR.max <= refR.max) {
            return WIN;
        }
        return LOOSE;
    }

    public String p50Percent() {
        if (refR.p50 == 0) {
            return ZERO_PERCENT;
        }
        return String.format(LOCALE, FORMAT_PERCENT, (challengerR.p50 * 100.0 / refR.p50) - 100.0);
    }

    public String p50Class() {
        if (challengerR.p50 <= refR.p50) {
            return WIN;
        }
        return LOOSE;
    }

    public String p95Percent() {
        if (refR.p95 == 0) {
            return ZERO_PERCENT;
        }
        return String.format(LOCALE, FORMAT_PERCENT, (challengerR.p95 * 100.0 / refR.p95) - 100.0);
    }

    public String p95Class() {
        if (challengerR.p95 <= refR.p95) {
            return WIN;
        }
        return LOOSE;
    }

    public String p99Percent() {
        if (refR.p99 == 0) {
            return ZERO_PERCENT;
        }
        return String.format(LOCALE, FORMAT_PERCENT, (challengerR.p99 * 100.0 / refR.p99) - 100.0);
    }

    public String p99Class() {
        if (challengerR.p99 <= refR.p99) {
            return WIN;
        }
        return LOOSE;
    }

    public String errorDiff() {
        return String.format(LOCALE, "%+d", challengerR.errorCount - refR.errorCount);
    }

    public String errorClass() {
        if (challengerR.errorCount <= refR.errorCount) {
            return WIN;
        }
        return LOOSE;
    }
    
    public String countDiff() {
        if (refR.count == 0) {
            return ZERO_PERCENT;
        }
        return String.format(LOCALE, FORMAT_PERCENT, (challengerR.count * 100.0 / refR.count) - 100.0);
    }
    
    public String countClass() {
        return challengerR.count >= refR.count ? WIN : LOOSE;
    }
}
