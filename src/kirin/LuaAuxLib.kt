package kirin

import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.Lua.lua_Debug
import kirin.Lua.lua_Reader
import kirin.Lua.lua_Alloc


//
// ** $Id: lauxlib.c,v 1.159.1.3 2008/01/21 13:20:51 roberto Exp $
// ** Auxiliary functions for building Lua libraries
// ** See Copyright Notice in lua.h
//
//
// ** #define lauxlib_c
// ** #define LUA_LIB
//
//using lua_Number = System.Double;
//using lua_Integer = System.Int32;
object LuaAuxLib {
    ///#if LUA_COMPAT_GETN
//		public static int luaL_getn(lua_State L, int t);
//		public static void luaL_setn(lua_State L, int t, int n);
///#else
    fun luaL_getn(L: lua_State?, i: Int): Int {
        return LuaAPI.lua_objlen(L!!, i)
    }

    fun luaL_setn(L: lua_State?, i: Int, j: Int) { // no op!
    }

    ///#endif
///#if LUA_COMPAT_OPENLIB
///#define luaI_openlib	luaL_openlib
///#endif
// extra error code for `luaL_load'
    val LUA_ERRFILE: Int = Lua.LUA_ERRERR + 1

    //
//		 ** ===============================================================
//		 ** some useful macros
//		 ** ===============================================================
//
    fun luaL_argcheck(L: lua_State?, cond: Boolean, numarg: Int, extramsg: String?) {
        if (!cond) {
            luaL_argerror(L, numarg, CharPtr.Companion.toCharPtr(extramsg!!))
        }
    }

    fun luaL_checkstring(L: lua_State?, n: Int): CharPtr {
        return luaL_checklstring(L, n)
    }

    fun luaL_optstring(L: lua_State?, n: Int, d: CharPtr?): CharPtr? {
        val len = IntArray(1) //uint
        return luaL_optlstring(L, n, d, len) //out
    }

    fun luaL_checkint(L: lua_State?, n: Int): Int {
        return luaL_checkinteger(L, n) //(int)
    }

    fun luaL_optint(L: lua_State?, n: Int, d: Int): Int { //lua_Integer - Int32
        return luaL_optinteger(L, n, d) //(int)
    }

    fun luaL_checklong(L: lua_State?, n: Int): Long {
        return luaL_checkinteger(L, n).toLong()
    }

    fun luaL_optlong(L: lua_State?, n: Int, d: Int): Long { //lua_Integer - Int32
        return luaL_optinteger(L, n, d).toLong()
    }

    fun luaL_typename(L: lua_State?, i: Int): CharPtr {
        return LuaAPI.lua_typename(L, LuaAPI.lua_type(L!!, i))
    }

    ///#define luaL_dofile(L, fn) \
//    (luaL_loadfile(L, fn) || lua_pcall(L, 0, LUA_MULTRET, 0))
///#define luaL_dostring(L, s) \
//    (luaL_loadstring(L, s) || lua_pcall(L, 0, LUA_MULTRET, 0))
    fun luaL_getmetatable(L: lua_State?, n: CharPtr?) {
        LuaAPI.lua_getfield(L!!, Lua.LUA_REGISTRYINDEX, n)
    }

    fun luaL_opt(
        L: lua_State?,
        f: luaL_opt_delegate,
        n: Int,
        d: Double
    ): Double { //lua_Number - lua_Number - Double
        return if (Lua.lua_isnoneornil(L, if (n != 0) d else f.exec(L, n))) 1 as Double else 0 as Double
    }

    fun luaL_opt_integer(
        L: lua_State?,
        f: luaL_opt_delegate_integer,
        n: Int,
        d: Double
    ): Int { //lua_Number - lua_Integer - Int32
        return (if (Lua.lua_isnoneornil(L, n.toDouble())) d else f.exec(L, n)) as Int //lua_Integer - Int32
    }

    fun luaL_addchar(B: luaL_Buffer, c: Char) {
        if (B.p >= LuaConf.LUAL_BUFFERSIZE) {
            luaL_prepbuffer(B)
        }
        B.buffer.set(B.p++, c)
    }

    // compatibility only
    fun luaL_putchar(B: luaL_Buffer, c: Char) {
        luaL_addchar(B, c)
    }

