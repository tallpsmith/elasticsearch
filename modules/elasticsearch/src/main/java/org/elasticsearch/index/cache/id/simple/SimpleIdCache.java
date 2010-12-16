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

package org.elasticsearch.index.cache.id.simple;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.util.StringHelper;
import org.elasticsearch.common.BytesWrap;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.collect.MapMaker;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.trove.ExtTObjectIntHasMap;
import org.elasticsearch.index.cache.id.IdCache;
import org.elasticsearch.index.cache.id.IdReaderCache;
import org.elasticsearch.index.mapper.ParentFieldMapper;
import org.elasticsearch.index.mapper.Uid;
import org.elasticsearch.index.mapper.UidFieldMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @author kimchy (shay.banon)
 */
public class SimpleIdCache implements IdCache {

    private final ConcurrentMap<Object, SimpleIdReaderCache> idReaders;

    @Inject public SimpleIdCache() {
        idReaders = new MapMaker().weakKeys().makeMap();
    }

    @Override public void clear() {
        idReaders.clear();
    }

    @Override public void clear(IndexReader reader) {
        idReaders.remove(reader.getFieldCacheKey());
    }

    @Override public void clearUnreferenced() {
        // nothing to do here...
    }

    @Override public IdReaderCache reader(IndexReader reader) {
        return idReaders.get(reader.getFieldCacheKey());
    }

    @SuppressWarnings({"unchecked"}) @Override public Iterator<IdReaderCache> iterator() {
        return (Iterator<IdReaderCache>) idReaders.values();
    }

    @SuppressWarnings({"StringEquality"})
    @Override public void refresh(IndexReader[] readers) throws Exception {
        // do a quick check for the common case, that all are there
        if (refreshNeeded(readers)) {
            synchronized (idReaders) {
                if (!refreshNeeded(readers)) {
                    return;
                }

                // do the refresh

                Map<Object, Map<String, TypeBuilder>> builders = new HashMap<Object, Map<String, TypeBuilder>>();

                // first, go over and load all the id->doc map for all types
                for (IndexReader reader : readers) {
                    if (idReaders.containsKey(reader.getFieldCacheKey())) {
                        // no need, continue
                        continue;
                    }

                    HashMap<String, TypeBuilder> readerBuilder = new HashMap<String, TypeBuilder>();
                    builders.put(reader.getFieldCacheKey(), readerBuilder);

                    String field = StringHelper.intern(UidFieldMapper.NAME);
                    TermDocs termDocs = reader.termDocs();
                    TermEnum termEnum = reader.terms(new Term(field));
                    try {
                        do {
                            Term term = termEnum.term();
                            if (term == null || term.field() != field) break;
                            // TODO we can optimize this, since type is the prefix, and we get terms ordered
                            // so, only need to move to the next type once its different
                            Uid uid = Uid.createUid(term.text());

                            TypeBuilder typeBuilder = readerBuilder.get(uid.type());
                            if (typeBuilder == null) {
                                typeBuilder = new TypeBuilder(reader);
                                readerBuilder.put(StringHelper.intern(uid.type()), typeBuilder);
                            }

                            BytesWrap idAsBytes = checkIfCanReuse(builders, new BytesWrap(uid.id()));
                            termDocs.seek(termEnum);
                            while (termDocs.next()) {
                                // when traversing, make sure to ignore deleted docs, so the key->docId will be correct
                                if (!reader.isDeleted(termDocs.doc())) {
                                    typeBuilder.idToDoc.put(idAsBytes, termDocs.doc());
                                }
                            }
                        } while (termEnum.next());
                    } finally {
                        termDocs.close();
                        termEnum.close();
                    }
                }

                // now, go and load the docId->parentId map

                for (IndexReader reader : readers) {
                    if (idReaders.containsKey(reader.getFieldCacheKey())) {
                        // no need, continue
                        continue;
                    }

                    Map<String, TypeBuilder> readerBuilder = builders.get(reader.getFieldCacheKey());

                    int t = 1;  // current term number (0 indicated null value)
                    String field = StringHelper.intern(ParentFieldMapper.NAME);
                    TermDocs termDocs = reader.termDocs();
                    TermEnum termEnum = reader.terms(new Term(field));
                    try {
                        do {
                            Term term = termEnum.term();
                            if (term == null || term.field() != field) break;
                            // TODO we can optimize this, since type is the prefix, and we get terms ordered
                            // so, only need to move to the next type once its different
                            Uid uid = Uid.createUid(term.text());

                            TypeBuilder typeBuilder = readerBuilder.get(uid.type());
                            if (typeBuilder == null) {
                                typeBuilder = new TypeBuilder(reader);
                                readerBuilder.put(StringHelper.intern(uid.type()), typeBuilder);
                            }

                            BytesWrap idAsBytes = checkIfCanReuse(builders, new BytesWrap(uid.id()));
                            boolean added = false; // optimize for when all the docs are deleted for this id

                            termDocs.seek(termEnum);
                            while (termDocs.next()) {
                                // ignore deleted docs while we are at it
                                if (!reader.isDeleted(termDocs.doc())) {
                                    if (!added) {
                                        typeBuilder.parentIdsValues.add(idAsBytes);
                                        added = true;
                                    }
                                    typeBuilder.parentIdsOrdinals[termDocs.doc()] = t;
                                }
                            }
                            if (added) {
                                t++;
                            }
                        } while (termEnum.next());
                    } finally {
                        termDocs.close();
                        termEnum.close();
                    }
                }


                // now, build it back
                for (Map.Entry<Object, Map<String, TypeBuilder>> entry : builders.entrySet()) {
                    MapBuilder<String, SimpleIdReaderTypeCache> types = MapBuilder.newMapBuilder();
                    for (Map.Entry<String, TypeBuilder> typeBuilderEntry : entry.getValue().entrySet()) {
                        types.put(typeBuilderEntry.getKey(), new SimpleIdReaderTypeCache(typeBuilderEntry.getKey(),
                                typeBuilderEntry.getValue().idToDoc,
                                typeBuilderEntry.getValue().parentIdsValues.toArray(new BytesWrap[typeBuilderEntry.getValue().parentIdsValues.size()]),
                                typeBuilderEntry.getValue().parentIdsOrdinals));
                    }
                    SimpleIdReaderCache readerCache = new SimpleIdReaderCache(entry.getKey(), types.immutableMap());
                    idReaders.put(readerCache.readerCacheKey(), readerCache);
                }
            }
        }
    }

