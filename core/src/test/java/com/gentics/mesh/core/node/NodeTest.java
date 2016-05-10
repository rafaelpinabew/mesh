package com.gentics.mesh.core.node;


import static com.gentics.mesh.assertj.MeshAssertions.assertThat;
import static com.gentics.mesh.core.data.search.SearchQueueEntryAction.DELETE_ACTION;
import static com.gentics.mesh.util.MeshAssert.assertAffectedElements;
import static com.gentics.mesh.util.MeshAssert.failingLatch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.mesh.api.common.SortOrder;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.GraphFieldContainer;
import com.gentics.mesh.core.data.GraphFieldContainerEdge.Type;
import com.gentics.mesh.core.data.Language;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.Release;
import com.gentics.mesh.core.data.Tag;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.node.handler.NodeMigrationHandler;
import com.gentics.mesh.core.data.page.impl.PageImpl;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.schema.SchemaContainerVersion;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.user.NodeReference;
import com.gentics.mesh.graphdb.Trx;
import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.query.impl.PagingParameter;
import com.gentics.mesh.test.AbstractBasicObjectTest;
import com.gentics.mesh.util.InvalidArgumentException;
import com.gentics.mesh.util.MeshAssert;

import io.vertx.ext.web.RoutingContext;
import rx.Observable;

public class NodeTest extends AbstractBasicObjectTest {
	@Autowired
	private NodeMigrationHandler nodeMigrationHandler;

	@Test
	@Override
	public void testTransformToReference() throws Exception {
		Node node = content();
		InternalActionContext ac = getMockedInternalActionContext("?version=draft");
		NodeReference reference = node.transformToReference(ac).toBlocking().first();
		assertNotNull(reference);
		assertEquals(node.getUuid(), reference.getUuid());
	}

	@Test
	public void testGetPath() throws Exception {
		Node newsNode = content("news overview");
		CountDownLatch latch = new CountDownLatch(2);
		Observable<String> path = newsNode.getPath(project().getLatestRelease().getUuid(), Type.DRAFT,
				english().getLanguageTag());
		path.subscribe(s -> {
			assertEquals("/News/News+Overview.en.html", s);
			latch.countDown();
		});

		Observable<String> pathSegementFieldValue = newsNode.getPathSegment(project().getLatestRelease().getUuid(),
				Type.DRAFT, english().getLanguageTag());
		pathSegementFieldValue.subscribe(s -> {
			assertEquals("News Overview.en.html", s);
			latch.countDown();
		});
		failingLatch(latch);
	}

	@Test
	public void testMeshNodeStructure() {
		Node newsNode = content("news overview");
		assertNotNull(newsNode);
		Node newSubNode;
		newSubNode = newsNode.create(user(), getSchemaContainer().getLatestVersion(), project());

		assertEquals(1, newsNode.getChildren().size());
		Node firstChild = newsNode.getChildren().iterator().next();
		assertEquals(newSubNode.getUuid(), firstChild.getUuid());
	}

	@Test
	public void testGetSegmentPath() {
		Node newsNode = content("news overview");
		RoutingContext rc = getMockedRoutingContext("?version=draft");
		InternalActionContext ac = InternalActionContext.create(rc);
		assertNotNull(newsNode.getPathSegment(ac));
	}

	@Test
	public void testGetFullSegmentPath() {
		Node newsNode = content("news overview");
		RoutingContext rc = getMockedRoutingContext("?version=draft");
		InternalActionContext ac = InternalActionContext.create(rc);
		System.out.println(newsNode.getPath(ac));
	}

	@Test
	public void testTaggingOfMeshNode() {
		Node newsNode = content("news overview");
		assertNotNull(newsNode);

		Tag carTag = tag("car");
		assertNotNull(carTag);

		newsNode.addTag(carTag, project().getLatestRelease());

		assertEquals(1, newsNode.getTags(project().getLatestRelease()).size());
		Tag firstTag = newsNode.getTags(project().getLatestRelease()).iterator().next();
		assertEquals(carTag.getUuid(), firstTag.getUuid());
	}

	@Test
	@Override
	public void testFindAll() throws InvalidArgumentException {
		RoutingContext rc = getMockedRoutingContext("version=draft");
		InternalActionContext ac = InternalActionContext.create(rc);
		PageImpl<? extends Node> page = boot.nodeRoot().findAll(ac, new PagingParameter(1, 10));

		assertEquals(getNodeCount(), page.getTotalElements());
		assertEquals(10, page.getSize());

		page = boot.nodeRoot().findAll(ac, new PagingParameter(1, 15));
		assertEquals(getNodeCount(), page.getTotalElements());
		assertEquals(15, page.getSize());
	}

