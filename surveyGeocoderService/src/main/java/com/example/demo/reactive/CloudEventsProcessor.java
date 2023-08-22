package com.example.demo.reactive;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.kafka.common.serialization.StringDeserializer;

import com.example.demo.Geocoder;
import com.google.maps.errors.ApiException;
import com.google.maps.model.PlaceDetails;

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
	private static final String ERROR_FAILED_GEOCODING = "Error performing geocoding";

	@Path("locationInput")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response locationInput(CloudEvent incoming) {
		if (LOG.isLoggable(Level.FINER))
			LOG.entering(CLASS_NAME, "locationInput", incoming);

		if (LOG.isLoggable(Level.INFO))
			LOG.info("Received CloudEvent: " + incoming);

		CloudEventData data = incoming.getData();

		if (LOG.isLoggable(Level.INFO))
			LOG.info("CloudEventData: " + data);

		String location = null;
		try (StringDeserializer deserializer = new StringDeserializer()) {
			location = deserializer.deserialize(null, data.toBytes());
		}

		if (LOG.isLoggable(Level.INFO))
			LOG.info("Input location: " + location);

		Response result = null;

		try {
			PlaceDetails geocodeResult = Geocoder.geocode(location);

			if (LOG.isLoggable(Level.INFO))
				LOG.info("Geocode results: " + geocodeResult);

			double latitude = geocodeResult.geometry.location.lat;
			double longitude = geocodeResult.geometry.location.lng;

			if (LOG.isLoggable(Level.INFO))
				LOG.info("Point: " + latitude + "," + longitude);

			result = Response.ok().build();

		} catch (ApiException | InterruptedException | IOException e) {
			if (LOG.isLoggable(Level.SEVERE))
				LOG.log(Level.SEVERE, ERROR_FAILED_GEOCODING, e);

			result = Response.serverError().entity(ERROR_FAILED_GEOCODING + ": " + e.getMessage()).build();
		}

		if (LOG.isLoggable(Level.FINER))
			LOG.exiting(CLASS_NAME, "locationInput", result);

		return result;
	}
}
