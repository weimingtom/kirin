package kirin

import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.LuaObject.TValue
import kirin.LuaObject.Proto
import kirin.LuaObject.Table
import kirin.LuaCode.InstructionPtr
import kirin.LuaOpCodes.OpCode
import kirin.LuaZIO.ZIO
import kirin.LuaZIO.Mbuffer
import kirin.LuaObject.TString
import java.lang.RuntimeException
import kirin.LuaLex.RESERVED
import kirin.LuaLex.LexState
import kirin.LuaObject.LocVar
import kirin.LuaCode.UnOpr
import kirin.LuaCode.BinOpr
import java.util.HashMap
import kotlin.jvm.Synchronized
import kotlin.experimental.and
import kotlin.experimental.or

//
// ** $Id: lparser.c,v 2.42.1.3 2007/12/28 15:32:23 roberto Exp $
// ** Lua Parser
// ** See Copyright Notice in lua.h
//
//using lu_byte = System.Byte;
//using lua_Number = System.Double;
object LuaParser {
    fun expkindToInt(exp: expkind?): Int {
        when (exp) {
            expkind.VVOID -> return 0
            expkind.VNIL -> return 1
            expkind.VTRUE -> return 2
            expkind.VFALSE -> return 3
            expkind.VK -> return 4
            expkind.VKNUM -> return 5
            expkind.VLOCAL -> return 6
            expkind.VUPVAL -> return 7
            expkind.VGLOBAL -> return 8
            expkind.VINDEXED -> return 9
            expkind.VJMP -> return 10
            expkind.VRELOCABLE -> return 11
            expkind.VNONRELOC -> return 12
            expkind.VCALL -> return 13
            expkind.VVARARG -> return 14
        }
        throw RuntimeException("expkindToInt error")
    }

    fun hasmultret(k: expkind?): Int {
        return if (k == expkind.VCALL || k == expkind.VVARARG) 1 else 0
    }

    fun getlocvar(fs: FuncState, i: Int): LocVar {
        return fs.f!!.locvars!!.get(fs.actvar[i])!!
    }

    fun luaY_checklimit(fs: FuncState, v: Int, l: Int, m: CharPtr) {
        if (v > l) {
            errorlimit(fs, l, m)
        }
    }

    private fun anchor_token(ls: LexState) {
        if (ls.t.token == RESERVED.TK_NAME as Int || ls.t.token == RESERVED.TK_STRING as Int) {
            val ts: TString? = ls.t.seminfo.ts
            LuaLex.luaX_newstring(ls, LuaObject.getstr(ts), ts!!.getTsv().len)
        }
    }

    private fun error_expected(ls: LexState, token: Int) {
        LuaLex.luaX_syntaxerror(
            ls,
            LuaObject.luaO_pushfstring(
                ls.L,
                CharPtr.Companion.toCharPtr(LuaConf.getLUA_QS().toString() + " expected"),
                LuaLex.luaX_token2str(ls, token)
            )
        )
    }

    private fun errorlimit(fs: FuncState, limit: Int, what: CharPtr) {
        val msg: CharPtr? = if (fs.f!!.linedefined == 0) LuaObject.luaO_pushfstring(
            fs.L,
            CharPtr.Companion.toCharPtr("main function has more than %d %s"),
            limit,
            what
        ) else LuaObject.luaO_pushfstring(
            fs.L,
            CharPtr.Companion.toCharPtr("function at line %d has more than %d %s"),
            fs.f!!.linedefined,
            limit,
            what
        )
        LuaLex.luaX_lexerror(fs.ls!!, msg, 0)
    }

    private fun testnext(ls: LexState, c: Int): Int {
        return if (ls.t.token == c) {
            LuaLex.luaX_next(ls)
            1
        } else {
            0
        }
    }

    private fun check(ls: LexState, c: Int) {
        if (ls.t.token != c) {
            error_expected(ls, c)
        }
    }

    private fun checknext(ls: LexState, c: Int) {
        check(ls, c)
        LuaLex.luaX_next(ls)
    }

    fun check_condition(ls: LexState?, c: Boolean, msg: CharPtr?) {
        if (!c) {
            LuaLex.luaX_syntaxerror(ls!!, msg)
        }
    }

    private fun check_match(ls: LexState, what: Int, who: Int, where: Int) {
        if (testnext(ls, what) == 0) {
            if (where == ls.linenumber) {
                error_expected(ls, what)
            } else {
                LuaLex.luaX_syntaxerror(
                    ls,
                    LuaObject.luaO_pushfstring(
                        ls.L,
                        CharPtr.Companion.toCharPtr(LuaConf.getLUA_QS().toString() + " expected (to close " + LuaConf.getLUA_QS() + " at line %d)"),
                        LuaLex.luaX_token2str(ls, what),
                        LuaLex.luaX_token2str(ls, who),
                        where
                    )
                )
            }
        }
    }

    private fun str_checkname(ls: LexState): TString? {
        val ts: TString?
        check(ls, RESERVED.TK_NAME as Int)
        ts = ls.t.seminfo.ts
        LuaLex.luaX_next(ls)
        return ts
    }

    private fun init_exp(e: expdesc, k: expkind, i: Int) {
        e.t = LuaCode.NO_JUMP
        e.f = e.t
        e.k = k
        e.u.s.info = i
    }

    private fun codestring(ls: LexState, e: expdesc, s: TString) {
        init_exp(e, expkind.VK, LuaCode.luaK_stringK(ls.fs!!, s))
    }

    private fun checkname(ls: LexState, e: expdesc) {
        codestring(ls, e, this!!.str_checkname(ls)!!)
    }

    private fun registerlocalvar(ls: LexState, varname: TString): Int {
        val fs: FuncState? = ls.fs
        val f: Proto? = fs!!.f
        var oldsize: Int = f!!.sizelocvars
        val locvars_ref: Array<Array<LocVar?>?> = arrayOfNulls<Array<LocVar?>>(1)
        locvars_ref[0] = f.locvars
        val sizelocvars_ref = IntArray(1)
        sizelocvars_ref[0] = f.sizelocvars
        LuaMem.luaM_growvector_LocVar(
            ls.L,
            locvars_ref,
            fs.nlocvars.toInt(),
            sizelocvars_ref,
            CLib.SHRT_MAX,
            CharPtr.Companion.toCharPtr("too many local variables"),
            ClassType(ClassType.Companion.TYPE_LOCVAR)
        ) //ref - ref
        f.sizelocvars = sizelocvars_ref[0]
        f.locvars = locvars_ref[0]
        while (oldsize < f.sizelocvars) {
            f.locvars!!.get(oldsize++)!!.varname = null
        }
        f.locvars!!.get(fs.nlocvars.toInt())!!.varname = varname
        LuaGC.luaC_objbarrier(ls.L, f, varname)
        return fs.nlocvars++.toInt()
    }

    fun new_localvarliteral(ls: LexState, v: CharPtr, n: Int) {
        new_localvar(
            ls,
            LuaLex.luaX_newstring(ls, CharPtr.Companion.toCharPtr("" + v), v.chars!!.size - 1),
            n
        ) //(uint)
    }

    private fun new_localvar(ls: LexState, name: TString, n: Int) {
        val fs: FuncState? = ls.fs
        luaY_checklimit(
            fs!!,
            fs.nactvar + n + 1,
            LuaConf.LUAI_MAXVARS,
            CharPtr.Companion.toCharPtr("local variables")
        )
        fs.actvar[fs.nactvar + n] = registerlocalvar(ls, name) //ushort
    }

    private fun adjustlocalvars(ls: LexState, nvars: Int) {
        var nvars = nvars
        val fs: FuncState? = ls.fs
        fs!!.nactvar = LuaLimits.cast_byte(fs.nactvar + nvars)
        while (nvars != 0) {
            getlocvar(fs!!, fs.nactvar - nvars).startpc = fs.pc
            nvars--
        }
    }

    private fun removevars(ls: LexState?, tolevel: Int) {
        val fs: FuncState? = ls!!.fs
        while (fs!!.nactvar > tolevel) {
            getlocvar(fs!!, (--fs!!.nactvar).toInt()).endpc = fs.pc
        }
    }

