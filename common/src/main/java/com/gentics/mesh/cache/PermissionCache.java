package com.gentics.mesh.cache;

import com.gentics.mesh.core.data.relationship.GraphPermission;

public interface PermissionCache extends MeshCache<String, Boolean> {

	/**
	 * Clear the local cache and send an event to inform other instances to also clear their caches.
	 * 
	 * @param notify
	 */
	void clear(boolean notify);

	/**
	 * Check whether the element with the given id has the permission.
	 * 
	 * @param userId
	 *            User id
	 * @param permission
	 * @param elementId
	 * @return
	 */
	boolean hasPermission(Object userId, GraphPermission permission, Object elementId);

	/**
	 * Store the granting permission in the perm store.
	 * 
	 * @param userId
	 *            User id
	 * @param permission
	 * @param elementId
	 */
	void store(Object userId, GraphPermission permission, Object elementId);

}
