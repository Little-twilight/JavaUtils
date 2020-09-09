package com.zhongyou.util.function;

public interface BaseCallback {

	default void onCanceled() {
		onException(new RuntimeException("Canceled"));
	}

	default void onException(Exception e) {
		throw new RuntimeException("Exception encountered", e);
	}

	default void onTimeout() {
		onException(new RuntimeException("Timeout"));
	}

}
