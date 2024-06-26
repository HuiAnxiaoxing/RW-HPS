package com.github.dr.rwserver.util.alone;

import com.github.dr.rwserver.func.BooleanIf;
import com.github.dr.rwserver.util.IsUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 链式Switch
 * @author Dr
 */
public class Switch<T> {

    /**
     * 输入值
     */
    final T input;

    /**
     * 当前的条件
     */
    Predicate<T> condition = null;
    BooleanIf<T> conditionBooleanIf = null;

    /**
     * 是否已经存在某个条件被满足
     */
    boolean met;

    private Switch(T input) {
        this.input = input;
    }

    /**
     * 在指定的值上使用 Switch，返回用于消费的 Switch 实例
     *
     * @param value 指定的值
     * @param <V>   值的类型
     * @return 用于消费的 Switch 实例
     */
    public static <V> ConsumptionSwitch<V> on(V value) {
        return new ConsumptionSwitch<>(value);
    }

    /**
     * 在指定的输入值上使用 Switch，返回用于求值的 Switch 实例
     *
     * @param input 指定的输入值
     * @param <I>   输入类型
     * @param <O>   输出类型
     * @return 用于消费的 Switch 实例
     */
    public static <I, O> EvaluationSwitch<I, O> in(I input) {
        return new EvaluationSwitch<>(input);
    }

    /**
     * 判断输入是否和给定的目标相等
     *
     * @param target 给定的目标
     * @return 当前 Switch 实例
     */
    protected Switch<T> is(T target) {
        return when(Predicate.isEqual(target));
    }

    /**
     * 判断输入是否和给定的目标相等
     *
     * @param target 给定的目标
     * @return 当前 Switch 实例
     */
    protected Switch<T> isCustom(BooleanIf<T> target) {
        return whenCustom(target);
    }

    /**
     * 判断输入是否存在给定的一群值中
     *
     * @param values 给定的一群值
     */
    @SuppressWarnings("unchecked")
    protected void isIn(T... values) {
        Objects.requireNonNull(values);

        when(e -> {
            // 不直接使用 Arrays.asList(values).contains(e)，提升效率
            for (T value : values) {
                if (Objects.equals(e, value)) {
                    return true;
                }
            }

            return false;
        });
    }

    /**
     * 设定输入值需要满足的条件
     *
     * @param condition 输入值需要满足的条件
     * @return 当前 Switch 实例
     */
    protected Switch<T> when(Predicate<T> condition) {
        // 短路处理
        if (met) { return this; }

        this.condition = Objects.requireNonNull(condition);
        return this;
    }

    /**
     * 设定输入值需要满足的条件
     *
     * @param condition 输入值需要满足的条件
     * @return 当前 Switch 实例
     */
    protected Switch<T> whenCustom(BooleanIf<T> condition) {
        // 短路处理
        if (met) { return this; }

        this.conditionBooleanIf = condition;
        return this;
    }

    void requireNonNullCondition() {
        if (condition == null && conditionBooleanIf == null) {
            throw new IllegalStateException("A condition must be set first");
        }
    }

    void requireNonNullArgAndCondition(Object arg) {
        Objects.requireNonNull(arg, "Null argument " + arg.getClass().getName());
        requireNonNullCondition();
    }

    /**
     * 用于消费的 Switch
     *
     * @param <T> 输入值的类型
     */
    public static class ConsumptionSwitch<T> extends Switch<T> {

        ConsumptionSwitch(T value) {
            super(value);
        }

        @Override
        public ConsumptionSwitch<T> is(T target) {
            super.is(target);
            return this;
        }

        @Override
        @SafeVarargs
        public final void isIn(T... values) {
            super.isIn(values);
        }

        @Override
        public ConsumptionSwitch<T> when(Predicate<T> condition) {
            super.when(condition);
            return this;
        }

        /**
         * 满足某个条件时，对输入值进行消费操作
         *
         * @param action 消费动作
         * @return 当前 Switch 实例
         */
        public ConsumptionSwitch<T> thenAccept(Consumer<T> action) {
            if (met) { return this; }

            requireNonNullArgAndCondition(action);

            if (condition.test(input)) {
                action.accept(input);
                // 标记已经存在过满足的条件
                met = true;
            }

            return this;
        }

