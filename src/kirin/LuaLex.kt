package kirin

import kirin.CLib.CharPtr
import kirin.LuaObject.TString
import kirin.LuaParser.FuncState
import kirin.LuaState.lua_State
import kirin.LuaZIO.Mbuffer
import kirin.LuaZIO.ZIO

//
// ** $Id: llex.c,v 2.20.1.1 2007/12/27 13:02:25 roberto Exp $
// ** Lexical Analyzer
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
//using lua_Number = System.Double;
object LuaLex {
    const val FIRST_RESERVED = 257
    // maximum length of a reserved word
    const val TOKEN_LEN = 9 // "function"
    // number of reserved words
    const val NUM_RESERVED = RESERVED.TK_WHILE - FIRST_RESERVED + 1

    fun next(ls: LexState) {
        ls.current = LuaZIO.zgetc(ls.z)
    }

    fun currIsNewline(ls: LexState): Boolean {
        return ls.current == '\n'.toInt() || ls.current == '\r'.toInt()
    }

    // ORDER RESERVED
    val luaX_tokens = arrayOf(
        "and",
        "break",
        "do",
        "else",
        "elseif",
        "end",
        "false",
        "for",
        "function",
        "if",
        "in",
        "local",
        "nil",
        "not",
        "or",
        "repeat",
        "return",
        "then",
        "true",
        "until",
        "while",
        "..",
        "...",
        "==",
        ">=",
        "<=",
        "~=",
        "<number>",
        "<name>",
        "<string>",
        "<eof>"
    )

    fun save_and_next(ls: LexState) {
        save(ls, ls.current)
        next(ls)
    }

    private fun save(ls: LexState, c: Int) {
        val b = ls.buff
        if (b!!.n + 1 > b.buffsize) {
            val newsize: Int //uint
            if (b.buffsize >= LuaLimits.MAX_SIZET / 2) {
                luaX_lexerror(ls, CharPtr.Companion.toCharPtr("lexical element too long"), 0)
            }
            newsize = b.buffsize * 2
            LuaZIO.luaZ_resizebuffer(ls.L, b, newsize)
        }
        b.buffer!![b.n++] = c.toChar()
    }

    fun luaX_init(L: lua_State?) {
        var i: Int
        i = 0
        while (i < NUM_RESERVED) {
            val ts = LuaString.luaS_new(L, CharPtr.Companion.toCharPtr(luaX_tokens[i]))
            LuaString.luaS_fix(ts) // reserved words are never collected
            LuaLimits.lua_assert(luaX_tokens[i].length + 1 <= TOKEN_LEN)
            ts.getTsv().reserved = LuaLimits.cast_byte(i + 1) // reserved word
            i++
        }
    }

    const val MAXSRC = 80
    fun luaX_token2str(ls: LexState, token: Int): CharPtr? {
        return if (token < FIRST_RESERVED) {
            LuaLimits.lua_assert(token == (token as Byte).toInt())
            if (CLib.iscntrl(token)) LuaObject.luaO_pushfstring(
                ls.L,
                CharPtr.Companion.toCharPtr("char(%d)"),
                token
            ) else LuaObject.luaO_pushfstring(ls.L, CharPtr.Companion.toCharPtr("%c"), token)
        } else {
            CharPtr.Companion.toCharPtr(luaX_tokens[token - FIRST_RESERVED])
        }
    }

    fun txtToken(ls: LexState, token: Int): CharPtr? {
        return when (token) {
            RESERVED.TK_NAME, RESERVED.TK_STRING, RESERVED.TK_NUMBER -> {
                save(ls, '\u0000'.toInt())
                LuaZIO.luaZ_buffer(ls.buff)
            }
            else -> {
                luaX_token2str(ls, token)
            }
        }
    }

    fun luaX_lexerror(ls: LexState, msg: CharPtr?, token: Int) {
        var msg = msg
        val buff: CharPtr = CharPtr.Companion.toCharPtr(CharArray(MAXSRC))
        LuaObject.luaO_chunkid(buff, LuaObject.getstr(ls.source), MAXSRC)
        msg = LuaObject.luaO_pushfstring(ls.L, CharPtr.Companion.toCharPtr("%s:%d: %s"), buff, ls.linenumber, msg)
        if (token != 0) {
            LuaObject.luaO_pushfstring(
                ls.L,
                CharPtr.Companion.toCharPtr("%s near " + LuaConf.getLUA_QS()),
                msg,
                txtToken(ls, token)
            )
        }
        LuaDo.luaD_throw(ls.L, Lua.LUA_ERRSYNTAX)
    }

