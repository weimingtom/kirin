package kirin
import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.Lua.lua_Debug
import kirin.LuaAuxLib.luaL_Reg

//
// ** $Id: loadlib.c,v 1.52.1.3 2008/08/06 13:29:28 roberto Exp $
// ** Dynamic library loader for Lua
// ** See Copyright Notice in lua.h
// **
// ** This module contains an implementation of loadlib for Unix systems
// ** that have dlfcn, an implementation for Darwin (Mac OS X), an
// ** implementation for Windows, and a stub for other systems.
//
object LuaLoadLib {
    // prefix for open functions in C libraries
    const val LUA_POF = "luaopen_"
    // separator for open functions in C libraries
    const val LUA_OFSEP = "_"
    const val LIBPREFIX = "LOADLIB: "
    const val POF = LUA_POF
    const val LIB_FAIL = "open"
    // error codes for ll_loadfunc
    const val ERRLIB = 1
    const val ERRFUNC = 2
    //public static void setprogdir(lua_State L) { }
    fun setprogdir(L: lua_State?) {
        val buff: CharPtr = CharPtr.Companion.toCharPtr(StreamProxy.Companion.GetCurrentDirectory())
        LuaAuxLib.luaL_gsub(L, Lua.lua_tostring(L, -1), CharPtr.Companion.toCharPtr(LuaConf.LUA_EXECDIR), buff)
        LuaAPI.lua_remove(L!!, -2) // remove original string
    }

