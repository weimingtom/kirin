package kirin

import kirin.CLib.CharPtr
import kirin.LuaCode.InstructionPtr
import kirin.LuaOpCodes.OpCode
import kirin.LuaState.CallInfo
import kirin.LuaState.lua_State
import kirin.LuaTM.TMS
import kirin.LuaObject.TValue
import kirin.LuaObject.TString
import kirin.LuaObject.Proto
import kirin.LuaObject.Closure
import kirin.LuaObject.Table
import kotlin.experimental.and
import kirin.LuaConf.op_delegate
import kirin.LuaObject.LClosure

//
// ** $Id: lvm.c,v 2.63.1.3 2007/12/28 15:32:23 roberto Exp $
// ** Lua virtual machine
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
//using StkId = TValue;
//using lua_Number = System.Double;
//using lu_byte = System.Byte;
//using ptrdiff_t = System.Int32;
//using Instruction = System.UInt32;
object LuaVM {
    fun tostring(L: lua_State?, o: TValue?): Int { //StkId
        return if (LuaObject.ttype(o) == Lua.LUA_TSTRING || luaV_tostring(L, o) != 0) 1 else 0
    }

    fun tonumber(o: Array<TValue?>, n: TValue?): Int { //StkId - ref
        return if (LuaObject.ttype(o[0]) == Lua.LUA_TNUMBER || luaV_tonumber(
                o[0],
                n
            ).also { o[0] = it } != null
        ) 1 else 0
    }

    fun equalobj(L: lua_State, o1: TValue?, o2: TValue?): Int {
        return if (LuaObject.ttype(o1) == LuaObject.ttype(o2) && luaV_equalval(L, o1, o2) != 0) 1 else 0
    }

    // limit for table tag-method chains (to avoid loops)
    const val MAXTAGLOOP = 100

    fun luaV_tonumber(obj: TValue?, n: TValue?): TValue? {
        val num = DoubleArray(1) //lua_Number
        if (LuaObject.ttisnumber(obj)) {
            return obj
        }
        return if (LuaObject.ttisstring(obj) && LuaObject.luaO_str2d(LuaObject.svalue(obj!!)!!, num) != 0) { //out
            LuaObject.setnvalue(n!!, num[0])
            n
        } else {
            null
        }
    }

    fun luaV_tostring(L: lua_State?, obj: TValue?): Int { //StkId
        return if (!LuaObject.ttisnumber(obj)) {
            0
        } else {
            val n = LuaObject.nvalue(obj!!) //lua_Number
            val s = LuaConf.lua_number2str(n)
            LuaObject.setsvalue2s(L, obj!!, LuaString.luaS_new(L, s))
            1
        }
    }

    private fun traceexec(L: lua_State, pc: InstructionPtr?) {
        val mask = L.hookmask //lu_byte
        val oldpc: InstructionPtr = InstructionPtr.Companion.Assign(L.savedpc)!!
        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
        if ((mask and Lua.LUA_MASKCOUNT.toByte()) != 0.toByte() && L.hookcount == 0) {
            LuaDebug.resethookcount(L)
            LuaDo.luaD_callhook(L, Lua.LUA_HOOKCOUNT, -1)
        }
        if ((mask and Lua.LUA_MASKLINE.toByte()) != 0.toByte()) {
            val p = LuaState.ci_func(L.ci)!!.l.p
            val npc = LuaDebug.pcRel(pc!!, p)
            val newline = LuaDebug.getline(p, npc)
            //                 call linehook when enter a new function, when jump back (loop),
//			   or when enter a new line
            if (npc == 0 || InstructionPtr.Companion.lessEqual(pc, oldpc) || newline != LuaDebug.getline(
                    p,
                    LuaDebug.pcRel(oldpc, p)
                )
            ) {
                LuaDo.luaD_callhook(L, Lua.LUA_HOOKLINE, newline)
            }
        }
    }

    private fun callTMres(L: lua_State, res: TValue, f: TValue, p1: TValue?, p2: TValue?) { //StkId
        var res: TValue? = res
        val result = LuaDo.savestack(L, res) //ptrdiff_t - Int32
        LuaObject.setobj2s(L, L.top, f) // push function
        LuaObject.setobj2s(L, TValue.Companion.plus(L.top!!, 1), p1!!) // 1st argument
        LuaObject.setobj2s(L, TValue.Companion.plus(L.top!!, 2), p2!!) // 2nd argument
        LuaDo.luaD_checkstack(L, 3)
        L.top = TValue.Companion.plus(L.top!!, 3)
        LuaDo.luaD_call(L, TValue.Companion.minus(L.top, 3)!!, 1)
        res = LuaDo.restorestack(L, result)
        val top = arrayOfNulls<TValue>(1)
        top[0] = L.top
        //StkId
        TValue.Companion.dec(top) //ref
        L.top = top[0]
        LuaObject.setobjs2s(L, res, L.top!!)
    }

    private fun callTM(L: lua_State, f: TValue, p1: TValue?, p2: TValue, p3: TValue) {
        LuaObject.setobj2s(L, L.top, f) // push function
        LuaObject.setobj2s(L, TValue.Companion.plus(L.top!!, 1), p1!!) // 1st argument
        LuaObject.setobj2s(L, TValue.Companion.plus(L.top!!, 2), p2) // 2nd argument
        LuaObject.setobj2s(L, TValue.Companion.plus(L.top!!, 3), p3) // 3th argument
        LuaDo.luaD_checkstack(L, 4)
        L.top = TValue.Companion.plus(L.top!!, 4)
        LuaDo.luaD_call(L, TValue.Companion.minus(L.top, 4)!!, 0)
    }

