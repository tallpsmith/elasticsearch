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

package org.elasticsearch.search.internal;

import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.Bytes;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.Scroll;

import java.io.IOException;

import static org.elasticsearch.common.unit.TimeValue.*;
import static org.elasticsearch.search.Scroll.*;

/**
 * Source structure:
 * <p/>
 * <pre>
 * {
 *  from : 0, size : 20, (optional, can be set on the request)
 *  sort : { "name.first" : {}, "name.last" : { reverse : true } }
 *  fields : [ "name.first", "name.last" ]
 *  queryParserName : "",
 *  query : { ... }
 *  facets : {
 *      "facet1" : {
 *          query : { ... }
 *      }
 *  }
 * }
 * </pre>
 *
 * @author kimchy (shay.banon)
 */
public class InternalSearchRequest implements Streamable {

    private String index;

    private int shardId;

    private int numberOfShards;

    private SearchType searchType;

    private Scroll scroll;

    private TimeValue timeout;

    private String[] types = Strings.EMPTY_ARRAY;

    private byte[] source;
    private int sourceOffset;
    private int sourceLength;

    private byte[] extraSource;
    private int extraSourceOffset;
    private int extraSourceLength;

    public InternalSearchRequest() {
    }

    public InternalSearchRequest(ShardRouting shardRouting, int numberOfShards, SearchType searchType) {
        this(shardRouting.index(), shardRouting.id(), numberOfShards, searchType);
    }

    public InternalSearchRequest(String index, int shardId, int numberOfShards, SearchType searchType) {
        this.index = index;
        this.shardId = shardId;
        this.numberOfShards = numberOfShards;
        this.searchType = searchType;
    }

    public String index() {
        return index;
    }

    public int shardId() {
        return shardId;
    }

    public SearchType searchType() {
        return this.searchType;
    }

    public int numberOfShards() {
        return numberOfShards;
    }

    public byte[] source() {
        return this.source;
    }

    public int sourceOffset() {
        return sourceOffset;
    }

    public int sourceLength() {
        return sourceLength;
    }

    public byte[] extraSource() {
        return this.extraSource;
    }

    public int extraSourceOffset() {
        return extraSourceOffset;
    }

    public int extraSourceLength() {
        return extraSourceLength;
    }

    public InternalSearchRequest source(byte[] source) {
        return source(source, 0, source.length);
    }

    public InternalSearchRequest source(byte[] source, int offset, int length) {
        this.source = source;
        this.sourceOffset = offset;
        this.sourceLength = length;
        return this;
    }

    public InternalSearchRequest extraSource(byte[] extraSource, int offset, int length) {
        this.extraSource = extraSource;
        this.extraSourceOffset = offset;
        this.extraSourceLength = length;
        return this;
    }

    public Scroll scroll() {
        return scroll;
    }

    public InternalSearchRequest scroll(Scroll scroll) {
        this.scroll = scroll;
        return this;
    }

    public TimeValue timeout() {
        return timeout;
    }

    public InternalSearchRequest timeout(TimeValue timeout) {
        this.timeout = timeout;
        return this;
    }

    public String[] types() {
        return types;
    }

    public void types(String[] types) {
        this.types = types;
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        index = in.readUTF();
        shardId = in.readVInt();
        searchType = SearchType.fromId(in.readByte());
        numberOfShards = in.readVInt();
        if (in.readBoolean()) {
            scroll = readScroll(in);
        }
        if (in.readBoolean()) {
            timeout = readTimeValue(in);
        }
        sourceOffset = 0;
        sourceLength = in.readVInt();
        if (sourceLength == 0) {
            source = Bytes.EMPTY_ARRAY;
        } else {
            source = new byte[sourceLength];
            in.readFully(source);
        }
        extraSourceOffset = 0;
        extraSourceLength = in.readVInt();
        if (extraSourceLength == 0) {
            extraSource = Bytes.EMPTY_ARRAY;
        } else {
            extraSource = new byte[extraSourceLength];
            in.readFully(extraSource);
        }
        int typesSize = in.readVInt();
        if (typesSize > 0) {
            types = new String[typesSize];
            for (int i = 0; i < typesSize; i++) {
                types[i] = in.readUTF();
            }
        }
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        out.writeUTF(index);
        out.writeVInt(shardId);
        out.writeByte(searchType.id());
        out.writeVInt(numberOfShards);
        if (scroll == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            scroll.writeTo(out);
        }
        if (timeout == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            timeout.writeTo(out);
        }
        if (source == null) {
            out.writeVInt(0);
        } else {
            out.writeVInt(sourceLength);
            out.writeBytes(source, sourceOffset, sourceLength);
        }
        if (extraSource == null) {
            out.writeVInt(0);
        } else {
            out.writeVInt(extraSourceLength);
            out.writeBytes(extraSource, extraSourceOffset, extraSourceLength);
        }
        out.writeVInt(types.length);
        for (String type : types) {
            out.writeUTF(type);
        }
    }
}