    fun luaL_addsize(B: luaL_Buffer, n: Int) {
        B.p += n
    }

    // }======================================================
// compatibility with ref system
// pre-defined references
    const val LUA_NOREF = -2
    const val LUA_REFNIL = -1
    ///#define lua_ref(L,lock) ((lock) ? luaL_ref(L, LUA_REGISTRYINDEX) : \
//      (lua_pushstring(L, "unlocked references are obsolete"), lua_error(L), 0))
///#define lua_unref(L,ref)        luaL_unref(L, LUA_REGISTRYINDEX, (ref))
///#define lua_getref(L,ref)       lua_rawgeti(L, LUA_REGISTRYINDEX, (ref))
///#define luaL_reg	luaL_Reg
//         This file uses only the official API of Lua.
//		 ** Any function declared here could be written as an application function.
//
///#define lauxlib_c
///#define LUA_LIB
    const val FREELIST_REF = 0 // free list of references

    // convert a stack index to positive
    fun abs_index(L: lua_State?, i: Int): Int {
        return if (i > 0 || i <= Lua.LUA_REGISTRYINDEX) i else LuaAPI.lua_gettop(L!!) + i + 1
    }

    //
//		 ** {======================================================
//		 ** Error-report functions
//		 ** =======================================================
//
    fun luaL_argerror(L: lua_State?, narg: Int, extramsg: CharPtr?): Int {
        var narg = narg
        val ar = lua_Debug()
        if (LuaDebug.lua_getstack(L, 0, ar) == 0) { // no stack frame?
            return luaL_error(L, CharPtr.Companion.toCharPtr("bad argument #%d (%s)"), narg, extramsg)
        }
        LuaDebug.lua_getinfo(L, CharPtr.Companion.toCharPtr("n"), ar)
        if (CLib.strcmp(ar.namewhat, CharPtr.Companion.toCharPtr("method")) == 0) {
            narg-- // do not count `self'
            if (narg == 0) { // error is in the self argument itself?
                return luaL_error(
                    L,
                    CharPtr.Companion.toCharPtr("calling " + LuaConf.getLUA_QS() + " on bad self ({1})"),
                    ar.name,
                    extramsg
                ) //FIXME:
            }
        }
        if (CharPtr.Companion.isEqual(ar.name, null)) {
            ar.name = CharPtr.Companion.toCharPtr("?")
        }
        return luaL_error(
            L,
            CharPtr.Companion.toCharPtr("bad argument #%d to " + LuaConf.getLUA_QS() + " (%s)"),
            narg,
            ar.name,
            extramsg
        )
    }

    fun luaL_typerror(L: lua_State?, narg: Int, tname: CharPtr?): Int {
        val msg: CharPtr? = LuaAPI.lua_pushfstring(
            L,
            CharPtr.Companion.toCharPtr("%s expected, got %s"),
            tname,
            luaL_typename(L, narg)
        )
        return luaL_argerror(L, narg, msg)
    }

    private fun tag_error(L: lua_State?, narg: Int, tag: Int) {
        luaL_typerror(L, narg, LuaAPI.lua_typename(L, tag))
    }

