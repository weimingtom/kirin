package kirin
import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.Lua.lua_Debug
import kirin.Lua.lua_Reader
import kirin.LuaAuxLib.luaL_Reg
import kirin.LuaAuxLib.luaL_checkint_delegate


//
// ** $Id: lbaselib.c,v 1.191.1.6 2008/02/14 16:46:22 roberto Exp $
// ** Basic library
// ** See Copyright Notice in lua.h
//
//using lua_Number = System.Double;
object LuaBaseLib {
    //
//		 ** If your system does not support `stdout', you can just remove this function.
//		 ** If you need, you can define your own `print' function, following this
//		 ** model but changing `fputs' to put the strings at a proper place
//		 ** (a console window or a log file, for instance).
//
    private fun luaB_print(L: lua_State): Int {
        val n: Int = LuaAPI.lua_gettop(L) // number of arguments
        var i: Int
        Lua.lua_getglobal(L, CharPtr.Companion.toCharPtr("tostring"))
        i = 1
        while (i <= n) {
            var s: CharPtr?
            LuaAPI.lua_pushvalue(L, -1) // function to be called
            LuaAPI.lua_pushvalue(L, i) // value to print
            LuaAPI.lua_call(L, 1, 1)
            s = Lua.lua_tostring(L, -1) // get result
            if (CharPtr.Companion.isEqual(s, null)) {
                return LuaAuxLib.luaL_error(
                    L,
                    CharPtr.Companion.toCharPtr(
                        LuaConf.LUA_QL("tostring").toString() + " must return a string to " + LuaConf.LUA_QL("print")
                    )
                ) //FIXME:
            }
            if (i > 1) {
                CLib.fputs(CharPtr.Companion.toCharPtr("\t"), CLib.stdout)
            }
            CLib.fputs(s, CLib.stdout)
            Lua.lua_pop(L, 1) // pop result
            i++
        }
        //FIXME:
//Console.Write("\n", CLib.stdout);
        StreamProxy.Companion.Write("\n")
        return 0
    }

    private fun luaB_tonumber(L: lua_State): Int {
        val base_: Int = LuaAuxLib.luaL_optint(L, 2, 10)
        if (base_ == 10) { // standard conversion
            LuaAuxLib.luaL_checkany(L, 1)
            if (LuaAPI.lua_isnumber(L, 1) != 0) {
                LuaAPI.lua_pushnumber(L, LuaAPI.lua_tonumber(L, 1))
                return 1
            }
        } else {
            val s1: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
            val s2: Array<CharPtr?> = arrayOfNulls<CharPtr>(1)
            s2[0] = CharPtr()
            val n: Long //ulong
            LuaAuxLib.luaL_argcheck(L, 2 <= base_ && base_ <= 36, 2, "base out of range")
            n = CLib.strtoul(s1, s2, base_) //out
            if (CharPtr.Companion.isNotEqual(s1, s2[0])) { // at least one valid digit?
                while (CLib.isspace((s2[0]!!.get(0) as Byte).toInt())) {
                    s2[0] = s2[0]!!.next() // skip trailing spaces
                }
                if (s2[0]!!.get(0) == '\u0000') { // no invalid trailing characters?
                    LuaAPI.lua_pushnumber(L, n.toDouble()) //lua_Number
                    return 1
                }
            }
        }
        LuaAPI.lua_pushnil(L) // else not a number
        return 1
    }

    private fun luaB_error(L: lua_State): Int {
        val level: Int = LuaAuxLib.luaL_optint(L, 2, 1)
        LuaAPI.lua_settop(L, 1)
        if (LuaAPI.lua_isstring(L, 1) != 0 && level > 0) { // add extra information?
            LuaAuxLib.luaL_where(L, level)
            LuaAPI.lua_pushvalue(L, 1)
            LuaAPI.lua_concat(L, 2)
        }
        return LuaAPI.lua_error(L)
    }

    private fun luaB_getmetatable(L: lua_State): Int {
        LuaAuxLib.luaL_checkany(L, 1)
        if (LuaAPI.lua_getmetatable(L, 1) == 0) {
            LuaAPI.lua_pushnil(L)
            return 1 // no metatable
        }
        LuaAuxLib.luaL_getmetafield(L, 1, CharPtr.Companion.toCharPtr("__metatable"))
        return 1 // returns either __metatable field (if present) or metatable
    }

