package kirin
import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.LuaObject.TValue
import kirin.LuaObject.Proto
import kirin.LuaOpCodes.OpCode
import kirin.Lua.lua_Writer


//
// ** $Id: luac.c,v 1.54 2006/06/02 17:37:11 lhf Exp $
// ** Lua compiler (saves bytecodes to files; also list bytecodes)
// ** See Copyright Notice in lua.h
//
//using Instruction = System.UInt32;
object LuacProgram {
    ///#include <errno.h>
///#include <stdio.h>
///#include <stdlib.h>
///#include <string.h>
///#define luac_c
///#define LUA_CORE
///#include "lua.h"
///#include "lauxlib.h"
///#include "ldo.h"
///#include "lfunc.h"
///#include "lmem.h"
///#include "lobject.h"
///#include "lopcodes.h"
///#include "lstring.h"
///#include "lundump.h"
    private val PROGNAME: CharPtr = CharPtr.Companion.toCharPtr("luac") // default program name
    private val OUTPUT: CharPtr =
        CharPtr.Companion.toCharPtr(PROGNAME.toString() + ".out") // default output file
    private var listing = 0 // list bytecodes?
    private var dumping = 1 // dump bytecodes?
    private var stripping = 0 // strip debug information?
    private val Output: CharPtr = OUTPUT // default output file name
    private var output: CharPtr? = Output // actual output file name
    private var progname: CharPtr = PROGNAME // actual program name
    private fun fatal(message: CharPtr?) {
        CLib.fprintf(CLib.stderr, CharPtr.Companion.toCharPtr("%s: %s\n"), progname, message)
        System.exit(CLib.EXIT_FAILURE)
    }

    private fun cannot(what: CharPtr) {
        CLib.fprintf(
            CLib.stderr,
            CharPtr.Companion.toCharPtr("%s: cannot %s %s: %s\n"),
            progname,
            what,
            output,
            CLib.strerror(CLib.errno())
        )
        System.exit(CLib.EXIT_FAILURE)
    }

    private fun usage(message: CharPtr) {
        if (message.get(0) == '-') {
            CLib.fprintf(
                CLib.stderr,
                CharPtr.Companion.toCharPtr("%s: unrecognized option " + LuaConf.getLUA_QS() + "\n"),
                progname,
                message
            )
        } else {
            CLib.fprintf(CLib.stderr, CharPtr.Companion.toCharPtr("%s: %s\n"), progname, message)
        }
        CLib.fprintf(
            CLib.stderr,
            CharPtr.Companion.toCharPtr(
                "usage: %s [options] [filenames].\n" + "Available options are:\n" + "  -        process stdin\n" + "  -l       list\n" + "  -o name  output to file " + LuaConf.LUA_QL(
                    "name"
                ) + " (default is \"%s\")\n" + "  -p       parse only\n" + "  -s       strip debug information\n" + "  -v       show version information\n" + "  --       stop handling options\n"
            ),
            progname,
            Output
        )
        System.exit(CLib.EXIT_FAILURE)
    }

