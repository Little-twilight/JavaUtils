package com.zhongyou.util.cache;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.zhongyou.util.collection.ArrayMap;

import java.util.Map;
import java.util.Objects;

public class KvCache<K, V> {
	private final BiMap<K, V> mCacheEntries = HashBiMap.create();
	private volatile int mSize = 0;

	public int size(){
		return mSize;
	}

	public synchronized Map<K, V> entries() {
		Map<K, V> ret = new ArrayMap<>();
		ret.putAll(mCacheEntries);
		mSize = mCacheEntries.size();
		return ret;
	}

	public synchronized boolean cache(K key, V entry) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(entry);
		if (mCacheEntries.containsKey(key) || mCacheEntries.containsValue(entry)) {
			return mCacheEntries.get(key) == entry;
		} else {
			mCacheEntries.put(key, entry);
			mSize = mCacheEntries.size();
			return true;
		}

	}

	public synchronized void drop(K key) {
		if (key == null) {
			return;
		}
		mCacheEntries.remove(key);
		mSize = mCacheEntries.size();
	}

	public synchronized void dropEntry(V entry) {
		if (entry == null) {
			return;
		}
		mCacheEntries.remove(queryEntryKeyCache(entry));
		mSize = mCacheEntries.size();
	}

	public synchronized boolean isEntryCached(V entry) {
		return entry != null && mCacheEntries.containsValue(entry);
	}

	public synchronized V queryEntryCache(K key) {
		return key == null ? null : mCacheEntries.get(key);
	}

	public synchronized K queryEntryKeyCache(V entry) {
		return entry == null ? null : mCacheEntries.inverse().get(entry);
	}

}
