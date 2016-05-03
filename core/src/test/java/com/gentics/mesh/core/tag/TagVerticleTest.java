package com.gentics.mesh.core.tag;

import static com.gentics.mesh.assertj.MeshAssertions.assertThat;
import static com.gentics.mesh.core.data.relationship.GraphPermission.DELETE_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.UPDATE_PERM;
import static com.gentics.mesh.demo.TestDataProvider.PROJECT_NAME;
import static com.gentics.mesh.util.MeshAssert.assertSuccess;
import static com.gentics.mesh.util.MeshAssert.latchFor;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.Collectors;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.mesh.core.AbstractSpringVerticle;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.Tag;
import com.gentics.mesh.core.data.TagFamily;
import com.gentics.mesh.core.rest.common.GenericMessageResponse;
import com.gentics.mesh.core.rest.common.ListResponse;
import com.gentics.mesh.core.rest.error.HttpStatusCodeErrorException;
import com.gentics.mesh.core.rest.tag.TagCreateRequest;
import com.gentics.mesh.core.rest.tag.TagFieldContainer;
import com.gentics.mesh.core.rest.tag.TagListResponse;
import com.gentics.mesh.core.rest.tag.TagResponse;
import com.gentics.mesh.core.rest.tag.TagUpdateRequest;
import com.gentics.mesh.core.verticle.tagfamily.TagFamilyVerticle;
import com.gentics.mesh.query.impl.PagingParameter;
import com.gentics.mesh.query.impl.RolePermissionParameter;
import com.gentics.mesh.rest.MeshRestClientHttpException;
import com.gentics.mesh.test.AbstractBasicCrudVerticleTest;

import io.vertx.core.Future;

public class TagVerticleTest extends AbstractBasicCrudVerticleTest {

	@Autowired
	private TagFamilyVerticle verticle;

	@Override
	public List<AbstractSpringVerticle> getAdditionalVertices() {
		List<AbstractSpringVerticle> list = new ArrayList<>();
		list.add(verticle);
		return list;
	}

	@Test
	@Override
	public void testReadMultiple() throws Exception {

		final int nBasicTags = 9;
		// Don't grant permissions to the no perm tag. We want to make sure that this one will not be listed.
		TagFamily basicTagFamily = tagFamily("basic");
		Tag noPermTag = basicTagFamily.create("noPermTag", project(), user());
		String noPermTagUUID = noPermTag.getUuid();
		// TODO check whether the project reference should be moved from generic class into node mesh class and thus not be available for tags
		basicTagFamily.getTagRoot().addTag(noPermTag);
		assertNotNull(noPermTag.getUuid());

		// Test default paging parameters
		Future<TagListResponse> future = getClient().findTags(PROJECT_NAME, basicTagFamily.getUuid());
		latchFor(future);
		assertSuccess(future);

		ListResponse<TagResponse> restResponse = future.result();
		assertEquals(25, restResponse.getMetainfo().getPerPage());
		assertEquals(1, restResponse.getMetainfo().getCurrentPage());
		assertEquals("The response did not contain the correct amount of items. We only have nine basic tags in the test data.", nBasicTags,
				restResponse.getData().size());

		int perPage = 4;
		// Extra Tags + permitted tag
		int totalTags = nBasicTags;
		int totalPages = (int) Math.ceil(totalTags / (double) perPage);
		List<TagResponse> allTags = new ArrayList<>();
		for (int page = 1; page <= totalPages; page++) {
			Future<TagListResponse> tagPageFut = getClient().findTags(PROJECT_NAME, basicTagFamily.getUuid(), new PagingParameter(page, perPage));
			latchFor(tagPageFut);
			assertSuccess(future);
			restResponse = tagPageFut.result();
			int expectedItemsCount = perPage;
			// The last page should only list 5 items
			if (page == 3) {
				expectedItemsCount = 1;
			}
			assertEquals("The expected item count for page {" + page + "} does not match", expectedItemsCount, restResponse.getData().size());
			assertEquals(perPage, restResponse.getMetainfo().getPerPage());
			assertEquals("We requested page {" + page + "} but got a metainfo with a different page back.", page,
					restResponse.getMetainfo().getCurrentPage());
			assertEquals("The amount of total pages did not match the expected value. There are {" + totalTags + "} tags and {" + perPage
					+ "} tags per page", totalPages, restResponse.getMetainfo().getPageCount());
			assertEquals("The total tag count does not match.", totalTags, restResponse.getMetainfo().getTotalCount());

			allTags.addAll(restResponse.getData());
		}
		assertEquals("Somehow not all users were loaded when loading all pages.", totalTags, allTags.size());

		// Verify that the no_perm_tag is not part of the response
		List<TagResponse> filteredUserList = allTags.parallelStream().filter(restTag -> restTag.getUuid().equals(noPermTagUUID))
				.collect(Collectors.toList());
		assertTrue("The no perm tag should not be part of the list since no permissions were added.", filteredUserList.size() == 0);

		Future<TagListResponse> pageFuture = getClient().findTags(PROJECT_NAME, basicTagFamily.getUuid(), new PagingParameter(-1, perPage));
		latchFor(pageFuture);
		expectException(pageFuture, BAD_REQUEST, "error_page_parameter_must_be_positive", "-1");

		pageFuture = getClient().findTags(PROJECT_NAME, basicTagFamily.getUuid(), new PagingParameter(0, perPage));
		latchFor(pageFuture);
		expectException(pageFuture, BAD_REQUEST, "error_page_parameter_must_be_positive", "0");

		pageFuture = getClient().findTags(PROJECT_NAME, basicTagFamily.getUuid(), new PagingParameter(1, -1));
		latchFor(pageFuture);
		expectException(pageFuture, BAD_REQUEST, "error_pagesize_parameter", "-1");

		perPage = 25;
		totalPages = (int) Math.ceil(totalTags / (double) perPage);
		pageFuture = getClient().findTags(PROJECT_NAME, basicTagFamily.getUuid(), new PagingParameter(4242, perPage));
		latchFor(pageFuture);
		TagListResponse tagList = pageFuture.result();
		assertEquals(0, tagList.getData().size());
		assertEquals(4242, tagList.getMetainfo().getCurrentPage());
		assertEquals(25, tagList.getMetainfo().getPerPage());
		assertEquals(nBasicTags, tagList.getMetainfo().getTotalCount());
		assertEquals(totalPages, tagList.getMetainfo().getPageCount());
	}

