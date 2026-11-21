# 小米冰封散热背夹 · 蓝牙协议（实测确认）

> 来源：静态反编译 `com.xiaomi.bluetooth`（Bluetooth Extension_16.apk）中的 `com.xiaomi.aivsbluetoothsdk`，
> 并经 Windows BLE 主机实际收发验证（2026-11-21，设备 "小米背夹" 54:14:A7:DD:19:27，固件未知）。
> 结论：**无需配对/绑定，直接 GATT 读写即可控制**。

---

## 1. GATT 结构（实测枚举）

| 角色 | UUID | 属性 |
|---|---|---|
| Service | `0000af00-0000-1000-8000-00805f9b34fb` | 主控制服务 |
| 写（背夹通道） | `0000af07-...` | Write Without Response |
| 通知（背夹通道） | `0000af08-...` | Notify |
| 写（通用通道） | `0000af01-...` / `0000af03-...` | Write Without Response |
| 通知（通用通道） | `0000af02-...` / `0000af04-...` | Notify |

设备还暴露 0x1800 / 0x1801 / 0x180A / 0x1813 / 0xFEE0。**控制只用 AF00 服务下的 AF07 写 + AF08 通知。**

SDK 中的命名：`UUID_SERVICE=AF00`、`UUID_WRITE_ANBEI=AF07`、`UUID_NOTIFICATION_ANBEI=AF08`（"ANBEI" = 背夹）。

---

## 2. 帧格式

```
FE DC BA | flags | opcode | len_hi | len_lo | seq | payload(len-1) | EF
```

| 字段 | 说明 |
|---|---|
| `FE DC BA` | 固定帧头 3 字节 |
| flags | bit7 = 类型（1=命令，0=响应），bit6 = 需要响应，bit0-2 = 目标应用（1=MIUI）。命令固定 **0xC1** |
| opcode | 命令码，见下表 |
| len | 大端 16 位，= payload 长度 + 1（含 seq） |
| seq | 序号，自增；响应里回显（响应方向第 2 字节） |
| payload | TLV 串，见下 |
| `EF` | 固定帧尾 |

命令码：

| opcode | 名称 | 说明 |
|---|---|---|
| 0x02 | GET_TARGET_INFO | 设备信息（vid/pid/版本/名称） |
| 0xF2 (242) | SET_DEVICE_CONFIG | 写配置项 |
| 0xF3 (243) | GET_DEVICE_CONFIG | 读配置项（payload = 2 字节大端属性 ID 列表） |
| 0xF4 (244) | NOTIFY_DEVICE_CONFIG | 设备主动上报（通知方向） |

### TLV

```
[len] [type_hi] [type_lo] [value ...]        len = 2 + value 长度
```

---

## 3. 配置属性

| 属性 | 名称 | 长度 | 取值 |
|---|---|---|---|
| 0x003F (63) | **温度** | 1 | 有符号字节，单位 ℃。例：`0x0D`=13℃，`0xF6`=−10℃ |
| 0x0040 (64) | **制冷模式** | 1 | 0=静音 / 1=自动 / 2=冰封 |
| 0x0041 (65) | **定时关闭** | 2（大端） | 剩余秒数；`0xFFFF` = 不启用 |
| 0x0042 (66) | **RGB 灯效** | 1~2 | 第 1 字节=灯效模式，第 2 字节=配色方案；`01 00` = 默认 |

小米设置插件里的写法与之一一对应：
`setCommonCommand(64, "0"+模式+手机温度)`、`(65, 剩余秒hex)`、`(66, "0"+灯模式+"0"+配色)`。

---

## 4. 实测报文（原样抓取）

读全部配置（温度 13℃、模式 0、定时关闭、灯效 01/00）：

```
TX  FE DC BA C1 F3 00 09 01 00 3F 00 40 00 41 00 42 EF
RX  FE DC BA 01 F3 00 14 00 01 | 03 00 3F 0C | 03 00 40 00 | 04 00 41 FF FF | 04 00 42 01 00 | EF
```

响应体结构：`[status:1][seq:1][TLV...]`，`status=0` 表示成功。

设置模式为"冰封"：

```
TX  FE DC BA C1 F2 00 05 02 | 03 00 40 02 | EF
RX  FE DC BA 01 F2 00 02 00 02 EF           （status=0，seq 回显=2）
```

设置灯效（模式 2、配色 3）与定时（3600 秒）：

```
TX  ... F2 00 06 04 | 04 00 42 02 03 | EF
TX  ... F2 00 06 06 | 04 00 41 0E 10 | EF
```

设备主动上报（约每 2–3 秒一批，`flags=0x81`，可一批包含多个 TLV）：

```
RX  FE DC BA 81 F4 00 05 ED 03 00 3F 0D EF    温度 13℃
RX  FE DC BA 81 F4 00 05 F0 03 00 3F 0C EF    温度 12℃
RX  FE DC BA 81 F4 00 05 F1 03 00 40 02 EF    模式 2
RX  FE DC BA 81 F4 00 06 F2 04 00 42 01 00 EF 灯效 01/00
```

实测曲线（模式=冰封，16 秒）：13℃ → 12 → 11 → 10 → 9 → 8℃，确认 0x3F 为温度且上报实时。

---

## 5. 行为要点

- **无需配对**：未绑定状态下直接写即可，一加 15 上无需走系统配对流程。
- 写特性是 **Write Without Response**，帧很小（≤20 字节），无需 MTU 协商。
- 通知需先写 CCCD（Notify）。
- 设备只在**连接建立后**才上报；空闲时不上报。
- 定时关闭是**倒计时**：写入 N 后，回读会看到 N−经过秒数。
- 已知风险：若先前的连接未释放，Windows 侧会短暂出现"服务/特征为空"，重试 1–3 次即可（Android 侧同名现象需处理，见下）。

---

## 5.1 配置取值（来自插件资源数组，已与真机核对）

| 属性 | 值 | 含义 |
|---|---|---|
| 0x40 制冷模式 | 0 / 1 / 2 / 3 | 自动 / 静音 / 冰封 / 高效（3 为本机固件的第 4 档） |
| 0x42 灯效（第 1 字节） | 0 / 1 / 2 / 3 / 4 | 关闭 / 彩虹 / 呼吸 / 流光 / 常亮 |
| 0x42 灯效（第 2 字节） | 0 / 1 / 2 | 彩色 / 单色 / 三色 |

> 来源：`entries_values_cooler_mode` = ["0","1","2"]、`entries_values_cooler_light_mode` = ["1","2","3","4"]、
> `entries_values_cooler_light_color_scheme` = ["0","1","2"]，配合 `entries_cooler_*` 的标签顺序。

## 6. 待确认项（后续迭代）

| 项 | 线索 |
|---|---|
| 改名 | 插件走 `setCommonCommand(22, 新名称)`；AIVS 中 `ATTR_TYPE_NAME=0x0000`，需实测 |
| 固件版本 / vid pid | `GET_TARGET_INFO`(0x02) 返回 `02 03 06 10 00`，结构待拆 |
| 灯效模式枚举值 | 插件 `entries_cooler_light_mode`，需从 split 资源解出 0..3 的中文名 |
| 模式枚举中文名 | 同上（静音/自动/冰封） |
| OTA | 暂不实现 |