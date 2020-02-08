package kirin

import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import java.lang.Exception
import kirin.LuaAuxLib.luaL_Reg

//
// ** $Id: loslib.c,v 1.19.1.3 2008/01/18 16:38:18 roberto Exp $
// ** Standard Operating System library
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
//using StkId = TValue;
//using lua_Integer = System.Int32;
//using lua_Number = System.Double;
object LuaOSLib {
    private fun os_pushresult(L: lua_State, i: Int, filename: CharPtr): Int {
        val en: Int = CLib.errno() // calls to Lua API may change this value
        return if (i != 0) {
            LuaAPI.lua_pushboolean(L, 1)
            1
        } else {
            LuaAPI.lua_pushnil(L)
            LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("%s: %s"), filename, CLib.strerror(en))
            LuaAPI.lua_pushinteger(L, en)
            3
        }
    }

    private fun os_execute(L: lua_State): Int {
        val strCmdLine: CharPtr = CharPtr.Companion.toCharPtr("" + LuaAuxLib.luaL_optstring(L, 1, null))
        LuaAPI.lua_pushinteger(L, ClassType.Companion.processExec(strCmdLine.toString()))
        return 1
    }

    private fun os_remove(L: lua_State): Int {
        val filename: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        var result = 1
        try {
            StreamProxy.Companion.Delete(filename.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            result = 0
        }
        return os_pushresult(L, result, filename)
    }

    private fun os_rename(L: lua_State): Int {
        val fromname: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        val toname: CharPtr = LuaAuxLib.luaL_checkstring(L, 2)
        val result: Int
        result = try {
            StreamProxy.Companion.Move(fromname.toString(), toname.toString())
            0
        } catch (e: Exception) {
            e.printStackTrace()
            1 // todo: this should be a proper error code
        }
        return os_pushresult(L, result, fromname)
    }

    private fun os_tmpname(L: lua_State): Int {
        LuaAPI.lua_pushstring(L, CharPtr.Companion.toCharPtr(StreamProxy.Companion.GetTempFileName()!!))
        return 1
    }

    private fun os_getenv(L: lua_State): Int {
        LuaAPI.lua_pushstring(L, CLib.getenv(LuaAuxLib.luaL_checkstring(L, 1))) // if null push nil
        return 1
    }

    private fun os_clock(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, DateTimeProxy.Companion.getClock())
        return 1
    }

    //
//		 ** {======================================================
//		 ** Time/Date operations
//		 ** { year=%Y, month=%m, day=%d, hour=%H, min=%M, sec=%S,
//		 **   wday=%w+1, yday=%j, isdst=? }
//		 ** =======================================================
//
    private fun setfield(L: lua_State, key: CharPtr, value: Int) {
        LuaAPI.lua_pushinteger(L, value)
        LuaAPI.lua_setfield(L, -2, key)
    }

    private fun setboolfield(L: lua_State, key: CharPtr, value: Int) {
        if (value < 0) { // undefined?
            return  // does not set field
        }
        LuaAPI.lua_pushboolean(L, value)
        LuaAPI.lua_setfield(L, -2, key)
    }

    private fun getboolfield(L: lua_State, key: CharPtr): Int {
        val res: Int
        LuaAPI.lua_getfield(L, -1, key)
        res = if (Lua.lua_isnil(L, -1)) -1 else LuaAPI.lua_toboolean(L, -1)
        Lua.lua_pop(L, 1)
        return res
    }

    private fun getfield(L: lua_State, key: CharPtr, d: Int): Int {
        val res: Int
        LuaAPI.lua_getfield(L, -1, key)
        res = if (LuaAPI.lua_isnumber(L, -1) != 0) {
            LuaAPI.lua_tointeger(L, -1)
        } else {
            if (d < 0) {
                return LuaAuxLib.luaL_error(
                    L,
                    CharPtr.Companion.toCharPtr("field " + LuaConf.getLUA_QS() + " missing in date table"),
                    key
                )
            }
            d
        }
        Lua.lua_pop(L, 1)
        return res
    }

    private fun os_date(L: lua_State): Int {
        val s: CharPtr? = LuaAuxLib.luaL_optstring(L, 1, CharPtr.Companion.toCharPtr("%c"))
        val stm = DateTimeProxy()
        if (s!!.get(0) == '!') { // UTC?
            stm.setUTCNow()
            s!!.inc() // skip `!'
        } else {
            stm.setNow()
        }
        if (CLib.strcmp(s, CharPtr.Companion.toCharPtr("*t")) == 0) {
            LuaAPI.lua_createtable(L, 0, 9) // 9 = number of fields
            setfield(L, CharPtr.Companion.toCharPtr("sec"), stm.getSecond())
            setfield(L, CharPtr.Companion.toCharPtr("min"), stm.getMinute())
            setfield(L, CharPtr.Companion.toCharPtr("hour"), stm.getHour())
            setfield(L, CharPtr.Companion.toCharPtr("day"), stm.getDay())
            setfield(L, CharPtr.Companion.toCharPtr("month"), stm.getMonth())
            setfield(L, CharPtr.Companion.toCharPtr("year"), stm.getYear())
            setfield(L, CharPtr.Companion.toCharPtr("wday"), stm.getDayOfWeek())
            setfield(L, CharPtr.Companion.toCharPtr("yday"), stm.getDayOfYear())
            setboolfield(L, CharPtr.Companion.toCharPtr("isdst"), if (stm.IsDaylightSavingTime()) 1 else 0)
        } else {
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr("strftime not implemented yet")
            ) // todo: implement this - mjf
            ///#if false
//				CharPtr cc = new char[3];
//				luaL_Buffer b;
//				cc[0] = '%';
//				cc[2] = '\0';
//				luaL_buffinit(L, b);
//				for (; s[0] != 0; s.inc())
//				{
//					if (s[0] != '%' || s[1] == '\0')  /* no conversion specifier? */
//					{
//						luaL_addchar(b, s[0]);
//					}
//					else
//					{
//						uint reslen;
//						CharPtr buff = new char[200];  /* should be big enough for any conversion result */
//						s.inc();
//						cc[1] = s[0];
//						reslen = strftime(buff, buff.Length, cc, stm);
//						luaL_addlstring(b, buff, reslen);
//					}
//				}
//				luaL_pushresult(b);
///#endif // #if 0
        }
        return 1
    }

    private fun os_time(L: lua_State): Int {
        var t = DateTimeProxy()
        if (Lua.lua_isnoneornil(L, 1.0)) { // called without args?
            t.setNow() // get current time
        } else {
            LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
            LuaAPI.lua_settop(L, 1) // make sure table is at the top
            val sec = getfield(L, CharPtr.Companion.toCharPtr("sec"), 0)
            val min = getfield(L, CharPtr.Companion.toCharPtr("min"), 0)
            val hour = getfield(L, CharPtr.Companion.toCharPtr("hour"), 12)
            val day = getfield(L, CharPtr.Companion.toCharPtr("day"), -1)
            val month = getfield(L, CharPtr.Companion.toCharPtr("month"), -1) - 1
            val year = getfield(L, CharPtr.Companion.toCharPtr("year"), -1) - 1900
            val isdst =
                getboolfield(L, CharPtr.Companion.toCharPtr("isdst")) // todo: implement this - mjf
            t = DateTimeProxy(year, month, day, hour, min, sec)
        }
        LuaAPI.lua_pushnumber(L, t.getTicks())
        return 1
    }

    private fun os_difftime(L: lua_State): Int {
        val ticks = LuaAuxLib.luaL_checknumber(L, 1) as Long - LuaAuxLib.luaL_optnumber(L, 2, 0.0) as Long
        LuaAPI.lua_pushnumber(L, ticks / 10000000.toDouble()) //FIXME: ticks / TimeSpan.TicksPerSecond
        return 1
    }

    // }======================================================
