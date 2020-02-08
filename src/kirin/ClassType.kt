package kirin

import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import java.lang.Exception
import kirin.LuaObject.TValue
import kirin.LuaState.CallInfo
import kirin.LuaObject.Proto
import kirin.LuaObject.Table
import kirin.LuaState.GCObject
import kirin.LuaObject.TString
import kirin.LuaObject.UpVal
import kirin.LuaObject.Udata
import java.lang.RuntimeException
import kirin.LuaObject.LocVar
import kirin.LuaIOLib.FilePtr
import kirin.LuaState.LG


class ClassType(type: Int) {
    private var type = 0
    fun GetTypeID(): Int {
        return type
    }

    fun GetTypeString(): String? {
        return if (DONNOT_USE_REIMPLEMENT) {
            GetTypeString_csharp()
        } else { //TODO:not sync
            var result: String? = null
            if (type == TYPE_CHAR) {
                result = "Char"
            } else if (type == TYPE_INT) {
                result = "Int"
            } else if (type == TYPE_DOUBLE) {
                result = "Double"
            } else if (type == TYPE_LONG) {
                result = "Int64" //FIXME:
            } else if (type == TYPE_LG) {
                result = "LG"
            } else if (type == TYPE_FILEPTR) {
                result = "FilePtr"
            } else if (type == TYPE_TVALUE) {
                result = "TValue"
            } else if (type == TYPE_CCLOSURE) {
                result = "CClosure"
            } else if (type == TYPE_LCLOSURE) {
                result = "LClosure"
            } else if (type == TYPE_TABLE) {
                result = "Table"
            } else if (type == TYPE_GCOBJECTREF) {
                result = "GCObjectRef"
            } else if (type == TYPE_TSTRING) {
                result = "TString"
            } else if (type == TYPE_NODE) {
                result = "Node"
            } else if (type == TYPE_UDATA) {
                result = "Udata"
            } else if (type == TYPE_LUA_STATE) {
                result = "lua_State"
            } else if (type == TYPE_CALLINFO) {
                result = "CallInfo"
            } else if (type == TYPE_PROTO) {
                result = "Proto"
            } else if (type == TYPE_LOCVAR) {
                result = "LocVar"
            } else if (type == TYPE_CLOSURE) {
                result = "Closure"
            } else if (type == TYPE_UPVAL) {
                result = "UpVal"
            } else if (type == TYPE_INT32) {
                result = "Int32" //FIXME:
            } else if (type == TYPE_GCOBJECT) {
                result = "GCObject"
            } else if (type == TYPE_CHARPTR) {
                result = "CharPtr"
            }
            //return null;
            result ?: "unknown type"
        }
    }

    fun Alloc(): Any? {
        return if (DONNOT_USE_REIMPLEMENT) {
            Alloc_csharp()
        } else {
            var result: Any? = null
            //FIXME:
//return System.Activator.CreateInstance(this.GetOriginalType());
            if (type == TYPE_CHAR) {
                result = '\u0000'
            } else if (type == TYPE_INT) {
                result = 0
            } else if (type == TYPE_DOUBLE) {
                result = 0
            } else if (type == TYPE_LONG) {
                result = 0 //FIXME:
            } else if (type == TYPE_LG) {
                result = LG()
            } else if (type == TYPE_FILEPTR) {
                result = FilePtr()
            } else if (type == TYPE_TVALUE) {
                result = TValue()
            } else if (type == TYPE_CCLOSURE) {
                throw RuntimeException("alloc CClosure error")
                //return new CClosure(null);
            } else if (type == TYPE_LCLOSURE) {
                throw RuntimeException("alloc LClosure error")
                //return new LClosure(null);
            } else if (type == TYPE_TABLE) {
                result = Table()
            } else if (type == TYPE_GCOBJECTREF) { //return null; //FIXME:interface!!!
                throw RuntimeException("alloc GCObjectRef error")
            } else if (type == TYPE_TSTRING) {
                result = TString()
            } else if (type == TYPE_NODE) {
                result = LuaObject.Node()
            } else if (type == TYPE_UDATA) {
                result = Udata()
            } else if (type == TYPE_LUA_STATE) {
                result = lua_State()
            } else if (type == TYPE_CALLINFO) {
                result = CallInfo()
            } else if (type == TYPE_PROTO) {
                result = Proto()
            } else if (type == TYPE_LOCVAR) {
                result = LocVar()
            } else if (type == TYPE_CLOSURE) {
                result = LuaObject.Closure()
            } else if (type == TYPE_UPVAL) {
                result = UpVal()
            } else if (type == TYPE_INT32) {
                result = 0 //FIXME:
            } else if (type == TYPE_GCOBJECT) {
                result = GCObject()
            } else if (type == TYPE_CHARPTR) {
                result = CharPtr()
            }
            //return null;
            //Debug.WriteLine("alloc " + result.GetType().ToString());
            result ?: throw RuntimeException("alloc unknown type error")
        }
    }

