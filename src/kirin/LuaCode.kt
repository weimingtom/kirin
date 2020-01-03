package kirin

import kirin.CLib.CharPtr
import kirin.LuaObject.TString
import kirin.LuaObject.TValue
import kirin.LuaParser.FuncState
import kirin.LuaParser.expdesc
import kirin.LuaOpCodes.OpCode
import kirin.LuaParser.expkind
import kirin.LuaOpCodes.OpMode
import kirin.LuaOpCodes.OpArgMask

//
// ** $Id: lcode.c,v 2.25.1.3 2007/12/28 15:32:23 roberto Exp $
// ** Code generator for Lua
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
//using lua_Number = System.Double;
//using Instruction = System.UInt32;
object LuaCode {
    //
//		 ** Marks the end of a patch list. It is an invalid value both as an absolute
//		 ** address, and as a list link (would link an element to itself).
//
    const val NO_JUMP = -1

    fun getcode(fs: FuncState, e: expdesc): InstructionPtr {
        return InstructionPtr(fs.f!!.code, e.u.s.info)
    }

    fun luaK_codeAsBx(fs: FuncState, o: OpCode?, A: Int, sBx: Int): Int {
        return luaK_codeABx(fs, o, A, sBx + LuaOpCodes.MAXARG_sBx)
    }

    fun luaK_setmultret(fs: FuncState, e: expdesc) {
        luaK_setreturns(fs, e, Lua.LUA_MULTRET)
    }

    fun hasjumps(e: expdesc): Boolean {
        return e.t != e.f
    }

    private fun isnumeral(e: expdesc): Int {
        return if (e.k == expkind.VKNUM && e.t == NO_JUMP && e.f == NO_JUMP) 1 else 0
    }

    fun luaK_nil(fs: FuncState, from: Int, n: Int) {
        val previous: InstructionPtr
        if (fs.pc > fs.lasttarget) { // no jumps to current position?
            if (fs.pc == 0) { // function start?
                if (from >= fs.nactvar) {
                    return  // positions are already clean
                }
            } else {
                previous = InstructionPtr(fs.f!!.code, fs.pc - 1)
                if (LuaOpCodes.GET_OPCODE(previous) == OpCode.OP_LOADNIL) {
                    val pfrom = LuaOpCodes.GETARG_A(previous)
                    val pto = LuaOpCodes.GETARG_B(previous)
                    if (pfrom <= from && from <= pto + 1) { // can connect both?
                        if (from + n - 1 > pto) {
                            LuaOpCodes.SETARG_B(previous, from + n - 1)
                        }
                        return
                    }
                }
            }
        }
        luaK_codeABC(fs, OpCode.OP_LOADNIL, from, from + n - 1, 0) // else no optimization
    }

    fun luaK_jump(fs: FuncState): Int {
        val jpc = fs.jpc // save list of jumps to here
        val j = IntArray(1)
        j[0] = 0
        fs.jpc = NO_JUMP
        j[0] = luaK_codeAsBx(fs, OpCode.OP_JMP, 0, NO_JUMP)
        luaK_concat(fs, j, jpc) // keep them on hold  - ref
        return j[0]
    }

    fun luaK_ret(fs: FuncState, first: Int, nret: Int) {
        luaK_codeABC(fs, OpCode.OP_RETURN, first, nret + 1, 0)
    }

    private fun condjump(fs: FuncState, op: OpCode, A: Int, B: Int, C: Int): Int {
        luaK_codeABC(fs, op, A, B, C)
        return luaK_jump(fs)
    }

    private fun fixjump(fs: FuncState, pc: Int, dest: Int) {
        val jmp = InstructionPtr(fs.f!!.code, pc)
        val offset = dest - (pc + 1)
        LuaLimits.lua_assert(dest != NO_JUMP)
        if (Math.abs(offset) > LuaOpCodes.MAXARG_sBx) {
            LuaLex.luaX_syntaxerror(fs.ls!!, CharPtr.Companion.toCharPtr("control structure too long"))
        }
        LuaOpCodes.SETARG_sBx(jmp, offset)
    }

    //
//		 ** returns current `pc' and marks it as a jump target (to avoid wrong
//		 ** optimizations with consecutive instructions not in the same basic block).
//
    fun luaK_getlabel(fs: FuncState): Int {
        fs.lasttarget = fs.pc
        return fs.pc
    }

    private fun getjump(fs: FuncState, pc: Int): Int {
        val offset = LuaOpCodes.GETARG_sBx(fs.f!!.code!![pc])
        return if (offset == NO_JUMP) { // point to itself represents end of list
            NO_JUMP // end of list
        } else {
            pc + 1 + offset // turn offset into absolute position
        }
    }

    private fun getjumpcontrol(fs: FuncState, pc: Int): InstructionPtr {
        val pi = InstructionPtr(fs.f!!.code, pc)
        return if (pc >= 1 && LuaOpCodes.testTMode(LuaOpCodes.GET_OPCODE(pi[-1])) != 0) {
            InstructionPtr(pi.codes, pi.pc - 1)
        } else {
            InstructionPtr(pi.codes, pi.pc)
        }
    }

