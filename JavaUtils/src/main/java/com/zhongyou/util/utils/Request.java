package com.zhongyou.util.utils;

import com.google.common.base.Joiner;
import com.zhongyou.util.ZyLogger;
import com.zhongyou.util.function.Callback;
import com.zhongyou.util.function.Function;
import com.zhongyou.util.ref.Ref;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Request<T> {
	private static final String TAG = Request.class.getSimpleName();
	private static final TaskScheduler sDefaultTaskScheduler = new TaskScheduler() {
		private Timer mTimer = new Timer();

		@Override
		public Task scheduleTask(Runnable action, long time) {
			TimerTask timerTask = new TimerTask() {
				@Override
				public void run() {
					try {
						action.run();
					} catch (Exception e) {
						ZyLogger.printException(TAG, new RuntimeException("Error executing task", e));
					}
				}
			};
			Task task = timerTask::cancel;
			mTimer.schedule(timerTask, new Date(time));
			return task;
		}
	};

	private TaskScheduler mTaskScheduler;
	protected final Object mRequestLock = new Object();
	private volatile RequestStatus mStatus = RequestStatus.Idle;
	private T mResultValue;
	private Exception mResultException;
	private long mTimeout;
	private long mLaunchTime;
	private String mTag;
	private final List<Request> mCallingChain = new ArrayList<>();
	private Function<T, String> mResultValuePrinter;

	protected Task scheduleTaskDelayed(Runnable action, long delay) {
		return scheduleTask(action, System.currentTimeMillis() + delay);
	}

	protected Task scheduleTask(Runnable action, long time) {
		TaskScheduler taskScheduler = Wnn.d(mTaskScheduler, sDefaultTaskScheduler);
		return taskScheduler.scheduleTask(action, time);
	}

	public interface TaskScheduler {
		Task scheduleTask(Runnable action, long time);
	}

	public interface Task {
		boolean cancel();
	}

	public void setTaskScheduler(TaskScheduler taskScheduler) {
		synchronized (mRequestLock) {
			RequestStatus status = getStatus();
			if (!RequestStatus.Idle.equals(status)) {
				throw new RuntimeException(String.format("Trying to set task scheduler when status is %s", status));
			}
			mTaskScheduler = taskScheduler;
		}
	}

	public TaskScheduler getTaskScheduler() {
		return mTaskScheduler;
	}

	public void setResultValuePrinter(Function<T, String> resultValuePrinter) {
		synchronized (mRequestLock) {
			RequestStatus status = getStatus();
			if (!RequestStatus.Idle.equals(status)) {
				throw new RuntimeException(String.format("Trying to set result value printer when status is %s", status));
			}
			mResultValuePrinter = resultValuePrinter;
		}
	}

	public String getTag() {
		return mTag;
	}

	public void setTag(String tag) {
		synchronized (mRequestLock) {
			if (!isRequestIdle()) {
				throw new RuntimeException("Set tag for request which is not idle");
			}
			mTag = tag;
		}
	}

	protected void setupCallingChain(List<Request> callingChain) {
		synchronized (mRequestLock) {
			if (!isRequestIdle()) {
				throw new RuntimeException("Setup calling chain for request which is not idle");
			}
			if (!mCallingChain.isEmpty()) {
				throw new RuntimeException("Setup calling chain for request which has been setup before");
			}
			mCallingChain.addAll(callingChain);
		}
	}

	protected List<Request> getCallingChain() {
		return new ArrayList<>(mCallingChain);
	}

	protected String generateCallChainInfo() {
		List<String> temp = new ArrayList<>();
		for (Request request : mCallingChain) {
			temp.add(String.format("{%s}", request.generateCallingInfo()));
		}
		temp.add(String.format("{%s}", generateCallingInfo()));
		return Joiner.on("-----").useForNull(String.format("{%s}", "unknown")).join(temp);
	}

	protected abstract String generateCallingInfo();

	public abstract String dumpCallingStatus();

	protected String printResultValue() {
		return Wnn.d(mResultValuePrinter, String::valueOf).apply(getResultValue());
	}

	protected void setupCallingChain(Request... callingChain) {
		setupCallingChain(Arrays.asList(callingChain));
	}

	public final boolean isRequestIdle() {
		return RequestStatus.Idle.equals(getStatus());
	}

	public final boolean isRequestRunning() {
		return RequestStatus.Pending.equals(getStatus());
	}

	public final RequestStatus getStatus() {
		return mStatus;
	}

	protected final boolean testCurrentStatus(RequestStatus status) {
		return status.equals(getStatus());
	}

	protected final void setStatus(RequestStatus status) {
		mStatus = status;
	}

	public final T getResultValue() {
		return mResultValue;
	}

	protected final void setResultValue(T resultValue) {
		mResultValue = resultValue;
	}

	public final Exception getResultException() {
		return mResultException;
	}

	protected final void setResultException(Exception resultException) {
		mResultException = resultException;
	}

	public final long getTimeout() {
		return mTimeout;
	}

	protected final void setTimeout(long timeout) {
		mTimeout = timeout;
	}

	public final long getLaunchTime() {
		return mLaunchTime;
	}

	protected final void setLaunchTime(long launchTime) {
		mLaunchTime = launchTime;
	}

	public final long getTimeoutRemains() {
		return Math.max(getTimeout() - (System.currentTimeMillis() - getLaunchTime()), 0);
	}

	public abstract void launch(Callback<T> callback, long timeout);

	public void launch(Callback<T> callback) {
		launch(callback, TIMEOUT_UNLIMITED);
	}

	protected void onLaunch() {

	}

	public abstract void cancel();

	protected void onCancel() {

	}

//    protected void onCancel() {
//
//    }
//
//    protected void onExecuted() {
//
//    }

	public static final long TIMEOUT_UNLIMITED = 0;

	public enum RequestStatus {
		Idle, Pending, Done, Exception, Canceled, Timeout
	}

	protected static String concatDumpTree(String title, List<String> entries) {
		if (entries.isEmpty()) {
			return title;
		}
		if (entries.size() == 1) {
			return title + " ----- " + entries.get(0);
		}
		List<List<String>> nodes = new ArrayList<>();
		int totalLineCount = 0;
		for (String entry : entries) {
			List<String> node = new ArrayList<>();
			String[] splits = entry.split("\n");
			for (String split : splits) {
				if (!split.isEmpty()) {
					node.add(split);
				}
			}
			if (node.isEmpty()) {
				continue;
			}
			nodes.add(node);
			totalLineCount += node.size();
		}
		if (!nodes.isEmpty()) {
			totalLineCount += (nodes.size() - 1);
		}

		int middleSepratorUpperBound = nodes.get(0).size() / 2;
		List<String> lastNode = nodes.get(nodes.size() - 1);
		int middleSepratorLowerBound = totalLineCount - lastNode.size() + lastNode.size() / 2;

//		int titleLineNum = totalLineCount / 2;
		int titleLineNum = (middleSepratorUpperBound + middleSepratorLowerBound) / 2;
		String titleLineContent = title + " ------";
		char[] emptyTitle = new char[titleLineContent.length()];
		Arrays.fill(emptyTitle, ' ');
		String titleLineContentEmpty = new String(emptyTitle);

		StringBuilder sb = new StringBuilder();
		int lineNum = 0;

		for (List<String> node : nodes) {
			if (lineNum != 0) {
				String titlePart = lineNum == titleLineNum ? titleLineContent : titleLineContentEmpty;
				String sepratorPart;
				if (lineNum == middleSepratorUpperBound) {
					sepratorPart = "/";
				} else if (lineNum == middleSepratorLowerBound) {
					sepratorPart = "\\";
				} else {
					sepratorPart = lineNum > middleSepratorUpperBound && lineNum < middleSepratorLowerBound ? "|" : " ";
				}
				sb.append(titlePart).append(sepratorPart).append('\n');
				lineNum++;
			}
			int middleLineNum = lineNum + node.size() / 2;
			String middleLineContent = " ------ ";
			char[] emptyMiddle = new char[middleLineContent.length()];
			Arrays.fill(emptyMiddle, ' ');
			String middleLineContentEmpty = new String(emptyMiddle);

			for (String line : node) {
				String titlePart = lineNum == titleLineNum ? titleLineContent : titleLineContentEmpty;
				String sepratorPart;
				if (lineNum == middleSepratorUpperBound) {
					sepratorPart = "/";
				} else if (lineNum == middleSepratorLowerBound) {
					sepratorPart = "\\";
				} else {
					sepratorPart = lineNum > middleSepratorUpperBound && lineNum < middleSepratorLowerBound ? "|" : " ";
				}
				String middleLinePart = lineNum == middleLineNum ? middleLineContent : middleLineContentEmpty;
				sb.append(titlePart).append(sepratorPart).append(middleLinePart).append(line);
				if (lineNum++ < totalLineCount) {
					sb.append("\n");
				}
			}
		}
		return sb.toString();
	}

//	public static void main(String... args) throws InterruptedException {
////		System.out.println(concatDumpTree(
////				"title",
////				Arrays.asList(
////						concatDumpTree("a", Arrays.asList("a")),
////						concatDumpTree("b", Arrays.asList("b", "b")),
////						concatDumpTree("c", Arrays.asList("c", "c", "c")),
////						concatDumpTree("d", Arrays.asList("d", "d", "d", "d"))
////				)
////		));
//		SerialRequest<Object> serialRequest = new SerialRequest<>(new SerialRequest.ValueCompositor<Object>() {
//			@Override
//			public Object composite(List<Request> subActions) {
//				return true;
//			}
//		});
//		ParallelRequest<Object> parallelRequest = new ParallelRequest<>(new ParallelRequest.ValueCompositor<Object>() {
//			@Override
//			public RequestStatus composite(Collection<Request<?>> subActions, Ref<Object> resultValue, Ref<Exception> resultException) {
//				resultValue.value = 1;
//				return RequestStatus.Done;
//			}
//		});
//		parallelRequest.setupSubActions(
//				new SimpleRequest<>(new SimpleRequest.RequestAction<Object>() {
//					@Override
//					public void execute(Callback<Object> callback, long timeout) {
//						callback.accept(2);
//					}
//
//					@Override
//					public void cancel() {
//
//					}
//				}),
//				new SimpleRequest<>(new SimpleRequest.RequestAction<Object>() {
//					@Override
//					public void execute(Callback<Object> callback, long timeout) {
//						callback.accept(3);
//					}
//
//					@Override
//					public void cancel() {
//
//					}
//				}),
//				new SimpleRequest<>(new SimpleRequest.RequestAction<Object>() {
//					@Override
//					public void execute(Callback<Object> callback, long timeout) {
//						callback.accept(4);
//					}
//
//					@Override
//					public void cancel() {
//
//					}
//				})
//		);
//		AtomicRequest atomicRequest = new AtomicRequest();
//		atomicRequest.setMajorAction(
//				new SimpleRequest<>(new SimpleRequest.RequestAction<Object>() {
//					@Override
//					public void execute(Callback<Object> callback, long timeout) {
//						callback.accept(5);
//					}
//
//					@Override
//					public void cancel() {
//
//					}
//				})
//		);
//		atomicRequest.setHelper(new AtomicRequest.Helper() {
//			AtomicBoolean mAtomicBoolean = new AtomicBoolean();
//
//			@Override
//			public boolean isResourceAcquired() {
//				return mAtomicBoolean.get();
//			}
//
//			@Override
//			public boolean acquireResource() {
//				return mAtomicBoolean.compareAndSet(false, true);
//			}
//
//			@Override
//			public boolean releaseResource() {
//				return mAtomicBoolean.compareAndSet(true, false);
//			}
//		});
//		atomicRequest.setResultValuePrinter(value->String.format("Print %s",value));
//		serialRequest.setupSubActions(
//				atomicRequest,
//				parallelRequest
//		);
//		Semaphore semaphore = new Semaphore(0);
//		serialRequest.launch(
//				new Callback<Object>() {
//					@Override
//					public void accept(Object o) {
//						semaphore.release();
//						System.out.println(serialRequest.dumpCallingStatus());
//					}
//
//					@Override
//					public void onException(Exception e) {
//						semaphore.release();
//						System.out.println(serialRequest.dumpCallingStatus());
//					}
//
//					@Override
//					public void onCanceled() {
//						semaphore.release();
//						System.out.println(serialRequest.dumpCallingStatus());
//					}
//
//					@Override
//					public void onTimeout() {
//						semaphore.release();
//						System.out.println(serialRequest.dumpCallingStatus());
//					}
//				}
//		);
//		semaphore.acquire();
////		System.out.println(serialRequest.dumpCallingStatus());
//	}

}