    fun CanIndex(): Boolean {
        return if (DONNOT_USE_REIMPLEMENT) {
            CanIndex_csharp()
        } else {
            if (type == TYPE_CHAR) {
                false
            } else if (type == TYPE_INT) {
                false
            } else if (type == TYPE_LOCVAR) {
                false
            } else if (type == TYPE_LONG) {
                false
            } else {
                true
            }
        }
    }

    fun GetUnmanagedSize(): Int {
        return if (DONNOT_USE_REIMPLEMENT) {
            GetUnmanagedSize_csharp()
        } else {
            var result = -1
            if (type == TYPE_LG) {
                result = 376
            } else if (type == TYPE_CALLINFO) {
                result = 24
            } else if (type == TYPE_TVALUE) {
                result = 16
            } else if (type == TYPE_TABLE) {
                result = 32
            } else if (type == TYPE_NODE) {
                result = 32
            } else if (type == TYPE_GCOBJECT) {
                result = 120
            } else if (type == TYPE_GCOBJECTREF) {
                result = 4
            } else if (type == TYPE_CLOSURE) { //FIXME: this is zero
                result = 0 // handle this one manually in the code
            } else if (type == TYPE_PROTO) {
                result = 76
            } else if (type == TYPE_LUA_STATE) {
                result = 120
            } else if (type == TYPE_TVALUE) {
                result = 16
            } else if (type == TYPE_TSTRING) {
                result = 16
            } else if (type == TYPE_LOCVAR) {
                result = 12
            } else if (type == TYPE_UPVAL) {
                result = 32
            } else if (type == TYPE_CCLOSURE) {
                result = 40
            } else if (type == TYPE_LCLOSURE) {
                result = 24
            } else if (type == TYPE_FILEPTR) {
                result = 4
            } else if (type == TYPE_UDATA) {
                result = 24
            } else if (type == TYPE_CHAR) {
                result = 1
            } else if (type == TYPE_INT32) {
                result = 4
            } else if (type == TYPE_INT) { //FIXME: added, equal to TYPE_INT32
                result = 4
            } else if (type == TYPE_LONG) {
                result = 8
            }
            if (result < 0) {
                throw RuntimeException("Trying to get unknown sized of unmanaged type " + GetTypeString())
            } else {
                result
            }
        }
    }

    //TODO:need reimplementation
    fun GetMarshalSizeOf(): Int {
        return if (DONNOT_USE_REIMPLEMENT) {
            GetMarshalSizeOf_csharp()
        } else { //new method
            GetUnmanagedSize()
        }
    }

    //only byValue type
    fun ObjToBytes(b: Any): ByteArray? {
        return if (DONNOT_USE_REIMPLEMENT) {
            ObjToBytes_csharp(b)
        } else { //TODO:not implemented
            null
            //LuaDump.DumpMem not work
//LuaStrLib.writer not work
        }
    }