    ///#if LUA_DL_DLOPEN
//		/*
//		 ** {========================================================================
//		 ** This is an implementation of loadlib based on the dlfcn interface.
//		 ** The dlfcn interface is available in Linux, SunOS, Solaris, IRIX, FreeBSD,
//		 ** NetBSD, AIX 4.2, HPUX 11, and  probably most other Unix flavors, at least
//		 ** as an emulation layer on top of native functions.
//		 ** =========================================================================
//		 */
//
//		//#include <dlfcn.h>
//
//		static void ll_unloadlib (void *lib)
//		{
//			dlclose(lib);
//		}
//
//		static void *ll_load (lua_State L, readonly CharPtr path)
//		{
//			void *lib = dlopen(path, RTLD_NOW);
//			if (lib == null)
//			{
//				lua_pushstring(L, dlerror());
//			}
//			return lib;
//		}
//
//		static lua_CFunction ll_sym (lua_State L, void *lib, readonly CharPtr sym)
//		{
//			lua_CFunction f = (lua_CFunction)dlsym(lib, sym);
//			if (f == null)
//			{
//				lua_pushstring(L, dlerror());
//			}
//			return f;
//		}
//
//		/* }====================================================== */
//
//
//
//		//#elif defined(LUA_DL_DLL)
//		/*
//		 ** {======================================================================
//		 ** This is an implementation of loadlib for Windows using native functions.
//		 ** =======================================================================
//		 */
//
//		//#include <windows.h>
//
//
//		//#undef setprogdir
//
//		static void setprogdir (lua_State L)
//		{
//			char buff[MAX_PATH + 1];
//			char *lb;
//			DWORD nsize = sizeof(buff)/GetUnmanagedSize(typeof(char));
//			DWORD n = GetModuleFileNameA(null, buff, nsize);
//			if (n == 0 || n == nsize || (lb = strrchr(buff, '\\')) == null)
//			{
//				luaL_error(L, "unable to get ModuleFileName");
//			}
//			else
//			{
//				*lb = '\0';
//				luaL_gsub(L, lua_tostring(L, -1), LUA_EXECDIR, buff);
//				lua_remove(L, -2);  /* remove original string */
//			}
//		}
//
//		static void pusherror (lua_State L)
//		{
//			int error = GetLastError();
//			char buffer[128];
//			if (FormatMessageA(FORMAT_MESSAGE_IGNORE_INSERTS | FORMAT_MESSAGE_FROM_SYSTEM,
//			                   null, error, 0, buffer, sizeof(buffer), null))
//			{
//				lua_pushstring(L, buffer);
//			}
//			else
//			{
//				lua_pushfstring(L, "system error %d\n", error);
//			}
//		}
//
//		static void ll_unloadlib(void *lib)
//		{
//			FreeLibrary((HINSTANCE)lib);
//		}
//
//		static void *ll_load (lua_State L, readonly CharPtr path)
//		{
//			HINSTANCE lib = LoadLibraryA(path);
//			if (lib == null)
//			{
//				pusherror(L);
//			}
//			return lib;
//		}
//
//		static lua_CFunction ll_sym (lua_State L, void *lib, readonly CharPtr sym)
//		{
//			lua_CFunction f = (lua_CFunction)GetProcAddress((HINSTANCE)lib, sym);
//			if (f == null)
//			{
//				pusherror(L);
//			}
//			return f;
//		}
//
//		/* }====================================================== */
//
//		#elif LUA_DL_DYLD
//		/*
//		 ** {======================================================================
//		 ** Native Mac OS X / Darwin Implementation
//		 ** =======================================================================
//		 */
//
//		//#include <mach-o/dyld.h>
//
//
//		/* Mac appends a `_' before C function names */
//		//#undef POF
//		//#define POF	"_" LUA_POF
//
//		static void pusherror (lua_State L)
//		{
//			CharPtr err_str;
//			CharPtr err_file;
//			NSLinkEditErrors err;
//			int err_num;
//			NSLinkEditError(err, err_num, err_file, err_str);
//			lua_pushstring(L, err_str);
//		}
//
//
//		static CharPtr errorfromcode (NSObjectFileImageReturnCode ret)
//		{
//			switch (ret)
//			{
//				case NSObjectFileImageInappropriateFile:
//					{
//						return "file is not a bundle";
//					}
//				case NSObjectFileImageArch:
//					{
//						return "library is for wrong CPU type";
//					}
//				case NSObjectFileImageFormat:
//					{
//						return "bad format";
//					}
//				case NSObjectFileImageAccess:
//					{
//						return "cannot access file";
//					}
//				case NSObjectFileImageFailure:
//				default:
//					{
//						return "unable to load library";
//					}
//			}
//		}
//
//		static void ll_unloadlib (void *lib)
//		{
//			NSUnLinkModule((NSModule)lib, NSUNLINKMODULE_OPTION_RESET_LAZY_REFERENCES);
//		}
//
//		static void *ll_load (lua_State L, readonly CharPtr path)
//		{
//			NSObjectFileImage img;
//			NSObjectFileImageReturnCode ret;
//			/* this would be a rare case, but prevents crashing if it happens */
//			if(!_dyld_present()) {
//				lua_pushliteral(L, "dyld not present");
//				return null;
//			}
//			ret = NSCreateObjectFileImageFromFile(path, img);
//			if (ret == NSObjectFileImageSuccess) {
//				NSModule mod = NSLinkModule(img, path, NSLINKMODULE_OPTION_PRIVATE |
//				                            NSLINKMODULE_OPTION_RETURN_ON_ERROR);
//				NSDestroyObjectFileImage(img);
//				if (mod == null) pusherror(L);
//				return mod;
//			}
//			lua_pushstring(L, errorfromcode(ret));
//			return null;
//		}
//
//		static lua_CFunction ll_sym (lua_State L, void *lib, readonly CharPtr sym)
//		{
//			NSSymbol nss = NSLookupSymbolInModule((NSModule)lib, sym);
//			if (nss == null)
//			{
//				lua_pushfstring(L, "symbol " + LUA_QS + " not found", sym);
//				return null;
//			}
//			return (lua_CFunction)NSAddressOfSymbol(nss);
//		}
//
//		/* }====================================================== */
///#else
//
//		 ** {======================================================
//		 ** Fallback for other systems
//		 ** =======================================================
//
///#undef LIB_FAIL
///#define LIB_FAIL	"absent"
    const val DLMSG = "dynamic libraries not enabled; check your Lua installation"

