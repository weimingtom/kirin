package kirin

import kirin.CLib.CharPtr
import kirin.Lua.lua_Debug
import kirin.Lua.lua_Hook
import kirin.LuaCode.InstructionPtr
import kirin.LuaConf.LuaException
import kirin.LuaObject.LClosure
import kirin.LuaObject.Proto
import kirin.LuaObject.TValue
import kirin.LuaObject.Table
import kirin.LuaOpCodes.OpCode
import kirin.LuaState.CallInfo
import kirin.LuaState.lua_State
import kirin.LuaTM.TMS
import kirin.LuaZIO.Mbuffer
import kirin.LuaZIO.ZIO
import kotlin.experimental.and

//
// ** $Id: ldo.c,v 2.38.1.3 2008/01/18 22:31:22 roberto Exp $
// ** Stack and Call structure of Lua
// ** See Copyright Notice in lua.h
//
//using lua_Integer = System.Int32;
//using ptrdiff_t = System.Int32;
//using TValue = Lua.TValue;
//using StkId = TValue;
//using lu_byte = System.Byte;
object LuaDo {
    fun luaD_checkstack(L: lua_State?, n: Int) {
        if (TValue.Companion.minus(L!!.stack_last, L!!.top) <= n) {
            luaD_growstack(L, n)
        } else { ///#if HARDSTACKTESTS
//				luaD_reallocstack(L, L.stacksize - EXTRA_STACK - 1);
///#endif
        }
    }

    fun incr_top(L: lua_State?) {
        luaD_checkstack(L, 1)
        val top: Array<TValue?> = arrayOfNulls<TValue>(1)
        top[0] = L!!.top
        //StkId
        TValue.Companion.inc(top) //ref
        L!!.top = top[0]
    }

    // in the original C code these values save and restore the stack by number of bytes. marshalling sizeof
// isn't that straightforward in managed languages, so i implement these by index instead.
    fun savestack(L: lua_State?, p: TValue?): Int { //StkId
        return TValue.Companion.toInt(p!!)
    }

    fun restorestack(L: lua_State?, n: Int): TValue? { //StkId
        return L!!.stack!!.get(n)
    }

    fun saveci(L: lua_State, p: CallInfo?): Int {
        return CallInfo.Companion.minus(p!!, L.base_ci!!)
    }

    fun restoreci(L: lua_State, n: Int): CallInfo? {
        return L!!.base_ci!!.get(n)
    }

    // results from luaD_precall
    const val PCRLUA = 0 // initiated a call to a Lua function
    const val PCRC = 1 // did a call to a C function
    const val PCRYIELD = 2 // C funtion yielded
    fun luaD_seterrorobj(L: lua_State, errcode: Int, oldtop: TValue) { //StkId
        when (errcode) {
            Lua.LUA_ERRMEM -> {
                LuaObject.setsvalue2s(
                    L,
                    oldtop,
                    LuaString.luaS_newliteral(L, CharPtr.Companion.toCharPtr(LuaMem.MEMERRMSG))
                )
            }
            Lua.LUA_ERRERR -> {
                LuaObject.setsvalue2s(
                    L,
                    oldtop,
                    LuaString.luaS_newliteral(L, CharPtr.Companion.toCharPtr("error in error handling"))
                )
            }
            Lua.LUA_ERRSYNTAX, Lua.LUA_ERRRUN -> {
                LuaObject.setobjs2s(L, oldtop, TValue.Companion.minus(L.top!!, 1)!!) // error message on current top
            }
        }
        L.top = TValue.Companion.plus(oldtop, 1)
    }

    private fun restore_stack_limit(L: lua_State) {
        LuaLimits.lua_assert(TValue.Companion.toInt(L.stack_last!!) == L.stacksize - LuaState.EXTRA_STACK - 1)
        if (L.size_ci > LuaConf.LUAI_MAXCALLS) { // there was an overflow?
            val inuse: Int = CallInfo.Companion.minus(L.ci!!, L.base_ci!!)
            if (inuse + 1 < LuaConf.LUAI_MAXCALLS) { // can `undo' overflow?
                luaD_reallocCI(L, LuaConf.LUAI_MAXCALLS)
            }
        }
    }

