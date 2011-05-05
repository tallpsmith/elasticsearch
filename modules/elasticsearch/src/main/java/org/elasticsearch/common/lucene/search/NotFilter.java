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

package org.elasticsearch.common.lucene.search;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.elasticsearch.common.lucene.docset.AllDocSet;
import org.elasticsearch.common.lucene.docset.DocSet;
import org.elasticsearch.common.lucene.docset.NotDocIdSet;
import org.elasticsearch.common.lucene.docset.NotDocSet;

import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
public class NotFilter extends Filter {

    private final Filter filter;

    public NotFilter(Filter filter) {
        this.filter = filter;
    }

    public Filter filter() {
        return filter;
    }

    @Override public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        DocIdSet set = filter.getDocIdSet(reader);
        if (set == null) {
            return new AllDocSet(reader.maxDoc());
        }
        if (set instanceof DocSet) {
            return new NotDocSet((DocSet) set, reader.maxDoc());
        }
        return new NotDocIdSet(set, reader.maxDoc());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NotFilter notFilter = (NotFilter) o;
        return !(filter != null ? !filter.equals(notFilter.filter) : notFilter.filter != null);
    }

    @Override
    public int hashCode() {
        return filter != null ? filter.hashCode() : 0;
    }
}
