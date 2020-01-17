package kirin

import kirin.CLib.CharPtr
import kirin.LuaObject.Node
import kirin.LuaObject.TKey
import kirin.LuaObject.TKey_nk
import kirin.LuaObject.TString
import kirin.LuaObject.TValue
import kirin.LuaObject.Table
import kirin.LuaState.lua_State

//
// ** $Id: ltable.c,v 2.32.1.2 2007/12/28 15:32:23 roberto Exp $
// ** Lua tables (hash)
// ** See Copyright Notice in lua.h
//
//using TValue = Lua.TValue;
//using StkId = TValue;
//using lua_Number = System.Double;
object LuaTable {
    //
//		 ** Implementation of tables (aka arrays, objects, or hash tables).
//		 ** Tables keep its elements in two parts: an array part and a hash part.
//		 ** Non-negative integer keys are all candidates to be kept in the array
//		 ** part. The actual size of the array is the largest `n' such that at
//		 ** least half the slots between 0 and n are in use.
//		 ** Hash uses a mix of chained scatter table with Brent's variation.
//		 ** A main invariant of these tables is that, if an element is not
//		 ** in its main position (i.e. the `original' position that its hash gives
//		 ** to it), then the colliding element is in its own main position.
//		 ** Hence even when the load factor reaches 100%, performance remains good.
//
    fun gnode(t: Table?, i: Int): LuaObject.Node {
        return t!!.node!!.get(i)!!
    }

    fun gkey(n: LuaObject.Node?): TKey_nk {
        return n!!.i_key.nk
    }

    fun gval(n: LuaObject.Node?): TValue {
        return n!!.i_val
    }

    fun gnext(n: LuaObject.Node?): LuaObject.Node? {
        return n!!.i_key.nk.next
    }

    fun gnext_set(n: LuaObject.Node?, v: LuaObject.Node?) {
        n!!.i_key.nk.next = v
    }

    fun key2tval(n: LuaObject.Node?): TValue {
        return n!!.i_key.getTvk()
    }

    //
//		 ** max size of array part is 2^MAXBITS
//
///#if LUAI_BITSINT > 26
    const val MAXBITS = 26 // in the dotnet port LUAI_BITSINT is 32
    ///#else
//public const int MAXBITS		= (LUAI_BITSINT-2);
///#endif
    const val MAXASIZE = 1 shl MAXBITS

    //public static Node gnode(Table t, int i)	{return t.node[i];}
    fun hashpow2(t: Table?, n: Double): LuaObject.Node { //lua_Number
        return gnode(t, CLib.lmod(n, LuaObject.sizenode(t).toDouble()).toInt())
    }

    fun hashstr(t: Table?, str: TString?): LuaObject.Node {
        return hashpow2(t, str!!.getTsv().hash.toDouble())
    }

    fun hashboolean(t: Table?, p: Int): LuaObject.Node {
        return hashpow2(t, p.toDouble())
    }

    //
//		 ** for some types, it is better to avoid modulus by power of 2, as
//		 ** they tend to have many 2 factors.
//
    fun hashmod(t: Table?, n: Int): LuaObject.Node {
        return gnode(t, n % (LuaObject.sizenode(t) - 1 or 1))
    }

    fun hashpointer(t: Table?, p: Any?): LuaObject.Node {
        return hashmod(t, p.hashCode())
    }

    //
//		 ** number of ints inside a lua_Number
//
    val numints: Int = ClassType.Companion.GetNumInts() //const
    //static const Node dummynode_ = {
//{{null}, LUA_TNIL},  /* value */
//{{{null}, LUA_TNIL, null}}  /* key */
//};
    var dummynode_ =
        LuaObject.Node(TValue(LuaObject.Value(), Lua.LUA_TNIL), TKey(LuaObject.Value(), Lua.LUA_TNIL, null))
    var dummynode = dummynode_
    //
//		 ** hash for lua_Numbers
//
    private fun hashnum(t: Table?, n: Double): LuaObject.Node { //lua_Number
        val a: IntArray = ClassType.Companion.GetBytes(n)
        for (i in 1 until a.size) {
            a[0] += a[i]
        }
        return hashmod(t, (a[0] and 0xff))
    }

