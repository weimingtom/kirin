package kirin

import kirin.CLib.CharPtr
import kirin.LuaObject.TString
import kirin.LuaObject.Table
import kirin.LuaObject.Udata
import kirin.LuaState.GCObject
import kirin.LuaState.lua_State
import kirin.LuaState.stringtable

//
// ** $Id: lstring.c,v 2.8.1.1 2007/12/27 13:02:25 roberto Exp $
// ** String table (keeps all strings handled by Lua)
// ** See Copyright Notice in lua.h
//
//using lu_byte = System.Byte;
object LuaString {
    fun sizestring(s: TString): Int {
        return (s.len as Int + 1) * CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_CHAR)) //char
    }

    fun sizeudata(u: Udata): Int {
        return u.len
    }

    fun luaS_new(L: lua_State?, s: CharPtr?): TString {
        return luaS_newlstr(L, s, CLib.strlen(s)) //(uint)
    }

    fun luaS_newliteral(L: lua_State?, s: CharPtr?): TString {
        return luaS_newlstr(L, s, CLib.strlen(s)) //(uint)
    }

    fun luaS_fix(s: TString?) {
        var marked: Byte = s!!.getTsv().marked // can't pass properties in as ref - lu_byte
        val marked_ref = ByteArray(1)
        marked_ref[0] = marked
        LuaGC.l_setbit(marked_ref, LuaGC.FIXEDBIT) //ref
        marked = marked_ref[0]
        s!!.getTsv().marked = marked
    }

    fun luaS_resize(L: lua_State?, newsize: Int) {
        val newhash: Array<GCObject?>
        val tb: stringtable
        var i: Int
        if (LuaState.G(L)!!.gcstate.toInt() == LuaGC.GCSsweepstring) {
            return  // cannot resize during GC traverse
        }
        // todo: fix this up
// I'm treating newhash as a regular C# array, but I need to allocate a dummy array
// so that the garbage collector behaves identical to the C version.
//newhash = luaM_newvector<GCObjectRef>(L, newsize);
        newhash = arrayOfNulls<GCObject>(newsize)
        LuaMem.AddTotalBytes(
            L,
            newsize * CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_GCOBJECTREF))
        ) //typeof(GCObjectRef)
        tb = LuaState.G(L)!!.strt
        i = 0
        while (i < newsize) {
            newhash[i] = null
            i++
        }
        // rehash
        i = 0
        while (i < tb.size) {
            var p: GCObject? = tb.hash!![i]
            while (p != null) { // for each node in the list
                val next: GCObject? = p.getGch().next // save next
                val h: Long = LuaState.gco2ts(p).hash //uint - int
                val h1 = CLib.lmod(h.toDouble(), newsize.toDouble()).toInt() // new position
                LuaLimits.lua_assert(((h % newsize).toInt()).toLong() == CLib.lmod(h.toDouble(), newsize.toDouble()))
                p.getGch().next = newhash[h1] // chain it
                newhash[h1] = p
                p = next
            }
            i++
        }
        //luaM_freearray(L, tb.hash);
        if (tb.hash != null) {
            LuaMem.SubtractTotalBytes(
                L,
                tb.hash!!.size * CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_GCOBJECTREF))
            ) //typeof(GCObjectRef)
        }
        tb.size = newsize
        tb.hash = newhash
    }

    fun newlstr(L: lua_State?, str: CharPtr?, l: Int, h: Long): TString { //uint - int - uint
        var h = h
        val ts: TString
        val tb: stringtable
        if (l + 1 > LuaLimits.MAX_SIZET / CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_CHAR))) { //typeof(char)
            LuaMem.luaM_toobig(L)
        }
        ts = TString(CharPtr.Companion.toCharPtr(CharArray(l + 1)))
        LuaMem.AddTotalBytes(
            L,
            (l + 1) * CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_CHAR)) + CLib.GetUnmanagedSize(
                ClassType(ClassType.Companion.TYPE_TSTRING)
            )
        ) //typeof(TString)//typeof(char)
        ts.getTsv().len = l
        ts.getTsv().hash = h
        ts.getTsv().marked = LuaGC.luaC_white(LuaState.G(L))
        ts.getTsv().tt = Lua.LUA_TSTRING.toByte()
        ts.getTsv().reserved = 0
        //memcpy(ts+1, str, l*GetUnmanagedSize(typeof(char)));
        CLib.memcpy_char(ts.str!!.chars!!, str!!.chars!!, str.index, l)
        ts.str!!.set(l, '\u0000') // ending 0
        tb = LuaState.G(L)!!.strt
        h = (CLib.lmod(h.toDouble(), tb.size.toDouble()).toInt()).toLong() //uint
        ts.getTsv().next = tb.hash!!.get(h.toInt()) // chain new entry
        tb.hash!![h.toInt()] = LuaState.obj2gco(ts)
        tb.nuse++
        if (tb.nuse > tb.size.toInt() && tb.size <= LuaLimits.MAX_INT / 2) {
            luaS_resize(L, tb.size * 2) // too crowded
        }
        return ts
    }

    fun luaS_newlstr(L: lua_State?, str: CharPtr?, l: Int): TString { //uint
        var o: GCObject?
        //FIXME:
        var h = l.toLong() and 0xffffffffL // seed  - (uint) - uint - int
        val step = (l shr 5) + 1 // if string is too long, don't hash all its chars  - uint
        var l1: Int //uint
        l1 = l
        while (l1 >= step) {
            //FIXME:
// compute hash
            h = 0xffffffffL and (h xor (h shl 5) + (h shr 2) + str!!.get(l1 - 1).toByte())
            l1 -= step
        }
        o = LuaState.G(L)!!.strt.hash!!.get(CLib.lmod(h.toDouble(), LuaState.G(L)!!.strt.size.toDouble()).toInt())
        while (o != null) {
            val ts: TString? = LuaState.rawgco2ts(o)
            if (ts!!.getTsv().len == l && CLib.memcmp(str, LuaObject.getstr(ts), l) == 0) { // string may be dead
                if (LuaGC.isdead(LuaState.G(L), o)) {
                    LuaGC.changewhite(o)
                }
                return ts
            }
            o = o.getGch().next
        }
        //return newlstr(L, str, l, h);  /* not found */
        return newlstr(L, str, l, h)
    }

    fun luaS_newudata(L: lua_State?, s: Int, e: Table?): Udata { //uint
        val u = Udata()
        u.uv.marked = LuaGC.luaC_white(LuaState.G(L)) // is not finalized
        u.uv.tt = Lua.LUA_TUSERDATA.toByte()
        u.uv.len = s
        u.uv.metatable = null
        u.uv.env = e
        u.user_data = ByteArray(s)
        // chain it on udata list (after main thread)
        u.uv.next = LuaState.G(L)!!.mainthread!!.next
        LuaState.G(L)!!.mainthread!!.next = LuaState.obj2gco(u)
        return u
    }

    fun luaS_newudata(L: lua_State?, t: ClassType?, e: Table?): Udata {
        val u = Udata()
        u.uv.marked = LuaGC.luaC_white(LuaState.G(L)) // is not finalized
        u.uv.tt = Lua.LUA_TUSERDATA.toByte()
        u.uv.len = 0
        u.uv.metatable = null
        u.uv.env = e
        u.user_data = LuaMem.luaM_realloc_(L, t!!)
        LuaMem.AddTotalBytes(L, CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_UDATA))) //typeof(Udata)
        // chain it on udata list (after main thread)
        u.uv.next = LuaState.G(L)!!.mainthread!!.next
        LuaState.G(L)!!.mainthread!!.next = LuaState.obj2gco(u)
        return u
    }
}