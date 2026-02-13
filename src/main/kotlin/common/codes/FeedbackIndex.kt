package cn.sweetberry.codes.dglab.websocket.common.codes

enum class FeedbackIndex(val code: String, val desc: String) {
    A_CIRCLE("0", "A通道：○"),        // A通道圆
    A_TRIANGLE("1", "A通道：△"),      // A通道三角
    A_SQUARE("2", "A通道：□"),        // A通道方块
    A_STAR("3", "A通道：☆"),          // A通道星
    A_HEXAGON("4", "A通道：⬡"),       // A通道六边形
    B_CIRCLE("5", "B通道：○"),        // B通道圆
    B_TRIANGLE("6", "B通道：△"),      // B通道三角
    B_SQUARE("7", "B通道：□"),        // B通道方块
    B_STAR("8", "B通道：☆"),          // B通道星
    B_HEXAGON("9", "B通道：⬡");       // B通道六边形

    companion object {
        fun fromCode(code: String): FeedbackIndex? =
            entries.firstOrNull { it.code == code }
    }
}