    private fun indexupvalue(fs: FuncState, name: TString, v: expdesc): Int {
        var i: Int
        val f: Proto? = fs.f
        var oldsize: Int = f!!.sizeupvalues
        i = 0
        while (i < f.nups) {
            if (fs.upvalues[i]!!.k.toInt() == expkindToInt(v.k) && fs.upvalues[i]!!.info.toInt() == v.u.s.info) {
                LuaLimits.lua_assert(f.upvalues!!.get(i) === name)
                return i
            }
            i++
        }
        // new one
        luaY_checklimit(fs, f.nups + 1, LuaConf.LUAI_MAXUPVALUES, CharPtr.Companion.toCharPtr("upvalues"))
        val upvalues_ref: Array<Array<TString?>?> = arrayOfNulls<Array<TString?>>(1)
        upvalues_ref[0] = f.upvalues
        val sizeupvalues_ref = IntArray(1)
        sizeupvalues_ref[0] = f.sizeupvalues
        LuaMem.luaM_growvector_TString(
            fs.L,
            upvalues_ref,
            f.nups.toInt(),
            sizeupvalues_ref,
            LuaLimits.MAX_INT,
            CharPtr.Companion.toCharPtr(""),
            ClassType(ClassType.Companion.TYPE_TSTRING)
        ) //ref - ref
        f.sizeupvalues = sizeupvalues_ref[0]
        f.upvalues = upvalues_ref[0]
        while (oldsize < f.sizeupvalues) {
            f.upvalues!![oldsize++] = null
        }
        f.upvalues!![f.nups.toInt()] = name
        LuaGC.luaC_objbarrier(fs.L, f, name)
        LuaLimits.lua_assert(v.k == expkind.VLOCAL || v.k == expkind.VUPVAL)
        fs.upvalues[f.nups.toInt()]!!.k = LuaLimits.cast_byte(expkindToInt(v.k))
        fs.upvalues[f.nups.toInt()]!!.info = LuaLimits.cast_byte(v.u.s.info)
        return f.nups++.toInt()
    }

    private fun searchvar(fs: FuncState, n: TString): Int {
        var i: Int
        i = fs.nactvar - 1
        while (i >= 0) {
            if (n === getlocvar(fs, i).varname) {
                return i
            }
            i--
        }
        return -1 // not found
    }

    private fun markupval(fs: FuncState, level: Int) {
        var bl = fs.bl
        while (bl != null && bl.nactvar > level) {
            bl = bl.previous
        }
        if (bl != null) {
            bl.upval = 1
        }
    }

    private fun singlevaraux(fs: FuncState?, n: TString, `var`: expdesc, base_: Int): expkind {
        return if (fs == null) { // no more levels?
            init_exp(`var`, expkind.VGLOBAL, LuaOpCodes.NO_REG) // default is global variable
            expkind.VGLOBAL
        } else {
            val v = searchvar(fs, n) // look up at current level
            if (v >= 0) {
                init_exp(`var`, expkind.VLOCAL, v)
                if (base_ == 0) {
                    markupval(fs, v) // local will be used as an upval
                }
                expkind.VLOCAL
            } else { // not found at current level; try upper one
                if (singlevaraux(fs.prev, n, `var`, 0) == expkind.VGLOBAL) {
                    return expkind.VGLOBAL
                }
                `var`.u.s.info = indexupvalue(fs, n, `var`) // else was LOCAL or UPVAL
                `var`.k = expkind.VUPVAL // upvalue in this level
                expkind.VUPVAL
            }
        }
    }

    private fun singlevar(ls: LexState, `var`: expdesc) {
        val varname: TString = str_checkname(ls)!!
        val fs: FuncState = ls.fs!!
        if (singlevaraux(fs, varname, `var`, 1) == expkind.VGLOBAL) {
            `var`.u.s.info = LuaCode.luaK_stringK(fs, varname) // info points to global name
        }
    }

    private fun adjust_assign(ls: LexState, nvars: Int, nexps: Int, e: expdesc) {
        val fs: FuncState = ls.fs!!
        var extra = nvars - nexps
        if (hasmultret(e.k) != 0) {
            extra++ // includes call itself
            if (extra < 0) {
                extra = 0
            }
            LuaCode.luaK_setreturns(fs, e, extra) // last exp. provides the difference
            if (extra > 1) {
                LuaCode.luaK_reserveregs(fs, extra - 1)
            }
        } else {
            if (e.k != expkind.VVOID) {
                LuaCode.luaK_exp2nextreg(fs, e) // close last expression
            }
            if (extra > 0) {
                val reg = fs.freereg
                LuaCode.luaK_reserveregs(fs, extra)
                LuaCode.luaK_nil(fs, reg, extra)
            }
        }
    }

    private fun enterlevel(ls: LexState) {
        if (++ls.L!!.nCcalls > LuaConf.LUAI_MAXCCALLS) {
            LuaLex.luaX_lexerror(ls, CharPtr.Companion.toCharPtr("chunk has too many syntax levels"), 0)
        }
    }

    private fun leavelevel(ls: LexState) {
        ls.L!!.nCcalls--
    }

    private fun enterblock(fs: FuncState, bl: BlockCnt, isbreakable: Byte) { //lu_byte
        bl.breaklist = LuaCode.NO_JUMP
        bl.isbreakable = isbreakable
        bl.nactvar = fs.nactvar
        bl.upval = 0
        bl.previous = fs.bl
        fs.bl = bl
        LuaLimits.lua_assert(fs.freereg == fs.nactvar.toInt())
    }

    private fun leaveblock(fs: FuncState) {
        val bl = fs.bl
        fs.bl = bl!!.previous
        removevars(fs.ls, bl.nactvar.toInt())
        if (bl.upval.toInt() != 0) {
            LuaCode.luaK_codeABC(fs, OpCode.OP_CLOSE, bl.nactvar.toInt(), 0, 0)
        }
        // a block either controls scope or breaks (never both)
        LuaLimits.lua_assert(bl.isbreakable.toInt() == 0 || bl.upval.toInt() == 0)
        LuaLimits.lua_assert(bl.nactvar == fs.nactvar)
        fs.freereg = fs.nactvar.toInt() // free registers
        LuaCode.luaK_patchtohere(fs, bl.breaklist)
    }

    private fun pushclosure(ls: LexState, func: FuncState, v: expdesc) {
        val fs: FuncState = ls.fs!!
        val f: Proto? = fs.f
        var oldsize: Int = f!!.sizep
        var i: Int
        val p_ref: Array<Array<Proto?>?> = arrayOfNulls<Array<Proto?>>(1)
        p_ref[0] = f!!.p
        val sizep_ref = IntArray(1)
        sizep_ref[0] = f!!.sizep
        LuaMem.luaM_growvector_Proto(
            ls.L,
            p_ref,
            fs.np,
            sizep_ref,
            LuaOpCodes.MAXARG_Bx,
            CharPtr.Companion.toCharPtr("constant table overflow"),
            ClassType(ClassType.Companion.TYPE_PROTO)
        ) //ref - ref
        f.sizep = sizep_ref[0]
        f.p = p_ref[0]
        while (oldsize < f.sizep) {
            f.p!![oldsize++] = null
        }
        f.p!![fs.np++] = func.f
        LuaGC.luaC_objbarrier(ls.L, f, func.f)
        init_exp(v, expkind.VRELOCABLE, LuaCode.luaK_codeABx(fs, OpCode.OP_CLOSURE, 0, fs.np - 1))
        i = 0
        while (i < func.f!!.nups) {
            val o: OpCode =
                if (func.upvalues[i]!!.k.toInt() == expkind.VLOCAL.getValue()) OpCode.OP_MOVE else OpCode.OP_GETUPVAL
            LuaCode.luaK_codeABC(fs, o, 0, func.upvalues[i]!!.info.toInt(), 0)
            i++
        }
    }

