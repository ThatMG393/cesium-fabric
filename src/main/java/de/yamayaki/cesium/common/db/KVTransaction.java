package de.yamayaki.cesium.common.db;

import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KVTransaction<K, V> {
    private final KVDatabase<K, V> storage;
    private final Object2ReferenceMap<K, byte[]> pending = new Object2ReferenceOpenHashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public KVTransaction(KVDatabase<K, V> storage) {
        this.storage = storage;
    }

    public void add(K key, V value) {
        this.lock.writeLock()
                .lock();

        try {
            if (value == null) {
                this.pending.put(key, null);
                return;
            }

            byte[] data = this.storage.getValueSerializer()
                    .serialize(value);

            byte[] compressedData = this.storage.getCompressor()
                    .compress(data);

            this.pending.put(key, compressedData);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't serialize value", e);
        } finally {
            this.lock.writeLock()
                    .unlock();
        }
    }

    void addChanges(Txn<byte[]> txn) {
        this.lock.readLock()
                .lock();

        try {
            for (Object2ReferenceMap.Entry<K, byte[]> entry : this.pending.object2ReferenceEntrySet()) {
                if (entry.getValue() != null) {
                    this.storage.putValue(txn, entry.getKey(), entry.getValue());
                } else {
                    this.storage.delete(txn, entry.getKey());
                }
            }
        } finally {
            this.lock.readLock()
                    .unlock();
        }
    }

    void clear() {
        this.lock.writeLock()
                .lock();
        try {
            this.pending.clear();
        } finally {
            this.lock.writeLock()
                    .unlock();
        }
    }
}