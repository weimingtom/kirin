package kirin

import kirin.CLib.CharPtr
import kirin.LuaCode.InstructionPtr
import java.lang.RuntimeException
import java.util.HashMap
import kotlin.jvm.Synchronized
import kotlin.experimental.and


//
// ** $Id: lopcodes.c,v 1.37.1.1 2007/12/27 13:02:25 roberto Exp $
// ** See Copyright Notice in lua.h
//
//using lu_byte = System.Byte;
//using Instruction = System.UInt32;
object LuaOpCodes {
    //
//		 ** size and position of opcode arguments.
//
    const val SIZE_C = 9
    const val SIZE_B = 9
    const val SIZE_Bx = SIZE_C + SIZE_B
    const val SIZE_A = 8
    const val SIZE_OP = 6
    const val POS_OP = 0
    const val POS_A = POS_OP + SIZE_OP
    const val POS_C = POS_A + SIZE_A
    const val POS_B = POS_C + SIZE_C
    const val POS_Bx = POS_C
    //
//		 ** limits for opcode arguments.
//		 ** we use (signed) int to manipulate most arguments,
//		 ** so they must fit in LUAI_BITSINT-1 bits (-1 for sign)
//
///#if SIZE_Bx < LUAI_BITSINT-1
    const val MAXARG_Bx = (1 shl SIZE_Bx) - 1
    const val MAXARG_sBx = MAXARG_Bx shr 1 // `sBx' is signed
    ///#else
//public const int MAXARG_Bx			= System.Int32.MaxValue;
//public const int MAXARG_sBx			= System.Int32.MaxValue;
///#endif
//public const uint MAXARG_A = (uint)((1 << (int)SIZE_A) -1);
//public const uint MAXARG_B = (uint)((1 << (int)SIZE_B) -1);
//public const uint MAXARG_C = (uint)((1 << (int)SIZE_C) -1);
    const val MAXARG_A = ((1 shl SIZE_A) - 1 and -0x1).toLong()
    const val MAXARG_B = ((1 shl SIZE_B) - 1 and -0x1).toLong()
    const val MAXARG_C = ((1 shl SIZE_C) - 1 and -0x1).toLong()
    // creates a mask with `n' 1 bits at position `p'
//public static int MASK1(int n, int p) { return ((~((~(Instruction)0) << n)) << p); }
    fun MASK1(n: Int, p: Int): Long { //uint
//return (uint)((~((~0) << n)) << p);
        return ((0.inv() shl n).inv() shl p and -0x1).toLong()
    }

    // creates a mask with `n' 0 bits at position `p'
    fun MASK0(n: Int, p: Int): Long { //uint
//return (uint)(~MASK1(n, p));
        return (MASK1(n, p).inv() and -0x1)
    }

    //
//		 ** the following macros help to manipulate instructions
//
    fun GET_OPCODE(i: Long): OpCode { //Instruction - UInt32
        return longToOpCode(i shr POS_OP and MASK1(SIZE_OP, 0))
    }

    fun GET_OPCODE(i: InstructionPtr): OpCode {
        return GET_OPCODE(i.get(0))
    }

    fun SET_OPCODE(i: LongArray, o: Long) { //Instruction - UInt32 - Instruction - UInt32 - ref
        i[0] = (i[0] and MASK0(
            SIZE_OP,
            POS_OP
        )) or (o shl POS_OP and MASK1(
            SIZE_OP,
            POS_OP
        )) //Instruction - UInt32
    }

    fun SET_OPCODE(i: LongArray, opcode: OpCode) { //Instruction - UInt32 - ref
        i[0] = (i[0] and MASK0(
            SIZE_OP,
            POS_OP
        )) or (opcode.getValue().toLong() shl POS_OP and MASK1(
            SIZE_OP,
            POS_OP
        )) //uint - Instruction - UInt32
    }

