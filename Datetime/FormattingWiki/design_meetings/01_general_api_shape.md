The Overall Shape of the Format API
===================================

This is a discussion of the general form the user-visible API should take.
Naming, idioms, and the precise set of provided functions are not the focus.

Classes for formats
-------------------

Things we 100% have to cover:

* Access to use some of the common formats.
  - ISO 8601, though we already have that via `parse`/`format`.
  - RFC 2822, date/time + time zone or UTC offset
    (`Fri, 15 May 2009 17:58:28 +0000`)
  - Maybe the format `Tue May 23 09:31:57 +0200 2023`.
    Note: unclear what its name is, it's ubiquous but every place I saw just
    uses the name of the place where it was used, like "the `date(1)` format,"
    "the `ctime(3)` format," "the Ruby date format,"
    or "the `java.Date.toString` format." Go calls this `ANSIC`.
* Ability to describe some less common formats.
* Parsing and formatting using the specified formats.

### Option 1: no classes for formats at all

```kotlin
val formatted = localTime.toString {
  appendHour()
  appendLiteral(':')
  appendMinute()
}

val parsed = LocalTime.parse(formatted) {
  parseHour()
  parseLiteral(':')
  parseMinute()
}

val formattedAsIso = localTime.toString(ConstantFormats.ISO8601)
```

* The approach taken by Go: there's no separate class to define a format, every
  format is exactly a format string.
  ```go
  time.Parse(time.RFC822Z,            "08 Mar 18 17:44 +0300")
  // equivalent to
  time.Parse("02 Jan 06 15:04 -0700", "08 Mar 18 17:44 +0300")
  ```
  Predefined formats are provided as constants.
* The approach by C, Python et all: also no separate entities for formats,
  every format is its format string.
  ```python
  from datetime import *
  datetime.strftime(datetime.now(), "%Y-%m-%d") # '2023-05-23'
  datetime.strftime(datetime.now(), "%FT%T") # '2023-05-23T10:52:04'
  ```
  Constants are built into `strftime`/`strptime` as specific directives. From
  the man page:
  ```
       %D     Equivalent   to  %m/%d/%y.   (Yecch—for  Americans
              only.  Americans should note that in  other  coun‐
              tries  %d/%m/%y is rather common.  This means that
              in international context this format is  ambiguous
              and should not be used.) (SU)
  ```
* The same goes for .NET:
  <https://learn.microsoft.com/en-us/dotnet/standard/base-types/custom-date-and-time-format-strings>
* Swift allows working with formatters in this style.
  Instead of constants, it says that every format is a "style". For example,
  this formats the ISO time portion of a `LocalDateTime`:
  ```swift
  let birthday = Date()

  birthday.formatted(
          .iso8601
          .second(.twoDigits)
          .hour(.twoDigits)
          .minute(.twoDigits)
          // component order doesn't matter
  ) 
  ```
  Swift also allows constructing formats, but specifically discourages it:
  <https://developer.apple.com/videos/play/wwdc2021/10109/?time=914>
  For creating custom formats for parsing or formatting something specific, the
  old API with the Unicode date formats is still used:
  ```swift
  var myFormatter = DateFormatter()
  myFormatter.locale = Locale(identifier: "en_US_POSIX") // otherwise, the system locale is used
  myFormatter.dateFormat = "MMM dd, yyyy" // month name, day, year
  print(myFormatter.string(from: Date())) // Jun 03, 2019
  ```

Pros:

* The API surface is as small as it can be, there is nothing to learn.
* Good fit for *locale-aware* formatting: there, a format can't be fully
  constructed before the locale is known. If a programmer caches a
  locale-specific format, this leads to the cache invalidation problems when
  locales change, but if they just cache a `Locale -> Format` function, it
  doesn't help much.

Cons:

* Format objects can't be printed, serialized, or accessed in general.
* A bit inefficient to constantly re-construct format objects: parsing format
  strings, allocating a bunch to build the internal representation, etc.
* Constants for existing formats just lying around is non-discoverable, and
  treating a set-in-stone format as a "formatting style" is weird.
  - Option: have `iso8601()` and the like functions in the builders that just
    append the format.

