/* Copyright (c) 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the LGPL 2.1 license, available at the root
 * application directory.
 */
package org.geogit.test.integration.bdbc;

import org.geogit.di.GeogitModule;
import org.geogit.storage.bdbc.BDBStorageModule;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;

public class DBRevTreeBuilderTest extends org.geogit.test.integration.RevTreeBuilderTest {
    @Override
    protected Injector createInjector() {
        return Guice.createInjector(Modules.override(new GeogitModule())
                .with(new BDBStorageModule()));
    }

    @Test
    public void testPutIterate() throws Exception {
        super.testPutIterate();
    }

    @Test
    public void testPutRandomGet() throws Exception {
        super.testPutRandomGet();
    }

    public static void main(String... args) {
        DBRevTreeBuilderTest test = new DBRevTreeBuilderTest();
        try {
            test.setUp();
            test.testPutRandomGet();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }
}
