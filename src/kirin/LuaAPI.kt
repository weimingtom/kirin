package kirin

import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.LuaObject.TValue
import kirin.LuaDo.Pfunc
import kirin.LuaObject.Table
import kirin.LuaZIO.ZIO
import kirin.LuaState.global_State
import kirin.LuaObject.Udata
import kirin.Lua.lua_Reader
import kirin.Lua.lua_Writer
import kirin.Lua.lua_Alloc
import kirin.LuaObject.Closure

//
// ** $Id: lapi.c,v 2.55.1.5 2008/07/04 18:41:18 roberto Exp $
// ** Lua API
// ** See Copyright Notice in lua.h
//
//using lu_mem = System.UInt32;
//using TValue = Lua.TValue;
//using StkId = Lua.TValue;
//using lua_Integer = System.Int32;
//using lua_Number = System.Double;
//using ptrdiff_t = System.Int32;
object LuaAPI {
    const val lua_ident =
        "\$Lua: " + Lua.LUA_RELEASE + " " + Lua.LUA_COPYRIGHT + " $\n" + "\$Authors: " + Lua.LUA_AUTHORS + " $\n" + "\$URL: www.lua.org $\n"

    fun api_checknelems(L: lua_State, n: Int) {
        LuaLimits.api_check(L, n <= TValue.Companion.minus(L.top!!, L.base_!!))
    }

    fun api_checkvalidindex(L: lua_State?, i: TValue) { //StkId
        LuaLimits.api_check(L, i !== LuaObject.luaO_nilobject)
    }

    fun api_incr_top(L: lua_State?) {
        LuaLimits.api_check(L, TValue.Companion.lessThan(L!!.top!!, L.ci!!.top!!))
        val top = arrayOfNulls<TValue>(1)
        top[0] = L!!.top
        //StkId
        TValue.Companion.inc(top) //ref
        L.top = top[0]
    }

    private fun index2adr(L: lua_State, idx: Int): TValue {
        var idx = idx
        return if (idx > 0) {
            val o: TValue = TValue.Companion.plus(L.base_!!, idx - 1)
            LuaLimits.api_check(L, idx <= TValue.Companion.minus(L.ci!!.top!!, L.base_!!))
            if (TValue.Companion.greaterEqual(o, L.top!!)) {
                LuaObject.luaO_nilobject
            } else {
                o
            }
        } else if (idx > Lua.LUA_REGISTRYINDEX) {
            LuaLimits.api_check(L, idx != 0 && -idx <= TValue.Companion.minus(L.top!!, L.base_!!))
            TValue.Companion.plus(L.top!!, idx)
        } else {
            when (idx) {
                Lua.LUA_REGISTRYINDEX -> {
                    LuaState.registry(L)
                }
                Lua.LUA_ENVIRONINDEX -> {
                    val func = LuaState.curr_func(L)
                    LuaObject.sethvalue(L, L.env, func!!.c.getEnv())
                    L.env
                }
                Lua.LUA_GLOBALSINDEX -> {
                    LuaState.gt(L)
                }
                else -> {
                    val func = LuaState.curr_func(L)
                    idx = Lua.LUA_GLOBALSINDEX - idx
                    if (idx <= func!!.c.getNupvalues()) func!!.c.upvalue!![idx - 1]!! else (LuaObject.luaO_nilobject as TValue)
                }
            }
        }
    }

    private fun getcurrenv(L: lua_State?): Table? {
        return if (L!!.ci === L!!.base_ci!![0]) { // no enclosing function?
            LuaObject.hvalue(LuaState.gt(L)) // use global table as environment
        } else {
            val func = LuaState.curr_func(L!!)
            func!!.c.getEnv()
        }
    }

    fun luaA_pushobject(L: lua_State, o: TValue?) {
        LuaObject.setobj2s(L, L.top, o!!)
        api_incr_top(L)
    }

    fun lua_checkstack(L: lua_State, size: Int): Int {
        var res = 1
        LuaLimits.lua_lock(L)
        if (size > LuaConf.LUAI_MAXCSTACK || TValue.Companion.minus(L.top!!, L.base_!!) + size > LuaConf.LUAI_MAXCSTACK) {
            res = 0 // stack overflow
        } else if (size > 0) {
            LuaDo.luaD_checkstack(L, size)
            if (TValue.Companion.lessThan(L.ci!!.top!!, TValue.Companion.plus(L.top!!, size))) {
                L.ci!!.top = TValue.Companion.plus(L.top!!, size)
            }
        }
        LuaLimits.lua_unlock(L)
        return res
    }

    fun lua_xmove(from: lua_State, to: lua_State, n: Int) {
        var i: Int
        if (from === to) {
            return
        }
        LuaLimits.lua_lock(to)
        api_checknelems(from, n)
        LuaLimits.api_check(from, LuaState.G(from) === LuaState.G(to))
        LuaLimits.api_check(from, TValue.Companion.minus(to.ci!!.top!!, to.top!!) >= n)
        from.top = TValue.Companion.minus(from.top!!, n)
        i = 0
        while (i < n) {
            val top = arrayOfNulls<TValue>(1)
            top[0] = to.top
            val ret: TValue? = TValue.Companion.inc(top) //ref - StkId
            to.top = top[0]
            LuaObject.setobj2s(to, ret, TValue.Companion.plus(from.top!!, i))
            i++
        }
        LuaLimits.lua_unlock(to)
    }