    //TODO:need reimplementation
    fun ObjToBytes2(b: Any): ByteArray? {
        return if (DONNOT_USE_REIMPLEMENT) {
            ObjToBytes2_csharp(b)
        } else {
            ObjToBytes(b)
        }
    }

    //TODO:need reimplementation
    fun bytesToObj(bytes: ByteArray): Any? {
        return if (DONNOT_USE_REIMPLEMENT) {
            bytesToObj_csharp(bytes)
        } else { //TODO:not implemented
            null
            //LuaUndump.LoadMem not work
        }
    }

    //object[] to T[]
    fun ToArray(arr: Array<Any?>): Any? {
        return if (DONNOT_USE_REIMPLEMENT) {
            ToArray_csharp(arr)
        } else { //TODO:not implemented
            null
            //LuaUndump
        }
    }

    //--------------------------------
//csharp only implementations
//--------------------------------
//using System.Runtime.Serialization.Formatters.Binary;
    private fun ObjToBytes2_csharp(b: Any): ByteArray? {
        return null
    }

    private fun GetMarshalSizeOf_csharp(): Int {
        return 0
    }

    private fun bytesToObj_csharp(bytes: ByteArray): Any? {
        return null
    }

    private fun ObjToBytes_csharp(b: Any): ByteArray? {
        return null
    }

    private fun Alloc_csharp(): Any? {
        return null
    }

    private fun GetOriginalType_csharp(): Class<*>? {
        return null
    }

    private fun ToArray_csharp(arr: Array<Any?>): Any? {
        return null
    }

    private fun CanIndex_csharp(): Boolean {
        return false
    }

    private fun GetTypeString_csharp(): String? {
        return null
    }

    private fun GetUnmanagedSize_csharp(): Int {
        return 0
    }

