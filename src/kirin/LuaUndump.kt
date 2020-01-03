package kirin
import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.LuaObject.TValue
import kirin.LuaObject.Proto
import kirin.LuaZIO.ZIO
import kirin.LuaZIO.Mbuffer
import kirin.LuaObject.TString

//
// ** $Id: lundump.c,v 2.7.1.4 2008/04/04 19:51:41 roberto Exp $
// ** load precompiled Lua chunks
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
//using lua_Number = System.Double;
//using lu_byte = System.Byte;
//using StkId = TValue;
//using Instruction = System.UInt32;
object LuaUndump {
    // for header of binary files -- this is Lua 5.1
    const val LUAC_VERSION = 0x51
    // for header of binary files -- this is the official format
    const val LUAC_FORMAT = 0
    // size of header of binary files
    const val LUAC_HEADERSIZE = 12

    ///#ifdef LUAC_TRUST_BINARIES
///#define IF(c,s)
///#define error(S,s)
///#else
///#define IF(c,s)		if (c) error(S,s)
    fun IF(c: Int, s: String?) {}

    fun IF(c: Boolean, s: String?) {}
    private fun error(S: LoadState, why: CharPtr) {
        LuaObject.luaO_pushfstring(S.L, CharPtr.Companion.toCharPtr("%s: %s in precompiled chunk"), S.name, why)
        LuaDo.luaD_throw(S.L, Lua.LUA_ERRSYNTAX)
    }

    ///#endif
    fun LoadMem(S: LoadState, t: ClassType): Any? {
        val size: Int = t.GetMarshalSizeOf()
        val str: CharPtr = CharPtr.Companion.toCharPtr(CharArray(size))
        LoadBlock(S, str, size)
        val bytes = ByteArray(str.chars!!.size)
        for (i in str.chars!!.indices) {
            bytes[i] = str.chars!!.get(i) as Byte
        }
        return t.bytesToObj(bytes)
    }

    fun LoadMem(S: LoadState, t: ClassType, n: Int): Any? { //ArrayList array = new ArrayList();
        val array = arrayOfNulls<Any>(n)
        for (i in 0 until n) {
            array[i] = LoadMem(S, t)
        }
        return t.ToArray(array)
    }

    fun LoadByte(S: LoadState): Byte { //lu_byte
        return LoadChar(S).toByte() //lu_byte
    }

    fun LoadVar(S: LoadState, t: ClassType): Any? {
        return LoadMem(S, t)
    }

    fun LoadVector(S: LoadState, t: ClassType, n: Int): Any? {
        return LoadMem(S, t, n)
    }

    private fun LoadBlock(S: LoadState, b: CharPtr, size: Int) {
        val r: Int = LuaZIO.luaZ_read(S.Z!!, b, size) //(uint) - uint
        IF(r != 0, "unexpected end")
    }

    private fun LoadChar(S: LoadState): Int {
        return (LoadVar(S, ClassType(ClassType.Companion.TYPE_CHAR)) as Char?)!!.toChar().toInt()
    }

    private fun LoadInt(S: LoadState): Int {
        val x = (LoadVar(S, ClassType(ClassType.Companion.TYPE_INT)) as Int?)!!.toInt()
        IF(x < 0, "bad integer")
        return x
    }

    private fun LoadNumber(S: LoadState): Double { //lua_Number
        return (LoadVar(
            S,
            ClassType(ClassType.Companion.TYPE_DOUBLE)
        ) as Double?)!!.toDouble() //lua_Number - lua_Number
    }

    private fun LoadString(S: LoadState): TString? { //typeof(int/*uint*/)
        val size = (LoadVar(S, ClassType(ClassType.Companion.TYPE_INT)) as Int?)!!.toInt() //uint - uint
        return if (size == 0) {
            null
        } else {
            val s: CharPtr = LuaZIO.luaZ_openspace(S.L, S.b!!, size)!!
            LoadBlock(S, s, size)
            LuaString.luaS_newlstr(S.L, s, size - 1) // remove trailing '\0'
        }
    }

    private fun LoadCode(S: LoadState, f: Proto) {
        val n = LoadInt(S)
        //UInt32
//Instruction
        f.code = LuaMem.luaM_newvector_long(S.L, n, ClassType(ClassType.Companion.TYPE_LONG))
        f.sizecode = n
        f.code = LoadVector(
            S,
            ClassType(ClassType.Companion.TYPE_LONG),
            n
        ) as LongArray? //Instruction[] - UInt32[]
    }

    private fun LoadConstants(S: LoadState, f: Proto) {
        var i: Int
        var n: Int
        n = LoadInt(S)
        f.k = LuaMem.luaM_newvector_TValue(S.L, n, ClassType(ClassType.Companion.TYPE_TVALUE))
        f.sizek = n
        i = 0
        while (i < n) {
            LuaObject.setnilvalue(f.k!!.get(i))
            i++
        }
        i = 0
        while (i < n) {
            val o: TValue = f.k!!.get(i)!!
            val t = LoadChar(S)
            when (t) {
                Lua.LUA_TNIL -> {
                    LuaObject.setnilvalue(o)
                }
                Lua.LUA_TBOOLEAN -> {
                    LuaObject.setbvalue(o, LoadChar(S))
                }
                Lua.LUA_TNUMBER -> {
                    LuaObject.setnvalue(o, LoadNumber(S))
                }
                Lua.LUA_TSTRING -> {
                    LuaObject.setsvalue2n(S.L, o, LoadString(S))
                }
                else -> {
                    error(S, CharPtr.Companion.toCharPtr("bad constant"))
                }
            }
            i++
        }
        n = LoadInt(S)
        f.p = LuaMem.luaM_newvector_Proto(S.L, n, ClassType(ClassType.Companion.TYPE_PROTO))
        f.sizep = n
        i = 0
        while (i < n) {
            f.p!![i] = null
            i++
        }
        i = 0
        while (i < n) {
            f.p!![i] = LoadFunction(S, f.source!!)
            i++
        }
    }

