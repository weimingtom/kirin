package kirin
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.LuaObject.TValue
import kirin.LuaDo.Pfunc
import kirin.LuaDo.lua_longjmp
import kirin.Lua.lua_Hook
import kirin.LuaObject.Proto
import kirin.LuaObject.Table
import kirin.LuaTM.TMS
import kirin.LuaCode.InstructionPtr
import kirin.LuaZIO.Mbuffer
import kirin.LuaObject.GCheader
import kirin.LuaObject.TString
import kirin.LuaObject.UpVal
import kirin.LuaObject.Udata
import kirin.Lua.lua_Alloc
import kirin.LuaObject.ArrayElement

//
// ** $Id: lstate.c,v 2.36.1.2 2008/01/03 15:20:39 roberto Exp $
// ** Global State
// ** See Copyright Notice in lua.h
//
//using lu_byte = System.Byte;
//using lu_int32 = System.Int32;
//using lu_mem = System.UInt32;
//using TValue = Lua.TValue;
//using StkId = TValue;
//using ptrdiff_t = System.Int32;
//using Instruction = System.UInt32;
object LuaState {
    // table of globals
    fun gt(L: lua_State?): TValue {
        return L!!.l_gt
    }

    // registry
    fun registry(L: lua_State?): TValue {
        return G(L)!!.l_registry
    }

    // extra stack space to handle TM calls and some other extras
    const val EXTRA_STACK = 5
    const val BASIC_CI_SIZE = 8
    val BASIC_STACK_SIZE: Int = 2 * Lua.LUA_MINSTACK
    fun curr_func(L: lua_State): LuaObject.Closure? {
        return LuaObject.clvalue(L.ci!!.func)
    }

    fun ci_func(ci: CallInfo?): LuaObject.Closure? {
        return LuaObject.clvalue(ci!!.func)
    }

    fun f_isLua(ci: CallInfo?): Boolean {
        return ci_func(ci)!!.c.getIsC().toInt() == 0
    }

    fun isLua(ci: CallInfo?): Boolean {
        return LuaObject.ttisfunction(ci!!.func) && f_isLua(ci)
    }

    fun G(L: lua_State?): global_State? {
        return L!!.l_G
    }

    fun G_set(L: lua_State?, s: global_State?) {
        L!!.l_G = s
    }

    // macros to convert a GCObject into a specific value
    fun rawgco2ts(o: GCObject): TString? {
        return LuaLimits.check_exp(o.getGch().tt.toInt() == Lua.LUA_TSTRING, o.getTs()) as TString
    }

    fun gco2ts(o: GCObject): TString {
        return rawgco2ts(o)!!.getTsv() as TString
    }

    fun rawgco2u(o: GCObject): Udata? {
        return LuaLimits.check_exp(o.getGch().tt.toInt() == Lua.LUA_TUSERDATA, o.getU()) as Udata
    }

    fun gco2u(o: GCObject): Udata {
        return rawgco2u(o)!!.uv as Udata
    }

    fun gco2cl(o: GCObject): LuaObject.Closure? {
        return LuaLimits.check_exp(o.getGch().tt.toInt() == Lua.LUA_TFUNCTION, o.getCl()) as LuaObject.Closure
    }

    fun gco2h(o: GCObject): Table {
        return LuaLimits.check_exp(o.getGch().tt.toInt() == Lua.LUA_TTABLE, o.getH()) as Table
    }

    fun gco2p(o: GCObject): Proto? {
        return LuaLimits.check_exp(o.getGch().tt.toInt() == LuaObject.LUA_TPROTO, o.getP()) as Proto
    }

    fun gco2uv(o: GCObject): UpVal? {
        return LuaLimits.check_exp(o.getGch().tt.toInt() == LuaObject.LUA_TUPVAL, o.getUv()) as UpVal
    }

    fun ngcotouv(o: GCObject?): UpVal? {
        return LuaLimits.check_exp(o == null || o.getGch().tt.toInt() == LuaObject.LUA_TUPVAL, o!!.getUv()) as UpVal
    }