        /**
         * 不满足任一条件时，对输入值进行消费操作
         *
         * @param action 消费动作
         */
        public void elseAccept(Consumer<T> action) {
            // 之前存在被满足的条件，直接返回
            if (met) { return; }

            Objects.requireNonNull(action);
            action.accept(input);
        }
    }

    /**
     * 用于求值的 Switch
     *
     * @param <I> 输入值的类型
     * @param <O> 输出值的类型
     */
    public static class EvaluationSwitch<I, O> extends Switch<I> {

        /**
         * 输出
         */
        private O output;

        EvaluationSwitch(I input) {
            super(input);
        }

        @Override
        public EvaluationSwitch<I, O> is(I target) {
            super.is(target);
            return this;
        }

        @Override
        public EvaluationSwitch<I, O> isCustom(BooleanIf<I> target) {
            super.isCustom(target);
            return this;
        }

        @Override
        @SafeVarargs
        public final void isIn(I... values) {
            super.isIn(values);
        }

        @Override
        public EvaluationSwitch<I, O> when(Predicate<I> condition) {
            super.when(condition);
            return this;
        }

        @Override
        public EvaluationSwitch<I, O> whenCustom(BooleanIf<I> condition) {
            super.whenCustom(condition);
            return this;
        }

        /**
         * 设定当前 EvaluationSwitch 的输出值的类型
         *
         * @param type 输出值的类型
         * @param <R>  指定的输出值类型
         * @return 当前的 EvaluationSwitch 实例
         */
        @SuppressWarnings("unchecked")
        public <R> EvaluationSwitch<I, R> out(Class<? extends R> type) {
            return (EvaluationSwitch<I, R>) this;
        }

        /**
         * 满足某个条件时，进行求值操作
         *
         * @param value 指定的输出值
         * @return 当前 Switch 实例
         */
        public EvaluationSwitch<I, O> thenGet(O value) {
            if (met) { return this; }

            requireNonNullCondition();

            // 满足条件
            if (IsUtil.notIsBlank(condition)) {
                if (condition.test(input)) {
                    output = value;
                    // 标记已经产生输出值
                    met = true;
                }
            } else {
                if (conditionBooleanIf.get(input)) {
                    output = value;
                    met = true;
                }
            }
            return this;
        }

        /**
         * 不满足任一条件时，进行求值操作
         *
         * @param value 指定的输出值
         * @return 如果某个条件被满足，则返回满足条件时所求的值；否则返回指定的输出值
         */
        public O elseGet(O value) {
            return met ? output : value;
        }

        /**
         * 满足某个条件时，使用 Function 进行求值操作，当前 Switch 实例的输入值会作为 Function 的输入
         *
         * @param mapper 指定的 Function
         * @return 当前 Switch 实例
         */
        public EvaluationSwitch<I, O> thenApply(Function<I, O> mapper) {
            if (met) { return this; }

            requireNonNullArgAndCondition(mapper);

            if (condition.test(input)) {
                output = mapper.apply(input);
                met = true;
            }

            return this;
        }

        /**
         * 不满足任一条件时，使用 Function 进行求值操作，当前 Switch 实例的输入值会作为 Function 的输入
         *
         * @param mapper 指定的 Function
         * @return 如果某个条件被满足，则返回满足条件时所求的值；否则返回指定的 Function 产生的输出值
         */
        public O elseApply(Function<I, O> mapper) {
            Objects.requireNonNull(mapper);

            return met ? output : mapper.apply(input);
        }

        /**
         * 满足某个条件时，使用 Supplier 进行求值操作
         *
         * @param supplier 指定的 Supplier
         * @return 当前 Switch 实例
         */
        public EvaluationSwitch<I, O> thenSupply(Supplier<O> supplier) {
            if (met) { return this; }

            requireNonNullArgAndCondition(supplier);

            if (condition.test(input)) {
                output = supplier.get();
                met = true;
            }

            return this;
        }

        /**
         * 不满足任一条件时，使用 Supplier 进行求值操作
         *
         * @param supplier 指定的 Supplier
         * @return 如果某个条件被满足，则返回满足条件时所求的值；否则返回指定的 Supplier 产生的输出值
         */
        public O elseSupply(Supplier<O> supplier) {
            Objects.requireNonNull(supplier);

            return met ? output : supplier.get();
        }

        /**
         * 直接获取输出值
         *
         * @return 使用 Optional 包装的输出值
         */
        public Optional<O> obtain() {
            return Optional.ofNullable(output);
        }
    }

}