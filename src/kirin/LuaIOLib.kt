package kirin
import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import java.lang.Exception
import kirin.LuaAuxLib.luaL_Reg
import kirin.LuaAuxLib.luaL_Buffer

//
// ** $Id: liolib.c,v 2.73.1.3 2008/01/18 17:47:43 roberto Exp $
// ** Standard I/O (and system) library
// ** See Copyright Notice in lua.h
//
//using lua_Number = System.Double;
//using lua_Integer = System.Int32;
object LuaIOLib {
    const val IO_INPUT = 1
    const val IO_OUTPUT = 2
    private val fnames = arrayOf("input", "output")
    private fun pushresult(L: lua_State, i: Int, filename: CharPtr?): Int {
        val en: Int = CLib.errno() // calls to Lua API may change this value
        return if (i != 0) {
            LuaAPI.lua_pushboolean(L, 1)
            1
        } else {
            LuaAPI.lua_pushnil(L)
            if (CharPtr.Companion.isNotEqual(filename, null)) {
                LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("%s: %s"), filename, CLib.strerror(en))
            } else {
                LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("%s"), CLib.strerror(en))
            }
            LuaAPI.lua_pushinteger(L, en)
            3
        }
    }

    private fun fileerror(L: lua_State, arg: Int, filename: CharPtr?) {
        LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("%s: %s"), filename, CLib.strerror(CLib.errno()))
        LuaAuxLib.luaL_argerror(L, arg, Lua.lua_tostring(L, -1))
    }

    fun tofilep(L: lua_State?): FilePtr? {
        return LuaAuxLib.luaL_checkudata(L, 1, CharPtr.Companion.toCharPtr(LuaLib.LUA_FILEHANDLE)) as FilePtr
    }

    private fun io_type(L: lua_State): Int {
        val ud: Any?
        LuaAuxLib.luaL_checkany(L, 1)
        ud = LuaAPI.lua_touserdata(L, 1)
        LuaAPI.lua_getfield(L, Lua.LUA_REGISTRYINDEX, CharPtr.Companion.toCharPtr(LuaLib.LUA_FILEHANDLE))
        if (ud == null || LuaAPI.lua_getmetatable(L, 1) == 0 || LuaAPI.lua_rawequal(L, -2, -1) == 0) {
            LuaAPI.lua_pushnil(L) // not a file
        } else if ((ud as? FilePtr)!!.file == null) {
            Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("closed file"))
        } else {
            Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("file"))
        }
        return 1
    }

    private fun tofile(L: lua_State): StreamProxy? {
        val f = tofilep(L)
        if (f!!.file == null) {
            LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("attempt to use a closed file"))
        }
        return f.file
    }

    //
//		 ** When creating file files, always creates a `closed' file file
//		 ** before opening the actual file; so, if there is a memory error, the
//		 ** file is not left opened.
//
    private fun newfile(L: lua_State): FilePtr {
        val pf = LuaAPI.lua_newuserdata(L, ClassType(ClassType.Companion.TYPE_FILEPTR)) as FilePtr //FilePtr
        pf.file = null // file file is currently `closed'
        LuaAuxLib.luaL_getmetatable(L, CharPtr.Companion.toCharPtr(LuaLib.LUA_FILEHANDLE))
        LuaAPI.lua_setmetatable(L, -2)
        return pf
    }

    //
//		 ** function to (not) close the standard files stdin, stdout, and stderr
//
    private fun io_noclose(L: lua_State): Int {
        LuaAPI.lua_pushnil(L)
        Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("cannot close standard file"))
        return 2
    }

    //
//		 ** function to close 'popen' files
//
    private fun io_pclose(L: lua_State): Int {
        val p = tofilep(L)
        val ok = if (LuaConf.lua_pclose(L, p!!.file) == 0) 1 else 0
        p.file = null
        return pushresult(L, ok, null)
    }

    //
