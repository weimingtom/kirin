package kirin
import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.LuaObject.TValue
import kirin.LuaState.CallInfo
import kirin.LuaObject.Proto
import kirin.LuaObject.Table
import kirin.LuaTM.TMS
import kirin.LuaState.GCObject
import kirin.LuaState.global_State
import kirin.LuaObject.GCheader
import kirin.LuaObject.TString
import kirin.LuaObject.Udata_uv
import kirin.LuaObject.UpVal
import kirin.LuaState.GCObjectRef
import kirin.LuaState.NextRef
import kirin.LuaState.OpenValRef
import kirin.LuaObject.Udata
import kirin.LuaState.RootGCRef
import kirin.LuaState.ArrayRef
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor

//
// ** $Id: lgc.c,v 2.38.1.1 2007/12/27 13:02:25 roberto Exp $
// ** Garbage Collector
// ** See Copyright Notice in lua.h
//
//using lu_int32 = System.UInt32;
//using l_mem = System.Int32;
//using lu_mem = System.UInt32;
//using TValue = Lua.TValue;
//using StkId = TValue;
//using lu_byte = System.Byte;
//using Instruction = System.UInt32;
object LuaGC {
    //
//		 ** Possible states of the Garbage Collector
//
    const val GCSpause = 0
    const val GCSpropagate = 1
    const val GCSsweepstring = 2
    const val GCSsweep = 3
    const val GCSfinalize = 4
    //
//		 ** some userful bit tricks
//
    fun resetbits(x: ByteArray, m: Int): Int { //lu_byte - ref
        x[0] = x[0] and m.inv().toByte() //lu_byte
        return x[0].toInt()
    }

    fun setbits(x: ByteArray, m: Int): Int { //lu_byte - ref
        x[0] = x[0] or m.toByte() //lu_byte
        return x[0].toInt()
    }

    fun testbits(x: Byte, m: Int): Boolean { //lu_byte
        return (x and m.toByte()) != 0.toByte() //lu_byte
    }

    fun bitmask(b: Int): Int {
        return 1 shl b
    }

    fun bit2mask(b1: Int, b2: Int): Int {
        return bitmask(b1) or bitmask(b2)
    }

    fun l_setbit(x: ByteArray, b: Int): Int { //lu_byte - ref
        return setbits(x, bitmask(b)) //ref
    }

    fun resetbit(x: ByteArray, b: Int): Int { //lu_byte - ref
        return resetbits(x, bitmask(b)) //ref
    }

    fun testbit(x: Byte, b: Int): Boolean { //lu_byte
        return testbits(x, bitmask(b))
    }

    fun set2bits(x: ByteArray, b1: Int, b2: Int): Int { //lu_byte - ref
        return setbits(x, bit2mask(b1, b2)) //ref
    }

    fun reset2bits(x: ByteArray, b1: Int, b2: Int): Int { //lu_byte - ref
        return resetbits(x, bit2mask(b1, b2)) //ref
    }

    fun test2bits(x: Byte, b1: Int, b2: Int): Boolean { //lu_byte
        return testbits(x, bit2mask(b1, b2))
    }

    //
//		 ** Layout for bit use in `marked' field:
//		 ** bit 0 - object is white (type 0)
//		 ** bit 1 - object is white (type 1)
//		 ** bit 2 - object is black
//		 ** bit 3 - for userdata: has been finalized
//		 ** bit 3 - for tables: has weak keys
//		 ** bit 4 - for tables: has weak values
//		 ** bit 5 - object is fixed (should not be collected)
//		 ** bit 6 - object is "super" fixed (only the main thread)
//
    const val WHITE0BIT = 0
    const val WHITE1BIT = 1
    const val BLACKBIT = 2
    const val FINALIZEDBIT = 3
    const val KEYWEAKBIT = 3
    const val VALUEWEAKBIT = 4
    const val FIXEDBIT = 5
    const val SFIXEDBIT = 6
    val WHITEBITS = bit2mask(WHITE0BIT, WHITE1BIT)
    fun iswhite(x: GCObject?): Boolean {
        return test2bits(x!!.getGch().marked, WHITE0BIT, WHITE1BIT)
    }

    fun isblack(x: GCObject): Boolean {
        return testbit(x.getGch().marked, BLACKBIT)
    }

    fun isgray(x: GCObject): Boolean {
        return !isblack(x) && !iswhite(x)
    }

    fun otherwhite(g: global_State?): Int {
        return (g!!.currentwhite xor WHITEBITS.toByte()).toInt()
    }

    fun isdead(g: global_State?, v: GCObject?): Boolean {
        return (v!!.getGch().marked and otherwhite(g).toByte() and WHITEBITS.toByte()) != 0.toByte()
    }

    fun changewhite(x: GCObject) {
        x.getGch().marked = x.getGch().marked xor WHITEBITS.toByte()
    }

    fun gray2black(x: GCObject) {
        val marked_ref = ByteArray(1)
        val gcheader: GCheader = x.getGch()
        marked_ref[0] = gcheader.marked
        l_setbit(marked_ref, BLACKBIT) //ref
        gcheader.marked = marked_ref[0]
    }

