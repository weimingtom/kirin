package kirin

object Tools {
    fun sprintf(Format: String, vararg Parameters: Any?): String {
        var Parameters_ = arrayOfNulls<Any?>(Parameters.count())
        Parameters.copyInto(Parameters_)
        var Format = Format
        var hasFloat = false
        if (Format == LuaConf.LUA_NUMBER_FMT && Parameters.size == 1) {
            if (Parameters[0] as Double == (Parameters[0] as Double).toLong().toDouble()) {
                Format = "%s"
                Parameters_[0] = (Parameters[0] as Double).toLong()
            } else {
                Format = "%s"
                hasFloat = true
            }
        } else if (Format == "%ld") {
            Format = "%d"
        }
        var result = String.format(Format, *Parameters_)
        if (hasFloat) {
            val subResults = result.split("\\.".toRegex()).toTypedArray()
            if (subResults.size == 2 && subResults[1].length > 13) {
                result = String.format(LuaConf.LUA_NUMBER_FMT, *Parameters)
            }
        }
        return result
    }

    fun printf(Format: String, vararg Parameters: Any?) {
        print(sprintf(Format, *Parameters))
    }
}