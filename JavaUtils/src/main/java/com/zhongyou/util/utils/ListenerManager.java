package com.zhongyou.util.utils;

import com.zhongyou.util.function.Consumer;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ListenerManager<T> {
	private Set<T> mListeners = new CopyOnWriteArraySet<>();
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
		synchronized (mLock) {
			for (T t : mListeners) {
				action.accept(t);
			}
		}
	}

}
