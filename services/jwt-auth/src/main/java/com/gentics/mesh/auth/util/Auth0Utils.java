package com.gentics.mesh.auth.util;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.OkHttpClient.Builder;

public final class Auth0Utils {

	private static final Logger log = LoggerFactory.getLogger(Auth0Utils.class);

	private Auth0Utils() {
	}

	public static Set<JsonObject> loadJWKs(String name) throws IOException {
		Request request = new Request.Builder()
			.header("Accept", "application/json")
			.url("https://" + name + ".auth0.com/.well-known/jwks.json")
			.build();

		try (Response response = httpClient().newCall(request).execute()) {
			if (!response.isSuccessful()) {
				log.error(response.body().toString());
				throw new RuntimeException("Error while loading certs. Got code {" + response.code() + "}");
			}
			JsonObject json = new JsonObject(response.body().string());
			JsonArray keys = json.getJsonArray("keys");
			Set<JsonObject> jwks = new HashSet<>();
			for (int i = 0; i < keys.size(); i++) {
				JsonObject key = keys.getJsonObject(i);
				jwks.add(key);
			}
			return jwks;
		}

	}

	public static JsonObject loginAuth0(String name, String clientId, String username, String password, String secret) throws IOException {
		StringBuilder content = new StringBuilder();
		content.append("client_id=" + clientId + "&");
		content.append("username=" + username + "&");
		content.append("password=" + password + "&");
		content.append("grant_type=password&");
		content.append("client_secret=" + secret);
		RequestBody body = RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), content.toString());
		Request request = new Request.Builder()
			.post(body)
			.url("https://" + name + ".auth0.com/oauth/token")
			.build();

		Response response = httpClient().newCall(request).execute();
		return new JsonObject(response.body().string());
	}

	private static OkHttpClient httpClient() {
		Builder builder = new OkHttpClient.Builder();
		OkHttpClient client = builder.build();
		return client;
	}

}
