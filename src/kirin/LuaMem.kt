package kirin

import kirin.CLib.CharPtr
import kirin.LuaState.lua_State
import kirin.LuaObject.TValue
import kirin.LuaObject.TString
import kirin.LuaObject.LocVar
import kirin.LuaState.CallInfo
import kirin.LuaObject.Proto
import kirin.LuaObject.Node
import kirin.LuaState.GCObject
import kirin.LuaObject.UpVal
import kirin.LuaObject.Closure
import kirin.LuaObject.Table
import kirin.LuaObject.Udata
import kirin.LuaObject.ArrayElement

//
// ** $Id: lmem.c,v 1.70.1.1 2007/12/27 13:02:25 roberto Exp $
// ** Interface to Memory Manager
// ** See Copyright Notice in lua.h
//
object LuaMem {
    const val MEMERRMSG = "not enough memory"
    //-------------------------------
    fun luaM_reallocv_char(L: lua_State?, block: CharArray?, new_size: Int, t: ClassType): CharArray {
        return luaM_realloc__char(L, block, new_size, t) as CharArray
    }

    fun luaM_reallocv_TValue(
        L: lua_State?,
        block: Array<TValue?>?,
        new_size: Int,
        t: ClassType
    ): Array<TValue?> {
        return luaM_realloc__TValue(L, block, new_size, t) as Array<TValue?>
    }

    fun luaM_reallocv_TString(
        L: lua_State?,
        block: Array<TString?>?,
        new_size: Int,
        t: ClassType
    ): Array<TString?> {
        return luaM_realloc__TString(L, block, new_size, t) as Array<TString?>
    }

    fun luaM_reallocv_CallInfo(
        L: lua_State?,
        block: Array<CallInfo?>?,
        new_size: Int,
        t: ClassType
    ): Array<CallInfo?> {
        return luaM_realloc__CallInfo(L, block, new_size, t) as Array<CallInfo?>
    }

    fun luaM_reallocv_long(L: lua_State?, block: LongArray?, new_size: Int, t: ClassType): LongArray {
        return luaM_realloc__long(L, block, new_size, t) as LongArray
    }

    fun luaM_reallocv_int(L: lua_State?, block: IntArray?, new_size: Int, t: ClassType): IntArray {
        return luaM_realloc__int(L, block, new_size, t) as IntArray
    }

    fun luaM_reallocv_Proto(
        L: lua_State?,
        block: Array<Proto?>?,
        new_size: Int,
        t: ClassType
    ): Array<Proto?> {
        return luaM_realloc__Proto(L, block, new_size, t) as Array<Proto?>
    }

    fun luaM_reallocv_LocVar(
        L: lua_State?,
        block: Array<LocVar?>?,
        new_size: Int,
        t: ClassType
    ): Array<LocVar?> {
        return luaM_realloc__LocVar(L, block, new_size, t) as Array<LocVar?>
    }

    fun luaM_reallocv_Node(
        L: lua_State?,
        block: Array<Node?>?,
        new_size: Int,
        t: ClassType
    ): Array<Node?> {
        return luaM_realloc__Node(L, block, new_size, t) as Array<Node?>
    }

    fun luaM_reallocv_GCObject(
        L: lua_State?,
        block: Array<GCObject?>?,
        new_size: Int,
        t: ClassType
    ): Array<GCObject> {
        return luaM_realloc__GCObject(L, block, new_size, t) as Array<GCObject>
    }

    //-------------------------------
///#define luaM_freemem(L, b, s)	luaM_realloc_(L, (b), (s), 0)
///#define luaM_free(L, b)		luaM_realloc_(L, (b), sizeof(*(b)), 0)
//public static void luaM_freearray(lua_State L, object b, int n, Type t) { luaM_reallocv(L, b, n, 0, Marshal.SizeOf(b)); }
// C# has it's own gc, so nothing to do here...in theory...
    fun luaM_freemem_Udata(L: lua_State?, b: Udata, t: ClassType) {
        luaM_realloc__Udata(L, arrayOf(b), 0, t)
    }

    fun luaM_freemem_TString(L: lua_State?, b: TString?, t: ClassType) {
        luaM_realloc__TString(L, arrayOf(b), 0, t)
    }

