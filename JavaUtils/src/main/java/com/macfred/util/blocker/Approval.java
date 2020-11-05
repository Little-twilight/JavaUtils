package com.macfred.util.blocker;

import com.macfred.util.function.Consumer;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Approval {

	private final String mTag;
	private final Consumer<Approval> mReleaseAction;
	private final Lock mLock = new ReentrantLock();
	private volatile boolean mIsReleased;

	public Approval(String tag, Consumer<Approval> releaseAction) {
		Objects.requireNonNull(tag);
		Objects.requireNonNull(releaseAction);
		mTag = tag;
		mReleaseAction = releaseAction;
	}

	public final void release() {
		mLock.lock();
		try {
			if (!mIsReleased) {
				mReleaseAction.accept(this);
				mIsReleased = true;
			}
		} finally {
			mLock.unlock();
		}
	}

	public final String getTag() {
		return mTag;
	}

	public final boolean isReleased() {
		return mIsReleased;
	}

	public final boolean transferToCurrentThread() {
		mLock.lock();
		try {
			if (mIsReleased) {
				return false;
			}
			return doTransferToCurrentThread();
		} finally {
			mLock.unlock();
		}
	}

	protected boolean doTransferToCurrentThread() {
		return false;
	}

}
