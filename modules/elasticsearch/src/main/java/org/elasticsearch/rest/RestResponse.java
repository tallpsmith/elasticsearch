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

package org.elasticsearch.rest;

import java.io.IOException;

/**
 * @author kimchy (Shay Banon)
 */
public interface RestResponse {

    /**
     * Can the content byte[] be used only with this thread (<tt>false</tt>), or by any thread (<tt>true</tt>).
     */
    boolean contentThreadSafe();

    String contentType();

    /**
     * Returns the actual content. Note, use {@link #contentLength()} in order to know the
     * content length of the byte array.
     */
    byte[] content() throws IOException;

    /**
     * The content length.
     */
    int contentLength() throws IOException;

    byte[] prefixContent();

    int prefixContentLength();

    byte[] suffixContent();

    int suffixContentLength();

    RestStatus status();
}
