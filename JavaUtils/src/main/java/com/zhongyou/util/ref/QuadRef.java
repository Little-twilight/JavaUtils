package com.zhongyou.util.ref;

public class QuadRef<T1, T2, T3, T4> {
	public T1 value1;
	public T2 value2;
	public T3 value3;
	public T4 value4;

	public QuadRef(T1 value1, T2 value2, T3 value3, T4 value4) {
		this.value1 = value1;
		this.value2 = value2;
		this.value3 = value3;
		this.value4 = value4;
	}

	public static <T1, T2, T3, T4> QuadRef<T1, T2, T3, T4> create(T1 value1, T2 value2, T3 value3, T4 value4) {
		return new QuadRef<>(value1, value2, value3, value4);
	}

}