    fun lua_setlevel(from: lua_State, to: lua_State) {
        to.nCcalls = from.nCcalls
    }

    fun lua_atpanic(L: lua_State?, panicf: lua_CFunction?): lua_CFunction {
        val old: lua_CFunction
        LuaLimits.lua_lock(L)
        old = LuaState.G(L)!!.panic!!
        LuaState.G(L)!!.panic = panicf
        LuaLimits.lua_unlock(L)
        return old
    }

    fun lua_newthread(L: lua_State): lua_State {
        val L1: lua_State
        LuaLimits.lua_lock(L)
        LuaGC.luaC_checkGC(L)
        L1 = LuaState.luaE_newthread(L)
        LuaObject.setthvalue(L, L.top!!, L1)
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
        LuaConf.luai_userstatethread(L, L1)
        return L1
    }

    //
//		 ** basic stack manipulation
//
    fun lua_gettop(L: lua_State): Int {
        return LuaLimits.cast_int(TValue.Companion.minus(L.top!!, L.base_!!))
    }

    fun lua_settop(L: lua_State, idx: Int) {
        LuaLimits.lua_lock(L)
        if (idx >= 0) {
            LuaLimits.api_check(L, idx <= TValue.Companion.minus(L.stack_last!!, L.base_!!))
            while (TValue.Companion.lessThan(L.top!!, TValue.Companion.plus(L.base_!!, idx))) {
                val top = arrayOfNulls<TValue>(1)
                top[0] = L.top
                LuaObject.setnilvalue(TValue.Companion.inc(top)) //ref - StkId
                L.top = top[0]
            }
            L.top = TValue.Companion.plus(L.base_!!, idx)
        } else {
            LuaLimits.api_check(L, -(idx + 1) <= TValue.Companion.minus(L.top!!, L.base_!!))
            L.top = TValue.Companion.plus(L.top!!, idx + 1) // `subtract' index (index is negative)
        }
        LuaLimits.lua_unlock(L)
    }

    fun lua_remove(L: lua_State, idx: Int) {
        var p: TValue //StkId
        LuaLimits.lua_lock(L)
        p = index2adr(L, idx)
        api_checkvalidindex(L, p)
        while (TValue.Companion.lessThan(p[1].also { p = it }, L.top!!)) {
            LuaObject.setobjs2s(L, TValue.Companion.minus(p, 1), p)
        }
        val top = arrayOfNulls<TValue>(1)
        top[0] = L.top
        //StkId
        TValue.Companion.dec(top) //ref
        L.top = top[0]
        LuaLimits.lua_unlock(L)
    }

    fun lua_insert(L: lua_State, idx: Int) {
        val p: TValue //StkId
        val q = arrayOfNulls<TValue>(1) //StkId
        q[0] = TValue()
        LuaLimits.lua_lock(L)
        p = index2adr(L, idx)
        api_checkvalidindex(L, p)
        q[0] = L.top
        while (TValue.Companion.greaterThan(q[0]!!, p)) {
            //ref - StkId
            LuaObject.setobjs2s(L, q[0], TValue.Companion.minus(q[0]!!, 1)!!)
            TValue.Companion.dec(q)
        }
        LuaObject.setobjs2s(L, p, L.top!!)
        LuaLimits.lua_unlock(L)
    }