    //
//		 ** check whether list has any jump that do not produce a value
//		 ** (or produce an inverted value)
//
    private fun need_value(fs: FuncState, list: Int): Int {
        var list = list
        while (list != NO_JUMP) {
            val i = getjumpcontrol(fs, list)
            if (LuaOpCodes.GET_OPCODE(i[0]) != OpCode.OP_TESTSET) {
                return 1
            }
            list = getjump(fs, list)
        }
        return 0 // not found
    }

    private fun patchtestreg(fs: FuncState, node: Int, reg: Int): Int {
        val i = getjumpcontrol(fs, node)
        if (LuaOpCodes.GET_OPCODE(i[0]) != OpCode.OP_TESTSET) {
            return 0 // cannot patch other instructions
        }
        if (reg != LuaOpCodes.NO_REG && reg != LuaOpCodes.GETARG_B(i[0])) {
            LuaOpCodes.SETARG_A(i, reg)
        } else { // no register to put value or register already has the value
            i[0] = (LuaOpCodes.CREATE_ABC(
                OpCode.OP_TEST,
                LuaOpCodes.GETARG_B(i[0]),
                0,
                LuaOpCodes.GETARG_C(i[0])
            ) and -0x1).toLong() //uint
        }
        return 1
    }

    private fun removevalues(fs: FuncState, list: Int) {
        var list = list
        while (list != NO_JUMP) {
            patchtestreg(fs, list, LuaOpCodes.NO_REG)
            list = getjump(fs, list)
        }
    }

    private fun patchlistaux(fs: FuncState, list: Int, vtarget: Int, reg: Int, dtarget: Int) {
        var list = list
        while (list != NO_JUMP) {
            val next = getjump(fs, list)
            if (patchtestreg(fs, list, reg) != 0) {
                fixjump(fs, list, vtarget)
            } else {
                fixjump(fs, list, dtarget) // jump to default target
            }
            list = next
        }
    }

    private fun dischargejpc(fs: FuncState) {
        patchlistaux(fs, fs.jpc, fs.pc, LuaOpCodes.NO_REG, fs.pc)
        fs.jpc = NO_JUMP
    }

    fun luaK_patchlist(fs: FuncState, list: Int, target: Int) {
        if (target == fs.pc) {
            luaK_patchtohere(fs, list)
        } else {
            LuaLimits.lua_assert(target < fs.pc)
            patchlistaux(fs, list, target, LuaOpCodes.NO_REG, target)
        }
    }

    fun luaK_patchtohere(fs: FuncState, list: Int) {
        luaK_getlabel(fs)
        val jpc_ref = IntArray(1)
        jpc_ref[0] = fs.jpc
        luaK_concat(fs, jpc_ref, list) //ref
        fs.jpc = jpc_ref[0]
    }

    fun luaK_concat(fs: FuncState, l1: IntArray, l2: Int) { //ref
        if (l2 == NO_JUMP) {
            return
        } else if (l1[0] == NO_JUMP) {
            l1[0] = l2
        } else {
            var list = l1[0]
            var next: Int
            while (getjump(fs, list).also { next = it } != NO_JUMP) { // find last element
                list = next
            }
            fixjump(fs, list, l2)
        }
    }

    fun luaK_checkstack(fs: FuncState, n: Int) {
        val newstack = fs.freereg + n
        if (newstack > fs.f!!.maxstacksize) {
            if (newstack >= LuaLimits.MAXSTACK) {
                LuaLex.luaX_syntaxerror(fs.ls!!, CharPtr.Companion.toCharPtr("function or expression too complex"))
            }
            fs.f!!.maxstacksize = LuaLimits.cast_byte(newstack)
        }
    }

    fun luaK_reserveregs(fs: FuncState, n: Int) {
        luaK_checkstack(fs, n)
        fs.freereg += n
    }

    private fun freereg(fs: FuncState, reg: Int) {
        if (LuaOpCodes.ISK(reg) == 0 && reg >= fs.nactvar) {
            fs.freereg--
            LuaLimits.lua_assert(reg == fs.freereg)
        }
    }

    private fun freeexp(fs: FuncState, e: expdesc) {
        if (e.k == expkind.VNONRELOC) {
            freereg(fs, e.u.s.info)
        }
    }

