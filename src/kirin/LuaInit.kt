package kirin

import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.LuaAuxLib.luaL_Reg
import kirin.LuaState.lua_State

//
// ** $Id: linit.c,v 1.14.1.1 2007/12/27 13:02:25 roberto Exp $
// ** Initialization of libraries for lua.c
// ** See Copyright Notice in lua.h
//
object LuaInit {
    private val lualibs = arrayOf(
        luaL_Reg(CharPtr.Companion.toCharPtr(""), LuaInit_delegate("LuaBaseLib.luaopen_base")),
        luaL_Reg(CharPtr.Companion.toCharPtr(LuaLib.LUA_LOADLIBNAME), LuaInit_delegate("LuaLoadLib.luaopen_package")),
        luaL_Reg(CharPtr.Companion.toCharPtr(LuaLib.LUA_TABLIBNAME), LuaInit_delegate("LuaTableLib.luaopen_table")),
        luaL_Reg(CharPtr.Companion.toCharPtr(LuaLib.LUA_IOLIBNAME), LuaInit_delegate("LuaIOLib.luaopen_io")),
        luaL_Reg(CharPtr.Companion.toCharPtr(LuaLib.LUA_OSLIBNAME), LuaInit_delegate("LuaOSLib.luaopen_os")),
        luaL_Reg(CharPtr.Companion.toCharPtr(LuaLib.LUA_STRLIBNAME), LuaInit_delegate("LuaStrLib.luaopen_string")),
        luaL_Reg(CharPtr.Companion.toCharPtr(LuaLib.LUA_MATHLIBNAME), LuaInit_delegate("LuaMathLib.luaopen_math")),
        luaL_Reg(CharPtr.Companion.toCharPtr(LuaLib.LUA_DBLIBNAME), LuaInit_delegate("LuaDebugLib.luaopen_debug")),
        luaL_Reg(null, null)
    )

    fun luaL_openlibs(L: lua_State) {
        for (i in 0 until lualibs.size - 1) {
            val lib = lualibs[i]
            Lua.lua_pushcfunction(L, lib.func)
            LuaAPI.lua_pushstring(L, lib.name)
            LuaAPI.lua_call(L, 1, 0)
        }
    }

    class LuaInit_delegate(private val name: String) : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return if ("LuaBaseLib.luaopen_base" == name) {
                LuaBaseLib.luaopen_base(L)
            } else if ("LuaLoadLib.luaopen_package" == name) {
                LuaLoadLib.luaopen_package(L)
            } else if ("LuaTableLib.luaopen_table" == name) {
                LuaTableLib.luaopen_table(L)
            } else if ("LuaIOLib.luaopen_io" == name) {
                LuaIOLib.luaopen_io(L)
            } else if ("LuaOSLib.luaopen_os" == name) {
                LuaOSLib.luaopen_os(L)
            } else if ("LuaStrLib.luaopen_string" == name) {
                LuaStrLib.luaopen_string(L)
            } else if ("LuaMathLib.luaopen_math" == name) {
                LuaMathLib.luaopen_math(L)
            } else if ("LuaDebugLib.luaopen_debug" == name) {
                LuaDebugLib.luaopen_debug(L)
            } else {
                0
            }
        }

    }
}