    fun gco2th(o: GCObject): lua_State? {
        return LuaLimits.check_exp(o.getGch().tt.toInt() == Lua.LUA_TTHREAD, o.getTh()) as lua_State
    }

    // macro to convert any Lua object into a GCObject
    fun obj2gco(v: Any): GCObject {
        return v as GCObject
    }

    fun state_size(x: Any?, t: ClassType): Int {
        return t.GetMarshalSizeOf() + LuaConf.LUAI_EXTRASPACE //Marshal.SizeOf(x)
    }

    //
//		public static lu_byte fromstate(object l)
//		{
//			return (lu_byte)(l - LUAI_EXTRASPACE);
//		}
//
    fun tostate(l: Any?): lua_State? {
        ClassType.Companion.Assert(LuaConf.LUAI_EXTRASPACE == 0, "LUAI_EXTRASPACE not supported")
        return l as lua_State?
    }

    private fun stack_init(L1: lua_State?, L: lua_State?) { // initialize CallInfo array
        L1!!.base_ci =
            LuaMem.luaM_newvector_CallInfo(L, BASIC_CI_SIZE, ClassType(ClassType.Companion.TYPE_CALLINFO))
        L1.ci = L1.base_ci!![0]
        L1.size_ci = BASIC_CI_SIZE
        L1.end_ci = L1.base_ci!![L1.size_ci - 1]
        // initialize stack array
        L1.stack = LuaMem.luaM_newvector_TValue(
            L,
            BASIC_STACK_SIZE + EXTRA_STACK,
            ClassType(ClassType.Companion.TYPE_TVALUE)
        )
        L1.stacksize = BASIC_STACK_SIZE + EXTRA_STACK
        L1.top = L1.stack!![0]
        L1.stack_last = L1.stack!![L1.stacksize - EXTRA_STACK - 1]
        // initialize first ci
        L1.ci!!.func = L1.top
        val top: Array<TValue?> = arrayOfNulls<TValue>(1)
        top[0] = L1.top
        val ret: TValue? = TValue.Companion.inc(top) //ref - StkId
        L1.top = top[0]
        LuaObject.setnilvalue(ret) // `function' entry for this `ci'
        L1.ci!!.base_ = L1.top
        L1.base_ = L1.ci!!.base_
        L1.ci!!.top = TValue.Companion.plus(L1.top!!, Lua.LUA_MINSTACK)
    }

    private fun freestack(L: lua_State?, L1: lua_State?) {
        LuaMem.luaM_freearray_CallInfo(L, L1!!.base_ci, ClassType(ClassType.Companion.TYPE_CALLINFO))
        LuaMem.luaM_freearray_TValue(L, L1.stack, ClassType(ClassType.Companion.TYPE_TVALUE))
    }

    //
//		 ** open parts that may cause memory-allocation errors
//
    private fun f_luaopen(L: lua_State?, ud: Any?) {
        val g = G(L)
        //UNUSED(ud);
        stack_init(L, L) // init stack
        LuaObject.sethvalue(L, gt(L), LuaTable.luaH_new(L, 0, 2)) // table of globals
        LuaObject.sethvalue(L, registry(L), LuaTable.luaH_new(L, 0, 2)) // registry
        LuaString.luaS_resize(L, LuaLimits.MINSTRTABSIZE) // initial size of string table
        LuaTM.luaT_init(L)
        LuaLex.luaX_init(L)
        LuaString.luaS_fix(LuaString.luaS_newliteral(L, CharPtr.Companion.toCharPtr(LuaMem.MEMERRMSG)))
        g!!.GCthreshold = 4 * g.totalbytes
    }

    private fun preinit_state(L: lua_State?, g: global_State?) {
        G_set(L, g)
        L!!.stack = null
        L.stacksize = 0
        L.errorJmp = null
        L.hook = null
        L.hookmask = 0
        L.basehookcount = 0
        L.allowhook = 1
        LuaDebug.resethookcount(L)
        L.openupval = null
        L.size_ci = 0
        L.baseCcalls = 0
        L.nCcalls = L.baseCcalls
        L.status = 0
        L.base_ci = null
        L.ci = null
        L.savedpc = InstructionPtr()
        L.errfunc = 0
        LuaObject.setnilvalue(gt(L))
    }