	@Test
	public void testMeshNodeFields() throws IOException {
		Node newsNode = content("news overview");
		Language german = german();
		RoutingContext rc = getMockedRoutingContext("lang=de,en&version=draft");
		InternalActionContext ac = InternalActionContext.create(rc);
		NodeGraphFieldContainer germanFields = newsNode.getGraphFieldContainer(german);
		assertEquals(germanFields.getString(newsNode.getSchemaContainer().getLatestVersion().getSchema().getDisplayField()).getString(),
				newsNode.getDisplayName(ac));
		// TODO add some fields
	}

	@Test
	@Override
	public void testFindAllVisible() throws InvalidArgumentException {
		List<String> languageTags = new ArrayList<>();
		languageTags.add("de");
		languageTags.add("en");
		PageImpl<? extends Node> page = boot.nodeRoot().findAll(getRequestUser(), languageTags, new PagingParameter(1, 25));
		assertNotNull(page);
	}

	@Test
	@Override
	public void testRootNode() {
		Project project = project();
		Node root = project.getBaseNode();
		assertNotNull(root);
	}

	@Test
	@Override
	@Ignore("nodes can not be located using the name")
	public void testFindByName() {

	}

	@Test
	@Override
	public void testFindByUUID() throws Exception {
		Node newsNode = content("news overview");
		Node node = boot.nodeRoot().findByUuid(newsNode.getUuid()).toBlocking().first();
		assertNotNull(node);
		assertEquals(newsNode.getUuid(), node.getUuid());
	}

	@Test
	@Override
	public void testTransformation() throws Exception {
		RoutingContext rc = getMockedRoutingContext("lang=en&version=draft");
		InternalActionContext ac = InternalActionContext.create(rc);
		Node newsNode = content("concorde");

		NodeResponse response = newsNode.transformToRest(ac, 0).toBlocking().first();
		String json = JsonUtil.toJson(response);
		assertNotNull(json);

		NodeResponse deserialized = JsonUtil.readValue(json, NodeResponse.class);
		assertNotNull(deserialized);

		assertThat(deserialized).as("node response").hasVersion("0.1");

		assertThat(deserialized.getCreator()).as("Creator").isNotNull();
		assertThat(deserialized.getCreated()).as("Created").isNotEqualTo(0);
		assertThat(deserialized.getEditor()).as("Editor").isNotNull();
		assertThat(deserialized.getEdited()).as("Edited").isNotEqualTo(0);

		// TODO assert for english fields
	}

	@Test
	@Override
	public void testCreateDelete() {
		Node folder = folder("2015");
		Node subNode = folder.create(user(), getSchemaContainer().getLatestVersion(), project());
		assertNotNull(subNode.getUuid());
		SearchQueueBatch batch = createBatch();
		subNode.delete(batch);
	}

	@Test
	@Override
	public void testCRUDPermissions() {
		Node node = folder("2015").create(user(), getSchemaContainer().getLatestVersion(), project());
		InternalActionContext ac = getMockedInternalActionContext("");
		assertFalse(user().hasPermissionAsync(ac, node, GraphPermission.CREATE_PERM).toBlocking().first());
		user().addCRUDPermissionOnRole(folder("2015"), GraphPermission.CREATE_PERM, node);
		ac.data().clear();
		assertTrue(user().hasPermissionAsync(ac, node, GraphPermission.CREATE_PERM).toBlocking().first());
	}

	@Test
	@Override
	public void testRead() throws IOException {
		Node node = folder("2015");
		assertEquals("folder", node.getSchemaContainer().getLatestVersion().getSchema().getName());
		assertTrue(node.getSchemaContainer().getLatestVersion().getSchema().isContainer());
		NodeGraphFieldContainer englishVersion = node.getGraphFieldContainer("en");
	}

