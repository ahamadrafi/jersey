/*
 * Copyright (c) 2013, 2021 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.server.internal.monitoring;

import java.util.concurrent.TimeUnit;

import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.internal.monitoring.core.ReservoirConstants;
import org.glassfish.jersey.server.internal.monitoring.core.UniformTimeReservoir;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests of {@link TimeWindowStatisticsImpl}.
 *
 * @author Miroslav Fuksa
 * @author Stepan Vavra
 */
public class TimeWindowStatisticsImplTest {

    private static final int COLLISION_BUFFER_POWER = 3;
    private static final double DELTA = 0.0001;

    @Test
    public void jvmLoaded() {
        assertEquals(COLLISION_BUFFER_POWER, ReservoirConstants.COLLISION_BUFFER_POWER);
        assertEquals(8, ReservoirConstants.COLLISION_BUFFER);
    }

    @Test
    public void test() {
        final long now = System.currentTimeMillis();
        final TimeWindowStatisticsImpl.Builder<Long> builder = new TimeWindowStatisticsImpl.Builder<>(
                new SlidingWindowTimeReservoir(1000L, TimeUnit.MILLISECONDS, now, TimeUnit.MILLISECONDS));
        builder.addRequest(now, 30L);
        builder.addRequest(now + 300, 100L);
        builder.addRequest(now + 600, 150L);
        builder.addRequest(now + 800, 15L);
        builder.addRequest(now + 999, 60L);
        builder.addRequest(now + 1000, 95L);

        check(builder, now + 1000, 6, 15, 150, 75, 6);
        builder.addRequest(now + 1001, 999L);
        // the original implementation was supposed to trim the first request, we can only guess why it didn't ...
        check(builder, now + 1001, 6, 15, 999, 236, 6);
    }

    @Test
    public void test10() {
        final long now = 0;
        final TimeWindowStatisticsImpl.Builder<Long> builder
                = new TimeWindowStatisticsImpl.Builder<>(
                new SlidingWindowTimeReservoir(10000, TimeUnit.MILLISECONDS, now, TimeUnit.MILLISECONDS));
        builder.addRequest(now, 30L);
        builder.addRequest(now + 300, 100L);
        builder.addRequest(now + 600, 150L);
        builder.addRequest(now + 800, 15L);
        builder.addRequest(now + 999, 60L);
        builder.addRequest(now + 1000, 95L);
        builder.addRequest(now + 8001, 600L);

        // check unfinished interval
        check(builder, now + 8001, 7, 15, 600, 150, 0.8748906);

        // the original implementation used chunks for time units and the metrics calculation
        // was accurate only when aligned with the chunks; now, we're accurate as possible which
        // is why we don't need to use ratio (to adjust the count of the request in the last chunk
        check(builder, now + 10900, 3, 60, 600, 251, 0.3);
        // the original calculation left minimum as '15' which collides with the api doc
        check(builder, now + 11000, 2, 95, 600, 347, 0.2);

    }

    /**
     * This test shows that current implementation of {@link org.glassfish.jersey.server.monitoring.TimeWindowStatistics} is able
     * to process information that happened before the last update time.
     */
    @Test
    public void testRequestInPast() {
        final long now = 0;
        final TimeWindowStatisticsImpl.Builder<Long> builder
                = new TimeWindowStatisticsImpl.Builder<>(
                new SlidingWindowTimeReservoir(1000, TimeUnit.MILLISECONDS, now, TimeUnit.MILLISECONDS));
        builder.addRequest(now, 40L);
        builder.addRequest(now + 1000, 30L);
        // this is a request in past which will actually reuse the time 'now + 1000'
        builder.addRequest(now + 100, 10L);

        check(builder, now + 1000, 3, 10, 40, 26, 3);

        // this request in past is so old that it doesn't even fit into the window
        builder.addRequest(now + 100, 0L);
        builder.addRequest(now + 1200, 20L);

        check(builder, now + 1201, 2, 20, 30, 25, 2);

        // snapshot retrieval in past does return values in past; in fact, time 'now + 1201' is used
        check(builder, now + 1000, 2, 20, 30, 25, 2);

    }

