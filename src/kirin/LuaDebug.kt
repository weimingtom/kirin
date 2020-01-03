package kirin
import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_Debug
import kirin.LuaObject.TValue
import kirin.LuaState.CallInfo
import kirin.Lua.lua_Hook
import kirin.LuaObject.Proto
import kirin.LuaObject.Table
import kirin.LuaCode.InstructionPtr
import kirin.LuaOpCodes.OpCode
import kirin.LuaOpCodes.OpMode
import kirin.LuaOpCodes.OpArgMask
import kotlin.experimental.and

//
// ** $Id: ldebug.c,v 2.29.1.6 2008/05/08 16:56:26 roberto Exp $
// ** Debug Interface
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
//using StkId = TValue;
//using Instruction = System.UInt32;
object LuaDebug {
    fun pcRel(pc: InstructionPtr, p: Proto?): Int {
        ClassType.Companion.Assert(pc.codes == p!!.code)
        return pc.pc - 1
    }

    fun getline(f: Proto?, pc: Int): Int {
        return if (f!!.lineinfo != null) f!!.lineinfo!!.get(pc) else 0
    }

    fun resethookcount(L: lua_State?) {
        L!!.hookcount = L!!.basehookcount
    }

    private fun currentpc(L: lua_State?, ci: CallInfo?): Int {
        if (!LuaState.isLua(ci)) {
            return -1 // function is not a Lua function?
        }
        if (ci === L!!.ci) {
            ci!!.savedpc = InstructionPtr.Companion.Assign(L!!.savedpc)
        }
        return pcRel(ci!!.savedpc!!, LuaState.ci_func(ci)!!.l.p)
    }

    private fun currentline(L: lua_State?, ci: CallInfo): Int {
        val pc = currentpc(L, ci)
        return if (pc < 0) {
            -1 // only active lua functions have current-line information
        } else {
            getline(LuaState.ci_func(ci)!!.l.p, pc)
        }
    }

    //
//		 ** this function can be called asynchronous (e.g. during a signal)
//
    fun lua_sethook(L: lua_State?, func: lua_Hook?, mask: Int, count: Int): Int {
        var func: lua_Hook? = func
        var mask = mask
        if (func == null || mask == 0) { // turn off hooks?
            mask = 0
            func = null
        }
        L!!.hook = func
        L!!.basehookcount = count
        resethookcount(L)
        L!!.hookmask = LuaLimits.cast_byte(mask)
        return 1
    }

    fun lua_gethook(L: lua_State): lua_Hook {
        return L.hook!!
    }

    fun lua_gethookmask(L: lua_State): Int {
        return L.hookmask.toInt()
    }

    fun lua_gethookcount(L: lua_State): Int {
        return L.basehookcount
    }

    fun lua_getstack(L: lua_State?, level: Int, ar: lua_Debug): Int {
        var level = level
        val status: Int
        val ci: Array<CallInfo?> = arrayOfNulls<CallInfo>(1)
        ci[0] = CallInfo()
        LuaLimits.lua_lock(L)
        ci[0] = L!!.ci
        while (level > 0 && CallInfo.Companion.greaterThan(ci[0]!!, L!!.base_ci!!.get(0)!!)) {
            //ref
            level--
            if (LuaState.f_isLua(ci[0])) { // Lua function?
                level -= ci[0]!!.tailcalls // skip lost tail calls
            }
            CallInfo.Companion.dec(ci)
        }
        if (level == 0 && CallInfo.Companion.greaterThan(ci!![0]!!, L!!.base_ci!!.get(0)!!)) { // level found?
            status = 1
            ar.i_ci = CallInfo.Companion.minus(ci!![0]!!, L.base_ci!!.get(0)!!)
        } else if (level < 0) { // level is of a lost tail call?
            status = 1
            ar.i_ci = 0
        } else {
            status = 0 // no such level
        }
        LuaLimits.lua_unlock(L)
        return status
    }

    private fun getluaproto(ci: CallInfo): Proto? {
        return if (LuaState.isLua(ci)) LuaState.ci_func(ci)!!.l.p else null
    }