    private fun open_func(ls: LexState, fs: FuncState) {
        val L: lua_State = ls.L!!
        val f: Proto = LuaFunc.luaF_newproto(L)!!
        fs.f = f
        fs.prev = ls.fs // linked list of funcstates
        fs.ls = ls
        fs.L = L
        ls.fs = fs
        fs.pc = 0
        fs.lasttarget = -1
        fs.jpc = LuaCode.NO_JUMP
        fs.freereg = 0
        fs.nk = 0
        fs.np = 0
        fs.nlocvars = 0
        fs.nactvar = 0
        fs.bl = null
        f.source = ls.source
        f.maxstacksize = 2 // registers 0/1 are always valid
        fs.h = LuaTable.luaH_new(L, 0, 0)
        // anchor table of constants and prototype (to avoid being collected)
        LuaObject.sethvalue2s(L, L.top!!, fs.h)
        LuaDo.incr_top(L)
        LuaObject.setptvalue2s(L, L.top!!, f)
        LuaDo.incr_top(L)
    }

    private var lastfunc: Proto? = null
    private fun close_func(ls: LexState) {
        val L: lua_State = ls.L!!
        val fs: FuncState = ls.fs!!
        val f: Proto? = fs.f
        lastfunc = f
        removevars(ls, 0)
        LuaCode.luaK_ret(fs, 0, 0) // final return
        val code_ref = arrayOfNulls<LongArray>(1)
        code_ref[0] = f!!.code
        LuaMem.luaM_reallocvector_long(
            L,
            code_ref,
            f.sizecode,
            fs.pc,
            ClassType(ClassType.Companion.TYPE_LONG)
        ) //, typeof(Instruction) - ref
        f.code = code_ref[0]
        f.sizecode = fs.pc
        val lineinfo_ref = arrayOfNulls<IntArray>(1)
        lineinfo_ref[0] = f.lineinfo
        LuaMem.luaM_reallocvector_int(
            L,
            lineinfo_ref,
            f.sizelineinfo,
            fs.pc,
            ClassType(ClassType.Companion.TYPE_INT)
        ) //, typeof(int) - ref
        f.lineinfo = lineinfo_ref[0]
        f.sizelineinfo = fs.pc
        val k_ref: Array<Array<TValue?>?> = arrayOfNulls<Array<TValue?>>(1)
        k_ref[0] = f.k
        LuaMem.luaM_reallocvector_TValue(
            L,
            k_ref,
            f.sizek,
            fs.nk,
            ClassType(ClassType.Companion.TYPE_TVALUE)
        ) //, TValue - ref
        f.k = k_ref[0]
        f.sizek = fs.nk
        val p_ref: Array<Array<Proto?>?> = arrayOfNulls<Array<Proto?>>(1)
        p_ref[0] = f.p
        LuaMem.luaM_reallocvector_Proto(
            L,
            p_ref,
            f.sizep,
            fs.np,
            ClassType(ClassType.Companion.TYPE_PROTO)
        ) //, Proto - ref
        f.p = p_ref[0]
        f.sizep = fs.np
        for (i in f.p!!.indices) {
            f.p!!.get(i)!!.protos = f.p
            f.p!!.get(i)!!.index = i
        }
        val locvars_ref: Array<Array<LocVar?>?> = arrayOfNulls<Array<LocVar?>>(1)
        locvars_ref[0] = f.locvars
        LuaMem.luaM_reallocvector_LocVar(
            L,
            locvars_ref,
            f.sizelocvars,
            fs.nlocvars.toInt(),
            ClassType(ClassType.Companion.TYPE_LOCVAR)
        ) //, LocVar - ref
        f.locvars = locvars_ref[0]
        f.sizelocvars = fs.nlocvars.toInt()
        val upvalues_ref: Array<Array<TString?>?> = arrayOfNulls<Array<TString?>>(1)
        upvalues_ref[0] = f.upvalues
        LuaMem.luaM_reallocvector_TString(
            L,
            upvalues_ref,
            f.sizeupvalues,
            f.nups.toInt(),
            ClassType(ClassType.Companion.TYPE_TSTRING)
        ) //, TString - ref
        f.upvalues = upvalues_ref[0]
        f.sizeupvalues = f.nups.toInt()
        LuaLimits.lua_assert(LuaDebug.luaG_checkcode(f))
        LuaLimits.lua_assert(fs.bl == null)
        ls.fs = fs.prev
        L.top = TValue.Companion.minus(L.top, 2) // remove table and prototype from the stack
        // last token read was anchored in defunct function; must reanchor it
        if (fs != null) {
            anchor_token(ls)
        }
    }

    fun luaY_parser(L: lua_State?, z: ZIO?, buff: Mbuffer?, name: CharPtr?): Proto? {
        val lexstate = LexState()
        val funcstate = FuncState()
        lexstate.buff = buff
        LuaLex.luaX_setinput(L, lexstate, z, LuaString.luaS_new(L, name))
        open_func(lexstate, funcstate)
        funcstate.f!!.is_vararg = LuaObject.VARARG_ISVARARG.toByte() // main func. is always vararg
        LuaLex.luaX_next(lexstate) // read first token
        chunk(lexstate)
        check(lexstate, RESERVED.TK_EOS as Int)
        close_func(lexstate)
        LuaLimits.lua_assert(funcstate.prev == null)
        LuaLimits.lua_assert(funcstate.f!!.nups.toInt() == 0)
        LuaLimits.lua_assert(lexstate.fs == null)
        return funcstate.f
    }

    //============================================================
// GRAMMAR RULES
//============================================================
    private fun field(ls: LexState, v: expdesc) { // field . ['.' | ':'] NAME
        val fs: FuncState = ls.fs!!
        val key = expdesc()
        LuaCode.luaK_exp2anyreg(fs, v)
        LuaLex.luaX_next(ls) // skip the dot or colon
        checkname(ls, key)
        LuaCode.luaK_indexed(fs, v, key)
    }

    private fun yindex(ls: LexState, v: expdesc) { // index . '[' expr ']'
        LuaLex.luaX_next(ls) // skip the '['
        expr(ls, v)
        LuaCode.luaK_exp2val(ls.fs!!, v)
        checknext(ls, ']'.toInt())
    }

    private fun recfield(ls: LexState, cc: ConsControl) { // recfield . (NAME | `['exp1`]') = exp1
        val fs: FuncState = ls.fs!!
        val reg: Int = ls.fs!!.freereg
        val key = expdesc()
        val `val` = expdesc()
        val rkkey: Int
        if (ls.t.token == RESERVED.TK_NAME as Int) {
            luaY_checklimit(
                fs,
                cc.nh,
                LuaLimits.MAX_INT,
                CharPtr.Companion.toCharPtr("items in a constructor")
            )
            checkname(ls, key)
        } else { // ls.t.token == '['
            yindex(ls, key)
        }
        cc.nh++
        checknext(ls, '='.toInt())
        rkkey = LuaCode.luaK_exp2RK(fs, key)
        expr(ls, `val`)
        LuaCode.luaK_codeABC(fs, OpCode.OP_SETTABLE, cc.t!!.u.s.info, rkkey, LuaCode.luaK_exp2RK(fs, `val`))
        fs.freereg = reg // free registers
    }

    private fun closelistfield(fs: FuncState, cc: ConsControl) {
        if (cc.v.k == expkind.VVOID) {
            return  // there is no list item
        }
        LuaCode.luaK_exp2nextreg(fs, cc.v)
        cc.v.k = expkind.VVOID
        if (cc.tostore == LuaOpCodes.LFIELDS_PER_FLUSH) {
            LuaCode.luaK_setlist(fs, cc.t!!.u.s.info, cc.na, cc.tostore) // flush
            cc.tostore = 0 // no more items pending
        }
    }

    private fun lastlistfield(fs: FuncState, cc: ConsControl) {
        if (cc.tostore == 0) {
            return
        }
        if (hasmultret(cc.v.k) != 0) {
            LuaCode.luaK_setmultret(fs, cc.v)
            LuaCode.luaK_setlist(fs, cc.t!!.u.s.info, cc.na, Lua.LUA_MULTRET)
            cc.na-- // do not count last expression (unknown number of elements)
        } else {
            if (cc.v.k != expkind.VVOID) {
                LuaCode.luaK_exp2nextreg(fs, cc.v)
            }
            LuaCode.luaK_setlist(fs, cc.t!!.u.s.info, cc.na, cc.tostore)
        }
    }

