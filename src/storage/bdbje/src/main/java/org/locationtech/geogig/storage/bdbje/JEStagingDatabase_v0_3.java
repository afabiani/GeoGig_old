/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.bdbje;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;

public final class JEStagingDatabase_v0_3 extends JEStagingDatabase {
    @Inject
    public JEStagingDatabase_v0_3(final ObjectDatabase repositoryDb,
            final EnvironmentBuilder envBuilder, final Platform platform,
            final ConfigDatabase configDB, final Hints hints) {
        super(repositoryDb, stagingDbSupplier(envBuilder, configDB, hints), platform, configDB,
                "0.3");
    }

    private static Supplier<JEObjectDatabase> stagingDbSupplier(
            final EnvironmentBuilder envBuilder, final ConfigDatabase configDB, final Hints hints) {
        return Suppliers.memoize(new Supplier<JEObjectDatabase>() {
            @Override
            public JEObjectDatabase get() {
                boolean readOnly = hints.getBoolean(Hints.STAGING_READ_ONLY);
                envBuilder.setIsStagingDatabase(true);
                JEObjectDatabase db = new JEObjectDatabase_v0_3(configDB, envBuilder, readOnly,
                        JEStagingDatabase.ENVIRONMENT_NAME);
                return db;
            }
        });
    }
}