    private fun findlocal(L: lua_State, ci: CallInfo, n: Int): CharPtr? {
        var name: CharPtr? = null
        val fp: Proto? = getluaproto(ci)
        return if (fp != null && CharPtr.Companion.isNotEqual(
                LuaFunc.luaF_getlocalname(
                    fp,
                    n,
                    currentpc(L, ci)
                ).also({ name = it }), null
            )
        ) {
            name // is a local variable in a Lua function
        } else {
            val limit: TValue? = if (ci === L.ci) L.top else CallInfo.Companion.plus(ci, 1).func //StkId
            if (TValue.Companion.minus(limit!!, ci.base_!!) >= n && n > 0) { // is 'n' inside 'ci' stack?
                CharPtr.Companion.toCharPtr("(*temporary)")
            } else {
                null
            }
        }
    }

    fun lua_getlocal(L: lua_State, ar: lua_Debug, n: Int): CharPtr? {
        val ci: CallInfo = L.base_ci!!.get(ar.i_ci)!!
        val name: CharPtr? = findlocal(L, ci, n)
        LuaLimits.lua_lock(L)
        if (CharPtr.Companion.isNotEqual(name, null)) {
            LuaAPI.luaA_pushobject(L, ci.base_!!.get(n - 1))
        }
        LuaLimits.lua_unlock(L)
        return name
    }

    fun lua_setlocal(L: lua_State, ar: lua_Debug, n: Int): CharPtr? {
        val ci: CallInfo = L.base_ci!!.get(ar.i_ci)!!
        val name: CharPtr? = findlocal(L, ci, n)
        LuaLimits.lua_lock(L)
        if (CharPtr.Companion.isNotEqual(name, null)) {
            LuaObject.setobjs2s(L, ci.base_!!.get(n - 1), TValue.Companion.minus(L.top!!, 1)!!)
        }
        val top: Array<TValue?> = arrayOfNulls<TValue>(1)
        top[0] = L.top
        //StkId
        TValue.Companion.dec(top) // pop value  - ref
        L.top = top[0]
        LuaLimits.lua_unlock(L)
        return name
    }

    private fun funcinfo(ar: lua_Debug, cl: LuaObject.Closure) {
        if (cl.c.getIsC().toInt() != 0) {
            ar.source = CharPtr.Companion.toCharPtr("=[C]")
            ar.linedefined = -1
            ar.lastlinedefined = -1
            ar.what = CharPtr.Companion.toCharPtr("C")
        } else {
            ar.source = LuaObject.getstr(cl.l.p!!.source)
            ar.linedefined = cl.l.p!!.linedefined
            ar.lastlinedefined = cl.l.p!!.lastlinedefined
            ar.what =
                if (ar.linedefined == 0) CharPtr.Companion.toCharPtr("main") else CharPtr.Companion.toCharPtr("Lua")
        }
        LuaObject.luaO_chunkid(ar.short_src, ar.source, LuaConf.LUA_IDSIZE)
    }

    private fun info_tailcall(ar: lua_Debug) {
        ar.namewhat = CharPtr.Companion.toCharPtr("")
        ar.name = ar.namewhat
        ar.what = CharPtr.Companion.toCharPtr("tail")
        ar.currentline = -1
        ar.linedefined = ar.currentline
        ar.lastlinedefined = ar.linedefined
        ar.source = CharPtr.Companion.toCharPtr("=(tail call)")
        LuaObject.luaO_chunkid(ar.short_src, ar.source, LuaConf.LUA_IDSIZE)
        ar.nups = 0
    }

    private fun collectvalidlines(L: lua_State?, f: LuaObject.Closure?) {
        if (f == null || f.c.getIsC().toInt() != 0) {
            LuaObject.setnilvalue(L!!.top)
        } else {
            val t: Table = LuaTable.luaH_new(L, 0, 0)
            val lineinfo: IntArray = f.l.p!!.lineinfo!!
            var i: Int
            i = 0
            while (i < f.l.p!!.sizelineinfo) {
                LuaObject.setbvalue(LuaTable.luaH_setnum(L, t, lineinfo[i]), 1)
                i++
            }
            LuaObject.sethvalue(L, L!!.top!!, t)
        }
        LuaDo.incr_top(L)
    }

