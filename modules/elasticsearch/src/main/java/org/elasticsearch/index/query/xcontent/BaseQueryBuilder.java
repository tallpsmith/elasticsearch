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

package org.elasticsearch.index.query.xcontent;

import org.elasticsearch.common.io.FastByteArrayOutputStream;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilderException;

import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
public abstract class BaseQueryBuilder implements XContentQueryBuilder {

    @Override public FastByteArrayOutputStream buildAsUnsafeBytes() throws QueryBuilderException {
        return buildAsUnsafeBytes(XContentType.JSON);
    }

    @Override public FastByteArrayOutputStream buildAsUnsafeBytes(XContentType contentType) throws QueryBuilderException {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(contentType);
            toXContent(builder, EMPTY_PARAMS);
            return builder.unsafeStream();
        } catch (Exception e) {
            throw new QueryBuilderException("Failed to build query", e);
        }
    }

    @Override public byte[] buildAsBytes() throws QueryBuilderException {
        return buildAsBytes(XContentType.JSON);
    }

    @Override public byte[] buildAsBytes(XContentType contentType) throws QueryBuilderException {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(contentType);
            toXContent(builder, EMPTY_PARAMS);
            return builder.copiedBytes();
        } catch (Exception e) {
            throw new QueryBuilderException("Failed to build query", e);
        }
    }

    @Override public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        doXContent(builder, params);
        builder.endObject();
        return builder;
    }

    protected abstract void doXContent(XContentBuilder builder, Params params) throws IOException;
}
