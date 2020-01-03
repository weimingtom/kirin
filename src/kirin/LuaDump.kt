package kirin

import kirin.CLib.CharPtr
import kirin.Lua.lua_Writer
import kirin.LuaObject.Proto
import kirin.LuaObject.TString
import kirin.LuaState.lua_State

//
// ** $Id: ldump.c,v 2.8.1.1 2007/12/27 13:02:25 roberto Exp $
// ** save precompiled Lua chunks
// ** See Copyright Notice in lua.h
//
//using lua_Number = System.Double;
//using TValue = Lua.TValue;
object LuaDump {
    fun DumpMem(b: Any?, D: DumpState, t: ClassType) {
        val bytes = t.ObjToBytes(b!!)
        val ch = CharArray(bytes!!.size)
        for (i in bytes!!.indices) {
            ch[i] = bytes[i].toChar()
        }
        val str: CharPtr = CharPtr.Companion.toCharPtr(ch)
        DumpBlock(str, str.chars!!.size, D) //(uint)
    }

    fun DumpMem_int(b: IntArray, n: Int, D: DumpState, t: ClassType) {
        ClassType.Companion.Assert(b.size == n)
        for (i in 0 until n) {
            DumpMem(b[i], D, t)
        }
    }

    fun DumpMem_long(b: LongArray, n: Int, D: DumpState, t: ClassType) {
        ClassType.Companion.Assert(b.size == n)
        for (i in 0 until n) {
            DumpMem(b[i], D, t)
        }
    }

    fun DumpVar(x: Any?, D: DumpState, t: ClassType) {
        DumpMem(x, D, t)
    }

    private fun DumpBlock(b: CharPtr, size: Int, D: DumpState) { //uint
        if (D.status == 0) {
            LuaLimits.lua_unlock(D.L)
            D.status = D.writer!!.exec(D.L!!, b, size, D.data!!)
            LuaLimits.lua_lock(D.L)
        }
    }

    private fun DumpChar(y: Int, D: DumpState) {
        val x = y.toChar()
        DumpVar(x, D, ClassType(ClassType.Companion.TYPE_CHAR))
    }

    private fun DumpInt(x: Int, D: DumpState) {
        DumpVar(x, D, ClassType(ClassType.Companion.TYPE_INT))
    }

    private fun DumpNumber(x: Double, D: DumpState) { //lua_Number
        DumpVar(x, D, ClassType(ClassType.Companion.TYPE_DOUBLE))
    }

    private fun DumpVector_int(b: IntArray, n: Int, D: DumpState, t: ClassType) {
        DumpInt(n, D)
        DumpMem_int(b, n, D, t)
    }

    private fun DumpVector_long(b: LongArray, n: Int, D: DumpState, t: ClassType) {
        DumpInt(n, D)
        DumpMem_long(b, n, D, t)
    }

    private fun DumpString(s: TString?, D: DumpState) {
        if (s == null || CharPtr.Companion.isEqual(LuaObject.getstr(s), null)) {
            val size = 0 //uint
            DumpVar(size, D, ClassType(ClassType.Companion.TYPE_INT))
        } else {
            val size = s.getTs().len + 1 // include trailing '\0'  - uint
            DumpVar(size, D, ClassType(ClassType.Companion.TYPE_INT))
            DumpBlock(LuaObject.getstr(s)!!, size, D)
        }
    }

    private fun DumpCode(f: Proto, D: DumpState) {
        DumpVector_long(f.code!!, f.sizecode, D, ClassType(ClassType.Companion.TYPE_LONG))
    }

    private fun DumpConstants(f: Proto, D: DumpState) {
        var i: Int
        var n = f.sizek
        DumpInt(n, D)
        i = 0
        while (i < n) {
            //const
            val o = f.k!![i]
            DumpChar(LuaObject.ttype(o), D)
            when (LuaObject.ttype(o)) {
                Lua.LUA_TNIL -> {
                }
                Lua.LUA_TBOOLEAN -> {
                    DumpChar(LuaObject.bvalue(o!!), D)
                }
                Lua.LUA_TNUMBER -> {
                    DumpNumber(LuaObject.nvalue(o!!), D)
                }
                Lua.LUA_TSTRING -> {
                    DumpString(LuaObject.rawtsvalue(o!!), D)
                }
                else -> {
                    LuaLimits.lua_assert(0) // cannot happen
                }
            }
            i++
        }
        n = f.sizep
        DumpInt(n, D)
        i = 0
        while (i < n) {
            DumpFunction(f.p!![i]!!, f.source, D)
            i++
        }
    }

    private fun DumpDebug(f: Proto, D: DumpState) {
        var i: Int
        var n: Int
        n = if (D.strip != 0) 0 else f.sizelineinfo
        DumpVector_int(f.lineinfo!!, n, D, ClassType(ClassType.Companion.TYPE_INT))
        n = if (D.strip != 0) 0 else f.sizelocvars
        DumpInt(n, D)
        i = 0
        while (i < n) {
            DumpString(f.locvars!![i]!!.varname, D)
            DumpInt(f.locvars!![i]!!.startpc, D)
            DumpInt(f.locvars!![i]!!.endpc, D)
            i++
        }
        n = if (D.strip != 0) 0 else f.sizeupvalues
        DumpInt(n, D)
        i = 0
        while (i < n) {
            DumpString(f.upvalues!![i], D)
            i++
        }
    }

    private fun DumpFunction(f: Proto, p: TString?, D: DumpState) {
        DumpString(if (f.source === p || D.strip != 0) null else f.source, D)
        DumpInt(f.linedefined, D)
        DumpInt(f.lastlinedefined, D)
        DumpChar(f.nups.toInt(), D)
        DumpChar(f.numparams.toInt(), D)
        DumpChar(f.is_vararg.toInt(), D)
        DumpChar(f.maxstacksize.toInt(), D)
        DumpCode(f, D)
        DumpConstants(f, D)
        DumpDebug(f, D)
    }

    private fun DumpHeader(D: DumpState) {
        val h: CharPtr = CharPtr.Companion.toCharPtr(CharArray(LuaUndump.LUAC_HEADERSIZE))
        LuaUndump.luaU_header(h)
        DumpBlock(h, LuaUndump.LUAC_HEADERSIZE, D)
    }

    //
//		 ** dump Lua function as precompiled chunk
//
    fun luaU_dump(L: lua_State?, f: Proto, w: lua_Writer?, data: Any?, strip: Int): Int {
        val D = DumpState()
        D.L = L
        D.writer = w
        D.data = data
        D.strip = strip
        D.status = 0
        DumpHeader(D)
        DumpFunction(f, null, D)
        return D.status
    }

    class DumpState {
        var L: lua_State? = null
        var writer: lua_Writer? = null
        var data: Any? = null
        var strip = 0
        var status = 0
    }
}