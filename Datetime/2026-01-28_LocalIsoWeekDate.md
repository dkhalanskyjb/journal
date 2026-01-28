`LocalIsoWeekDate`
==================

Concepts
--------

<https://en.wikipedia.org/wiki/ISO_week_date>

There is a separate calendar for specialized business applications.
The start of the year is aligned with Monday, and the date has the form
"year-week number-day of week".
For example, 2026-01-28 is 2026-W04-3.

Pitfall:
The year and the ISO week calendar year are almost the same, but not quite.
This leads people to believe they can use it to find "the week number",
but around the edge of the year, the results are surprising.
Example: 2007-12-31 is 2008-W01-1.

Prior art
---------

Python: a plain object with three fields: `year`, `week`, `day`.
<https://docs.python.org/3/library/datetime.html#datetime.date.isocalendar>

Java: exposes ISO-week-related fields on `LocalDate`, `LocalDateTime`, but
does not provide them as properties:

```kotlin
val date = LocalDate.of(2024, 1, 1)
val isoWeek = date[IsoFields.WEEK_OF_WEEK_BASED_YEAR]
val isoYear = date[IsoFields.WEEK_BASED_YEAR]
```

C#: separate collection of functions for obtaining the fields:
<https://learn.microsoft.com/en-us/dotnet/api/system.globalization.isoweek>.

Rust: fields in a normal date object.
<https://docs.rs/chrono/latest/chrono/naive/struct.NaiveDate.html>
Notably, `IsoWeek` is available separately,
which is the ISO week year + ISO week number; similar to `YearMonth`.

Design
------

The simplest option:

```kotlin
class LocalDate {
    val isoWeekYear: Int get() = ...
    val isoWeekNumber: Int get() = ...
    val dayOfWeek: DayOfWeek get() = ... // already have this one
}
```

It is not suitable because of the pitfall around year edges.

We can not follow Java's example and use date fields, as we don't have them
and haven't seen a reason to add them.

Instead, it may be a separate class:

```kotlin
class LocalIsoWeekDate(
    val isoWeekYear: Int,
    val isoWeekNumber: Int,
    val dayOfWeek: DayOfWeek,
)
```

Another option is to have it strictly a transport concept used for parsing and
formatting, but not much else.
Then, it could be a set of fields in
`kotlinx.datetime.format.DateTimeComponents`:

```kotlin
class DateTimeComponents {
    var isoWeekYear: Int? = null
    var isoWeekNumber: Int? = null
    // the existing fields...

    fun toLocalDate(): LocalDate {
        // enhanced with querying the new fields
    }

    fun setDate(date: LocalDate) {
        // enhanced with setting the new fields
    }
}
```

This approach does answer several conceptual questions automatically.
However, it is uncomfortable in that the information about ISO week dates is
not isolated into a place with clear documentation.
Additionally, the answers we get for `LocalIsoWeekDate` may be useful later
if we decide to support other calendars.

Specifics
---------

Required functionality:

* Construction from components.
* Converting to and from `LocalDate`.
* Parsing and formatting with a set format (`2007-W13-5`).
* Equality, hash code, and inequality.

Optional functionality:

* General parsing and formatting.
* Date arithmetics.

### Construction from components

#### Admissible range

```kotlin
import java.time.*

fun main() {
    // The same as kotlinx-datetime
    println("${LocalDate.MIN} is ${LocalDate.MIN.dayOfWeek}")
    println("${LocalDate.MAX} is ${LocalDate.MAX.dayOfWeek}")
}

// -999999999-01-01 is MONDAY
// +999999999-12-31 is FRIDAY
```

The left boundary is clear: `-999999999-W01-1` is the minimal ISO week date.
The right boundary is more difficult: do we take `+999999999-W52-7` or
`+999999999-W52-5` as the largest date?

