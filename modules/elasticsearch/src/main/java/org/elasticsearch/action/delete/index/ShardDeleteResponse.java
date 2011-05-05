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

package org.elasticsearch.action.delete.index;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;

import java.io.IOException;

/**
 * Delete response executed on a specific shard.
 *
 * @author kimchy (shay.banon)
 */
public class ShardDeleteResponse implements ActionResponse, Streamable {

    private long version;

    private boolean notFound;

    public ShardDeleteResponse() {
    }

    public ShardDeleteResponse(long version, boolean notFound) {
        this.version = version;
        this.notFound = notFound;
    }

    public long version() {
        return version;
    }

    public boolean notFound() {
        return notFound;
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        version = in.readLong();
        notFound = in.readBoolean();
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        out.writeLong(version);
        out.writeBoolean(notFound);
    }
}