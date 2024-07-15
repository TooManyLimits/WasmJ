package io.github.toomanylimits.wasmj.compiling.helpers;

/**
 * Static methods to name some things
 */
public class Names {

    // General
    public static String className(String moduleName) { return "wasmj_modules/" + moduleName; }
    public static String initMethodName() { return "init"; }
    public static String limiterFieldName() { return "limiter"; }
    public static String exportedFunctionsFieldName() { return "exportedFunctions"; }

    // The special externref table key, used for @ExternrefTableAccess, and the table allocator function name
    public static final String SPECIAL_EXTERNREF_TABLE_EXPORT_KEY = "wasmj-externref";
    public static String externrefTableAccessorImplClassName(String moduleName) { return "accessor_impls/" + moduleName; }
    public static String externrefTableAccessorFieldName() { return "externrefTableAccessor"; }

    // Data / elements
    public static String dataFieldName(int declaredIndex) { return "data_" + declaredIndex; }
    public static String elemFieldName(int declaredIndex) { return "elem_" + declaredIndex; }

    // Functions
    public static String funcName(int declaredIndex) { return "func_" + declaredIndex; }
    public static String glueFuncName(int funcImportIndex) { return "glue_func_" + funcImportIndex; }
    public static String globalInstanceFieldName(String javaModuleName) { return "global_instance_for_" + javaModuleName; }
    public static String exportFuncName(String memberName) { return "export_func_" + memberName; }

    // Globals
    public static String globalName(int declaredIndex) { return "global_" + declaredIndex; }
    public static String exportGlobalGetterName(String memberName) { return "global_get_" + memberName; }
    public static String exportGlobalSetterName(String memberName) { return "global_set_" + memberName; }

    // Tables
    public static String tableName(int declaredIndex) { return "table_" + declaredIndex; }
    public static String exportTableGetterName(String memberName) { return "table_get_" + memberName; }
    public static String exportTableSetterName(String memberName) { return "table_set_" + memberName; }

    // Memories
    public static String memoryName(int declaredIndex) { return "memory_" + declaredIndex; }

}
