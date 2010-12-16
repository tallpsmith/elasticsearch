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

package org.elasticsearch.index.field.data.longs;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldCache;
import org.elasticsearch.common.joda.time.MutableDateTime;
import org.elasticsearch.common.thread.ThreadLocals;
import org.elasticsearch.common.trove.TLongArrayList;
import org.elasticsearch.index.field.data.FieldDataType;
import org.elasticsearch.index.field.data.NumericFieldData;
import org.elasticsearch.index.field.data.support.FieldDataLoader;

import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
public abstract class LongFieldData extends NumericFieldData<LongDocFieldData> {

    static final long[] EMPTY_LONG_ARRAY = new long[0];
    static final MutableDateTime[] EMPTY_DATETIME_ARRAY = new MutableDateTime[0];

    private ThreadLocal<ThreadLocals.CleanableValue<MutableDateTime>> dateTimeCache = new ThreadLocal<ThreadLocals.CleanableValue<MutableDateTime>>() {
        @Override protected ThreadLocals.CleanableValue<MutableDateTime> initialValue() {
            return new ThreadLocals.CleanableValue<MutableDateTime>(new MutableDateTime());
        }
    };

    protected final long[] values;

    protected LongFieldData(String fieldName, long[] values) {
        super(fieldName);
        this.values = values;
    }

    abstract public long value(int docId);

    abstract public long[] values(int docId);

    public MutableDateTime date(int docId) {
        MutableDateTime dateTime = dateTimeCache.get().get();
        dateTime.setMillis(value(docId));
        return dateTime;
    }

    public abstract MutableDateTime[] dates(int docId);

    @Override public LongDocFieldData docFieldData(int docId) {
        return super.docFieldData(docId);
    }

    @Override protected LongDocFieldData createFieldData() {
        return new LongDocFieldData(this);
    }

    @Override public void forEachValue(StringValueProc proc) {
        for (int i = 1; i < values.length; i++) {
            proc.onValue(Long.toString(values[i]));
        }
    }

    @Override public String stringValue(int docId) {
        return Long.toString(docId);
    }

    @Override public byte byteValue(int docId) {
        return (byte) value(docId);
    }

    @Override public short shortValue(int docId) {
        return (short) value(docId);
    }

    @Override public int intValue(int docId) {
        return (int) value(docId);
    }

    @Override public long longValue(int docId) {
        return value(docId);
    }

    @Override public float floatValue(int docId) {
        return (float) value(docId);
    }

    @Override public double doubleValue(int docId) {
        return (double) value(docId);
    }

    @Override public FieldDataType type() {
        return FieldDataType.DefaultTypes.LONG;
    }

    public void forEachValue(ValueProc proc) {
        for (int i = 1; i < values.length; i++) {
            proc.onValue(values[i]);
        }
    }

    public static interface ValueProc {
        void onValue(long value);
    }


    public static LongFieldData load(IndexReader reader, String field) throws IOException {
        return FieldDataLoader.load(reader, field, new LongTypeLoader());
    }

    static class LongTypeLoader extends FieldDataLoader.FreqsTypeLoader<LongFieldData> {

        private final TLongArrayList terms = new TLongArrayList();

        LongTypeLoader() {
            super();
            // the first one indicates null value
            terms.add(0);
        }

        @Override public void collectTerm(String term) {
            terms.add(FieldCache.NUMERIC_UTILS_LONG_PARSER.parseLong(term));
        }

        @Override public LongFieldData buildSingleValue(String field, int[] ordinals) {
            return new SingleValueLongFieldData(field, ordinals, terms.toNativeArray());
        }

        @Override public LongFieldData buildMultiValue(String field, int[][] ordinals) {
            return new MultiValueLongFieldData(field, ordinals, terms.toNativeArray());
        }
    }
}