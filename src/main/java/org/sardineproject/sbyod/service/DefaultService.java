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
package org.sardineproject.sbyod.service;

import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.TpPort;
import org.onosproject.net.AbstractElement;
import org.onosproject.net.ElementId;
import org.onosproject.net.provider.ProviderId;
import org.sardineproject.sbyod.portal.PortalManager;
import org.slf4j.Logger;

import java.net.URI;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static org.slf4j.LoggerFactory.getLogger;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 10.03.16.
 */
public class DefaultService extends AbstractElement implements Service {

    private static final Logger log = getLogger(PortalManager.class);

    private final Set<Ip4Address> ip4AddressSet;
    private final TpPort tpPort;
    private final String name;
    private final Discovery discovery;
    private String icon;
    // fields describing which ip protocol the service has
    // every service is by default tcp
    private byte protocol;


    // for serialization
    private DefaultService(){
        this.tpPort = null;
        this.name = null;
        this.ip4AddressSet = null;
        this.discovery = null;
    }

    private DefaultService(Builder builder){
        super(builder.providerId, builder.elementId);
        this.ip4AddressSet = builder.ip4AddressSet;
        this.tpPort = builder.tpPort;
        this.name = builder.name;
        this.discovery = builder.discovery;
        this.icon = builder.icon;
        this.protocol = builder.protocol;
    }

    /**
     * Get the transport protocol port of the service
     *
     * @return TpPort
     */
    @Override
    public TpPort tpPort() {
        return tpPort;
    }

    /**
     * Get the discovery tag
     *
     * @return Discovery
     */
    @Override
    public Discovery serviceDiscovery() {
        return discovery;
    }

    /**
     * Returning the icon to display in Portal
     *
     * @return icon string
     */
    @Override
    public String icon() {
        return icon;
    }

    /**
     * Return the transport protocol of the service
     *
     * @return transport protocol
     */
    @Override
    public byte protocol() {
        return protocol;
    }

    /**
     * Get the ip address of the service
     *
     * @return a Ip4Address
     */
    @Override
    public Set<Ip4Address> ipAddressSet() {
        return ip4AddressSet;
    }

    /**
     * Get the name of the service
     *
     * @return name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the network service identifier.
     *
     * @return service identifier
     */
    @Override
    public ServiceId id() {
        return (ServiceId) this.id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultService that = (DefaultService) o;

        if (protocol != that.protocol) return false;
        if (!ip4AddressSet.equals(that.ip4AddressSet)) return false;
        if (tpPort != null ? !tpPort.equals(that.tpPort) : that.tpPort != null) return false;
        if (!name.equals(that.name)) return false;
        if (discovery != that.discovery) return false;
        return !(icon != null ? !icon.equals(that.icon) : that.icon != null);

    }

    @Override
    public int hashCode() {
        int result = ip4AddressSet.hashCode();
        result = 31 * result + (tpPort != null ? tpPort.hashCode() : 0);
        result = 31 * result + name.hashCode();
        result = 31 * result + (discovery != null ? discovery.hashCode() : 0);
        result = 31 * result + (icon != null ? icon.hashCode() : 0);
        result = 31 * result + (int) protocol;
        return result;
    }

    @Override
    public String toString() {
        return "DefaultService{" +
                "ip4AddressSet=" + ip4AddressSet +
                ", tpPort=" + tpPort +
                ", name='" + name + '\'' +
                ", discovery=" + discovery +
                ", icon='" + icon + '\'' +
                ", protocol=" + protocol +
                '}';
    }

    /**
     * Returns a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a copy of this service
     *
     * @return a serviceBuilder
     */
    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder implements Service.Builder {

        private Set<Ip4Address> ip4AddressSet = null;
        private TpPort tpPort;
        private String name;
        private Discovery discovery = Discovery.NONE;
        private String icon = "list";
        private byte protocol = IPv4.PROTOCOL_TCP;
        private ProviderId providerId = ProviderId.NONE;
        private ElementId elementId;

        // creates an empty builder
        private Builder(){}

        // creates a builder set to create a copy of the specified service.
        private Builder(Service service){
            this.ip4AddressSet = service.ipAddressSet();
            this.tpPort = service.tpPort();
            this.name = service.name();
            this.discovery = service.serviceDiscovery();
            this.icon = service.icon();
            this.protocol = service.protocol();
            this.providerId = service.providerId();
            this.elementId = service.id();
        }

        public Builder withIp(Set<Ip4Address> ip4AddressSet){
            this.ip4AddressSet = ip4AddressSet;
            return this;
        }

        public Builder withName(String name){
            this.name = name;
            return this;
        }

        public Builder withPort(TpPort tpPort){
            this.tpPort = tpPort;
            return this;
        }

        public Builder withDiscovery(Discovery discovery){
            this.discovery = discovery;
            return this;
        }

        public Builder withIcon(String icon){
            this.icon = icon;
            return this;
        }

        public Builder withProtocol(byte protocol){
            this.protocol = protocol;
            return this;
        }

        public Builder withProviderId(ProviderId providerId){
            this.providerId = providerId;
            return this;
        }

        public Builder withElementId(ElementId elementId){
            this.elementId = elementId;
            return this;
        }

        public Service build(){
            checkNotNull(ip4AddressSet, "Must have an IP address set");
            checkArgument(!ip4AddressSet.isEmpty(), "Must have at least one IP address!");
            checkNotNull(name, "Must have a name");

            if(tpPort == null){
                log.warn("DefaultService: No transport protocol port defined for service {}.", name);
            }

            if(elementId == null){
                elementId = ServiceId.serviceId(URI.create(name + System.currentTimeMillis()));
            }

            return new DefaultService(this);
        }

    }

}
