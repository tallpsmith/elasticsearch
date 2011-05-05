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

package org.elasticsearch.search.controller;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.PriorityQueue;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.collect.Iterables;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.collect.Ordering;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.trove.ExtTIntArrayList;
import org.elasticsearch.common.trove.impl.Constants;
import org.elasticsearch.common.trove.map.hash.TObjectIntHashMap;
import org.elasticsearch.search.SearchShardTarget;
import org.elasticsearch.search.dfs.AggregatedDfs;
import org.elasticsearch.search.dfs.DfsSearchResult;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.FacetProcessors;
import org.elasticsearch.search.facet.InternalFacets;
import org.elasticsearch.search.fetch.FetchSearchResult;
import org.elasticsearch.search.fetch.FetchSearchResultProvider;
import org.elasticsearch.search.internal.InternalSearchHit;
import org.elasticsearch.search.internal.InternalSearchHits;
import org.elasticsearch.search.internal.InternalSearchResponse;
import org.elasticsearch.search.query.QuerySearchResult;
import org.elasticsearch.search.query.QuerySearchResultProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author kimchy (shay.banon)
 */
public class SearchPhaseController extends AbstractComponent {

    public static Ordering<QuerySearchResultProvider> QUERY_RESULT_ORDERING = new Ordering<QuerySearchResultProvider>() {
        @Override public int compare(@Nullable QuerySearchResultProvider o1, @Nullable QuerySearchResultProvider o2) {
            int i = o1.shardTarget().index().compareTo(o2.shardTarget().index());
            if (i == 0) {
                i = o1.shardTarget().shardId() - o2.shardTarget().shardId();
            }
            return i;
        }
    };

    private static final ShardDoc[] EMPTY = new ShardDoc[0];

    private final FacetProcessors facetProcessors;

    private final boolean optimizeSingleShard;

    @Inject public SearchPhaseController(Settings settings, FacetProcessors facetProcessors) {
        super(settings);
        this.facetProcessors = facetProcessors;
        this.optimizeSingleShard = componentSettings.getAsBoolean("optimize_single_shard", true);
    }

    public boolean optimizeSingleShard() {
        return optimizeSingleShard;
    }

    public AggregatedDfs aggregateDfs(Iterable<DfsSearchResult> results) {
        TObjectIntHashMap<Term> dfMap = new TObjectIntHashMap<Term>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
        long aggMaxDoc = 0;
        for (DfsSearchResult result : results) {
            for (int i = 0; i < result.freqs().length; i++) {
                dfMap.adjustOrPutValue(result.terms()[i], result.freqs()[i], result.freqs()[i]);
            }
            aggMaxDoc += result.maxDoc();
        }
        return new AggregatedDfs(dfMap, aggMaxDoc);
    }