    private fun addk(fs: FuncState, k: TValue, v: TValue): Int {
        val L = fs.L
        val idx = LuaTable.luaH_set(L, fs.h, k)
        val f = fs.f
        var oldsize = f!!.sizek
        return if (LuaObject.ttisnumber(idx)) {
            LuaLimits.lua_assert(LuaObject.luaO_rawequalObj(fs!!.f!!.k!![LuaLimits.cast_int(LuaObject.nvalue(idx))]!!, v))
            LuaLimits.cast_int(LuaObject.nvalue(idx))
        } else { // constant not found; create a new entry
            LuaObject.setnvalue(idx, LuaLimits.cast_num(fs.nk))
            val k_ref = arrayOfNulls<Array<TValue?>?>(1)
            k_ref[0] = f!!.k
            val sizek_ref = IntArray(1)
            sizek_ref[0] = f!!.sizek
            LuaMem.luaM_growvector_TValue(
                L,
                k_ref,
                fs.nk,
                sizek_ref,
                LuaOpCodes.MAXARG_Bx,
                CharPtr.Companion.toCharPtr("constant table overflow"),
                ClassType(ClassType.Companion.TYPE_TVALUE)
            ) //ref - ref
            f!!.sizek = sizek_ref[0]
            f!!.k = k_ref[0]!!
            while (oldsize < f.sizek) {
                LuaObject.setnilvalue(f.k!![oldsize++])
            }
            LuaObject.setobj(L, f.k!![fs.nk], v)
            LuaGC.luaC_barrier(L, f, v)
            fs.nk++
        }
    }

    fun luaK_stringK(fs: FuncState, s: TString?): Int {
        val o = TValue()
        LuaObject.setsvalue(fs.L, o, s)
        return addk(fs, o, o)
    }

    fun luaK_numberK(fs: FuncState, r: Double): Int { //lua_Number
        val o = TValue()
        LuaObject.setnvalue(o, r)
        return addk(fs, o, o)
    }

    private fun boolK(fs: FuncState, b: Int): Int {
        val o = TValue()
        LuaObject.setbvalue(o, b)
        return addk(fs, o, o)
    }

    private fun nilK(fs: FuncState): Int {
        val k = TValue()
        val v = TValue()
        LuaObject.setnilvalue(v)
        // cannot use nil as key; instead use table itself to represent nil
        LuaObject.sethvalue(fs.L, k, fs.h)
        return addk(fs, k, v)
    }

    fun luaK_setreturns(fs: FuncState, e: expdesc, nresults: Int) {
        if (e.k == expkind.VCALL) { // expression is an open function call?
            LuaOpCodes.SETARG_C(getcode(fs, e), nresults + 1)
        } else if (e.k == expkind.VVARARG) {
            LuaOpCodes.SETARG_B(getcode(fs, e), nresults + 1)
            LuaOpCodes.SETARG_A(getcode(fs, e), fs.freereg)
            luaK_reserveregs(fs, 1)
        }
    }

    fun luaK_setoneret(fs: FuncState, e: expdesc) {
        if (e.k == expkind.VCALL) { // expression is an open function call?
            e.k = expkind.VNONRELOC
            e.u.s.info = LuaOpCodes.GETARG_A(getcode(fs, e))
        } else if (e.k == expkind.VVARARG) {
            LuaOpCodes.SETARG_B(getcode(fs, e), 2)
            e.k = expkind.VRELOCABLE // can relocate its simple result
        }
    }

    fun luaK_dischargevars(fs: FuncState, e: expdesc) {
        when (e.k) {
            expkind.VLOCAL -> {
                e.k = expkind.VNONRELOC
            }
            expkind.VUPVAL -> {
                e.u.s.info = luaK_codeABC(fs, OpCode.OP_GETUPVAL, 0, e.u.s.info, 0)
                e.k = expkind.VRELOCABLE
            }
            expkind.VGLOBAL -> {
                e.u.s.info = luaK_codeABx(fs, OpCode.OP_GETGLOBAL, 0, e.u.s.info)
                e.k = expkind.VRELOCABLE
            }
            expkind.VINDEXED -> {
                freereg(fs, e.u.s.aux)
                freereg(fs, e.u.s.info)
                e.u.s.info = luaK_codeABC(fs, OpCode.OP_GETTABLE, 0, e.u.s.info, e.u.s.aux)
                e.k = expkind.VRELOCABLE
            }
            expkind.VVARARG, expkind.VCALL -> {
                luaK_setoneret(fs, e)
            }
            else -> {
            }
        }
    }

    private fun code_label(fs: FuncState, A: Int, b: Int, jump: Int): Int {
        luaK_getlabel(fs) // those instructions may be jump targets
        return luaK_codeABC(fs, OpCode.OP_LOADBOOL, A, b, jump)
    }

    private fun discharge2reg(fs: FuncState, e: expdesc, reg: Int) {
        luaK_dischargevars(fs, e)
        when (e.k) {
            expkind.VNIL -> {
                luaK_nil(fs, reg, 1)
            }
            expkind.VFALSE, expkind.VTRUE -> {
                luaK_codeABC(fs, OpCode.OP_LOADBOOL, reg, if (e.k == expkind.VTRUE) 1 else 0, 0)
            }
            expkind.VK -> {
                luaK_codeABx(fs, OpCode.OP_LOADK, reg, e.u.s.info)
            }
            expkind.VKNUM -> {
                luaK_codeABx(fs, OpCode.OP_LOADK, reg, luaK_numberK(fs, e.u.nval))
            }
            expkind.VRELOCABLE -> {
                val pc = getcode(fs, e)
                LuaOpCodes.SETARG_A(pc, reg)
            }
            expkind.VNONRELOC -> {
                if (reg != e.u.s.info) {
                    luaK_codeABC(fs, OpCode.OP_MOVE, reg, e.u.s.info, 0)
                }
            }
            else -> {
                LuaLimits.lua_assert(e.k == expkind.VVOID || e.k == expkind.VJMP)
                return  // nothing to do...
            }
        }
        e.u.s.info = reg
        e.k = expkind.VNONRELOC
    }