    companion object {
        //FIXME:remove typeof
//TODO:need reimplementation->search for stub replacement
//TODO:not implemented->search for empty stub
//TODO:not sync
        private const val DONNOT_USE_REIMPLEMENT = false
        //char //---
        const val TYPE_CHAR = 1
        //FIXME:TYPE_INT equal TYPE_INT32
//int //typeof(int/*uint*/)
        const val TYPE_INT = 2
        //Double, Lua_Number
        const val TYPE_DOUBLE = 3
        // UInt32 Instruction //---
        const val TYPE_LONG = 4
        //LG
        const val TYPE_LG = 5
        //FilePtr
        const val TYPE_FILEPTR = 6
        //TValue; //---
        const val TYPE_TVALUE = 7
        //CClosure
        const val TYPE_CCLOSURE = 8
        //LClosure
        const val TYPE_LCLOSURE = 9
        //Table
        const val TYPE_TABLE = 10
        //GCObjectRef
        const val TYPE_GCOBJECTREF = 11
        //TString
        const val TYPE_TSTRING = 12
        //Node
        const val TYPE_NODE = 13
        //Udata
        const val TYPE_UDATA = 14
        //lua_State
        const val TYPE_LUA_STATE = 15
        //CallInfo //---
        const val TYPE_CALLINFO = 16
        //Proto //---
        const val TYPE_PROTO = 17
        //LocVar
        const val TYPE_LOCVAR = 18
        //---
//Closure
        const val TYPE_CLOSURE = 19
        //UpVal
        const val TYPE_UPVAL = 20
        //Int32
        const val TYPE_INT32 = 21
        //GCObject
        const val TYPE_GCOBJECT = 22
        //---
        const val TYPE_CHARPTR = 23

        //number of ints inside a lua_Number
        fun GetNumInts(): Int { //return sizeof(Double/*lua_Number*/) / sizeof(int); //FIXME:
            return 8 / 4
        }

        fun SizeOfInt(): Int { //return sizeof(int); //FIXME:
            return 4
        }

        fun SizeOfLong(): Int { //sizeof(long/*uint*/)
//sizeof(long/*UInt32*//*Instruction*/));
//return sizeof(long); //FIXME:
            return 8
        }

        fun SizeOfDouble(): Int { //sizeof(Double/*lua_Number*/)
//return sizeof(double);//FIXME:
            return 8
        }

        fun ConvertToSingle(o: Any): Double { //return Convert.ToSingle(o); //FIXME:
            return o.toString().toFloat().toDouble()
        }

        fun ConvertToChar(str: String?): Char {
            return if (str!!.length > 0) str[0] else '\u0000'
        }

        fun ConvertToInt32(str: String?): Int {
            return str!!.toInt()
        }

        fun ConvertToInt32(i: Long): Int { //return Convert.ToInt32(i); //FIXME:
            return i.toInt()
        }

        fun ConvertToInt32_object(i: Any): Int { //return Convert.ToInt32(i);//FIXME:
            return i.toString().toInt()
        }

        fun ConvertToDouble(str: String?, isSuccess: BooleanArray?): Double {
            if (isSuccess != null) {
                isSuccess[0] = true
            }
            return try {
                str!!.toDouble()
            } catch (e2: Exception) {
                e2.printStackTrace()
                if (isSuccess != null) {
                    isSuccess[0] = false
                }
                0 as Double
            }
        }

        fun isNaN(d: Double): Boolean {
            return java.lang.Double.isNaN(d)
        }

        fun log2(x: Double): Int {
            return if (DONNOT_USE_REIMPLEMENT) {
                log2_csharp(x)
            } else {
                (Math.log(x) / Math.log(2.0)).toInt()
            }
        }

        fun ConvertToInt32(obj: Any): Double { //return Convert.ToInt32(obj);//FIXME:
            return obj.toString().toInt().toDouble()
        }

        fun IsPunctuation(c: Char): Boolean {
            return if (c == ',' || c == '.' || c == ';' || c == ':' || c == '!' || c == '?' || c == '/' || c == '\\' || c == '\'' || c == '\"'
            ) {
                true
            } else {
                false
            }
        }

        fun IndexOfAny(str: String, anyOf: CharArray): Int {
            return if (DONNOT_USE_REIMPLEMENT) {
                IndexOfAny_csharp(str, anyOf)
            } else {
                var index = -1
                for (i in anyOf.indices) {
                    val index2 = str.indexOf(anyOf[i])
                    if (index2 >= 0) {
                        if (index == -1) {
                            index = index2
                        } else {
                            if (index2 < index) {
                                index = index2
                            }
                        }
                    }
                }
                index
            }
        }

        fun Assert(condition: Boolean) {
            if (DONNOT_USE_REIMPLEMENT) {
                Assert_csharp(condition)
            } else {
                if (!condition) {
                    throw RuntimeException("Assert")
                }
            }
        }

        fun Assert(condition: Boolean, message: String) {
            if (DONNOT_USE_REIMPLEMENT) {
                Assert_csharp(condition, message)
            } else {
                if (!condition) {
                    throw RuntimeException(message)
                }
            }
        }

        fun processExec(strCmdLine: String): Int {
            return if (DONNOT_USE_REIMPLEMENT) {
                processExec_csharp(strCmdLine)
            } else { //TODO:not implemented
                0
                //LuaOSLib.os_execute
            }
        }

        fun GetBytes(d: Double): IntArray { //FIXME:
            val value = java.lang.Double.doubleToRawLongBits(d)
            val byteRet = IntArray(8)
            for (i in 0..7) {
                byteRet[i] = (value shr 8 * i and 0xff).toInt()
            }
            return byteRet
        }

        private fun processExec_csharp(strCmdLine: String): Int {
            return 0
        }

        private fun Assert_csharp(condition: Boolean) {}
        private fun Assert_csharp(condition: Boolean, message: String) {}
        fun IndexOfAny_csharp(str: String?, anyOf: CharArray?): Int {
            return 0
        }

        fun log2_csharp(x: Double): Int {
            return 0
        }
    }

    init {
        this.type = type
    }
}