    fun luaX_syntaxerror(ls: LexState, msg: CharPtr?) {
        luaX_lexerror(ls, msg, ls.t.token)
    }

    fun luaX_newstring(ls: LexState, str: CharPtr?, l: Int): TString { //uint
        val L = ls.L
        val ts = LuaString.luaS_newlstr(L, str, l)
        val o = LuaTable.luaH_setstr(L, ls.fs!!.h, ts) // entry for `str'
        if (LuaObject.ttisnil(o)) {
            LuaObject.setbvalue(o, 1) // make sure `str' will not be collected
        }
        return ts
    }

    private fun inclinenumber(ls: LexState) {
        val old = ls.current
        LuaLimits.lua_assert(currIsNewline(ls))
        next(ls) // skip `\n' or `\r'
        if (currIsNewline(ls) && ls.current != old) {
            next(ls) // skip `\n\r' or `\r\n'
        }
        if (++ls.linenumber >= LuaLimits.MAX_INT) {
            luaX_syntaxerror(ls, CharPtr.Companion.toCharPtr("chunk has too many lines"))
        }
    }

    fun luaX_setinput(L: lua_State?, ls: LexState, z: ZIO?, source: TString?) {
        ls.decpoint = '.'
        ls.L = L
        ls.lookahead.token = RESERVED.TK_EOS // no look-ahead token
        ls.z = z
        ls.fs = null
        ls.linenumber = 1
        ls.lastline = 1
        ls.source = source
        LuaZIO.luaZ_resizebuffer(ls.L, ls.buff, LuaLimits.LUA_MINBUFFER) // initialize buffer
        next(ls) // read first char
    }

    //
//		 ** =======================================================
//		 ** LEXICAL ANALYZER
//		 ** =======================================================
//
    private fun check_next(ls: LexState, set: CharPtr): Int {
        if (CharPtr.Companion.isEqual(CLib.strchr(set, ls.current.toChar()), null)) {
            return 0
        }
        save_and_next(ls)
        return 1
    }

    private fun buffreplace(ls: LexState, from: Char, to: Char) {
        var n = LuaZIO.luaZ_bufflen(ls.buff) //uint
        val p = LuaZIO.luaZ_buffer(ls.buff)
        while (n-- != 0) {
            if (p!![n] == from) {
                p[n] = to
            }
        }
    }

    private fun trydecpoint(
        ls: LexState,
        seminfo: SemInfo
    ) { // format error: try to update decimal point separator
// todo: add proper support for localeconv - mjf
//lconv cv = localeconv();
        val old = ls.decpoint
        ls.decpoint = '.' // (cv ? cv.decimal_point[0] : '.');
        buffreplace(ls, old, ls.decpoint) // try updated decimal separator
        val r = DoubleArray(1)
        r[0] = seminfo.r
        val ret = LuaObject.luaO_str2d(LuaZIO.luaZ_buffer(ls.buff)!!, r)
        seminfo.r = r[0]
        if (ret == 0) { // format error with correct decimal point: no more options
            buffreplace(ls, ls.decpoint, '.') // undo change (for error message)
            luaX_lexerror(ls, CharPtr.Companion.toCharPtr("malformed number"), RESERVED.TK_NUMBER)
        }
    }

    // LUA_NUMBER
    private fun read_numeral(ls: LexState, seminfo: SemInfo) {
        LuaLimits.lua_assert(CLib.isdigit(ls.current))
        do {
            save_and_next(ls)
        } while (CLib.isdigit(ls.current) || ls.current == '.'.toInt())
        if (check_next(ls, CharPtr.Companion.toCharPtr("Ee")) != 0) { // `E'?
            check_next(ls, CharPtr.Companion.toCharPtr("+-")) // optional exponent sign
        }
        while (CLib.isalnum(ls.current) || ls.current == '_'.toInt()) {
            save_and_next(ls)
        }
        save(ls, '\u0000'.toInt())
        buffreplace(ls, '.', ls.decpoint) // follow locale for decimal point
        val r = DoubleArray(1)
        r[0] = seminfo.r
        val ret = LuaObject.luaO_str2d(LuaZIO.luaZ_buffer(ls.buff)!!, r)
        seminfo.r = r[0]
        if (ret == 0) { // format error?
            trydecpoint(ls, seminfo) // try to update decimal point separator
        }
    }

