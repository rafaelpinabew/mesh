package com.gentics.mesh.rest;

import org.apache.commons.lang.StringUtils;

import com.gentics.mesh.core.rest.common.RestModel;
import com.gentics.mesh.json.JsonUtil;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Utility methods to be used in combination with the vertx http client.
 */
public final class MeshRestRequestUtil {

	private static final Logger log = LoggerFactory.getLogger(MeshRestRequestUtil.class);
	public static final String BASEURI = "/api/v1";

	/**
	 * Handle the request.
	 * 
	 * @param method
	 *            Request method
	 * @param path
	 *            Path
	 * @param classOfT
	 *            Expected response POJO class
	 * @param bodyData
	 *            Body data to post
	 * @param contentType
	 * @param client
	 *            Client to use
	 * @param authentication
	 *            Authentication provider to use
	 * @return
	 */
	public static <T> MeshRequest<T> handleRequest(HttpMethod method, String path, Class<? extends T> classOfT, Buffer bodyData, String contentType,
			HttpClient client, MeshRestClientAuthenticationProvider authentication) {
		String uri = BASEURI + path;
		MeshResponseHandler<T> handler = new MeshResponseHandler<>(classOfT, method, uri);

		HttpClientRequest request = client.request(method, uri, handler);
		// Let the response handler fail when an error ocures
		request.exceptionHandler(e -> {
			handler.getFuture().fail(e);
		});
		if (log.isDebugEnabled()) {
			log.debug("Invoking get request to {" + uri + "}");
		}

		if (authentication != null) {
			authentication.addAuthenticationInformation(request).subscribe(() -> {
				request.headers().add("Accept", "application/json");

				if (bodyData.length() != 0) {
					request.headers().add("content-length", String.valueOf(bodyData.length()));
					if (!StringUtils.isEmpty(contentType)) {
						request.headers().add("content-type", contentType);
					}
					// Somehow the buffer gets mix up after some requests. It seems that the buffer object is somehow reused and does not return the correct data. toString seems to alleviate the problem.
					if (contentType != null && contentType.startsWith("application/json")) {
						request.write(bodyData.toString());
					} else {
						request.write(bodyData);
					}
				}
				
			});
		} else {
			request.headers().add("Accept", "application/json");

			if (bodyData.length() != 0) {
				request.headers().add("content-length", String.valueOf(bodyData.length()));
				if (!StringUtils.isEmpty(contentType)) {
					request.headers().add("content-type", contentType);
				}
				// Somehow the buffer gets mix up after some requests. It seems that the buffer object is somehow reused and does not return the correct data. toString seems to alleviate the problem.
				if (contentType != null && contentType.startsWith("application/json")) {
					request.write(bodyData.toString());
				} else {
					request.write(bodyData);
				}
			}
		}

		return new MeshRequest<T>(request, handler);
	}

	/**
	 * Handle the request.
	 * 
	 * @param method
	 *            Request method
	 * @param path
	 *            Request path
	 * @param classOfT
	 *            Expected response object class
	 * @param restModel
	 *            Model to be converted to json and send to the path
	 * @param client
	 *            Http client to be used
	 * @param authentication
	 *            Authentication provider to use
	 * @return
	 */
	public static <T> MeshRequest<T> handleRequest(HttpMethod method, String path, Class<? extends T> classOfT, RestModel restModel, HttpClient client,
			MeshRestClientAuthenticationProvider authentication) {
		Buffer buffer = Buffer.buffer();
		String json = JsonUtil.toJson(restModel);
		if (log.isDebugEnabled()) {
			log.debug(json);
		}
		buffer.appendString(json);
		return handleRequest(method, path, classOfT, buffer, "application/json", client, authentication);
	}

	/**
	 * Handle the request.
	 * 
	 * @param method
	 *            Request method
	 * @param path
	 *            Request path
	 * @param classOfT
	 *            Expected response object class
	 * @param jsonBodyData
	 *            JSON Data to post
	 * @param client
	 *            Http client to be used
	 * @param authentication
	 *            Authentication provider to use
	 * @return
	 */
	public static <T> MeshRequest<T> handleRequest(HttpMethod method, String path, Class<? extends T> classOfT, String jsonBodyData, HttpClient client,
			MeshRestClientAuthenticationProvider authentication) {

		if (log.isDebugEnabled()) {
			log.debug("Posting json {" + jsonBodyData + "}");
		}
		Buffer buffer = Buffer.buffer();
		if (!StringUtils.isEmpty(jsonBodyData)) {
			buffer.appendString(jsonBodyData);
		}

		return handleRequest(method, path, classOfT, buffer, "application/json", client, authentication);
	}

	/**
	 * Handle the request.
	 * 
	 * @param method
	 *            Request method
	 * @param path
	 *            Request path
	 * @param classOfT
	 *            Expected response object class
	 * @param client
	 *            Http client to be used
	 * @param authentication
	 *            Authentication provider to use
	 * @return
	 */
	public static <T> MeshRequest<T> handleRequest(HttpMethod method, String path, Class<? extends T> classOfT, HttpClient client,
			MeshRestClientAuthenticationProvider authentication) {
		return handleRequest(method, path, classOfT, Buffer.buffer(), null, client, authentication);
	}
}