	@Test
	public void testReadMetaCountOnly() {
		TagFamily parentTagFamily = tagFamily("colors");

		Future<TagListResponse> pageFuture = getClient().findTags(PROJECT_NAME, parentTagFamily.getUuid(), new PagingParameter(1, 0));
		latchFor(pageFuture);
		assertSuccess(pageFuture);
		assertEquals(0, pageFuture.result().getData().size());
	}

	@Test
	public void testReadByUUID() throws Exception {
		Tag tag = tag("red");
		TagFamily parentTagFamily = tagFamily("colors");

		assertNotNull("The UUID of the tag must not be null.", tag.getUuid());
		Future<TagResponse> future = getClient().findTagByUuid(PROJECT_NAME, parentTagFamily.getUuid(), tag.getUuid());
		latchFor(future);
		assertSuccess(future);
		assertThat(future.result()).matches(tag);
	}

	@Test
	public void testReadByUuidWithRolePerms() {
		Tag tag = tag("red");
		String uuid = tag.getUuid();
		TagFamily parentTagFamily = tagFamily("colors");

		TagResponse response = call(() -> getClient().findTagByUuid(PROJECT_NAME, parentTagFamily.getUuid(), uuid,
				new RolePermissionParameter().setRoleUuid(role().getUuid())));
		assertThat(response.getRolePerms()).as("Role perms").isNotNull().contains("create", "read", "update", "delete");
	}

	@Test
	public void testReadTagByUUIDWithoutPerm() throws Exception {
		TagFamily parentTagFamily = tagFamily("basic");
		Tag tag = tag("vehicle");
		String uuid = tag.getUuid();
		assertNotNull("The UUID of the tag must not be null.", tag.getUuid());
		role().revokePermissions(tag, READ_PERM);

		Future<TagResponse> future = getClient().findTagByUuid(PROJECT_NAME, parentTagFamily.getUuid(), uuid);
		latchFor(future);
		expectException(future, FORBIDDEN, "error_missing_perm", uuid);
	}