    //-------------------------------
    fun luaM_free_Table(L: lua_State?, b: Table, t: ClassType) {
        luaM_realloc__Table(L, arrayOf(b), 0, t)
    }

    fun luaM_free_UpVal(L: lua_State?, b: UpVal, t: ClassType) {
        luaM_realloc__UpVal(L, arrayOf(b), 0, t)
    }

    fun luaM_free_Proto(L: lua_State?, b: Proto?, t: ClassType) {
        luaM_realloc__Proto(L, arrayOf(b), 0, t)
    }

    //-------------------------------
    fun luaM_freearray_long(L: lua_State?, b: LongArray?, t: ClassType) {
        luaM_reallocv_long(L, b, 0, t)
    }

    fun luaM_freearray_Proto(L: lua_State?, b: Array<Proto?>?, t: ClassType) {
        luaM_reallocv_Proto(L, b, 0, t)
    }

    fun luaM_freearray_TValue(L: lua_State?, b: Array<TValue?>?, t: ClassType) {
        luaM_reallocv_TValue(L, b, 0, t)
    }

    fun luaM_freearray_int(L: lua_State?, b: IntArray?, t: ClassType) {
        luaM_reallocv_int(L, b, 0, t)
    }

    fun luaM_freearray_LocVar(L: lua_State?, b: Array<LocVar?>?, t: ClassType) {
        luaM_reallocv_LocVar(L, b, 0, t)
    }

    fun luaM_freearray_TString(L: lua_State?, b: Array<TString?>?, t: ClassType) {
        luaM_reallocv_TString(L, b, 0, t)
    }

    fun luaM_freearray_Node(L: lua_State?, b: Array<Node?>?, t: ClassType) {
        luaM_reallocv_Node(L, b, 0, t)
    }

    fun luaM_freearray_CallInfo(L: lua_State?, b: Array<CallInfo?>?, t: ClassType) {
        luaM_reallocv_CallInfo(L, b, 0, t)
    }

    fun luaM_freearray_GCObject(L: lua_State?, b: Array<GCObject?>?, t: ClassType) {
        luaM_reallocv_GCObject(L, b, 0, t)
    }

    //-------------------------------
//public static T luaM_malloc<T>(lua_State L, ClassType t)
//{
//	return (T)luaM_realloc_<T>(L, t);
//}
    fun luaM_new_Proto(L: lua_State?, t: ClassType): Proto {
        return luaM_realloc__Proto(L, t) as Proto
    }

    fun luaM_new_Closure(L: lua_State?, t: ClassType): Closure {
        return luaM_realloc__Closure(L, t) as Closure
    }

    fun luaM_new_UpVal(L: lua_State?, t: ClassType): UpVal {
        return luaM_realloc__UpVal(L, t) as UpVal
    }

    fun luaM_new_lua_State(L: lua_State?, t: ClassType): lua_State {
        return luaM_realloc__lua_State(L, t) as lua_State
    }

    fun luaM_new_Table(L: lua_State?, t: ClassType): Table {
        return luaM_realloc__Table(L, t) as Table
    }

    //-------------------------------
    fun luaM_newvector_long(L: lua_State?, n: Int, t: ClassType): LongArray {
        return luaM_reallocv_long(L, null, n, t)
    }

    fun luaM_newvector_TString(L: lua_State?, n: Int, t: ClassType): Array<TString?> {
        return luaM_reallocv_TString(L, null, n, t)
    }

    fun luaM_newvector_LocVar(L: lua_State?, n: Int, t: ClassType): Array<LocVar?> {
        return luaM_reallocv_LocVar(L, null, n, t)
    }

    fun luaM_newvector_int(L: lua_State?, n: Int, t: ClassType): IntArray {
        return luaM_reallocv_int(L, null, n, t)
    }

    fun luaM_newvector_Proto(L: lua_State?, n: Int, t: ClassType): Array<Proto?> {
        return luaM_reallocv_Proto(L, null, n, t)
    }

    fun luaM_newvector_TValue(L: lua_State?, n: Int, t: ClassType): Array<TValue?> {
        return luaM_reallocv_TValue(L, null, n, t)
    }

