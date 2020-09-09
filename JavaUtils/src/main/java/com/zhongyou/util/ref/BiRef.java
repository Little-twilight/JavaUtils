package com.zhongyou.util.ref;

public class BiRef<T1, T2> {

	private T1 mFirst;
	private T2 mSecond;

	public BiRef(T1 first, T2 second) {
		this.setFirst(first);
		this.setSecond(second);
	}

	public static <T1, T2> BiRef<T1, T2> create(T1 first, T2 second) {

		return new BiRef<>(first, second);
	}

	public T1 getFirst() {
		return mFirst;
	}

	public void setFirst(T1 first) {
		this.mFirst = first;
	}

	public T2 getSecond() {
		return mSecond;
	}

	public void setSecond(T2 second) {
		this.mSecond = second;
	}
}