> **Resolution**. The fact that we can't print or serialize formats or, at a
> later point, add new functionality to them is a dealbreaker.
> Also, if someone *does* need every ounce of performance and sees that creating
> formats becomes too expensive, it would be nice to provide the means to avoid
> it. The upside of the lack of the format class, namely the `toString { }`
> syntax, can also be replicated with a dedicated class.

### Option 2: one class for all types' formats

```kotlin
val format = kotlinx.datetime.Format.build {
  hourOfDay()
  literal(':')
  minuteOfHour()
}

val formatted = localTime.toString(format)
val parsed = LocalTime.parse(formatted, format)

localDate.format(format) // crash
```

The approach taken by Java's `DateTimeFormatter`,
Objective-C's `NSDateFormatter`.

Pros:

* A clear entry point to the formatting API.
  - All common formats can just be in the companion object of `Format`.

Neutral note:

* It's probably wrong to have *just one* class, as some fields are completely
  incompatible and can't ever occur at the same place. For example,
  `minuteOfHour` of a `LocalTime` and `minutesOfHour` of a `DateTimePeriod`
  look very similarly but mean entirely different things.
  We should consider the option of having one format class for each "maximal"
  class: one `Format` for subsets of information provided by `ZonedDateTime`
  and one `Format` for subsets of information provided by `DateTimePeriod`.

Cons:

* Non-type-safe: `hourOfDay` can't be used when formatting a date, but it's
  permitted.
* Lack of tooling guidance: a common question on Stack Overflow is by people
  who don't understand how to format a `LocalDate` using a `LocalDateTime`
  pattern (or parse a `LocalDateTime` using a `LocalDate` pattern) and try to
  build monstrosities. Forbidding this may guide them to the solution.
* The return type of `format.parse` is some collection of all parsed fields, not
  the object we actually want to parse.

> **Resolution**. In this case, type safety won't hurt anyone. Just having
> a formless `Format` doesn't seem to be better in any regard than `Format<T>`.

### Option 3: a generic class

```kotlin
// Requires some very nasty tricks for this syntax to work, and is non-extensible:
val format = kotlinx.datetime.Format.build<LocalTime> {
  appendHour()
  literal(':')
  appendMinute()
}

// implemented with something like that, where `LocalTime : IsActuallyLocalTime`.
fun build<T: IsActuallyLocalTime>(block: LocalTimeFormatBuilder.() -> Unit)
  : Format<LocalTime>

// Alternative that's extensible and sensibly implemented but looks odd:
val format: Format<LocalTime> = kotlinx.datetime.LocalTime.buildFormat { }
```

Pros:

* Same as the previous option, but also type-safe.

Cons:

* `Format.ISO` doesn't have a type.
  It can be split into `Format.ISO_DATE` + `Format.ISO_DATETIME` + ...
  like in Java.
* If someone wants to parse vaguely structured data and only later interpret it
  as some specific data type, the type bounds can be a nuisance.

> **Resolution**. The `build<T>` trickery *is* nasty, and isn't worth it.
> The "extensible alternative" is okay, but is not intuitive. There's also the
> option to have the mouthful of `Format.buildLocalTimeFormat { }`.
> Given that option 3.5 was suggested, not worth considering.

### (**Winner**) Option 3.5: a generic format class, but also a namespace for formats in each class

```kotlin
interface DateTimeFormat<T> {
  fun format(value: T): String
  fun parse(string: String): T
}

class LocalTime {
  object Format {
    val ISO: DateTimeFormat<LocalTime>

    fun build(block: LocalTimeFormatBuilder.() -> Unit): DateTimeFormat<LocalTime>
  }
}
```

> **Resolution**. This option is the middle ground between options 3 and 4 and
> was suggested during the design meeting. **We'll attempt to implement this**.
> However, we'll have to think carefully about the naming at a later point.
> Currently, it looks like there's nothing date-time-specific to
> `DateTimeFormat`, it's just used in the date-time library, but could as well
> parse/format some other things. In particular, it's fine for numbers and such.