    private fun luaB_setmetatable(L: lua_State): Int {
        val t: Int = LuaAPI.lua_type(L, 2)
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        LuaAuxLib.luaL_argcheck(L, t == Lua.LUA_TNIL || t == Lua.LUA_TTABLE, 2, "nil or table expected")
        if (LuaAuxLib.luaL_getmetafield(L, 1, CharPtr.Companion.toCharPtr("__metatable")) != 0) {
            LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("cannot change a protected metatable"))
        }
        LuaAPI.lua_settop(L, 2)
        LuaAPI.lua_setmetatable(L, 1)
        return 1
    }

    private fun getfunc(L: lua_State, opt: Int) {
        if (Lua.lua_isfunction(L, 1)) {
            LuaAPI.lua_pushvalue(L, 1)
        } else {
            val ar = lua_Debug()
            val level: Int = if (opt != 0) LuaAuxLib.luaL_optint(L, 1, 1) else LuaAuxLib.luaL_checkint(L, 1)
            LuaAuxLib.luaL_argcheck(L, level >= 0, 1, "level must be non-negative")
            if (LuaDebug.lua_getstack(L, level, ar) == 0) {
                LuaAuxLib.luaL_argerror(L, 1, CharPtr.Companion.toCharPtr("invalid level"))
            }
            LuaDebug.lua_getinfo(L, CharPtr.Companion.toCharPtr("f"), ar)
            if (Lua.lua_isnil(L, -1)) {
                LuaAuxLib.luaL_error(
                    L,
                    CharPtr.Companion.toCharPtr("no function environment for tail call at level %d"),
                    level
                )
            }
        }
    }

    private fun luaB_getfenv(L: lua_State): Int {
        getfunc(L, 1)
        if (LuaAPI.lua_iscfunction(L, -1)) { // is a C function?
            LuaAPI.lua_pushvalue(L, Lua.LUA_GLOBALSINDEX) // return the thread's global env.
        } else {
            LuaAPI.lua_getfenv(L, -1)
        }
        return 1
    }

    private fun luaB_setfenv(L: lua_State): Int {
        LuaAuxLib.luaL_checktype(L, 2, Lua.LUA_TTABLE)
        getfunc(L, 0)
        LuaAPI.lua_pushvalue(L, 2)
        if (LuaAPI.lua_isnumber(L, 1) != 0 && LuaAPI.lua_tonumber(
                L,
                1
            ) == 0.0
        ) { // change environment of current thread
            LuaAPI.lua_pushthread(L)
            LuaAPI.lua_insert(L, -2)
            LuaAPI.lua_setfenv(L, -2)
            return 0
        } else if (LuaAPI.lua_iscfunction(L, -2) || LuaAPI.lua_setfenv(L, -2) == 0) {
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("setfenv").toString() + " cannot change environment of given object")
            )
        }
        return 1
    }

    private fun luaB_rawequal(L: lua_State): Int {
        LuaAuxLib.luaL_checkany(L, 1)
        LuaAuxLib.luaL_checkany(L, 2)
        LuaAPI.lua_pushboolean(L, LuaAPI.lua_rawequal(L, 1, 2))
        return 1
    }

    private fun luaB_rawget(L: lua_State): Int {
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        LuaAuxLib.luaL_checkany(L, 2)
        LuaAPI.lua_settop(L, 2)
        LuaAPI.lua_rawget(L, 1)
        return 1
    }

    private fun luaB_rawset(L: lua_State): Int {
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        LuaAuxLib.luaL_checkany(L, 2)
        LuaAuxLib.luaL_checkany(L, 3)
        LuaAPI.lua_settop(L, 3)
        LuaAPI.lua_rawset(L, 1)
        return 1
    }

    private fun luaB_gcinfo(L: lua_State): Int {
        LuaAPI.lua_pushinteger(L, Lua.lua_getgccount(L))
        return 1
    }

    val opts: Array<CharPtr?> = arrayOf<CharPtr?>(
        CharPtr.Companion.toCharPtr("stop"),
        CharPtr.Companion.toCharPtr("restart"),
        CharPtr.Companion.toCharPtr("collect"),
        CharPtr.Companion.toCharPtr("count"),
        CharPtr.Companion.toCharPtr("step"),
        CharPtr.Companion.toCharPtr("setpause"),
        CharPtr.Companion.toCharPtr("setstepmul"),
        null
    )
    val optsnum = intArrayOf(
        Lua.LUA_GCSTOP,
        Lua.LUA_GCRESTART,
        Lua.LUA_GCCOLLECT,
        Lua.LUA_GCCOUNT,
        Lua.LUA_GCSTEP,
        Lua.LUA_GCSETPAUSE,
        Lua.LUA_GCSETSTEPMUL
    )

    private fun luaB_collectgarbage(L: lua_State): Int {
        val o: Int = LuaAuxLib.luaL_checkoption(L, 1, CharPtr.Companion.toCharPtr("collect"), opts)
        val ex: Int = LuaAuxLib.luaL_optint(L, 2, 0)
        val res: Int = LuaAPI.lua_gc(L, optsnum[o], ex)
        return when (optsnum[o]) {
            Lua.LUA_GCCOUNT -> {
                val b: Int = LuaAPI.lua_gc(L, Lua.LUA_GCCOUNTB, 0)
                LuaAPI.lua_pushnumber(L, res + b.toDouble() / 1024) //lua_Number
                1
            }
            Lua.LUA_GCSTEP -> {
                LuaAPI.lua_pushboolean(L, res)
                1
            }
            else -> {
                LuaAPI.lua_pushnumber(L, res.toDouble())
                1
            }
        }
    }

    private fun luaB_type(L: lua_State): Int {
        LuaAuxLib.luaL_checkany(L, 1)
        LuaAPI.lua_pushstring(L, LuaAuxLib.luaL_typename(L, 1))
        return 1
    }

    private fun luaB_next(L: lua_State): Int {
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        LuaAPI.lua_settop(L, 2) // create a 2nd argument if there isn't one
        return if (LuaAPI.lua_next(L, 1) != 0) {
            2
        } else {
            LuaAPI.lua_pushnil(L)
            1
        }
    }

    private fun luaB_pairs(L: lua_State): Int {
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        LuaAPI.lua_pushvalue(L, Lua.lua_upvalueindex(1)) // return generator,
        LuaAPI.lua_pushvalue(L, 1) // state,
        LuaAPI.lua_pushnil(L) // and initial value
        return 3
    }

    private fun ipairsaux(L: lua_State): Int {
        var i: Int = LuaAuxLib.luaL_checkint(L, 2)
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        i++ // next value
        LuaAPI.lua_pushinteger(L, i)
        LuaAPI.lua_rawgeti(L, 1, i)
        return if (Lua.lua_isnil(L, -1)) 0 else 2
    }

    private fun luaB_ipairs(L: lua_State): Int {
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        LuaAPI.lua_pushvalue(L, Lua.lua_upvalueindex(1)) // return generator,
        LuaAPI.lua_pushvalue(L, 1) // state,
        LuaAPI.lua_pushinteger(L, 0) // and initial value
        return 3
    }

    private fun load_aux(L: lua_State, status: Int): Int {
        return if (status == 0) { // OK?
            1
        } else {
            LuaAPI.lua_pushnil(L)
            LuaAPI.lua_insert(L, -2) // put before error message
            2 // return nil plus error message
        }
    }

    private fun luaB_loadstring(L: lua_State): Int {
        val l = IntArray(1) //uint
        val s: CharPtr = LuaAuxLib.luaL_checklstring(L, 1, l) //out
        val chunkname: CharPtr? = LuaAuxLib.luaL_optstring(L, 2, s)
        return load_aux(L, LuaAuxLib.luaL_loadbuffer(L, s, l[0], chunkname))
    }

    private fun luaB_loadfile(L: lua_State): Int {
        val fname: CharPtr? = LuaAuxLib.luaL_optstring(L, 1, null)
        return load_aux(L, LuaAuxLib.luaL_loadfile(L, fname))
    }

    //
