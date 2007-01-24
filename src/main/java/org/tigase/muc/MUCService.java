/*
 * tigase-muc Copyright (C) 2007 by Bartosz M. Ma³kowski
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * $Id$
 */
package org.tigase.muc;

import java.util.Map;

import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManagerConfig;
import tigase.util.DNSResolver;

/**
 * Implements MUC service for tigase server.
 * <p>
 * Created: 2007-01-24 13:02:15
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class MUCService extends AbstractMessageReceiver {

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getDefaults(Map<String, Object> params) {
        Map<String, Object> props = super.getDefaults(params);
        String[] hostnamesPropVal = null;
        if (params.get("--virt-hosts") != null) {
            hostnamesPropVal = ((String) params.get("--virt-hosts")).split(",");
        } else {
            hostnamesPropVal = DNSResolver.getDefHostNames();
        }
        for (int i = 0; i < hostnamesPropVal.length; i++) {
            hostnamesPropVal[i] = "muc." + hostnamesPropVal[i];
        }
        props.put(SessionManagerConfig.HOSTNAMES_PROP_KEY, hostnamesPropVal);
        return props;
    }

    /** {@inheritDoc} */
    @Override
    public void processPacket(Packet packet) {
        System.out.println(" obedra³em: " + packet);
    }

    /** {@inheritDoc} */
    @Override
    public void setProperties(Map<String, Object> props) {
        super.setProperties(props);
        String[] hostnames = (String[]) props.get(SessionManagerConfig.HOSTNAMES_PROP_KEY);
        clearRoutings();
        for (String host : hostnames) {
            addRouting(host);
        } // end of for ()
    }
}
