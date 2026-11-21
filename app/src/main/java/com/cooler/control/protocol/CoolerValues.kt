package com.cooler.control.protocol

/**
 * 取值全部来自设置插件的资源数组（entries_values_*），已在真机与用户反馈中核对：
 *  - 制冷模式：0=自动 1=静音 2=冰封（插件为 3 项；本机固件另有第 4 档，先按 3=高效 处理）
 *  - 灯效模式：0=关闭 1=彩虹 2=呼吸 3=流光 4=常亮
 *  - 灯效配色：0=彩色 1=单色 2=三色
 */
enum class CoolerMode(val value: Int, val label: String, val desc: String) {
    AUTO(0, "自动", "根据温度自动调节"),
    SILENT(1, "静音", "低转速，安静优先"),
    TURBO(3, "高效", "高转速，制冷更强"),
    ICE(2, "冰封", "最高转速，制冷最强");

    companion object {
        fun from(v: Int?): CoolerMode? = entries.firstOrNull { it.value == v }

        /** 展示顺序与 HyperOS 一致 */
        val displayOrder = listOf(AUTO, SILENT, TURBO, ICE)
    }
}

/** 灯效模式（属性 0x42 第 1 字节） */
enum class LightMode(val value: Int, val label: String) {
    OFF(0, "关闭"),
    RAINBOW(1, "彩虹"),
    BREATH(2, "呼吸"),
    FLOW(3, "流光"),
    ALWAYS_ON(4, "常亮");

    companion object {
        fun from(v: Int?): LightMode? = entries.firstOrNull { it.value == v }

        val displayOrder = listOf(ALWAYS_ON, BREATH, FLOW, RAINBOW, OFF)
    }
}

/** 灯效配色（属性 0x42 第 2 字节） */
enum class LightColor(val value: Int, val label: String) {
    COLORFUL(0, "彩色"),
    SINGLE(1, "单色"),
    THREE(2, "三色");

    companion object {
        fun from(v: Int?): LightColor? = entries.firstOrNull { it.value == v }

        val displayOrder = listOf(SINGLE, THREE, COLORFUL)
    }
}

/** 温度体感文案，阈值参考小米设置插件 */
object TempFeeling {
    fun of(temp: Int): String = when {
        temp <= 0 -> "冰凉"
        temp <= 10 -> "有点凉"
        temp <= 25 -> "无感"
        temp <= 35 -> "有点热"
        else -> "很热"
    }
}
