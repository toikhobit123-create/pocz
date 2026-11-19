package com.example.model

enum class Rarity(val displayName: String, val shopCostGold: Int) {
    COMMON("Thường", 75),
    RARE("Hiếm", 125),
    EPIC("Sử Thi", 200),
    LEGENDARY("Huyền Thoại", 300)
}

enum class Region(val displayName: String) {
    PILTOVER_ZAUN("Piltover & Zaun"),
    DEMACIA("Demacia"),
    IONIA("Ionia"),
    FRELJORD("Freljord"),
    SHADOW_ISLES("Quần Đảo Bóng Đêm"),
    TARGON("Targon"),
    NOXUS("Noxus"),
    RUNETERRA("Runeterra")
}

enum class CardType(val displayName: String) {
    UNIT("Tùy Tùng"),
    CHAMPION("Tướng"),
    SPELL("Bài Phép"),
    EQUIPMENT("Trang Bị"),
    LANDMARK("Địa Danh"),
    CURSE("Lời Nguyền")
}

enum class SpellSpeed(val displayName: String, val description: String, val shortBadge: String) {
    BURST("Siêu Tốc", "Kích hoạt ngay lập tức mà không chuyển lượt ưu tiên.", "SIÊU"),
    FOCUS("Tập Trung", "Kích hoạt ngay ngoài giao tranh khi hàng chờ phép đang trống.", "TẬP"),
    FAST("Nhanh", "Vào hàng chờ phép và chuyển lượt ưu tiên; dùng được trong giao tranh hoặc phản ứng.", "NHANH"),
    SLOW("Chậm", "Vào hàng chờ phép và chuyển lượt ưu tiên; chỉ dùng ngoài giao tranh khi hàng chờ trống.", "CHẬM"),
    SKILL("Kỹ Năng", "Kỹ năng do bài quân tạo ra và đặt vào hàng chờ phép.", "KỸ"),
    NONE("Bài Quân", "Triệu hồi trực tiếp lên bàn đấu.", "QUÂN")
}

enum class KeywordCategory {
    COMBAT_POSITIVE,
    NEGATIVE_STATUS,
    CARD_PROPERTY
}