    //
//		 ** returns the `main' position of an element in a table (that is, the index
//		 ** of its hash value)
//
    private fun mainposition(t: Table?, key: TValue): LuaObject.Node {
        return when (LuaObject.ttype(key)) {
            Lua.LUA_TNUMBER -> {
                hashnum(t, LuaObject.nvalue(key))
            }
            Lua.LUA_TSTRING -> {
                hashstr(t, LuaObject.rawtsvalue(key))
            }
            Lua.LUA_TBOOLEAN -> {
                hashboolean(t, LuaObject.bvalue(key))
            }
            Lua.LUA_TLIGHTUSERDATA -> {
                hashpointer(t, LuaObject.pvalue(key))
            }
            else -> {
                hashpointer(t, LuaObject.gcvalue(key))
            }
        }
    }

    //
//		 ** returns the index for `key' if `key' is an appropriate key to live in
//		 ** the array part of the table, -1 otherwise.
//
    private fun arrayindex(key: TValue): Int {
        if (LuaObject.ttisnumber(key)) {
            val n = LuaObject.nvalue(key) //lua_Number
            val k = IntArray(1)
            LuaConf.lua_number2int(k, n) //out
            if (LuaConf.luai_numeq(LuaLimits.cast_num(k[0]), n)) {
                return k[0]
            }
        }
        return -1 // `key' did not match some condition
    }

    //
//		 ** returns the index of a `key' for table traversals. First goes all
//		 ** elements in the array part, then elements in the hash part. The
//		 ** beginning of a traversal is signalled by -1.
//
    private fun findindex(L: lua_State, t: Table, key: TValue): Int { //StkId
        var i: Int
        if (LuaObject.ttisnil(key)) {
            return -1 // first iteration
        }
        i = arrayindex(key)
        return if (0 < i && i <= t.sizearray) { // is `key' inside array part?
            i - 1 // yes; that's the index (corrected to C)
        } else {
            var n: LuaObject.Node? = mainposition(t, key)
            do { // check whether `key' is somewhere in the chain
// key may be dead already, but it is ok to use it in `next'
                if (LuaObject.luaO_rawequalObj(
                        key2tval(n),
                        key
                    ) != 0 || LuaObject.ttype(gkey(n)) == LuaObject.LUA_TDEADKEY && LuaObject.iscollectable(
                        key
                    ) && LuaObject.gcvalue(gkey(n)) === LuaObject.gcvalue(key)
                ) {
                    i = LuaLimits.cast_int(
                        LuaObject.Node.Companion.minus(
                            n!!,
                            gnode(t, 0)
                        )
                    ) // key index in hash table
                    // hash elements are numbered after array ones
                    return i + t.sizearray
                } else {
                    n = gnext(n)
                }
            } while (LuaObject.Node.Companion.isNotEqual(n, null))
            LuaDebug.luaG_runerror(
                L,
                CharPtr.Companion.toCharPtr("invalid key to " + LuaConf.LUA_QL("next"))
            ) // key not found
            0 // to avoid warnings
        }
    }

    fun luaH_next(L: lua_State, t: Table, key: TValue): Int { //StkId
        var i = findindex(L, t, key) // find original element
        i++
        while (i < t.sizearray) {
            // try first array part
            if (!LuaObject.ttisnil(t.array!!.get(i))) { // a non-nil value?
                LuaObject.setnvalue(key, LuaLimits.cast_num(i + 1))
                LuaObject.setobj2s(L, TValue.Companion.plus(key, 1), t.array!!.get(i)!!)
                return 1
            }
            i++
        }
        i -= t.sizearray
        while (i < LuaObject.sizenode(t)) {
            // then hash part
            if (!LuaObject.ttisnil(gval(gnode(t, i)))) { // a non-nil value?
                LuaObject.setobj2s(L, key, key2tval(gnode(t, i)))
                LuaObject.setobj2s(L, TValue.Companion.plus(key, 1), gval(gnode(t, i)))
                return 1
            }
            i++
        }
        return 0 // no more elements
    }