    fun valiswhite(x: TValue?): Boolean {
        return LuaObject.iscollectable(x) && iswhite(LuaObject.gcvalue(x))
    }

    fun luaC_white(g: global_State?): Byte {
        return (g!!.currentwhite and WHITEBITS.toByte())
    }

    fun luaC_checkGC(L: lua_State?) { //condhardstacktests(luaD_reallocstack(L, L.stacksize - EXTRA_STACK - 1));
//luaD_reallocstack(L, L.stacksize - EXTRA_STACK - 1);
        if (LuaState.G(L)!!.totalbytes >= LuaState.G(L)!!.GCthreshold) {
            luaC_step(L)
        }
    }

    fun luaC_barrier(L: lua_State?, p: Any, v: TValue?) {
        if (valiswhite(v) && isblack(LuaState.obj2gco(p))) {
            luaC_barrierf(L, LuaState.obj2gco(p), LuaObject.gcvalue(v))
        }
    }

    fun luaC_barriert(L: lua_State?, t: Table, v: TValue?) {
        if (valiswhite(v) && isblack(LuaState.obj2gco(t))) {
            luaC_barrierback(L, t)
        }
    }

    fun luaC_objbarrier(L: lua_State?, p: Any?, o: Any?) {
        if (iswhite(LuaState.obj2gco(o!!)) && isblack(LuaState.obj2gco(p!!))) {
            luaC_barrierf(L, LuaState.obj2gco(p), LuaState.obj2gco(o))
        }
    }

    fun luaC_objbarriert(L: lua_State?, t: Table, o: Any) {
        if (iswhite(LuaState.obj2gco(o)) && isblack(LuaState.obj2gco(t))) {
            luaC_barrierback(L, t)
        }
    }

    const val GCSTEPSIZE = 1024 //uint
    const val GCSWEEPMAX = 40
    const val GCSWEEPCOST = 10
    const val GCFINALIZECOST = 100
    var maskmarks = (bitmask(BLACKBIT) or WHITEBITS).inv().toByte()
    fun makewhite(g: global_State?, x: GCObject?) {
        x!!.getGch().marked = (x!!.getGch().marked and maskmarks or luaC_white(g))
    }

    fun white2gray(x: GCObject?) {
        val marked_ref = ByteArray(1)
        val gcheader: GCheader = x!!.getGch()
        marked_ref[0] = gcheader.marked
        reset2bits(marked_ref, WHITE0BIT, WHITE1BIT) //ref
        gcheader.marked = marked_ref[0]
    }

    fun black2gray(x: GCObject) {
        val marked_ref = ByteArray(1)
        val gcheader: GCheader = x.getGch()
        marked_ref[0] = gcheader.marked
        resetbit(marked_ref, BLACKBIT) //ref
        gcheader.marked = marked_ref[0]
    }

    fun stringmark(s: TString?) {
        val marked_ref = ByteArray(1)
        val gcheader: GCheader = s!!.getGch()
        marked_ref[0] = gcheader.marked
        reset2bits(marked_ref, WHITE0BIT, WHITE1BIT) //ref
        gcheader.marked = marked_ref[0]
    }

    fun isfinalized(u: Udata_uv): Boolean {
        return testbit(u.marked, FINALIZEDBIT)
    }

    fun markfinalized(u: Udata_uv) {
        var marked: Byte = u.marked // can't pass properties in as ref - lu_byte
        val marked_ref = ByteArray(1)
        marked_ref[0] = marked
        l_setbit(marked_ref, FINALIZEDBIT) //ref
        marked = marked_ref[0]
        u.marked = marked
    }

    var KEYWEAK = bitmask(KEYWEAKBIT)
    var VALUEWEAK = bitmask(VALUEWEAKBIT)
    fun markvalue(g: global_State?, o: TValue?) {
        LuaObject.checkconsistency(o)
        if (LuaObject.iscollectable(o) && iswhite(LuaObject.gcvalue(o))) {
            reallymarkobject(g, LuaObject.gcvalue(o))
        }
    }

    fun markobject(g: global_State?, t: Any?) {
        if (iswhite(LuaState.obj2gco(t!!))) {
            reallymarkobject(g, LuaState.obj2gco(t))
        }
    }

    fun setthreshold(g: global_State) {
        g.GCthreshold = g.estimate / 100 * g.gcpause //(uint)
    }

    private fun removeentry(n: LuaObject.Node) {
        LuaLimits.lua_assert(LuaObject.ttisnil(LuaTable.gval(n)))
        if (LuaObject.iscollectable(LuaTable.gkey(n))) {
            LuaObject.setttype(LuaTable.gkey(n), LuaObject.LUA_TDEADKEY) // dead key; remove it
        }
    }