### Option 4: a separate format class for each field container

```kotlin
val ltFormat = LocalTime.Format.build {
  appendHour()
  appendLiteral(':')
  appendMinute()
}

val ldFormat = LocalDate.Format.build {
  appendMonthNumber()
  appendLiteral('-')
  appendDay()
}
```

The approach taken by Noda Time: for each thing like `LocalDate`, there's a
thing like `LocalDatePattern`.

Pros:

* Like the `Format<T>` option, but also without any trickery.
* Each class can store the format constants that are applicable to it.

Cons:

* Semantically, these classes don't have any notable differences between them
  except the implicit type argument.
  This is just `Format<LocalDate>` in disguise, tweaked to fit the language
  constraints.
* Actually, the `Format<T>` interface may still be needed to share the
  documentation between the methods common for all formats, depending on how
  many such methods we end up with.

> **Resolution**. Doesn't bring any clear benefits over 3.5. It *is* true that
> these `Format` classes don't introduce anything new on their own over
> `Format<T>`, after all.

Interfaces for building formats
-------------------------------

There are two types of APIs for building formats:

* Format strings, a limited environment with no compile-time validity check,
  that specify the format in a single line of directives.
* Type-safe builders.

Things that builders should support:
* String literals.
  `appendLiteral(':')`
* Appending format strings.
  `appendStringFormat('hh:mm')`
* Appending formats for specific fields.
  `appendMonthNumber(minDigits = 2)`
* Possibly: space-padded sections.
  `appendSpacePadded(2) { appendMonthNumber() }`
  The alternative seems to be something like
  `appendMonthNumber(minDigits = 1, minLength = 2)`
* Possibly: defining optional/alternative sections.
  `appendOptional { appendLiteral(':'); appendSeconds() }`

### Option 1: no builders, only format strings

```kotlin
val format = LocalDate.Format("yyyy-mm-dd")
```

As seen above, most ecosystems chose this approach.

Pros:

* Very simple constructors for formats, if they are even needed.
* Given that each format is just a single value, it can be fairly efficiently
  cached internally, skipping the phase of parsing the format or building the
  internal representation.
* With no type safety inside the string patterns, there's not much point in
  having separate types for formats, which means one fewer headache.

Cons:

* Some things can't be represented in this concise form. As a result, with time,
  the formatting functions using format strings instead of builders end up
  growing a bunch of extra parameters or putting them into a separate container.
  Example: <https://dateutil.readthedocs.io/en/stable/parser.html#dateutil.parser.parserinfo>
* The way forward for adding localized formats is unclear with this.
  The way to specify localized patterns as format strings is by defining
  "skeletons":
  ```objective-c
  NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
  NSDate *date = [NSDate dateWithTimeIntervalSinceReferenceDate:410220000];
 
  // US English Locale (en_US)
  dateFormatter.locale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US"];
  [dateFormatter setLocalizedDateFormatFromTemplate:@"MMMMd"]; // set template after setting locale
  NSLog(@"%@", [dateFormatter stringFromDate:date]); // December 31
  ```
  The skeleton here is `MMMMd`, which is "full month name and the day."
  This API looks even less readable than the typical format strings, and due to
  how the skeleton gets adapted to the specified locale (not only by reordering,
  but also adding or removing fields), also more tough to debug.

> **Resolution**: in an online poll, most people voted for builders. Also,
> in theory, we *could* introduce any language we wanted, to the point of
> packing the whole of Kotlin into a string format. The problem is, we would
> just be trading a language with a proper compiler and a known set of rules for
> a DSL that, while concise, would only be needed occasionally, not warranting
> gaining proficiency in it.

### Option 2: a single builder for everything

```kotlin
fun FormatBuilder.appendIsoDate() {
  appendYear(minDigits = 4, outputPlusOnOverflow = true)
  appendLiteral('-')
  appendMonth(minDigits = 2)
  appendLiteral('-')
  appendDay(minDigits = 2)
}

localDate.format { appendIsoDate() }
localDateTime.format { appendIsoDate() }
localTime.format { appendIsoDate() } // will crash in runtime
val format = LocalTime.Format { appendIsoDate() } // will crash in runtime
```

