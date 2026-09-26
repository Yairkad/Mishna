package app.mishna.core.state

import app.mishna.core.plan.StudyPlan
import app.mishna.core.time.Place
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.LocalDate

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class LineSpacing(val factor: Float) { COMPACT(1.55f), NORMAL(1.85f), WIDE(2.25f) }

@Serializable
data class Prefs(
    val fontScale: Float = 1f,
    val lineSpacing: LineSpacing = LineSpacing.NORMAL,
    val showIkarTosafotYomTov: Boolean = true,
    val keepScreenOn: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notifications: Boolean = true,
    val weeklyBackup: Boolean = true,
)

/** Everything the app stores. Also the backup file format (SPEC §9). */
@Serializable
data class AppState(
    val version: Int = 1,
    val name: String = "",
    val place: Place = Place.JERUSALEM,
    val plan: StudyPlan? = null,
    val prefs: Prefs = Prefs(),
    /** Page the reader was on, to reopen at the same mishna (SPEC §5). */
    @Serializable(with = LocalDateSerializer::class) val readingDate: LocalDate? = null,
    val readingPage: Int = 0,
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun decode(text: String): AppState = json.decodeFromString(serializer(), text)
    }

    fun encode(): String = json.encodeToString(serializer(), this)
}
