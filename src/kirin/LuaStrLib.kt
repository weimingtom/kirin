package kirin

import kirin.LuaState.lua_State
import kirin.CLib.CharPtr
import kirin.Lua.lua_CFunction
import kirin.Lua.lua_Writer
import kirin.LuaAuxLib.luaL_Reg
import kirin.LuaAuxLib.luaL_Buffer

//
// ** $Id: lstrlib.c,v 1.132.1.4 2008/07/11 17:27:21 roberto Exp $
// ** Standard library for string operations and pattern-matching
// ** See Copyright Notice in lua.h
//
//using ptrdiff_t = System.Int32;
//using lua_Integer = System.Int32;
//using LUA_INTFRM_T = System.Int64;
//using UNSIGNED_LUA_INTFRM_T = System.UInt64;
object LuaStrLib {
    fun str_len(L: lua_State?): Int {
        val l = IntArray(1) //uint
        LuaAuxLib.luaL_checklstring(L, 1, l) //out
        LuaAPI.lua_pushinteger(L!!, l[0]) //(int)
        return 1
    }

    private fun posrelat(pos: Int, len: Int): Int { //uint - ptrdiff_t - Int32 - ptrdiff_t - Int32
// relative string position: negative means back from end
        var pos = pos
        if (pos < 0) {
            pos += len + 1 //ptrdiff_t - Int32
        }
        return if (pos >= 0) pos else 0
    }

    fun str_sub(L: lua_State?): Int {
        val l = IntArray(1) //uint
        val s: CharPtr = LuaAuxLib.luaL_checklstring(L, 1, l) //out
        var start = posrelat(LuaAuxLib.luaL_checkinteger(L, 2), l[0]) //ptrdiff_t - Int32
        var end = posrelat(LuaAuxLib.luaL_optinteger(L, 3, -1), l[0]) //ptrdiff_t - Int32
        if (start < 1) {
            start = 1
        }
        if (end > l[0]) { //ptrdiff_t - Int32
            end = l[0] //ptrdiff_t - Int32
        }
        if (start <= end) {
            LuaAPI.lua_pushlstring(L!!, CharPtr.Companion.plus(s, start - 1), end - start + 1) //(uint)
        } else {
            Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr(""))
        }
        return 1
    }

    fun str_reverse(L: lua_State?): Int {
        val l = IntArray(1) //uint
        val b = luaL_Buffer()
        val s: CharPtr = LuaAuxLib.luaL_checklstring(L, 1, l) //out
        LuaAuxLib.luaL_buffinit(L, b)
        while (l[0]-- != 0) {
            LuaAuxLib.luaL_addchar(b, s.get(l[0]))
        }
        LuaAuxLib.luaL_pushresult(b)
        return 1
    }

    fun str_lower(L: lua_State?): Int {
        val l = IntArray(1) //uint
        var i: Int //uint
        val b = luaL_Buffer()
        val s: CharPtr = LuaAuxLib.luaL_checklstring(L, 1, l) //out
        LuaAuxLib.luaL_buffinit(L, b)
        i = 0
        while (i < l[0]) {
            LuaAuxLib.luaL_addchar(b, CLib.tolower(s.get(i)))
            i++
        }
        LuaAuxLib.luaL_pushresult(b)
        return 1
    }

    fun str_upper(L: lua_State?): Int {
        val l = IntArray(1) //uint
        var i: Int //uint
        val b = luaL_Buffer()
        val s: CharPtr = LuaAuxLib.luaL_checklstring(L, 1, l) //out
        LuaAuxLib.luaL_buffinit(L, b)
        i = 0
        while (i < l[0]) {
            LuaAuxLib.luaL_addchar(b, CLib.toupper(s.get(i)))
            i++
        }
        LuaAuxLib.luaL_pushresult(b)
        return 1
    }

    fun str_rep(L: lua_State?): Int {
        val l = IntArray(1) //uint
        val b = luaL_Buffer()
        val s: CharPtr = LuaAuxLib.luaL_checklstring(L, 1, l) //out
        var n: Int = LuaAuxLib.luaL_checkint(L, 2)
        LuaAuxLib.luaL_buffinit(L, b)
        while (n-- > 0) {
            LuaAuxLib.luaL_addlstring(b, s, l[0])
        }
        LuaAuxLib.luaL_pushresult(b)
        return 1
    }

    fun str_byte(L: lua_State?): Int {
        val l = IntArray(1) //uint
        val s: CharPtr = LuaAuxLib.luaL_checklstring(L, 1, l) //out
        var posi = posrelat(LuaAuxLib.luaL_optinteger(L, 2, 1), l[0]) //ptrdiff_t - Int32
        var pose = posrelat(LuaAuxLib.luaL_optinteger(L, 3, posi), l[0]) //ptrdiff_t - Int32
        val n: Int
        var i: Int
        if (posi <= 0) {
            posi = 1
        }
        if (pose > l[0]) { //uint
            pose = l[0]
        }
        if (posi > pose) {
            return 0 // empty interval; return no values
        }
        n = (pose - posi + 1)
        if (posi + n <= pose) { // overflow?
            LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("string slice too long"))
        }
        LuaAuxLib.luaL_checkstack(L, n, CharPtr.Companion.toCharPtr("string slice too long"))
        i = 0
        while (i < n) {
            LuaAPI.lua_pushinteger(L!!, (s.get(posi + i - 1) as Byte).toInt())
            i++
        }
        return n
    }

    fun str_char(L: lua_State?): Int {
        val n: Int = LuaAPI.lua_gettop(L!!) // number of arguments
        var i: Int
        val b = luaL_Buffer()
        LuaAuxLib.luaL_buffinit(L, b)
        i = 1
        while (i <= n) {
            val c: Int = LuaAuxLib.luaL_checkint(L, i)
            LuaAuxLib.luaL_argcheck(L, (c as Byte).toInt() == c, i, "invalid value")
            LuaAuxLib.luaL_addchar(b, c.toByte().toChar())
            i++
        }
        LuaAuxLib.luaL_pushresult(b)
        return 1
    }

    private fun writer(L: lua_State, b: Any, size: Int, B: Any, t: ClassType): Int { //uint
//FIXME:b always is CharPtr
//if (b.GetType() != typeof(CharPtr))
        var b = b
        if (t.GetTypeID() == ClassType.Companion.TYPE_CHARPTR) {
            val bytes: ByteArray? = t.ObjToBytes2(b)
            val chars = CharArray(bytes!!.size)
            for (i in bytes!!.indices) {
                chars[i] = bytes[i].toChar()
            }
            b = CharPtr(chars)
        }
        LuaAuxLib.luaL_addlstring(B as luaL_Buffer, b as CharPtr, size)
        return 0
    }

    fun str_dump(L: lua_State?): Int {
        val b = luaL_Buffer()
        LuaAuxLib.luaL_checktype(L, 1, Lua.LUA_TFUNCTION)
        LuaAPI.lua_settop(L!!, 1)
        LuaAuxLib.luaL_buffinit(L, b)
        if (LuaAPI.lua_dump(L!!, writer_delegate(), b) != 0) {
            LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("unable to dump given function"))
        }
        LuaAuxLib.luaL_pushresult(b)
        return 1
    }

    //
