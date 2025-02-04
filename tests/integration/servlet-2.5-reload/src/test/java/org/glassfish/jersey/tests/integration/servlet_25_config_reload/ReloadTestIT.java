/*
 * Copyright (c) 2012, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.tests.integration.servlet_25_config_reload;

import javax.ws.rs.core.Response;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.external.ExternalTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Jakub Podlesak
 */
public class ReloadTestIT extends JerseyTest {

    @Override
    protected ResourceConfig configure() {
        return new ResourceConfig(HelloWorldResource.class);
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new ExternalTestContainerFactory();
    }

    @Test
    @Ignore //TODO - fix after 2.36
    public void testReload() throws Exception {
        Response response = target().path("helloworld").request().get();
        assertEquals(200, response.getStatus());
        assertEquals("Hello World! " + this.getClass().getPackage().getName(), response.readEntity(String.class));

        response = target().path("another").request().get();
        assertEquals(404, response.getStatus());

        response = target().path("reload").request().get();
        assertEquals(200, response.getStatus());

        response = target().path("another").request().get();
        assertEquals(200, response.getStatus());
    }
}