    private fun auxgetinfo(L: lua_State?, what: CharPtr?, ar: lua_Debug, f: LuaObject.Closure?, ci: CallInfo?): Int {
        var what: CharPtr? = what
        var status = 1
        if (f == null) {
            info_tailcall(ar)
            return status
        }
        while (what!!.get(0).toInt() != 0) {
            when (what!!.get(0)) {
                'S' -> {
                    funcinfo(ar, f)
                }
                'l' -> {
                    ar.currentline = if (ci != null) currentline(L, ci) else -1
                }
                'u' -> {
                    ar.nups = f.c.getNupvalues().toInt()
                }
                'n' -> {
                    val name_ref: Array<CharPtr?> = arrayOfNulls<CharPtr>(1)
                    name_ref[0] = ar.name
                    ar.namewhat = if (ci != null) getfuncname(L, ci, name_ref) else null //ref
                    ar.name = name_ref[0]
                    if (CharPtr.Companion.isEqual(ar.namewhat, null)) {
                        ar.namewhat = CharPtr.Companion.toCharPtr("") // not found
                        ar.name = null
                    }
                }
                'L', 'f' -> {
                }
                else -> {
                    status = 0
                }
            }
            what = what!!.next()
        }
        return status
    }

    fun lua_getinfo(L: lua_State?, what: CharPtr?, ar: lua_Debug): Int {
        var what: CharPtr? = what
        val status: Int
        var f: LuaObject.Closure? = null
        var ci: CallInfo? = null
        LuaLimits.lua_lock(L)
        if (CharPtr.Companion.isEqualChar(what!!, '>')) {
            val func: TValue = TValue.Companion.minus(L!!.top!!, 1)!! //StkId
            LuaConf.luai_apicheck(L, LuaObject.ttisfunction(func))
            what = what!!.next() // skip the '>'
            f = LuaObject.clvalue(func)
            val top: Array<TValue?> = arrayOfNulls<TValue>(1)
            top[0] = L!!.top
            //StkId
            TValue.Companion.dec(top) // pop function  - ref
            L!!.top = top[0]
        } else if (ar.i_ci != 0) { // no tail call?
            ci = L!!.base_ci!!.get(ar.i_ci)
            LuaLimits.lua_assert(LuaObject.ttisfunction(ci!!.func))
            f = LuaObject.clvalue(ci!!.func)
        }
        status = auxgetinfo(L, what, ar, f, ci)
        if (CharPtr.Companion.isNotEqual(CLib.strchr(what, 'f'), null)) {
            if (f == null) {
                LuaObject.setnilvalue(L!!.top)
            } else {
                LuaObject.setclvalue(L, L!!.top!!, f)
            }
            LuaDo.incr_top(L)
        }
        if (CharPtr.Companion.isNotEqual(CLib.strchr(what, 'L'), null)) {
            collectvalidlines(L, f)
        }
        LuaLimits.lua_unlock(L)
        return status
    }

    //
//		 ** {======================================================
//		 ** Symbolic Execution and code checker
//		 ** =======================================================
//
    private fun checkjump(pt: Proto, pc: Int): Int {
        return if (!(0 <= pc && pc < pt.sizecode)) {
            0
        } else 1
    }

    private fun checkreg(pt: Proto?, reg: Int): Int {
        return if (reg >= pt!!.maxstacksize) {
            0
        } else 1
    }

    private fun precheck(pt: Proto?): Int {
        if (pt!!.maxstacksize > LuaLimits.MAXSTACK) {
            return 0
        }
        if (pt!!.numparams + (pt!!.is_vararg and LuaObject.VARARG_HASARG.toByte()) > pt!!.maxstacksize) {
            return 0
        }
        if (!(pt!!.is_vararg and LuaObject.VARARG_NEEDSARG.toByte() == 0.toByte() || pt!!.is_vararg and LuaObject.VARARG_HASARG.toByte() != (0 as Byte))) {
            return 0
        }
        if (pt!!.sizeupvalues > pt!!.nups) {
            return 0
        }
        if (!(pt!!.sizelineinfo == pt!!.sizecode || pt!!.sizelineinfo == 0)) {
            return 0
        }
        return if (!(pt!!.sizecode > 0 && LuaOpCodes.GET_OPCODE(pt!!.code!!.get(pt!!.sizecode - 1)) == OpCode.OP_RETURN)) {
            0
        } else 1
    }

    fun checkopenop(pt: Proto?, pc: Int): Int {
        return luaG_checkopenop(pt!!.code!!.get(pc + 1))
    }

    fun luaG_checkopenop(i: Long): Int { //Instruction - UInt32
        return when (LuaOpCodes.GET_OPCODE(i)) {
            OpCode.OP_CALL, OpCode.OP_TAILCALL, OpCode.OP_RETURN, OpCode.OP_SETLIST -> {
                if (LuaOpCodes.GETARG_B(i) != 0) {
                    0
                } else 1
            }
            else -> {
                0 // invalid instruction after an open call
            }
        }
    }

