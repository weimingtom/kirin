package kirin

import java.lang.Exception

object CLib {
    // misc stuff needed for the compile
    fun isalpha(c: Char): Boolean {
        return Character.isLetter(c)
    }

    fun iscntrl(c: Char): Boolean {
        return Character.isISOControl(c)
    }

    fun isdigit(c: Char): Boolean {
        return Character.isDigit(c)
    }

    fun islower(c: Char): Boolean {
        return Character.isLowerCase(c)
    }

    fun ispunct(c: Char): Boolean {
        return ClassType.Companion.IsPunctuation(c)
    }

    fun isspace(c: Char): Boolean {
        return c == ' ' || c >= 0x09.toChar() && c <= 0x0D.toChar()
    }

    fun isupper(c: Char): Boolean {
        return Character.isUpperCase(c)
    }

    fun isalnum(c: Char): Boolean {
        return Character.isLetterOrDigit(c)
    }

    fun isxdigit(c: Char): Boolean {
        return "0123456789ABCDEFabcdef".indexOf(c) >= 0
    }

    fun isalpha(c: Int): Boolean {
        return Character.isLetter(c.toChar())
    }

    fun iscntrl(c: Int): Boolean {
        return Character.isISOControl(c.toChar())
    }

    fun isdigit(c: Int): Boolean {
        return Character.isDigit(c.toChar())
    }

    fun islower(c: Int): Boolean {
        return Character.isLowerCase(c.toChar())
    }

    fun ispunct(c: Int): Boolean {
        return c.toChar() != ' ' && !isalnum(c.toChar())
    } // *not* the same as Char.IsPunctuation

    fun isspace(c: Int): Boolean {
        return c.toChar() == ' ' || c.toChar() >= 0x09.toChar() && c.toChar() <= 0x0D.toChar()
    }

    fun isupper(c: Int): Boolean {
        return Character.isUpperCase(c.toChar())
    }

    fun isalnum(c: Int): Boolean {
        return Character.isLetterOrDigit(c.toChar())
    }

    fun tolower(c: Char): Char {
        return Character.toLowerCase(c)
    }

    fun toupper(c: Char): Char {
        return Character.toUpperCase(c)
    }

    fun tolower(c: Int): Char {
        return Character.toLowerCase(c.toChar())
    }

    fun toupper(c: Int): Char {
        return Character.toUpperCase(c.toChar())
    }

    fun strtoul(s: CharPtr, end: Array<CharPtr?>, base_: Int): Long { //out - ulong
        return try {
            end[0] = CharPtr(s.chars, s.index)
            // skip over any leading whitespace
            while (end[0]!![0] == ' ') {
                end[0] = end[0]!!.next()
            }
            // ignore any leading 0x
            if (end[0]!![0] == '0' && end[0]!![1] == 'x') {
                end[0] = end[0]!!.next().next()
            } else if (end[0]!![0] == '0' && end[0]!![1] == 'X') {
                end[0] = end[0]!!.next().next()
            }
            // do we have a leading + or - sign?
            var negate = false
            if (end[0]!![0] == '+') {
                end[0] = end[0]!!.next()
            } else if (end[0]!![0] == '-') {
                negate = true
                end[0] = end[0]!!.next()
            }
            // loop through all chars
            var invalid = false
            var had_digits = false
            var result: Long = 0 //ulong
            while (true) { // get this char
                val ch = end[0]!![0]
                // which digit is this?
                var this_digit = 0
                this_digit = if (isdigit(ch)) {
                    ch - '0'
                } else if (isalpha(ch)) {
                    tolower(ch) - 'a' + 10
                } else {
                    break
                }
                // is this digit valid?
                if (this_digit >= base_) {
                    invalid = true
                } else {
                    had_digits = true
                    result = result * base_.toLong() + this_digit.toLong() //ulong - ulong
                }
                end[0] = end[0]!!.next()
            }
            // were any of the digits invalid?
            if (invalid || !had_digits) {
                end[0] = s
                return Long.MAX_VALUE //Int64.MaxValue - UInt64.MaxValue
            }
            // if the value was a negative then negate it here
            if (negate) {
                result = -result //ulong
            }
            // ok, we're done
            result //ulong
        } catch (e: Exception) {
            e.printStackTrace();
            end[0] = s
            0
        }
    }

    fun putchar(ch: Char) {
        StreamProxy.Companion.Write("" + ch)
    }

    fun putchar(ch: Int) {
        StreamProxy.Companion.Write("" + ch.toChar())
    }

    fun isprint(c: Byte): Boolean {
        return c >= ' '.toByte() && c <= 127.toByte()
    }

