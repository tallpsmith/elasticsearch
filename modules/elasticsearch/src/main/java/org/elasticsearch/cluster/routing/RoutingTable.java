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

package org.elasticsearch.cluster.routing;

import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlocks;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.collect.Iterables;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.UnmodifiableIterator;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.util.concurrent.Immutable;
import org.elasticsearch.index.Index;
import org.elasticsearch.indices.IndexMissingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.collect.Lists.*;
import static org.elasticsearch.common.collect.Maps.*;

/**
 * @author kimchy (shay.banon)
 */
@Immutable
public class RoutingTable implements Iterable<IndexRoutingTable> {

    public static final RoutingTable EMPTY_ROUTING_TABLE = newRoutingTableBuilder().build();

    // index to IndexRoutingTable map
    private final ImmutableMap<String, IndexRoutingTable> indicesRouting;

    RoutingTable(Map<String, IndexRoutingTable> indicesRouting) {
        this.indicesRouting = ImmutableMap.copyOf(indicesRouting);
    }

    @Override public UnmodifiableIterator<IndexRoutingTable> iterator() {
        return indicesRouting.values().iterator();
    }

    public boolean hasIndex(String index) {
        return indicesRouting.containsKey(index);
    }

    public IndexRoutingTable index(String index) {
        return indicesRouting.get(index);
    }

    public Map<String, IndexRoutingTable> indicesRouting() {
        return indicesRouting;
    }

    public Map<String, IndexRoutingTable> getIndicesRouting() {
        return indicesRouting();
    }

    public RoutingNodes routingNodes(ClusterState state) {
        return routingNodes(state.metaData(), state.blocks());
    }

    public RoutingNodes routingNodes(MetaData metaData, ClusterBlocks blocks) {
        return new RoutingNodes(metaData, blocks, this);
    }

    public RoutingTable validateRaiseException(MetaData metaData) throws RoutingValidationException {
        RoutingTableValidation validation = validate(metaData);
        if (!validation.valid()) {
            throw new RoutingValidationException(validation);
        }
        return this;
    }

    public RoutingTableValidation validate(MetaData metaData) {
        RoutingTableValidation validation = new RoutingTableValidation();
        for (IndexRoutingTable indexRoutingTable : this) {
            indexRoutingTable.validate(validation, metaData);
        }
        return validation;
    }

    public List<ShardRouting> shardsWithState(ShardRoutingState... states) {
        List<ShardRouting> shards = newArrayList();
        for (IndexRoutingTable indexRoutingTable : this) {
            shards.addAll(indexRoutingTable.shardsWithState(states));
        }
        return shards;
    }

    /**
     * All the shards (replicas) for the provided indices.
     *
     * @param indices The indices to return all the shards (replicas), can be <tt>null</tt> or empty array to indicate all indices
     * @return All the shards matching the specific index
     * @throws IndexMissingException If an index passed does not exists
     */
    public List<ShardRouting> allShards(String... indices) throws IndexMissingException {
        List<ShardRouting> shards = Lists.newArrayList();
        if (indices == null || indices.length == 0) {
            indices = indicesRouting.keySet().toArray(new String[indicesRouting.keySet().size()]);
        }
        for (String index : indices) {
            IndexRoutingTable indexRoutingTable = index(index);
            if (indexRoutingTable == null) {
                throw new IndexMissingException(new Index(index));
            }
            for (IndexShardRoutingTable indexShardRoutingTable : indexRoutingTable) {
                for (ShardRouting shardRouting : indexShardRoutingTable) {
                    shards.add(shardRouting);
                }
            }
        }
        return shards;
    }

