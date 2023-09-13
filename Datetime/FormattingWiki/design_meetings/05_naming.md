Naming of the datetime formatting facilities
============================================

Entry points
------------

### Sets of rules for parsing and formatting a thing

```kotlin
package kotlinx.datetime.format

interface Format<T> {
    fun format(value: T): String
    fun formatTo(appendable: Appendable, value: T)
    fun parse(input: CharSequence): T
    fun parseOrNull(input: CharSequence): T?
}
```

#### Prior art

##### `java.time.format.DateTimeFormatter`

```kotlin
class DateTimeFormatter(
  private val printerParser: DateTimePrinterParser,
  var locale: Locale = Locale.DEFAULT,
  var chronology: Chronology = IsoChronology,
  // Symbols for `+`, `-`, `0`, and `.`
  var decimalStyle: DecimalStyle = DecimalStyle.STANDARD,
  // how to interpret out-of-bounds and missing data
  var resolverStyle: ResolverStyle = ResolverStyle.SMART,
  // which fields to use when resolving dates
  var resolverFields: Set<TemporalField> = emptySet(),
  // which time zone to use for resolving if none was parsed
  var timeZone: ZoneId? = null,
) {
    companion object {
        fun ofPattern(pattern: String, locale: Locale = Locale.DEFAULT)
        fun ofLocalizedDate(dateStyle: FormatStyle)
        fun ofLocalizedTime(timeStyle: FormatStyle)
        fun ofLocalizedDateTime(dateStyle: FormatStyle, timeStyle: FormatStyle)
    }

    fun toFormat(parseQuery: TemporalQuery<*>? = null): Format

    fun format(temporal: TemporalAccessor): String
    fun formatTo(temporal: TemporalAccessor, appendable: Appendable)
    fun parse(text: CharSequence, position: ParsePosition = ParsePosition(0)): TemporalAccessor
    fun<T> parse(text: CharSequence, query: TemporalQuery<T>): T
    fun parseBest(text: CharSequence, vararg queries: TemporalQuery<*>): TemporalAccessor
}
```

##### `java.text.SimpleDateFormat: java.text.DateFormat: java.text.Format`

<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/text/Format.html>
```kotlin
abstract class Format {
    abstract fun format(obj: Any): String
    abstract fun format(obj: Any, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer
    abstract fun formatToCharacterIterator(obj: Any): AttributedCharacterIterator
    abstract fun parseObject(source: String, pos: ParsePosition = ParsePosition(0)): Any
}
```

<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/text/DateFormat.html>
```kotlin
class DateFormat: Format() {
    companion object {
        val AM_PM_FIELD: Int
        val DATE_FIELD: Int
        val HOUR_OF_DAY0_FIELD: Int // 24-hour
        val HOUR1_FIELD: Int // 12-hour
        // ...
        val DEFAULT: Int
        val LONG: Int
        val MEDIUM: Int
        val SHORT: Int
    }

    abstract var numberFormat: NumberFormat

    abstract var calendar: Calendar

    var lenient: Boolean
      get() = calendar.isLenient
      set(isLenient: Boolean) { calendar.isLenient = isLenient }

    var timeZone: TimeZone
      get() = calendar.timeZone
      set(zone: TimeZone) { calendar.timeZone = zone }
}
```

<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/text/SimpleDateFormat.html>

```kotlin
class SimpleDateFormat(
    var pattern: String,
    var formatSymbols: DateFormatSymbols = Locale.getDefault(Locale.Category.FORMAT)
): DateFormat() {
  var twoDigitYearStart: Date

  // localize-specific, user-friendly way to define patterns.
  // Not documented, and seemingly not actually used.
  // Maybe it converts dots to commas in some locales?
  var localizedPattern: String
    get() = TODO(pattern)
    set(pattern: String) { pattern = TODO() }
}
```

##### `System.Globalization.DateTimeFormatInfo: System.IFormatProvider`

```kotlin
package System.Globalization

class DateTimeFormatInfo {
    var AbbreviatedDayNames: Array<String>
    var AbbreviatedMonthNames: Array<String>
    // ...
    var MonthDayPattern: String
    var ShortDatePattern: String
    var YearMonthPattern: String
    // ...
    val RFC1123Pattern: String
}

dateTime.toString("dd, MM") // 06, 09
dateTime.toString(DateTimeFormatInfo.CurrentInfo) // 06/09
TimeOnly.parse("23:34:12", DateTimeFormatInfo.CurrentInfo)
TimeOnly.parseExact("23:34:12 AM", "hh:mm:ss tt", DateTimeFormatInfo.CurrentInfo)
```

