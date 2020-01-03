package kirin
import kirin.LuaState.lua_State

//
// ** $Id: llimits.h,v 1.69.1.1 2007/12/27 13:02:25 roberto Exp $
// ** Limits, basic types, and some other `installation-dependent' definitions
// ** See Copyright Notice in lua.h
//
//
// ** #define lua_assert
//
//using lu_int32 = System.UInt32;
//using lu_mem = System.UInt32;
//using l_mem = System.Int32;
//using lu_byte = System.Byte;
//using l_uacNumber = System.Double;
//using lua_Number = System.Double;
//using Instruction = System.UInt32;
object LuaLimits {
    //typedef LUAI_UINT32 lu_int32;
//typedef LUAI_UMEM lu_mem;
//typedef LUAI_MEM l_mem;
// chars used as small naturals (so that `char' is reserved for characters)
//typedef unsigned char lu_byte;
    const val MAX_SIZET = Int.MAX_VALUE - 2 //uint - uint
    const val MAX_LUMEM = Int.MAX_VALUE - 2 //UInt32 - lu_mem - lu_mem - UInt32
    const val MAX_INT = Int.MAX_VALUE - 2 // maximum value of an int (-2 for safety)  - Int32
    //
//		 ** conversion of pointer to integer
//		 ** this is for hashing only; there is no problem if the integer
//		 ** cannot hold the whole pointer value
//
///#define IntPoint(p)  ((uint)(lu_mem)(p))
// type to ensure maximum alignment
//typedef LUAI_USER_ALIGNMENT_T L_Umaxalign;
// result of a `usual argument conversion' over lua_Number
//typedef LUAI_UACNUMBER l_uacNumber;
// internal assertions for in-house debugging
///#if lua_assert
//		[Conditional("DEBUG")]
//		public static void lua_assert(bool c)
//		{
//			Debug.Assert(c);
//		}
//
//		[Conditional("DEBUG")]
//		public static void lua_assert(int c)
//		{
//			Debug.Assert(c != 0);
//		}
//
//		public static object check_exp(bool c, object e)
//		{
//			lua_assert(c);
//			return e;
//		}
//		public static object check_exp(int c, object e)
//		{
//			lua_assert(c != 0);
//			return e;
//		}
///#else
//[Conditional("DEBUG")]
    fun lua_assert(c: Boolean) {}

    //[Conditional("DEBUG")]
    fun lua_assert(c: Int) {}

    fun check_exp(c: Boolean, e: Any?): Any? {
        return e
    }

    fun check_exp(c: Int, e: Any): Any {
        return e
    }

    ///#endif
//[Conditional("DEBUG")]
    fun api_check(o: Any?, e: Boolean) {
        lua_assert(e)
    }

    fun api_check(o: Any?, e: Int) {
        lua_assert(e != 0)
    }

    ///#define UNUSED(x)	((void)(x))	/* to avoid warnings */
    fun cast_byte(i: Int): Byte { //lu_byte
        return i.toByte() //lu_byte
    }

    fun cast_byte(i: Long): Byte { //lu_byte
        return i.toInt().toByte() //lu_byte
    }

    fun cast_byte(i: Boolean): Byte { //lu_byte
        return if (i) 1.toByte() else 0.toByte() //lu_byte - lu_byte
    }

    fun cast_byte(i: Double): Byte { //lua_Number - lu_byte
        return i.toByte() //lu_byte
    }

    //public static Byte/*lu_byte*/ cast_byte(object i)
//{
//	return (Byte/*lu_byte*/)(int)(Int32)(i);
//}
    fun cast_int(i: Int): Int {
        return i
    }

    fun cast_int(i: Long): Int {
        return i.toInt()
    }

    fun cast_int(i: Boolean): Int {
        return if (i) 1 else 0
    }

    fun cast_int(i: Double): Int { //lua_Number
        return i.toInt()
    }

    fun cast_int_instruction(i: Long): Int { //Instruction - UInt32
        return ClassType.Companion.ConvertToInt32(i)
    }

    fun cast_int(i: Any): Int {
        ClassType.Companion.Assert(false, "Can't convert int.")
        return ClassType.Companion.ConvertToInt32_object(i)
    }

    fun cast_num(i: Int): Double { //lua_Number
        return i.toDouble() //lua_Number
    }

    fun cast_num(i: Long): Double { //lua_Number
        return i.toDouble() //lua_Number
    }

    fun cast_num(i: Boolean): Double { //lua_Number
        return if (i) 1.toDouble() else 0.toDouble() //lua_Number - lua_Number
    }

    fun cast_num(i: Any): Double { //lua_Number
//FIXME:
        ClassType.Companion.Assert(false, "Can't convert number.")
        return ClassType.Companion.ConvertToSingle(i)
    }

    //
//		 ** type for virtual-machine instructions
//		 ** must be an unsigned with (at least) 4 bytes (see details in lopcodes.h)
//
//typedef lu_int32 Instruction;
// maximum stack for a Lua function
    const val MAXSTACK = 250
    // minimum size for the string table (must be power of 2)
    const val MINSTRTABSIZE = 32
    // minimum size for string buffer
    const val LUA_MINBUFFER = 32

    ///#if !lua_lock
    fun lua_lock(L: lua_State?) {}

    fun lua_unlock(L: lua_State?) {}
    ///#endif
///#if !luai_threadyield
    fun luai_threadyield(L: lua_State?) {
        lua_unlock(L)
        lua_lock(L)
    } ///#endif
//
//		 ** macro to control inclusion of some hard tests on stack reallocation
//
///#ifndef HARDSTACKTESTS
///#define condhardstacktests(x)	((void)0)
///#else
///#define condhardstacktests(x)	x
///#endif
}