The approach taken by Java.

Pros:

* The only option if we decide to go the single-format-for-all route.
* If we go the many-format-classes way, this could provide an easy way to
  extensibility and abstraction.
  - People do occasionally write such functions for Java's
    `DateTimeFormatterBuilder`, though it is rare.

Cons:

* A class of detectable compile-time errors are transferred to runtime, though
  this seems only inconvenient, not dangerous, given the usage patterns.
* Autocompletion is cluttered with a priori useless things.

> **Resolution**: these builders are not going to be accessed manually, they
> will mostly be used as receivers. Hiding these entry points would not
> significantly simplify the API, but could add a bit of friction for the people
> confused about which data structures contain which fields.

### Option 3: one builder for each type

```
LocalTimeFormatBuilder
LocalDateFormatBuilder
LocalDateTimeFormatBuilder
...
```

Pros:

* Only the relevant definitions are present.
* Looks quite simple.

Cons:

* A bunch of duplicated methods documentation.

> **Resolution**: as the next option shows, it's possible to introduce only a
> couple of extra entities and avoid the duplicated non-abstractable code
> entirely, so no reason not to go in that direction.

### (**Winner**) Option 4: a hierarchy of builders

A separate interface for each piece of shared functionality:

```
                  / TimeContainerFormatBuilder ----
                 /                                 \
                /                                   --- DateTimeContainerFormatBuilder -\
               /                                   /                                     -- ZonedDateTimeFormatBuilder
FormatBuilder ----- DateContainerFormatBuilder ----                                     /
               \                                                                       /
                \                                                                     /
                 \- UtcOffsetContainerFormatBuilder ---------------------------------/
```


Examples:

* `appendLiteral` is in `FormatBuilder`,
* `appendDayOfWeekName` is in `DateContainerFormatBuilder`,
* `TimeContainerFormatBuilder` is the receiver in `localTime.format { }`.

Technical note: if we go the road of the traditional builders
(`LocalDateTimeFormatBuilder().appendHour().appendLiteral(':').appendMinute().build()`)
**or** if we go the DSL road and decide to have nested sections
(`build { appendOptonal { appendSpacePadded(2) { appendSeconds() } } }`), the
functions need to know the specific type of the builder:

```kotlin
fun TimeContainerFormatBuilder.appendSeconds(minDigits: Int? = null): TheActualBuilder
fun FormatBuilder.appendOptional(block: TheActualBuilder.() -> Unit)
```

One option is to use self-types:

```kotlin
interface FormatBuilder<out Self> {
    fun appendOptional(block: Self.() -> Unit)
}
```

Another option is to circumvent the type system, using the fact that we know the
full set of builder implementations in advance:

```kotlin
@Suppress("UNCHECKED_CAST")
public fun <T: FormatBuilder> T.appendOptional(block: T.() -> Unit): Unit = when (this) {
    is AbstractFormatBuilder<*, *> -> appendOptionalImpl(block as (AbstractFormatBuilder<*, *>.() -> Unit))
    else -> throw IllegalStateException("impossible")
}
```

Pros:

* Type-safe.
* Only the things that make sense are available.

Cons:

* A bit complex.
* In `UtcOffsetContainerFormatBuilder`, we can't have `appendHours`, as in
  `ZonedDateTimeFormatBuilder`, `appendHour` is already taken by
  `TimeContainerFormatBuilder`, which would lead to much confusion.
  Therefore, we need to have methods like `appendOffsetHours` even in
  `UtcOffset.Format { ... }`, where we could not have meant anything else.
* In general, any behavior that we may want to introduce for `LocalTime` formats
  explicitly will also have to be present in `LocalDateTime` and
  `ZonedDateTime` formats due to the substitution principle.

> **Resolution**: seems to be the best approach. Having type-safe builders for
> every data type is nice for autocompletion and static error checking, and it
> seems like no functionality *that is actually needed* is lost by not
> introducing separate interfaces for all classes.