    private fun discharge2anyreg(fs: FuncState, e: expdesc) {
        if (e.k != expkind.VNONRELOC) {
            luaK_reserveregs(fs, 1)
            discharge2reg(fs, e, fs.freereg - 1)
        }
    }

    private fun exp2reg(fs: FuncState, e: expdesc, reg: Int) {
        discharge2reg(fs, e, reg)
        if (e.k == expkind.VJMP) {
            val t_ref = IntArray(1)
            t_ref[0] = e.t
            luaK_concat(fs, t_ref, e.u.s.info) // put this jump in `t' list  - ref
            e.t = t_ref[0]
        }
        if (hasjumps(e)) {
            val final_: Int // position after whole expression
            var p_f = NO_JUMP // position of an eventual LOAD false
            var p_t = NO_JUMP // position of an eventual LOAD true
            if (need_value(fs, e.t) != 0 || need_value(fs, e.f) != 0) {
                val fj = if (e.k == expkind.VJMP) NO_JUMP else luaK_jump(fs)
                p_f = code_label(fs, reg, 0, 1)
                p_t = code_label(fs, reg, 1, 0)
                luaK_patchtohere(fs, fj)
            }
            final_ = luaK_getlabel(fs)
            patchlistaux(fs, e.f, final_, reg, p_f)
            patchlistaux(fs, e.t, final_, reg, p_t)
        }
        e.t = NO_JUMP
        e.f = e.t
        e.u.s.info = reg
        e.k = expkind.VNONRELOC
    }

    fun luaK_exp2nextreg(fs: FuncState, e: expdesc) {
        luaK_dischargevars(fs, e)
        freeexp(fs, e)
        luaK_reserveregs(fs, 1)
        exp2reg(fs, e, fs.freereg - 1)
    }

    fun luaK_exp2anyreg(fs: FuncState, e: expdesc): Int {
        luaK_dischargevars(fs, e)
        if (e.k == expkind.VNONRELOC) {
            if (!hasjumps(e)) {
                return e.u.s.info // exp is already in a register
            }
            if (e.u.s.info >= fs.nactvar) { // reg. is not a local?
                exp2reg(fs, e, e.u.s.info) // put value on it
                return e.u.s.info
            }
        }
        luaK_exp2nextreg(fs, e) // default
        return e.u.s.info
    }

    fun luaK_exp2val(fs: FuncState, e: expdesc) {
        if (hasjumps(e)) {
            luaK_exp2anyreg(fs, e)
        } else {
            luaK_dischargevars(fs, e)
        }
    }

    fun luaK_exp2RK(fs: FuncState, e: expdesc): Int {
        luaK_exp2val(fs, e)
        when (e.k) {
            expkind.VKNUM, expkind.VTRUE, expkind.VFALSE, expkind.VNIL -> {
                if (fs.nk <= LuaOpCodes.MAXINDEXRK) { // constant fit in RK operand?
                    e.u.s.info =
                        if (e.k == expkind.VNIL) nilK(fs) else if (e.k == expkind.VKNUM) luaK_numberK(
                            fs,
                            e.u.nval
                        ) else boolK(fs, if (e.k == expkind.VTRUE) 1 else 0)
                    e.k = expkind.VK
                    return LuaOpCodes.RKASK(e.u.s.info)
                } else {
                    //break
                }
            }
            expkind.VK -> {
                if (e.u.s.info <= LuaOpCodes.MAXINDEXRK) { // constant fit in argC?
                    return LuaOpCodes.RKASK(e.u.s.info)
                } else {
                    //break
                }
            }
            else -> {
            }
        }
        // not a constant in the right range: put it in a register
        return luaK_exp2anyreg(fs, e)
    }

    fun luaK_storevar(fs: FuncState, `var`: expdesc, ex: expdesc) {
        when (`var`.k) {
            expkind.VLOCAL -> {
                freeexp(fs, ex)
                exp2reg(fs, ex, `var`.u.s.info)
                return
            }
            expkind.VUPVAL -> {
                val e = luaK_exp2anyreg(fs, ex)
                luaK_codeABC(fs, OpCode.OP_SETUPVAL, e, `var`.u.s.info, 0)
            }
            expkind.VGLOBAL -> {
                val e = luaK_exp2anyreg(fs, ex)
                luaK_codeABx(fs, OpCode.OP_SETGLOBAL, e, `var`.u.s.info)
            }
            expkind.VINDEXED -> {
                val e = luaK_exp2RK(fs, ex)
                luaK_codeABC(fs, OpCode.OP_SETTABLE, `var`.u.s.info, `var`.u.s.aux, e)
            }
            else -> {
                LuaLimits.lua_assert(0) // invalid var kind to store
            }
        }
        freeexp(fs, ex)
    }