    private fun skip_sep(ls: LexState): Int {
        var count = 0
        val s = ls.current
        LuaLimits.lua_assert(s == '['.toInt() || s == ']'.toInt())
        save_and_next(ls)
        while (ls.current == '='.toInt()) {
            save_and_next(ls)
            count++
        }
        return if (ls.current == s) count else -count - 1
    }

    private fun read_long_string(ls: LexState, seminfo: SemInfo?, sep: Int) { //int cont = 0;
//(void)(cont);  /* avoid warnings when `cont' is not used */
        save_and_next(ls) // skip 2nd `['
        if (currIsNewline(ls)) { // string starts with a newline?
            inclinenumber(ls) // skip it
        }
        loop@ while (true) {
            var endloop = false
            when (ls.current) {
                LuaZIO.EOZ -> {
                    luaX_lexerror(
                        ls,
                        if (seminfo != null) CharPtr.Companion.toCharPtr("unfinished long string") else CharPtr.Companion.toCharPtr(
                            "unfinished long comment"
                        ),
                        RESERVED.TK_EOS
                    )
                }
                ']'.toInt() -> {
                    if (skip_sep(ls) == sep) {
                        save_and_next(ls) // skip 2nd `]'
                        ///#if defined(LUA_COMPAT_LSTR) && LUA_COMPAT_LSTR == 2
//          cont--;
//          if (sep == 0 && cont >= 0) break;
///#endif
//goto endloop;
                        endloop = true
                        break@loop
                    }
                }
                '\n'.toInt(), '\r'.toInt() -> {
                    save(ls, '\n'.toInt())
                    inclinenumber(ls)
                    if (seminfo == null) {
                        LuaZIO.luaZ_resetbuffer(ls.buff) // avoid wasting space
                    }
                }
                else -> {
                    if (seminfo != null) {
                        save_and_next(ls)
                    } else {
                        next(ls)
                    }
                }
            }
            if (endloop) {
                break
            }
        }
        //endloop:
        if (seminfo != null) {
            seminfo.ts = luaX_newstring(
                ls,
                CharPtr.Companion.plus(LuaZIO.luaZ_buffer(ls.buff), 2 + sep),
                LuaZIO.luaZ_bufflen(ls.buff) - 2 * (2 + sep)
            ) //(uint)
        }
    }

    private fun read_string(ls: LexState, del: Int, seminfo: SemInfo) {
        save_and_next(ls)
        loop@ while (ls.current != del) {
            when (ls.current) {
                LuaZIO.EOZ -> {
                    luaX_lexerror(ls, CharPtr.Companion.toCharPtr("unfinished string"), RESERVED.TK_EOS)
                    continue@loop  // to avoid warnings
                }
                '\n'.toInt(), '\r'.toInt() -> {
                    luaX_lexerror(
                        ls,
                        CharPtr.Companion.toCharPtr("unfinished string"),
                        RESERVED.TK_STRING
                    )
                    continue@loop  // to avoid warnings
                }
                '\\'.toInt() -> {
                    var c: Int
                    next(ls) // do not save the `\'
                    when (ls.current) {
                        'a'.toInt() -> {
                            c = '\u0007'.toInt() //'\a'; FIXME:
                        }
                        'b'.toInt() -> {
                            c = '\b'.toInt()
                        }
                        'f'.toInt() -> {
                            c = 0x0C; //'\f'.toInt() //FIXME:
                        }
                        'n'.toInt() -> {
                            c = '\n'.toInt()
                        }
                        'r'.toInt() -> {
                            c = '\r'.toInt()
                        }
                        't'.toInt() -> {
                            c = '\t'.toInt()
                        }
                        'v'.toInt() -> {
                            c = '\u000B'.toInt() //'\v'; FIXME:
                        }
                        '\n'.toInt(), '\r'.toInt() -> {
                            save(ls, '\n'.toInt())
                            inclinenumber(ls)
                            continue@loop
                        }
                        LuaZIO.EOZ -> {
                            continue@loop  // will raise an error next loop
                        }
                        else -> {
                            if (!CLib.isdigit(ls.current)) {
                                save_and_next(ls) // handles \\, \", \', and \?
                            } else { // \xxx
                                var i = 0
                                c = 0
                                do {
                                    c = 10 * c + (ls.current - '0'.toInt())
                                    next(ls)
                                } while (++i < 3 && CLib.isdigit(ls.current))
                                //System.Byte.MaxValue
                                if (c > Byte.MAX_VALUE) {
                                    luaX_lexerror(
                                        ls,
                                        CharPtr.Companion.toCharPtr("escape sequence too large"),
                                        RESERVED.TK_STRING
                                    )
                                }
                                save(ls, c)
                            }
                            continue@loop
                        }
                    }
                    save(ls, c)
                    next(ls)
                    continue@loop
                }
                else -> {
                    save_and_next(ls)
                }
            }
        }
        save_and_next(ls) // skip delimiter
        seminfo.ts = luaX_newstring(
            ls,
            CharPtr.Companion.plus(LuaZIO.luaZ_buffer(ls.buff), 1),
            LuaZIO.luaZ_bufflen(ls.buff) - 2
        )
    }