Note: this relates to how we could implement other calendars down the line.
Is it preferable for all calendars to start and end their admissible ranges at
year boundaries, or do we prefer consistency with our range for
the ISO calendar?

Note: we *don't* document the exact admissible range anyway.

Pro of consistency with `LocalDate`:
the functions converting to and from it are total.

Pro of having custom year end range:
the validation logic in `LocalIsoWeekDate` is simpler and clearer.

Philosophically: is `LocalIsoWeekDate` *a view* of `LocalDate`,
or is it an entity of its own? See also: converters to and from `LocalDate`.

#### Converting to and from `LocalDate`

`LocalIsoWeekDate.toLocalDate(): LocalDate` is mostly an obvious API.
Only one question: member or extension?

The conversion back is trickier.
Should it be a member of `LocalIsoWeekDate` or an extension on `LocalDate`?

Member:

```kotlin
class LocalIsoWeekDate {
    companion object {
        fun fromLocalDate(date: LocalDate): LocalIsoWeekDate
    }
}
```

Extension:

```kotlin
fun LocalDate.toLocalIsoWeekDate(): LocalIsoWeekDate
```

For member: the corresponding `LocalDate` looks like
a fundamental property of `LocalIsoWeekDate`,
not as an operation derived from fundamental ones.
The algorithm is non-trivial.

For extension: it's more consistent with the rest of the library.
We only have `from` converters for primitive types:
`fromEpochSeconds`, `fromEpochDays`, `fromClosedRange`.
On `DateTimeComponents`, we have the mutating `setDate`, but that is a special
case, since we often want to mix and match several `set*` functions and direct
property accesses before formatting a single value.

Note: we have other non-trivial operations defined as extensions.
Example: `LocalDate.plus(DatePeriod)`.

#### Parsing and formatting in the ISO week date format

There are some custom ISO week date formats. Statistics:
- 65 usages found on the Internet.
- That is 0.2% of all custom formats.
- Specific formats used:
```
[yyyy-'W'ww]: 9,              // wrong
[w]: 8,                       // useless
[YYYY-ww]: 4,
[YYYY'W'wwe]: 3,
[YYYY'W'wwe'T'HHmmss]: 3,
[YYYY-'W'ww]: 3,
[YYYY-'W'ww-e'T'HH:mm:ss]: 3,
[Y-w-e]: 3,
[[[YYYY]'W'wwe]]: 2,
[uuuu-ww]: 2]                 // wrong
```

We haven't received any specific requests for them, though.
Even the `LocalIsoWeekDate` entity itself is quite niche.
We could probably get away with not adding the ISO week date fields to
`DateTimeComponents`.

Another concern: which datetime format builder would accept the `isoWeekYear()`
and `isoWeekNumber()` fields? The current hierarchy:

```
yearMonth <- date <-------- dateTime <- dateTimeComponents
                     time <----/             /
                     offset <---------------/
```

Given that the ISO week dates are isomorphic to `LocalDate`, there doesn't seem
to be a better place to put the new fields than `date`.
Similarly, `time` contains the directive both for hours in `0..23` and for
hours in `1..12` + AM/PM markers.
Yet placing `isoWeekNumber()` directly into `date` is just asking for trouble,
as it becomes easy to write the incorrect `year(); isoWeekNumber()` format.

#### Equality, hash code, and inequality

All entities in `kotlinx-datetime` are only comparable with other instances of
the same class.

If we consider `LocalIsoWeekDate` a view of `LocalDate`, there's an option to
make them comparable as well.

```kotlin
interface DateLike {
    fun toLocalDate(): LocalDate
}

class LocalDate: DateLike, Comparable<DateLike> {
    @Deprecated("Just use `this`", level = DeprecationLevel.WARNING, ReplaceWith("this"))
    override fun toLocalDate(): LocalDate = this

    override fun equals(other: Any?) = other is DateLike && actualEquals(this, other.toLocalDate())
    override fun hashCode() = TODO()
    override fun compareTo(other: DateLike): Int = actualCompareTo(other.toLocalDate())
}

class LocalIsoWeekDate: DateLike, Comparable<DateLike> {
    override fun toLocalDate(): LocalDate = TODO()

    override fun equals(other: Any?) = toLocalDate().equals(other)
    override fun hashCode() = toLocalDate().hashCode()
    override fun compareTo(other: DateLike): Int = toLocalDate().compareTo(other)
}
```

