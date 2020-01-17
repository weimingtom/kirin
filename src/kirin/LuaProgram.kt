package kirin

import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.Lua.lua_Debug
import kirin.Lua.lua_Hook
import kirin.LuaState.lua_State

//
// ** $Id: lua.c,v 1.160.1.2 2007/12/28 15:32:23 roberto Exp $
// ** Lua stand-alone interpreter
// ** See Copyright Notice in lua.h
//
object LuaProgram {
    ///#define lua_c
///#include "lua.h"
///#include "lauxlib.h"
///#include "lualib.h"
    private var globalL: lua_State? = null
    private var progname: CharPtr? = CharPtr.Companion.toCharPtr(LuaConf.LUA_PROGNAME)
    private fun lstop(L: lua_State, ar: lua_Debug) {
        LuaDebug.lua_sethook(L, null, 0, 0)
        LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("interrupted!"))
    }

    private fun laction(i: Int) { //signal(i, SIG_DFL); /* if another SIGINT happens before lstop,
//						  terminate process (default action) */
        LuaDebug.lua_sethook(
            globalL,
            lstop_delegate(),
            Lua.LUA_MASKCALL or Lua.LUA_MASKRET or Lua.LUA_MASKCOUNT,
            1
        )
    }

    private fun print_usage() {
        StreamProxy.Companion.ErrorWrite(
            "usage: " + progname.toString() + " [options] [script [args]].\n" + "Available options are:\n" + "  -e stat  execute string " + LuaConf.LUA_QL(
                "stat"
            ).toString() + "\n" + "  -l name  require library " + LuaConf.LUA_QL("name").toString() + "\n" + "  -i       enter interactive mode after executing " + LuaConf.LUA_QL(
                "script"
            ).toString() + "\n" + "  -v       show version information\n" + "  --       stop handling options\n" + "  -        execute stdin and stop handling options\n"
        )
    }

    private fun l_message(pname: CharPtr?, msg: CharPtr?) {
        if (CharPtr.Companion.isNotEqual(pname, null)) {
            CLib.fprintf(CLib.stderr, CharPtr.Companion.toCharPtr("%s: "), pname)
        }
        CLib.fprintf(CLib.stderr, CharPtr.Companion.toCharPtr("%s\n"), msg)
        CLib.fflush(CLib.stderr)
    }

    private fun report(L: lua_State?, status: Int): Int {
        if (status != 0 && !Lua.lua_isnil(L, -1)) {
            var msg: CharPtr? = Lua.lua_tostring(L, -1)
            if (CharPtr.Companion.isEqual(msg, null)) {
                msg = CharPtr.Companion.toCharPtr("(error object is not a string)")
            }
            l_message(progname, msg)
            Lua.lua_pop(L, 1)
        }
        return status
    }

    private fun traceback(L: lua_State): Int {
        if (LuaAPI.lua_isstring(L, 1) == 0) { // 'message' not a string?
            return 1 // keep it intact
        }
        LuaAPI.lua_getfield(L, Lua.LUA_GLOBALSINDEX, CharPtr.Companion.toCharPtr("debug"))
        if (!Lua.lua_istable(L, -1)) {
            Lua.lua_pop(L, 1)
            return 1
        }
        LuaAPI.lua_getfield(L, -1, CharPtr.Companion.toCharPtr("traceback"))
        if (!Lua.lua_isfunction(L, -1)) {
            Lua.lua_pop(L, 2)
            return 1
        }
        LuaAPI.lua_pushvalue(L, 1) // pass error message
        LuaAPI.lua_pushinteger(L, 2) // skip this function and traceback
        LuaAPI.lua_call(L, 2, 1) // call debug.traceback
        return 1
    }

    private fun docall(L: lua_State, narg: Int, clear: Int): Int {
        val status: Int
        val base_: Int = LuaAPI.lua_gettop(L) - narg // function index
        Lua.lua_pushcfunction(L, traceback_delegate()) // push traceback function
        LuaAPI.lua_insert(L, base_) // put it under chunk and args
        //signal(SIGINT, laction);
        status = LuaAPI.lua_pcall(L, narg, if (clear != 0) 0 else Lua.LUA_MULTRET, base_)
        //signal(SIGINT, SIG_DFL);
        LuaAPI.lua_remove(L, base_) // remove traceback function
        // force a complete garbage collection in case of errors
        if (status != 0) {
            LuaAPI.lua_gc(L, Lua.LUA_GCCOLLECT, 0)
        }
        return status
    }

    private fun print_version() {
        l_message(null, CharPtr.Companion.toCharPtr(Lua.LUA_RELEASE + "  " + Lua.LUA_COPYRIGHT))
    }

    private fun getargs(L: lua_State, argv: Array<String?>?, n: Int): Int {
        val narg: Int
        var i: Int
        val argc = argv!!.size // count total number of arguments
        narg = argc - (n + 1) // number of arguments to the script
        LuaAuxLib.luaL_checkstack(L, narg + 3, CharPtr.Companion.toCharPtr("too many arguments to script"))
        i = n + 1
        while (i < argc) {
            LuaAPI.lua_pushstring(L, CharPtr.Companion.toCharPtr(argv[i]!!))
            i++
        }
        LuaAPI.lua_createtable(L, narg, n + 1)
        i = 0
        while (i < argc) {
            LuaAPI.lua_pushstring(L, CharPtr.Companion.toCharPtr(argv[i]!!))
            LuaAPI.lua_rawseti(L, -2, i - n)
            i++
        }
        return narg
    }

    private fun dofile(L: lua_State, name: CharPtr?): Int {
        val status = if (LuaAuxLib.luaL_loadfile(L, name) != 0 || docall(L, 0, 1) != 0) 1 else 0
        return report(L, status)
    }

    private fun dostring(L: lua_State, s: CharPtr?, name: CharPtr): Int {
        val status = if (LuaAuxLib.luaL_loadbuffer(L, s, CLib.strlen(s), name) != 0 || docall(
                L,
                0,
                1
            ) != 0
        ) 1 else 0 //(uint)
        return report(L, status)
    }

    private fun dolibrary(L: lua_State, name: CharPtr): Int {
        Lua.lua_getglobal(L, CharPtr.Companion.toCharPtr("require"))
        LuaAPI.lua_pushstring(L, name)
        return report(L, docall(L, 1, 1))
    }

    private fun get_prompt(L: lua_State, firstline: Int): CharPtr? {
        var p: CharPtr?
        LuaAPI.lua_getfield(
            L,
            Lua.LUA_GLOBALSINDEX,
            if (firstline != 0) CharPtr.Companion.toCharPtr("_PROMPT") else CharPtr.Companion.toCharPtr("_PROMPT2")
        )
        p = Lua.lua_tostring(L, -1)
        if (CharPtr.Companion.isEqual(p, null)) {
            p = if (firstline != 0) CharPtr.Companion.toCharPtr(LuaConf.LUA_PROMPT) else CharPtr.Companion.toCharPtr(
                LuaConf.LUA_PROMPT2
            )
        }
        Lua.lua_pop(L, 1) // remove global
        return p
    }

    private fun incomplete(L: lua_State, status: Int): Int {
        if (status == Lua.LUA_ERRSYNTAX) {
            val lmsg = IntArray(1) //uint
            val msg: CharPtr? = LuaAPI.lua_tolstring(L, -1, lmsg) //out
            val tp: CharPtr = CharPtr.Companion.plus(msg, lmsg[0] - CLib.strlen(LuaConf.LUA_QL("<eof>")))
            if (CharPtr.Companion.isEqual(CLib.strstr(msg, LuaConf.LUA_QL("<eof>")), tp)) {
                Lua.lua_pop(L, 1)
                return 1
            }
        }
        return 0 // else...
    }

    private fun pushline(L: lua_State, firstline: Int): Int {
        val buffer: CharPtr = CharPtr.Companion.toCharPtr(CharArray(LuaConf.LUA_MAXINPUT))
        val b = CharPtr(buffer)
        val l: Int
        val prmt: CharPtr? = get_prompt(L, firstline)
        if (!LuaConf.lua_readline(L, b, prmt)) {
            return 0 // no input
        }
        l = CLib.strlen(b)
        if (l > 0 && b.get(l - 1) == '\n') { // line ends with newline?
            b.set(l - 1, '\u0000') // remove it
        }
        if (firstline != 0 && b.get(0) == '=') { // first line starts with `=' ?
            LuaAPI.lua_pushfstring(
                L,
                CharPtr.Companion.toCharPtr("return %s"),
                CharPtr.Companion.plus(b, 1)
            ) // change it to `return'
        } else {
            LuaAPI.lua_pushstring(L, b)
        }
        LuaConf.lua_freeline(L, b)
        return 1
    }

    private fun loadline(L: lua_State): Int {
        var status: Int
        LuaAPI.lua_settop(L, 0)
        if (pushline(L, 1) == 0) {
            return -1 // no input
        }
        while (true) {
            // repeat until gets a complete line
            status = LuaAuxLib.luaL_loadbuffer(
                L,
                Lua.lua_tostring(L, 1),
                Lua.lua_strlen(L, 1),
                CharPtr.Companion.toCharPtr("=stdin")
            )
            if (incomplete(L, status) == 0) {
                break // cannot try to add lines?
            }
            if (pushline(L, 0) == 0) { // no more input?
                return -1
            }
            Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("\n")) // add a new line...
            LuaAPI.lua_insert(L, -2) //...between the two lines
            LuaAPI.lua_concat(L, 3) // join them
        }
        LuaConf.lua_saveline(L, 1)
        LuaAPI.lua_remove(L, 1) // remove line
        return status
    }

    private fun dotty(L: lua_State) {
        var status: Int
        val oldprogname: CharPtr? = progname
        progname = null
        while (loadline(L).also { status = it } != -1) {
            if (status == 0) {
                status = docall(L, 0, 0)
            }
            report(L, status)
            if (status == 0 && LuaAPI.lua_gettop(L) > 0) { // any result to print?
                Lua.lua_getglobal(L, CharPtr.Companion.toCharPtr("print"))
                LuaAPI.lua_insert(L, 1)
                if (LuaAPI.lua_pcall(L, LuaAPI.lua_gettop(L) - 1, 0, 0) != 0) {
                    l_message(
                        progname,
                        LuaAPI.lua_pushfstring(
                            L,
                            CharPtr.Companion.toCharPtr("error calling " + LuaConf.LUA_QL("print").toString() + " (%s)"),
                            Lua.lua_tostring(L, -1)
                        )!!
                    )
                }
            }
        }
        LuaAPI.lua_settop(L, 0) // clear stack
        CLib.fputs(CharPtr.Companion.toCharPtr("\n"), CLib.stdout)
        CLib.fflush(CLib.stdout)
        progname = oldprogname
    }

    private fun handle_script(L: lua_State, argv: Array<String?>?, n: Int): Int {
        var status: Int
        var fname: CharPtr?
        val narg = getargs(L, argv, n) // collect arguments
        Lua.lua_setglobal(L, CharPtr.Companion.toCharPtr("arg"))
        fname = CharPtr.Companion.toCharPtr(argv!![n]!!)
        if (CLib.strcmp(
                fname,
                CharPtr.Companion.toCharPtr("-")
            ) == 0 && CLib.strcmp(CharPtr.Companion.toCharPtr(argv[n - 1]!!), CharPtr.Companion.toCharPtr("--")) != 0
        ) {
            fname = null // stdin
        }
        status = LuaAuxLib.luaL_loadfile(L, fname)
        LuaAPI.lua_insert(L, -(narg + 1))
        if (status == 0) {
            status = docall(L, narg, 0)
        } else {
            Lua.lua_pop(L, narg)
        }
        return report(L, status)
    }

    // check that argument has no extra characters at the end
