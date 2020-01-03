package kirin

import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.LuaAuxLib.luaL_Reg
import kirin.LuaAuxLib.luaL_Buffer
import kirin.LuaAuxLib.luaL_checkint_delegate

//
// ** $Id: ltablib.c,v 1.38.1.3 2008/02/14 16:46:58 roberto Exp $
// ** Library for Table Manipulation
// ** See Copyright Notice in lua.h
//
//using lua_Number = System.Double;
object LuaTableLib {
    private fun aux_getn(L: lua_State, n: Int): Int {
        LuaAuxLib.luaL_checktype(L, n, Lua.LUA_TTABLE)
        return LuaAuxLib.luaL_getn(L, n)
    }

    private fun foreachi(L: lua_State): Int {
        var i: Int
        val n = aux_getn(L, 1)
        LuaAuxLib.luaL_checktype(L, 2, Lua.LUA_TFUNCTION)
        i = 1
        while (i <= n) {
            LuaAPI.lua_pushvalue(L, 2) // function
            LuaAPI.lua_pushinteger(L, i) // 1st argument
            LuaAPI.lua_rawgeti(L, 1, i) // 2nd argument
            LuaAPI.lua_call(L, 2, 1)
            if (!Lua.lua_isnil(L, -1)) {
                return 1
            }
            Lua.lua_pop(L, 1) // remove nil result
            i++
        }
        return 0
    }

    private fun _foreach(L: lua_State): Int {
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        LuaAuxLib.luaL_checktype(L, 2, Lua.LUA_TFUNCTION)
        LuaAPI.lua_pushnil(L) // first key
        while (LuaAPI.lua_next(L, 1) != 0) {
            LuaAPI.lua_pushvalue(L, 2) // function
            LuaAPI.lua_pushvalue(L, -3) // key
            LuaAPI.lua_pushvalue(L, -3) // value
            LuaAPI.lua_call(L, 2, 1)
            if (!Lua.lua_isnil(L, -1)) {
                return 1
            }
            Lua.lua_pop(L, 2) // remove value and result
        }
        return 0
    }

    private fun maxn(L: lua_State): Int {
        var max = 0.0 //lua_Number
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        LuaAPI.lua_pushnil(L) // first key
        while (LuaAPI.lua_next(L, 1) != 0) {
            Lua.lua_pop(L, 1) // remove value
            if (LuaAPI.lua_type(L, -1) == Lua.LUA_TNUMBER) {
                val v: Double = LuaAPI.lua_tonumber(L, -1) //lua_Number
                if (v > max) {
                    max = v
                }
            }
        }
        LuaAPI.lua_pushnumber(L, max)
        return 1
    }

    private fun getn(L: lua_State): Int {
        LuaAPI.lua_pushinteger(L, aux_getn(L, 1))
        return 1
    }