    fun luaK_self(fs: FuncState, e: expdesc, key: expdesc) {
        val func: Int
        luaK_exp2anyreg(fs, e)
        freeexp(fs, e)
        func = fs.freereg
        luaK_reserveregs(fs, 2)
        luaK_codeABC(fs, OpCode.OP_SELF, func, e.u.s.info, luaK_exp2RK(fs, key))
        freeexp(fs, key)
        e.u.s.info = func
        e.k = expkind.VNONRELOC
    }

    private fun invertjump(fs: FuncState, e: expdesc) {
        val pc = getjumpcontrol(fs, e.u.s.info)
        LuaLimits.lua_assert(
            LuaOpCodes.testTMode(LuaOpCodes.GET_OPCODE(pc[0])) != 0 && LuaOpCodes.GET_OPCODE(
                pc[0]
            ) != OpCode.OP_TESTSET && LuaOpCodes.GET_OPCODE(pc[0]) != OpCode.OP_TEST
        )
        LuaOpCodes.SETARG_A(pc, if (LuaOpCodes.GETARG_A(pc[0]) == 0) 1 else 0)
    }

    private fun jumponcond(fs: FuncState, e: expdesc, cond: Int): Int {
        if (e.k == expkind.VRELOCABLE) {
            val ie = getcode(fs, e)
            if (LuaOpCodes.GET_OPCODE(ie) == OpCode.OP_NOT) {
                fs.pc-- // remove previous OpCode.OP_NOT
                return condjump(fs, OpCode.OP_TEST, LuaOpCodes.GETARG_B(ie), 0, if (cond == 0) 1 else 0)
            }
            // else go through
        }
        discharge2anyreg(fs, e)
        freeexp(fs, e)
        return condjump(fs, OpCode.OP_TESTSET, LuaOpCodes.NO_REG, e.u.s.info, cond)
    }

    fun luaK_goiftrue(fs: FuncState, e: expdesc) {
        val pc: Int // pc of last jump
        luaK_dischargevars(fs, e)
        pc = when (e.k) {
            expkind.VK, expkind.VKNUM, expkind.VTRUE -> {
                NO_JUMP // always true; do nothing
            }
            expkind.VFALSE -> {
                luaK_jump(fs) // always jump
            }
            expkind.VJMP -> {
                invertjump(fs, e)
                e.u.s.info
            }
            else -> {
                jumponcond(fs, e, 0)
            }
        }
        val f_ref = IntArray(1)
        f_ref[0] = e.f
        luaK_concat(fs, f_ref, pc) // insert last jump in `f' list  - ref
        e.f = f_ref[0]
        luaK_patchtohere(fs, e.t)
        e.t = NO_JUMP
    }

    private fun luaK_goiffalse(fs: FuncState, e: expdesc) {
        val pc: Int // pc of last jump
        luaK_dischargevars(fs, e)
        pc = when (e.k) {
            expkind.VNIL, expkind.VFALSE -> {
                NO_JUMP // always false; do nothing
            }
            expkind.VTRUE -> {
                luaK_jump(fs) // always jump
            }
            expkind.VJMP -> {
                e.u.s.info
            }
            else -> {
                jumponcond(fs, e, 1)
            }
        }
        val t_ref = IntArray(1)
        t_ref[0] = e.t
        luaK_concat(fs, t_ref, pc) // insert last jump in `t' list  - ref
        e.t = t_ref[0]
        luaK_patchtohere(fs, e.f)
        e.f = NO_JUMP
    }

    private fun codenot(fs: FuncState, e: expdesc) {
        luaK_dischargevars(fs, e)
        when (e.k) {
            expkind.VNIL, expkind.VFALSE -> {
                e.k = expkind.VTRUE
            }
            expkind.VK, expkind.VKNUM, expkind.VTRUE -> {
                e.k = expkind.VFALSE
            }
            expkind.VJMP -> {
                invertjump(fs, e)
            }
            expkind.VRELOCABLE, expkind.VNONRELOC -> {
                discharge2anyreg(fs, e)
                freeexp(fs, e)
                e.u.s.info = luaK_codeABC(fs, OpCode.OP_NOT, 0, e.u.s.info, 0)
                e.k = expkind.VRELOCABLE
            }
            else -> {
                LuaLimits.lua_assert(0) // cannot happen
            }
        }
        //
// interchange true and false lists
//
        if (true) {
            val temp = e.f
            e.f = e.t
            e.t = temp
        }
        removevalues(fs, e.f)
        removevalues(fs, e.t)
    }