    fun lua_replace(L: lua_State, idx: Int) {
        val o: TValue //StkId
        LuaLimits.lua_lock(L)
        // explicit test for incompatible code
        if (idx == Lua.LUA_ENVIRONINDEX && L.ci === L.base_ci!![0]) {
            LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("no calling environment"))
        }
        api_checknelems(L, 1)
        o = index2adr(L, idx)
        api_checkvalidindex(L, o)
        if (idx == Lua.LUA_ENVIRONINDEX) {
            val func = LuaState.curr_func(L)
            LuaLimits.api_check(L, LuaObject.ttistable(TValue.Companion.minus(L.top!!, 1)))
            func!!.c.setEnv(LuaObject.hvalue(TValue.Companion.minus(L.top!!, 1)!!))
            LuaGC.luaC_barrier(L, func, TValue.Companion.minus(L.top!!, 1))
        } else {
            LuaObject.setobj(L, o, TValue.Companion.minus(L.top!!, 1)!!)
            if (idx < Lua.LUA_GLOBALSINDEX) { // function upvalue?
                LuaGC.luaC_barrier(L, LuaState.curr_func(L)!!, TValue.Companion.minus(L.top!!, 1))
            }
        }
        val top = arrayOfNulls<TValue>(1)
        top[0] = L.top
        //StkId
        TValue.Companion.dec(top) //ref
        L.top = top[0]
        LuaLimits.lua_unlock(L)
    }

    fun lua_pushvalue(L: lua_State, idx: Int) {
        LuaLimits.lua_lock(L)
        LuaObject.setobj2s(L, L.top, index2adr(L, idx))
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    //
//		 ** access functions (stack . C)
//
    fun lua_type(L: lua_State, idx: Int): Int {
        val o = index2adr(L, idx) //StkId
        return if (o === LuaObject.luaO_nilobject) Lua.LUA_TNONE else LuaObject.ttype(o)
    }

    fun lua_typename(L: lua_State?, t: Int): CharPtr { //UNUSED(L);
        return if (t == Lua.LUA_TNONE) CharPtr.Companion.toCharPtr("no value") else LuaTM.luaT_typenames[t]
    }

    fun lua_iscfunction(L: lua_State, idx: Int): Boolean {
        val o = index2adr(L, idx) //StkId
        return LuaObject.iscfunction(o)
    }

    fun lua_isnumber(L: lua_State, idx: Int): Int {
        val n = TValue()
        var o = index2adr(L, idx)
        val o_ref = arrayOfNulls<TValue>(1)
        o_ref[0] = o
        val ret = LuaVM.tonumber(o_ref, n) //ref
        o = o_ref[0]!!
        return ret
    }

    fun lua_isstring(L: lua_State, idx: Int): Int {
        val t = lua_type(L, idx)
        return if (t == Lua.LUA_TSTRING || t == Lua.LUA_TNUMBER) 1 else 0
    }

    fun lua_isuserdata(L: lua_State, idx: Int): Int {
        val o = index2adr(L, idx)
        return if (LuaObject.ttisuserdata(o) || LuaObject.ttislightuserdata(o)) 1 else 0
    }

    fun lua_rawequal(L: lua_State, index1: Int, index2: Int): Int {
        val o1 = index2adr(L, index1) //StkId
        val o2 = index2adr(L, index2) //StkId
        return if (o1 === LuaObject.luaO_nilobject || o2 === LuaObject.luaO_nilobject) 0 else LuaObject.luaO_rawequalObj(
            o1,
            o2
        )
    }

    fun lua_equal(L: lua_State, index1: Int, index2: Int): Int {
        val o1: TValue
        val o2: TValue //StkId
        val i: Int
        LuaLimits.lua_lock(L) // may call tag method
        o1 = index2adr(L, index1)
        o2 = index2adr(L, index2)
        i = if (o1 === LuaObject.luaO_nilobject || o2 === LuaObject.luaO_nilobject) 0 else LuaVM.equalobj(L, o1, o2)
        LuaLimits.lua_unlock(L)
        return i
    }

    fun lua_lessthan(L: lua_State, index1: Int, index2: Int): Int {
        val o1: TValue
        val o2: TValue //StkId
        val i: Int
        LuaLimits.lua_lock(L) // may call tag method
        o1 = index2adr(L, index1)
        o2 = index2adr(L, index2)
        i = if (o1 === LuaObject.luaO_nilobject || o2 === LuaObject.luaO_nilobject) 0 else LuaVM.luaV_lessthan(
            L,
            o1,
            o2
        )
        LuaLimits.lua_unlock(L)
        return i
    }

    fun lua_tonumber(L: lua_State, idx: Int): Double { //lua_Number
        val n = TValue()
        var o = index2adr(L, idx)
        val o_ref = arrayOfNulls<TValue>(1)
        o_ref[0] = o
        val ret = LuaVM.tonumber(o_ref, n) //ref
        o = o_ref[0]!!
        return if (ret != 0) {
            LuaObject.nvalue(o)
        } else {
            0 as Double
        }
    }

    fun lua_tointeger(L: lua_State, idx: Int): Int { //lua_Integer - Int32
        val n = TValue()
        var o = index2adr(L, idx)
        val o_ref = arrayOfNulls<TValue>(1)
        o_ref[0] = o
        val ret = LuaVM.tonumber(o_ref, n) //ref
        o = o_ref[0]!!
        return if (ret != 0) {
            val res = IntArray(1) //lua_Integer - Int32
            val num = LuaObject.nvalue(o) //lua_Number - Double
            LuaConf.lua_number2integer(res, num) //out
            res[0]
        } else {
            0
        }
    }

    fun lua_toboolean(L: lua_State, idx: Int): Int {
        val o = index2adr(L, idx)
        return if (LuaObject.l_isfalse(o) == 0) 1 else 0
    }

    fun lua_tolstring(L: lua_State, idx: Int, len: IntArray): CharPtr? { //uint - out
        var o = index2adr(L, idx) //StkId
        if (!LuaObject.ttisstring(o)) {
            LuaLimits.lua_lock(L) // `luaV_tostring' may create a new string
            if (LuaVM.luaV_tostring(L, o) == 0) { // conversion failed?
                len[0] = 0
                LuaLimits.lua_unlock(L)
                return null
            }
            LuaGC.luaC_checkGC(L)
            o = index2adr(L, idx) // previous call may reallocate the stack
            LuaLimits.lua_unlock(L)
        }
        len[0] = LuaObject.tsvalue(o).len
        return LuaObject.svalue(o)
    }

    fun lua_objlen(L: lua_State, idx: Int): Int { //uint
        val o = index2adr(L, idx) //StkId
        return when (LuaObject.ttype(o)) {
            Lua.LUA_TSTRING -> LuaObject.tsvalue(o).len
            Lua.LUA_TUSERDATA -> LuaObject.uvalue(o).len
            Lua.LUA_TTABLE -> LuaTable.luaH_getn(LuaObject.hvalue(o)!!) //(uint)
            Lua.LUA_TNUMBER -> {
                val l: Int //uint
                LuaLimits.lua_lock(L) // `luaV_tostring' may create a new string
                l = if (LuaVM.luaV_tostring(L, o) != 0) LuaObject.tsvalue(o).len else 0
                LuaLimits.lua_unlock(L)
                l
            }
            else -> 0
        }
    }

    fun lua_tocfunction(L: lua_State, idx: Int): lua_CFunction? {
        val o = index2adr(L, idx) //StkId
        return if (!LuaObject.iscfunction(o)) null else LuaObject.clvalue(o)!!.c.f
    }

    fun lua_touserdata(L: lua_State, idx: Int): Any? {
        val o = index2adr(L, idx) //StkId
        return when (LuaObject.ttype(o)) {
            Lua.LUA_TUSERDATA -> {
                LuaObject.rawuvalue(o)!!.user_data
            }
            Lua.LUA_TLIGHTUSERDATA -> {
                LuaObject.pvalue(o)
            }
            else -> {
                null
            }
        }
    }

    fun lua_tothread(L: lua_State, idx: Int): lua_State? {
        val o = index2adr(L, idx) //StkId
        return if (!LuaObject.ttisthread(o)) null else LuaObject.thvalue(o)
    }

    fun lua_topointer(L: lua_State, idx: Int): Any? {
        val o = index2adr(L, idx) //StkId
        return when (LuaObject.ttype(o)) {
            Lua.LUA_TTABLE -> {
                LuaObject.hvalue(o)
            }
            Lua.LUA_TFUNCTION -> {
                LuaObject.clvalue(o)
            }
            Lua.LUA_TTHREAD -> {
                LuaObject.thvalue(o)
            }
            Lua.LUA_TUSERDATA, Lua.LUA_TLIGHTUSERDATA -> {
                lua_touserdata(L, idx)
            }
            else -> {
                null
            }
        }
    }

    //
//		 ** push functions (C . stack)
//
    fun lua_pushnil(L: lua_State) {
        LuaLimits.lua_lock(L)
        LuaObject.setnilvalue(L.top)
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    fun lua_pushnumber(L: lua_State, n: Double) { //lua_Number - Double
        LuaLimits.lua_lock(L)
        LuaObject.setnvalue(L.top!!, n)
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    fun lua_pushinteger(L: lua_State, n: Int) { //lua_Integer - Int32
        LuaLimits.lua_lock(L)
        LuaObject.setnvalue(L.top!!, LuaLimits.cast_num(n))
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    fun lua_pushlstring(L: lua_State, s: CharPtr?, len: Int) { //uint
        LuaLimits.lua_lock(L)
        LuaGC.luaC_checkGC(L)
        LuaObject.setsvalue2s(L, L.top!!, LuaString.luaS_newlstr(L, s, len))
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    fun lua_pushstring(L: lua_State, s: CharPtr?) {
        if (CharPtr.Companion.isEqual(s, null)) {
            lua_pushnil(L)
        } else {
            lua_pushlstring(L, s, CLib.strlen(s)) //(uint)
        }
    }

    fun lua_pushvfstring(L: lua_State?, fmt: CharPtr?, argp: Array<Any?>): CharPtr? {
        val ret: CharPtr?
        LuaLimits.lua_lock(L)
        LuaGC.luaC_checkGC(L)
        ret = LuaObject.luaO_pushvfstring(L, fmt, *argp)
        LuaLimits.lua_unlock(L)
        return ret
    }

    fun lua_pushfstring(L: lua_State?, fmt: CharPtr?): CharPtr? {
        val ret: CharPtr?
        LuaLimits.lua_lock(L)
        LuaGC.luaC_checkGC(L)
        ret = LuaObject.luaO_pushvfstring(L, fmt, null)
        LuaLimits.lua_unlock(L)
        return ret
    }

    fun lua_pushfstring(L: lua_State?, fmt: CharPtr?, vararg p: Any?): CharPtr? {
        val ret: CharPtr?
        LuaLimits.lua_lock(L)
        LuaGC.luaC_checkGC(L)
        ret = LuaObject.luaO_pushvfstring(L, fmt, *p)
        LuaLimits.lua_unlock(L)
        return ret
    }

    fun lua_pushcclosure(L: lua_State, fn: lua_CFunction?, n: Int) {
        var n = n
        val cl: Closure?
        LuaLimits.lua_lock(L)
        LuaGC.luaC_checkGC(L)
        api_checknelems(L, n)
        cl = LuaFunc.luaF_newCclosure(L, n, getcurrenv(L))
        cl!!.c.f = fn
        L.top = TValue.Companion.minus(L.top!!, n)
        while (n-- != 0) {
            LuaObject.setobj2n(L, cl.c.upvalue!![n]!!, TValue.Companion.plus(L.top!!, n))
        }
        LuaObject.setclvalue(L, L.top!!, cl)
        LuaLimits.lua_assert(LuaGC.iswhite(LuaState.obj2gco(cl)))
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    fun lua_pushboolean(L: lua_State, b: Int) {
        LuaLimits.lua_lock(L)
        LuaObject.setbvalue(L.top!!, if (b != 0) 1 else 0) // ensure that true is 1
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    fun lua_pushlightuserdata(L: lua_State, p: Any?) {
        LuaLimits.lua_lock(L)
        LuaObject.setpvalue(L.top!!, p)
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    fun lua_pushthread(L: lua_State): Int {
        LuaLimits.lua_lock(L)
        LuaObject.setthvalue(L, L.top!!, L)
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
        return if (LuaState.G(L)!!.mainthread === L) 1 else 0
    }

    //
//		 ** get functions (Lua . stack)
//
    fun lua_gettable(L: lua_State, idx: Int) {
        val t: TValue //StkId
        LuaLimits.lua_lock(L)
        t = index2adr(L, idx)
        api_checkvalidindex(L, t)
        LuaVM.luaV_gettable(L, t, TValue.Companion.minus(L.top!!, 1), TValue.Companion.minus(L.top!!, 1)!!)
        LuaLimits.lua_unlock(L)
    }

    fun lua_getfield(L: lua_State, idx: Int, k: CharPtr?) {
        val t: TValue //StkId
        val key = TValue()
        LuaLimits.lua_lock(L)
        t = index2adr(L, idx)
        api_checkvalidindex(L, t)
        LuaObject.setsvalue(L, key, LuaString.luaS_new(L, k))
        LuaVM.luaV_gettable(L, t, key, L.top!!)
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    fun lua_rawget(L: lua_State, idx: Int) {
        val t: TValue //StkId
        LuaLimits.lua_lock(L)
        t = index2adr(L, idx)
        LuaLimits.api_check(L, LuaObject.ttistable(t))
        LuaObject.setobj2s(
            L,
            TValue.Companion.minus(L.top!!, 1),
            LuaTable.luaH_get(LuaObject.hvalue(t), TValue.Companion.minus(L.top!!, 1)!!)
        )
        LuaLimits.lua_unlock(L)
    }

    fun lua_rawgeti(L: lua_State, idx: Int, n: Int) {
        val o: TValue //StkId
        LuaLimits.lua_lock(L)
        o = index2adr(L, idx)
        LuaLimits.api_check(L, LuaObject.ttistable(o))
        LuaObject.setobj2s(L, L.top, LuaTable.luaH_getnum(LuaObject.hvalue(o), n))
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    fun lua_createtable(L: lua_State, narray: Int, nrec: Int) {
        LuaLimits.lua_lock(L)
        LuaGC.luaC_checkGC(L)
        LuaObject.sethvalue(L, L.top!!, LuaTable.luaH_new(L, narray, nrec))
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    fun lua_getmetatable(L: lua_State, objindex: Int): Int {
        val obj: TValue
        var mt: Table? = null
        val res: Int
        LuaLimits.lua_lock(L)
        obj = index2adr(L, objindex)
        mt = when (LuaObject.ttype(obj)) {
            Lua.LUA_TTABLE -> {
                LuaObject.hvalue(obj)!!.metatable
            }
            Lua.LUA_TUSERDATA -> {
                LuaObject.uvalue(obj).metatable
            }
            else -> {
                LuaState.G(L)!!.mt[LuaObject.ttype(obj)]
            }
        }
        res = if (mt == null) {
            0
        } else {
            LuaObject.sethvalue(L, L.top!!, mt)
            api_incr_top(L)
            1
        }
        LuaLimits.lua_unlock(L)
        return res
    }

    fun lua_getfenv(L: lua_State, idx: Int) {
        val o: TValue //StkId
        LuaLimits.lua_lock(L)
        o = index2adr(L, idx)
        api_checkvalidindex(L, o)
        when (LuaObject.ttype(o)) {
            Lua.LUA_TFUNCTION -> {
                LuaObject.sethvalue(L, L.top!!, LuaObject.clvalue(o)!!.c.getEnv())
            }
            Lua.LUA_TUSERDATA -> {
                LuaObject.sethvalue(L, L.top!!, LuaObject.uvalue(o).env)
            }
            Lua.LUA_TTHREAD -> {
                LuaObject.setobj2s(L, L.top, LuaState.gt(LuaObject.thvalue(o)))
            }
            else -> {
                LuaObject.setnilvalue(L.top)
            }
        }
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
    }

    //
//		 ** set functions (stack . Lua)
//
    fun lua_settable(L: lua_State, idx: Int) {
        val t: TValue //StkId
        LuaLimits.lua_lock(L)
        api_checknelems(L, 2)
        t = index2adr(L, idx)
        api_checkvalidindex(L, t)
        LuaVM.luaV_settable(L, t, TValue.Companion.minus(L.top!!, 2)!!, TValue.Companion.minus(L.top!!, 1)!!)
        L.top = TValue.Companion.minus(L.top!!, 2) // pop index and value
        LuaLimits.lua_unlock(L)
    }

    fun lua_setfield(L: lua_State, idx: Int, k: CharPtr?) {
        val t: TValue //StkId
        val key = TValue()
        LuaLimits.lua_lock(L)
        api_checknelems(L, 1)
        t = index2adr(L, idx)
        api_checkvalidindex(L, t)
        LuaObject.setsvalue(L, key, LuaString.luaS_new(L, k))
        LuaVM.luaV_settable(L, t, key, TValue.Companion.minus(L.top!!, 1)!!)
        val top = arrayOfNulls<TValue>(1)
        top[0] = L.top
        //StkId
        TValue.Companion.dec(top) // pop value  - ref
        L.top = top[0]
        LuaLimits.lua_unlock(L)
    }

    fun lua_rawset(L: lua_State, idx: Int) {
        val t: TValue //StkId
        LuaLimits.lua_lock(L)
        api_checknelems(L, 2)
        t = index2adr(L, idx)
        LuaLimits.api_check(L, LuaObject.ttistable(t))
        LuaObject.setobj2t(
            L,
            LuaTable.luaH_set(L, LuaObject.hvalue(t), TValue.Companion.minus(L.top!!, 2)!!),
            TValue.Companion.minus(L.top!!, 1)!!
        )
        LuaGC.luaC_barriert(L, LuaObject.hvalue(t)!!, TValue.Companion.minus(L.top!!, 1))
        L.top = TValue.Companion.minus(L.top!!, 2)
        LuaLimits.lua_unlock(L)
    }

    fun lua_rawseti(L: lua_State, idx: Int, n: Int) {
        val o: TValue //StkId
        LuaLimits.lua_lock(L)
        api_checknelems(L, 1)
        o = index2adr(L, idx)
        LuaLimits.api_check(L, LuaObject.ttistable(o))
        LuaObject.setobj2t(L, LuaTable.luaH_setnum(L, LuaObject.hvalue(o), n), TValue.Companion.minus(L.top!!, 1)!!)
        LuaGC.luaC_barriert(L, LuaObject.hvalue(o)!!, TValue.Companion.minus(L.top!!, 1))
        val top = arrayOfNulls<TValue>(1)
        top[0] = L.top
        //StkId
        TValue.Companion.dec(top) //ref
        L.top = top[0]
        LuaLimits.lua_unlock(L)
    }

    fun lua_setmetatable(L: lua_State, objindex: Int): Int {
        val obj: TValue
        val mt: Table?
        LuaLimits.lua_lock(L)
        api_checknelems(L, 1)
        obj = index2adr(L, objindex)
        api_checkvalidindex(L, obj)
        mt = if (LuaObject.ttisnil(TValue.Companion.minus(L.top!!, 1))) {
            null
        } else {
            LuaLimits.api_check(L, LuaObject.ttistable(TValue.Companion.minus(L.top!!, 1)))
            LuaObject.hvalue(TValue.Companion.minus(L.top!!, 1)!!)
        }
        when (LuaObject.ttype(obj)) {
            Lua.LUA_TTABLE -> {
                LuaObject.hvalue(obj)!!.metatable = mt
                if (mt != null) {
                    LuaGC.luaC_objbarriert(L, LuaObject.hvalue(obj)!!, mt)
                }
            }
            Lua.LUA_TUSERDATA -> {
                LuaObject.uvalue(obj).metatable = mt
                if (mt != null) {
                    LuaGC.luaC_objbarrier(L, LuaObject.rawuvalue(obj), mt)
                }
            }
            else -> {
                LuaState.G(L)!!.mt[LuaObject.ttype(obj)] = mt
            }
        }
        val top = arrayOfNulls<TValue>(1)
        top[0] = L.top
        //StkId
        TValue.Companion.dec(top) //ref
        L.top = top[0]
        LuaLimits.lua_unlock(L)
        return 1
    }

    fun lua_setfenv(L: lua_State, idx: Int): Int {
        val o: TValue //StkId
        var res = 1
        LuaLimits.lua_lock(L)
        api_checknelems(L, 1)
        o = index2adr(L, idx)
        api_checkvalidindex(L, o)
        LuaLimits.api_check(L, LuaObject.ttistable(TValue.Companion.minus(L.top!!, 1)))
        when (LuaObject.ttype(o)) {
            Lua.LUA_TFUNCTION -> {
                LuaObject.clvalue(o)!!.c.setEnv(LuaObject.hvalue(TValue.Companion.minus(L.top!!, 1)!!))
            }
            Lua.LUA_TUSERDATA -> {
                LuaObject.uvalue(o).env = LuaObject.hvalue(TValue.Companion.minus(L.top!!, 1)!!)
            }
            Lua.LUA_TTHREAD -> {
                LuaObject.sethvalue(
                    L,
                    LuaState.gt(LuaObject.thvalue(o)),
                    LuaObject.hvalue(TValue.Companion.minus(L.top!!, 1)!!)
                )
            }
            else -> {
                res = 0
            }
        }
        if (res != 0) {
            LuaGC.luaC_objbarrier(L, LuaObject.gcvalue(o), LuaObject.hvalue(TValue.Companion.minus(L.top!!, 1)!!)!!)
        }
        val top = arrayOfNulls<TValue>(1)
        top[0] = L.top
        //StkId
        TValue.Companion.dec(top) //ref
        L.top = top[0]
        LuaLimits.lua_unlock(L)
        return res
    }

    //
//		 ** `load' and `call' functions (run Lua code)
//
    fun adjustresults(L: lua_State, nres: Int) {
        if (nres == Lua.LUA_MULTRET && TValue.Companion.greaterEqual(L.top!!, L.ci!!.top!!)) {
            L.ci!!.top = L.top
        }
    }

    fun checkresults(L: lua_State, na: Int, nr: Int) {
        LuaLimits.api_check(L, nr == Lua.LUA_MULTRET || TValue.Companion.minus(L.ci!!.top!!, L.top!!) >= nr - na)
    }

    fun lua_call(L: lua_State, nargs: Int, nresults: Int) {
        val func: TValue //StkId
        LuaLimits.lua_lock(L)
        api_checknelems(L, nargs + 1)
        checkresults(L, nargs, nresults)
        func = TValue.Companion.minus(L.top!!, nargs + 1)!!
        LuaDo.luaD_call(L, func, nresults)
        adjustresults(L, nresults)
        LuaLimits.lua_unlock(L)
    }

    private fun f_call(L: lua_State?, ud: Any?) {
        val c = ud as? CallS
        LuaDo.luaD_call(L, c!!.func!!, c.nresults)
    }

    fun lua_pcall(L: lua_State, nargs: Int, nresults: Int, errfunc: Int): Int {
        val c = CallS()
        val status: Int
        val func: Int //ptrdiff_t - Int32
        LuaLimits.lua_lock(L)
        api_checknelems(L, nargs + 1)
        checkresults(L, nargs, nresults)
        func = if (errfunc == 0) {
            0
        } else {
            val o = index2adr(L, errfunc) //StkId
            api_checkvalidindex(L, o)
            LuaDo.savestack(L, o)
        }
        c.func = TValue.Companion.minus(L.top!!, nargs + 1) // function to be called
        c.nresults = nresults
        status = LuaDo.luaD_pcall(L, f_call_delegate(), c, LuaDo.savestack(L, c.func), func)
        adjustresults(L, nresults)
        LuaLimits.lua_unlock(L)
        return status
    }

    private fun f_Ccall(L: lua_State?, ud: Any?) {
        val c = ud as? CCallS
        val cl: Closure?
        cl = LuaFunc.luaF_newCclosure(L, 0, getcurrenv(L))
        cl!!.c.f = c!!.func
        LuaObject.setclvalue(L, L!!.top!!, cl) // push function
        api_incr_top(L)
        LuaObject.setpvalue(L!!.top!!, c.ud) // push only argument
        api_incr_top(L)
        LuaDo.luaD_call(L, TValue.Companion.minus(L!!.top!!, 2)!!, 0)
    }

    fun lua_cpcall(L: lua_State, func: lua_CFunction?, ud: Any?): Int {
        val c = CCallS()
        val status: Int
        LuaLimits.lua_lock(L)
        c.func = func
        c.ud = ud
        status = LuaDo.luaD_pcall(L, f_Ccall_delegate(), c, LuaDo.savestack(L, L.top), 0)
        LuaLimits.lua_unlock(L)
        return status
    }

    fun lua_load(L: lua_State?, reader: lua_Reader?, data: Any?, chunkname: CharPtr?): Int {
        var chunkname = chunkname
        val z = ZIO()
        val status: Int
        LuaLimits.lua_lock(L)
        if (CharPtr.Companion.isEqual(chunkname, null)) {
            chunkname = CharPtr.Companion.toCharPtr("?")
        }
        LuaZIO.luaZ_init(L, z, reader, data)
        status = LuaDo.luaD_protectedparser(L!!, z, chunkname)
        LuaLimits.lua_unlock(L)
        return status
    }

    fun lua_dump(L: lua_State, writer: lua_Writer?, data: Any?): Int {
        val status: Int
        val o: TValue
        LuaLimits.lua_lock(L)
        api_checknelems(L, 1)
        o = TValue.Companion.minus(L.top!!, 1)!!
        status = if (LuaObject.isLfunction(o)) {
            LuaDump.luaU_dump(L, LuaObject.clvalue(o)!!.l.p!!, writer, data, 0)
        } else {
            1
        }
        LuaLimits.lua_unlock(L)
        return status
    }

    fun lua_status(L: lua_State): Int {
        return L.status.toInt()
    }

    //
//		 ** Garbage-collection function
//
    fun lua_gc(L: lua_State?, what: Int, data: Int): Int {
        var res = 0
        val g: global_State
        LuaLimits.lua_lock(L)
        g = LuaState.G(L)!!
        when (what) {
            Lua.LUA_GCSTOP -> {
                g.GCthreshold = LuaLimits.MAX_LUMEM.toLong()
            }
            Lua.LUA_GCRESTART -> {
                g.GCthreshold = g.totalbytes
            }
            Lua.LUA_GCCOLLECT -> {
                LuaGC.luaC_fullgc(L)
            }
            Lua.LUA_GCCOUNT -> {
                // GC values are expressed in Kbytes: #bytes/2^10
                res = LuaLimits.cast_int(g.totalbytes shr 10)
            }
            Lua.LUA_GCCOUNTB -> {
                res = LuaLimits.cast_int(g.totalbytes and 0x3ff)
            }
            Lua.LUA_GCSTEP -> {
                val a = data.toLong() shl 10 //lu_mem - UInt32 - lu_mem - UInt32
                if (a <= g.totalbytes) {
                    g.GCthreshold = g.totalbytes - a //(uint)
                } else {
                    g.GCthreshold = 0
                }
                while (g.GCthreshold <= g.totalbytes) {
                    LuaGC.luaC_step(L)
                    if (g.gcstate.toInt() == LuaGC.GCSpause) { // end of cycle?
                        res = 1 // signal it
                        break
                    }
                }
            }
            Lua.LUA_GCSETPAUSE -> {
                res = g.gcpause
                g.gcpause = data
            }
            Lua.LUA_GCSETSTEPMUL -> {
                res = g.gcstepmul
                g.gcstepmul = data
            }
            else -> {
                res = -1 // invalid option
            }
        }
        LuaLimits.lua_unlock(L)
        return res
    }

    //
//		 ** miscellaneous functions
//
    fun lua_error(L: lua_State): Int {
        LuaLimits.lua_lock(L)
        api_checknelems(L, 1)
        LuaDebug.luaG_errormsg(L)
        LuaLimits.lua_unlock(L)
        return 0 // to avoid warnings
    }

    fun lua_next(L: lua_State, idx: Int): Int {
        val t: TValue //StkId
        val more: Int
        LuaLimits.lua_lock(L)
        t = index2adr(L, idx)
        LuaLimits.api_check(L, LuaObject.ttistable(t))
        more = LuaTable.luaH_next(L, LuaObject.hvalue(t)!!, TValue.Companion.minus(L.top!!, 1)!!)
        if (more != 0) {
            api_incr_top(L)
        } else { // no more elements
            val top = arrayOfNulls<TValue>(1)
            top[0] = L.top
            //StkId
            TValue.Companion.dec(top) // remove key  - ref
            L.top = top[0]
        }
        LuaLimits.lua_unlock(L)
        return more
    }

    fun lua_concat(L: lua_State, n: Int) {
        LuaLimits.lua_lock(L)
        api_checknelems(L, n)
        if (n >= 2) {
            LuaGC.luaC_checkGC(L)
            LuaVM.luaV_concat(L, n, LuaLimits.cast_int(TValue.Companion.minus(L.top!!, L.base_!!)) - 1) //FIXME:
            L.top = TValue.Companion.minus(L.top!!, n - 1)
        } else if (n == 0) { // push empty string
            LuaObject.setsvalue2s(L, L.top!!, LuaString.luaS_newlstr(L, CharPtr.Companion.toCharPtr(""), 0))
            api_incr_top(L)
        }
        // else n == 1; nothing to do
        LuaLimits.lua_unlock(L)
    }

    fun lua_getallocf(L: lua_State?, ud: Array<Any?>): lua_Alloc { //ref
        val f: lua_Alloc
        LuaLimits.lua_lock(L)
        if (ud[0] != null) {
            ud[0] = LuaState.G(L)!!.ud
        }
        f = LuaState.G(L)!!.frealloc!!
        LuaLimits.lua_unlock(L)
        return f
    }

    fun lua_setallocf(L: lua_State?, f: lua_Alloc?, ud: Any?) {
        LuaLimits.lua_lock(L)
        LuaState.G(L)!!.ud = ud
        LuaState.G(L)!!.frealloc = f
        LuaLimits.lua_unlock(L)
    }

    fun lua_newuserdata(L: lua_State, size: Int): Any? { //uint
        val u: Udata
        LuaLimits.lua_lock(L)
        LuaGC.luaC_checkGC(L)
        u = LuaString.luaS_newudata(L, size, getcurrenv(L))
        LuaObject.setuvalue(L, L.top!!, u)
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
        return u.user_data
    }

    fun lua_newuserdata(L: lua_State, t: ClassType?): Any? {
        val u: Udata
        LuaLimits.lua_lock(L)
        LuaGC.luaC_checkGC(L)
        u = LuaString.luaS_newudata(L, t, getcurrenv(L))
        LuaObject.setuvalue(L, L.top!!, u)
        api_incr_top(L)
        LuaLimits.lua_unlock(L)
        return u.user_data
    }

    private fun aux_upvalue(fi: TValue, n: Int, `val`: Array<TValue?>): CharPtr? { //ref - StkId
        val f: Closure
        if (!LuaObject.ttisfunction(fi)) {
            return null
        }
        f = LuaObject.clvalue(fi)!!
        return if (f.c.getIsC().toInt() != 0) {
            if (!(1 <= n && n <= f.c.getNupvalues())) {
                return null
            }
            `val`[0] = f.c.upvalue!![n - 1]
            CharPtr.Companion.toCharPtr("")
        } else {
            val p = f.l.p
            if (!(1 <= n && n <= p!!.sizeupvalues)) {
                return null
            }
            `val`[0] = f.l.upvals!![n - 1]!!.v
            LuaObject.getstr(p!!.upvalues!![n - 1])
        }
    }

    fun lua_getupvalue(L: lua_State, funcindex: Int, n: Int): CharPtr? {
        val name: CharPtr?
        var `val` = TValue()
        LuaLimits.lua_lock(L)
        val val_ref = arrayOfNulls<TValue>(1)
        val_ref[0] = `val`
        name = aux_upvalue(index2adr(L, funcindex), n, val_ref) //ref
        `val` = val_ref[0]!!
        if (CharPtr.Companion.isNotEqual(name, null)) {
            LuaObject.setobj2s(L, L.top, `val`)
            api_incr_top(L)
        }
        LuaLimits.lua_unlock(L)
        return name
    }

    fun lua_setupvalue(L: lua_State, funcindex: Int, n: Int): CharPtr? {
        val name: CharPtr?
        var `val` = TValue()
        val fi: TValue //StkId
        LuaLimits.lua_lock(L)
        fi = index2adr(L, funcindex)
        api_checknelems(L, 1)
        val val_ref = arrayOfNulls<TValue>(1)
        val_ref[0] = `val`
        name = aux_upvalue(fi, n, val_ref) //ref
        `val` = val_ref[0]!!
        if (CharPtr.Companion.isNotEqual(name, null)) {
            val top = arrayOfNulls<TValue>(1)
            top[0] = L.top
            //StkId
            TValue.Companion.dec(top) //ref
            L.top = top[0]
            LuaObject.setobj(L, `val`, L.top!!)
            LuaGC.luaC_barrier(L, LuaObject.clvalue(fi)!!, L.top)
        }
        LuaLimits.lua_unlock(L)
        return name
    }

    /*
	 ** Execute a protected call.
	 */
    class CallS {
        /* data to `f_call' */
        var func /*StkId*/: TValue? = null
        var nresults = 0
    }

    class f_call_delegate : Pfunc {
        override fun exec(L: lua_State?, ud: Any?) {
            f_call(L, ud)
        }
    }

    /*
	 ** Execute a protected C call.
	 */
    class CCallS {
        /* data to `f_Ccall' */
        var func: lua_CFunction? = null
        var ud: Any? = null
    }

    class f_Ccall_delegate : Pfunc {
        override fun exec(L: lua_State?, ud: Any?) {
            f_Ccall(L, ud)
        }
    }
}