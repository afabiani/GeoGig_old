/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.integration.je;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.di.GeogigModule;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class JEFindCommonAncestorTest extends
        org.locationtech.geogig.test.integration.FindCommonAncestorTest {
    @Override
    protected Context createInjector() {
        return Guice.createInjector(
                Modules.override(new GeogigModule()).with(new JETestStorageModule())).getInstance(
                Context.class);
    }
}
