package io.github.toomanylimits.wasmj.compiling.helpers;

/**
 * Static methods to name some things
 */
public class Names {

    // General
    public static String className(String moduleName) { return "wasmj_modules/" + moduleName; }
    public static String initMethodName() { return "init"; }
    public static String limiterFieldName() { return "limiter"; }

    // Data / elements
    public static String dataFieldName(int declaredIndex) { return "data_" + declaredIndex; }


    // Functions
    public static String funcName(int declaredIndex) { return "func_" + declaredIndex; }
    public static String glueFuncName(int funcImportIndex) { return "glue_func_" + funcImportIndex; }
    public static String globalInstanceFieldName(String javaModuleName) { return "global_instance_for_" + javaModuleName; }
    public static String exportFuncName(String memberName) { return "export_func_" + memberName; }

    // Globals
    public static String globalName(int declaredIndex) { return "global_" + declaredIndex; }

    // Tables
    public static String tableName(int declaredIndex) { return "table_" + declaredIndex; }

    // Memories
    public static String memoryName(int declaredIndex) { return "memory_" + declaredIndex; }

}