    private fun listfield(ls: LexState, cc: ConsControl) {
        expr(ls, cc.v)
        luaY_checklimit(
            ls.fs!!,
            cc.na,
            LuaLimits.MAX_INT,
            CharPtr.Companion.toCharPtr("items in a constructor")
        )
        cc.na++
        cc.tostore++
    }

    private fun constructor(ls: LexState, t: expdesc) { // constructor . ??
        val fs: FuncState = ls.fs!!
        val line: Int = ls.linenumber
        val pc: Int = LuaCode.luaK_codeABC(fs, OpCode.OP_NEWTABLE, 0, 0, 0)
        val cc = ConsControl()
        cc.tostore = 0
        cc.nh = cc.tostore
        cc.na = cc.nh
        cc.t = t
        init_exp(t, expkind.VRELOCABLE, pc)
        init_exp(cc.v, expkind.VVOID, 0) // no value (yet)
        LuaCode.luaK_exp2nextreg(ls.fs!!, t) // fix it at stack top (for gc)
        checknext(ls, '{'.toInt())
        do {
            LuaLimits.lua_assert(cc.v.k == expkind.VVOID || cc.tostore > 0)
            if (ls.t.token == '}'.toInt()) {
                break
            }
            closelistfield(fs, cc)
            when (ls.t.token) {
                RESERVED.TK_NAME -> {
                    // may be listfields or recfields
                    LuaLex.luaX_lookahead(ls)
                    if (ls.lookahead.token != '='.toInt()) { // expression?
                        listfield(ls, cc)
                    } else {
                        recfield(ls, cc)
                    }
                }
                '['.toInt() -> {
                    // constructor_item . recfield
                    recfield(ls, cc)
                }
                else -> {
                    // constructor_part . listfield
                    listfield(ls, cc)
                }
            }
        } while (testnext(ls, ','.toInt()) != 0 || testnext(ls, ';'.toInt()) != 0)
        check_match(ls, '}'.toInt(), '{'.toInt(), line)
        lastlistfield(fs, cc)
        LuaOpCodes.SETARG_B(
            InstructionPtr(fs.f!!.code, pc),
            LuaObject.luaO_int2fb(cc.na)
        ) // set initial array size  - uint
        LuaOpCodes.SETARG_C(
            InstructionPtr(fs.f!!.code, pc),
            LuaObject.luaO_int2fb(cc.nh)
        ) // set initial table size  - uint
    }

    // }======================================================================
    private fun parlist(ls: LexState) { // parlist . [ param { `,' param } ]
        val fs: FuncState = ls.fs!!
        val f: Proto? = fs.f
        var nparams = 0
        f!!.is_vararg = 0
        if (ls.t.token != ')'.toInt()) { // is `parlist' not empty?
            do {
                when (ls.t.token) {
                    RESERVED.TK_NAME -> {
                        // param . NAME
                        new_localvar(ls, str_checkname(ls)!!, nparams++)
                    }
                    RESERVED.TK_DOTS -> {
                        // param . `...'
                        LuaLex.luaX_next(ls)
                        ///#if LUA_COMPAT_VARARG
// use `arg' as default name
                        new_localvarliteral(ls, CharPtr.Companion.toCharPtr("arg"), nparams++)
                        f!!.is_vararg = (LuaObject.VARARG_HASARG or LuaObject.VARARG_NEEDSARG).toByte()
                        ///#endif
                        f!!.is_vararg = (f!!.is_vararg or LuaObject.VARARG_ISVARARG.toByte())
                    }
                    else -> {
                        LuaLex.luaX_syntaxerror(
                            ls,
                            CharPtr.Companion.toCharPtr("<name> or " + LuaConf.LUA_QL("...") + " expected")
                        )
                    }
                }
            } while (f!!.is_vararg.toInt() == 0 && testnext(ls, ','.toInt()) != 0)
        }
        adjustlocalvars(ls, nparams)
        f!!.numparams = LuaLimits.cast_byte(fs.nactvar - (f!!.is_vararg and LuaObject.VARARG_HASARG.toByte()))
        LuaCode.luaK_reserveregs(fs, fs.nactvar.toInt()) // reserve register for parameters
    }

    private fun body(
        ls: LexState,
        e: expdesc,
        needself: Int,
        line: Int
    ) { // body .  `(' parlist `)' chunk END
        val new_fs = FuncState()
        open_func(ls, new_fs)
        new_fs.f!!.linedefined = line
        checknext(ls, '('.toInt())
        if (needself != 0) {
            new_localvarliteral(ls, CharPtr.Companion.toCharPtr("self"), 0)
            adjustlocalvars(ls, 1)
        }
        parlist(ls)
        checknext(ls, ')'.toInt())
        chunk(ls)
        new_fs.f!!.lastlinedefined = ls.linenumber
        check_match(ls, RESERVED.TK_END as Int, RESERVED.TK_FUNCTION as Int, line)
        close_func(ls)
        pushclosure(ls, new_fs, e)
    }

    private fun explist1(ls: LexState, v: expdesc): Int { // explist1 . expr { `,' expr }
        var n = 1 // at least one expression
        expr(ls, v)
        while (testnext(ls, ','.toInt()) != 0) {
            LuaCode.luaK_exp2nextreg(ls.fs!!, v)
            expr(ls, v)
            n++
        }
        return n
    }

    private fun funcargs(ls: LexState, f: expdesc) {
        val fs: FuncState = ls.fs!!
        val args = expdesc()
        val base_: Int
        val nparams: Int
        val line: Int = ls.linenumber
        when (ls.t.token) {
            '('.toInt() -> {
                // funcargs . `(' [ explist1 ] `)'
                if (line != ls.lastline) {
                    LuaLex.luaX_syntaxerror(
                        ls,
                        CharPtr.Companion.toCharPtr("ambiguous syntax (function call x new statement)")
                    )
                }
                LuaLex.luaX_next(ls)
                if (ls.t.token == ')'.toInt()) { // arg list is empty?
                    args.k = expkind.VVOID
                } else {
                    explist1(ls, args)
                    LuaCode.luaK_setmultret(fs, args)
                }
                check_match(ls, ')'.toInt(), '('.toInt(), line)
            }
            '{'.toInt() -> {
                // funcargs . constructor
                constructor(ls, args)
            }
            RESERVED.TK_STRING -> {
                // funcargs . STRING
                codestring(ls, args, ls.t.seminfo.ts!!)
                LuaLex.luaX_next(ls) // must use `seminfo' before `next'
            }
            else -> {
                LuaLex.luaX_syntaxerror(ls, CharPtr.Companion.toCharPtr("function arguments expected"))
                return
            }
        }
        LuaLimits.lua_assert(f.k == expkind.VNONRELOC)
        base_ = f.u.s.info // base_ register for call
        nparams = if (hasmultret(args.k) != 0) {
            Lua.LUA_MULTRET // open call
        } else {
            if (args.k != expkind.VVOID) {
                LuaCode.luaK_exp2nextreg(fs, args) // close last argument
            }
            fs.freereg - (base_ + 1)
        }
        init_exp(f, expkind.VCALL, LuaCode.luaK_codeABC(fs, OpCode.OP_CALL, base_, nparams + 1, 2))
        LuaCode.luaK_fixline(fs, line)
        fs.freereg = base_ + 1 // call remove function and arguments and leaves
        //									(unless changed) one result
    }

    //
//		 ** {======================================================================
//		 ** Expression parsing
//		 ** =======================================================================
//
    private fun prefixexp(ls: LexState, v: expdesc) { // prefixexp . NAME | '(' expr ')'
        when (ls.t.token) {
            '('.toInt() -> {
                val line: Int = ls.linenumber
                LuaLex.luaX_next(ls)
                expr(ls, v)
                check_match(ls, ')'.toInt(), '('.toInt(), line)
                LuaCode.luaK_dischargevars(ls.fs!!, v)
                return
            }
            RESERVED.TK_NAME -> {
                singlevar(ls, v)
                return
            }
            else -> {
                LuaLex.luaX_syntaxerror(ls, CharPtr.Companion.toCharPtr("unexpected symbol"))
                return
            }
        }
    }