    fun luaK_indexed(fs: FuncState, t: expdesc, k: expdesc) {
        t.u.s.aux = luaK_exp2RK(fs, k)
        t.k = expkind.VINDEXED
    }

    private fun constfolding(op: OpCode, e1: expdesc, e2: expdesc): Int {
        val v1: Double
        val v2: Double
        val r: Double //lua_Number
        if (isnumeral(e1) == 0 || isnumeral(e2) == 0) {
            return 0
        }
        v1 = e1.u.nval
        v2 = e2.u.nval
        r = when (op) {
            OpCode.OP_ADD -> {
                LuaConf.luai_numadd(v1, v2)
            }
            OpCode.OP_SUB -> {
                LuaConf.luai_numsub(v1, v2)
            }
            OpCode.OP_MUL -> {
                LuaConf.luai_nummul(v1, v2)
            }
            OpCode.OP_DIV -> {
                if (v2 == 0.0) {
                    return 0 // do not attempt to divide by 0
                }
                LuaConf.luai_numdiv(v1, v2)
            }
            OpCode.OP_MOD -> {
                if (v2 == 0.0) {
                    return 0 // do not attempt to divide by 0
                }
                LuaConf.luai_nummod(v1, v2)
            }
            OpCode.OP_POW -> {
                LuaConf.luai_numpow(v1, v2)
            }
            OpCode.OP_UNM -> {
                LuaConf.luai_numunm(v1)
            }
            OpCode.OP_LEN -> {
                return 0 // no constant folding for 'len'
            }
            else -> {
                LuaLimits.lua_assert(0)
                0.0
            }
        }
        if (LuaConf.luai_numisnan(r)) {
            return 0 // do not attempt to produce NaN
        }
        e1.u.nval = r
        return 1
    }

    private fun codearith(fs: FuncState, op: OpCode, e1: expdesc, e2: expdesc) {
        if (constfolding(op, e1, e2) != 0) {
            return
        } else {
            val o2 = if (op != OpCode.OP_UNM && op != OpCode.OP_LEN) luaK_exp2RK(fs, e2) else 0
            val o1 = luaK_exp2RK(fs, e1)
            if (o1 > o2) {
                freeexp(fs, e1)
                freeexp(fs, e2)
            } else {
                freeexp(fs, e2)
                freeexp(fs, e1)
            }
            e1.u.s.info = luaK_codeABC(fs, op, 0, o1, o2)
            e1.k = expkind.VRELOCABLE
        }
    }

    private fun codecomp(fs: FuncState, op: OpCode, cond: Int, e1: expdesc, e2: expdesc) {
        var cond = cond
        var o1 = luaK_exp2RK(fs, e1)
        var o2 = luaK_exp2RK(fs, e2)
        freeexp(fs, e2)
        freeexp(fs, e1)
        if (cond == 0 && op != OpCode.OP_EQ) {
            val temp: Int // exchange args to replace by `<' or `<='
            temp = o1
            o1 = o2
            o2 = temp // o1 <==> o2
            cond = 1
        }
        e1.u.s.info = condjump(fs, op, cond, o1, o2)
        e1.k = expkind.VJMP
    }

    fun luaK_prefix(fs: FuncState, op: UnOpr?, e: expdesc) {
        val e2 = expdesc()
        e2.f = NO_JUMP
        e2.t = e2.f
        e2.k = expkind.VKNUM
        e2.u.nval = 0.0
        when (op) {
            UnOpr.OPR_MINUS -> {
                if (isnumeral(e) == 0) {
                    luaK_exp2anyreg(fs, e) // cannot operate on non-numeric constants
                }
                codearith(fs, OpCode.OP_UNM, e, e2)
            }
            UnOpr.OPR_NOT -> {
                codenot(fs, e)
            }
            UnOpr.OPR_LEN -> {
                luaK_exp2anyreg(fs, e) // cannot operate on constants
                codearith(fs, OpCode.OP_LEN, e, e2)
            }
            else -> {
                LuaLimits.lua_assert(0)
            }
        }
    }

    fun luaK_infix(fs: FuncState, op: BinOpr?, v: expdesc) {
        when (op) {
            BinOpr.OPR_AND -> {
                luaK_goiftrue(fs, v)
            }
            BinOpr.OPR_OR -> {
                luaK_goiffalse(fs, v)
            }
            BinOpr.OPR_CONCAT -> {
                luaK_exp2nextreg(fs, v) // operand must be on the `stack'
            }
            BinOpr.OPR_ADD, BinOpr.OPR_SUB, BinOpr.OPR_MUL, BinOpr.OPR_DIV, BinOpr.OPR_MOD, BinOpr.OPR_POW -> {
                if (isnumeral(v) == 0) {
                    luaK_exp2RK(fs, v)
                }
            }
            else -> {
                luaK_exp2RK(fs, v)
            }
        }
    }

