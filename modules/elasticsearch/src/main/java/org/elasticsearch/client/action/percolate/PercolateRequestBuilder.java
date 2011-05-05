/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.client.action.percolate;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.percolate.PercolateRequest;
import org.elasticsearch.action.percolate.PercolateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.action.support.BaseRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;

import java.util.Map;

/**
 * @author kimchy (shay.banon)
 */
public class PercolateRequestBuilder extends BaseRequestBuilder<PercolateRequest, PercolateResponse> {

    public PercolateRequestBuilder(Client client, String index, String type) {
        super(client, new PercolateRequest(index, type));
    }

    /**
     * Sets the index to percolate the document against.
     */
    public PercolateRequestBuilder setIndex(String index) {
        request.index(index);
        return this;
    }

    /**
     * Sets the type of the document to percolate.
     */
    public PercolateRequestBuilder setType(String type) {
        request.type(type);
        return this;
    }

    /**
     * Index the Map as a JSON.
     *
     * @param source The map to index
     */
    public PercolateRequestBuilder setSource(Map<String, Object> source) {
        request.source(source);
        return this;
    }

    /**
     * Index the Map as the provided content type.
     *
     * @param source The map to index
     */
    public PercolateRequestBuilder setSource(Map<String, Object> source, XContentType contentType) {
        request.source(source, contentType);
        return this;
    }

    /**
     * Sets the document source to index.
     *
     * <p>Note, its preferable to either set it using {@link #setSource(org.elasticsearch.common.xcontent.XContentBuilder)}
     * or using the {@link #setSource(byte[])}.
     */
    public PercolateRequestBuilder setSource(String source) {
        request.source(source);
        return this;
    }

    /**
     * Sets the content source to index.
     */
    public PercolateRequestBuilder setSource(XContentBuilder sourceBuilder) {
        request.source(sourceBuilder);
        return this;
    }

    /**
     * Sets the document to index in bytes form.
     */
    public PercolateRequestBuilder setSource(byte[] source) {
        request.source(source);
        return this;
    }

    /**
     * Sets the document to index in bytes form (assumed to be safe to be used from different
     * threads).
     *
     * @param source The source to index
     * @param offset The offset in the byte array
     * @param length The length of the data
     */
    public PercolateRequestBuilder setSource(byte[] source, int offset, int length) {
        request.source(source, offset, length);
        return this;
    }

    /**
     * Sets the document to index in bytes form.
     *
     * @param source The source to index
     * @param offset The offset in the byte array
     * @param length The length of the data
     * @param unsafe Is the byte array safe to be used form a different thread
     */
    public PercolateRequestBuilder setSource(byte[] source, int offset, int length, boolean unsafe) {
        request.source(source, offset, length, unsafe);
        return this;
    }

    /**
     * Should the listener be called on a separate thread if needed.
     */
    public PercolateRequestBuilder setListenerThreaded(boolean listenerThreaded) {
        request.listenerThreaded(listenerThreaded);
        return this;
    }

    /**
     * if this operation hits a node with a local relevant shard, should it be preferred
     * to be executed on, or just do plain round robin. Defaults to <tt>true</tt>
     */
    public PercolateRequestBuilder setPreferLocal(boolean preferLocal) {
        request.preferLocal(preferLocal);
        return this;
    }

    /**
     * Controls if the operation will be executed on a separate thread when executed locally. Defaults
     * to <tt>true</tt> when running in embedded mode.
     */
    public PercolateRequestBuilder setOperationThreaded(boolean operationThreaded) {
        request.operationThreaded(operationThreaded);
        return this;
    }

    @Override protected void doExecute(ActionListener<PercolateResponse> listener) {
        client.percolate(request, listener);
    }

}
