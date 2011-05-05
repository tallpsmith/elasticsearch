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

package org.elasticsearch.script;

import org.elasticsearch.common.util.concurrent.NotThreadSafe;

/**
 * An executable script, can't be used concurrently.
 *
 * @author kimchy (shay.banon)
 */
@NotThreadSafe
public interface ExecutableScript {

    void setNextVar(String name, Object value);

    /**
     * Executes the script.
     */
    Object run();

    /**
     * Unwraps a possible script value. For example, when passing vars and expecting the returned value to
     * be part of the vars.
     */
    Object unwrap(Object value);
}
