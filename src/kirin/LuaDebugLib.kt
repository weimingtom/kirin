package kirin
import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.Lua.lua_Debug
import kirin.Lua.lua_Hook
import kirin.LuaAuxLib.luaL_Reg

//
// ** $Id: ldblib.c,v 1.104.1.3 2008/01/21 13:11:21 roberto Exp $
// ** Interface from Lua to its debug API
// ** See Copyright Notice in lua.h
//
object LuaDebugLib {
    private fun db_getregistry(L: lua_State): Int {
        LuaAPI.lua_pushvalue(L, Lua.LUA_REGISTRYINDEX)
        return 1
    }

    private fun db_getmetatable(L: lua_State): Int {
        LuaAuxLib.luaL_checkany(L, 1)
        if (LuaAPI.lua_getmetatable(L, 1) == 0) {
            LuaAPI.lua_pushnil(L) // no metatable
        }
        return 1
    }

    private fun db_setmetatable(L: lua_State): Int {
        val t: Int = LuaAPI.lua_type(L, 2)
        LuaAuxLib.luaL_argcheck(L, t == Lua.LUA_TNIL || t == Lua.LUA_TTABLE, 2, "nil or table expected")
        LuaAPI.lua_settop(L, 2)
        LuaAPI.lua_pushboolean(L, LuaAPI.lua_setmetatable(L, 1))
        return 1
    }

    private fun db_getfenv(L: lua_State): Int {
        LuaAPI.lua_getfenv(L, 1)
        return 1
    }