    fun luaL_where(L: lua_State?, level: Int) {
        val ar = lua_Debug()
        if (LuaDebug.lua_getstack(L, level, ar) != 0) { // check function at level
            LuaDebug.lua_getinfo(L, CharPtr.Companion.toCharPtr("Sl"), ar) // get info about it
            if (ar.currentline > 0) { // is there info?
                LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("%s:%d: "), ar.short_src, ar.currentline)
                return
            }
        }
        Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("")) // else, no information available...
    }

    fun luaL_error(L: lua_State?, fmt: CharPtr?, vararg p: Any?): Int {
        luaL_where(L, 1)
        LuaAPI.lua_pushvfstring(L, fmt, arrayOf(p))
        LuaAPI.lua_concat(L!!, 2)
        return LuaAPI.lua_error(L!!)
    }

    // }======================================================
    fun luaL_checkoption(L: lua_State?, narg: Int, def: CharPtr?, lst: Array<CharPtr?>): Int {
        val name: CharPtr? = if (CharPtr.Companion.isNotEqual(def, null)) luaL_optstring(
            L,
            narg,
            def
        ) else luaL_checkstring(L, narg)
        var i: Int
        i = 0
        while (i < lst.size) {
            if (CLib.strcmp(lst[i], name) == 0) {
                return i
            }
            i++
        }
        return luaL_argerror(
            L,
            narg,
            LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("invalid option " + LuaConf.getLUA_QS()), name)
        )
    }

    fun luaL_newmetatable(L: lua_State?, tname: CharPtr?): Int {
        LuaAPI.lua_getfield(L!!, Lua.LUA_REGISTRYINDEX, tname) // get registry.name
        if (!Lua.lua_isnil(L, -1)) { // name already in use?
            return 0 // leave previous value on top, but return 0
        }
        Lua.lua_pop(L, 1)
        Lua.lua_newtable(L) // create metatable
        LuaAPI.lua_pushvalue(L, -1)
        LuaAPI.lua_setfield(L, Lua.LUA_REGISTRYINDEX, tname) // registry.name = metatable
        return 1
    }

    fun luaL_checkudata(L: lua_State?, ud: Int, tname: CharPtr?): Any? {
        val p: Any? = LuaAPI.lua_touserdata(L!!, ud)
        if (p != null) { // value is a userdata?
            if (LuaAPI.lua_getmetatable(L!!, ud) != 0) { // does it have a metatable?
                LuaAPI.lua_getfield(L!!, Lua.LUA_REGISTRYINDEX, tname) // get correct metatable
                if (LuaAPI.lua_rawequal(L!!, -1, -2) != 0) { // does it have the correct mt?
                    Lua.lua_pop(L, 2) // remove both metatables
                    return p
                }
            }
        }
        luaL_typerror(L, ud, tname) // else error
        return null // to avoid warnings
    }

    fun luaL_checkstack(L: lua_State?, space: Int, mes: CharPtr?) {
        if (LuaAPI.lua_checkstack(L!!, space) == 0) {
            luaL_error(L, CharPtr.Companion.toCharPtr("stack overflow (%s)"), mes)
        }
    }

    fun luaL_checktype(L: lua_State?, narg: Int, t: Int) {
        if (LuaAPI.lua_type(L!!, narg) != t) {
            tag_error(L, narg, t)
        }
    }

    fun luaL_checkany(L: lua_State?, narg: Int) {
        if (LuaAPI.lua_type(L!!, narg) == Lua.LUA_TNONE) {
            luaL_argerror(L, narg, CharPtr.Companion.toCharPtr("value expected"))
        }
    }

    fun luaL_checklstring(L: lua_State?, narg: Int): CharPtr {
        val len = IntArray(1) //uint
        return luaL_checklstring(L, narg, len) //out
    }

    fun luaL_checklstring(L: lua_State?, narg: Int, len: IntArray?): CharPtr { //uint - out
        val s: CharPtr? = LuaAPI.lua_tolstring(L!!, narg, len!!) //out
        if (CharPtr.Companion.isEqual(s, null)) {
            tag_error(L, narg, Lua.LUA_TSTRING)
        }
        return s!!
    }

    fun luaL_optlstring(L: lua_State?, narg: Int, def: CharPtr?): CharPtr? {
        val len = IntArray(1) //uint
        return luaL_optlstring(L, narg, def, len) //out
    }

    fun luaL_optlstring(L: lua_State?, narg: Int, def: CharPtr?, len: IntArray): CharPtr? { //uint - out
        return if (Lua.lua_isnoneornil(L, narg.toDouble())) {
            len[0] = if (CharPtr.Companion.isNotEqual(def, null)) CLib.strlen(def) else 0 //(uint)
            def
        } else {
            luaL_checklstring(L, narg, len) //out
        }
    }

    fun luaL_checknumber(L: lua_State?, narg: Int): Double { //lua_Number - Double
        val d: Double = LuaAPI.lua_tonumber(L!!, narg) //lua_Number - Double
        if (d == 0.0 && LuaAPI.lua_isnumber(L!!, narg) == 0) { // avoid extra test when d is not 0
            tag_error(L, narg, Lua.LUA_TNUMBER)
        }
        return d
    }

    fun luaL_optnumber(L: lua_State?, narg: Int, def: Double): Double { //lua_Number - lua_Number - Double
        return luaL_opt(L, luaL_checknumber_delegate(), narg, def)
    }

    fun luaL_checkinteger(L: lua_State?, narg: Int): Int { //lua_Integer - Int32
        val d: Int = LuaAPI.lua_tointeger(L!!, narg) //lua_Integer - Int32
        if (d == 0 && LuaAPI.lua_isnumber(L!!, narg) == 0) { // avoid extra test when d is not 0
            tag_error(L, narg, Lua.LUA_TNUMBER)
        }
        return d
    }

    fun luaL_optinteger(L: lua_State?, narg: Int, def: Int): Int { //lua_Integer - Int32 - lua_Integer - Int32
        return luaL_opt_integer(L, luaL_checkinteger_delegate(), narg, def.toDouble())
    }

    fun luaL_getmetafield(L: lua_State?, obj: Int, event_: CharPtr?): Int {
        if (LuaAPI.lua_getmetatable(L!!, obj) == 0) { // no metatable?
            return 0
        }
        LuaAPI.lua_pushstring(L!!, event_)
        LuaAPI.lua_rawget(L!!, -2)
        return if (Lua.lua_isnil(L, -1)) {
            Lua.lua_pop(L, 2) // remove metatable and metafield
            0
        } else {
            LuaAPI.lua_remove(L!!, -2) // remove only metatable
            1
        }
    }

    fun luaL_callmeta(L: lua_State?, obj: Int, event_: CharPtr?): Int {
        var obj = obj
        obj = abs_index(L, obj)
        if (luaL_getmetafield(L, obj, event_) == 0) { // no metafield?
            return 0
        }
        LuaAPI.lua_pushvalue(L!!, obj)
        LuaAPI.lua_call(L!!, 1, 1)
        return 1
    }

    fun luaL_register(L: lua_State?, libname: CharPtr?, l: Array<luaL_Reg>) {
        luaI_openlib(L, libname, l, 0)
    }

    // we could just take the .Length member here, but let's try
