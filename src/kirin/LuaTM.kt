package kirin

import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.LuaObject.TValue
import kirin.LuaObject.Table
import kirin.LuaState.global_State
import kirin.LuaObject.TString
import java.lang.RuntimeException
import kotlin.experimental.and
import kotlin.experimental.or

//
// ** $Id: ltm.c,v 2.8.1.1 2007/12/27 13:02:25 roberto Exp $
// ** Tag methods
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
object LuaTM {
    fun convertTMStoInt(tms: TMS?): Int {
        when (tms) {
            TMS.TM_INDEX -> return 0
            TMS.TM_NEWINDEX -> return 1
            TMS.TM_GC -> return 2
            TMS.TM_MODE -> return 3
            TMS.TM_EQ -> return 4
            TMS.TM_ADD -> return 5
            TMS.TM_SUB -> return 6
            TMS.TM_MUL -> return 7
            TMS.TM_DIV -> return 8
            TMS.TM_MOD -> return 9
            TMS.TM_POW -> return 10
            TMS.TM_UNM -> return 11
            TMS.TM_LEN -> return 12
            TMS.TM_LT -> return 13
            TMS.TM_LE -> return 14
            TMS.TM_CONCAT -> return 15
            TMS.TM_CALL -> return 16
            TMS.TM_N -> return 17
        }
        throw RuntimeException("convertTMStoInt error")
    }

    fun gfasttm(g: global_State?, et: Table?, e: TMS): TValue? {
        return if (et == null) null else if ((et.flags and ((1 shl e.getValue()).toByte())) != 0.toByte()) null else luaT_gettm(
            et,
            e,
            g!!.tmname.get(e.getValue())
        )
    }

    fun fasttm(l: lua_State?, et: Table?, e: TMS): TValue? {
        return gfasttm(LuaState.G(l), et, e)
    }

    val luaT_typenames: Array<CharPtr> = arrayOf<CharPtr>(
        CharPtr.Companion.toCharPtr("nil"),
        CharPtr.Companion.toCharPtr("boolean"),
        CharPtr.Companion.toCharPtr("userdata"),
        CharPtr.Companion.toCharPtr("number"),
        CharPtr.Companion.toCharPtr("string"),
        CharPtr.Companion.toCharPtr("table"),
        CharPtr.Companion.toCharPtr("function"),
        CharPtr.Companion.toCharPtr("userdata"),
        CharPtr.Companion.toCharPtr("thread"),
        CharPtr.Companion.toCharPtr("proto"),
        CharPtr.Companion.toCharPtr("upval")
    )
    private val luaT_eventname: Array<CharPtr> = arrayOf<CharPtr>(
        CharPtr.Companion.toCharPtr("__index"),
        CharPtr.Companion.toCharPtr("__newindex"),
        CharPtr.Companion.toCharPtr("__gc"),
        CharPtr.Companion.toCharPtr("__mode"),
        CharPtr.Companion.toCharPtr("__eq"),
        CharPtr.Companion.toCharPtr("__add"),
        CharPtr.Companion.toCharPtr("__sub"),
        CharPtr.Companion.toCharPtr("__mul"),
        CharPtr.Companion.toCharPtr("__div"),
        CharPtr.Companion.toCharPtr("__mod"),
        CharPtr.Companion.toCharPtr("__pow"),
        CharPtr.Companion.toCharPtr("__unm"),
        CharPtr.Companion.toCharPtr("__len"),
        CharPtr.Companion.toCharPtr("__lt"),
        CharPtr.Companion.toCharPtr("__le"),
        CharPtr.Companion.toCharPtr("__concat"),
        CharPtr.Companion.toCharPtr("__call")
    )

    fun luaT_init(L: lua_State?) {
        var i: Int
        i = 0
        while (i < TMS.TM_N.getValue()) {
            LuaState.G(L)!!.tmname[i] = LuaString.luaS_new(L, luaT_eventname[i])
            LuaString.luaS_fix(LuaState.G(L)!!.tmname.get(i)) // never collect these names
            i++
        }
    }

    //
//		 ** function to be used with macro "fasttm": optimized for absence of
//		 ** tag methods
//
    fun luaT_gettm(events: Table, event_: TMS, ename: TString?): TValue? { //const
        val tm: TValue = LuaTable.luaH_getstr(events, ename)
        LuaLimits.lua_assert(convertTMStoInt(event_) <= convertTMStoInt(TMS.TM_EQ))
        return if (LuaObject.ttisnil(tm)) { // no tag method?
            events.flags = (events.flags or
                    ((1 shl event_.getValue()).toByte())
                    ).toByte() // cache this fact
            null
        } else {
            tm
        }
    }

    fun luaT_gettmbyobj(L: lua_State?, o: TValue?, event_: TMS): TValue {
        val mt: Table
        mt = when (LuaObject.ttype(o)) {
            Lua.LUA_TTABLE -> {
                LuaObject.hvalue(o!!)!!.metatable!!
            }
            Lua.LUA_TUSERDATA -> {
                LuaObject.uvalue(o!!).metatable!!
            }
            else -> {
                LuaState.G(L)!!.mt.get(LuaObject.ttype(o))!!
            }
        }
        return if (mt != null) LuaTable.luaH_getstr(
            mt,
            LuaState.G(L)!!.tmname.get(event_.getValue())
        ) else LuaObject.luaO_nilobject
    }

    /*
	 * WARNING: if you change the order of this enumeration,
	 * grep "ORDER TM"
	 */
    enum class TMS {
        TM_INDEX, TM_NEWINDEX, TM_GC, TM_MODE, TM_EQ,  /* last tag method with `fast' access */
        TM_ADD, TM_SUB, TM_MUL, TM_DIV, TM_MOD, TM_POW, TM_UNM, TM_LEN, TM_LT, TM_LE, TM_CONCAT, TM_CALL, TM_N;

        /* number of elements in the enum */
        fun getValue(): Int {
            return ordinal
        }

        companion object {
            fun forValue(value: Int): TMS {
                return LuaTM.TMS.values()[value]
            }
        }
    }
}