﻿package kirin

import kotlin.jvm.JvmStatic

//{
object Program {
    @JvmStatic
    fun main(args: Array<String>) {
        var args_ = emptyArray<String?>()
        //args = new String[] {"test/bisect.lua"};
        //args = new String[] {"test/cf.lua"};
        //args = new String[] {"test/echo.lua"};
        //args = new String[] {"test/env.lua"};
        //args = new String[] {"test/factorial.lua"};
        //args = new String[] {"test/fib.lua"};
        //args = new String[] {"test/fibfor.lua"};
        //args = new String[] {"test/globals.lua"}; // not tested
        //args = new String[] {"test/hello.lua"};
        //args = new String[] {"test/life.lua"};
        //args = new String[] {"test/luac.lua"}; // not tested
        //args = new String[] {"test/printf.lua"}; // error
        //args = new String[] {"test/readonly.lua"};
        //args = new String[] {"test/sieve.lua"}; // java not pass
        //args = new String[] {"test/sort.lua"};
        //args = new String[] {"test/table.lua"}; // not tested
        //args = new String[] {"test/trace-calls.lua"}; // not tested
        //args = new String[] {"test/trace-globals.lua"};
        //args = new String[] {"test/xd.lua"}; //not tested
        if (false) {
            LuacProgram.MainLuac(args_)
        } else {
            LuaProgram.MainLua(args_)
        }
    }
}