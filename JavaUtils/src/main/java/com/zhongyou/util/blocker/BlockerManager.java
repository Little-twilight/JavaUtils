package com.zhongyou.util.blocker;


import com.zhongyou.util.function.Consumer;
import com.zhongyou.util.ref.BiRef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class BlockerManager {
	private final Map<Blocker, BiRef<Thread, BlockerPolicy>> mBlockers = new HashMap<>();
	private final Map<Thread, Set<Blocker>> mBlockerGeneratorThreads = new HashMap<>();
	private final Set<Thread> mBlockedThreads = new HashSet<>();
	private final Map<Approval, Thread> mApprovals = new HashMap<>();
	private final Map<Thread, Set<Approval>> mApprovalGeneratorThreads = new HashMap<>();
	private final Lock mLock;
	private final Condition mBlockerCondition;
	private final Condition mApprovalCondition;
	private volatile boolean mIsBlocked = false;
	private volatile boolean mIsApprovalInUse = false;
	private Consumer<Boolean> mOnBlockStatusChangedListener;
	private Consumer<Boolean> mOnApprovalStatusChangedListener;

	public BlockerManager(Lock lock) {
		Objects.requireNonNull(lock);
		mLock = lock;
		mBlockerCondition = lock.newCondition();
		mApprovalCondition = lock.newCondition();
	}

	public Approval registerApproval(String tag) {
		mLock.lock();
		try {
			whileLoop:
			while (true) {
				BlockerApprovalTestType type = testBlockerApproval();
				switch (type) {
					case Approved:
						break whileLoop;
					case ThrowException:
						throw new BlockedException();
					case Blocked:
						Thread thread = Thread.currentThread();
						try {
							mBlockedThreads.add(thread);
							mBlockerCondition.await();
						} catch (InterruptedException e) {
							Thread.interrupted();
							throw new RuntimeException(e);
						} finally {
							mBlockedThreads.remove(thread);
						}
						break;
					default:
						throw new RuntimeException(String.format("Unsupported value while register approval: %s", type));
				}
			}
			Approval approval = new Approval(tag, approvalRef -> {
				mLock.lock();
				try {
					if (!mApprovals.containsKey(approvalRef)) {
						return;
					}
					Thread thread = mApprovals.remove(approvalRef);
					Set<Approval> approvals = mApprovalGeneratorThreads.get(thread);
					if (approvals != null) {
						approvals.remove(approvalRef);
						if (approvals.isEmpty()) {
							mApprovalGeneratorThreads.remove(thread);
						}
					}
					if (mApprovals.isEmpty() && mIsApprovalInUse) {
						mIsApprovalInUse = false;
						mApprovalCondition.signalAll();
						if (mOnApprovalStatusChangedListener != null) {
							mOnApprovalStatusChangedListener.accept(false);
						}
					}
				} finally {
					mLock.unlock();
				}
			}) {
				@Override
				protected boolean doTransferToCurrentThread() {
					mLock.lock();
					try {
						if (!mApprovals.containsKey(this)) {
							return false;
						}
						Thread previousThread = mApprovals.get(this);
						Thread currentThread = Thread.currentThread();
						if (currentThread.equals(previousThread)) {
							return true;
						}
						Set<Approval> previousThreadApprovals = mApprovalGeneratorThreads.get(previousThread);
						if (previousThreadApprovals != null) {
							previousThreadApprovals.remove(this);
							if (previousThreadApprovals.isEmpty()) {
								mApprovalGeneratorThreads.remove(previousThread);
							}
						}
						Set<Approval> currentThreadApprovals = mApprovalGeneratorThreads.get(currentThread);
						if (currentThreadApprovals == null) {
							currentThreadApprovals = new HashSet<>();
							mApprovalGeneratorThreads.put(currentThread, currentThreadApprovals);
						}
						currentThreadApprovals.add(this);

						mApprovals.put(this, currentThread);

						return true;
					} finally {
						mLock.unlock();
					}
				}
			};
			Thread thread = Thread.currentThread();
			Set<Approval> approvals = mApprovalGeneratorThreads.get(thread);
			if (approvals == null) {
				approvals = new HashSet<>();
				mApprovalGeneratorThreads.put(thread, approvals);
			}
			approvals.add(approval);
			mApprovals.put(approval, thread);
			if (!mIsApprovalInUse) {
				mIsApprovalInUse = true;
				if (mOnApprovalStatusChangedListener != null) {
					mOnApprovalStatusChangedListener.accept(true);
				}
			}
			return approval;
		} finally {
			mLock.unlock();
		}
	}

	public void clearApprovals() {
		mLock.lock();
		try {
			for (Approval approval : new HashSet<>(mApprovals.keySet())) {
				approval.release();
			}
		} finally {
			mLock.unlock();
		}
	}

	public boolean isApprovalInUse() {
		return mIsApprovalInUse;
	}

	public Blocker registerBlocker(String tag, BlockerPolicy policy) {
		Objects.requireNonNull(policy);
		mLock.lock();
		try {
			Thread thread = Thread.currentThread();
			whileLoop:
			while (true) {
				if (!mIsApprovalInUse) {
					break;
				}
				int approvalGeneratorThreadCount = mApprovalGeneratorThreads.size();
				switch (approvalGeneratorThreadCount) {
					case 0:
						break whileLoop;
					case 1:
						if (mApprovalGeneratorThreads.containsKey(thread) && BlockerPolicy.IgnoreBlockerGeneratorThread.equals(policy)) {
							break whileLoop;
						}
					default:
						try {
							mApprovalCondition.await();
						} catch (InterruptedException e) {
							Thread.interrupted();
							throw new RuntimeException(e);
						}
						break;
				}
			}
			Blocker blocker = generateBlocker(tag);
			mBlockers.put(blocker, BiRef.create(thread, policy));
			Set<Blocker> blockers = mBlockerGeneratorThreads.get(thread);
			if (blockers == null) {
				blockers = new HashSet<>();
				mBlockerGeneratorThreads.put(thread, blockers);
			}
			blockers.add(blocker);
			if (!mIsBlocked) {
				mIsBlocked = true;
				if (mOnBlockStatusChangedListener != null) {
					mOnBlockStatusChangedListener.accept(true);
				}
			}
			return blocker;
		} finally {
			mLock.unlock();
		}
	}

	private Blocker generateBlocker(String tag) {
		return new Blocker(tag, thisBlocker -> {
			mLock.lock();
			try {
				BiRef<Thread, BlockerPolicy> threadAndBlockParam = mBlockers.remove(thisBlocker);
				if (threadAndBlockParam == null) {
					return;
				}
				Thread thread = threadAndBlockParam.value1;
				Set<Blocker> blockers = mBlockerGeneratorThreads.get(thread);
				if (blockers != null) {
					blockers.remove(thisBlocker);
					if (blockers.isEmpty()) {
						mBlockerGeneratorThreads.remove(thread);
					}
				}
				if (mBlockers.isEmpty() || mIsBlocked) {
					mBlockerCondition.signalAll();
					mIsBlocked = false;
					if (mOnBlockStatusChangedListener != null) {
						mOnBlockStatusChangedListener.accept(false);
					}
				}
			} finally {
				mLock.unlock();
			}
		}) {
			@Override
			protected boolean doTransferToCurrentThread() {
				mLock.lock();
				try {
					if (!mBlockers.containsKey(this)) {
						return false;
					}
					BiRef<Thread, BlockerPolicy> threadAndPolicy = mBlockers.get(this);
					Thread previousThread = threadAndPolicy.value1;
					Thread currentThread = Thread.currentThread();
					if (currentThread.equals(previousThread)) {
						return true;
					}
					Set<Blocker> previousThreadBlockers = mBlockerGeneratorThreads.get(previousThread);
					if (previousThreadBlockers != null) {
						previousThreadBlockers.remove(this);
						if (previousThreadBlockers.isEmpty()) {
							mBlockerGeneratorThreads.remove(previousThread);
						}
					}
					Set<Blocker> currentThreadBlockers = mBlockerGeneratorThreads.get(currentThread);
					if (currentThreadBlockers == null) {
						currentThreadBlockers = new HashSet<>();
						mBlockerGeneratorThreads.put(currentThread, currentThreadBlockers);
					}
					currentThreadBlockers.add(this);

					threadAndPolicy.value1 = currentThread;

					return true;
				} finally {
					mLock.unlock();
				}
			}
		};
	}

	public void clearBlockers() {
		mLock.lock();
		try {
			for (Blocker blocker : new HashSet<>(mBlockers.keySet())) {
				blocker.release();
			}
		} finally {
			mLock.unlock();
		}
	}

	public boolean isBlocked() {
		return mIsBlocked;
	}

	public BlockerApprovalTestType testBlockerApproval() {
		mLock.lock();
		try {
			if (mBlockers.isEmpty()) {
				return BlockerApprovalTestType.Approved;
			}
			switch (mBlockerGeneratorThreads.size()) {
				case 0:
					throw new RuntimeException(String.format("mBlockerGeneratorThreads detected empty while mBlockers contains %s element when test blocker", mBlockers.size()));
				case 1:
					Set<Blocker> blockers = mBlockerGeneratorThreads.get(Thread.currentThread());
					if (blockers != null) {
						boolean shouldBlock = false;
						forLoop:
						for (Blocker blocker : blockers) {
							BiRef<Thread, BlockerPolicy> threadAndBlockParam = mBlockers.get(blocker);
							if (threadAndBlockParam != null) {
								switch (threadAndBlockParam.value2) {
									case ThrowException:
										return BlockerApprovalTestType.ThrowException;
									case TotallyBlocked:
										shouldBlock = true;
										break forLoop;
									case IgnoreBlockerGeneratorThread:
										break;
								}
							}
						}
						if (!shouldBlock) {
							return BlockerApprovalTestType.Approved;
						}
					}
				default:
					return BlockerApprovalTestType.Blocked;
			}
		} finally {
			mLock.unlock();
		}
	}

	public void setOnBlockStatusChangedListener(Consumer<Boolean> onBlockStatusChangedListener) {
		mLock.lock();
		try {
			mOnBlockStatusChangedListener = onBlockStatusChangedListener;
		} finally {
			mLock.unlock();
		}
	}

	public void setOnApprovalStatusChangedListener(Consumer<Boolean> onApprovalStatusChangedListener) {
		mLock.lock();
		try {
			mOnApprovalStatusChangedListener = onApprovalStatusChangedListener;
		} finally {
			mLock.unlock();
		}
	}
}
