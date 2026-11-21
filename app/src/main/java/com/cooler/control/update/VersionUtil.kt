package com.cooler.control.update

/** 版本号比较，纯函数，便于单测 */
object VersionUtil {

    /** candidate 是否比 current 新 */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").mapNotNull { it.trim().toIntOrNull() }
        val b = current.split(".").mapNotNull { it.trim().toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
