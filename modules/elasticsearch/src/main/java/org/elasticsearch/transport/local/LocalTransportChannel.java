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

package org.elasticsearch.transport.local;

import org.elasticsearch.common.io.ThrowableObjectOutputStream;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.CachedStreamOutput;
import org.elasticsearch.common.io.stream.HandlesStreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.transport.NotSerializableTransportException;
import org.elasticsearch.transport.RemoteTransportException;
import org.elasticsearch.transport.TransportChannel;
import org.elasticsearch.transport.TransportResponseOptions;
import org.elasticsearch.transport.support.TransportStreams;

import java.io.IOException;
import java.io.NotSerializableException;

/**
 * @author kimchy (shay.banon)
 */
public class LocalTransportChannel implements TransportChannel {

    private final LocalTransport sourceTransport;

    // the transport we will *send to*
    private final LocalTransport targetTransport;

    private final String action;

    private final long requestId;

    public LocalTransportChannel(LocalTransport sourceTransport, LocalTransport targetTransport, String action, long requestId) {
        this.sourceTransport = sourceTransport;
        this.targetTransport = targetTransport;
        this.action = action;
        this.requestId = requestId;
    }

    @Override public String action() {
        return action;
    }

    @Override public void sendResponse(Streamable message) throws IOException {
        sendResponse(message, TransportResponseOptions.EMPTY);
    }

    @Override public void sendResponse(Streamable message, TransportResponseOptions options) throws IOException {
        HandlesStreamOutput stream = CachedStreamOutput.cachedHandlesBytes();
        stream.writeLong(requestId);
        byte status = 0;
        status = TransportStreams.statusSetResponse(status);
        stream.writeByte(status); // 0 for request, 1 for response.
        message.writeTo(stream);
        final byte[] data = ((BytesStreamOutput) stream.wrappedOut()).copiedByteArray();
        targetTransport.threadPool().cached().execute(new Runnable() {
            @Override public void run() {
                targetTransport.messageReceived(data, action, sourceTransport, null);
            }
        });
    }

    @Override public void sendResponse(Throwable error) throws IOException {
        BytesStreamOutput stream;
        try {
            stream = CachedStreamOutput.cachedBytes();
            writeResponseExceptionHeader(stream);
            RemoteTransportException tx = new RemoteTransportException(targetTransport.nodeName(), targetTransport.boundAddress().boundAddress(), action, error);
            ThrowableObjectOutputStream too = new ThrowableObjectOutputStream(stream);
            too.writeObject(tx);
            too.close();
        } catch (NotSerializableException e) {
            stream = CachedStreamOutput.cachedBytes();
            writeResponseExceptionHeader(stream);
            RemoteTransportException tx = new RemoteTransportException(targetTransport.nodeName(), targetTransport.boundAddress().boundAddress(), action, new NotSerializableTransportException(error));
            ThrowableObjectOutputStream too = new ThrowableObjectOutputStream(stream);
            too.writeObject(tx);
            too.close();
        }
        final byte[] data = stream.copiedByteArray();
        targetTransport.threadPool().cached().execute(new Runnable() {
            @Override public void run() {
                targetTransport.messageReceived(data, action, sourceTransport, null);
            }
        });
    }

    private void writeResponseExceptionHeader(BytesStreamOutput stream) throws IOException {
        stream.writeLong(requestId);
        byte status = 0;
        status = TransportStreams.statusSetResponse(status);
        status = TransportStreams.statusSetError(status);
        stream.writeByte(status);
    }
}
