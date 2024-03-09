package io.github.toomanylimits.wasmj.compiler;

// If the values are -1, then the compiler won't track that sandbox value
public record CompileFlags(long maxInstructions, long maxByteArray, long maxJvmHeap) {

    public static CompileFlags empty() { return new CompileFlags(-1, -1, -1); }

    public CompileFlags withMaxInstructions(long maxInstructions) { return new CompileFlags(maxInstructions, maxByteArray, maxJvmHeap); }
    public CompileFlags withMaxByteArray(long maxByteArray) { return new CompileFlags(maxInstructions, maxByteArray, maxJvmHeap); }
    public CompileFlags withMaxJvmHeap(long maxJvmHeap) { return new CompileFlags(maxInstructions, maxByteArray, maxJvmHeap); }

}