    fun luaV_gettable(L: lua_State, t: TValue?, key: TValue?, `val`: TValue) { //StkId
        var t = t
        var loop: Int
        loop = 0
        while (loop < MAXTAGLOOP) {
            var tm: TValue? = null
            if (LuaObject.ttistable(t)) { // `t' is a table?
                val h = LuaObject.hvalue(t!!)
                val res = LuaTable.luaH_get(h, key!!) // do a primitive get
                if (!LuaObject.ttisnil(res) || LuaTM.fasttm(L, h!!.metatable, TMS.TM_INDEX).also {
                        tm = it
                    } == null) { // result is no nil?
// or no TM?
                    LuaObject.setobj2s(L, `val`, res)
                    return
                }
                // else will try the tag method
            } else if (LuaObject.ttisnil(LuaTM.luaT_gettmbyobj(L, t, TMS.TM_INDEX).also { tm = it })) {
                LuaDebug.luaG_typeerror(L, t!!, CharPtr.Companion.toCharPtr("index"))
            }
            if (LuaObject.ttisfunction(tm!!)) {
                callTMres(L, `val`, tm!!, t, key)
                return
            }
            t = tm // else repeat with `tm'
            loop++
        }
        LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("loop in gettable"))
    }

    fun luaV_settable(L: lua_State, t: TValue?, key: TValue, `val`: TValue) { //StkId
        var t = t
        var loop: Int
        loop = 0
        while (loop < MAXTAGLOOP) {
            var tm: TValue? = null
            if (LuaObject.ttistable(t)) { // `t' is a table?
                val h = LuaObject.hvalue(t!!)
                val oldval = LuaTable.luaH_set(L, h, key) // do a primitive set
                if (!LuaObject.ttisnil(oldval) || LuaTM.fasttm(L, h!!.metatable, TMS.TM_NEWINDEX).also {
                        tm = it
                    } == null) { // result is no nil?
// or no TM?
                    LuaObject.setobj2t(L, oldval, `val`)
                    LuaGC.luaC_barriert(L, h!!, `val`)
                    return
                }
                // else will try the tag method
            } else if (LuaObject.ttisnil(LuaTM.luaT_gettmbyobj(L, t, TMS.TM_NEWINDEX).also { tm = it })) {
                LuaDebug.luaG_typeerror(L, t!!, CharPtr.Companion.toCharPtr("index"))
            }
            if (LuaObject.ttisfunction(tm)) {
                callTM(L, tm!!, t, key, `val`)
                return
            }
            t = tm // else repeat with `tm'
            loop++
        }
        LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("loop in settable"))
    }

    private fun call_binTM(L: lua_State, p1: TValue?, p2: TValue?, res: TValue, event_: TMS): Int { //StkId
        var tm = LuaTM.luaT_gettmbyobj(L, p1, event_) // try first operand
        if (LuaObject.ttisnil(tm)) {
            tm = LuaTM.luaT_gettmbyobj(L, p2, event_) // try second operand
        }
        if (LuaObject.ttisnil(tm)) {
            return 0
        }
        callTMres(L, res, tm, p1, p2)
        return 1
    }

    private fun get_compTM(L: lua_State, mt1: Table, mt2: Table, event_: TMS): TValue? {
        val tm1 = LuaTM.fasttm(L, mt1, event_)
        val tm2: TValue?
        if (tm1 == null) {
            return null // no metamethod
        }
        if (mt1 === mt2) {
            return tm1 // same metatables => same metamethods
        }
        tm2 = LuaTM.fasttm(L, mt2, event_)
        if (tm2 == null) {
            return null // no metamethod
        }
        return if (LuaObject.luaO_rawequalObj(tm1, tm2) != 0) { // same metamethods?
            tm1
        } else null
    }

    private fun call_orderTM(L: lua_State, p1: TValue, p2: TValue, event_: TMS): Int {
        val tm1 = LuaTM.luaT_gettmbyobj(L, p1, event_)
        val tm2: TValue
        if (LuaObject.ttisnil(tm1)) {
            return -1 // no metamethod?
        }
        tm2 = LuaTM.luaT_gettmbyobj(L, p2, event_)
        if (LuaObject.luaO_rawequalObj(tm1, tm2) == 0) { // different metamethods?
            return -1
        }
        callTMres(L, L.top!!, tm1, p1, p2)
        return if (LuaObject.l_isfalse(L.top!!) == 0) 1 else 0
    }

    private fun l_strcmp(ls: TString, rs: TString): Int {
        var l = LuaObject.getstr(ls)
        var ll = ls.getTsv().len //uint
        var r = LuaObject.getstr(rs)
        var lr = rs.getTsv().len //uint
        while (true) {
            //int temp = strcoll(l, r);
            val temp = l.toString().compareTo(r.toString())
            if (temp != 0) {
                return temp
            } else { // strings are equal up to a `\0'
                var len = l.toString().length // index of first `\0' in both strings  - (uint) - uint
                if (len == lr) { // r is finished?
                    return if (len == ll) 0 else 1
                } else if (len == ll) { // l is finished?
                    return -1 // l is smaller than r (because r is not finished)
                }
                // both strings longer than `len'; go on comparing (after the `\0')
                len++
                l = CharPtr.Companion.plus(l, len)
                ll -= len
                r = CharPtr.Companion.plus(r, len)
                lr -= len
            }
        }
    }

    fun luaV_lessthan(L: lua_State, l: TValue, r: TValue): Int {
        var res: Int
        if (LuaObject.ttype(l) != LuaObject.ttype(r)) {
            return LuaDebug.luaG_ordererror(L, l, r)
        } else if (LuaObject.ttisnumber(l)) {
            return if (LuaConf.luai_numlt(LuaObject.nvalue(l), LuaObject.nvalue(r))) 1 else 0
        } else if (LuaObject.ttisstring(l)) {
            return if (l_strcmp(LuaObject.rawtsvalue(l)!!, LuaObject.rawtsvalue(r)!!) < 0) 1 else 0
        } else if (call_orderTM(L, l, r, TMS.TM_LT).also { res = it } != -1) {
            return res
        }
        return LuaDebug.luaG_ordererror(L, l, r)
    }

    private fun lessequal(L: lua_State, l: TValue, r: TValue): Int {
        var res: Int
        if (LuaObject.ttype(l) != LuaObject.ttype(r)) {
            return LuaDebug.luaG_ordererror(L, l, r)
        } else if (LuaObject.ttisnumber(l)) {
            return if (LuaConf.luai_numle(LuaObject.nvalue(l), LuaObject.nvalue(r))) 1 else 0
        } else if (LuaObject.ttisstring(l)) {
            return if (l_strcmp(LuaObject.rawtsvalue(l)!!, LuaObject.rawtsvalue(r)!!) <= 0) 1 else 0
        } else if (call_orderTM(L, l, r, TMS.TM_LE).also { res = it } != -1) { // first try `le'
            return res
        } else if (call_orderTM(L, r, l, TMS.TM_LT).also { res = it } != -1) { // else try `lt'
            return if (res == 0) 1 else 0
        }
        return LuaDebug.luaG_ordererror(L, l, r)
    }

    private var mybuff: CharPtr? = null
    fun luaV_equalval(L: lua_State, t1: TValue?, t2: TValue?): Int {
        var tm: TValue? = null
        LuaLimits.lua_assert(LuaObject.ttype(t1) == LuaObject.ttype(t2))
        tm = when (LuaObject.ttype(t1)) {
            Lua.LUA_TNIL -> {
                return 1
            }
            Lua.LUA_TNUMBER -> {
                return if (LuaConf.luai_numeq(LuaObject.nvalue(t1!!), LuaObject.nvalue(t2!!))) 1 else 0
            }
            Lua.LUA_TBOOLEAN -> {
                return if (LuaObject.bvalue(t1!!) == LuaObject.bvalue(t2!!)) 1 else 0 // true must be 1 !!
            }
            Lua.LUA_TLIGHTUSERDATA -> {
                return if (LuaObject.pvalue(t1!!) === LuaObject.pvalue(t2!!)) 1 else 0
            }
            Lua.LUA_TUSERDATA -> {
                if (LuaObject.uvalue(t1!!) === LuaObject.uvalue(t2!!)) {
                    return 1
                }
                get_compTM(L, LuaObject.uvalue(t1!!).metatable!!, LuaObject.uvalue(t2!!).metatable!!, TMS.TM_EQ)
            }
            Lua.LUA_TTABLE -> {
                if (LuaObject.hvalue(t1!!) === LuaObject.hvalue(t2!!)) {
                    return 1
                }
                get_compTM(L, LuaObject.hvalue(t1!!)!!.metatable!!, LuaObject.hvalue(t2!!)!!.metatable!!, TMS.TM_EQ)
            }
            else -> {
                return if (LuaObject.gcvalue(t1) === LuaObject.gcvalue(t2)) 1 else 0
            }
        }
        if (tm == null) {
            return 0 // no TM?
        }
        callTMres(L, L.top!!, tm, t1, t2) // call TM
        return if (LuaObject.l_isfalse(L.top!!) == 0) 1 else 0
    }

    fun luaV_concat(L: lua_State, total: Int, last: Int) {
        var total = total
        var last = last
        do {
            val top: TValue = TValue.Companion.plus(L.base_!!, last + 1) //StkId
            var n = 2 // number of elements handled in this pass (at least 2)
            if (!(LuaObject.ttisstring(TValue.Companion.minus(top, 2)) || LuaObject.ttisnumber(
                    TValue.Companion.minus(
                        top,
                        2
                    )
                )) || tostring(L, TValue.Companion.minus(top, 1)) == 0
            ) {
                if (call_binTM(
                        L,
                        TValue.Companion.minus(top, 2),
                        TValue.Companion.minus(top, 1),
                        TValue.Companion.minus(top, 2)!!,
                        TMS.TM_CONCAT
                    ) == 0
                ) {
                    LuaDebug.luaG_concaterror(L, TValue.Companion.minus(top, 2)!!, TValue.Companion.minus(top, 1)!!)
                }
            } else if (LuaObject.tsvalue(TValue.Companion.minus(top, 1)!!).len == 0) { // second op is empty?
                tostring(L, TValue.Companion.minus(top, 2)) // result is first op (as string)
            } else { // at least two string values; get as many as possible
                var tl = LuaObject.tsvalue(TValue.Companion.minus(top, 1)!!).len //uint
                var buffer: CharPtr?
                var i: Int
                // collect total length
                n = 1
                while (n < total && tostring(
                        L,
                        TValue.Companion.minus(TValue.Companion.minus(top, n), 1)
                    ) != 0
                ) {
                    //FIXME:
                    val l = LuaObject.tsvalue(TValue.Companion.minus(TValue.Companion.minus(top, n), 1)!!).len //uint
                    if (l >= LuaLimits.MAX_SIZET - tl) {
                        LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("string length overflow"))
                    }
                    tl += l
                    n++
                }
                buffer = LuaZIO.luaZ_openspace(L, LuaState.G(L)!!.buff, tl)
                if (CharPtr.Companion.isEqual(mybuff, null)) {
                    mybuff = buffer
                }
                tl = 0
                i = n
                while (i > 0) {
                    // concat all strings
                    val l = LuaObject.tsvalue(TValue.Companion.minus(top, i)!!).len //uint
                    CLib.memcpy_char(
                        buffer!!.chars!!,
                        tl,
                        LuaObject.svalue(TValue.Companion.minus(top, i)!!)!!.chars!!,
                        l
                    ) //(int)
                    tl += l
                    i--
                }
                LuaObject.setsvalue2s(L, TValue.Companion.minus(top, n)!!, LuaString.luaS_newlstr(L, buffer, tl))
            }
            total -= n - 1 // got `n' strings to create 1 new
            last -= n - 1
        } while (total > 1) // repeat until only 1 result left
    }

    fun Arith(L: lua_State, ra: TValue, rb: TValue?, rc: TValue?, op: TMS) { //StkId
        val tempb = TValue()
        val tempc = TValue()
        var b: TValue?
        var c: TValue? = null
        if (luaV_tonumber(rb, tempb).also { b = it } != null && luaV_tonumber(rc, tempc).also {
                c = it
            } != null) {
            val nb = LuaObject.nvalue(b!!)
            val nc = LuaObject.nvalue(c!!) //lua_Number
            when (op) {
                TMS.TM_ADD -> {
                    LuaObject.setnvalue(ra, LuaConf.luai_numadd(nb, nc))
                }
                TMS.TM_SUB -> {
                    LuaObject.setnvalue(ra, LuaConf.luai_numsub(nb, nc))
                }
                TMS.TM_MUL -> {
                    LuaObject.setnvalue(ra, LuaConf.luai_nummul(nb, nc))
                }
                TMS.TM_DIV -> {
                    LuaObject.setnvalue(ra, LuaConf.luai_numdiv(nb, nc))
                }
                TMS.TM_MOD -> {
                    LuaObject.setnvalue(ra, LuaConf.luai_nummod(nb, nc))
                }
                TMS.TM_POW -> {
                    LuaObject.setnvalue(ra, LuaConf.luai_numpow(nb, nc))
                }
                TMS.TM_UNM -> {
                    LuaObject.setnvalue(ra, LuaConf.luai_numunm(nb))
                }
                else -> {
                    LuaLimits.lua_assert(false)
                }
            }
        } else if (call_binTM(L, rb, rc, ra, op) == 0) {
            LuaDebug.luaG_aritherror(L, rb!!, rc!!)
        }
    }

    //