    fun ll_unloadlib(lib: Any?) { //(void)lib;  /* to avoid warnings */
    }

    fun ll_load(L: lua_State?, path: CharPtr?): Any? { //(void)path;  /* to avoid warnings */
        Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr(DLMSG))
        return null
    }

    fun ll_sym(
        L: lua_State?,
        lib: Any?,
        sym: CharPtr?
    ): lua_CFunction? { //(void)lib; (void)sym;  /* to avoid warnings */
        Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr(DLMSG))
        return null
    }

    // }======================================================
///#endif
    private fun ll_register(
        L: lua_State,
        path: CharPtr?
    ): Any? { // todo: the whole usage of plib here is wrong, fix it - mjf
//void **plib;
        var plib: Any? = null
        LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("%s%s"), LIBPREFIX, path)
        LuaAPI.lua_gettable(L, Lua.LUA_REGISTRYINDEX) // check library in registry?
        if (!Lua.lua_isnil(L, -1)) { // is there an entry?
            plib = LuaAPI.lua_touserdata(L, -1)
        } else { // no entry yet; create one
            Lua.lua_pop(L, 1)
            //plib = lua_newuserdata(L, (uint)Marshal.SizeOf(plib));
//plib[0] = null;
            LuaAuxLib.luaL_getmetatable(L, CharPtr.Companion.toCharPtr("_LOADLIB"))
            LuaAPI.lua_setmetatable(L, -2)
            LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("%s%s"), LIBPREFIX, path)
            LuaAPI.lua_pushvalue(L, -2)
            LuaAPI.lua_settable(L, Lua.LUA_REGISTRYINDEX)
        }
        return plib
    }

    //
//		 ** __gc tag method: calls library's `ll_unloadlib' function with the lib
//		 ** handle
//
    private fun gctm(L: lua_State): Int {
        var lib: Any? = LuaAuxLib.luaL_checkudata(L, 1, CharPtr.Companion.toCharPtr("_LOADLIB"))
        lib?.let { ll_unloadlib(it) }
        lib = null // mark library as closed
        return 0
    }

    private fun ll_loadfunc(L: lua_State, path: CharPtr?, sym: CharPtr): Int {
        var reg = ll_register(L, path)
        if (reg == null) {
            reg = ll_load(L, path)
        }
        return if (reg == null) {
            ERRLIB // unable to load library
        } else {
            val f: lua_CFunction = ll_sym(L, reg, sym)
                ?: return ERRFUNC // unable to find function
            Lua.lua_pushcfunction(L, f)
            0 // return function
        }
    }

    private fun ll_loadlib(L: lua_State): Int {
        val path: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        val init: CharPtr = LuaAuxLib.luaL_checkstring(L, 2)
        val stat = ll_loadfunc(L, path, init)
        return if (stat == 0) { // no errors?
            1 // return the loaded function
        } else { // error; error message is on stack top
            LuaAPI.lua_pushnil(L)
            LuaAPI.lua_insert(L, -2)
            LuaAPI.lua_pushstring(
                L,
                if (stat == ERRLIB) CharPtr.Companion.toCharPtr(LIB_FAIL) else CharPtr.Companion.toCharPtr(
                    "init"
                )
            )
            3 // return nil, error message, and where
        }
    }

    //
