/* ==================================================================
 * WebsocketLoxoneServiceSettings.java - 2/10/2016 5:44:21 PM
 * 
 * Copyright 2007-2016 SolarNetwork.net Dev Team
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ==================================================================
 */

package net.solarnetwork.node.loxone.impl;

/**
 * Internal API to allow bean managed Configuration Admin settings to be
 * applied.
 * 
 * This is necessary because the bean is wrapped with a transaction proxy, and
 * only interface methods are available afterwards.
 * 
 * @author matt
 * @version 1.1
 */
public interface WebsocketLoxoneServiceSettings {

	void setHost(String host);

	void setUsername(String username);

	void setPassword(String password);

	void setConfigKey(String configKey);

	ControlDatumDataSource getDatumDataSource();

	void setDatumLoggerFrequencySeconds(int datumLoggerFrequencySeconds);

	void disconnect();

}
