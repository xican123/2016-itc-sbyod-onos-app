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
package org.sardineproject.sbyod.consul;

import com.ecwid.consul.transport.TransportException;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.IpAddress;
import org.onlab.packet.TpPort;
import org.onosproject.net.ElementId;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.net.provider.ProviderId;
import org.sardineproject.sbyod.PortalManager;
import org.sardineproject.sbyod.connection.DefaultConnection;
import org.sardineproject.sbyod.service.DefaultService;
import org.sardineproject.sbyod.service.ServiceId;
import org.slf4j.Logger;
import org.sardineproject.sbyod.connection.Connection;
import org.sardineproject.sbyod.connection.ConnectionStore;
import org.sardineproject.sbyod.service.Service;
import org.sardineproject.sbyod.service.ServiceStore;

import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 19.04.16.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class ConsulServiceApi implements ConsulService {

    private static final long WAIT_TIME = 50; // seconds - 5*60 is default consul wait time (max wait time = 60*10 s)
    // consul api throws read time out at 60s in com.ecwid.consul.transport.AbstractHttpTransport class

    private static final Logger log = getLogger(PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceStore serviceStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConnectionStore connectionStore;


    protected ConsulClient consulClient;
    private IpAddress consulIp;
    private TpPort consulTpPort;

    // thread checking the consul services every WAIT_TIME seconds
    protected Thread checkServices;


    @Activate
    protected void activate() {
        checkServices = new CheckConsulCatalogServiceUpdates();
        checkServices.setDaemon(true);
    }

    @Deactivate
    protected void deactivate() {
        try {
            if(checkServices.isAlive()) {
                checkServices.interrupt();
            }
        } catch(Exception e){
            log.warn(e.toString());
        }
    }


    /**
     * Connect to a running consul agent.
     *
     * @param ipAddress Ip address to connect to
     * @param tpPort    transport protocol port
     */
    @Override
    public boolean connectConsul(IpAddress ipAddress, TpPort tpPort) {

        if(checkConnection(ipAddress, tpPort)) {
            // remove old services
            disconnectConsul();

            consulClient = new ConsulClient(ipAddress.toString(), tpPort.toInt());
            consulIp = ipAddress;
            consulTpPort = tpPort;

            // add all services from consul to the service store
            Set<Service> consulServices = getServices();
            consulServices.forEach(s -> serviceStore.addService(s));

            // start the thread checking for consul service updates
            checkServices.start();

            return true;
        } else {
            return false;
        }

    }

    /**
     * Connect to a running consul agent on TpPort 8500.
     *
     * @param ipAddress Ip address to connect to
     */
    @Override
    public boolean connectConsul(IpAddress ipAddress) {
        return connectConsul(ipAddress, TpPort.tpPort(8500));
    }

    /**
     * Disconnect the running consul agent
     */
    @Override
    public void disconnectConsul() {

        // interrupt the service update thread if it is running
        if(checkServices.isAlive()) {
            checkServices.interrupt();
        }

        // get all services registered by consul from the service store
        Set<Service> storeServices = getConsulServicesFromStore();
        // remove consul services in the service store
        storeServices.forEach(s -> serviceStore.removeService(s));

        consulClient = null;
        consulIp = null;
        consulTpPort = null;

        // restore service update thread
        checkServices = new CheckConsulCatalogServiceUpdates();
        checkServices.setDaemon(true);

    }

    /**
     * Get the ip address the consul client is running on
     *
     * @return Ip address
     */
    @Override
    public IpAddress getConsulIp() {
        if(consulClient != null){
            return consulIp;
        }
        return null;
    }

    /**
     * Get the transport protocol port the consul client is running on
     *
     * @return transport protocol port
     */
    @Override
    public TpPort getConsulTpPort() {
        if(consulClient != null){
            return consulTpPort;
        }
        return null;
    }

    /**
     * Checks the connection to the consul client
     *
     * @param ipAddress IP address of the consul client
     * @param tpPort Transport protocol port of the consul client
     * @return true if connection is available, false otherwise
     */
    private boolean checkConnection(IpAddress ipAddress, TpPort tpPort){
        try{
            // creating consul client to test connection for
            ConsulClient testConsulClient = new ConsulClient(ipAddress.toString(), tpPort.toInt());
            // throws transport exception if no connection to client is available
            testConsulClient.getCatalogServices(new QueryParams(""));
        } catch (TransportException transportException){
            log.warn("ConsulServiceApi: No connection to consul client possible!");
            return false;
        } catch (Exception e){
            log.warn("ConsulServiceApi: Exception while connecting to Consul. Exception: {}", e);
            return false;
        }
        return true;
    }


    /**
     * Get all services marked as Consul discovery from the service store
     * @return Set of services
     */
    private Set<Service> getConsulServicesFromStore(){
        return serviceStore.getServices().stream()
                .filter(s -> s.getServiceDiscovery().equals(Service.Discovery.CONSUL))
                .collect(Collectors.toSet());
    }

    /**
     * Get all services from consul, that are visible in onos cluster
     * @return Set of services
     */
    private Set<Service> getServices(){

        Set<Service> consulServices = new HashSet<>();

        QueryParams queryParams = new QueryParams("");

        if(consulClient != null) {
            // get the registered service names
            Map<String, List<String>> mapOfServices = consulClient.getCatalogServices(queryParams).getValue();

            // show the services in log
            mapOfServices.forEach((s, t) -> log.debug("ConsulServiceApi: Found consul service [" + s + " : " + t + "]."));

            // list of services registered in consul
            List<CatalogService> serviceDescription = new LinkedList<>();
            // get the information of the services stored in consul
            for(Map.Entry<String, List<String>> entry : mapOfServices.entrySet()){
                // the name to search for
                String serviceName = entry.getKey();
                try {
                    // encode name to url - replacing spaces with '%20' for example
                    URL url = new URI("http", "example.com", "/" + serviceName + "/", "").toURL();
                    // get only the path part of the url
                    String serviceNameEncoded = url.getPath();
                    // remove the backspaces
                    serviceNameEncoded = serviceNameEncoded.substring(1, serviceNameEncoded.length()-1);

                    // query consul for the service description
                    serviceDescription.addAll(consulClient.getCatalogService(serviceNameEncoded, queryParams).getValue());
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }

            // add the catalog services to the collection
            for (CatalogService catalogService : serviceDescription) {

                // if the host running the service is known to ONOS,
                // it is added to the consulServices collection
                addServiceToCollection(catalogService, consulServices);

            }
        }

        return consulServices;
    }

    private void addServiceToCollection(CatalogService catalogService, Collection consulServices){

        // get all hosts with corresponding ip address
        Set<Host> hosts;
        if (catalogService.getServiceAddress().isEmpty()) {
            // default ip address is the consul ip address
            hosts = hostService.getHostsByIp(IpAddress.valueOf(catalogService.getAddress()));
        } else {
            try {
                // try to find the host in the onos cluster
                hosts = hostService.getHostsByIp(IpAddress.valueOf(catalogService.getServiceAddress()));
            } catch (IllegalArgumentException e) {
                log.warn("ConsulServiceApi: Could not find host with address = {}, Error: {}",
                        catalogService.getServiceAddress(), e);
                hosts = Sets.newHashSet();
            }
        }

        if (hosts.size() == 1) {
            Host host = hosts.iterator().next();
            log.debug("ConsulServiceApi: Consul service {} running on {} is in ONOS cluster.",
                    catalogService.getServiceName(), host.ipAddresses());

            // create a new byod service corresponding to the CatalogService
            DefaultService.Builder service = DefaultService.builder()
                    .withHost(host)
                    .withPort(TpPort.tpPort(catalogService.getServicePort()))
                    .withName(catalogService.getServiceName())
                    .withElementId(ServiceId.serviceId(URI.create(catalogService.getServiceId())))
                    .withDiscovery(Service.Discovery.CONSUL);

            // add an icon to the service, defined as the first tag in description
            if(!catalogService.getServiceTags().isEmpty()) {
                service.withIcon(catalogService.getServiceTags().iterator().next());
            }

            consulServices.add(service.build());
        } else if (hosts.isEmpty()) {
            log.debug("ConsulServiceApi: No host found with ip address = {}", catalogService.getAddress());
        } else {
            log.debug("ConsulServiceApi: More than one host found with ip address = {}", catalogService.getAddress());
        }

    }

    private class CheckConsulCatalogServiceUpdates extends Thread{

        /**
         * When an object implementing interface <code>Runnable</code> is used
         * to create a thread, starting the thread causes the object's
         * <code>run</code> method to be called in that separately executing
         * thread.
         * <p>
         * The general contract of the method <code>run</code> is that it may
         * take any action whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {

            while(consulClient != null && !isInterrupted()) {
                try {
                    // get the consul index to wait for
                    QueryParams queryParams = new QueryParams("");
                    Response<Map<String, List<String>>> services = consulClient.getCatalogServices(queryParams);

                    // TODO: check what happens if connection to consul is lost while doing blocking query
                    // start blocking query for index
                    queryParams = new QueryParams(WAIT_TIME, services.getConsulIndex());
                    if (!isInterrupted())
                        services = consulClient.getCatalogServices(queryParams);

                    if (!isInterrupted()) {
                        log.info("ConsulServiceApi: Updating consul services - {}", services.toString());
                        updateConsulServices();
                    }
                } catch(TransportException te){
                    log.warn("ConsulServiceApi: Transport exception - check if the consul service is running " +
                            "and restart the consul Sardine-BYOD extension.");
                    disconnectConsul();
                }
            }
        }
    }

    private void updateConsulServices(){

        // get the services registered in consul
        Set<Service> consulServices = getServices();
        // get the consul services from the byod service store
        Set<Service> storeServices = getConsulServicesFromStore();

        for(Service oldService : storeServices){
            // services in consul with same id as old service
            Set<Service> equalConsulServices = consulServices.stream()
                    .filter(cs -> cs.id().equals(oldService.id()))
                    .collect(Collectors.toSet());

            if(equalConsulServices.isEmpty()){
                // service has been deleted

                log.debug("ConsulServiceApi: Service with ServiceId = {} has been deleted.", oldService.id());
                serviceStore.removeService(oldService);

            } else if(equalConsulServices.size() == 1){
                // service is active, but could be changed

                Service newService = equalConsulServices.iterator().next();
                // check for updates
                if(oldService.equals(newService)){
                    // nothing changed, no update needed
                } else{
                    // service updated, update all connections
                    Set<Connection> connections = connectionStore.getConnections(oldService);
                    // get the connected hosts of the service
                    Set<Host> hosts = connections.stream()
                            .map(Connection::getUser)
                            .collect(Collectors.toSet());

                    // remove old service from store, connections are removed automatically
                    serviceStore.removeService(oldService);
                    // add new service to store
                    serviceStore.addService(newService);

                    // add connection for each host
                    for(Host host : hosts) {
                        connectionStore.addConnection(new DefaultConnection(host, newService));
                    }

                    log.info("ConsulServiceApi: Updated old service = {} to new service = {} and connected hosts = {}.",
                            oldService, newService, hosts);
                }

            } else{
                // more than one service with same ServiceId
                log.warn("ConsulServiceApi: More than one service with ServiceId = {}. Removing service from store.", oldService.id());
                serviceStore.removeService(oldService);
            }

            // remove the processed services
            consulServices.removeAll(equalConsulServices);
        }

        // add all remaining services as new service to store
        consulServices.forEach(cs -> serviceStore.addService(cs));
        if(!consulServices.isEmpty())
            log.info("ConsulServiceApi: Added new services = {}", consulServices);

    }

}
