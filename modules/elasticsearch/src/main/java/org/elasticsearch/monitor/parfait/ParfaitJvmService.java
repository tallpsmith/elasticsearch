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

import com.custardsource.parfait.MonitorableRegistry;
import com.custardsource.parfait.PollingMonitoredValue;
import com.custardsource.parfait.ValueSemantics;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.util.List;

public class ParfaitJvmService extends AbstractComponent {
    private final int updateFrequency;
    private final MonitorableRegistry monitorableRegistry;
    private final ParfaitService parfaitService;

    @Inject public ParfaitJvmService(Settings settings, ParfaitService parfaitService) {
        super(settings);
        this.updateFrequency = settings.getAsInt("parfait.polling.frequency", 5000);
        this.parfaitService = parfaitService;
        this.monitorableRegistry = parfaitService.getMonitorableRegistry();

        registerMemoryMetrics();
        registerGCMetrics();
    }

    private void registerGCMetrics() {
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();

        for (final GarbageCollectorMXBean collectorMXBean : garbageCollectorMXBeans) {
            String name = "jvm.memory." + collectorMXBean.getName().toLowerCase();
            String description = Joiner.on(",").join(collectorMXBean.getMemoryPoolNames());

            registerNewPollingMonitoredValue(name + ".count", description, new Supplier<Long>() {
                @Override public Long get() {
                    return collectorMXBean.getCollectionCount();
                }
            }, ValueSemantics.MONOTONICALLY_INCREASING);
            registerNewPollingMonitoredValue(name + ".time", description, new Supplier<Long>() {
                @Override public Long get() {
                    return collectorMXBean.getCollectionTime();
                }
            }, ValueSemantics.MONOTONICALLY_INCREASING);
        }
    }

    private void registerMemoryMetrics() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        final MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();

        try {
            BeanInfo memoryUsageBeanInfo = Introspector.getBeanInfo(MemoryUsage.class);
            PropertyDescriptor[] propertyDescriptors = memoryUsageBeanInfo.getPropertyDescriptors();

            for (final PropertyDescriptor pd : propertyDescriptors) {
                if (pd.getPropertyType().equals(long.class)) {
                    Supplier<Long> propertySupplier = new Supplier<Long>() {
                        @Override public Long get() {
                            Method readMethod = pd.getReadMethod();
                            try {
                                Long value = (Long) readMethod.invoke(heapMemoryUsage);
                                return value;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    };

                    // This get registered automatically via construction
                    // TODO reconsider metric namespace and descriptions
                    registerNewPollingMonitoredValue("jvm.memory." + pd.getName(), "TODO", propertySupplier, ValueSemantics.CONSTANT);
                }
            }

        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    private void registerNewPollingMonitoredValue(String name, String description, Supplier<Long> propertySupplier, ValueSemantics valueSemantics) {
        new PollingMonitoredValue(name, description, monitorableRegistry, updateFrequency, propertySupplier, valueSemantics);
    }
}
