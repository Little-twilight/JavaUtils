package com.zhongyou.util.utils;


import com.zhongyou.util.function.BiConsumer;
import com.zhongyou.util.function.BooleanSupplier;
import com.zhongyou.util.function.Consumer;
import com.zhongyou.util.function.Function;
import com.zhongyou.util.function.Supplier;

import java.util.Objects;

/**
 * Function style utils
 */
public class Wnn {

	private Wnn() {

	}

	public static <T> T bool(boolean test, T forTrue, T forFalse) {
		return test ? forTrue : forFalse;
	}

	public static <T> T bool(BooleanSupplier test, T forTrue, T forFalse) {
		return bool(test.getAsBoolean(), forTrue, forFalse);
	}

	public static <T> Function<Boolean, T> bool(T forTrue, T forFalse) {
		return test -> bool(test, forTrue, forFalse);
	}

	/**
	 * when input not null, return input, else return default value
	 */
	public static <I> I d(Supplier<I> supplier, I defaultValue) {
		return d(supplier.get(), defaultValue);
	}

	/**
	 * when input not null, return input, else return default value
	 */
	public static <I> I d(I input, I defaultValue) {
		return input == null ? defaultValue : input;
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, O> Function<I, O> f_f(Function<I, O> operation) {
		return input -> f(input, operation, (O) null);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, O> Function<I, O> f_f(Function<I, O> operation, O defaultValue) {
		return input -> f(input, operation, defaultValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, O> O f(I input, Function<I, O> operation) {
		return f(input, operation, (O) null);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, O> O f(Function<I, O> operation, Supplier<I> supplier) {
		return f(supplier.get(), operation, (O) null);
	}


	/**
	 * when input not null, execute operation
	 */
	private static <I, O> O f(Function<I, O> operation, Supplier<I> supplier, O defaultReturnValue) {
		return f(supplier.get(), operation, defaultReturnValue);
	}

	/**
	 * when input not null, execute operation
	 */
	private static <I, O> O f(I input, Function<I, O> operation, O defaultReturnValue) {
		return d(input == null ? null : operation.apply(input), defaultReturnValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, O> Function<I, O> f_f(Function<M, O> operation, Function<I, M> mapper) {
		return input -> f(input, operation, mapper, (O) null);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, O> Function<I, O> f_f(Function<M, O> operation, Function<I, M> mapper, O defaultValue) {
		return input -> f(input, operation, mapper, defaultValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, O> O f(I input, Function<M, O> operation, Function<I, M> mapper) {
		return f(input, operation, mapper, (O) null);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, M, O> O f(Function<M, O> operation, Supplier<I> supplier, Function<I, M> mapper) {
		return f(supplier.get(), operation, mapper, (O) null);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, M, O> O f(Function<M, O> operation, Supplier<I> supplier, Function<I, M> mapper, O defaultReturnValue) {
		return f(supplier.get(), operation, mapper, defaultReturnValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, O> O f(I input, Function<M, O> operation, Function<I, M> mapper, O defaultReturnValue) {
		return f(f(input, mapper), operation, defaultReturnValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, OM, O> Function<I, O> f_f(Function<OM, O> operation, Function<I, IM> iMapper, Function<IM, OM> oMapper) {
		return input -> f(input, operation, iMapper, oMapper, null);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, OM, O> Function<I, O> f_f(Function<OM, O> operation, Function<I, IM> iMapper, Function<IM, OM> oMapper, O defaultValue) {
		return input -> f(input, operation, iMapper, oMapper, defaultValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, OM, O> O f(I input, Function<OM, O> operation, Function<I, IM> iMapper, Function<IM, OM> oMapper) {
		return f(input, operation, iMapper, oMapper, null);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, OM, O> O f(Function<OM, O> operation, Supplier<I> supplier, Function<I, IM> iMapper, Function<IM, OM> oMapper) {
		return f(supplier.get(), operation, iMapper, oMapper, null);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, OM, O> O f(Function<OM, O> operation, Supplier<I> supplier, Function<I, IM> iMapper, Function<IM, OM> oMapper, O defaultReturnValue) {
		return f(supplier.get(), operation, iMapper, oMapper, defaultReturnValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, OM, O> O f(I input, Function<OM, O> operation, Function<I, IM> iMapper, Function<IM, OM> oMapper, O defaultReturnValue) {
		return f(f(f(input, iMapper), oMapper), operation, defaultReturnValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I> void c(I input, Consumer<I> operation) {
		if (input != null) {
			operation.accept(input);
		}
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I> void c(Consumer<I> operation, Supplier<I> supplier) {
		c(supplier.get(), operation);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I> void c(Consumer<I> operation, Supplier<I> supplier, I defaultValue) {
		c(d(supplier.get(), defaultValue), operation);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I> Consumer<I> c_c(Consumer<I> operation) {
		return input -> c(input, operation);
	}
//
//    /**
//     * when input not null, execute operation
//     */
//    public static <I> Consumer<I> c_c(Consumer<I> operation, I defaultValue) {
//        return input -> c(d(input, defaultValue), operation);
//    }


	/**
	 * when input not null, execute operation
	 */
	public static <I, C> Consumer<I> c_c(Consumer<C> operation, Function<I, C> mapper) {
		return input -> c(input, operation, mapper);
	}

//    /**
//     * when input not null, execute operation
//     */
//    public static <I, C> Consumer<I> c_c(Consumer<C> operation, Function<I, C> mapper, C defaultValue) {
//        return input -> c(input, operation, mapper, defaultValue);
//    }

	/**
	 * when input not null, execute operation
	 */
	public static <I, C> void c(Consumer<C> operation, Supplier<I> supplier, Function<I, C> mapper) {
		c(supplier.get(), operation, mapper);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, C> void c(Consumer<C> operation, Supplier<I> supplier, Function<I, C> mapper, C defaultValue) {
		c(supplier.get(), operation, mapper, defaultValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, C> void c(I input, Consumer<C> operation, Function<I, C> mapper) {
		c(f(input, mapper), operation);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, C> void c(I input, Consumer<C> operation, Function<I, C> mapper, C defaultValue) {
		c(d(f(input, mapper), defaultValue), operation);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, C> void cb(BiConsumer<I, C> operation, Supplier<I> supplier, Function<I, C> mapper) {
		cb(supplier.get(), operation, mapper);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, C> void cb(BiConsumer<I, C> operation, Supplier<I> supplier, Function<I, C> mapper, C defaultValue) {
		cb(supplier.get(), operation, mapper, defaultValue);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, C> void cb(I input, BiConsumer<I, C> operation, Function<I, C> mapper) {
		c(input, c -> operation.accept(input, c), mapper);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, C> void cb(I input, BiConsumer<I, C> operation, Function<I, C> mapper, C defaultValue) {
		c(input, c -> operation.accept(input, c), mapper, defaultValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, C> Consumer<I> cb_c(BiConsumer<I, C> operation, Function<I, C> mapper) {
		return input -> cb(input, operation, mapper);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> void c(Consumer<C> operation, Supplier<I> supplier, Function<I, M> iMapper, Function<M, C> cMapper) {
		c(supplier.get(), operation, iMapper, cMapper);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> void c(Consumer<C> operation, Supplier<I> supplier, Function<I, M> iMapper, Function<M, C> cMapper, C defaultValue) {
		c(supplier.get(), operation, iMapper, cMapper, defaultValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> void c(I input, Consumer<C> operation, Function<I, M> iMapper, Function<M, C> cMapper) {
		c(f(input, cMapper, iMapper), operation);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> void c(I input, Consumer<C> operation, Function<I, M> iMapper, Function<M, C> cMapper, C defaultValue) {
		c(f(input, cMapper, iMapper, defaultValue), operation);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> Consumer<I> c_c(Consumer<C> operation, Function<I, M> iMapper, Function<M, C> cMapper) {
		return input -> c(input, operation, iMapper, cMapper);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> Consumer<I> c_c(Consumer<C> operation, Function<I, M> iMapper, Function<M, C> cMapper, C defaultValue) {
		return input -> c(input, operation, iMapper, cMapper, defaultValue);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> void cb(BiConsumer<I, C> operation, Supplier<I> supplier, Function<I, M> iMapper, Function<M, C> cMapper) {
		cb(supplier.get(), operation, iMapper, cMapper);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> void cb(BiConsumer<I, C> operation, Supplier<I> supplier, Function<I, M> iMapper, Function<M, C> cMapper, C defaultValue) {
		cb(supplier.get(), operation, iMapper, cMapper, defaultValue);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> void cb(I input, BiConsumer<I, C> operation, Function<I, M> iMapper, Function<M, C> cMapper) {
		c(f(input, cMapper, iMapper), c -> operation.accept(input, c));
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> void cb(I input, BiConsumer<I, C> operation, Function<I, M> iMapper, Function<M, C> cMapper, C defaultValue) {
		c(f(input, cMapper, iMapper, defaultValue), c -> operation.accept(input, c));
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> Consumer<I> cb_c(BiConsumer<I, C> operation, Function<I, M> iMapper, Function<M, C> cMapper) {
		return input -> cb(input, operation, iMapper, cMapper);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, M, C> Consumer<I> cb_c(BiConsumer<I, C> operation, Function<I, M> iMapper, Function<M, C> cMapper, C defaultValue) {
		return input -> cb(input, operation, iMapper, cMapper, defaultValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> void c(Consumer<C> operation, Supplier<I> supplier, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper) {
		c(supplier.get(), operation, iMapper, icMapper, cMapper);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> void c(Consumer<C> operation, Supplier<I> supplier, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper, C defaultValue) {
		c(supplier.get(), operation, iMapper, icMapper, cMapper, defaultValue);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> void c(I input, Consumer<C> operation, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper) {
		c(f(input, cMapper, iMapper, icMapper), operation);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> void c(I input, Consumer<C> operation, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper, C defaultValue) {
		c(f(input, cMapper, iMapper, icMapper, defaultValue), operation);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> Consumer<I> c_c(Consumer<C> operation, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper) {
		return input -> c(input, operation, iMapper, icMapper, cMapper);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> Consumer<I> c_c(Consumer<C> operation, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper, C defaultValue) {
		return input -> c(input, operation, iMapper, icMapper, cMapper, defaultValue);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> void cb(BiConsumer<I, C> operation, Supplier<I> supplier, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper) {
		cb(supplier.get(), operation, iMapper, icMapper, cMapper);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> void cb(BiConsumer<I, C> operation, Supplier<I> supplier, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper, C defaultValue) {
		cb(supplier.get(), operation, iMapper, icMapper, cMapper, defaultValue);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> void cb(I input, BiConsumer<I, C> operation, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper) {
		c(f(input, cMapper, iMapper, icMapper), c -> operation.accept(input, c));
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> void cb(I input, BiConsumer<I, C> operation, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper, C defaultValue) {
		c(f(input, cMapper, iMapper, icMapper, defaultValue), c -> operation.accept(input, c));
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> Consumer<I> cb_c(BiConsumer<I, C> operation, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper) {
		return input -> cb(input, operation, iMapper, icMapper, cMapper);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I, IM, CM, C> Consumer<I> cb_c(BiConsumer<I, C> operation, Function<I, IM> iMapper, Function<IM, CM> icMapper, Function<CM, C> cMapper, C defaultValue) {
		return input -> cb(input, operation, iMapper, icMapper, cMapper, defaultValue);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I> void r(Runnable operation, Supplier<I> supplier) {
		r(supplier.get(), operation);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <I> void r(I input, Runnable operation) {
		if (input != null) {
			operation.run();
		}
	}


	/**
	 * when input not null, execute operation
	 */
	public static <I> Chain<I> chain(I input) {
		return new Chain<>(() -> input);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <T> Chain<T> chain(Supplier<T> supplier) {
		return new Chain<>(supplier);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <T, U> Consumer<T> chain(Function<Chain<T>, Chain<U>> chainHop, Consumer<U> chainConsumer, U defaultValue) {
		return input -> chainHop.apply(chain(input)).commit(chainConsumer, defaultValue);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <T, V, U> Consumer<T> chain(Function<T, V> hopperTV, Function<V, U> hopperVU, Consumer<U> chainConsumer, U defaultValue) {
		return chain(
				chain -> chain.hop(hopperTV).hop(hopperVU),
				chainConsumer,
				defaultValue
		);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <T, V, W, U> Consumer<T> chain(Function<T, V> hopperTV, Function<V, W> hopperVW, Function<W, U> hopperWU, Consumer<U> chainConsumer, U defaultValue) {
		return chain(
				chain -> chain.hop(hopperTV).hop(hopperVW).hop(hopperWU),
				chainConsumer,
				defaultValue
		);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <T, V, W, X, U> Consumer<T> chain(Function<T, V> hopperTV, Function<V, W> hopperVW, Function<W, X> hopperWX, Function<X, U> hopperXU, Consumer<U> chainConsumer, U defaultValue) {
		return chain(
				chain -> chain.hop(hopperTV).hop(hopperVW).hop(hopperWX).hop(hopperXU),
				chainConsumer,
				defaultValue
		);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <T, V, W, X, Y, U> Consumer<T> chain(Function<T, V> hopperTV, Function<V, W> hopperVW, Function<W, X> hopperWX, Function<X, Y> hopperXY, Function<Y, U> hopperYU, Consumer<U> chainConsumer, U defaultValue) {
		return chain(
				chain -> chain.hop(hopperTV).hop(hopperVW).hop(hopperWX).hop(hopperXY).hop(hopperYU),
				chainConsumer,
				defaultValue
		);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <T, U> Consumer<T> chain(Function<Chain<T>, Chain<U>> chainMapper, BiConsumer<T, U> chainConsumer, U defaultValue) {
		return input -> chainMapper.apply(chain(input)).commit(u -> chainConsumer.accept(input, u), defaultValue);
	}


	/**
	 * when input not null, execute operation
	 */
	public static <T, V, U> Consumer<T> chain(Function<T, V> hopperTV, Function<V, U> hopperVU, BiConsumer<T, U> chainConsumer, U defaultValue) {
		return chain(
				chain -> chain.hop(hopperTV).hop(hopperVU),
				chainConsumer,
				defaultValue
		);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <T, V, W, U> Consumer<T> chain(Function<T, V> hopperTV, Function<V, W> hopperVW, Function<W, U> hopperWU, BiConsumer<T, U> chainConsumer, U defaultValue) {
		return chain(
				chain -> chain.hop(hopperTV).hop(hopperVW).hop(hopperWU),
				chainConsumer,
				defaultValue
		);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <T, V, W, X, U> Consumer<T> chain(Function<T, V> hopperTV, Function<V, W> hopperVW, Function<W, X> hopperWX, Function<X, U> hopperXU, BiConsumer<T, U> chainConsumer, U defaultValue) {
		return chain(
				chain -> chain.hop(hopperTV).hop(hopperVW).hop(hopperWX).hop(hopperXU),
				chainConsumer,
				defaultValue
		);
	}

	/**
	 * when input not null, execute operation
	 */
	public static <T, V, W, X, Y, U> Consumer<T> chain(Function<T, V> hopperTV, Function<V, W> hopperVW, Function<W, X> hopperWX, Function<X, Y> hopperXY, Function<Y, U> hopperYU, BiConsumer<T, U> chainConsumer, U defaultValue) {
		return chain(
				chain -> chain.hop(hopperTV).hop(hopperVW).hop(hopperWX).hop(hopperXY).hop(hopperYU),
				chainConsumer,
				defaultValue
		);
	}

	public static class Chain<T> {
		private Supplier<T> mSupplier;

		private Chain(Supplier<T> supplier) {
			mSupplier = supplier;
		}

		public void commit(Consumer<T> consumer, T defaultValue) {
			Objects.requireNonNull(defaultValue);
			c(d(mSupplier.get(), defaultValue), consumer);
		}

		public <U> Chain<U> hop(Function<T, U> hopper) {
			return new Chain<>(() -> f(hopper, mSupplier));
		}

	}


}
