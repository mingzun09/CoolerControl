package com.cooler.control.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 入口：让游戏空间认为“已连接散热背夹”，从而解锁 X 模式。
 * 作用域：com.oplus.games
 * 参考 LuckyTool 的 EnableXModeFeature / CloudConditionFeature。
 */
class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != GAME_PACKAGE) return
        try {
            hookBackClip(lpparam.classLoader)
            hookFeatureHelper(lpparam.classLoader)
            XposedBridge.log(TAG + ": hooked " + lpparam.packageName)
        } catch (t: Throwable) {
            XposedBridge.log(TAG + ": hook failed: " + t.message)
        }
    }

    /** 背夹连接判定：版本不同类名不同，逐个尝试，按方法名匹配 */
    private fun hookBackClip(cl: ClassLoader) {
        val classes = listOf(
            "business.module.perfmode.backclip.CoolingBackClipOplusFeature",
            "business.module.perfmode.backclip.CoolingBackClipRealmeFeature",
            "business.module.perfmode.CoolingBackClipHelper",
            "business.module.perfmode.CoolingBackClipFeature",
        )
        for (name in classes) {
            val clazz = XposedHelpers.findClassIfExists(name, cl) ?: continue
            var count = 0
            for (m in clazz.declaredMethods) {
                if (!isBoolean(m.returnType)) continue
                val n = m.name.lowercase()
                if (!(n.contains("isconnect") || n.contains("iscooltype"))) continue
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    })
                    count++
                } catch (t: Throwable) {
                    XposedBridge.log(TAG + ": hook " + name + "." + m.name + " failed" )
                }
            }
            if (count > 0) XposedBridge.log(TAG + ": " + name + " hooked " + count + " method(s)")
        }
    }

    /** 特性位：oplus.software.general.cooling.back.clip.enable */
    private fun hookFeatureHelper(cl: ClassLoader) {
        val helper = XposedHelpers.findClassIfExists(FEATURE_HELPER, cl) ?: return
        var count = 0
        for (m in helper.declaredMethods) {
            if (!isBoolean(m.returnType)) continue
            val params = m.parameterTypes
            if (params.size != 2 || params[0] != String::class.java) continue
            try {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args.firstOrNull() as? String ?: return
                        if (key == BACK_CLIP_KEY) param.result = true
                    }
                })
                count++
            } catch (t: Throwable) {
                XposedBridge.log(TAG + ": hook feature getter failed")
            }
        }
        if (count > 0) XposedBridge.log(TAG + ": " + FEATURE_HELPER + " hooked " + count + " method(s)")
    }

    private fun isBoolean(t: Class<*>): Boolean =
        t == Boolean::class.javaPrimitiveType || t == java.lang.Boolean::class.java

    companion object {
        private const val TAG = "CoolerXposed"
        private const val GAME_PACKAGE = "com.oplus.games"
        private const val FEATURE_HELPER = "com.oplus.addon.OplusFeatureHelper"
        private const val BACK_CLIP_KEY = "oplus.software.general.cooling.back.clip.enable"
    }
}