The change is not going to be backwards compatible later, because
`isoWeekDate == localDate` is allowed and always returns `false` without this
implementation, but with this implementation, it will sometimes return `true`.

A problem with `DateLike` is that we probably don't want people to use date
arithmetics with `LocalIsoWeekDate`, it also feels inconsistent to only have,
say, `LocalDate.plus` and not `DateLike.plus`.

Finding the next Monday
=======================

The use cases people ask about week numbers for are usually covered by finding
the start of the week.

Use cases
---------

Very common: finding the start of the week.
What the start of the week is is unclear: it's either `Monday` or `Sunday`.
In any case, we can't provide it as a built-in function.

Checking if two dates happen in the ISO-same week:
```kotlin
date1.previousOrSame(DayOfWeek.MONDAY) == date2.previousOrSame(DayOfWeek.MONDAY)
```

Checking if the date fell into the last week:
```kotlin
date.previous(DayOfWeek.MONDAY).let { it..<it.plus(1, DateTimeUnit.WEEK) }
```

Prior art
---------

Java.Time: `next`/`nextOrSame`/`previous`/`previousOrSame`:
* <https://grep.app/search?f.lang=Java&f.lang=Kotlin&words=true&q=next%28DayOfWeek> (98 hits)
* <https://grep.app/search?f.lang=Java&f.lang=Kotlin&words=true&q=nextOrSame%28DayOfWeek> (100 hits)
* <https://grep.app/search?f.lang=Java&f.lang=Kotlin&words=true&q=previous%28DayOfWeek> (45 hits)
* <https://grep.app/search?f.lang=Java&f.lang=Kotlin&words=true&q=previousOrSame%28DayOfWeek> (120 hits)

Python: temporal adjustments are perfomed with
<https://dateutil.readthedocs.io/en/stable/relativedelta.html>, which allows
setting date components individually, *including* the week day.
<https://grep.app/search?q=relativedelta%28weekday>
Example: `date + relativedelta(SU)` is the next Sunday.
The mechanism is more flexible than just week days and allows various temporal
adjustments.

JavaScript: third-party library called `date-fns`.
See <https://date-fns.org/v4.1.0/docs/nextFriday> and the whole sections
titled "Weekday Helpers", "Week Helpers", and "ISO Week Helpers"
("week" = Sunday-based, "ISO week" = Monday-based).

Noda Time: <https://nodatime.org/3.3.x/api/NodaTime.DateAdjusters.html>,
also `Next`/`NextOrSame`/`Previous`/`PreviousOrSame`.

Ruby: <https://api.rubyonrails.org/v8.1.2/classes/DateAndTime/Calculations.html>
* `monday()`, `sunday()`: the `Monday` and `Sunday` of the same Monday-based
  week.
* `next_week(:monday)`, `previous_week(:monday)`: the `Monday` of the next or
  the previous Monday-based week.

Swift:
<https://developer.apple.com/documentation/foundation/calendar/nextdate(after:matching:matchingpolicy:repeatedtimepolicy:direction:)>
Side note: <https://developer.apple.com/documentation/foundation/calendar/matchingpolicy>
looks very much like *Instant resolvers*.

Design
------

No one seems to have generalized the weekday-setting method to something
that's convenient and can handle more date transformations.
Separate functions look like the winning strategy.

Any concerns w.r.t. their naming?

```kotlin
fun LocalDate.previousOrSame(dayOfWeek: DayOfWeek): LocalDate
```