    private fun close_state(L: lua_State?) {
        val g = G(L)
        LuaFunc.luaF_close(L!!, L!!.stack!![0]) // close all upvalues for this thread
        LuaGC.luaC_freeall(L) // collect all objects
        LuaLimits.lua_assert(g!!.rootgc === obj2gco(L))
        LuaLimits.lua_assert(g!!.strt.nuse == 0L)
        LuaMem.luaM_freearray_GCObject(L, G(L)!!.strt.hash, ClassType(ClassType.Companion.TYPE_GCOBJECT))
        LuaZIO.luaZ_freebuffer(L, g.buff)
        freestack(L, L)
        LuaLimits.lua_assert(g.totalbytes == CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_LG)).toLong()) //typeof(LG)
        //g.frealloc(g.ud, fromstate(L), (uint)state_size(typeof(LG)), 0);
    }

    //private
    fun luaE_newthread(L: lua_State): lua_State { //lua_State L1 = tostate(luaM_malloc(L, state_size(typeof(lua_State))));
        val L1: lua_State = LuaMem.luaM_new_lua_State(L, ClassType(ClassType.Companion.TYPE_LUA_STATE))
        LuaGC.luaC_link(L, obj2gco(L1), Lua.LUA_TTHREAD as Byte)
        preinit_state(L1, G(L))
        stack_init(L1, L) // init stack
        LuaObject.setobj2n(L, gt(L1), gt(L)) // share table of globals
        L1.hookmask = L.hookmask
        L1.basehookcount = L.basehookcount
        L1.hook = L.hook
        LuaDebug.resethookcount(L1)
        LuaLimits.lua_assert(LuaGC.iswhite(obj2gco(L1)))
        return L1
    }

    //private
    fun luaE_freethread(L: lua_State?, L1: lua_State?) {
        LuaFunc.luaF_close(L1!!, L1!!.stack!![0]) // close all upvalues for this thread
        LuaLimits.lua_assert(L1.openupval == null)
        LuaConf.luai_userstatefree(L1)
        freestack(L, L1)
        //luaM_freemem(L, fromstate(L1));
    }

    fun lua_newstate(f: lua_Alloc, ud: Any?): lua_State? {
        var i: Int
        var L: lua_State?
        val g: global_State
        //object l = f(ud, null, 0, (uint)state_size(typeof(LG)));
        val l: Any = f.exec(ClassType(ClassType.Companion.TYPE_LG)) ?: return null //typeof(LG)
        L = tostate(l)
        g = (L as? LG)!!.g
        L.next = null
        L.tt = Lua.LUA_TTHREAD.toByte()
        g.currentwhite = LuaGC.bit2mask(LuaGC.WHITE0BIT, LuaGC.FIXEDBIT).toByte() //lu_byte
        L.marked = LuaGC.luaC_white(g)
        var marked: Byte = L.marked // can't pass properties in as ref - lu_byte
        val marked_ref = ByteArray(1)
        marked_ref[0] = marked
        LuaGC.set2bits(marked_ref, LuaGC.FIXEDBIT, LuaGC.SFIXEDBIT) //ref
        marked = marked_ref[0]
        L.marked = marked
        preinit_state(L, g)
        g.frealloc = f
        g.ud = ud
        g.mainthread = L
        g.uvhead.u.l.prev = g.uvhead
        g.uvhead.u.l.next = g.uvhead
        g.GCthreshold = 0 // mark it as unfinished state
        g.strt.size = 0
        g.strt.nuse = 0
        g.strt.hash = null
        LuaObject.setnilvalue(registry(L))
        LuaZIO.luaZ_initbuffer(L, g.buff)
        g.panic = null
        g.gcstate = LuaGC.GCSpause.toByte()
        g.rootgc = obj2gco(L!!)
        g.sweepstrgc = 0
        g.sweepgc = RootGCRef(g)
        g.gray = null
        g.grayagain = null
        g.weak = null
        g.tmudata = null
        g.totalbytes = CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_LG)).toLong() //typeof(LG) - uint
        g.gcpause = LuaConf.LUAI_GCPAUSE
        g.gcstepmul = LuaConf.LUAI_GCMUL
        g.gcdept = 0
        i = 0
        while (i < LuaObject.NUM_TAGS) {
            g.mt[i] = null
            i++
        }
        if (LuaDo.luaD_rawrunprotected(
                L,
                f_luaopen_delegate(),
                null
            ) != 0
        ) { // memory allocation error: free partial state
            close_state(L)
            L = null
        } else {
            LuaConf.luai_userstateopen(L)
        }
        return L
    }

    private fun callallgcTM(L: lua_State?, ud: Any?) { //UNUSED(ud);
        LuaGC.luaC_callGCTM(L) // call GC metamethods for all udata
    }

    fun lua_close(L: lua_State?) {
        var L = L
        L = G(L)!!.mainthread // only the main thread can be closed
        LuaLimits.lua_lock(L)
        LuaFunc.luaF_close(L!!, L!!.stack!![0]) // close all upvalues for this thread
        LuaGC.luaC_separateudata(L, 1) // separate udata that have GC metamethods
        L.errfunc = 0 // no error function during GC metamethods
        do { // repeat until no more errors
            L.ci = L.base_ci!![0]
            L.top = L.ci!!.base_
            L.base_ = L.top
            L.baseCcalls = 0
            L.nCcalls = L.baseCcalls
        } while (LuaDo.luaD_rawrunprotected(L, callallgcTM_delegate(), null) != 0)
        LuaLimits.lua_assert(G(L)!!.tmudata == null)
        LuaConf.luai_userstateclose(L)
        close_state(L)
    }

    class stringtable {
        var hash: Array<GCObject?>? = null
        var nuse: Long = 0 /*UInt32*/ /*lu_mem*/ /* number of elements */
        var size = 0
    }

    /*
    ** informations about a call
    */
    class CallInfo : ArrayElement {
        private var values: Array<CallInfo>? = null
        private var index = -1
        var base_ /*StkId*/ /* base for this function */: TValue? = null
        var func /*StkId*/ /* function index in the stack */: TValue? = null
        var top /*StkId*/ /* top for this function */: TValue? = null
        var savedpc: InstructionPtr? = null
        var nresults /* expected number of results from this function */ = 0
        var tailcalls /* number of tail calls lost under this entry */ = 0
        override fun set_index(index: Int) {
            this.index = index
        }

        override fun set_array(array: Any?) {
            values = array as Array<CallInfo>?
            ClassType.Companion.Assert(values != null)
        }

        operator fun get(offset: Int): CallInfo {
            return values!![index + offset]
        }

        companion object {
            fun plus(value: CallInfo, offset: Int): CallInfo {
                return value.values!![value.index + offset]
            }

            fun minus(value: CallInfo, offset: Int): CallInfo {
                return value.values!![value.index - offset]
            }

            fun minus(ci: CallInfo, values: Array<CallInfo?>): Int {
                ClassType.Companion.Assert(ci.values == values)
                return ci.index
            }

            fun minus(ci1: CallInfo, ci2: CallInfo): Int {
                ClassType.Companion.Assert(ci1.values == ci2.values)
                return ci1.index - ci2.index
            }

            fun lessThan(ci1: CallInfo, ci2: CallInfo): Boolean {
                ClassType.Companion.Assert(ci1.values == ci2.values)
                return ci1.index < ci2.index
            }

            fun lessEqual(ci1: CallInfo, ci2: CallInfo): Boolean {
                ClassType.Companion.Assert(ci1.values == ci2.values)
                return ci1.index <= ci2.index
            }

            fun greaterThan(ci1: CallInfo, ci2: CallInfo): Boolean {
                ClassType.Companion.Assert(ci1.values == ci2.values)
                return ci1.index > ci2.index
            }

            fun greaterEqual(ci1: CallInfo, ci2: CallInfo): Boolean {
                ClassType.Companion.Assert(ci1.values == ci2.values)
                return ci1.index >= ci2.index
            }

            fun inc( /*ref*/
                value: Array<CallInfo?>
            ): CallInfo? {
                value[0] = value[0]!![1]
                return value[0]!![-1]
            }

            fun dec( /*ref*/
                value: Array<CallInfo?>
            ): CallInfo? {
                value[0] = value[0]!![-1]
                return value[0]!![1]
            }
        }
    }

    /*
	 ** `global state', shared by all threads of this state
	 */
    class global_State {
        var strt = stringtable() /* hash table for strings */
        var frealloc /* function to reallocate memory */: lua_Alloc? = null
        var ud /* auxiliary data to `frealloc' */: Any? = null
        var currentwhite /*Byte*/ /*lu_byte*/: Byte = 0
        var gcstate /*Byte*/ /*lu_byte*/ /* state of garbage collector */: Byte = 0
        var sweepstrgc /* position of sweep in `strt' */ = 0
        var rootgc /* list of all collectable objects */: GCObject? = null
        var sweepgc /* position of sweep in `rootgc' */: GCObjectRef? = null
        var gray /* list of gray objects */: GCObject? = null
        var grayagain /* list of objects to be traversed atomically */: GCObject? = null
        var weak /* list of weak tables (to be cleared) */: GCObject? = null
        var tmudata /* last element of list of userdata to be GC */: GCObject? = null
        var buff: Mbuffer = Mbuffer() /* temporary buffer for string concatentation */
        var   /*UInt32*/ /*lu_mem*/GCthreshold: Long = 0
        var   /*UInt32*/ /*lu_mem*/totalbytes /* number of bytes currently allocated */: Long = 0
        var   /*UInt32*/ /*lu_mem*/estimate /* an estimate of number of bytes actually in use */: Long = 0
        var   /*UInt32*/ /*lu_mem*/gcdept /* how much GC is `behind schedule' */: Long = 0
        var gcpause /* size of pause between successive GCs */ = 0
        var gcstepmul /* GC `granularity' */ = 0
        var panic /* to be called in unprotected errors */: lua_CFunction? = null
        var l_registry: TValue = TValue()
        var mainthread: lua_State? = null
        var uvhead: UpVal = UpVal() /* head of double-linked list of all open upvalues */
        var mt: Array<Table?> = arrayOfNulls<Table>(LuaObject.NUM_TAGS) /* metatables for basic types */
        var tmname: Array<TString?> = arrayOfNulls<TString>(TMS.TM_N.getValue()) // array with tag-method names
    }

    /*
	 ** `per thread' state
	 */
    open class lua_State : GCObject() {
        var status /*Byte*/ /*lu_byte*/: Byte = 0
        var top: TValue? = null /*StkId*/ /* first free slot in the stack */
        var base_: TValue? = null /*StkId*/ /* base of current function */
        var l_G: global_State? = null
        var ci : CallInfo? = null /* call info for current function */
        var savedpc: InstructionPtr = InstructionPtr() /* `savedpc' of current function */
        var stack_last: TValue? = null /*StkId*/ /* last free slot in the stack */
        var stack: Array<TValue?>? = null /*StkId[]*//* stack base */
        var end_ci: CallInfo? = null /* points after end of ci array*/
        var base_ci: Array<CallInfo?>? = null /* array of CallInfo's */
        var stacksize = 0
        var size_ci /* size of array `base_ci' */ = 0
        var   /*ushort*/nCcalls /* number of nested C calls */ = 0
        var   /*ushort*/baseCcalls /* nested C calls when resuming coroutine */ = 0
        var hookmask /*Byte*/ /*lu_byte*/: Byte = 0
        var allowhook /*Byte*/ /*lu_byte*/: Byte = 0
        var basehookcount = 0
        var hookcount = 0
        var hook: lua_Hook? = null
        var l_gt: TValue = TValue() /* table of globals */
        var env: TValue = TValue() /* temporary place for environments */
        var openupval /* list of open upvalues in this stack */: GCObject? = null
        var gclist: GCObject? = null
        var errorJmp /* current error recover point */: lua_longjmp? = null
        var   /*Int32*/ /*ptrdiff_t*/errfunc /* current error handling function (stack index) */ = 0
    }

    /*
	 ** Union of all collectable objects (not a union anymore in the C# port)
	 */
    open class GCObject : GCheader(), ArrayElement {
        // todo: remove this?
//private GCObject[] values = null;
//private int index = -1;
        override fun set_index(index: Int) { //this.index = index;
        }

        override fun set_array(array: Any?) { //this.values = (GCObject[])array;
//ClassType.Assert(this.values != null);
        }

        fun getGch(): GCheader {
            return this as GCheader
        }

        fun getTs(): TString {
            return this as TString
        }

        fun getU(): Udata {
            return this as Udata
        }

        fun getCl(): LuaObject.Closure {
            return this as LuaObject.Closure
        }

        fun getH(): Table {
            return this as Table
        }

        fun getP(): Proto {
            return this as Proto
        }

        fun getUv(): UpVal {
            return this as UpVal
        }

        fun getTh(): lua_State {
            return this as lua_State
        }
    }

    /*
	 ** this interface and is used for implementing GCObject references,
	 ** it's used to emulate the behaviour of a C-style GCObject
	 */
    interface GCObjectRef {
        fun set(value: GCObject)
        fun get(): GCObject?
    }

    class ArrayRef : GCObjectRef, ArrayElement {
        // ArrayRef is used to reference GCObject objects in an array, the next two members
// point to that array and the index of the GCObject element we are referencing
        private var array_elements: Array<GCObject?>?
        private var array_index: Int
        // ArrayRef is itself stored in an array and derived from ArrayElement, the next
// two members refer to itself i.e. the array and index of it's own instance.
        private var vals: Array<ArrayRef>?
        private var index: Int

        constructor() {
            array_elements = null
            array_index = 0
            vals = null
            index = 0
        }

        constructor(array_elements: Array<GCObject?>?, array_index: Int) {
            this.array_elements = array_elements
            this.array_index = array_index
            vals = null
            index = 0
        }

        override fun set(value: GCObject) {
            array_elements!![array_index] = value
        }

        override fun get(): GCObject? {
            return array_elements!![array_index]
        }

        override fun set_index(index: Int) {
            this.index = index
        }

        override fun set_array(vals: Any?) { // don't actually need this
            this.vals = vals as Array<ArrayRef>?
            ClassType.Companion.Assert(this.vals != null)
        }
    }

    class OpenValRef(private val L: lua_State) : GCObjectRef {
        override fun set(value: GCObject) {
            L.openupval = value
        }

        override fun get(): GCObject? {
            return L.openupval
        }

    }

    class RootGCRef(private val g: global_State) : GCObjectRef {
        override fun set(value: GCObject) {
            g.rootgc = value
        }

        override fun get(): GCObject? {
            return g.rootgc
        }

    }

    class NextRef(header: GCheader) : GCObjectRef {
        private val header: GCheader
        override fun set(value: GCObject) {
            header.next = value
        }

        override fun get(): GCObject? {
            return header.next
        }

        init {
            this.header = header
        }
    }

    /*
	 ** Main thread combines a thread state and the global state
	 */
    class LG : lua_State() {
        var g = global_State()
        fun getL(): lua_State {
            return this
        }
    }

    class f_luaopen_delegate : Pfunc {
        override fun exec(L: lua_State?, ud: Any?) {
            f_luaopen(L, ud)
        }
    }

    class callallgcTM_delegate : Pfunc {
        override fun exec(L: lua_State?, ud: Any?) {
            callallgcTM(L, ud)
        }
    }
}