    private fun primaryexp(ls: LexState, v: expdesc) { //             primaryexp .
//				prefixexp { `.' NAME | `[' exp `]' | `:' NAME funcargs | funcargs }
        val fs: FuncState = ls.fs!!
        prefixexp(ls, v)
        while (true) {
            when (ls.t.token) {
                '.'.toInt() -> {
                    // field
                    field(ls, v)
                }
                '['.toInt() -> {
                    // `[' exp1 `]'
                    val key = expdesc()
                    LuaCode.luaK_exp2anyreg(fs, v)
                    yindex(ls, key)
                    LuaCode.luaK_indexed(fs, v, key)
                }
                ':'.toInt() -> {
                    // `:' NAME funcargs
                    val key = expdesc()
                    LuaLex.luaX_next(ls)
                    checkname(ls, key)
                    LuaCode.luaK_self(fs, v, key)
                    funcargs(ls, v)
                }
                '('.toInt(), RESERVED.TK_STRING, '{'.toInt() -> {
                    // funcargs
                    LuaCode.luaK_exp2nextreg(fs, v)
                    funcargs(ls, v)
                }
                else -> {
                    return
                }
            }
        }
    }

    private fun simpleexp(
        ls: LexState,
        v: expdesc
    ) { //             simpleexp . NUMBER | STRING | NIL | true | false | ... |
//						  constructor | FUNCTION body | primaryexp
        when (ls.t.token) {
            RESERVED.TK_NUMBER -> {
                init_exp(v, expkind.VKNUM, 0)
                v.u.nval = ls.t.seminfo.r
            }
            RESERVED.TK_STRING -> {
                codestring(ls, v, ls.t.seminfo.ts!!)
            }
            RESERVED.TK_NIL -> {
                init_exp(v, expkind.VNIL, 0)
            }
            RESERVED.TK_TRUE -> {
                init_exp(v, expkind.VTRUE, 0)
            }
            RESERVED.TK_FALSE -> {
                init_exp(v, expkind.VFALSE, 0)
            }
            RESERVED.TK_DOTS -> {
                // vararg
                val fs: FuncState = ls.fs!!
                check_condition(
                    ls,
                    fs.f!!.is_vararg.toInt() != 0,
                    CharPtr.Companion.toCharPtr("cannot use " + LuaConf.LUA_QL("...") + " outside a vararg function")
                )
                fs.f!!.is_vararg =
                    (fs.f!!.is_vararg and ((LuaObject.VARARG_NEEDSARG.inv() and 0xff) as Byte)) // don't need 'arg'  - lu_byte - unchecked
                init_exp(v, expkind.VVARARG, LuaCode.luaK_codeABC(fs, OpCode.OP_VARARG, 0, 1, 0))
            }
            '{'.toInt() -> {
                // constructor
                constructor(ls, v)
                return
            }
            RESERVED.TK_FUNCTION -> {
                LuaLex.luaX_next(ls)
                body(ls, v, 0, ls.linenumber)
                return
            }
            else -> {
                primaryexp(ls, v)
                return
            }
        }
        LuaLex.luaX_next(ls)
    }

    private fun getunopr(op: Int): UnOpr {
        return when (op) {
            RESERVED.TK_NOT -> {
                UnOpr.OPR_NOT
            }
            '-'.toInt() -> {
                UnOpr.OPR_MINUS
            }
            '#'.toInt() -> {
                UnOpr.OPR_LEN
            }
            else -> {
                UnOpr.OPR_NOUNOPR
            }
        }
    }

    private fun getbinopr(op: Int): BinOpr {
        return when (op) {
            '+'.toInt() -> {
                BinOpr.OPR_ADD
            }
            '-'.toInt() -> {
                BinOpr.OPR_SUB
            }
            '*'.toInt() -> {
                BinOpr.OPR_MUL
            }
            '/'.toInt() -> {
                BinOpr.OPR_DIV
            }
            '%'.toInt() -> {
                BinOpr.OPR_MOD
            }
            '^'.toInt() -> {
                BinOpr.OPR_POW
            }
            RESERVED.TK_CONCAT -> {
                BinOpr.OPR_CONCAT
            }
            RESERVED.TK_NE -> {
                BinOpr.OPR_NE
            }
            RESERVED.TK_EQ -> {
                BinOpr.OPR_EQ
            }
            '<'.toInt() -> {
                BinOpr.OPR_LT
            }
            RESERVED.TK_LE -> {
                BinOpr.OPR_LE
            }
            '>'.toInt() -> {
                BinOpr.OPR_GT
            }
            RESERVED.TK_GE -> {
                BinOpr.OPR_GE
            }
            RESERVED.TK_AND -> {
                BinOpr.OPR_AND
            }
            RESERVED.TK_OR -> {
                BinOpr.OPR_OR
            }
            else -> {
                BinOpr.OPR_NOBINOPR
            }
        }
    }

    // ORDER OPR
// `+' `-' `/' `%'
// power and concat (right associative)
// equality and inequality
// order
// logical (and/or)
    private val priority = arrayOf(
        priority_(6.toByte(), 6.toByte()),
        priority_(6.toByte(), 6.toByte()),
        priority_(7.toByte(), 7.toByte()),
        priority_(7.toByte(), 7.toByte()),
        priority_(7.toByte(), 7.toByte()),
        priority_(10.toByte(), 9.toByte()),
        priority_(5.toByte(), 4.toByte()),
        priority_(3.toByte(), 3.toByte()),
        priority_(3.toByte(), 3.toByte()),
        priority_(3.toByte(), 3.toByte()),
        priority_(3.toByte(), 3.toByte()),
        priority_(3.toByte(), 3.toByte()),
        priority_(3.toByte(), 3.toByte()),
        priority_(2.toByte(), 2.toByte()),
        priority_(1.toByte(), 1.toByte())
    )
    const val UNARY_PRIORITY = 8 // priority for unary operators
    //
//		 ** subexpr . (simpleexp | unop subexpr) { binop subexpr }
//		 ** where `binop' is any binary operator with a priority higher than `limit'
//
    private fun subexpr(ls: LexState, v: expdesc, limit: Int): BinOpr { //uint
        var op: BinOpr // = new BinOpr();
        val uop: UnOpr // = new UnOpr();
        enterlevel(ls)
        uop = getunopr(ls.t.token)
        if (uop != UnOpr.OPR_NOUNOPR) {
            LuaLex.luaX_next(ls)
            subexpr(ls, v, UNARY_PRIORITY)
            LuaCode.luaK_prefix(ls.fs!!, uop, v)
        } else {
            simpleexp(ls, v)
        }
        // expand while operators have priorities higher than `limit'
        op = getbinopr(ls.t.token)
        while (op != BinOpr.OPR_NOBINOPR && priority[op.getValue()].left > limit) {
            val v2 = expdesc()
            var nextop: BinOpr
            LuaLex.luaX_next(ls)
            LuaCode.luaK_infix(ls.fs!!, op, v)
            // read sub-expression with higher priority
            nextop = subexpr(ls, v2, priority[op.getValue()].right.toInt())
            LuaCode.luaK_posfix(ls.fs!!, op, v, v2)
            op = nextop
        }
        leavelevel(ls)
        return op // return first untreated operator
    }

    private fun expr(ls: LexState, v: expdesc) {
        subexpr(ls, v, 0)
    }

    // }====================================================================
//
//		 ** {======================================================================
//		 ** Rules for Statements
//		 ** =======================================================================
//
    private fun block_follow(token: Int): Int {
        return when (token) {
            RESERVED.TK_ELSE, RESERVED.TK_ELSEIF, RESERVED.TK_END, RESERVED.TK_UNTIL, RESERVED.TK_EOS -> {
                1
            }
            else -> {
                0
            }
        }
    }