    fun SET_OPCODE(i: InstructionPtr, opcode: OpCode?) {
        val c_ref = LongArray(1)
        c_ref[0] = i.codes!!.get(i.pc)
        SET_OPCODE(c_ref, opcode!!) //ref
        i.codes!![i.pc] = c_ref[0]
    }

    fun GETARG_A(i: Long): Int { //Instruction - UInt32
        return (i shr POS_A and MASK1(SIZE_A, 0)).toInt()
    }

    fun GETARG_A(i: InstructionPtr): Int {
        return GETARG_A(i.get(0))
    }

    fun SETARG_A(i: InstructionPtr, u: Int) {
        i.set(
            0,
            (i.get(0) and MASK0(
                SIZE_A,
                POS_A
            ) or ((u shl POS_A and MASK1(SIZE_A, POS_A).toInt()).toLong())) as Long
        ) //Instruction - UInt32
    }

    fun GETARG_B(i: Long): Int { //Instruction - UInt32
        return (i shr POS_B and MASK1(SIZE_B, 0)).toInt()
    }

    fun GETARG_B(i: InstructionPtr): Int {
        return GETARG_B(i.get(0))
    }

    fun SETARG_B(i: InstructionPtr, b: Int) {
        i.set(
            0,
            (i.get(0) and MASK0(
                SIZE_B,
                POS_B
            ) or ((b shl POS_B and MASK1(SIZE_B, POS_B).toInt()).toLong())) as Long
        ) //Instruction - UInt32
    }

    fun GETARG_C(i: Long): Int { //Instruction - UInt32
        return (i shr POS_C and MASK1(SIZE_C, 0)).toInt()
    }

    fun GETARG_C(i: InstructionPtr): Int {
        return GETARG_C(i.get(0))
    }

    fun SETARG_C(i: InstructionPtr, b: Int) {
        i.set(
            0,
            (i.get(0) and MASK0(
                SIZE_C,
                POS_C
            ) or ((b shl POS_C and MASK1(SIZE_C, POS_C).toInt()).toLong())) as Long
        ) //Instruction - UInt32
    }

    fun GETARG_Bx(i: Long): Int { //Instruction - UInt32
        return (i shr POS_Bx and MASK1(SIZE_Bx, 0)).toInt()
    }

    fun GETARG_Bx(i: InstructionPtr): Int {
        return GETARG_Bx(i.get(0))
    }

    fun SETARG_Bx(i: InstructionPtr, b: Int) {
        i.set(
            0,
            (i.get(0) and MASK0(
                SIZE_Bx,
                POS_Bx
            ) or ((b shl POS_Bx and MASK1(SIZE_Bx, POS_Bx).toInt()).toLong())) as Long
        ) //Instruction - UInt32
    }

    fun GETARG_sBx(i: Long): Int { //Instruction - UInt32
        return GETARG_Bx(i) - MAXARG_sBx
    }

    fun GETARG_sBx(i: InstructionPtr): Int {
        return GETARG_sBx(i.get(0))
    }

    fun SETARG_sBx(i: InstructionPtr, b: Int) {
        SETARG_Bx(i, b + MAXARG_sBx)
    }

    //FIXME:long
    fun CREATE_ABC(o: OpCode, a: Int, b: Int, c: Int): Int {
        return (o.getValue() shl POS_OP or (a shl POS_A) or (b shl POS_B) or (c shl POS_C))
    }

    //FIXME:long
    fun CREATE_ABx(o: OpCode, a: Int, bc: Int): Int {
        val result =
            (o.getValue() shl POS_OP or (a shl POS_A) or (bc shl POS_Bx))
        return (o.getValue() shl POS_OP or (a shl POS_A) or (bc shl POS_Bx))
    }

    //
//		 ** Macros to operate RK indices
//
// this bit 1 means constant (0 means register)
    const val BITRK = 1 shl SIZE_B - 1

    // test whether value is a constant
    fun ISK(x: Int): Int {
        return x and BITRK
    }

