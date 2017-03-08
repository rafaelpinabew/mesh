package com.gentics.mesh.graphql.type;

import static graphql.Scalars.GraphQLLong;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.MeshAuthUser;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.page.Page;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.root.MeshRoot;
import com.gentics.mesh.core.data.service.WebRootService;
import com.gentics.mesh.path.Path;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;

@Singleton
public class RootTypeProvider extends AbstractTypeProvider {

	@Inject
	public NodeFieldTypeProvider nodeFieldProvider;

	@Inject
	public NodeTypeProvider nodeTypeProvider;

	@Inject
	public ProjectTypeProvider projectTypeProvider;

	@Inject
	public UserTypeProvider userFieldProvider;

	@Inject
	public TagTypeProvider tagTypeProvider;

	@Inject
	public TagFamilyTypeProvider tagFamilyTypeProvider;

	@Inject
	public RoleTypeProvider roleTypeProvider;

	@Inject
	public GroupTypeProvider groupTypeProvider;

	@Inject
	public WebRootService webrootService;

	@Inject
	public BootstrapInitializer boot;

	@Inject
	public ReleaseTypeProvider releaseTypeProvider;

	@Inject
	public SchemaTypeProvider schemaTypeProvider;

	@Inject
	public MicroschemaTypeProvider microschemaTypeProvider;

	@Inject
	public RootTypeProvider() {
	}

	public Object nodesFetcher(DataFetchingEnvironment env) {
		String uuid = env.getArgument("uuid");
		if (uuid != null) {
			InternalActionContext ac = (InternalActionContext) env.getContext();
			Node node = boot.nodeRoot()
					.findByUuid(uuid);
			// Check permissions
			if (ac.getUser()
					.hasPermission(node, GraphPermission.READ_PERM)
					|| ac.getUser()
							.hasPermission(node, GraphPermission.READ_PUBLISHED_PERM)) {
				return node;
			}
		}

		String path = env.getArgument("path");
		if (path != null) {
			InternalActionContext ac = (InternalActionContext) env.getContext();
			Path pathResult = webrootService.findByProjectPath(ac, path);
			return pathResult.getLast()
					.getNode();
		}
		return null;
	}

	public Object userMeFetcher(DataFetchingEnvironment env) {
		Object source = env.getSource();
		if (source instanceof InternalActionContext) {
			InternalActionContext ac = (InternalActionContext) source;
			MeshAuthUser requestUser = ac.getUser();
			return requestUser;
		}
		return null;
	}

	public Object usersFetcher(DataFetchingEnvironment env) {
		Object source = env.getSource();
		if (source instanceof InternalActionContext) {
			InternalActionContext ac = (InternalActionContext) source;
			MeshRoot meshRoot = boot.meshRoot();
			return meshRoot.getUserRoot()
					.findAll(ac, ac.getPagingParameters());
		}
		return null;
	}