// locale not supported yet
    private fun os_setlocale(L: lua_State): Int { //
//		  static string[] cat = {LC_ALL, LC_COLLATE, LC_CTYPE, LC_MONETARY,
//							  LC_NUMERIC, LC_TIME};
//		  static string[] catnames[] = {"all", "collate", "ctype", "monetary",
//			 "numeric", "time", null};
//		  CharPtr l = luaL_optstring(L, 1, null);
//		  int op = luaL_checkoption(L, 2, "all", catnames);
//		  lua_pushstring(L, setlocale(cat[op], l));
//
        val l: CharPtr? = LuaAuxLib.luaL_optstring(L, 1, null)
        LuaAPI.lua_pushstring(L, CharPtr.Companion.toCharPtr("C"))
        return if (l.toString() == "C") 1 else 0
    }

    private fun os_exit(L: lua_State): Int {
        System.exit(CLib.EXIT_SUCCESS)
        return 0
    }

    private val syslib: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("clock"), LuaOSLib_delegate("os_clock")),
        luaL_Reg(CharPtr.Companion.toCharPtr("date"), LuaOSLib_delegate("os_date")),
        luaL_Reg(CharPtr.Companion.toCharPtr("difftime"), LuaOSLib_delegate("os_difftime")),
        luaL_Reg(CharPtr.Companion.toCharPtr("execute"), LuaOSLib_delegate("os_execute")),
        luaL_Reg(CharPtr.Companion.toCharPtr("exit"), LuaOSLib_delegate("os_exit")),
        luaL_Reg(CharPtr.Companion.toCharPtr("getenv"), LuaOSLib_delegate("os_getenv")),
        luaL_Reg(CharPtr.Companion.toCharPtr("remove"), LuaOSLib_delegate("os_remove")),
        luaL_Reg(CharPtr.Companion.toCharPtr("rename"), LuaOSLib_delegate("os_rename")),
        luaL_Reg(CharPtr.Companion.toCharPtr("setlocale"), LuaOSLib_delegate("os_setlocale")),
        luaL_Reg(CharPtr.Companion.toCharPtr("time"), LuaOSLib_delegate("os_time")),
        luaL_Reg(CharPtr.Companion.toCharPtr("tmpname"), LuaOSLib_delegate("os_tmpname")),
        luaL_Reg(null, null)
    )

    // }======================================================
    fun luaopen_os(L: lua_State?): Int {
        LuaAuxLib.luaL_register(L, CharPtr.Companion.toCharPtr(LuaLib.LUA_OSLIBNAME), syslib)
        return 1
    }

    class LuaOSLib_delegate(private val name: String) : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return if ("os_clock" == name) {
                os_clock(L)
            } else if ("os_date" == name) {
                os_date(L)
            } else if ("os_difftime" == name) {
                os_difftime(L)
            } else if ("os_execute" == name) {
                os_execute(L)
            } else if ("os_exit" == name) {
                os_exit(L)
            } else if ("os_getenv" == name) {
                os_getenv(L)
            } else if ("os_remove" == name) {
                os_remove(L)
            } else if ("os_rename" == name) {
                os_rename(L)
            } else if ("os_setlocale" == name) {
                os_setlocale(L)
            } else if ("os_time" == name) {
                os_time(L)
            } else if ("os_tmpname" == name) {
                os_tmpname(L)
            } else {
                0
            }
        }

    }
}