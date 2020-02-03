package com.zhongyou.util.utils;

import com.zhongyou.util.ZyLogger;
import com.zhongyou.util.function.Consumer;
import com.zhongyou.util.function.Function;
import com.zhongyou.util.function.Predicate;
import com.zhongyou.util.function.Supplier;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class StatusManager<Status> {
	private final ReentrantReadWriteLock mStatusLock = new ReentrantReadWriteLock();
	private final ListenerManager<Consumer<Status>> mListeners = new ListenerManager<>();
	private Status mStatus;

	public StatusManager(Status status) {
		mStatus = status;
	}

	public Status getStatus() {
		return mStatus;
	}

//    public void updateStatus(Status status) {
//        if (mStatusLock.getReadHoldCount() > 0) {
//            throw new RuntimeException("ReadWriteLock upgrade not available!");
//        }
//        boolean writeLocked = false;
//        if (!mStatusLock.isWriteLockedByCurrentThread()) {
//            mStatusLock.writeLock().lock();
//            writeLocked = true;
//        }
//        try {
//            mStatus = status;
//        } finally {
//            if (writeLocked) {
//                mStatusLock.writeLock().unlock();
//            }
//        }
//    }

	private void updateStatus(Status status) {
		Objects.requireNonNull(status);
		if (mStatus == status) {
			return;
		}
		mStatus = status;
		mListeners.forEachListener(listener -> listener.accept(status));
	}

	public ListenerManager<Consumer<Status>> getListeners() {
		return mListeners;
	}

	public void doWhileTestedStatus(Runnable action, Predicate<Status> tester) {
		doWhileTestedStatus(() -> {
			action.run();
			return null;
		}, tester);
	}

	public <T> T doWhileTestedStatus(Supplier<T> action, Predicate<Status> tester) {
		boolean readLockedSelf = false;
		if (!mStatusLock.isWriteLockedByCurrentThread() && mStatusLock.getReadHoldCount() == 0) {
			mStatusLock.readLock().lock();
			readLockedSelf = true;
		}
		try {
			if (tester == null || tester.test(mStatus)) {
				return action.get();
			}
		} finally {
			if (readLockedSelf) {
				mStatusLock.readLock().unlock();
			}
		}
		return null;
	}

	public <T> T doWhileTestedStatus(Supplier<T> action, Predicate<Status> tester, Status updateBeforeAction, Status updateAfterAction) {
//        boolean writeLockedSelf = false;
//        if (!mStatusLock.isWriteLockedByCurrentThread()) {
//            mStatusLock.writeLock().lock();
//            writeLockedSelf = true;
//        }
//        boolean downgradeToReadLock = false;
//        try {
//            if (tester == null || tester.test(mStatus)) {
//                if (updateBeforeAction != null) {
//                    updateStatus(updateBeforeAction);
//                }
//                //downgrade to read lock
//                if (writeLockedSelf && updateAfterAction == null) {
//                    mStatusLock.readLock().lock();
//                    downgradeToReadLock = true;
//                }
//                try {
//                    T t = action.get();
//                    if (updateAfterAction != null) {
//                        updateStatus(updateAfterAction);
//                    }
//                    return t;
//                } finally {
//                    if (downgradeToReadLock) {
//                        mStatusLock.readLock().unlock();
//                    }
//                }
//            }
//        } finally {
//            if (writeLockedSelf && !downgradeToReadLock) {
//                if (mStatusLock.isWriteLockedByCurrentThread()) {
//                    mStatusLock.writeLock().unlock();
//                }
//                if (mStatusLock.isWriteLockedByCurrentThread()) {
//                    ZyLogger.e("Someone mush have modified write lock here");
//                }
//            }
//        }
//        return null;
		return doWhileTestedStatus(
				action,
				tester,
				updateBeforeAction == null ? null : () -> updateBeforeAction,
				updateAfterAction == null ? null : () -> updateAfterAction
		);
	}

	public <T> T doWhileTestedStatus(Runnable action, Predicate<Status> tester, Status updateBeforeAction, Status updateAfterAction) {
		return doWhileTestedStatus(
				() -> {
					action.run();
					return null;
				},
				tester,
				updateBeforeAction == null ? null : () -> updateBeforeAction,
				updateAfterAction == null ? null : () -> updateAfterAction
		);
	}

	public <T> T doWhileTestedStatus(Runnable action, Predicate<Status> tester, Supplier<Status> updateBeforeActionSupplier, Supplier<Status> updateAfterActionSupplier) {
		return doWhileTestedStatus(
				() -> {
					action.run();
					return null;
				},
				tester,
				updateBeforeActionSupplier,
				updateAfterActionSupplier
		);
	}

	public <T> T doWhileTestedStatus(Supplier<T> action, Predicate<Status> tester, Supplier<Status> updateBeforeActionSupplier, Supplier<Status> updateAfterActionSupplier) {
		if (mStatusLock.getReadHoldCount() > 0) {
			throw new RuntimeException("Cannot acquire write lock when read lock acquired");
		}
		boolean writeLockedSelf = false;
		if (!mStatusLock.isWriteLockedByCurrentThread()) {
			mStatusLock.writeLock().lock();
			writeLockedSelf = true;
		}
		boolean downgradeToReadLock = false;
		try {
			if (tester == null || tester.test(mStatus)) {
				if (updateBeforeActionSupplier != null) {
					Status update = updateBeforeActionSupplier.get();
					if (update != null) {
						updateStatus(update);
					}
//                    updateStatus(update); todo
				}
				//downgrade to read lock
				if (writeLockedSelf && updateAfterActionSupplier == null) {
					mStatusLock.readLock().lock();
					mStatusLock.writeLock().unlock();
					downgradeToReadLock = true;
				}
				try {
					T t = action.get();
					if (updateAfterActionSupplier != null) {
						Status update = updateAfterActionSupplier.get();
						if (update != null) {
							updateStatus(update);
						}
//                    updateStatus(update); todo
					}
					return t;
				} finally {
					if (downgradeToReadLock) {
						mStatusLock.readLock().unlock();
					}
				}
			}
		} finally {
			if (writeLockedSelf && !downgradeToReadLock) {
				if (mStatusLock.isWriteLockedByCurrentThread()) {
					mStatusLock.writeLock().unlock();
				}
				if (mStatusLock.isWriteLockedByCurrentThread()) {
					ZyLogger.e("Someone mush have modified write lock here");
				}
			}
		}
		return null;
	}

	public <T> T doWhileTestedStatus(Function<Consumer<Status>, T> action, Predicate<Status> tester, T defaultValue) {
		if (mStatusLock.getReadHoldCount() > 0) {
			throw new RuntimeException("Cannot acquire write lock when read lock acquired");
		}
		boolean writeLockedSelf = false;
		if (!mStatusLock.isWriteLockedByCurrentThread()) {
			mStatusLock.writeLock().lock();
			writeLockedSelf = true;
		}
		try {
			if (tester == null || tester.test(mStatus)) {
				return action.apply(status -> mStatus = status);
			}
		} finally {
			if (writeLockedSelf) {
				if (mStatusLock.isWriteLockedByCurrentThread()) {
					mStatusLock.writeLock().unlock();
				}
				if (mStatusLock.isWriteLockedByCurrentThread()) {
					ZyLogger.e("Someone mush have modified write lock here");
				}
			}
		}
		return defaultValue;
	}

}