    fun luaM_newvector_CallInfo(L: lua_State?, n: Int, t: ClassType): Array<CallInfo?> {
        return luaM_reallocv_CallInfo(L, null, n, t)
    }

    fun luaM_newvector_Node(L: lua_State?, n: Int, t: ClassType): Array<Node?> {
        return luaM_reallocv_Node(L, null, n, t)
    }

    //-------------------------------
    fun luaM_growvector_long(
        L: lua_State?,
        v: Array<LongArray?>,
        nelems: Int,
        size: IntArray,
        limit: Int,
        e: CharPtr?,
        t: ClassType
    ) { //ref - ref
        if (nelems + 1 > size[0]) {
            v[0] = luaM_growaux__long(L, v, size, limit, e, t) //ref - ref
        }
    }

    fun luaM_growvector_Proto(
        L: lua_State?,
        v: Array<Array<Proto?>?>,
        nelems: Int,
        size: IntArray,
        limit: Int,
        e: CharPtr?,
        t: ClassType
    ) { //ref - ref
        if (nelems + 1 > size[0]) {
            v[0] = luaM_growaux__Proto(L, v, size, limit, e, t) //ref - ref
        }
    }

    fun luaM_growvector_TString(
        L: lua_State?,
        v: Array<Array<TString?>?>,
        nelems: Int,
        size: IntArray,
        limit: Int,
        e: CharPtr?,
        t: ClassType
    ) { //ref - ref
        if (nelems + 1 > size[0]) {
            v[0] = luaM_growaux__TString(L, v, size, limit, e, t) //ref - ref
        }
    }

    fun luaM_growvector_TValue(
        L: lua_State?,
        v: Array<Array<TValue?>?>,
        nelems: Int,
        size: IntArray,
        limit: Int,
        e: CharPtr?,
        t: ClassType
    ) { //ref - ref
        if (nelems + 1 > size[0]) {
            v[0] = luaM_growaux__TValue(L, v, size, limit, e, t) //ref - ref
        }
    }

    fun luaM_growvector_LocVar(
        L: lua_State?,
        v: Array<Array<LocVar?>?>,
        nelems: Int,
        size: IntArray,
        limit: Int,
        e: CharPtr?,
        t: ClassType
    ) { //ref - ref
        if (nelems + 1 > size[0]) {
            v[0] = luaM_growaux__LocVar(L, v, size, limit, e, t) //ref - ref
        }
    }

    fun luaM_growvector_int(
        L: lua_State?,
        v: Array<IntArray?>,
        nelems: Int,
        size: IntArray,
        limit: Int,
        e: CharPtr?,
        t: ClassType
    ) { //ref - ref
        if (nelems + 1 > size[0]) {
            v[0] = luaM_growaux__int(L, v, size, limit, e, t) //ref - ref
        }
    }

    //-------------------------------
    fun luaM_reallocvector_char(
        L: lua_State?,
        v: Array<CharArray?>,
        oldn: Int,
        n: Int,
        t: ClassType
    ): CharArray? { //ref
        ClassType.Companion.Assert(v[0] == null && oldn == 0 || v[0]!!.count() == oldn)
        v[0] = luaM_reallocv_char(L, v[0], n, t)
        return v[0]
    }

    fun luaM_reallocvector_TValue(
        L: lua_State?,
        v: Array<Array<TValue?>?>,
        oldn: Int,
        n: Int,
        t: ClassType
    ): Array<TValue?>? { //ref
        ClassType.Companion.Assert(v[0] == null && oldn == 0 || v[0]!!.count() == oldn)
        v[0] = luaM_reallocv_TValue(L, v[0], n, t)
        return v[0]
    }

    fun luaM_reallocvector_TString(
        L: lua_State?,
        v: Array<Array<TString?>?>,
        oldn: Int,
        n: Int,
        t: ClassType
    ): Array<TString?>? { //ref
        ClassType.Companion.Assert(v[0] == null && oldn == 0 || v[0]!!.count() == oldn)
        v[0] = luaM_reallocv_TString(L, v[0], n, t)
        return v[0]
    }