    private fun LoadDebug(S: LoadState, f: Proto) {
        var i: Int
        var n: Int
        n = LoadInt(S)
        f.lineinfo = LuaMem.luaM_newvector_int(S.L, n, ClassType(ClassType.Companion.TYPE_INT))
        f.sizelineinfo = n
        f.lineinfo =
            LoadVector(S, ClassType(ClassType.Companion.TYPE_INT), n) as IntArray? //typeof(int)
        n = LoadInt(S)
        f.locvars = LuaMem.luaM_newvector_LocVar(S.L, n, ClassType(ClassType.Companion.TYPE_LOCVAR))
        f.sizelocvars = n
        i = 0
        while (i < n) {
            f.locvars!!.get(i)!!.varname = null
            i++
        }
        i = 0
        while (i < n) {
            f.locvars!!.get(i)!!.varname = LoadString(S)
            f.locvars!!.get(i)!!.startpc = LoadInt(S)
            f.locvars!!.get(i)!!.endpc = LoadInt(S)
            i++
        }
        n = LoadInt(S)
        f.upvalues = LuaMem.luaM_newvector_TString(S.L, n, ClassType(ClassType.Companion.TYPE_TSTRING))
        f.sizeupvalues = n
        i = 0
        while (i < n) {
            f.upvalues!![i] = null
            i++
        }
        i = 0
        while (i < n) {
            f.upvalues!![i] = LoadString(S)
            i++
        }
    }

    private fun LoadFunction(S: LoadState, p: TString): Proto {
        val f: Proto
        if (++S.L!!.nCcalls > LuaConf.LUAI_MAXCCALLS) {
            error(S, CharPtr.Companion.toCharPtr("code too deep"))
        }
        f = LuaFunc.luaF_newproto(S.L)!!
        LuaObject.setptvalue2s(S.L, S.L!!.top!!, f)
        LuaDo.incr_top(S.L)
        f.source = LoadString(S)
        if (f.source == null) {
            f.source = p
        }
        f.linedefined = LoadInt(S)
        f.lastlinedefined = LoadInt(S)
        f.nups = LoadByte(S)
        f.numparams = LoadByte(S)
        f.is_vararg = LoadByte(S)
        f.maxstacksize = LoadByte(S)
        LoadCode(S, f)
        LoadConstants(S, f)
        LoadDebug(S, f)
        IF(if (LuaDebug.luaG_checkcode(f) == 0) 1 else 0, "bad code")
        val top: Array<TValue?> = arrayOfNulls<TValue>(1)
        top[0] = S.L!!.top
        //StkId
        TValue.Companion.dec(top) //ref
        S.L!!.top = top[0]
        S.L!!.nCcalls--
        return f
    }

    private fun LoadHeader(S: LoadState) {
        val h: CharPtr = CharPtr.Companion.toCharPtr(CharArray(LUAC_HEADERSIZE))
        val s: CharPtr = CharPtr.Companion.toCharPtr(CharArray(LUAC_HEADERSIZE))
        luaU_header(h)
        LoadBlock(S, s, LUAC_HEADERSIZE)
        IF(CLib.memcmp(h, s, LUAC_HEADERSIZE) != 0, "bad header")
    }

    //
//		 ** load precompiled chunk
//
    fun luaU_undump(L: lua_State?, Z: ZIO?, buff: Mbuffer?, name: CharPtr?): Proto {
        val S = LoadState()
        if (name!!.get(0) == '@' || name!!.get(0) == '=') {
            S.name = CharPtr.Companion.plus(name, 1)
        } else if (name!!.get(0) == Lua.LUA_SIGNATURE.get(0)) {
            S.name = CharPtr.Companion.toCharPtr("binary string")
        } else {
            S.name = name
        }
        S.L = L
        S.Z = Z
        S.b = buff
        LoadHeader(S)
        return LoadFunction(S, LuaString.luaS_newliteral(L, CharPtr.Companion.toCharPtr("=?")))
    }

    //
//		 * make header
//
    fun luaU_header(h: CharPtr) {
        var h: CharPtr = h
        h = CharPtr(h)
        val x = 1
        CLib.memcpy(h, CharPtr.Companion.toCharPtr(Lua.LUA_SIGNATURE), Lua.LUA_SIGNATURE.length)
        h = h.add(Lua.LUA_SIGNATURE.length)
        h.set(0, LUAC_VERSION.toChar())
        h.inc()
        h.set(0, LUAC_FORMAT.toChar())
        h.inc()
        //*h++=(char)*(char*)&x;				/* endianness */
        h.set(0, x.toChar()) // endianness
        h.inc()
        h.set(0, ClassType.Companion.SizeOfInt() as Char)
        h.inc()
        //FIXME:
        h.set(0, ClassType.Companion.SizeOfLong() as Char)
        //sizeof(long/*uint*/)
        h.inc()
        h.set(0, ClassType.Companion.SizeOfLong() as Char)
        //sizeof(long/*UInt32*//*Instruction*/));
        h.inc()
        h.set(0, ClassType.Companion.SizeOfDouble() as Char)
        //sizeof(Double/*lua_Number*/)
        h.inc()
        //(h++)[0] = ((lua_Number)0.5 == 0) ? 0 : 1;		/* is lua_Number integral? */
        h.set(0, 0.toChar()) // always 0 on this build
    }

    class LoadState {
        var L: lua_State? = null
        var Z: ZIO? = null
        var b: Mbuffer? = null
        var name: CharPtr? = null
    }
}