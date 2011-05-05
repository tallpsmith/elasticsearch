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

package org.elasticsearch.index.service;

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.common.collect.ImmutableSet;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.index.IndexComponent;
import org.elasticsearch.index.IndexShardMissingException;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.cache.IndexCache;
import org.elasticsearch.index.engine.IndexEngine;
import org.elasticsearch.index.gateway.IndexGateway;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.percolator.PercolatorService;
import org.elasticsearch.index.query.IndexQueryParserService;
import org.elasticsearch.index.shard.service.IndexShard;
import org.elasticsearch.index.similarity.SimilarityService;
import org.elasticsearch.index.store.IndexStore;

/**
 * @author kimchy (shay.banon)
 */
public interface IndexService extends IndexComponent, Iterable<IndexShard> {

    Injector injector();

    IndexGateway gateway();

    IndexCache cache();

    PercolatorService percolateService();

    AnalysisService analysisService();

    MapperService mapperService();

    IndexQueryParserService queryParserService();

    SimilarityService similarityService();

    IndexEngine engine();

    IndexStore store();

    IndexShard createShard(int sShardId) throws ElasticSearchException;

    /**
     * Cleans the shard locally, does not touch the gateway!.
     */
    void cleanShard(int shardId, String reason) throws ElasticSearchException;

    /**
     * Removes the shard, does not delete local data or the gateway.
     */
    void removeShard(int shardId, String reason) throws ElasticSearchException;

    int numberOfShards();

    ImmutableSet<Integer> shardIds();

    boolean hasShard(int shardId);

    IndexShard shard(int shardId);

    IndexShard shardSafe(int shardId) throws IndexShardMissingException;

    Injector shardInjector(int shardId);

    Injector shardInjectorSafe(int shardId) throws IndexShardMissingException;
}
