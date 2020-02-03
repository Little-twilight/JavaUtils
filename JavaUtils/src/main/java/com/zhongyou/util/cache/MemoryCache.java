package com.zhongyou.util.cache;

import com.zhongyou.util.ZyLogger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;

public class MemoryCache<Key, Entry> {

	private static final String TAG = MemoryCache.class.getSimpleName();

	private KvCache<Key, Entry> mEntryKeyCache = new KvCache<>();
	private SortedKvCache<Long, Entry> mEntryIndexCache = new SortedKvCache<>();
	private Deque<Entry> mLruCacheQueue = new ArrayDeque<>();
	private int LRU_TRIGGER_THERMAL;
	private int LRU_REMAIN_THERMAL;

	public MemoryCache() {
		this(500, 200);
	}

	public MemoryCache(int lru_trigger_thermal, int lru_remain_thermal) {
		if (lru_remain_thermal <= 0 || lru_trigger_thermal <= 0 || lru_remain_thermal >= lru_trigger_thermal) {
			throw new IllegalArgumentException(String.format("Illegal LRU thermal assignment, LRU_TRIGGER_THERMAL: %s, LRU_REMAIN_THERMAL: %s", lru_trigger_thermal, lru_remain_thermal));
		}
		LRU_TRIGGER_THERMAL = lru_trigger_thermal;
		LRU_REMAIN_THERMAL = lru_remain_thermal;
	}

	public synchronized void clear() {
		for (Entry entry : mEntryKeyCache.entries().values()) {
			drop(entry);
		}
	}

	private synchronized Entry updateLruCache(Entry target) {
		if (target == null) {
			return null;
		}
		if (isEntryCached(target) && mLruCacheQueue.peekLast() != target) {
			mLruCacheQueue.remove(target);
			mLruCacheQueue.offer(target);
		}
		final int currentCacheSize = mLruCacheQueue.size();
		if (currentCacheSize > LRU_TRIGGER_THERMAL) {
			ZyLogger.d(TAG, String.format("LRU recycle triggered, LRU_TRIGGER_THERMAL: %s, LRU_REMAIN_THERMAL: %s, current:%s", LRU_TRIGGER_THERMAL, LRU_REMAIN_THERMAL, currentCacheSize));
			int totalRecycleThermal = currentCacheSize - LRU_REMAIN_THERMAL;
			for (int i = 0; i < currentCacheSize - 1; i++) {
				Entry entryCache = mLruCacheQueue.poll();
				if (drop(entryCache)) {
					totalRecycleThermal--;
				} else {
					mLruCacheQueue.offer(entryCache);
				}
				if (totalRecycleThermal <= 0) {
					break;
				}
			}
			ZyLogger.d(TAG, String.format("LRU recycled: %s", mLruCacheQueue.size() - currentCacheSize));
		}
		return target;
	}

	public synchronized boolean isEntryCached(Entry entry) {
		return mEntryKeyCache.isEntryCached(entry);
	}

	public synchronized Entry queryCache(Key key) {
		return updateLruCache(mEntryKeyCache.queryEntryCache(key));
	}

	public synchronized Key queryEntryKeyCache(Entry entry) {
		return mEntryKeyCache.queryEntryKeyCache(updateLruCache(entry));
	}

	public synchronized Entry queryCache(long index) {
		return updateLruCache(mEntryIndexCache.queryEntryCache(index));
	}

	public synchronized Long queryEntryIndexCache(Entry entry) {
		return mEntryIndexCache.queryEntryKeyCache(updateLruCache(entry));
	}

	public synchronized boolean cache(Entry entry, Key key, Long index) {
		Objects.requireNonNull(entry);
		Objects.requireNonNull(key);
		Objects.requireNonNull(index);
		if (isEntryCached(entry)) {
			return true;
		}
		if (!mEntryKeyCache.cache(key, entry)) {
			return false;
		}
		if (!mEntryIndexCache.cache(index, entry)) {
			mEntryKeyCache.drop(key);
			return false;
		}
		mLruCacheQueue.offer(entry);
		updateLruCache(entry);
		return true;
	}

	public synchronized boolean drop(Entry entry) {
		Objects.requireNonNull(entry);
		if (!isEntryCached(entry)) {
			return true;
		}

		mEntryKeyCache.dropEntry(entry);
		mEntryIndexCache.dropEntry(entry);
		mLruCacheQueue.remove(entry);
		return true;
	}

	public synchronized void notifyIndexInsert(long index) {
		for (Map.Entry<Long, Entry> entry : mEntryIndexCache.tailEntries(index, true).descendingMap().entrySet()) {
			long previousIndex = entry.getKey();
			mEntryIndexCache.drop(previousIndex);
			mEntryIndexCache.cache(previousIndex + 1, entry.getValue());
		}
	}

	public synchronized void notifyIndexRemoved(long index) {
		for (Map.Entry<Long, Entry> entry : mEntryIndexCache.tailEntries(index, false).entrySet()) {
			long previousIndex = entry.getKey();
			mEntryIndexCache.drop(previousIndex);
			mEntryIndexCache.cache(previousIndex - 1, entry.getValue());
		}
	}
}
