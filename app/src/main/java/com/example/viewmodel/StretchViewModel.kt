package com.example.viewmodel

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class StretchExercise(
    val id: String,
    val name: String,
    val targetArea: String,
    val durationSeconds: Int,
    @DrawableRes val imageRes: Int,
    val instructions: List<String>,
    val breathingTip: String
)

class StretchViewModel(
    private val random: Random = Random.Default
) : ViewModel() {

    val exercises: List<StretchExercise> = listOf(
        StretchExercise(
            id = "stretch_neck_tilt",
            name = "Nghiêng Cổ & Thư Giãn Vai",
            targetArea = "Cổ & Cơ Cầu Vai Trên",
            durationSeconds = 30,
            imageRes = R.drawable.img_stretch_neck_roll,
            instructions = listOf(
                "Ngồi thẳng lưng, thả lỏng hai vai và hạ nhẹ xuống xa khỏi tai.",
                "Nhẹ nhàng nghiêng tai phải về phía vai phải cho đến khi cảm thấy căng nhẹ.",
                "Giữ yên 15 giây, đưa đầu về giữa rồi lặp lại với bên trái."
            ),
            breathingTip = "Hít vào chậm rãi khi vươn thẳng cột sống; thở ra để thả lỏng hoàn toàn hai vai."
        ),
        StretchExercise(
            id = "stretch_chest_opener",
            name = "Mở Rộng Ngực & Cột Sống Ngồi",
            targetArea = "Ngực & Vai Trước",
            durationSeconds = 30,
            imageRes = R.drawable.img_stretch_chest_opener,
            instructions = listOf(
                "Đan các ngón tay ra sau lưng hoặc nắm nhẹ vào thành sau của ghế.",
                "Cuộn hai bả vai ra sau và xuống dưới, đồng thời ưỡn nhẹ xương ức hướng lên.",
                "Giữ tư thế mở ngực trong 30 giây, giữ cằm song song với mặt sàn."
            ),
            breathingTip = "Hít thở sâu mở rộng lồng ngực để giải tỏa căng cứng cơ ngực."
        ),
        StretchExercise(
            id = "stretch_wrist_flexor",
            name = "Giãn Cơ Cẳng Tay & Cổ Tay",
            targetArea = "Cổ Tay & Cẳng Tay",
            durationSeconds = 25,
            imageRes = R.drawable.img_stretch_wrist_flexor,
            instructions = listOf(
                "Duỗi thẳng một tay ra trước mặt, lòng bàn tay hướng ra ngoài và các ngón hướng lên.",
                "Dùng tay còn lại kéo nhẹ các đầu ngón tay về phía thân người.",
                "Giữ 25 giây mỗi bên tay, giữ khuỷu tay hơi mềm và vai thả lỏng."
            ),
            breathingTip = "Hít thở đều qua mũi và thả nhẹ lực ngay nếu thấy căng buốt."
        ),
        StretchExercise(
            id = "stretch_cat_cow",
            name = "Tư Thế Mèo - Bò Linh Hoạt Cột Sống",
            targetArea = "Cột Sống Trên & Thắt Lưng",
            durationSeconds = 40,
            imageRes = R.drawable.img_stretch_cat_cow,
            instructions = listOf(
                "Chống hai tay và đầu gối xuống thảm, cổ tay thẳng dưới vai, đầu gối dưới hông.",
                "Hít vào, hạ bụng xuống, nâng ngực và võng nhẹ lưng (tư thế Bò).",
                "Thở ra, cong tròn lưng hướng lên trần và thu cằm về ngực (tư thế Mèo). Lặp lại nhịp nhàng."
            ),
            breathingTip = "Đồng bộ một nhịp hít thở trọn vẹn với mỗi chuyển động uốn cột sống."
        ),
        StretchExercise(
            id = "stretch_hamstring_fold",
            name = "Gập Người Giãn Gân Kheo Ngồi",
            targetArea = "Gân Kheo & Lưng Dưới",
            durationSeconds = 35,
            imageRes = R.drawable.img_stretch_hamstring_fold,
            instructions = listOf(
                "Ngồi duỗi thẳng hai chân ra trước, nhẹ nhàng kéo mũi bàn chân hướng về phía người.",
                "Vươn thẳng cột sống rồi gập người từ khớp hông, đưa tay vươn về phía cẳng chân hoặc mũi chân.",
                "Giữ 35 giây với lưng thẳng tự nhiên — tránh khóa cứng khớp gối."
            ),
            breathingTip = "Thở ra nhẹ nhàng khi gập sâu thêm một chút mà không gù vai trên."
        )
    )

    private val _currentExercise = MutableStateFlow(exercises[random.nextInt(exercises.size)])
    val currentExercise: StateFlow<StretchExercise> = _currentExercise.asStateFlow()

    /**
     * Picks a random stretching exercise from the predefined list, updates [currentExercise],
     * and returns the chosen [StretchExercise].
     */
    fun getRandomExercise(excludeCurrent: Boolean = true): StretchExercise {
        val candidates = if (excludeCurrent && exercises.size > 1) {
            exercises.filter { it.id != _currentExercise.value.id }
        } else {
            exercises
        }
        val chosen = candidates[random.nextInt(candidates.size)]
        _currentExercise.value = chosen
        return chosen
    }
}