    private fun llex(ls: LexState, seminfo: SemInfo): Int {
        LuaZIO.luaZ_resetbuffer(ls.buff)
        loop@ while (true) {
            when (ls.current) {
                '\n'.toInt(), '\r'.toInt() -> {
                    inclinenumber(ls)
                    continue@loop
                }
                '-'.toInt() -> {
                    next(ls)
                    if (ls.current != '-'.toInt()) {
                        return '-'.toInt()
                    }
                    // else is a comment
                    next(ls)
                    if (ls.current == '['.toInt()) {
                        val sep = skip_sep(ls)
                        LuaZIO.luaZ_resetbuffer(ls.buff) // `skip_sep' may dirty the buffer
                        if (sep >= 0) {
                            read_long_string(ls, null, sep) // long comment
                            LuaZIO.luaZ_resetbuffer(ls.buff)
                            continue@loop
                        }
                    }
                    // else short comment
                    while (!currIsNewline(ls) && ls.current != LuaZIO.EOZ) {
                        next(ls)
                    }
                    continue@loop
                }
                '['.toInt() -> {
                    val sep = skip_sep(ls)
                    if (sep >= 0) {
                        read_long_string(ls, seminfo, sep)
                        return RESERVED.TK_STRING
                    } else if (sep == -1) {
                        return '['.toInt()
                    } else {
                        luaX_lexerror(
                            ls,
                            CharPtr.Companion.toCharPtr("invalid long string delimiter"),
                            RESERVED.TK_STRING
                        )
                    }
                }
                '='.toInt() -> {
                    next(ls)
                    return if (ls.current != '='.toInt()) {
                        '='.toInt()
                    } else {
                        next(ls)
                        RESERVED.TK_EQ
                    }
                }
                '<'.toInt() -> {
                    next(ls)
                    return if (ls.current != '='.toInt()) {
                        '<'.toInt()
                    } else {
                        next(ls)
                        RESERVED.TK_LE
                    }
                }
                '>'.toInt() -> {
                    next(ls)
                    return if (ls.current != '='.toInt()) {
                        '>'.toInt()
                    } else {
                        next(ls)
                        RESERVED.TK_GE
                    }
                }
                '~'.toInt() -> {
                    next(ls)
                    return if (ls.current != '='.toInt()) {
                        '~'.toInt()
                    } else {
                        next(ls)
                        RESERVED.TK_NE
                    }
                }
                '"'.toInt(), '\''.toInt() -> {
                    read_string(ls, ls.current, seminfo)
                    return RESERVED.TK_STRING
                }
                '.'.toInt() -> {
                    save_and_next(ls)
                    return if (check_next(ls, CharPtr.Companion.toCharPtr(".")) != 0) {
                        if (check_next(ls, CharPtr.Companion.toCharPtr(".")) != 0) {
                            RESERVED.TK_DOTS //...
                        } else {
                            RESERVED.TK_CONCAT //..
                        }
                    } else if (!CLib.isdigit(ls.current)) {
                        '.'.toInt()
                    } else {
                        read_numeral(ls, seminfo)
                        RESERVED.TK_NUMBER
                    }
                }
                LuaZIO.EOZ -> {
                    return RESERVED.TK_EOS
                }
                else -> {
                    return if (CLib.isspace(ls.current)) {
                        LuaLimits.lua_assert(!currIsNewline(ls))
                        next(ls)
                        continue@loop
                    } else if (CLib.isdigit(ls.current)) {
                        read_numeral(ls, seminfo)
                        RESERVED.TK_NUMBER
                    } else if (CLib.isalpha(ls.current) || ls.current == '_'.toInt()) { // identifier or reserved word
                        val ts: TString
                        do {
                            save_and_next(ls)
                        } while (CLib.isalnum(ls.current) || ls.current == '_'.toInt())
                        ts = luaX_newstring(ls, LuaZIO.luaZ_buffer(ls.buff), LuaZIO.luaZ_bufflen(ls.buff))
                        if (ts.getTsv().reserved > 0) { // reserved word?
                            ts.getTsv().reserved - 1 + FIRST_RESERVED
                        } else {
                            seminfo.ts = ts
                            RESERVED.TK_NAME
                        }
                    } else {
                        val c = ls.current
                        next(ls)
                        c // single-char tokens (+ - /...)
                    }
                }
            }
        }
    }