    fun luaM_reallocvector_CallInfo(
        L: lua_State?,
        v: Array<Array<CallInfo?>?>,
        oldn: Int,
        n: Int,
        t: ClassType
    ): Array<CallInfo?>? { //ref
        ClassType.Companion.Assert(v[0] == null && oldn == 0 || v[0]!!.count() == oldn)
        v[0] = luaM_reallocv_CallInfo(L, v[0], n, t)
        return v[0]
    }

    fun luaM_reallocvector_long(
        L: lua_State?,
        v: Array<LongArray?>,
        oldn: Int,
        n: Int,
        t: ClassType
    ): LongArray? { //ref
        ClassType.Companion.Assert(v[0] == null && oldn == 0 || v[0]!!.count() == oldn)
        v[0] = luaM_reallocv_long(L, v[0], n, t)
        return v[0]
    }

    fun luaM_reallocvector_int(
        L: lua_State?,
        v: Array<IntArray?>,
        oldn: Int,
        n: Int,
        t: ClassType
    ): IntArray? { //ref
        ClassType.Companion.Assert(v[0] == null && oldn == 0 || v[0]!!.count() == oldn)
        v[0] = luaM_reallocv_int(L, v[0], n, t)
        return v[0]
    }

    fun luaM_reallocvector_Proto(
        L: lua_State?,
        v: Array<Array<Proto?>?>,
        oldn: Int,
        n: Int,
        t: ClassType
    ): Array<Proto?>? { //ref
        ClassType.Companion.Assert(v[0] == null && oldn == 0 || v[0]!!.count() == oldn)
        v[0] = luaM_reallocv_Proto(L, v[0], n, t)
        return v[0]
    }

    fun luaM_reallocvector_LocVar(
        L: lua_State?,
        v: Array<Array<LocVar?>?>,
        oldn: Int,
        n: Int,
        t: ClassType
    ): Array<LocVar?>? { //ref
        ClassType.Companion.Assert(v[0] == null && oldn == 0 || v[0]!!.count() == oldn)
        v[0] = luaM_reallocv_LocVar(L, v[0], n, t)
        return v[0]
    }

    //-------------------------------
//
//		 ** About the realloc function:
//		 ** void * frealloc (void *ud, void *ptr, uint osize, uint nsize);
//		 ** (`osize' is the old size, `nsize' is the new size)
//		 **
//		 ** Lua ensures that (ptr == null) iff (osize == 0).
//		 **
//		 ** * frealloc(ud, null, 0, x) creates a new block of size `x'
//		 **
//		 ** * frealloc(ud, p, x, 0) frees the block `p'
//		 ** (in this specific case, frealloc must return null).
//		 ** particularly, frealloc(ud, null, 0, 0) does nothing
//		 ** (which is equivalent to free(null) in ANSI C)
//		 **
//		 ** frealloc returns null if it cannot create or reallocate the area
//		 ** (any reallocation to an equal or smaller size cannot fail!)
//
    const val MINSIZEARRAY = 4

    fun luaM_growaux__long(
        L: lua_State?,
        block: Array<LongArray?>,
        size: IntArray,
        limit: Int,
        errormsg: CharPtr?,
        t: ClassType
    ): LongArray { //ref - ref
        val newblock: LongArray
        var newsize: Int
        if (size[0] >= limit / 2) { // cannot double it?
            if (size[0] >= limit) { // cannot grow even a little?
                LuaDebug.luaG_runerror(L, errormsg)
            }
            newsize = limit // still have at least one free place
        } else {
            newsize = size[0] * 2
            if (newsize < MINSIZEARRAY) {
                newsize = MINSIZEARRAY // minimum size
            }
        }
        newblock = luaM_reallocv_long(L, block[0], newsize, t)
        size[0] = newsize // update only when everything else is OK
        return newblock
    }

