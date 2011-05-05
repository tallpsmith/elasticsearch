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

package org.elasticsearch.index.engine;

import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.deletionpolicy.KeepOnlyLastDeletionPolicy;
import org.elasticsearch.index.deletionpolicy.SnapshotDeletionPolicy;
import org.elasticsearch.index.deletionpolicy.SnapshotIndexCommit;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.merge.policy.LogByteSizeMergePolicyProvider;
import org.elasticsearch.index.merge.policy.MergePolicyProvider;
import org.elasticsearch.index.merge.scheduler.MergeSchedulerProvider;
import org.elasticsearch.index.merge.scheduler.SerialMergeSchedulerProvider;
import org.elasticsearch.index.settings.IndexSettingsService;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.store.Store;
import org.elasticsearch.index.store.ram.RamStore;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.index.translog.fs.FsTranslog;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.elasticsearch.common.lucene.DocumentBuilder.*;
import static org.elasticsearch.common.settings.ImmutableSettings.Builder.*;
import static org.elasticsearch.index.deletionpolicy.SnapshotIndexCommitExistsMatcher.*;
import static org.elasticsearch.index.engine.Engine.Operation.Origin.*;
import static org.elasticsearch.index.engine.EngineSearcherTotalHitsMatcher.*;
import static org.elasticsearch.index.translog.TranslogSizeMatcher.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * @author kimchy (Shay Banon)
 */
public abstract class AbstractSimpleEngineTests {

    protected final ShardId shardId = new ShardId(new Index("index"), 1);

    private Store store;
    private Store storeReplica;

    protected Engine engine;
    protected Engine replicaEngine;

    @BeforeMethod public void setUp() throws Exception {
        store = createStore();
        store.deleteContent();
        storeReplica = createStoreReplica();
        storeReplica.deleteContent();
        engine = createEngine(store, createTranslog());
        engine.start();
        replicaEngine = createEngine(storeReplica, createTranslogReplica());
        replicaEngine.start();
    }

    @AfterMethod public void tearDown() throws Exception {
        replicaEngine.close();
        storeReplica.close();

        engine.close();
        store.close();
    }

    protected Store createStore() throws IOException {
        return new RamStore(shardId, EMPTY_SETTINGS, null);
    }

    protected Store createStoreReplica() throws IOException {
        return new RamStore(shardId, EMPTY_SETTINGS, null);
    }

    protected Translog createTranslog() {
        return new FsTranslog(shardId, EMPTY_SETTINGS, new File("work/fs-translog/primary"), false);
    }

    protected Translog createTranslogReplica() {
        return new FsTranslog(shardId, EMPTY_SETTINGS, new File("work/fs-translog/replica"), false);
    }

    protected IndexDeletionPolicy createIndexDeletionPolicy() {
        return new KeepOnlyLastDeletionPolicy(shardId, EMPTY_SETTINGS);
    }

    protected SnapshotDeletionPolicy createSnapshotDeletionPolicy() {
        return new SnapshotDeletionPolicy(createIndexDeletionPolicy());
    }

    protected MergePolicyProvider createMergePolicy() {
        return new LogByteSizeMergePolicyProvider(store, new IndexSettingsService(new Index("test"), EMPTY_SETTINGS));
    }

    protected MergeSchedulerProvider createMergeScheduler() {
        return new SerialMergeSchedulerProvider(shardId, EMPTY_SETTINGS);
    }

    protected abstract Engine createEngine(Store store, Translog translog);

    protected static final byte[] B_1 = new byte[]{1};
    protected static final byte[] B_2 = new byte[]{2};
    protected static final byte[] B_3 = new byte[]{3};

    @Test public void testSimpleOperations() throws Exception {
        Engine.Searcher searchResult = engine.searcher();

        assertThat(searchResult, engineSearcherTotalHits(0));
        searchResult.release();

        // create a document
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        engine.create(new Engine.Create(null, newUid("1"), doc));

        // its not there...
        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(0));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        searchResult.release();

        // refresh and it should be there
        engine.refresh(new Engine.Refresh(true));

        // now its there...
        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        searchResult.release();