    private fun resetstack(L: lua_State, status: Int) {
        L.ci = L.base_ci!!.get(0)
        L.base_ = L.ci!!.base_
        LuaFunc.luaF_close(L, L.base_) // close eventual pending closures
        luaD_seterrorobj(L, status, L.base_!!)
        L.nCcalls = L.baseCcalls
        L.allowhook = 1
        restore_stack_limit(L)
        L.errfunc = 0
        L.errorJmp = null
    }

    fun luaD_throw(L: lua_State?, errcode: Int) {
        if (L!!.errorJmp != null) {
            L!!.errorJmp!!.status = errcode
            LuaConf.LUAI_THROW(L, L!!.errorJmp)
        } else {
            L.status = LuaLimits.cast_byte(errcode)
            if (LuaState.G(L)!!.panic != null) {
                resetstack(L!!, errcode)
                LuaLimits.lua_unlock(L)
                LuaState.G(L)!!.panic!!.exec(L!!)
            }
            System.exit(CLib.EXIT_FAILURE)
        }
    }

    fun luaD_rawrunprotected(L: lua_State?, f: Pfunc, ud: Any?): Int {
        val lj = lua_longjmp()
        lj.status = 0
        lj.previous = L!!.errorJmp // chain new error handler
        L!!.errorJmp = lj
        //LUAI_TRY(L, lj,
//f(L, ud)
//);
        if (LuaConf.CATCH_EXCEPTIONS) {
            try {
                f.exec(L, ud)
            } catch (e: Exception) {
                e.printStackTrace()
                if (lj.status == 0) {
                    lj.status = -1
                }
            }
        } else {
            try {
                f.exec(L, ud)
            } catch (e: LuaException) {
                e.printStackTrace()
                if (lj.status == 0) {
                    lj.status = -1
                }
            }
        }
        L!!.errorJmp = lj.previous // restore old error handler
        return lj.status
    }

    // }======================================================
    private fun correctstack(
        L: lua_State?,
        oldstack: Array<TValue?>
    ) { //             don't need to do this
//		  CallInfo ci;
//		  GCObject up;
//		  L.top = L.stack[L.top - oldstack];
//		  for (up = L.openupval; up != null; up = up.gch.next)
//			gco2uv(up).v = L.stack[gco2uv(up).v - oldstack];
//		  for (ci = L.base_ci[0]; ci <= L.ci; CallInfo.inc(ref ci)) {
//			  ci.top = L.stack[ci.top - oldstack];
//			ci.base_ = L.stack[ci.base_ - oldstack];
//			ci.func = L.stack[ci.func - oldstack];
//		  }
//		  L.base_ = L.stack[L.base_ - oldstack];
//			 *
    }

    fun luaD_reallocstack(L: lua_State?, newsize: Int) {
        val oldstack: Array<TValue?> = L!!.stack!!
        val realsize: Int = newsize + 1 + LuaState.EXTRA_STACK
        LuaLimits.lua_assert(TValue.Companion.toInt(L.stack_last!!) == L.stacksize - LuaState.EXTRA_STACK - 1)
        val stack: Array<Array<TValue?>?> = arrayOfNulls<Array<TValue?>>(1)
        stack[0] = L.stack!!
        LuaMem.luaM_reallocvector_TValue(
            L,
            stack!!,
            L.stacksize,
            realsize,
            ClassType(ClassType.Companion.TYPE_TVALUE)
        ) //, TValue - ref
        L.stack = stack[0]
        L.stacksize = realsize
        L.stack_last = L.stack!!.get(newsize)
        correctstack(L, oldstack)
    }

    fun luaD_reallocCI(L: lua_State?, newsize: Int) {
        val oldci: CallInfo = L!!.base_ci!!.get(0)!!
        val base_ci: Array<Array<CallInfo?>?> = arrayOfNulls<Array<CallInfo?>>(1)
        base_ci[0] = L!!.base_ci!!
        LuaMem.luaM_reallocvector_CallInfo(
            L,
            base_ci,
            L.size_ci,
            newsize,
            ClassType(ClassType.Companion.TYPE_CALLINFO)
        ) //, CallInfo - ref
        L.base_ci = base_ci[0]
        L.size_ci = newsize
        L.ci = L.base_ci!!.get(CallInfo.Companion.minus(L.ci!!, oldci))
        L.end_ci = L.base_ci!!.get(L.size_ci - 1)
    }

    fun luaD_growstack(L: lua_State?, n: Int) {
        if (n <= L!!.stacksize) { // double size is enough?
            luaD_reallocstack(L, 2 * L!!.stacksize)
        } else {
            luaD_reallocstack(L, L!!.stacksize + n)
        }
    }