    // gets the index of the constant
    fun INDEXK(r: Int): Int {
        return r and BITRK.inv()
    }

    const val MAXINDEXRK = BITRK - 1
    // code a constant index as a RK value
    fun RKASK(x: Int): Int {
        return x or BITRK
    }

    //
//		 ** invalid register that fits in 8 bits
//
    const val NO_REG = MAXARG_A.toInt()

    fun opCodeToLong(code: OpCode?): Long {
        when (code) {
            OpCode.OP_MOVE -> return 0
            OpCode.OP_LOADK -> return 1
            OpCode.OP_LOADBOOL -> return 2
            OpCode.OP_LOADNIL -> return 3
            OpCode.OP_GETUPVAL -> return 4
            OpCode.OP_GETGLOBAL -> return 5
            OpCode.OP_GETTABLE -> return 6
            OpCode.OP_SETGLOBAL -> return 7
            OpCode.OP_SETUPVAL -> return 8
            OpCode.OP_SETTABLE -> return 9
            OpCode.OP_NEWTABLE -> return 10
            OpCode.OP_SELF -> return 11
            OpCode.OP_ADD -> return 12
            OpCode.OP_SUB -> return 13
            OpCode.OP_MUL -> return 14
            OpCode.OP_DIV -> return 15
            OpCode.OP_MOD -> return 16
            OpCode.OP_POW -> return 17
            OpCode.OP_UNM -> return 18
            OpCode.OP_NOT -> return 19
            OpCode.OP_LEN -> return 20
            OpCode.OP_CONCAT -> return 21
            OpCode.OP_JMP -> return 22
            OpCode.OP_EQ -> return 23
            OpCode.OP_LT -> return 24
            OpCode.OP_LE -> return 25
            OpCode.OP_TEST -> return 26
            OpCode.OP_TESTSET -> return 27
            OpCode.OP_CALL -> return 28
            OpCode.OP_TAILCALL -> return 29
            OpCode.OP_RETURN -> return 30
            OpCode.OP_FORLOOP -> return 31
            OpCode.OP_FORPREP -> return 32
            OpCode.OP_TFORLOOP -> return 33
            OpCode.OP_SETLIST -> return 34
            OpCode.OP_CLOSE -> return 35
            OpCode.OP_CLOSURE -> return 36
            OpCode.OP_VARARG -> return 37
        }
        throw RuntimeException("OpCode error")
    }

    fun longToOpCode(code: Long): OpCode {
        when (code.toInt()) {
            0 -> return OpCode.OP_MOVE
            1 -> return OpCode.OP_LOADK
            2 -> return OpCode.OP_LOADBOOL
            3 -> return OpCode.OP_LOADNIL
            4 -> return OpCode.OP_GETUPVAL
            5 -> return OpCode.OP_GETGLOBAL
            6 -> return OpCode.OP_GETTABLE
            7 -> return OpCode.OP_SETGLOBAL
            8 -> return OpCode.OP_SETUPVAL
            9 -> return OpCode.OP_SETTABLE
            10 -> return OpCode.OP_NEWTABLE
            11 -> return OpCode.OP_SELF
            12 -> return OpCode.OP_ADD
            13 -> return OpCode.OP_SUB
            14 -> return OpCode.OP_MUL
            15 -> return OpCode.OP_DIV
            16 -> return OpCode.OP_MOD
            17 -> return OpCode.OP_POW
            18 -> return OpCode.OP_UNM
            19 -> return OpCode.OP_NOT
            20 -> return OpCode.OP_LEN
            21 -> return OpCode.OP_CONCAT
            22 -> return OpCode.OP_JMP
            23 -> return OpCode.OP_EQ
            24 -> return OpCode.OP_LT
            25 -> return OpCode.OP_LE
            26 -> return OpCode.OP_TEST
            27 -> return OpCode.OP_TESTSET
            28 -> return OpCode.OP_CALL
            29 -> return OpCode.OP_TAILCALL
            30 -> return OpCode.OP_RETURN
            31 -> return OpCode.OP_FORLOOP
            32 -> return OpCode.OP_FORPREP
            33 -> return OpCode.OP_TFORLOOP
            34 -> return OpCode.OP_SETLIST
            35 -> return OpCode.OP_CLOSE
            36 -> return OpCode.OP_CLOSURE
            37 -> return OpCode.OP_VARARG
        }
        throw RuntimeException("OpCode error")
    }

