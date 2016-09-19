/* ==================================================================
 * LoxoneEvents.java - 17/09/2016 3:14:17 PM
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

package net.solarnetwork.node.loxone.protocol.ws;

/**
 * Event constants and utilities.
 * 
 * @author matt
 * @version 1.0
 */
public abstract class LoxoneEvents {

	private LoxoneEvents() {
		// don't construct me
	}

	/**
	 * Event broadcast with the last modification date of the Loxone structure
	 * file. The {@link #EVENT_PROPERTY_DATE} property will be available.
	 */
	public static final String STRUCTURE_FILE_MODIFICATION_DATE_EVENT = "net/solarnetwork/node/loxone/CONFIG_MOD_DATE";

	/**
	 * Event broadcast with the last modification date of the Loxone structure
	 * file after it has been successfully saved. The
	 * {@link #EVENT_PROPERTY_DATE} property will be available.
	 */
	public static final String STRUCTURE_FILE_SAVED_EVENT = "net/solarnetwork/node/loxone/CONFIG_SAVE_DATE";

	/**
	 * The Event property used to convey a date, expressed as a {@code Long}
	 * value of milliseconds since the epoch.
	 */
	public static final String EVENT_PROPERTY_DATE = "date";

}