    //
//		 ** {=============================================================
//		 ** Rehash
//		 ** ==============================================================
//
    private fun computesizes(nums: IntArray, narray: IntArray): Int { //ref
        var i: Int
        var twotoi: Int // 2^i
        var a = 0 // number of elements smaller than 2^i
        var na = 0 // number of elements to go to array part
        var n = 0 // optimal size for array part
        i = 0
        twotoi = 1
        while (twotoi / 2 < narray[0]) {
            if (nums[i] > 0) {
                a += nums[i]
                if (a > twotoi / 2) { // more than half elements present?
                    n = twotoi // optimal size (till now)
                    na = a // all elements smaller than n will go to array part
                }
            }
            if (a == narray[0]) {
                break // all elements already counted
            }
            i++
            twotoi *= 2
        }
        narray[0] = n
        LuaLimits.lua_assert(narray[0] / 2 <= na && na <= narray[0])
        return na
    }

    private fun countint(key: TValue, nums: IntArray): Int {
        val k = arrayindex(key)
        return if (0 < k && k <= MAXASIZE) { // is `key' an appropriate array index?
            nums[LuaObject.ceillog2(k)]++ // count as such
            1
        } else {
            0
        }
    }

    private fun numusearray(t: Table?, nums: IntArray): Int {
        var lg: Int
        var ttlg: Int // 2^lg
        var ause = 0 // summation of `nums'
        var i = 1 // count to traverse all array keys
        lg = 0
        ttlg = 1
        while (lg <= MAXBITS) {
            // for each slice
            var lc = 0 // counter
            var lim = ttlg
            if (lim > t!!.sizearray) {
                lim = t!!.sizearray // adjust upper limit
                if (i > lim) {
                    break // no more elements to count
                }
            }
            // count elements in range (2^(lg-1), 2^lg]
            while (i <= lim) {
                if (!LuaObject.ttisnil(t!!.array!!.get(i - 1))) {
                    lc++
                }
                i++
            }
            nums[lg] += lc
            ause += lc
            lg++
            ttlg *= 2
        }
        return ause
    }

    private fun numusehash(t: Table?, nums: IntArray, pnasize: IntArray): Int { //ref
        var totaluse = 0 // total number of elements
        var ause = 0 // summation of `nums'
        var i = LuaObject.sizenode(t)
        while (i-- != 0) {
            val n: Node = t!!.node!!.get(i)!!
            if (!LuaObject.ttisnil(gval(n))) {
                ause += countint(key2tval(n), nums)
                totaluse++
            }
        }
        pnasize[0] += ause
        return totaluse
    }

    private fun setarrayvector(L: lua_State?, t: Table?, size: Int) {
        var i: Int
        val array_ref: Array<Array<TValue?>?> = arrayOfNulls<Array<TValue?>>(1)
        array_ref[0] = t!!.array
        LuaMem.luaM_reallocvector_TValue(
            L,
            array_ref,
            t!!.sizearray,
            size,
            ClassType(ClassType.Companion.TYPE_TVALUE)
        ) //, TValue - ref
        t!!.array = array_ref[0]
        i = t!!.sizearray
        while (i < size) {
            LuaObject.setnilvalue(t!!.array!!.get(i))
            i++
        }
        t!!.sizearray = size
    }