    fun parse_scanf(str: String?, fmt: CharPtr, vararg argp: Any?): Int {
        var argp_ = emptyArray<Any?>() //FIXME:
        argp.copyInto(argp_); //FIXME:
        var parm_index = 0
        var index = 0
        while (fmt[index].toInt() != 0) {
            if (fmt[index++] == '%') {
                when (fmt[index++]) {
                    's' -> {
                        argp_[parm_index++] = str
                    }
                    'c' -> {
                        argp_[parm_index++] = ClassType.Companion.ConvertToChar(str)
                    }
                    'd' -> {
                        argp_[parm_index++] = ClassType.Companion.ConvertToInt32(str)
                    }
                    'l' -> {
                        argp_[parm_index++] = ClassType.Companion.ConvertToDouble(str, null)
                    }
                    'f' -> {
                        argp_[parm_index++] = ClassType.Companion.ConvertToDouble(str, null)
                    }
                }
            }
        }
        return parm_index
    }

    fun printf(str: CharPtr, vararg argv: Any?) {
        Tools.printf(str.toString(), *argv)
    }

    fun sprintf(buffer: CharPtr, str: CharPtr, vararg argv: Any?) {
        val temp: String = Tools.sprintf(str.toString(), *argv)
        strcpy(buffer, CharPtr.toCharPtr(temp))
    }

    fun fprintf(stream: StreamProxy?, str: CharPtr, vararg argv: Any?): Int {
        val result: String = Tools.sprintf(str.toString(), *argv)
        val chars = result.toCharArray()
        val bytes = ByteArray(chars.size)
        for (i in chars.indices) {
            bytes[i] = chars[i].toByte()
        }
        stream!!.Write(bytes, 0, bytes.size)
        return 1
    }

    const val EXIT_SUCCESS = 0
    const val EXIT_FAILURE = 1
    fun errno(): Int {
        return -1 // todo: fix this - mjf
    }

    fun strerror(error: Int): CharPtr {
        return CharPtr.toCharPtr(
            String.format(
                "error #%1\$s",
                error
            )
        ) // todo: check how this works - mjf
        //FIXME:
    }

    fun getenv(envname: CharPtr): CharPtr? { // todo: fix this - mjf
//if (envname.Equals("LUA_PATH"))
//return "MyPath";
        val result = System.getenv(envname.toString())
        return result?.let { CharPtr(it) }
    }

    //public static int memcmp(CharPtr ptr1, CharPtr ptr2, uint size)
//{
//	return memcmp(ptr1, ptr2, (int)size);
//}
    fun memcmp(ptr1: CharPtr?, ptr2: CharPtr?, size: Int): Int {
        for (i in 0 until size) {
            if (ptr1!![i] != ptr2!![i]) {
                return if (ptr1[i] < ptr2[i]) {
                    -1
                } else {
                    1
                }
            }
        }
        return 0
    }

    fun memchr(ptr: CharPtr, c: Char, count: Int): CharPtr? { //uint
        for (i in 0 until count) { //uint
            if (ptr[i] == c) {
                return CharPtr(ptr.chars, (ptr.index + i))
            }
        }
        return null
    }

    fun strpbrk(str: CharPtr, charset: CharPtr): CharPtr? {
        var i = 0
        while (str[i] != '\u0000') {
            var j = 0
            while (charset[j] != '\u0000') {
                if (str[i] == charset[j]) {
                    return CharPtr(str.chars, str.index + i)
                }
                j++
            }
            i++
        }
        return null
    }

    // find c in str
    fun strchr(str: CharPtr?, c: Char): CharPtr? {
        var index = str!!.index
        while (str.chars!![index].toInt() != 0) {
            if (str.chars!![index] == c) {
                return CharPtr(str.chars, index)
            }
            index++
        }
        return null
    }

    fun strcpy(dst: CharPtr, src: CharPtr): CharPtr {
        var i: Int
        i = 0
        while (src[i] != '\u0000') {
            dst[i] = src[i]
            i++
        }
        dst[i] = '\u0000'
        return dst
    }

    fun strcat(dst: CharPtr, src: CharPtr?): CharPtr {
        var dst_index = 0
        while (dst[dst_index] != '\u0000') {
            dst_index++
        }
        var src_index = 0
        while (src!![src_index] != '\u0000') {
            dst[dst_index++] = src[src_index++]
        }
        dst[dst_index++] = '\u0000'
        return dst
    }