    /**
     * All the shards (primary + replicas) for the provided indices grouped (each group is a single element, consisting
     * of the shard). This is handy for components that expect to get group iterators, but still want in some
     * cases to iterate over all the shards (and not just one shard in replication group).
     *
     * @param indices The indices to return all the shards (replicas), can be <tt>null</tt> or empty array to indicate all indices
     * @return All the shards grouped into a single shard element group each
     * @throws IndexMissingException If an index passed does not exists
     * @see IndexRoutingTable#groupByAllIt()
     */
    public GroupShardsIterator allShardsGrouped(String... indices) throws IndexMissingException {
        // use list here since we need to maintain identity across shards
        ArrayList<ShardIterator> set = new ArrayList<ShardIterator>();
        if (indices == null || indices.length == 0) {
            indices = indicesRouting.keySet().toArray(new String[indicesRouting.keySet().size()]);
        }
        for (String index : indices) {
            IndexRoutingTable indexRoutingTable = index(index);
            if (indexRoutingTable == null) {
                continue;
                // we simply ignore indices that don't exists (make sense for operations that use it currently)
//                throw new IndexMissingException(new Index(index));
            }
            for (IndexShardRoutingTable indexShardRoutingTable : indexRoutingTable) {
                for (ShardRouting shardRouting : indexShardRoutingTable) {
                    set.add(shardRouting.shardsIt());
                }
            }
        }
        return new GroupShardsIterator(set);
    }

