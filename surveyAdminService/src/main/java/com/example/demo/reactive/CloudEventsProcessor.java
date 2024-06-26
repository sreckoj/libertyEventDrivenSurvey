/*
 * Copyright 2023 IBM Corp. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.example.demo.reactive;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.lucene.util.SloppyMath;

import com.example.demo.Configuration;
import com.example.demo.websockets.GeolocationWebSocket;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@RequestScoped
@Path("/cloudevents")
public class CloudEventsProcessor {
	private static final String CLASS_NAME = CloudEventsProcessor.class.getCanonicalName();
	private static final Logger LOG = Logger.getLogger(CLASS_NAME);

	@Path("geocodeComplete")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response geocodeComplete(CloudEvent incoming) {
		if (LOG.isLoggable(Level.FINER))
			LOG.entering(CLASS_NAME, "geocodeComplete", incoming);

		if (LOG.isLoggable(Level.INFO))
			LOG.info("Received CloudEvent: " + incoming);

		CloudEventData data = incoming.getData();

		if (LOG.isLoggable(Level.INFO))
			LOG.info("CloudEventData: " + data);

		String jsonString = null;
		try (StringDeserializer deserializer = new StringDeserializer()) {
			jsonString = deserializer.deserialize(null, data.toBytes());
		}

		if (LOG.isLoggable(Level.INFO))
			LOG.info("Geocode results: " + jsonString);

		JsonObject jsonObj = (new Gson()).fromJson(jsonString, JsonObject.class);
		String latitudeStr = jsonObj.get("latitude").getAsString();
		String longitudeStr = jsonObj.get("longitude").getAsString();
		String location = jsonObj.get("location").getAsString();
		String color = jsonObj.get("color").getAsString();
		String key = jsonObj.get("key").getAsString();

		double latitude = Double.parseDouble(latitudeStr);
		double longitude = Double.parseDouble(longitudeStr);
		double approximateDistance = SloppyMath.haversinMeters(latitude, longitude, Configuration.getSurveyLatitude(),
				Configuration.getSuveyLongitude());

		String finalResults = latitude + " " + longitude + " " + approximateDistance + " " + color + " " + key + " " + location;

		try {
			GeolocationWebSocket.sendMessageToAllBrowsers(finalResults);
		} catch (IOException e) {
			if (LOG.isLoggable(Level.SEVERE))
				LOG.log(Level.SEVERE, "Failed calling sendMessageToAllBrowsers", e);
		}

		Response result = Response.ok().build();

		if (LOG.isLoggable(Level.FINER))
			LOG.exiting(CLASS_NAME, "geocodeComplete", result);

		return result;
	}
}