### Option 5: a complete hierarchy of builders

The same as the previous option, but each data type gets its own builder
implementation as a leaf of the hierarchy:

```
                                                    ________________________________
                                                   /                                \
                  / TimeContainerFormatBuilder ------ LocalTimeFormatBuilder         \
                 /                                 \                                  \
                /                                   ---- LocalDateTimeFormatBuilder    \
               /                                   /                                    -- ZonedDateTimeFormatBuilder
FormatBuilder ----- DateContainerFormatBuilder ------ LocalDateFormatBuilder           /
               \                                   \__________________________________/
                \                                                                    /
                 \- UtcOffsetContainerFormatBuilder --------------------------------/
                                                    \- UtcOffsetFormatBuilder 
```

The receiver of `LocalTime.Format` would be `LocalTimeFormatBuilder`.

Pros:

* The version most reliable for future extensibility.
* Still extensible in theory, though prohibitively unpleasant in practice, given
  the need to figure out the interfaces.

Cons:

* Quite a complex thing to expose. The documentation becomes non-browsable.

> **Resolution**: it's unlikely that we'll need this sort of extensibility.
> Yes, `UtcOffset.Format { appendHour() }` instead of `appendOffsetHour` becomes
> possible, but, first, manually constructing UTC offset formats should be a
> very infrequent use case, and second, `appendOffsetHour` is needed anyway for
> `ZonedDateTimeFormatBuilder`, adding to the confusion.
> This is just not worth the additional several classes that would need to be
> introduced.
> There is also a possibility of having scoped subbuilders like
> `time { hour() }; offset { hour() }`, but most of the time, it's just noise:
> `day` is clearly part of a `date`, no need to clarify that in code.


Interfaces for defining field format directives
-----------------------------------------------

Is only relevant if we decide to use builders.

### Option 1: few functions for directives, using the concept of the field

```kotlin
appendNumeric(DateFields.MONTH_OF_YEAR, minDigits = 2)
// we could also call `appendEnum(DateFields.MONTH_OF_YEAR, ...)` instead for
// defining the strings that represent months
appendLiteral('-')
appendNumeric(DateFields.DAY_OF_MONTH, minDigits = 2)
appendLiteral(", ")
appendEnum(DateFields.DAY_OF_WEEK,
  listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"))
```
or with overloads,

```kotlin
append(DateFileds.MONTH_OF_YEAR, minDigits = 2)
append('-')
append(DateFields.DAY_OF_MONTH, minDigits = 2)
append(", ")
append(DateFields.DAY_OF_WEEK,
  listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"))
```

Pro this option:

* Automatic uniformity of parameter names.
* Shared documentation for the functions.

> **Resolution**. This is less comfortable to use, that's for certain.
> For some use cases, like automatic translation from some other ways to
> represent datetime formats to our ones, this may be more convenient, but these
> use cases are advanced and should not drive the API design at the initial
> stage.

### (**Winner**) Option 2: for every directive form, introduce a separate function

```kotlin
appendMonthNumber(minDigits = 2) // there's also `appendMonthName`
appendLiteral('-')
appendDay(minDigits = 2)
appendLiteral(", ")
appendDayOfWeekName(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"))
```

Pro this option:

* Looks like there isn't any code out there that uses the fact that these forms
  are general, like
  `fun appendTwoDigit(field: NumericField) = append(field, minDigits = 2)`, so
  the generality of the other option feels wasted.
* This looks less busy, easier to skim.
* The directives that people define are not unpredictable.
* Autocompletion is more predictable: people probably think "I want to output
  the month... as a number" and not "I want to output a number... that
  represents the month"

> **Resolution**. Even if we wanted to go with the option 1, we would still
> need to introduce something like this: easier to read, easier to write.
> So, there's no avoiding these functions, as well as the duplication of
> parameter names and forms. We'll go with this one for now, and will introduce
> the API from option 1 only if there's some demand.


The shape of the freeform data
------------------------------

There are needs to format or parse incomplete things or many things at once,
like

* `LocalDateTime` + `UtcOffset`,
* Only the year and the month,
* Month, day, and weekday.