// to keep it as close to the C implementation as possible.
    private fun libsize(l: Array<luaL_Reg>): Int {
        var size = 0
        while (CharPtr.Companion.isNotEqual(l[size].name, null)) {
            size++
        }
        return size
    }

    fun luaI_openlib(L: lua_State?, libname: CharPtr?, l: Array<luaL_Reg>, nup: Int) {
        if (CharPtr.Companion.isNotEqual(libname, null)) {
            val size = libsize(l)
            // check whether lib already exists
            luaL_findtable(L, Lua.LUA_REGISTRYINDEX, CharPtr.Companion.toCharPtr("_LOADED"), 1)
            LuaAPI.lua_getfield(L!!, -1, libname) // get _LOADED[libname]
            if (!Lua.lua_istable(L, -1)) { // not found?
                Lua.lua_pop(L, 1) // remove previous result
                // try global variable (and create one if it does not exist)
                if (CharPtr.Companion.isNotEqual(
                        luaL_findtable(L, Lua.LUA_GLOBALSINDEX, libname, size),
                        null
                    )
                ) {
                    luaL_error(
                        L,
                        CharPtr.Companion.toCharPtr("name conflict for module " + LuaConf.getLUA_QS()),
                        libname
                    )
                }
                LuaAPI.lua_pushvalue(L!!, -1)
                LuaAPI.lua_setfield(L!!, -3, libname) // _LOADED[libname] = new table
            }
            LuaAPI.lua_remove(L!!, -2) // remove _LOADED table
            LuaAPI.lua_insert(L!!, -(nup + 1)) // move library table to below upvalues
        }
        var reg_num = 0
        while (CharPtr.Companion.isNotEqual(l[reg_num].name, null)) {
            var i: Int
            i = 0
            while (i < nup) {
                // copy upvalues to the top
                LuaAPI.lua_pushvalue(L!!, -nup)
                i++
            }
            LuaAPI.lua_pushcclosure(L!!, l[reg_num].func, nup)
            LuaAPI.lua_setfield(L!!, -(nup + 2), l[reg_num].name)
            reg_num++
        }
        Lua.lua_pop(L, nup) // remove upvalues
    }

    //