    private BytesWrap checkIfCanReuse(Map<Object, Map<String, TypeBuilder>> builders, BytesWrap idAsBytes) {
        BytesWrap finalIdAsBytes;
        // go over and see if we can reuse this id
        for (SimpleIdReaderCache idReaderCache : idReaders.values()) {
            finalIdAsBytes = idReaderCache.canReuse(idAsBytes);
            if (finalIdAsBytes != null) {
                return finalIdAsBytes;
            }
        }
        for (Map<String, TypeBuilder> map : builders.values()) {
            for (TypeBuilder typeBuilder : map.values()) {
                finalIdAsBytes = typeBuilder.canReuse(idAsBytes);
                if (finalIdAsBytes != null) {
                    return finalIdAsBytes;
                }
            }
        }
        return idAsBytes;
    }

    private boolean refreshNeeded(IndexReader[] readers) {
        for (IndexReader reader : readers) {
            if (!idReaders.containsKey(reader.getFieldCacheKey())) {
                return true;
            }
        }
        return false;
    }

    static class TypeBuilder {
        final ExtTObjectIntHasMap<BytesWrap> idToDoc = new ExtTObjectIntHasMap<BytesWrap>().defaultReturnValue(-1);
        final ArrayList<BytesWrap> parentIdsValues = new ArrayList<BytesWrap>();
        final int[] parentIdsOrdinals;

        TypeBuilder(IndexReader reader) {
            parentIdsOrdinals = new int[reader.maxDoc()];
            // the first one indicates null value
            parentIdsValues.add(null);
        }

        /**
         * Returns an already stored instance if exists, if not, returns null;
         */
        public BytesWrap canReuse(BytesWrap id) {
            return idToDoc.key(id);
        }
    }
}