    fun strncat(dst: CharPtr, src: CharPtr?, count: Int): CharPtr {
        var count = count
        var dst_index = 0
        while (dst[dst_index] != '\u0000') {
            dst_index++
        }
        var src_index = 0
        while (src!![src_index] != '\u0000' && count-- > 0) {
            dst[dst_index++] = src[src_index++]
        }
        return dst
    }

    fun strcspn(str: CharPtr?, charset: CharPtr): Int { //uint
//int index = str.ToString().IndexOfAny(charset.ToString().ToCharArray());
        var index: Int = ClassType.Companion.IndexOfAny(str.toString(), charset.toString().toCharArray())
        if (index < 0) {
            index = str.toString().length
        }
        return index //(uint)
    }

    fun strncpy(dst: CharPtr, src: CharPtr, length: Int): CharPtr {
        var index = 0
        while (src[index] != '\u0000' && index < length) {
            dst[index] = src[index]
            index++
        }
        while (index < length) {
            dst[index++] = '\u0000'
        }
        return dst
    }

    fun strlen(str: CharPtr?): Int {
        var index = 0
        while (str!![index] != '\u0000') {
            index++
        }
        return index
    }

    fun fmod(a: Double, b: Double): Double { //lua_Number - lua_Number - lua_Number
        val quotient: Float = (Math.floor(a / b) as Int).toFloat()
        return a - quotient * b
    }

    fun modf(a: Double, b: DoubleArray): Double { //lua_Number - out - lua_Number - lua_Number
        b[0] = Math.floor(a)
        return a - Math.floor(a)
    }

    fun lmod(a: Double, b: Double): Long { //lua_Number - lua_Number
        return a.toLong() % b.toLong()
    }

    fun getc(f: StreamProxy?): Int {
        return f!!.ReadByte()
    }

    fun ungetc(c: Int, f: StreamProxy?) {
        f!!.ungetc(c)
    }

    var stdout: StreamProxy = StreamProxy.Companion.OpenStandardOutput()
    var stdin: StreamProxy = StreamProxy.Companion.OpenStandardInput()
    var stderr: StreamProxy = StreamProxy.Companion.OpenStandardError()
    var EOF = -1
    fun fputs(str: CharPtr?, stream: StreamProxy?) {
        StreamProxy.Companion.Write(str.toString()) //FIXME:
    }

    fun feof(s: StreamProxy?): Int {
        return if (s!!.isEof()) 1 else 0
    }