    fun luaK_posfix(fs: FuncState, op: BinOpr?, e1: expdesc, e2: expdesc) {
        when (op) {
            BinOpr.OPR_AND -> {
                LuaLimits.lua_assert(e1.t == NO_JUMP) // list must be closed
                luaK_dischargevars(fs, e2)
                val f_ref = IntArray(1)
                f_ref[0] = e2.f
                luaK_concat(fs, f_ref, e1.f) //ref
                e2.f = f_ref[0]
                e1.Copy(e2)
            }
            BinOpr.OPR_OR -> {
                LuaLimits.lua_assert(e1.f == NO_JUMP) // list must be closed
                luaK_dischargevars(fs, e2)
                val t_ref = IntArray(1)
                t_ref[0] = e2.t
                luaK_concat(fs, t_ref, e1.t) //ref
                e2.t = t_ref[0]
                e1.Copy(e2)
            }
            BinOpr.OPR_CONCAT -> {
                luaK_exp2val(fs, e2)
                if (e2.k == expkind.VRELOCABLE && LuaOpCodes.GET_OPCODE(getcode(fs, e2)) == OpCode.OP_CONCAT) {
                    LuaLimits.lua_assert(e1.u.s.info == LuaOpCodes.GETARG_B(getcode(fs, e2)) - 1)
                    freeexp(fs, e1)
                    LuaOpCodes.SETARG_B(getcode(fs, e2), e1.u.s.info)
                    e1.k = expkind.VRELOCABLE
                    e1.u.s.info = e2.u.s.info
                } else {
                    luaK_exp2nextreg(fs, e2) // operand must be on the 'stack'
                    codearith(fs, OpCode.OP_CONCAT, e1, e2)
                }
            }
            BinOpr.OPR_ADD -> {
                codearith(fs, OpCode.OP_ADD, e1, e2)
            }
            BinOpr.OPR_SUB -> {
                codearith(fs, OpCode.OP_SUB, e1, e2)
            }
            BinOpr.OPR_MUL -> {
                codearith(fs, OpCode.OP_MUL, e1, e2)
            }
            BinOpr.OPR_DIV -> {
                codearith(fs, OpCode.OP_DIV, e1, e2)
            }
            BinOpr.OPR_MOD -> {
                codearith(fs, OpCode.OP_MOD, e1, e2)
            }
            BinOpr.OPR_POW -> {
                codearith(fs, OpCode.OP_POW, e1, e2)
            }
            BinOpr.OPR_EQ -> {
                codecomp(fs, OpCode.OP_EQ, 1, e1, e2)
            }
            BinOpr.OPR_NE -> {
                codecomp(fs, OpCode.OP_EQ, 0, e1, e2)
            }
            BinOpr.OPR_LT -> {
                codecomp(fs, OpCode.OP_LT, 1, e1, e2)
            }
            BinOpr.OPR_LE -> {
                codecomp(fs, OpCode.OP_LE, 1, e1, e2)
            }
            BinOpr.OPR_GT -> {
                codecomp(fs, OpCode.OP_LT, 0, e1, e2)
            }
            BinOpr.OPR_GE -> {
                codecomp(fs, OpCode.OP_LE, 0, e1, e2)
            }
            else -> {
                LuaLimits.lua_assert(0)
            }
        }
    }

    fun luaK_fixline(fs: FuncState, line: Int) {
        fs.f!!.lineinfo!![fs.pc - 1] = line
    }

    private fun luaK_code(fs: FuncState, i: Int, line: Int): Int {
        val f = fs.f
        dischargejpc(fs) // `pc' will change
        // put new instruction in code array
        val code_ref = arrayOfNulls<LongArray>(1)
        code_ref[0] = f!!.code
        val sizecode_ref = IntArray(1)
        sizecode_ref[0] = f!!.sizecode
        LuaMem.luaM_growvector_long(
            fs.L,
            code_ref,
            fs.pc,
            sizecode_ref,
            LuaLimits.MAX_INT,
            CharPtr.Companion.toCharPtr("code size overflow"),
            ClassType(ClassType.Companion.TYPE_LONG)
        ) //ref - ref
        f!!.sizecode = sizecode_ref[0]
        f!!.code = code_ref[0]!!
        f!!.code!![fs.pc] = i.toLong() //uint
        // save corresponding line information
        val lineinfo_ref = arrayOfNulls<IntArray>(1)
        lineinfo_ref[0] = f!!.lineinfo
        val sizelineinfo_ref = IntArray(1)
        sizelineinfo_ref[0] = f!!.sizelineinfo
        LuaMem.luaM_growvector_int(
            fs.L,
            lineinfo_ref,
            fs.pc,
            sizelineinfo_ref,
            LuaLimits.MAX_INT,
            CharPtr.Companion.toCharPtr("code size overflow"),
            ClassType(ClassType.Companion.TYPE_INT)
        ) //ref - ref
        f.sizelineinfo = sizelineinfo_ref[0]
        f.lineinfo = lineinfo_ref[0]!!
        f.lineinfo!![fs.pc] = line
        return fs.pc++
    }