    fun luaM_growaux__Proto(
        L: lua_State?,
        block: Array<Array<Proto?>?>,
        size: IntArray,
        limit: Int,
        errormsg: CharPtr?,
        t: ClassType
    ): Array<Proto?> { //ref - ref
        val newblock: Array<Proto?>
        var newsize: Int
        if (size[0] >= limit / 2) { // cannot double it?
            if (size[0] >= limit) { // cannot grow even a little?
                LuaDebug.luaG_runerror(L, errormsg)
            }
            newsize = limit // still have at least one free place
        } else {
            newsize = size[0] * 2
            if (newsize < MINSIZEARRAY) {
                newsize = MINSIZEARRAY // minimum size
            }
        }
        newblock = luaM_reallocv_Proto(L, block[0], newsize, t)
        size[0] = newsize // update only when everything else is OK
        return newblock
    }

    fun luaM_growaux__TString(
        L: lua_State?,
        block: Array<Array<TString?>?>,
        size: IntArray,
        limit: Int,
        errormsg: CharPtr?,
        t: ClassType
    ): Array<TString?> { //ref - ref
        val newblock: Array<TString?>
        var newsize: Int
        if (size[0] >= limit / 2) { // cannot double it?
            if (size[0] >= limit) { // cannot grow even a little?
                LuaDebug.luaG_runerror(L, errormsg)
            }
            newsize = limit // still have at least one free place
        } else {
            newsize = size[0] * 2
            if (newsize < MINSIZEARRAY) {
                newsize = MINSIZEARRAY // minimum size
            }
        }
        newblock = luaM_reallocv_TString(L, block[0], newsize, t)
        size[0] = newsize // update only when everything else is OK
        return newblock
    }

    fun luaM_growaux__TValue(
        L: lua_State?,
        block: Array<Array<TValue?>?>,
        size: IntArray,
        limit: Int,
        errormsg: CharPtr?,
        t: ClassType
    ): Array<TValue?> { //ref - ref
        val newblock: Array<TValue?>
        var newsize: Int
        if (size[0] >= limit / 2) { // cannot double it?
            if (size[0] >= limit) { // cannot grow even a little?
                LuaDebug.luaG_runerror(L, errormsg)
            }
            newsize = limit // still have at least one free place
        } else {
            newsize = size[0] * 2
            if (newsize < MINSIZEARRAY) {
                newsize = MINSIZEARRAY // minimum size
            }
        }
        newblock = luaM_reallocv_TValue(L, block[0], newsize, t)
        size[0] = newsize // update only when everything else is OK
        return newblock
    }

    fun luaM_growaux__LocVar(
        L: lua_State?,
        block: Array<Array<LocVar?>?>,
        size: IntArray,
        limit: Int,
        errormsg: CharPtr?,
        t: ClassType
    ): Array<LocVar?> { //ref - ref
        val newblock: Array<LocVar?>
        var newsize: Int
        if (size[0] >= limit / 2) { // cannot double it?
            if (size[0] >= limit) { // cannot grow even a little?
                LuaDebug.luaG_runerror(L, errormsg)
            }
            newsize = limit // still have at least one free place
        } else {
            newsize = size[0] * 2
            if (newsize < MINSIZEARRAY) {
                newsize = MINSIZEARRAY // minimum size
            }
        }
        newblock = luaM_reallocv_LocVar(L, block[0], newsize, t)
        size[0] = newsize // update only when everything else is OK
        return newblock
    }

    fun luaM_growaux__int(
        L: lua_State?,
        block: Array<IntArray?>,
        size: IntArray,
        limit: Int,
        errormsg: CharPtr?,
        t: ClassType
    ): IntArray { //ref - ref
        val newblock: IntArray
        var newsize: Int
        if (size[0] >= limit / 2) { // cannot double it?
            if (size[0] >= limit) { // cannot grow even a little?
                LuaDebug.luaG_runerror(L, errormsg)
            }
            newsize = limit // still have at least one free place
        } else {
            newsize = size[0] * 2
            if (newsize < MINSIZEARRAY) {
                newsize = MINSIZEARRAY // minimum size
            }
        }
        newblock = luaM_reallocv_int(L, block[0], newsize, t)
        size[0] = newsize // update only when everything else is OK
        return newblock
    }

    //-------------------------------
    fun luaM_toobig(L: lua_State?): Any? {
        LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("memory allocation error: block too big"))
        return null // to avoid warnings
    }

    //
