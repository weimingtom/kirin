package kirin

import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.LuaState.GCObject
import kirin.LuaState.global_State

//
// ** $Id: lobject.c,v 2.22.1.1 2007/12/27 13:02:25 roberto Exp $
// ** Some generic functions over Lua objects
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
//using StkId = TValue;
//using lu_byte = System.Byte;
//using lua_Number = System.Double;
//using l_uacNumber = System.Double;
//using Instruction = System.UInt32;
object LuaObject {
    // tags for values visible from Lua
    val LAST_TAG: Int = Lua.LUA_TTHREAD
    val NUM_TAGS = LAST_TAG + 1
    //
//		 ** Extra tags for non-values
//
    val LUA_TPROTO = LAST_TAG + 1
    val LUA_TUPVAL = LAST_TAG + 2
    val LUA_TDEADKEY = LAST_TAG + 3
    // Macros to test type
    fun ttisnil(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TNIL
    }

    fun ttisnumber(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TNUMBER
    }

    fun ttisstring(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TSTRING
    }

    fun ttistable(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TTABLE
    }

    fun ttisfunction(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TFUNCTION
    }

    fun ttisboolean(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TBOOLEAN
    }

    fun ttisuserdata(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TUSERDATA
    }

    fun ttisthread(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TTHREAD
    }

    fun ttislightuserdata(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TLIGHTUSERDATA
    }

    // Macros to access values
    fun ttype(o: TValue?): Int {
        return o!!.tt
    }

    fun ttype(o: CommonHeader): Int {
        return o.tt.toInt()
    }

    fun gcvalue(o: TValue?): GCObject? {
        return LuaLimits.check_exp(iscollectable(o), o!!.value.gc) as GCObject
    }

    fun pvalue(o: TValue): Any? {
        return LuaLimits.check_exp(ttislightuserdata(o), o.value.p) as Any
    }

    fun nvalue(o: TValue): Double { //lua_Number
        return (LuaLimits.check_exp(ttisnumber(o), o.value.n) as Double).toDouble() //lua_Number
    }

    fun rawtsvalue(o: TValue): TString? {
        return LuaLimits.check_exp(ttisstring(o), o.value.gc!!.getTs()) as TString
    }

    fun tsvalue(o: TValue): TString_tsv {
        return rawtsvalue(o)!!.getTsv()
    }

    fun rawuvalue(o: TValue): Udata? {
        return LuaLimits.check_exp(ttisuserdata(o), o.value.gc!!.getU()) as Udata
    }

    fun uvalue(o: TValue): Udata_uv {
        return rawuvalue(o)!!.uv
    }

    fun clvalue(o: TValue?): Closure? {
        return LuaLimits.check_exp(ttisfunction(o), o!!.value.gc!!.getCl()) as Closure
    }

    fun hvalue(o: TValue): Table? {
        return LuaLimits.check_exp(ttistable(o), o.value.gc!!.getH()) as Table
    }

    fun bvalue(o: TValue): Int {
        return (LuaLimits.check_exp(ttisboolean(o), o.value.b) as Int).toInt()
    }

    fun thvalue(o: TValue): lua_State? {
        return LuaLimits.check_exp(ttisthread(o), o.value.gc!!.getTh()) as lua_State
    }

    fun l_isfalse(o: TValue): Int {
        return if (ttisnil(o) || ttisboolean(o) && bvalue(o) == 0) 1 else 0
    }

    //
//		 ** for internal debug only
//
    fun checkconsistency(obj: TValue?) {
        LuaLimits.lua_assert(!iscollectable(obj) || ttype(obj) == obj!!.value.gc!!.getGch().tt.toInt())
    }

    fun checkliveness(g: global_State?, obj: TValue?) {
        LuaLimits.lua_assert(
            !iscollectable(obj) || ttype(obj) == obj!!.value.gc!!.getGch().tt.toInt() && !LuaGC.isdead(
                g,
                obj.value.gc
            )
        )
    }

