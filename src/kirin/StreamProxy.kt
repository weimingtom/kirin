package kirin

import java.io.File
import java.io.RandomAccessFile
import java.io.FileNotFoundException
import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader

class StreamProxy {
    var type = TYPE_FILE
    var isOK = false
    private var _file: RandomAccessFile? = null

    private constructor() {
        isOK = false
    }

    constructor(path: String?, modeStr: String?) {
        isOK = false
        try {
            _file = RandomAccessFile(path, modeStr)
            isOK = true
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        type = TYPE_FILE
    }

    fun Flush() {
        if (type == TYPE_STDOUT) { //RandomAccessFile flush not need ?
        }
    }

    fun Close() {
        if (type == TYPE_STDOUT) {
            if (_file != null) {
                try {
                    _file!!.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                _file = null
            }
        }
    }

    fun Write(buffer: ByteArray?, offset: Int, count: Int) {
        if (type == TYPE_STDOUT) {
            print(String(buffer!!, offset, count))
        } else if (type == TYPE_STDERR) {
            System.err.print(String(buffer!!, offset, count))
        } else if (type == TYPE_FILE) {
            if (_file != null) {
                try {
                    _file!!.writeBytes(String(buffer!!, offset, count))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } else { //FIXME:TODO
        }
    }

    fun Read(buffer: ByteArray?, offset: Int, count: Int): Int {
        if (type == TYPE_FILE) {
            if (_file != null) {
                try {
                    return _file!!.read(buffer, offset, count)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return 0
    }

    fun Seek(offset: Long, origin: Int): Int {
        if (type == TYPE_FILE) {
            if (_file != null) { //CLib.SEEK_SET,
//CLib.SEEK_CUR,
//CLib.SEEK_END
                var pos: Long = -1
                if (origin == CLib.SEEK_CUR) {
                    pos = offset
                } else if (origin == CLib.SEEK_CUR) {
                    try {
                        pos = _file!!.getFilePointer() + offset
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                } else if (origin == CLib.SEEK_END) {
                    try {
                        pos = _file!!.length() + offset
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                try {
                    _file!!.seek(pos)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return 0
    }

    fun ReadByte(): Int {
        return if (type == TYPE_STDIN) {
            try {
                return System.`in`.read()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            0
        } else if (type == TYPE_FILE) {
            if (_file != null) {
                try {
                    return _file!!.read()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            0
        } else {
            0
        }
    }

    fun ungetc(c: Int) {
        if (type == TYPE_FILE) {
            if (_file != null) {
                try {
                    _file!!.seek(_file!!.getFilePointer() - 1)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getPosition(): Long {
        if (type == TYPE_FILE) {
            if (_file != null) {
                try {
                    return _file!!.getFilePointer()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return 0
    }

    fun isEof(): Boolean {
        if (type == TYPE_FILE) {
            if (_file != null) {
                try {
                    return _file!!.getFilePointer() >= _file!!.length()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return true
    }

    companion object {
        private const val TYPE_FILE = 0
        private const val TYPE_STDOUT = 1
        private const val TYPE_STDIN = 2
        private const val TYPE_STDERR = 3
        //--------------------------------------
        fun tmpfile(): StreamProxy {
            return StreamProxy()
        }

        fun OpenStandardOutput(): StreamProxy {
            val result = StreamProxy()
            result.type = TYPE_STDOUT
            result.isOK = true
            return result
        }

        fun OpenStandardInput(): StreamProxy {
            val result = StreamProxy()
            result.type = TYPE_STDIN
            result.isOK = true
            return result
        }

        fun OpenStandardError(): StreamProxy {
            val result = StreamProxy()
            result.type = TYPE_STDERR
            result.isOK = true
            return result
        }

        fun GetCurrentDirectory(): String {
            val directory = File("")
            return directory.absolutePath
        }

        fun Delete(path: String?) {
            File(path).delete()
        }

        fun Move(path1: String?, path2: String?) {
            File(path1).renameTo(File(path2))
        }

        fun GetTempFileName(): String? {
            try {
                return File.createTempFile("abc", ".tmp").absolutePath
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }

        fun ReadLine(): String? {
            val `in` = BufferedReader(InputStreamReader(System.`in`))
            try {
                return `in`.readLine()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }

        fun Write(str: String?) {
            print(str)
        }

        fun WriteLine() {
            println()
        }

        fun ErrorWrite(str: String?) {
            System.err.print(str)
            System.err.flush()
        }
    }
}