    private fun db_setfenv(L: lua_State): Int {
        LuaAuxLib.luaL_checktype(L, 2, Lua.LUA_TTABLE)
        LuaAPI.lua_settop(L, 2)
        if (LuaAPI.lua_setfenv(L, 1) == 0) {
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("setfenv").toString() + " cannot change environment of given object")
            )
        }
        return 1
    }

    private fun settabss(L: lua_State, i: CharPtr, v: CharPtr) {
        LuaAPI.lua_pushstring(L, v)
        LuaAPI.lua_setfield(L, -2, i)
    }

    private fun settabsi(L: lua_State, i: CharPtr, v: Int) {
        LuaAPI.lua_pushinteger(L, v)
        LuaAPI.lua_setfield(L, -2, i)
    }

    private fun getthread(L: lua_State, arg: IntArray): lua_State { //out
        return if (Lua.lua_isthread(L, 1)) {
            arg[0] = 1
            LuaAPI.lua_tothread(L, 1)!!
        } else {
            arg[0] = 0
            L
        }
    }

    private fun treatstackoption(L: lua_State, L1: lua_State, fname: CharPtr) {
        if (L === L1) {
            LuaAPI.lua_pushvalue(L, -2)
            LuaAPI.lua_remove(L, -3)
        } else {
            LuaAPI.lua_xmove(L1, L, 1)
        }
        LuaAPI.lua_setfield(L, -2, fname)
    }

    private fun db_getinfo(L: lua_State): Int {
        val ar = lua_Debug()
        val arg = IntArray(1)
        val L1: lua_State = getthread(L, arg) //out
        var options: CharPtr? = LuaAuxLib.luaL_optstring(L, arg[0] + 2, CharPtr.Companion.toCharPtr("flnSu"))
        if (LuaAPI.lua_isnumber(L, arg[0] + 1) != 0) {
            if (LuaDebug.lua_getstack(L1, LuaAPI.lua_tointeger(L, arg[0] + 1) as Int, ar) == 0) {
                LuaAPI.lua_pushnil(L) // level out of range
                return 1
            }
        } else if (Lua.lua_isfunction(L, arg[0] + 1)) {
            LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr(">%s"), options)
            options = Lua.lua_tostring(L, -1)
            LuaAPI.lua_pushvalue(L, arg[0] + 1)
            LuaAPI.lua_xmove(L, L1, 1)
        } else {
            return LuaAuxLib.luaL_argerror(L, arg[0] + 1, CharPtr.Companion.toCharPtr("function or level expected"))
        }
        if (LuaDebug.lua_getinfo(L1, options, ar) == 0) {
            return LuaAuxLib.luaL_argerror(L, arg[0] + 2, CharPtr.Companion.toCharPtr("invalid option"))
        }
        LuaAPI.lua_createtable(L, 0, 2)
        if (CharPtr.Companion.isNotEqual(CLib.strchr(options, 'S'), null)) {
            settabss(L, CharPtr.Companion.toCharPtr("source"), ar.source!!)
            settabss(L, CharPtr.Companion.toCharPtr("short_src"), ar.short_src)
            settabsi(L, CharPtr.Companion.toCharPtr("linedefined"), ar.linedefined)
            settabsi(L, CharPtr.Companion.toCharPtr("lastlinedefined"), ar.lastlinedefined)
            settabss(L, CharPtr.Companion.toCharPtr("what"), ar.what!!)
        }
        if (CharPtr.Companion.isNotEqual(CLib.strchr(options, 'l'), null)) {
            settabsi(L, CharPtr.Companion.toCharPtr("currentline"), ar.currentline)
        }
        if (CharPtr.Companion.isNotEqual(CLib.strchr(options, 'u'), null)) {
            settabsi(L, CharPtr.Companion.toCharPtr("nups"), ar.nups)
        }
        if (CharPtr.Companion.isNotEqual(CLib.strchr(options, 'n'), null)) {
            settabss(L, CharPtr.Companion.toCharPtr("name"), ar.name!!)
            settabss(L, CharPtr.Companion.toCharPtr("namewhat"), ar.namewhat!!)
        }
        if (CharPtr.Companion.isNotEqual(CLib.strchr(options, 'L'), null)) {
            treatstackoption(L, L1, CharPtr.Companion.toCharPtr("activelines"))
        }
        if (CharPtr.Companion.isNotEqual(CLib.strchr(options, 'f'), null)) {
            treatstackoption(L, L1, CharPtr.Companion.toCharPtr("func"))
        }
        return 1 // return table
    }

    private fun db_getlocal(L: lua_State): Int {
        val arg = IntArray(1)
        val L1: lua_State = getthread(L, arg) //out
        val ar = lua_Debug()
        val name: CharPtr?
        if (LuaDebug.lua_getstack(L1, LuaAuxLib.luaL_checkint(L, arg[0] + 1), ar) == 0) { // out of range?
            return LuaAuxLib.luaL_argerror(L, arg[0] + 1, CharPtr.Companion.toCharPtr("level out of range"))
        }
        name = LuaDebug.lua_getlocal(L1, ar, LuaAuxLib.luaL_checkint(L, arg[0] + 2))
        return if (CharPtr.Companion.isNotEqual(name, null)) {
            LuaAPI.lua_xmove(L1, L, 1)
            LuaAPI.lua_pushstring(L, name)
            LuaAPI.lua_pushvalue(L, -2)
            2
        } else {
            LuaAPI.lua_pushnil(L)
            1
        }
    }

    private fun db_setlocal(L: lua_State): Int {
        val arg = IntArray(1)
        val L1: lua_State = getthread(L, arg) //out
        val ar = lua_Debug()
        if (LuaDebug.lua_getstack(L1, LuaAuxLib.luaL_checkint(L, arg[0] + 1), ar) == 0) { // out of range?
            return LuaAuxLib.luaL_argerror(L, arg[0] + 1, CharPtr.Companion.toCharPtr("level out of range"))
        }
        LuaAuxLib.luaL_checkany(L, arg[0] + 3)
        LuaAPI.lua_settop(L, arg[0] + 3)
        LuaAPI.lua_xmove(L, L1, 1)
        LuaAPI.lua_pushstring(L, LuaDebug.lua_setlocal(L1, ar, LuaAuxLib.luaL_checkint(L, arg[0] + 2)))
        return 1
    }

    private fun auxupvalue(L: lua_State, get: Int): Int {
        var name: CharPtr
        val n: Int = LuaAuxLib.luaL_checkint(L, 2)
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TFUNCTION)
        if (LuaAPI.lua_iscfunction(L, 1)) { // cannot touch C upvalues from Lua
            return 0
        }
        run { name = if (get != 0) LuaAPI.lua_getupvalue(L, 1, n)!! else LuaAPI.lua_setupvalue(L, 1, n)!! }
        if (CharPtr.Companion.isEqual(name, null)) {
            return 0
        }
        LuaAPI.lua_pushstring(L, name)
        LuaAPI.lua_insert(L, -(get + 1))
        return get + 1
    }

    private fun db_getupvalue(L: lua_State): Int {
        return auxupvalue(L, 1)
    }

    private fun db_setupvalue(L: lua_State): Int {
        LuaAuxLib.luaL_checkany(L, 3)
        return auxupvalue(L, 0)
    }

    private const val KEY_HOOK = "h"
    private val hooknames =
        arrayOf("call", "return", "line", "count", "tail return")

    private fun hookf(L: lua_State, ar: lua_Debug) {
        LuaAPI.lua_pushlightuserdata(L, KEY_HOOK)
        LuaAPI.lua_rawget(L, Lua.LUA_REGISTRYINDEX)
        LuaAPI.lua_pushlightuserdata(L, L)
        LuaAPI.lua_rawget(L, -2)
        if (Lua.lua_isfunction(L, -1)) {
            LuaAPI.lua_pushstring(L, CharPtr.Companion.toCharPtr(hooknames[ar.event_]))
            if (ar.currentline >= 0) {
                LuaAPI.lua_pushinteger(L, ar.currentline)
            } else {
                LuaAPI.lua_pushnil(L)
            }
            LuaLimits.lua_assert(LuaDebug.lua_getinfo(L, CharPtr.Companion.toCharPtr("lS"), ar))
            LuaAPI.lua_call(L, 2, 0)
        }
    }

    private fun makemask(smask: CharPtr, count: Int): Int {
        var mask = 0
        if (CharPtr.Companion.isNotEqual(CLib.strchr(smask, 'c'), null)) {
            mask = mask or Lua.LUA_MASKCALL
        }
        if (CharPtr.Companion.isNotEqual(CLib.strchr(smask, 'r'), null)) {
            mask = mask or Lua.LUA_MASKRET
        }
        if (CharPtr.Companion.isNotEqual(CLib.strchr(smask, 'l'), null)) {
            mask = mask or Lua.LUA_MASKLINE
        }
        if (count > 0) {
            mask = mask or Lua.LUA_MASKCOUNT
        }
        return mask
    }

    private fun unmakemask(mask: Int, smask: CharPtr): CharPtr {
        var i = 0
        if (mask and Lua.LUA_MASKCALL != 0) {
            smask.set(i++, 'c')
        }
        if (mask and Lua.LUA_MASKRET != 0) {
            smask.set(i++, 'r')
        }
        if (mask and Lua.LUA_MASKLINE != 0) {
            smask.set(i++, 'l')
        }
        smask.set(i, '\u0000')
        return smask
    }

    private fun gethooktable(L: lua_State) {
        LuaAPI.lua_pushlightuserdata(L, KEY_HOOK)
        LuaAPI.lua_rawget(L, Lua.LUA_REGISTRYINDEX)
        if (!Lua.lua_istable(L, -1)) {
            Lua.lua_pop(L, 1)
            LuaAPI.lua_createtable(L, 0, 1)
            LuaAPI.lua_pushlightuserdata(L, KEY_HOOK)
            LuaAPI.lua_pushvalue(L, -2)
            LuaAPI.lua_rawset(L, Lua.LUA_REGISTRYINDEX)
        }
    }

    private fun db_sethook(L: lua_State): Int {
        val arg = IntArray(1)
        val mask: Int
        val count: Int
        val func: lua_Hook?
        val L1: lua_State = getthread(L, arg) //out
        if (Lua.lua_isnoneornil(L, arg[0] + 1.toDouble())) {
            LuaAPI.lua_settop(L, arg[0] + 1)
            func = null
            mask = 0
            count = 0 // turn off hooks
        } else {
            val smask: CharPtr = LuaAuxLib.luaL_checkstring(L, arg[0] + 2)
            LuaAuxLib.luaL_checktype(L, arg[0] + 1, Lua.LUA_TFUNCTION)
            count = LuaAuxLib.luaL_optint(L, arg[0] + 3, 0)
            func = hookf_delegate()
            mask = makemask(smask, count)
        }
        gethooktable(L)
        LuaAPI.lua_pushlightuserdata(L, L1)
        LuaAPI.lua_pushvalue(L, arg[0] + 1)
        LuaAPI.lua_rawset(L, -3) // set new hook
        Lua.lua_pop(L, 1) // remove hook table
        LuaDebug.lua_sethook(L1, func, mask, count) // set hooks
        return 0
    }

    private fun db_gethook(L: lua_State): Int {
        val arg = IntArray(1)
        val L1: lua_State = getthread(L, arg) //out
        val buff: CharPtr = CharPtr.Companion.toCharPtr(CharArray(5))
        val mask: Int = LuaDebug.lua_gethookmask(L1)
        val hook: lua_Hook = LuaDebug.lua_gethook(L1)
        if (hook != null && hook is hookf_delegate) { // external hook?
            Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("external hook"))
        } else {
            gethooktable(L)
            LuaAPI.lua_pushlightuserdata(L, L1)
            LuaAPI.lua_rawget(L, -2) // get hook
            LuaAPI.lua_remove(L, -2) // remove hook table
        }
        LuaAPI.lua_pushstring(L, unmakemask(mask, buff))
        LuaAPI.lua_pushinteger(L, LuaDebug.lua_gethookcount(L1))
        return 3
    }

    private fun db_debug(L: lua_State): Int {
        while (true) {
            val buffer: CharPtr = CharPtr.Companion.toCharPtr(CharArray(250))
            CLib.fputs(CharPtr.Companion.toCharPtr("lua_debug> "), CLib.stderr)
            if (CharPtr.Companion.isEqual(CLib.fgets(buffer, CLib.stdin), null) || CLib.strcmp(
                    buffer,
                    CharPtr.Companion.toCharPtr("cont\n")
                ) == 0
            ) {
                return 0
            }
            if (LuaAuxLib.luaL_loadbuffer(
                    L,
                    buffer,
                    CLib.strlen(buffer),
                    CharPtr.Companion.toCharPtr("=(debug command)")
                ) != 0 || LuaAPI.lua_pcall(L, 0, 0, 0) != 0
            ) { //(uint)
                CLib.fputs(Lua.lua_tostring(L, -1), CLib.stderr)
                CLib.fputs(CharPtr.Companion.toCharPtr("\n"), CLib.stderr)
            }
            LuaAPI.lua_settop(L, 0) // remove eventual returns
        }
    }

    const val LEVELS1 = 12 // size of the first part of the stack
    const val LEVELS2 = 10 // size of the second part of the stack
    private fun db_errorfb(L: lua_State): Int {
        var level: Int
        var firstpart = true // still before eventual `...'
        val arg = IntArray(1)
        val L1: lua_State = getthread(L, arg) //out
        val ar = lua_Debug()
        if (LuaAPI.lua_isnumber(L, arg[0] + 2) != 0) {
            level = LuaAPI.lua_tointeger(L, arg[0] + 2)
            Lua.lua_pop(L, 1)
        } else {
            level = if (L === L1) 1 else 0 // level 0 may be this own function
        }
        if (LuaAPI.lua_gettop(L) == arg[0]) {
            Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr(""))
        } else if (LuaAPI.lua_isstring(L, arg[0] + 1) == 0) {
            return 1 // message is not a string
        } else {
            Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("\n"))
        }
        Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("stack traceback:"))
        while (LuaDebug.lua_getstack(L1, level++, ar) != 0) {
            if (level > LEVELS1 && firstpart) { // no more than `LEVELS2' more levels?
                if (LuaDebug.lua_getstack(L1, level + LEVELS2, ar) == 0) {
                    level-- // keep going
                } else {
                    Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("\n\t...")) // too many levels
                    while (LuaDebug.lua_getstack(L1, level + LEVELS2, ar) != 0) { // find last levels
                        level++
                    }
                }
                firstpart = false
                continue
            }
            Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("\n\t"))
            LuaDebug.lua_getinfo(L1, CharPtr.Companion.toCharPtr("Snl"), ar)
            LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("%s:"), ar.short_src)
            if (ar.currentline > 0) {
                LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("%d:"), ar.currentline)
            }
            if (CharPtr.Companion.isNotEqualChar(ar.namewhat!!, '\u0000')) { // is there a name?
                LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr(" in function " + LuaConf.getLUA_QS()), ar.name)
            } else {
                if (CharPtr.Companion.isEqualChar(ar.what!!, 'm')) { // main?
                    LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr(" in main chunk"))
                } else if (CharPtr.Companion.isEqualChar(ar.what!!, 'C') || CharPtr.Companion.isEqualChar(ar.what!!, 't')) {
                    Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr(" ?")) // C function or tail call
                } else {
                    LuaAPI.lua_pushfstring(
                        L,
                        CharPtr.Companion.toCharPtr(" in function <%s:%d>"),
                        ar.short_src,
                        ar.linedefined
                    )
                }
            }
            LuaAPI.lua_concat(L, LuaAPI.lua_gettop(L) - arg[0])
        }
        LuaAPI.lua_concat(L, LuaAPI.lua_gettop(L) - arg[0])
        return 1
    }

    private val dblib: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("debug"), LuaDebugLib_delegate("db_debug")),
        luaL_Reg(CharPtr.Companion.toCharPtr("getfenv"), LuaDebugLib_delegate("db_getfenv")),
        luaL_Reg(CharPtr.Companion.toCharPtr("gethook"), LuaDebugLib_delegate("db_gethook")),
        luaL_Reg(CharPtr.Companion.toCharPtr("getinfo"), LuaDebugLib_delegate("db_getinfo")),
        luaL_Reg(CharPtr.Companion.toCharPtr("getlocal"), LuaDebugLib_delegate("db_getlocal")),
        luaL_Reg(CharPtr.Companion.toCharPtr("getregistry"), LuaDebugLib_delegate("db_getregistry")),
        luaL_Reg(CharPtr.Companion.toCharPtr("getmetatable"), LuaDebugLib_delegate("db_getmetatable")),
        luaL_Reg(CharPtr.Companion.toCharPtr("getupvalue"), LuaDebugLib_delegate("db_getupvalue")),
        luaL_Reg(CharPtr.Companion.toCharPtr("setfenv"), LuaDebugLib_delegate("db_setfenv")),
        luaL_Reg(CharPtr.Companion.toCharPtr("sethook"), LuaDebugLib_delegate("db_sethook")),
        luaL_Reg(CharPtr.Companion.toCharPtr("setlocal"), LuaDebugLib_delegate("db_setlocal")),
        luaL_Reg(CharPtr.Companion.toCharPtr("setmetatable"), LuaDebugLib_delegate("db_setmetatable")),
        luaL_Reg(CharPtr.Companion.toCharPtr("setupvalue"), LuaDebugLib_delegate("db_setupvalue")),
        luaL_Reg(CharPtr.Companion.toCharPtr("traceback"), LuaDebugLib_delegate("db_errorfb")),
        luaL_Reg(null, null)
    )

    fun luaopen_debug(L: lua_State?): Int {
        LuaAuxLib.luaL_register(L, CharPtr.Companion.toCharPtr(LuaLib.LUA_DBLIBNAME), dblib)
        return 1
    }

    class hookf_delegate : lua_Hook {
        override fun exec(L: lua_State, ar: lua_Debug) {
            hookf(L, ar)
        }
    }

    class LuaDebugLib_delegate(private val name: String) : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return if ("db_debug" == name) {
                db_debug(L)
            } else if ("db_getfenv" == name) {
                db_getfenv(L)
            } else if ("db_gethook" == name) {
                db_gethook(L)
            } else if ("db_getinfo" == name) {
                db_getinfo(L)
            } else if ("db_getlocal" == name) {
                db_getlocal(L)
            } else if ("db_getregistry" == name) {
                db_getregistry(L)
            } else if ("db_getmetatable" == name) {
                db_getmetatable(L)
            } else if ("db_getupvalue" == name) {
                db_getupvalue(L)
            } else if ("db_setfenv" == name) {
                db_setfenv(L)
            } else if ("db_sethook" == name) {
                db_sethook(L)
            } else if ("db_setlocal" == name) {
                db_setlocal(L)
            } else if ("db_setmetatable" == name) {
                db_setmetatable(L)
            } else if ("db_setupvalue" == name) {
                db_setupvalue(L)
            } else if ("db_errorfb" == name) {
                db_errorfb(L)
            } else {
                0
            }
        }

    }
}