        // now do an update
        doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).add(field("value", "test1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        engine.index(new Engine.Index(null, newUid("1"), doc));

        // its not updated yet...
        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.release();

        // refresh and it should be updated
        engine.refresh(new Engine.Refresh(true));

        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 1));
        searchResult.release();

        // now delete
        engine.delete(new Engine.Delete("test", "1", newUid("1")));

        // its not deleted yet
        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 1));
        searchResult.release();

        // refresh and it should be deleted
        engine.refresh(new Engine.Refresh(true));

        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(0));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.release();

        // add it back
        doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        engine.create(new Engine.Create(null, newUid("1"), doc));

        // its not there...
        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(0));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.release();

        // refresh and it should be there
        engine.refresh(new Engine.Refresh(true));

        // now its there...
        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.release();

        // now flush
        engine.flush(new Engine.Flush());

        // make sure we can still work with the engine
        // now do an update
        doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).add(field("value", "test1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        engine.index(new Engine.Index(null, newUid("1"), doc));

        // its not updated yet...
        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 0));
        searchResult.release();

        // refresh and it should be updated
        engine.refresh(new Engine.Refresh(true));

        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test1")), 1));
        searchResult.release();

        engine.close();
    }

    @Test public void testSearchResultRelease() throws Exception {
        Engine.Searcher searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(0));
        searchResult.release();

        // create a document
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        engine.create(new Engine.Create(null, newUid("1"), doc));

        // its not there...
        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(0));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 0));
        searchResult.release();

        // refresh and it should be there
        engine.refresh(new Engine.Refresh(true));

        // now its there...
        searchResult = engine.searcher();
        assertThat(searchResult, engineSearcherTotalHits(1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        // don't release the search result yet...

        // delete, refresh and do a new search, it should not be there
        engine.delete(new Engine.Delete("test", "1", newUid("1")));
        engine.refresh(new Engine.Refresh(true));
        Engine.Searcher updateSearchResult = engine.searcher();
        assertThat(updateSearchResult, engineSearcherTotalHits(0));
        updateSearchResult.release();

        // the non release search result should not see the deleted yet...
        assertThat(searchResult, engineSearcherTotalHits(1));
        assertThat(searchResult, engineSearcherTotalHits(new TermQuery(new Term("value", "test")), 1));
        searchResult.release();
    }

    @Test public void testSimpleSnapshot() throws Exception {
        // create a document
        ParsedDocument doc1 = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        engine.create(new Engine.Create(null, newUid("1"), doc1));

        final ExecutorService executorService = Executors.newCachedThreadPool();

        engine.snapshot(new Engine.SnapshotHandler<Void>() {
            @Override public Void snapshot(final SnapshotIndexCommit snapshotIndexCommit1, final Translog.Snapshot translogSnapshot1) {
                assertThat(snapshotIndexCommit1, snapshotIndexCommitExists());
                assertThat(translogSnapshot1.hasNext(), equalTo(true));
                Translog.Create create1 = (Translog.Create) translogSnapshot1.next();
                assertThat(create1.source(), equalTo(B_1));
                assertThat(translogSnapshot1.hasNext(), equalTo(false));

                Future<Object> future = executorService.submit(new Callable<Object>() {
                    @Override public Object call() throws Exception {
                        engine.flush(new Engine.Flush());
                        ParsedDocument doc2 = new ParsedDocument("2", "2", "test", null, doc().add(uidField("2")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_2, false);
                        engine.create(new Engine.Create(null, newUid("2"), doc2));
                        engine.flush(new Engine.Flush());
                        ParsedDocument doc3 = new ParsedDocument("3", "3", "test", null, doc().add(uidField("3")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_3, false);
                        engine.create(new Engine.Create(null, newUid("3"), doc3));
                        return null;
                    }
                });

                try {
                    future.get();
                } catch (Exception e) {
                    e.printStackTrace();
                    assertThat(e.getMessage(), false, equalTo(true));
                }

                assertThat(snapshotIndexCommit1, snapshotIndexCommitExists());

                engine.snapshot(new Engine.SnapshotHandler<Void>() {
                    @Override public Void snapshot(SnapshotIndexCommit snapshotIndexCommit2, Translog.Snapshot translogSnapshot2) throws EngineException {
                        assertThat(snapshotIndexCommit1, snapshotIndexCommitExists());
                        assertThat(snapshotIndexCommit2, snapshotIndexCommitExists());
                        assertThat(snapshotIndexCommit2.getSegmentsFileName(), not(equalTo(snapshotIndexCommit1.getSegmentsFileName())));
                        assertThat(translogSnapshot2.hasNext(), equalTo(true));
                        Translog.Create create3 = (Translog.Create) translogSnapshot2.next();
                        assertThat(create3.source(), equalTo(B_3));
                        assertThat(translogSnapshot2.hasNext(), equalTo(false));
                        return null;
                    }
                });
                return null;
            }
        });

        engine.close();
    }

    @Test public void testSimpleRecover() throws Exception {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        engine.create(new Engine.Create(null, newUid("1"), doc));
        engine.flush(new Engine.Flush());

        engine.recover(new Engine.RecoveryHandler() {
            @Override public void phase1(SnapshotIndexCommit snapshot) throws EngineException {
                try {
                    engine.flush(new Engine.Flush());
                    assertThat("flush is not allowed in phase 3", false, equalTo(true));
                } catch (FlushNotAllowedEngineException e) {
                    // all is well
                }
            }

            @Override public void phase2(Translog.Snapshot snapshot) throws EngineException {
                assertThat(snapshot, translogSize(0));
                try {
                    engine.flush(new Engine.Flush());
                    assertThat("flush is not allowed in phase 3", false, equalTo(true));
                } catch (FlushNotAllowedEngineException e) {
                    // all is well
                }
            }

            @Override public void phase3(Translog.Snapshot snapshot) throws EngineException {
                assertThat(snapshot, translogSize(0));
                try {
                    // we can do this here since we are on the same thread
                    engine.flush(new Engine.Flush());
                    assertThat("flush is not allowed in phase 3", false, equalTo(true));
                } catch (FlushNotAllowedEngineException e) {
                    // all is well
                }
            }
        });

        engine.flush(new Engine.Flush());
        engine.close();
    }

    @Test public void testRecoverWithOperationsBetweenPhase1AndPhase2() throws Exception {
        ParsedDocument doc1 = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        engine.create(new Engine.Create(null, newUid("1"), doc1));
        engine.flush(new Engine.Flush());
        ParsedDocument doc2 = new ParsedDocument("2", "2", "test", null, doc().add(uidField("2")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_2, false);
        engine.create(new Engine.Create(null, newUid("2"), doc2));

        engine.recover(new Engine.RecoveryHandler() {
            @Override public void phase1(SnapshotIndexCommit snapshot) throws EngineException {
            }

            @Override public void phase2(Translog.Snapshot snapshot) throws EngineException {
                assertThat(snapshot.hasNext(), equalTo(true));
                Translog.Create create = (Translog.Create) snapshot.next();
                assertThat(create.source(), equalTo(B_2));
                assertThat(snapshot.hasNext(), equalTo(false));
            }

            @Override public void phase3(Translog.Snapshot snapshot) throws EngineException {
                assertThat(snapshot, translogSize(0));
            }
        });

        engine.flush(new Engine.Flush());
        engine.close();
    }

    @Test public void testRecoverWithOperationsBetweenPhase1AndPhase2AndPhase3() throws Exception {
        ParsedDocument doc1 = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        engine.create(new Engine.Create(null, newUid("1"), doc1));
        engine.flush(new Engine.Flush());
        ParsedDocument doc2 = new ParsedDocument("2", "2", "test", null, doc().add(uidField("2")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_2, false);
        engine.create(new Engine.Create(null, newUid("2"), doc2));

        engine.recover(new Engine.RecoveryHandler() {
            @Override public void phase1(SnapshotIndexCommit snapshot) throws EngineException {
            }

            @Override public void phase2(Translog.Snapshot snapshot) throws EngineException {
                assertThat(snapshot.hasNext(), equalTo(true));
                Translog.Create create = (Translog.Create) snapshot.next();
                assertThat(snapshot.hasNext(), equalTo(false));
                assertThat(create.source(), equalTo(B_2));

                // add for phase3
                ParsedDocument doc3 = new ParsedDocument("3", "3", "test", null, doc().add(uidField("3")).add(field("value", "test")).build(), Lucene.STANDARD_ANALYZER, B_3, false);
                engine.create(new Engine.Create(null, newUid("3"), doc3));
            }

            @Override public void phase3(Translog.Snapshot snapshot) throws EngineException {
                assertThat(snapshot.hasNext(), equalTo(true));
                Translog.Create create = (Translog.Create) snapshot.next();
                assertThat(snapshot.hasNext(), equalTo(false));
                assertThat(create.source(), equalTo(B_3));
            }
        });

        engine.flush(new Engine.Flush());
        engine.close();
    }

    @Test public void testVersioningNewCreate() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Create create = new Engine.Create(null, newUid("1"), doc);
        engine.create(create);
        assertThat(create.version(), equalTo(1l));

        create = new Engine.Create(null, newUid("1"), doc).version(create.version()).origin(REPLICA);
        replicaEngine.create(create);
        assertThat(create.version(), equalTo(1l));
    }

    @Test public void testExternalVersioningNewCreate() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Create create = new Engine.Create(null, newUid("1"), doc).versionType(VersionType.EXTERNAL).version(12);
        engine.create(create);
        assertThat(create.version(), equalTo(12l));

        create = new Engine.Create(null, newUid("1"), doc).version(create.version()).origin(REPLICA);
        replicaEngine.create(create);
        assertThat(create.version(), equalTo(12l));
    }

    @Test public void testVersioningNewIndex() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Index index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(1l));

        index = new Engine.Index(null, newUid("1"), doc).version(index.version()).origin(REPLICA);
        replicaEngine.index(index);
        assertThat(index.version(), equalTo(1l));
    }

    @Test public void testExternalVersioningNewIndex() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Index index = new Engine.Index(null, newUid("1"), doc).versionType(VersionType.EXTERNAL).version(12);
        engine.index(index);
        assertThat(index.version(), equalTo(12l));

        index = new Engine.Index(null, newUid("1"), doc).version(index.version()).origin(REPLICA);
        replicaEngine.index(index);
        assertThat(index.version(), equalTo(12l));
    }

    @Test public void testVersioningIndexConflict() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Index index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(1l));

        index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(2l));

        index = new Engine.Index(null, newUid("1"), doc).version(1l);
        try {
            engine.index(index);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }

        // future versions should not work as well
        index = new Engine.Index(null, newUid("1"), doc).version(3l);
        try {
            engine.index(index);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }
    }

    @Test public void testExternalVersioningIndexConflict() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Index index = new Engine.Index(null, newUid("1"), doc).versionType(VersionType.EXTERNAL).version(12);
        engine.index(index);
        assertThat(index.version(), equalTo(12l));

        index = new Engine.Index(null, newUid("1"), doc).versionType(VersionType.EXTERNAL).version(14);
        engine.index(index);
        assertThat(index.version(), equalTo(14l));

        index = new Engine.Index(null, newUid("1"), doc).versionType(VersionType.EXTERNAL).version(13l);
        try {
            engine.index(index);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }
    }

    @Test public void testVersioningIndexConflictWithFlush() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Index index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(1l));

        index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(2l));

        engine.flush(new Engine.Flush());

        index = new Engine.Index(null, newUid("1"), doc).version(1l);
        try {
            engine.index(index);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }

        // future versions should not work as well
        index = new Engine.Index(null, newUid("1"), doc).version(3l);
        try {
            engine.index(index);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }
    }

    @Test public void testExternalVersioningIndexConflictWithFlush() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Index index = new Engine.Index(null, newUid("1"), doc).versionType(VersionType.EXTERNAL).version(12);
        engine.index(index);
        assertThat(index.version(), equalTo(12l));

        index = new Engine.Index(null, newUid("1"), doc).versionType(VersionType.EXTERNAL).version(14);
        engine.index(index);
        assertThat(index.version(), equalTo(14l));

        engine.flush(new Engine.Flush());

        index = new Engine.Index(null, newUid("1"), doc).versionType(VersionType.EXTERNAL).version(13);
        try {
            engine.index(index);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }
    }

    @Test public void testVersioningDeleteConflict() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Index index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(1l));

        index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(2l));

        Engine.Delete delete = new Engine.Delete("test", "1", newUid("1")).version(1l);
        try {
            engine.delete(delete);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }

        // future versions should not work as well
        delete = new Engine.Delete("test", "1", newUid("1")).version(3l);
        try {
            engine.delete(delete);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }

        // now actually delete
        delete = new Engine.Delete("test", "1", newUid("1")).version(2l);
        engine.delete(delete);
        assertThat(delete.version(), equalTo(3l));

        // now check if we can index to a delete doc with version
        index = new Engine.Index(null, newUid("1"), doc).version(2l);
        try {
            engine.index(index);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }

        // we shouldn't be able to create as well
        Engine.Create create = new Engine.Create(null, newUid("1"), doc).version(2l);
        try {
            engine.create(create);
        } catch (VersionConflictEngineException e) {
            // all is well
        }
    }

    @Test public void testVersioningDeleteConflictWithFlush() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Index index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(1l));

        index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(2l));

        engine.flush(new Engine.Flush());

        Engine.Delete delete = new Engine.Delete("test", "1", newUid("1")).version(1l);
        try {
            engine.delete(delete);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }

        // future versions should not work as well
        delete = new Engine.Delete("test", "1", newUid("1")).version(3l);
        try {
            engine.delete(delete);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }

        engine.flush(new Engine.Flush());

        // now actually delete
        delete = new Engine.Delete("test", "1", newUid("1")).version(2l);
        engine.delete(delete);
        assertThat(delete.version(), equalTo(3l));

        engine.flush(new Engine.Flush());

        // now check if we can index to a delete doc with version
        index = new Engine.Index(null, newUid("1"), doc).version(2l);
        try {
            engine.index(index);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }

        // we shouldn't be able to create as well
        Engine.Create create = new Engine.Create(null, newUid("1"), doc).version(2l);
        try {
            engine.create(create);
        } catch (VersionConflictEngineException e) {
            // all is well
        }
    }

    @Test public void testVersioningCreateExistsException() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Create create = new Engine.Create(null, newUid("1"), doc);
        engine.create(create);
        assertThat(create.version(), equalTo(1l));

        create = new Engine.Create(null, newUid("1"), doc);
        try {
            engine.create(create);
            assert false;
        } catch (DocumentAlreadyExistsEngineException e) {
            // all is well
        }
    }

    @Test public void testVersioningCreateExistsExceptionWithFlush() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Create create = new Engine.Create(null, newUid("1"), doc);
        engine.create(create);
        assertThat(create.version(), equalTo(1l));

        engine.flush(new Engine.Flush());

        create = new Engine.Create(null, newUid("1"), doc);
        try {
            engine.create(create);
            assert false;
        } catch (DocumentAlreadyExistsEngineException e) {
            // all is well
        }
    }

    @Test public void testVersioningReplicaConflict1() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Index index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(1l));

        index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(2l));

        // apply the second index to the replica, should work fine
        index = new Engine.Index(null, newUid("1"), doc).version(2l).origin(REPLICA);
        replicaEngine.index(index);
        assertThat(index.version(), equalTo(2l));

        // now, the old one should not work
        index = new Engine.Index(null, newUid("1"), doc).version(1l).origin(REPLICA);
        try {
            replicaEngine.index(index);
            assert false;
        } catch (VersionConflictEngineException e) {
            // all is well
        }

        // second version on replica should fail as well
        try {
            index = new Engine.Index(null, newUid("1"), doc).version(2l).origin(REPLICA);
            replicaEngine.index(index);
            assertThat(index.version(), equalTo(2l));
        } catch (VersionConflictEngineException e) {
            // all is well
        }
    }

    @Test public void testVersioningReplicaConflict2() {
        ParsedDocument doc = new ParsedDocument("1", "1", "test", null, doc().add(uidField("1")).build(), Lucene.STANDARD_ANALYZER, B_1, false);
        Engine.Index index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(1l));

        // apply the first index to the replica, should work fine
        index = new Engine.Index(null, newUid("1"), doc).version(1l).origin(REPLICA);
        replicaEngine.index(index);
        assertThat(index.version(), equalTo(1l));

        // index it again
        index = new Engine.Index(null, newUid("1"), doc);
        engine.index(index);
        assertThat(index.version(), equalTo(2l));

        // now delete it
        Engine.Delete delete = new Engine.Delete("test", "1", newUid("1"));
        engine.delete(delete);
        assertThat(delete.version(), equalTo(3l));

        // apply the delete on the replica (skipping the second index)
        delete = new Engine.Delete("test", "1", newUid("1")).version(3l).origin(REPLICA);
        replicaEngine.delete(delete);
        assertThat(delete.version(), equalTo(3l));

        // second time delete with same version should fail
        try {
            delete = new Engine.Delete("test", "1", newUid("1")).version(3l).origin(REPLICA);
            replicaEngine.delete(delete);
            assertThat(delete.version(), equalTo(3l));
        } catch (VersionConflictEngineException e) {
            // all is well
        }

        // now do the second index on the replica, it should fail
        try {
            index = new Engine.Index(null, newUid("1"), doc).version(2l).origin(REPLICA);
            replicaEngine.index(index);
            assertThat(index.version(), equalTo(2l));
        } catch (VersionConflictEngineException e) {
            // all is well
        }
    }

    protected Term newUid(String id) {
        return new Term("_uid", id);
    }
}