    private fun block(ls: LexState) { // block . chunk
        val fs: FuncState = ls.fs!!
        val bl = BlockCnt()
        enterblock(fs, bl, 0.toByte())
        chunk(ls)
        LuaLimits.lua_assert(bl.breaklist == LuaCode.NO_JUMP)
        leaveblock(fs)
    }

    //
//		 ** check whether, in an assignment to a local variable, the local variable
//		 ** is needed in a previous assignment (to a table). If so, save original
//		 ** local value in a safe place and use this safe copy in the previous
//		 ** assignment.
//
    private fun check_conflict(ls: LexState, lh: LHS_assign, v: expdesc) {
        var lh: LHS_assign? = lh
        val fs: FuncState = ls.fs!!
        val extra = fs.freereg // eventual position to save local variable
        var conflict = 0
        while (lh != null) {
            if (lh.v.k == expkind.VINDEXED) {
                if (lh.v.u.s.info == v.u.s.info) { // conflict?
                    conflict = 1
                    lh.v.u.s.info = extra // previous assignment will use safe copy
                }
                if (lh.v.u.s.aux == v.u.s.info) { // conflict?
                    conflict = 1
                    lh.v.u.s.aux = extra // previous assignment will use safe copy
                }
            }
            lh = lh.prev
        }
        if (conflict != 0) {
            LuaCode.luaK_codeABC(fs, OpCode.OP_MOVE, fs.freereg, v.u.s.info, 0) // make copy
            LuaCode.luaK_reserveregs(fs, 1)
        }
    }

    private fun assignment(ls: LexState, lh: LHS_assign, nvars: Int) {
        val e = expdesc()
        check_condition(
            ls,
            expkindToInt(expkind.VLOCAL) <= expkindToInt(lh.v.k) && expkindToInt(lh.v.k) <= expkindToInt(
                expkind.VINDEXED
            ),
            CharPtr.Companion.toCharPtr("syntax error")
        )
        if (testnext(ls, ','.toInt()) != 0) { // assignment . `,' primaryexp assignment
            val nv = LHS_assign()
            nv.prev = lh
            primaryexp(ls, nv.v)
            if (nv.v.k == expkind.VLOCAL) {
                check_conflict(ls, lh, nv.v)
            }
            luaY_checklimit(
                ls.fs!!,
                nvars,
                LuaConf.LUAI_MAXCCALLS - ls.L!!.nCcalls,
                CharPtr.Companion.toCharPtr("variables in assignment")
            )
            assignment(ls, nv, nvars + 1)
        } else { // assignment . `=' explist1
            val nexps: Int
            checknext(ls, '='.toInt())
            nexps = explist1(ls, e)
            if (nexps != nvars) {
                adjust_assign(ls, nvars, nexps, e)
                if (nexps > nvars) {
                    ls.fs!!.freereg -= nexps - nvars // remove extra values
                }
            } else {
                LuaCode.luaK_setoneret(ls.fs!!, e) // close last expression
                LuaCode.luaK_storevar(ls.fs!!, lh.v, e)
                return  // avoid default
            }
        }
        init_exp(e, expkind.VNONRELOC, ls.fs!!.freereg - 1) // default assignment
        LuaCode.luaK_storevar(ls.fs!!, lh.v, e)
    }

    private fun cond(ls: LexState): Int { // cond . exp
        val v = expdesc()
        expr(ls, v) // read condition
        if (v.k == expkind.VNIL) { // `falses' are all equal here
            v.k = expkind.VFALSE
        }
        LuaCode.luaK_goiftrue(ls.fs!!, v)
        return v.f
    }

    private fun breakstat(ls: LexState) {
        val fs: FuncState = ls.fs!!
        var bl = fs.bl
        var upval = 0
        while (bl != null && bl.isbreakable.toInt() == 0) {
            upval = upval or bl.upval.toInt()
            bl = bl.previous
        }
        if (bl == null) {
            LuaLex.luaX_syntaxerror(ls, CharPtr.Companion.toCharPtr("no loop to break"))
        }
        if (upval != 0) {
            LuaCode.luaK_codeABC(fs, OpCode.OP_CLOSE, bl!!.nactvar.toInt(), 0, 0)
        }
        val breaklist_ref = IntArray(1)
        breaklist_ref[0] = bl!!.breaklist
        LuaCode.luaK_concat(fs, breaklist_ref, LuaCode.luaK_jump(fs)) //ref
        bl.breaklist = breaklist_ref[0]
    }

    private fun whilestat(ls: LexState, line: Int) { // whilestat . WHILE cond DO block END
        val fs: FuncState = ls.fs!!
        val whileinit: Int
        val condexit: Int
        val bl = BlockCnt()
        LuaLex.luaX_next(ls) // skip WHILE
        whileinit = LuaCode.luaK_getlabel(fs)
        condexit = cond(ls)
        enterblock(fs, bl, 1.toByte())
        checknext(ls, RESERVED.TK_DO as Int)
        block(ls)
        LuaCode.luaK_patchlist(fs, LuaCode.luaK_jump(fs), whileinit)
        check_match(ls, RESERVED.TK_END as Int, RESERVED.TK_WHILE as Int, line)
        leaveblock(fs)
        LuaCode.luaK_patchtohere(fs, condexit) // false conditions finish the loop
    }

    private fun repeatstat(ls: LexState, line: Int) { // repeatstat . REPEAT block UNTIL cond
        val condexit: Int
        val fs: FuncState = ls.fs!!
        val repeat_init: Int = LuaCode.luaK_getlabel(fs)
        val bl1 = BlockCnt()
        val bl2 = BlockCnt()
        enterblock(fs, bl1, 1.toByte()) // loop block
        enterblock(fs, bl2, 0.toByte()) // scope block
        LuaLex.luaX_next(ls) // skip REPEAT
        chunk(ls)
        check_match(ls, RESERVED.TK_UNTIL as Int, RESERVED.TK_REPEAT as Int, line)
        condexit = cond(ls) // read condition (inside scope block)
        if (bl2.upval.toInt() == 0) { // no upvalues?
            leaveblock(fs) // finish scope
            LuaCode.luaK_patchlist(ls.fs!!, condexit, repeat_init) // close the loop
        } else { // complete semantics when there are upvalues
            breakstat(ls) // if condition then break
            LuaCode.luaK_patchtohere(ls.fs!!, condexit) // else...
            leaveblock(fs) // finish scope...
            LuaCode.luaK_patchlist(ls.fs!!, LuaCode.luaK_jump(fs), repeat_init) // and repeat
        }
        leaveblock(fs) // finish loop
    }

    private fun exp1(ls: LexState): Int {
        val e = expdesc()
        val k: Int
        expr(ls, e)
        k = expkindToInt(e.k)
        LuaCode.luaK_exp2nextreg(ls.fs!!, e)
        return k
    }

    private fun forbody(
        ls: LexState,
        base_: Int,
        line: Int,
        nvars: Int,
        isnum: Int
    ) { // forbody . DO block
        val bl = BlockCnt()
        val fs: FuncState = ls.fs!!
        val prep: Int
        val endfor: Int
        adjustlocalvars(ls, 3) // control variables
        checknext(ls, RESERVED.TK_DO as Int)
        prep = if (isnum != 0) LuaCode.luaK_codeAsBx(
            fs,
            OpCode.OP_FORPREP,
            base_,
            LuaCode.NO_JUMP
        ) else LuaCode.luaK_jump(fs)
        enterblock(fs, bl, 0.toByte()) // scope for declared variables
        adjustlocalvars(ls, nvars)
        LuaCode.luaK_reserveregs(fs, nvars)
        block(ls)
        leaveblock(fs) // end of scope for declared variables
        LuaCode.luaK_patchtohere(fs, prep)
        endfor = if (isnum != 0) LuaCode.luaK_codeAsBx(
            fs,
            OpCode.OP_FORLOOP,
            base_,
            LuaCode.NO_JUMP
        ) else LuaCode.luaK_codeABC(fs, OpCode.OP_TFORLOOP, base_, 0, nvars)
        LuaCode.luaK_fixline(fs, line) // pretend that `OP_FOR' starts the loop
        LuaCode.luaK_patchlist(fs, if (isnum != 0) endfor else LuaCode.luaK_jump(fs), prep + 1)
    }