//		 ** Reader for generic `load' function: `lua_load' uses the
//		 ** stack for internal stuff, so the reader cannot change the
//		 ** stack top. Instead, it keeps its resulting string in a
//		 ** reserved slot inside the stack.
//
    private fun generic_reader(L: lua_State?, ud: Any, size: IntArray): CharPtr? { //uint - out
//(void)ud;  /* to avoid warnings */
        LuaAuxLib.luaL_checkstack(L, 2, CharPtr.Companion.toCharPtr("too many nested functions"))
        LuaAPI.lua_pushvalue(L!!, 1) // get function
        LuaAPI.lua_call(L!!, 0, 1) // call it
        if (Lua.lua_isnil(L, -1)) {
            size[0] = 0
            return null
        } else if (LuaAPI.lua_isstring(L!!, -1) != 0) {
            LuaAPI.lua_replace(L, 3) // save string in a reserved stack slot
            return LuaAPI.lua_tolstring(L!!, 3, size) //out
        } else {
            size[0] = 0
            LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("reader function must return a string"))
        }
        return null // to avoid warnings
    }

    private fun luaB_load(L: lua_State): Int {
        val status: Int
        val cname: CharPtr? = LuaAuxLib.luaL_optstring(L, 2, CharPtr.Companion.toCharPtr("=(load)"))
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TFUNCTION)
        LuaAPI.lua_settop(L, 3) // function, eventual name, plus one reserved slot
        status = LuaAPI.lua_load(L, generic_reader_delegate(), null, cname)
        return load_aux(L, status)
    }

    private fun luaB_dofile(L: lua_State): Int {
        val fname: CharPtr? = LuaAuxLib.luaL_optstring(L, 1, null)
        val n: Int = LuaAPI.lua_gettop(L)
        if (LuaAuxLib.luaL_loadfile(L, fname) != 0) {
            LuaAPI.lua_error(L)
        }
        LuaAPI.lua_call(L, 0, Lua.LUA_MULTRET)
        return LuaAPI.lua_gettop(L) - n
    }

    private fun luaB_assert(L: lua_State): Int {
        LuaAuxLib.luaL_checkany(L, 1)
        return if (LuaAPI.lua_toboolean(L, 1) == 0) {
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr("%s"),
                LuaAuxLib.luaL_optstring(L, 2, CharPtr.Companion.toCharPtr("assertion failed!"))
            )
        } else LuaAPI.lua_gettop(L)
    }

    private fun luaB_unpack(L: lua_State): Int {
        var i: Int
        val e: Int
        val n: Int
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        i = LuaAuxLib.luaL_optint(L, 2, 1)
        e = LuaAuxLib.luaL_opt_integer(L, luaL_checkint_delegate(), 3, LuaAuxLib.luaL_getn(L, 1).toDouble())
        if (i > e) {
            return 0 // empty range
        }
        n = e - i + 1 // number of elements
        if (n <= 0 || LuaAPI.lua_checkstack(L, n) == 0) { // n <= 0 means arith. overflow
            return LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("too many results to unpack"))
        }
        LuaAPI.lua_rawgeti(L, 1, i) // push arg[i] (avoiding overflow problems)
        while (i++ < e) { // push arg[i + 1...e]
            LuaAPI.lua_rawgeti(L, 1, i)
        }
        return n
    }

    private fun luaB_select(L: lua_State): Int {
        val n: Int = LuaAPI.lua_gettop(L)
        return if (LuaAPI.lua_type(L, 1) == Lua.LUA_TSTRING && Lua.lua_tostring(L, 1)!!.get(0) == '#') {
            LuaAPI.lua_pushinteger(L, n - 1)
            1
        } else {
            var i: Int = LuaAuxLib.luaL_checkint(L, 1)
            if (i < 0) {
                i = n + i
            } else if (i > n) {
                i = n
            }
            LuaAuxLib.luaL_argcheck(L, 1 <= i, 1, "index out of range")
            n - i
        }
    }

    private fun luaB_pcall(L: lua_State): Int {
        val status: Int
        LuaAuxLib.luaL_checkany(L, 1)
        status = LuaAPI.lua_pcall(L, LuaAPI.lua_gettop(L) - 1, Lua.LUA_MULTRET, 0)
        LuaAPI.lua_pushboolean(L, if (status == 0) 1 else 0)
        LuaAPI.lua_insert(L, 1)
        return LuaAPI.lua_gettop(L) // return status + all results
    }

    private fun luaB_xpcall(L: lua_State): Int {
        val status: Int
        LuaAuxLib.luaL_checkany(L, 2)
        LuaAPI.lua_settop(L, 2)
        LuaAPI.lua_insert(L, 1) // put error function under function to be called
        status = LuaAPI.lua_pcall(L, 0, Lua.LUA_MULTRET, 1)
        LuaAPI.lua_pushboolean(L, if (status == 0) 1 else 0)
        LuaAPI.lua_replace(L, 1)
        return LuaAPI.lua_gettop(L) // return status + all results
    }

    private fun luaB_tostring(L: lua_State): Int {
        LuaAuxLib.luaL_checkany(L, 1)
        if (LuaAuxLib.luaL_callmeta(L, 1, CharPtr.Companion.toCharPtr("__tostring")) != 0) { // is there a metafield?
            return 1 // use its value
        }
        when (LuaAPI.lua_type(L, 1)) {
            Lua.LUA_TNUMBER -> {
                LuaAPI.lua_pushstring(L, Lua.lua_tostring(L, 1))
            }
            Lua.LUA_TSTRING -> {
                LuaAPI.lua_pushvalue(L, 1)
            }
            Lua.LUA_TBOOLEAN -> {
                LuaAPI.lua_pushstring(
                    L,
                    if (LuaAPI.lua_toboolean(
                            L,
                            1
                        ) != 0
                    ) CharPtr.Companion.toCharPtr("true") else CharPtr.Companion.toCharPtr("false")
                )
            }
            Lua.LUA_TNIL -> {
                Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("nil"))
            }
            else -> {
                LuaAPI.lua_pushfstring(
                    L,
                    CharPtr.Companion.toCharPtr("%s: %p"),
                    LuaAuxLib.luaL_typename(L, 1),
                    LuaAPI.lua_topointer(L, 1)
                )
            }
        }
        return 1
    }

    private fun luaB_newproxy(L: lua_State): Int {
        LuaAPI.lua_settop(L, 1)
        LuaAPI.lua_newuserdata(L, 0) // create proxy
        if (LuaAPI.lua_toboolean(L, 1) == 0) {
            return 1 // no metatable
        } else if (Lua.lua_isboolean(L, 1)) {
            Lua.lua_newtable(L) // create a new metatable `m'...
            LuaAPI.lua_pushvalue(L, -1) //... and mark `m' as a valid metatable
            LuaAPI.lua_pushboolean(L, 1)
            LuaAPI.lua_rawset(L, Lua.lua_upvalueindex(1)) // weaktable[m] = true
        } else {
            var validproxy = 0 // to check if weaktable[metatable(u)] == true
            if (LuaAPI.lua_getmetatable(L, 1) != 0) {
                LuaAPI.lua_rawget(L, Lua.lua_upvalueindex(1))
                validproxy = LuaAPI.lua_toboolean(L, -1)
                Lua.lua_pop(L, 1) // remove value
            }
            LuaAuxLib.luaL_argcheck(L, validproxy != 0, 1, "boolean or proxy expected")
            LuaAPI.lua_getmetatable(L, 1) // metatable is valid; get it
        }
        LuaAPI.lua_setmetatable(L, 2)
        return 1
    }

    private val base_funcs: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("assert"), LuaBaseLib_delegate("luaB_assert")),
        luaL_Reg(CharPtr.Companion.toCharPtr("collectgarbage"), LuaBaseLib_delegate("luaB_collectgarbage")),
        luaL_Reg(CharPtr.Companion.toCharPtr("dofile"), LuaBaseLib_delegate("luaB_dofile")),
        luaL_Reg(CharPtr.Companion.toCharPtr("error"), LuaBaseLib_delegate("luaB_error")),
        luaL_Reg(CharPtr.Companion.toCharPtr("gcinfo"), LuaBaseLib_delegate("luaB_gcinfo")),
        luaL_Reg(CharPtr.Companion.toCharPtr("getfenv"), LuaBaseLib_delegate("luaB_getfenv")),
        luaL_Reg(CharPtr.Companion.toCharPtr("getmetatable"), LuaBaseLib_delegate("luaB_getmetatable")),
        luaL_Reg(CharPtr.Companion.toCharPtr("loadfile"), LuaBaseLib_delegate("luaB_loadfile")),
        luaL_Reg(CharPtr.Companion.toCharPtr("load"), LuaBaseLib_delegate("luaB_load")),
        luaL_Reg(CharPtr.Companion.toCharPtr("loadstring"), LuaBaseLib_delegate("luaB_loadstring")),
        luaL_Reg(CharPtr.Companion.toCharPtr("next"), LuaBaseLib_delegate("luaB_next")),
        luaL_Reg(CharPtr.Companion.toCharPtr("pcall"), LuaBaseLib_delegate("luaB_pcall")),
        luaL_Reg(CharPtr.Companion.toCharPtr("print"), LuaBaseLib_delegate("luaB_print")),
        luaL_Reg(CharPtr.Companion.toCharPtr("rawequal"), LuaBaseLib_delegate("luaB_rawequal")),
        luaL_Reg(CharPtr.Companion.toCharPtr("rawget"), LuaBaseLib_delegate("luaB_rawget")),
        luaL_Reg(CharPtr.Companion.toCharPtr("rawset"), LuaBaseLib_delegate("luaB_rawset")),
        luaL_Reg(CharPtr.Companion.toCharPtr("select"), LuaBaseLib_delegate("luaB_select")),
        luaL_Reg(CharPtr.Companion.toCharPtr("setfenv"), LuaBaseLib_delegate("luaB_setfenv")),
        luaL_Reg(CharPtr.Companion.toCharPtr("setmetatable"), LuaBaseLib_delegate("luaB_setmetatable")),
        luaL_Reg(CharPtr.Companion.toCharPtr("tonumber"), LuaBaseLib_delegate("luaB_tonumber")),
        luaL_Reg(CharPtr.Companion.toCharPtr("tostring"), LuaBaseLib_delegate("luaB_tostring")),
        luaL_Reg(CharPtr.Companion.toCharPtr("type"), LuaBaseLib_delegate("luaB_type")),
        luaL_Reg(CharPtr.Companion.toCharPtr("unpack"), LuaBaseLib_delegate("luaB_unpack")),
        luaL_Reg(CharPtr.Companion.toCharPtr("xpcall"), LuaBaseLib_delegate("luaB_xpcall")),
        luaL_Reg(null, null)
    )
    //