    private fun setnodevector(L: lua_State?, t: Table?, size: Int) {
        var size = size
        val lsize: Int
        if (size == 0) { // no elements to hash part?
            t!!.node = arrayOf(dummynode) // use common `dummynode'
            lsize = 0
        } else {
            var i: Int
            lsize = LuaObject.ceillog2(size)
            if (lsize > MAXBITS) {
                LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("table overflow"))
            }
            size = LuaObject.twoto(lsize)
            val nodes: Array<Node?> =
                LuaMem.luaM_newvector_Node(L, size, ClassType(ClassType.Companion.TYPE_NODE))
            t!!.node = nodes!!
            i = 0
            while (i < size) {
                val n = gnode(t, i)
                gnext_set(n, null)
                LuaObject.setnilvalue(gkey(n))
                LuaObject.setnilvalue(gval(n))
                i++
            }
        }
        t.lsizenode = LuaLimits.cast_byte(lsize)
        t.lastfree = size // all positions are free
    }

    private fun resize(L: lua_State?, t: Table?, nasize: Int, nhsize: Int) {
        var i: Int
        val oldasize: Int = t!!.sizearray
        val oldhsize: Int = t!!.lsizenode.toInt()
        val nold: Array<Node?> = t.node!! // save old hash...
        if (nasize > oldasize) { // array part must grow?
            setarrayvector(L, t, nasize)
        }
        // create new hash part with appropriate size
        setnodevector(L, t, nhsize)
        if (nasize < oldasize) { // array part must shrink?
            t.sizearray = nasize
            // re-insert elements from vanishing slice
            i = nasize
            while (i < oldasize) {
                if (!LuaObject.ttisnil(t.array!!.get(i))) {
                    LuaObject.setobjt2t(L, luaH_setnum(L, t, i + 1), t!!.array!!.get(i)!!)
                }
                i++
            }
            // shrink array
            val array_ref: Array<Array<TValue?>?> = arrayOfNulls<Array<TValue?>>(1)
            array_ref[0] = t.array
            LuaMem.luaM_reallocvector_TValue(
                L,
                array_ref,
                oldasize,
                nasize,
                ClassType(ClassType.Companion.TYPE_TVALUE)
            ) //, TValue - ref
            t.array = array_ref[0]
        }
        // re-insert elements from hash part
        i = LuaObject.twoto(oldhsize) - 1
        while (i >= 0) {
            val old = nold[i]
            if (!LuaObject.ttisnil(gval(old))) {
                LuaObject.setobjt2t(L, luaH_set(L, t, key2tval(old)), gval(old))
            }
            i--
        }
        if (LuaObject.Node.Companion.isNotEqual(nold[0], dummynode)) {
            LuaMem.luaM_freearray_Node(L, nold, ClassType(ClassType.Companion.TYPE_NODE)) // free old array
        }
    }

    fun luaH_resizearray(L: lua_State?, t: Table, nasize: Int) {
        val nsize =
            if (LuaObject.Node.Companion.isEqual(t.node!!.get(0), dummynode)) 0 else LuaObject.sizenode(
                t
            )
        resize(L, t, nasize, nsize)
    }

    private fun rehash(L: lua_State?, t: Table?, ek: TValue) {
        val nasize = IntArray(1)
        val na: Int
        val nums =
            IntArray(MAXBITS + 1) // nums[i] = number of keys between 2^(i-1) and 2^i
        var i: Int
        var totaluse: Int
        i = 0
        while (i <= MAXBITS) {
            nums[i] = 0 // reset counts
            i++
        }
        nasize[0] = numusearray(t, nums) // count keys in array part
        totaluse = nasize[0] // all those keys are integer keys
        totaluse += numusehash(t, nums, nasize) // count keys in hash part  - ref
        // count extra key
        nasize[0] += countint(ek, nums)
        totaluse++
        // compute new size for array part
        na = computesizes(nums, nasize) //ref
        // resize the table to new computed sizes
        resize(L, t, nasize[0], totaluse - na)
    }

    //
//		 ** }=============================================================
//
    fun luaH_new(L: lua_State?, narray: Int, nhash: Int): Table {
        val t: Table = LuaMem.luaM_new_Table(L, ClassType(ClassType.Companion.TYPE_TABLE))
        LuaGC.luaC_link(L, LuaState.obj2gco(t), Lua.LUA_TTABLE.toByte())
        t.metatable = null
        t.flags = LuaLimits.cast_byte(0.inv())
        // temporary values (kept only if some malloc fails)
        t.array = null
        t.sizearray = 0
        t.lsizenode = 0
        t.node = arrayOf(dummynode)
        setarrayvector(L, t, narray)
        setnodevector(L, t, nhash)
        return t
    }

    fun luaH_free(L: lua_State?, t: Table) {
        if (LuaObject.Node.Companion.isNotEqual(t.node!!.get(0), dummynode)) {
            LuaMem.luaM_freearray_Node(L, t.node, ClassType(ClassType.Companion.TYPE_NODE))
        }
        LuaMem.luaM_freearray_TValue(L, t.array, ClassType(ClassType.Companion.TYPE_TVALUE))
        LuaMem.luaM_free_Table(L, t, ClassType(ClassType.Companion.TYPE_TABLE))
    }

    private fun getfreepos(t: Table?): LuaObject.Node? {
        while (t!!.lastfree-- > 0) {
            if (LuaObject.ttisnil(gkey(t!!.node!!.get(t.lastfree)))) {
                return t!!.node!!.get(t!!.lastfree)
            }
        }
        return null // could not find a free place
    }

    //