    ///#define	IS(s)	(strcmp(argv[i],s)==0)
    private fun doargs(argc: Int, argv: Array<String?>?): Int {
        var i: Int
        var version = 0
        if (argv!!.size > 0 && argv[0] != "") {
            progname = CharPtr.Companion.toCharPtr(argv[0]!!)!!
        }
        i = 1
        while (i < argc) {
            if (argv[i]!![0] != '-') { // end of options; keep it
                break
            } else if (CLib.strcmp(
                    CharPtr.Companion.toCharPtr(argv[i]!!),
                    CharPtr.Companion.toCharPtr("--")
                ) == 0
            ) { // end of options; skip it
                ++i
                if (version != 0) {
                    ++version
                }
                break
            } else if (CLib.strcmp(
                    CharPtr.Companion.toCharPtr(argv[i]!!),
                    CharPtr.Companion.toCharPtr("-")
                ) == 0
            ) { // end of options; use stdin
                break
            } else if (CLib.strcmp(
                    CharPtr.Companion.toCharPtr(argv[i]!!),
                    CharPtr.Companion.toCharPtr("-l")
                ) == 0
            ) { // list
                ++listing
            } else if (CLib.strcmp(
                    CharPtr.Companion.toCharPtr(argv[i]!!),
                    CharPtr.Companion.toCharPtr("-o")
                ) == 0
            ) { // output file
                output = CharPtr.Companion.toCharPtr(argv[++i]!!)
                if (CharPtr.Companion.isEqual(output, null) || output!!.get(0).toInt() == 0) {
                    usage(CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("-o").toString() + " needs argument"))
                }
                if (CLib.strcmp(CharPtr.Companion.toCharPtr(argv[i]!!), CharPtr.Companion.toCharPtr("-")) == 0) {
                    output = null
                }
            } else if (CLib.strcmp(
                    CharPtr.Companion.toCharPtr(argv[i]!!),
                    CharPtr.Companion.toCharPtr("-p")
                ) == 0
            ) { // parse only
                dumping = 0
            } else if (CLib.strcmp(
                    CharPtr.Companion.toCharPtr(argv[i]!!),
                    CharPtr.Companion.toCharPtr("-s")
                ) == 0
            ) { // strip debug information
                stripping = 1
            } else if (CLib.strcmp(
                    CharPtr.Companion.toCharPtr(argv[i]!!),
                    CharPtr.Companion.toCharPtr("-v")
                ) == 0
            ) { // show version
                ++version
            } else { // unknown option
                usage(CharPtr.Companion.toCharPtr(argv[i]!!))
            }
            i++
        }
        if (i == argc && (listing != 0 || dumping == 0)) {
            dumping = 0
            argv[--i] = Output.toString()
        }
        if (version != 0) {
            CLib.printf(CharPtr.Companion.toCharPtr("%s  %s\n"), Lua.LUA_RELEASE, Lua.LUA_COPYRIGHT)
            if (version == argc - 1) {
                System.exit(CLib.EXIT_SUCCESS)
            }
        }
        return i
    }

    private fun toproto(L: lua_State, i: Int): Proto? {
        return LuaObject.clvalue(TValue.Companion.plus(L.top!!, i))!!.l.p
    }

    private fun combine(L: lua_State, n: Int): Proto? {
        return if (n == 1) {
            toproto(L, -1)
        } else {
            var i: Int
            var pc: Int
            val f: Proto = LuaFunc.luaF_newproto(L)!!
            LuaObject.setptvalue2s(L, L.top!!, f)
            LuaDo.incr_top(L)
            f.source = LuaString.luaS_newliteral(L, CharPtr.Companion.toCharPtr("=($PROGNAME)"))
            f.maxstacksize = 1
            pc = 2 * n + 1
            //UInt32[]
//Instruction[]
//UInt32
//Instruction
            f.code = LuaMem.luaM_newvector_long(L, pc, ClassType(ClassType.Companion.TYPE_LONG))
            f.sizecode = pc
            f.p = LuaMem.luaM_newvector_Proto(L, n, ClassType(ClassType.Companion.TYPE_PROTO))
            f.sizep = n
            pc = 0
            i = 0
            while (i < n) {
                f.p!![i] = toproto(L, i - n - 1)
                f.code!![pc++] = LuaOpCodes.CREATE_ABx(OpCode.OP_CLOSURE, 0, i) as Long //uint
                f.code!![pc++] = LuaOpCodes.CREATE_ABC(OpCode.OP_CALL, 0, 1, 1) as Long //uint
                i++
            }
            f.code!![pc++] = LuaOpCodes.CREATE_ABC(OpCode.OP_RETURN, 0, 1, 0) as Long //uint
            f
        }
    }

    //FIXME:StreamProxy/*object*/ u
    private fun writer(L: lua_State, p: CharPtr, size: Int, u: Any): Int { //uint
//UNUSED(L);
        return if (CLib.fwrite(p, size, 1, u as StreamProxy) != 1 && size != 0) 1 else 0
    }

    private fun pmain(L: lua_State): Int {
        val s = LuaAPI.lua_touserdata(L, 1) as SmainLuac
        val argc = s.argc
        val argv = s.argv
        val f: Proto?
        var i: Int
        if (LuaAPI.lua_checkstack(L, argc) == 0) {
            fatal(CharPtr.Companion.toCharPtr("too many input files"))
        }
        i = 0
        while (i < argc) {
            val filename: CharPtr? = if (CLib.strcmp(
                    CharPtr.Companion.toCharPtr(argv!![i]!!),
                    CharPtr.Companion.toCharPtr("-")
                ) == 0
            ) null else CharPtr.Companion.toCharPtr(argv[i]!!)
            if (LuaAuxLib.luaL_loadfile(L, filename) != 0) {
                fatal(Lua.lua_tostring(L, -1))
            }
            i++
        }
        f = combine(L, argc)
        if (listing != 0) {
            LuaPrint.luaU_print(f, if (listing > 1) 1 else 0)
        }
        if (dumping != 0) {
            val D: StreamProxy = if (CharPtr.Companion.isEqual(
                    output,
                    null
                )
            ) CLib.stdout else CLib.fopen(output, CharPtr.Companion.toCharPtr("wb"))!!
            if (D == null) {
                cannot(CharPtr.Companion.toCharPtr("open"))
            }
            LuaLimits.lua_lock(L)
            LuaDump.luaU_dump(L, f!!, writer_delegate(), D, stripping)
            LuaLimits.lua_unlock(L)
            if (CLib.ferror(D) != 0) {
                cannot(CharPtr.Companion.toCharPtr("write"))
            }
            if (CLib.fclose(D) != 0) {
                cannot(CharPtr.Companion.toCharPtr("close"))
            }
        }
        return 0
    }

    fun MainLuac(args: Array<String?>?): Int { // prepend the exe name to the arg list as it's done in C
// so that we don't have to change any of the args indexing
// code above
        var args = args
        val newargs =
            arrayOfNulls<String>((args?.size ?: 0) + 1)
        newargs[0] = "luac" //Assembly.GetExecutingAssembly().Location);
        for (idx in args!!.indices) {
            newargs[idx + 1] = args[idx]
        }
        args = newargs
        val L: lua_State
        val s = SmainLuac()
        var argc = args.size
        val i = doargs(argc, args)
        //newargs.RemoveRange(0, i);
        val newargs2 = arrayOfNulls<String>(newargs.size - i)
        for (idx in newargs.size - i until newargs.size) {
            newargs2[idx - (newargs.size - i)] = newargs[idx]
        }
        argc -= i
        args = newargs2 //(string[])newargs.ToArray();
        if (argc <= 0) {
            usage(CharPtr.Companion.toCharPtr("no input files given"))
        }
        L = Lua.lua_open()!!
        if (L == null) {
            fatal(CharPtr.Companion.toCharPtr("not enough memory for state"))
        }
        s.argc = argc
        s.argv = args
        if (LuaAPI.lua_cpcall(L, pmain_delegate(), s) != 0) {
            fatal(Lua.lua_tostring(L, -1))
        }
        LuaState.lua_close(L)
        return CLib.EXIT_SUCCESS
    }

    class writer_delegate : lua_Writer {
        override fun exec(L: lua_State, p: CharPtr, sz: Int, ud: Any): Int { //uint
//FIXME:StreamProxy/*object*/ u
            return writer(L, p, sz, ud)
        }
    }

    class SmainLuac {
        var argc = 0
        var argv: Array<String?>? = null
        var status = 0
    }

    class pmain_delegate : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return pmain(L)
        }
    }
}