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

package org.elasticsearch.client;

import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.count.CountRequest;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequest;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.mlt.MoreLikeThisRequest;
import org.elasticsearch.action.percolate.PercolateRequest;
import org.elasticsearch.action.percolate.PercolateResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.action.count.CountRequestBuilder;
import org.elasticsearch.client.action.delete.DeleteRequestBuilder;
import org.elasticsearch.client.action.deletebyquery.DeleteByQueryRequestBuilder;
import org.elasticsearch.client.action.get.GetRequestBuilder;
import org.elasticsearch.client.action.index.IndexRequestBuilder;
import org.elasticsearch.client.action.percolate.PercolateRequestBuilder;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.client.action.search.SearchScrollRequestBuilder;
import org.elasticsearch.common.Nullable;

/**
 * A client provides a one stop interface for performing actions/operations against the cluster.
 *
 * <p>All operations performed are asynchronous by nature. Each action/operation has two flavors, the first
 * simply returns an {@link org.elasticsearch.action.ActionFuture}, while the second accepts an
 * {@link org.elasticsearch.action.ActionListener}.
 *
 * <p>A client can either be retrieved from a {@link org.elasticsearch.node.Node} started, or connected remotely
 * to one or more nodes using {@link org.elasticsearch.client.transport.TransportClient}.
 *
 * @author kimchy (shay.banon)
 * @see org.elasticsearch.node.Node#client()
 * @see org.elasticsearch.client.transport.TransportClient
 */
public interface Client {

    /**
     * Closes the client.
     */
    void close();

    /**
     * The admin client that can be used to perform administrative operations.
     */
    AdminClient admin();

    /**
     * Index a JSON source associated with a given index and type.
     *
     * <p>The id is optional, if it is not provided, one will be generated automatically.
     *
     * @param request The index request
     * @return The result future
     * @see Requests#indexRequest(String)
     */
    ActionFuture<IndexResponse> index(IndexRequest request);

    /**
     * Index a document associated with a given index and type.
     *
     * <p>The id is optional, if it is not provided, one will be generated automatically.
     *
     * @param request  The index request
     * @param listener A listener to be notified with a result
     * @see Requests#indexRequest(String)
     */
    void index(IndexRequest request, ActionListener<IndexResponse> listener);

    /**
     * Index a document associated with a given index and type.
     *
     * <p>The id is optional, if it is not provided, one will be generated automatically.
     */
    IndexRequestBuilder prepareIndex();

    /**
     * Index a document associated with a given index and type.
     *
     * <p>The id is optional, if it is not provided, one will be generated automatically.
     *
     * @param index The index to index the document to
     * @param type  The type to index the document to
     */
    IndexRequestBuilder prepareIndex(String index, String type);

    /**
     * Index a document associated with a given index and type.
     *
     * <p>The id is optional, if it is not provided, one will be generated automatically.
     *
     * @param index The index to index the document to
     * @param type  The type to index the document to
     * @param id    The id of the document
     */
    IndexRequestBuilder prepareIndex(String index, String type, @Nullable String id);

    /**
     * Deletes a document from the index based on the index, type and id.
     *
     * @param request The delete request
     * @return The result future
     * @see Requests#deleteRequest(String)
     */
    ActionFuture<DeleteResponse> delete(DeleteRequest request);

    /**
     * Deletes a document from the index based on the index, type and id.
     *
     * @param request  The delete request
     * @param listener A listener to be notified with a result
     * @see Requests#deleteRequest(String)
     */
    void delete(DeleteRequest request, ActionListener<DeleteResponse> listener);

    /**
     * Deletes a document from the index based on the index, type and id.
     */
    DeleteRequestBuilder prepareDelete();

    /**
     * Deletes a document from the index based on the index, type and id.
     *
     * @param index The index to delete the document from
     * @param type  The type of the document to delete
     * @param id    The id of the document to delete
     */
    DeleteRequestBuilder prepareDelete(String index, String type, String id);

    /**
     * Executes a bulk of index / delete operations.
     *
     * @param request The bulk request
     * @return The result future
     * @see org.elasticsearch.client.Requests#bulkRequest()
     */
    ActionFuture<BulkResponse> bulk(BulkRequest request);

    /**
     * Executes a bulk of index / delete operations.
     *
     * @param request  The bulk request
     * @param listener A listener to be notified with a result
     * @see org.elasticsearch.client.Requests#bulkRequest()
     */
    void bulk(BulkRequest request, ActionListener<BulkResponse> listener);