//		 ** inserts a new key into a hash table; first, check whether key's main
//		 ** position is free. If not, check whether colliding node is in its main
//		 ** position or not: if it is not, move colliding node to an empty place and
//		 ** put new key in its main position; otherwise (colliding node is in its main
//		 ** position), new key goes to an empty position.
//
    private fun newkey(L: lua_State?, t: Table?, key: TValue): TValue {
        var mp: LuaObject.Node? = mainposition(t, key)
        if (!LuaObject.ttisnil(gval(mp)) || LuaObject.Node.Companion.isEqual(mp, dummynode)) {
            var othern: LuaObject.Node?
            val n = getfreepos(t) // get a free place
            if (LuaObject.Node.Companion.isEqual(n, null)) { // cannot find a free place?
                rehash(L, t, key) // grow table
                return luaH_set(L, t, key) // re-insert key into grown table
            }
            LuaLimits.lua_assert(LuaObject.Node.Companion.isNotEqual(n, dummynode))
            othern = mainposition(t, key2tval(mp))
            if (LuaObject.Node.Companion.isNotEqual(
                    othern,
                    mp
                )
            ) { // is colliding node out of its main position?
// yes; move colliding node into free position
                while (LuaObject.Node.Companion.isNotEqual(gnext(othern), mp)) {
                    othern = gnext(othern) // find previous
                }
                gnext_set(othern, n) // redo the chain with `n' in place of `mp'
                n!!.i_val = TValue(mp!!.i_val) // copy colliding node into free pos. (mp.next also goes)
                n.i_key = TKey(mp!!.i_key)
                gnext_set(mp, null) // now `mp' is free
                LuaObject.setnilvalue(gval(mp))
            } else { // colliding node is in its own main position
// new node will go into free position
                gnext_set(n, gnext(mp)) // chain new position
                gnext_set(mp, n)
                mp = n
            }
        }
        gkey(mp).value.copyFrom(key.value)
        gkey(mp).tt = key.tt
        LuaGC.luaC_barriert(L, t!!, key)
        LuaLimits.lua_assert(LuaObject.ttisnil(gval(mp)))
        return gval(mp)
    }

    //
//		 ** search function for integers
//
    fun luaH_getnum(t: Table?, key: Int): TValue { // (1 <= key && key <= t.sizearray)
        if ( (((key - 1).toLong() and 0xffffffffL).toLong()) < (((t!!.sizearray.toLong()) and 0xffffffffL).toLong())) {
            //uint - uint
            return t!!.array!!.get(key - 1)!!
        } else {
            val nk: Double = LuaLimits.cast_num(key) //lua_Number
            var n: LuaObject.Node? = hashnum(t, nk)
            do { // check whether `key' is somewhere in the chain
                n = if (LuaObject.ttisnumber(gkey(n)) && LuaConf.luai_numeq(
                        LuaObject.nvalue(gkey(n)),
                        nk
                    )
                ) {
                    return gval(n) // that's it
                } else {
                    gnext(n)
                }
            } while (LuaObject.Node.Companion.isNotEqual(n, null))
            return LuaObject.luaO_nilobject
        }
    }

    //
//		 ** search function for strings
//
    fun luaH_getstr(t: Table?, key: TString?): TValue {
        var n: LuaObject.Node? = hashstr(t, key)
        do { // check whether `key' is somewhere in the chain
            n = if (LuaObject.ttisstring(gkey(n)) && LuaObject.rawtsvalue(gkey(n)) === key) {
                return gval(n) // that's it
            } else {
                gnext(n)
            }
        } while (LuaObject.Node.Companion.isNotEqual(n, null))
        return LuaObject.luaO_nilobject
    }

    //
