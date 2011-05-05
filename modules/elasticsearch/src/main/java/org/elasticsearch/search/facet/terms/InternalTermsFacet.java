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

package org.elasticsearch.search.facet.terms;

import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.InternalFacet;
import org.elasticsearch.search.facet.terms.bytes.InternalByteTermsFacet;
import org.elasticsearch.search.facet.terms.doubles.InternalDoubleTermsFacet;
import org.elasticsearch.search.facet.terms.floats.InternalFloatTermsFacet;
import org.elasticsearch.search.facet.terms.ints.InternalIntTermsFacet;
import org.elasticsearch.search.facet.terms.ip.InternalIpTermsFacet;
import org.elasticsearch.search.facet.terms.longs.InternalLongTermsFacet;
import org.elasticsearch.search.facet.terms.shorts.InternalShortTermsFacet;
import org.elasticsearch.search.facet.terms.strings.InternalStringTermsFacet;

import java.util.List;

/**
 * @author kimchy (shay.banon)
 */
public abstract class InternalTermsFacet implements TermsFacet, InternalFacet {

    public static void registerStreams() {
        InternalStringTermsFacet.registerStream();
        InternalLongTermsFacet.registerStream();
        InternalDoubleTermsFacet.registerStream();
        InternalIntTermsFacet.registerStream();
        InternalFloatTermsFacet.registerStream();
        InternalShortTermsFacet.registerStream();
        InternalByteTermsFacet.registerStream();
        InternalIpTermsFacet.registerStream();
    }

    public abstract Facet reduce(String name, List<Facet> facets);
}
