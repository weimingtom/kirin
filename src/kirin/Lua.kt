package kirin

import kirin.CLib.CharPtr
import kirin.LuaState.lua_State

//
// ** $Id: lua.h,v 1.218.1.5 2008/08/06 13:30:12 roberto Exp $
// ** Lua - An Extensible Extension Language
// ** Lua.org, PUC-Rio, Brazil (http://www.lua.org)
// ** See Copyright Notice at the end of this file
//
//using lua_Number = Double;
//using lua_Integer = System.Int32;
object Lua {
    const val LUA_VERSION = "Lua 5.1"
    const val LUA_RELEASE = "Lua 5.1.4"
    const val LUA_VERSION_NUM = 501
    const val LUA_COPYRIGHT = "Copyright (C) 1994-2008 Lua.org, PUC-Rio"
    const val LUA_AUTHORS = "R. Ierusalimschy, L. H. de Figueiredo & W. Celes"
    // mark for precompiled code (`<esc>Lua')
    const val LUA_SIGNATURE = "\u001bLua"
    // option for multiple returns in `lua_pcall' and `lua_call'
    const val LUA_MULTRET = -1
    //
//		 ** pseudo-indices
//
    const val LUA_REGISTRYINDEX = -10000
    const val LUA_ENVIRONINDEX = -10001
    const val LUA_GLOBALSINDEX = -10002
    fun lua_upvalueindex(i: Int): Int {
        return LUA_GLOBALSINDEX - i
    }

    // thread status; 0 is OK
    const val LUA_YIELD = 1
    const val LUA_ERRRUN = 2
    const val LUA_ERRSYNTAX = 3
    const val LUA_ERRMEM = 4
    const val LUA_ERRERR = 5
    //
//		 ** basic types
//
    const val LUA_TNONE = -1
    const val LUA_TNIL = 0
    const val LUA_TBOOLEAN = 1
    const val LUA_TLIGHTUSERDATA = 2
    const val LUA_TNUMBER = 3
    const val LUA_TSTRING = 4
    const val LUA_TTABLE = 5
    const val LUA_TFUNCTION = 6
    const val LUA_TUSERDATA = 7
    const val LUA_TTHREAD = 8
    // minimum Lua stack available to a C function
    const val LUA_MINSTACK = 20
    // type of numbers in Lua
//typedef LUA_NUMBER lua_Number;
// type for integer functions
//typedef LUA_INTEGER lua_Integer;
//
//		 ** garbage-collection function and options
//
    const val LUA_GCSTOP = 0
    const val LUA_GCRESTART = 1
    const val LUA_GCCOLLECT = 2
    const val LUA_GCCOUNT = 3
    const val LUA_GCCOUNTB = 4
    const val LUA_GCSTEP = 5
    const val LUA_GCSETPAUSE = 6
    const val LUA_GCSETSTEPMUL = 7
    //
//		 ** ===============================================================
//		 ** some useful macros
//		 ** ===============================================================
//
    fun lua_pop(L: lua_State?, n: Int) {
        LuaAPI.lua_settop(L!!, -n - 1)
    }

    fun lua_newtable(L: lua_State?) {
        LuaAPI.lua_createtable(L!!, 0, 0)
    }

    fun lua_register(L: lua_State?, n: CharPtr?, f: lua_CFunction?) {
        lua_pushcfunction(L, f)
        lua_setglobal(L, n)
    }

    fun lua_pushcfunction(L: lua_State?, f: lua_CFunction?) {
        LuaAPI.lua_pushcclosure(L!!, f, 0)
    }

    fun lua_strlen(L: lua_State?, i: Int): Int { //uint
        return LuaAPI.lua_objlen(L!!, i)
    }

    fun lua_isfunction(L: lua_State?, n: Int): Boolean {
        return LuaAPI.lua_type(L!!, n) == LUA_TFUNCTION
    }

    fun lua_istable(L: lua_State?, n: Int): Boolean {
        return LuaAPI.lua_type(L!!, n) == LUA_TTABLE
    }

    fun lua_islightuserdata(L: lua_State?, n: Int): Boolean {
        return LuaAPI.lua_type(L!!, n) == LUA_TLIGHTUSERDATA
    }

    fun lua_isnil(L: lua_State?, n: Int): Boolean {
        return LuaAPI.lua_type(L!!, n) == LUA_TNIL
    }

    fun lua_isboolean(L: lua_State?, n: Int): Boolean {
        return LuaAPI.lua_type(L!!, n) == LUA_TBOOLEAN
    }

    fun lua_isthread(L: lua_State?, n: Int): Boolean {
        return LuaAPI.lua_type(L!!, n) == LUA_TTHREAD
    }

    fun lua_isnone(L: lua_State?, n: Int): Boolean {
        return LuaAPI.lua_type(L!!, n) == LUA_TNONE
    }

