package io.github.toomanylimits.wasmj.parsing.instruction;

import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.util.ListUtils;

import java.util.List;

public sealed interface StackType {

    List<ValType> inTypes();
    List<ValType> outTypes();

    // Return a stack type which does the opposite of this one.
    // For example, if this pops an f32, then an i32, then pushes
    // an externref, then an i64:
    // The inverse will pop an i64, then an externref, then push
    // an i32, then push an f32.
    default StackType inverse() {
        if (this == SPECIAL)
            return SPECIAL;
        return new StackType.Basic(ListUtils.reversed(outTypes()), ListUtils.reversed(inTypes()));
    }


    // Special: when this instruction doesn't always have the same stack type.
    // Must be handled specially!
    StackType SPECIAL = new Basic(null, null);
    final class Special implements StackType {
        public List<ValType> inTypes() { throw new UnsupportedOperationException(); }
        public List<ValType> outTypes() { throw new UnsupportedOperationException(); }
    }
    record Basic(List<ValType> inTypes, List<ValType> outTypes) implements StackType { }

    StackType nop = consume();

    StackType i32_i32 = unop(ValType.I32);
    StackType i64_i64 = unop(ValType.I64);
    StackType f32_f32 = unop(ValType.F32);
    StackType f64_f64 = unop(ValType.F64);
    StackType v128_v128 = unop(ValType.V128);

    StackType i32_ = consume(ValType.I32);
    StackType i32i32_ = consume(ValType.I32, ValType.I32);
    StackType i32i32i32_ = consume(ValType.I32, ValType.I32, ValType.I32);
    StackType i32i64_ = consume(ValType.I32, ValType.I64);
    StackType i32f32_ = consume(ValType.I32, ValType.F32);
    StackType i32f64_ = consume(ValType.I32, ValType.F64);
    StackType i32v128_ = consume(ValType.I32, ValType.V128);

    StackType i32v128_v128 = new Basic(List.of(ValType.I32, ValType.V128), List.of(ValType.V128));
    StackType v128v128v128_v128 = new Basic(List.of(ValType.V128, ValType.V128, ValType.V128), List.of(ValType.V128));

    StackType v128i32_v128 = replacelane(ValType.I32);
    StackType v128i64_v128 = replacelane(ValType.I64);
    StackType v128f32_v128 = replacelane(ValType.F32);
    StackType v128f64_v128 = replacelane(ValType.F64);

    StackType _i32 = produce(ValType.I32);
    StackType _i64 = produce(ValType.I64);
    StackType _f32 = produce(ValType.F32);
    StackType _f64 = produce(ValType.F64);
    StackType _funcref = produce(ValType.FUNCREF);
    StackType _externref = produce(ValType.EXTERNREF);
    StackType _v128 = produce(ValType.V128);

    StackType i32i32_i32 = binop(ValType.I32);
    StackType i64i64_i64 = binop(ValType.I64);
    StackType f32f32_f32 = binop(ValType.F32);
    StackType f64f64_f64 = binop(ValType.F64);
    StackType v128v128_v128 = binop(ValType.V128);

    StackType i64i64_i32 = bicompare(ValType.I64);
    StackType f32f32_i32 = bicompare(ValType.F32);
    StackType f64f64_i32 = bicompare(ValType.F64);
    StackType v128v128_i32 = bicompare(ValType.V128);

    StackType i64_i32 = ab(ValType.I64, ValType.I32);
    StackType f32_i32 = ab(ValType.F32, ValType.I32);
    StackType f64_i32 = ab(ValType.F64, ValType.I32);
    StackType v128_i32 = ab(ValType.V128, ValType.I32);
    StackType ref_i32 = ab(ValType.EXTERNREF, ValType.I32);

    StackType i32_i64 = ab(ValType.I32, ValType.I64);
    StackType f32_i64 = ab(ValType.F32, ValType.I64);
    StackType f64_i64 = ab(ValType.F64, ValType.I64);
    StackType v128_i64 = ab(ValType.V128, ValType.I64);

    StackType i32_f32 = ab(ValType.I32, ValType.F32);
    StackType i64_f32 = ab(ValType.I64, ValType.F32);
    StackType f64_f32 = ab(ValType.F64, ValType.F32);
    StackType v128_f32 = ab(ValType.V128, ValType.F32);

    StackType i32_f64 = ab(ValType.I32, ValType.F64);
    StackType i64_f64 = ab(ValType.I64, ValType.F64);
    StackType f32_f64 = ab(ValType.F32, ValType.F64);
    StackType v128_f64 = ab(ValType.V128, ValType.F64);

    StackType i32_v128 = ab(ValType.I32, ValType.V128);
    StackType i64_v128 = ab(ValType.I64, ValType.V128);
    StackType f32_v128 = ab(ValType.F32, ValType.V128);
    StackType f64_v128 = ab(ValType.F64, ValType.V128);


    private static StackType unop(ValType t) { return new Basic(List.of(t), List.of(t)); }
    private static StackType consume(ValType... types) { return new Basic(List.of(types), List.of()); }
    private static StackType produce(ValType... types) { return new Basic(List.of(), List.of(types)); }
    private static StackType binop(ValType t) { return new StackType.Basic(List.of(t, t), List.of(t)); }
    private static StackType bicompare(ValType t) { return new StackType.Basic(List.of(t, t), List.of(ValType.I32)); }
    private static StackType ab(ValType a, ValType b) { return new StackType.Basic(List.of(a), List.of(b)); }
    private static StackType replacelane(ValType t) { return new StackType.Basic(List.of(ValType.V128, t), List.of(ValType.V128)); }

}