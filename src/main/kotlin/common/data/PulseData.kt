package cn.sweetberry.codes.dglab.websocket.common.data

import kotlinx.serialization.json.Json

/**
 * 脉冲/波形数据
 *
 * 用于存储和传输刺激设备的波形数据。
 * 波形数据由一系列 16 进制字符串组成，每条字符串代表一个波形单元。
 *
 * 特点：
 * - 不可变设计：创建后不可修改
 * - 验证机制：自动验证波形格式（16 位十六进制）
 * - JSON 序列化：支持与协议消息的相互转换
 *
 * 使用示例：
 * ```
 * // 创建空波形数据
 * val empty = PulseData.empty()
 *
 * // 从十六进制字符串列表创建
 * val pulse = PulseData.fromHexData(listOf("0123456789abcdef", "fedcba9876543210"))
 *
 * // 添加波形单元
 * val pulse2 = PulseData.empty().apply {
 *     addWaveUnit("0123456789abcdef")
 * }
 * ```
 *
 * @see Payload.sendPulse 发送波形数据
 */
class PulseData private constructor(
    /**
     * 波形数据列表
     *
     * 每个元素是一个 16 位的十六进制字符串
     */
    private val data: MutableList<String>
) {

    /**
     * 添加单个波形单元
     *
     * @param hex 16 位十六进制字符串
     * @throws IllegalArgumentException 格式无效时
     */
    fun addWaveUnit(hex: String) {
        data.add(validateAndNormalize(hex))
    }

    /**
     * 批量添加波形单元
     *
     * @param hexes 十六进制字符串列表
     */
    fun addWaveUnits(hexes: List<String>) {
        hexes.forEach { addWaveUnit(it) }
    }

    /**
     * 转换为 JSON 字符串
     *
     * @return JSON 格式的字符串数组
     */
    fun toJson(): String =
        Json.encodeToString(data)

    companion object {
        /**
         * 创建空波形数据
         *
         * @return 空的 PulseData 实例
         */
        fun empty(): PulseData =
            PulseData(mutableListOf())

        /**
         * 从十六进制数据创建
         *
         * @param hexes 十六进制字符串列表
         * @return 包含给定数据的 PulseData 实例
         */
        fun fromHexData(hexes: List<String>): PulseData =
            PulseData(mutableListOf<String>().apply {
                hexes.forEach { add(validateAndNormalize(it)) }
            })

        /**
         * 验证并规范化十六进制字符串
         *
         * @param hex 输入的十六进制字符串
         * @return 小写规范化后的字符串
         * @throws IllegalArgumentException 格式无效时
         */
        private fun validateAndNormalize(hex: String): String {
            require(hex.length == 16) {
                "Wave string must be 16 hex characters (8 bytes)"
            }
            require(hex.all { it in "0123456789abcdefABCDEF" }) {
                "Wave string must be a valid hex string"
            }
            return hex.lowercase()
        }
    }

    /**
     * 获取波形单元数量
     *
     * @return 波形数据条数
     */
    fun size(): Int {
        return data.size
    }
}