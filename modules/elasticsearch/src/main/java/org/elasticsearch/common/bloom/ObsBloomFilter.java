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

package org.elasticsearch.common.bloom;

import org.apache.lucene.util.OpenBitSet;
import org.elasticsearch.common.RamUsage;

import java.nio.ByteBuffer;

public class ObsBloomFilter implements BloomFilter {

    private final int hashCount;

    private final OpenBitSet bitset;

    ObsBloomFilter(int hashCount, OpenBitSet bs) {
        this.hashCount = hashCount;
        this.bitset = bs;
    }

    long emptyBuckets() {
        long n = 0;
        for (long i = 0; i < buckets(); i++) {
            if (!bitset.get(i)) {
                n++;
            }
        }
        return n;
    }

    private long buckets() {
        return bitset.size();
    }

    private long[] getHashBuckets(ByteBuffer key) {
        return getHashBuckets(key, hashCount, buckets());
    }

    private long[] getHashBuckets(byte[] key, int offset, int length) {
        return getHashBuckets(key, offset, length, hashCount, buckets());
    }

    // Murmur is faster than an SHA-based approach and provides as-good collision
    // resistance.  The combinatorial generation approach described in
    // http://www.eecs.harvard.edu/~kirsch/pubs/bbbf/esa06.pdf
    // does prove to work in actual tests, and is obviously faster
    // than performing further iterations of murmur.
    static long[] getHashBuckets(ByteBuffer b, int hashCount, long max) {
        long[] result = new long[hashCount];
        long hash1 = MurmurHash.hash64(b, b.position(), b.remaining(), 0L);
        long hash2 = MurmurHash.hash64(b, b.position(), b.remaining(), hash1);
        for (int i = 0; i < hashCount; ++i) {
            result[i] = Math.abs((hash1 + (long) i * hash2) % max);
        }
        return result;
    }

    // Murmur is faster than an SHA-based approach and provides as-good collision
    // resistance.  The combinatorial generation approach described in
    // http://www.eecs.harvard.edu/~kirsch/pubs/bbbf/esa06.pdf
    // does prove to work in actual tests, and is obviously faster
    // than performing further iterations of murmur.
    static long[] getHashBuckets(byte[] b, int offset, int length, int hashCount, long max) {
        long[] result = new long[hashCount];
        long hash1 = MurmurHash.hash64(b, offset, length, 0L);
        long hash2 = MurmurHash.hash64(b, offset, length, hash1);
        for (int i = 0; i < hashCount; ++i) {
            result[i] = Math.abs((hash1 + (long) i * hash2) % max);
        }
        return result;
    }

    @Override public void add(byte[] key, int offset, int length) {
        for (long bucketIndex : getHashBuckets(key, offset, length)) {
            bitset.fastSet(bucketIndex);
        }
    }

    public void add(ByteBuffer key) {
        for (long bucketIndex : getHashBuckets(key)) {
            bitset.fastSet(bucketIndex);
        }
    }

    @Override public boolean isPresent(byte[] key, int offset, int length) {
        for (long bucketIndex : getHashBuckets(key, offset, length)) {
            if (!bitset.fastGet(bucketIndex)) {
                return false;
            }
        }
        return true;
    }

    public boolean isPresent(ByteBuffer key) {
        for (long bucketIndex : getHashBuckets(key)) {
            if (!bitset.fastGet(bucketIndex)) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        bitset.clear(0, bitset.size());
    }

    @Override public long sizeInBytes() {
        return bitset.getBits().length * RamUsage.NUM_BYTES_LONG + RamUsage.NUM_BYTES_ARRAY_HEADER + RamUsage.NUM_BYTES_INT /* wlen */;
    }
}