    private fun reallymarkobject(g: global_State?, o: GCObject?) {
        LuaLimits.lua_assert(iswhite(o) && !isdead(g, o))
        white2gray(o)
        when (o!!.getGch().tt.toInt()) {
            Lua.LUA_TSTRING -> {
                return
            }
            Lua.LUA_TUSERDATA -> {
                val mt: Table? = LuaState.gco2u(o).metatable
                gray2black(o) // udata are never gray
                if (mt != null) {
                    markobject(g, mt)
                }
                markobject(g, LuaState.gco2u(o).env)
                return
            }
            LuaObject.LUA_TUPVAL -> {
                val uv: UpVal? = LuaState.gco2uv(o)
                markvalue(g, uv!!.v)
                if (uv!!.v === uv!!.u.value) { // closed?
                    gray2black(o) // open upvalues are never black
                }
                return
            }
            Lua.LUA_TFUNCTION -> {
                LuaState.gco2cl(o)!!.c.setGclist(g!!.gray)
                g.gray = o
            }
            Lua.LUA_TTABLE -> {
                LuaState.gco2h(o).gclist = g!!.gray
                g.gray = o
            }
            Lua.LUA_TTHREAD -> {
                LuaState.gco2th(o)!!.gclist = g!!.gray
                g.gray = o
            }
            LuaObject.LUA_TPROTO -> {
                LuaState.gco2p(o)!!.gclist = g!!.gray
                g!!.gray = o
            }
            else -> {
                LuaLimits.lua_assert(0)
            }
        }
    }

    private fun marktmu(g: global_State?) {
        var u: GCObject? = g!!.tmudata
        if (u != null) {
            do {
                u = u!!.getGch().next
                makewhite(g, u) // may be marked, if left from previous GC
                reallymarkobject(g, u)
            } while (u !== g.tmudata)
        }
    }

    // move `dead' udata that need finalization to list `tmudata'
    fun luaC_separateudata(L: lua_State?, all: Int): Int { //uint
        val g: global_State = LuaState.G(L)!!
        var deadmem = 0 //uint
        var p: GCObjectRef = NextRef(g.mainthread!!)
        var curr: GCObject? = null
        while (p.get().also({ curr = it }) != null) {
            if (!(iswhite(curr) || all != 0) || isfinalized(LuaState.gco2u(curr!!))) {
                p = NextRef(curr!!.getGch()) // don't bother with them
            } else if (LuaTM.fasttm(L, LuaState.gco2u(curr!!).metatable, TMS.TM_GC) == null) {
                markfinalized(LuaState.gco2u(curr!!)) // don't need finalization
                p = NextRef(curr!!.getGch())
            } else { // must call its gc method
                deadmem += LuaString.sizeudata(LuaState.gco2u(curr!!)) //(uint)
                markfinalized(LuaState.gco2u(curr!!))
                p.set(curr!!.getGch().next!!)
                // link `curr' at the end of `tmudata' list
                if (g.tmudata == null) { // list is empty?
                    curr!!.getGch().next = curr
                    g.tmudata = curr!!.getGch().next // creates a circular list
                } else {
                    curr!!.getGch().next = g.tmudata!!.getGch().next
                    g.tmudata!!.getGch().next = curr
                    g.tmudata = curr
                }
            }
        }
        return deadmem
    }

    private fun traversetable(g: global_State, h: Table): Int {
        var i: Int
        var weakkey = 0
        var weakvalue = 0
        //const
        val mode: TValue?
        if (h.metatable != null) {
            markobject(g, h.metatable)
        }
        mode = LuaTM.gfasttm(g, h.metatable, TMS.TM_MODE)
        if (mode != null && LuaObject.ttisstring(mode)) { // is there a weak mode?
            weakkey = if (CharPtr.Companion.isNotEqual(CLib.strchr(LuaObject.svalue(mode), 'k'), null)) 1 else 0
            weakvalue = if (CharPtr.Companion.isNotEqual(CLib.strchr(LuaObject.svalue(mode), 'v'), null)) 1 else 0
            if (weakkey != 0 || weakvalue != 0) { // is really weak?
                h.marked = h.marked and (KEYWEAK or VALUEWEAK).inv().toByte() // clear bits
                h.marked =
                    h.marked or LuaLimits.cast_byte(weakkey shl KEYWEAKBIT or (weakvalue shl VALUEWEAKBIT))
                h.gclist = g.weak // must be cleared after GC,...
                g.weak = LuaState.obj2gco(h) //... so put in the appropriate list
            }
        }
        if (weakkey != 0 && weakvalue != 0) {
            return 1
        }
        if (weakvalue == 0) {
            i = h.sizearray
            while (i-- != 0) {
                markvalue(g, h.array!!.get(i))
            }
        }
        i = LuaObject.sizenode(h)
        while (i-- != 0) {
            val n: LuaObject.Node = LuaTable.gnode(h, i)
            LuaLimits.lua_assert(
                LuaObject.ttype(LuaTable.gkey(n)) != LuaObject.LUA_TDEADKEY || LuaObject.ttisnil(
                    LuaTable.gval(n)
                )
            )
            if (LuaObject.ttisnil(LuaTable.gval(n))) {
                removeentry(n) // remove empty entries
            } else {
                LuaLimits.lua_assert(LuaObject.ttisnil(LuaTable.gkey(n)))
                if (weakkey == 0) {
                    markvalue(g, LuaTable.gkey(n))
                }
                if (weakvalue == 0) {
                    markvalue(g, LuaTable.gval(n))
                }
            }
        }
        return if (weakkey != 0 || weakvalue != 0) 1 else 0
    }