    // Macros to set values
    fun setnilvalue(obj: TValue?) {
        obj!!.tt = Lua.LUA_TNIL
    }

    fun setnvalue(obj: TValue, x: Double) { //lua_Number
        obj.value.n = x
        obj.tt = Lua.LUA_TNUMBER
    }

    fun setpvalue(obj: TValue, x: Any?) {
        obj.value.p = x
        obj.tt = Lua.LUA_TLIGHTUSERDATA
    }

    fun setbvalue(obj: TValue, x: Int) {
        obj.value.b = x
        obj.tt = Lua.LUA_TBOOLEAN
    }

    fun setsvalue(L: lua_State?, obj: TValue, x: GCObject?) {
        obj.value.gc = x
        obj.tt = Lua.LUA_TSTRING
        checkliveness(LuaState.G(L), obj)
    }

    fun setuvalue(L: lua_State?, obj: TValue, x: GCObject?) {
        obj.value.gc = x
        obj.tt = Lua.LUA_TUSERDATA
        checkliveness(LuaState.G(L), obj)
    }

    fun setthvalue(L: lua_State?, obj: TValue, x: GCObject?) {
        obj.value.gc = x
        obj.tt = Lua.LUA_TTHREAD
        checkliveness(LuaState.G(L), obj)
    }

    fun setclvalue(L: lua_State?, obj: TValue, x: Closure?) {
        obj.value.gc = x
        obj.tt = Lua.LUA_TFUNCTION
        checkliveness(LuaState.G(L), obj)
    }

    fun sethvalue(L: lua_State?, obj: TValue, x: Table?) {
        obj.value.gc = x
        obj.tt = Lua.LUA_TTABLE
        checkliveness(LuaState.G(L), obj)
    }

    fun setptvalue(L: lua_State?, obj: TValue, x: Proto?) {
        obj.value.gc = x
        obj.tt = LUA_TPROTO
        checkliveness(LuaState.G(L), obj)
    }

    fun setobj(L: lua_State?, obj1: TValue?, obj2: TValue) {
        obj1!!.value.copyFrom(obj2.value)
        obj1.tt = obj2.tt
        checkliveness(LuaState.G(L), obj1)
    }

    //
//		 ** different types of sets, according to destination
//
// from stack to (same) stack
///#define setobjs2s	setobj
    fun setobjs2s(L: lua_State?, obj: TValue?, x: TValue) {
        setobj(L, obj, x)
    }

    //to stack (not from same stack)
///#define setobj2s	setobj
    fun setobj2s(L: lua_State?, obj: TValue?, x: TValue) {
        setobj(L, obj, x)
    }

    ///#define setsvalue2s	setsvalue
    fun setsvalue2s(L: lua_State?, obj: TValue, x: TString?) {
        setsvalue(L, obj, x)
    }

    ///#define sethvalue2s	sethvalue
    fun sethvalue2s(L: lua_State?, obj: TValue, x: Table?) {
        sethvalue(L, obj, x)
    }

    ///#define setptvalue2s	setptvalue
    fun setptvalue2s(L: lua_State?, obj: TValue, x: Proto?) {
        setptvalue(L, obj, x)
    }

    // from table to same table
///#define setobjt2t	setobj
    fun setobjt2t(L: lua_State?, obj: TValue?, x: TValue) {
        setobj(L, obj, x)
    }

    // to table
///#define setobj2t	setobj
    fun setobj2t(L: lua_State?, obj: TValue?, x: TValue) {
        setobj(L, obj, x)
    }

    // to new object
///#define setobj2n	setobj
    fun setobj2n(L: lua_State?, obj: TValue?, x: TValue) {
        setobj(L, obj, x)
    }

    ///#define setsvalue2n	setsvalue
    fun setsvalue2n(L: lua_State?, obj: TValue, x: TString?) {
        setsvalue(L, obj, x)
    }

    fun setttype(obj: TValue, tt: Int) {
        obj.tt = tt
    }