    private fun checkArgMode(pt: Proto?, r: Int, mode: OpArgMask): Int {
        when (mode) {
            OpArgMask.OpArgN -> {
                if (r != 0) {
                    return 0
                }
            }
            OpArgMask.OpArgU -> {
            }
            OpArgMask.OpArgR -> {
                checkreg(pt, r)
            }
            OpArgMask.OpArgK -> {
                if (!(if (LuaOpCodes.ISK(r) != 0) LuaOpCodes.INDEXK(r) < pt!!.sizek else r < pt!!.maxstacksize)) {
                    return 0
                }
            }
        }
        return 1
    }

    private fun symbexec(pt: Proto?, lastpc: Int, reg: Int): Long { //Instruction - UInt32
        var pc: Int
        var last: Int // stores position of last instruction that changed `reg'
        var dest: Int
        last = pt!!.sizecode - 1 // points to final return (a `neutral' instruction)
        if (precheck(pt) == 0) {
            return 0
        }
        pc = 0
        while (pc < lastpc) {
            val i: Long = pt!!.code!!.get(pc) //Instruction - UInt32
            val op: OpCode = LuaOpCodes.GET_OPCODE(i)
            val a: Int = LuaOpCodes.GETARG_A(i)
            var b = 0
            var c = 0
            if (op.getValue() >= LuaOpCodes.NUM_OPCODES) {
                return 0
            }
            checkreg(pt, a)
            when (LuaOpCodes.getOpMode(op)) {
                OpMode.iABC -> {
                    b = LuaOpCodes.GETARG_B(i)
                    c = LuaOpCodes.GETARG_C(i)
                    if (checkArgMode(pt, b, LuaOpCodes.getBMode(op)) == 0) {
                        return 0
                    }
                    if (checkArgMode(pt, c, LuaOpCodes.getCMode(op)) == 0) {
                        return 0
                    }
                }
                OpMode.iABx -> {
                    b = LuaOpCodes.GETARG_Bx(i)
                    if (LuaOpCodes.getBMode(op) == OpArgMask.OpArgK) {
                        if (b >= pt!!.sizek) {
                            return 0
                        }
                    }
                }
                OpMode.iAsBx -> {
                    b = LuaOpCodes.GETARG_sBx(i)
                    if (LuaOpCodes.getBMode(op) == OpArgMask.OpArgR) {
                        dest = pc + 1 + b
                        if (!(0 <= dest && dest < pt!!.sizecode)) {
                            return 0
                        }
                        if (dest > 0) {
                            var j: Int
                            //                                     check that it does not jump to a setlist count; this
//					   is tricky, because the count from a previous setlist may
//					   have the same value of an invalid setlist; so, we must
//					   go all the way back to the first of them (if any)
                            j = 0
                            while (j < dest) {
                                val d: Long = pt!!.code!!.get(dest - 1 - j) //Instruction - UInt32
                                if (!(LuaOpCodes.GET_OPCODE(d) == OpCode.OP_SETLIST && LuaOpCodes.GETARG_C(d) == 0)) {
                                    break
                                }
                                j++
                            }
                            //                                     if 'j' is even, previous value is not a setlist (even if
//					   it looks like one)
                            if (j and 1 != 0) {
                                return 0
                            }
                        }
                    }
                }
            }
            if (LuaOpCodes.testAMode(op) != 0) {
                if (a == reg) {
                    last = pc // change register `a'
                }
            }
            if (LuaOpCodes.testTMode(op) != 0) {
                if (pc + 2 >= pt!!.sizecode) {
                    return 0 // check skip
                }
                if (LuaOpCodes.GET_OPCODE(pt!!.code!!.get(pc + 1)) != OpCode.OP_JMP) {
                    return 0
                }
            }
            when (op) {
                OpCode.OP_LOADBOOL -> {
                    if (c == 1) { // does it jump?
                        if (pc + 2 >= pt!!.sizecode) {
                            return 0 // check its jump
                        }
                        if (!(LuaOpCodes.GET_OPCODE(pt!!.code!!.get(pc + 1)) != OpCode.OP_SETLIST || LuaOpCodes.GETARG_C(
                                pt!!.code!!.get(
                                    pc + 1
                                )
                            ) != 0)
                        ) {
                            return 0
                        }
                    }
                }
                OpCode.OP_LOADNIL -> {
                    if (a <= reg && reg <= b) {
                        last = pc // set registers from `a' to `b'
                    }
                }
                OpCode.OP_GETUPVAL, OpCode.OP_SETUPVAL -> {
                    if (b >= pt!!.nups) {
                        return 0
                    }
                }
                OpCode.OP_GETGLOBAL, OpCode.OP_SETGLOBAL -> {
                    if (!LuaObject.ttisstring(pt!!.k!!.get(b))) {
                        return 0
                    }
                }
                OpCode.OP_SELF -> {
                    checkreg(pt, a + 1)
                    if (reg == a + 1) {
                        last = pc
                    }
                }
                OpCode.OP_CONCAT -> {
                    if (b >= c) {
                        return 0 // at least two operands
                    }
                }
                OpCode.OP_TFORLOOP -> {
                    if (c < 1) {
                        return 0 // at least one result (control variable)
                    }
                    checkreg(pt, a + 2 + c) // space for results
                    if (reg >= a + 2) {
                        last = pc // affect all regs above its base
                    }
                }
                OpCode.OP_FORLOOP, OpCode.OP_FORPREP -> {
                    checkreg(pt, a + 3)
                    // go through ...no, on second thoughts don't, because this is C#
                    dest = pc + 1 + b
                    // not full check and jump is forward and do not skip `lastpc'?
                    if (reg != LuaOpCodes.NO_REG && pc < dest && dest <= lastpc) {
                        pc += b // do the jump
                    }
                }
                OpCode.OP_JMP -> {
                    dest = pc + 1 + b
                    // not full check and jump is forward and do not skip `lastpc'?
                    if (reg != LuaOpCodes.NO_REG && pc < dest && dest <= lastpc) {
                        pc += b // do the jump
                    }
                }
                OpCode.OP_CALL, OpCode.OP_TAILCALL -> {
                    if (b != 0) {
                        checkreg(pt, a + b - 1)
                    }
                    c-- // c = num. returns
                    if (c == Lua.LUA_MULTRET) {
                        if (checkopenop(pt, pc) == 0) {
                            return 0
                        }
                    } else if (c != 0) {
                        checkreg(pt, a + c - 1)
                    }
                    if (reg >= a) {
                        last = pc // affect all registers above base
                    }
                }
                OpCode.OP_RETURN -> {
                    b-- // b = num. returns
                    if (b > 0) {
                        checkreg(pt, a + b - 1)
                    }
                }
                OpCode.OP_SETLIST -> {
                    if (b > 0) {
                        checkreg(pt, a + b)
                    }
                    if (c == 0) {
                        pc++
                        if (pc >= pt!!.sizecode - 1) {
                            return 0
                        }
                    }
                }
                OpCode.OP_CLOSURE -> {
                    var nup: Int
                    var j: Int
                    if (b >= pt!!.sizep) {
                        return 0
                    }
                    nup = pt!!.p!!.get(b)!!.nups.toInt()
                    if (pc + nup >= pt.sizecode) {
                        return 0
                    }
                    j = 1
                    while (j <= nup) {
                        val op1: OpCode = LuaOpCodes.GET_OPCODE(pt.code!!.get(pc + j))
                        if (!(op1 == OpCode.OP_GETUPVAL || op1 == OpCode.OP_MOVE)) {
                            return 0
                        }
                        j++
                    }
                    if (reg != LuaOpCodes.NO_REG) { // tracing?
                        pc += nup // do not 'execute' these pseudo-instructions
                    }
                }
                OpCode.OP_VARARG -> {
                    if (!(pt!!.is_vararg and LuaObject.VARARG_ISVARARG.toByte() != 0.toByte() && pt.is_vararg and LuaObject.VARARG_NEEDSARG.toByte() == (0 as Byte))) {
                        return 0
                    }
                    b--
                    if (b == Lua.LUA_MULTRET) {
                        if (checkopenop(pt, pc) == 0) {
                            return 0
                        }
                    }
                    checkreg(pt, a + b - 1)
                }
                else -> {
                }
            }
            pc++
        }
        return pt!!.code!!.get(last)
    }