    //
//		 ** All marks are conditional because a GC may happen while the
//		 ** prototype is still being created
//
    private fun traverseproto(g: global_State, f: Proto?) {
        var i: Int
        if (f!!.source != null) {
            stringmark(f!!.source)
        }
        i = 0
        while (i < f!!.sizek) {
            // mark literals
            markvalue(g, f!!.k!!.get(i))
            i++
        }
        i = 0
        while (i < f!!.sizeupvalues) {
            // mark upvalue names
            if (f!!.upvalues!!.get(i) != null) {
                stringmark(f!!.upvalues!!.get(i))
            }
            i++
        }
        i = 0
        while (i < f!!.sizep) {
            // mark nested protos
            if (f!!.p!!.get(i) != null) {
                markobject(g, f!!.p!!.get(i))
            }
            i++
        }
        i = 0
        while (i < f.sizelocvars) {
            // mark local-variable names
            if (f.locvars!!.get(i)!!.varname != null) {
                stringmark(f.locvars!!.get(i)!!.varname)
            }
            i++
        }
    }

    private fun traverseclosure(g: global_State, cl: LuaObject.Closure?) {
        markobject(g, cl!!.c.getEnv())
        if (cl!!.c.getIsC().toInt() != 0) {
            var i: Int
            i = 0
            while (i < cl!!.c.getNupvalues()) {
                // mark its upvalues
                markvalue(g, cl!!.c.upvalue!!.get(i))
                i++
            }
        } else {
            var i: Int
            LuaLimits.lua_assert(cl!!.l.getNupvalues() == cl!!.l.p!!.nups)
            markobject(g, cl!!.l.p)
            i = 0
            while (i < cl!!.l.getNupvalues()) {
                // mark its upvalues
                markobject(g, cl!!.l.upvals!!.get(i))
                i++
            }
        }
    }

    private fun checkstacksizes(L: lua_State?, max: TValue) { //StkId
        val ci_used: Int =
            LuaLimits.cast_int(CallInfo.Companion.minus(L!!.ci!!, L!!.base_ci!!.get(0)!!)) // number of `ci' in use
        val s_used: Int = LuaLimits.cast_int(TValue.Companion.minus(max, L.stack!!)) // part of stack in use
        if (L.size_ci > LuaConf.LUAI_MAXCALLS) { // handling overflow?
            return  // do not touch the stacks
        }
        if (4 * ci_used < L.size_ci && 2 * LuaState.BASIC_CI_SIZE < L.size_ci) {
            LuaDo.luaD_reallocCI(L, L.size_ci / 2) // still big enough...
        }
        //condhardstacktests(luaD_reallocCI(L, ci_used + 1));
        if (4 * s_used < L.stacksize && 2 * (LuaState.BASIC_STACK_SIZE + LuaState.EXTRA_STACK) < L.stacksize) {
            LuaDo.luaD_reallocstack(L, L.stacksize / 2) // still big enough...
        }
        //condhardstacktests(luaD_reallocstack(L, s_used));
    }

    private fun traversestack(g: global_State, l: lua_State?) {
        val o: Array<TValue?> = arrayOfNulls<TValue>(1) //StkId
        o[0] = TValue()
        var lim: TValue //StkId
        val ci: Array<CallInfo?> = arrayOfNulls<CallInfo>(1)
        ci[0] = CallInfo()
        markvalue(g, LuaState.gt(l))
        lim = l!!.top!!
        ci[0] = l!!.base_ci!!.get(0)
        while (CallInfo.Companion.lessEqual(ci[0]!!, l.ci!!)) {
            //ref
            LuaLimits.lua_assert(TValue.Companion.lessEqual(ci[0]!!.top!!, l.stack_last!!))
            if (TValue.Companion.lessThan(lim, ci[0]!!.top!!)) {
                lim = ci[0]!!.top!!
            }
            CallInfo.Companion.inc(ci)
        }
        o[0] = l.stack!!.get(0)
        while (TValue.Companion.lessThan(o[0]!!, l.top!!)) {
            //ref - StkId
            markvalue(g, o[0])
            TValue.Companion.inc(o)
        }
        while (TValue.Companion.lessEqual(o[0]!!, lim)) {
            //ref - StkId
            LuaObject.setnilvalue(o[0])
            TValue.Companion.inc(o)
        }
        checkstacksizes(l, lim)
    }