```kotlin
package System

interface IFormatProvider {
    fun <T> GetFormat(cls: Class<T>): T?
}
```

##### `kotlin.io.encoding.Base64`

```kotlin
fun <A : Appendable> encodeToAppendable(
    source: ByteArray,
    destination: A,
    startIndex: Int = 0,
    endIndex: Int = source.size
): A
```

##### `kotlinx.serialization.KSerializer`

```kotlin
abstract fun deserialize(decoder: Decoder): T
abstract fun serialize(encoder: Encoder, value: T): Unit
```

#### Things to decide

* Package name.
* The interface name.
* Type parameter: `T`, `T: Any`?
* `format(value: T): String`: `format` vs `formatted`, `value` vs `input`, etc.
* `formatTo(appendable: Appendable, value: T): Unit`
  vs `<A> formatToAppendable(appendable: A, value: T): A`,
  `appendable` vs `destination`, argument order.
* `parse(input: CharSequence): T`?
* `parseOrNull(input: CharSequence): T?`?


### Building datetype-specific sets of rules for parsing and formatting

```kotlin
package kotlinx.datetime

class LocalTime {
    object Format; // :(, can't put common code into expect classes sensibly
}

public operator fun LocalTime.Format.invoke(builder: TimeFormatBuilderFields.() -> Unit): kotlinx.datetime.format.Format<LocalTime>
```

#### Things to decide

* Are extension functions ok, or do we have to copy-paste code on all platforms?
* The name `Format`: not nice if it conflicts with `Format<T>`.

### Predefined constants

```kotlin
package kotlinx.datetime

/**
 * ISO 8601 extended format, used by [LocalTime.toString] and [LocalTime.parse].
 *
 * Examples: `12:34`, `12:34:56`, `12:34:56.789`.
 */
val LocalTime.Format.ISO: Format<LocalTime>

/**
 * ISO 8601 basic format.
 *
 * Examples: `T1234`, `T123456`, `T123456.789`.
 */
val LocalTime.Format.ISO_BASIC: Format<LocalTime>

/**
 * ISO 8601 extended format, which is the format used by [LocalDate.toString] and [LocalDate.parse].
 *
 * Examples of dates in ISO 8601 format:
 * - `2020-08-30`
 * - `+12020-08-30`
 * - `0000-08-30`
 * - `-0001-08-30`
 */
public val LocalDate.Format.ISO: Format<LocalDate>

/**
 * ISO 8601 basic format.
 *
 * Examples of dates in ISO 8601 basic format:
 * - `20200830`
 * - `+120200830`
 * - `00000830`
 * - `-00010830`
 */
public val LocalDate.Format.ISO_BASIC: Format<LocalDate>

/**
 * ISO 8601 extended format, which is the format used by [LocalDateTime.toString] and [LocalDateTime.parse].
 *
 * Examples of date/time in ISO 8601 format:
 * - `2020-08-30T18:43`
 * - `+12020-08-30T18:43:00`
 * - `0000-08-30T18:43:00.500`
 * - `-0001-08-30T18:43:00.123456789`
 */
public val LocalDateTime.Format.ISO: Format<LocalDateTime>

/**
 * ISO 8601 basic format.
 *
 * Examples of date/time in ISO 8601 basic format:
 * - `20200830T1843`
 * - `+120200830T184300`
 * - `00000830T184300.500`
 * - `-00010830T184300.123456789`
 */
public val LocalDateTime.Format.ISO_BASIC: Format<LocalDateTime>

/**
 * ISO 8601 extended format, which is the format used by [UtcOffset.toString].
 *
 * Examples of UTC offsets in ISO 8601 format:
 * - `Z`
 * - `+05:00`
 * - `-17:16`
 * - `+10:36:30`
 */
val UtcOffset.Format.ISO: Format<UtcOffset>

/**
 * ISO 8601 basic format.
 *
 * Examples of UTC offsets in ISO 8601 basic format:
 * - `Z`
 * - `+05`
 * - `-1716`
 * - `+103630`
 *
 * @see UtcOffset.Format.COMPACT
 */
val UtcOffset.Format.ISO_BASIC: Format<UtcOffset>

/**
 * A format similar to ISO 8601 basic format, but outputting `+0000` instead of `Z` for the zero offset and always
 * requiring the minute component to be present.
 *
 * Examples of UTC offsets in this format:
 * - `+0000`
 * - `+0500`
 * - `-1716`
 * - `+103630`
 *
 * @see UtcOffset.Format.ISO_BASIC
 */
val UtcOffset.Format.COMPACT: Format<UtcOffset>


/**
 * ISO 8601 extended format for dates and times with UTC offset.
 *
 * Examples of valid strings:
 * * `2020-01-01T23:59:59+01:00`
 * * `2020-01-01T23:59:59+01`
 * * `2020-01-01T23:59:59Z`
 *
 * This format uses the local date, local time, and UTC offset fields of [ValueBag].
 *
 * See ISO-8601-1:2019, 5.4.2.1b), excluding the format without the offset.
 */
val ValueBag.Format.ISO_INSTANT: Format<ValueBag>

val ValueBag.Format.RFC_1123: Format<ValueBag>
```