    fun iscollectable(o: TValue?): Boolean {
        return ttype(o) >= Lua.LUA_TSTRING
    }

    fun getstr(ts: TString?): CharPtr? {
        return ts!!.str
    }

    fun svalue(o: TValue): CharPtr? { //StkId
        return getstr(rawtsvalue(o))
    }

    // masks for new-style vararg
    const val VARARG_HASARG = 1
    const val VARARG_ISVARARG = 2
    const val VARARG_NEEDSARG = 4
    fun iscfunction(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TFUNCTION && clvalue(o)!!.c.getIsC().toInt() != 0
    }

    fun isLfunction(o: TValue?): Boolean {
        return ttype(o) == Lua.LUA_TFUNCTION && clvalue(o)!!.c.getIsC().toInt() == 0
    }

    //
//		 ** `module' operation for hashing (size is always a power of 2)
//
///#define lmod(s,size) \
//    (check_exp((size&(size-1))==0, (cast(int, (s) & ((size)-1)))))
    fun twoto(x: Int): Int {
        return 1 shl x
    }

    fun sizenode(t: Table?): Int {
        return twoto(t!!.lsizenode.toInt())
    }

    var luaO_nilobject_ = TValue(Value(), Lua.LUA_TNIL)
    var luaO_nilobject = luaO_nilobject_
    fun ceillog2(x: Int): Int {
        return luaO_log2(((x - 1) as Int).toLong()) + 1 //uint
    }

    //
//		 ** converts an integer to a "floating point byte", represented as
//		 ** (eeeeexxx), where the real value is (1xxx) * 2^(eeeee - 1) if
//		 ** eeeee != 0 and (xxx) otherwise.
//
    fun luaO_int2fb(x: Int): Int { //uint
        var x = x
        var e = 0 // expoent
        while (x >= 16) {
            x = x + 1 shr 1
            e++
        }
        return if (x < 8) {
            x
        } else {
            e + 1 shl 3 or LuaLimits.cast_int(x) - 8
        }
    }

    // converts back
    fun luaO_fb2int(x: Int): Int {
        val e = x shr 3 and 31
        return if (e == 0) {
            x
        } else {
            (x and 7) + 8 shl e - 1
        }
    }

    private val log_2 = byteArrayOf(
        0,
        1,
        2,
        2,
        3,
        3,
        3,
        3,
        4,
        4,
        4,
        4,
        4,
        4,
        4,
        4,
        5,
        5,
        5,
        5,
        5,
        5,
        5,
        5,
        5,
        5,
        5,
        5,
        5,
        5,
        5,
        5,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        6,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8
    )

    fun luaO_log2(x: Long): Int { //uint
        var x = x
        var l = -1
        while (x >= 256) {
            l += 8
            x = x shr 8
        }
        return l + log_2[x.toInt()]
    }

    fun luaO_rawequalObj(t1: TValue, t2: TValue): Int {
        return if (ttype(t1) != ttype(t2)) {
            0
        } else {
            when (ttype(t1)) {
                Lua.LUA_TNIL -> {
                    1
                }
                Lua.LUA_TNUMBER -> {
                    if (LuaConf.luai_numeq(nvalue(t1), nvalue(t2))) 1 else 0
                }
                Lua.LUA_TBOOLEAN -> {
                    if (bvalue(t1) == bvalue(t2)) 1 else 0 // boolean true must be 1....but not in C# !!
                }
                Lua.LUA_TLIGHTUSERDATA -> {
                    if (pvalue(t1) === pvalue(t2)) 1 else 0
                }
                else -> {
                    LuaLimits.lua_assert(iscollectable(t1))
                    if (gcvalue(t1) === gcvalue(t2)) 1 else 0
                }
            }
        }
    }

