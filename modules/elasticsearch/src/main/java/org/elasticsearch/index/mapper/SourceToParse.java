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

package org.elasticsearch.index.mapper;

/**
 * @author kimchy (shay.banon)
 */
public class SourceToParse {

    public static SourceToParse source(byte[] source) {
        return new SourceToParse(source);
    }

    private final byte[] source;

    private String type;

    private String id;

    private String routing;

    private String parentId;

    public SourceToParse(byte[] source) {
        this.source = source;
    }

    public byte[] source() {
        return this.source;
    }

    public String type() {
        return this.type;
    }

    public SourceToParse type(String type) {
        this.type = type;
        return this;
    }

    public String id() {
        return this.id;
    }

    public SourceToParse id(String id) {
        this.id = id;
        return this;
    }

    public String parent() {
        return this.parentId;
    }

    public SourceToParse parent(String parentId) {
        this.parentId = parentId;
        return this;
    }

    public String routing() {
        return this.routing;
    }

    public SourceToParse routing(String routing) {
        this.routing = routing;
        return this;
    }
}
