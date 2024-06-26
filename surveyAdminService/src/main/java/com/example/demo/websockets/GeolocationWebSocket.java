package com.example.demo.websockets;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import jakarta.websocket.EncodeException;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

/**
 * https://blogs.oracle.com/javamagazine/post/how-to-build-applications-with-the-websocket-api-for-java-ee-and-jakarta-ee
 */
@ServerEndpoint("/GeolocationWebSocket")
public class GeolocationWebSocket {

	private static final String CLASS_NAME = GeolocationWebSocket.class.getCanonicalName();
	private static final Logger LOG = Logger.getLogger(CLASS_NAME);

	private static final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, TestMessageSender> testSenders = new ConcurrentHashMap<>();

	private static final boolean TEST = Boolean.parseBoolean(System.getenv("TEST"));

	@OnOpen
	public void onOpen(Session session) throws IOException, EncodeException {
		if (LOG.isLoggable(Level.INFO))
			LOG.info("GeolocationWebSocket received new session: " + session.getId());

		sessions.put(session.getId(), session);

		if (TEST) {
			TestMessageSender testSender = new TestMessageSender(session.getId());
			testSenders.put(session.getId(), testSender);
			testSender.start();
		}
	}

	@OnMessage
	public void onBrowserMessage(Session session, String message) {
		if (LOG.isLoggable(Level.FINER))
			LOG.finer("GeolocationWebSocket received message from " + session.getId() + ": " + message);
		
		if (!"PING".equals(message)) {
			// Message other than a ping
			
			if (LOG.isLoggable(Level.INFO))
				LOG.info("GeolocationWebSocket received message from " + session.getId() + ": " + message);

			// Send message from websocket to Kafka topic
			sendToKafka(message, "feedbacktopic");
		}

	}

	@OnClose
	public void onClose(Session session) {
		if (LOG.isLoggable(Level.INFO))
			LOG.info("GeolocationWebSocket closing session: " + session.getId());

		sessions.remove(session.getId());
		
		TestMessageSender testSender = testSenders.remove(session.getId());
		if (testSender != null) {
			testSender.stopRunning();
		}
	}

	@OnError
	public void onError(Session session, Throwable throwable) {
		handleException(session, throwable);
	}

	private static final void handleException(Session session, Throwable throwable) {
		if (LOG.isLoggable(Level.SEVERE))
			LOG.log(Level.SEVERE, "Error for session: " + session.getId(), throwable);
	}

	public static final void sendMessageToBrowser(String sessionId, String text) throws IOException {
		Session session = sessions.get(sessionId);
		if (session != null) {
			try {
				if (LOG.isLoggable(Level.INFO))
					LOG.info("sendMessageToBrowser sending " + text + " to " + session.getId());

				session.getBasicRemote().sendText(text);
			} catch (Throwable t) {
				handleException(session, t);
			}
		} else {
			if (LOG.isLoggable(Level.WARNING))
				LOG.warning("sendMessageToBrowser skipping missing session for " + sessionId);
		}
	}

	public static final void sendMessageToAllBrowsers(String text) throws IOException {
		for (Session session : sessions.values()) {
			try {
				if (LOG.isLoggable(Level.INFO))
					LOG.info("sendMessageToAllBrowsers sending " + text + " to " + session.getId());

				session.getBasicRemote().sendText(text);
			} catch (Throwable t) {
				handleException(session, t);
			}
		}
	}

	private void sendToKafka(String message, String topic) {
		String bootstrapServer = System.getenv("mp.messaging.connector.liberty-kafka.bootstrap.servers");

		Properties props = new Properties();
		props.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
		props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		KafkaProducer<String, String> producer = new KafkaProducer<String, String>(props);
		ProducerRecord<String, String> record = new ProducerRecord<String,String>(topic, message);
		producer.send(record);
		producer.flush();
		producer.close();
	}
}