//		 ** function to close regular files
//
    private fun io_fclose(L: lua_State): Int {
        val p = tofilep(L)
        val ok = if (CLib.fclose(p!!.file) == 0) 1 else 0
        p.file = null
        return pushresult(L, ok, null)
    }

    private fun aux_close(L: lua_State): Int {
        LuaAPI.lua_getfenv(L, 1)
        LuaAPI.lua_getfield(L, -1, CharPtr.Companion.toCharPtr("__close"))
        return LuaAPI.lua_tocfunction(L, -1)!!.exec(L)
    }

    private fun io_close(L: lua_State): Int {
        if (Lua.lua_isnone(L, 1)) {
            LuaAPI.lua_rawgeti(L, Lua.LUA_ENVIRONINDEX, IO_OUTPUT)
        }
        tofile(L) // make sure argument is a file
        return aux_close(L)
    }

    private fun io_gc(L: lua_State): Int {
        val f: StreamProxy? = tofilep(L)!!.file
        // ignore closed files
        if (f != null) {
            aux_close(L)
        }
        return 0
    }

    private fun io_tostring(L: lua_State): Int {
        val f: StreamProxy? = tofilep(L)!!.file
        if (f == null) {
            Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("file (closed)"))
        } else {
            LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("file (%p)"), f)
        }
        return 1
    }

    private fun io_open(L: lua_State): Int {
        val filename: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        val mode: CharPtr? = LuaAuxLib.luaL_optstring(L, 2, CharPtr.Companion.toCharPtr("r"))
        val pf = newfile(L)
        pf.file = CLib.fopen(filename, mode)
        return if (pf.file == null) pushresult(L, 0, filename) else 1
    }

    //
//		 ** this function has a separated environment, which defines the
//		 ** correct __close for 'popen' files
//
    private fun io_popen(L: lua_State): Int {
        val filename: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        val mode: CharPtr? = LuaAuxLib.luaL_optstring(L, 2, CharPtr.Companion.toCharPtr("r"))
        val pf = newfile(L)
        pf.file = LuaConf.lua_popen(L, filename, mode)
        return if (pf.file == null) pushresult(L, 0, filename) else 1
    }

    private fun io_tmpfile(L: lua_State): Int {
        val pf = newfile(L)
        pf.file = CLib.tmpfile()
        return if (pf.file == null) pushresult(L, 0, null) else 1
    }

    private fun getiofile(L: lua_State, findex: Int): StreamProxy? {
        val f: StreamProxy?
        LuaAPI.lua_rawgeti(L, Lua.LUA_ENVIRONINDEX, findex)
        val tempVar: Any? = LuaAPI.lua_touserdata(L, -1)
        f = (tempVar as? FilePtr)!!.file
        if (f == null) {
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr("standard %s file is closed"),
                fnames[findex - 1]
            )
        }
        return f
    }

    private fun g_iofile(L: lua_State, f: Int, mode: CharPtr): Int {
        if (!Lua.lua_isnoneornil(L, 1.0)) {
            val filename: CharPtr? = Lua.lua_tostring(L, 1)
            if (CharPtr.Companion.isNotEqual(filename, null)) {
                val pf = newfile(L)
                pf.file = CLib.fopen(filename, mode)
                if (pf.file == null) {
                    fileerror(L, 1, filename)
                }
            } else {
                tofile(L) // check that it's a valid file file
                LuaAPI.lua_pushvalue(L, 1)
            }
            LuaAPI.lua_rawseti(L, Lua.LUA_ENVIRONINDEX, f)
        }
        // return current value
        LuaAPI.lua_rawgeti(L, Lua.LUA_ENVIRONINDEX, f)
        return 1
    }

    private fun io_input(L: lua_State): Int {
        return g_iofile(L, IO_INPUT, CharPtr.Companion.toCharPtr("r"))
    }

    private fun io_output(L: lua_State): Int {
        return g_iofile(L, IO_OUTPUT, CharPtr.Companion.toCharPtr("w"))
    }

    private fun aux_lines(L: lua_State, idx: Int, toclose: Int) {
        LuaAPI.lua_pushvalue(L, idx)
        LuaAPI.lua_pushboolean(L, toclose) // close/not close file when finished
        LuaAPI.lua_pushcclosure(L, LuaIOLib_delegate("io_readline"), 2)
    }

    private fun f_lines(L: lua_State): Int {
        tofile(L) // check that it's a valid file file
        aux_lines(L, 1, 0)
        return 1
    }

    private fun io_lines(L: lua_State): Int {
        return if (Lua.lua_isnoneornil(L, 1.0)) { // no arguments?
// will iterate over default input
            LuaAPI.lua_rawgeti(L, Lua.LUA_ENVIRONINDEX, IO_INPUT)
            f_lines(L)
        } else {
            val filename: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
            val pf = newfile(L)
            pf.file = CLib.fopen(filename, CharPtr.Companion.toCharPtr("r"))
            if (pf.file == null) {
                fileerror(L, 1, filename)
            }
            aux_lines(L, LuaAPI.lua_gettop(L), 1)
            1
        }
    }

    //