    /**
     * Executes a bulk of index / delete operations.
     */
    BulkRequestBuilder prepareBulk();

    /**
     * Deletes all documents from one or more indices based on a query.
     *
     * @param request The delete by query request
     * @return The result future
     * @see Requests#deleteByQueryRequest(String...)
     */
    ActionFuture<DeleteByQueryResponse> deleteByQuery(DeleteByQueryRequest request);

    /**
     * Deletes all documents from one or more indices based on a query.
     *
     * @param request  The delete by query request
     * @param listener A listener to be notified with a result
     * @see Requests#deleteByQueryRequest(String...)
     */
    void deleteByQuery(DeleteByQueryRequest request, ActionListener<DeleteByQueryResponse> listener);

    /**
     * Deletes all documents from one or more indices based on a query.
     */
    DeleteByQueryRequestBuilder prepareDeleteByQuery(String... indices);

    /**
     * Gets the document that was indexed from an index with a type and id.
     *
     * @param request The get request
     * @return The result future
     * @see Requests#getRequest(String)
     */
    ActionFuture<GetResponse> get(GetRequest request);

    /**
     * Gets the document that was indexed from an index with a type and id.
     *
     * @param request  The get request
     * @param listener A listener to be notified with a result
     * @see Requests#getRequest(String)
     */
    void get(GetRequest request, ActionListener<GetResponse> listener);

    /**
     * Gets the document that was indexed from an index with a type and id.
     */
    GetRequestBuilder prepareGet();

    /**
     * Gets the document that was indexed from an index with a type and id.
     */
    GetRequestBuilder prepareGet(String index, String type, String id);

    /**
     * A count of all the documents matching a specific query.
     *
     * @param request The count request
     * @return The result future
     * @see Requests#countRequest(String...)
     */
    ActionFuture<CountResponse> count(CountRequest request);

    /**
     * A count of all the documents matching a specific query.
     *
     * @param request  The count request
     * @param listener A listener to be notified of the result
     * @see Requests#countRequest(String...)
     */
    void count(CountRequest request, ActionListener<CountResponse> listener);

    /**
     * A count of all the documents matching a specific query.
     */
    CountRequestBuilder prepareCount(String... indices);

    /**
     * Search across one or more indices and one or more types with a query.
     *
     * @param request The search request
     * @return The result future
     * @see Requests#searchRequest(String...)
     */
    ActionFuture<SearchResponse> search(SearchRequest request);

    /**
     * Search across one or more indices and one or more types with a query.
     *
     * @param request  The search request
     * @param listener A listener to be notified of the result
     * @see Requests#searchRequest(String...)
     */
    void search(SearchRequest request, ActionListener<SearchResponse> listener);

    /**
     * Search across one or more indices and one or more types with a query.
     */
    SearchRequestBuilder prepareSearch(String... indices);

    /**
     * A search scroll request to continue searching a previous scrollable search request.
     *
     * @param request The search scroll request
     * @return The result future
     * @see Requests#searchScrollRequest(String)
     */
    ActionFuture<SearchResponse> searchScroll(SearchScrollRequest request);

    /**
     * A search scroll request to continue searching a previous scrollable search request.
     *
     * @param request  The search scroll request
     * @param listener A listener to be notified of the result
     * @see Requests#searchScrollRequest(String)
     */
    void searchScroll(SearchScrollRequest request, ActionListener<SearchResponse> listener);

    /**
     * A search scroll request to continue searching a previous scrollable search request.
     */
    SearchScrollRequestBuilder prepareSearchScroll(String scrollId);

    /**
     * A more like this action to search for documents that are "like" a specific document.
     *
     * @param request The more like this request
     * @return The response future
     */
    ActionFuture<SearchResponse> moreLikeThis(MoreLikeThisRequest request);

    /**
     * A more like this action to search for documents that are "like" a specific document.
     *
     * @param request  The more like this request
     * @param listener A listener to be notified of the result
     */
    void moreLikeThis(MoreLikeThisRequest request, ActionListener<SearchResponse> listener);

    /**
     * Percolates a request returning the matches documents.
     */
    ActionFuture<PercolateResponse> percolate(PercolateRequest request);

    /**
     * Percolates a request returning the matches documents.
     */
    void percolate(PercolateRequest request, ActionListener<PercolateResponse> listener);

    /**
     * Percolates a request returning the matches documents.
     *
     * @param index The index to percolate the doc
     * @param type  The type of the doc
     */
    PercolateRequestBuilder preparePercolate(String index, String type);
}