    public ShardDoc[] sortDocs(Collection<? extends QuerySearchResultProvider> results1) {
        if (results1.isEmpty()) {
            return EMPTY;
        }

        if (optimizeSingleShard) {
            boolean canOptimize = false;
            QuerySearchResult result = null;
            if (results1.size() == 1) {
                canOptimize = true;
                result = results1.iterator().next().queryResult();
            } else {
                // lets see if we only got hits from a single shard, if so, we can optimize...
                for (QuerySearchResultProvider queryResult : results1) {
                    if (queryResult.queryResult().topDocs().scoreDocs.length > 0) {
                        if (result != null) { // we already have one, can't really optimize
                            canOptimize = false;
                            break;
                        }
                        canOptimize = true;
                        result = queryResult.queryResult();
                    }
                }
            }
            if (canOptimize) {
                ScoreDoc[] scoreDocs = result.topDocs().scoreDocs;
                if (scoreDocs.length < result.from()) {
                    return EMPTY;
                }
                int resultDocsSize = result.size();
                if ((scoreDocs.length - result.from()) < resultDocsSize) {
                    resultDocsSize = scoreDocs.length - result.from();
                }
                if (result.topDocs() instanceof TopFieldDocs) {
                    ShardDoc[] docs = new ShardDoc[resultDocsSize];
                    for (int i = 0; i < resultDocsSize; i++) {
                        ScoreDoc scoreDoc = scoreDocs[result.from() + i];
                        docs[i] = new ShardFieldDoc(result.shardTarget(), scoreDoc.doc, scoreDoc.score, ((FieldDoc) scoreDoc).fields);
                    }
                    return docs;
                } else {
                    ShardDoc[] docs = new ShardDoc[resultDocsSize];
                    for (int i = 0; i < resultDocsSize; i++) {
                        ScoreDoc scoreDoc = scoreDocs[result.from() + i];
                        docs[i] = new ShardScoreDoc(result.shardTarget(), scoreDoc.doc, scoreDoc.score);
                    }
                    return docs;
                }
            }
        }

        List<? extends QuerySearchResultProvider> results = QUERY_RESULT_ORDERING.sortedCopy(results1);

        QuerySearchResultProvider queryResultProvider = results.get(0);

        int totalNumDocs = 0;

        int queueSize = queryResultProvider.queryResult().from() + queryResultProvider.queryResult().size();
        if (queryResultProvider.includeFetch()) {
            // if we did both query and fetch on the same go, we have fetched all the docs from each shards already, use them...
            // this is also important since we shortcut and fetch only docs from "from" and up to "size"
            queueSize *= results.size();
        }
        PriorityQueue queue;
        if (queryResultProvider.queryResult().topDocs() instanceof TopFieldDocs) {
            // sorting, first if the type is a String, chance CUSTOM to STRING so we handle nulls properly (since our CUSTOM String sorting might return null)
            TopFieldDocs fieldDocs = (TopFieldDocs) queryResultProvider.queryResult().topDocs();
            for (int i = 0; i < fieldDocs.fields.length; i++) {
                boolean allValuesAreNull = true;
                boolean resolvedField = false;
                for (QuerySearchResultProvider resultProvider : results) {
                    for (ScoreDoc doc : resultProvider.queryResult().topDocs().scoreDocs) {
                        FieldDoc fDoc = (FieldDoc) doc;
                        if (fDoc.fields[i] != null) {
                            allValuesAreNull = false;
                            if (fDoc.fields[i] instanceof String) {
                                fieldDocs.fields[i] = new SortField(fieldDocs.fields[i].getField(), SortField.STRING, fieldDocs.fields[i].getReverse());
                            }
                            resolvedField = true;
                            break;
                        }
                    }
                    if (resolvedField) {
                        break;
                    }
                }
                if (!resolvedField && allValuesAreNull && fieldDocs.fields[i].getField() != null) {
                    // we did not manage to resolve a field (and its not score or doc, which have no field), and all the fields are null (which can only happen for STRING), make it a STRING
                    fieldDocs.fields[i] = new SortField(fieldDocs.fields[i].getField(), SortField.STRING, fieldDocs.fields[i].getReverse());
                }
            }
            queue = new ShardFieldDocSortedHitQueue(fieldDocs.fields, queueSize);

            // we need to accumulate for all and then filter the from
            for (QuerySearchResultProvider resultProvider : results) {
                QuerySearchResult result = resultProvider.queryResult();
                ScoreDoc[] scoreDocs = result.topDocs().scoreDocs;
                totalNumDocs += scoreDocs.length;
                for (ScoreDoc doc : scoreDocs) {
                    ShardFieldDoc nodeFieldDoc = new ShardFieldDoc(result.shardTarget(), doc.doc, doc.score, ((FieldDoc) doc).fields);
                    if (queue.insertWithOverflow(nodeFieldDoc) == nodeFieldDoc) {
                        // filled the queue, break
                        break;
                    }
                }
            }
        } else {
            queue = new ScoreDocQueue(queueSize); // we need to accumulate for all and then filter the from
            for (QuerySearchResultProvider resultProvider : results) {
                QuerySearchResult result = resultProvider.queryResult();
                ScoreDoc[] scoreDocs = result.topDocs().scoreDocs;
                totalNumDocs += scoreDocs.length;
                for (ScoreDoc doc : scoreDocs) {
                    ShardScoreDoc nodeScoreDoc = new ShardScoreDoc(result.shardTarget(), doc.doc, doc.score);
                    if (queue.insertWithOverflow(nodeScoreDoc) == nodeScoreDoc) {
                        // filled the queue, break
                        break;
                    }
                }
            }

        }

        int resultDocsSize = queryResultProvider.queryResult().size();
        if (queryResultProvider.includeFetch()) {
            // if we did both query and fetch on the same go, we have fetched all the docs from each shards already, use them...
            resultDocsSize *= results.size();
        }
        if (totalNumDocs < queueSize) {
            resultDocsSize = totalNumDocs - queryResultProvider.queryResult().from();
        }

        if (resultDocsSize <= 0) {
            return EMPTY;
        }

        // we only pop the first, this handles "from" nicely since the "from" are down the queue
        // that we already fetched, so we are actually popping the "from" and up to "size"
        ShardDoc[] shardDocs = new ShardDoc[resultDocsSize];
        for (int i = resultDocsSize - 1; i >= 0; i--)      // put docs in array
            shardDocs[i] = (ShardDoc) queue.pop();
        return shardDocs;
    }

