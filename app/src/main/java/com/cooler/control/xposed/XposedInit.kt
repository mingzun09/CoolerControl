package com.cooler.control.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != GAME_PACKAGE) return
        try {
            hookRealmeFeature(lpparam.classLoader)
        } catch (t: Throwable) {
            XposedBridge.log(TAG + ": fatal " + t)
        }
    }

    private fun hookRealmeFeature(cl: ClassLoader) {
        val c = findClass(REALME_FEATURE, cl)
        if (c == null) {
            XposedBridge.log(TAG + ": NOT FOUND " + REALME_FEATURE)
            return
        }
        forceFlags(c)
        var hooked = 0
        for (m in c.declaredMethods) {
            val rt = m.returnType
            val isBool = rt == Boolean::class.javaPrimitiveType || rt == java.lang.Boolean::class.java
            val isSuspend = rt == Any::class.java
            if (!isBool && !isSuspend) continue
            if (m.name == "name") continue
            try {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        forceFlags(param.thisObject?.javaClass ?: c)
                        val r = param.result
                        if (isBool || r is Boolean) {
                            param.result = true
                            XposedBridge.log(TAG + ": force " + m.name + " -> true")
                        }
                    }
                })
                hooked++
            } catch (t: Throwable) {
                XposedBridge.log(TAG + ": hook fail " + m.name + " -> " + t)
            }
        }
        XposedBridge.log(TAG + ": " + REALME_FEATURE + " hooked " + hooked)
    }

    private fun forceFlags(c: Class<*>) {
        try {
            XposedHelpers.setStaticBooleanField(c, "h", true)
            XposedHelpers.setStaticBooleanField(c, "i", true)
        } catch (_: Throwable) {
        }
    }

    private fun findClass(name: String, cl: ClassLoader?): Class<*>? =
        try {
            XposedHelpers.findClassIfExists(name, cl)
        } catch (t: Throwable) {
            null
        }

    companion object {
        private const val TAG = "CoolerXposed"
        private const val GAME_PACKAGE = "com.oplus.games"
        private const val REALME_FEATURE = "business.module.perfmode.backclip.CoolingBackClipRealmeFeature"
    }
}