    fun lua_isnoneornil(L: lua_State?, n: Double): Boolean { //lua_Number
        return LuaAPI.lua_type(L!!, n.toInt()) <= 0
    }

    fun lua_pushliteral(
        L: lua_State?,
        s: CharPtr?
    ) { //TODO: Implement use using lua_pushlstring instead of lua_pushstring
//lua_pushlstring(L, "" s, (sizeof(s)/GetUnmanagedSize(typeof(char)))-1)
        LuaAPI.lua_pushstring(L!!, s)
    }

    fun lua_setglobal(L: lua_State?, s: CharPtr?) {
        LuaAPI.lua_setfield(L!!, LUA_GLOBALSINDEX, s)
    }

    fun lua_getglobal(L: lua_State?, s: CharPtr?) {
        LuaAPI.lua_getfield(L!!, LUA_GLOBALSINDEX, s)
    }

    fun lua_tostring(L: lua_State?, i: Int): CharPtr? {
        val blah = IntArray(1) //uint
        return LuaAPI.lua_tolstring(L!!, i, blah) //out
    }

    ////#define lua_open()	luaL_newstate()
    fun lua_open(): lua_State? {
        return LuaAuxLib.luaL_newstate()
    }

    ////#define lua_getregistry(L)	lua_pushvalue(L, LUA_REGISTRYINDEX)
    fun lua_getregistry(L: lua_State?) {
        LuaAPI.lua_pushvalue(L!!, LUA_REGISTRYINDEX)
    }

    ////#define lua_getgccount(L)	lua_gc(L, LUA_GCCOUNT, 0)
    fun lua_getgccount(L: lua_State?): Int {
        return LuaAPI.lua_gc(L, LUA_GCCOUNT, 0)
    }

    ///#define lua_Chunkreader		lua_Reader
///#define lua_Chunkwriter		lua_Writer
//
//		 ** {======================================================================
//		 ** Debug API
//		 ** =======================================================================
//
//
//		 ** Event codes
//
    const val LUA_HOOKCALL = 0
    const val LUA_HOOKRET = 1
    const val LUA_HOOKLINE = 2
    const val LUA_HOOKCOUNT = 3
    const val LUA_HOOKTAILRET = 4
    //
//		 ** Event masks
//
    const val LUA_MASKCALL = 1 shl LUA_HOOKCALL
    const val LUA_MASKRET = 1 shl LUA_HOOKRET
    const val LUA_MASKLINE = 1 shl LUA_HOOKLINE
    const val LUA_MASKCOUNT = 1 shl LUA_HOOKCOUNT

    interface lua_CFunction {
        fun exec(L: lua_State): Int
    }

    interface lua_Reader {
        /*sz*/ /*out*/ /*uint*/
        fun exec(L: lua_State?, ud: Any, sz: IntArray): CharPtr?
    }

    // functions that read/write blocks when loading/dumping Lua chunks
//public delegate int lua_Writer(lua_State L, CharPtr p, int//uint// sz, object ud);
    interface lua_Writer {
        //uint sz
        fun exec(L: lua_State, p: CharPtr, sz: Int, ud: Any): Int
    }

    interface lua_Alloc {
        fun exec(t: ClassType): Any?
    }

    /* Functions to be called by the debuger in specific events */ //public delegate void lua_Hook(lua_State L, lua_Debug ar);
    interface lua_Hook {
        fun exec(L: lua_State, ar: lua_Debug)
    }

    class lua_Debug {
        var event_ = 0
        var name /* (n) */: CharPtr? = null
        var namewhat /* (n) `global', `local', `field', `method' */: CharPtr? = null
        var what /* (S) `Lua', `C', `main', `tail' */: CharPtr? = null
        var source /* (S) */: CharPtr? = null
        var currentline /* (l) */ = 0
        var nups /* (u) number of upvalues */ = 0
        var linedefined /* (S) */ = 0
        var lastlinedefined /* (S) */ = 0
        var short_src: CharPtr = CharPtr.Companion.toCharPtr(CharArray(LuaConf.LUA_IDSIZE)) /* (S) */
        /* private part */
        var i_ci /* active function */ = 0
    } // }======================================================================
//        *****************************************************************************
//		 * Copyright (C) 1994-2008 Lua.org, PUC-Rio.  All rights reserved.
//		 *
//		 * Permission is hereby granted, free of charge, to any person obtaining
//		 * a copy of this software and associated documentation files (the
//		 * "Software"), to deal in the Software without restriction, including
//		 * without limitation the rights to use, copy, modify, merge, publish,
//		 * distribute, sublicense, and/or sell copies of the Software, and to
//		 * permit persons to whom the Software is furnished to do so, subject to
//		 * the following conditions:
//		 *
//		 * The above copyright notice and this permission notice shall be
//		 * included in all copies or substantial portions of the Software.
//		 *
//		 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//		 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//		 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//		 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//		 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//		 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//		 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//		 *****************************************************************************
}