	@Test
	@Override
	public void testCreate() {
		User user = user();
		Node parentNode = folder("2015");
		SchemaContainerVersion schemaVersion = schemaContainer("content").getLatestVersion();
		Node node = parentNode.create(user, schemaVersion, project());
		long ts = System.currentTimeMillis();
		node.setCreationTimestamp(ts);
		Long creationTimeStamp = node.getCreationTimestamp();
		assertNotNull(creationTimeStamp);
		assertEquals(ts, creationTimeStamp.longValue());
		assertEquals(user, node.getCreator());
		Language english = english();
		Language german = german();

		NodeGraphFieldContainer englishContainer = node.createGraphFieldContainer(english, node.getProject().getLatestRelease(), user);
		englishContainer.createString("content").setString("english content");
		englishContainer.createString("name").setString("english.html");
		assertNotNull(node.getUuid());
		assertEquals(user, englishContainer.getEditor());
		assertNotNull(englishContainer.getLastEditedTimestamp());

		List<? extends GraphFieldContainer> allProperties = node.getGraphFieldContainers();
		assertNotNull(allProperties);
		assertEquals(1, allProperties.size());

		NodeGraphFieldContainer germanContainer = node.createGraphFieldContainer(german, node.getProject().getLatestRelease(), user);
		germanContainer.createString("content").setString("german content");
		assertEquals(2, node.getGraphFieldContainers().size());

		NodeGraphFieldContainer container = node.getGraphFieldContainer(english);
		assertNotNull(container);
		String text = container.getString("content").getString();
		assertNotNull(text);
		assertEquals("english content", text);

	}

	@Test
	@Override
	public void testDelete() throws Exception {
		Map<String, ElementEntry> affectedElements = new HashMap<>();
		String uuid;
		Node node = folder("news");

		// Add subfolders
		affectedElements.put("folder: news", new ElementEntry(DELETE_ACTION, node.getUuid(), "en", "de"));
		affectedElements.put("folder: news.2015", new ElementEntry(DELETE_ACTION, folder("2015").getUuid(), "en"));
		affectedElements.put("folder: news 2014", new ElementEntry(DELETE_ACTION, folder("2014").getUuid(), "en"));
		affectedElements.put("folder: news.2014.march", new ElementEntry(DELETE_ACTION, folder("march").getUuid(), "en", "de"));

		// Add Contents
		affectedElements.put("content: news.2014.news_2014", new ElementEntry(DELETE_ACTION, content("news_2014").getUuid(), "en", "de"));
		affectedElements.put("content: news.overview", new ElementEntry(DELETE_ACTION, content("news overview").getUuid(), "en", "de"));
		affectedElements.put("content: news.2014.march.news_in_march",
				new ElementEntry(DELETE_ACTION, content("new_in_march_2014").getUuid(), "en", "de"));
		affectedElements.put("content: news.2014.special_news", new ElementEntry(DELETE_ACTION, content("special news_2014").getUuid(), "en", "de"));
		affectedElements.put("content: news.2015.news_2015", new ElementEntry(DELETE_ACTION, content("news_2015").getUuid(), "en", "de"));

		uuid = node.getUuid();
		MeshAssert.assertElement(meshRoot().getNodeRoot(), uuid, true);
		SearchQueueBatch batch = createBatch();
		try (Trx tx = db.trx()) {
			node.delete(batch);
			tx.success();
		}

		MeshAssert.assertElement(meshRoot().getNodeRoot(), uuid, false);
		batch.reload();
		assertAffectedElements(affectedElements, batch);
	}

	@Test
	@Override
	public void testUpdate() {
		Node node = content();
		try (Trx tx = db.trx()) {
			User newUser = meshRoot().getUserRoot().create("newUser", user());
			newUser.addGroup(group());
			assertEquals(user().getUuid(), node.getCreator().getUuid());
			System.out.println(newUser.getUuid());
			node.setCreator(newUser);
			System.out.println(node.getCreator().getUuid());

			assertEquals(newUser.getUuid(), node.getCreator().getUuid());
			// TODO update other fields
		}
	}

	@Test
	@Override
	public void testReadPermission() {
		testPermission(GraphPermission.READ_PERM, content());
	}

	@Test
	@Override
	public void testDeletePermission() {
		testPermission(GraphPermission.DELETE_PERM, content());
	}

	@Test
	@Override
	public void testUpdatePermission() {
		testPermission(GraphPermission.UPDATE_PERM, content());
	}

	@Test
	@Override
	public void testCreatePermission() {
		testPermission(GraphPermission.CREATE_PERM, content());
	}