//		 ** generic allocation routine.
//
    fun luaM_realloc_(L: lua_State?, t: ClassType): Any? {
        val new_obj = t.Alloc()
        AddTotalBytes(L, CLib.GetUnmanagedSize(t))
        return new_obj
    }

    fun luaM_realloc__Proto(L: lua_State?, t: ClassType): Any {
        val new_obj = t.Alloc() as Proto //System.Activator.CreateInstance(typeof(T));
        AddTotalBytes(L, t.GetUnmanagedSize())
        return new_obj
    }

    fun luaM_realloc__Closure(L: lua_State?, t: ClassType): Any {
        val new_obj = t.Alloc() as Closure //System.Activator.CreateInstance(typeof(T));
        AddTotalBytes(L, t.GetUnmanagedSize())
        return new_obj
    }

    fun luaM_realloc__UpVal(L: lua_State?, t: ClassType): Any {
        val new_obj = t.Alloc() as UpVal //System.Activator.CreateInstance(typeof(T));
        AddTotalBytes(L, t.GetUnmanagedSize())
        return new_obj
    }

    fun luaM_realloc__lua_State(L: lua_State?, t: ClassType): Any {
        val new_obj = t.Alloc() as lua_State //System.Activator.CreateInstance(typeof(T));
        AddTotalBytes(L, t.GetUnmanagedSize())
        return new_obj
    }

    fun luaM_realloc__Table(L: lua_State?, t: ClassType): Any {
        val new_obj = t.Alloc() as Table //System.Activator.CreateInstance(typeof(T));
        AddTotalBytes(L, t.GetUnmanagedSize())
        return new_obj
    }

    //---------------------------------
