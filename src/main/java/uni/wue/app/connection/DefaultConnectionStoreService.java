/*
 * Copyright 2015 Lorenz Reinhart
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */
package uni.wue.app.connection;

import com.esotericsoftware.kryo.pool.KryoPool;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.util.KryoNamespace;
import org.onosproject.codec.CodecService;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.DistributedSet;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 06.03.16.
 */
@Component(immediate = true)
@Service
public class DefaultConnectionStoreService implements ConnectionStoreService {

    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);
    private static String APPLICATION_ID = "uni.wue.app";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostConnectionService hostConnectionService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CodecService codecService;

    private DistributedSet<Connection> connections;

    @Activate
    protected void activate(){
        connections = storageService.<Connection>setBuilder()
                .withApplicationId(applicationIdStore.getAppId(APPLICATION_ID))
                .withSerializer(Serializer.using(KryoNamespaces.API.newBuilder()
                        .register(DefaultConnection.class)
                        .register(org.onlab.packet.Ip4Address.class)
                        .register(byte[].class)
                        .register(org.onlab.packet.IpAddress.Version.class)
                        .register(org.onlab.packet.TpPort.class)
                        .register(org.onlab.packet.MacAddress.class)
                        .build()))
                .withName("connections")
                .build();
        connections.clear();

        codecService.registerCodec(Connection.class, new ConnectionCodec());

        log.info("Started ConnectionManager");
    }

    @Deactivate
    protected void deactivate(){

        log.info("Stopped ConnectionManager");
    }

    /**
     * Add a new connection to the service
     *
     * @param connection
     */
    @Override
    public void addConnection(Connection connection) {
        if(!connections.contains(connection))
            connections.add(connection);

        //TODO: test functionality, synchronize with flows in table (timeout)
        hostConnectionService.addConnection(connection);

        log.debug("Added connection {}", connection.toString());
    }

    /**
     * Get the set of connections for source IP and source MAC
     *
     * @param srcIp
     * @param srcMac
     * @return set of connections
     */
    @Override
    public Set<Connection> getConnections(Ip4Address srcIp, MacAddress srcMac) {
        return connections.stream()
                .filter(c -> (c.getSrcIp().equals(srcIp) && c.getSrcMac().equals(srcMac)))
                .sorted(Comparator.comparing(Connection::getDstIp))
                .collect(Collectors.toSet());
    }

    /**
     * Get the set of connections for destination IP and destination traffic protocol port
     *
     * @param dstIp
     * @param dstTpPort
     * @return set of connections
     */
    @Override
    public Set<Connection> getConnections(Ip4Address dstIp, TpPort dstTpPort) {
        return connections.stream()
                .filter(c -> (c.getDstIp().equals(dstIp) && c.getDstTpPort().equals(dstTpPort)))
                .sorted(Comparator.comparing(Connection::getSrcIp))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Connection> getConnections(){
        return connections.stream()
                .sorted(Comparator.comparing(Connection::getSrcIp)
                        .thenComparing(Comparator.comparing(Connection::getDstIp)))
                .collect(Collectors.toSet());
    }
}
