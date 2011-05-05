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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.NumericRangeFilter;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.NumericUtils;
import org.elasticsearch.common.Numbers;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.analysis.NumericIntegerAnalyzer;
import org.elasticsearch.index.cache.field.data.FieldDataCache;
import org.elasticsearch.index.field.data.FieldDataType;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.MergeMappingException;
import org.elasticsearch.index.search.NumericRangeFieldDataFilter;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.common.xcontent.support.XContentMapValues.*;
import static org.elasticsearch.index.mapper.xcontent.XContentMapperBuilders.*;
import static org.elasticsearch.index.mapper.xcontent.XContentTypeParsers.*;

/**
 * @author kimchy (shay.banon)
 */
public class ShortFieldMapper extends NumberFieldMapper<Short> {

    public static final String CONTENT_TYPE = "short";

    public static class Defaults extends NumberFieldMapper.Defaults {
        public static final Short NULL_VALUE = null;
    }

    public static class Builder extends NumberFieldMapper.Builder<Builder, ShortFieldMapper> {

        protected Short nullValue = Defaults.NULL_VALUE;

        public Builder(String name) {
            super(name);
            builder = this;
        }

        public Builder nullValue(short nullValue) {
            this.nullValue = nullValue;
            return this;
        }

        @Override public ShortFieldMapper build(BuilderContext context) {
            ShortFieldMapper fieldMapper = new ShortFieldMapper(buildNames(context),
                    precisionStep, index, store, boost, omitNorms, omitTermFreqAndPositions, nullValue);
            fieldMapper.includeInAll(includeInAll);
            return fieldMapper;
        }
    }

