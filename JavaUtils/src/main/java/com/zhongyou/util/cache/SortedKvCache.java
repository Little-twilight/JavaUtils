package com.zhongyou.util.cache;

import com.zhongyou.util.collection.ArrayMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class SortedKvCache<K extends Comparable, V> {
	private final TreeMap<K, V> mCacheEntries = new TreeMap<>();
	private final Map<V, K> mCacheEntriesInverse = new HashMap<>();
	private volatile int mSize = 0;

	public int size(){
		return mSize;
	}

	public synchronized boolean cache(K key, V entry) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(entry);
		if (mCacheEntries.containsKey(key) || mCacheEntries.containsValue(entry)) {
			return mCacheEntries.get(key) == entry;
		} else {
			mCacheEntries.put(key, entry);
			mCacheEntriesInverse.put(entry, key);
			mSize = mCacheEntries.size();
			return true;
		}

	}

	public synchronized void drop(K key) {
		if (key == null) {
			return;
		}
		mCacheEntriesInverse.remove(mCacheEntries.remove(key));
		mSize = mCacheEntries.size();
	}

	public synchronized void dropEntry(V entry) {
		if (entry == null) {
			return;
		}
		mCacheEntries.remove(mCacheEntriesInverse.remove(entry));
		mSize = mCacheEntries.size();
	}

	public synchronized boolean isEntryCached(V entry) {
		return entry != null && mCacheEntries.containsValue(entry);
	}

	public synchronized V queryEntryCache(K key) {
		return key == null ? null : mCacheEntries.get(key);
	}

	public synchronized K queryEntryKeyCache(V entry) {
		return entry == null ? null : mCacheEntriesInverse.get(entry);
	}

	public synchronized Map<K, V> entries() {
		Map<K, V> ret = new ArrayMap<>();
		ret.putAll(mCacheEntries);
		return ret;
	}

	public synchronized TreeMap<K, V> headEntries(K toKey, boolean inclusive) {
		Objects.requireNonNull(toKey);
		return new TreeMap<>(mCacheEntries.headMap(toKey, inclusive));
	}

	public synchronized TreeMap<K, V> tailEntries(K fromKey, boolean inclusive) {
		Objects.requireNonNull(fromKey);
		return new TreeMap<>(mCacheEntries.tailMap(fromKey, inclusive));
	}

}