    fun luaO_str2d(s: CharPtr, result: DoubleArray): Int { //lua_Number - out
        val endptr: Array<CharPtr?> = arrayOfNulls<CharPtr?>(1)
        endptr[0] = CharPtr()
        result[0] = LuaConf.lua_str2number(s, endptr) //out
        if (CharPtr.Companion.isEqual(endptr[0], s)) {
            return 0 // conversion failed
        }
        if (endptr[0]!!.get(0) == 'x' || endptr[0]!!.get(0) == 'X') { // maybe an hexadecimal constant?
            result[0] = LuaLimits.cast_num(CLib.strtoul(s, endptr, 16)) //out
        }
        if (endptr[0]!!.get(0) == '\u0000') {
            return 1 // most common case
        }
        while (CLib.isspace(endptr[0]!!.get(0))) {
            endptr[0] = endptr[0]!!.next()
        }
        return if (endptr[0]!!.get(0) != '\u0000') {
            0 // invalid trailing characters?
        } else 1
    }

    private fun pushstr(L: lua_State?, str: CharPtr?) {
        setsvalue2s(L, L!!.top!!, LuaString.luaS_new(L, str))
        LuaDo.incr_top(L)
    }

    // this function handles only `%d', `%c', %f, %p, and `%s' formats
    fun luaO_pushvfstring(L: lua_State?, fmt: CharPtr?, vararg argp: Any?): CharPtr? {
        var fmt: CharPtr? = fmt
        var parm_index = 0
        var n = 1
        pushstr(L, CharPtr.Companion.toCharPtr(""))
        while (true) {
            val e: CharPtr = CLib.strchr(fmt, '%')!!
            if (CharPtr.Companion.isEqual(e, null)) {
                break
            }
            setsvalue2s(L, L!!.top!!, LuaString.luaS_newlstr(L, fmt, CharPtr.Companion.minus(e, fmt))) //(uint)
            LuaDo.incr_top(L)
            when (e.get(1)) {
                's' -> {
                    val o = argp[parm_index++]
                    var s: CharPtr? = o as? CharPtr as CharPtr?
                    if (CharPtr.Companion.isEqual(s, null)) {
                        s = CharPtr.Companion.toCharPtr(o as String)
                    }
                    if (CharPtr.Companion.isEqual(s, null)) {
                        s = CharPtr.Companion.toCharPtr("(null)")
                    }
                    pushstr(L, s)
                }
                'c' -> {
                    val buff: CharPtr = CharPtr.Companion.toCharPtr(CharArray(2))
                    buff.set(0, (argp[parm_index++] as Int).toInt().toChar())
                    buff.set(1, '\u0000')
                    pushstr(L, buff)
                }
                'd' -> {
                    setnvalue(L.top!!, (argp[parm_index++] as Int).toInt().toDouble())
                    LuaDo.incr_top(L)
                }
                'f' -> {
                    setnvalue(L.top!!, (argp[parm_index++] as Double).toDouble()) //l_uacNumber
                    LuaDo.incr_top(L)
                }
                'p' -> {
                    //CharPtr buff = new char[4*sizeof(void *) + 8]; /* should be enough space for a `%p' */
                    val buff: CharPtr = CharPtr.Companion.toCharPtr(CharArray(32))
                    CLib.sprintf(buff, CharPtr.Companion.toCharPtr("0x%08x"), argp[parm_index++].hashCode())
                    pushstr(L, buff)
                }
                '%' -> {
                    pushstr(L, CharPtr.Companion.toCharPtr("%"))
                }
                else -> {
                    val buff: CharPtr = CharPtr.Companion.toCharPtr(CharArray(3))
                    buff.set(0, '%')
                    buff.set(1, e.get(1))
                    buff.set(2, '\u0000')
                    pushstr(L, buff)
                }
            }
            n += 2
            fmt = CharPtr.Companion.plus(e, 2)
        }
        pushstr(L, fmt)
        LuaVM.luaV_concat(L!!, n + 1, LuaLimits.cast_int(TValue.Companion.minus(L.top, L.base_)) - 1)
        L.top = TValue.minus(L!!.top!!, n)!!
        return svalue(TValue.minus(L!!.top!!, 1)!!)
    }

