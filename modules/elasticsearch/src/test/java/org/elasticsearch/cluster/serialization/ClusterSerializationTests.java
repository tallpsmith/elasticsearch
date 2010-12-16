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

package org.elasticsearch.cluster.serialization;

import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.RoutingTable;
import org.elasticsearch.cluster.routing.allocation.ShardsAllocation;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.testng.annotations.Test;

import static org.elasticsearch.cluster.ClusterState.*;
import static org.elasticsearch.cluster.metadata.IndexMetaData.*;
import static org.elasticsearch.cluster.metadata.MetaData.*;
import static org.elasticsearch.cluster.routing.RoutingBuilders.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * @author kimchy (Shay Banon)
 */
public class ClusterSerializationTests {

    @Test public void testClusterStateSerialization() throws Exception {
        MetaData metaData = newMetaDataBuilder()
                .put(newIndexMetaDataBuilder("test").numberOfShards(10).numberOfReplicas(1))
                .build();

        RoutingTable routingTable = routingTable()
                .add(indexRoutingTable("test").initializeEmpty(metaData.index("test")))
                .build();

        DiscoveryNodes nodes = DiscoveryNodes.newNodesBuilder().put(newNode("node1")).put(newNode("node2")).put(newNode("node3")).localNodeId("node1").masterNodeId("node2").build();

        ClusterState clusterState = newClusterStateBuilder().nodes(nodes).metaData(metaData).routingTable(routingTable).build();

        ShardsAllocation strategy = new ShardsAllocation();
        clusterState = newClusterStateBuilder().state(clusterState).routingTable(strategy.reroute(clusterState).routingTable()).build();

        ClusterState serializedClusterState = ClusterState.Builder.fromBytes(ClusterState.Builder.toBytes(clusterState), newNode("node1"));

        assertThat(serializedClusterState.routingTable().prettyPrint(), equalTo(clusterState.routingTable().prettyPrint()));
    }


    @Test public void testRoutingTableSerialization() throws Exception {
        MetaData metaData = newMetaDataBuilder()
                .put(newIndexMetaDataBuilder("test").numberOfShards(10).numberOfReplicas(1))
                .build();

        RoutingTable routingTable = routingTable()
                .add(indexRoutingTable("test").initializeEmpty(metaData.index("test")))
                .build();

        DiscoveryNodes nodes = DiscoveryNodes.newNodesBuilder().put(newNode("node1")).put(newNode("node2")).put(newNode("node3")).build();

        ClusterState clusterState = newClusterStateBuilder().nodes(nodes).metaData(metaData).routingTable(routingTable).build();

        ShardsAllocation strategy = new ShardsAllocation();
        RoutingTable source = strategy.reroute(clusterState).routingTable();

        BytesStreamOutput outStream = new BytesStreamOutput();
        RoutingTable.Builder.writeTo(source, outStream);
        BytesStreamInput inStream = new BytesStreamInput(outStream.copiedByteArray());
        RoutingTable target = RoutingTable.Builder.readFrom(inStream);

        assertThat(target.prettyPrint(), equalTo(source.prettyPrint()));
    }

    private DiscoveryNode newNode(String nodeId) {
        return new DiscoveryNode(nodeId, DummyTransportAddress.INSTANCE);
    }
}