//		 ** {======================================================
//		 ** 'require' function
//		 ** =======================================================
//
    private fun readable(filename: CharPtr?): Int {
        val f: StreamProxy = CLib.fopen(filename, CharPtr.Companion.toCharPtr("r"))
            ?: // open failed
            return 0 // try to open file
        CLib.fclose(f)
        return 1
    }

    private fun pushnexttemplate(L: lua_State, path: CharPtr): CharPtr? {
        var path: CharPtr = path
        var l: CharPtr?
        while (path.get(0) == LuaConf.LUA_PATHSEP.get(0)) {
            path = path.next() // skip separators
        }
        if (path.get(0) == '\u0000') {
            return null // no more templates
        }
        l = CLib.strchr(path, LuaConf.LUA_PATHSEP.get(0)) // find next separator
        if (CharPtr.Companion.isEqual(l, null)) {
            l = CharPtr.Companion.plus(path, CLib.strlen(path))
        }
        LuaAPI.lua_pushlstring(L, path, CharPtr.Companion.minus(l!!, path)) // template  - (uint)
        return l
    }

    private fun findfile(L: lua_State, name: CharPtr?, pname: CharPtr?): CharPtr? {
        var name: CharPtr? = name
        var path: CharPtr?
        name = LuaAuxLib.luaL_gsub(
            L,
            name,
            CharPtr.Companion.toCharPtr("."),
            CharPtr.Companion.toCharPtr(LuaConf.LUA_DIRSEP)
        )
        LuaAPI.lua_getfield(L, Lua.LUA_ENVIRONINDEX, pname)
        path = Lua.lua_tostring(L, -1)
        if (CharPtr.Companion.isEqual(path, null)) {
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("package.%s").toString() + " must be a string"),
                pname
            )
        }
        Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("")) // error accumulator
        while (CharPtr.Companion.isNotEqual(pushnexttemplate(L, path!!).also({ path = it }), null)) {
            var filename: CharPtr?
            filename = LuaAuxLib.luaL_gsub(
                L,
                Lua.lua_tostring(L, -1),
                CharPtr.Companion.toCharPtr(LuaConf.LUA_PATH_MARK),
                name
            )
            LuaAPI.lua_remove(L, -2) // remove path template
            if (readable(filename) != 0) { // does file exist and is readable?
                return filename // return that file name
            }
            LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("\n\tno file " + LuaConf.getLUA_QS()), filename)
            LuaAPI.lua_remove(L, -2) // remove file name
            LuaAPI.lua_concat(L, 2) // add entry to possible error message
        }
        return null // not found
    }

    private fun loaderror(L: lua_State, filename: CharPtr?) {
        LuaAuxLib.luaL_error(
            L,
            CharPtr.Companion.toCharPtr("error loading module " + LuaConf.getLUA_QS() + " from file " + LuaConf.getLUA_QS() + ":\n\t%s"),
            Lua.lua_tostring(L, 1),
            filename,
            Lua.lua_tostring(L, -1)
        )
    }

    private fun loader_Lua(L: lua_State): Int {
        val filename: CharPtr?
        val name: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        filename = findfile(L, name, CharPtr.Companion.toCharPtr("path"))
        if (CharPtr.Companion.isEqual(filename, null)) {
            return 1 // library not found in this path
        }
        if (LuaAuxLib.luaL_loadfile(L, filename) != 0) {
            loaderror(L, filename)
        }
        return 1 // library loaded successfully
    }

    private fun mkfuncname(L: lua_State, modname: CharPtr): CharPtr? {
        var modname: CharPtr? = modname
        var funcname: CharPtr?
        val mark: CharPtr? = CLib.strchr(modname, LuaConf.LUA_IGMARK.get(0))
        if (CharPtr.Companion.isNotEqual(mark, null)) {
            modname = CharPtr.Companion.plus(mark, 1)
        }
        funcname = LuaAuxLib.luaL_gsub(
            L,
            modname,
            CharPtr.Companion.toCharPtr("."),
            CharPtr.Companion.toCharPtr(LUA_OFSEP)
        )
        funcname = LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("$POF%s"), funcname)
        LuaAPI.lua_remove(L, -2) // remove 'gsub' result
        return funcname
    }

    private fun loader_C(L: lua_State): Int {
        val funcname: CharPtr?
        val name: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        val filename: CharPtr? = findfile(L, name, CharPtr.Companion.toCharPtr("cpath"))
        if (CharPtr.Companion.isEqual(filename, null)) {
            return 1 // library not found in this path
        }
        funcname = mkfuncname(L, name)
        if (ll_loadfunc(L, filename, funcname!!) != 0) {
            loaderror(L, filename)
        }
        return 1 // library loaded successfully
    }

    private fun loader_Croot(L: lua_State): Int {
        val funcname: CharPtr?
        val filename: CharPtr?
        val name: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        val p: CharPtr? = CLib.strchr(name, '.')
        var stat: Int
        if (CharPtr.Companion.isEqual(p, null)) {
            return 0 // is root
        }
        LuaAPI.lua_pushlstring(L, name, CharPtr.Companion.minus(p!!, name)) //(uint)
        filename = findfile(L, Lua.lua_tostring(L, -1), CharPtr.Companion.toCharPtr("cpath"))
        if (CharPtr.Companion.isEqual(filename, null)) {
            return 1 // root not found
        }
        funcname = mkfuncname(L, name)
        if (ll_loadfunc(L, filename, funcname!!).also { stat = it } != 0) {
            if (stat != ERRFUNC) {
                loaderror(L, filename) // real error
            }
            LuaAPI.lua_pushfstring(
                L,
                CharPtr.Companion.toCharPtr("\n\tno module " + LuaConf.getLUA_QS() + " in file " + LuaConf.getLUA_QS()),
                name,
                filename
            )
            return 1 // function not found
        }
        return 1
    }

    private fun loader_preload(L: lua_State): Int {
        val name: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        LuaAPI.lua_getfield(L, Lua.LUA_ENVIRONINDEX, CharPtr.Companion.toCharPtr("preload"))
        if (!Lua.lua_istable(L, -1)) {
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("package.preload").toString() + " must be a table")
            )
        }
        LuaAPI.lua_getfield(L, -1, name)
        if (Lua.lua_isnil(L, -1)) { // not found?
            LuaAPI.lua_pushfstring(L, CharPtr.Companion.toCharPtr("\n\tno field package.preload['%s']"), name)
        }
        return 1
    }

    var sentinel = Any()
    fun ll_require(L: lua_State?): Int {
        val name: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        var i: Int
        LuaAPI.lua_settop(L!!, 1) // _LOADED table will be at index 2
        LuaAPI.lua_getfield(L!!, Lua.LUA_REGISTRYINDEX, CharPtr.Companion.toCharPtr("_LOADED"))
        LuaAPI.lua_getfield(L!!, 2, name)
        if (LuaAPI.lua_toboolean(L!!, -1) != 0) { // is it there?
            if (LuaAPI.lua_touserdata(L!!, -1) === sentinel) { // check loops
                LuaAuxLib.luaL_error(
                    L,
                    CharPtr.Companion.toCharPtr("loop or previous error loading module " + LuaConf.getLUA_QS()),
                    name
                )
            }
            return 1 // package is already loaded
        }
        // else must load it; iterate over available loaders
        LuaAPI.lua_getfield(L!!, Lua.LUA_ENVIRONINDEX, CharPtr.Companion.toCharPtr("loaders"))
        if (!Lua.lua_istable(L, -1)) {
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("package.loaders").toString() + " must be a table")
            )
        }
        Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("")) // error message accumulator
        i = 1
        while (true) {
            LuaAPI.lua_rawgeti(L, -2, i) // get a loader
            if (Lua.lua_isnil(L, -1)) {
                LuaAuxLib.luaL_error(
                    L,
                    CharPtr.Companion.toCharPtr("module " + LuaConf.getLUA_QS() + " not found:%s"),
                    name,
                    Lua.lua_tostring(L, -2)
                )
            }
            LuaAPI.lua_pushstring(L!!, name)
            LuaAPI.lua_call(L!!, 1, 1) // call it
            if (Lua.lua_isfunction(L, -1)) { // did it find module?
                break // module loaded successfully
            } else if (LuaAPI.lua_isstring(L!!, -1) != 0) { // loader returned error message?
                LuaAPI.lua_concat(L!!, 2) // accumulate it
            } else {
                Lua.lua_pop(L, 1)
            }
            i++
        }
        LuaAPI.lua_pushlightuserdata(L!!, sentinel)
        LuaAPI.lua_setfield(L!!, 2, name) // _LOADED[name] = sentinel
        LuaAPI.lua_pushstring(L!!, name) // pass name as argument to module
        LuaAPI.lua_call(L!!, 1, 1) // run loaded module
        if (!Lua.lua_isnil(L, -1)) { // non-nil return?
            LuaAPI.lua_setfield(L!!, 2, name) // _LOADED[name] = returned value
        }
        LuaAPI.lua_getfield(L!!, 2, name)
        if (LuaAPI.lua_touserdata(L, -1) === sentinel) { // module did not set a value?
            LuaAPI.lua_pushboolean(L!!, 1) // use true as result
            LuaAPI.lua_pushvalue(L!!, -1) // extra copy to be returned
            LuaAPI.lua_setfield(L!!, 2, name) // _LOADED[name] = true
        }
        return 1
    }

    // }======================================================