    fun luaO_pushfstring(L: lua_State?, fmt: CharPtr?, vararg args: Any?): CharPtr? {
        return luaO_pushvfstring(L, fmt, *args)
    }

    fun luaO_chunkid(out_: CharPtr, source: CharPtr?, bufflen: Int) { //uint
//out_ = "";
        var source: CharPtr? = source
        var bufflen = bufflen
        if (source!!.get(0) == '=') {
            CLib.strncpy(out_, CharPtr.Companion.plus(source, 1), bufflen) // remove first char  - (int)
            out_.set(bufflen - 1, '\u0000') // ensures null termination
        } else { // out = "source", or "...source"
            if (source!!.get(0) == '@') {
                val l: Int //uint
                source = source!!.next() // skip the `@'
                bufflen -= " '...' ".length + 1 //FIXME: - (uint)
                l = CLib.strlen(source) //(uint)
                CLib.strcpy(out_, CharPtr.Companion.toCharPtr(""))
                if (l > bufflen) {
                    source = CharPtr.Companion.plus(source, l - bufflen) // get last part of file name
                    CLib.strcat(out_, CharPtr.Companion.toCharPtr("..."))
                }
                CLib.strcat(out_, source)
            } else { // out = [string "string"]
                var len: Int =
                    CLib.strcspn(source, CharPtr.Companion.toCharPtr("\n\r")) // stop at first newline  - uint
                bufflen -= " [string \"...\"] ".length + 1 //(uint)
                if (len > bufflen) {
                    len = bufflen
                }
                CLib.strcpy(out_, CharPtr.Companion.toCharPtr("[string \""))
                if (source.get(len) != '\u0000') { // must truncate?
                    CLib.strncat(out_, source, len)
                    CLib.strcat(out_, CharPtr.Companion.toCharPtr("..."))
                } else {
                    CLib.strcat(out_, source)
                }
                CLib.strcat(out_, CharPtr.Companion.toCharPtr("\"]"))
            }
        }
    }

    interface ArrayElement {
        fun set_index(index: Int)
        fun set_array(array: Any?)
    }

    /*
     ** Common Header for all collectable objects (in macro form, to be
     ** included in other objects)
     */
    open class CommonHeader {
        var next: GCObject? = null
        var tt /*Byte*/ /*lu_byte*/: Byte = 0
        var marked /*Byte*/ /*lu_byte*/: Byte = 0
    }

    /*
	 ** Common header in struct form
	 */
    open class GCheader : CommonHeader()

    /*
	 ** Union of all Lua values
	 */
    class Value /*struct ValueCls*/ {
        var gc: GCObject? = null
        var p: Any? = null
        var n /*Double*/ /*lua_Number*/ = 0.0
        var b = 0

        constructor() {}
        constructor(copy: Value) {
            gc = copy.gc
            p = copy.p
            n = copy.n
            b = copy.b
        }

        fun copyFrom(copy: Value) {
            gc = copy.gc
            p = copy.p
            n = copy.n
            b = copy.b
        }
    }

    //
//		 ** Tagged Values
//
///#define TValuefields	Value value; int tt
    open class TValue : ArrayElement {
        private var values: Array<TValue>? = null
        private var index = -1
        var value = Value()
        var tt: Int
        override fun set_index(index: Int) {
            this.index = index
        }

        override fun set_array(array: Any?) {
            values = array as Array<TValue>?
            ClassType.Companion.Assert(values != null)
        }

        //TValue this[int offset] get
        operator fun get(offset: Int): TValue {
            return values!![index + offset]
        }

        constructor() {
            values = null
            index = 0
            value = Value()
            tt = 0
        }

        constructor(value: TValue) {
            values = value.values
            index = value.index
            this.value = Value(value.value) // todo: do a shallow copy here
            tt = value.tt
        }