Additionally, there's are needs to parse malformed data.

How to represent it?

### (**WINNER**) Option 1: one class with all the values, but optionally

We can introduce a huge class that serves as just a bunch of values related to
dates and times:

```kotlin
/**
 * A collection of date-time fields.
 *
 * Its main purpose is to provide support for complex date-time formats that don't correspond to any of the standard
 * entities in the library.
 *
 * Accessing the fields of this class is not thread-safe.
 * Make sure to apply proper synchronization if you are using a single instance from multiple threads.
 */
public class ValueBag internal constructor {
    /**
     * Writes the contents of the specified [localTime] to this [ValueBag].
     * The [localTime] is written to the [hour], [minute], [second] and [nanosecond] fields.
     *
     * If any of the fields are already set, they will be overwritten.
     */
    public fun populateFrom(localTime: LocalTime) {
        hour = localTime.hour
        minute = localTime.minute
        second = localTime.second
        nanosecond = localTime.nanosecond
    }

    public fun populateFrom(localDate: LocalDate)
    public fun populateFrom(localDateTime: LocalDateTime)
    public fun populateFrom(utcOffset: UtcOffset)
    public fun populateFrom(instant: Instant, offset: UtcOffset)

    /** Returns the year component of the date. */
    public var year: Int?

    /** Returns the number-of-month (1..12) component of the date. */
    public var monthNumber: Int?

    /** Returns the month ([Month]) component of the date. */
    public var month: Month?
        get() = monthNumber?.let { Month(it) }
        set(value) {
            monthNumber = value?.number
        }

    public var dayOfMonth: Int?
    public var dayOfWeek: DayOfWeek?
    public var hour: Int? by contents.time::hour
    public var minute: Int? by contents.time::minute
    public var second: Int? by contents.time::second
    public var nanosecond: Int? by contents.time::nanosecond
    public var offsetIsNegative: Boolean? by contents.offset::isNegative
    public var offsetTotalHours: Int? by contents.offset::totalHoursAbs
    public var offsetMinutesOfHour: Int? by contents.offset::minutesOfHour
    public var offsetSecondsOfMinute: Int? by contents.offset::secondsOfMinute
    public var timeZoneId: String? by contents::timeZoneId

    /**
     * Builds a [UtcOffset] from the fields in this [ValueBag].
     *
     * This method uses the following fields:
     * * [offsetTotalHours] (default value is 0)
     * * [offsetMinutesOfHour] (default value is 0)
     * * [offsetSecondsOfMinute] (default value is 0)
     *
     * Since all of these fields have default values, this method never fails.
     */
    public fun toUtcOffset(): UtcOffset
    public fun toLocalDate(): LocalDate
    public fun toLocalTime(): LocalTime
    public fun toLocalDateTime(): LocalDateTime
    public fun toInstantUsingUtcOffset(): Instant
}
```

Examples of client code:
```kotlin
ValueBag().apply { populateFrom(localDateTime); populateFrom(offset) }
  .format(ValueBag.Format.ISO_OFFSET_DATE_TIME)

ValueBag.Format.fromFormatString("ld<yyyy-mm(|-dd)>")
  .parse("2023-02")
  .apply { dayOfMonth = dayOfMonth ?: 1 }
  .toLocalDate()
```

An approach taken by Objective-C: <https://developer.apple.com/documentation/foundation/nsdatecomponents>
`NSDateComponents` is an all-encompassing object containing all the existing
fields.

Pros:
* Type-safe. One can't put an `Int` to the `timeZoneId` field.
* It may look like a soup of values. However, `OffsetDateTime`, a thing we're
  often asked about, has almost all of them, so this thing is about as complex
  as it should be.
* Extensible. All fields are `null` by default, so adding something new is
  backward-compatible.

Cons:
* The API surface is huge, and getting to the desired
* The notion of equality is inconvenient.
  `ValueBag().also { hour = 13; minute = 16 } != ValueBag().also { hour = 13; minute = 16; second = 0 }`,
  even though `toLocalTime()` works the same on them, and people don't typically
  need to check the value of `second` manually. Actual equality behaves
  differently from the *practically observable* equality.
  * We can just provide a reliable `toString`, which can be used for comparison
    as well. The use case of comparing two `ValueBag` instances doesn't seem all
    that useful.