    //
//		 ** grep "ORDER OP" if you change these enums
//
    fun getOpMode(m: OpCode): OpMode {
        return when ((luaP_opmodes[m.getValue()] and 3).toInt()) {
            0 -> OpMode.iABC
            1 -> OpMode.iABx
            2 -> OpMode.iAsBx
            else -> OpMode.iABC
        }
    }

    fun getBMode(m: OpCode): OpArgMask {
        return when (((luaP_opmodes[m.getValue()] as Long) shr 4 and 3).toInt()) {
            0 -> OpArgMask.OpArgN
            1 -> OpArgMask.OpArgU
            2 -> OpArgMask.OpArgR
            3 -> OpArgMask.OpArgK
            else -> OpArgMask.OpArgN
        }
    }

    fun getCMode(m: OpCode): OpArgMask {
        return when (((luaP_opmodes[m.getValue()] as Long) shr 2 and 3).toInt()) {
            0 -> OpArgMask.OpArgN
            1 -> OpArgMask.OpArgU
            2 -> OpArgMask.OpArgR
            3 -> OpArgMask.OpArgK
            else -> OpArgMask.OpArgN
        }
    }

    fun testAMode(m: OpCode): Int {
        return (luaP_opmodes[m.getValue()] and (1 shl 6)).toInt()
    }

    fun testTMode(m: OpCode): Int {
        return (luaP_opmodes[m.getValue()] and ((1 shl 7).toByte())).toInt()
    }

    // number of list items to accumulate before a SETLIST instruction
    const val LFIELDS_PER_FLUSH = 50
    // ORDER OP
    val luaP_opnames: Array<CharPtr> = arrayOf<CharPtr>(
        CharPtr.Companion.toCharPtr("MOVE"),
        CharPtr.Companion.toCharPtr("LOADK"),
        CharPtr.Companion.toCharPtr("LOADBOOL"),
        CharPtr.Companion.toCharPtr("LOADNIL"),
        CharPtr.Companion.toCharPtr("GETUPVAL"),
        CharPtr.Companion.toCharPtr("GETGLOBAL"),
        CharPtr.Companion.toCharPtr("GETTABLE"),
        CharPtr.Companion.toCharPtr("SETGLOBAL"),
        CharPtr.Companion.toCharPtr("SETUPVAL"),
        CharPtr.Companion.toCharPtr("SETTABLE"),
        CharPtr.Companion.toCharPtr("NEWTABLE"),
        CharPtr.Companion.toCharPtr("SELF"),
        CharPtr.Companion.toCharPtr("ADD"),
        CharPtr.Companion.toCharPtr("SUB"),
        CharPtr.Companion.toCharPtr("MUL"),
        CharPtr.Companion.toCharPtr("DIV"),
        CharPtr.Companion.toCharPtr("MOD"),
        CharPtr.Companion.toCharPtr("POW"),
        CharPtr.Companion.toCharPtr("UNM"),
        CharPtr.Companion.toCharPtr("NOT"),
        CharPtr.Companion.toCharPtr("LEN"),
        CharPtr.Companion.toCharPtr("CONCAT"),
        CharPtr.Companion.toCharPtr("JMP"),
        CharPtr.Companion.toCharPtr("EQ"),
        CharPtr.Companion.toCharPtr("LT"),
        CharPtr.Companion.toCharPtr("LE"),
        CharPtr.Companion.toCharPtr("TEST"),
        CharPtr.Companion.toCharPtr("TESTSET"),
        CharPtr.Companion.toCharPtr("CALL"),
        CharPtr.Companion.toCharPtr("TAILCALL"),
        CharPtr.Companion.toCharPtr("RETURN"),
        CharPtr.Companion.toCharPtr("FORLOOP"),
        CharPtr.Companion.toCharPtr("FORPREP"),
        CharPtr.Companion.toCharPtr("TFORLOOP"),
        CharPtr.Companion.toCharPtr("SETLIST"),
        CharPtr.Companion.toCharPtr("CLOSE"),
        CharPtr.Companion.toCharPtr("CLOSURE"),
        CharPtr.Companion.toCharPtr("VARARG")
    )

