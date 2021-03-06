package com.gentics.mesh.parameter;

/**
 * The role permission parameter can be used to set the role parameter value in form of an UUID which will cause mesh to add the rolePerm field to the rest
 * response.
 */
public interface RolePermissionParameters extends ParameterProvider {

	public static final String ROLE_PERMISSION_QUERY_PARAM_KEY = "role";

	/**
	 * Set the role UUID.
	 * 
	 * @param roleUuid
	 * @return Fluent API
	 */
	default RolePermissionParameters setRoleUuid(String roleUuid) {
		setParameter(ROLE_PERMISSION_QUERY_PARAM_KEY, roleUuid);
		return this;
	}

	/**
	 * Return the role UUID.
	 * 
	 * @return
	 */
	default String getRoleUuid() {
		return getParameter(ROLE_PERMISSION_QUERY_PARAM_KEY);
	}

}
