package kirin
import kirin.CLib.CharPtr
import kirin.LuaObject.TValue
import kirin.LuaObject.Proto
import kirin.LuaOpCodes.OpCode
import kirin.LuaObject.TString
import kirin.LuaOpCodes.OpMode
import kirin.LuaOpCodes.OpArgMask

//
// ** $Id: print.c,v 1.55a 2006/05/31 13:30:05 lhf Exp $
// ** print bytecodes
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
//using Instruction = System.UInt32;
object LuaPrint {
    fun luaU_print(f: Proto?, full: Int) {
        PrintFunction(f, full)
    }

    ///#define Sizeof(x)	((int)sizeof(x))
///#define VOID(p)		((const void*)(p))
    fun PrintString(ts: TString?) {
        val s: CharPtr? = LuaObject.getstr(ts)
        var i: Int
        val n: Int = ts!!.getTsv().len //uint
        CLib.putchar('"')
        i = 0
        while (i < n) {
            val c: Char = s!!.get(i).toInt() as Char
            when (c) {
                '"' -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("\\\""))
                }
                '\\' -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("\\\\"))
                }
                '\u0007' -> {
                    //'\a': //FIXME:
                    CLib.printf(CharPtr.Companion.toCharPtr("\\a"))
                }
                '\b' -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("\\b"))
                }
                '\u000C' -> {  /*'\f'*/
                    CLib.printf(CharPtr.Companion.toCharPtr("\\f"))
                }
                '\n' -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("\\n"))
                }
                '\r' -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("\\r"))
                }
                '\t' -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("\\t"))
                }
                '\u000B' -> {
                    //case '\v': //FIXME:
                    CLib.printf(CharPtr.Companion.toCharPtr("\\v"))
                }
                else -> {
                    if (CLib.isprint(c.toByte())) {
                        CLib.putchar(c)
                    } else {
                        CLib.printf(CharPtr.Companion.toCharPtr("\\%03u"), c.toByte())
                    }
                }
            }
            i++
        }
        CLib.putchar('"')
    }

    private fun PrintConstant(f: Proto?, i: Int) { //const
        val o: TValue = f!!.k!!.get(i)!!
        when (LuaObject.ttype(o)) {
            Lua.LUA_TNIL -> {
                CLib.printf(CharPtr.Companion.toCharPtr("nil"))
            }
            Lua.LUA_TBOOLEAN -> {
                CLib.printf(
                    if (LuaObject.bvalue(o) != 0) CharPtr.Companion.toCharPtr("true") else CharPtr.Companion.toCharPtr(
                        "false"
                    )
                )
            }
            Lua.LUA_TNUMBER -> {
                CLib.printf(CharPtr.Companion.toCharPtr(LuaConf.LUA_NUMBER_FMT), LuaObject.nvalue(o))
            }
            Lua.LUA_TSTRING -> {
                PrintString(LuaObject.rawtsvalue(o))
            }
            else -> {
                // cannot happen
                CLib.printf(CharPtr.Companion.toCharPtr("? type=%d"), LuaObject.ttype(o))
            }
        }
    }

    private fun PrintCode(f: Proto?) {
        val code: LongArray = f!!.code!! //Instruction[] - UInt32[]
        var pc: Int
        val n: Int = f.sizecode
        pc = 0
        while (pc < n) {
            val i: Long = f.code!!.get(pc) //Instruction - UInt32
            val o: OpCode = LuaOpCodes.GET_OPCODE(i)
            val a: Int = LuaOpCodes.GETARG_A(i)
            val b: Int = LuaOpCodes.GETARG_B(i)
            val c: Int = LuaOpCodes.GETARG_C(i)
            val bx: Int = LuaOpCodes.GETARG_Bx(i)
            val sbx: Int = LuaOpCodes.GETARG_sBx(i)
            val line: Int = LuaDebug.getline(f, pc)
            CLib.printf(CharPtr.Companion.toCharPtr("\t%d\t"), pc + 1)
            if (line > 0) {
                CLib.printf(CharPtr.Companion.toCharPtr("[%d]\t"), line)
            } else {
                CLib.printf(CharPtr.Companion.toCharPtr("[-]\t"))
            }
            CLib.printf(CharPtr.Companion.toCharPtr("%-9s\t"), LuaOpCodes.luaP_opnames.get(o.getValue()))
            when (LuaOpCodes.getOpMode(o)) {
                OpMode.iABC -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("%d"), a)
                    if (LuaOpCodes.getBMode(o) != OpArgMask.OpArgN) {
                        CLib.printf(
                            CharPtr.Companion.toCharPtr(" %d"),
                            if (LuaOpCodes.ISK(b) != 0) -1 - LuaOpCodes.INDEXK(b) else b
                        )
                    }
                    if (LuaOpCodes.getCMode(o) != OpArgMask.OpArgN) {
                        CLib.printf(
                            CharPtr.Companion.toCharPtr(" %d"),
                            if (LuaOpCodes.ISK(c) != 0) -1 - LuaOpCodes.INDEXK(c) else c
                        )
                    }
                }
                OpMode.iABx -> {
                    if (LuaOpCodes.getBMode(o) == OpArgMask.OpArgK) {
                        CLib.printf(CharPtr.Companion.toCharPtr("%d %d"), a, -1 - bx)
                    } else {
                        CLib.printf(CharPtr.Companion.toCharPtr("%d %d"), a, bx)
                    }
                }
                OpMode.iAsBx -> if (o == OpCode.OP_JMP) {
                    CLib.printf(CharPtr.Companion.toCharPtr("%d"), sbx)
                } else {
                    CLib.printf(CharPtr.Companion.toCharPtr("%d %d"), a, sbx)
                }
            }
            when (o) {
                OpCode.OP_LOADK -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("\t; "))
                    PrintConstant(f, bx)
                }
                OpCode.OP_GETUPVAL, OpCode.OP_SETUPVAL -> {
                    CLib.printf(
                        CharPtr.Companion.toCharPtr("\t; %s"),
                        if (f.sizeupvalues > 0) LuaObject.getstr(f.upvalues!!.get(b)) else CharPtr.Companion.toCharPtr("-")
                    )
                }
                OpCode.OP_GETGLOBAL, OpCode.OP_SETGLOBAL -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("\t; %s"), LuaObject.svalue(f.k!!.get(bx)!!))
                }
                OpCode.OP_GETTABLE, OpCode.OP_SELF -> {
                    if (LuaOpCodes.ISK(c) != 0) {
                        CLib.printf(CharPtr.Companion.toCharPtr("\t; "))
                        PrintConstant(f, LuaOpCodes.INDEXK(c))
                    }
                }
                OpCode.OP_SETTABLE, OpCode.OP_ADD, OpCode.OP_SUB, OpCode.OP_MUL, OpCode.OP_DIV, OpCode.OP_POW, OpCode.OP_EQ, OpCode.OP_LT, OpCode.OP_LE -> {
                    if (LuaOpCodes.ISK(b) != 0 || LuaOpCodes.ISK(c) != 0) {
                        CLib.printf(CharPtr.Companion.toCharPtr("\t; "))
                        if (LuaOpCodes.ISK(b) != 0) {
                            PrintConstant(f, LuaOpCodes.INDEXK(b))
                        } else {
                            CLib.printf(CharPtr.Companion.toCharPtr("-"))
                        }
                        CLib.printf(CharPtr.Companion.toCharPtr(" "))
                        if (LuaOpCodes.ISK(c) != 0) {
                            PrintConstant(f, LuaOpCodes.INDEXK(c))
                        } else {
                            CLib.printf(CharPtr.Companion.toCharPtr("-"))
                        }
                    }
                }
                OpCode.OP_JMP, OpCode.OP_FORLOOP, OpCode.OP_FORPREP -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("\t; to %d"), sbx + pc + 2)
                }
                OpCode.OP_CLOSURE -> {
                    CLib.printf(CharPtr.Companion.toCharPtr("\t; %p"), CLib.VOID(f.p!!.get(bx)))
                }
                OpCode.OP_SETLIST -> {
                    if (c == 0) {
                        CLib.printf(CharPtr.Companion.toCharPtr("\t; %d"), code[++pc].toInt())
                    } else {
                        CLib.printf(CharPtr.Companion.toCharPtr("\t; %d"), c)
                    }
                }
                else -> {
                }
            }
            CLib.printf(CharPtr.Companion.toCharPtr("\n"))
            pc++
        }
    }

    fun SS(x: Int): String {
        return if (x == 1) "" else "s"
    }

    ///#define S(x)	x,SS(x)
    private fun PrintHeader(f: Proto?) {
        var s: CharPtr? = LuaObject.getstr(f!!.source)
        s = if (s!!.get(0) == '@' || s!!.get(0) == '=') {
            s!!.next()
        } else if (s!!.get(0) == Lua.LUA_SIGNATURE.get(0)) {
            CharPtr.Companion.toCharPtr("(bstring)")
        } else {
            CharPtr.Companion.toCharPtr("(string)")
        }
        CLib.printf(
            CharPtr.Companion.toCharPtr("\n%s <%s:%d,%d> (%d Instruction%s, %d bytes at %p)\n"),
            if (f!!.linedefined == 0) "main" else "function",
            s,
            f!!.linedefined,
            f!!.lastlinedefined,
            f!!.sizecode,
            SS(f!!.sizecode),
            f!!.sizecode * CLib.GetUnmanagedSize(ClassType(ClassType.Companion.TYPE_LONG)),
            CLib.VOID(f)
        )
        //typeof(long/*UInt32*//*Instruction*/)
        CLib.printf(
            CharPtr.Companion.toCharPtr("%d%s param%s, %d slot%s, %d upvalue%s, "),
            f.numparams,
            if (f.is_vararg.toInt() != 0) "+" else "",
            SS(f.numparams.toInt()),
            f.maxstacksize,
            SS(f.maxstacksize.toInt()),
            f.nups,
            SS(f.nups.toInt())
        )
        CLib.printf(
            CharPtr.Companion.toCharPtr("%d local%s, %d constant%s, %d function%s\n"),
            f.sizelocvars,
            SS(f.sizelocvars),
            f.sizek,
            SS(f.sizek),
            f.sizep,
            SS(f.sizep)
        )
    }

    private fun PrintConstants(f: Proto?) {
        var i: Int
        val n: Int = f!!.sizek
        CLib.printf(CharPtr.Companion.toCharPtr("constants (%d) for %p:\n"), n, CLib.VOID(f))
        i = 0
        while (i < n) {
            CLib.printf(CharPtr.Companion.toCharPtr("\t%d\t"), i + 1)
            PrintConstant(f, i)
            CLib.printf(CharPtr.Companion.toCharPtr("\n"))
            i++
        }
    }

    private fun PrintLocals(f: Proto?) {
        var i: Int
        val n: Int = f!!.sizelocvars
        CLib.printf(CharPtr.Companion.toCharPtr("locals (%d) for %p:\n"), n, CLib.VOID(f))
        i = 0
        while (i < n) {
            CLib.printf(
                CharPtr.Companion.toCharPtr("\t%d\t%s\t%d\t%d\n"),
                i,
                LuaObject.getstr(f!!.locvars!!.get(i)!!.varname),
                f!!.locvars!!.get(i)!!.startpc + 1,
                f!!.locvars!!.get(i)!!.endpc + 1
            )
            i++
        }
    }

    private fun PrintUpvalues(f: Proto?) {
        var i: Int
        val n: Int = f!!.sizeupvalues
        CLib.printf(CharPtr.Companion.toCharPtr("upvalues (%d) for %p:\n"), n, CLib.VOID(f))
        if (f!!.upvalues == null) {
            return
        }
        i = 0
        while (i < n) {
            CLib.printf(CharPtr.Companion.toCharPtr("\t%d\t%s\n"), i, LuaObject.getstr(f!!.upvalues!!.get(i)))
            i++
        }
    }

    fun PrintFunction(f: Proto?, full: Int) {
        var i: Int
        val n: Int = f!!.sizep
        PrintHeader(f)
        PrintCode(f)
        if (full != 0) {
            PrintConstants(f)
            PrintLocals(f)
            PrintUpvalues(f)
        }
        i = 0
        while (i < n) {
            PrintFunction(f!!.p!!.get(i), full)
            i++
        }
    }
}