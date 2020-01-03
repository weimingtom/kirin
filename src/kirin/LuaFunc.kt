package kirin

import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.LuaObject.TValue
import kirin.LuaObject.Proto
import kirin.LuaObject.Table
import kirin.LuaObject.UpVal
import kirin.LuaState.GCObjectRef
import kirin.LuaState.NextRef
import kirin.LuaState.OpenValRef
import kirin.LuaObject.Closure

//
// ** $Id: lfunc.c,v 2.12.1.2 2007/12/28 14:58:43 roberto Exp $
// ** Auxiliary functions to manipulate prototypes and closures
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
//using StkId = TValue;
//using Instruction = System.UInt32;
object LuaFunc {
    fun sizeCclosure(n: Int): Int {
        return CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_CCLOSURE)) + CLib.GetUnmanagedSize(
            ClassType(
                ClassType.Companion.TYPE_TVALUE
            )
        ) * (n - 1) //typeof(CClosure)//typeof(TValue)
    }

    fun sizeLclosure(n: Int): Int {
        return CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_LCLOSURE)) + CLib.GetUnmanagedSize(
            ClassType(
                ClassType.Companion.TYPE_TVALUE
            )
        ) * (n - 1) //typeof(LClosure)//typeof(TValue)
    }

    fun luaF_newCclosure(
        L: lua_State?,
        nelems: Int,
        e: Table?
    ): Closure? { //Closure c = (Closure)luaM_malloc(L, sizeCclosure(nelems));
        val c = LuaMem.luaM_new_Closure(L, ClassType(ClassType.Companion.TYPE_CLOSURE))
        LuaMem.AddTotalBytes(L, sizeCclosure(nelems))
        LuaGC.luaC_link(L, LuaState.obj2gco(c), Lua.LUA_TFUNCTION.toByte())
        c!!.c.setIsC(1.toByte())
        c.c.setEnv(e)
        c.c.setNupvalues(LuaLimits.cast_byte(nelems))
        c.c.upvalue = arrayOfNulls(nelems)
        for (i in 0 until nelems) {
            c.c.upvalue!![i] = TValue()
        }
        return c
    }

    fun luaF_newLclosure(
        L: lua_State?,
        nelems: Int,
        e: Table?
    ): Closure? { //Closure c = (Closure)luaM_malloc(L, sizeLclosure(nelems));
        var nelems = nelems
        val c = LuaMem.luaM_new_Closure(L, ClassType(ClassType.Companion.TYPE_CLOSURE))
        LuaMem.AddTotalBytes(L, sizeLclosure(nelems))
        LuaGC.luaC_link(L, LuaState.obj2gco(c), Lua.LUA_TFUNCTION.toByte())
        c!!.l.setIsC(0.toByte())
        c.l.setEnv(e)
        c.l.setNupvalues(LuaLimits.cast_byte(nelems))
        c.l.upvals = arrayOfNulls(nelems)
        for (i in 0 until nelems) {
            c.l.upvals!![i] = UpVal()
        }
        while (nelems-- > 0) {
            c.l.upvals!![nelems] = null
        }
        return c
    }

    fun luaF_newupval(L: lua_State?): UpVal? {
        val uv = LuaMem.luaM_new_UpVal(L, ClassType(ClassType.Companion.TYPE_UPVAL))
        LuaGC.luaC_link(L, LuaState.obj2gco(uv), LuaObject.LUA_TUPVAL.toByte())
        uv!!.v = uv.u.value
        LuaObject.setnilvalue(uv.v)
        return uv
    }

    fun luaF_findupval(L: lua_State?, level: TValue): UpVal? { //StkId
        val g = LuaState.G(L)
        var pp: GCObjectRef = OpenValRef(L!!)
        var p: UpVal? = null
        val uv: UpVal?
        while (pp.get() != null && TValue.Companion.greaterEqual(
                LuaState.ngcotouv(pp.get()).also { p = it }!!.v!!,
                level
            )
        ) {
            LuaLimits.lua_assert(p!!.v !== p!!.u.value)
            if (p!!.v!! === level) { // found a corresponding upvalue?
                if (LuaGC.isdead(g, LuaState.obj2gco(p!!))) { // is it dead?
                    LuaGC.changewhite(LuaState.obj2gco(p!!)) // ressurect it
                }
                return p
            }
            pp = NextRef(p!!)
        }
        uv = LuaMem.luaM_new_UpVal(L, ClassType(ClassType.Companion.TYPE_UPVAL)) // not found: create a new one
        uv.tt = LuaObject.LUA_TUPVAL.toByte()
        uv.marked = LuaGC.luaC_white(g)
        uv.v = level // current value lives in the stack
        uv.next = pp.get() // chain it in the proper position
        pp.set(LuaState.obj2gco(uv))
        uv.u.l.prev = g!!.uvhead // double link it in `uvhead' list
        uv.u.l.next = g!!.uvhead.u.l.next
        uv.u.l.next!!.u.l.prev = uv
        g.uvhead.u.l.next = uv
        LuaLimits.lua_assert(uv.u.l.next!!.u.l.prev === uv && uv.u.l.prev!!.u.l.next === uv)
        return uv
    }

    private fun unlinkupval(uv: UpVal) {
        LuaLimits.lua_assert(uv.u.l.next!!.u.l.prev === uv && uv.u.l.prev!!.u.l.next === uv)
        uv.u.l.next!!.u.l.prev = uv.u.l.prev // remove from `uvhead' list
        uv.u.l.prev!!.u.l.next = uv.u.l.next
    }

    fun luaF_freeupval(L: lua_State?, uv: UpVal) {
        if (uv.v !== uv.u.value) { // is it open?
            unlinkupval(uv) // remove from open list
        }
        LuaMem.luaM_free_UpVal(L, uv, ClassType(ClassType.Companion.TYPE_UPVAL)) // free upvalue
    }

    fun luaF_close(L: lua_State, level: TValue?) { //StkId
        var uv: UpVal? = null
        val g = LuaState.G(L)
        while (L.openupval != null && TValue.Companion.greaterEqual(LuaState.ngcotouv(L.openupval).also {
                uv = it
            }!!.v!!, level!!)) {
            val o = LuaState.obj2gco(uv!!)
            LuaLimits.lua_assert(!LuaGC.isblack(o) && uv!!.v !== uv!!.u.value)
            L.openupval = uv!!.next // remove from `open' list
            if (LuaGC.isdead(g, o)) {
                luaF_freeupval(L, uv!!) // free upvalue
            } else {
                unlinkupval(uv!!)
                LuaObject.setobj(L, uv!!.u.value, uv!!.v!!)
                uv!!.v = uv!!.u.value // now current value lives here
                LuaGC.luaC_linkupval(L, uv!!) // link upvalue into `gcroot' list
            }
        }
    }

    fun luaF_newproto(L: lua_State?): Proto? {
        val f = LuaMem.luaM_new_Proto(L, ClassType(ClassType.Companion.TYPE_PROTO))
        LuaGC.luaC_link(L, LuaState.obj2gco(f), LuaObject.LUA_TPROTO.toByte())
        f!!.k = null
        f.sizek = 0
        f.p = null
        f.sizep = 0
        f.code = null
        f.sizecode = 0
        f.sizelineinfo = 0
        f.sizeupvalues = 0
        f.nups = 0
        f.upvalues = null
        f.numparams = 0
        f.is_vararg = 0
        f.maxstacksize = 0
        f.lineinfo = null
        f.sizelocvars = 0
        f.locvars = null
        f.linedefined = 0
        f.lastlinedefined = 0
        f.source = null
        return f
    }

    fun luaF_freeproto(L: lua_State?, f: Proto) { //UInt32
//Instruction
        LuaMem.luaM_freearray_long(L, f.code, ClassType(ClassType.Companion.TYPE_LONG))
        LuaMem.luaM_freearray_Proto(L, f.p, ClassType(ClassType.Companion.TYPE_PROTO))
        LuaMem.luaM_freearray_TValue(L, f.k, ClassType(ClassType.Companion.TYPE_TVALUE))
        //Int32
        LuaMem.luaM_freearray_int(L, f.lineinfo, ClassType(ClassType.Companion.TYPE_INT32))
        LuaMem.luaM_freearray_LocVar(L, f.locvars, ClassType(ClassType.Companion.TYPE_LOCVAR))
        LuaMem.luaM_freearray_TString(L, f.upvalues, ClassType(ClassType.Companion.TYPE_TSTRING))
        LuaMem.luaM_free_Proto(L, f, ClassType(ClassType.Companion.TYPE_PROTO))
    }

    // we have a gc, so nothing to do
    fun luaF_freeclosure(L: lua_State?, c: Closure) {
        val size =
            if (c.c.getIsC().toInt() != 0) sizeCclosure(c.c.getNupvalues().toInt()) else sizeLclosure(
                c.l.getNupvalues().toInt()
            )
        //luaM_freemem(L, c, size);
        LuaMem.SubtractTotalBytes(L, size)
    }

    //
//		 ** Look for n-th local variable at line `line' in function `func'.
//		 ** Returns null if not found.
//
    fun luaF_getlocalname(f: Proto, local_number: Int, pc: Int): CharPtr? {
        var local_number = local_number
        var i: Int
        i = 0
        while (i < f.sizelocvars && f.locvars!![i]!!.startpc <= pc) {
            if (pc < f.locvars!![i]!!.endpc) { // is variable active?
                local_number--
                if (local_number == 0) {
                    return LuaObject.getstr(f.locvars!![i]!!.varname)
                }
            }
            i++
        }
        return null // not found
    }
}