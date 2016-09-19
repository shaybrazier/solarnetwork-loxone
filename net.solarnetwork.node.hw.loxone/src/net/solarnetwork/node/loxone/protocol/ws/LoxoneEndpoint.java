/* ==================================================================
 * LoxoneEndpoint.java - 17/09/2016 7:40:39 AM
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.HmacUtils;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.solarnetwork.node.loxone.dao.ConfigDao;
import net.solarnetwork.node.loxone.domain.Config;
import net.solarnetwork.node.loxone.protocol.ws.handler.BaseCommandHandler;
import net.solarnetwork.node.settings.SettingSpecifier;
import net.solarnetwork.node.settings.SettingSpecifierProvider;
import net.solarnetwork.node.settings.support.BasicTextFieldSettingSpecifier;
import net.solarnetwork.util.OptionalService;

/**
 * Endpoint for the Loxone miniserver websocket API.
 * 
 * The Loxone server responds to all websocket message requests with a pair of
 * response messages: first a binary "message header" message and then another
 * binary or text message with the actual payload of the response. This class
 * acts as a broker for the messages, and handles the authentication of the
 * connection. It then relies on {@link CommandHandler} instances to deal with
 * all other messages, configured via {@link #setCommandHandlers(List)}.
 * 
 * This class also relies on a list of {@link BinaryFileHandler} instances to
 * deal with the {@code BinaryFile} message type, configured via
 * {@link #setBinaryFileHandlers(List)}.
 * 
 * This class listens for the
 * {@code LoxoneEvents#STRUCTURE_FILE_MODIFICATION_DATE_EVENT} event and if that
 * date changes will request the structure file again from the Loxone server.
 * 
 * @author matt
 * @version 1.0
 */