//		 ** {======================================================
//		 ** Coroutine library
//		 ** =======================================================
//
    const val CO_RUN = 0 // running
    const val CO_SUS = 1 // suspended
    const val CO_NOR = 2 // 'normal' (it resumed another coroutine)
    const val CO_DEAD = 3
    private val statnames =
        arrayOf("running", "suspended", "normal", "dead")

    private fun costatus(L: lua_State, co: lua_State?): Int {
        return if (L === co) {
            CO_RUN
        } else when (LuaAPI.lua_status(co!!)) {
            Lua.LUA_YIELD -> {
                CO_SUS
            }
            0 -> {
                val ar = lua_Debug()
                if (LuaDebug.lua_getstack(co, 0, ar) > 0) { // does it have frames?
                    CO_NOR // it is running
                } else if (LuaAPI.lua_gettop(co!!) == 0) {
                    CO_DEAD
                } else {
                    CO_SUS // initial state
                }
            }
            else -> {
                // some error occured
                CO_DEAD
            }
        }
    }

    private fun luaB_costatus(L: lua_State): Int {
        val co: lua_State? = LuaAPI.lua_tothread(L, 1)
        LuaAuxLib.luaL_argcheck(L, co != null, 1, "coroutine expected")
        LuaAPI.lua_pushstring(L, CharPtr.Companion.toCharPtr(statnames[costatus(L, co)]))
        return 1
    }

    private fun auxresume(L: lua_State, co: lua_State?, narg: Int): Int {
        var status = costatus(L, co)
        if (LuaAPI.lua_checkstack(co!!, narg) == 0) {
            LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("too many arguments to resume"))
        }
        if (status != CO_SUS) {
            LuaAPI.lua_pushfstring(
                L,
                CharPtr.Companion.toCharPtr("cannot resume %s coroutine"),
                statnames[status]
            )
            return -1 // error flag
        }
        LuaAPI.lua_xmove(L, co!!, narg)
        LuaAPI.lua_setlevel(L, co!!)
        status = LuaDo.lua_resume(co, narg)
        return if (status == 0 || status == Lua.LUA_YIELD) {
            val nres: Int = LuaAPI.lua_gettop(co!!)
            if (LuaAPI.lua_checkstack(L, nres + 1) == 0) {
                LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("too many results to resume"))
            }
            LuaAPI.lua_xmove(co, L, nres) // move yielded values
            nres
        } else {
            LuaAPI.lua_xmove(co, L, 1) // move error message
            -1 // error flag
        }
    }

    private fun luaB_coresume(L: lua_State): Int {
        val co: lua_State? = LuaAPI.lua_tothread(L, 1)
        val r: Int
        LuaAuxLib.luaL_argcheck(L, co != null, 1, "coroutine expected")
        r = auxresume(L, co, LuaAPI.lua_gettop(L) - 1)
        return if (r < 0) {
            LuaAPI.lua_pushboolean(L, 0)
            LuaAPI.lua_insert(L, -2)
            2 // return false + error message
        } else {
            LuaAPI.lua_pushboolean(L, 1)
            LuaAPI.lua_insert(L, -(r + 1))
            r + 1 // return true + `resume' returns
        }
    }

    private fun luaB_auxwrap(L: lua_State): Int {
        val co: lua_State? = LuaAPI.lua_tothread(L, Lua.lua_upvalueindex(1))
        val r = auxresume(L, co, LuaAPI.lua_gettop(L))
        if (r < 0) {
            if (LuaAPI.lua_isstring(L, -1) != 0) { // error object is a string?
                LuaAuxLib.luaL_where(L, 1) // add extra info
                LuaAPI.lua_insert(L, -2)
                LuaAPI.lua_concat(L, 2)
            }
            LuaAPI.lua_error(L) // propagate error
        }
        return r
    }

    private fun luaB_cocreate(L: lua_State): Int {
        val NL: lua_State = LuaAPI.lua_newthread(L)
        LuaAuxLib.luaL_argcheck(
            L,
            Lua.lua_isfunction(L, 1) && !LuaAPI.lua_iscfunction(L, 1),
            1,
            "Lua function expected"
        )
        LuaAPI.lua_pushvalue(L, 1) // move function to top
        LuaAPI.lua_xmove(L, NL, 1) // move function from L to NL
        return 1
    }

    private fun luaB_cowrap(L: lua_State): Int {
        luaB_cocreate(L)
        LuaAPI.lua_pushcclosure(L, LuaBaseLib_delegate("luaB_auxwrap"), 1)
        return 1
    }

    private fun luaB_yield(L: lua_State): Int {
        return LuaDo.lua_yield(L, LuaAPI.lua_gettop(L))
    }

    private fun luaB_corunning(L: lua_State): Int {
        if (LuaAPI.lua_pushthread(L) != 0) {
            LuaAPI.lua_pushnil(L) // main thread is not a coroutine
        }
        return 1
    }

    private val co_funcs: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("create"), LuaBaseLib_delegate("luaB_cocreate")),
        luaL_Reg(CharPtr.Companion.toCharPtr("resume"), LuaBaseLib_delegate("luaB_coresume")),
        luaL_Reg(CharPtr.Companion.toCharPtr("running"), LuaBaseLib_delegate("luaB_corunning")),
        luaL_Reg(CharPtr.Companion.toCharPtr("status"), LuaBaseLib_delegate("luaB_costatus")),
        luaL_Reg(CharPtr.Companion.toCharPtr("wrap"), LuaBaseLib_delegate("luaB_cowrap")),
        luaL_Reg(CharPtr.Companion.toCharPtr("yield"), LuaBaseLib_delegate("luaB_yield")),
        luaL_Reg(null, null)
    )

    // }======================================================
    private fun auxopen(L: lua_State, name: CharPtr, f: lua_CFunction, u: lua_CFunction) {
        Lua.lua_pushcfunction(L, u)
        LuaAPI.lua_pushcclosure(L, f, 1)
        LuaAPI.lua_setfield(L, -2, name)
    }

    private fun base_open(L: lua_State) { // set global _G
        LuaAPI.lua_pushvalue(L, Lua.LUA_GLOBALSINDEX)
        Lua.lua_setglobal(L, CharPtr.Companion.toCharPtr("_G"))
        // open lib into global table
        LuaAuxLib.luaL_register(L, CharPtr.Companion.toCharPtr("_G"), base_funcs)
        Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr(Lua.LUA_VERSION))
        Lua.lua_setglobal(L, CharPtr.Companion.toCharPtr("_VERSION")) // set global _VERSION
        // `ipairs' and `pairs' need auxliliary functions as upvalues
        auxopen(
            L,
            CharPtr.Companion.toCharPtr("ipairs"),
            LuaBaseLib_delegate("luaB_ipairs"),
            LuaBaseLib_delegate("ipairsaux")
        )
        auxopen(
            L,
            CharPtr.Companion.toCharPtr("pairs"),
            LuaBaseLib_delegate("luaB_pairs"),
            LuaBaseLib_delegate("luaB_next")
        )
        // `newproxy' needs a weaktable as upvalue
        LuaAPI.lua_createtable(L, 0, 1) // new table `w'
        LuaAPI.lua_pushvalue(L, -1) // `w' will be its own metatable
        LuaAPI.lua_setmetatable(L, -2)
        Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("kv"))
        LuaAPI.lua_setfield(L, -2, CharPtr.Companion.toCharPtr("__mode")) // metatable(w).__mode = "kv"
        LuaAPI.lua_pushcclosure(L, LuaBaseLib_delegate("luaB_newproxy"), 1)
        Lua.lua_setglobal(L, CharPtr.Companion.toCharPtr("newproxy")) // set global `newproxy'
    }

    fun luaopen_base(L: lua_State): Int {
        base_open(L)
        LuaAuxLib.luaL_register(L, CharPtr.Companion.toCharPtr(LuaLib.LUA_COLIBNAME), co_funcs)
        return 2
    }

    class generic_reader_delegate : lua_Reader {
        override fun exec(L: lua_State?, ud: Any, sz: IntArray): CharPtr? { //uint - out
            return generic_reader(L, ud, sz) //out
        }
    }

    class LuaBaseLib_delegate(private val name: String) : lua_CFunction {
        override fun exec(L: lua_State): Int {
            if ("luaB_assert" == name) {
                return luaB_assert(L)
            } else if ("luaB_collectgarbage" == name) {
                return luaB_collectgarbage(L)
            } else if ("luaB_dofile" == name) {
                return luaB_dofile(L)
            } else if ("luaB_error" == name) {
                return luaB_error(L)
            } else if ("luaB_gcinfo" == name) {
                return luaB_gcinfo(L)
            } else if ("luaB_getfenv" == name) {
                return luaB_getfenv(L)
            } else if ("luaB_getmetatable" == name) {
                return luaB_getmetatable(L)
            } else if ("luaB_loadfile" == name) {
                return luaB_loadfile(L)
            } else if ("luaB_load" == name) {
                return luaB_load(L)
            } else if ("luaB_loadstring" == name) {
                return luaB_loadstring(L)
            } else if ("luaB_next" == name) {
                return luaB_next(L)
            } else if ("luaB_pcall" == name) {
                return luaB_pcall(L)
            } else if ("luaB_print" == name) {
                return luaB_print(L)
            } else if ("luaB_rawequal" == name) {
                return luaB_rawequal(L)
            } else if ("luaB_rawget" == name) {
                return luaB_rawget(L)
            } else if ("luaB_rawset" == name) {
                return luaB_rawset(L)
            } else if ("luaB_select"== name) {
                return luaB_select(L)
            } else if ("luaB_setfenv" == name) {
                return luaB_setfenv(L)
            } else if ("luaB_setmetatable" == name) {
                return luaB_setmetatable(L)
            } else if ("luaB_tonumber" == name) {
                return luaB_tonumber(L)
            } else if ("luaB_tostring" == name) {
                return luaB_tostring(L)
            } else if ("luaB_type" == name) {
                return luaB_type(L)
            } else if ("luaB_unpack" == name) {
                return luaB_unpack(L)
            } else if ("luaB_xpcall" == name) {
                return luaB_xpcall(L)
            }
            return if ("luaB_cocreate" == name) {
                luaB_cocreate(L)
            } else if ("luaB_coresume" == name) {
                luaB_coresume(L)
            } else if ("luaB_corunning" == name) {
                luaB_corunning(L)
            } else if ("luaB_costatus" == name) {
                luaB_costatus(L)
            } else if ("luaB_cowrap" == name) {
                luaB_cowrap(L)
            } else if ("luaB_yield" == name) {
                luaB_yield(L)
            } else if ("luaB_ipairs" == name) {
                luaB_ipairs(L)
            } else if ("ipairsaux" == name) {
                ipairsaux(L)
            } else if ("luaB_pairs" == name) {
                luaB_pairs(L)
            } else if ("luaB_next" == name) {
                luaB_next(L)
            } else if ("luaB_newproxy" == name) {
                luaB_newproxy(L)
            } else if ("luaB_auxwrap" == name) {
                luaB_auxwrap(L)
            } else {
                0
            }
        }

    }
}