    fun luaX_next(ls: LexState) {
        ls.lastline = ls.linenumber
        if (ls.lookahead.token != RESERVED.TK_EOS) { // is there a look-ahead token?
            ls.t = Token(ls.lookahead) // use this one
            ls.lookahead.token = RESERVED.TK_EOS // and discharge it
        } else {
            ls.t.token = llex(ls, ls.t.seminfo) // read next token
        }
    }

    fun luaX_lookahead(ls: LexState) {
        LuaLimits.lua_assert(ls.lookahead.token == RESERVED.TK_EOS)
        ls.lookahead.token = llex(ls, ls.lookahead.seminfo)
    }

    /*
	 * WARNING: if you change the order of this enumeration,
	 * grep "ORDER RESERVED"
	 */
    object RESERVED {
        /* terminal symbols denoted by reserved words */
        const val TK_AND = FIRST_RESERVED
        const val TK_BREAK = FIRST_RESERVED + 1
        const val TK_DO = FIRST_RESERVED + 2
        const val TK_ELSE = FIRST_RESERVED + 3
        const val TK_ELSEIF = FIRST_RESERVED + 4
        const val TK_END = FIRST_RESERVED + 5
        const val TK_FALSE = FIRST_RESERVED + 6
        const val TK_FOR = FIRST_RESERVED + 7
        const val TK_FUNCTION = FIRST_RESERVED + 8
        const val TK_IF = FIRST_RESERVED + 9
        const val TK_IN = FIRST_RESERVED + 10
        const val TK_LOCAL = FIRST_RESERVED + 11
        const val TK_NIL = FIRST_RESERVED + 12
        const val TK_NOT = FIRST_RESERVED + 13
        const val TK_OR = FIRST_RESERVED + 14
        const val TK_REPEAT = FIRST_RESERVED + 15
        const val TK_RETURN = FIRST_RESERVED + 16
        const val TK_THEN = FIRST_RESERVED + 17
        const val TK_TRUE = FIRST_RESERVED + 18
        const val TK_UNTIL = FIRST_RESERVED + 19
        const val TK_WHILE = FIRST_RESERVED + 20
        /* other terminal symbols */
        const val TK_CONCAT = FIRST_RESERVED + 21
        const val TK_DOTS = FIRST_RESERVED + 22
        const val TK_EQ = FIRST_RESERVED + 23
        const val TK_GE = FIRST_RESERVED + 24
        const val TK_LE = FIRST_RESERVED + 25
        const val TK_NE = FIRST_RESERVED + 26
        const val TK_NUMBER = FIRST_RESERVED + 27
        const val TK_NAME = FIRST_RESERVED + 28
        const val TK_STRING = FIRST_RESERVED + 29
        const val TK_EOS = FIRST_RESERVED + 30
    }

    class SemInfo {
        var r /*Double*/ /*lua_Number*/ = 0.0
        var ts: TString? = null

        constructor() {}
        constructor(copy: SemInfo) {
            r = copy.r
            ts = copy.ts
        }
    } /* semantics information */

    class Token {
        var token = 0
        var seminfo = SemInfo()

        constructor() {}
        constructor(copy: Token) {
            token = copy.token
            seminfo = SemInfo(copy.seminfo)
        }
    }

    class LexState {
        var current /* current character (charint) */ = 0
        var linenumber /* input line counter */ = 0
        var lastline /* line of last token `consumed' */ = 0
        var t = Token() /* current token */
        var lookahead = Token() /* look ahead token */
        var fs /* `FuncState' is private to the parser */: FuncState? = null
        var L: lua_State? = null
        var z /* input stream */: ZIO? = null
        var buff /* buffer for tokens */: Mbuffer? = null
        var source /* current source name */: TString? = null
        var decpoint /* locale decimal point */ = 0.toChar()
    }
}