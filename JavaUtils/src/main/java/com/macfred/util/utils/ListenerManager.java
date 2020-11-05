package com.macfred.util.utils;

import com.macfred.util.function.Consumer;

import java.util.HashSet;
import java.util.Set;

public class ListenerManager<T> {
	private final Set<T> mListeners = new HashSet<>();
	private final Object mLock = new Object();

	public void registerListener(T t) {
		synchronized (mLock) {
			mListeners.add(t);
		}
	}

	public void unregisterListener(T t) {
		synchronized (mLock) {
			mListeners.remove(t);
		}
	}

	public void unregisterAllListener() {
		synchronized (mLock) {
			mListeners.clear();
		}
	}

	public void forEachListener(Consumer<T> action) {
		Set<T> cache;
		synchronized (mLock) {
			cache = new HashSet<>(mListeners);
		}
		for (T t : cache) {
			synchronized (mLock) {
				if (!mListeners.contains(t)) {
					continue;
				}
			}
			action.accept(t);
		}
	}

}