    //
//		 ** traverse one gray object, turning it to black.
//		 ** Returns `quantity' traversed.
//
    private fun propagatemark(g: global_State): Int { //l_mem - Int32
        val o: GCObject = g.gray!!
        LuaLimits.lua_assert(isgray(o))
        gray2black(o)
        return when (o.getGch().tt.toInt()) {
            Lua.LUA_TTABLE -> {
                val h: Table = LuaState.gco2h(o)
                g.gray = h.gclist
                if (traversetable(g, h) != 0) { // table is weak?
                    black2gray(o) // keep it gray
                }
                CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_TABLE)) + CLib.GetUnmanagedSize(
                    ClassType(ClassType.Companion.TYPE_TVALUE)
                ) * h.sizearray + CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_NODE)) * LuaObject.sizenode(
                    h
                ) //typeof(Node) - typeof(TValue) - typeof(Table)
            }
            Lua.LUA_TFUNCTION -> {
                val cl: LuaObject.Closure = LuaState.gco2cl(o)!!
                g.gray = cl!!.c.getGclist()
                traverseclosure(g, cl)
                if (cl!!.c.getIsC().toInt() != 0) LuaFunc.sizeCclosure(cl!!.c.getNupvalues().toInt()) else LuaFunc.sizeLclosure(
                    cl!!.l.getNupvalues().toInt()
                )
            }
            Lua.LUA_TTHREAD -> {
                val th: lua_State = LuaState.gco2th(o)!!
                g.gray = th.gclist
                th.gclist = g.grayagain
                g.grayagain = o
                black2gray(o)
                traversestack(g, th)
                //typeof(lua_State)
//typeof(TValue)
//typeof(CallInfo)
                CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_LUA_STATE)) + CLib.GetUnmanagedSize(
                    ClassType(ClassType.Companion.TYPE_TVALUE)
                ) * th.stacksize + CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_CALLINFO)) * th.size_ci
            }
            LuaObject.LUA_TPROTO -> {
                val p: Proto = LuaState.gco2p(o)!!
                g.gray = p.gclist
                traverseproto(g, p)
                //typeof(Proto)
//typeof(long/*UInt32*//*Instruction*/)
//typeof(Proto)
//typeof(TValue)
//typeof(int)
//typeof(LocVar)
//typeof(TString)
                CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_PROTO)) + CLib.GetUnmanagedSize(
                    ClassType(ClassType.Companion.TYPE_LONG)
                ) * p.sizecode + CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_PROTO)) * p.sizep + CLib.GetUnmanagedSize(
                    ClassType(ClassType.Companion.TYPE_TVALUE)
                ) * p.sizek + CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_INT)) * p.sizelineinfo + CLib.GetUnmanagedSize(
                    ClassType(ClassType.Companion.TYPE_LOCVAR)
                ) * p.sizelocvars + CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_TSTRING)) * p.sizeupvalues
            }
            else -> {
                LuaLimits.lua_assert(0)
                0
            }
        }
    }

    private fun propagateall(g: global_State?): Int { //uint
        var m = 0 //uint
        while (g!!.gray != null) {
            m += propagatemark(g!!) //(uint)
        }
        return m
    }

    //
//		 ** The next function tells whether a key or value can be cleared from
//		 ** a weak table. Non-collectable objects are never removed from weak
//		 ** tables. Strings behave as `values', so are never removed too. for
//		 ** other objects: if really collected, cannot keep them; for userdata
//		 ** being finalized, keep them in keys, but not in values
//
    private fun iscleared(o: TValue, iskey: Boolean): Boolean {
        if (!LuaObject.iscollectable(o)) {
            return false
        }
        if (LuaObject.ttisstring(o)) {
            stringmark(LuaObject.rawtsvalue(o)) // strings are `values', so are never weak
            return false
        }
        return iswhite(LuaObject.gcvalue(o)) || LuaObject.ttisuserdata(o) && !iskey && isfinalized(
            LuaObject.uvalue(o)
        )
    }

    //
