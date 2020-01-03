package kirin

import java.util.*
import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.LuaAuxLib.luaL_Reg


//
// ** $Id: lmathlib.c,v 1.67.1.1 2007/12/27 13:02:25 roberto Exp $
// ** Standard mathematical library
// ** See Copyright Notice in lua.h
//
//using lua_Number = System.Double;
object LuaMathLib {
    const val PI = 3.14159265358979323846
    const val RADIANS_PER_DEGREE = PI / 180.0
    private fun math_abs(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.abs(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_sin(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.sin(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_sinh(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.sinh(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_cos(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.cos(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_cosh(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.cosh(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_tan(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.tan(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_tanh(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.tanh(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_asin(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.asin(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_acos(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.acos(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_atan(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.atan(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_atan2(L: lua_State): Int {
        LuaAPI.lua_pushnumber(
            L,
            Math.atan2(LuaAuxLib.luaL_checknumber(L, 1), LuaAuxLib.luaL_checknumber(L, 2))
        )
        return 1
    }

    private fun math_ceil(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.ceil(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_floor(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.floor(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_fmod(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, CLib.fmod(LuaAuxLib.luaL_checknumber(L, 1), LuaAuxLib.luaL_checknumber(L, 2)))
        return 1
    }

    private fun math_modf(L: lua_State): Int {
        val ip = DoubleArray(1)
        val fp: Double = CLib.modf(LuaAuxLib.luaL_checknumber(L, 1), ip) //out
        LuaAPI.lua_pushnumber(L, ip[0])
        LuaAPI.lua_pushnumber(L, fp)
        return 2
    }

    private fun math_sqrt(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.sqrt(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_pow(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.pow(LuaAuxLib.luaL_checknumber(L, 1), LuaAuxLib.luaL_checknumber(L, 2)))
        return 1
    }

    private fun math_log(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.log(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_log10(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.log10(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_exp(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, Math.exp(LuaAuxLib.luaL_checknumber(L, 1)))
        return 1
    }

    private fun math_deg(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, LuaAuxLib.luaL_checknumber(L, 1) / RADIANS_PER_DEGREE)
        return 1
    }

    private fun math_rad(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, LuaAuxLib.luaL_checknumber(L, 1) * RADIANS_PER_DEGREE)
        return 1
    }

    private fun math_frexp(L: lua_State): Int {
        val e = IntArray(1)
        LuaAPI.lua_pushnumber(L, CLib.frexp(LuaAuxLib.luaL_checknumber(L, 1), e)) //out
        LuaAPI.lua_pushinteger(L, e[0])
        return 2
    }

    private fun math_ldexp(L: lua_State): Int {
        LuaAPI.lua_pushnumber(L, CLib.ldexp(LuaAuxLib.luaL_checknumber(L, 1), LuaAuxLib.luaL_checkint(L, 2)))
        return 1
    }

    private fun math_min(L: lua_State): Int {
        val n: Int = LuaAPI.lua_gettop(L) // number of arguments
        var dmin: Double = LuaAuxLib.luaL_checknumber(L, 1) //lua_Number
        var i: Int
        i = 2
        while (i <= n) {
            val d: Double = LuaAuxLib.luaL_checknumber(L, i) //lua_Number
            if (d < dmin) {
                dmin = d
            }
            i++
        }
        LuaAPI.lua_pushnumber(L, dmin)
        return 1
    }

    private fun math_max(L: lua_State): Int {
        val n: Int = LuaAPI.lua_gettop(L) // number of arguments
        var dmax: Double = LuaAuxLib.luaL_checknumber(L, 1) //lua_Number
        var i: Int
        i = 2
        while (i <= n) {
            val d: Double = LuaAuxLib.luaL_checknumber(L, i) //lua_Number
            if (d > dmax) {
                dmax = d
            }
            i++
        }
        LuaAPI.lua_pushnumber(L, dmax)
        return 1
    }

    private var rng = Random()
    private fun math_random(L: lua_State): Int { //             the `%' avoids the (rare) case of r==1, and is needed also because on
//			 some systems (SunOS!) `rand()' may return a value larger than RAND_MAX
//lua_Number r = (lua_Number)(rng.Next()%RAND_MAX) / (lua_Number)RAND_MAX;
        val r = rng.nextDouble() //lua_Number - lua_Number
        when (LuaAPI.lua_gettop(L)) {
            0 -> {
                // no arguments
                LuaAPI.lua_pushnumber(L, r) // Number between 0 and 1
            }
            1 -> {
                // only upper limit
                val u: Int = LuaAuxLib.luaL_checkint(L, 1)
                LuaAuxLib.luaL_argcheck(L, 1 <= u, 1, "interval is empty")
                LuaAPI.lua_pushnumber(L, Math.floor(r * u) + 1) // int between 1 and `u'
            }
            2 -> {
                // lower and upper limits
                val l: Int = LuaAuxLib.luaL_checkint(L, 1)
                val u: Int = LuaAuxLib.luaL_checkint(L, 2)
                LuaAuxLib.luaL_argcheck(L, l <= u, 2, "interval is empty")
                LuaAPI.lua_pushnumber(L, Math.floor(r * (u - l + 1)) + l) // int between `l' and `u'
            }
            else -> {
                return LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("wrong number of arguments"))
            }
        }
        return 1
    }

    private fun math_randomseed(L: lua_State): Int { //srand(luaL_checkint(L, 1));
        rng = Random(LuaAuxLib.luaL_checkint(L, 1).toLong())
        return 0
    }

    private val mathlib: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("abs"), LuaMathLib_delegate("math_abs")),
        luaL_Reg(CharPtr.Companion.toCharPtr("acos"), LuaMathLib_delegate("math_acos")),
        luaL_Reg(CharPtr.Companion.toCharPtr("asin"), LuaMathLib_delegate("math_asin")),
        luaL_Reg(CharPtr.Companion.toCharPtr("atan2"), LuaMathLib_delegate("math_atan2")),
        luaL_Reg(CharPtr.Companion.toCharPtr("atan"), LuaMathLib_delegate("math_atan")),
        luaL_Reg(CharPtr.Companion.toCharPtr("ceil"), LuaMathLib_delegate("math_ceil")),
        luaL_Reg(CharPtr.Companion.toCharPtr("cosh"), LuaMathLib_delegate("math_cosh")),
        luaL_Reg(CharPtr.Companion.toCharPtr("cos"), LuaMathLib_delegate("math_cos")),
        luaL_Reg(CharPtr.Companion.toCharPtr("deg"), LuaMathLib_delegate("math_deg")),
        luaL_Reg(CharPtr.Companion.toCharPtr("exp"), LuaMathLib_delegate("math_exp")),
        luaL_Reg(CharPtr.Companion.toCharPtr("floor"), LuaMathLib_delegate("math_floor")),
        luaL_Reg(CharPtr.Companion.toCharPtr("fmod"), LuaMathLib_delegate("math_fmod")),
        luaL_Reg(CharPtr.Companion.toCharPtr("frexp"), LuaMathLib_delegate("math_frexp")),
        luaL_Reg(CharPtr.Companion.toCharPtr("ldexp"), LuaMathLib_delegate("math_ldexp")),
        luaL_Reg(CharPtr.Companion.toCharPtr("log10"), LuaMathLib_delegate("math_log10")),
        luaL_Reg(CharPtr.Companion.toCharPtr("log"), LuaMathLib_delegate("math_log")),
        luaL_Reg(CharPtr.Companion.toCharPtr("max"), LuaMathLib_delegate("math_max")),
        luaL_Reg(CharPtr.Companion.toCharPtr("min"), LuaMathLib_delegate("math_min")),
        luaL_Reg(CharPtr.Companion.toCharPtr("modf"), LuaMathLib_delegate("math_modf")),
        luaL_Reg(CharPtr.Companion.toCharPtr("pow"), LuaMathLib_delegate("math_pow")),
        luaL_Reg(CharPtr.Companion.toCharPtr("rad"), LuaMathLib_delegate("math_rad")),
        luaL_Reg(CharPtr.Companion.toCharPtr("random"), LuaMathLib_delegate("math_random")),
        luaL_Reg(CharPtr.Companion.toCharPtr("randomseed"), LuaMathLib_delegate("math_randomseed")),
        luaL_Reg(CharPtr.Companion.toCharPtr("sinh"), LuaMathLib_delegate("math_sinh")),
        luaL_Reg(CharPtr.Companion.toCharPtr("sin"), LuaMathLib_delegate("math_sin")),
        luaL_Reg(CharPtr.Companion.toCharPtr("sqrt"), LuaMathLib_delegate("math_sqrt")),
        luaL_Reg(CharPtr.Companion.toCharPtr("tanh"), LuaMathLib_delegate("math_tanh")),
        luaL_Reg(CharPtr.Companion.toCharPtr("tan"), LuaMathLib_delegate("math_tan")),
        luaL_Reg(null, null)
    )

    //
//		 ** Open math library
//
    fun luaopen_math(L: lua_State?): Int {
        LuaAuxLib.luaL_register(L, CharPtr.Companion.toCharPtr(LuaLib.LUA_MATHLIBNAME), mathlib)
        LuaAPI.lua_pushnumber(L!!, PI)
        LuaAPI.lua_setfield(L!!, -2, CharPtr.Companion.toCharPtr("pi"))
        LuaAPI.lua_pushnumber(L!!, CLib.HUGE_VAL)
        LuaAPI.lua_setfield(L!!, -2, CharPtr.Companion.toCharPtr("huge"))
        ///#if LUA_COMPAT_MOD
        LuaAPI.lua_getfield(L!!, -1, CharPtr.Companion.toCharPtr("fmod"))
        LuaAPI.lua_setfield(L!!, -2, CharPtr.Companion.toCharPtr("mod"))
        ///#endif
        return 1
    }

    class LuaMathLib_delegate(private val name: String) : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return if ("math_abs" == name) {
                math_abs(L)
            } else if ("math_acos" == name) {
                math_acos(L)
            } else if ("math_asin" == name) {
                math_asin(L)
            } else if ("math_atan2" == name) {
                math_atan2(L)
            } else if ("math_atan" == name) {
                math_atan(L)
            } else if ("math_ceil" == name) {
                math_ceil(L)
            } else if ("math_cosh" == name) {
                math_cosh(L)
            } else if ("math_cos" == name) {
                math_cos(L)
            } else if ("math_deg" == name) {
                math_deg(L)
            } else if ("math_exp" == name) {
                math_exp(L)
            } else if ("math_floor" == name) {
                math_floor(L)
            } else if ("math_fmod" == name) {
                math_fmod(L)
            } else if ("math_frexp" == name) {
                math_frexp(L)
            } else if ("math_ldexp" == name) {
                math_ldexp(L)
            } else if ("math_log10" == name) {
                math_log10(L)
            } else if ("math_log" == name) {
                math_log(L)
            } else if ("math_max" == name) {
                math_max(L)
            } else if ("math_min" == name) {
                math_min(L)
            } else if ("math_modf" == name) {
                math_modf(L)
            } else if ("math_pow" == name) {
                math_pow(L)
            } else if ("math_rad" == name) {
                math_rad(L)
            } else if ("math_random" == name) {
                math_random(L)
            } else if ("math_randomseed" == name) {
                math_randomseed(L)
            } else if ("math_sinh" == name) {
                math_sinh(L)
            } else if ("math_sin" == name) {
                math_sin(L)
            } else if ("math_sqrt" == name) {
                math_sqrt(L)
            } else if ("math_tanh" == name) {
                math_tanh(L)
            } else if ("math_tan" == name) {
                math_tan(L)
            } else {
                0
            }
        }

    }
}