/* Copyright (c) 2013 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.locationtech.geogig.test.integration.postgresql;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.storage.postgresql.PGStorageModule;
import org.locationtech.geogig.test.integration.DiffOpTest;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class PGDiffOpTest extends DiffOpTest {

    @Override
    protected Context createInjector() {
        return Guice.createInjector(
                Modules.override(new GeogigModule()).with(new PGStorageModule())).getInstance(
                Context.class);
    }
}
