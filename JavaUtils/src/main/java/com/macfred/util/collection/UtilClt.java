package com.macfred.util.collection;

import com.macfred.util.function.BiConsumer;
import com.macfred.util.function.Consumer;
import com.macfred.util.ref.Ref;

import java.util.Map;

public class UtilClt {

	public static <T> void forEach(Consumer<T> actionTaker, Iterable<T> iterable) {
		for (T t : iterable) {
			actionTaker.accept(t);
		}
	}

	public static <T> void forEach(Consumer<T> actionTaker, T... items) {
		for (T item : items) {
			actionTaker.accept(item);
		}
	}

	public interface Quit {
		void quit();
	}

	/**
	 * @return true if quit occurred
	 */
	public static <T> boolean forEach(BiConsumer<T, Quit> actionAndQuitTaker, T... items) {
		Ref<Boolean> quitFlag = new Ref<>(false);
		Quit quit = () -> quitFlag.setValue(true);
		for (T item : items) {
			actionAndQuitTaker.accept(item, quit);
			if (Boolean.TRUE.equals(quitFlag.value)) {
				return true;
			}
		}
		return false;
	}

	public static <K, V> void forEach(Map<K, V> map, BiConsumer<K, V> actionTaker) {
		for (Map.Entry<K, V> entry : map.entrySet()) {
			actionTaker.accept(entry.getKey(), entry.getValue());
		}
	}

	public static <K, V> void forEachEntry(Map<K, V> map, BiConsumer<K, V> actionTaker) {
		forEach(map, actionTaker);
	}

}