//
//		 ** {======================================================
//		 ** 'module' function
//		 ** =======================================================
//
    private fun setfenv(L: lua_State) {
        val ar = lua_Debug()
        if (LuaDebug.lua_getstack(L, 1, ar) == 0 || LuaDebug.lua_getinfo(
                L,
                CharPtr.Companion.toCharPtr("f"),
                ar
            ) == 0 || LuaAPI.lua_iscfunction(L, -1)
        ) { // get calling function
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr(LuaConf.LUA_QL("module").toString() + " not called from a Lua function")
            )
        }
        LuaAPI.lua_pushvalue(L, -2)
        LuaAPI.lua_setfenv(L, -2)
        Lua.lua_pop(L, 1)
    }

    private fun dooptions(L: lua_State, n: Int) {
        var i: Int
        i = 2
        while (i <= n) {
            LuaAPI.lua_pushvalue(L, i) // get option (a function)
            LuaAPI.lua_pushvalue(L, -2) // module
            LuaAPI.lua_call(L, 1, 0)
            i++
        }
    }

    private fun modinit(L: lua_State, modname: CharPtr) {
        var dot: CharPtr?
        LuaAPI.lua_pushvalue(L, -1)
        LuaAPI.lua_setfield(L, -2, CharPtr.Companion.toCharPtr("_M")) // module._M = module
        LuaAPI.lua_pushstring(L, modname)
        LuaAPI.lua_setfield(L, -2, CharPtr.Companion.toCharPtr("_NAME"))
        dot = CLib.strrchr(modname, '.') // look for last dot in module name
        dot = if (CharPtr.Companion.isEqual(dot, null)) {
            modname
        } else {
            dot!!.next()
        }
        // set _PACKAGE as package name (full module name minus last part)
        LuaAPI.lua_pushlstring(L, modname, CharPtr.Companion.minus(dot, modname)) //(uint)
        LuaAPI.lua_setfield(L, -2, CharPtr.Companion.toCharPtr("_PACKAGE"))
    }

    private fun ll_module(L: lua_State): Int {
        val modname: CharPtr = LuaAuxLib.luaL_checkstring(L, 1)
        val loaded: Int = LuaAPI.lua_gettop(L) + 1 // index of _LOADED table
        LuaAPI.lua_getfield(L, Lua.LUA_REGISTRYINDEX, CharPtr.Companion.toCharPtr("_LOADED"))
        LuaAPI.lua_getfield(L, loaded, modname) // get _LOADED[modname]
        if (!Lua.lua_istable(L, -1)) { // not found?
            Lua.lua_pop(L, 1) // remove previous result
            // try global variable (and create one if it does not exist)
            if (CharPtr.Companion.isNotEqual(LuaAuxLib.luaL_findtable(L, Lua.LUA_GLOBALSINDEX, modname, 1), null)) {
                return LuaAuxLib.luaL_error(
                    L,
                    CharPtr.Companion.toCharPtr("name conflict for module " + LuaConf.getLUA_QS()),
                    modname
                )
            }
            LuaAPI.lua_pushvalue(L, -1)
            LuaAPI.lua_setfield(L, loaded, modname) // _LOADED[modname] = new table
        }
        // check whether table already has a _NAME field
        LuaAPI.lua_getfield(L, -1, CharPtr.Companion.toCharPtr("_NAME"))
        if (!Lua.lua_isnil(L, -1)) { // is table an initialized module?
            Lua.lua_pop(L, 1)
        } else { // no; initialize it
            Lua.lua_pop(L, 1)
            modinit(L, modname)
        }
        LuaAPI.lua_pushvalue(L, -1)
        setfenv(L)
        dooptions(L, loaded - 1)
        return 0
    }

    private fun ll_seeall(L: lua_State): Int {
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TTABLE)
        if (LuaAPI.lua_getmetatable(L, 1) == 0) {
            LuaAPI.lua_createtable(L, 0, 1) // create new metatable
            LuaAPI.lua_pushvalue(L, -1)
            LuaAPI.lua_setmetatable(L, 1)
        }
        LuaAPI.lua_pushvalue(L, Lua.LUA_GLOBALSINDEX)
        LuaAPI.lua_setfield(L, -2, CharPtr.Companion.toCharPtr("__index")) // mt.__index = _G
        return 0
    }

    // }======================================================