#### Prior art

Java:

```kotlin
// The ISO date formatter that formats or parses a date without an offset, such as '20111203'.
DateTimeFormatter.BASIC_ISO_DATE
// The ISO date formatter that formats or parses a date with the offset if available, such as '2011-12-03' or '2011-12-03+01:00'.
DateTimeFormatter.ISO_DATE
// The ISO-like date-time formatter that formats or parses a date-time with the offset and zone if available, such as '2011-12-03T10:15:30', '2011-12-03T10:15:30+01:00' or '2011-12-03T10:15:30+01:00[Europe/Paris]'.
DateTimeFormatter.ISO_DATE_TIME
// The ISO instant formatter that formats or parses an instant in UTC, such as '2011-12-03T10:15:30Z'.
DateTimeFormatter.ISO_INSTANT
// The ISO date formatter that formats or parses a date without an offset, such as '2011-12-03'.
DateTimeFormatter.ISO_LOCAL_DATE
// The ISO date-time formatter that formats or parses a date-time without an offset, such as '2011-12-03T10:15:30'.
DateTimeFormatter.ISO_LOCAL_DATE_TIME
// The ISO time formatter that formats or parses a time without an offset, such as '10:15' or '10:15:30'.
DateTimeFormatter.ISO_LOCAL_TIME
// The ISO date formatter that formats or parses a date with an offset, such as '2011-12-03+01:00'.
DateTimeFormatter.ISO_OFFSET_DATE
// The ISO date-time formatter that formats or parses a date-time with an offset, such as '2011-12-03T10:15:30+01:00'.
DateTimeFormatter.ISO_OFFSET_DATE_TIME
// The ISO time formatter that formats or parses a time with an offset, such as '10:15+01:00' or '10:15:30+01:00'.
DateTimeFormatter.ISO_OFFSET_TIME
// The ISO date formatter that formats or parses the ordinal date without an offset, such as '2012-337'.
DateTimeFormatter.ISO_ORDINAL_DATE
// The ISO time formatter that formats or parses a time, with the offset if available, such as '10:15', '10:15:30' or '10:15:30+01:00'.
DateTimeFormatter.ISO_TIME
// The ISO date formatter that formats or parses the week-based date without an offset, such as '2012-W48-6'.
DateTimeFormatter.ISO_WEEK_DATE
// The ISO-like date-time formatter that formats or parses a date-time with offset and zone, such as '2011-12-03T10:15:30+01:00[Europe/Paris]'.
DateTimeFormatter.ISO_ZONED_DATE_TIME
// The RFC-1123 date-time formatter, such as 'Tue, 3 Jun 2008 11:05:30 GMT'.
DateTimeFormatter.RFC_1123_DATE_TIME
```

