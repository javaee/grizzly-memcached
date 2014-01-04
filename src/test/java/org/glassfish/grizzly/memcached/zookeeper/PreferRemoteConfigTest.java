/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.memcached.zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.glassfish.grizzly.memcached.GrizzlyMemcachedCache;
import org.glassfish.grizzly.memcached.GrizzlyMemcachedCacheManager;
import org.glassfish.grizzly.memcached.MemcachedCache;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Bongjae Chang
 */
public class PreferRemoteConfigTest {

    private static final String DEFAULT_ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final String DEFAULT_LOCAL_HOST = "localhost";
    private static final String ROOT = "/zktest";
    private static final String BASE_PATH = "/barrier";
    private static final String DATA_PATH = "/data";
    private static final byte[] NO_DATA = new byte[0];

    private static final String DEFAULT_CHARSET = "UTF-8";

    @Test
    public void testWithoutZooKeeperConfig() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        try {
            final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
            final Set<SocketAddress> serverList = new HashSet<SocketAddress>();
            final SocketAddress local = new InetSocketAddress(DEFAULT_LOCAL_HOST, 11211);
            serverList.add(local);
            builder.servers(serverList);
            builder.preferRemoteConfig(true);
            final MemcachedCache<String, String> userCache = builder.build();
            // if zookeeper config is not set, local config is used
            assertTrue(userCache.isInServerList(local));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testWithoutZooKeeperOrRemoteConfig() {
        final GrizzlyMemcachedCacheManager.Builder managerBuilder = new GrizzlyMemcachedCacheManager.Builder();
        // setup zookeeper server
        final ZooKeeperConfig zkConfig = ZooKeeperConfig.create("cache-manager", DEFAULT_ZOOKEEPER_ADDRESS);
        zkConfig.setRootPath(ROOT);
        zkConfig.setConnectTimeoutInMillis(3000);
        zkConfig.setSessionTimeoutInMillis(30000);
        zkConfig.setCommitDelayTimeInSecs(2);
        managerBuilder.zooKeeperConfig(zkConfig);
        // create a cache manager
        final GrizzlyMemcachedCacheManager manager = managerBuilder.build();
        final ZKClient zkClient = createZKClient();
        try {
            final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
            final Set<SocketAddress> serverList = new HashSet<SocketAddress>();
            final SocketAddress local = new InetSocketAddress(DEFAULT_LOCAL_HOST, 11211);
            serverList.add(local);
            builder.servers(serverList);
            builder.preferRemoteConfig(true);
            final MemcachedCache<String, String> userCache = builder.build();
            if (zkClient == null) {
                // if zookeeper is not booted, local config is used
                assertTrue(userCache.isInServerList(local));
            } else {
                // if zookeeper is booted, assumes that the remote config is not setup
                assertFalse(userCache.isInServerList(local));
            }
        } finally {
            manager.shutdown();
            clearTestRepository(zkClient);
        }
    }

    // zookeeper server should be booted in local
    //@Test
    public void testWithRemoteConfig() {
        // setup remote config
        final ZKClient zkClient = createZKClient();
        Assert.assertNotNull(zkClient);
        createWhenThereIsNoNode(zkClient, ROOT, NO_DATA, CreateMode.PERSISTENT);
        createWhenThereIsNoNode(zkClient, ROOT + BASE_PATH, NO_DATA, CreateMode.PERSISTENT);
        createWhenThereIsNoNode(zkClient, ROOT + BASE_PATH + "/user", NO_DATA, CreateMode.PERSISTENT);
        createWhenThereIsNoNode(zkClient, ROOT + BASE_PATH + "/user" + DATA_PATH, NO_DATA, CreateMode.PERSISTENT);
        final String cacheServerList = DEFAULT_LOCAL_HOST + ":11211";
        byte[] serverListBytes = null;
        try {
            serverListBytes = cacheServerList.getBytes(DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException uee) {
            Assert.fail(uee.getMessage());
        }
        zkClient.setData(ROOT + BASE_PATH + "/user" + DATA_PATH, serverListBytes, 0);

        final GrizzlyMemcachedCacheManager.Builder managerBuilder = new GrizzlyMemcachedCacheManager.Builder();
        // setup zookeeper server
        final ZooKeeperConfig zkConfig = ZooKeeperConfig.create("cache-manager", DEFAULT_ZOOKEEPER_ADDRESS);
        zkConfig.setRootPath(ROOT);
        zkConfig.setConnectTimeoutInMillis(3000);
        zkConfig.setSessionTimeoutInMillis(30000);
        zkConfig.setCommitDelayTimeInSecs(2);
        managerBuilder.zooKeeperConfig(zkConfig);
        // create a cache manager
        final GrizzlyMemcachedCacheManager manager = managerBuilder.build();
        try {
            final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
            final SocketAddress local = new InetSocketAddress(DEFAULT_LOCAL_HOST, 11211);
            builder.preferRemoteConfig(true);
            final MemcachedCache<String, String> userCache = builder.build();
            assertTrue(userCache.isInServerList(local));
        } finally {
            manager.shutdown();
            clearTestRepository(zkClient);
        }
    }

    private static ZKClient createZKClient() {
        final ZKClient.Builder zkBuilder = new ZKClient.Builder("test-zk-client", DEFAULT_ZOOKEEPER_ADDRESS);
        zkBuilder.rootPath(ROOT).connectTimeoutInMillis(3000).sessionTimeoutInMillis(3000).commitDelayTimeInSecs(30);
        final ZKClient zkClient = zkBuilder.build();
        try {
            if (!zkClient.connect()) {
                return null;
            }
        } catch (IOException ie) {
            return null;
        } catch (InterruptedException ignore) {
            return null;
        }
        return zkClient;
    }

    private static boolean createWhenThereIsNoNode(final ZKClient zkClient, final String path, final byte[] data, final CreateMode createMode) {
        if (zkClient == null) {
            return false;
        }
        if (zkClient.exists(path, false) != null) {
            return false;
        }
        zkClient.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
        return true;
    }

    private static void clearTestRepository(final ZKClient zkClient) {
        if (zkClient == null) {
            return;
        }
        zkClient.delete(ROOT + BASE_PATH + "/user/participants", -1);
        zkClient.delete(ROOT + BASE_PATH + "/user/current", -1);
        zkClient.delete(ROOT + BASE_PATH + "/user" + DATA_PATH, -1);
        zkClient.delete(ROOT + BASE_PATH + "/user", -1);
        zkClient.delete(ROOT + BASE_PATH, -1);
        zkClient.delete(ROOT, -1);
        zkClient.shutdown();
    }
}
