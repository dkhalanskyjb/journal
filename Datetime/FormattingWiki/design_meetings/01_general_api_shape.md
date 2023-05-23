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

### Option 3: a generic class

```kotlin
// Requires some very nasty tricks for this syntax to work, and is non-extensible:
val format = kotlinx.datetime.Format.build<LocalTime> {
  appendHour()
  literal(':')
  appendMinute()
}

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

* Like the previous option, but also without any trickery.
* Each class can store the format constants that are applicable to it.

Cons:

* Semantically, these classes don't have any notable differences between them
  except the implicit type argument.
  This is just `Format<LocalDate>` in disguise, tweaked to fit the language
  constraints.
* Actually, the `Format<T>` interface may still be needed to share the
  documentation between the methods common for all formats, depending on how
  many such methods we end up with.


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

### Option 3: a hierarchy of builders

A separate builder for each type and interfaces that define shared
functionality:
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
                 \- UtcOffsetContainerFormatBuilder -- UtcOffsetFormatBuilder ------/
```

Examples:
* `appendLiteral` is in `FormatBuilder`,
* `appendDayOfWeekName` is in `DateContainerFormatBuilder`,
* `LocalTimeFormatBuilder` is the receiver in `localTime.format { }`.

Technical note: if we go the road of the traditional builders
(`LocalDateTimeFormatBuilder().appendHour().appendLiteral(':').appendMinute().build()`)
**or** if we go the DSL road and decide to have nested sections
(`build { appendOptonal { appendSpacePadded(2) { appendSeconds() } } }`), the
functions need to know the specific type of the builder:
```kotlin
fun TimeContainerFormatBuilder.appendSeconds(minDigits: Int? = null): TheActualBuilder
fun FormatBuilder.appendOptional(block: TheActualBuilder.() -> Unit)
```
For this, looks like the only option is to use self-types:
```kotlin
interface FormatBuilder<out Self> {
    fun appendOptional(block: Self.() -> Unit)
}
```

Pros:
* Type-safe.
* Only the things that make sense are available.
* Still extensible in theory, though prohibitively unpleasant in practice, given
  the need to figure out the interfaces.

Cons:
* Quite a complex thing to expose. The documentation becomes non-browsable.

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

### Option 2: for every directive form, introduce a separate function

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