```go
const (
	Layout      = "01/02 03:04:05PM '06 -0700" // The reference time, in numerical order.
	ANSIC       = "Mon Jan _2 15:04:05 2006"
	UnixDate    = "Mon Jan _2 15:04:05 MST 2006"
	RubyDate    = "Mon Jan 02 15:04:05 -0700 2006"
	RFC822      = "02 Jan 06 15:04 MST"
	RFC822Z     = "02 Jan 06 15:04 -0700" // RFC822 with numeric zone
	RFC850      = "Monday, 02-Jan-06 15:04:05 MST"
	RFC1123     = "Mon, 02 Jan 2006 15:04:05 MST"
	RFC1123Z    = "Mon, 02 Jan 2006 15:04:05 -0700" // RFC1123 with numeric zone
	RFC3339     = "2006-01-02T15:04:05Z07:00"
	RFC3339Nano = "2006-01-02T15:04:05.999999999Z07:00"
	Kitchen     = "3:04PM"
	// Handy time stamps.
	Stamp      = "Jan _2 15:04:05"
	StampMilli = "Jan _2 15:04:05.000"
	StampMicro = "Jan _2 15:04:05.000000"
	StampNano  = "Jan _2 15:04:05.000000000"
	DateTime   = "2006-01-02 15:04:05"
	DateOnly   = "2006-01-02"
	TimeOnly   = "15:04:05"
)
```

#### Things to decide

* Is it okay that these are extension values?
* `ValueBag.Format.RFC_1123` can comfortably be defined as a non-extension value--should it also be an extension value for consistency?
* Package name.
* Names.
* Should we add separate objects like `UtcOffset.Formats` (note the plural), Java-style?

### Shorthands

```kotlin
package kotlinx.datetime

fun LocalTime.format(format: Format<LocalTime>): String = format.format(this)

fun LocalTime.Companion.parse(input: String, format: Format<LocalTime>): LocalTime = format.parse(input)

// fun LocalTime.format(builder: TimeFormatBuilderFields.() -> Unit): String =
//     LocalTime.Format(builder).format(this)

// fun LocalTime.Companion.parse(input: String, builder: TimeFormatBuilderFields.() -> Unit): LocalTime =
//     LocalTime.Format(builder).parse(input)
```

#### Things to decide

* Names, input value names.
* Extensions or members.
* Input value order.
  Note: given the signature `parse(format, input)` and asked to write some code,
  ChatGPT made a mistake and wrote `parse("2023-03-09", format)`.
  This is consistent with having a single `parse(input, format = ISO)` function.
* Do we want the commented-out things?

### ValueBag

```kotlin
class ValueBag internal constructor {
    companion object {}

    object Format;

    fun populateFrom(localTime: LocalTime)
    fun populateFrom(localDate: LocalDate)
    fun populateFrom(localDateTime: LocalDateTime)
    fun populateFrom(utcOffset: UtcOffset)
    fun populateFrom(instant: Instant, offset: UtcOffset)

    var year: Int?
    var monthNumber: Int?

    var month: Month?
        get() = monthNumber?.let { Month(it) }
        set(value) {
            monthNumber = value?.number
        }

    var dayOfMonth: Int?

    var dayOfWeek: DayOfWeek?
        get() = contents.date.isoDayOfWeek?.let { DayOfWeek(it) }
        set(value) {
            contents.date.isoDayOfWeek = value?.isoDayNumber
        }

    var hour: Int?
    var hourOfAmPm: Int?
    var isPm: Boolean?
    var minute: Int?
    var second: Int?
    var nanosecond: Int?

    var offsetIsNegative: Boolean?
    var offsetTotalHours: Int?
    var offsetMinutesOfHour: Int?
    var offsetSecondsOfMinute: Int?

    var timeZoneId: String?

    fun toUtcOffset(): UtcOffset
    fun toLocalDate(): LocalDate
    fun toLocalTime(): LocalTime
    fun toLocalDateTime(): LocalDateTime

    /**
     * Builds an [Instant] from the fields in this [ValueBag].
     *
     * Uses the fields required for [toLocalDateTime] and [toUtcOffset].
     *
     * Almost always equivalent to `toLocalDateTime().toInstant(toUtcOffset())`, but also accounts for cases when
     * the year is outside the range representable by [LocalDate] but not outside the range representable by [Instant].
     */
    fun toInstantUsingUtcOffset(): Instant
}

fun DateTimeFormat<ValueBag>.format(block: ValueBag.() -> Unit): String =
    format(ValueBag().apply { block() })
```

Builders
--------

### Configuration options

```kotlin
package kotlinx.datetime.format

enum class Padding {
    NONE,
    ZERO,
    SPACE,
}
```

#### Things to decide

* Package name.
* Name.
* Variant names.

### Name collections

