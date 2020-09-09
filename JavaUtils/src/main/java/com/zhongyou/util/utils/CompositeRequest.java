package com.zhongyou.util.utils;

import java.util.Arrays;
import java.util.List;

public abstract class CompositeRequest<T> extends Request<T> {

	public void setupSubActions(Request<?>... subActions) {
		setupSubActions(Arrays.asList(subActions));
	}

	public abstract void setupSubActions(List<Request<?>> subActions);

}
