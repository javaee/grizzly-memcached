/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.memcached.MemcachedCache;

import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Bongjae Chang
 */
public class PreferRemoteConfigBarrierListener extends CacheServerListBarrierListener {

    private static final Logger LOGGER = Grizzly.logger(PreferRemoteConfigBarrierListener.class);

    public PreferRemoteConfigBarrierListener(final MemcachedCache cache, final Set<SocketAddress> cacheServerSet) {
        super(cache, cacheServerSet);
    }

    @Override
    public void onInit(final String regionName, final String path, final byte[] remoteBytes) {
        if (remoteBytes == null || remoteBytes.length == 0) {
            throw new IllegalStateException("remote config was not ready. path=" + path + ", cacheName=" + cacheName);
        }
        final String remoteCacheServerList;
        try {
            remoteCacheServerList = new String(remoteBytes, DEFAULT_SERVER_LIST_CHARSET);
        } catch (UnsupportedEncodingException uee) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "failed to encode the cache server list from the remote. path=" + path + ", cacheName=" + cacheName, uee);
            }
            throw new IllegalStateException("remote config was not ready. path=" + path + ", cacheName=" + cacheName);
        }
        final Set<SocketAddress> remoteCacheServers = getAddressesFromStringList(remoteCacheServerList);
        if (remoteCacheServerList.isEmpty()) {
            throw new IllegalStateException("remote config was not ready. path=" + path + ", cacheName=" + cacheName);
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "remote config is ready. remoteCacheServers={0}", remoteCacheServers);
        }
        // initializes local server list with remote config
        localCacheServerSet.clear();
        for (final SocketAddress address : remoteCacheServers) {
            localCacheServerSet.add(address);
            cache.addServer(address);
        }
        super.onInit(regionName, path, remoteBytes);
    }
}
