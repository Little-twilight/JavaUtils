package com.zhongyou.util.ref;

public class TriRef<T1, T2, T3> {

	public T1 value1;
	public T2 value2;
	public T3 value3;

	public TriRef(T1 value1, T2 value2, T3 value3) {
		this.value1 = value1;
		this.value2 = value2;
		this.value3 = value3;
	}

	public static <T1, T2, T3> TriRef<T1, T2, T3> create(T1 value1, T2 value2, T3 value3) {
		return new TriRef<>(value1, value2, value3);
	}
}
