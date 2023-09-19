Specific behavior of datetime formatting facilities
===================================================

Out-of-bounds unstructured data
-------------------------------

```kotlin
val packedFormat = LocalTime.Format.build {
  hour(minDigits = 2)
  minute(minDigits = 2)
  second(minDigits = 2)
  fractionOfSecond(minDigits = 3)
}
```

Given a string `020356123456789`, we understand that it's
`02:03:56.123456789`, since every field has an implicit max width. It could not
have been `020:356:123.456789`, for example.

However, when working with unstructured data, the values *can* be so grossly out
of bounds (like the minute-of-hour 356).

Unstructured data with the same order of magnitude is common: `24` as the hour,
`60` as the second, <https://en.wikipedia.org/wiki/24-hour_clock#Times_after_24:00>,
31th of February. There's also often misuse of `LocalDate` to mean
`DateTimePeriod` in Java, in which case 300 days is not out of the question.

What to do?

* Have different parsing mechanisms, where packed formats don't work for
  unstructured data, but in return, `12-03-300` is a valid unstructured
  "year-month-day".
* (**WINNER**) Define the maximum order of magnitude for each value and don't
  allow it to exceed that, even when the data is unstructured.
* Introduce a `maxDigits` field to each directive specifically for the sake of
  packed formats.
* For most fields, instead of `minDigits`, have `digits`, defining both the
  lower and the upper bounds explicitly. Though for most fields, the only
  valid values would be `2` and `null`.

> **Resolution**. Looking at the actual use cases, we can split them into
> legitimate ones and API abuse. The legitimate ones all stay in the reasonable
> numeric width: 29 for an hour-of-day (to mean 5-hour overflow into the next
> day), 60 for the second, and so on. API abuse *can* mean treating the
> hour-of-day field as, for example, just some number of hours, and this *may*
> be error-prone: for example, if someone is under the impression that they
> can place any amount of hours in `ValueBag`'s hour-of-day field, for small
> values, it will work, but when the number of hours reaches 100, it will result
> in a runtime crash. It's not clear how to prevent this API abuse, but we
> shouldn't sacrifice legitimate use cases. Packed unstructured data is a proper
> use case: it's clear what 235960 means. Polluting the API with `maxDigits`
> would not actually help: the same API abuse would just result in a surprising
> runtime crash from another place.

For any case but the first one, there is a question: what to do when the
maximum digits bound is exceeded during *formatting*?

* Silently produce a normally unparseable value.
* (**WINNER**) Don’t allow ValueBag values that exceed the required range.
* Throw during formatting when the passed values are out of bounds.

> **Resolution**. It's better to catch the API abuse as early as possible,
> before the unparseable strings enter the databases, so producing incorrect
> values is not a pleasant solution. Throwing during formatting is also not
> optimal: after all, `ValueBag` is explicitly for manipulating values for
> parsing and formatting, and what good would it do to allow putting unusable
> values there? Additionally, we would like not to throw during formatting at
> all if possible, see the relevant section.

Functions on a format
---------------------

### Formatting

#### Which functions to provide

* Bare minimum: `Format<T>.format(value: T): String`.
* Additional option:
  `Format<T>.formatTo(appendable: kotlin.text.Appendable, value: T)`
  Could make sense, as fields are appended one after another, the string is not
  manipulated as a whole.

> **Resolution**. Both seem useful.

#### Throwing behavior

* On any attempt to format, this will fail if the format makes sense for
  parsing but not for formatting.
* Depending on how we implement `reducedYear`, it may throw for some values.
* See "Out-of-bounds unstructured data".

> **Resolution**. We would like to avoid throwing anywhere during formatting,
> because formatting can be used for logging. If your system encounters some
> invalid values, the last thing you want is for the logging facilities to
> throw an exception because of that. By eliminating each point in this list
> separately (with no other sacrifices), we ensured that `format` only throws
> when it is supposed to format a `ValueBag` and `null` was passed as one of
> the values, but this is a tricky enough situation to get into.

### Parsing

#### Which functions to provide

* (**Yes**) Bare minimum: `Format<T>.parse(input: CharSequence): T`.
* (**No**) If we don't have a separate `ValueBag`, then
  `Format<T>.parseToMap(input: CharSequence): Map<DateTimeField, Any>`.
  (The bare minimum can then be factored through this, but doesn't seem like
  a realistic option in practice).
* (**Yes**) Option: `Format<T>.parseOrNull(input: CharSequence): T?`.
* (**No**) Option: also add parameters to grab a piece of string, like
  `Format<T>.parse(input: CharSequence, start: Int, end: Int)`.

  - If `start` is specified, it doesn't make sense not to specify `end` as well,
    so this probably should be a separate overload.
  - May be useful if we don't introduce `find`, for the use case of
    "find a format with a regex, then parse it."
  - In theory, could be useful for parsing some complex string where fields come
    one after another in some structured manner. It doesn't seem though like we
    have such functions for `Int`, etc.
  - When needed, can be less efficiently emulated by stripping out a substring.

> **Resolution**. `parse` and `parseOrNull` are both useful. As we have a
> separate `ValueBag`, `parseToMap` is not needed. `parse` that accepts the
> start and the end positions of a substring seems *very* situationally useful,
> only as a performance optimization over `parse(string.substring())`, and who
> needs this kind of performance specifically when parsing pieces of strings as
> dates?

#### Throwing behavior

* When a string that doesn't fit the format is passed, an exception is thrown.
  - **Yes**, throws `DateTimeFormatException: IllegalArgumentException`