//		 ** clear collected entries from weaktables
//
    private fun cleartable(l: GCObject) {
        var l: GCObject? = l
        while (l != null) {
            val h: Table = LuaState.gco2h(l)
            var i: Int = h.sizearray
            LuaLimits.lua_assert(
                testbit(h.marked, VALUEWEAKBIT) || testbit(
                    h.marked,
                    KEYWEAKBIT
                )
            )
            if (testbit(h.marked, VALUEWEAKBIT)) {
                while (i-- != 0) {
                    val o: TValue = h.array!!.get(i)!!
                    if (iscleared(o, false)) { // value was collected?
                        LuaObject.setnilvalue(o) // remove value
                    }
                }
            }
            i = LuaObject.sizenode(h)
            while (i-- != 0) {
                val n: LuaObject.Node = LuaTable.gnode(h, i)
                if (!LuaObject.ttisnil(LuaTable.gval(n)) && (iscleared(
                        LuaTable.key2tval(n),
                        true
                    ) || iscleared(LuaTable.gval(n), false))
                ) { // non-empty entry?
                    LuaObject.setnilvalue(LuaTable.gval(n)) // remove value...
                    removeentry(n) // remove entry from Table
                }
            }
            l = h.gclist
        }
    }

    private fun freeobj(L: lua_State, o: GCObject) {
        when (o.getGch().tt.toInt()) {
            LuaObject.LUA_TPROTO -> {
                LuaFunc.luaF_freeproto(L, LuaState.gco2p(o)!!)
            }
            Lua.LUA_TFUNCTION -> {
                LuaFunc.luaF_freeclosure(L, LuaState.gco2cl(o)!!)
            }
            LuaObject.LUA_TUPVAL -> {
                LuaFunc.luaF_freeupval(L, LuaState.gco2uv(o)!!)
            }
            Lua.LUA_TTABLE -> {
                LuaTable.luaH_free(L, LuaState.gco2h(o))
            }
            Lua.LUA_TTHREAD -> {
                LuaLimits.lua_assert(LuaState.gco2th(o) !== L && LuaState.gco2th(o) !== LuaState.G(L)!!.mainthread)
                LuaState.luaE_freethread(L, LuaState.gco2th(o))
            }
            Lua.LUA_TSTRING -> {
                LuaState.G(L)!!.strt.nuse--
                LuaMem.SubtractTotalBytes(L, LuaString.sizestring(LuaState.gco2ts(o)))
                LuaMem.luaM_freemem_TString(L, LuaState.gco2ts(o), ClassType(ClassType.Companion.TYPE_TSTRING))
            }
            Lua.LUA_TUSERDATA -> {
                LuaMem.SubtractTotalBytes(L, LuaString.sizeudata(LuaState.gco2u(o)))
                LuaMem.luaM_freemem_Udata(L, LuaState.gco2u(o), ClassType(ClassType.Companion.TYPE_UDATA))
            }
            else -> {
                LuaLimits.lua_assert(0)
            }
        }
    }

    fun sweepwholelist(L: lua_State, p: GCObjectRef) {
        sweeplist(L, p, LuaLimits.MAX_LUMEM.toLong())
    }

    private fun sweeplist(L: lua_State, p: GCObjectRef, count: Long): GCObjectRef { //lu_mem - UInt32
        var p: GCObjectRef = p
        var count = count
        var curr: GCObject? = null
        val g: global_State = LuaState.G(L)!!
        val deadmask = otherwhite(g)
        while (p.get().also({ curr = it }) != null && count-- > 0) {
            if (curr!!.getGch().tt.toInt() == Lua.LUA_TTHREAD) { // sweep open upvalues of each thread
                sweepwholelist(L, OpenValRef(LuaState.gco2th(curr!!)!!))
            }
            if ((curr!!.getGch().marked xor WHITEBITS.toByte() and deadmask.toByte()) != 0.toByte()) { // not dead?
                LuaLimits.lua_assert(isdead(g, curr) || testbit(curr!!.getGch().marked, FIXEDBIT))
                makewhite(g, curr) // make it white (for next cycle)
                p = NextRef(curr!!.getGch())
            } else { // must erase `curr'
                LuaLimits.lua_assert(isdead(g, curr) || deadmask == bitmask(SFIXEDBIT))
                p.set(curr!!.getGch().next!!)
                if (curr === g.rootgc) { // is the first element of the list?
                    g.rootgc = curr!!.getGch().next // adjust first
                }
                freeobj(L, curr!!)
            }
        }
        return p
    }

    private fun checkSizes(L: lua_State?) {
        val g: global_State = LuaState.G(L)!!
        // check size of string hash
        if (g.strt.nuse < (g.strt.size / 4) as Long && g.strt.size > LuaLimits.MINSTRTABSIZE * 2) { //lu_int32 - UInt32
            LuaString.luaS_resize(L, g.strt.size / 2) // table is too big
        }
        // check size of buffer
        if (LuaZIO.luaZ_sizebuffer(g.buff) > LuaLimits.LUA_MINBUFFER * 2) { // buffer too big?
            val newsize: Int = LuaZIO.luaZ_sizebuffer(g.buff) / 2 //uint
            LuaZIO.luaZ_resizebuffer(L, g.buff, newsize) //(int)
        }
    }

    private fun GCTM(L: lua_State?) {
        val g: global_State = LuaState.G(L)!!
        val o: GCObject = g.tmudata!!.getGch().next!! // get first element
        val udata: Udata = LuaState.rawgco2u(o)!!
        val tm: TValue?
        // remove udata from `tmudata'
        if (o === g.tmudata) { // last element?
            g.tmudata = null
        } else {
            g.tmudata!!.getGch().next = udata.uv.next
        }
        udata.uv.next = g.mainthread!!.next // return it to `root' list
        g.mainthread!!.next = o
        makewhite(g, o)
        tm = LuaTM.fasttm(L, udata.uv.metatable, TMS.TM_GC)
        if (tm != null) {
            val oldah: Byte = L!!.allowhook //lu_byte
            val oldt = g.GCthreshold as Long //lu_mem - UInt32 - lu_mem - UInt32
            L.allowhook = 0 // stop debug hooks during GC tag method
            g.GCthreshold = 2 * g.totalbytes // avoid GC steps
            LuaObject.setobj2s(L, L.top, tm)
            LuaObject.setuvalue(L, TValue.Companion.plus(L.top!!, 1), udata)
            L.top = TValue.Companion.plus(L.top!!, 2)
            LuaDo.luaD_call(L, TValue.Companion.minus(L.top!!, 2)!!, 0)
            L.allowhook = oldah // restore hooks
            g.GCthreshold = oldt // restore threshold  - (uint)
        }
    }

    //