    @Test
    public void test3s() {
        final long now = 0;
        final TimeWindowStatisticsImpl.Builder<Long> builder
                = new TimeWindowStatisticsImpl.Builder<>(
                new SlidingWindowTimeReservoir(3000, TimeUnit.MILLISECONDS, now, TimeUnit.MILLISECONDS));
        builder.addRequest(now, 99L);
        builder.addRequest(now + 300, 98L);
        builder.addRequest(now + 600, 1L);
        builder.addRequest(now + 1000, 96L);
        builder.addRequest(now + 1500, 95L);
        builder.addRequest(now + 2500, 3L);
        // ... above should be ignored

        builder.addRequest(now + 3500, 90L);
        builder.addRequest(now + 3900, 4L);
        builder.addRequest(now + 3900, 80L);
        builder.addRequest(now + 4200, 92L);
        builder.addRequest(now + 4900, 15L);
        builder.addRequest(now + 5300, 8L);
        builder.addRequest(now + 5600, 50L);

        check(builder, now + 6001, 7, 4, 92, 48, 2.333333);
    }

    @Test
    public void testLongPause() {
        final long now = 0;
        final TimeWindowStatisticsImpl.Builder<Long> builder = new TimeWindowStatisticsImpl.Builder<>(
                new SlidingWindowTimeReservoir(60, TimeUnit.SECONDS, now, TimeUnit.MILLISECONDS));
        builder.addRequest(now, 99L);
        final long time = now + 1000 * 60 * 60 * 23;
        builder.addRequest(time, 95L);
        builder.addRequest(time + 5, 5L);
        check(builder, time + 20000, 2, 5, 95, 50, 0.03333);
    }

    @Test
    public void testMultipleRequestsAtTheSameTime() {
        final long now = 0;
        final TimeWindowStatisticsImpl.Builder<Long> builder = new TimeWindowStatisticsImpl.Builder<>(
                new SlidingWindowTimeReservoir(1, TimeUnit.SECONDS, now, TimeUnit.MILLISECONDS));
        // put multiple requests at the beginning so that even the COLLISION_BUFFER bounds is tested
        builder.addRequest(now, 10L);
        builder.addRequest(now, 20L);
        builder.addRequest(now, 30L);
        builder.addRequest(now, 40L);

        builder.addRequest(now + 1, 50L);
        // put multiple requests in the middle of the window
        builder.addRequest(now + 500, 60L);
        builder.addRequest(now + 500, 70L);
        check(builder, now + 500, 7, 10, 70, 40, 14);

        // put multiple requests at the end of the window
        builder.addRequest(now + 1000, 80L);
        builder.addRequest(now + 1000, 90L);
        check(builder, now + 1000, 9, 10, 90, 50, 9);

        // at 'now + 1001' all the requests from 'now' should be gone
        check(builder, now + 1001, 5, 50, 90, 70, 5);
    }

    @Test
    public void testExhaustiveRequestsAtTheSameTime() {
        final long now = 0;
        final TimeWindowStatisticsImpl.Builder<Long> builder = new TimeWindowStatisticsImpl.Builder<>(
                new SlidingWindowTimeReservoir(1, TimeUnit.SECONDS, now, TimeUnit.MILLISECONDS));
        // put multiple requests at the beginning so that even the COLLISION_BUFFER bounds is tested
        for (int i = 0; i < ReservoirConstants.COLLISION_BUFFER; ++i) {
            builder.addRequest(now, 10L);
        }
        // add one more request which should be visible at 'now + 1001'
        builder.addRequest(now + 1, 10L);

        // put multiple requests in the middle of the window
        for (int i = 0; i < ReservoirConstants.COLLISION_BUFFER; ++i) {
            builder.addRequest(now + 500, 10L);
        }
        check(builder, now + 500, ReservoirConstants.COLLISION_BUFFER * 2 + 1, 10, 10, 10, ReservoirConstants.COLLISION_BUFFER * 2 * 2 + 1 * 2);

        // put multiple requests at the end of the window
        for (int i = 0; i < ReservoirConstants.COLLISION_BUFFER; ++i) {
            builder.addRequest(now + 1000, 10L);
        }

        check(builder, now + 1000, ReservoirConstants.COLLISION_BUFFER * 3 + 1, 10, 10, 10, ReservoirConstants.COLLISION_BUFFER * 3 + 1);

        // at 'now + 1001' all the requests from 'now' should be gone
        check(builder, now + 1001, ReservoirConstants.COLLISION_BUFFER * 2 + 1, 10, 10, 10, ReservoirConstants.COLLISION_BUFFER * 2 + 1);

        // at 'now + 1002' the one additional request we added is gone
        check(builder, now + 1002, ReservoirConstants.COLLISION_BUFFER * 2, 10, 10, 10, ReservoirConstants.COLLISION_BUFFER * 2);
    }