//		 ** {======================================================
//		 ** PATTERN MATCHING
//		 ** =======================================================
//
    const val CAP_UNFINISHED = -1
    const val CAP_POSITION = -2
    const val L_ESC = '%'
    const val SPECIALS = "^$*+?.([%-"
    private fun check_capture(ms: MatchState, l: Int): Int {
        var l = l
        l -= '1'.toInt()
        return if (l < 0 || l >= ms.level || ms.capture[l]!!.len == CAP_UNFINISHED) {
            LuaAuxLib.luaL_error(ms.L, CharPtr.Companion.toCharPtr("invalid capture index"))
        } else l
    }

    private fun capture_to_close(ms: MatchState): Int {
        var level = ms.level
        level--
        while (level >= 0) {
            if (ms.capture[level]!!.len == CAP_UNFINISHED) {
                return level
            }
            level--
        }
        return LuaAuxLib.luaL_error(ms.L, CharPtr.Companion.toCharPtr("invalid pattern capture"))
    }

    private fun classend(ms: MatchState, p: CharPtr): CharPtr {
        var p: CharPtr = p
        p = CharPtr(p)
        var c: Char = p.get(0)
        p = p.next()
        return when (c) {
            L_ESC -> {
                if (p.get(0) == '\u0000') {
                    LuaAuxLib.luaL_error(
                        ms.L,
                        CharPtr.Companion.toCharPtr("malformed pattern (ends with " + LuaConf.LUA_QL("%%") + ")")
                    )
                }
                CharPtr.Companion.plus(p, 1)
            }
            '[' -> {
                if (p.get(0) == '^') {
                    p = p.next()
                }
                do { // look for a `]'
                    if (p.get(0) == '\u0000') {
                        LuaAuxLib.luaL_error(
                            ms.L,
                            CharPtr.Companion.toCharPtr("malformed pattern (missing " + LuaConf.LUA_QL("]") + ")")
                        )
                    }
                    c = p.get(0)
                    p = p.next()
                    if (c == L_ESC && p.get(0) != '\u0000') {
                        p = p.next() // skip escapes (e.g. `%]')
                    }
                } while (p.get(0) != ']')
                CharPtr.Companion.plus(p, 1)
            }
            else -> {
                p
            }
        }
    }

    private fun match_class(c: Int, cl: Int): Int {
        val res: Boolean
        res = when (CLib.tolower(cl)) {
            'a' -> {
                CLib.isalpha(c)
            }
            'c' -> {
                CLib.iscntrl(c)
            }
            'd' -> {
                CLib.isdigit(c)
            }
            'l' -> {
                CLib.islower(c)
            }
            'p' -> {
                CLib.ispunct(c)
            }
            's' -> {
                CLib.isspace(c)
            }
            'u' -> {
                CLib.isupper(c)
            }
            'w' -> {
                CLib.isalnum(c)
            }
            'x' -> {
                CLib.isxdigit(c.toChar())
            }
            'z' -> {
                c == 0
            }
            else -> {
                return if (cl == c) 1 else 0
            }
        }
        return if (CLib.islower(cl)) if (res) 1 else 0 else if (!res) 1 else 0
    }

    private fun matchbracketclass(c: Int, p: CharPtr, ec: CharPtr): Int {
        var p: CharPtr = p
        var sig = 1
        if (p.get(1) == '^') {
            sig = 0
            p = p.next() // skip the `^'
        }
        while (CharPtr.Companion.lessThan(p.next().also({ p = it }), ec)) {
            if (CharPtr.Companion.isEqualChar(p, L_ESC)) {
                p = p.next()
                if (match_class(c, (p.get(0) as Byte).toInt()) != 0) {
                    return sig
                }
            } else if (p.get(1) == '-' && CharPtr.Companion.lessThan(CharPtr.Companion.plus(p, 2), ec)) {
                p = CharPtr.Companion.plus(p, 2)
                if (p.get(-2) as Byte <= c && c <= p.get(0) as Byte) {
                    return sig
                }
            } else if ((p.get(0) as Byte).toInt() == c){
                return sig
            }
        }
        return if (sig == 0) 1 else 0
    }

    private fun singlematch(c: Int, p: CharPtr, ep: CharPtr): Int {
        return when (p.get(0)) {
            '.' -> {
                1 // matches any char
            }
            L_ESC -> {
                match_class(c, (p.get(1) as Byte).toInt())
            }
            '[' -> {
                matchbracketclass(c, p, CharPtr.Companion.minus(ep, 1))
            }
            else -> {
                if (((p.get(0) as Byte).toInt() == c)) 1 else 0
            }
        }
    }

    private fun matchbalance(ms: MatchState, s: CharPtr, p: CharPtr): CharPtr? {
        var s: CharPtr = s
        if (p.get(0).toInt() == 0 || p.get(1).toInt() == 0) {
            LuaAuxLib.luaL_error(ms.L, CharPtr.Companion.toCharPtr("unbalanced pattern"))
        }
        if (s.get(0) != p.get(0)) {
            return null
        } else {
            val b: Int = p.get(0).toInt()
            val e: Int = p.get(1).toInt()
            var cont = 1
            while (CharPtr.Companion.lessThan(s.next().also({ s = it }), ms.src_end!!)) {
                if (s.get(0).toInt() == e) {
                    if (--cont == 0) {
                        return CharPtr.Companion.plus(s, 1)
                    }
                } else if (s.get(0).toInt() == b) {
                    cont++
                }
            }
        }
        return null // string ends out of balance
    }

    private fun max_expand(ms: MatchState, s: CharPtr, p: CharPtr, ep: CharPtr): CharPtr? {
        var i = 0 // counts maximum expand for item  - ptrdiff_t - Int32
        while (CharPtr.Companion.lessThan(
                CharPtr.Companion.plus(s, i),
                ms.src_end!!
            ) && singlematch((s.get(i) as Byte).toInt(), p, ep) != 0
        ) {
            i++
        }
        // keeps trying to match with the maximum repetitions
        while (i >= 0) {
            val res: CharPtr? = match(ms, CharPtr.Companion.plus(s, i), CharPtr.Companion.plus(ep, 1))
            if (CharPtr.Companion.isNotEqual(res, null)) {
                return res
            }
            i-- // else didn't match; reduce 1 repetition to try again
        }
        return null
    }

    private fun min_expand(ms: MatchState, s: CharPtr, p: CharPtr, ep: CharPtr): CharPtr? {
        var s: CharPtr = s
        while (true) {
            val res: CharPtr? = match(ms, s, CharPtr.Companion.plus(ep, 1))
            s = if (CharPtr.Companion.isNotEqual(res, null)) {
                return res
            } else if (CharPtr.Companion.lessThan(
                    s,
                    ms.src_end!!
                ) && singlematch((s.get(0) as Byte).toInt(), p, ep) != 0
            ) {
                s.next() // try with one more repetition
            } else {
                return null
            }
        }
    }

    private fun start_capture(ms: MatchState, s: CharPtr, p: CharPtr, what: Int): CharPtr? {
        var res: CharPtr? = null
        val level = ms.level
        if (level >= LuaConf.LUA_MAXCAPTURES) {
            LuaAuxLib.luaL_error(ms.L, CharPtr.Companion.toCharPtr("too many captures"))
        }
        ms.capture[level]!!.init = s
        ms.capture[level]!!.len = what
        ms.level = level + 1
        if (CharPtr.Companion.isEqual(match(ms, s, p).also({ res = it }), null)) { // match failed?
            ms.level-- // undo capture
        }
        return res
    }

    private fun end_capture(ms: MatchState, s: CharPtr, p: CharPtr): CharPtr? {
        val l = capture_to_close(ms)
        var res: CharPtr? = null
        ms.capture[l]!!.len = CharPtr.Companion.minus(s, ms.capture[l]!!.init!!) // close capture
        if (CharPtr.Companion.isEqual(match(ms, s, p).also({ res = it!! }), null)) { // match failed?
            ms.capture[l]!!.len = CAP_UNFINISHED // undo capture
        }
        return res
    }

    private fun match_capture(ms: MatchState, s: CharPtr, l: Int): CharPtr? {
        var l = l
        val len: Int //uint
        l = check_capture(ms, l)
        len = ms.capture[l]!!.len //(uint)
        return if (CharPtr.Companion.minus(ms.src_end!!, s) as Int >= len && CLib.memcmp(
                ms.capture[l]!!.init,
                s,
                len
            ) == 0
        ) { //uint
            CharPtr.Companion.plus(s, len)
        } else {
            null
        }
    }

    private fun match(ms: MatchState, s: CharPtr?, p: CharPtr): CharPtr? {
        var s: CharPtr? = s
        var p: CharPtr = p
        s = CharPtr(s!!)
        p = CharPtr(p)
        //init: /* using goto's to optimize tail recursion */
        loop@ while (true) {
            var init = false
            return when (p.get(0)) {
                '(' -> {
                    // start capture
                    if (p.get(1) == ')') { // position capture?
                        start_capture(ms, s!!, CharPtr.Companion.plus(p, 2), CAP_POSITION)
                    } else {
                        start_capture(ms, s!!, CharPtr.Companion.plus(p, 1), CAP_UNFINISHED)
                    }
                }
                ')' -> {
                    // end capture
                    end_capture(ms, s!!, CharPtr.Companion.plus(p, 1))
                }
                L_ESC -> {
                    var init2 = false
                    when (p.get(1)) {
                        'b' -> {
                            // balanced string?
                            s = matchbalance(ms, s!!, CharPtr.Companion.plus(p, 2))
                            if (CharPtr.Companion.isEqual(s, null)) {
                                return null
                            }
                            p = CharPtr.Companion.plus(p, 4)
                            //goto init;  /* else return match(ms, s, p+4); */
                            init2 = true
                        }
                        'f' -> {
                            // frontier?
                            val ep: CharPtr
                            val previous: Char
                            p = CharPtr.Companion.plus(p, 2)
                            if (p.get(0) != '[') {
                                LuaAuxLib.luaL_error(
                                    ms.L,
                                    CharPtr.Companion.toCharPtr(
                                        "missing " + LuaConf.LUA_QL("[") + " after " + LuaConf.LUA_QL("%%f") + " in pattern"
                                    )
                                )
                            }
                            ep = classend(ms, p) // points to what is next
                            previous = if (CharPtr.Companion.isEqual(s, ms.src_init)) '\u0000' else s!!.get(-1)
                            if (matchbracketclass(
                                    (previous as Byte).toInt(),
                                    p,
                                    CharPtr.Companion.minus(ep, 1)
                                ) != 0 || matchbracketclass(
                                    (s!!.get(0) as Byte).toInt(),
                                    p,
                                    CharPtr.Companion.minus(ep, 1)
                                ) == 0
                            ) {
                                return null
                            }
                            p = ep
                            //goto init;  /* else return match(ms, s, ep); */
                            init2 = true
                        }
                        else -> {
                            if (CLib.isdigit((p.get(1) as Byte).toInt())) { // capture results (%0-%9)?
                                s = match_capture(ms, s!!, (p.get(1) as Byte).toInt())
                                if (CharPtr.Companion.isEqual(s, null)) {
                                    return null
                                }
                                p = CharPtr.Companion.plus(p, 2)
                                //goto init;
// else return match(ms, s, p+2)
                                init2 = true
                                break@loop
                            }
                            //goto dflt;
// case default
                            if (true) { //------------------dflt start--------------
// it is a pattern item
                                val ep: CharPtr = classend(ms, p) // points to what is next
                                val m = if (CharPtr.Companion.lessThan(
                                        s!!,
                                        ms.src_end!!
                                    ) && singlematch((s!!.get(0) as Byte).toInt(), p, ep) != 0
                                ) 1 else 0
                                var init3 = false
                                when (ep.get(0)) {
                                    '?' -> {
                                        // optional
                                        var res: CharPtr? = null
                                        if (m != 0 && CharPtr.Companion.isNotEqual(
                                                match(
                                                    ms,
                                                    CharPtr.Companion.plus(s, 1),
                                                    CharPtr.Companion.plus(ep, 1)
                                                ).also({ res = it }), null
                                            )
                                        ) {
                                            return res
                                        }
                                        p = CharPtr.Companion.plus(ep, 1)
                                        //goto init;  /* else return match(ms, s, ep+1); */
                                        init3 = true
                                    }
                                    '*' -> {
                                        // 0 or more repetitions
                                        return max_expand(ms, s, p, ep)
                                    }
                                    '+' -> {
                                        // 1 or more repetitions
                                        return if (m != 0) max_expand(
                                            ms,
                                            CharPtr.Companion.plus(s, 1),
                                            p,
                                            ep
                                        ) else null
                                    }
                                    '-' -> {
                                        // 0 or more repetitions (minimum)
                                        return min_expand(ms, s, p, ep)
                                    }
                                    else -> {
                                        if (m == 0) {
                                            return null
                                        }
                                        s = s.next()
                                        p = ep
                                        //goto init;  /* else return match(ms, s+1, ep); */
                                        init3 = true
                                    }
                                }
                                if (init3 == true) {
                                    init2 = true
                                    break@loop
                                } else {
                                    break@loop
                                }
                                //------------------dflt end--------------
                            }
                        }
                    }
                    if (init2 == true) {
                        init = true
                        break@loop
                    } else {
                        break@loop
                    }
                }
                '\u0000' -> {
                    // end of pattern
                    s // match succeeded
                }
                '$' -> {
                    if (p.get(1) == '\u0000') { // is the `$' the last char in pattern?
                        if (CharPtr.Companion.isEqual(s, ms.src_end)) s else null // check end of string
                    } else { //goto dflt;
//------------------dflt start--------------
// it is a pattern item
                        val ep: CharPtr = classend(ms, p) // points to what is next
                        val m = if (CharPtr.Companion.lessThan(
                                s!!,
                                ms.src_end!!
                            ) && singlematch((s.get(0) as Byte).toInt(), p, ep) != 0
                        ) 1 else 0
                        var init2 = false
                        when (ep.get(0)) {
                            '?' -> {
                                // optional
                                var res: CharPtr? = null
                                if (m != 0 && CharPtr.Companion.isNotEqual(
                                        match(
                                            ms,
                                            CharPtr.Companion.plus(s, 1),
                                            CharPtr.Companion.plus(ep, 1)
                                        ).also({ res = it }), null
                                    )
                                ) {
                                    return res
                                }
                                p = CharPtr.Companion.plus(ep, 1)
                                //goto init;  /* else return match(ms, s, ep+1); */
                                init2 = true
                            }
                            '*' -> {
                                // 0 or more repetitions
                                return max_expand(ms, s, p, ep)
                            }
                            '+' -> {
                                // 1 or more repetitions
                                return if (m != 0) max_expand(
                                    ms,
                                    CharPtr.Companion.plus(s, 1),
                                    p,
                                    ep
                                ) else null
                            }
                            '-' -> {
                                // 0 or more repetitions (minimum)
                                return min_expand(ms, s, p, ep)
                            }
                            else -> {
                                if (m == 0) {
                                    return null
                                }
                                s = s.next()
                                p = ep
                                //goto init;
// else return match(ms, s+1, ep);
                                init2 = true
                            }
                        }
                        if (init2 == true) {
                            init = true
                            break@loop
                        } else {
                            break@loop
                        }
                        //------------------dflt end--------------
                    }
                }
                else -> {
                    //dflt:
// it is a pattern item
                    val ep: CharPtr = classend(ms, p) // points to what is next
                    val m = if (CharPtr.Companion.lessThan(
                            s!!,
                            ms.src_end!!
                        ) && singlematch((s.get(0) as Byte).toInt(), p, ep) != 0
                    ) 1 else 0
                    var init2 = false
                    when (ep.get(0)) {
                        '?' -> {
                            // optional
                            var res: CharPtr? = null
                            if (m != 0 && CharPtr.Companion.isNotEqual(
                                    match(
                                        ms,
                                        CharPtr.Companion.plus(s, 1),
                                        CharPtr.Companion.plus(ep, 1)
                                    ).also({ res = it }), null
                                )
                            ) {
                                return res
                            }
                            p = CharPtr.Companion.plus(ep, 1)
                            //goto init;  /* else return match(ms, s, ep+1); */
                            init2 = true
                        }
                        '*' -> {
                            // 0 or more repetitions
                            return max_expand(ms, s, p, ep)
                        }
                        '+' -> {
                            // 1 or more repetitions
                            return if (m != 0) max_expand(
                                ms,
                                CharPtr.Companion.plus(s, 1),
                                p,
                                ep
                            ) else null
                        }
                        '-' -> {
                            // 0 or more repetitions (minimum)
                            return min_expand(ms, s, p, ep)
                        }
                        else -> {
                            if (m == 0) {
                                return null
                            }
                            s = s.next()
                            p = ep
                            //goto init;  /* else return match(ms, s+1, ep); */
                            init2 = true
                        }
                    }
                    if (init2 == true) {
                        init = true
                        break@loop
                    } else {
                        break@loop
                    }
                }
            }
            if (init == true) {
                continue
            } else {
                break
            }
        }
        return null //FIXME:unreachable
    }

    private fun lmemfind(s1: CharPtr, l1: Int, s2: CharPtr, l2: Int): CharPtr? { //uint - uint
        var s1: CharPtr = s1
        var l1 = l1
        var l2 = l2
        return if (l2 == 0) {
            s1 // empty strings are everywhere
        } else if (l2 > l1) {
            null // avoids a negative `l1'
        } else {
            var init: CharPtr? = null // to search for a `*s2' inside `s1'
            l2-- // 1st char will be checked by `memchr'
            l1 = l1 - l2 // `s2' cannot be found after that
            while (l1 > 0 && CharPtr.Companion.isNotEqual(CLib.memchr(s1, s2.get(0), l1).also({ init = it }), null)) {
                init = init!!.next() // 1st char is already checked
                if (CLib.memcmp(init, CharPtr.Companion.plus(s2, 1), l2) == 0) {
                    return CharPtr.Companion.minus(init!!, 1)
                } else { // correct `l1' and `s1' to try again
                    l1 -= CharPtr.Companion.minus(init!!, s1) as Int //uint
                    s1 = init!!
                }
            }
            null // not found
        }
    }

    private fun push_onecapture(ms: MatchState, i: Int, s: CharPtr?, e: CharPtr?) {
        if (i >= ms.level) {
            if (i == 0) { // ms.level == 0, too
                LuaAPI.lua_pushlstring(ms.L!!, s, CharPtr.Companion.minus(e!!, s!!)) // add whole match  - (uint)
            } else {
                LuaAuxLib.luaL_error(ms.L, CharPtr.Companion.toCharPtr("invalid capture index"))
            }
        } else {
            val l = ms.capture[i]!!.len //ptrdiff_t - Int32
            if (l == CAP_UNFINISHED) {
                LuaAuxLib.luaL_error(ms.L, CharPtr.Companion.toCharPtr("unfinished capture"))
            }
            if (l == CAP_POSITION) {
                LuaAPI.lua_pushinteger(ms.L!!, CharPtr.Companion.minus(ms.capture[i]!!.init!!, ms.src_init!!) + 1)
            } else {
                LuaAPI.lua_pushlstring(ms.L!!, ms.capture[i]!!.init, l) //(uint)
            }
        }
    }

    private fun push_captures(ms: MatchState, s: CharPtr?, e: CharPtr?): Int {
        var i: Int
        val nlevels = if (ms.level == 0 && CharPtr.Companion.isNotEqual(s, null)) 1 else ms.level
        LuaAuxLib.luaL_checkstack(ms.L, nlevels, CharPtr.Companion.toCharPtr("too many captures"))
        i = 0
        while (i < nlevels) {
            push_onecapture(ms, i, s, e)
            i++
        }
        return nlevels // number of strings pushed
    }

    private fun str_find_aux(L: lua_State, find: Int): Int {
        val l1 = IntArray(1) //uint
        val l2 = IntArray(1) //uint
        val s: CharPtr = LuaAuxLib.luaL_checklstring(L, 1, l1) //out
        var p: CharPtr = LuaAuxLib.luaL_checklstring(L, 2, l2) //out
        var init = posrelat(LuaAuxLib.luaL_optinteger(L, 3, 1), l1[0]) - 1 //ptrdiff_t - Int32
        if (init < 0) {
            init = 0
        } else if (init > l1[0]) { //uint
            init = l1[0] //ptrdiff_t - Int32
        }
        if (find != 0 && (LuaAPI.lua_toboolean(L, 4) != 0 || CharPtr.Companion.isEqual(
                CLib.strpbrk(
                    p,
                    CharPtr.Companion.toCharPtr(SPECIALS)
                ), null
            ))
        ) { // explicit request?
// or no special characters?
// do a plain search
            val s2: CharPtr? = lmemfind(
                CharPtr.Companion.plus(s, init),
                (l1[0] - init),
                p,
                l2[0]
            ) //uint - uint
            if (CharPtr.Companion.isNotEqual(s2, null)) {
                LuaAPI.lua_pushinteger(L, CharPtr.Companion.minus(s2!!, s) + 1)
                LuaAPI.lua_pushinteger(L, (CharPtr.Companion.minus(s2!!, s) + l2[0]) as Int)
                return 2
            }
        } else {
            val ms = MatchState()
            var anchor = 0
            if (p.get(0) == '^') {
                p = p.next()
                anchor = 1
            }
            var s1: CharPtr = CharPtr.Companion.plus(s, init)
            ms.L = L
            ms.src_init = s
            ms.src_end = CharPtr.Companion.plus(s, l1[0])
            do {
                var res: CharPtr? = null
                ms.level = 0
                if (CharPtr.Companion.isNotEqual(match(ms, s1, p).also({ res = it }), null)) {
                    return if (find != 0) {
                        LuaAPI.lua_pushinteger(L, CharPtr.Companion.minus(s1, s) + 1) // start
                        LuaAPI.lua_pushinteger(L, CharPtr.Companion.minus(res!!, s)) // end
                        push_captures(ms, null, null) + 2
                    } else {
                        push_captures(ms, s1, res)
                    }
                }
            } while (CharPtr.Companion.lessEqual(s1.next().also({ s1 = it }), ms.src_end!!) && anchor == 0)
        }
        LuaAPI.lua_pushnil(L) // not found
        return 1
    }

    fun str_find(L: lua_State): Int {
        return str_find_aux(L, 1)
    }

    fun str_match(L: lua_State): Int {
        return str_find_aux(L, 0)
    }

    fun gmatch_aux(L: lua_State?): Int {
        val ms = MatchState()
        val ls = IntArray(1) //uint
        val s: CharPtr? = LuaAPI.lua_tolstring(L!!, Lua.lua_upvalueindex(1), ls) //out
        val p: CharPtr = Lua.lua_tostring(L, Lua.lua_upvalueindex(2))
        var src: CharPtr
        ms.L = L
        ms.src_init = s
        ms.src_end = CharPtr.Companion.plus(s, ls[0])
        src = CharPtr.Companion.plus(s, LuaAPI.lua_tointeger(L!!, Lua.lua_upvalueindex(3)))
        while (CharPtr.Companion.lessEqual(src, ms.src_end!!)) {
            //(uint)
            var e: CharPtr? = null
            ms.level = 0
            if (CharPtr.Companion.isNotEqual(match(ms, src, p).also({ e = it }), null)) {
                var newstart: Int = CharPtr.Companion.minus(e!!, s!!) //lua_Integer - Int32
                if (CharPtr.Companion.isEqual(e, src)) {
                    newstart++ // empty match? go at least one position
                }
                LuaAPI.lua_pushinteger(L, newstart)
                LuaAPI.lua_replace(L, Lua.lua_upvalueindex(3))
                return push_captures(ms, src, e)
            }
            src = src.next()
        }
        return 0 // not found
    }

    fun gmatch(L: lua_State?): Int {
        LuaAuxLib.luaL_checkstring(L, 1)
        LuaAuxLib.luaL_checkstring(L, 2)
        LuaAPI.lua_settop(L!!, 2)
        LuaAPI.lua_pushinteger(L!!, 0)
        LuaAPI.lua_pushcclosure(L!!, LuaStrLib_delegate("gmatch_aux"), 3)
        return 1
    }

    fun gfind_nodef(L: lua_State?): Int {
        return LuaAuxLib.luaL_error(
            L,
            CharPtr.Companion.toCharPtr(
                LuaConf.LUA_QL("string.gfind").toString() + " was renamed to " + LuaConf.LUA_QL("string.gmatch")
            )
        )
    }

    private fun add_s(ms: MatchState, b: luaL_Buffer, s: CharPtr, e: CharPtr) {
        val l = IntArray(1) //uint
        var i: Int
        val news: CharPtr? = LuaAPI.lua_tolstring(ms.L!!, 3, l) //out
        i = 0
        while (i < l[0]) {
            if (news!!.get(i) != L_ESC) {
                LuaAuxLib.luaL_addchar(b, news!!.get(i))
            } else {
                i++ // skip ESC
                if (!CLib.isdigit((news!!.get(i) as Byte).toInt())) {
                    LuaAuxLib.luaL_addchar(b, news!!.get(i))
                } else if (news.get(i) == '0') {
                    LuaAuxLib.luaL_addlstring(b, s, CharPtr.Companion.minus(e, s)) //(uint)
                } else {
                    push_onecapture(ms, news!!.get(i) - '1', s, e)
                    LuaAuxLib.luaL_addvalue(b) // add capture to accumulated result
                }
            }
            i++
        }
    }

    private fun add_value(ms: MatchState, b: luaL_Buffer, s: CharPtr?, e: CharPtr?) {
        val L: lua_State? = ms.L
        when (LuaAPI.lua_type(L!!, 3)) {
            Lua.LUA_TNUMBER, Lua.LUA_TSTRING -> {
                add_s(ms, b, s!!, e!!)
                return
            }
            Lua.LUA_TFUNCTION -> {
                val n: Int
                LuaAPI.lua_pushvalue(L!!, 3)
                n = push_captures(ms, s, e)
                LuaAPI.lua_call(L!!, n, 1)
            }
            Lua.LUA_TTABLE -> {
                push_onecapture(ms, 0, s, e)
                LuaAPI.lua_gettable(L!!, 3)
            }
        }
        if (LuaAPI.lua_toboolean(L!!, -1) == 0) { // nil or false?
            Lua.lua_pop(L, 1)
            LuaAPI.lua_pushlstring(L!!, s, CharPtr.Companion.minus(e!!, s!!)) // keep original text  - (uint)
        } else if (LuaAPI.lua_isstring(L!!, -1) == 0) {
            LuaAuxLib.luaL_error(
                L,
                CharPtr.Companion.toCharPtr("invalid replacement value (a %s)"),
                LuaAuxLib.luaL_typename(L, -1)
            )
        }
        LuaAuxLib.luaL_addvalue(b) // add result to accumulator
    }

    fun str_gsub(L: lua_State?): Int {
        val srcl = IntArray(1) //uint
        var src: CharPtr? = LuaAuxLib.luaL_checklstring(L, 1, srcl) //out
        var p: CharPtr = LuaAuxLib.luaL_checkstring(L, 2)
        val tr: Int = LuaAPI.lua_type(L!!, 3)
        val max_s: Int = LuaAuxLib.luaL_optint(L, 4, (srcl[0] + 1))
        var anchor = 0
        if (p.get(0) == '^') {
            p = p.next()
            anchor = 1
        }
        var n = 0
        val ms = MatchState()
        val b = luaL_Buffer()
        LuaAuxLib.luaL_argcheck(
            L,
            tr == Lua.LUA_TNUMBER || tr == Lua.LUA_TSTRING || tr == Lua.LUA_TFUNCTION || tr == Lua.LUA_TTABLE,
            3,
            "string/function/table expected"
        )
        LuaAuxLib.luaL_buffinit(L, b)
        ms.L = L
        ms.src_init = src
        ms.src_end = CharPtr.Companion.plus(src, srcl[0])
        while (n < max_s) {
            var e: CharPtr?
            ms.level = 0
            e = match(ms, src, p)
            if (CharPtr.Companion.isNotEqual(e, null)) {
                n++
                add_value(ms, b, src, e)
            }
            if (CharPtr.Companion.isNotEqual(e, null) && CharPtr.Companion.greaterThan(e!!, src!!)) { // non empty match?
                src = e // skip it
            } else if (CharPtr.Companion.lessThan(src!!, ms.src_end!!)) {
                val c: Char = src.get(0)
                src = src.next()
                LuaAuxLib.luaL_addchar(b, c)
            } else {
                break
            }
            if (anchor != 0) {
                break
            }
        }
        LuaAuxLib.luaL_addlstring(b, src, CharPtr.Companion.minus(ms.src_end!!, src!!)) //(uint)
        LuaAuxLib.luaL_pushresult(b)
        LuaAPI.lua_pushinteger(L!!, n) // number of substitutions
        return 2
    }

    // }======================================================