// auxiliary mark (for internal use)
    val AUXMARK = String.format("%1\$s", 1.toChar())

    private fun setpath(L: lua_State?, fieldname: CharPtr, envname: CharPtr, def: CharPtr) {
        var path: CharPtr? = CLib.getenv(envname)
        if (CharPtr.Companion.isEqual(path, null)) { // no environment variable?
            LuaAPI.lua_pushstring(L!!, def) // use default
        } else { // replace ";;" by ";AUXMARK;" and then AUXMARK by default path
            path = LuaAuxLib.luaL_gsub(
                L,
                path,
                CharPtr.Companion.toCharPtr(LuaConf.LUA_PATHSEP + LuaConf.LUA_PATHSEP),
                CharPtr.Companion.toCharPtr(LuaConf.LUA_PATHSEP + AUXMARK + LuaConf.LUA_PATHSEP)
            )
            LuaAuxLib.luaL_gsub(L, path, CharPtr.Companion.toCharPtr(AUXMARK), def)
            LuaAPI.lua_remove(L!!, -2)
        }
        setprogdir(L)
        LuaAPI.lua_setfield(L!!, -2, fieldname)
    }

    private val pk_funcs: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("loadlib"), LuaLoadLib_delegate("ll_loadlib")),
        luaL_Reg(CharPtr.Companion.toCharPtr("seeall"), LuaLoadLib_delegate("ll_seeall")),
        luaL_Reg(null, null)
    )
    private val ll_funcs: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("module"), LuaLoadLib_delegate("ll_module")),
        luaL_Reg(CharPtr.Companion.toCharPtr("require"), LuaLoadLib_delegate("ll_require")),
        luaL_Reg(null, null)
    )
    val loaders: Array<lua_CFunction?> = arrayOf<lua_CFunction?>(
        LuaLoadLib_delegate("loader_preload"),
        LuaLoadLib_delegate("loader_Lua"),
        LuaLoadLib_delegate("loader_C"),
        LuaLoadLib_delegate("loader_Croot"),
        null
    )

    fun luaopen_package(L: lua_State?): Int {
        var i: Int
        // create new type _LOADLIB
        LuaAuxLib.luaL_newmetatable(L, CharPtr.Companion.toCharPtr("_LOADLIB"))
        Lua.lua_pushcfunction(L, LuaLoadLib_delegate("gctm"))
        LuaAPI.lua_setfield(L!!, -2, CharPtr.Companion.toCharPtr("__gc"))
        // create `package' table
        LuaAuxLib.luaL_register(L, CharPtr.Companion.toCharPtr(LuaLib.LUA_LOADLIBNAME), pk_funcs)
        ///#if LUA_COMPAT_LOADLIB
//			lua_getfield(L, -1, "loadlib");
//			lua_setfield(L, LUA_GLOBALSINDEX, "loadlib");
///#endif
        LuaAPI.lua_pushvalue(L!!, -1)
        LuaAPI.lua_replace(L!!, Lua.LUA_ENVIRONINDEX)
        // create `loaders' table
        LuaAPI.lua_createtable(L!!, 0, loaders.size - 1)
        // fill it with pre-defined loaders
        i = 0
        while (loaders[i] != null) {
            Lua.lua_pushcfunction(L, loaders[i])
            LuaAPI.lua_rawseti(L!!, -2, i + 1)
            i++
        }
        LuaAPI.lua_setfield(L!!, -2, CharPtr.Companion.toCharPtr("loaders")) // put it in field `loaders'
        setpath(
            L,
            CharPtr.Companion.toCharPtr("path"),
            CharPtr.Companion.toCharPtr(LuaConf.LUA_PATH),
            CharPtr.Companion.toCharPtr(LuaConf.LUA_PATH_DEFAULT)
        ) // set field `path'
        setpath(
            L,
            CharPtr.Companion.toCharPtr("cpath"),
            CharPtr.Companion.toCharPtr(LuaConf.LUA_CPATH),
            CharPtr.Companion.toCharPtr(LuaConf.LUA_CPATH_DEFAULT)
        ) // set field `cpath'
        // store config information
        Lua.lua_pushliteral(
            L,
            CharPtr.Companion.toCharPtr(LuaConf.LUA_DIRSEP + "\n" + LuaConf.LUA_PATHSEP + "\n" + LuaConf.LUA_PATH_MARK + "\n" + LuaConf.LUA_EXECDIR + "\n" + LuaConf.LUA_IGMARK)
        )
        LuaAPI.lua_setfield(L!!, -2, CharPtr.Companion.toCharPtr("config"))
        // set field `loaded'
        LuaAuxLib.luaL_findtable(L, Lua.LUA_REGISTRYINDEX, CharPtr.Companion.toCharPtr("_LOADED"), 2)
        LuaAPI.lua_setfield(L!!, -2, CharPtr.Companion.toCharPtr("loaded"))
        // set field `preload'
        Lua.lua_newtable(L)
        LuaAPI.lua_setfield(L!!, -2, CharPtr.Companion.toCharPtr("preload"))
        LuaAPI.lua_pushvalue(L!!, Lua.LUA_GLOBALSINDEX)
        LuaAuxLib.luaL_register(L, null, ll_funcs) // open lib into global table
        Lua.lua_pop(L, 1)
        return 1 // return 'package' table
    }

    class LuaLoadLib_delegate(private val name: String) : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return if ("ll_loadlib" == name) {
                ll_loadlib(L)
            } else if ("ll_seeall" == name) {
                ll_seeall(L)
            } else if ("ll_module" == name) {
                ll_module(L)
            } else if ("ll_require" == name) {
                ll_require(L)
            } else if ("loader_preload" == name) {
                loader_preload(L)
            } else if ("loader_Lua" == name) {
                loader_Lua(L)
            } else if ("loader_C" == name) {
                loader_C(L)
            } else if ("loader_Croot" == name) {
                loader_Croot(L)
            } else if ("gctm" == name) {
                gctm(L)
            } else {
                0
            }
        }

    }
}