    fun fread(ptr: CharPtr, size: Int, num: Int, stream: StreamProxy?): Int {
        val num_bytes = num * size
        val bytes = ByteArray(num_bytes)
        return try {
            val result: Int = stream!!.Read(bytes, 0, num_bytes)
            for (i in 0 until result) {
                ptr[i] = bytes[i].toChar()
            }
            result / size
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    fun fwrite(ptr: CharPtr, size: Int, num: Int, stream: StreamProxy?): Int {
        val num_bytes = num * size
        val bytes = ByteArray(num_bytes)
        for (i in 0 until num_bytes) {
            bytes[i] = ptr[i].toByte()
        }
        try {
            stream!!.Write(bytes, 0, num_bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
        return num
    }

    fun strcmp(s1: CharPtr?, s2: CharPtr?): Int {
        if (CharPtr.isEqual(s1, s2)) {
            return 0
        }
        if (CharPtr.isEqual(s1, null)) {
            return -1
        }
        if (CharPtr.isEqual(s2, null)) {
            return 1
        }
        var i = 0
        while (true) {
            if (s1!![i] != s2!![i]) {
                return if (s1[i] < s2[i]) {
                    -1
                } else {
                    1
                }
            }
            if (s1[i] == '\u0000') {
                return 0
            }
            i++
        }
    }

    fun fgets(str: CharPtr, stream: StreamProxy?): CharPtr {
        var index = 0
        try {
            while (true) {
                str[index] = stream!!.ReadByte().toChar();
                if (str[index] == '\n') {
                    break
                }
                if (index >= str.chars!!.size) {
                    break
                }
                index++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return str
    }

    fun frexp(x: Double, expptr: IntArray): Double { //out
        expptr[0] = ClassType.Companion.log2(x) + 1
        return x / Math.pow(2.0, expptr[0].toDouble())
    }

    fun ldexp(x: Double, expptr: Int): Double {
        return x * Math.pow(2.0, expptr.toDouble())
    }

    fun strstr(str: CharPtr?, substr: CharPtr): CharPtr? {
        val index = str.toString().indexOf(substr.toString())
        return if (index < 0) {
            null
        } else CharPtr(CharPtr.plus(str, index))
    }

    fun strrchr(str: CharPtr, ch: Char): CharPtr? {
        val index = str.toString().lastIndexOf(ch)
        return if (index < 0) {
            null
        } else CharPtr.plus(str, index)
    }

    fun fopen(filename: CharPtr?, mode: CharPtr?): StreamProxy? {
        val str = filename.toString()
        var modeStr = ""
        var i = 0
        while (mode!![i] != '\u0000') {
            modeStr += mode[i]
            i++
        }
        return try {
            val result = StreamProxy(str, modeStr)
            if (result.isOK) {
                result
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun freopen(filename: CharPtr?, mode: CharPtr?, stream: StreamProxy?): StreamProxy? {
        try {
            stream!!.Flush()
            stream!!.Close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fopen(filename, mode)
    }

    fun fflush(stream: StreamProxy) {
        stream.Flush()
    }

    fun ferror(stream: StreamProxy?): Int { //FIXME:
        return 0 // todo: fix this - mjf
    }

    fun fclose(stream: StreamProxy?): Int {
        stream!!.Close()
        return 0
    }

    fun tmpfile(): StreamProxy { //new FileStream(Path.GetTempFileName(), FileMode.Create, FileAccess.ReadWrite);
        return StreamProxy.Companion.tmpfile()
    }

    fun fscanf(f: StreamProxy?, format: CharPtr, vararg argp: Any?): Int {
        val str: String? = StreamProxy.Companion.ReadLine() //FIXME: f
        return parse_scanf(str, format, *argp)
    }

    fun fseek(f: StreamProxy?, offset: Long, origin: Int): Int {
        return f!!.Seek(offset, origin)
    }

    fun ftell(f: StreamProxy?): Int {
        return f!!.getPosition() as Int
    }

    fun clearerr(f: StreamProxy?): Int { //ClassType.Assert(false, "clearerr not implemented yet - mjf");
        return 0
    }

    fun setvbuf(stream: StreamProxy?, buffer: CharPtr?, mode: Int, size: Int): Int { //uint
//FIXME:stream
        ClassType.Companion.Assert(false, "setvbuf not implemented yet - mjf")
        return 0
    }

    //public static void memcpy<T>(T[] dst, T[] src, int length)
//{
//	for (int i = 0; i < length; i++)
//	{
//		dst[i] = src[i];
//	}
//}
    fun memcpy_char(dst: CharArray, offset: Int, src: CharArray, length: Int) {
        for (i in 0 until length) {
            dst[offset + i] = src[i]
        }
    }

    fun memcpy_char(dst: CharArray, src: CharArray, srcofs: Int, length: Int) {
        for (i in 0 until length) {
            dst[i] = src[srcofs + i]
        }
    }

    //public static void memcpy(CharPtr ptr1, CharPtr ptr2, uint size)
//{
//	memcpy(ptr1, ptr2, (int)size);
//}
    fun memcpy(ptr1: CharPtr, ptr2: CharPtr, size: Int) {
        for (i in 0 until size) {
            ptr1[i] = ptr2[i]
        }
    }

    fun VOID(f: Any?): Any? {
        return f
    }

    val HUGE_VAL = Double.MAX_VALUE //System.
    const val SHRT_MAX = Short.MAX_VALUE.toInt() //System.UInt16 - uint
    const val _IONBF = 0
    const val _IOFBF = 1
    const val _IOLBF = 2
    const val SEEK_SET = 0
    const val SEEK_CUR = 1
    const val SEEK_END = 2
    // one of the primary objectives of this port is to match the C version of Lua as closely as
// possible. a key part of this is also matching the behaviour of the garbage collector, as
// that affects the operation of things such as weak tables. in order for this to occur the
// size of structures that are allocated must be reported as identical to their C++ equivelents.
// that this means that variables such as global_State.totalbytes no longer indicate the true
// amount of memory allocated.
    fun GetUnmanagedSize(t: ClassType): Int {
        return t.GetUnmanagedSize()
    }

    class CharPtr {
        var chars: CharArray?
        var index: Int
        //public char this[int offset] get
        operator fun get(offset: Int): Char {
            return chars!![index + offset]
        }

        //public char this[int offset] set
        operator fun set(offset: Int, `val`: Char) {
            chars!![index + offset] = `val`
        }

        //public char this[long offset] get
        operator fun get(offset: Long): Char {
            return chars!![index + offset.toInt()]
        }

        //public char this[long offset] set
        operator fun set(offset: Long, `val`: Char) {
            chars!![index + offset.toInt()] = `val`
        }

        constructor() {
            chars = null
            index = 0
        }

        constructor(str: String) {
            chars = (str + '\u0000').toCharArray()
            index = 0
        }

        constructor(ptr: CharPtr) {
            chars = ptr.chars
            index = ptr.index
        }

        constructor(ptr: CharPtr, index: Int) {
            chars = ptr.chars
            this.index = index
        }

        constructor(chars: CharArray?) {
            this.chars = chars
            index = 0
        }

        constructor(chars: CharArray?, index: Int) {
            this.chars = chars
            this.index = index
        }

        fun inc() {
            index++
        }

        fun dec() {
            index--
        }

        operator fun next(): CharPtr {
            return CharPtr(chars, index + 1)
        }

        fun prev(): CharPtr {
            return CharPtr(chars, index - 1)
        }

        fun add(ofs: Int): CharPtr {
            return CharPtr(chars, index + ofs)
        }

        fun sub(ofs: Int): CharPtr {
            return CharPtr(chars, index - ofs)
        }

        override fun equals(o: Any?): Boolean {
            return isEqual(this, o as? CharPtr)
        }

        override fun hashCode(): Int {
            return 0
        }

        override fun toString(): String {
            var result = ""
            var i = index
            while (i < chars!!.size && chars!![i] != '\u0000') {
                result += chars!![i]
                i++
            }
            return result
        }

        companion object {
            //implicit operator CharPtr
            fun toCharPtr(str: String): CharPtr {
                return CharPtr(str)
            }

            //implicit operator CharPtr
            fun toCharPtr(chars: CharArray?): CharPtr {
                return CharPtr(chars)
            }

            //public CharPtr(IntPtr ptr)
//{
//	this.chars = new char[0];
//	this.index = 0;
//}
            fun plus(ptr: CharPtr?, offset: Int): CharPtr {
                return CharPtr(ptr!!.chars, ptr.index + offset)
            }

            fun minus(ptr: CharPtr, offset: Int): CharPtr {
                return CharPtr(ptr.chars, ptr.index - offset)
            }

            //operator ==
            fun isEqualChar(ptr: CharPtr, ch: Char): Boolean {
                return ptr[0] == ch
            }

            //operator ==
            fun isEqualChar(ch: Char, ptr: CharPtr): Boolean {
                return ptr[0] == ch
            }

            //operator !=
            fun isNotEqualChar(ptr: CharPtr, ch: Char): Boolean {
                return ptr[0] != ch
            }

            //operator !=
            fun isNotEqualChar(ch: Char, ptr: CharPtr): Boolean {
                return ptr[0] != ch
            }

            fun plus(ptr1: CharPtr, ptr2: CharPtr): CharPtr {
                var result = ""
                run {
                    var i = 0
                    while (ptr1[i] != '\u0000') {
                        result += ptr1[i]
                        i++
                    }
                }
                var i = 0
                while (ptr2[i] != '\u0000') {
                    result += ptr2[i]
                    i++
                }
                return CharPtr(result)
            }

            fun minus(ptr1: CharPtr?, ptr2: CharPtr?): Int {
                ClassType.Companion.Assert(ptr1!!.chars == ptr2!!.chars)
                return ptr1!!.index - ptr2!!.index
            }

            //operator <
            fun lessThan(ptr1: CharPtr, ptr2: CharPtr): Boolean {
                ClassType.Companion.Assert(ptr1.chars == ptr2.chars)
                return ptr1.index < ptr2.index
            }

            //operator <=
            fun lessEqual(ptr1: CharPtr, ptr2: CharPtr): Boolean {
                ClassType.Companion.Assert(ptr1.chars == ptr2.chars)
                return ptr1.index <= ptr2.index
            }

            fun greaterThan(ptr1: CharPtr, ptr2: CharPtr): Boolean {
                ClassType.Companion.Assert(ptr1.chars == ptr2.chars)
                return ptr1.index > ptr2.index
            }

            //operator >=
            fun greaterEqual(ptr1: CharPtr, ptr2: CharPtr): Boolean {
                ClassType.Companion.Assert(ptr1.chars == ptr2.chars)
                return ptr1.index >= ptr2.index
            }

            //operator ==
            fun isEqual(ptr1: CharPtr?, ptr2: CharPtr?): Boolean {
                val o1: Any? = ptr1
                val o2: Any? = ptr2
                if (o1 == null && o2 == null) {
                    return true
                }
                if (o1 == null) {
                    return false
                }
                return if (o2 == null) {
                    false
                } else ptr1!!.chars == ptr2!!.chars && ptr1.index == ptr2.index
            }

            //operator !=
            fun isNotEqual(ptr1: CharPtr?, ptr2: CharPtr?): Boolean {
                return !isEqual(ptr1, ptr2)
            }
        }
    }
}