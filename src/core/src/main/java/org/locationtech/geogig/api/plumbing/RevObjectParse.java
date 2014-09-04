/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Resolves the reference given by a ref spec to the {@link RevObject} it finally points to,
 * dereferencing symbolic refs as necessary.
 * 
 * @see RevParse
 * @see ResolveObjectType
 */
public class RevObjectParse extends AbstractGeoGigOp<Optional<RevObject>> {

    private ObjectId objectId;

    private String refSpec;

    private Hints hints;

    public RevObjectParse() {
        this.hints = Hints.nil();
    }

    /**
     * @param refSpec the ref spec to resolve
     * @return {@code this}
     */
    public RevObjectParse setRefSpec(final String refSpec) {
        this.objectId = null;
        this.refSpec = refSpec;
        return this;
    }

    /**
     * @param objectId the {@link ObjectId object id} to resolve
     * @return {@code this}
     */
    public RevObjectParse setObjectId(final ObjectId objectId) {
        this.refSpec = null;
        this.objectId = objectId;
        return this;
    }

    public RevObjectParse setHints(Hints hints) {
        Preconditions.checkNotNull(hints);
        this.hints = hints;
        return this;
    }

    /**
     * @return the resolved object id
     * @throws IllegalArgumentException if the provided refspec doesn't resolve to any known object
     * @see RevObject
     */
    @Override
    protected Optional<RevObject> _call() throws IllegalArgumentException {
        return call(RevObject.class);
    }

    /**
     * @param clazz the base type of the parsed objects
     * @return the resolved object id
     * @see RevObject
     */
    public <T extends RevObject> Optional<T> call(Class<T> clazz) {
        final ObjectId resolvedObjectId;
        if (objectId == null) {
            Optional<ObjectId> parsed = command(RevParse.class).setRefSpec(refSpec).call();
            if (parsed.isPresent()) {
                resolvedObjectId = parsed.get();
            } else {
                resolvedObjectId = ObjectId.NULL;
            }
        } else {
            resolvedObjectId = objectId;
        }
        if (resolvedObjectId.isNull()) {
            return Optional.absent();
        }
        ObjectDatabase db = stagingDatabase();
        Hints hints = this.hints;
        RevObject revObject = db.get(resolvedObjectId, clazz, hints);
        Preconditions.checkArgument(clazz.isAssignableFrom(revObject.getClass()),
                "Wrong return class for RevObjectParse operation");

        return Optional.of(clazz.cast(revObject));
    }
}