> **Resolution**: having a proper, centralized entry point for the tricky use
> cases is valuable. This way, the normal use cases are separated from the
> tricky ones and the tricky ones don't pollute the API surface that will be
> accessed the majority of time.

### Option 2: Clojure's pride, the Map

```kotlin
public fun LocalTime.toComponents(): Map<Field, Any> = buildMap {
    put(HourField, hour)
    put(MinuteField, minute)
    put(SecondField, second)
    put(NanosecondField, nanosecond)
}

public fun LocalTime.Companion.fromComponents(components: Map<Field, Any>): LocalTime {
    fun getField(field: Field, name: String, defaultValue: Int? = null): Int {
        val component = components[field] ?: return defaultValue ?: throw IllegalArgumentException("Missing field '$name'")
        return component as? Int ?: throw IllegalArgumentException("Field '$name' is not an Int but a ${component::class}")
    }
    val hour = getField(HourField, "HourField")
    val minute = getField(MinuteField, "MinuteField")
    val second = getField(SecondField, "SecondField", 0)
    val nanosecond = getField(NanosecondField, "NanosecondField", 0)
    return LocalTime(hour, minute, second, nanosecond)
}

public fun Format<*>.parseUnresolved(charSequence: CharSequence): MutableMap<Field, Any>

public fun Format<*>.format(components: Map<Field, Any>): String
```

Client code examples:
```kotlin
Format.ISO_OFFSET_DATE_TIME.format(
  localDateTime.toComponents() + offset.toComponents()
)

Format.fromFormatString("ld<yyyy-mm(|-dd)>")
// can also be LocalDate.Format.fromFormatString in this case
  .parseUnresolved("2023-02")
  .apply { getOrPut(DateFields.DAY_OF_MONTH) { 1 } }
  .toLocalDate()
```

More or less done by Java:
<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatter.html#parseUnresolved(java.lang.CharSequence,java.text.ParsePosition)>,
<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatter.html#format(java.time.temporal.TemporalAccessor)>

Pros:
* Aside from a bunch of field constants, the API surface is small.
* Nothing new to explain: these are simply maps, with all their `getOrPut`
  and `merge` glory.

Cons:
* `Format.build`, `Format.fromFormatString`, and predefined constants of type
  `Format<*>` may be confusing entry points that are required with this
  approach if there are other, better-typed entry points.
* Shuffling maps may have a bit more overhead.
* `Format<LocalDate>.parseUnresolved` and `Format<LocalDateTime>.parseUnresolved`
  would behave exactly the same way for a format like `yyyy-MM-dd`, making the
  type bound meaningless for that case and muddying its role.
  `Format<ValueBag>.parse` cleanly links the output to the type boundary.
* If we decide to add `find` and `findAll`, we may also want to add
  `findUnresolved` and `findAllUnresolved`, whereas with `ValueBag`,
  they will be available automatically, and with clear behavior.
* `DateTimePeriod`’s fields don’t fit with the `DateTimeFields` at all, and
  neither does it make sense for `Format.build` to be able to produce formats
  for both. This probably means that `DateTimePeriod` will have to get its own
  separate `PeriodFormat` class with the exact same behavior, as
  `parseUnresolved` with the given signature will just not work.
    * Though we could parameterize `Format` also with the type of the fields it
      works with, the complexity seems to big for such an edge case.
* Forces us to preemptively decide the semantics of the fields.
* No putting a breakpoint on field access or assignment with these things.

> **Resolution**: introducing fields without a clear need for them bloats the
> API surface more than `ValueBag` ever would. In any case, `ValueBag` could
> implement the `Map` instance if the fields are eventually introduced, serving
> as an optimized implementation of a datetime field container. Its methods
> could be deprecated later. If we go the `Map` route immediately, the migration
> path to `ValueBag` is not nearly as straightforward.
