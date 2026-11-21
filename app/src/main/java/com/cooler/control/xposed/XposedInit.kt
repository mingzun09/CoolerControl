package com.cooler.control.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 入口（诊断模式）。
 *
 * 现状：强制“已连接背夹”会让游戏空间进入背夹处理分支，
 * 而该分支依赖的类（如 wgd）在本固件中已被裁剪，
 * 结果触发 NoClassDefFoundError，导致游戏助手侧边栏反复崩溃重启。
 *
 * 因此这里只做探测日志，不再改写任何返回值。
 */
class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != GAME_PACKAGE) return
        XposedBridge.log(TAG + ": loaded in " + lpparam.packageName + " (diagnostic only)")
        try {
            probe(lpparam.classLoader)
        } catch (t: Throwable) {
            XposedBridge.log(TAG + ": probe failed " + t)
        }
    }

    /** 只观察调用，不改变行为 */
    private fun probe(cl: ClassLoader) {
        for (name in CANDIDATES) {
            val c = runCatching { XposedHelpers.findClassIfExists(name, cl) }.getOrNull() ?: continue
            XposedBridge.log(TAG + ": found " + name + " methods=" + c.declaredMethods.size)
            for (m in c.declaredMethods) {
                val rt = m.returnType
                val isBool = rt == Boolean::class.javaPrimitiveType || rt == java.lang.Boolean::class.java
                if (!isBool) continue
                if (m.parameterCount > 2) continue
                runCatching {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            XposedBridge.log(TAG + ": call " + name + "." + m.name + " = " + param.result)
                        }
                    })
                }
            }
        }
    }

    companion object {
        private const val TAG = "CoolerXposed"
        private const val GAME_PACKAGE = "com.oplus.games"
        private val CANDIDATES = listOf(
            "business.module.perfmode.backclip.a",
            "business.module.perfmode.backclip.b",
            "business.module.perfmode.backclip.c",
            "business.module.perfmode.backclip.d",
        )
    }
}