        //public TValue(TValue[] values)
//{
//	this.values = values;
//	this.index = Array.IndexOf(values, this);
//	this.value = new Value();
//	this.tt = 0;
//}
        constructor(value: Value, tt: Int) {
            values = null
            index = 0
            this.value = Value(value)
            this.tt = tt
        } //public TValue(TValue[] values, Value valueCls, int tt)

        //{
//	this.values = values;
//	this.index = Array.IndexOf(values, this);
//	this.value = new Value(valueCls);
//	this.tt = tt;
//}
        companion object {
            //TValue this[uint offset] get
//public TValue get(uint offset)
//{
//	return this.values[this.index + (int)offset];
//}
            fun plus(value: TValue, offset: Int): TValue {
                return value.values!![value.index + offset]
            }

            //operator +
            fun plus(offset: Int, value: TValue): TValue {
                return value.values!![value.index + offset]
            }

            fun minus(value: TValue?, offset: Int): TValue? {
                return value!!.values!![value.index - offset]
            }

            //operator -
            fun minus(value: TValue, array: Array<TValue?>): Int {
                ClassType.Companion.Assert(value.values == array)
                return value.index
            }

            //operator -
            fun minus(a: TValue?, b: TValue?): Int {
                ClassType.Companion.Assert(a!!.values == b!!.values)
                return a!!.index - b!!.index
            }

            //operator <
            fun lessThan(a: TValue?, b: TValue?): Boolean {
                ClassType.Companion.Assert(a!!.values == b!!.values)
                return a!!.index < b!!.index
            }

            //operator <=
            fun lessEqual(a: TValue, b: TValue): Boolean {
                ClassType.Companion.Assert(a.values == b.values)
                return a.index <= b.index
            }

            //operator >
            fun greaterThan(a: TValue, b: TValue): Boolean {
                ClassType.Companion.Assert(a.values == b.values)
                return a.index > b.index
            }

            //operator >=
            fun greaterEqual(a: TValue, b: TValue): Boolean {
                ClassType.Companion.Assert(a.values == b.values)
                return a.index >= b.index
            }

            fun inc( /*ref*/
                value: Array<TValue?>
            ): TValue? {
                value[0] = value[0]!![1]
                return value[0]!![-1]
            }

            fun dec( /*ref*/
                value: Array<TValue?>
            ): TValue? {
                value[0] = value[0]!![-1]
                return value[0]!![1]
            }

            //implicit operator int
            fun toInt(value: TValue): Int {
                return value.index
            }
        }
    }

    open class Udata_uv : GCObject() {
        var metatable: Table? = null
        var env: Table? = null
        var len /*uint*/ = 0
    }

    class Udata : Udata_uv() {
        /*new*/  var uv: Udata_uv
        //public L_Umaxalign dummy;  /* ensures maximum alignment for `local' udata */
// in the original C code this was allocated alongside the structure memory. it would probably
// be possible to still do that by allocating memory and pinning it down, but we can do the
// same thing just as easily by allocating a seperate byte array for it instead.
        var user_data: Any? = null

        init {
            uv = this
        }
    }

    //typedef TValue *StkId;  /* index to stack elements */
/*
	 ** String headers for string table
	 */
    open class TString_tsv : GCObject() {
        var reserved /*Byte*/ /*lu_byte*/: Byte = 0
        /*FIXME:*/
        var hash /*int*/ /*uint*/: Long = 0
        var len /*uint*/ = 0
    }

    class TString : TString_tsv {
        var str: CharPtr? = null
        //public L_Umaxalign dummy;  /* ensures maximum alignment for strings */
        fun getTsv(): TString_tsv {
            return this
        }

        constructor() {}
        constructor(str: CharPtr?) {
            this.str = str
        }

        override fun toString(): String {
            return str.toString()
        } // for debugging
    }