//		 ** {======================================================
//		 ** getn-setn: size for arrays
//		 ** =======================================================
//
///#if LUA_COMPAT_GETN
//		static int checkint(lua_State L, int topop)
//		{
//			int n = (lua_type(L, -1) == LUA_TNUMBER) ? lua_tointeger(L, -1) : -1;
//			lua_pop(L, topop);
//			return n;
//		}
//
//		static void getsizes(lua_State L)
//		{
//			lua_getfield(L, LUA_REGISTRYINDEX, "LUA_SIZES");
//			if (lua_isnil(L, -1))
//			{
//				/* no `size' table? */
//				lua_pop(L, 1);  /* remove nil */
//				lua_newtable(L);  /* create it */
//				lua_pushvalue(L, -1);  /* `size' will be its own metatable */
//				lua_setmetatable(L, -2);
//				lua_pushliteral(L, "kv");
//				lua_setfield(L, -2, "__mode");  /* metatable(N).__mode = "kv" */
//				lua_pushvalue(L, -1);
//				lua_setfield(L, LUA_REGISTRYINDEX, "LUA_SIZES");  /* store in register */
//			}
//		}
//
//		public static void luaL_setn(lua_State L, int t, int n)
//		{
//			t = abs_index(L, t);
//			lua_pushliteral(L, "n");
//			lua_rawget(L, t);
//			if (checkint(L, 1) >= 0)
//			{
//				/* is there a numeric field `n'? */
//				lua_pushliteral(L, "n");  /* use it */
//				lua_pushinteger(L, n);
//				lua_rawset(L, t);
//			}
//			else
//			{
//				/* use `sizes' */
//				getsizes(L);
//				lua_pushvalue(L, t);
//				lua_pushinteger(L, n);
//				lua_rawset(L, -3);  /* sizes[t] = n */
//				lua_pop(L, 1);  /* remove `sizes' */
//			}
//		}
//
//		public static int luaL_getn(lua_State L, int t)
//		{
//			int n;
//			t = abs_index(L, t);
//			lua_pushliteral(L, "n");  /* try t.n */
//			lua_rawget(L, t);
//			if ((n = checkint(L, 1)) >= 0)
//			{
//				return n;
//			}
//			getsizes(L);  /* else try sizes[t] */
//			lua_pushvalue(L, t);
//			lua_rawget(L, -2);
//			if ((n = checkint(L, 2)) >= 0)
//			{
//				return n;
//			}
//			return (int)lua_objlen(L, t);
//		}
///#endif
// }======================================================
    fun luaL_gsub(L: lua_State?, s: CharPtr?, p: CharPtr, r: CharPtr?): CharPtr? {
        var s: CharPtr? = s
        var wild: CharPtr? = null
        val l: Int = CLib.strlen(p) //(uint) - uint
        val b = luaL_Buffer()
        luaL_buffinit(L, b)
        while (CharPtr.Companion.isNotEqual(CLib.strstr(s, p).also({ wild = it }), null)) {
            luaL_addlstring(b, s, CharPtr.Companion.minus(wild!!, s!!)) // push prefix  - (uint)
            luaL_addstring(b, r) // push replacement in place of pattern
            s = CharPtr.Companion.plus(wild, l) // continue after `p'
        }
        luaL_addstring(b, s) // push last suffix
        luaL_pushresult(b)
        return Lua.lua_tostring(L, -1)
    }

    fun luaL_findtable(L: lua_State?, idx: Int, fname: CharPtr?, szhint: Int): CharPtr? {
        var fname: CharPtr? = fname
        var e: CharPtr?
        LuaAPI.lua_pushvalue(L!!, idx)
        do {
            e = CLib.strchr(fname, '.')
            if (CharPtr.Companion.isEqual(e, null)) {
                e = CharPtr.Companion.plus(fname, CLib.strlen(fname))
            }
            LuaAPI.lua_pushlstring(L!!, fname, CharPtr.Companion.minus(e!!, fname!!)) //(uint)
            LuaAPI.lua_rawget(L!!, -2)
            if (Lua.lua_isnil(L, -1)) { // no such field?
                Lua.lua_pop(L, 1) // remove this nil
                LuaAPI.lua_createtable(
                    L,
                    0,
                    if (CharPtr.Companion.isEqualChar(e, '.')) 1 else szhint
                ) // new table for field
                LuaAPI.lua_pushlstring(L!!, fname, CharPtr.Companion.minus(e, fname)) //(uint)
                LuaAPI.lua_pushvalue(L!!, -2)
                LuaAPI.lua_settable(L!!, -4) // set new table into field
            } else if (!Lua.lua_istable(L, -1)) { // field has a non-table value?
                Lua.lua_pop(L, 2) // remove table and value
                return fname // return problematic part of the name
            }
            LuaAPI.lua_remove(L, -2) // remove previous table
            fname = CharPtr.Companion.plus(e, 1)
        } while (CharPtr.Companion.isEqualChar(e!!, '.'))
        return null
    }

    //