	@Test
	@Override
	public void testUpdate() throws Exception {
		Tag tag = tag("vehicle");
		TagFamily parentTagFamily = tagFamily("basic");

		String tagUuid = tag.getUuid();
		String tagName = tag.getName();
		assertNotNull(tag.getEditor());
		Future<TagResponse> readTagFut = getClient().findTagByUuid(PROJECT_NAME, parentTagFamily.getUuid(), tagUuid);
		latchFor(readTagFut);
		assertSuccess(readTagFut);

		// 1. Read the current tag
		assertNotNull("The name of the tag should be loaded.", tagName);
		String restName = readTagFut.result().getFields().getName();
		assertNotNull("The tag name must be set.", restName);
		assertEquals(tagName, restName);

		// 2. Update the tag
		TagUpdateRequest request = new TagUpdateRequest();
		request.getFields().setName("new Name");
		TagUpdateRequest tagUpdateRequest = new TagUpdateRequest();
		final String newName = "new Name";
		tagUpdateRequest.getFields().setName(newName);
		assertEquals(newName, tagUpdateRequest.getFields().getName());

		// 3. Send the request to the server
		Future<TagResponse> updatedTagFut = getClient().updateTag(PROJECT_NAME, parentTagFamily.getUuid(), tagUuid, tagUpdateRequest);
		latchFor(updatedTagFut);
		assertSuccess(updatedTagFut);
		TagResponse tag2 = updatedTagFut.result();
		assertThat(tag2).matches(tag);

		// 4. read the tag again and verify that it was changed
		Future<TagResponse> reloadedTagFut = getClient().findTagByUuid(PROJECT_NAME, parentTagFamily.getUuid(), tagUuid);
		latchFor(reloadedTagFut);
		assertSuccess(reloadedTagFut);
		TagResponse reloadedTag = reloadedTagFut.result();
		assertEquals(request.getFields().getName(), reloadedTag.getFields().getName());

		assertThat(reloadedTag).matches(tag);
	}

	@Test
	public void testUpdateTagWithConflictingName() {
		Tag tag = tag("red");
		TagFamily parentTagFamily = tagFamily("colors");

		String uuid = tag.getUuid();
		String tagFamilyName = tag.getTagFamily().getName();

		final String newName = "green";
		TagUpdateRequest request = new TagUpdateRequest();
		request.getFields().setName(newName);
		TagUpdateRequest tagUpdateRequest = new TagUpdateRequest();
		tagUpdateRequest.getFields().setName(newName);
		assertEquals(newName, tagUpdateRequest.getFields().getName());

		Future<TagResponse> updatedTagFut = getClient().updateTag(PROJECT_NAME, parentTagFamily.getUuid(), uuid, tagUpdateRequest);
		latchFor(updatedTagFut);
		expectException(updatedTagFut, CONFLICT, "tag_create_tag_with_same_name_already_exists", newName, tagFamilyName);
	}

	@Test
	public void testUpdateTagWithNoName() {
		Tag tag = tag("red");
		TagFamily parentTagFamily = tagFamily("colors");

		String uuid = tag.getUuid();
		TagUpdateRequest tagUpdateRequest = new TagUpdateRequest();

		Future<TagResponse> updatedTagFut = getClient().updateTag(PROJECT_NAME, parentTagFamily.getUuid(), uuid, tagUpdateRequest);
		latchFor(updatedTagFut);
		expectException(updatedTagFut, BAD_REQUEST, "tag_name_not_set");
	}

	@Test
	@Override
	public void testUpdateByUUIDWithoutPerm() throws Exception {

		Tag tag = tag("vehicle");
		TagFamily parentTagFamily = tagFamily("basic");

		String tagName = tag.getName();
		String tagUuid = tag.getUuid();
		role().revokePermissions(tag, UPDATE_PERM);

		// Create an tag update request
		TagUpdateRequest request = new TagUpdateRequest();
		request.getFields().setName("new Name");

		Future<TagResponse> tagUpdateFut = getClient().updateTag(PROJECT_NAME, parentTagFamily.getUuid(), tagUuid, request);
		latchFor(tagUpdateFut);
		expectException(tagUpdateFut, FORBIDDEN, "error_missing_perm", tagUuid);

		// read the tag again and verify that it was not changed
		Future<TagResponse> tagReloadFut = getClient().findTagByUuid(PROJECT_NAME, parentTagFamily.getUuid(), tagUuid);
		latchFor(tagReloadFut);
		assertTrue(tagReloadFut.succeeded());
		TagResponse loadedTag = tagReloadFut.result();
		assertEquals(tagName, loadedTag.getFields().getName());
	}

	// Delete Tests
	@Test
	@Override
	public void testDeleteByUUID() throws Exception {
		Tag tag = tag("vehicle");
		TagFamily parentTagFamily = tagFamily("basic");

		String name = tag.getName();
		String uuid = tag.getUuid();

		Future<GenericMessageResponse> future = getClient().deleteTag(PROJECT_NAME, parentTagFamily.getUuid(), uuid);
		latchFor(future);
		assertSuccess(future);
		expectResponseMessage(future, "tag_deleted", uuid + "/" + name);

		tag = boot.tagRoot().findByUuid(uuid).toBlocking().single();
		assertNull("The tag should have been deleted", tag);

		Project project = boot.projectRoot().findByName(PROJECT_NAME).toBlocking().single();
		assertNotNull(project);
	}

