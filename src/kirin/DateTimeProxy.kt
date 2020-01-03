package kirin
import java.util.Calendar
import java.util.TimeZone

//{
class DateTimeProxy {
    //see https://github.com/weimingtom/mochalua/blob/master/Mochalua/src/com/groundspeak/mochalua/LuaOSLib.java
    private var _calendar: Calendar

    constructor() {
        _calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
    }

    constructor(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int) {
        _calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        _calendar.set(year, month, day, hour, min, sec)
    }

    fun setUTCNow() {
        _calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    }

    fun setNow() {
        _calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
    }

    fun getSecond(): Int {
        return _calendar.get(Calendar.SECOND)
    }

    fun getMinute(): Int {
        return _calendar.get(Calendar.MINUTE)
    }

    fun getHour(): Int {
        return _calendar.get(Calendar.HOUR_OF_DAY)
    }

    fun getDay(): Int {
        return _calendar.get(Calendar.DATE)
    }

    fun getMonth(): Int {
        return _calendar.get(Calendar.MONTH) + 1
    }

    fun getYear(): Int {
        return _calendar.get(Calendar.YEAR)
    }

    fun getDayOfWeek(): Int {
        return _calendar.get(Calendar.DAY_OF_WEEK)
    }

    fun getDayOfYear(): Int {
        return _calendar.get(Calendar.DAY_OF_YEAR)
    }

    //http://www.cnblogs.com/zyw-205520/p/4632490.html
//https://github.com/anonl/luajpp2/blob/master/core/src/main/java/nl/weeaboo/lua2/lib/OsLib.java
    fun IsDaylightSavingTime(): Boolean {
        return _calendar.get(Calendar.DST_OFFSET) != 0
    }

    //https://github.com/weimingtom/mochalua/blob/master/Mochalua/src/com/groundspeak/mochalua/LuaOSLib.java
    fun getTicks(): Double {
        return _calendar.getTime().getTime().toDouble()
    }

    companion object {
        //https://github.com/anonl/luajpp2/blob/master/core/src/main/java/nl/weeaboo/lua2/lib/OsLib.java
        private val _t0 = System.currentTimeMillis()

        fun getClock(): Double {
            return (System.currentTimeMillis() - _t0) / 1000.0
        }
    }
}