enum class Keyword(
    val displayName: String,
    val category: KeywordCategory,
    val isSharablePositive: Boolean,
    val isStackable: Boolean,
    val description: String
) {
    // 8.1 Từ khóa giao tranh & hiệu ứng có lợi
    OVERWHELM(
        "Áp Đảo", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Sát thương giao tranh dư ra sau khi tiêu diệt kẻ chặn sẽ gây trực tiếp lên Nhà Chính đối thủ."
    ),
    QUICK_ATTACK(
        "Tấn Công Nhanh", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Khi tấn công, ra đòn trước kẻ chặn. Nếu kẻ chặn bị hạ gục, không chịu sát thương phản công."
    ),
    DOUBLE_ATTACK(
        "Tấn Công Kép", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Khi tấn công, ra đòn 2 lần: 1 lần trước kẻ chặn và 1 lần đồng thời."
    ),
    ELUSIVE(
        "Lẩn Khuất", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Chỉ có thể bị chặn bởi bài quân địch cũng có Lẩn Khuất hoặc Tinh Mắt."
    ),
    SHARPSIGHT(
        "Tinh Mắt", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Cho phép bài quân phòng thủ chặn được các kẻ tấn công Lẩn Khuất."
    ),
    FEARSOME(
        "Đáng Sợ", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Chỉ có thể bị chặn bởi bài quân địch có từ 3 Sức Mạnh trở lên."
    ),
    BRASH(
        "Hùng Hổ", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Chỉ có thể bị chặn bởi bài quân địch có từ 3 Máu hiện tại trở lên."
    ),
    CHALLENGER(
        "Thách Đấu", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Khi tấn công, có thể ép một bài quân địch hợp lệ ra chặn mình."
    ),
    IMPACT(
        "Tác Động", KeywordCategory.COMBAT_POSITIVE, true, true,
        "Cộng dồn. Khi đánh trúng lúc tấn công, gây 1 sát thương lên Nhà Chính đối thủ mỗi điểm cộng dồn."
    ),
    LIFESTEAL(
        "Hút Máu", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Sát thương bài quân này gây ra sẽ hồi lượng máu tương ứng cho Nhà Chính của bạn."
    ),
    REGENERATION(
        "Hồi Phục", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Hồi toàn bộ sát thương vào Cuối Vòng Đấu, khôi phục về Máu tối đa."
    ),
    TOUGH(
        "Kiên Cường", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Giảm 1 sát thương nhận vào từ mọi nguồn."
    ),
    BARRIER(
        "Lá Chắn", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Chặn đứng lần sát thương tiếp theo mà bài quân này phải chịu trong vòng."
    ),
    SPELLSHIELD(
        "Khiên Phép", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Vô hiệu hóa bài phép hoặc Kỹ Năng địch tiếp theo tác động lên bài quân này."
    ),
    FURY(
        "Cuồng Nộ", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Nhận +1|+1 mỗi khi bài quân này tiêu diệt một bài quân khác."
    ),
    SCOUT(
        "Trinh Sát", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Lần đầu mỗi vòng bạn chỉ tấn công bằng các bài quân Trinh Sát, được Tập Hợp."
    ),
    LURK(
        "Ẩn Nấp", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Khi tấn công mà lá trên cùng bộ bài có Ẩn Nấp, ban +1|+0 cho các đồng minh Ẩn Nấp."
    ),
    EVOLVE(
        "Tiến Hóa", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Nhận vĩnh viễn +2|+2 khi bạn đã sở hữu từ 6 từ khóa có lợi khác nhau trong trận."
    ),
    AUGMENT(
        "Nâng Cấp", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Nhận +1|+0 mỗi khi bạn sử dụng một lá bài được tạo ra."
    ),
    FATED(
        "Định Mệnh", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Mỗi vòng 1 lần, nhận +1|+1 khi được nhắm mục tiêu bởi lá bài hoặc hiệu ứng đồng minh."
    ),
    FORMIDABLE(
        "Uy Dũng", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Ra đòn bằng lượng Máu hiện tại thay vì Sức Mạnh."
    ),
    HALLOWED(
        "Thiêng Liêng", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Khi hy sinh, tăng cộng dồn Thiêng Liêng lên 1. Đợt tấn công đầu tiên nhận +X|+0."
    ),
    SPIRIT(
        "Tinh Linh", KeywordCategory.COMBAT_POSITIVE, true, true,
        "Cộng dồn. Ban +1|+1 cho mỗi điểm Tinh Linh trên bài quân."
    ),
    DEATHLESS(
        "Bất Tử", KeywordCategory.COMBAT_POSITIVE, true, false,
        "Lần đầu bài quân này hy sinh, hồi sinh với 1 Máu và bị Choáng, sau đó mất Bất Tử."
    ),

    // 8.2 Từ khóa bất lợi & khống chế
    EPHEMERAL(
        "Bóng Ma", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Hy sinh ngay sau khi ra đòn trong giao tranh hoặc vào Cuối Vòng Đấu."
    ),
    VULNERABLE(
        "Tổn Thương", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Đối thủ có thể ép bài quân này ra chặn bất kỳ kẻ tấn công nào."
    ),
    STUN(
        "Choáng", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Rời khỏi giao tranh và không thể tấn công hoặc phòng thủ trong vòng này."
    ),
    FROSTBITE(
        "Đóng Băng", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Giảm Sức Mạnh về 0 trong vòng hiện tại (các hiệu ứng tăng chỉ số sau đó vẫn tính bình thường)."
    ),
    SILENCE(
        "Câm Lặng", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Xóa bỏ toàn bộ văn bản, từ khóa và hiệu ứng thay đổi chỉ số trên bài quân."
    ),
    GLOOM(
        "U Ám", KeywordCategory.NEGATIVE_STATUS, false, true,
        "Cộng dồn. Gây -1|-1 cho mỗi điểm U Ám trên bài quân."
    ),
    CANT_BLOCK(
        "Không Thể Chặn", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Bài quân này không thể tham gia chặn đòn."
    ),
    IMMOBILE(
        "Bất Động", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Bài quân này không thể tấn công hay chặn đòn."
    ),
    SHACKLED(
        "Xiềng Xích", KeywordCategory.NEGATIVE_STATUS, false, false,
        "Khóa chặt bài quân cho đến khi lá Lời Nguyền Xiềng Xích được giải trừ từ trên tay."
    ),

    // 8.3 Thuộc tính lá bài
    ATTUNE(
        "Điều Hòa", KeywordCategory.CARD_PROPERTY, false, false,
        "Hồi 1 Năng Lượng Phép khi được triệu hồi."
    ),
    FLEETING(
        "Chóng Tàn", KeywordCategory.CARD_PROPERTY, false, false,
        "Bị loại bỏ khỏi tay vào Cuối Vòng Đấu nếu chưa sử dụng."
    ),
    ELEMENTAL(
        "Nguyên Tố", KeywordCategory.CARD_PROPERTY, false, false,
        "Phân loại bài phép/Kỹ Năng Nguyên Tố giúp kích hoạt cộng hưởng và giảm tiêu hao."
    ),
    DAYBREAK(
        "Hừng Đông", KeywordCategory.CARD_PROPERTY, false, false,
        "Kích hoạt hiệu ứng thưởng nếu đây là lá bài đầu tiên bạn chơi trong vòng."
    );

    companion object {
        val sharablePool: List<Keyword> by lazy {
            entries.filter { it.isSharablePositive && it != EVOLVE }
        }
    }
}

enum class TriggerHook(val displayName: String) {
    PLAY("Xuất Trận"),
    SUMMON("Triệu Hồi"),
    STRIKE("Ra Đòn"),
    NEXUS_STRIKE("Đánh Nhà Chính"),
    ATTACK("Tấn Công"),
    SUPPORT("Hỗ Trợ"),
    LAST_BREATH("Trăng Trối"),
    ROUND_START("Đầu Vòng Đấu"),
    ROUND_END("Cuối Vòng Đấu"),
    LEVEL_UP("Thăng Cấp")
}

enum class StateCondition(val displayName: String, val description: String) {
    SLAY("Hạ Gục", "Tính số bài quân địch bị tiêu diệt bởi sát thương hoặc hiệu ứng kết liễu của bạn."),
    PLUNDER("Cướp Bóc", "Kích hoạt nếu bạn đã gây sát thương lên Nhà Chính địch trong vòng này."),
    REPUTATION("Danh Tiếng", "Kích hoạt khi đồng minh có 5+ Sức Mạnh đã ra đòn từ 4 lần trở lên."),
    FLOW("Lưu Chuyển", "Kích hoạt Đầu Vòng Đấu nếu bạn đã dùng 2+ bài phép/Kỹ Năng ở vòng trước."),
    DAYBREAK("Hừng Đông", "Kích hoạt nếu đây là lá bài đầu tiên được chơi trong vòng."),
    NIGHTFALL("Chạng Vạng", "Kích hoạt nếu đây KHÔNG phải lá bài đầu tiên trong vòng."),
    ENLIGHTENED("Khai Sáng", "Kích hoạt khi đạt mốc 10 Năng Lượng Tối Đa."),
    DEEP("Thẩm Sâu", "Kích hoạt khi bộ bài còn 15 lá trở xuống.")
}
