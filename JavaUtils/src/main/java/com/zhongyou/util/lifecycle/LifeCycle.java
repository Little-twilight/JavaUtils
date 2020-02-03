package com.zhongyou.util.lifecycle;


import com.zhongyou.util.function.Predicate;

import java.util.Stack;

public class LifeCycle<T extends LifeCycle.Stage> {

	private Stack<T> mStageStack = new Stack<>();

	public boolean isInForeground(T stage) {
		return !mStageStack.isEmpty() && mStageStack.peek() == stage;
	}

	public void forwardStage(T stage) {
		if (!mStageStack.isEmpty()) {
			mStageStack.peek().onPause();
		}
		mStageStack.push(stage);
		stage.onEnter();
		onStageInForeground();
	}

	public boolean backwardStage() {
		if (!mStageStack.isEmpty()) {
			mStageStack.pop().onExit();
			if (!mStageStack.isEmpty()) {
				mStageStack.peek().onResume();
				onStageInForeground();
			} else {
				onStageInForeground();
			}
			return true;
		}
		return false;
	}

	public boolean backwardToStage(T stage) {
		if (!mStageStack.contains(stage)) {
			return false;
		}
		T peek;
		while ((peek = mStageStack.peek()) != stage) {
			peek.onExit();
			mStageStack.pop();
		}
		peek.onResume();
		onStageInForeground();
		return true;
	}

	public boolean backwardToStage(Predicate<T> target) {
		boolean test = false;
		for (T t : mStageStack) {
			if (target.test(t)) {
				test = true;
				break;
			}
		}
		if (!test) {
			return false;
		}
		T peek;
		while (!target.test((peek = mStageStack.peek()))) {
			mStageStack.pop().onExit();
		}
		peek.onResume();
		onStageInForeground();
		return true;
	}

	public boolean replaceTop(T replace) {
		if (mStageStack.isEmpty()) {
			return false;
		}
		mStageStack.peek().onExit();
		mStageStack.pop();
		mStageStack.push(replace);
		replace.onEnter();
		onStageInForeground();
		return true;
	}

	public boolean backwardToStageAndReplace(T stage, T replace) {
		if (!mStageStack.contains(stage)) {
			return false;
		}
		T peek;
		while ((peek = mStageStack.peek()) != stage) {
			peek.onExit();
			mStageStack.pop();
		}
		peek.onExit();
		mStageStack.pop();
		mStageStack.push(replace);
		replace.onResume();
		onStageInForeground();
		return true;
	}

	public boolean backwardToStageAndReplace(Predicate<T> target, T replace) {
		boolean test = false;
		for (T t : mStageStack) {
			if (target.test(t)) {
				test = true;
				break;
			}
		}
		if (!test) {
			return false;
		}
		T peek;
		while (!target.test((peek = mStageStack.peek()))) {
			peek.onExit();
			mStageStack.pop();
		}
		peek.onExit();
		mStageStack.pop();
		mStageStack.push(replace);
		replace.onResume();
		onStageInForeground();
		return true;
	}

	protected void onStageInForeground() {

	}

	public T getCurrentStage() {
		return mStageStack.isEmpty() ? null : mStageStack.peek();
	}

	public int getStackedStageCount() {
		return mStageStack.size();
	}

	public interface Stage {
		default void onEnter() {

		}

		default void onExit() {
		}

		default void onPause() {
		}

		default void onResume() {
		}
	}
}