    private fun setn(L: lua_State): Int {
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        ///#ifndef luaL_setn
//luaL_setn(L, 1, luaL_checkint(L, 2));
///#else
        LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("setn").toString() + " is obsolete"))
        ///#endif
        LuaAPI.lua_pushvalue(L, 1)
        return 1
    }

    private fun tinsert(L: lua_State): Int {
        var e = aux_getn(L, 1) + 1 // first empty element
        val pos: Int // where to insert new element
        when (LuaAPI.lua_gettop(L)) {
            2 -> {
                // called with only 2 arguments
                pos = e // insert new element at the end
            }
            3 -> {
                var i: Int
                pos = LuaAuxLib.luaL_checkint(L, 2) // 2nd argument is the position
                if (pos > e) {
                    e = pos // `grow' array if necessary
                }
                i = e
                while (i > pos) {
                    // move up elements
                    LuaAPI.lua_rawgeti(L, 1, i - 1)
                    LuaAPI.lua_rawseti(L, 1, i) // t[i] = t[i-1]
                    i--
                }
            }
            else -> {
                return LuaAuxLib.luaL_error(
                    L,
                    CharPtr.Companion.toCharPtr("wrong number of arguments to " + LuaConf.LUA_QL("insert"))
                )
            }
        }
        LuaAuxLib.luaL_setn(L, 1, e) // new size
        LuaAPI.lua_rawseti(L, 1, pos) // t[pos] = v
        return 0
    }

    private fun tremove(L: lua_State): Int {
        val e = aux_getn(L, 1)
        var pos: Int = LuaAuxLib.luaL_optint(L, 2, e)
        if (!(1 <= pos && pos <= e)) { // position is outside bounds?
            return 0 // nothing to remove
        }
        LuaAuxLib.luaL_setn(L, 1, e - 1) // t.n = n-1
        LuaAPI.lua_rawgeti(L, 1, pos) // result = t[pos]
        while (pos < e) {
            LuaAPI.lua_rawgeti(L, 1, pos + 1)
            LuaAPI.lua_rawseti(L, 1, pos) // t[pos] = t[pos+1]
            pos++
        }
        LuaAPI.lua_pushnil(L)
        LuaAPI.lua_rawseti(L, 1, e) // t[e] = nil
        return 1
    }

    private fun addfield(L: lua_State, b: luaL_Buffer, i: Int) {
        LuaAPI.lua_rawgeti(L, 1, i)
        if (LuaAPI.lua_isstring(L, -1) == 0) {
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr("invalid value (%s) at index %d in table for " + LuaConf.LUA_QL("concat")),
                LuaAuxLib.luaL_typename(L, -1),
                i
            )
        }
        LuaAuxLib.luaL_addvalue(b)
    }

    private fun tconcat(L: lua_State): Int {
        val b = luaL_Buffer()
        val lsep = IntArray(1) //uint
        var i: Int
        val last: Int
        val sep: CharPtr? = LuaAuxLib.luaL_optlstring(L, 2, CharPtr.Companion.toCharPtr(""), lsep) //out
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        i = LuaAuxLib.luaL_optint(L, 3, 1)
        last = LuaAuxLib.luaL_opt_integer(L, luaL_checkint_delegate(), 4, LuaAuxLib.luaL_getn(L, 1).toDouble())
        LuaAuxLib.luaL_buffinit(L, b)
        while (i < last) {
            addfield(L, b, i)
            LuaAuxLib.luaL_addlstring(b, sep, lsep[0])
            i++
        }
        if (i == last) { // add last value (if interval was not empty)
            addfield(L, b, i)
        }
        LuaAuxLib.luaL_pushresult(b)
        return 1
    }

    //
//		 ** {======================================================
//		 ** Quicksort
//		 ** (based on `Algorithms in MODULA-3', Robert Sedgewick;
//		 **  Addison-Wesley, 1993.)
//
    private fun set2(L: lua_State, i: Int, j: Int) {
        LuaAPI.lua_rawseti(L, 1, i)
        LuaAPI.lua_rawseti(L, 1, j)
    }

    private fun sort_comp(L: lua_State, a: Int, b: Int): Int {
        return if (!Lua.lua_isnil(L, 2)) { // function?
            val res: Int
            LuaAPI.lua_pushvalue(L, 2)
            LuaAPI.lua_pushvalue(L, a - 1) // -1 to compensate function
            LuaAPI.lua_pushvalue(L, b - 2) // -2 to compensate function and `a'
            LuaAPI.lua_call(L, 2, 1)
            res = LuaAPI.lua_toboolean(L, -1)
            Lua.lua_pop(L, 1)
            res
        } else { // a < b?
            LuaAPI.lua_lessthan(L, a, b)
        }
    }

    private fun auxsort_loop1(L: lua_State, i: IntArray): Int { //ref
        LuaAPI.lua_rawgeti(L, 1, ++i[0])
        return sort_comp(L, -1, -2)
    }

    private fun auxsort_loop2(L: lua_State, j: IntArray): Int { //ref
        LuaAPI.lua_rawgeti(L, 1, --j[0])
        return sort_comp(L, -3, -1)
    }

    private fun auxsort(L: lua_State, l: Int, u: Int) {
        var l = l
        var u = u
        while (l < u) { // for tail recursion
            var i: Int
            var j: Int
            // sort elements a[l], a[(l+u)/2] and a[u]
            LuaAPI.lua_rawgeti(L, 1, l)
            LuaAPI.lua_rawgeti(L, 1, u)
            if (sort_comp(L, -1, -2) != 0) { // a[u] < a[l]?
                set2(L, l, u) // swap a[l] - a[u]
            } else {
                Lua.lua_pop(L, 2)
            }
            if (u - l == 1) {
                break // only 2 elements
            }
            i = (l + u) / 2
            LuaAPI.lua_rawgeti(L, 1, i)
            LuaAPI.lua_rawgeti(L, 1, l)
            if (sort_comp(L, -2, -1) != 0) { // a[i]<a[l]?
                set2(L, i, l)
            } else {
                Lua.lua_pop(L, 1) // remove a[l]
                LuaAPI.lua_rawgeti(L, 1, u)
                if (sort_comp(L, -1, -2) != 0) { // a[u]<a[i]?
                    set2(L, i, u)
                } else {
                    Lua.lua_pop(L, 2)
                }
            }
            if (u - l == 2) {
                break // only 3 elements
            }
            LuaAPI.lua_rawgeti(L, 1, i) // Pivot
            LuaAPI.lua_pushvalue(L, -1)
            LuaAPI.lua_rawgeti(L, 1, u - 1)
            set2(L, i, u - 1)
            // a[l] <= P == a[u-1] <= a[u], only need to sort from l+1 to u-2
            i = l
            j = u - 1
            while (true) {
                // invariant: a[l..i] <= P <= a[j..u]
// repeat ++i until a[i] >= P
                while (true) {
                    val i_ref = IntArray(1)
                    i_ref[0] = i
                    val ret_1 = auxsort_loop1(L, i_ref) //ref
                    i = i_ref[0]
                    if (ret_1 == 0) {
                        break
                    }
                    if (i > u) {
                        LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("invalid order function for sorting"))
                    }
                    Lua.lua_pop(L, 1) // remove a[i]
                }
                // repeat --j until a[j] <= P
                while (true) {
                    val j_ref = IntArray(1)
                    j_ref[0] = i
                    val ret_2 = auxsort_loop2(L, j_ref) //ref
                    j = j_ref[0]
                    if (ret_2 == 0) {
                        break
                    }
                    if (j < l) {
                        LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("invalid order function for sorting"))
                    }
                    Lua.lua_pop(L, 1) // remove a[j]
                }
                if (j < i) {
                    Lua.lua_pop(L, 3) // pop pivot, a[i], a[j]
                    break
                }
                set2(L, i, j)
            }
            LuaAPI.lua_rawgeti(L, 1, u - 1)
            LuaAPI.lua_rawgeti(L, 1, i)
            set2(L, u - 1, i) // swap pivot (a[u-1]) with a[i]
            // a[l..i-1] <= a[i] == P <= a[i+1..u]
