/*
 ** $Id: lualib.h,v 1.36.1.1 2007/12/27 13:02:25 roberto Exp $
 ** Lua standard libraries
 ** See Copyright Notice in lua.h
 */
package kirin

//{
object LuaLib {
    /* Key to file-handle type */
    const val LUA_FILEHANDLE = "FILE*"
    const val LUA_COLIBNAME = "coroutine"
    const val LUA_TABLIBNAME = "table"
    const val LUA_IOLIBNAME = "io"
    const val LUA_OSLIBNAME = "os"
    const val LUA_STRLIBNAME = "string"
    const val LUA_MATHLIBNAME = "math"
    const val LUA_DBLIBNAME = "debug"
    const val LUA_LOADLIBNAME = "package"
}