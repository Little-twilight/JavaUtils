package com.zhongyou.util.ref;

public class BiRef<T1, T2> {

	public T1 value1;
	public T2 value2;

	public BiRef(T1 value1, T2 value2) {
		this.value1 = value1;
		this.value2 = value2;
	}

	public static <T1, T2> BiRef<T1, T2> create(T1 value1, T2 value2) {

		return new BiRef<>(value1, value2);
	}
}