	@Test
	@Override
	public void testDeleteByUUIDWithNoPermission() throws Exception {
		TagFamily parentTagFamily = tagFamily("basic");
		Tag tag = tag("vehicle");
		String uuid = tag.getUuid();
		role().revokePermissions(tag, DELETE_PERM);

		Future<GenericMessageResponse> messageFut = getClient().deleteTag(PROJECT_NAME, parentTagFamily.getUuid(), uuid);
		latchFor(messageFut);
		expectException(messageFut, FORBIDDEN, "error_missing_perm", uuid);

		tag = boot.tagRoot().findByUuid(uuid).toBlocking().first();
		assertNotNull("The tag should not have been deleted", tag);
	}

	@Test
	public void testCreateConflictingName() {
		TagFamily tagFamily = tagFamily("colors");

		TagCreateRequest tagCreateRequest = new TagCreateRequest();
		tagCreateRequest.getFields().setName("red");
		//tagCreateRequest.setTagFamily(new TagFamilyReference().setName(tagFamily.getName()).setUuid(tagFamily.getUuid()));

		Future<TagResponse> future = getClient().createTag(PROJECT_NAME, tagFamily.getUuid(), tagCreateRequest);
		latchFor(future);
		expectException(future, CONFLICT, "tag_create_tag_with_same_name_already_exists", "red", "colors");
		MeshRestClientHttpException exception = ((MeshRestClientHttpException) future.cause());
		assertNotNull(exception.getResponseMessage().getProperties());
		assertNotNull(exception.getResponseMessage().getProperties().get("conflictingUuid"));

	}

	@Test
	@Override
	public void testCreate() {
		TagCreateRequest tagCreateRequest = new TagCreateRequest();
		TagFamily parentTagFamily = tagFamily("colors");

		tagCreateRequest.getFields().setName("SomeName");
		TagFamily tagFamily = tagFamilies().get("colors");
		//tagCreateRequest.setTagFamily(new TagFamilyReference().setName(tagFamily.getName()).setUuid(tagFamily.getUuid()));

		Future<TagResponse> future = getClient().createTag(PROJECT_NAME, parentTagFamily.getUuid(), tagCreateRequest);
		latchFor(future);
		assertSuccess(future);
		assertEquals("SomeName", future.result().getFields().getName());

		assertNotNull("The tag could not be found within the meshRoot.tagRoot node.",
				meshRoot().getTagRoot().findByUuid(future.result().getUuid()).toBlocking().first());
		assertNotNull("The tag could not be found within the project.tagRoot node.",
				project().getTagRoot().findByUuid(future.result().getUuid()).toBlocking().first());

		future = getClient().findTagByUuid(PROJECT_NAME, parentTagFamily.getUuid(), future.result().getUuid());
		latchFor(future);
		assertSuccess(future);
		assertEquals("SomeName", future.result().getFields().getName());

	}

	@Test
	public void testCreateTagWithSameNameInSameTagFamily() {
		TagFamily parentTagFamily = tagFamily("colors");

		TagCreateRequest tagCreateRequest = new TagCreateRequest();
		assertNotNull("We expect that a tag with the name already exists.", tag("red"));
		tagCreateRequest.getFields().setName("red");
		String tagFamilyName;

		TagFamily tagFamily = tagFamilies().get("colors");
		tagFamilyName = tagFamily.getName();
		//tagCreateRequest.setTagFamily(new TagFamilyReference().setName(tagFamily.getName()).setUuid(tagFamily.getUuid()));
		Future<TagResponse> future = getClient().createTag(PROJECT_NAME, parentTagFamily.getUuid(), tagCreateRequest);
		latchFor(future);
		expectException(future, CONFLICT, "tag_create_tag_with_same_name_already_exists", "red", tagFamilyName);
	}

	@Test
	@Override
	@Ignore("Not yet supported")
	public void testUpdateMultithreaded() throws Exception {
		int nJobs = 5;
		TagFamily parentTagFamily = tagFamily("colors");

		TagUpdateRequest request = new TagUpdateRequest();
		request.setFields(new TagFieldContainer().setName("newName"));
		String uuid = tag("red").getUuid();
		CyclicBarrier barrier = prepareBarrier(nJobs);
		Set<Future<?>> set = new HashSet<>();
		for (int i = 0; i < nJobs; i++) {
			set.add(getClient().updateTag(PROJECT_NAME, parentTagFamily.getUuid(), uuid, request));
		}
		validateSet(set, barrier);

	}

