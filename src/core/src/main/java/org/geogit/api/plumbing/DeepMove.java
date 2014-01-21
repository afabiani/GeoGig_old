/* Copyright (c) 2013 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */

package org.geogit.api.plumbing;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.geogit.api.AbstractGeoGitOp;
import org.geogit.api.Bucket;
import org.geogit.api.Node;
import org.geogit.api.NodeRef;
import org.geogit.api.ObjectId;
import org.geogit.api.RevObject;
import org.geogit.api.RevObject.TYPE;
import org.geogit.api.RevTree;
import org.geogit.api.plumbing.LsTreeOp.Strategy;
import org.geogit.repository.StagingArea;
import org.geogit.storage.BulkOpListener;
import org.geogit.storage.BulkOpListener.CountingListener;
import org.geogit.storage.ObjectDatabase;
import org.geogit.storage.StagingDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

/**
 * Moves the {@link #setObjectRef(Supplier) specified object} from the {@link StagingArea index
 * database} to the permanent {@link ObjectDatabase object database}, including any child reference,
 * or from the repository database to the index database if {@link #setToIndex} is set to
 * {@code true}.
 */
public class DeepMove extends AbstractGeoGitOp<ObjectId> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepMove.class);

    private boolean toIndex;

    private ObjectDatabase odb;

    private StagingDatabase index;

    private Supplier<Node> objectRef;

    private Supplier<ObjectId> objectId;

    private Supplier<Iterator<Node>> nodesToMove;

    /**
     * Constructs a new instance of the {@code DeepMove} operation with the specified parameters.
     * 
     * @param odb the repository object database
     * @param index the staging database
     */
    @Inject
    public DeepMove(ObjectDatabase odb, StagingDatabase index) {
        this.odb = odb;
        this.index = index;
    }

    /**
     * @param toIndex if {@code true} moves the object from the repository's object database to the
     *        index database instead
     * @return {@code this}
     */
    public DeepMove setToIndex(boolean toIndex) {
        this.toIndex = toIndex;
        return this;
    }

    /**
     * @param id the id of the object to move, mutually exclusive with
     *        {@link #setObjectRef(Supplier)}
     * @return
     */
    public DeepMove setObject(Supplier<ObjectId> id) {
        this.objectId = id;
        this.objectRef = null;
        this.nodesToMove = null;
        return this;
    }

    /**
     * @param objectRef the object to move from the origin database to the destination one, mutually
     *        exclusive with {@link #setObject(Supplier)}
     * @return {@code this}
     */
    public DeepMove setObjectRef(Supplier<Node> objectRef) {
        this.objectRef = objectRef;
        this.objectId = null;
        this.nodesToMove = null;
        return this;
    }

    public DeepMove setObjects(Supplier<Iterator<Node>> nodesToMove) {
        this.nodesToMove = nodesToMove;
        this.objectId = null;
        this.objectRef = null;
        return this;
    }

    /**
     * Executes a deep move using the supplied {@link Node}.
     * 
     * @return the {@link ObjectId} of the moved object, or {@code null} if {@link #setObjects} was
     *         used and hence no such information it available
     */
    @Override
    public ObjectId call() {
        ObjectDatabase from = toIndex ? odb : index;
        ObjectDatabase to = toIndex ? index : odb;

        Set<ObjectId> metadataIds = new HashSet<ObjectId>();

        final ObjectId ret;
        if (objectRef != null) {
            Node ref = objectRef.get();
            ret = ref.getObjectId();
            deepMove(ref, from, to, metadataIds);
        } else if (objectId != null) {
            ObjectId id = objectId.get();
            moveObject(id, from, to);
            ret = id;
        } else if (nodesToMove != null) {
            moveObjects(from, to, nodesToMove, metadataIds);
            ret = null;
        } else {
            throw new IllegalStateException("No object supplied to be moved");
        }

        for (ObjectId metadataId : metadataIds) {
            moveObject(metadataId, from, to);
        }

        return ret;
    }

    private static class DeleteTask implements Runnable {
        private Collection<ObjectId> ids;

        private ObjectDatabase db;

        public DeleteTask(Collection<ObjectId> ids, ObjectDatabase db) {
            this.ids = ids;
            this.db = db;
        }

        @Override
        public void run() {
            Stopwatch s = new Stopwatch().start();
            db.deleteAll(ids.iterator());
            LOGGER.debug("Removed {} objects in {}", ids.size(), s.stop());
            ids.clear();
            ids = null;
        }
    }

    private static class DeletingListener extends BulkOpListener {

        private final int limit = 1000 * 100;

        private SortedSet<ObjectId> removeIds = Sets.newTreeSet();

        private ExecutorService deletingService;

        private ObjectDatabase db;

        public DeletingListener(ExecutorService deletingService, ObjectDatabase db) {
            this.deletingService = deletingService;
            this.db = db;
        }

        @Override
        public synchronized void inserted(ObjectId object, @Nullable Integer storageSizeBytes) {
            removeIds.add(object);
            if (removeIds.size() == limit) {
                deleteInserted();
            }
        }

        public void deleteInserted() {
            if (!removeIds.isEmpty()) {
                DeleteTask deleteTask = new DeleteTask(removeIds, db);
                deletingService.execute(deleteTask);
                removeIds = Sets.newTreeSet();
            }
        }
    }

    private long moveObjects(final ObjectDatabase from, final ObjectDatabase to,
            final Supplier<Iterator<Node>> nodesToMove, final Set<ObjectId> metadataIds) {

        Iterable<ObjectId> ids = new Iterable<ObjectId>() {

            final Function<Node, ObjectId> asId = new Function<Node, ObjectId>() {
                @Override
                public ObjectId apply(Node input) {
                    Optional<ObjectId> metadataId = input.getMetadataId();
                    if (metadataId.isPresent()) {
                        metadataIds.add(input.getMetadataId().get());
                    }
                    ObjectId id = input.getObjectId();
                    return id;
                }
            };

            @Override
            public Iterator<ObjectId> iterator() {
                Iterator<Node> iterator = nodesToMove.get();
                Iterator<ObjectId> ids = Iterators.transform(iterator, asId);

                return ids;
            }
        };

        return moveObjects(ids, from, to);
    }

    private long moveObjects(Iterable<ObjectId> ids, final ObjectDatabase from,
            final ObjectDatabase to) {

        // store objects into the target db and remove them from the origin db in one shot
        Iterator<RevObject> objects;
        if (from instanceof StagingDatabase) {
            objects = ((StagingDatabase) from).getAllPresentStagingOnly(ids,
                    BulkOpListener.NOOP_LISTENER);
        } else {
            objects = from.getAllPresent(ids);
        }

        return moveObjects(objects, from, to);
    }

    private long moveObjects(Iterator<? extends RevObject> objects, final ObjectDatabase from,
            final ObjectDatabase to) {

        final ExecutorService deletingService = Executors.newSingleThreadExecutor();
        CountingListener countingListener = BulkOpListener.newCountingListener();
        try {
            final DeletingListener deletingListener = new DeletingListener(deletingService, from);

            to.putAll(objects, BulkOpListener.composite(deletingListener, countingListener));
            // in case there are some deletes pending cause the iterator finished and the listener
            // didn't fill its buffer
            deletingListener.deleteInserted();

        } finally {
            deletingService.shutdown();
            while (!deletingService.isTerminated()) {
                try {
                    deletingService.awaitTermination(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // ok, still awaiting for delete tasks to finish
                }
            }
        }
        return countingListener.inserted();
    }

    /**
     * Transfers the object referenced by {@code objectRef} from the given object database to the
     * given objectInserter as well as any child object if {@code objectRef} references a tree.
     */
    private void deepMove(final Node objectRef, final ObjectDatabase from, final ObjectDatabase to,
            Set<ObjectId> metadataIds) {

        if (objectRef.getMetadataId().isPresent()) {
            metadataIds.add(objectRef.getMetadataId().get());
        }

        final ObjectId objectId = objectRef.getObjectId();
        if (TYPE.TREE.equals(objectRef.getType())) {
            moveTree(objectId, from, to, metadataIds);
        } else {
            moveObject(objectId, from, to);
        }
    }

    private void moveTree(final ObjectId treeId, final ObjectDatabase from,
            final ObjectDatabase to, final Set<ObjectId> metadataIds) {

        Supplier<Iterator<NodeRef>> refs = command(LsTreeOp.class).setReference(treeId.toString())
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES);

        final Supplier<Iterator<Node>> nodes = Suppliers.compose(
                new Function<Iterator<NodeRef>, Iterator<Node>>() {

                    @Override
                    public Iterator<Node> apply(Iterator<NodeRef> input) {
                        return Iterators.transform(input, new Function<NodeRef, Node>() {
                            @Override
                            public Node apply(NodeRef input) {
                                return input.getNode();
                            }
                        });
                    }
                }, refs);

        // move all features, recursively as given by the LsTreeOp strategy
        Stopwatch sw = new Stopwatch().start();
        long movedCount = moveObjects(from, to, nodes, metadataIds);
        LOGGER.debug("{} features moved in {}", movedCount, sw.stop());

        // iterator that traverses the tree,all its subtrees, an bucket trees
        Iterator<RevTree> allTrees = new AllTrees(treeId, from);

        sw.reset().start();
        movedCount = moveObjects(allTrees, from, to);
        LOGGER.debug("{} trees and buckets moved in {}", movedCount, sw.stop());
    }

    private static class AllTrees extends AbstractIterator<RevTree> {

        private RevTree tree;

        private ObjectDatabase from;

        private Iterator<Node> trees;

        private Iterator<Bucket> buckets;

        private Iterator<RevTree> bucketTrees;

        public AllTrees(ObjectId id, ObjectDatabase from) {
            this.from = from;
            this.tree = from.getTree(id);
            this.trees = Iterators.emptyIterator();
            if (tree.trees().isPresent()) {
                trees = tree.trees().get().iterator();
            }
            buckets = Iterators.emptyIterator();
            if (tree.buckets().isPresent()) {
                buckets = tree.buckets().get().values().iterator();
            }
            bucketTrees = Iterators.emptyIterator();
        }

        @Override
        protected RevTree computeNext() {
            if (tree != null) {
                RevTree ret = tree;
                tree = null;
                return ret;
            }
            if (trees.hasNext()) {
                ObjectId objectId = trees.next().getObjectId();
                return from.getTree(objectId);
            }
            if (bucketTrees.hasNext()) {
                return bucketTrees.next();
            }
            if (buckets.hasNext()) {
                bucketTrees = new AllTrees(buckets.next().id(), from);
                return computeNext();
            }
            return endOfData();
        }

    }

    private void moveObject(RevObject object, ObjectDatabase from, ObjectDatabase to) {
        to.put(object);
        from.delete(object.getId());
    }

    private void moveObject(final ObjectId objectId, final ObjectDatabase from,
            final ObjectDatabase to) {

        RevObject object = from.get(objectId);

        if (object instanceof RevTree) {
            Set<ObjectId> metadataIds = new HashSet<ObjectId>();
            moveTree(object.getId(), from, to, metadataIds);
            for (ObjectId metadataId : metadataIds) {
                moveObject(metadataId, from, to);
            }
        } else {
            moveObject(object, from, to);
        }
    }

    public DeepMove setFrom(ObjectDatabase odb) {
        this.odb = odb;
        return this;
    }

}
