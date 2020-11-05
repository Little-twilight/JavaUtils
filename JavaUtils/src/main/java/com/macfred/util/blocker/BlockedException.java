package com.macfred.util.blocker;


public class BlockedException extends RuntimeException {

	public BlockedException() {
		super();
	}

	public BlockedException(String message) {
		super(message);
	}

	public BlockedException(String message, Throwable cause) {
		super(message, cause);
	}

	public BlockedException(Throwable cause) {
		super(cause);
	}

	protected BlockedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
