package com.gentics.mesh.core.data.service;

import io.vertx.ext.apex.RoutingContext;

import java.util.List;
import java.util.concurrent.ForkJoinPool;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gentics.mesh.core.Page;
import com.gentics.mesh.core.data.model.tinkerpop.MeshNode;
import com.gentics.mesh.core.data.service.transformation.TransformationInfo;
import com.gentics.mesh.core.data.service.transformation.node.MeshNodeTransformationTask;
import com.gentics.mesh.core.rest.node.response.NodeResponse;
import com.gentics.mesh.etc.MeshSpringConfiguration;
import com.gentics.mesh.paging.MeshPageRequest;
import com.gentics.mesh.paging.PagingInfo;

@Component
public class MeshNodeServiceImpl extends AbstractMeshService implements MeshNodeService {

	@Autowired
	private LanguageService languageService;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private UserService userService;

	@Autowired
	private GroupService groupService;

	@Autowired
	private SchemaService objectSchemaService;

	@Autowired
	private TagService tagService;

	@Autowired
	private MeshSpringConfiguration springConfiguration;

	@Autowired
	private I18NService i18n;

	@Autowired
	private RoutingContextService rcs;

	private static ForkJoinPool pool = new ForkJoinPool(8);

	@Override
	public NodeResponse transformToRest(RoutingContext rc, MeshNode content) {

		TransformationInfo info = new TransformationInfo(rc);

		List<String> languageTags = rcs.getSelectedLanguageTags(rc);
		info.setLanguageTags(languageTags);
		info.setUserService(userService);
		info.setLanguageService(languageService);
		info.setTagService(tagService);
		info.setSpringConfiguration(springConfiguration);
		info.setContentService(this);
		info.setI18nService(i18n);
		NodeResponse restContent = new NodeResponse();
		MeshNodeTransformationTask task = new MeshNodeTransformationTask(content, info, restContent);
		pool.invoke(task);
		return restContent;

	}

	@Override
	public Page<MeshNode> findChildren(RoutingContext rc, String projectName, MeshNode parentNode, List<String> languageTags, PagingInfo pagingInfo) {
		String userUuid = rc.session().getPrincipal().getString("uuid");

		MeshPageRequest pr = new MeshPageRequest(pagingInfo);
		//		if (languageTags == null || languageTags.size() == 0) {
		//			return findChildren(userUuid, projectName, parentNode, pr);
		//		} else {
		//			return findChildren(userUuid, projectName, parentNode, languageTags, pr);
		//		}

		//		Page<MeshNode> findChildren(String userUuid, String projectName, MeshNode parentNode, List<String> languageTags, Pageable pr) {
		//			@Query(value = MATCH_PERMISSION_ON_NODE + MATCH_NODE_OF_PROJECT + " MATCH (parentNode)-[:HAS_PARENT_NODE]->(node) " + "WHERE "
		//					+ FILTER_USER_PERM_AND_PROJECT + " AND id(parentNode) = {2} " + "WITH p, node " + ORDER_BY_NAME_DESC + "RETURN DISTINCT childNode",
		//
		//			countQuery = MATCH_PERMISSION_ON_NODE + MATCH_NODE_OF_PROJECT + " MATCH (parentNode)-[:HAS_PARENT_NODE]->(node) " + "WHERE "
		//					+ FILTER_USER_PERM_AND_PROJECT + " AND id(parentNode) = {2} " + "RETURN count(DISTINCT node)"
		//
		//			)		
		//		}

		//		Page<MeshNode> findChildren(String userUuid, String projectName, MeshNode parentNode, Pageable pr) {
		//			@Query(value = MATCH_PERMISSION_ON_NODE + MATCH_NODE_OF_PROJECT + " MATCH (parentNode)<-[:HAS_PARENT_NODE]-(node) " + "WHERE "
		//					+ FILTER_USER_PERM_AND_PROJECT + " AND id(parentNode) = {2} " + "WITH p, node " + "ORDER by p.`properties-name` desc "
		//					+ "RETURN DISTINCT node",
		//
		//			countQuery = MATCH_PERMISSION_ON_NODE + MATCH_NODE_OF_PROJECT + " MATCH (parentNode)<-[:HAS_PARENT_NODE]-(node) " + "WHERE "
		//					+ FILTER_USER_PERM_AND_PROJECT + " AND id(parentNode) = {2} " + "RETURN count(DISTINCT node)")
		//		}
		return null;

	}

	@Override
	public Page<MeshNode> findAll(RoutingContext rc, String projectName, List<String> languageTags, PagingInfo pagingInfo) {
		String userUuid = rc.session().getPrincipal().getString("uuid");

		//		@Query(value = MATCH_PERMISSION_ON_NODE + MATCH_NODE_OF_PROJECT + "WHERE l.languageTag IN {2} AND " + FILTER_USER_PERM_AND_PROJECT
		//				+ "WITH p, node " + ORDER_BY_NAME_DESC + "RETURN DISTINCT node",
		//
		//		countQuery = MATCH_PERMISSION_ON_NODE + MATCH_NODE_OF_PROJECT + "WHERE l.languageTag IN {2} AND " + FILTER_USER_PERM_AND_PROJECT
		//				+ "RETURN count(DISTINCT node)"
		//
		//		)
		//		Page<MeshNode> findAll(String userUuid, String projectName, List<String> languageTags, Pageable pageable);
		//
		//		@Query(value = MATCH_PERMISSION_ON_NODE + MATCH_NODE_OF_PROJECT + "WHERE " + FILTER_USER_PERM_AND_PROJECT + "WITH p, node " + ORDER_BY_NAME_DESC
		//				+ "RETURN DISTINCT node",
		//
		//		countQuery = MATCH_PERMISSION_ON_NODE + MATCH_NODE_OF_PROJECT + "WHERE " + FILTER_USER_PERM_AND_PROJECT + "RETURN count(DISTINCT node)")
		//		Page<MeshNode> findAll(String userUuid, String projectName, Pageable pageable);

		//		
		//		MeshPageRequest pr = new MeshPageRequest(pagingInfo);
		//		if (languageTags == null || languageTags.size() == 0) {
		//			return findAll(userUuid, projectName, pr);
		//		} else {
		//			return findAll(userUuid, projectName, languageTags, pr);
		//		}
		return null;
	}

	public void createLink(MeshNode from, MeshNode to) {
		// TODO maybe extract information about link start and end to speedup rendering of page with links
		// Linked link = new Linked(this, page);
		// this.links.add(link);
	}

	public List<MeshNode> findAllNodes() {
		//		@Query("MATCH (node:MeshNode) RETURN node")
		return null;
	}

	@Override
	public MeshNode create() {
		return framedGraph.addFramedVertex(MeshNode.class);
	}

	@Override
	public MeshNode findByUUID(String uuid) {
		return framedGraph.v().has("uuid", uuid).has("ferma_type", MeshNode.class.getName()).next(MeshNode.class);
	}

	@Override
	public void delete(MeshNode node) {
		node.getVertex().remove();
	}

	// node children

}
