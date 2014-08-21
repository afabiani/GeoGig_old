/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.performance.je;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.storage.bdbje.JEStorageModule;
import org.locationtech.geogig.test.performance.RevTreeBuilderPerformanceTest;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class JERevTreeBuilderPerformanceTest extends RevTreeBuilderPerformanceTest {
    @Override
    protected Context createInjector() {
        return Guice.createInjector(
                Modules.override(new GeogigModule()).with(new JEStorageModule())).getInstance(
                Context.class);
    }
}
