package com.gentics.mesh.core.verticle.handler;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.etc.config.MeshOptions;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ILock;

@Singleton
public class WriteLockImpl implements WriteLock {

	private final ILock lock;
	private final MeshOptions options;

	@Inject
	public WriteLockImpl(MeshOptions options, HazelcastInstance hazelcast) {
		this.options = options;
		this.lock = hazelcast.getLock(WRITE_LOCK_KEY);
	}

	@Override
	public void close() {
		lock.unlock();
	}

	/**
	 * Locks writes. Use this to prevent concurrent write transactions.
	 */
	@Override
	public WriteLock lock(InternalActionContext ac) {
		if (ac != null && ac.isSkipWriteLock()) {
			return this;
		} else {
			boolean syncWrites = options.getStorageOptions().isSynchronizeWrites();
			if (syncWrites) {
				try {
					boolean isTimeout = !lock.tryLock(240, TimeUnit.SECONDS);
					if (isTimeout) {
						throw new RuntimeException("Got timeout while waiting for write lock.");
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			return this;
		}
	}

}
