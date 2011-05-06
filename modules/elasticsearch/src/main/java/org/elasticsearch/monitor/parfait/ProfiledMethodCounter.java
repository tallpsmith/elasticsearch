/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.monitor.parfait;

import com.custardsource.parfait.MonitoredCounter;
import com.custardsource.parfait.timing.EventMetricCollector;
import org.elasticsearch.common.aopalliance.intercept.MethodInterceptor;
import org.elasticsearch.common.aopalliance.intercept.MethodInvocation;

class ProfiledMethodCounter implements MethodInterceptor {
    private final String eventGroup;
    private final MonitoredCounter counter;
    private final EventMetricCollector collector;
    private final String action;

    ProfiledMethodCounter(EventMetricCollector collector, MonitoredCounter counter, String eventGroup, String action) {
        this.counter = counter;
        this.eventGroup = eventGroup;
        this.action = action;
        this.collector = collector;
    }

    @Override public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        collector.startTiming(eventGroup, action);
        try {
            counter.inc();
            return methodInvocation.proceed();
        } finally {
            collector.stopTiming();
        }
    }
}