    /**
     * All the primary shards for the provided indices grouped (each group is a single element, consisting
     * of the primary shard). This is handy for components that expect to get group iterators, but still want in some
     * cases to iterate over all primary shards (and not just one shard in replication group).
     *
     * @param indices The indices to return all the shards (replicas), can be <tt>null</tt> or empty array to indicate all indices
     * @return All the primary shards grouped into a single shard element group each
     * @throws IndexMissingException If an index passed does not exists
     * @see IndexRoutingTable#groupByAllIt()
     */
    public GroupShardsIterator primaryShardsGrouped(String... indices) throws IndexMissingException {
        // use list here since we need to maintain identity across shards
        ArrayList<ShardIterator> set = new ArrayList<ShardIterator>();
        if (indices == null || indices.length == 0) {
            indices = indicesRouting.keySet().toArray(new String[indicesRouting.keySet().size()]);
        }
        for (String index : indices) {
            IndexRoutingTable indexRoutingTable = index(index);
            if (indexRoutingTable == null) {
                throw new IndexMissingException(new Index(index));
            }
            for (IndexShardRoutingTable indexShardRoutingTable : indexRoutingTable) {
                set.add(indexShardRoutingTable.primaryShard().shardsIt());
            }
        }
        return new GroupShardsIterator(set);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder newRoutingTableBuilder() {
        return new Builder();
    }

    public static class Builder {

        private final Map<String, IndexRoutingTable> indicesRouting = newHashMap();

        public Builder routingTable(RoutingTable routingTable) {
            for (IndexRoutingTable indexRoutingTable : routingTable) {
                indicesRouting.put(indexRoutingTable.index(), indexRoutingTable);
            }
            return this;
        }

        public Builder updateNumberOfReplicas(int numberOfReplicas, String... indices) throws IndexMissingException {
            if (indices == null || indices.length == 0) {
                indices = indicesRouting.keySet().toArray(new String[indicesRouting.keySet().size()]);
            }
            for (String index : indices) {
                IndexRoutingTable indexRoutingTable = indicesRouting.get(index);
                if (indexRoutingTable == null) {
                    throw new IndexMissingException(new Index(index));
                }
                int currentNumberOfReplicas = indexRoutingTable.shards().get(0).size() - 1; // remove the required primary
                IndexRoutingTable.Builder builder = new IndexRoutingTable.Builder(index);
                // re-add all the shards
                for (IndexShardRoutingTable indexShardRoutingTable : indexRoutingTable) {
                    builder.addIndexShard(indexShardRoutingTable);
                }
                if (currentNumberOfReplicas < numberOfReplicas) {
                    // now, add "empty" ones
                    for (int i = 0; i < (numberOfReplicas - currentNumberOfReplicas); i++) {
                        builder.addReplica();
                    }
                } else if (currentNumberOfReplicas > numberOfReplicas) {
                    int delta = currentNumberOfReplicas - numberOfReplicas;
                    if (delta <= 0) {
                        // ignore, can't remove below the current one...
                    } else {
                        for (int i = 0; i < delta; i++) {
                            builder.removeReplica();
                        }
                    }
                }
                indicesRouting.put(index, builder.build());
            }
            return this;
        }

        public Builder add(IndexRoutingTable indexRoutingTable) {
            indexRoutingTable.validate();
            indicesRouting.put(indexRoutingTable.index(), indexRoutingTable);
            return this;
        }

        public Builder add(IndexRoutingTable.Builder indexRoutingTableBuilder) {
            add(indexRoutingTableBuilder.build());
            return this;
        }

        public Builder remove(String index) {
            indicesRouting.remove(index);
            return this;
        }

        public Builder updateNodes(RoutingNodes routingNodes) {
            Map<String, IndexRoutingTable.Builder> indexRoutingTableBuilders = newHashMap();
            for (RoutingNode routingNode : routingNodes) {
                for (MutableShardRouting shardRoutingEntry : routingNode) {
                    // every relocating shard has a double entry, ignore the target one.
                    if (shardRoutingEntry.state() == ShardRoutingState.INITIALIZING && shardRoutingEntry.relocatingNodeId() != null)
                        continue;

                    String index = shardRoutingEntry.index();
                    IndexRoutingTable.Builder indexBuilder = indexRoutingTableBuilders.get(index);
                    if (indexBuilder == null) {
                        indexBuilder = new IndexRoutingTable.Builder(index);
                        indexRoutingTableBuilders.put(index, indexBuilder);
                    }

                    boolean allocatedPostApi = routingNodes.routingTable().index(shardRoutingEntry.index()).shard(shardRoutingEntry.id()).allocatedPostApi();
                    indexBuilder.addShard(new ImmutableShardRouting(shardRoutingEntry), !allocatedPostApi);
                }
            }
            for (MutableShardRouting shardRoutingEntry : Iterables.concat(routingNodes.unassigned(), routingNodes.ignoredUnassigned())) {
                String index = shardRoutingEntry.index();
                IndexRoutingTable.Builder indexBuilder = indexRoutingTableBuilders.get(index);
                if (indexBuilder == null) {
                    indexBuilder = new IndexRoutingTable.Builder(index);
                    indexRoutingTableBuilders.put(index, indexBuilder);
                }
                boolean allocatedPostApi = routingNodes.routingTable().index(shardRoutingEntry.index()).shard(shardRoutingEntry.id()).allocatedPostApi();
                indexBuilder.addShard(new ImmutableShardRouting(shardRoutingEntry), !allocatedPostApi);
            }
            for (IndexRoutingTable.Builder indexBuilder : indexRoutingTableBuilders.values()) {
                add(indexBuilder);
            }
            return this;
        }

        public RoutingTable build() {
            return new RoutingTable(indicesRouting);
        }

        public static RoutingTable readFrom(StreamInput in) throws IOException {
            Builder builder = new Builder();
            int size = in.readVInt();
            for (int i = 0; i < size; i++) {
                IndexRoutingTable index = IndexRoutingTable.Builder.readFrom(in);
                builder.add(index);
            }

            return builder.build();
        }

        public static void writeTo(RoutingTable table, StreamOutput out) throws IOException {
            out.writeVInt(table.indicesRouting.size());
            for (IndexRoutingTable index : table.indicesRouting.values()) {
                IndexRoutingTable.Builder.writeTo(index, out);
            }
        }
    }

    public String prettyPrint() {
        StringBuilder sb = new StringBuilder("routing_table:\n");
        for (Map.Entry<String, IndexRoutingTable> entry : indicesRouting.entrySet()) {
            sb.append(entry.getValue().prettyPrint()).append('\n');
        }
        return sb.toString();
    }


}