// adjust so that smaller half is in [j..i] and larger one in [l..u]
            if (i - l < u - i) {
                j = l
                i = i - 1
                l = i + 2
            } else {
                j = i + 1
                i = u
                u = j - 2
            }
            auxsort(L, j, i) // call recursively the smaller one
        } // repeat the routine for the larger one
    }

    private fun sort(L: lua_State): Int {
        val n = aux_getn(L, 1)
        LuaAuxLib.luaL_checkstack(L, 40, CharPtr.Companion.toCharPtr("")) // assume array is smaller than 2^40
        if (!Lua.lua_isnoneornil(L, 2.0)) { // is there a 2nd argument?
            LuaAuxLib.luaL_checktype(L, 2, Lua.LUA_TFUNCTION)
        }
        LuaAPI.lua_settop(L, 2) // make sure there is two arguments
        auxsort(L, 1, n)
        return 0
    }

    // }======================================================
    private val tab_funcs: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("concat"), LuaTableLib_delegate("tconcat")),
        luaL_Reg(CharPtr.Companion.toCharPtr("foreach"), LuaTableLib_delegate("_foreach")),
        luaL_Reg(CharPtr.Companion.toCharPtr("foreachi"), LuaTableLib_delegate("foreachi")),
        luaL_Reg(CharPtr.Companion.toCharPtr("getn"), LuaTableLib_delegate("getn")),
        luaL_Reg(CharPtr.Companion.toCharPtr("maxn"), LuaTableLib_delegate("maxn")),
        luaL_Reg(CharPtr.Companion.toCharPtr("insert"), LuaTableLib_delegate("tinsert")),
        luaL_Reg(CharPtr.Companion.toCharPtr("remove"), LuaTableLib_delegate("tremove")),
        luaL_Reg(CharPtr.Companion.toCharPtr("setn"), LuaTableLib_delegate("setn")),
        luaL_Reg(CharPtr.Companion.toCharPtr("sort"), LuaTableLib_delegate("sort")),
        luaL_Reg(null, null)
    )

    fun luaopen_table(L: lua_State?): Int {
        LuaAuxLib.luaL_register(L, CharPtr.Companion.toCharPtr(LuaLib.LUA_TABLIBNAME), tab_funcs)
        return 1
    }

    class LuaTableLib_delegate(private val name: String) : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return if ("tconcat" == name) {
                tconcat(L)
            } else if ("_foreach" == name) {
                _foreach(L)
            } else if ("foreachi" == name) {
                foreachi(L)
            } else if ("getn" == name) {
                getn(L)
            } else if ("maxn" == name) {
                maxn(L)
            } else if ("tinsert" == name) {
                tinsert(L)
            } else if ("tremove" == name) {
                tremove(L)
            } else if ("setn" == name) {
                setn(L)
            } else if ("sort" == name) {
                sort(L)
            } else {
                0
            }
        }

    }
}