    ///#undef check
///#undef checkjump
///#undef checkreg
// }======================================================
    fun luaG_checkcode(pt: Proto?): Int {
        return if (symbexec(pt, pt!!.sizecode, LuaOpCodes.NO_REG) != 0L) 1 else 0
    }

    private fun kname(p: Proto?, c: Int): CharPtr? {
        return if (LuaOpCodes.ISK(c) != 0 && LuaObject.ttisstring(p!!.k!!.get(LuaOpCodes.INDEXK(c)))) {
            LuaObject.svalue(p!!.k!!.get(LuaOpCodes.INDEXK(c))!!)
        } else {
            CharPtr.Companion.toCharPtr("?")
        }
    }

    private fun getobjname(L: lua_State?, ci: CallInfo?, stackpos: Int, name: Array<CharPtr?>): CharPtr? { //ref
        if (LuaState.isLua(ci)) { // a Lua function?
            val p: Proto? = LuaState.ci_func(ci)!!.l.p
            val pc = currentpc(L, ci)
            val i: Long //Instruction - UInt32
            name[0] = LuaFunc.luaF_getlocalname(p!!, stackpos + 1, pc)
            if (CharPtr.Companion.isNotEqual(name[0], null)) { // is a local?
                return CharPtr.Companion.toCharPtr("local")
            }
            i = symbexec(p, pc, stackpos) // try symbolic execution
            LuaLimits.lua_assert(pc != -1)
            when (LuaOpCodes.GET_OPCODE(i)) {
                OpCode.OP_GETGLOBAL -> {
                    val g: Int = LuaOpCodes.GETARG_Bx(i) // global index
                    LuaLimits.lua_assert(LuaObject.ttisstring(p!!.k!!.get(g)))
                    name[0] = LuaObject.svalue(p!!.k!!.get(g)!!)
                    return CharPtr.Companion.toCharPtr("global")
                }
                OpCode.OP_MOVE -> {
                    val a: Int = LuaOpCodes.GETARG_A(i)
                    val b: Int = LuaOpCodes.GETARG_B(i) // move from `b' to `a'
                    if (b < a) {
                        return getobjname(L, ci, b, name) // get name for `b'  - ref
                    }
                }
                OpCode.OP_GETTABLE -> {
                    val k: Int = LuaOpCodes.GETARG_C(i) // key index
                    name[0] = kname(p, k)
                    return CharPtr.Companion.toCharPtr("field")
                }
                OpCode.OP_GETUPVAL -> {
                    val u: Int = LuaOpCodes.GETARG_B(i) // upvalue index
                    name[0] =
                        if (p!!.upvalues != null) LuaObject.getstr(p!!.upvalues!!.get(u)) else CharPtr.Companion.toCharPtr("?")
                    return CharPtr.Companion.toCharPtr("upvalue")
                }
                OpCode.OP_SELF -> {
                    val k: Int = LuaOpCodes.GETARG_C(i) // key index
                    name[0] = kname(p, k)
                    return CharPtr.Companion.toCharPtr("method")
                }
                else -> {
                }
            }
        }
        return null // no useful name found
    }

