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
import com.google.common.base.Function;
import com.google.common.collect.MapMaker;
import org.elasticsearch.common.aopalliance.intercept.MethodInterceptor;
import org.elasticsearch.common.aopalliance.intercept.MethodInvocation;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.shard.ShardId;

import java.util.concurrent.ConcurrentMap;

class ProfiledShardBasedMethodCounter implements MethodInterceptor {
    private final String eventGroup;
    private final EventMetricCollector collector;
    private final String action;
    private final ConcurrentMap<ShardId, MonitoredCounter> counterMap;

    ProfiledShardBasedMethodCounter(final ParfaitService parfaitService, String eventGroup, final String action) {
        this.eventGroup = eventGroup;
        this.action = action;
        this.collector = parfaitService.getEventTimer().getCollector();
        this.counterMap = new MapMaker().makeComputingMap(new Function<ShardId, MonitoredCounter>() {
            @Override public MonitoredCounter apply(ShardId from) {
                return parfaitService.forShard(from).count(action);
            }
        });
    }

    @Override public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        collector.startTiming(eventGroup, action);
        try {
            Engine engine = (Engine)methodInvocation.getThis();
            counterMap.get(engine.shardId()).inc();
            return methodInvocation.proceed();
        } finally {
            collector.stopTiming();
        }
    }
}