    fun luaK_codeABC(fs: FuncState, o: OpCode?, a: Int, b: Int, c: Int): Int {
        LuaLimits.lua_assert(LuaOpCodes.getOpMode(o!!) == OpMode.iABC)
        LuaLimits.lua_assert(LuaOpCodes.getBMode(o!!) != OpArgMask.OpArgN || b == 0)
        LuaLimits.lua_assert(LuaOpCodes.getCMode(o!!) != OpArgMask.OpArgN || c == 0)
        return luaK_code(fs, LuaOpCodes.CREATE_ABC(o!!, a, b, c), fs.ls!!.lastline)
    }

    fun luaK_codeABx(fs: FuncState, o: OpCode?, a: Int, bc: Int): Int {
        LuaLimits.lua_assert(LuaOpCodes.getOpMode(o!!) == OpMode.iABx || LuaOpCodes.getOpMode(o!!) == OpMode.iAsBx)
        LuaLimits.lua_assert(LuaOpCodes.getCMode(o!!) == OpArgMask.OpArgN)
        return luaK_code(fs, LuaOpCodes.CREATE_ABx(o!!, a, bc), fs.ls!!.lastline)
    }

    fun luaK_setlist(fs: FuncState, base_: Int, nelems: Int, tostore: Int) {
        val c = (nelems - 1) / LuaOpCodes.LFIELDS_PER_FLUSH + 1
        val b = if (tostore == Lua.LUA_MULTRET) 0 else tostore
        LuaLimits.lua_assert(tostore != 0)
        if (c <= LuaOpCodes.MAXARG_C) {
            luaK_codeABC(fs, OpCode.OP_SETLIST, base_, b, c)
        } else {
            luaK_codeABC(fs, OpCode.OP_SETLIST, base_, b, 0)
            luaK_code(fs, c, fs.ls!!.lastline)
        }
        fs.freereg = base_ + 1 // free registers with list values
    }

    class InstructionPtr {
        var   /*UInt32[]*/ /*Instruction[]*/codes: LongArray?
        var pc: Int

        constructor() {
            codes = null
            pc = -1
        }

        constructor(   /*UInt32[]*/ /*Instruction[]*/codes: LongArray?, pc: Int) {
            this.codes = codes
            this.pc = pc
        }

        //UInt32/*Instruction*/ this[int index]
        operator fun  /*UInt32*/ /*Instruction*/get(index: Int): Long {
            return codes!![pc + index]
        }

        operator fun set(index: Int,    /*UInt32*/ /*Instruction*/`val`: Long) {
            codes!![pc + index] = `val`
        }

        companion object {
            fun Assign(ptr: InstructionPtr?): InstructionPtr? {
                return if (ptr == null) {
                    null
                } else InstructionPtr(ptr.codes, ptr.pc)
            }

            fun inc( /*ref*/
                ptr: Array<InstructionPtr?>
            ): InstructionPtr {
                val result = InstructionPtr(ptr[0]!!.codes, ptr[0]!!.pc)
                ptr[0]!!.pc++
                return result
            }

            fun dec( /*ref*/
                ptr: Array<InstructionPtr?>
            ): InstructionPtr {
                val result = InstructionPtr(ptr[0]!!.codes, ptr[0]!!.pc)
                ptr[0]!!.pc--
                return result
            }

            //operator <
            fun lessThan(p1: InstructionPtr, p2: InstructionPtr): Boolean {
                ClassType.Companion.Assert(p1.codes == p2.codes)
                return p1.pc < p2.pc
            }

            //operator >
            fun greaterThan(p1: InstructionPtr, p2: InstructionPtr): Boolean {
                ClassType.Companion.Assert(p1.codes == p2.codes)
                return p1.pc > p2.pc
            }

            //operator <=
            fun lessEqual(p1: InstructionPtr?, p2: InstructionPtr?): Boolean {
                ClassType.Companion.Assert(p1!!.codes == p2!!.codes)
                return p1.pc < p2.pc
            }

            //operator >=
            fun greaterEqual(p1: InstructionPtr, p2: InstructionPtr): Boolean {
                ClassType.Companion.Assert(p1.codes == p2.codes)
                return p1.pc > p2.pc
            }
        }
    }

    /*
	 ** grep "ORDER OPR" if you change these enums
	 */
    enum class BinOpr {
        OPR_ADD, OPR_SUB, OPR_MUL, OPR_DIV, OPR_MOD, OPR_POW, OPR_CONCAT, OPR_NE, OPR_EQ, OPR_LT, OPR_LE, OPR_GT, OPR_GE, OPR_AND, OPR_OR, OPR_NOBINOPR;

        fun getValue(): Int {
            return ordinal
        }

        companion object {
            fun forValue(value: Int): BinOpr {
                return values()[value]
            }
        }
    }

    enum class UnOpr {
        OPR_MINUS, OPR_NOT, OPR_LEN, OPR_NOUNOPR;

        fun getValue(): Int {
            return ordinal
        }

        companion object {
            fun forValue(value: Int): UnOpr {
                return values()[value]
            }
        }
    }
}