    private fun growCI(L: lua_State?): CallInfo {
        if (L!!.size_ci > LuaConf.LUAI_MAXCALLS) { // overflow while handling overflow?
            luaD_throw(L, Lua.LUA_ERRERR)
        } else {
            luaD_reallocCI(L, 2 * L!!.size_ci)
            if (L.size_ci > LuaConf.LUAI_MAXCALLS) {
                LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("stack overflow"))
            }
        }
        val ci_ref: Array<CallInfo?> = arrayOfNulls<CallInfo>(1)
        ci_ref[0] = L!!.ci
        CallInfo.Companion.inc(ci_ref) //ref
        L!!.ci = ci_ref[0]
        return L!!.ci!!
    }

    fun luaD_callhook(L: lua_State?, event_: Int, line: Int) {
        val hook: lua_Hook = L!!.hook!!
        if (hook != null && L!!.allowhook.toInt() != 0) {
            val top = savestack(L, L.top) //ptrdiff_t - Int32
            val ci_top = savestack(L, L.ci!!.top) //ptrdiff_t - Int32
            val ar = lua_Debug()
            ar.event_ = event_
            ar.currentline = line
            if (event_ == Lua.LUA_HOOKTAILRET) {
                ar.i_ci = 0 // tail call; no debug information about it
            } else {
                ar.i_ci = CallInfo.Companion.minus(L.ci!!, L.base_ci!!)
            }
            luaD_checkstack(L, Lua.LUA_MINSTACK) // ensure minimum stack size
            L.ci!!.top = TValue.Companion.plus(L.top!!, Lua.LUA_MINSTACK)
            LuaLimits.lua_assert(TValue.Companion.lessEqual(L.ci!!.top!!, L.stack_last!!))
            L.allowhook = 0 // cannot call hooks inside a hook
            LuaLimits.lua_unlock(L)
            hook.exec(L, ar)
            LuaLimits.lua_lock(L)
            LuaLimits.lua_assert(L.allowhook.toInt() == 0)
            L.allowhook = 1
            L.ci!!.top = restorestack(L, ci_top)
            L.top = restorestack(L, top)
        }
    }

    private fun adjust_varargs(L: lua_State?, p: Proto, actual: Int): TValue { //StkId
        var actual = actual
        var i: Int
        val nfixargs: Int = p.numparams.toInt()
        var htab: Table? = null
        val base_: TValue
        val fixed_: TValue //StkId
        while (actual < nfixargs) {
            val top: Array<TValue?> = arrayOfNulls<TValue>(1)
            top[0] = L!!.top
            val ret: TValue? = TValue.Companion.inc(top) //ref - StkId
            L.top = top[0]
            LuaObject.setnilvalue(ret)
            ++actual
        }
        ///#if LUA_COMPAT_VARARG
        if ((p.is_vararg and LuaObject.VARARG_NEEDSARG.toByte()) != 0.toByte()) { // compat. with old-style vararg?
            val nvar = actual - nfixargs // number of extra arguments
            LuaLimits.lua_assert((p.is_vararg and LuaObject.VARARG_HASARG.toByte()).toInt())
            LuaGC.luaC_checkGC(L)
            htab = LuaTable.luaH_new(L, nvar, 1) // create `arg' table
            i = 0
            while (i < nvar) {
                // put extra arguments into `arg' table
                LuaObject.setobj2n(
                    L,
                    LuaTable.luaH_setnum(L, htab, i + 1),
                    TValue.Companion.plus(TValue.Companion.minus(L!!.top!!, nvar)!!, i)
                ) //FIXME:
                i++
            }
            // store counter in field `n'
            LuaObject.setnvalue(
                LuaTable.luaH_setstr(
                    L,
                    htab,
                    LuaString.luaS_newliteral(L, CharPtr.Companion.toCharPtr("n"))
                ), LuaLimits.cast_num(nvar)
            )
        }
        ///#endif
// move fixed parameters to final position
        fixed_ = TValue.Companion.minus(L!!.top!!, actual)!! // first fixed argument
        base_ = L.top!! // final position of first argument
        i = 0
        while (i < nfixargs) {
            val top: Array<TValue?> = arrayOfNulls<TValue>(1)
            top[0] = L!!.top
            val ret: TValue = TValue.Companion.inc(top)!! //ref - StkId
            L.top = top[0]
            LuaObject.setobjs2s(L, ret, TValue.Companion.plus(fixed_, i))
            LuaObject.setnilvalue(TValue.Companion.plus(fixed_, i))
            i++
        }
        // add `arg' parameter
        if (htab != null) {
            val top: TValue = L.top!! //StkId
            val top_ref: Array<TValue?> = arrayOfNulls<TValue>(1)
            top_ref[0] = L.top
            //StkId
            TValue.Companion.inc(top_ref) //ref
            L.top = top_ref[0]
            LuaObject.sethvalue(L, top, htab)
            LuaLimits.lua_assert(LuaGC.iswhite(LuaState.obj2gco(htab)))
        }
        return base_
    }

    private fun tryfuncTM(L: lua_State?, func: TValue): TValue { //StkId - StkId
//const
        var func: TValue = func
        val tm: TValue = LuaTM.luaT_gettmbyobj(L, func, TMS.TM_CALL)
        val p: Array<TValue?> = arrayOfNulls<TValue>(1) //StkId
        p[0] = TValue()
        val funcr = savestack(L, func) //ptrdiff_t - Int32
        if (!LuaObject.ttisfunction(tm)) {
            LuaDebug.luaG_typeerror(L, func, CharPtr.Companion.toCharPtr("call"))
        }
        // Open a hole inside the stack at `func'
        p[0] = L!!.top
        while (TValue.Companion.greaterThan(p[0]!!, func)) {
            //ref - StkId
            LuaObject.setobjs2s(L, p[0], TValue.Companion.minus(p[0]!!, 1)!!)
            TValue.Companion.dec(p)
        }
        incr_top(L)
        func = restorestack(L, funcr)!! // previous call may change stack
        LuaObject.setobj2s(L, func, tm) // tag method is the new function to be called
        return func
    }

    fun inc_ci(L: lua_State?): CallInfo {
        if (L!!.ci === L!!.end_ci) {
            return growCI(L)
        }
        //   (condhardstacktests(luaD_reallocCI(L, L.size_ci)), ++L.ci))
        val ci_ref: Array<CallInfo?> = arrayOfNulls<CallInfo>(1)
        ci_ref[0] = L!!.ci
        CallInfo.Companion.inc(ci_ref) //ref
        L!!.ci = ci_ref[0]
        return L!!.ci!!
    }

    fun luaD_precall(L: lua_State?, func: TValue, nresults: Int): Int { //StkId
        var func: TValue = func
        val cl: LClosure
        val funcr: Int //ptrdiff_t - Int32
        if (!LuaObject.ttisfunction(func)) { // `func' is not a function?
            func = tryfuncTM(L, func) // check the `function' tag method
        }
        funcr = savestack(L, func)
        cl = LuaObject.clvalue(func)!!.l
        L!!.ci!!.savedpc = InstructionPtr.Companion.Assign(L.savedpc)
        return if (cl.getIsC().toInt() == 0) { // Lua function? prepare its call
            val ci: CallInfo
            val st: Array<TValue?> = arrayOfNulls<TValue>(1) //StkId
            st[0] = TValue()
            val base_: TValue //StkId
            val p: Proto = cl.p!!
            luaD_checkstack(L, p.maxstacksize.toInt())
            func = restorestack(L, funcr)!!
            if (p.is_vararg.toInt() == 0) { // no varargs?
                base_ = L.stack!!.get(TValue.Companion.toInt(TValue.Companion.plus(func, 1)))!!
                if (TValue.Companion.greaterThan(L.top!!, TValue.Companion.plus(base_, p.numparams.toInt()))) {
                    L.top = TValue.Companion.plus(base_, p.numparams.toInt())
                }
            } else { // vararg function
                val nargs: Int = TValue.Companion.minus(L.top, func) - 1
                base_ = adjust_varargs(L, p, nargs)
                func = restorestack(L, funcr)!! // previous call may change the stack
            }
            ci = inc_ci(L) // now `enter' new function
            ci.func = func
            ci.base_ = base_
            L.base_ = ci.base_
            ci.top = TValue.Companion.plus(L.base_!!, p.maxstacksize.toInt())
            LuaLimits.lua_assert(TValue.Companion.lessEqual(ci.top!!, L.stack_last!!))
            L.savedpc = InstructionPtr(p.code, 0) // starting point
            ci.tailcalls = 0
            ci.nresults = nresults
            st[0] = L.top
            while (TValue.Companion.lessThan(st[0], ci.top)) {
                //ref - StkId
                LuaObject.setnilvalue(st[0])
                TValue.Companion.inc(st)
            }
            L.top = ci.top
            if ((L.hookmask and Lua.LUA_MASKCALL.toByte()) != 0.toByte()) {
                val savedpc_ref: Array<InstructionPtr?> = arrayOfNulls<InstructionPtr>(1)
                savedpc_ref[0] = L.savedpc
                InstructionPtr.Companion.inc(savedpc_ref) // hooks assume 'pc' is already incremented  - ref
                L.savedpc = savedpc_ref[0]!!
                luaD_callhook(L, Lua.LUA_HOOKCALL, -1)
                savedpc_ref[0] = L.savedpc
                InstructionPtr.Companion.dec(savedpc_ref!!) // correct 'pc'  - ref
                L.savedpc = savedpc_ref[0]!!
            }
            PCRLUA
        } else { // if is a C function, call it
            val ci: CallInfo
            val n: Int
            luaD_checkstack(L, Lua.LUA_MINSTACK) // ensure minimum stack size
            ci = inc_ci(L) // now `enter' new function
            ci.func = restorestack(L, funcr)
            ci.base_ = TValue.Companion.plus(ci.func!!, 1)
            L.base_ = ci.base_
            ci.top = TValue.Companion.plus(L.top!!, Lua.LUA_MINSTACK)
            LuaLimits.lua_assert(TValue.Companion.lessEqual(ci.top!!, L.stack_last!!))
            ci.nresults = nresults
            if ((L.hookmask and Lua.LUA_MASKCALL.toByte()) != 0.toByte()) {
                luaD_callhook(L, Lua.LUA_HOOKCALL, -1)
            }
            LuaLimits.lua_unlock(L)
            n = LuaState.curr_func(L)!!.c.f!!.exec(L) // do the actual call
            LuaLimits.lua_lock(L)
            if (n < 0) { // yielding?
                PCRYIELD
            } else {
                luaD_poscall(L, TValue.Companion.minus(L.top!!, n)!!)
                PCRC
            }
        }
    }

    private fun callrethooks(L: lua_State, firstResult: TValue): TValue { //StkId - StkId
        val fr = savestack(L, firstResult) // next call may change stack  - ptrdiff_t - Int32
        luaD_callhook(L, Lua.LUA_HOOKRET, -1)
        if (LuaState.f_isLua(L.ci)) { // Lua function?
            while ((L.hookmask and Lua.LUA_MASKRET.toByte()) != 0.toByte() && L.ci!!.tailcalls-- != 0) { // tail calls
                luaD_callhook(L, Lua.LUA_HOOKTAILRET, -1)
            }
        }
        return restorestack(L, fr)!!
    }

    fun luaD_poscall(L: lua_State, firstResult: TValue): Int { //StkId
        var firstResult: TValue = firstResult
        var res: TValue //StkId
        val wanted: Int
        var i: Int
        val ci: CallInfo
        if ((L.hookmask and Lua.LUA_MASKRET.toByte()) != 0.toByte()) {
            firstResult = callrethooks(L, firstResult)
        }
        val ci_ref: Array<CallInfo?> = arrayOfNulls<CallInfo>(1)
        ci_ref[0] = L.ci
        ci = CallInfo.Companion.dec(ci_ref)!! //ref
        L.ci = ci_ref[0]
        res = ci.func!! // res == final position of 1st result
        wanted = ci.nresults
        L.base_ = CallInfo.Companion.minus(ci, 1).base_ // restore base
        L.savedpc = InstructionPtr.Companion.Assign(CallInfo.Companion.minus(ci, 1).savedpc)!! // restore savedpc
        // move results to correct place
        i = wanted
        while (i != 0 && TValue.Companion.lessThan(firstResult, L.top)) {
            LuaObject.setobjs2s(L, res, firstResult)
            res = TValue.Companion.plus(res, 1)
            firstResult = TValue.Companion.plus(firstResult, 1)
            i--
        }
        while (i-- > 0) {
            val res_ref: Array<TValue?> = arrayOfNulls<TValue>(1)
            res_ref[0] = res
            val ret: TValue = TValue.Companion.inc(res_ref)!! //ref - StkId
            res = res_ref[0]!!
            LuaObject.setnilvalue(ret)
        }
        L.top = res
        return wanted - Lua.LUA_MULTRET // 0 iff wanted == LUA_MULTRET
    }

    //