public class LoxoneEndpoint extends Endpoint
		implements MessageHandler.Whole<ByteBuffer>, EventHandler, SettingSpecifierProvider {

	/**
	 * A session user property key that must provide the {@link Config} ID value
	 * to use.
	 */
	public static final String CONFIG_ID_USER_PROPERTY = "config-id";

	private String host = "10.0.0.1:3000";
	private String username = null;
	private String password = null;

	private ObjectMapper objectMapper = new ObjectMapper();
	private List<CommandHandler> commandHandlers = null;
	private List<BinaryFileHandler> binaryFileHandlers = null;
	private OptionalService<EventAdmin> eventAdmin = null;
	private final int keepAliveSeconds = 240;
	private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(0);
	private MessageSource messageSource;
	private ConfigDao configDao;

	private final Logger log = LoggerFactory.getLogger(getClass());

	// create a fixed size queue for handling message headers
	private final Queue<MessageHeader> headerQueue = new ArrayBlockingQueue<>(1, true);

	// create an internal handler for authentication, etc.
	private final InternalCommandHandler internalCommandHandler = new InternalCommandHandler();

	private Session session;
	private ScheduledFuture<?> keepAliveFuture = null;
	private ScheduledFuture<?> connectFuture = null;
	private Config configuration = null;

	private synchronized void connect() {
		javax.websocket.WebSocketContainer container = org.glassfish.tyrus.client.ClientManager
				.createClient(
						org.glassfish.tyrus.container.jdk.client.JdkClientContainer.class.getName());
		ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
				.preferredSubprotocols(Arrays.asList("remotecontrol")).build();
		session = null;
		connectFuture = null;
		try {
			log.debug("Opening Loxone websocket connection to ws://{}", host);
			session = container.connectToServer(this, config, new URI("ws://" + host));
		} catch ( Exception e ) {
			logConciseException("Error connecting to ws://{}", e, host);
		}
	}

	/**
	 * Disconnect from the Loxone server.
	 */
	public synchronized void disconnect() {
		if ( session != null && session.isOpen() == true ) {
			try {
				session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "All done."));
				log.debug("Connection closed");
			} catch ( IOException e ) {
				log.debug("IOException closing websocket Session", e);
			} finally {
				session = null;
			}
		} else {
			session = null;
		}
	}

	private synchronized void connectionDetailsChanged() {
		if ( host == null || username == null || password == null || host.isEmpty() || username.isEmpty()
				|| password.isEmpty() ) {
			return;
		}

		if ( connectFuture != null && !connectFuture.isDone() ) {
			connectFuture.cancel(true);
			connectFuture = null;
		}
		disconnect();
		connectFuture = scheduledExecutorService.schedule(new Runnable() {

			@Override
			public void run() {
				connect();
			}
		}, 2, TimeUnit.SECONDS);
	}

	@Override
	public void onMessage(ByteBuffer buf) {
		if ( buf.order() != ByteOrder.LITTLE_ENDIAN ) {
			buf.order(ByteOrder.LITTLE_ENDIAN);
		}
		MessageHeader h = headerQueue.poll();
		if ( h != null ) {
			log.debug("Got binary message {}", h);
			if ( !handleBinaryFileIfPossible(h, buf) ) {
				// hmm, seems sometimes we get more than one response from Loxone
				if ( !headerQueue.offer(h) ) {
					log.warn("Dropping message header: {}", h);
				}
			}
		} else {
			// this should be a message header message, and another message will follow 
			// from the Loxone that logically refers to this header
			MessageHeader header = new MessageHeader(buf);
			log.debug("Got message header {}", header);
			if ( !headerQueue.offer(header) ) {
				log.warn("Dropping message header: {}", header);
			}
		}
	}

	@Override
	public void onClose(Session session, CloseReason closeReason) {
		super.onClose(session, closeReason);
		log.debug("Session closed: {}", closeReason);
		stopKeepAliveTask();
		this.session = null;
	}

	@Override
	public void onError(Session session, Throwable throwable) {
		super.onError(session, throwable);
		logConciseException("Unknown websocket error", throwable);
	}

	@Override
	public void onOpen(Session session, EndpointConfig config) {
		this.session = session;

		// setup our Config based on an ID derived from the host value
		String hostDigest = DigestUtils.sha1Hex(host);
		Long configId = Long.parseLong(hostDigest.substring(0, 16), 16);
		session.getUserProperties().put(CONFIG_ID_USER_PROPERTY, configId);

		configuration = configDao.getConfig(configId);

		// add binary handler to decode message headers and other binary messages
		session.addMessageHandler(this);

		// add a streaming text handler to support very large responses (such as structure file)
		session.addMessageHandler(new StreamingTextMessageHandler());

		// authenticate
		try {
			log.info("Connected to Loxone server, will request authenticaiton key now.");
			internalCommandHandler.sendCommand(CommandType.GetAuthenticationKey, session);
		} catch ( IOException e ) {
			log.error("Communication error authenticating: {}", e.getMessage());
		}
	}

	/**
	 * Command handler to deal with authentication and internal bookeeping
	 * tasks.
	 */
	private class InternalCommandHandler extends BaseCommandHandler {

		@Override
		public boolean supportsCommand(CommandType command) {
			return (command == CommandType.GetAuthenticationKey || command == CommandType.Authenticate
					|| command == CommandType.Auth || command == CommandType.KeepAlive);
		}

		@Override
		public boolean handleCommandValue(CommandType command, MessageHeader header, Session session,
				JsonNode parser, String value) throws IOException {
			log.debug("Handling message {} command {}: {}", header, command, value);
			if ( command == CommandType.GetAuthenticationKey ) {
				byte[] key;
				try {
					key = Hex.decodeHex(value.toCharArray());
				} catch ( DecoderException e ) {
					throw new RuntimeException(e);
				}
				String authString = getUsername() + ":" + getPassword();
				if ( log.isDebugEnabled() ) {
					log.debug("Authenticating using key {} and user {}", new String(key, "UTF-8"),
							getUsername());
				}
				String msg = "authenticate/" + HmacUtils.hmacSha1Hex(key, authString.getBytes("UTF-8"));
				session.getBasicRemote().sendText(msg);
				return true;
			} else if ( command == CommandType.Authenticate ) {
				// immediately after authentication, check for last modification date of structure file
				sendCommandIfPossible(CommandType.StructureFileLastModifiedDate);

				// also schedule a keepalive message
				scheduleKeepAliveTask();
				return true;
			} else if ( command == CommandType.Auth ) {
				return true;
			} else if ( command == CommandType.KeepAlive ) {
				log.debug("Keepalive response received");
				return true;
			}
			return false;
		}

	}

	private synchronized void scheduleKeepAliveTask() {
		if ( keepAliveFuture == null || keepAliveFuture.isDone() ) {
			keepAliveFuture = scheduledExecutorService.scheduleAtFixedRate(new KeepAliveTask(),
					keepAliveSeconds, keepAliveSeconds, TimeUnit.SECONDS);
		}
	}

	private synchronized void stopKeepAliveTask() {
		if ( keepAliveFuture != null ) {
			keepAliveFuture.cancel(true);
		}
	}

	private class KeepAliveTask implements Runnable {

		@Override
		public void run() {
			try {
				sendCommandIfPossible(CommandType.KeepAlive);
			} catch ( IOException e ) {
				logConciseException("Error sending keepalive message to Loxone server", e);
			}
		}

	}

	/**
	 * Streaming text message handler that acts as a broker for
	 * {@link CommandHandler} instances to process messages.
	 */
	private class StreamingTextMessageHandler implements javax.websocket.MessageHandler.Whole<String> {

		@Override
		public void onMessage(String payload) {
			// take our corresponding message header
			final MessageHeader header = headerQueue.poll();
			if ( header == null ) {
				// we expect to have that header, but we should also be able to continue 
				// without it so just log a message
				log.debug("MessageHeader not available for text message!");
				return;
			}

			log.debug("Handling message {}: {}", header, payload);

			if ( header.getType() == MessageType.TextMessage ) {
				// start inspecting the message to know what to do
				try {
					JsonNode json = getObjectMapper().readTree(payload);
					if ( json.hasNonNull("LL") ) {
						JsonNode root = json.path("LL");
						String control = root.path("control").textValue();
						CommandType command = CommandType.forControlValue(control);
						handleCommandIfPossible(command, header, root);
					} else {
						log.debug("Unknown command message {}: {}", header, payload);
					}
				} catch ( IOException e ) {
					logConciseException("Error parsing text command {}", e, header);
				}
			} else if ( header.getType() == MessageType.BinaryFile ) {
				try (Reader reader = new StringReader(payload)) {
					handleBinaryFileIfPossible(header, reader);
				} catch ( IOException e ) {
					logConciseException("Error parsing text file {}", e, header);
				}
			}
		}
	}

	/**
	 * Attempt to send a command to the Loxone device using a configured
	 * {@link CommandHandler}.
	 * 
	 * @param command
	 *        The command to send.
	 * @return <em>true</em> if a handler was found and it handled the command
	 * @throws IOException
	 *         if a communication error occurs
	 */
	private boolean sendCommandIfPossible(CommandType command) throws IOException {
		CommandHandler handler = getCommandHandlerForCommand(command);
		if ( handler != null ) {
			return handler.sendCommand(command, session);
		}
		return false;
	}

	private boolean handleCommandIfPossible(CommandType command, MessageHeader header, JsonNode tree)
			throws IOException {
		// first check if the command is supported directly, and if so handle it
		if ( internalCommandHandler.supportsCommand(command) ) {
			return internalCommandHandler.handleCommand(command, header, session, tree);
		} else {
			CommandHandler handler = getCommandHandlerForCommand(command);
			if ( handler != null ) {
				return handler.handleCommand(command, header, session, tree);
			}
		}
		return false;
	}

	private CommandHandler getCommandHandlerForCommand(CommandType command) {
		List<CommandHandler> list = getCommandHandlers();
		if ( list != null ) {
			for ( CommandHandler handler : list ) {
				if ( handler.supportsCommand(command) ) {
					return handler;
				}
			}
		}
		return null;
	}

	private static final int BINARY_BUFFER_SIZE = 4096;

	/**
	 * Attempt to handle a {@code BinaryFile} message type with text content.
	 * 
	 * @param header
	 *        The current message header.
	 * @param reader
	 *        The reader to read from. The reader may not be used further after
	 *        calling this method.
	 * @return <em>true</em> if the message was handled
	 * @throws IOException
	 *         if any communication error occurs
	 */
	private boolean handleBinaryFileIfPossible(MessageHeader header, Reader reader) throws IOException {
		List<BinaryFileHandler> list = getBinaryFileHandlers();
		if ( list != null ) {
			Reader r = (reader.markSupported() ? reader
					: new BufferedReader(reader, BINARY_BUFFER_SIZE));
			r.mark(BINARY_BUFFER_SIZE);
			for ( BinaryFileHandler handler : list ) {
				boolean supports = handler.supportsTextMessage(header, r, BINARY_BUFFER_SIZE);
				r.reset();
				if ( supports ) {
					return handler.handleTextMessage(header, session, r);
				}
			}
		}
		return false;
	}

	/**
	 * Attempt to handle a {@code BinaryFile} message type with text content.
	 * 
	 * @param header
	 *        The current message header.
	 * @param buffer
	 *        The buffer to read from.
	 * @return <em>true</em> if the message was handled
	 * @throws IOException
	 *         if any communication error occurs
	 */
	private boolean handleBinaryFileIfPossible(MessageHeader header, ByteBuffer buffer) {
		List<BinaryFileHandler> list = getBinaryFileHandlers();
		if ( list != null ) {
			buffer.mark();
			for ( BinaryFileHandler handler : list ) {
				boolean supports = handler.supportsDataMessage(header, buffer);
				buffer.reset();
				if ( supports ) {
					return handler.handleDataMessage(header, session, buffer);
				}
			}
		}
		return false;
	}

	@Override
	public void handleEvent(Event event) {
		final String topic = event.getTopic();
		final long lastStructureFileModificationDate = (configuration == null
				|| configuration.getLastModified() == null ? -1
						: configuration.getLastModified().getTime());
		if ( LoxoneEvents.STRUCTURE_FILE_MODIFICATION_DATE_EVENT.equals(topic) ) {
			Object prop = event.getProperty(LoxoneEvents.EVENT_PROPERTY_DATE);
			if ( prop instanceof Number ) {
				final long date = ((Number) prop).longValue();
				if ( date != lastStructureFileModificationDate ) {
					log.info("Loxone configuration date different than local copy: will download now.");
					try {
						sendCommandIfPossible(CommandType.GetStructureFile);
					} catch ( IOException e ) {
						logConciseException("Communication problem requesting structure file", e);
					}
				} else {
					log.info("Loxone configuration up to date; enabling status updates.");
					try {
						sendCommandIfPossible(CommandType.EnableInputStatusUpdate);
					} catch ( IOException e ) {
						logConciseException("Communication problem requesting status updates", e);
					}
				}
			}
		} else if ( LoxoneEvents.STRUCTURE_FILE_SAVED_EVENT.equals(topic) ) {
			log.info("Loxone configuration saved; enabling status updates.");
			configuration = configDao
					.getConfig((Long) session.getUserProperties().get(CONFIG_ID_USER_PROPERTY));
			try {
				sendCommandIfPossible(CommandType.EnableInputStatusUpdate);
			} catch ( IOException e ) {
				logConciseException("Communication problem requesting status updates", e);
			}
		}
	}

	private void logConciseException(String msg, Throwable t, Object... params) {
		// if DEBUG enabled, print exception stack trace, otherwise just the exception message
		if ( log.isDebugEnabled() ) {
			log.error(msg, params, t);
		} else {
			Object[] newParams;
			if ( params != null ) {
				newParams = new Object[params.length + 1];
				System.arraycopy(params, 0, newParams, 0, params.length);
			} else {
				newParams = new Object[] { t };
			}
			log.error(msg + ": {}", newParams);
		}
	}

	@Override
	public String getSettingUID() {
		return "net.solarnetwork.node.loxone.ws";
	}

	@Override
	public String getDisplayName() {
		return "Loxone Miniserver";
	}

	@Override
	public MessageSource getMessageSource() {
		return messageSource;
	}

	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	@Override
	public List<SettingSpecifier> getSettingSpecifiers() {
		LoxoneEndpoint defaults = new LoxoneEndpoint();
		List<SettingSpecifier> results = new ArrayList<SettingSpecifier>(4);
		results.add(new BasicTextFieldSettingSpecifier("host", defaults.getHost()));
		results.add(new BasicTextFieldSettingSpecifier("username", defaults.getUsername()));
		results.add(new BasicTextFieldSettingSpecifier("password", defaults.getPassword(), true));
		return results;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		if ( this.host == null || !this.host.equals(host) ) {
			this.host = host;
			connectionDetailsChanged();
		}
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		if ( this.username == null || !this.username.equals(username) ) {
			this.username = username;
			connectionDetailsChanged();
		}
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		if ( this.password == null || !this.password.equals(password) ) {
			this.password = password;
			connectionDetailsChanged();
		}
	}

	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public List<CommandHandler> getCommandHandlers() {
		return commandHandlers;
	}

	public void setCommandHandlers(List<CommandHandler> commandHandlers) {
		this.commandHandlers = commandHandlers;
	}

	public List<BinaryFileHandler> getBinaryFileHandlers() {
		return binaryFileHandlers;
	}

	public void setBinaryFileHandlers(List<BinaryFileHandler> binaryFileHandlers) {
		this.binaryFileHandlers = binaryFileHandlers;
	}

	public OptionalService<EventAdmin> getEventAdmin() {
		return eventAdmin;
	}

	public void setEventAdmin(OptionalService<EventAdmin> eventAdmin) {
		this.eventAdmin = eventAdmin;
		internalCommandHandler.setEventAdmin(eventAdmin);
	}

	public ScheduledExecutorService getScheduledExecutorService() {
		return scheduledExecutorService;
	}

	public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
		this.scheduledExecutorService = scheduledExecutorService;
	}

	public ConfigDao getConfigDao() {
		return configDao;
	}

	public void setConfigDao(ConfigDao configDao) {
		this.configDao = configDao;
	}

}