    private fun opmode(
        t: Byte,
        a: Byte,
        b: OpArgMask,
        c: OpArgMask,
        m: OpMode
    ): Byte { //lu_byte - lu_byte - lu_byte
        var bValue = 0
        var cValue = 0
        var mValue = 0
        bValue = when (b) {
            OpArgMask.OpArgN -> 0
            OpArgMask.OpArgU -> 1
            OpArgMask.OpArgR -> 2
            OpArgMask.OpArgK -> 3
        }
        cValue = when (c) {
            OpArgMask.OpArgN -> 0
            OpArgMask.OpArgU -> 1
            OpArgMask.OpArgR -> 2
            OpArgMask.OpArgK -> 3
        }
        mValue = when (m) {
            OpMode.iABC -> 0
            OpMode.iABx -> 1
            OpMode.iAsBx -> 2
        }
        return ((t.toLong() shl 7 or (a.toLong() shl 6) or (bValue.toByte().toLong() shl 4) or (cValue.toByte().toLong() shl 2) or mValue.toByte().toLong()).toByte()) //lu_byte - lu_byte - lu_byte - lu_byte
    }

    //       T  A    B       C     mode		   opcode
//lu_byte[]
    private val luaP_opmodes = byteArrayOf(
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgN, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgN, OpMode.iABx),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgU, OpArgMask.OpArgU, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgN, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgU, OpArgMask.OpArgN, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgN, OpMode.iABx),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgK, OpMode.iABC),
        opmode(0.toByte(), 0.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgN, OpMode.iABx),
        opmode(0.toByte(), 0.toByte(), OpArgMask.OpArgU, OpArgMask.OpArgN, OpMode.iABC),
        opmode(0.toByte(), 0.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgK, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgU, OpArgMask.OpArgU, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgK, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgK, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgK, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgK, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgK, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgK, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgK, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgN, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgN, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgN, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgR, OpMode.iABC),
        opmode(0.toByte(), 0.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgN, OpMode.iAsBx),
        opmode(1.toByte(), 0.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgK, OpMode.iABC),
        opmode(1.toByte(), 0.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgK, OpMode.iABC),
        opmode(1.toByte(), 0.toByte(), OpArgMask.OpArgK, OpArgMask.OpArgK, OpMode.iABC),
        opmode(1.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgU, OpMode.iABC),
        opmode(1.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgU, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgU, OpArgMask.OpArgU, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgU, OpArgMask.OpArgU, OpMode.iABC),
        opmode(0.toByte(), 0.toByte(), OpArgMask.OpArgU, OpArgMask.OpArgN, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgN, OpMode.iAsBx),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgR, OpArgMask.OpArgN, OpMode.iAsBx),
        opmode(1.toByte(), 0.toByte(), OpArgMask.OpArgN, OpArgMask.OpArgU, OpMode.iABC),
        opmode(0.toByte(), 0.toByte(), OpArgMask.OpArgU, OpArgMask.OpArgU, OpMode.iABC),
        opmode(0.toByte(), 0.toByte(), OpArgMask.OpArgN, OpArgMask.OpArgN, OpMode.iABC),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgU, OpArgMask.OpArgN, OpMode.iABx),
        opmode(0.toByte(), 1.toByte(), OpArgMask.OpArgU, OpArgMask.OpArgN, OpMode.iABC)
    )
    val NUM_OPCODES = OpCode.OP_VARARG.getValue()

    //        ===========================================================================