// maximum size of each formatted item (> len(format('%99.99f', -1e308)))
    const val MAX_ITEM = 512
    // valid flags in a format specification
    const val FLAGS = "-+ #0"
    //
//		 ** maximum size of each format specification (such as '%-099.99d')
//		 ** (+10 accounts for %99.99x plus margin of error)
//
    val MAX_FORMAT: Int = FLAGS.length + 1 + (LuaConf.LUA_INTFRMLEN.length + 1) + 10

    private fun addquoted(L: lua_State?, b: luaL_Buffer, arg: Int) {
        val l = IntArray(1) //uint
        var s: CharPtr = LuaAuxLib.luaL_checklstring(L, arg, l) //out
        LuaAuxLib.luaL_addchar(b, '"')
        while (l[0]-- != 0) {
            when (s.get(0)) {
                '"', '\\', '\n' -> {
                    LuaAuxLib.luaL_addchar(b, '\\')
                    LuaAuxLib.luaL_addchar(b, s.get(0))
                }
                '\r' -> {
                    LuaAuxLib.luaL_addlstring(b, CharPtr.Companion.toCharPtr("\\r"), 2)
                }
                '\u0000' -> {
                    LuaAuxLib.luaL_addlstring(b, CharPtr.Companion.toCharPtr("\\000"), 4)
                }
                else -> {
                    LuaAuxLib.luaL_addchar(b, s.get(0))
                }
            }
            s = s.next()
        }
        LuaAuxLib.luaL_addchar(b, '"')
    }

    private fun scanformat(L: lua_State?, strfrmt: CharPtr, form: CharPtr): CharPtr {
        var form: CharPtr = form
        var p: CharPtr = strfrmt
        while (p.get(0) != '\u0000' && CharPtr.Companion.isNotEqual(
                CLib.strchr(
                    CharPtr.Companion.toCharPtr(FLAGS),
                    p.get(0)
                ), null
            )
        ) {
            p = p.next() // skip flags
        }
        if (CharPtr.Companion.minus(p, strfrmt) as Int >= FLAGS.length + 1) { //uint
            LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("invalid format (repeated flags)"))
        }
        if (CLib.isdigit((p.get(0) as Byte).toInt())) {
            p = p.next() // skip width
        }
        if (CLib.isdigit((p.get(0) as Byte).toInt())) {
            p = p.next() // (2 digits at most)
        }
        if (p.get(0) == '.') {
            p = p.next()
            if (CLib.isdigit((p.get(0) as Byte).toInt())) {
                p = p.next() // skip precision
            }
            if (CLib.isdigit((p.get(0) as Byte).toInt())) {
                p = p.next() // (2 digits at most)
            }
        }
        if (CLib.isdigit((p.get(0) as Byte).toInt())) {
            LuaAuxLib.luaL_error(L, CharPtr.Companion.toCharPtr("invalid format (width or precision too long)"))
        }
        form.set(0, '%')
        form = form.next()
        CLib.strncpy(form, strfrmt, CharPtr.Companion.minus(p, strfrmt) + 1)
        form = CharPtr.Companion.plus(form, CharPtr.Companion.minus(p, strfrmt) + 1)
        form.set(0, '\u0000')
        return p
    }

    private fun addintlen(form: CharPtr) {
        val l: Int = CLib.strlen(form) //(uint) - uint
        val spec: Char = form.get(l - 1)
        CLib.strcpy(CharPtr.Companion.plus(form, l - 1), CharPtr.Companion.toCharPtr(LuaConf.LUA_INTFRMLEN))
        form.set(l + (LuaConf.LUA_INTFRMLEN.length + 1) - 2, spec)
        form.set(l + (LuaConf.LUA_INTFRMLEN.length + 1) - 1, '\u0000')
    }

    fun str_format(L: lua_State?): Int {
        var arg = 1
        val sfl = IntArray(1) //uint
        var strfrmt: CharPtr = LuaAuxLib.luaL_checklstring(L, arg, sfl) //out
        val strfrmt_end: CharPtr = CharPtr.Companion.plus(strfrmt, sfl[0])
        val b = luaL_Buffer()
        LuaAuxLib.luaL_buffinit(L, b)
        loop@ while (CharPtr.Companion.lessThan(strfrmt, strfrmt_end)) {
            if (strfrmt.get(0) != L_ESC) {
                LuaAuxLib.luaL_addchar(b, strfrmt.get(0))
                strfrmt = strfrmt.next()
            } else if (strfrmt.get(1) == L_ESC) {
                LuaAuxLib.luaL_addchar(b, strfrmt.get(0)) // %%
                strfrmt = CharPtr.Companion.plus(strfrmt, 2)
            } else { // format item
                strfrmt = strfrmt.next()
                val form: CharPtr =
                    CharPtr.Companion.toCharPtr(CharArray(MAX_FORMAT)) // to store the format (`%...')
                val buff: CharPtr =
                    CharPtr.Companion.toCharPtr(CharArray(MAX_ITEM)) // to store the formatted item
                arg++
                strfrmt = scanformat(L, strfrmt, form)
                val ch: Char = strfrmt.get(0)
                strfrmt = strfrmt.next()
                when (ch) {
                    'c' -> {
                        CLib.sprintf(buff, form, LuaAuxLib.luaL_checknumber(L, arg) as Int)
                    }
                    'd', 'i' -> {
                        addintlen(form)
                        CLib.sprintf(buff, form, LuaAuxLib.luaL_checknumber(L, arg) as Long) //LUA_INTFRM_T - Int64
                    }
                    'o', 'u', 'x', 'X' -> {
                        addintlen(form)
                        CLib.sprintf(
                            buff,
                            form,
                            LuaAuxLib.luaL_checknumber(L, arg) as Long
                        ) //UNSIGNED_LUA_INTFRM_T - UInt64
                    }
                    'e', 'E', 'f', 'g', 'G' -> {
                        CLib.sprintf(buff, form, LuaAuxLib.luaL_checknumber(L, arg))
                    }
                    'q' -> {
                        addquoted(L, b, arg)
                        continue@loop  // skip the 'addsize' at the end
                    }
                    's' -> {
                        val l = IntArray(1) //uint
                        val s: CharPtr = LuaAuxLib.luaL_checklstring(L, arg, l) //out
                        if (CharPtr.Companion.isEqual(
                                CLib.strchr(form, '.'),
                                null
                            ) && l[0] >= 100
                        ) { //                                     no precision and string is too long to be formatted;
//									 keep original string
                            LuaAPI.lua_pushvalue(L!!, arg)
                            LuaAuxLib.luaL_addvalue(b)
                            continue@loop  // skip the `addsize' at the end
                        } else {
                            CLib.sprintf(buff, form, s)
                            break@loop
                        }
                    }
                    else -> {
                        // also treat cases `pnLlh'
                        return LuaAuxLib.luaL_error(
                            L,
                            CharPtr.Companion.toCharPtr(
                                "invalid option " + LuaConf.LUA_QL("%%%c") + " to " + LuaConf.LUA_QL("format")
                            ),
                            strfrmt.get(-1)
                        )
                    }
                }
                LuaAuxLib.luaL_addlstring(b, buff, CLib.strlen(buff)) //(uint)
            }
        }
        LuaAuxLib.luaL_pushresult(b)
        return 1
    }

    private val strlib: Array<luaL_Reg> = arrayOf<luaL_Reg>(
        luaL_Reg(CharPtr.Companion.toCharPtr("byte"), LuaStrLib_delegate("str_byte")),
        luaL_Reg(CharPtr.Companion.toCharPtr("char"), LuaStrLib_delegate("str_char")),
        luaL_Reg(CharPtr.Companion.toCharPtr("dump"), LuaStrLib_delegate("str_dump")),
        luaL_Reg(CharPtr.Companion.toCharPtr("find"), LuaStrLib_delegate("str_find")),
        luaL_Reg(CharPtr.Companion.toCharPtr("format"), LuaStrLib_delegate("str_format")),
        luaL_Reg(CharPtr.Companion.toCharPtr("gfind"), LuaStrLib_delegate("gfind_nodef")),
        luaL_Reg(CharPtr.Companion.toCharPtr("gmatch"), LuaStrLib_delegate("gmatch")),
        luaL_Reg(CharPtr.Companion.toCharPtr("gsub"), LuaStrLib_delegate("str_gsub")),
        luaL_Reg(CharPtr.Companion.toCharPtr("len"), LuaStrLib_delegate("str_len")),
        luaL_Reg(CharPtr.Companion.toCharPtr("lower"), LuaStrLib_delegate("str_lower")),
        luaL_Reg(CharPtr.Companion.toCharPtr("match"), LuaStrLib_delegate("str_match")),
        luaL_Reg(CharPtr.Companion.toCharPtr("rep"), LuaStrLib_delegate("str_rep")),
        luaL_Reg(CharPtr.Companion.toCharPtr("reverse"), LuaStrLib_delegate("str_reverse")),
        luaL_Reg(CharPtr.Companion.toCharPtr("sub"), LuaStrLib_delegate("str_sub")),
        luaL_Reg(CharPtr.Companion.toCharPtr("upper"), LuaStrLib_delegate("str_upper")),
        luaL_Reg(null, null)
    )

    private fun createmetatable(L: lua_State?) {
        LuaAPI.lua_createtable(L!!, 0, 1) // create metatable for strings
        Lua.lua_pushliteral(L, CharPtr.Companion.toCharPtr("")) // dummy string
        LuaAPI.lua_pushvalue(L!!, -2)
        LuaAPI.lua_setmetatable(L!!, -2) // set string metatable
        Lua.lua_pop(L, 1) // pop dummy string
        LuaAPI.lua_pushvalue(L!!, -2) // string library...
        LuaAPI.lua_setfield(L!!, -2, CharPtr.Companion.toCharPtr("__index")) //...is the __index metamethod
        Lua.lua_pop(L, 1) // pop metatable
    }

    //