//		 ** Call all GC tag methods
//
    fun luaC_callGCTM(L: lua_State?) {
        while (LuaState.G(L)!!.tmudata != null) {
            GCTM(L)
        }
    }

    fun luaC_freeall(L: lua_State?) {
        val g: global_State = LuaState.G(L)!!
        var i: Int
        g.currentwhite =
            (WHITEBITS or bitmask(SFIXEDBIT)).toByte() // mask to collect all elements
        sweepwholelist(L!!, RootGCRef(g))
        i = 0
        while (i < g.strt.size) {
            // free all string lists
            sweepwholelist(L, ArrayRef(g.strt.hash, i))
            i++
        }
    }

    private fun markmt(g: global_State?) {
        var i: Int
        i = 0
        while (i < LuaObject.NUM_TAGS) {
            if (g!!.mt.get(i) != null) {
                markobject(g, g!!.mt.get(i))
            }
            i++
        }
    }

    // mark root set
    private fun markroot(L: lua_State) {
        val g: global_State = LuaState.G(L)!!
        g.gray = null
        g.grayagain = null
        g.weak = null
        markobject(g, g.mainthread)
        // make global table be traversed before main stack
        markvalue(g, LuaState.gt(g.mainthread))
        markvalue(g, LuaState.registry(L))
        markmt(g)
        g.gcstate = GCSpropagate.toByte()
    }

    private fun remarkupvals(g: global_State?) {
        var uv: UpVal
        uv = g!!.uvhead.u.l.next!!
        while (uv !== g!!.uvhead) {
            LuaLimits.lua_assert(uv.u.l.next!!.u.l.prev === uv && uv.u.l.prev!!.u.l.next === uv)
            if (isgray(LuaState.obj2gco(uv))) {
                markvalue(g, uv.v)
            }
            uv = uv.u.l.next!!
        }
    }

    private fun atomic(L: lua_State) {
        val g: global_State = LuaState.G(L)!!
        var udsize: Int // total size of userdata to be finalized  - uint
        // remark occasional upvalues of (maybe) dead threads
        remarkupvals(g)
        // traverse objects cautch by write barrier and by 'remarkupvals'
        propagateall(g)
        // remark weak tables
        g.gray = g.weak
        g.weak = null
        LuaLimits.lua_assert(!iswhite(LuaState.obj2gco(g.mainthread!!)))
        markobject(g, L) // mark running thread
        markmt(g) // mark basic metatables (again)
        propagateall(g)
        // remark gray again
        g.gray = g.grayagain
        g.grayagain = null
        propagateall(g)
        udsize = luaC_separateudata(L, 0) // separate userdata to be finalized
        marktmu(g) // mark `preserved' userdata
        udsize += propagateall(g) // remark, to propagate `preserveness'
        cleartable(g.weak!!) // remove collected objects from weak tables
        // flip current white
        g.currentwhite = LuaLimits.cast_byte(otherwhite(g))
        g.sweepstrgc = 0
        g.sweepgc = RootGCRef(g)
        g.gcstate = GCSsweepstring.toByte()
        g.estimate = g.totalbytes - udsize // first estimate
    }

    private fun singlestep(L: lua_State?): Int { //l_mem - Int32
        val g: global_State = LuaState.G(L)!!
        return when (g.gcstate.toInt()) {
            GCSpause -> {
                markroot(L!!) // start a new collection
                0
            }
            GCSpropagate -> {
                if (g.gray != null) {
                    propagatemark(g)
                } else { // no more `gray' objects
                    atomic(L!!) // finish mark phase
                    0
                }
            }
            GCSsweepstring -> {
                val old = g.totalbytes as Long //lu_mem - UInt32 - lu_mem - UInt32
                sweepwholelist(L!!, ArrayRef(g.strt.hash, g.sweepstrgc++))
                if (g.sweepstrgc >= g.strt.size) { // nothing more to sweep?
                    g.gcstate = GCSsweep.toByte() // end sweep-string phase
                }
                LuaLimits.lua_assert(old >= g.totalbytes)
                g.estimate -= old - g.totalbytes //(uint)
                GCSWEEPCOST
            }
            GCSsweep -> {
                val old = g.totalbytes as Long //lu_mem - UInt32 - lu_mem - UInt32
                g.sweepgc = sweeplist(L!!, g.sweepgc!!, GCSWEEPMAX.toLong())
                if (g.sweepgc!!.get() == null) { // nothing more to sweep?
                    checkSizes(L)
                    g.gcstate = GCSfinalize.toByte() // end sweep phase
                }
                LuaLimits.lua_assert(old >= g.totalbytes)
                g.estimate -= old - g.totalbytes //(uint)
                GCSWEEPMAX * GCSWEEPCOST
            }
            GCSfinalize -> {
                if (g.tmudata != null) {
                    GCTM(L)
                    if (g.estimate > GCFINALIZECOST) {
                        g.estimate -= GCFINALIZECOST.toLong()
                    }
                    GCFINALIZECOST
                } else {
                    g.gcstate = GCSpause.toByte() // end collection
                    g.gcdept = 0
                    0
                }
            }
            else -> {
                LuaLimits.lua_assert(0)
                0
            }
        }
    }

    fun luaC_step(L: lua_State?) {
        val g: global_State = LuaState.G(L)!!
        var lim = (GCSTEPSIZE / 100 * g.gcstepmul) as Int //l_mem - Int32 - l_mem - Int32
        if (lim == 0) {
            lim = ((LuaLimits.MAX_LUMEM - 1) / 2) // no limit  - l_mem - Int32
        }
        g.gcdept += g.totalbytes - g.GCthreshold
        do {
            lim -= singlestep(L)
            if (g.gcstate.toInt() == GCSpause) {
                break
            }
        } while (lim > 0)
        if (g.gcstate.toInt() != GCSpause) {
            if (g.gcdept < GCSTEPSIZE) {
                g.GCthreshold = g.totalbytes + GCSTEPSIZE // - lim/g.gcstepmul;
            } else {
                g.gcdept -= GCSTEPSIZE.toLong()
                g.GCthreshold = g.totalbytes
            }
        } else {
            LuaLimits.lua_assert(g.totalbytes >= g.estimate)
            setthreshold(g)
        }
    }

    fun luaC_fullgc(L: lua_State?) {
        val g: global_State = LuaState.G(L)!!
        if (g.gcstate <= GCSpropagate) { // reset sweep marks to sweep all elements (returning them to white)
            g.sweepstrgc = 0
            g.sweepgc = RootGCRef(g)
            // reset other collector lists
            g.gray = null
            g.grayagain = null
            g.weak = null
            g.gcstate = GCSsweepstring.toByte()
        }
        LuaLimits.lua_assert(g.gcstate.toInt() != GCSpause && g.gcstate.toInt() != GCSpropagate)
        // finish any pending sweep phase
        while (g.gcstate.toInt() != GCSfinalize) {
            LuaLimits.lua_assert(g.gcstate.toInt() == GCSsweepstring || g.gcstate.toInt() == GCSsweep)
            singlestep(L)
        }
        markroot(L!!)
        while (g.gcstate.toInt() != GCSpause) {
            singlestep(L)
        }
        setthreshold(g)
    }

    fun luaC_barrierf(L: lua_State?, o: GCObject, v: GCObject?) {
        val g: global_State = LuaState.G(L)!!
        LuaLimits.lua_assert(isblack(o) && iswhite(v) && !isdead(g, v) && !isdead(g, o))
        LuaLimits.lua_assert(g.gcstate.toInt() != GCSfinalize && g.gcstate.toInt() != GCSpause)
        LuaLimits.lua_assert(LuaObject.ttype(o.getGch()) != Lua.LUA_TTABLE)
        // must keep invariant?
        if (g.gcstate.toInt() == GCSpropagate) {
            reallymarkobject(g, v) // restore invariant
        } else { // don't mind
            makewhite(g, o) // mark as white just to avoid other barriers
        }
    }

    fun luaC_barrierback(L: lua_State?, t: Table) {
        val g: global_State = LuaState.G(L)!!
        val o: GCObject = LuaState.obj2gco(t)
        LuaLimits.lua_assert(isblack(o) && !isdead(g, o))
        LuaLimits.lua_assert(g.gcstate.toInt() != GCSfinalize && g.gcstate.toInt() != GCSpause)
        black2gray(o) // make table gray (again)
        t.gclist = g.grayagain
        g.grayagain = o
    }

    fun luaC_link(L: lua_State?, o: GCObject, tt: Byte) { //lu_byte
        val g: global_State = LuaState.G(L)!!
        o.getGch().next = g.rootgc
        g.rootgc = o
        o.getGch().marked = luaC_white(g)
        o.getGch().tt = tt
    }

    fun luaC_linkupval(L: lua_State?, uv: UpVal) {
        val g: global_State = LuaState.G(L)!!
        val o: GCObject = LuaState.obj2gco(uv)
        o.getGch().next = g.rootgc // link upvalue into `rootgc' list
        g.rootgc = o
        if (isgray(o)) {
            if (g.gcstate.toInt() == GCSpropagate) {
                gray2black(o) // closed upvalues need barrier
                luaC_barrier(L, uv, uv.v)
            } else { // sweep phase: sweep it (turning it into white)
                makewhite(g, o)
                LuaLimits.lua_assert(g.gcstate.toInt() != GCSfinalize && g.gcstate.toInt() != GCSpause)
            }
        }
    }
}