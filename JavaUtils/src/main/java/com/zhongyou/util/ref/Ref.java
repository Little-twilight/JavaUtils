package com.zhongyou.util.ref;

public class Ref<T> {
    public T value;

    public Ref(){

    }

    public Ref(T value) {
        this.value = value;
    }


    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