    private fun fornum(
        ls: LexState,
        varname: TString,
        line: Int
    ) { // fornum . NAME = exp1,exp1[,exp1] forbody
        val fs: FuncState = ls.fs!!
        val base_ = fs.freereg
        new_localvarliteral(ls, CharPtr.Companion.toCharPtr("(for index)"), 0)
        new_localvarliteral(ls, CharPtr.Companion.toCharPtr("(for limit)"), 1)
        new_localvarliteral(ls, CharPtr.Companion.toCharPtr("(for step)"), 2)
        new_localvar(ls, varname, 3)
        checknext(ls, '='.toInt())
        exp1(ls) // initial value
        checknext(ls, ','.toInt())
        exp1(ls) // limit
        if (testnext(ls, ','.toInt()) != 0) {
            exp1(ls) // optional step
        } else { // default step = 1
            LuaCode.luaK_codeABx(fs, OpCode.OP_LOADK, fs.freereg, LuaCode.luaK_numberK(fs, 1.0))
            LuaCode.luaK_reserveregs(fs, 1)
        }
        forbody(ls, base_, line, 1, 1)
    }

    private fun forlist(ls: LexState, indexname: TString) { // forlist . NAME {,NAME} IN explist1 forbody
        val fs: FuncState = ls.fs!!
        val e = expdesc()
        var nvars = 0
        val line: Int
        val base_ = fs.freereg
        // create control variables
        new_localvarliteral(ls, CharPtr.Companion.toCharPtr("(for generator)"), nvars++)
        new_localvarliteral(ls, CharPtr.Companion.toCharPtr("(for state)"), nvars++)
        new_localvarliteral(ls, CharPtr.Companion.toCharPtr("(for control)"), nvars++)
        // create declared variables
        new_localvar(ls, indexname, nvars++)
        while (testnext(ls, ','.toInt()) != 0) {
            new_localvar(ls, str_checkname(ls)!!, nvars++)
        }
        checknext(ls, RESERVED.TK_IN as Int)
        line = ls.linenumber
        adjust_assign(ls, 3, explist1(ls, e), e)
        LuaCode.luaK_checkstack(fs, 3) // extra space to call generator
        forbody(ls, base_, line, nvars - 3, 0)
    }