    /**
     * Tests JERSEY-2848
     */
    @Test
    public void testGapGreaterThanTimeWindowPause() {
        final long now = 0;
        final TimeWindowStatisticsImpl.Builder<Long> builder = new TimeWindowStatisticsImpl.Builder<>(
                new SlidingWindowTimeReservoir(10, TimeUnit.SECONDS, now, TimeUnit.MILLISECONDS));
        builder.addRequest(now, 91L);
        builder.addRequest(now + 1000, 92L);
        builder.addRequest(now + 2000, 93L);

        // we need to add the time of last request + the whole time windows pause + additional time that is greater than unit time
        // which is 1000
        final long time = now + 2000 + 10000 + 1001;

        // this request addition causes the queue to reset; however, the original implementation didn't reset the total count
        // and total duration; as a result, the stats in that window became corrupted
        builder.addRequest(time, 94L);

        builder.addRequest(time + 1000, 95L);
        builder.addRequest(time + 2000, 96L);

        check(builder, time + 3000, 3, 94, 96, 95, 0.3);

        // this line would pass before JERSEY-2848 was fixed; apparently, the values in this window became corrupted
        // check(builder, time + 3000, 5, 94, 96, 93, 0.5);
    }

    private void check(final TimeWindowStatisticsImpl.Builder builder,
                       final long buildTime,
                       final int totalCount,
                       final int minimumExecTime,
                       final int maximumExecTime,
                       final long average,
                       final double requestsPerSecond) {
        final TimeWindowStatisticsImpl stat = builder.build(buildTime);

        assertEquals("Total count does not match!", totalCount, stat.getRequestCount());
        assertEquals("Min exec time does not match!", minimumExecTime, stat.getMinimumDuration());
        assertEquals("Max exec time does not match!", maximumExecTime, stat.getMaximumDuration());
        assertEquals("Average exec time does not match!", average, stat.getAverageDuration());
        assertEquals("Requests per seconds does not match!", requestsPerSecond, stat.getRequestsPerSecond(), DELTA);
    }

    @Test
    public void testGeneric() {
        final TimeWindowStatisticsImpl.Builder<Long> builder = new TimeWindowStatisticsImpl.Builder<>(
                new SlidingWindowTimeReservoir(10, TimeUnit.SECONDS, 0, TimeUnit.MILLISECONDS));
        for (int i = 0; i < 100; i++) {
            final int requestTime = i * 10000;
            builder.addRequest(requestTime + 1, (long) i);
            for (int j = 11; j < 100; j++) {
                try {
                    final TimeWindowStatisticsImpl stat = builder.build(requestTime + j * 100);
                    assertEquals(1, stat.getRequestCount());
                    assertEquals(i, stat.getMinimumDuration());
                    assertEquals(i, stat.getMaximumDuration());
                } catch (final AssertionError e) {
                    throw new AssertionError("i=" + i + ", j=" + j, e);
                }
            }
        }
    }

    @Test
    public void testUnlimited() {
        final TimeWindowStatisticsImpl.Builder<Long> builder = new TimeWindowStatisticsImpl.Builder<>(
                new UniformTimeReservoir(0, TimeUnit.MILLISECONDS));

        check(builder, 0, 0, 0, 0, 0, 0);
        check(builder, 10000, 0, 0, 0, 0, 0);

        builder.addRequest(0, 10L);

        check(builder, 50, 1, 10, 10, 10, 20.0);

        builder.addRequest(100 + 300, 20L);
        builder.addRequest(1000 + 600, 30L);
        builder.addRequest(1587 + 800, 40L);
        builder.addRequest(5544 + 999, 60L);
        builder.addRequest(9998 + 1000, 50L);

        check(builder, 10000, 6, 10, 60, 35, 0.6);
    }

}



