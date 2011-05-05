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

package org.elasticsearch.index.mapper.xcontent;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.mapper.xcontent.ip.IpFieldMapper;

/**
 * @author kimchy (shay.banon)
 */
public final class XContentMapperBuilders {

    private XContentMapperBuilders() {

    }

    public static XContentDocumentMapper.Builder doc(String index, RootObjectMapper.Builder objectBuilder) {
        return new XContentDocumentMapper.Builder(index, null, objectBuilder);
    }

    public static XContentDocumentMapper.Builder doc(String index, @Nullable Settings settings, RootObjectMapper.Builder objectBuilder) {
        return new XContentDocumentMapper.Builder(index, settings, objectBuilder);
    }

    public static SourceFieldMapper.Builder source() {
        return new SourceFieldMapper.Builder();
    }

    public static IdFieldMapper.Builder id() {
        return new IdFieldMapper.Builder();
    }

    public static RoutingFieldMapper.Builder routing() {
        return new RoutingFieldMapper.Builder();
    }

    public static UidFieldMapper.Builder uid() {
        return new UidFieldMapper.Builder();
    }

    public static TypeFieldMapper.Builder type() {
        return new TypeFieldMapper.Builder();
    }

    public static IndexFieldMapper.Builder index() {
        return new IndexFieldMapper.Builder();
    }

    public static BoostFieldMapper.Builder boost(String name) {
        return new BoostFieldMapper.Builder(name);
    }

    public static AllFieldMapper.Builder all() {
        return new AllFieldMapper.Builder();
    }

    public static AnalyzerMapper.Builder analyzer() {
        return new AnalyzerMapper.Builder();
    }

    public static MultiFieldMapper.Builder multiField(String name) {
        return new MultiFieldMapper.Builder(name);
    }

    public static RootObjectMapper.Builder rootObject(String name) {
        return new RootObjectMapper.Builder(name);
    }

    public static ObjectMapper.Builder object(String name) {
        return new ObjectMapper.Builder(name);
    }

    public static BooleanFieldMapper.Builder booleanField(String name) {
        return new BooleanFieldMapper.Builder(name);
    }

    public static StringFieldMapper.Builder stringField(String name) {
        return new StringFieldMapper.Builder(name);
    }

    public static BinaryFieldMapper.Builder binaryField(String name) {
        return new BinaryFieldMapper.Builder(name);
    }

    public static DateFieldMapper.Builder dateField(String name) {
        return new DateFieldMapper.Builder(name);
    }

    public static IpFieldMapper.Builder ipField(String name) {
        return new IpFieldMapper.Builder(name);
    }

    public static ShortFieldMapper.Builder shortField(String name) {
        return new ShortFieldMapper.Builder(name);
    }

    public static ByteFieldMapper.Builder byteField(String name) {
        return new ByteFieldMapper.Builder(name);
    }

    public static IntegerFieldMapper.Builder integerField(String name) {
        return new IntegerFieldMapper.Builder(name);
    }

    public static LongFieldMapper.Builder longField(String name) {
        return new LongFieldMapper.Builder(name);
    }

    public static FloatFieldMapper.Builder floatField(String name) {
        return new FloatFieldMapper.Builder(name);
    }

    public static DoubleFieldMapper.Builder doubleField(String name) {
        return new DoubleFieldMapper.Builder(name);
    }
}