//		 ** some macros for common tasks in `luaV_execute'
//
    fun runtime_check(L: lua_State?, c: Boolean) {
        ClassType.Companion.Assert(c)
    }

    ///#define RA(i)	(base+GETARG_A(i))
// to be used after possible stack reallocation
///#define RB(i)	check_exp(getBMode(GET_OPCODE(i)) == OpArgMask.OpArgR, base+GETARG_B(i))
///#define RC(i)	check_exp(getCMode(GET_OPCODE(i)) == OpArgMask.OpArgR, base+GETARG_C(i))
///#define RKB(i)	check_exp(getBMode(GET_OPCODE(i)) == OpArgMask.OpArgK, \
//ISK(GETARG_B(i)) ? k+INDEXK(GETARG_B(i)) : base+GETARG_B(i))
///#define RKC(i)	check_exp(getCMode(GET_OPCODE(i)) == OpArgMask.OpArgK, \
//	ISK(GETARG_C(i)) ? k+INDEXK(GETARG_C(i)) : base+GETARG_C(i))
///#define KBx(i)	check_exp(getBMode(GET_OPCODE(i)) == OpArgMask.OpArgK, k+GETARG_Bx(i))
// todo: implement proper checks, as above
    fun RA(L: lua_State?, base_: TValue?, i: Long): TValue { //Instruction - UInt32 - StkId
        return TValue.Companion.plus(base_!!, LuaOpCodes.GETARG_A(i))
    }

    fun RB(L: lua_State?, base_: TValue?, i: Long): TValue { //Instruction - UInt32 - StkId
        return TValue.Companion.plus(base_!!, LuaOpCodes.GETARG_B(i))
    }

    fun RC(L: lua_State?, base_: TValue?, i: Long): TValue { //Instruction - UInt32 - StkId
        return TValue.Companion.plus(base_!!, LuaOpCodes.GETARG_C(i))
    }

    fun RKB(L: lua_State?, base_: TValue?, i: Long, k: Array<TValue?>): TValue { //Instruction - UInt32 - StkId
        return if (LuaOpCodes.ISK(LuaOpCodes.GETARG_B(i)) != 0) k[LuaOpCodes.INDEXK(LuaOpCodes.GETARG_B(i))]!! else TValue.Companion.plus(
            base_!!,
            LuaOpCodes.GETARG_B(i)
        )
    }

    fun RKC(L: lua_State?, base_: TValue?, i: Long, k: Array<TValue?>): TValue { //Instruction - UInt32 - StkId
        return if (LuaOpCodes.ISK(LuaOpCodes.GETARG_C(i)) != 0) k[LuaOpCodes.INDEXK(LuaOpCodes.GETARG_C(i))]!! else TValue.Companion.plus(
            base_!!,
            LuaOpCodes.GETARG_C(i)
        )
    }

    fun KBx(L: lua_State?, i: Long, k: Array<TValue?>): TValue { //Instruction - UInt32
        return k[LuaOpCodes.GETARG_Bx(i)]!!
    }

    fun dojump(L: lua_State?, pc: InstructionPtr?, i: Int) {
        pc!!.pc += i
        LuaLimits.luai_threadyield(L)
    }

    ///#define Protect(x)	{ L.savedpc = pc; {x;}; base = L.base_; }
    fun arith_op(
        L: lua_State,
        op: op_delegate,
        tm: TMS,
        base_: TValue?,
        i: Long,
        k: Array<TValue?>,
        ra: TValue,
        pc: InstructionPtr?
    ) { //StkId - Instruction - UInt32 - StkId
        var base_ = base_
        val rb = RKB(L, base_, i, k)
        val rc = RKC(L, base_, i, k)
        if (LuaObject.ttisnumber(rb) && LuaObject.ttisnumber(rc)) {
            val nb = LuaObject.nvalue(rb)
            val nc = LuaObject.nvalue(rc) //lua_Number
            LuaObject.setnvalue(ra, op.exec(nb, nc))
        } else { //Protect(
            L.savedpc = InstructionPtr.Companion.Assign(pc)!!
            Arith(L, ra, rb, rc, tm)
            base_ = L.base_
            //);
        }
    }

    private fun Dump(pc: Int, i: Long) { //Instruction - UInt32
        val A = LuaOpCodes.GETARG_A(i)
        val B = LuaOpCodes.GETARG_B(i)
        val C = LuaOpCodes.GETARG_C(i)
        val Bx = LuaOpCodes.GETARG_Bx(i)
        var sBx = LuaOpCodes.GETARG_sBx(i)
        if (sBx and 0x100 != 0) {
            sBx = -(sBx and 0xff)
        }
        StreamProxy.Companion.Write("$pc ($i): ") //FIXME:"{0,5} ({1,10}): "
        StreamProxy.Companion.Write("" + LuaOpCodes.luaP_opnames[LuaOpCodes.GET_OPCODE(i).getValue()].toString() + "\t") //"{0,-10}\t"
        when (LuaOpCodes.GET_OPCODE(i)) {
            OpCode.OP_CLOSE -> {
                StreamProxy.Companion.Write("" + A + "")
            }
            OpCode.OP_MOVE, OpCode.OP_LOADNIL, OpCode.OP_GETUPVAL, OpCode.OP_SETUPVAL, OpCode.OP_UNM, OpCode.OP_NOT, OpCode.OP_RETURN -> {
                StreamProxy.Companion.Write("$A, $B")
            }
            OpCode.OP_LOADBOOL, OpCode.OP_GETTABLE, OpCode.OP_SETTABLE, OpCode.OP_NEWTABLE, OpCode.OP_SELF, OpCode.OP_ADD, OpCode.OP_SUB, OpCode.OP_MUL, OpCode.OP_DIV, OpCode.OP_POW, OpCode.OP_CONCAT, OpCode.OP_EQ, OpCode.OP_LT, OpCode.OP_LE, OpCode.OP_TEST, OpCode.OP_CALL, OpCode.OP_TAILCALL -> {
                StreamProxy.Companion.Write("$A, $B, $C")
            }
            OpCode.OP_LOADK -> {
                StreamProxy.Companion.Write("$A, $Bx")
            }
            OpCode.OP_GETGLOBAL, OpCode.OP_SETGLOBAL, OpCode.OP_SETLIST, OpCode.OP_CLOSURE -> {
                StreamProxy.Companion.Write("$A, $Bx")
            }
            OpCode.OP_TFORLOOP -> {
                StreamProxy.Companion.Write("$A, $C")
            }
            OpCode.OP_JMP, OpCode.OP_FORLOOP, OpCode.OP_FORPREP -> {
                StreamProxy.Companion.Write("$A, $sBx")
            }
        }
        StreamProxy.Companion.WriteLine()
    }

    fun luaV_execute(L: lua_State, nexeccalls: Int) {
        var nexeccalls = nexeccalls
        var cl: LClosure
        var base_: TValue //StkId
        var k: Array<TValue?>
        //const
        var pc: InstructionPtr
        //reentry:  /* entry point */
        while (true) {
            var reentry = false
            LuaLimits.lua_assert(LuaState.isLua(L.ci))
            pc = InstructionPtr.Companion.Assign(L.savedpc)!!
            cl = LuaObject.clvalue(L.ci!!.func)!!.l
            base_ = L.base_!!
            k = cl.p!!.k!!
            // main loop of interpreter
            loop@ while (true) {
                val pc_ref = arrayOfNulls<InstructionPtr>(1)
                pc_ref[0] = pc
                val ret: InstructionPtr = InstructionPtr.Companion.inc(pc_ref) //ref
                pc = pc_ref[0]!!
                //const
                val i = ret[0] //Instruction - UInt32
                var ra: TValue //StkId
                if ((L.hookmask and ((Lua.LUA_MASKLINE or Lua.LUA_MASKCOUNT).toByte())) != 0.toByte() &&
                     (--L.hookcount == 0 || ((L.hookmask and Lua.LUA_MASKLINE.toByte()) != 0.toByte()) )) {
                    traceexec(L, pc)
                    if (L.status.toInt() == Lua.LUA_YIELD) { // did hook yield?
                        L.savedpc = InstructionPtr(pc.codes, pc.pc - 1)
                        return
                    }
                    base_ = L.base_!!
                }
                // warning!! several calls may realloc the stack and invalidate `ra'
                ra = RA(L, base_, i)
                LuaLimits.lua_assert(base_ === L.base_ && L.base_ === L.ci!!.base_)
                LuaLimits.lua_assert(
                    TValue.Companion.lessEqual(base_, L.top!!) && TValue.Companion.minus(
                        L.top!!,
                        L.stack!!
                    ) <= L.stacksize
                )
                LuaLimits.lua_assert(L.top === L.ci!!.top || LuaDebug.luaG_checkopenop(i) != 0)
                //Dump(pc.pc, i);
                var reentry2 = false
                when (LuaOpCodes.GET_OPCODE(i)) {
                    OpCode.OP_MOVE -> {
                        LuaObject.setobjs2s(L, ra, RB(L, base_, i))
                        continue@loop
                    }
                    OpCode.OP_LOADK -> {
                        LuaObject.setobj2s(L, ra, KBx(L, i, k!!))
                        continue@loop
                    }
                    OpCode.OP_LOADBOOL -> {
                        LuaObject.setbvalue(ra, LuaOpCodes.GETARG_B(i))
                        if (LuaOpCodes.GETARG_C(i) != 0) {
                            val pc_ref2 = arrayOfNulls<InstructionPtr>(1)
                            pc_ref2[0] = pc
                            InstructionPtr.Companion.inc(pc_ref2) // skip next instruction (if C)  - ref
                            pc = pc_ref2[0]!!
                        }
                        continue@loop
                    }
                    OpCode.OP_LOADNIL -> {
                        var rb = RB(L, base_, i)
                        do {
                            val rb_ref = arrayOfNulls<TValue>(1)
                            rb_ref[0] = rb
                            val ret2: TValue = TValue.Companion.dec(rb_ref)!! //ref - StkId
                            rb = rb_ref[0]!!
                            LuaObject.setnilvalue(ret2)
                        } while (TValue.Companion.greaterEqual(rb, ra))
                        continue@loop
                    }
                    OpCode.OP_GETUPVAL -> {
                        val b = LuaOpCodes.GETARG_B(i)
                        LuaObject.setobj2s(L, ra, cl.upvals!![b]!!.v!!)
                        continue@loop
                    }
                    OpCode.OP_GETGLOBAL -> {
                        val g = TValue()
                        val rb = KBx(L, i, k!!)
                        LuaObject.sethvalue(L, g, cl.getEnv())
                        LuaLimits.lua_assert(LuaObject.ttisstring(rb))
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        luaV_gettable(L, g, rb, ra)
                        base_ = L.base_!!
                        //);
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        continue@loop
                    }
                    OpCode.OP_GETTABLE -> {
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        luaV_gettable(L, RB(L, base_, i), RKC(L, base_, i, k!!), ra)
                        base_ = L.base_!!
                        //);
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        continue@loop
                    }
                    OpCode.OP_SETGLOBAL -> {
                        val g = TValue()
                        LuaObject.sethvalue(L, g, cl.getEnv())
                        LuaLimits.lua_assert(LuaObject.ttisstring(KBx(L, i, k!!)))
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        luaV_settable(L, g, KBx(L, i, k!!), ra)
                        base_ = L.base_!!
                        //);
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        continue@loop
                    }
                    OpCode.OP_SETUPVAL -> {
                        val uv = cl.upvals!![LuaOpCodes.GETARG_B(i)]
                        LuaObject.setobj(L, uv!!.v, ra)
                        LuaGC.luaC_barrier(L, uv!!, ra)
                        continue@loop
                    }
                    OpCode.OP_SETTABLE -> {
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        luaV_settable(L, ra, RKB(L, base_, i, k), RKC(L, base_, i, k))
                        base_ = L.base_!!
                        //);
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        continue@loop
                    }
                    OpCode.OP_NEWTABLE -> {
                        val b = LuaOpCodes.GETARG_B(i)
                        val c = LuaOpCodes.GETARG_C(i)
                        LuaObject.sethvalue(
                            L,
                            ra,
                            LuaTable.luaH_new(L, LuaObject.luaO_fb2int(b), LuaObject.luaO_fb2int(c))
                        )
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        LuaGC.luaC_checkGC(L)
                        base_ = L.base_!!
                        //);
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        continue@loop
                    }
                    OpCode.OP_SELF -> {
                        //StkId
                        val rb = RB(L, base_, i)
                        LuaObject.setobjs2s(L, TValue.Companion.plus(ra, 1), rb)
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        luaV_gettable(L, rb, RKC(L, base_, i, k!!), ra)
                        base_ = L.base_!!
                        //);
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        continue@loop
                    }
                    OpCode.OP_ADD -> {
                        arith_op(L, LuaConf.luai_numadd_delegate(), TMS.TM_ADD, base_, i, k!!, ra, pc)
                        continue@loop
                    }
                    OpCode.OP_SUB -> {
                        arith_op(L, LuaConf.luai_numsub_delegate(), TMS.TM_SUB, base_, i, k!!, ra, pc)
                        continue@loop
                    }
                    OpCode.OP_MUL -> {
                        arith_op(L, LuaConf.luai_nummul_delegate(), TMS.TM_MUL, base_, i, k!!, ra, pc)
                        continue@loop
                    }
                    OpCode.OP_DIV -> {
                        arith_op(L, LuaConf.luai_numdiv_delegate(), TMS.TM_DIV, base_, i, k!!, ra, pc)
                        continue@loop
                    }
                    OpCode.OP_MOD -> {
                        arith_op(L, LuaConf.luai_nummod_delegate(), TMS.TM_MOD, base_, i, k!!, ra, pc)
                        continue@loop
                    }
                    OpCode.OP_POW -> {
                        arith_op(L, LuaConf.luai_numpow_delegate(), TMS.TM_POW, base_, i, k!!, ra, pc)
                        continue@loop
                    }
                    OpCode.OP_UNM -> {
                        val rb = RB(L, base_, i)
                        if (LuaObject.ttisnumber(rb)) {
                            val nb = LuaObject.nvalue(rb) //lua_Number
                            LuaObject.setnvalue(ra, LuaConf.luai_numunm(nb))
                        } else { //Protect(
                            L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                            Arith(L, ra, rb, rb, TMS.TM_UNM)
                            base_ = L.base_!!
                            //);
                            L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        }
                        continue@loop
                    }
                    OpCode.OP_NOT -> {
                        val res = if (LuaObject.l_isfalse(
                                RB(
                                    L,
                                    base_,
                                    i
                                )
                            ) == 0
                        ) 0 else 1 // next assignment may change this value
                        LuaObject.setbvalue(ra, res)
                        continue@loop
                    }
                    OpCode.OP_LEN -> {
                        val rb = RB(L, base_, i)
                        when (LuaObject.ttype(rb)) {
                            Lua.LUA_TTABLE -> {
                                LuaObject.setnvalue(
                                    ra,
                                    LuaTable.luaH_getn(LuaObject.hvalue(rb)!!).toDouble()
                                ) //lua_Number
                            }
                            Lua.LUA_TSTRING -> {
                                LuaObject.setnvalue(ra, LuaObject.tsvalue(rb).len.toDouble()) //lua_Number
                            }
                            else -> {
                                // try metamethod
//Protect(
                                L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                                if (call_binTM(L, rb, LuaObject.luaO_nilobject, ra, TMS.TM_LEN) == 0) {
                                    LuaDebug.luaG_typeerror(L, rb, CharPtr.Companion.toCharPtr("get length of"))
                                }
                                base_ = L.base_!!
                            }
                        }
                        continue@loop
                    }
                    OpCode.OP_CONCAT -> {
                        val b = LuaOpCodes.GETARG_B(i)
                        val c = LuaOpCodes.GETARG_C(i)
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        luaV_concat(L, c - b + 1, c)
                        LuaGC.luaC_checkGC(L)
                        base_ = L.base_!!
                        //);
                        LuaObject.setobjs2s(L, RA(L, base_, i), TValue.Companion.plus(base_, b))
                        continue@loop
                    }
                    OpCode.OP_JMP -> {
                        dojump(L, pc, LuaOpCodes.GETARG_sBx(i))
                        continue@loop
                    }
                    OpCode.OP_EQ -> {
                        val rb = RKB(L, base_, i, k!!)
                        val rc = RKC(L, base_, i, k!!)
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        if (equalobj(L, rb, rc) == LuaOpCodes.GETARG_A(i)) {
                            dojump(L, pc, LuaOpCodes.GETARG_sBx(pc[0]))
                        }
                        base_ = L.base_!!
                        //);
                        val pc_ref2 = arrayOfNulls<InstructionPtr>(1)
                        pc_ref2[0] = pc
                        InstructionPtr.Companion.inc(pc_ref2) //ref
                        pc = pc_ref2[0]!!
                        continue@loop
                    }
                    OpCode.OP_LT -> {
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        if (luaV_lessthan(
                                L,
                                RKB(L, base_, i, k),
                                RKC(L, base_, i, k)
                            ) == LuaOpCodes.GETARG_A(i)
                        ) {
                            dojump(L, pc, LuaOpCodes.GETARG_sBx(pc[0]))
                        }
                        base_ = L.base_!!
                        //);
                        val pc_ref3 = arrayOfNulls<InstructionPtr>(1)
                        pc_ref3[0] = pc
                        InstructionPtr.Companion.inc(pc_ref3) //ref
                        pc = pc_ref3[0]!!
                        continue@loop
                    }
                    OpCode.OP_LE -> {
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        if (lessequal(
                                L,
                                RKB(L, base_, i, k!!),
                                RKC(L, base_, i, k!!)
                            ) == LuaOpCodes.GETARG_A(i)
                        ) {
                            dojump(L, pc, LuaOpCodes.GETARG_sBx(pc[0]))
                        }
                        base_ = L.base_!!
                        //);
                        val pc_ref4 = arrayOfNulls<InstructionPtr>(1)
                        pc_ref4[0] = pc
                        InstructionPtr.Companion.inc(pc_ref4) //ref
                        pc = pc_ref4[0]!!
                        continue@loop
                    }
                    OpCode.OP_TEST -> {
                        if (LuaObject.l_isfalse(ra) != LuaOpCodes.GETARG_C(i)) {
                            dojump(L, pc, LuaOpCodes.GETARG_sBx(pc[0]))
                        }
                        val pc_ref5 = arrayOfNulls<InstructionPtr>(1)
                        pc_ref5[0] = pc
                        InstructionPtr.Companion.inc(pc_ref5) //ref
                        pc = pc_ref5[0]!!
                        continue@loop
                    }
                    OpCode.OP_TESTSET -> {
                        val rb = RB(L, base_, i)
                        if (LuaObject.l_isfalse(rb) != LuaOpCodes.GETARG_C(i)) {
                            LuaObject.setobjs2s(L, ra, rb)
                            dojump(L, pc, LuaOpCodes.GETARG_sBx(pc[0]))
                        }
                        val pc_ref6 = arrayOfNulls<InstructionPtr>(1)
                        pc_ref6[0] = pc
                        InstructionPtr.Companion.inc(pc_ref6) //ref
                        pc = pc_ref6[0]!!
                        continue@loop
                    }
                    OpCode.OP_CALL -> {
                        val b = LuaOpCodes.GETARG_B(i)
                        val nresults = LuaOpCodes.GETARG_C(i) - 1
                        if (b != 0) {
                            L.top = TValue.Companion.plus(ra, b) // else previous instruction set top
                        }
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        var reentry3 = false
                        when (LuaDo.luaD_precall(L, ra, nresults)) {
                            LuaDo.PCRLUA -> {
                                nexeccalls++
                                //goto reentry;  /* restart luaV_execute over new Lua function */
                                reentry3 = true
                            }
                            LuaDo.PCRC -> {
                                // it was a C function (`precall' called it); adjust results
                                if (nresults >= 0) {
                                    L.top = L.ci!!.top
                                }
                                base_ = L.base_!!
                                continue@loop
                            }
                            else -> {
                                return  // yield
                            }
                        }
                        if (reentry3) {
                            reentry2 = true
                            break@loop
                        } else {
                            break@loop
                        }
                    }
                    OpCode.OP_TAILCALL -> {
                        val b = LuaOpCodes.GETARG_B(i)
                        if (b != 0) {
                            L.top = TValue.Companion.plus(ra, b) // else previous instruction set top
                        }
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        LuaLimits.lua_assert(LuaOpCodes.GETARG_C(i) - 1 == Lua.LUA_MULTRET)
                        var reentry4 = false
                        when (LuaDo.luaD_precall(L, ra, Lua.LUA_MULTRET)) {
                            LuaDo.PCRLUA -> {
                                // tail call: put new frame in place of previous one
                                val ci: CallInfo = CallInfo.Companion.minus(L.ci!!, 1) // previous frame
                                var aux: Int
                                val func = ci.func //StkId
                                val pfunc: TValue =
                                    CallInfo.Companion.plus(ci, 1).func!! // previous function index  - StkId
                                if (L.openupval != null) {
                                    LuaFunc.luaF_close(L, ci.base_)
                                }
                                ci.base_ =
                                    TValue.Companion.plus(ci.func!!, TValue.Companion.minus(ci[1].base_, pfunc))
                                L.base_ = ci.base_
                                aux = 0
                                while (TValue.Companion.lessThan(TValue.Companion.plus(pfunc, aux), L.top)) {
                                    // move frame down
                                    LuaObject.setobjs2s(
                                        L,
                                        TValue.Companion.plus(func!!, aux),
                                        TValue.Companion.plus(pfunc, aux)
                                    )
                                    aux++
                                }
                                L.top = TValue.Companion.plus(func!!, aux)
                                ci.top = L.top // correct top
                                LuaLimits.lua_assert(
                                    L.top === TValue.Companion.plus(
                                        L.base_!!,
                                        LuaObject.clvalue(func)!!.l.p!!.maxstacksize.toInt()
                                    )
                                )
                                ci.savedpc = InstructionPtr.Companion.Assign(L.savedpc)
                                ci.tailcalls++ // one more call lost
                                val ci_ref3 = arrayOfNulls<CallInfo>(1)
                                ci_ref3[0] = L.ci
                                CallInfo.Companion.dec(ci_ref3) // remove new frame  - ref
                                L.ci = ci_ref3[0]
                                //goto reentry;
                                reentry4 = true
                            }
                            LuaDo.PCRC -> {
                                // it was a C function (`precall' called it)
                                base_ = L.base_!!
                                continue@loop
                            }
                            else -> {
                                return  // yield
                            }
                        }
                        if (reentry4) {
                            reentry2 = true
                            break@loop
                        } else {
                            break@loop
                        }
                    }
                    OpCode.OP_RETURN -> {
                        var b = LuaOpCodes.GETARG_B(i)
                        if (b != 0) {
                            L.top = TValue.Companion.plus(ra, b - 1) //FIXME:
                        }
                        if (L.openupval != null) {
                            LuaFunc.luaF_close(L, base_)
                        }
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        b = LuaDo.luaD_poscall(L, ra)
                        if (--nexeccalls == 0) { // was previous function running `here'?
                            return  // no: return
                        } else { // yes: continue its execution
                            if (b != 0) {
                                L.top = L.ci!!.top
                            }
                            LuaLimits.lua_assert(LuaState.isLua(L.ci))
                            LuaLimits.lua_assert(LuaOpCodes.GET_OPCODE(L.ci!!.savedpc!![-1]!!) == OpCode.OP_CALL)
                            //goto reentry;
                            reentry2 = true
                            break@loop
                        }
                    }
                    OpCode.OP_FORLOOP -> {
                        val step = LuaObject.nvalue(TValue.Companion.plus(ra, 2)) //lua_Number
                        val idx =
                            LuaConf.luai_numadd(LuaObject.nvalue(ra), step) // increment index  - lua_Number
                        val limit = LuaObject.nvalue(TValue.Companion.plus(ra, 1)) //lua_Number
                        if (if (LuaConf.luai_numlt(0.0, step)) LuaConf.luai_numle(idx, limit) else LuaConf.luai_numle(
                                limit,
                                idx
                            )
                        ) {
                            dojump(L, pc, LuaOpCodes.GETARG_sBx(i)) // jump back
                            LuaObject.setnvalue(ra, idx) // update internal index...
                            LuaObject.setnvalue(TValue.Companion.plus(ra, 3), idx) //...and external index
                        }
                        continue@loop
                    }
                    OpCode.OP_FORPREP -> {
                        var init = ra
                        var plimit: TValue = TValue.Companion.plus(ra, 1)
                        var pstep: TValue = TValue.Companion.plus(ra, 2)
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!! // next steps may throw errors
                        var retxxx: Int
                        val init_ref = arrayOfNulls<TValue>(1)
                        init_ref[0] = init
                        retxxx = tonumber(init_ref, ra) //ref
                        init = init_ref[0]!!
                        if (retxxx == 0) {
                            LuaDebug.luaG_runerror(
                                L,
                                CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("for").toString() + " initial value must be a number")
                            )
                        } else {
                            val plimit_ref = arrayOfNulls<TValue>(1)
                            plimit_ref[0] = plimit
                            retxxx = tonumber(plimit_ref, TValue.Companion.plus(ra, 1)) //ref
                            plimit = plimit_ref[0]!!
                            if (retxxx == 0) {
                                LuaDebug.luaG_runerror(
                                    L,
                                    CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("for").toString() + " limit must be a number")
                                )
                            } else {
                                val pstep_ref = arrayOfNulls<TValue>(1)
                                pstep_ref[0] = pstep
                                retxxx = tonumber(pstep_ref, TValue.Companion.plus(ra, 2)) //ref
                                pstep = pstep_ref[0]!!
                                if (retxxx == 0) {
                                    LuaDebug.luaG_runerror(
                                        L,
                                        CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("for").toString() + " step must be a number")
                                    )
                                }
                            }
                        }
                        LuaObject.setnvalue(ra, LuaConf.luai_numsub(LuaObject.nvalue(ra), LuaObject.nvalue(pstep)))
                        dojump(L, pc, LuaOpCodes.GETARG_sBx(i))
                        continue@loop
                    }
                    OpCode.OP_TFORLOOP -> {
                        var cb: TValue = TValue.Companion.plus(ra, 3) // call base  - StkId
                        LuaObject.setobjs2s(L, TValue.Companion.plus(cb, 2), TValue.Companion.plus(ra, 2))
                        LuaObject.setobjs2s(L, TValue.Companion.plus(cb, 1), TValue.Companion.plus(ra, 1))
                        LuaObject.setobjs2s(L, cb, ra)
                        L.top = TValue.Companion.plus(cb, 3) // func. + 2 args (state and index)
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        LuaDo.luaD_call(L, cb, LuaOpCodes.GETARG_C(i))
                        base_ = L.base_!!
                        //);
                        L.top = L.ci!!.top
                        cb = TValue.Companion.plus(RA(L, base_, i), 3) // previous call may change the stack
                        if (!LuaObject.ttisnil(cb)) { // continue loop?
                            LuaObject.setobjs2s(L, TValue.Companion.minus(cb, 1), cb) // save control variable
                            dojump(L, pc, LuaOpCodes.GETARG_sBx(pc[0])) // jump back
                        }
                        val pc_ref3 = arrayOfNulls<InstructionPtr>(1)
                        pc_ref3[0] = pc
                        InstructionPtr.Companion.inc(pc_ref3) //ref
                        pc = pc_ref3[0]!!
                        continue@loop
                    }
                    OpCode.OP_SETLIST -> {
                        var n = LuaOpCodes.GETARG_B(i)
                        var c = LuaOpCodes.GETARG_C(i)
                        var last: Int
                        var h: Table
                        if (n == 0) {
                            n = LuaLimits.cast_int(TValue.Companion.minus(L.top, ra)) - 1
                            L.top = L.ci!!.top
                        }
                        if (c == 0) {
                            c = LuaLimits.cast_int_instruction(pc[0])
                            val pc_ref5 = arrayOfNulls<InstructionPtr>(1)
                            pc_ref5[0] = pc
                            InstructionPtr.Companion.inc(pc_ref5) //ref
                            pc = pc_ref5[0]!!
                        }
                        runtime_check(L, LuaObject.ttistable(ra))
                        h = LuaObject.hvalue(ra)!!
                        last = (c - 1) * LuaOpCodes.LFIELDS_PER_FLUSH + n
                        if (last > h.sizearray) { // needs more space?
                            LuaTable.luaH_resizearray(L, h, last) // pre-alloc it at once
                        }
                        while (n > 0) {
                            val `val`: TValue = TValue.Companion.plus(ra, n)
                            LuaObject.setobj2t(L, LuaTable.luaH_setnum(L, h, last--), `val`)
                            LuaGC.luaC_barriert(L, h, `val`)
                            n--
                        }
                        continue@loop
                    }
                    OpCode.OP_CLOSE -> {
                        LuaFunc.luaF_close(L, ra)
                        continue@loop
                    }
                    OpCode.OP_CLOSURE -> {
                        var p: Proto
                        var ncl: Closure?
                        var nup: Int
                        var j: Int
                        p = cl.p!!.p!![LuaOpCodes.GETARG_Bx(i)]!!
                        nup = p.nups.toInt()
                        ncl = LuaFunc.luaF_newLclosure(L, nup, cl.getEnv())
                        ncl!!.l.p = p
                        j = 0
                        while (j < nup) {
                            if (LuaOpCodes.GET_OPCODE(pc[0]) == OpCode.OP_GETUPVAL) {
                                ncl.l.upvals!![j] = cl.upvals!![LuaOpCodes.GETARG_B(pc[0])]!!
                            } else {
                                LuaLimits.lua_assert(LuaOpCodes.GET_OPCODE(pc[0]) == OpCode.OP_MOVE)
                                ncl.l.upvals!![j] = LuaFunc.luaF_findupval(
                                    L,
                                    TValue.Companion.plus(base_, LuaOpCodes.GETARG_B(pc[0]))
                                )
                            }
                            j++
                            val pc_ref4 = arrayOfNulls<InstructionPtr>(1)
                            pc_ref4[0] = pc
                            InstructionPtr.Companion.inc(pc_ref4) //ref
                            pc = pc_ref4[0]!!
                        }
                        LuaObject.setclvalue(L, ra, ncl)
                        //Protect(
                        L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                        LuaGC.luaC_checkGC(L)
                        base_ = L.base_!!
                        //);
                        continue@loop
                    }
                    OpCode.OP_VARARG -> {
                        var b = LuaOpCodes.GETARG_B(i) - 1
                        var j: Int
                        val ci = L.ci
                        val n: Int = LuaLimits.cast_int(TValue.Companion.minus(ci!!.base_, ci.func)) - cl.p!!.numparams - 1
                        if (b == Lua.LUA_MULTRET) { //Protect(
                            L.savedpc = InstructionPtr.Companion.Assign(pc)!!
                            LuaDo.luaD_checkstack(L, n)
                            base_ = L.base_!!
                            //);
                            ra = RA(L, base_, i) // previous call may change the stack
                            b = n
                            L.top = TValue.Companion.plus(ra, n)
                        }
                        j = 0
                        while (j < b) {
                            if (j < n) {
                                LuaObject.setobjs2s(
                                    L,
                                    TValue.Companion.plus(ra, j),
                                    TValue.Companion.plus(TValue.Companion.minus(ci!!.base_, n)!!, j)
                                ) //FIXME:
                            } else {
                                LuaObject.setnilvalue(TValue.Companion.plus(ra, j))
                            }
                            j++
                        }
                        continue@loop
                    }
                }
                if (reentry2 == true) {
                    reentry = true
                    break
                }
            }
            if (reentry == true) {
                continue
            } else {
                break
            }
        } //end while
    }
}