//		 ** Call a function (C or Lua). The function to be called is at *func.
//		 ** The arguments are on the stack, right after the function.
//		 ** When returns, all the results are on the stack, starting at the original
//		 ** function position.
//
//private
    fun luaD_call(L: lua_State?, func: TValue, nResults: Int) { //StkId
        if (++L!!.nCcalls >= LuaConf.LUAI_MAXCCALLS) {
            if (L!!.nCcalls == LuaConf.LUAI_MAXCCALLS) {
                LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("C stack overflow"))
            } else if (L!!.nCcalls >= LuaConf.LUAI_MAXCCALLS + (LuaConf.LUAI_MAXCCALLS shr 3)) {
                luaD_throw(L, Lua.LUA_ERRERR) // error while handing stack error
            }
        }
        if (luaD_precall(L, func, nResults) == PCRLUA) { // is a Lua function?
            LuaVM.luaV_execute(L!!, 1) // call it
        }
        L!!.nCcalls--
        LuaGC.luaC_checkGC(L)
    }

    fun resume(L: lua_State?, ud: Any?) {
        val firstArg: TValue? = ud as TValue? //StkId - StkId
        val ci: CallInfo = L!!.ci!!
        if (L!!.status.toInt() == 0) { // start coroutine?
            LuaLimits.lua_assert(ci === L.base_ci!!.get(0) && TValue.Companion.greaterThan(firstArg!!, L.base_!!))
            if (luaD_precall(L, TValue.Companion.minus(firstArg!!, 1)!!, Lua.LUA_MULTRET) != PCRLUA) {
                return
            }
        } else { // resuming from previous yield
            LuaLimits.lua_assert(L.status.toInt() == Lua.LUA_YIELD)
            L.status = 0
            if (!LuaState.f_isLua(ci)) { // `common' yield?
// finish interrupted execution of `OP_CALL'
                LuaLimits.lua_assert(
                    LuaOpCodes.GET_OPCODE(
                        CallInfo.Companion.minus(
                            ci,
                            1
                        ).savedpc!!.get(-1)
                    ) == OpCode.OP_CALL || LuaOpCodes.GET_OPCODE(
                        CallInfo.Companion.minus(
                            ci,
                            1
                        ).savedpc!!.get(-1)
                    ) == OpCode.OP_TAILCALL
                )
                if (luaD_poscall(L!!, firstArg!!) != 0) { // complete it...
                    L!!.top = L!!.ci!!.top // and correct top if not multiple results
                }
            } else { // yielded inside a hook: just continue its execution
                L.base_ = L!!.ci!!.base_
            }
        }
        LuaVM.luaV_execute(L, CallInfo.Companion.minus(L!!.ci!!, L!!.base_ci!!))
    }

    private fun resume_error(L: lua_State?, msg: CharPtr): Int {
        L!!.top = L!!.ci!!.base_
        LuaObject.setsvalue2s(L, L.top!!, LuaString.luaS_new(L, msg))
        incr_top(L)
        LuaLimits.lua_unlock(L)
        return Lua.LUA_ERRRUN
    }

    fun lua_resume(L: lua_State?, nargs: Int): Int {
        var status: Int
        LuaLimits.lua_lock(L)
        if (L!!.status.toInt() != Lua.LUA_YIELD && (L!!.status.toInt() != 0 || L!!.ci !== L!!.base_ci!!.get(0))) {
            return resume_error(L, CharPtr.Companion.toCharPtr("cannot resume non-suspended coroutine"))
        }
        if (L!!.nCcalls >= LuaConf.LUAI_MAXCCALLS) {
            return resume_error(L, CharPtr.Companion.toCharPtr("C stack overflow"))
        }
        LuaConf.luai_userstateresume(L, nargs)
        LuaLimits.lua_assert(L.errfunc == 0)
        L!!.baseCcalls = ++L.nCcalls
        status = luaD_rawrunprotected(L, resume_delegate(), TValue.Companion.minus(L.top!!, nargs))
        if (status != 0) { // error?
            L!!.status = LuaLimits.cast_byte(status) // mark thread as `dead'
            luaD_seterrorobj(L, status, L!!.top!!)
            L!!.ci!!.top = L!!.top
        } else {
            LuaLimits.lua_assert(L!!.nCcalls == L!!.baseCcalls)
            status = L!!.status.toInt()
        }
        --L.nCcalls
        LuaLimits.lua_unlock(L)
        return status
    }

    fun lua_yield(L: lua_State, nresults: Int): Int {
        LuaConf.luai_userstateyield(L, nresults)
        LuaLimits.lua_lock(L)
        if (L.nCcalls > L.baseCcalls) {
            LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("attempt to yield across metamethod/C-call boundary"))
        }
        L.base_ = TValue.Companion.minus(L.top!!, nresults) // protect stack slots below
        L.status = Lua.LUA_YIELD.toByte()
        LuaLimits.lua_unlock(L)
        return -1
    }

    fun luaD_pcall(
        L: lua_State,
        func: Pfunc,
        u: Any?,
        old_top: Int,
        ef: Int
    ): Int { //ptrdiff_t - Int32 - ptrdiff_t - Int32
        val status: Int
        val oldnCcalls: Int = L.nCcalls //ushort
        val old_ci = saveci(L, L.ci) //ptrdiff_t - Int32
        val old_allowhooks: Byte = L.allowhook //lu_byte - Byte
        val old_errfunc: Int = L.errfunc //ptrdiff_t - Int32
        L.errfunc = ef
        status = luaD_rawrunprotected(L, func, u)
        if (status != 0) { // an error occurred?
            val oldtop: TValue = restorestack(L, old_top)!! //StkId
            LuaFunc.luaF_close(L, oldtop) // close eventual pending closures
            luaD_seterrorobj(L, status, oldtop)
            L.nCcalls = oldnCcalls
            L.ci = restoreci(L, old_ci)
            L.base_ = L.ci!!.base_
            L.savedpc = InstructionPtr.Companion.Assign(L.ci!!.savedpc)!!
            L.allowhook = old_allowhooks
            restore_stack_limit(L)
        }
        L.errfunc = old_errfunc
        return status
    }

    fun f_parser(L: lua_State?, ud: Any?) {
        var i: Int
        val tf: Proto
        val cl: LuaObject.Closure
        val p = ud as SParser?
        val c: Int = LuaZIO.luaZ_lookahead(p!!.z!!)
        LuaGC.luaC_checkGC(L)
        tf = if (c == Lua.LUA_SIGNATURE.get(0).toInt()) LuaUndump.luaU_undump(
            L,
            p.z,
            p.buff,
            p.name
        ) else LuaParser.luaY_parser(L, p.z, p.buff, p.name)!!
        cl = LuaFunc.luaF_newLclosure(L, tf.nups.toInt(), LuaObject.hvalue(LuaState.gt(L)))!!
        cl.l.p = tf
        i = 0
        while (i < tf.nups) {
            // initialize eventual upvalues
            cl.l.upvals!![i] = LuaFunc.luaF_newupval(L)!!
            i++
        }
        LuaObject.setclvalue(L, L!!.top!!, cl)
        incr_top(L)
    }

    fun luaD_protectedparser(L: lua_State, z: ZIO?, name: CharPtr?): Int {
        val p = SParser()
        val status: Int
        p.z = z
        p.name = CharPtr(name!!)
        LuaZIO.luaZ_initbuffer(L, p.buff)
        status = luaD_pcall(L, f_parser_delegate(), p, savestack(L, L.top), L.errfunc)
        LuaZIO.luaZ_freebuffer(L, p.buff)
        return status
    }

    interface Pfunc {
        fun exec(L: lua_State?, ud: Any?)
    }

    /*
	 ** {======================================================
	 ** Error-recovery functions
	 ** =======================================================
	 */
    interface luai_jmpbuf {
        /*Int32*/ /*lua_Integer*/
        fun exec(b: Int)
    }

    /* chain list of long jump buffers */
    class lua_longjmp {
        var previous: lua_longjmp? = null
        var b: luai_jmpbuf? = null
        @Volatile
        var status /* error code */ = 0
    }

    class resume_delegate : Pfunc {
        override fun exec(L: lua_State?, ud: Any?) {
            resume(L, ud)
        }
    }

    /*
	 ** Execute a protected parser.
	 */
    class SParser {
        /* data to `f_parser' */
        var z: ZIO? = null
        var buff: Mbuffer = Mbuffer() /* buffer to be used by the scanner */
        var name: CharPtr? = null
    }

    class f_parser_delegate : Pfunc {
        override fun exec(L: lua_State?, ud: Any?) {
            f_parser(L, ud)
        }
    }
}