* When parsing structured data (`LocalDate`, `LocalTime`, etc), boundaries are
  checked (otherwise, we couldn't construct `T`).
  - **Yes**, throws `DateTimeFormatException: IllegalArgumentException`
    with the message from the original exception.
* When parsing unstructured data (`Map<Field, Any>` or `ValueBag`), boundaries
  are not checked, *but* the order of magnitude is checked.
  - See "Out-of-bounds unstructured data". **Resolution** from that section
    means that no crash is possible at that point.

### Search

> **Resolution**. We didn't even discuss this section, deciding that it's too
> early to introduce this functionality. Maybe when we collect more use cases
> to discuss this more on-point.

See <https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/find.html>,
<https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/find-all.html>.

* Bare minimum: regex + parse the substring. Problems: requires writing the
  format in both the parser and the regex, it's easy to have a discrepancy
  between them, and the behavior of `find` and `findAll` is not always best
  suited for the goal.
* `Format<T>.find(input: CharSequence, startIndex: Int = 0): T?`,
  `Format<T>.findAll(input: CharSequence, startIndex: Int = 0): List<T>`.


What to return if there are several intersecting strings with the given format?
For example, we want to parse `minute:second` and receive the string
`Today at 23:50:39`.

* Return all matches: `23:50`, `50:39`.
* **Current behavior** Return just the first one: `23:50`.
  If you want the correct result, write `(|hour:)minute:second`.
  Combined with the longest match being taken, this will get parsed as
  `(23:)50:39`.

What to return when there are several ways to parse a string? For example,
let's say we want to parse `hour:minute(|:second)` and are given the string
`Today at 23:50:39`.

* Return all matches: `listOf(23:50, 23:50:39)` (+ maybe `listOf(50:39)`).
* **Current behavior** Return the longest match: `listOf(23:50:39)`.
  Otherwise, the semantics of which directives are greedy and which aren't would
  need to be specified, like it's done in regular expressions, and we don't want
  that.

What to return if we're given the string `After 102:56:16` and want to parse
`hour:minute`?

* `02:56`.
* **Current behavior**
  Nothing, it's illegal to start parsing in the middle of a number.

When parsing `96:96:96` as a `LocalTime`, we can't do anything meaningful, but
as unstructured data, this would be parsed into
`96 hours, 96 minutes, 96 seconds`.

* **Current behavior** `find` and `findAll` also work this way:
  for structured data, no match would be found, but for unstructured data,
  `find` should return this, consistently with `parse`.
  Filtering invalid values manually later is easy.
* Invalid data when parsing may be common, but searching for it in text is
  simply too fuzzy and shouldn't be supported.

Commonly used specific directives
---------------------------------

### Years

* A zero-padded four-digit number, with a leading sign if negative.
  This is what most people are doing manually with builders: see
  <https://grep.app/search?q=appendValue%28YEAR> +
  <https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html#appendValue(java.time.temporal.TemporalField)>
* A zero-padded four-digit number, with a leading sign if negative *or*
  the number is larger than 4 digits. This is the way JSR 310 interprets
  ISO 8601.
* The last two digits of the year.
  <https://github.com/search?q=%22yy-MM+lang%3AJava+&type=code>

### Months

* Zero-padded two-digit month numbers.
* Month numbers, padded with spaces to two characters.
* Short POSIX month names: `Jan`, `Feb`, `Mar`, etc.
* Full POSIX month names: `January`, `February`, `March`, etc.
* Upper-case short POSIX month names: `JAN`, `FEB`, `MAR`, etc.

### Days of week

* Short POSIX weekday names: `Mon`, `Tue`, `Wed`, etc.
* Full POSIX weekday names: `Monday`, `Tuesday`, `Wednesday`, etc.

### Days

* Zero-padded two-digit days of month.
* Day of month, possibly single-digit.
* Strange localized strings: `1st`, `2nd`, or `третье`.

### Hours

* 24-hour hours, zero-padded to two digits.
* 12-hour hours, with the `AM`/`PM` marker...
* ... or the `am`/`pm` marker...
* ... or the `a.m.`/`p.m.` marker.
* There is also a fancy variation: 12-hour hours + period of day (evening,
  night, morning, day). This is obscure, and it seems like people only use this
  for localization, never for machine-to-machine communication.

### Minutes, seconds

* 2-digit zero-padded values. Doesn't seem like anyone's using anything else.

### Fractions of a second

Heavily varies:

* Whether or not a dot is included.
* Whether or not a dangling dot is permitted.
* How many digits to round to.
* Dual: how many digits to parse at most.
* How many digits to output at the least, even with the trailing zeros
* Dual: how many digits to require for successful parsing.

### UTC offset

The number of ways to output the UTC offset is very limited. See
<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html#appendOffset(java.lang.String,java.lang.String)>

* If the offset is zero, there may be the special behavior of outputting
  `Z` or `z`, without doing anything else.
* Occasionally, people don't want to output anything at all for the offset zero.
* First, the sign is always output.
* Then, the hour, either zero-padded two-digit, or without padding.
* Either all components are separated with a `:` or they aren't separated
  at all.
* Minutes and seconds, when they are used, are zero-padded to two digits.
* Minutes and seconds are not always used. Sometimes they need to always be
  used, sometimes they are only used if non-zero.

The variations that are used in practice, in the order of decreasing popularity,
via the format strings:

* Unconditionally print the sign, the two-digit hours, and the minutes, without
  separators.
* Print `Z` on zero, otherwise print the sign, the two-digit hours, and if the
  minutes are non-zero, also them.
* Print `Z` on zero, otherwise the sign, two-digit hours, `:`, and the minutes.
* The sign, two-digit hours, `:`, and the minutes.

Needs vary wildly, but these formats are ubiquitous.
Usages that employ builders instead:
<https://grep.app/search?q=appendOffset%28%22&case=true&filter[lang][0]=Kotlin&filter[lang][1]=Java>

The provided set of functions in builders
-----------------------------------------

Some takeaways and problems from the description of required directives.

### Localized strings in non-localized formats

Localized English names of months, days of week, and AM/PM markers sometimes
need to be used in machine-readable formats.
The POSIX locale describes these names:
<https://www.localeplanet.com/icu/en-US-POSIX/index.html>
Should we provide these in some form?

* `monthPosixName()` + `monthShortPosixName()`
* `monthPosixName(formatStyle)` ("short", "full", maybe "abbreviated").
  Note that so far, we haven't established any need for these styles anywhere
  else.
* The general mechanism `appendMonthName()` + constant like
  "short POSIX month names" somewhere. Usage example:
  `monthName(listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"))`
* (Suggested during the design meeting)
  `appendMonthName`, but with a separate class, `MonthNames`. The same for
  `appendDayOfWeek`.

#### Classes for names


Example:

```kotlin
class kotlinx.datetime.format.MonthNames(val names: List<String>) {
    companion object {
        val ENGLISH_NAMES: MonthNames
        fun englishShortNames(isUpperCase: Boolean)
    }
}
```

Usage examples of these classes vs not having them:

```kotlin
val format = LocalDateTime.Format.build {
  year(4)
  string(", ")
  monthName(MonthNames.ENGLISH)
  char('-')
  dayOfMonth(2)
  string(", ")
  hourOfAmPm(2)
  char(':')
  minute(2)
  optional {
    char(':')
    second(2)
  }
  char(' ')
  amPmMarker(AmPmMarkerNames("am", "pm"))
}

val format = LocalDateTime.Format.build {
  year(4)
  string(", ")
  monthPosixName()
  char('-')
  dayOfMonth(2)
  string(", ")
  hourOfAmPm(2)
  char(':')
  minute(2)
  optional {
     char(':')
    second(2)
  }
  char(' ')
  amPmMarker("am", "pm")
}
```

* Should we have a single class like `Names` for all strings and have the names
  themselves know what field type they are?
  **Resolution: No**: see the next point. The same reasoning applies. Also,
  having `MONTH_NAMES_ENGLISH` and `DAY_OF_WEEK_NAMES_ENGLISH` in the same
  namespace is odd.
* Should we have a single method with overloads, or are different string fields
  disparate enough to warrant their own functions?
  Like `nameOf(MonthNames.ENGLISH)` and `nameOf(AmPmMakerNames.ENGLISH)` vs
  `monthName(MonthNames.ENGLISH)` and `amPmMarker(AmPmMarkerNames.ENGLISH)`.
  **Resolution: Separate methods**: it is true that
  `monthName(MonthNames.ENGLISH)` has repetition in a single line of code
  (which is on Kotlin for not having an equivalent of Swift's
  `monthName(.ENGLISH)`), but this is still preferable: the intention is to
  format **month names** in the form of English strings, not to form some names
  that happen to be month names.
* Should we introduce a class for AM/PM markers as well? **Resolution: No**:
  that's too verbose for a thing that doesn't essentially grant any type safety
  nor introduces a meaningful namespace for constants: even we don't know the
  proper names of `a.m.` vs `A.M.` vs `AM`, and the users of our library are yet
  more unlikely to know what form `AmPmMarkerNames.ENGLISH_FULL` refers to
  without looking at the documentation.
  `amPmMarkerUpperCaseWithoutDots()`, `amPmMarkerLowerCaseWithoutDots()` just
  doesn't have a ring to it.

Which specific functions should we introduce to these classes?

* English names, full and short. As separate constants or as a function like
  `monthName(MonthNames.english(full = true))`, or with a separate constant like
  `FormatStyle.FULL`? **Resolution: just constants, no functions**. Other
  ecosystems do have `FormatStyle.FULL` and its equivalents, but it's of
  questionable usefulness. It's typically for localized formatting and is used
  to choose among predefined skeletons of formats to decide which fields will
  be present.
* Should we allow ignoring the case? **Resolution: No**: allowing to parse
  `jAn` doesn't seem to be actually useful, and when the intention is to parse
  one of several formats, there's a specific directive for that already:
  `oneOf({ monthName(lowerCase) }, { monthName(upperCase) })`. When compiled, it
  shouldn't be any less efficient.

### Last two digits of the year

`2019` -> `19`, `19` -> `2019`.

To resolve the year when parsing, we need some base point to add the number to.
For example, if the year 1960 is the base, `73` -> `1973`, but `59` -> `2059`.

Java's approach:
<https://play.kotlinlang.org/#eyJ2ZXJzaW9uIjoiMS44LjIxIiwiY29kZSI6ImltcG9ydCBqYXZhLnRpbWUuKlxuaW1wb3J0IGphdmEudGltZS5mb3JtYXQuKlxuaW1wb3J0IGphdmEudGltZS50ZW1wb3JhbC4qXG5cbmZ1biBTdHJpbmcuemVyb1BhZChsZW5ndGg6IEludCk6IFN0cmluZyA9IGJ1aWxkU3RyaW5nIHtcbiAgICByZXBlYXQobGVuZ3RoIC0gdGhpc0B6ZXJvUGFkLmxlbmd0aCkgeyBhcHBlbmQoJzAnKSB9XG4gICAgYXBwZW5kKHRoaXNAemVyb1BhZClcbn1cblxuZnVuIG1haW4oKSB7XG4gICAgdmFsIGZvcm1hdHRlciA9IERhdGVUaW1lRm9ybWF0dGVyQnVpbGRlcigpLmFwcGVuZFZhbHVlUmVkdWNlZChDaHJvbm9GaWVsZC5ZRUFSLCAyLCAyLCAxOTYwKS50b0Zvcm1hdHRlcigpXG4gICAgZm9yICh5ZWFyIGluIDE5MDAuLjIxMDApIHtcbiAgICAgICAgdmFsIGRhdGUgPSBMb2NhbERhdGUub2YoeWVhciwgMSwgMSlcbiAgICAgICAgcHJpbnRsbihcIiR5ZWFyIC0+ICR7Zm9ybWF0dGVyLmZvcm1hdChkYXRlKX1cIilcbiAgICB9XG4gICAgZm9yICh5ZWFyIGluIDAuLjEwMCkge1xuICAgICAgICB2YWwgcGFyc2VkID0gZm9ybWF0dGVyLnBhcnNlKFwiJHt5ZWFyLnRvU3RyaW5nKCkuemVyb1BhZCgyKX1cIilcbiAgICAgICAgcHJpbnRsbihcIiR5ZWFyIC0+ICRwYXJzZWRcIilcbiAgICB9XG59IiwicGxhdGZvcm0iOiJqYXZhIiwiYXJncyI6IiJ9>

* When formatting, the base is ignored, just the value modulo 100 is used.
* When parsing, only exactly two digits are permitted.

Problem: the formatter can produce values that will be parsed back, but
incorrectly. Using `1960` as the base, `2100` -> `00` -> `2000`.

Possible mitigations:

* Just replicate what Java does. No one seems to be complaining.
  **Resolution**: if there's no better option. It's not pleasant to be producing
  incorrect values, so we'd like to avoid that.
* Throw on formatting if the value if outside `[base; base + 100)`.
  **Resolution**: we don't want to allow formatting to throw. Invalid values
  should still be formatted, logging shouldn't be allowed to fail.
* Deliberately output something not according to the format when out of bounds.
  For example, `2100` -> `100`, `1959` -> `-159`. This way, parsing will break,
  the users will learn about the problem and will still have a way to extract
  the actual value.
  **Resolution**: ok, but maybe something less cryptic.
* (**WINNER**, Suggested during the design meeting):
  generalize the format so that it's parseable, error-proof, and sensible, but
  when in `[base; base + 100)`, it's working on two-year values.
  One such format is:
  - The last two digits if the year is in `[base; base + 100)`,
  - The full year with a leading sign if the year is outside the range: for
    base = 1960, this would mean `2100 -> +2100`, `50 -> +50`, `1960 -> 60`,
    `2011 -> 11`, `-10 -> -10`.

### Space padding

Month and day numbers are often space-padded. How to describe this
functionality?

With a dedicated subbuilder:

```kotlin
monthNamePosix()
char(' ')
spacePadded(2) {
  day()
}
char(' ')
year()
```

With arguments to the specific directives:

```kotlin
monthNamePosix()
char(' ')
day(doPad = true, padChar = ' ') // "padChar: Char?" ?
char(' ')
year(padStyle = Padding.NONE)

enum class Padding {
  NONE,
  ZERO, // default
  SPACE,
}
```

Example of why space padding is used: fixed-width human-readable output.

```
$ ls -l
drwxr-xr-x  4 dmitry.khalanskiy dmitry.khalanskiy      4096 Mar  7  2023 proofs/
-rw-rw-r--  1 dmitry.khalanskiy dmitry.khalanskiy     24678 Jun 22 14:15 rulestrings
```

Another example: `asctime(3)` formats time as `EEE MMM ppd HH:mm:ss uuuu`,
ensuring that a fixed-size array can be used for all dates.

* In theory, there could be more elaborate uses for space padding, like
  "pad the whole date to 20 characters," but in practice, padding days and
  month to two digits seems to be the *only* use case. See
  <https://grep.app/search?q=padNext%28&words=true> for usages of
  padding in builders, and in format strings, the only padding that's ever
  mentioned is for the days. So introducing a whole separate function may be a
  waste.
* On the other hand, polluting the signatures of every unsigned field is
  cumbersome, whereas having different signatures for fields that in practice
  behave the same may be strange.

> **Resolution**. Since there's no known use cases for space-padding several
> directives at a time, or for padding with both zeros and spaces, we should
> instead enhance the directives themselves, making this functionality more
> discoverable.
>
> Having the `Padding` parameter for every unsigned field is not strange:
> it's also the place where zero-padding could be turned off.

### The UTC offsets

#### Convenient access to common formats

It's easy to define `LocalTime.Format.ISO_TIME`, but for offsets, the usage
varies wildly and there's no widely used "`ISO_OFFSET`".

The four most popular offsets can be described like this in the current
implementation, in the decreasing order of popularity:

```kotlin
val offsetFormat1 = UtcOffset.Format.build {
  offsetSign()
  offsetHours(2)
  offsetMinutes(2)
}

val offsetFormat2 = UtcOffset.Format.build {
  optional("Z") {
    offsetSign()
    offsetHours(2)
    optional {
      offsetMinutes()
    }
  }
}

val offsetFormat3 = UtcOffset.Format.build {
  optional("Z") {
    offsetSign()
    offsetHours(2)
    char(':')
    offsetMinutes(2)
  }
}

val offsetFormat4 = UtcOffset.Format.build {
  offsetSign()
  offsetHours(2)
  char(':')
  offsetMinutes(2)
}
```

* Half of them outputs `Z` on the offset zero.
* Half of them uses the `:` separator.
* One of them doesn't output minutes if they are zero.
* None of them output seconds, even if they are non-zero.

The general version that covers all available formats except those where the
hour is single-digit (which does happen):

```kotlin
internal enum class WhenToOutput {
    NEVER,
    IF_NONZERO,
    ALWAYS,
}

internal fun isoOffset(
        zOnZero: Boolean,
        separateWithColons: Boolean,
        outputMinutes: WhenToOutput,
        outputSeconds: WhenToOutput,
): Format<UtcOffset> {
    require(outputMinutes >= outputSeconds) { "Seconds cannot be included without minutes" }
    // ...
}
```

> **Resolution**. The general version looks **nasty**, and it doesn't seem like
> there can be any better version that's still general. So, it's probably better
> not to even try to go there, but instead, stick to the builder API and provide
> the most commonly used formats constants. According to the statistics, these
> four formats cover the vast majority of ways people actually format and parse
> UTC offsets.
>
> The fourth format doesn't seem to have a common name, but it's okay because
> it's fairly simple to define. The second and the third formats are ISO formats
> if we also include an optional seconds-of-minute component. It's likely that
> people don't discard the seconds-of-minute components intentionally, but
> instead, their data simply never has the seconds be non-zero, so it's okay to
> just generalize these formats to include the seconds as well and provide them
> as ISO constants.
>
> The first format is trickier, as it seemingly doesn't have a good name if
> we include the seconds. What kind of a format is it that formats everything
> as four-digit numbers with the leading sign, unless the seconds are set, in
> which case it's six digits? It's not "compact": `+04:00` will be formatted as
> `+0400`, even though even by the ISO standard, it can be `+4`. It's also not
> ISO at all, as `00:00` should be formatted as `Z`, but this format doesn't
> support `Z`. We should also keep autocompletion in mind: no one is going to
> learn the meaning of our constants by heart, and most people won't even know
> what "`ISO_BASIC`" means. Looks like the proper decision here, after all, is
> to leave out the seconds and just call this "`UtcOffset.Formats.FOUR_DIGIT`".
> The name is very telling, and people don't seem to actually need the seconds,
> so we're not losing much by not including seconds.

#### Sign

In practice, the sign always precedes the hour, yet logically it's distinct:
it's the sign not of the hour but of the whole offset.
Should we introduce `offsetHours` and `offsetSign` separately, or (**WINNER**)
should `offsetHours` just include the sign?

* Not specifying the sign separately is less boilerplate.
* If one doesn't know the sign is included in `offsetHours`, the lack of it
  can seem like a mistake and lead the person to search how to add the sign.
* If one thinks the sign is included in `offsetHours` but it isn't, it will be
  obvious immediately, so this isn't error-prone in any case.

> **Resolution**. There are no known formats that don't have the sign precede
> the hour in the UTC offset. Sure, in theory, there could be other meaningful
> representations, like "`<sign><total seconds>s`", but their theorethical
> existence is not worth making everyone who actually uses our API deal with the
> boilerplate.

### The pesky sign of the year

Go, Python, Obj-C etc. only work at most with years in `1` to `9999`, so they
don't encounter this problem, but in Java, when the year overflows the
padding in the ISO 8601 format, a sign is unconditionally displayed:

```
2020
+12020
-2020
-12020
```

ISO 8601 *does* say that a possible extension is permitted if the year is not
4-digit, and the extension is to output the sign and a number of a predefined
length, but in general, the behavior for non-four-digit years is not defined.

Sometimes people do use this behavior of the sign
<https://grep.app/search?q=EXCEEDS_PAD>, but only for years.

```kotlin
public fun appendYear(
  minDigits: Int = 1,
  outputPlusOnExceededLength: Boolean = false
)
```

> **Resolution**. Most dates in practice *do* deal with years between `1000`
> and `9999`, so the padding of the year and what happens when it overflows is
> relevant only to a tiny minority of users. Since our `parse` and `toString`
> already work with the leading sign on the overflow of padding, having the
> option to include this sign is already required, but does anyone actually
> intentionally want not to have that sign? It doesn't seem like it if we search
> for the actual use cases: people don't even complain they can't work with
> years outside ±9999 in other ecosystems, so no one is likely to even notice
> what we do with the signs here. Hence, we won't include this option until
> there's actual demand, and by default, the sign will be included on padding
> overflow.

### Call chaining?

This formatting looks stylistically incorrect (**yes**, it does):

```kotlin
LocalTime.Format.build { hour(2); char(':'); minute(2); char(':'); second(2);
  optional { char('.'); secondFraction() } }
// equivalent to
LocalTime.Format.build { hour(2); char(':'); minute(2); char(':'); second(2);
  oneOf({}) { char('.'); secondFraction() } }
```

But this is too much wasted space:

```kotlin
LocalTime.Format.build {
  hour(2)
  char(':')
  minute(2)
  char(':')
  second(2)
  optional {
    char('.')
    secondFraction()
  }
}
// equivalent to
LocalTime.Format.build {
  hour(2)
  char(':')
  minute(2)
  char(':')
  second(2)
  oneOf({
  }) {
    char('.')
    secondFraction()
  }
}
```

This builder form looks stylistically incorrect (**yes**):

```kotlin
LocalTime.Format.build { hour(2).char(':').minute(2).char(':').second(2)
  .optional { char('.').secondFraction() } }
// equivalent to
LocalTime.Format.build { hour(2).char(':').minute(2).char(':').second(2)
  .oneOf({}) { char('.').secondFraction() } }
```

This builder form is nasty and unreadable when there are is some lexical scoping
implied (**yes**):

```kotlin
LocalTime.Format.Builder()
  .hour(2).char(':').minute(2).char(':').second(2)
  .startOptional().char('.').secondFraction().endOptional()
// equivalent to
LocalTime.Format.Builder()
  .hour(2).char(':').minute(2).char(':').second(2)
  .startAlternativeList()
  .startAlternative().endAlternative()
  .startAlternative().char('.').secondFraction().endAlternative()
  .endAlternativeList()
```

> **Resolution**. Fluent API separated by dots is a no-go, as yes, in both forms
> it looks confusing and is not even idiomatic, given Kotlin's `apply()`-like
> approach to builders that's usually taken.
>
> Wasted space is not that much of a concern, as people *can*
> write everything in one line. The first example looks too busy only because
> of the nesting, but the following seems fine and is what we should promote in
> examples in the documentation:

```kotlin
LocalTime.Format.build {
  hour(2); char(':'); minute(2); char(':'); second(2)
  optional { char('.'); secondFraction() }
}
```


Miscallaneous
-------------

### Default number of digits

Should we zero-pad (/request zero-padding) by default? **Yes**.

If we do request zero-padding to the likely width by default:

```kotlin
LocalTime(15, 8).format {
  hour()
  char(':')
  minute()
} // 15:08

LocalDate(196, 1, 1).format {
  day(maxDigits = 1)
  char(' ')
  monthShortPosixName()
  char(' ')
  year()
} // 1 Jan 0196
```

If we don't:

```kotlin
LocalTime(15, 8).format {
  hour(2)
  char(':')
  minute(2)
} // 15:08

LocalDate(196, 1, 1).format {
  day()
  char(' ')
  monthShortPosixName()
  char(' ')
  year(4)
} // 1 Jan 0196
```

> **Resolution**. It's much more likely that people do want padding than that
> they don't, and when they test their code on dates like `2023-12-24`, they
> may not notice the fact that their numbers doesn't have padding. So, not
> having padding seems more error-prone. So, padding to the reasonable width
> should be the default.

### Overwriting already-parsed components during parsing

```kotlin
LocalTime.Format.build {
  hour(2)
  char(':')
  minute(2)
  string(" (")
    hourOfAmPm(2)
    char(':')
    minute(2)
    char(' ')
    amPmMarker("AM", "PM")
  string(")")
}
```

Given a string like `15:36 (03:36 AM)` or `15:36 (03:37 PM)`,
what should the parser do?

Also, what should happen on `May 16 2023, Tue`?

* (Everyone except Java): take one of the two parsed values, either the first
  or the last one.
* (Java): **WINNER**: throw an exception.
  - A parsing exception if one field, repeated several times, has conflicting
    values.
  - A resolution exception if several fields (like 24-hours and AM/PM markers)
    are in conflict.

> **Resolution**. Java's approach seems sensible: if a field is _repeated_,
> clearly, there's no way for us to only return a single value and still produce
> sensible results if the value is in coflict with itself. If different fields
> are in conflict, it's up to the resolution mechanism to ensure the parsed
> value is represented faithfully as an object, and there's no way to represent
> `May 16 2023` that's also a `Tue`, so this is an error.
>
> Users will be able to sidestep the resolution errors by employing `ValueBag`,
> so this is only the question of default behaviors. When given strange data,
> use the strange means.

### Parsing with default values

```kotlin
LocalTime.parse("02:16") { hour(2); char(':'); minute(2) }
LocalTime.parse("02") { hour(2) }
LocalTime.parse("") { }
LocalDate.parse("2020") { year(4) }
```

* Java: by default (this is configurable), parses the first two.
  In general, tries to behave in a "smart" manner: if an era is not given,
  it's the current one, if any of the time components except hours is not given,
  it's zero, etc. However, hours are required, as are all the date components.
* Go, Python, C: parse everything, zero-initialize all missing data:

```python
+>>> datetime.datetime.strptime("", "").isoformat()
'1900-01-01T00:00:00'
```

Note that it's in theory **possible** to request supplying even all the fields
in a parser for it to succeed. Partial data can be recovered via a `ValueBag`,
so we're not blocking off any use cases either way.

What to do?

* Default-initialize everything. Would be strange to ask everyone to provide
  nanoseconds for a `LocalTime`.
* Default-initialize nothing.
* Default-initialize something. What?
  - On a case-by-case basis.
  - (**WINNER**)
    Zero-initialize exactly the things that have default values in constructors
    for our classes: minutes, seconds, nanoseconds, all the components of a UTC
    offset, all the components of a `DateTimePeriod`.

> **Resolution**. `DateTimeFormat<LocalTime>` should be consistent with ways to
> construct a `LocalTime`. A `LocalTime` can be constructed with just an hour
> and a minute, omitting seconds and nanoseconds. Hence, this is what should
> happen in its format as well.
>
> When parsing with `DateTimeFormat<ValueBag>`, it's possible to have optional
> minutes: just set them to zero explicitly after the parsing but before
> obtaining a `LocalTime`.
>
> When formatting with `DateTimeFormat<ValueBag>`, it's *not* possible to
> omit minutes if they are zero, but looking through various code, it doesn't
> look like people actually do that.

### Appending a format

Option 1 (**WINNER**):

```kotlin
date(LocalDate.Format.ISO)
char(' ')
time(LocalTime.Format.ISO)
// also dateTime, utcOffset, valueBag
```

Option 2:

```kotlin
format(LocalDate.Format.ISO)
char(' ')
format(LocalTime.Format.ISO)
```

Option 2 has severe consequences: how to allow `format(Format<LocalDate>)` in
`DateFormatBuilder: FormatBuilder`
but not in `TimeFormatBuilder: FormatBuilder`, and vice versa?

* It’s okay to allow passing any `Format<*>` to format, sidestepping type safety.
* FormatBuilder must know what it’s building a format for, like
  `fun FormatBuilder<T>.format(format: Format<T>)`.
    * But then, `DateTimeFormatBuilder: FormatBuilder<LocalTime>, FormatBuilder<LocalDate>, FormatBuilder<LocalDateTime>`,
      which is impossible. Can be fixed by introducing interfaces to `LocalDate`
      etc and relying on variance.

This decision is also relevant for the following section: option 2 opens up new
API forms there as well.

> **Resolution**. `date` and `time` are perfectly fine names, no need to
> mess up the entire class hierarchy of the library just for this (and the
> new API forms in the following section).

### `oneOf` sections

One use case: parsing vaguely-structured data.

```kotlin
oneOf({
  year(4)
  char('-')
  monthNumber(2)
  char('-')
  dayOfMonth(2)
}, {
  dayOfMonth(2)
  char('/')
  monthNumber(2)
  char(' ')
  year(4)
}, {
  dayOfMonth(2)
  char(' ')
  monthName(listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"))
})
```

Make perfect sense and have a clear semantics for parsing: try everything
until parsing succeeds.

The second use case: parsing well-structured data that allows optional fields.

```kotlin
oneOf({
  char('Z')
}, {
  offsetSign()
  offsetHours(2)
  char(':')
  offsetMinutes(2)
})

optional('Z') {
  offsetSign()
  offsetHours(2)
  char(':')
  offsetMinutes(2)
}
```

If we allow default-initialization, this works perfectly well for parsing as
well: for string `Z`, the offset will be parsed as zero.

For *formatting*, the first use case doesn't make sense, as we know exactly how
we want to format everything.

* (**WINNER**)
  Provide `oneOf` and `optional` with different semantics: in `oneOf`, the first
  entry is always formatted, and for `optional`, the placeholder string will
  get formatted if the fields are all in their default values.
  - For parsing, there will be two similar ways of doing the same thing, one
    of which works for formatting and one which doesn't. **Resolution**. It's
    okay that there are two ways of doing the same thing: they are distinct
    enough that they reflect the intention behind the code well.
* Tweak the semantics of `oneOf` to work like `optional`, and throw on attempts
  to format using a `oneOf` bunch that just doesn't make sense for formatting.
  **Resolution**. In practice, most formats *are* used only for parsing or only
  for formatting, not for both at the same time. The formats for parsing are
  commonly structured in a way that would lead to broken formatting, like
  `[dd][ d]`, which is clearly intended to parse either a two-digit day or
  a one-digit day with a sepace before it, but when formatting, it would result
  in something like `15 15` or `09 9`. So, it principle, this wouldn't be
  *worse* than what the existing systems have, and no one would likely notice
  this. However, this option means broken semantics: whether a given invocation
  of `oneOf` has a bug is not known until we look at whether it will be used
  for formatting. It is quite strange: we're defining a _format_, not a
  "parser plus maybe formatter". What `oneOf` even means changes on the intended
  usage of the format. This is not pretty. Add to this that some code could be
  parameterized with a `DateTimeFormat` and would have no way of knowing whether
  a given format is safe for formatting, and you get very unpleasant lack of
  separation of responsibilities.
* Tweak the semantics of `oneOf` to work like `optional`, and pigeonhole it to
  always output *something* in a predictable manner.
  **Resolution**. After some attempts, we couldn't find an option that would be
  useful, total, and associative. Associativity seems like a very natural thing
  to ask for in an operator like this: `oneOf(a, oneOf(b, c)) == oneOf(oneOf(a, b), c)`.
  Example of the problem: what should be the meaning of
  `oneOf({ second() }, { hour(); minute() }, { second(); day() })`?
* Should we even provide `optional` if it's a thin wrapper, given how thin it
  is, or should we direct people to the more general API immediately?
  **Resolution**. It should be its own separate thing.

Having decided that `oneOf` and `optional` have different semantics, the
question is, what would `oneOf` look like to make sense in both cases.

First question: should the first or the last entry be the default one to be
formatted? A couple of details of Kotlin's behavior that will be needed to
decide that:

```kotlin
fun check1() {
    fun oneOf(vararg blocks: () -> Unit, default: () -> Unit) {

    }
    // OK
    oneOf({

    }) {

    }
    // OK
    oneOf({

    }, default = {

    })
    // FAIL: "no value passed for parameter 'default'"
    /*
    oneOf(
        {

        },
        {

        }
    )
   */
}

fun check2() {
    fun oneOf(default: () -> Unit, vararg blocks: () -> Unit) {

    }
    // OK
    oneOf({

    }, {

    })
    // FAIL: Passing value as a vararg is only allowed inside a parenthesized argument list
    /*
    oneOf({
        
    }) {
        
    }
   */
}
```

Having the option that will be parsed as the last one has the advantage that the
default case is syntactically distinct, but a disadvantage that the order in
which various subformats will be tried during parsing is strange: first, the
"default" one, the last one, will be tried, and the alternative versions will
be tried only then. So, the order is $n$, 1, 2, ..., $n - 1$ if we go from the
top to the bottom.

**Resolution**. The order of resolution almost never matters in real life, as
the subformats are going to be disjoint. When a subformat does matter, it's
expected that the syntactically distinct block will be used first.

Now, some forms we considered:

Introducing a new type that would allow chaining options. This would lead to
syntactic distinction and read fluently, without noisy syntax. **Resolution**.
For such an off-to-the-side part of the API, introducing a separate interface
would be a bit too much. This functionality is just not worth more than one
documentation entry.

```kotlin
parseAlternatives {
}.addOption {
}.addOption {
}
```

```kotlin
{
    year()
    month()
    dayOfMonth()
    alternativeParsing(LocalDate.Format { }) {
    }
}
```

Another option would be to only accept prebuilt formats as arguments. This way,
there are extra garbage words, but at least everything's readable at a glance.
The downside is, this would lead us to the problem described in the previous
section, where `LocalDate` would need to implement an interface for `oneOf`
to be type-safe. **Resolution**. Not worth it.

```kotlin
oneOf(
  LocalDate.Format {
  }, // <- this gets formatted?
  LocalDate.Format {
  },
  LocalDate.Format {
  },
)

// also, would we need to change `optional` for consistency
optional("", LocalDate.Format {
   ...
})
// or keep it like it is?
optional('Z') {
  offsetHours()
  char(':')
  offsetMinutes()
}

```

(**WINNER**) A bunch of braces and parens, but with careful discipline, could be
written in a clear and readable manner.

```kotlin
alternativeParsing(
  {
      offsetHours(2)
      char(':')
      offsetMinutes(2)
  },
  {
      offsetHours(2)
      offsetMinutes(2)
  },
) {
   char("Z")
}
```

### Serialization of the Format classes

A common use case for format strings is to extract a format into a configuration
or to send it from one place to another. To support this, we need some kind of
serialization.

`toString` already outputs formats as code that is both readable and can be
copy-pasted into a program as is:
`year(4);char('-');monthNumber(2);char('-');dayOfMonth(2)`.

* This format is fine for people and for migration purposes, but as a
  serialization format, it's backward-incompatible. If we choose to rename the
  `dayOfMonth` method to `day`, the format, too, has to change, but this will
  break the outdated parsers of the format. If we introduce a separate format,
  it can evolve independently.
* Having a `parse` or `deserialize` function on a `LocalTime.Format` can be
  confusing. Seeing `LocalTime.Format.parse` in code could confuse people.
* Should we provide some functions for (de)serialization that don't rely on
  `kotlinx-serialization`, or is it okay to just give our users a `Serializer`?
  Note: currently, with `toString`/`parse`, all entities in the library can be
  transmitted between processes as strings without `kotlinx-serialization`.

Example that we could cook up if we decide to just rely on the serialization
library:

```json
{
  "type": "Composite",
  "directives": [
    {
      "type": "Field",
      "name": "IsoYear",
      "minDigits": 4
    },
    {
      "type": "Literal",
      "value": "-"
    },
```

This is too verbose though, and not really suitable for configs.

**Resolution**.

Before deciding on the format, we should gather requirements and non-requirements.

Requirements:

* One-liners for config files
* Readable in simple cases (without docs)
* Consistency: should allow easy tweaking
* All formats must be serializable (and best-effort deserializable)
* Primary use case: for machine communication

Desirable:

* Concise. Future-proofing for printf-like directives on the language level,
  this format probably should be the same one as in `printf` directives.

Non-requirements:

* Discoverability

Looks like we won't be able to quickly think of such a format, so we'll return
to this after the initial release.

### The API of the ValueBag

This is what the value bag looks like for now:

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
public class ValueBag {
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
    public var hour: Int?
    public var minute: Int?
    public var second: Int?
    public var nanosecond: Int?
    public var offsetIsNegative: Boolean?
    public var offsetTotalHours: Int?
    public var offsetMinutesOfHour: Int?
    public var offsetSecondsOfMinute: Int?
    public var timeZoneId: String?

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

The requirements we have for this data structure:

* Representing unstructured data: incomplete data and data that's slightly
  out of bounds.
* Interoperability with other classes: converting to and from them.
* Combining several parts together.
* Checking that the values have some *sensible* range on their assignment.

What is the sensible range for each field?

* Years.
* Month numbers.
* Days of month. Sometimes overflows the boundaries by dozens of days.
* Days of week.
* Hours.
* Minutes.
* Seconds. Sometimes have the value 60. Usually people deal with it by replacing
  it with 59 and parsing anew.
* Fraction of a second.
  Anything but `[0; 1)` seems to be non-representable nonsense, given that we
  don't provide an explicit "nanoseconds of a second" directive.
* The offset hour.
  In practice, offsets are `[-12; 14]`; we support `[-18; 18]`;
  POSIX mandates support for `(-25; 26)` for the timezone-handling facilities.
* The timezone ID. The values to support depend heavily on the timezone database
  and the specific format that may define the meaning for ambiguous timezone
  abbreviations (like RFC 822 does).

How to handle duplicate data? For example, `month` and `monthNumber`.

* Only provide access to one field of the two.
* Define one field as a property that accesses the other field.

How to handle duplicate *split* data? `hour` vs `hourOfAmPm` + `hourIsPm`,
or `year` + `monthNumber` + `dayOfMonth` vs `weekBasedYear` + `isoWeekNumber` +
`dayOfWeek`.

* Option 1 (spooky action at a distance).
  There should be a complicated system of resolution in `ValueBag`
  that ensures the data is propagated to all the known fields. For example,
  setting `year` + `monthNumber` + `dayOfMonth` should automatically set
  `dayOfWeek`.
* Option 2 ("what does it mean, the field is unset?").
  All these fields should just be independent. If `hourOfAmPm` and
  `hourIsPm` are set, it doesn't mean `hour` should also be set, and vice versa.
  `ValueBag().apply { hour = 13 }.format { hourOfAmPm() }` should throw.
* Option 3 ("I wonder which one it is this time").
  There is no consistent rule, we should decide this on a case by case
  basis. For example, `hourOfAmPm` + `hourIsPm` could get its values from
  `hour` / affect its value, but `dayOfWeek` shouldn't be touched.

Construction: how to set the fields of a newly-created `ValueBag`?

* Manual setters (almost mandatory anyway if we are to represent partial data
  in some manner, though they can be replace with "set the default value"
  directive to the parser if we really have to).
* Functions to populate `ValueBag` from a given object.
* Constructors that accept these objects one by one.
* Adding together several `ValueBag` values.
* Constructors that accept everything at once. Note: difficult to extend, and
  there are several constructors needed anyway to represent `LocalDateTime` vs
  `LocalDate` + `LocalTime`.

Access: how to obtain the values from a newly-created `ValueBag`?

