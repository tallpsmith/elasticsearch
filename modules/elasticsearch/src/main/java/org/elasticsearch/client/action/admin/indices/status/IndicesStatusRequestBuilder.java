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

package org.elasticsearch.client.action.admin.indices.status;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.status.IndicesStatusRequest;
import org.elasticsearch.action.admin.indices.status.IndicesStatusResponse;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.client.action.admin.indices.support.BaseIndicesRequestBuilder;

/**
 * @author kimchy (shay.banon)
 */
public class IndicesStatusRequestBuilder extends BaseIndicesRequestBuilder<IndicesStatusRequest, IndicesStatusResponse> {

    public IndicesStatusRequestBuilder(IndicesAdminClient indicesClient) {
        super(indicesClient, new IndicesStatusRequest());
    }

    public IndicesStatusRequestBuilder setIndices(String... indices) {
        request.indices(indices);
        return this;
    }

    /**
     * Should the status include recovery information. Defaults to <tt>false</tt>.
     */
    public IndicesStatusRequestBuilder setRecovery(boolean recovery) {
        request.recovery(recovery);
        return this;
    }

    /**
     * Should the status include recovery information. Defaults to <tt>false</tt>.
     */
    public IndicesStatusRequestBuilder setSnapshot(boolean snapshot) {
        request.snapshot(snapshot);
        return this;
    }

    @Override protected void doExecute(ActionListener<IndicesStatusResponse> listener) {
        client.status(request, listener);
    }
}
