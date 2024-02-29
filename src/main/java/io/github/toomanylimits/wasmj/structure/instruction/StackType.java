package io.github.toomanylimits.wasmj.structure.instruction;

import io.github.toomanylimits.wasmj.structure.types.ValType;

import java.util.List;

public sealed interface StackType {

    List<ValType> inTypes();
    List<ValType> outTypes();

    // Special: when this instruction doesn't always have the same stack type.
    // Must be handled specially!
    StackType SPECIAL = new Basic(null, null);
    final class Special implements StackType {
        public List<ValType> inTypes() { throw new UnsupportedOperationException(); }
        public List<ValType> outTypes() { throw new UnsupportedOperationException(); }
    }
    record Basic(List<ValType> inTypes, List<ValType> outTypes) implements StackType { }

    StackType nop = consume();

    StackType i32_i32 = unop(ValType.i32);
    StackType i64_i64 = unop(ValType.i64);
    StackType f32_f32 = unop(ValType.f32);
    StackType f64_f64 = unop(ValType.f64);
    StackType v128_v128 = unop(ValType.v128);

    StackType i32_ = consume(ValType.i32);
    StackType i32i32_ = consume(ValType.i32, ValType.i32);
    StackType i32i32i32_ = consume(ValType.i32, ValType.i32, ValType.i32);
    StackType i32i64_ = consume(ValType.i32, ValType.i64);
    StackType i32f32_ = consume(ValType.i32, ValType.f32);
    StackType i32f64_ = consume(ValType.i32, ValType.f64);
    StackType i32v128_ = consume(ValType.i32, ValType.v128);

    StackType i32v128_v128 = new Basic(List.of(ValType.i32, ValType.v128), List.of(ValType.v128));
    StackType v128v128v128_v128 = new Basic(List.of(ValType.v128, ValType.v128, ValType.v128), List.of(ValType.v128));

    StackType v128i32_v128 = replacelane(ValType.i32);
    StackType v128i64_v128 = replacelane(ValType.i64);
    StackType v128f32_v128 = replacelane(ValType.f32);
    StackType v128f64_v128 = replacelane(ValType.f64);

    StackType _i32 = produce(ValType.i32);
    StackType _i64 = produce(ValType.i64);
    StackType _f32 = produce(ValType.f32);
    StackType _f64 = produce(ValType.f64);
    StackType _funcref = produce(ValType.funcref);
    StackType _externref = produce(ValType.externref);
    StackType _v128 = produce(ValType.v128);

    StackType i32i32_i32 = binop(ValType.i32);
    StackType i64i64_i64 = binop(ValType.i64);
    StackType f32f32_f32 = binop(ValType.f32);
    StackType f64f64_f64 = binop(ValType.f64);
    StackType v128v128_v128 = binop(ValType.v128);

    StackType i64i64_i32 = bicompare(ValType.i64);
    StackType f32f32_i32 = bicompare(ValType.f32);
    StackType f64f64_i32 = bicompare(ValType.f64);
    StackType v128v128_i32 = bicompare(ValType.v128);

    StackType i64_i32 = ab(ValType.i64, ValType.i32);
    StackType f32_i32 = ab(ValType.f32, ValType.i32);
    StackType f64_i32 = ab(ValType.f64, ValType.i32);
    StackType v128_i32 = ab(ValType.v128, ValType.i32);
    StackType ref_i32 = ab(ValType.externref, ValType.i32);

    StackType i32_i64 = ab(ValType.i32, ValType.i64);
    StackType f32_i64 = ab(ValType.f32, ValType.i64);
    StackType f64_i64 = ab(ValType.f64, ValType.i64);
    StackType v128_i64 = ab(ValType.v128, ValType.i64);

    StackType i32_f32 = ab(ValType.i32, ValType.f32);
    StackType i64_f32 = ab(ValType.i64, ValType.f32);
    StackType f64_f32 = ab(ValType.f64, ValType.f32);
    StackType v128_f32 = ab(ValType.v128, ValType.f32);

    StackType i32_f64 = ab(ValType.i32, ValType.f64);
    StackType i64_f64 = ab(ValType.i64, ValType.f64);
    StackType f32_f64 = ab(ValType.f32, ValType.f64);
    StackType v128_f64 = ab(ValType.v128, ValType.f64);

    StackType i32_v128 = ab(ValType.i32, ValType.v128);
    StackType i64_v128 = ab(ValType.i64, ValType.v128);
    StackType f32_v128 = ab(ValType.f32, ValType.v128);
    StackType f64_v128 = ab(ValType.f64, ValType.v128);


    private static StackType unop(ValType t) { return new Basic(List.of(t), List.of(t)); }
    private static StackType consume(ValType... types) { return new Basic(List.of(types), List.of()); }
    private static StackType produce(ValType... types) { return new Basic(List.of(), List.of(types)); }
    private static StackType binop(ValType t) { return new StackType.Basic(List.of(t, t), List.of(t)); }
    private static StackType bicompare(ValType t) { return new StackType.Basic(List.of(t, t), List.of(ValType.i32)); }
    private static StackType ab(ValType a, ValType b) { return new StackType.Basic(List.of(a), List.of(b)); }
    private static StackType replacelane(ValType t) { return new StackType.Basic(List.of(ValType.v128, t), List.of(ValType.v128)); }

}