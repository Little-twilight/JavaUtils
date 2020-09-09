package com.zhongyou.util.ref;

public class TriRef<T1, T2, T3> {

	private T1 mFirst;
	private T2 mSecond;
	private T3 mThird;

	public TriRef(T1 first, T2 second, T3 third) {
		this.setFirst(first);
		this.setSecond(second);
		this.setThird(third);
	}

	public static <T1, T2, T3> TriRef<T1, T2, T3> create(T1 first, T2 second, T3 third) {
		return new TriRef<>(first, second, third);
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

	public T3 getThird() {
		return mThird;
	}

	public void setThird(T3 third) {
		this.mThird = third;
	}
}
