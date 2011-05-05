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

package org.elasticsearch.action.admin.indices.status;

import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.index.merge.MergeStats;
import org.elasticsearch.index.refresh.RefreshStats;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.collect.Lists.*;

/**
 * @author kimchy (shay.banon)
 */
public class IndexStatus implements Iterable<IndexShardStatus> {

    private final String index;

    private final Map<Integer, IndexShardStatus> indexShards;

    IndexStatus(String index, ShardStatus[] shards) {
        this.index = index;

        Map<Integer, List<ShardStatus>> tmpIndexShards = Maps.newHashMap();
        for (ShardStatus shard : shards) {
            List<ShardStatus> lst = tmpIndexShards.get(shard.shardRouting().id());
            if (lst == null) {
                lst = newArrayList();
                tmpIndexShards.put(shard.shardRouting().id(), lst);
            }
            lst.add(shard);
        }
        indexShards = Maps.newHashMap();
        for (Map.Entry<Integer, List<ShardStatus>> entry : tmpIndexShards.entrySet()) {
            indexShards.put(entry.getKey(), new IndexShardStatus(entry.getValue().get(0).shardRouting().shardId(), entry.getValue().toArray(new ShardStatus[entry.getValue().size()])));
        }
    }

    public String index() {
        return this.index;
    }

    public String getIndex() {
        return index();
    }

    /**
     * A shard id to index shard status map (note, index shard status is the replication shard group that maps
     * to the shard id).
     */
    public Map<Integer, IndexShardStatus> shards() {
        return this.indexShards;
    }

    public Map<Integer, IndexShardStatus> getShards() {
        return shards();
    }

    /**
     * Returns only the primary shards store size in bytes.
     */
    public ByteSizeValue primaryStoreSize() {
        long bytes = -1;
        for (IndexShardStatus shard : this) {
            if (shard.primaryStoreSize() != null) {
                if (bytes == -1) {
                    bytes = 0;
                }
                bytes += shard.primaryStoreSize().bytes();
            }
        }
        if (bytes == -1) {
            return null;
        }
        return new ByteSizeValue(bytes);
    }

    /**
     * Returns only the primary shards store size in bytes.
     */
    public ByteSizeValue getPrimaryStoreSize() {
        return primaryStoreSize();
    }

    /**
     * Returns the full store size in bytes, of both primaries and replicas.
     */
    public ByteSizeValue storeSize() {
        long bytes = -1;
        for (IndexShardStatus shard : this) {
            if (shard.storeSize() != null) {
                if (bytes == -1) {
                    bytes = 0;
                }
                bytes += shard.storeSize().bytes();
            }
        }
        if (bytes == -1) {
            return null;
        }
        return new ByteSizeValue(bytes);
    }

    /**
     * Returns the full store size in bytes, of both primaries and replicas.
     */
    public ByteSizeValue getStoreSize() {
        return storeSize();
    }

    public long translogOperations() {
        long translogOperations = -1;
        for (IndexShardStatus shard : this) {
            if (shard.translogOperations() != -1) {
                if (translogOperations == -1) {
                    translogOperations = 0;
                }
                translogOperations += shard.translogOperations();
            }
        }
        return translogOperations;
    }

    public long getTranslogOperations() {
        return translogOperations();
    }

    private transient DocsStatus docs;

    public DocsStatus docs() {
        if (docs != null) {
            return docs;
        }
        DocsStatus docs = null;
        for (IndexShardStatus shard : this) {
            if (shard.docs() == null) {
                continue;
            }
            if (docs == null) {
                docs = new DocsStatus();
            }
            docs.numDocs += shard.docs().numDocs();
            docs.maxDoc += shard.docs().maxDoc();
            docs.deletedDocs += shard.docs().deletedDocs();
        }
        this.docs = docs;
        return docs;
    }

    public DocsStatus getDocs() {
        return docs();
    }

    /**
     * Total merges of this index.
     */
    public MergeStats mergeStats() {
        MergeStats mergeStats = new MergeStats();
        for (IndexShardStatus shard : this) {
            mergeStats.add(shard.mergeStats());
        }
        return mergeStats;
    }

    /**
     * Total merges of this index.
     */
    public MergeStats getMergeStats() {
        return this.mergeStats();
    }

    public RefreshStats refreshStats() {
        RefreshStats refreshStats = new RefreshStats();
        for (IndexShardStatus shard : this) {
            refreshStats.add(shard.refreshStats());
        }
        return refreshStats;
    }

    public RefreshStats getRefreshStats() {
        return refreshStats();
    }

    @Override public Iterator<IndexShardStatus> iterator() {
        return indexShards.values().iterator();
    }

}