    private fun forstat(ls: LexState, line: Int) { // forstat . FOR (fornum | forlist) END
        val fs: FuncState = ls.fs!!
        val varname: TString
        val bl = BlockCnt()
        enterblock(fs, bl, 1.toByte()) // scope for loop and control variables
        LuaLex.luaX_next(ls) // skip `for'
        varname = str_checkname(ls)!! // first variable name
        when (ls.t.token) {
            '='.toInt() -> {
                fornum(ls, varname, line)
            }
            ','.toInt(), RESERVED.TK_IN -> {
                forlist(ls, varname)
            }
            else -> {
                LuaLex.luaX_syntaxerror(
                    ls,
                    CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("=").toString() + " or " + LuaConf.LUA_QL("in") + " expected")
                )
            }
        }
        check_match(ls, RESERVED.TK_END as Int, RESERVED.TK_FOR as Int, line)
        leaveblock(fs) // loop scope (`break' jumps to this point)
    }

    private fun test_then_block(ls: LexState): Int { // test_then_block . [IF | ELSEIF] cond THEN block
        val condexit: Int
        LuaLex.luaX_next(ls) // skip IF or ELSEIF
        condexit = cond(ls)
        checknext(ls, RESERVED.TK_THEN as Int)
        block(ls) // `then' part
        return condexit
    }

    private fun ifstat(
        ls: LexState,
        line: Int
    ) { // ifstat . IF cond THEN block {ELSEIF cond THEN block} [ELSE block] END
        val fs: FuncState = ls.fs!!
        var flist: Int
        val escapelist = IntArray(1)
        escapelist[0] = LuaCode.NO_JUMP
        flist = test_then_block(ls) // IF cond THEN block
        while (ls.t.token == RESERVED.TK_ELSEIF as Int) {
            LuaCode.luaK_concat(fs, escapelist, LuaCode.luaK_jump(fs)) //ref
            LuaCode.luaK_patchtohere(fs, flist)
            flist = test_then_block(ls) // ELSEIF cond THEN block
        }
        if (ls.t.token == RESERVED.TK_ELSE as Int) {
            LuaCode.luaK_concat(fs, escapelist, LuaCode.luaK_jump(fs)) //ref
            LuaCode.luaK_patchtohere(fs, flist)
            LuaLex.luaX_next(ls) // skip ELSE (after patch, for correct line info)
            block(ls) // `else' part
        } else {
            LuaCode.luaK_concat(fs, escapelist, flist) //ref
        }
        LuaCode.luaK_patchtohere(fs, escapelist[0])
        check_match(ls, RESERVED.TK_END as Int, RESERVED.TK_IF as Int, line)
    }

    private fun localfunc(ls: LexState) {
        val v = expdesc()
        val b = expdesc()
        val fs: FuncState = ls.fs!!
        new_localvar(ls, str_checkname(ls)!!, 0)
        init_exp(v, expkind.VLOCAL, fs.freereg)
        LuaCode.luaK_reserveregs(fs, 1)
        adjustlocalvars(ls, 1)
        body(ls, b, 0, ls.linenumber)
        LuaCode.luaK_storevar(fs, v, b)
        // debug information will only see the variable after this point!
        getlocvar(fs, fs.nactvar - 1).startpc = fs.pc
    }

    private fun localstat(ls: LexState) { // stat . LOCAL NAME {`,' NAME} [`=' explist1]
        var nvars = 0
        val nexps: Int
        val e = expdesc()
        do {
            new_localvar(ls, str_checkname(ls)!!, nvars++)
        } while (testnext(ls, ','.toInt()) != 0)
        if (testnext(ls, '='.toInt()) != 0) {
            nexps = explist1(ls, e)
        } else {
            e.k = expkind.VVOID
            nexps = 0
        }
        adjust_assign(ls, nvars, nexps, e)
        adjustlocalvars(ls, nvars)
    }

    private fun funcname(ls: LexState, v: expdesc): Int { // funcname . NAME {field} [`:' NAME]
        var needself = 0
        singlevar(ls, v)
        while (ls.t.token == '.'.toInt()) {
            field(ls, v)
        }
        if (ls.t.token == ':'.toInt()) {
            needself = 1
            field(ls, v)
        }
        return needself
    }

    private fun funcstat(ls: LexState, line: Int) { // funcstat . FUNCTION funcname body
        val needself: Int
        val v = expdesc()
        val b = expdesc()
        LuaLex.luaX_next(ls) // skip FUNCTION
        needself = funcname(ls, v)
        body(ls, b, needself, line)
        LuaCode.luaK_storevar(ls.fs!!, v, b)
        LuaCode.luaK_fixline(ls.fs!!, line) // definition `happens' in the first line
    }

    private fun exprstat(ls: LexState) { // stat . func | assignment
        val fs: FuncState = ls.fs!!
        val v = LHS_assign()
        primaryexp(ls, v.v)
        if (v.v.k == expkind.VCALL) { // stat. func
            LuaOpCodes.SETARG_C(LuaCode.getcode(fs, v.v), 1) // call statement uses no results
        } else { // stat . assignment
            v.prev = null
            assignment(ls, v, 1)
        }
    }

    private fun retstat(ls: LexState) { // stat . RETURN explist
        val fs: FuncState = ls.fs!!
        val e = expdesc()
        val first: Int
        var nret: Int // registers with returned values
        LuaLex.luaX_next(ls) // skip RETURN
        if (block_follow(ls.t.token) != 0 || ls.t.token == ';'.toInt()) {
            nret = 0
            first = nret // return no values
        } else {
            nret = explist1(ls, e) // optional return values
            if (hasmultret(e.k) != 0) {
                LuaCode.luaK_setmultret(fs, e)
                if (e.k == expkind.VCALL && nret == 1) { // tail call?
                    LuaOpCodes.SET_OPCODE(LuaCode.getcode(fs, e), OpCode.OP_TAILCALL)
                    LuaLimits.lua_assert(LuaOpCodes.GETARG_A(LuaCode.getcode(fs, e)) == fs.nactvar.toInt())
                }
                first = fs.nactvar.toInt()
                nret = Lua.LUA_MULTRET // return all values
            } else {
                if (nret == 1) { // only one single value?
                    first = LuaCode.luaK_exp2anyreg(fs, e)
                } else {
                    LuaCode.luaK_exp2nextreg(fs, e) // values must go to the `stack'
                    first = fs.nactvar.toInt() // return all `active' values
                    LuaLimits.lua_assert(nret == fs.freereg - first)
                }
            }
        }
        LuaCode.luaK_ret(fs, first, nret)
    }

    private fun statement(ls: LexState): Int {
        val line: Int = ls.linenumber // may be needed for error messages
        return when (ls.t.token) {
            RESERVED.TK_IF -> {
                // stat . ifstat
                ifstat(ls, line)
                0
            }
            RESERVED.TK_WHILE -> {
                // stat . whilestat
                whilestat(ls, line)
                0
            }
            RESERVED.TK_DO -> {
                // stat . DO block END
                LuaLex.luaX_next(ls) // skip DO
                block(ls)
                check_match(ls, RESERVED.TK_END as Int, RESERVED.TK_DO as Int, line)
                0
            }
            RESERVED.TK_FOR -> {
                // stat . forstat
                forstat(ls, line)
                0
            }
            RESERVED.TK_REPEAT -> {
                // stat . repeatstat
                repeatstat(ls, line)
                0
            }
            RESERVED.TK_FUNCTION -> {
                funcstat(ls, line) // stat. funcstat
                0
            }
            RESERVED.TK_LOCAL -> {
                // stat . localstat
                LuaLex.luaX_next(ls) // skip LOCAL
                if (testnext(ls, RESERVED.TK_FUNCTION as Int) != 0) { // local function?
                    localfunc(ls)
                } else {
                    localstat(ls)
                }
                0
            }
            RESERVED.TK_RETURN -> {
                // stat . retstat
                retstat(ls)
                1 // must be last statement
            }
            RESERVED.TK_BREAK -> {
                // stat . breakstat
                LuaLex.luaX_next(ls) // skip BREAK
                breakstat(ls)
                1 // must be last statement
            }
            else -> {
                exprstat(ls)
                0 // to avoid warnings
            }
        }
    }

    private fun chunk(ls: LexState) { // chunk . { stat [`;'] }
        var islast = 0
        enterlevel(ls)
        while (islast == 0 && block_follow(ls.t.token) == 0) {
            islast = statement(ls)
            testnext(ls, ';'.toInt())
            LuaLimits.lua_assert(ls.fs!!.f!!.maxstacksize >= ls.fs!!.freereg && ls.fs!!.freereg >= ls.fs!!.nactvar)
            ls.fs!!.freereg = ls.fs!!.nactvar.toInt() // free registers
        }
        leavelevel(ls)
    } // }======================================================================

    /*
	 ** Expression descriptor
	 */
    enum class expkind(/* info = instruction pc */
        private val intValue: Int
    ) {
        VVOID(0),  /* no value */
        VNIL(1), VTRUE(2), VFALSE(3), VK(4),  /* info = index of constant in `k' */
        VKNUM(5),  /* nval = numerical value */
        VLOCAL(6),  /* info = local register */
        VUPVAL(7),  /* info = index of upvalue in `upvalues' */
        VGLOBAL(8),  /* info = index of table; aux = index of global name in `k' */
        VINDEXED(9),  /* info = table register; aux = index register (or `k') */
        VJMP(10),  /* info = instruction pc */
        VRELOCABLE(11),  /* info = instruction pc */
        VNONRELOC(12),  /* info = result register */
        VCALL(13),  /* info = instruction pc */
        VVARARG(14);

        fun getValue(): Int {
            return intValue
        }

//        companion object {
//            private var mappings: HashMap<Int, expkind>? = null
//            @Synchronized
//            private fun getMappings(): HashMap<Int, expkind>? {
//                if (mappings == null) {
//                    mappings = HashMap()
//                }
//                return mappings
//            }
//
//            fun forValue(value: Int): expkind? {
//                return getMappings()!![value]
//            }
//        }

        init {
//            expkind.Companion.getMappings()!![intValue] = this
        }
    }

    class expdesc {
        class _u {
            var s = _s()
            var nval /*Double*/ /*lua_Number*/ = 0.0
            fun Copy(u: LuaParser.expdesc._u) {
                s.Copy(u.s)
                nval = u.nval
            }

            class _s {
                var info = 0
                var aux = 0
                fun Copy(s: _s) {
                    info = s.info
                    aux = s.aux
                }
            }
        }

        var u = LuaParser.expdesc._u()
        var t /* patch list of `exit when true' */ = 0
        var f /* patch list of `exit when false' */ = 0
        var k = expkind.VVOID //expkind.forValue(0)
        fun Copy(e: expdesc) {
            k = e.k
            u.Copy(e.u)
            t = e.t
            f = e.f
        }
    }

    class upvaldesc {
        var k /*Byte*/ /*lu_byte*/: Byte = 0
        var info /*Byte*/ /*lu_byte*/: Byte = 0
    }

    /* state needed to generate code for a given function */
    class FuncState {
        var f /* current function header */: Proto? = null
        var h /* table to find (and reuse) elements in `k' */: Table? = null
        var prev /* enclosing function */: FuncState? = null
        var ls /* lexical state */: LexState? = null
        var L /* copy of the Lua state */: lua_State? = null
        var bl /* chain of current blocks */: BlockCnt? = null
        var pc /* next position to code (equivalent to `ncode') */ = 0
        var lasttarget /* `pc' of last `jump target' */ = 0
        var jpc /* list of pending jumps to `pc' */ = 0
        var freereg /* first free register */ = 0
        var nk /* number of elements in `k' */ = 0
        var np /* number of elements in `p' */ = 0
        var nlocvars /* number of elements in `locvars' */: Short = 0
        var nactvar /*Byte*/ /*lu_byte*/ /* number of active local variables */: Byte = 0
        var upvalues = arrayOfNulls<upvaldesc>(LuaConf.LUAI_MAXUPVALUES) /* upvalues */
        var   /*ushort[]*/actvar = IntArray(LuaConf.LUAI_MAXVARS) /* declared-variable stack */

        init {
            for (i in upvalues.indices) {
                upvalues[i] = upvaldesc()
            }
        }
    }

    /*
    ** nodes for block list (list of active blocks)
    */
    class BlockCnt {
        var previous /* chain */: BlockCnt? = null
        var breaklist /* list of jumps out of this loop */ = 0
        var nactvar /*Byte lu_byte*/ /* # active locals outside the breakable structure */: Byte = 0
        var upval /*Byte lu_byte*/ /* true if some variable in the block is an upvalue */: Byte = 0
        var isbreakable /*Bytelu_byte*/ /* true if `block' is a loop */: Byte = 0
    }

    //
//		 ** {======================================================================
//		 ** Rules for Constructors
//		 ** =======================================================================
//
    class ConsControl {
        var v = expdesc() /* last list item read */
        var t /* table descriptor */: expdesc? = null
        var nh /* total number of `record' elements */ = 0
        var na /* total number of array elements */ = 0
        var tostore /* number of array elements pending to be stored */ = 0
    }

    class priority_ /*Byte*/ /*lu_byte*/(/*Byte*/ /*lu_byte*/ /* left priority for each binary operator */
        var left: Byte, /*Byte*/ /*lu_byte*/ /* right priority */
        var right: Byte
    )

    /*
	 ** structure to chain all variables in the left-hand side of an
	 ** assignment
	 */
    class LHS_assign {
        var prev: LHS_assign? = null
        var v = expdesc() /* variable (global, local, upvalue, or indexed) */
    }
}