```kotlin
package kotlinx.datetime.format

class MonthNames(val names: List<String>) {
    init {
        require(names.size == 12) { "Month names must contain exactly 12 elements" }
    }

    constructor(
        january: String, february: String, march: String, april: String, may: String, june: String,
        july: String, august: String, september: String, october: String, november: String, december: String
    ) :
        this(listOf(january, february, march, april, may, june, july, august, september, october, november, december))

    companion object {
        val ENGLISH_FULL: MonthNames = MonthNames(
            listOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
        )

        val ENGLISH_ABBREVIATED: MonthNames = MonthNames(
            listOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )
        )
    }
}

class DayOfWeekNames(val names: List<String>) {
    init {
        require(names.size == 7) { "Day of week names must contain exactly 7 elements" }
    }

    constructor(
        monday: String,
        tuesday: String,
        wednesday: String,
        thursday: String,
        friday: String,
        saturday: String,
        sunday: String
    ) :
        this(listOf(monday, tuesday, wednesday, thursday, friday, saturday, sunday))

    companion object {
        val ENGLISH_FULL: DayOfWeekNames = DayOfWeekNames(
            listOf(
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
            )
        )

        val ENGLISH_ABBREVIATED: DayOfWeekNames = DayOfWeekNames(
            listOf(
                "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
            )
        )
    }
}

```

#### Things to decide

* Package.
* Class names.
* Should we have the additional constructors?
* Which constants to have and what to call them.

### The builder interface hierarchy

* `FormatBuilder`
* `TimeContainerFormatBuilder`, `DateContainerFormatBuilder`,
  `UtcOffsetContainerFormatBuilder` `: FormatBuilder`
* `DateTimeContainerFormatBuilder`
  `: TimeContainerFormatBuilder, DateContainerFormatBuilder`
* `ValueBagFormatBuilder`
  `: DateTimeContainerFormatBuilder, UtcOffsetContainerFormatBuilder`

`package kotlinx.datetime`

### Format builder

```kotlin
@DslMarker
annotation class DateTimeBuilder

@DateTimeBuilder
sealed interface FormatBuilder {
    fun appendLiteral(string: String)
}

fun <T: FormatBuilder> T.alternativeParsing(
    vararg otherFormats: T.() -> Unit,
    mainFormat: T.() -> Unit
): Unit

fun <T: FormatBuilder> T.appendOptional(
    onZero: String = "",
    block: T.() -> Unit
): Unit

fun FormatBuilder.appendLiteral(char: Char): Unit =
    appendLiteral(char.toString())
```

### Date format builder

```kotlin
sealed interface DateFormatBuilder : FormatBuilder {
    fun appendYear(padding: Padding = Padding.ZERO)
    fun appendYearTwoDigits(base: Int)
    fun appendMonthNumber(padding: Padding = Padding.ZERO)
    fun appendMonthName(names: MonthNames)
    fun appendDayOfMonth(padding: Padding = Padding.ZERO)
    fun appendDayOfWeek(names: DayOfWeekNames)
    fun appendDate(dateFormat: DateTimeFormat<LocalDate>)
}
```

### Time format builder

```kotlin
sealed interface TimeFormatBuilderFields : FormatBuilder {
    fun appendHour(padding: Padding = Padding.ZERO)
    fun appendAmPmHour(padding: Padding = Padding.ZERO)
    fun appendAmPmMarker(amString: String, pmString: String)
    fun appendMinute(padding: Padding = Padding.ZERO)
    fun appendSecond(padding: Padding = Padding.ZERO)
    fun appendSecondFraction(minLength: Int? = null, maxLength: Int? = null)
    fun appendTime(format: DateTimeFormat<LocalTime>)
}
```

### Offset format builder

```kotlin
sealed interface UtcOffsetFormatBuilderFields : FormatBuilder {
    // also includes the sign
    fun appendOffsetTotalHours(padding: Padding = Padding.ZERO)
    fun appendOffsetMinutesOfHour(padding: Padding = Padding.ZERO)
    fun appendOffsetSecondsOfMinute(padding: Padding = Padding.ZERO)
    fun appendOffset(format: DateTimeFormat<UtcOffset>)
}
```

### Datetime format builder

```kotlin
sealed interface DateTimeFormatBuilder : DateFormatBuilder, TimeFormatBuilderFields {
    fun appendDateTime(format: DateTimeFormat<LocalDateTime>)
}
```

### Value bag format builder

```kotlin
sealed interface ValueBagFormatBuilder : DateTimeFormatBuilder, UtcOffsetFormatBuilderFields {
    fun appendTimeZoneId()
    fun appendValueBag(format: DateTimeFormat<ValueBag>)
}
```
