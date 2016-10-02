/* ==================================================================
 * ValueEventDao.java - 19/09/2016 7:26:17 AM
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

package net.solarnetwork.node.loxone.dao;

import java.util.List;
import net.solarnetwork.node.loxone.domain.DatumUUIDEntity;
import net.solarnetwork.node.loxone.domain.DatumUUIDEntityParameters;
import net.solarnetwork.node.loxone.domain.UUIDEntityParametersPair;
import net.solarnetwork.node.loxone.domain.ValueEvent;

/**
 * DAO API for value events.
 * 
 * @author matt
 * @version 1.0
 */
public interface ValueEventDao extends EventEntityDao<ValueEvent> {

	/**
	 * Get a list of persisted entities associated with {@link DatumUUIDEntity}.
	 * 
	 * @return list of all persisted entities that have associated
	 *         {@link DatumUUIDEntity} values, or empty list if none available
	 */
	List<UUIDEntityParametersPair<ValueEvent, DatumUUIDEntityParameters>> findAllForDatumUUIDEntities(
			Long configId);

}