//public static object luaM_realloc_<T>(lua_State L, T obj, ClassType t)
//{
//	int unmanaged_size = (int)t.GetUnmanagedSize();//CLib.GetUnmanagedSize(typeof(T))
//	int old_size = (obj == null) ? 0 : unmanaged_size;
//	int osize = old_size * unmanaged_size;
//	int nsize = unmanaged_size;
//   T new_obj = (T)t.Alloc(); //System.Activator.CreateInstance(typeof(T))
//	SubtractTotalBytes(L, osize);
//	AddTotalBytes(L, nsize);
//	return new_obj;
//}
//public static object luaM_realloc_<T>(lua_State L, T[] old_block, int new_size, ClassType t)
//{
//	int unmanaged_size = (int)t.GetUnmanagedSize();//CLib.GetUnmanagedSize(typeof(T));
//	int old_size = (old_block == null) ? 0 : old_block.Length;
//	int osize = old_size * unmanaged_size;
//	int nsize = new_size * unmanaged_size;
//	T[] new_block = new T[new_size];
//	for (int i = 0; i < Math.Min(old_size, new_size); i++)
//	{
//		new_block[i] = old_block[i];
//	}
//	for (int i = old_size; i < new_size; i++)
//	{
//       new_block[i] = (T)t.Alloc();// System.Activator.CreateInstance(typeof(T));
//	}
//	if (CanIndex(t))
//	{
//		for (int i = 0; i < new_size; i++)
//		{
//			ArrayElement elem = new_block[i] as ArrayElement;
//			ClassType.Assert(elem != null, String.Format("Need to derive type {0} from ArrayElement", t.GetTypeString()));
//			elem.set_index(i);
//			elem.set_array(new_block);
//		}
//	}
//	SubtractTotalBytes(L, osize);
//	AddTotalBytes(L, nsize);
//	return new_block;
//}
    fun luaM_realloc__Table(L: lua_State?, old_block: Array<Table>?, new_size: Int, t: ClassType): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = arrayOfNulls<Table>(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = t.Alloc() as Table // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) {
            for (i in 0 until new_size) {
                val elem =
                    (if (new_block[i] is ArrayElement) new_block[i] else null) as ArrayElement?
                ClassType.Companion.Assert(
                    elem != null,
                    String.format("Need to derive type %1\$s from ArrayElement", t.GetTypeString())
                )
                elem!!.set_index(i)
                elem.set_array(new_block)
            }
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__UpVal(L: lua_State?, old_block: Array<UpVal>?, new_size: Int, t: ClassType): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = arrayOfNulls<UpVal>(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = t.Alloc() as UpVal // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) {
            for (i in 0 until new_size) {
                val elem =
                    (if (new_block[i] is ArrayElement) new_block[i] else null) as ArrayElement?
                ClassType.Companion.Assert(
                    elem != null,
                    String.format("Need to derive type %1\$s from ArrayElement", t.GetTypeString())
                )
                elem!!.set_index(i)
                elem.set_array(new_block)
            }
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__char(L: lua_State?, old_block: CharArray?, new_size: Int, t: ClassType): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = CharArray(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = (t.Alloc() as Char).toChar() // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) { // FIXME:not necessary
//
//                for (int i = 0; i < new_size; i++)
//                {
//                    ArrayElement elem = new_block[i] as ArrayElement;
//                    ClassType.Assert(elem != null, String.Format("Need to derive type {0} from ArrayElement", t.GetTypeString()));
//                    elem.set_index(i);
//                    elem.set_array(new_block);
//                }
//
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__TValue(
        L: lua_State?,
        old_block: Array<TValue?>?,
        new_size: Int,
        t: ClassType
    ): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = arrayOfNulls<TValue>(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = t.Alloc() as TValue // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) {
            for (i in 0 until new_size) {
                val elem =
                    (if (new_block[i] is ArrayElement) new_block[i] else null) as ArrayElement?
                ClassType.Companion.Assert(
                    elem != null,
                    String.format("Need to derive type %1\$s from ArrayElement", t.GetTypeString())
                )
                elem!!.set_index(i)
                elem.set_array(new_block)
            }
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__TString(
        L: lua_State?,
        old_block: Array<TString?>?,
        new_size: Int,
        t: ClassType
    ): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = arrayOfNulls<TString>(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = t.Alloc() as TString // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) {
            for (i in 0 until new_size) {
                val elem =
                    (if (new_block[i] is ArrayElement) new_block[i] else null) as ArrayElement?
                ClassType.Companion.Assert(
                    elem != null,
                    String.format("Need to derive type %1\$s from ArrayElement", t.GetTypeString())
                )
                elem!!.set_index(i)
                elem.set_array(new_block)
            }
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__Udata(L: lua_State?, old_block: Array<Udata>?, new_size: Int, t: ClassType): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = arrayOfNulls<Udata>(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = t.Alloc() as Udata // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) {
            for (i in 0 until new_size) {
                val elem =
                    (if (new_block[i] is ArrayElement) new_block[i] else null) as ArrayElement?
                ClassType.Companion.Assert(
                    elem != null,
                    String.format("Need to derive type %1\$s from ArrayElement", t.GetTypeString())
                )
                elem!!.set_index(i)
                elem.set_array(new_block)
            }
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__CallInfo(
        L: lua_State?,
        old_block: Array<CallInfo?>?,
        new_size: Int,
        t: ClassType
    ): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = arrayOfNulls<CallInfo>(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = t.Alloc() as CallInfo // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) {
            for (i in 0 until new_size) {
                val elem =
                    (if (new_block[i] is ArrayElement) new_block[i] else null) as ArrayElement?
                ClassType.Companion.Assert(
                    elem != null,
                    String.format("Need to derive type %1\$s from ArrayElement", t.GetTypeString())
                )
                elem!!.set_index(i)
                elem.set_array(new_block)
            }
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__long(L: lua_State?, old_block: LongArray?, new_size: Int, t: ClassType): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = LongArray(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = (t.Alloc() as Int).toLong() // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) { //FIXME: not necessary
//
//                for (int i = 0; i < new_size; i++)
//                {
//                    ArrayElement elem = new_block[i] as ArrayElement;
//                    ClassType.Assert(elem != null, String.Format("Need to derive type {0} from ArrayElement", t.GetTypeString()));
//                    elem.set_index(i);
//                    elem.set_array(new_block);
//                }
//
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__int(L: lua_State?, old_block: IntArray?, new_size: Int, t: ClassType): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = IntArray(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = (t.Alloc() as Int).toInt() // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) { //FIXME: not necessary
//
//                for (int i = 0; i < new_size; i++)
//                {
//                    ArrayElement elem = new_block[i] as ArrayElement;
//                    ClassType.Assert(elem != null, String.Format("Need to derive type {0} from ArrayElement", t.GetTypeString()));
//                    elem.set_index(i);
//                    elem.set_array(new_block);
//                }
//
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__Proto(L: lua_State?, old_block: Array<Proto?>?, new_size: Int, t: ClassType): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = arrayOfNulls<Proto>(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = t.Alloc() as Proto // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) {
            for (i in 0 until new_size) {
                val elem =
                    (if (new_block[i] is ArrayElement) new_block[i] else null) as ArrayElement?
                ClassType.Companion.Assert(
                    elem != null,
                    String.format("Need to derive type %1\$s from ArrayElement", t.GetTypeString())
                )
                elem!!.set_index(i)
                elem.set_array(new_block)
            }
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__LocVar(
        L: lua_State?,
        old_block: Array<LocVar?>?,
        new_size: Int,
        t: ClassType
    ): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = arrayOfNulls<LocVar?>(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = t.Alloc() as LocVar // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) {
            //FIXME:
//            for (i in 0 until new_size) {
//                val elem =
//                    (if (new_block[i] is ArrayElement?) new_block[i] else null) as ArrayElement?
//                ClassType.Companion.Assert(
//                    elem != null,
//                    String.format("Need to derive type %1\$s from ArrayElement", t.GetTypeString())
//                )
//                elem!!.set_index(i)
//                elem.set_array(new_block)
//            }
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__Node(
        L: lua_State?,
        old_block: Array<Node?>?,
        new_size: Int,
        t: ClassType
    ): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = arrayOfNulls<Node>(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = t.Alloc() as Node // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) {
            for (i in 0 until new_size) {
                val elem =
                    (if (new_block[i] is ArrayElement) new_block[i] else null) as ArrayElement?
                ClassType.Companion.Assert(
                    elem != null,
                    String.format("Need to derive type %1\$s from ArrayElement", t.GetTypeString())
                )
                elem!!.set_index(i)
                elem.set_array(new_block)
            }
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun luaM_realloc__GCObject(
        L: lua_State?,
        old_block: Array<GCObject?>?,
        new_size: Int,
        t: ClassType
    ): Any {
        val unmanaged_size = t.GetUnmanagedSize() //CLib.GetUnmanagedSize(typeof(T));
        val old_size = old_block?.size ?: 0
        val osize = old_size * unmanaged_size
        val nsize = new_size * unmanaged_size
        val new_block = arrayOfNulls<GCObject>(new_size)
        for (i in 0 until Math.min(old_size, new_size)) {
            new_block[i] = old_block!![i]
        }
        for (i in old_size until new_size) {
            new_block[i] = t.Alloc() as GCObject // System.Activator.CreateInstance(typeof(T));
        }
        if (CanIndex(t)) {
            for (i in 0 until new_size) {
                val elem =
                    (if (new_block[i] is ArrayElement) new_block[i] else null) as ArrayElement?
                ClassType.Companion.Assert(
                    elem != null,
                    String.format("Need to derive type %1\$s from ArrayElement", t.GetTypeString())
                )
                elem!!.set_index(i)
                elem.set_array(new_block)
            }
        }
        SubtractTotalBytes(L, osize)
        AddTotalBytes(L, nsize)
        return new_block
    }

    fun CanIndex(t: ClassType): Boolean {
        return t.CanIndex()
    }

    fun AddTotalBytes(L: lua_State?, num_bytes: Int) {
        LuaState.G(L)!!.totalbytes += (num_bytes as Int).toLong() //uint
    }

    fun SubtractTotalBytes(L: lua_State?, num_bytes: Int) {
        LuaState.G(L)!!.totalbytes -= (num_bytes as Int).toLong() //uint
    } //static void AddTotalBytes(lua_State L, uint num_bytes) {G(L).totalbytes += num_bytes;}
//static void SubtractTotalBytes(lua_State L, uint num_bytes) {G(L).totalbytes -= num_bytes;}
}