///#define notail(x)	{if ((x)[2] != '\0') return -1;}
    private fun collectargs(
        argv: Array<String?>?,
        pi: IntArray,
        pv: IntArray,
        pe: IntArray
    ): Int { //ref - ref - ref
        var i: Int
        i = 1
        while (i < argv!!.size) {
            if (argv[i]!![0] != '-') { // not an option?
                return i
            }
            val ch = argv[i]!![1]
            when (ch) {
                '-' -> {
                    if (argv[i]!!.length != 2) {
                        return -1
                    }
                    return if (i + 1 >= argv.size) i + 1 else 0
                }
                '\u0000' -> {
                    return i
                }
                'i' -> {
                    if (argv[i]!!.length != 2) {
                        return -1
                    }
                    pi[0] = 1
                    if (argv[i]!!.length != 2) {
                        return -1
                    }
                    pv[0] = 1
                }
                'v' -> {
                    if (argv[i]!!.length != 2) {
                        return -1
                    }
                    pv[0] = 1
                }
                'e' -> {
                    pe[0] = 1
                    if (argv[i]!!.length == 2) {
                        i++
                        if (argv[i] == null) {
                            return -1
                        }
                    }
                }
                'l' -> {
                    if (argv[i]!!.length == 2) {
                        i++
                        if (i >= argv.size) {
                            return -1
                        }
                    }
                }
                else -> {
                    return -1 // invalid option
                }
            }
            i++
        }
        return 0
    }

    private fun runargs(L: lua_State, argv: Array<String?>?, n: Int): Int {
        var i: Int
        i = 1
        while (i < n) {
            if (argv!![i] == null) {
                i++
                continue
            }
            LuaLimits.lua_assert(argv[i]!![0] == '-')
            val ch = argv[i]!![1]
            when (ch) {
                'e' -> {
                    var chunk = argv[i]!!.substring(2)
                    if (chunk == "") {
                        chunk = argv[++i]!!
                    }
                    LuaLimits.lua_assert(chunk != null)
                    if (dostring(
                            L,
                            CharPtr.Companion.toCharPtr(chunk),
                            CharPtr.Companion.toCharPtr("=(command line)")
                        ) != 0
                    ) {
                        return 1
                    }
                }
                'l' -> {
                    var filename = argv[i]!!.substring(2)
                    if (filename == "") {
                        filename = argv[++i]!!
                    }
                    LuaLimits.lua_assert(filename != null)
                    if (dolibrary(L, CharPtr.Companion.toCharPtr(filename)) != 0) {
                        return 1 // stop if file fails
                    }
                }
                else -> {
                }
            }
            i++
        }
        return 0
    }

    private fun handle_luainit(L: lua_State): Int {
        val init: CharPtr? = CLib.getenv(CharPtr.Companion.toCharPtr(LuaConf.LUA_INIT))
        return if (CharPtr.Companion.isEqual(init, null)) {
            0 // status OK
        } else if (init!!.get(0) == '@') {
            dofile(L, CharPtr.Companion.plus(init, 1))
        } else {
            dostring(L, init, CharPtr.Companion.toCharPtr("=" + LuaConf.LUA_INIT))
        }
    }

    private fun pmain(L: lua_State): Int {
        val s = LuaAPI.lua_touserdata(L, 1) as SmainLua
        val argv = s.argv
        val script: Int
        val has_i = IntArray(1)
        val has_v = IntArray(1)
        val has_e = IntArray(1)
        has_i[0] = 0
        has_v[0] = 0
        has_e[0] = 0
        globalL = L
        if (argv!!.size > 0 && argv[0] != "") {
            progname = CharPtr.Companion.toCharPtr(argv[0]!!)
        }
        LuaAPI.lua_gc(L, Lua.LUA_GCSTOP, 0) // stop collector during initialization
        LuaInit.luaL_openlibs(L) // open libraries
        LuaAPI.lua_gc(L, Lua.LUA_GCRESTART, 0)
        s.status = handle_luainit(L)
        if (s.status != 0) {
            return 0
        }
        script = collectargs(argv, has_i, has_v, has_e) //ref - ref - ref
        if (script < 0) { // invalid args?
            print_usage()
            s.status = 1
            return 0
        }
        if (has_v[0] != 0) {
            print_version()
        }
        s.status = runargs(L, argv, if (script > 0) script else s.argc)
        if (s.status != 0) {
            return 0
        }
        if (script != 0) {
            s.status = handle_script(L, argv, script)
        }
        if (s.status != 0) {
            return 0
        }
        if (has_i[0] != 0) {
            dotty(L)
        } else if (script == 0 && has_e[0] == 0 && has_v[0] == 0) {
            if (LuaConf.lua_stdin_is_tty() != 0) {
                print_version()
                dotty(L)
            } else {
                dofile(L, null) // executes stdin as a file
            }
        }
        return 0
    }

    fun MainLua(args: Array<String?>?): Int { // prepend the exe name to the arg list as it's done in C
// so that we don't have to change any of the args indexing
// code above
        var args = args
        val newargs =
            arrayOfNulls<String>((args?.size ?: 0) + 1)
        newargs[0] = "lua" //Assembly.GetExecutingAssembly().Location);
        for (idx in args!!.indices) {
            newargs[idx + 1] = args[idx]
        }
        args = newargs
        val status: Int
        val s = SmainLua()
        val L: lua_State? = Lua.lua_open() // create state
        if (L == null) {
            l_message(
                CharPtr.Companion.toCharPtr(args[0]!!),
                CharPtr.Companion.toCharPtr("cannot create state: not enough memory")
            )
            return CLib.EXIT_FAILURE
        }
        s.argc = args.size
        s.argv = args
        status = LuaAPI.lua_cpcall(L, pmain_delegate(), s)
        report(L, status)
        LuaState.lua_close(L)
        return if (status != 0 || s.status != 0) CLib.EXIT_FAILURE else CLib.EXIT_SUCCESS
    }

    class lstop_delegate : lua_Hook {
        override fun exec(L: lua_State, ar: lua_Debug) {
            lstop(L, ar)
        }
    }

    class SmainLua {
        var argc = 0
        var argv: Array<String?>? = null
        var status = 0
    }

    class pmain_delegate : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return pmain(L)
        }
    }

    class traceback_delegate : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return traceback(L)
        }
    }
}