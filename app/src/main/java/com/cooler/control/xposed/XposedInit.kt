package com.cooler.control.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 入口：伪装“已连接散热背夹”以解锁 X 模式。
 *
 * 关键点：伪装后游戏空间会进入背夹处理分支，而该分支引用的部分类
 * （如 wgd）在本固件被裁剪，会抛 NoClassDefFoundError 导致侧边栏崩溃重启。
 * 因此同时把已知会踩空的入口（GameAiToolFeature.gameStart）短路掉。
 */
class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != GAME_PACKAGE) return
        try {
            hookBackClip(lpparam.classLoader)
            hookCrashGuards(lpparam.classLoader)
            XposedBridge.log(TAG + ": ready")
        } catch (t: Throwable) {
            XposedBridge.log(TAG + ": fatal " + t)
        }
    }

    private fun hookBackClip(cl: ClassLoader) {
        for (name in CANDIDATES) {
            val c = runCatching { XposedHelpers.findClassIfExists(name, cl) }.getOrNull() ?: continue
            var hooked = 0
            for (m in c.declaredMethods) {
                val rt = m.returnType
                val isBool = rt == Boolean::class.javaPrimitiveType || rt == java.lang.Boolean::class.java
                val isObj = rt == Any::class.java
                if (!isBool && !isObj) continue
                if (m.parameterCount > 2) continue
                runCatching {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (isBool) {
                                param.result = true
                            } else if (param.result is Boolean) {
                                param.result = true
                            }
                        }
                    })
                    hooked++
                }
            }
            XposedBridge.log(TAG + ": " + name + " hooked " + hooked)
        }
    }

    /** 短路会踩空的分支，避免 NoClassDefFoundError */
    private fun hookCrashGuards(cl: ClassLoader) {
        for (name in GUARDS) {
            val c = runCatching { XposedHelpers.findClassIfExists(name, cl) }.getOrNull()
            if (c == null) {
                XposedBridge.log(TAG + ": guard not found " + name)
                continue
            }
            var count = 0
            for (m in c.declaredMethods) {
                if (m.name != "gameStart" && m.name != "gameStop") continue
                runCatching {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (param.method is java.lang.reflect.Method &&
                                (param.method as java.lang.reflect.Method).returnType == Void.TYPE
                            ) {
                                param.result = null
                                XposedBridge.log(TAG + ": skipped " + param.method.name)
                            }
                        }
                    })
                    count++
                }
            }
            XposedBridge.log(TAG + ": guard " + name + " hooked " + count)
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
        private val GUARDS = listOf(
            "com.oplus.games.feature.gameaitool.game.GameAiToolFeature",
        )
    }
}