	public GraphQLObjectType getRootType(Project project) {
		Builder root = newObject();
		root.name("Mesh root");

		// .me
		root.field(newFieldDefinition().name("me")
				.description("The current user")
				.type(userFieldProvider.getUserType())
				.dataFetcher(this::userMeFetcher)
				.build());

		// .projects
		root.field(newFieldDefinition().name("projects")
				.description("Load a project")
				.argument(getUuidArg("Uuid of the project"))
				.argument(getNameArg("Name of the project"))
				.dataFetcher(fetcher -> {
					return handleUuidNameArgs(fetcher, boot.projectRoot());
				})
				.type(projectTypeProvider.getProjectType(project))
				.build());

		// .nodes
		root.field(newFieldDefinition().name("nodes")
				.description("Load a node")
				.argument(getUuidArg("Node uuid"))
				.argument(getPathArg())
				.dataFetcher(this::nodesFetcher)
				.type(nodeTypeProvider.getNodeType(project))
				.build());

		// .tags
		root.field(newFieldDefinition().name("tags")
				.description("Load a tag")
				.argument(getUuidArg("Uuid of the tag"))
				.argument(getNameArg("Name of the tag"))
				.dataFetcher(fetcher -> {
					return handleUuidNameArgs(fetcher, boot.tagRoot());
				})
				.type(tagTypeProvider.getTagType())
				.build());

		// .tagFamilies
		root.field(newFieldDefinition().name("tagFamilies")
				.description("Load a tag family")
				.argument(getUuidArg("Uuid of the tag family"))
				.argument(getNameArg("Name of the tag family"))
				.dataFetcher(fetcher -> {
					return handleUuidNameArgs(fetcher, boot.tagFamilyRoot());
				})
				.type(tagFamilyTypeProvider.getTagFamilyType())
				.build());

		// .releases
		root.field(newFieldDefinition().name("releases")
				.description("Load a specific release")
				.argument(getNameArg("Name of the release"))
				.argument(getUuidArg("Uuid of the release"))
				.type(releaseTypeProvider.getReleaseType())
				.dataFetcher(fetcher -> {
					return handleUuidNameArgs(fetcher, project.getReleaseRoot());
				})
				.build());

		// .schemas
		root.field(newFieldDefinition().name("schemas")
				.description("Load a schema")
				.argument(getUuidArg("Uuid of the schema"))
				.argument(getNameArg("Name of the schema"))
				.type(schemaTypeProvider.getSchemaType())
				.dataFetcher(fetcher -> {
					return handleUuidNameArgs(fetcher, boot.schemaContainerRoot());
				})
				.build());

		// .microschemas
		root.field(newFieldDefinition().name("microschemas")
				.description("Load a microschema")
				.argument(getUuidArg("Uuid of the microschema"))
				.argument(getNameArg("Name of the microschema"))
				.type(microschemaTypeProvider.getMicroschemaType())
				.dataFetcher(fetcher -> {
					return handleUuidNameArgs(fetcher, boot.microschemaContainerRoot());
				})
				.build());

		// .roles
		root.field(newFieldDefinition().name("roles")
				.description("Load a role")
				.argument(getUuidArg("Uuid of the role"))
				.argument(getNameArg("Name of the role"))
				.dataFetcher(fetcher -> {
					return handleUuidNameArgs(fetcher, boot.roleRoot());
				})
				.type(roleTypeProvider.getRoleType())
				.build());

		// .groups
		root.field(newFieldDefinition().name("groups")
				.description("Load a group")
				.argument(getUuidArg("Uuid of the group"))
				.argument(getNameArg("Name of the group"))
				.dataFetcher(fetcher -> {
					return handleUuidNameArgs(fetcher, boot.groupRoot());
				})
				.type(groupTypeProvider.getGroupType())
				.build());

		// .user
		root.field(newFieldDefinition().name("user")
				.description("Load a user")
				.argument(getUuidArg("Uuid of the user"))
				.argument(getNameArg("Username of the user"))
				.dataFetcher(fetcher -> {
					return handleUuidNameArgs(fetcher, boot.userRoot());
				})
				.type(userFieldProvider.getUserType())
				.build());

		// .users
		root.field(newFieldDefinition().name("users")
				.description("Load a page of users")
				.dataFetcher(this::usersFetcher)
				.type(getPageType(userFieldProvider.getUserType()))
				.build());

		return root.build();
	}

	public GraphQLObjectType getPageType(GraphQLType elementType) {
		Builder type = newObject().name("Page")
				.description("Paged result");
		type.field(newFieldDefinition().name("elements")
				.type(new GraphQLList(elementType)).dataFetcher(env -> {
					Object source = env.getSource();
					if (source instanceof Page) {
						return ((Page) source);
					}
					return null;
				})
				.build());
		type.field(newFieldDefinition().name("totalElements")
				.dataFetcher(env -> {
					Object source = env.getSource();
					if (source instanceof Page) {
						return ((Page) source).getTotalElements();
					}
					return null;
				})
				.type(GraphQLLong));
		return type.build();
	}

	public GraphQLSchema getRootSchema(Project project) {
		graphql.schema.GraphQLSchema.Builder schema = GraphQLSchema.newSchema();
		return schema.query(getRootType(project))
				.build();
	}

}