    public static class TypeParser implements XContentMapper.TypeParser {
        @Override public XContentMapper.Builder parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            ShortFieldMapper.Builder builder = shortField(name);
            parseNumberField(builder, name, node, parserContext);
            for (Map.Entry<String, Object> entry : node.entrySet()) {
                String propName = Strings.toUnderscoreCase(entry.getKey());
                Object propNode = entry.getValue();
                if (propName.equals("null_value")) {
                    builder.nullValue(nodeShortValue(propNode));
                }
            }
            return builder;
        }
    }

    private Short nullValue;

    private String nullValueAsString;

    protected ShortFieldMapper(Names names, int precisionStep, Field.Index index, Field.Store store,
                               float boost, boolean omitNorms, boolean omitTermFreqAndPositions,
                               Short nullValue) {
        super(names, precisionStep, index, store, boost, omitNorms, omitTermFreqAndPositions,
                new NamedAnalyzer("_short/" + precisionStep, new NumericIntegerAnalyzer(precisionStep)),
                new NamedAnalyzer("_short/max", new NumericIntegerAnalyzer(Integer.MAX_VALUE)));
        this.nullValue = nullValue;
        this.nullValueAsString = nullValue == null ? null : nullValue.toString();
    }

    @Override protected int maxPrecisionStep() {
        return 32;
    }

    @Override public Short value(Fieldable field) {
        byte[] value = field.getBinaryValue();
        if (value == null) {
            return null;
        }
        return Numbers.bytesToShort(value);
    }

    @Override public Short valueFromString(String value) {
        return Short.valueOf(value);
    }

    @Override public String indexedValue(String value) {
        return NumericUtils.intToPrefixCoded(Short.parseShort(value));
    }

    @Override public Query rangeQuery(String lowerTerm, String upperTerm, boolean includeLower, boolean includeUpper) {
        return NumericRangeQuery.newIntRange(names.indexName(), precisionStep,
                lowerTerm == null ? null : Integer.parseInt(lowerTerm),
                upperTerm == null ? null : Integer.parseInt(upperTerm),
                includeLower, includeUpper);
    }

    @Override public Filter rangeFilter(String lowerTerm, String upperTerm, boolean includeLower, boolean includeUpper) {
        return NumericRangeFilter.newIntRange(names.indexName(), precisionStep,
                lowerTerm == null ? null : Integer.parseInt(lowerTerm),
                upperTerm == null ? null : Integer.parseInt(upperTerm),
                includeLower, includeUpper);
    }

    @Override public Filter rangeFilter(FieldDataCache fieldDataCache, String lowerTerm, String upperTerm, boolean includeLower, boolean includeUpper) {
        return NumericRangeFieldDataFilter.newShortRange(fieldDataCache, names.indexName(),
                lowerTerm == null ? null : Short.parseShort(lowerTerm),
                upperTerm == null ? null : Short.parseShort(upperTerm),
                includeLower, includeUpper);
    }

    @Override protected Fieldable parseCreateField(ParseContext context) throws IOException {
        short value;
        if (context.externalValueSet()) {
            Object externalValue = context.externalValue();
            if (externalValue == null) {
                if (nullValue == null) {
                    return null;
                }
                value = nullValue;
            } else {
                value = ((Number) externalValue).shortValue();
            }
            if (context.includeInAll(includeInAll)) {
                context.allEntries().addText(names.fullName(), Short.toString(value), boost);
            }
        } else {
            if (context.parser().currentToken() == XContentParser.Token.VALUE_NULL) {
                if (nullValue == null) {
                    return null;
                }
                value = nullValue;
                if (nullValueAsString != null && (context.includeInAll(includeInAll))) {
                    context.allEntries().addText(names.fullName(), nullValueAsString, boost);
                }
            } else {
                value = context.parser().shortValue();
                if (context.includeInAll(includeInAll)) {
                    context.allEntries().addText(names.fullName(), context.parser().text(), boost);
                }
            }
        }
        return new CustomShortNumericField(this, value);
    }

    @Override public FieldDataType fieldDataType() {
        return FieldDataType.DefaultTypes.SHORT;
    }

    @Override protected String contentType() {
        return CONTENT_TYPE;
    }

    @Override public void merge(XContentMapper mergeWith, MergeContext mergeContext) throws MergeMappingException {
        super.merge(mergeWith, mergeContext);
        if (!this.getClass().equals(mergeWith.getClass())) {
            return;
        }
        if (!mergeContext.mergeFlags().simulate()) {
            this.nullValue = ((ShortFieldMapper) mergeWith).nullValue;
            this.nullValueAsString = ((ShortFieldMapper) mergeWith).nullValueAsString;
        }
    }

    @Override protected void doXContentBody(XContentBuilder builder) throws IOException {
        super.doXContentBody(builder);
        if (index != Defaults.INDEX) {
            builder.field("index", index.name().toLowerCase());
        }
        if (store != Defaults.STORE) {
            builder.field("store", store.name().toLowerCase());
        }
        if (termVector != Defaults.TERM_VECTOR) {
            builder.field("term_vector", termVector.name().toLowerCase());
        }
        if (omitNorms != Defaults.OMIT_NORMS) {
            builder.field("omit_norms", omitNorms);
        }
        if (omitTermFreqAndPositions != Defaults.OMIT_TERM_FREQ_AND_POSITIONS) {
            builder.field("omit_term_freq_and_positions", omitTermFreqAndPositions);
        }
        if (precisionStep != Defaults.PRECISION_STEP) {
            builder.field("precision_step", precisionStep);
        }
        if (nullValue != null) {
            builder.field("null_value", nullValue);
        }
        if (includeInAll != null) {
            builder.field("include_in_all", includeInAll);
        }
    }

    public static class CustomShortNumericField extends CustomNumericField {

        private final short number;

        private final NumberFieldMapper mapper;

        public CustomShortNumericField(NumberFieldMapper mapper, short number) {
            super(mapper, mapper.stored() ? Numbers.shortToBytes(number) : null);
            this.mapper = mapper;
            this.number = number;
        }

        @Override public TokenStream tokenStreamValue() {
            if (isIndexed) {
                return mapper.popCachedStream().setIntValue(number);
            }
            return null;
        }
    }
}