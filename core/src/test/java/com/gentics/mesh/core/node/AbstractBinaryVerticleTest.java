package com.gentics.mesh.core.node;

import static com.gentics.mesh.demo.TestDataProvider.PROJECT_NAME;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;

import com.gentics.mesh.Mesh;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.service.ServerSchemaStorage;
import com.gentics.mesh.core.rest.common.GenericMessageResponse;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.core.rest.schema.impl.BinaryFieldSchemaImpl;
import com.gentics.mesh.rest.MeshRequest;
import com.gentics.mesh.test.AbstractIsolatedRestVerticleTest;

import io.vertx.core.buffer.Buffer;
import io.vertx.test.core.TestUtils;

public abstract class AbstractBinaryVerticleTest extends AbstractIsolatedRestVerticleTest {

	@Before
	public void setup() throws IOException {
		File uploadDir = new File(Mesh.mesh().getOptions().getUploadOptions().getDirectory());
		FileUtils.deleteDirectory(uploadDir);
		uploadDir.mkdirs();

		File tempDir = new File(Mesh.mesh().getOptions().getUploadOptions().getTempDirectory());
		FileUtils.deleteDirectory(tempDir);
		tempDir.mkdirs();

		File imageCacheDir = new File(Mesh.mesh().getOptions().getImageOptions().getImageCacheDirectory());
		FileUtils.deleteDirectory(imageCacheDir);
		imageCacheDir.mkdirs();

		Mesh.mesh().getOptions().getUploadOptions().setByteLimit(Long.MAX_VALUE);
	}

	@After
	public void cleanup() throws Exception {
		super.cleanup();
		FileUtils.deleteDirectory(new File(Mesh.mesh().getOptions().getImageOptions().getImageCacheDirectory()));
		FileUtils.deleteDirectory(new File(Mesh.mesh().getOptions().getUploadOptions().getDirectory()));
		FileUtils.deleteDirectory(new File(Mesh.mesh().getOptions().getUploadOptions().getTempDirectory()));
	}

	/**
	 * Prepare the schema of the given node by adding the binary content field to its schema fields. This method will also update the clientside schema storage.
	 * 
	 * @param node
	 * @param mimeTypeWhitelist
	 * @param binaryFieldName
	 * @throws IOException
	 */
	protected void prepareSchema(Node node, String mimeTypeWhitelist, String binaryFieldName) throws IOException {
		// Update the schema and enable binary support for folders
		Schema schema = node.getSchemaContainer().getLatestVersion().getSchema();
		schema.addField(new BinaryFieldSchemaImpl().setAllowedMimeTypes(mimeTypeWhitelist).setName(binaryFieldName).setLabel("Binary content"));
		node.getSchemaContainer().getLatestVersion().setSchema(schema);
		ServerSchemaStorage.getInstance().clear();
		// node.getSchemaContainer().setSchema(schema);
	}

	protected MeshRequest<GenericMessageResponse> uploadRandomData(String uuid, String languageTag, String fieldKey, int binaryLen, String contentType,
			String fileName) {

		// role().grantPermissions(node, UPDATE_PERM);
		Buffer buffer = TestUtils.randomBuffer(binaryLen);
		return getClient().updateNodeBinaryField(PROJECT_NAME, uuid, languageTag, fieldKey, buffer, fileName, contentType);
	}

}
