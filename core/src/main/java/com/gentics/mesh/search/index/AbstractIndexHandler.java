package com.gentics.mesh.search.index;

import static com.gentics.mesh.core.data.search.SearchQueueEntryAction.STORE_ACTION;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

import java.util.HashSet;
import java.util.Set;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequestBuilder;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;

import com.gentics.mesh.core.data.MeshCoreVertex;
import com.gentics.mesh.core.data.root.RootVertex;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.data.search.SearchQueueEntry;
import com.gentics.mesh.core.data.search.SearchQueueEntryAction;
import com.gentics.mesh.dagger.MeshCore;
import com.gentics.mesh.graphdb.NoTx;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.search.SearchProvider;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import rx.Completable;

public abstract class AbstractIndexHandler<T extends MeshCoreVertex<?, T>> implements IndexHandler {

	private static final Logger log = LoggerFactory.getLogger(AbstractIndexHandler.class);

	protected SearchProvider searchProvider;

	protected Database db;

	public AbstractIndexHandler(SearchProvider searchProvider, Database db) {
		this.searchProvider = searchProvider;
		this.db = db;
	}

	/**
	 * Return the document type.
	 * 
	 * @return
	 */
	abstract protected String getType();

	/**
	 * Return the index name.
	 * 
	 * @param entry
	 *            entry
	 * 
	 * @return
	 */
	abstract protected String getIndex(SearchQueueEntry entry);

	/**
	 * Return the root vertex of the index handler. The root vertex is used to retrieve nodes by UUID in order to update the search index.
	 * 
	 * @return
	 */
	abstract protected RootVertex<T> getRootVertex();

	abstract protected Transformator getTransformator();

	/**
	 * Store the given object within the search index.
	 * 
	 * @param object
	 * @param documentType
	 * @param entry
	 *            search queue entry
	 * @return
	 */
	public Completable store(T object, String documentType, SearchQueueEntry entry) {
		return searchProvider.storeDocument(getIndex(entry), documentType, object.getUuid(), getTransformator().toDocument(object))
				.doOnCompleted(() -> {
					if (log.isDebugEnabled()) {
						log.debug("Stored object in index.");
					}
					MeshCore.get().searchProvider().refreshIndex();
				});
	}

	@Override
	public Completable delete(String uuid, String documentType, SearchQueueEntry entry) {
		// We don't need to resolve the uuid and load the graph object in this case.
		return searchProvider.deleteDocument(getIndex(entry), documentType, uuid);
	}

	@Override
	public Completable store(String uuid, String indexType, SearchQueueEntry entry) {
		return Completable.defer(() -> {
			try (NoTx noTx = db.noTx()) {
				T element = getRootVertex().findByUuidSync(uuid);
				if (element == null) {
					throw error(INTERNAL_SERVER_ERROR, "error_element_for_document_type_not_found", uuid, indexType);
				} else {
					return store(element, indexType, entry);
				}
			}
		});
	}

	/**
	 * Check whether the search provider is available. Some tests are not starting an search provider and thus we must be able to determine whether we can use
	 * the search provider.
	 * 
	 * @return
	 */
	protected boolean isSearchClientAvailable() {
		return searchProvider != null;
	}

	@Override
	public Completable handleAction(SearchQueueEntry entry) {
		String uuid = entry.getElementUuid();
		String actionName = entry.getElementActionName();
		String indexType = entry.getElementIndexType();

		if (!isSearchClientAvailable()) {
			String msg = "Elasticsearch provider has not been initalized. It can't be used. Omitting search index handling!";
			log.error(msg);
			return Completable.error(new Exception(msg));
		}

		if (indexType == null) {
			indexType = getType();
		}
		if (log.isDebugEnabled()) {
			log.debug("Handling action {" + actionName + "} for element {" + uuid + "} of type {" + indexType + "}");
		}
		SearchQueueEntryAction action = SearchQueueEntryAction.valueOfName(actionName);
		switch (action) {
		case DELETE_ACTION:
			return delete(uuid, indexType, entry);
		case STORE_ACTION:
			return store(uuid, indexType, entry);
		case REINDEX_ALL:
			return reindexAll();
		case CREATE_INDEX:
			return createIndex(uuid).doOnCompleted(() -> updateMapping(uuid));
		default:
			return Completable.error(new Exception("Action type {" + action + "} is unknown."));
		}
	}

	@Override
	public Completable reindexAll() {
		return Completable.create(sub -> {
			log.info("Handling full reindex entry");

			for (T element : getRootVertex().findAll()) {
				log.info("Invoking reindex for {" + getType() + "/" + element.getUuid() + "}");
				SearchQueueBatch batch = element.createIndexBatch(STORE_ACTION);
				for (SearchQueueEntry entry : batch.getEntries()) {
					entry.process().await();
				}
				batch.delete(null);
			}
			sub.onCompleted();
		});
	}

	@Override
	public Completable updateMapping() {
		try {
			Set<Completable> obsSet = new HashSet<>();
			getIndices().forEach(indexName -> {
				obsSet.add(updateMapping(indexName));
			});

			if (obsSet.isEmpty()) {
				return Completable.complete();
			} else {
				return Completable.merge(obsSet);
			}
		} catch (Exception e) {
			return Completable.error(e);
		}
	}

	@Override
	public Completable updateMapping(String indexName) {
		if (searchProvider.getNode() != null) {
			PutMappingRequestBuilder mappingRequestBuilder = searchProvider.getNode().client().admin().indices().preparePutMapping(indexName);
			mappingRequestBuilder.setType(getType());

			JsonObject mapping = getTransformator().getMapping(getType());

			mappingRequestBuilder.setSource(mapping.toString());

			return Completable.create(sub -> {
				mappingRequestBuilder.execute(new ActionListener<PutMappingResponse>() {

					@Override
					public void onResponse(PutMappingResponse response) {
						if (log.isDebugEnabled()) {
							log.debug("Updated mapping for index {" + indexName + "}");
						}
						sub.onCompleted();
					}

					@Override
					public void onFailure(Throwable e) {
						sub.onError(e);
					}
				});
			});
		} else {
			return Completable.complete();
		}
	}

	@Override
	public Completable createIndex() {
		Set<Completable> obs = new HashSet<>();
		getIndices().forEach(index -> obs.add(searchProvider.createIndex(index)));
		if (obs.isEmpty()) {
			return Completable.complete();
		} else {
			return Completable.merge(obs);
		}
	}

	/**
	 * Create the index, if it is one of the indices handled by this index handler. If the index name is not handled by this index handler, an error will be
	 * thrown
	 * 
	 * @param indexName
	 *            index name
	 * @return
	 */
	protected Completable createIndex(String indexName) {
		if (getIndices().contains(indexName)) {
			return searchProvider.createIndex(indexName);
		} else {
			throw error(INTERNAL_SERVER_ERROR, "error_index_unknown", indexName);
		}
	}

	@Override
	public Completable init() {
		return createIndex().andThen(updateMapping());
	}

	@Override
	public Completable clearIndex() {
		Set<Completable> obs = new HashSet<>();
		getIndices().forEach(index -> obs.add(searchProvider.clearIndex(index)));
		if (obs.isEmpty()) {
			return Completable.complete();
		} else {
			return Completable.merge(obs);
		}
	}
}
