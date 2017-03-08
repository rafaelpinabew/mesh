package com.gentics.mesh.core.data.impl;

import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_CREATOR;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_EDITOR;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_ROLE;
import static com.gentics.mesh.core.rest.error.Errors.conflict;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.cache.PermissionStore;
import com.gentics.mesh.core.data.Group;
import com.gentics.mesh.core.data.HandleElementAction;
import com.gentics.mesh.core.data.MeshVertex;
import com.gentics.mesh.core.data.Role;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.generic.AbstractMeshCoreVertex;
import com.gentics.mesh.core.data.generic.MeshVertexImpl;
import com.gentics.mesh.core.data.page.Page;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.rest.role.RoleReference;
import com.gentics.mesh.core.rest.role.RoleResponse;
import com.gentics.mesh.core.rest.role.RoleUpdateRequest;
import com.gentics.mesh.dagger.MeshInternal;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.util.ETag;
import com.gentics.mesh.util.TraversalHelper;
import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.traversals.VertexTraversal;
import com.tinkerpop.blueprints.Edge;

import rx.Single;

/**
 * @see Role
 */
public class RoleImpl extends AbstractMeshCoreVertex<RoleResponse, Role> implements Role {

	public static void init(Database database) {
		database.addVertexType(RoleImpl.class, MeshVertexImpl.class);
		database.addVertexIndex(RoleImpl.class, true, "name");
	}

	@Override
	public RoleReference transformToReference() {
		return new RoleReference().setName(getName()).setUuid(getUuid());
	}

	@Override
	public String getName() {
		return getProperty("name");
	}

	@Override
	public void setName(String name) {
		setProperty("name", name);
	}

	@Override
	public List<? extends Group> getGroups() {
		return out(HAS_ROLE).toListExplicit(GroupImpl.class);
	}

	@Override
	public Page<? extends Group> getGroups(InternalActionContext ac) {
		VertexTraversal<?, ?, ?> traversal = out(HAS_ROLE).filter(group -> {
			return ac.getUser().hasPermissionForId(group.getId(), GraphPermission.READ_PERM);
		});
		return TraversalHelper.getPagedResult(traversal, ac.getPagingParameters(), GroupImpl.class);
	}

	@Override
	public Set<GraphPermission> getPermissions(MeshVertex vertex) {
		Set<GraphPermission> permissions = new HashSet<>();
		for (GraphPermission permission : GraphPermission.values()) {
			if (hasPermission(permission, vertex)) {
				permissions.add(permission);
			}
		}
		return permissions;
	}

	@Override
	public boolean hasPermission(GraphPermission permission, MeshVertex vertex) {
		FramedGraph graph = Database.getThreadLocalGraph();
		Iterable<Edge> edges = graph.getEdges("e." + permission.label() + "_inout",
				MeshInternal.get().database().createComposedIndexKey(vertex.getId(), getId()));
		return edges.iterator().hasNext();
	}

	@Override
	public void grantPermissions(MeshVertex vertex, GraphPermission... permissions) {
		for (GraphPermission permission : permissions) {
			if (!hasPermission(permission, vertex)) {
				addFramedEdge(permission.label(), vertex);
			}
		}
	}

	@Override
	public RoleResponse transformToRestSync(InternalActionContext ac, int level, String... languageTags) {
		RoleResponse restRole = new RoleResponse();
		restRole.setName(getName());

		setGroups(ac, restRole);
		fillCommonRestFields(ac, restRole);
		setRolePermissions(ac, restRole);

		return restRole;
	}

	private void setGroups(InternalActionContext ac, RoleResponse restRole) {
		for (Group group : getGroups()) {
			restRole.getGroups().add(group.transformToReference());
		}
	}

	@Override
	public void revokePermissions(MeshVertex node, GraphPermission... permissions) {
		for (GraphPermission permission : permissions) {
			outE(permission.label()).mark().inV().retain((MeshVertexImpl) node).back().removeAll();
		}
		PermissionStore.invalidate();
	}

	@Override
	public void delete(SearchQueueBatch batch) {
		// TODO don't allow deletion of admin role
		batch.delete(this, true);
		getVertex().remove();
		PermissionStore.invalidate();
	}

	@Override
	public Role update(InternalActionContext ac, SearchQueueBatch batch) {
		RoleUpdateRequest requestModel = ac.fromJson(RoleUpdateRequest.class);
		BootstrapInitializer boot = MeshInternal.get().boot();
		if (shouldUpdate(requestModel.getName(), getName())) {
			// Check for conflict
			Role roleWithSameName = boot.roleRoot().findByName(requestModel.getName());
			if (roleWithSameName != null && !roleWithSameName.getUuid().equals(getUuid())) {
				throw conflict(roleWithSameName.getUuid(), requestModel.getName(), "role_conflicting_name");
			}

			setName(requestModel.getName());
			batch.store(this, true);
		}
		return this;
	}

	@Override
	public void handleRelatedEntries(HandleElementAction action) {
		for (Group group : getGroups()) {
			action.call(group, null);
		}
	}

	@Override
	public String getETag(InternalActionContext ac) {
		return ETag.hash(getUuid() + "-" + getLastEditedTimestamp());
	}

	@Override
	public String getAPIPath(InternalActionContext ac) {
		return "/api/v1/roles/" + getUuid();
	}

	@Override
	public User getCreator() {
		return out(HAS_CREATOR).nextOrDefault(UserImpl.class, null);
	}

	@Override
	public User getEditor() {
		return out(HAS_EDITOR).nextOrDefaultExplicit(UserImpl.class, null);
	}

	@Override
	public Single<RoleResponse> transformToRest(InternalActionContext ac, int level, String... languageTags) {
		return db.operateNoTx(() -> {
			return Single.just(transformToRestSync(ac, level, languageTags));
		});
	}

}
