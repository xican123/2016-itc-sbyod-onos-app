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
 */

package org.sardineproject.sbyod.dns;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.IPv4;
import org.onlab.packet.TpPort;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.Host;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.provider.ProviderId;
import org.sardineproject.sbyod.ByodConfig;
import org.sardineproject.sbyod.PortalManager;
import org.sardineproject.sbyod.PortalService;
import org.sardineproject.sbyod.connection.Connection;
import org.sardineproject.sbyod.connection.ConnectionStore;
import org.sardineproject.sbyod.connection.DefaultConnection;
import org.sardineproject.sbyod.service.DefaultService;
import org.sardineproject.sbyod.service.Service;
import org.sardineproject.sbyod.service.ServiceStore;
import org.slf4j.Logger;

import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 08.06.16.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class DefaultDnsService implements DnsService {

    private static final String APPLICATION_ID = PortalService.APP_ID;
    private static final Logger log = getLogger(PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceStore serviceStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConnectionStore connectionStore;

    // set of dns connections for every host
    private Service dnsServiceTcp;
    private Service dnsServiceUdp;

    // TODO: add listener for new host and add dns connection

    public void activateDns(){

        // get the ip address of the default gateway
        ByodConfig cfg = cfgService.getConfig(applicationIdStore.getAppId(APPLICATION_ID), ByodConfig.class);
        // get the host with ip of the default gateway
        Set<Host> routers = hostService.getHostsByIp(cfg.defaultGateway());

        // if exactly one host was found
        if(routers.size() == 1){
            // get the only one router host
            Host router = routers.iterator().next();
            log.info("DefaultDnsService: Found router with id={}", router.id());

            // dns running on both tcp and udp protocol
            dnsServiceTcp = new DefaultService(router, TpPort.tpPort(53), "DnsServiceTcp", ProviderId.NONE);
            dnsServiceTcp.setProtocol(IPv4.PROTOCOL_TCP);
            serviceStore.addService(dnsServiceTcp);

            dnsServiceUdp = new DefaultService(router, TpPort.tpPort(53), "DnsServiceUdp", ProviderId.NONE);
            dnsServiceUdp.setProtocol(IPv4.PROTOCOL_UDP);
            serviceStore.addService(dnsServiceUdp);

            log.info("DefaultDnsService: Added tcp and upd service for dns");

            // connect all valid hosts to the dns service
            for(Host host : hostService.getHosts()){
                // do not install the service for the router itself
                if(!host.equals(router)){
                    // install the connection for both services
                    connectionStore.addConnection(new DefaultConnection(host, dnsServiceTcp));
                    connectionStore.addConnection(new DefaultConnection(host, dnsServiceUdp));

                    log.info("DefaultDnsService: Added dns connection for host={}", host.id());
                }
            }
        } else if(routers.isEmpty()){
            log.warn("DefaultDnsService: No host found with IP={} to use as DNS service", cfg.defaultGateway());
        } else{
            log.warn("DefaultDnsService: More than one host found with IP={} to use as DNS service", cfg.defaultGateway());
        }

    }

    public void deactivateDns(){
        removeConnection(dnsServiceTcp);
        removeConnection(dnsServiceUdp);
        dnsServiceTcp = null;
        dnsServiceUdp = null;
        log.info("DefaultDnsService: Removed dns connections.");
    }

    private void removeConnection(Service service){
        // get all connections to the dns router
        Set<Connection> connections = connectionStore.getConnections(service);
        // remove all connections of the dns router
        connections.forEach(c -> connectionStore.removeConnection(c));
        // remove the dns service from the service store
        serviceStore.removeService(service);
    }

    private class DnsHostListener implements HostListener{

        /**
         * Reacts to the specified event.
         *
         * @param event event to be processed
         */
        @Override
        public void event(HostEvent event) {

        }
    }
}
