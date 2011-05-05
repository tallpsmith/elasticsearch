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

package org.elasticsearch.index.field.data.support;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.util.StringHelper;
import org.elasticsearch.index.field.data.FieldData;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author kimchy (shay.banon)
 */
public class FieldDataLoader {

    @SuppressWarnings({"StringEquality"})
    public static <T extends FieldData> T load(IndexReader reader, String field, TypeLoader<T> loader) throws IOException {

        loader.init();

        field = StringHelper.intern(field);
        ArrayList<int[]> ordinals = new ArrayList<int[]>();
        ordinals.add(new int[reader.maxDoc()]);

        int t = 1;  // current term number

        TermDocs termDocs = reader.termDocs();
        TermEnum termEnum = reader.terms(new Term(field));
        try {
            do {
                Term term = termEnum.term();
                if (term == null || term.field() != field) break;
                loader.collectTerm(term.text());
                termDocs.seek(termEnum);
                while (termDocs.next()) {
                    int doc = termDocs.doc();
                    boolean found = false;
                    for (int i = 0; i < ordinals.size(); i++) {
                        int[] ordinal = ordinals.get(i);
                        if (ordinal[doc] == 0) {
                            // we found a spot, use it
                            ordinal[doc] = t;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        // did not find one, increase by one and redo
                        int[] ordinal = new int[reader.maxDoc()];
                        ordinals.add(ordinal);
                        ordinal[doc] = t;
                    }
                }
                t++;
            } while (termEnum.next());
        } catch (RuntimeException e) {
            if (e.getClass().getName().endsWith("StopFillCacheException")) {
                // all is well, in case numeric parsers are used.
            } else {
                throw e;
            }
        } finally {
            termDocs.close();
            termEnum.close();
        }

        if (ordinals.size() == 1) {
            return loader.buildSingleValue(field, ordinals.get(0));
        } else {
            int[][] nativeOrdinals = new int[ordinals.size()][];
            for (int i = 0; i < nativeOrdinals.length; i++) {
                nativeOrdinals[i] = ordinals.get(i);
            }
            return loader.buildMultiValue(field, nativeOrdinals);
        }
    }

    public static interface TypeLoader<T extends FieldData> {

        void init();

        void collectTerm(String term);

        T buildSingleValue(String fieldName, int[] ordinals);

        T buildMultiValue(String fieldName, int[][] ordinals);
    }

    public static abstract class FreqsTypeLoader<T extends FieldData> implements TypeLoader<T> {

        protected FreqsTypeLoader() {
        }

        @Override public void init() {
        }
    }
}