    public Map<SearchShardTarget, ExtTIntArrayList> docIdsToLoad(ShardDoc[] shardDocs) {
        Map<SearchShardTarget, ExtTIntArrayList> result = Maps.newHashMap();
        for (ShardDoc shardDoc : shardDocs) {
            ExtTIntArrayList list = result.get(shardDoc.shardTarget());
            if (list == null) {
                list = new ExtTIntArrayList(); // can't be shared!, uses unsafe on it later on
                result.put(shardDoc.shardTarget(), list);
            }
            list.add(shardDoc.docId());
        }
        return result;
    }

    public InternalSearchResponse merge(ShardDoc[] sortedDocs, Map<SearchShardTarget, ? extends QuerySearchResultProvider> queryResults, Map<SearchShardTarget, ? extends FetchSearchResultProvider> fetchResults) {

        boolean sorted = false;
        int sortScoreIndex = -1;
        QuerySearchResult querySearchResult = Iterables.get(queryResults.values(), 0).queryResult();

        if (querySearchResult.topDocs() instanceof TopFieldDocs) {
            sorted = true;
            TopFieldDocs fieldDocs = (TopFieldDocs) querySearchResult.queryResult().topDocs();
            for (int i = 0; i < fieldDocs.fields.length; i++) {
                if (fieldDocs.fields[i].getType() == SortField.SCORE) {
                    sortScoreIndex = i;
                }
            }
        }

        // merge facets
        InternalFacets facets = null;
        if (!queryResults.isEmpty()) {
            // we rely on the fact that the order of facets is the same on all query results
            QuerySearchResult queryResult = queryResults.values().iterator().next().queryResult();

            if (queryResult.facets() != null && queryResult.facets().facets() != null && !queryResult.facets().facets().isEmpty()) {
                List<Facet> aggregatedFacets = Lists.newArrayList();
                List<Facet> namedFacets = Lists.newArrayList();
                for (Facet facet : queryResult.facets()) {
                    // aggregate each facet name into a single list, and aggregate it
                    namedFacets.clear();
                    for (QuerySearchResultProvider queryResultProvider : queryResults.values()) {
                        for (Facet facet1 : queryResultProvider.queryResult().facets()) {
                            if (facet.name().equals(facet1.name())) {
                                namedFacets.add(facet1);
                            }
                        }
                    }
                    Facet aggregatedFacet = facetProcessors.processor(facet.type()).reduce(facet.name(), namedFacets);
                    aggregatedFacets.add(aggregatedFacet);
                }
                facets = new InternalFacets(aggregatedFacets);
            }
        }

        // count the total (we use the query result provider here, since we might not get any hits (we scrolled past them))
        long totalHits = 0;
        float maxScore = Float.NEGATIVE_INFINITY;
        boolean timedOut = false;
        for (QuerySearchResultProvider queryResultProvider : queryResults.values()) {
            if (queryResultProvider.queryResult().searchTimedOut()) {
                timedOut = true;
            }
            totalHits += queryResultProvider.queryResult().topDocs().totalHits;
            if (!Float.isNaN(queryResultProvider.queryResult().topDocs().getMaxScore())) {
                maxScore = Math.max(maxScore, queryResultProvider.queryResult().topDocs().getMaxScore());
            }
        }
        if (Float.isInfinite(maxScore)) {
            maxScore = Float.NaN;
        }

        // clean the fetch counter
        for (FetchSearchResultProvider fetchSearchResultProvider : fetchResults.values()) {
            fetchSearchResultProvider.fetchResult().initCounter();
        }

        // merge hits
        List<InternalSearchHit> hits = new ArrayList<InternalSearchHit>();
        if (!fetchResults.isEmpty()) {
            for (ShardDoc shardDoc : sortedDocs) {
                FetchSearchResultProvider fetchResultProvider = fetchResults.get(shardDoc.shardTarget());
                if (fetchResultProvider == null) {
                    continue;
                }
                FetchSearchResult fetchResult = fetchResultProvider.fetchResult();
                int index = fetchResult.counterGetAndIncrement();
                if (index < fetchResult.hits().internalHits().length) {
                    InternalSearchHit searchHit = fetchResult.hits().internalHits()[index];
                    searchHit.score(shardDoc.score());
                    searchHit.shard(fetchResult.shardTarget());

                    if (sorted) {
                        FieldDoc fieldDoc = (FieldDoc) shardDoc;
                        searchHit.sortValues(fieldDoc.fields);
                        if (sortScoreIndex != -1) {
                            searchHit.score(((Number) fieldDoc.fields[sortScoreIndex]).floatValue());
                        }
                    }

                    hits.add(searchHit);
                }
            }
        }

        InternalSearchHits searchHits = new InternalSearchHits(hits.toArray(new InternalSearchHit[hits.size()]), totalHits, maxScore);
        return new InternalSearchResponse(searchHits, facets, timedOut);
    }
}