//		  We assume that instructions are unsigned numbers.
//		  All instructions have an opcode in the first 6 bits.
//		  Instructions can have the following fields:
//			`A' : 8 bits
//			`B' : 9 bits
//			`C' : 9 bits
//			`Bx' : 18 bits (`B' and `C' together)
//			`sBx' : signed Bx
//
//		  A signed argument is represented in excess K; that is, the number
//		  value is the unsigned value minus K. K is exactly the maximum value
//		  for that argument (so that -max is represented by 0, and +max is
//		  represented by 2*max), which is half the maximum for the corresponding
//		  unsigned argument.
//		===========================================================================
    enum class OpMode {
        /* basic instruction format */
        iABC,
        iABx, iAsBx;

        fun getValue(): Int {
            return ordinal
        }

        companion object {
            fun forValue(value: Int): OpMode {
                return LuaOpCodes.OpMode.values()[value]
            }
        }
    }

    //
//		 ** R(x) - register
//		 ** Kst(x) - constant (in constant table)
//		 ** RK(x) == if ISK(x) then Kst(INDEXK(x)) else R(x)
//
    enum class OpCode(/*	A B	R(A), R(A+1), ..., R(A+B-1) = vararg		*/
        private val intValue: Int
    ) {
        /*----------------------------------------------------------------------
		name		args	description
		------------------------------------------------------------------------*/
        OP_MOVE(0),  /*	A B	R(A) := R(B)					*/
        OP_LOADK(1),  /*	A Bx	R(A) := Kst(Bx)					*/
        OP_LOADBOOL(2),  /*	A B C	R(A) := (Bool)B; if (C) pc++			*/
        OP_LOADNIL(3),  /*	A B	R(A) := ... := R(B) := nil			*/
        OP_GETUPVAL(4),  /*	A B	R(A) := UpValue[B]				*/
        OP_GETGLOBAL(5),  /*	A Bx	R(A) := Gbl[Kst(Bx)]				*/
        OP_GETTABLE(6),  /*	A B C	R(A) := R(B)[RK(C)]				*/
        OP_SETGLOBAL(7),  /*	A Bx	Gbl[Kst(Bx)] := R(A)				*/
        OP_SETUPVAL(8),  /*	A B	UpValue[B] := R(A)				*/
        OP_SETTABLE(9),  /*	A B C	R(A)[RK(B)] := RK(C)				*/
        OP_NEWTABLE(10),  /*	A B C	R(A) := {} (size = B,C)				*/
        OP_SELF(11),  /*	A B C	R(A+1) := R(B); R(A) := R(B)[RK(C)]		*/
        OP_ADD(12),  /*	A B C	R(A) := RK(B) + RK(C)				*/
        OP_SUB(13),  /*	A B C	R(A) := RK(B) - RK(C)				*/
        OP_MUL(14),  /*	A B C	R(A) := RK(B) * RK(C)				*/
        OP_DIV(15),  /*	A B C	R(A) := RK(B) / RK(C)				*/
        OP_MOD(16),  /*	A B C	R(A) := RK(B) % RK(C)				*/
        OP_POW(17),  /*	A B C	R(A) := RK(B) ^ RK(C)				*/
        OP_UNM(18),  /*	A B	R(A) := -R(B)					*/
        OP_NOT(19),  /*	A B	R(A) := not R(B)				*/
        OP_LEN(20),  /*	A B	R(A) := length of R(B)				*/
        OP_CONCAT(21),  /*	A B C	R(A) := R(B).. ... ..R(C)			*/
        OP_JMP(22),  /*	sBx	pc+=sBx					*/
        OP_EQ(23),  /*	A B C	if ((RK(B) == RK(C)) ~= A) then pc++		*/
        OP_LT(24),  /*	A B C	if ((RK(B) <  RK(C)) ~= A) then pc++  		*/
        OP_LE(25),  /*	A B C	if ((RK(B) <= RK(C)) ~= A) then pc++  		*/
        OP_TEST(26),  /*	A C	if not (R(A) <=> C) then pc++			*/
        OP_TESTSET(27),  /*	A B C	if (R(B) <=> C) then R(A) := R(B) else pc++	*/
        OP_CALL(28),  /*	A B C	R(A), ... ,R(A+C-2) := R(A)(R(A+1), ... ,R(A+B-1)) */
        OP_TAILCALL(29),  /*	A B C	return R(A)(R(A+1), ... ,R(A+B-1))		*/
        OP_RETURN(30),  /*	A B	return R(A), ... ,R(A+B-2)	(see note)	*/
        OP_FORLOOP(31),  /*	A sBx	R(A)+=R(A+2);
					if R(A) <?= R(A+1) then { pc+=sBx; R(A+3)=R(A) }*/
        OP_FORPREP(32),  /*	A sBx	R(A)-=R(A+2); pc+=sBx				*/
        OP_TFORLOOP(33),  /*	A C	R(A+3), ... ,R(A+2+C) := R(A)(R(A+1), R(A+2));
								if R(A+3) ~= nil then R(A+2)=R(A+3) else pc++	*/
        OP_SETLIST(34),  /*	A B C	R(A)[(C-1)*FPF+i] := R(A+i), 1 <= i <= B	*/
        OP_CLOSE(35),  /*	A 	close all variables in the stack up to (>=) R(A)*/
        OP_CLOSURE(36),  /*	A Bx	R(A) := closure(KPROTO[Bx], R(A), ... ,R(A+n))	*/
        OP_VARARG(37);

        fun getValue(): Int {
            return intValue
        }

        companion object {
            private var mappings: HashMap<Int, OpCode>? = null
            @Synchronized
            private fun getMappings(): HashMap<Int, OpCode>? {
                if (mappings == null) {
                    mappings = HashMap()
                }
                return mappings
            }

            fun forValue(value: Int): OpCode? {
                return getMappings()!![value]
            }
        }

        init {
            OpCode.getMappings()!![intValue] = this
        }
    }

    /*===========================================================================
    Notes:
    (*) In OP_CALL, if (B == 0) then B = top. C is the number of returns - 1,
  	  and can be 0: OP_CALL then sets `top' to last_result+1, so
  	  next open instruction (OP_CALL, OP_RETURN, OP_SETLIST) may use `top'.

    (*) In OP_VARARG, if (B == 0) then use actual number of varargs and
  	  set top (like in OP_CALL with C == 0).

    (*) In OP_RETURN, if (B == 0) then return up to `top'

    (*) In OP_SETLIST, if (B == 0) then B = `top';
  	  if (C == 0) then next `instruction' is real C

    (*) For comparisons, A specifies what condition the test should accept
  	  (true or false).

    (*) All `skips' (pc++) assume that next instruction is a jump
  	===========================================================================*/
/*
	 ** masks for instruction properties. The format is:
	 ** bits 0-1: op mode
	 ** bits 2-3: C arg mode
	 ** bits 4-5: B arg mode
	 ** bit 6: instruction set register A
	 ** bit 7: operator is a test
	 */
    enum class OpArgMask {
        OpArgN,  /* argument is not used */
        OpArgU,  /* argument is used */
        OpArgR,  /* argument is a register or a jump offset */
        OpArgK;

        /* argument is a constant or register/constant */
        fun getValue(): Int {
            return ordinal
        }

        companion object {
            fun forValue(value: Int): OpArgMask {
                return LuaOpCodes.OpArgMask.values()[value]
            }
        }
    }
}