//		 ** main search function
//
    fun luaH_get(t: Table?, key: TValue): TValue {
        return when (LuaObject.ttype(key)) {
            Lua.LUA_TNIL -> {
                LuaObject.luaO_nilobject
            }
            Lua.LUA_TSTRING -> {
                luaH_getstr(t, LuaObject.rawtsvalue(key))
            }
            Lua.LUA_TNUMBER -> {
                val k = IntArray(1)
                val n = LuaObject.nvalue(key) //lua_Number
                LuaConf.lua_number2int(k, n) //out
                if (LuaConf.luai_numeq(LuaLimits.cast_num(k[0]), LuaObject.nvalue(key))) { // index is int?
                    return luaH_getnum(t, k[0]) // use specialized version
                }
                // else go through ... actually on second thoughts don't, because this is C#
                var node: LuaObject.Node? = mainposition(t, key)
                do { // check whether `key' is somewhere in the chain
                    node = if (LuaObject.luaO_rawequalObj(key2tval(node), key) != 0) {
                        return gval(node) // that's it
                    } else {
                        gnext(node)
                    }
                } while (LuaObject.Node.Companion.isNotEqual(node, null))
                LuaObject.luaO_nilobject
            }
            else -> {
                var node: LuaObject.Node? = mainposition(t, key)
                do { // check whether `key' is somewhere in the chain
                    node = if (LuaObject.luaO_rawequalObj(key2tval(node), key) != 0) {
                        return gval(node) // that's it
                    } else {
                        gnext(node)
                    }
                } while (LuaObject.Node.Companion.isNotEqual(node, null))
                LuaObject.luaO_nilobject
            }
        }
    }

    fun luaH_set(L: lua_State?, t: Table?, key: TValue): TValue {
        val p: TValue = luaH_get(t, key)
        t!!.flags = 0
        return if (p !== LuaObject.luaO_nilobject) {
            p as TValue
        } else {
            if (LuaObject.ttisnil(key)) {
                LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("table index is nil"))
            } else if (LuaObject.ttisnumber(key) && LuaConf.luai_numisnan(LuaObject.nvalue(key))) {
                LuaDebug.luaG_runerror(L, CharPtr.Companion.toCharPtr("table index is NaN"))
            }
            newkey(L, t, key)
        }
    }

    fun luaH_setnum(L: lua_State?, t: Table?, key: Int): TValue {
        val p: TValue = luaH_getnum(t, key)
        return if (p !== LuaObject.luaO_nilobject) {
            p as TValue
        } else {
            val k = TValue()
            LuaObject.setnvalue(k, LuaLimits.cast_num(key))
            newkey(L, t, k)
        }
    }

    fun luaH_setstr(L: lua_State?, t: Table?, key: TString?): TValue {
        val p: TValue = luaH_getstr(t, key)
        return if (p !== LuaObject.luaO_nilobject) {
            p as TValue
        } else {
            val k = TValue()
            LuaObject.setsvalue(L, k, key)
            newkey(L, t, k)
        }
    }

    fun unbound_search(t: Table?, j: Int): Int { //uint
        var j = j
        var i = j // i is zero or a present index  - uint
        j++
        // find `i' and `j' such that i is present and j is not
        while (!LuaObject.ttisnil(luaH_getnum(t, j))) {
            i = j
            j *= 2
            if (j > LuaLimits.MAX_INT) { //uint
// overflow?
// table was built with bad purposes: resort to linear search
                i = 1
                while (!LuaObject.ttisnil(luaH_getnum(t, i))) {
                    i++
                }
                return (i - 1)
            }
        }
        // now do a binary search between them
        while (j - i > 1) {
            val m = (i + j) / 2 //uint
            if (LuaObject.ttisnil(luaH_getnum(t, m))) {
                j = m
            } else {
                i = m
            }
        }
        return i
    }

    //
//		 ** Try to find a boundary in table `t'. A `boundary' is an integer index
//		 ** such that t[i] is non-nil and t[i+1] is nil (and 0 if t[1] is nil).
//
    fun luaH_getn(t: Table): Int {
        var j = t.sizearray as Int //uint - uint
        return if (j > 0 && LuaObject.ttisnil(t.array!!.get(j - 1))) { // there is a boundary in the array part: (binary) search for it
            var i = 0 //uint
            while (j - i > 1) {
                val m = (i + j) / 2 //uint
                if (LuaObject.ttisnil(t.array!!.get(m - 1))) {
                    j = m
                } else {
                    i = m
                }
            }
            i
        } else if (LuaObject.Node.Companion.isEqual(t!!.node!!.get(0), dummynode)) { // hash part is empty?
            j // that is easy...
        } else {
            unbound_search(t, j)
        }
    } ///#if defined(LUA_DEBUG)
//Node *luaH_mainposition (const Table *t, const TValue *key) {
//  return mainposition(t, key);
//}
//int luaH_isdummy (Node *n) { return n == dummynode; }
///#endif
}