    private fun getfuncname(L: lua_State?, ci: CallInfo?, name: Array<CharPtr?>): CharPtr? { //ref
        var ci: CallInfo? = ci
        val i: Long //Instruction - UInt32
        if (LuaState.isLua(ci) && ci!!.tailcalls > 0 || !LuaState.isLua(CallInfo.Companion.minus(ci!!, 1))) {
            return null // calling function is not Lua (or is unknown)
        }
        val ci_ref: Array<CallInfo?> = arrayOfNulls<CallInfo>(1)
        ci_ref[0] = ci
        CallInfo.Companion.dec(ci_ref) // calling function  - ref
        ci = ci_ref[0]
        i = LuaState.ci_func(ci)!!.l.p!!.code!!.get(currentpc(L, ci))
        return if (LuaOpCodes.GET_OPCODE(i) == OpCode.OP_CALL || LuaOpCodes.GET_OPCODE(i) == OpCode.OP_TAILCALL || LuaOpCodes.GET_OPCODE(
                i
            ) == OpCode.OP_TFORLOOP
        ) {
            getobjname(L, ci, LuaOpCodes.GETARG_A(i), name) //ref
        } else {
            null // no useful name can be found
        }
    }

    // only ANSI way to check whether a pointer points to an array
    private fun isinstack(ci: CallInfo, o: TValue): Int {
        val p: Array<TValue?> = arrayOfNulls<TValue>(1) //StkId
        p[0] = TValue()
        p[0] = ci.base_
        while (TValue.Companion.lessThan(p!![0]!!, ci!!.top!!)) {
            //ref - StkId
            if (o === p[0]) {
                return 1
            }
            TValue.Companion.inc(p)
        }
        return 0
    }

