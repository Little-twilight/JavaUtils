package com.macfred.util.ref;

public class QuadRef<T1, T2, T3, T4> {
	private T1 mFirst;
	private T2 mSecond;
	private T3 mThird;
	private T4 mForth;

	public QuadRef(T1 first, T2 second, T3 third, T4 forth) {
		this.setFirst(first);
		this.setSecond(second);
		this.setThird(third);
		this.setForth(forth);
	}

	public static <T1, T2, T3, T4> QuadRef<T1, T2, T3, T4> create(T1 first, T2 second, T3 third, T4 forth) {
		return new QuadRef<>(first, second, third, forth);
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

	public T4 getForth() {
		return mForth;
	}

	public void setForth(T4 forth) {
		this.mForth = forth;
	}
}