//		 ** Open string library
//
    fun luaopen_string(L: lua_State?): Int {
        LuaAuxLib.luaL_register(L, CharPtr.Companion.toCharPtr(LuaLib.LUA_STRLIBNAME), strlib)
        ///#if LUA_COMPAT_GFIND
        LuaAPI.lua_getfield(L!!, -1, CharPtr.Companion.toCharPtr("gmatch"))
        LuaAPI.lua_setfield(L!!, -2, CharPtr.Companion.toCharPtr("gfind"))
        ///#endif
        createmetatable(L)
        return 1
    }

    class writer_delegate : lua_Writer {
        override fun exec(L: lua_State, p: CharPtr, sz: Int, ud: Any): Int { //uint
            return writer(L, p, sz, ud, ClassType(ClassType.Companion.TYPE_CHARPTR))
        }
    }

    class MatchState {
        class capture_ {
            var init: CharPtr? = null
            var   /*Int32*/ /*ptrdiff_t*/len = 0
        }

        var src_init /* init of source string */: CharPtr? = null
        var src_end /* end (`\0') of source string */: CharPtr? = null
        var L: lua_State? = null
        var level /* total number of captures (finished or unfinished) */ = 0
        var capture = arrayOfNulls<capture_>(LuaConf.LUA_MAXCAPTURES)

        init {
            for (i in 0 until LuaConf.LUA_MAXCAPTURES) {
                capture[i] = capture_()
            }
        }
    }

    class LuaStrLib_delegate(private val name: String) : lua_CFunction {
        override fun exec(L: lua_State): Int {
            return if ("str_byte" == name) {
                str_byte(L)
            } else if ("str_char" == name) {
                str_char(L)
            } else if ("str_dump" == name) {
                str_dump(L)
            } else if ("str_find" == name) {
                str_find(L)
            } else if ("str_format" == name) {
                str_format(L)
            } else if ("gfind_nodef" == name) {
                gfind_nodef(L)
            } else if ("gmatch" == name) {
                gmatch(L)
            } else if ("str_gsub" == name) {
                str_gsub(L)
            } else if ("str_len" == name) {
                str_len(L)
            } else if ("str_lower" == name) {
                str_lower(L)
            } else if ("str_match" == name) {
                str_match(L)
            } else if ("str_rep" == name) {
                str_rep(L)
            } else if ("str_reverse" == name) {
                str_reverse(L)
            } else if ("str_sub" == name) {
                str_sub(L)
            } else if ("str_upper" == name) {
                str_upper(L)
            } else if ("gmatch_aux" == name) {
                gmatch_aux(L)
            } else {
                0
            }
        }

    }
}