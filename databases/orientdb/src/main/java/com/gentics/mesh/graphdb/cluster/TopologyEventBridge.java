package com.gentics.mesh.graphdb.cluster;

import static com.gentics.mesh.core.rest.MeshEvent.CLUSTER_DATABASE_CHANGE_STATUS;
import static com.gentics.mesh.core.rest.MeshEvent.CLUSTER_NODE_JOINED;
import static com.gentics.mesh.core.rest.MeshEvent.CLUSTER_NODE_JOINING;
import static com.gentics.mesh.core.rest.MeshEvent.CLUSTER_NODE_LEFT;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.etc.config.ClusterOptions;
import com.gentics.mesh.etc.config.MeshOptions;
import com.hazelcast.core.HazelcastInstance;
import com.orientechnologies.orient.server.distributed.ODistributedLifecycleListener;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager.DB_STATUS;

import dagger.Lazy;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Listener for OrientDB cluster specific events. The listener relays the events via messages to the eventbus.
 */
public class TopologyEventBridge implements ODistributedLifecycleListener {

	private static final Logger log = LoggerFactory.getLogger(TopologyEventBridge.class);

	public static final String DB_STATUS_MAP_KEY = "DB_STATUS_MAP";

	public static final String SERVER_STATUS_MAP_KEY = "SERVER_STATUS_MAP";

	private final Lazy<Vertx> vertx;

	private OrientDBClusterManager manager;

	private final Lazy<BootstrapInitializer> boot;

	private CountDownLatch nodeJoinLatch = new CountDownLatch(1);

	private ClusterOptions clusterOptions;

	public TopologyEventBridge(MeshOptions options, Lazy<Vertx> vertx, Lazy<BootstrapInitializer> boot, OrientDBClusterManager manager,
		HazelcastInstance hz) {
		this.clusterOptions = options.getClusterOptions();
		this.vertx = vertx;
		this.boot = boot;
		this.manager = manager;
	}

	EventBus getEventBus() {
		return vertx.get().eventBus();
	}

	@Override
	public boolean onNodeJoining(String nodeName) {
		if (log.isDebugEnabled()) {
			log.debug("Node {" + nodeName + "} is joining the cluster.");
		}
		if (isVertxReady()) {
			getEventBus().publish(CLUSTER_NODE_JOINING.address, nodeName);
		}
		return true;
	}

	@Override
	public void onNodeJoined(String nodeName) {
		if (log.isDebugEnabled()) {
			log.debug("Node {" + nodeName + "} joined the cluster.");
		}
		if (isVertxReady()) {
			getEventBus().publish(CLUSTER_NODE_JOINED.address, nodeName);
		}
	}

	@Override
	public void onNodeLeft(String nodeName) {
		if (log.isDebugEnabled()) {
			log.debug("Node {" + nodeName + "} left the cluster");
		}
		// db.removeNode(iNode);
		if (isVertxReady()) {
			getEventBus().publish(CLUSTER_NODE_LEFT.address, nodeName);
		}
	}

	@Override
	public void onDatabaseChangeStatus(String nodeName, String iDatabaseName, DB_STATUS iNewStatus) {
		if (iNewStatus == DB_STATUS.ONLINE) {
			// Delay the online status a few seconds
			long postOnlineDelay = clusterOptions.getTopologyLockDelay();
			if (postOnlineDelay != 0) {
				try {
					Thread.sleep(postOnlineDelay);
				} catch (InterruptedException e) {
					log.warn("Topology lock dalay was interrupted", e);
				}
			}
		}
		log.info("Node {" + nodeName + "} Database {" + iDatabaseName + "} changed status {" + iNewStatus.name() + "}");
		if (isVertxReady()) {
			JsonObject statusInfo = new JsonObject();
			statusInfo.put("node", nodeName);
			statusInfo.put("database", iDatabaseName);
			statusInfo.put("status", iNewStatus.name());
			getEventBus().publish(CLUSTER_DATABASE_CHANGE_STATUS.address, statusInfo);
		}
		if ("storage".equals(iDatabaseName) && iNewStatus == DB_STATUS.ONLINE && nodeName.equals(manager.getNodeName())) {
			nodeJoinLatch.countDown();
		}
	}

	/**
	 * Block until another node joined the cluster and the database is ready to use.
	 * 
	 * @param timeout
	 *            the maximum time to wait
	 * @param unit
	 *            the time unit of the {@code timeout} argument
	 * @return {@code true} if the count reached zero and {@code false} if the waiting time elapsed before the count reached zero
	 * @throws InterruptedException
	 *             if the current thread is interrupted while waiting
	 */
	public boolean waitForMainGraphDB(int timeout, TimeUnit unit) throws InterruptedException {
		return nodeJoinLatch.await(timeout, unit);
	}

	public boolean isVertxReady() {
		return boot.get().isVertxReady();
	}

}