    /*
	 ** Function Prototypes
	 */
    class Proto : GCObject() {
        var protos: Array<Proto?>? = null
        var index = 0
        var k: Array<TValue?>? = null /* constants used by the function */
        var code: LongArray? = null /*UInt32[]*/ /*Instruction[]*/
        var p: Array<Proto?>? = null /*new*/  /* functions defined inside the function */
        var lineinfo: IntArray? = null /* map from opcodes to source lines */
        var locvars: Array<LocVar?>? = null /* information about local variables */
        var upvalues: Array<TString?>? = null /* upvalue names */
        var source: TString? = null
        var sizeupvalues = 0
        var sizek = 0 /* size of `k' */
        var sizecode = 0
        var sizelineinfo = 0
        var sizep = 0 /* size of `p' */
        var sizelocvars = 0
        var linedefined = 0
        var lastlinedefined = 0
        var gclist: GCObject? = null
        var nups: Byte = 0 /*Byte*/ /*lu_byte*/ /* number of upvalues */
        var numparams: Byte = 0 /*Byte*/ /*lu_byte*/
        var is_vararg: Byte = 0 /*Byte*/ /*lu_byte*/
        var maxstacksize: Byte = 0 /*Byte*/ /*lu_byte*/
        //Proto this[int offset] get
        operator fun get(offset: Int): Proto {
            return protos!![index + offset]!!
        }
    }

    class LocVar {
        var varname: TString? = null
        var startpc = 0 /* first point where variable is active */
        var endpc = 0 /* first point where variable is dead */
    }

    /*
	 ** Upvalues
	 */
    class UpVal : GCObject() {
        class _u {
            class _l {
                /* double linked list (when open) */
                var prev: UpVal? = null
                var next: UpVal? = null
            }

            var value = TValue() /* the value (when closed) */
            var l = _l()
        }

        /*new*/  var u = LuaObject.UpVal._u()
        var v /* points to stack or to its own value */: TValue? = null
    }

    /*
     ** Closures
     */
    open class ClosureHeader : GCObject() {
        var isC /*Byte*/ /*lu_byte*/: Byte = 0
        var nupvalues /*Byte*/ /*lu_byte*/: Byte = 0
        var gclist: GCObject? = null
        var env: Table? = null
    }

    open class ClosureType(private val header: ClosureHeader) {
        /*Byte*/ /*lu_byte*/
        fun getIsC(): Byte {
            return header.isC
        }

        /*Byte*/ /*lu_byte*/
        fun setIsC(`val`: Byte) {
            header.isC = `val`
        }

        /*Byte*/ /*lu_byte*/
        fun getNupvalues(): Byte {
            return header.nupvalues
        }

        /*Byte*/ /*lu_byte*/
        fun setNupvalues(`val`: Byte) {
            header.nupvalues = `val`
        }

        fun getGclist(): GCObject? {
            return header.gclist
        }

        fun setGclist(`val`: GCObject?) {
            header.gclist = `val`
        }

        fun getEnv(): Table? {
            return header.env
        }

        fun setEnv(`val`: Table?) {
            header.env = `val`
        }

        companion object {
            //implicit operator ClosureHeader
            fun toClosureHeader(ctype: ClosureType): ClosureHeader {
                return ctype.header
            }
        }

    }

    class CClosure(header: ClosureHeader) : ClosureType(header) {
        var f: lua_CFunction? = null
        var upvalue: Array<TValue?>? = null
    }

    class LClosure(header: ClosureHeader) : ClosureType(header) {
        var p: Proto? = null
        var upvals: Array<UpVal?>? = null
    }

    class Closure : ClosureHeader() {
        var c: CClosure
        var l: LClosure

        init {
            c = CClosure(this)
            l = LClosure(this)
        }
    }

    /*
	 ** Tables
	 */
    class TKey_nk : TValue {
        var next /* for chaining */: Node? = null

        constructor() {}
        constructor(value: Value, tt: Int, next: Node?) : super(Value(value), tt) {
            this.next = next
        }
    }

    class TKey {
        var nk = TKey_nk()

        constructor() {
            nk = TKey_nk()
        }