	@Test
	@Override
	public void testReadByUuidMultithreaded() throws Exception {
		int nJobs = 100;
		TagFamily parentTagFamily = tagFamily("colors");

		String uuid = tag("red").getUuid();
		CyclicBarrier barrier = prepareBarrier(nJobs);
		Set<Future<?>> set = new HashSet<>();
		for (int i = 0; i < nJobs; i++) {
			set.add(getClient().findTagByUuid(PROJECT_NAME, parentTagFamily.getUuid(), uuid));
		}
		validateSet(set, barrier);
	}

	@Test
	@Override
	@Ignore("Not yet supported")
	public void testDeleteByUUIDMultithreaded() throws Exception {
		int nJobs = 3;
		TagFamily parentTagFamily = tagFamily("colors");

		String uuid = tag("red").getUuid();
		CyclicBarrier barrier = prepareBarrier(nJobs);
		Set<Future<GenericMessageResponse>> set = new HashSet<>();
		for (int i = 0; i < nJobs; i++) {
			set.add(getClient().deleteTag(PROJECT_NAME, parentTagFamily.getUuid(), uuid));
		}
		validateDeletion(set, barrier);
	}

	@Test
	@Ignore("Disabled since test is unstable - CL-246")
	@Override
	public void testCreateMultithreaded() throws Exception {
		int nJobs = 200;
		TagFamily parentTagFamily = tagFamily("colors");

		Set<Future<?>> set = new HashSet<>();
		for (int i = 0; i < nJobs; i++) {
			TagCreateRequest request = new TagCreateRequest();
			request.getFields().setName("newcolor_" + i);
			//request.setTagFamily(new TagFamilyReference().setName("colors"));
			set.add(getClient().createTag(PROJECT_NAME, parentTagFamily.getUuid(), request));
		}
		validateCreation(set, null);
	}

	@Test
	@Override
	public void testReadByUuidMultithreadedNonBlocking() throws Exception {
		int nJobs = 200;
		TagFamily parentTagFamily = tagFamily("colors");

		Set<Future<TagResponse>> set = new HashSet<>();
		for (int i = 0; i < nJobs; i++) {
			set.add(getClient().findTagByUuid(PROJECT_NAME, parentTagFamily.getUuid(), tag("red").getUuid()));
		}
		for (Future<TagResponse> future : set) {
			latchFor(future);
			assertSuccess(future);
		}
	}

	@Test
	@Override
	public void testCreateReadDelete() throws Exception {
		TagCreateRequest tagCreateRequest = new TagCreateRequest();
		tagCreateRequest.getFields().setName("SomeName");
		TagFamily tagFamily = tagFamilies().get("colors");
		//		tagCreateRequest.setTagFamily(new TagFamilyReference().setName(tagFamily.getName()).setUuid(tagFamily.getUuid()));

		// Create
		Future<TagResponse> future = getClient().createTag(PROJECT_NAME, tagFamily.getUuid(), tagCreateRequest);
		latchFor(future);
		assertSuccess(future);
		assertEquals("SomeName", future.result().getFields().getName());

		// Read
		future = getClient().findTagByUuid(PROJECT_NAME, tagFamily.getUuid(), future.result().getUuid());
		latchFor(future);
		assertSuccess(future);
		assertEquals("SomeName", future.result().getFields().getName());

		// Delete
		Future<GenericMessageResponse> deleteFuture = getClient().deleteTag(PROJECT_NAME, tagFamily.getUuid(), future.result().getUuid());
		latchFor(deleteFuture);
		assertSuccess(deleteFuture);

	}

	@Test
	@Override
	public void testReadByUUIDWithMissingPermission() throws Exception {
		Tag tag = tag("red");
		TagFamily parentTagFamily = tagFamily("colors");

		assertNotNull("The UUID of the tag must not be null.", tag.getUuid());
		String uuid = tag.getUuid();
		role().revokePermissions(tag, READ_PERM);

		Future<TagResponse> future = getClient().findTagByUuid(PROJECT_NAME, parentTagFamily.getUuid(), uuid);
		latchFor(future);
		expectException(future, FORBIDDEN, "error_missing_perm", uuid);

	}

	@Test
	@Override
	public void testUpdateWithBogusUuid() throws HttpStatusCodeErrorException, Exception {
		TagUpdateRequest request = new TagUpdateRequest();
		request.setFields(new TagFieldContainer().setName("newName"));
		TagFamily parentTagFamily = tagFamily("colors");

		Future<TagResponse> future = getClient().updateTag(PROJECT_NAME, parentTagFamily.getUuid(), "bogus", request);
		latchFor(future);
		expectException(future, NOT_FOUND, "object_not_found_for_uuid", "bogus");

	}

}
