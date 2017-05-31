package com.gentics.mesh.core.schema;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.UPDATE_PERM;
import static com.gentics.mesh.test.TestDataProvider.PROJECT_NAME;
import static com.gentics.mesh.test.context.MeshTestHelper.call;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.gentics.ferma.Tx;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.root.ProjectRoot;
import com.gentics.mesh.core.data.schema.MicroschemaContainer;
import com.gentics.mesh.core.rest.microschema.impl.MicroschemaResponse;
import com.gentics.mesh.core.rest.project.ProjectCreateRequest;
import com.gentics.mesh.core.rest.project.ProjectResponse;
import com.gentics.mesh.core.rest.schema.MicroschemaListResponse;
import com.gentics.mesh.core.rest.schema.SchemaReference;
import com.gentics.mesh.test.context.AbstractMeshTest;
import com.gentics.mesh.test.context.MeshTestSetting;
import static com.gentics.mesh.test.TestSize.FULL;

@MeshTestSetting(useElasticsearch = false, testSize = FULL, startServer = true)
public class MicroschemaProjectEndpointTest extends AbstractMeshTest {

	@Test
	public void testReadProjectMicroschemas() {
		try (Tx tx = db().tx()) {
			MicroschemaListResponse list = call(() -> client().findMicroschemas(PROJECT_NAME));
			assertEquals(2, list.getData().size());

			call(() -> client().unassignMicroschemaFromProject(PROJECT_NAME, microschemaContainer("vcard").getUuid()));

			list = call(() -> client().findMicroschemas(PROJECT_NAME));
			assertEquals(1, list.getData().size());
		}
	}

	// Microschema Project Testcases - PUT / Add

	@Test
	public void testAddMicroschemaToExtraProject() {
		final String name = "test12345";
		try (Tx tx = db().tx()) {
			MicroschemaContainer microschema = microschemaContainer("vcard");

			ProjectCreateRequest request = new ProjectCreateRequest();
			request.setSchema(new SchemaReference().setName("folder"));
			request.setName(name);

			ProjectResponse restProject = call(() -> client().createProject(request));

			call(() -> client().assignMicroschemaToProject(restProject.getName(), microschema.getUuid()));
		}
	}

	@Test
	public void testAddMicroschemaToProjectWithPerm() throws Exception {
		try (Tx tx = db().tx()) {
			MicroschemaContainer microschema = microschemaContainer("vcard");
			ProjectRoot projectRoot = meshRoot().getProjectRoot();

			ProjectCreateRequest request = new ProjectCreateRequest();
			request.setSchema(new SchemaReference().setName("folder"));
			request.setName("extraProject");
			ProjectResponse created = call(() -> client().createProject(request));
			Project extraProject = projectRoot.findByUuid(created.getUuid());

			// Add only read perms
			role().grantPermissions(microschema, READ_PERM);
			role().grantPermissions(extraProject, UPDATE_PERM);

			MicroschemaResponse restMicroschema = call(() -> client().assignMicroschemaToProject(extraProject.getName(), microschema.getUuid()));
			assertThat(restMicroschema.getUuid()).isEqualTo(microschema.getUuid());
			extraProject.reload();
			extraProject.getMicroschemaContainerRoot().reload();
			assertNotNull("The microschema should be added to the extra project",
					extraProject.getMicroschemaContainerRoot().findByUuid(microschema.getUuid()));
		}
	}

	@Test
	public void testAddMicroschemaToProjectWithoutPerm() throws Exception {
		String projectUuid;
		String microschemaUuid;
		Project extraProject;
		try (Tx tx = db().tx()) {
			MicroschemaContainer microschema = microschemaContainer("vcard");
			microschemaUuid = microschema.getUuid();
			ProjectRoot projectRoot = meshRoot().getProjectRoot();
			ProjectCreateRequest request = new ProjectCreateRequest();
			request.setName("extraProject");
			request.setSchema(new SchemaReference().setName("folder"));
			ProjectResponse response = call(() -> client().createProject(request));
			projectUuid = response.getUuid();
			extraProject = projectRoot.findByUuid(projectUuid);
			// Revoke Update perm on project
			role().revokePermissions(extraProject, UPDATE_PERM);
		}

		call(() -> client().assignMicroschemaToProject("extraProject", microschemaUuid), FORBIDDEN, "error_missing_perm", projectUuid);

		try (Tx tx = db().tx()) {
			// Reload the microschema and check for expected changes
			MicroschemaContainer microschema = microschemaContainer("vcard");

			assertFalse("The microschema should not have been added to the extra project but it was",
					extraProject.getMicroschemaContainerRoot().contains(microschema));
		}
	}

	// Microschema Project Testcases - DELETE / Remove
	@Test
	public void testRemoveMicroschemaFromProjectWithPerm() throws Exception {
		try (Tx tx = db().tx()) {
			MicroschemaContainer microschema = microschemaContainer("vcard");
			Project project = project();
			assertTrue("The microschema should be assigned to the project.", project.getMicroschemaContainerRoot().contains(microschema));

			call(() -> client().unassignMicroschemaFromProject(project.getName(), microschema.getUuid()));

			MicroschemaListResponse list = call(() -> client().findMicroschemas(PROJECT_NAME));

			assertEquals("The removed microschema should not be listed in the response", 0,
					list.getData().stream().filter(s -> s.getUuid().equals(microschema.getUuid())).count());
			project.getMicroschemaContainerRoot().reload();
			assertFalse("The microschema should no longer be assigned to the project.", project.getMicroschemaContainerRoot().contains(microschema));
		}
	}

	@Test
	public void testRemoveMicroschemaFromProjectWithoutPerm() throws Exception {
		try (Tx tx = db().tx()) {
			MicroschemaContainer microschema = microschemaContainer("vcard");
			Project project = project();

			assertTrue("The microschema should be assigned to the project.", project.getMicroschemaContainerRoot().contains(microschema));
			// Revoke update perms on the project
			role().revokePermissions(project, UPDATE_PERM);

			call(() -> client().unassignMicroschemaFromProject(project.getName(), microschema.getUuid()), FORBIDDEN, "error_missing_perm",
					project.getUuid());

			// Reload the microschema and check for expected changes
			assertTrue("The microschema should still be listed for the project.", project.getMicroschemaContainerRoot().contains(microschema));
		}
	}

}
