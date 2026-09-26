package app.mishna.core.time

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.TimeZone

@Serializable
data class Place(val name: String, val latitude: Double, val longitude: Double, val elevation: Double = 0.0) {
    companion object {
        private const val TZ = "Asia/Jerusalem"
        val JERUSALEM = Place("ירושלים", 31.778, 35.235, 754.0)
        val BNEI_BRAK = Place("בני ברק", 32.084, 34.834, 30.0)
        val BEIT_SHEMESH = Place("בית שמש", 31.747, 34.988, 300.0)
        val TEL_AVIV = Place("תל אביב", 32.085, 34.782, 20.0)
        val HAIFA = Place("חיפה", 32.794, 34.990, 250.0)
        val CITIES = listOf(JERUSALEM, BNEI_BRAK, BEIT_SHEMESH, TEL_AVIV, HAIFA)
        fun timeZone(): TimeZone = TimeZone.getTimeZone(TZ)
    }
}

/** A study day starts at sunrise (SPEC §4). */
object StudyClock {
    fun sunrise(date: LocalDate, place: Place, zone: TimeZone = Place.timeZone()): ZonedDateTime? {
        val cal = ComplexZmanimCalendar(GeoLocation(place.name, place.latitude, place.longitude, place.elevation, zone))
        cal.calendar.apply {
            clear()
            timeZone = zone
            set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0)
        }
        return cal.sunrise?.toInstant()?.atZone(zone.toZoneId())
    }

    /** The study day [now] belongs to: before sunrise it is still the previous day. */
    fun studyDate(now: ZonedDateTime, place: Place): LocalDate {
        val zone = TimeZone.getTimeZone(now.zone)
        val date = now.toLocalDate()
        val rise = sunrise(date, place, zone) ?: return date
        return if (now.isBefore(rise)) date.minusDays(1) else date
    }
}