    fun luaG_typeerror(L: lua_State?, o: TValue, op: CharPtr?) {
        var name: CharPtr? = null
        val t: CharPtr = LuaTM.luaT_typenames.get(LuaObject.ttype(o))
        val name_ref: Array<CharPtr?> = arrayOfNulls<CharPtr>(1)
        name_ref[0] = name
        val kind: CharPtr? = if (isinstack(L!!.ci!!, o) != 0) getobjname(
            L,
            L.ci,
            LuaLimits.cast_int(TValue.Companion.minus(o, L!!.base_!!)),
            name_ref
        ) else null //ref
        name = name_ref[0]
        if (CharPtr.Companion.isNotEqual(kind, null)) {
            luaG_runerror(
                L,
                CharPtr.Companion.toCharPtr("attempt to %s %s " + LuaConf.getLUA_QS() + " (a %s value)"),
                op,
                kind,
                name,
                t
            )
        } else {
            luaG_runerror(L, CharPtr.Companion.toCharPtr("attempt to %s a %s value"), op, t)
        }
    }

    fun luaG_concaterror(L: lua_State?, p1: TValue, p2: TValue) { //StkId - StkId
        var p1: TValue = p1
        if (LuaObject.ttisstring(p1) || LuaObject.ttisnumber(p1)) {
            p1 = p2
        }
        LuaLimits.lua_assert(!LuaObject.ttisstring(p1) && !LuaObject.ttisnumber(p1))
        luaG_typeerror(L, p1, CharPtr.Companion.toCharPtr("concatenate"))
    }

    fun luaG_aritherror(L: lua_State?, p1: TValue, p2: TValue) {
        var p2: TValue = p2
        val temp = TValue()
        if (LuaVM.luaV_tonumber(p1, temp) == null) {
            p2 = p1 // first operand is wrong
        }
        luaG_typeerror(L, p2, CharPtr.Companion.toCharPtr("perform arithmetic on"))
    }

    fun luaG_ordererror(L: lua_State?, p1: TValue?, p2: TValue?): Int {
        val t1: CharPtr = LuaTM.luaT_typenames.get(LuaObject.ttype(p1))
        val t2: CharPtr = LuaTM.luaT_typenames.get(LuaObject.ttype(p2))
        if (t1.get(2) == t2.get(2)) {
            luaG_runerror(L, CharPtr.Companion.toCharPtr("attempt to compare two %s values"), t1)
        } else {
            luaG_runerror(L, CharPtr.Companion.toCharPtr("attempt to compare %s with %s"), t1, t2)
        }
        return 0
    }

    private fun addinfo(L: lua_State?, msg: CharPtr?) {
        val ci: CallInfo = L!!.ci!!
        if (LuaState.isLua(ci)) { // is Lua code?
            val buff = CharPtr(CharArray(LuaConf.LUA_IDSIZE)) // add file:line information
            val line = currentline(L, ci)
            LuaObject.luaO_chunkid(buff, LuaObject.getstr(getluaproto(ci)!!.source), LuaConf.LUA_IDSIZE)
            LuaObject.luaO_pushfstring(L, CharPtr.Companion.toCharPtr("%s:%d: %s"), buff, line, msg)
        }
    }

    fun luaG_errormsg(L: lua_State?) {
        if (L!!.errfunc != 0) { // is there an error handling function?
            val errfunc: TValue = LuaDo.restorestack(L, L!!.errfunc)!! //StkId
            if (!LuaObject.ttisfunction(errfunc)) {
                LuaDo.luaD_throw(L, Lua.LUA_ERRERR)
            }
            LuaObject.setobjs2s(L, L.top, TValue.Companion.minus(L!!.top!!, 1)!!) // move argument
            LuaObject.setobjs2s(L, TValue.Companion.minus(L!!.top!!, 1), errfunc) // push function
            LuaDo.incr_top(L)
            LuaDo.luaD_call(L, TValue.Companion.minus(L!!.top!!, 2)!!, 1) // call it
        }
        LuaDo.luaD_throw(L, Lua.LUA_ERRRUN)
    }

    fun luaG_runerror(L: lua_State?, fmt: CharPtr?, vararg argp: Any?) {
        addinfo(L, LuaObject.luaO_pushvfstring(L, fmt, *argp))
        luaG_errormsg(L)
    }
}