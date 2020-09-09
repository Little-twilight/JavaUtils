package com.zhongyou.util.utils;

import com.zhongyou.util.function.Callback;

public class CallbackDelegate<T> implements Callback<T> {

    private Callback<T> mDelegate;

    public CallbackDelegate(Callback<T> delegate) {
        mDelegate = delegate;
    }

    @Override
    public final void accept(T t) {
        acceptDelegate(t);
        mDelegate.accept(t);
    }

    @Override
    public final void onCanceled() {
        onCanceledDelegate();
        mDelegate.onCanceled();
    }

    @Override
    public final void onException(Exception e) {
        onExceptionDelegate(e);
        mDelegate.onException(e);
    }

    @Override
    public final void onTimeout() {
        onTimeoutDelegate();
        mDelegate.onTimeout();
    }

    protected void acceptDelegate(T t) {
        defaultCallbackReaction();
    }

    protected void onCanceledDelegate() {
        defaultCallbackReaction();
    }

    protected void onExceptionDelegate(Exception e) {
        defaultCallbackReaction();
    }

    protected void onTimeoutDelegate() {
        defaultCallbackReaction();
    }

    protected void defaultCallbackReaction(){

    }
}