	@Test
	public void testDeleteWithChildren() {
		Project project = project();
		Release initialRelease = project.getInitialRelease();
		SchemaContainerVersion folderSchema = schemaContainer("folder").getLatestVersion();

		// 1. create folder with subfolder and subsubfolder
		Node folder = project.getBaseNode().create(user(), folderSchema, project);
		folder.createGraphFieldContainer(english(), initialRelease, user()).createString("name").setString("Folder");
		String folderUuid = folder.getUuid();
		Node subFolder = folder.create(user(), folderSchema, project);
		subFolder.createGraphFieldContainer(english(), initialRelease, user()).createString("name").setString("SubFolder");
		String subFolderUuid = subFolder.getUuid();
		Node subSubFolder = subFolder.create(user(), folderSchema, project);
		subSubFolder.createGraphFieldContainer(english(), initialRelease, user()).createString("name").setString("SubSubFolder");
		String subSubFolderUuid = subSubFolder.getUuid();

		// 2. delete folder for initial release
		subFolder.deleteFromRelease(initialRelease, createBatch());
		folder.reload();

		// 3. assert for new release
		assertThat(folder).as("folder").hasNoChildren(initialRelease);

		// 4. assert for initial release
		List<String> nodeUuids = new ArrayList<>();
		project.getNodeRoot().findAll().forEach(node -> nodeUuids.add(node.getUuid()));
		assertThat(nodeUuids).as("All nodes").contains(folderUuid).doesNotContain(subFolderUuid, subSubFolderUuid);
	}

	@Test
	public void testDeleteWithChildrenInRelease() throws InvalidArgumentException {
		Project project = project();
		Release initialRelease = project.getInitialRelease();
		SchemaContainerVersion folderSchema = schemaContainer("folder").getLatestVersion();

		// 1. create folder with subfolder and subsubfolder
		Node folder = project.getBaseNode().create(user(), folderSchema, project);
		folder.createGraphFieldContainer(english(), initialRelease, user()).createString("name").setString("Folder");
		Node subFolder = folder.create(user(), folderSchema, project);
		subFolder.createGraphFieldContainer(english(), initialRelease, user()).createString("name").setString("SubFolder");
		Node subSubFolder = subFolder.create(user(), folderSchema, project);
		subSubFolder.createGraphFieldContainer(english(), initialRelease, user()).createString("name").setString("SubSubFolder");

		// 2. create a new release
		Release newRelease = project.getReleaseRoot().create("newrelease", user());

		// 3. migrate nodes
		nodeMigrationHandler.migrateNodes(newRelease).toBlocking().single();
		folder.reload();
		subFolder.reload();
		subSubFolder.reload();

		// 4. assert nodes in new release
		assertThat(folder).as("folder").hasOnlyChildren(newRelease, subFolder);
		assertThat(subFolder).as("subFolder").hasOnlyChildren(newRelease, subSubFolder);
		assertThat(subSubFolder).as("subSubFolder").hasNoChildren(newRelease);

		// 5. reverse folders in new release
		subSubFolder.moveTo(getMockedInternalActionContext(""), folder);
		folder.reload();
		subFolder.reload();
		subSubFolder.reload();
		subFolder.moveTo(getMockedInternalActionContext(""), subSubFolder);
		folder.reload();
		subFolder.reload();
		subSubFolder.reload();

		// 6. assert for new release
		assertThat(folder).as("folder").hasChildren(newRelease, subSubFolder);
		assertThat(subSubFolder).as("subSubFolder").hasChildren(newRelease, subFolder);
		assertThat(subFolder).as("subFolder").hasNoChildren(newRelease);

		// 7. assert for initial release
		assertThat(folder).as("folder").hasChildren(initialRelease, subFolder);
		assertThat(subFolder).as("subFolder").hasChildren(initialRelease, subSubFolder);
		assertThat(subSubFolder).as("subSubFolder").hasNoChildren(initialRelease);

		// 8. delete folder for initial release
		subFolder.deleteFromRelease(initialRelease, createBatch());
		folder.reload();
		subFolder.reload();
		subSubFolder.reload();

		// 9. assert for new release
		assertThat(folder).as("folder").hasChildren(newRelease, subSubFolder);
		assertThat(subSubFolder).as("subSubFolder").hasChildren(newRelease, subFolder);
		assertThat(subFolder).as("subFolder").hasNoChildren(newRelease);

		// 10. assert for initial release
		List<Node> nodes = new ArrayList<>();
		project.getNodeRoot().findAll(getMockedInternalActionContext("release=" + initialRelease.getName()),
				new PagingParameter(1, 10000, "name", SortOrder.ASCENDING)).forEach(node -> nodes.add(node));
		assertThat(nodes).as("Nodes in initial release").usingElementComparatorOnFields("uuid")
				.doesNotContain(subFolder, subSubFolder);
		assertThat(folder).as("folder").hasNoChildren(initialRelease);
	}
}
