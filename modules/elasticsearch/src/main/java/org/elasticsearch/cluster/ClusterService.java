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

package org.elasticsearch.cluster;

import org.elasticsearch.ElasticSearchIllegalStateException;
import org.elasticsearch.cluster.block.ClusterBlock;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.routing.operation.OperationRouting;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.unit.TimeValue;

/**
 * The cluster service allowing to both register for cluster state events ({@link ClusterStateListener})
 * and submit state update tasks ({@link ClusterStateUpdateTask}.
 *
 * @author kimchy (shay.banon)
 */
public interface ClusterService extends LifecycleComponent<ClusterService> {

    /**
     * The local node.
     */
    DiscoveryNode localNode();

    /**
     * The current state.
     */
    ClusterState state();

    /**
     * Adds an initial block to be set on the first cluster state created.
     */
    void addInitialStateBlock(ClusterBlock block) throws ElasticSearchIllegalStateException;

    /**
     * The operation routing.
     */
    OperationRouting operationRouting();

    /**
     * Adds a listener for updated cluster states.
     */
    void add(ClusterStateListener listener);

    /**
     * Removes a listener for updated cluster states.
     */
    void remove(ClusterStateListener listener);

    /**
     * Adds a cluster state listener that will timeout after the provided timeout.
     */
    void add(TimeValue timeout, TimeoutClusterStateListener listener);

    /**
     * Submits a task that will update the cluster state.
     */
    void submitStateUpdateTask(final String source, final ClusterStateUpdateTask updateTask);
}