        constructor(copy: TKey) {
            nk = TKey_nk(Value(copy.nk.value), copy.nk.tt, copy.nk.next)
        }

        constructor(value: Value, tt: Int, next: Node?) {
            nk = TKey_nk(Value(value), tt, next)
        }

        fun getTvk(): TValue {
            return nk
        }
    }

    class Node : ArrayElement {
        private var values: Array<Node>? = null
        private var index = -1
        var id: Int = LuaObject.Node.Companion.ids++
        var i_val: TValue
        var i_key: TKey
        override fun set_index(index: Int) {
            this.index = index
        }

        override fun set_array(array: Any?) {
            values = array as Array<Node>?
            ClassType.Companion.Assert(values != null)
        }

        constructor() {
            i_val = TValue()
            i_key = TKey()
        }

        constructor(copy: Node) {
            values = copy.values
            index = copy.index
            i_val = TValue(copy.i_val)
            i_key = TKey(copy.i_key)
        }

        constructor(i_val: TValue, i_key: TKey) {
            values = arrayOf(this)
            index = 0
            this.i_val = i_val
            this.i_key = i_key
        }

        //Node this[int offset]
        operator fun get(offset: Int): Node {
            return values!![index + offset]
        }

        override fun equals(o: Any?): Boolean { //return this == (Node)o;
            return LuaObject.Node.Companion.isEqual(this, o as Node?)
        }

        override fun hashCode(): Int {
            return 0
        }

        companion object {
            var ids = 0
            //Node this[uint offset]
//public Node get(uint offset)
//{
//    return this.values[this.index + (int)offset];
//}
//operator -
            fun minus(n1: Node, n2: Node): Int {
                ClassType.Companion.Assert(n1.values == n2.values)
                return n1.index - n2.index
            }

            fun inc( /*ref*/
                node: Array<Node>
            ): Node {
                node[0] = node[0][1]
                return node[0][-1]
            }

            fun dec( /*ref*/
                node: Array<Node>
            ): Node {
                node[0] = node[0][-1]
                return node[0][1]
            }

            //operator >
            fun greaterThan(n1: Node, n2: Node): Boolean {
                ClassType.Companion.Assert(n1.values == n2.values)
                return n1.index > n2.index
            }

            //operator >=
            fun greaterEqual(n1: Node, n2: Node): Boolean {
                ClassType.Companion.Assert(n1.values == n2.values)
                return n1.index >= n2.index
            }

            //operator <
            fun lessThan(n1: Node, n2: Node): Boolean {
                ClassType.Companion.Assert(n1.values == n2.values)
                return n1.index < n2.index
            }

            //operator <=
            fun lessEqual(n1: Node, n2: Node): Boolean {
                ClassType.Companion.Assert(n1.values == n2.values)
                return n1.index <= n2.index
            }

            //operator ==
            fun isEqual(n1: Node?, n2: Node?): Boolean {
                val o1: Any? = n1
                val o2: Any? = n2
                if (o1 == null && o2 == null) {
                    return true
                }
                if (o1 == null) {
                    return false
                }
                if (o2 == null) {
                    return false
                }
                return if (n1!!.values != n2!!.values) {
                    false
                } else n1!!.index == n2!!.index
            }

            //operator !=
            fun isNotEqual(n1: Node?, n2: Node?): Boolean { //return !(n1 == n2);
                return !LuaObject.Node.Companion.isEqual(n1, n2)
            }
        }
    }

    class Table : GCObject() {
        var flags: Byte = 0 /*Byte*/ /*lu_byte*/ /* 1<<p means tagmethod(p) is not present */
        var lsizenode: Byte = 0 /*Byte*/ /*lu_byte*/ /* log2 of size of `node' array */
        var metatable: Table? = null
        var array: Array<TValue?>? = null /* array part */
        var node: Array<Node?>? = null
        var lastfree /* any free position is before this position */ = 0
        var gclist: GCObject? = null
        var sizearray /* size of `array' array */ = 0
    }
}