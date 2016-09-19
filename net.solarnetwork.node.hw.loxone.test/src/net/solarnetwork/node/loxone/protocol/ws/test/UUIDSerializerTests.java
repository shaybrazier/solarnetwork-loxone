/* ==================================================================
 * UUIDSerializerTests.java - 19/09/2016 4:43:39 PM
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

package net.solarnetwork.node.loxone.protocol.ws.test;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.solarnetwork.node.loxone.protocol.ws.UUIDSerializer;
import net.solarnetwork.util.ObjectMapperFactoryBean;

/**
 * Unit tests for the {@link UUIDSerializer}.
 * 
 * @author matt
 * @version 1.0
 */
public class UUIDSerializerTests {

	private ObjectMapper objectMapper;
	private UUIDSerializer serializer;

	@Before
	public void setup() throws Exception {
		serializer = new UUIDSerializer();
		ObjectMapperFactoryBean factory = new ObjectMapperFactoryBean();
		factory.setSerializers(Arrays.asList(serializer));
		objectMapper = factory.getObject();
	}

	@Test
	public void serialize() throws IOException {
		String result = objectMapper
				.writeValueAsString(UUID.fromString("0e839a0b-00d8-1ab1-ffff-a1b98ee6c71d"));
		Assert.assertNotNull(result);
		Assert.assertEquals("\"0e839a0b-00d8-1ab1-ffffa1b98ee6c71d\"", result);
	}

}
