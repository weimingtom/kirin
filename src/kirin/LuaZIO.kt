package kirin

import kirin.CLib.CharPtr
import kirin.Lua.lua_Reader
import kirin.LuaState.lua_State

//
// ** $Id: lzio.c,v 1.31.1.1 2007/12/27 13:02:25 roberto Exp $
// ** a generic input stream interface
// ** See Copyright Notice in lua.h
//
object LuaZIO {
    const val EOZ = -1 // end of stream
    fun char2int(c: Char): Int {
        return c.toInt()
    }

    fun zgetc(z: ZIO?): Int {
        return if (z!!.n-- > 0) {
            val ch = char2int(z!!.p!![0])
            z!!.p!!.inc()
            ch
        } else {
            luaZ_fill(z)
        }
    }

    fun luaZ_initbuffer(L: lua_State?, buff: Mbuffer) {
        buff.buffer = null
    }

    fun luaZ_buffer(buff: Mbuffer?): CharPtr? {
        return buff!!.buffer
    }

    fun luaZ_sizebuffer(buff: Mbuffer): Int { //uint
        return buff.buffsize
    }

    fun luaZ_bufflen(buff: Mbuffer?): Int { //uint
        return buff!!.n
    }

    fun luaZ_resetbuffer(buff: Mbuffer?) {
        buff!!.n = 0
    }

    fun luaZ_resizebuffer(L: lua_State?, buff: Mbuffer?, size: Int) {
        if (CharPtr.Companion.isEqual(buff!!.buffer, null)) {
            buff.buffer = CharPtr()
        }
        val chars_ref = arrayOfNulls<CharArray>(1)
        chars_ref[0] = buff.buffer!!.chars
        LuaMem.luaM_reallocvector_char(
            L,
            chars_ref,
            buff.buffsize,
            size,
            ClassType(ClassType.Companion.TYPE_CHAR)
        ) //(int) - ref
        buff.buffer!!.chars = chars_ref[0]
        buff.buffsize = buff.buffer!!.chars!!.size  //(uint)
    }

    fun luaZ_freebuffer(L: lua_State?, buff: Mbuffer?) {
        luaZ_resizebuffer(L, buff, 0)
    }

    fun luaZ_fill(z: ZIO?): Int {
        val size = IntArray(1) //uint
        val L = z!!.L
        val buff: CharPtr
        LuaLimits.lua_unlock(L)
        buff = z.reader!!.exec(L, z.data!!, size)!! //out
        LuaLimits.lua_lock(L)
        if (CharPtr.Companion.isEqual(buff, null) || size[0] == 0) {
            return EOZ
        }
        z.n = size[0] - 1
        z.p = CharPtr(buff)
        val result = char2int(z.p!![0])
        z.p!!.inc()
        return result
    }

    fun luaZ_lookahead(z: ZIO): Int {
        if (z.n == 0) {
            if (luaZ_fill(z) == EOZ) {
                return EOZ
            } else {
                z.n++ // luaZ_fill removed first byte; put back it
                z.p!!.dec()
            }
        }
        return char2int(z.p!![0])
    }

    fun luaZ_init(L: lua_State?, z: ZIO, reader: lua_Reader?, data: Any?) {
        z.L = L
        z.reader = reader
        z.data = data
        z.n = 0
        z.p = null
    }

    // --------------------------------------------------------------- read ---
    fun luaZ_read(z: ZIO, b: CharPtr?, n: Int): Int { //uint - uint
        var b = b
        var n = n
        b = CharPtr(b!!)
        while (n != 0) {
            var m: Int //uint
            if (luaZ_lookahead(z) == EOZ) {
                return n // return number of missing bytes
            }
            m = if (n <= z.n) n else z.n // min. between n and z.n
            CLib.memcpy(b!!, z.p!!, m)
            z.n -= m
            z.p = CharPtr.Companion.plus(z.p, m)
            b = CharPtr.Companion.plus(b, m)
            n -= m
        }
        return 0
    }

    // ------------------------------------------------------------------------
    fun luaZ_openspace(L: lua_State?, buff: Mbuffer, n: Int): CharPtr? { //uint
        var n = n
        if (n > buff.buffsize) {
            if (n < LuaLimits.LUA_MINBUFFER) {
                n = LuaLimits.LUA_MINBUFFER
            }
            luaZ_resizebuffer(L, buff, n) //(int)
        }
        return buff.buffer
    }

    class Mbuffer {
        var buffer: CharPtr? = CharPtr()
        var n /*uint*/ = 0
        var buffsize /*uint*/ = 0
    }

    // --------- Private Part ------------------
    class ZIO  { //Zio
        var n /*uint*/ /* bytes still unread */ = 0
        var p /* current position in buffer */: CharPtr? = null
        var reader: lua_Reader? = null
        var data /* additional data */: Any? = null
        var L /* Lua state (for reader) */: lua_State? = null //public class ZIO : Zio { };
    }
}