//		 ** {======================================================
//		 ** Generic Buffer manipulation
//		 ** =======================================================
//
    private fun bufflen(B: luaL_Buffer): Int {
        return B.p
    }

    private fun bufffree(B: luaL_Buffer): Int {
        return LuaConf.LUAL_BUFFERSIZE - bufflen(B)
    }

    val LIMIT: Int = Lua.LUA_MINSTACK / 2
    private fun emptybuffer(B: luaL_Buffer): Int {
        val l = bufflen(B) //(uint) - uint
        return if (l == 0) {
            0 // put nothing on stack
        } else {
            LuaAPI.lua_pushlstring(B.L!!, B.buffer, l)
            B.p = 0
            B.lvl++
            1
        }
    }

    private fun adjuststack(B: luaL_Buffer) {
        if (B.lvl > 1) {
            val L: lua_State? = B.L
            var toget = 1 // number of levels to concat
            var toplen: Int = Lua.lua_strlen(L, -1) //uint
            do {
                val l: Int = Lua.lua_strlen(L, -(toget + 1)) //uint
                if (B.lvl - toget + 1 >= LIMIT || toplen > l) {
                    toplen += l
                    toget++
                } else {
                    break
                }
            } while (toget < B.lvl)
            LuaAPI.lua_concat(L!!, toget)
            B.lvl = B.lvl - toget + 1
        }
    }

    fun luaL_prepbuffer(B: luaL_Buffer): CharPtr {
        if (emptybuffer(B) != 0) {
            adjuststack(B)
        }
        return CharPtr(B.buffer, B.p)
    }

    fun luaL_addlstring(B: luaL_Buffer, s: CharPtr?, l: Int) { //uint
        var s: CharPtr? = s
        var l = l
        while (l-- != 0) {
            val c: Char = s!!.get(0)
            s = s.next()
            luaL_addchar(B, c)
        }
    }

    fun luaL_addstring(B: luaL_Buffer, s: CharPtr?) {
        luaL_addlstring(B, s, CLib.strlen(s)) //(uint)
    }

    fun luaL_pushresult(B: luaL_Buffer) {
        emptybuffer(B)
        LuaAPI.lua_concat(B.L!!, B.lvl)
        B.lvl = 1
    }

    fun luaL_addvalue(B: luaL_Buffer) {
        val L: lua_State? = B.L
        val vl = IntArray(1) //uint
        val s: CharPtr? = LuaAPI.lua_tolstring(L!!, -1, vl) //out
        if (vl[0] <= bufffree(B)) { // fit into buffer?
            val dst = CharPtr(B.buffer.chars, B.buffer.index + B.p)
            val src = CharPtr(s!!.chars, s!!.index)
            for (i in 0 until vl[0]) { //uint
                dst.set(i, src.get(i))
            }
            B.p += vl[0] //(int)
            Lua.lua_pop(L, 1) // remove from stack
        } else {
            if (emptybuffer(B) != 0) {
                LuaAPI.lua_insert(L!!, -2) // put buffer before new value
            }
            B.lvl++ // add new value into B stack
            adjuststack(B)
        }
    }

    fun luaL_buffinit(L: lua_State?, B: luaL_Buffer) {
        B.L = L
        B.p = 0 //B.buffer
        B.lvl = 0
    }

    // }======================================================
    fun luaL_ref(L: lua_State?, t: Int): Int {
        var t = t
        var ref_: Int
        t = abs_index(L, t)
        if (Lua.lua_isnil(L, -1)) {
            Lua.lua_pop(L, 1) // remove from stack
            return LUA_REFNIL // `nil' has a unique fixed reference
        }
        LuaAPI.lua_rawgeti(L!!, t, FREELIST_REF) // get first free element
        ref_ = LuaAPI.lua_tointeger(L!!, -1) // ref = t[FREELIST_REF]  - (int)
        Lua.lua_pop(L, 1) // remove it from stack
        if (ref_ != 0) { // any free element?
            LuaAPI.lua_rawgeti(L!!, t, ref_) // remove it from list
            LuaAPI.lua_rawseti(L!!, t, FREELIST_REF) // (t[FREELIST_REF] = t[ref])
        } else { // no free elements
            ref_ = LuaAPI.lua_objlen(L!!, t) //(int)
            ref_++ // create new reference
        }
        LuaAPI.lua_rawseti(L!!, t, ref_)
        return ref_
    }

    fun luaL_unref(L: lua_State?, t: Int, ref_: Int) {
        var t = t
        if (ref_ >= 0) {
            t = abs_index(L, t)
            LuaAPI.lua_rawgeti(L!!, t, FREELIST_REF)
            LuaAPI.lua_rawseti(L!!, t, ref_) // t[ref] = t[FREELIST_REF]
            LuaAPI.lua_pushinteger(L!!, ref_)
            LuaAPI.lua_rawseti(L!!, t, FREELIST_REF) // t[FREELIST_REF] = ref
        }
    }

    fun getF(L: lua_State?, ud: Any, size: IntArray): CharPtr? { //uint - out
        size[0] = 0
        val lf = ud as LoadF
        //(void)L;
        if (lf.extraline != 0) {
            lf.extraline = 0
            size[0] = 1
            return CharPtr.Companion.toCharPtr("\n")
        }
        if (CLib.feof(lf.f) != 0) {
            return null
        }
        size[0] = CLib.fread(lf.buff, 1, lf.buff.chars!!.size, lf.f) //(uint)
        return if (size[0] > 0) CharPtr(lf.buff) else null
    }

    private fun errfile(L: lua_State?, what: CharPtr, fnameindex: Int): Int {
        val serr: CharPtr = CLib.strerror(CLib.errno())
        val filename: CharPtr = CharPtr.Companion.plus(Lua.lua_tostring(L, fnameindex), 1)
        LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("cannot %s %s: %s"), what, filename, serr)
        LuaAPI.lua_remove(L!!, fnameindex)
        return LUA_ERRFILE
    }

    fun luaL_loadfile(L: lua_State?, filename: CharPtr?): Int {
        val lf = LoadF()
        val status: Int
        val readstatus: Int
        var c: Int
        val fnameindex: Int = LuaAPI.lua_gettop(L!!) + 1 // index of filename on the stack
        lf.extraline = 0
        if (CharPtr.Companion.isEqual(filename, null)) {
            Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("=stdin"))
            lf.f = CLib.stdin
        } else {
            LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("@%s"), filename)
            lf.f = CLib.fopen(filename, CharPtr.Companion.toCharPtr("r"))
            if (lf.f == null) {
                return errfile(L, CharPtr.Companion.toCharPtr("open"), fnameindex)
            }
        }
        c = CLib.getc(lf.f)
        if (c == '#'.toInt()) { // Unix exec. file?
            lf.extraline = 1
            while (CLib.getc(lf.f).also({ c = it }) != CLib.EOF && c != '\n'.toInt()) {
                // skip first line
            }
            if (c == '\n'.toInt()) {
                c = CLib.getc(lf.f)
            }
        }
        if (c == Lua.LUA_SIGNATURE.get(0).toInt() && CharPtr.Companion.isNotEqual(filename, null)) { // binary file?
            lf.f = CLib.freopen(filename, CharPtr.Companion.toCharPtr("rb"), lf.f) // reopen in binary mode
            if (lf.f == null) {
                return errfile(L, CharPtr.Companion.toCharPtr("reopen"), fnameindex)
            }
            // skip eventual `#!...'
            while (CLib.getc(lf.f).also({ c = it }) != CLib.EOF && c != Lua.LUA_SIGNATURE.get(0).toInt()) {
            }
            lf.extraline = 0
        }
        CLib.ungetc(c, lf.f)
        status = LuaAPI.lua_load(L, getF_delegate(), lf, Lua.lua_tostring(L, -1))
        readstatus = CLib.ferror(lf.f)
        if (CharPtr.Companion.isNotEqual(filename, null)) {
            CLib.fclose(lf.f) // close file (even in case of errors)
        }
        if (readstatus != 0) {
            LuaAPI.lua_settop(L!!, fnameindex) // ignore results from `lua_load'
            return errfile(L, CharPtr.Companion.toCharPtr("read"), fnameindex)
        }
        LuaAPI.lua_remove(L!!, fnameindex)
        return status
    }

    private fun getS(L: lua_State?, ud: Any, size: IntArray): CharPtr? { //uint - out
        val ls = ud as LoadS
        //(void)L;
//if (ls.size == 0) return null;
        size[0] = ls.size
        ls.size = 0
        return ls.s
    }

    fun luaL_loadbuffer(L: lua_State?, buff: CharPtr?, size: Int, name: CharPtr?): Int { //uint
        val ls = LoadS()
        ls.s = CharPtr(buff!!)
        ls.size = size
        return LuaAPI.lua_load(L, getS_delegate(), ls, name)
    }

    fun luaL_loadstring(L: lua_State?, s: CharPtr?): Int {
        return luaL_loadbuffer(L, s, CLib.strlen(s), s) //(uint)
    }

    // }======================================================
    private fun l_alloc(t: ClassType): Any? {
        return t.Alloc()
    }

    private fun panic(L: lua_State): Int { //(void)L;  /* to avoid warnings */
        CLib.fprintf(
            CLib.stderr,
            CharPtr.Companion.toCharPtr("PANIC: unprotected error in call to Lua API (%s)\n"),
            Lua.lua_tostring(L, -1)
        )
        return 0
    }

    fun luaL_newstate(): lua_State? {
        val L: lua_State? = LuaState.lua_newstate(l_alloc_delegate(), null)
        if (L != null) {
            LuaAPI.lua_atpanic(L, LuaAuxLib_delegate("panic"))
        }
        return L
    }

    class luaL_Reg(name: CharPtr?, func: lua_CFunction?) {
        var name: CharPtr?
        var func: lua_CFunction?

        init {
            this.name = name
            this.func = func
        }
    }

    class luaL_checkint_delegate : luaL_opt_delegate_integer {
        override fun exec(L: lua_State?, narg: Int): Int { //lua_Integer - Int32
            return luaL_checkint(L, narg)
        }
    }

    interface luaL_opt_delegate {
        /*Double*/ /*lua_Number*/
        fun exec(L: lua_State?, narg: Int): Double
    }

    interface luaL_opt_delegate_integer {
        /*Int32*/ /*lua_Integer*/
        fun exec(L: lua_State?, narg: Int): Int
    }

    //