//		 ** {======================================================
//		 ** READ
//		 ** =======================================================
//
    private fun read_number(L: lua_State, f: StreamProxy?): Int { //lua_Number d;
        val parms = arrayOf(0.0 as Any)
        return if (CLib.fscanf(f, CharPtr.Companion.toCharPtr(LuaConf.LUA_NUMBER_SCAN), *parms) == 1) {
            LuaAPI.lua_pushnumber(L, (parms[0] as Double).toDouble())
            1
        } else {
            0 // read fails
        }
    }

    private fun test_eof(L: lua_State, f: StreamProxy?): Int {
        val c: Int = CLib.getc(f)
        CLib.ungetc(c, f)
        LuaAPI.lua_pushlstring(L, null, 0)
        return if (c != CLib.EOF) 1 else 0
    }

    private fun read_line(L: lua_State, f: StreamProxy?): Int {
        val b = luaL_Buffer()
        LuaAuxLib.luaL_buffinit(L, b)
        while (true) {
            var l: Int //uint
            val p: CharPtr = LuaAuxLib.luaL_prepbuffer(b)
            if (CharPtr.Companion.isEqual(CLib.fgets(p, f), null)) { // eof?
                LuaAuxLib.luaL_pushresult(b) // close buffer
                return if (LuaAPI.lua_objlen(L, -1) > 0) 1 else 0 // check whether read something
            }
            l = CLib.strlen(p) //uint
            if (l == 0 || p.get(l - 1) != '\n') {
                LuaAuxLib.luaL_addsize(b, l)
            } else {
                LuaAuxLib.luaL_addsize(b, (l - 1)) // do not include `eol'
                LuaAuxLib.luaL_pushresult(b) // close buffer
                return 1 // read at least an `eol'
            }
        }
    }

    private fun read_chars(L: lua_State, f: StreamProxy?, n: Long): Int { //uint
        var n = n
        var rlen: Long // how much to read  - uint
        var nr: Int // number of chars actually read  - uint
        val b = luaL_Buffer()
        LuaAuxLib.luaL_buffinit(L, b)
        rlen = LuaConf.LUAL_BUFFERSIZE.toLong() // try to read that much each time
        do {
            val p: CharPtr = LuaAuxLib.luaL_prepbuffer(b)
            if (rlen > n) {
                rlen = n // cannot read more than asked
            }
            nr = CLib.fread(
                p,
                CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_CHAR)),
                rlen.toInt(),
                f
            ) //typeof(char) - uint
            LuaAuxLib.luaL_addsize(b, nr)
            n -= nr.toLong() // still have to read `n' chars
        } while (n > 0 && nr.toLong() == rlen) // until end of count or eof
        LuaAuxLib.luaL_pushresult(b) // close buffer
        return if (n == 0L || LuaAPI.lua_objlen(L, -1) > 0) 1 else 0
    }

    private fun g_read(L: lua_State, f: StreamProxy?, first: Int): Int {
        var nargs: Int = LuaAPI.lua_gettop(L) - 1
        var success: Int
        var n: Int
        CLib.clearerr(f)
        if (nargs == 0) { // no arguments?
            success = read_line(L, f)
            n = first + 1 // to return 1 result
        } else { // ensure stack space for all results and for auxlib's buffer
            LuaAuxLib.luaL_checkstack(L, nargs + Lua.LUA_MINSTACK, CharPtr.Companion.toCharPtr("too many arguments"))
            success = 1
            n = first
            while (nargs-- != 0 && success != 0) {
                success = if (LuaAPI.lua_type(L, n) == Lua.LUA_TNUMBER) {
                    val l = LuaAPI.lua_tointeger(L, n) as Int //uint - uint
                    if (l == 0) test_eof(L, f) else read_chars(L, f, l.toLong())
                } else {
                    val p: CharPtr? = Lua.lua_tostring(L, n)
                    LuaAuxLib.luaL_argcheck(
                        L,
                        CharPtr.Companion.isNotEqual(p, null) && p!!.get(0) == '*',
                        n,
                        "invalid option"
                    )
                    when (p!!.get(1)) {
                        'n' -> {
                            // number
                            read_number(L, f)
                        }
                        'l' -> {
                            // line
                            read_line(L, f)
                        }
                        'a' -> {
                            // file
                            read_chars(
                                L,
                                f,
                                ((0 as Int).inv() and -0x1).toLong()
                            ) // read MAX_uint chars  - ~((uint)0
                            1 // always success
                        }
                        else -> {
                            return LuaAuxLib.luaL_argerror(L, n, CharPtr.Companion.toCharPtr("invalid format"))
                        }
                    }
                }
                n++
            }
        }
        if (CLib.ferror(f) != 0) {
            return pushresult(L, 0, null)
        }
        if (success == 0) {
            Lua.lua_pop(L, 1) // remove last result
            LuaAPI.lua_pushnil(L) // push nil instead
        }
        return n - first
    }

    private fun io_read(L: lua_State): Int {
        return g_read(L, getiofile(L, IO_INPUT), 1)
    }

    private fun f_read(L: lua_State): Int {
        return g_read(L, tofile(L), 2)
    }

    private fun io_readline(L: lua_State): Int {
        val tempVar: Any? = LuaAPI.lua_touserdata(L, Lua.lua_upvalueindex(1))
        val f: StreamProxy? = (tempVar as? FilePtr)!!.file
        val sucess: Int
        if (f == null) { // file is already closed?
            LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("file is already closed"))
        }
        sucess = read_line(L, f)
        if (CLib.ferror(f) != 0) {
            return LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("%s"), CLib.strerror(CLib.errno()))
        }
        return if (sucess != 0) {
            1
        } else { // EOF
            if (LuaAPI.lua_toboolean(L, Lua.lua_upvalueindex(2)) != 0) { // generator created file?
                LuaAPI.lua_settop(L, 0)
                LuaAPI.lua_pushvalue(L, Lua.lua_upvalueindex(1))
                aux_close(L) // close it
            }
            0
        }
    }

    // }======================================================
    private fun g_write(L: lua_State, f: StreamProxy?, arg: Int): Int {
        var arg = arg
        var nargs: Int = LuaAPI.lua_gettop(L) - 1
        var status = 1
        while (nargs-- != 0) {
            status =
                if (LuaAPI.lua_type(L, arg) == Lua.LUA_TNUMBER) { // optimization: could be done exactly as for strings
                    if (status != 0 && CLib.fprintf(
                            f,
                            CharPtr.Companion.toCharPtr(LuaConf.LUA_NUMBER_FMT),
                            LuaAPI.lua_tonumber(L, arg)
                        ) > 0
                    ) 1 else 0
                } else {
                    val l = IntArray(1) //uint
                    val s: CharPtr = LuaAuxLib.luaL_checklstring(L, arg, l) //out
                    if (status != 0 && CLib.fwrite(
                            s,
                            CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_CHAR)),
                            l[0],
                            f
                        ) == l[0]
                    ) 1 else 0 //typeof(char)
                }
            arg++
        }
        return pushresult(L, status, null)
    }

    private fun io_write(L: lua_State): Int {
        return g_write(L, getiofile(L, IO_OUTPUT), 1)
    }

    private fun f_write(L: lua_State): Int {
        return g_write(L, tofile(L), 2)
    }

    private fun f_seek(L: lua_State): Int {
        val mode = intArrayOf(
            CLib.SEEK_SET,
            CLib.SEEK_CUR,
            CLib.SEEK_END
        )
        val modenames: Array<CharPtr?> = arrayOf<CharPtr?>(
            CharPtr.Companion.toCharPtr("set"),
            CharPtr.Companion.toCharPtr("cur"),
            CharPtr.Companion.toCharPtr("end"),
            null
        )
        val f: StreamProxy? = tofile(L)
        var op: Int = LuaAuxLib.luaL_checkoption(L, 2, CharPtr.Companion.toCharPtr("cur"), modenames)
        val offset: Long = LuaAuxLib.luaL_optlong(L, 3, 0)
        op = CLib.fseek(f, offset, mode[op])
        return if (op != 0) {
            pushresult(L, 0, null) // error
        } else {
            LuaAPI.lua_pushinteger(L, CLib.ftell(f))
            1
        }
    }

    private fun f_setvbuf(L: lua_State): Int {
        val modenames: Array<CharPtr?> = arrayOf<CharPtr?>(
            CharPtr.Companion.toCharPtr("no"),
            CharPtr.Companion.toCharPtr("full"),
            CharPtr.Companion.toCharPtr("line"),
            null
        )
        val mode = intArrayOf(CLib._IONBF, CLib._IOFBF, CLib._IOLBF)
        val f: StreamProxy? = tofile(L)
        val op: Int = LuaAuxLib.luaL_checkoption(L, 2, null, modenames)
        val sz: Int = LuaAuxLib.luaL_optinteger(L, 3, LuaConf.LUAL_BUFFERSIZE) //lua_Integer - Int32
        val res: Int = CLib.setvbuf(f, null, mode[op], sz) //uint
        return pushresult(L, if (res == 0) 1 else 0, null)
    }

    private fun io_flush(L: lua_State): Int {
        var result = 1
        try {
            getiofile(L, IO_OUTPUT)!!.Flush()
        } catch (e: Exception) {
            result = 0
        }
        return pushresult(L, result, null)
    }

    private fun f_flush(L: lua_State): Int {
        var result = 1
        try {
            tofile(L)!!.Flush()
        } catch (e: Exception) {
            result = 0
        }
        return pushresult(L, result, null)
    }

    private val iolib: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("close"), LuaIOLib_delegate("io_close")),
        luaL_Reg(CharPtr.Companion.toCharPtr("flush"), LuaIOLib_delegate("io_flush")),
        luaL_Reg(CharPtr.Companion.toCharPtr("input"), LuaIOLib_delegate("io_input")),
        luaL_Reg(CharPtr.Companion.toCharPtr("lines"), LuaIOLib_delegate("io_lines")),
        luaL_Reg(CharPtr.Companion.toCharPtr("open"), LuaIOLib_delegate("io_open")),
        luaL_Reg(CharPtr.Companion.toCharPtr("output"), LuaIOLib_delegate("io_output")),
        luaL_Reg(CharPtr.Companion.toCharPtr("popen"), LuaIOLib_delegate("io_popen")),
        luaL_Reg(CharPtr.Companion.toCharPtr("read"), LuaIOLib_delegate("io_read")),
        luaL_Reg(CharPtr.Companion.toCharPtr("tmpfile"), LuaIOLib_delegate("io_tmpfile")),
        luaL_Reg(CharPtr.Companion.toCharPtr("type"), LuaIOLib_delegate("io_type")),
        luaL_Reg(CharPtr.Companion.toCharPtr("write"), LuaIOLib_delegate("io_write")),
        luaL_Reg(null, null)
    )
    private val flib: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("close"), LuaIOLib_delegate("io_close")),
        luaL_Reg(CharPtr.Companion.toCharPtr("flush"), LuaIOLib_delegate("f_flush")),
        luaL_Reg(CharPtr.Companion.toCharPtr("lines"), LuaIOLib_delegate("f_lines")),
        luaL_Reg(CharPtr.Companion.toCharPtr("read"), LuaIOLib_delegate("f_read")),
        luaL_Reg(CharPtr.Companion.toCharPtr("seek"), LuaIOLib_delegate("f_seek")),
        luaL_Reg(CharPtr.Companion.toCharPtr("setvbuf"), LuaIOLib_delegate("f_setvbuf")),
        luaL_Reg(CharPtr.Companion.toCharPtr("write"), LuaIOLib_delegate("f_write")),
        luaL_Reg(CharPtr.Companion.toCharPtr("__gc"), LuaIOLib_delegate("io_gc")),
        luaL_Reg(CharPtr.Companion.toCharPtr("__tostring"), LuaIOLib_delegate("io_tostring")),
        luaL_Reg(null, null)
    )

    private fun createmeta(L: lua_State) {
        LuaAuxLib.luaL_newmetatable(
            L,
            CharPtr.Companion.toCharPtr(LuaLib.LUA_FILEHANDLE)
        ) // create metatable for file files
        LuaAPI.lua_pushvalue(L, -1) // push metatable
        LuaAPI.lua_setfield(L, -2, CharPtr.Companion.toCharPtr("__index")) // metatable.__index = metatable
        LuaAuxLib.luaL_register(L, null, flib) // file methods
    }

    private fun createstdfile(L: lua_State, f: StreamProxy, k: Int, fname: CharPtr) {
        newfile(L).file = f
        if (k > 0) {
            LuaAPI.lua_pushvalue(L, -1)
            LuaAPI.lua_rawseti(L, Lua.LUA_ENVIRONINDEX, k)
        }
        LuaAPI.lua_pushvalue(L, -2) // copy environment
        LuaAPI.lua_setfenv(L, -2) // set it
        LuaAPI.lua_setfield(L, -3, fname)
    }

    private fun newfenv(L: lua_State, cls: lua_CFunction) {
        LuaAPI.lua_createtable(L, 0, 1)
        Lua.lua_pushcfunction(L, cls)
        LuaAPI.lua_setfield(L, -2, CharPtr.Companion.toCharPtr("__close"))
    }

    fun luaopen_io(L: lua_State): Int {
        createmeta(L)
        // create (private) environment (with fields IO_INPUT, IO_OUTPUT, __close)
        newfenv(L, LuaIOLib_delegate("io_fclose"))
        LuaAPI.lua_replace(L, Lua.LUA_ENVIRONINDEX)
        // open library
        LuaAuxLib.luaL_register(L, CharPtr.Companion.toCharPtr(LuaLib.LUA_IOLIBNAME), iolib)
        // create (and set) default files
        newfenv(L, LuaIOLib_delegate("io_noclose")) // close function for default files
        createstdfile(L, CLib.stdin, IO_INPUT, CharPtr.Companion.toCharPtr("stdin"))
        createstdfile(L, CLib.stdout, IO_OUTPUT, CharPtr.Companion.toCharPtr("stdout"))
        createstdfile(L, CLib.stderr, 0, CharPtr.Companion.toCharPtr("stderr"))
        Lua.lua_pop(L, 1) // pop environment for default files
        LuaAPI.lua_getfield(L, -1, CharPtr.Companion.toCharPtr("popen"))
        newfenv(L, LuaIOLib_delegate("io_pclose")) // create environment for 'popen'
        LuaAPI.lua_setfenv(L, -2) // set fenv for 'popen'
        Lua.lua_pop(L, 1) // pop 'popen'
        return 1
    }

    class FilePtr {
        var file: StreamProxy? = null
    }

    class LuaIOLib_delegate(private val name: String) : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return if ("io_close" == name) {
                io_close(L)
            } else if ("io_flush" == name) {
                io_flush(L)
            } else if ("io_input" == name) {
                io_input(L)
            } else if ("io_lines" == name) {
                io_lines(L)
            } else if ("io_open" == name) {
                io_open(L)
            } else if ("io_output" == name) {
                io_output(L)
            } else if ("io_popen" == name) {
                io_popen(L)
            } else if ("io_read" == name) {
                io_read(L)
            } else if ("io_tmpfile" == name) {
                io_tmpfile(L)
            } else if ("io_type" == name) {
                io_type(L)
            } else if ("io_write" == name) {
                io_write(L)
            } else if ("f_flush" == name) {
                f_flush(L)
            } else if ("f_lines" == name) {
                f_lines(L)
            } else if ("f_read" == name) {
                f_read(L)
            } else if ("f_seek" == name) {
                f_seek(L)
            } else if ("f_setvbuf" == name) {
                f_setvbuf(L)
            } else if ("f_write" == name) {
                f_write(L)
            } else if ("io_gc" == name) {
                io_gc(L)
            } else if ("io_tostring" == name) {
                io_tostring(L)
            } else if ("io_fclose" == name) {
                io_fclose(L)
            } else if ("io_noclose" == name) {
                io_noclose(L)
            } else if ("io_pclose" == name) {
                io_pclose(L)
            } else if ("io_readline" == name) {
                io_readline(L)
            } else {
                0
            }
        }

    }
}