//		 ** {======================================================
//		 ** Generic Buffer manipulation
//		 ** =======================================================
//
    class luaL_Buffer {
        var p /* current position in buffer */ = 0
        var lvl /* number of strings in the stack (level) */ = 0
        var L: lua_State? = null
        var buffer: CharPtr = CharPtr.Companion.toCharPtr(CharArray(LuaConf.LUAL_BUFFERSIZE))
    }

    class luaL_checknumber_delegate : luaL_opt_delegate {
        override fun exec(L: lua_State?, narg: Int): Double { //lua_Number - Double
            return luaL_checknumber(L, narg)
        }
    }

    class luaL_checkinteger_delegate : luaL_opt_delegate_integer {
        override fun exec(L: lua_State?, narg: Int): Int { //lua_Integer - Int32
            return luaL_checkinteger(L, narg)
        }
    }

    //
//		 ** {======================================================
//		 ** Load functions
//		 ** =======================================================
//
    class LoadF {
        var extraline = 0
        var f: StreamProxy? = null
        var buff: CharPtr = CharPtr.Companion.toCharPtr(CharArray(LuaConf.LUAL_BUFFERSIZE))
    }

    class LoadS {
        var s: CharPtr? = null
        var   /*uint*/size = 0
    }

    class l_alloc_delegate : lua_Alloc {
        override fun exec(t: ClassType): Any? {
            return l_alloc(t)
        }
    }

    class LuaAuxLib_delegate(private val name: String) : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return if ("panic" == name) {
                panic(L)
            } else {
                0
            }
        }

    }

    class getF_delegate : lua_Reader {
        override fun exec(L: lua_State?, ud: Any, sz: IntArray): CharPtr? { //uint - out
            return getF(L, ud, sz) //out
        }
    }

    class getS_delegate : lua_Reader {
        override fun exec(L: lua_State?, ud: Any, sz: IntArray): CharPtr? { //uint - out
            return getS(L, ud, sz) //out
        }
    }
}