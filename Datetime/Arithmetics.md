Datetime Arithmetics API Shape
==============================

Introduction
------------

An approximation of the main entities in our API (there are significant
differences in how it's used, but not conceptually):

```kotlin
// A moment in time, independent of the time zone.
// As of writing, the Unix time is 1739286088, whether you're in Tokyo,
// Madrid, or New York.
class Instant(
    val epochSecond: Double
)

// Time-of-day: [00:00; 23:59:59.9999...]
// At one `Instant`, different time-of-day is observed by different time zones
class LocalTime(
    // [0; 59]
    val hour: Int,
    // [0; 59]
    val minute: Int,
    // [0; 60)
    val second: Double
)

// Calendar date.
// At one `Instant`, different dates can be observed by different time zones
// (but typically at most 1 day apart)
class LocalDate(
    val year: Int,
    // [JANUARY; DECEMBER]
    val month: Month,
    // [1; 31], depends on the year and the month
    val day: Int,
)

// What someone thinks of as their "time", forgetting about the time zone
class LocalDateTime(
    val date: LocalDate,
    val time: LocalTime,
)


// Date durations: "wait for three months and two days",
// "wait for a whole month minus one day".
// Can not just be some number of days, as the length of a month depends on the
// start point:
// 2024-01-01 + 1 month = 2024-02-01, 31 day later
// 2024-02-01 + 1 month = 2024-03-01, 29 days later
class DatePeriod(
    val totalMonths: Long,
    val totalDays: Long,
) : DateTimePeriod

// Datetime periods: "wait for three months, two days, and three seconds".
// The number of days can not be put together with the `Duration`, as the length
// of the day depends on whether clocks were shifted that day:
// - Normal day: 24 hours
// - Day when clocks are shifted forward: 23 hours
// - Day when clocks are shifted backward: 25 hours
class DateTimePeriod(
    val datePeriod: DatePeriod,
    val duration: Duration,
)

// Offset of a time zone from the Greenwich, like `+03:30` or `-07:00`
class UtcOffset(
    val hour,
    val minute,
    val second,
)

// A procedure of converting a LocalDateTime to Instant
// We don't actually have this interface yet,
// so it's all just a raw example of what it could be,
// but we certainly want to introduce it in some form.
interface InstantResolver {
    // Called if the clocks were shifted forward in such a way that this
    // [localDateTime] never existed.
    fun resolveTimeGap(
        // the non-existent clock reading we are trying to fix
        localDateTime: LocalDateTime,
        // the moment when the gap started
        gapStart: Isntant,
        // the offset from the UTC before the gap started
        offsetBeforeGap: UtcOffset,
        // the offset from the UTC after the gap ended
        offsetAfterGap: UtcOffset,
    ): Instant

    // Called if the clocks were shifted backwards in such a way that this
    // [localDateTime] happened twice.
    fun resolveTimeOverlap(
        // the clock reading we are trying to disambiguate
        localDateTime: LocalDateTime,
        // the first time we've seen the clock reading
        firstEncounter: Instant,
        // the second time we've seen the clock reading
        secondEncounter: Instant,
    ): Instant
}

// A time zone like "Europe/Berlin"
class TimeZone(val id: String) {
    // What do people in this time zone see on their clocks at that moment?
    fun instantToLocalDateTime(instant: Instant): LocalDateTime

    // At which moment did the people in this time zone see that on their clock?
    fun localDateTimeToInstant(
        localDateTime: LocalDateTime,
        resolver: InstantResolver
    ): Instant

    // Which UTC offset does this time zone observe at that moment?
    fun offsetAt(instant: Instant): UtcOffset
}

// Measurement units of the DateTimePeriod fields
sealed class DateTimeUnit {
    class TimeBased(val nanoseconds: Long) : DateTimeUnit()

    sealed class DateBased : DateTimeUnit()

    class DayBased(val days: Int) : DateBased()

    class MonthBased(val months: Int) : DateBased()

    companion object {
        val NANOSECOND: TimeBased = TimeBased(nanoseconds = 1)
        val SECOND: TimeBased = MILLISECOND * 1000
        val DAY: DayBased = DayBased(days = 1)
        val WEEK: DayBased = DAY * 7
        val MONTH: MonthBased = MonthBased(months = 1)
        val YEAR: MonthBased = MONTH * 12
    }
}

fun LocalDateTime.toInstant(offset: UtcOffset): Instant

operator fun Instant.plus(duration: Duration): Instant
operator fun Instant.minus(duration: Duration): Instant
operator fun Instant.minus(other: Instant): Duration
fun Instant.plus(value: Int, unit: DateTimeUnit.TimeBased): Instant

operator fun LocalDate.plus(period: DatePeriod): LocalDate
operator fun LocalDate.minus(period: DatePeriod): LocalDate
operator fun LocalDate.minus(other: LocalDate): DatePeriod
fun LocalDate.plus(value: Int, unit: DateTimeUnit.DateBased): LocalDate
```

Problem
-------

The parts that are less clear:

* Working with dates when the time is also present.
* Changing the wall clock time.

Approach 1: no extra public entities.

```kotlin
// Time zones and resolvers can be hidden as context parameters if we want

// 1. Finds the date at the Instant in this time zone,
// 2. adds the date portion of the period,
// 3. resolves the Instant corresponding to the resulting clock readings,
// 4. and adds the duration to the Instant
fun Instant.plus(
    period: DateTimePeriod, timeZone: TimeZone, resolver: InstantResolver
): Instant

// 1. Finds the date at the Instant in this time zone,
// 2a. if the unit is date-based, modifies the date and
//     resolves the Instant corresponding to the resulting clock readings;
// 2b. if the unit is time-based, adds the time-based unit.
fun Instant.plus(
    value: Int, unit: DateTimeUnit, timeZone: TimeZone, resolver: InstantResolver
): Instant

// 1. Finds the date at the Instant in this time zone,
// 2. changes the date to a new one,
// 3. resolves the Instant corresponding to the resulting clock readings.
fun Instant.withDate(
    timeZone: TimeZone,
    resolver: InstantResolver,
    action: (LocalDate) -> LocalDate
): Instant

// 1. Finds the date and time at the Instant in this time zone,
// 2. changes the time to a new one,
// 3. resolves the Instant corresponding to the resulting clock readings.
fun Instant.withTime(
    timeZone: TimeZone,
    resolver: InstantResolver,
    action: (LocalTime) -> LocalTime
): Instant
```

Optional but convenient addition to approach 1:

```kotlin
// 1. Adds the date portion of the period,
// 2. resolves the Instant corresponding to the resulting clock readings,
// 3. and adds the duration to the Instant
fun LocalDateTime.plus(
    period: DateTimePeriod, timeZone: TimeZone, resolver: InstantResolver
): Instant

// 1a. If the unit is date-based, modifies the date and
//     resolves the Instant corresponding to the resulting clock readings.
// 1b. If the unit is time-based, resolves the Instant
//     corresponding to the clock readings and adds the time-based unit.
fun LocalDateTime.plus(
    value: Int, unit: DateTimeUnit, timeZone: TimeZone, resolver: InstantResolver
): Instant

// 1. Changes the date to a new one,
// 2. Resolves the Instant corresponding to the resulting clock readings.
fun LocalDateTime.withDate(
    timeZone: TimeZone,
    resolver: InstantResolver,
    action: (LocalDate) -> LocalDate
): Instant

// 1. Changes the time to a new one,
// 2. Resolves the Instant corresponding to the resulting clock readings.
fun LocalDateTime.withTime(
    timeZone: TimeZone,
    resolver: InstantResolver,
    action: (LocalTime) -> LocalTime
): Instant
```

Approach 2: extra entities.

```kotlin
sealed class UnresolvedZonedDateTime(
    val rawLocalDateTime: LocalDateTime,
    val timeZone: TimeZone,
) {
    fun resolve(resolver: Resolver): ZonedDateTime

}

class ZonedDateTime(
    localDateTime: LocalDateTime,
    val utcOffset: UtcOffset,
    timeZone: TimeZone,
): UnresolvedLocalDateTime(localDateTime, timeZone) {
    fun toInstant(): Instant

    val localDateTime: LocalDateTime get() = rawLocalDateTime

}

fun LocalDateTime.atZone(timeZone: TimeZone): UnresolvedZonedDateTime

fun Instant.atZone(timeZone: TimeZone): ZonedDateTime

// 1. Adds the date period to the LocalDate portion of `rawLocalDateTime`,
// 2. resolves the ZonedDateTime,
// 3. adds the time-based portion to the resulting Instant.
fun UnresolvedZonedDateTime.plus(
    period: DateTimePeriod, resolver: InstantResolver
): ZonedDateTime

// Modifies the date.
fun UnresolvedZonedDateTime.withDate(
    action: (LocalDate) -> LocalDate
): UnresolvedZonedDateTime

// Modifies the time.
fun UnresolvedZonedDateTime.withTime(
    action: (LocalTime) -> LocalTime
): UnresolvedZonedDateTime

// Adds the time to the Instant, resolves the LocalDateTime.
fun ZonedDateTime.plus(
    value: Int, unit: DateTimeUnit.TimeBased
): ZonedDateTime

// 1a. If the unit is date-based, modifies the date portion of the LocalDateTime
// 1b. If the unit is time-based, adds the time to the Instant
fun ZonedDateTime.plus(
    value: Int, unit: DateTimeUnit
): UnresolvedZonedDateTime

// A derived operation
fun UnresolvedZonedDateTime.plus(
    value: Int, unit: DateTimeUnit.DateBased
): UnresolvedZonedDateTime = withDate { it.plus(value, unit) }
```

Comparison
----------

Examples of solving some tasks using the three approaches:
approach 1, approach 1 with extras, and approach 2.

### The same time, a month later

"This meeting started at `2025-02-14T15:30` in the current time zone.
The next meeting should be a month later at the same time."

Approach 1:

```kotlin
LocalDateTime(2025, 02, 14, 15, 30)
    .toInstant(TimeZone.currentSystemDefault(), anyResolver)
    .plus(1, DateTimeUnit.MONTH, TimeZone.currentSystemDefault(), myResolver)
```

`anyResolver` is any reasonable resolver. Because we know the meeting already
took place, it means the local date-time is not in a time gap, and if it's
in a time overlap, it doesn't matter anyway, as strip away the offset.

If the local date-time could have been invalid, we would have to take a
non-obvious approach:

```kotlin
LocalDateTime(2025, 02, 14, 15, 30)
    .let { it.date.plus(1, DateTimeUnit.MONTH).atTime(it.time) }
    .toInstant(TimeZone.currentSystemDefault(), myResolver)
```

Problem: guides our users to store `LocalDateTime` as the ground truth.

Approach 1+:

```kotlin
LocalDateTime(2025, 02, 14, 15, 30)
    .plus(1, DateTimeUnit.MONTH, TimeZone.currentSystemDefault(), myResolver)
```

Semantically the same as the non-obvious solution with the approach 1.

Approach 2:

```kotlin
LocalDateTime(2025, 02, 14, 15, 30)
    .atZone(TimeZone.currentSystemDefault())
    .plus(1, DateTimeUnit.MONTH)
    .resolve(myResolver)
    // .instant // if you want
```

### The given time today

"Return the moment corresponding to the time 14:30 today".

Approach 1:

either

```kotlin
Clock.System.todayIn(TimeZone.currentSystemDefault())
    .atTime(14, 30).toInstant(TimeZone.currentSystemDefault(), myResolver)
```

or

```kotlin
Clock.System.now().withTime(TimeZone.currentSystemDefault(), myResolver) {
    LocalTime(14, 30)
}
```

Approach 2:

```kotlin
Clock.System.now().atZone(TimeZone.currentSystemDefault())
    .withTime { LocalTime(14, 30) }
    .resolve(myResolver)
    // .instant // if you want
```

### The given time a month later

"Return the moment corresponding to the time 14:30 on the day a month later".

Approach-agnostic correct solution:

```kotlin
Clock.System.todayIn(TimeZone.currentSystemDefault())
    .plus(1, DateTimeUnit.MONTH)
    .atTime(14, 30)
    .toInstant(TimeZone.currentSystemDefault())
```

Approach 1:

A solution that our users are more likely to implement:

```kotlin
Clock.System.now()
    .plus(
        1, DateTimeUnit.MONTH,
        TimeZone.currentSystemDefault(), resolvePreservingDay
    )
    .withTime(TimeZone.currentSystemDefault(), myResolver) { LocalTime(14, 30) }
```

`resolvePreservingDay` means that, if the time a month later lands in
a time gap, (for example, `23:45` when `23:30` jumps directly to `00:30`),
we must preserve the date.

Problem: guides our users to store `LocalDateTime` as the ground truth.

Approach 2:

```kotlin
Clock.System.now().atZone(TimeZone.currentSystemDefault())
    .plus(1, DateTimeUnit.MONTH)
    .withTime { LocalTime(14, 30) }
    .resolve(myResolver)
    // .instant // if you want
```

### The next time it's the given time

"Return the moment later than now when it's 12:30 on the clock,
[or if that time doesn't exist, something close to it on the same day]"

So, 2025-02-12T14:30 and 2025-02-13T09:00 both become 2025-02-13T12:30, and
if this time were in a time gap, something close to it would be returned.

Approach 1:

```kotlin
val now = Clock.System.now()
val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
val desiredTime = today.atTime(12, 30)
val possibleAnswer = desiredTime.toInstant(resolveToLaterOnOverlap)
if (now < possibleAnswer) return possibleAnswer
return today.plus(1, DateTimeUnit.DAY).atTime(12, 30)
    .toInstant(TimeZone.currentSystemDefault(), myResolver)
```

Approach 2:

```kotlin
val now = Clock.System.now().atZone(TimeZone.currentSystemDefault())
val desiredTime = now.withTime { LocalTime(12, 30) }
val possibleAnswer = desiredTime.resolve(resolveToLaterOnOverlap)
if (now.instant < possibleAnswer.instant) return possibleAnswer
return now.plus(1, DateTimeUnit.DAY).withTime { LocalTime(12, 30) }
    .resolve(myResolver)
```

### An Instant value it's 12:00 [or close to that] for each day of the year

Correct solution:

```kotlin
var date = LocalDate(year, 1, 1)
buildList {
    while (date.year == year) {
        add(date.atTime(12, 0).toInstant(
            TimeZone.currentSystemDefault(), myResolver
        ))
        date = date.plus(1, DateTimeUnit.DAY)
    }
}
```

It's likely, though, that instead, something like this gets written:

```kotlin
var instant = LocalDateTime(year, 1, 1, 12, 0)
    .toInstant(TimeZone.currentSystemDefault(), myResolver)
buildList {
    while (
        instant.toLocalDateTime(TimeZone.currentSystemDefault()).year == year
    ) {
        add(instant)
        instant = instant.plus(
            1, DateTimeUnit.DAY, TimeZone.currentSystemDefault(), myResolver
        )
    }
}
```

A subtle mistake hides here: if on a single day, `12:00` does not exist, then
for every subsequent day, an instant corresponding to some time different from
`12:00` will be returned.

With the second approach, the same mistake can be made:

```kotlin
var zdt = LocalDateTime(year, 1, 1, 12, 0).atZone(TimeZone.currentSystemDefault())
    .resolve(myResolver)
buildList {
    while (zdt.localDateTime.year == year) {
        add(zdt)
        zdt = zdt.plus(1, DateTimeUnit.DAY).resolve(myResolver)
    }
}
```

It's possible to use the second approach without making this mistake, though:

```kotlin
var zdt = LocalDateTime(year, 1, 1, 12, 0).atZone(TimeZone.currentSystemDefault())
buildList {
    while (zdt.rawLocalDateTime.year == year) {
        add(zdt.resolve(myResolver))
        zdt = zdt.plus(1, DateTimeUnit.DAY)
    }
}
```

Topics
------

* First approach, first+, or the second approach?
* `withDate`: introduce all the `LocalDate` operations
  to `Instant`/`ZonedDateTime` or guide people to use `LocalDate` arithmetics?
* Naming (for the things we end up deciding to implement):
  - `Instant.withDate`/`ZonedDateTime.withDate`
  - `Instant.withTime`/`ZonedDateTime.withTime`
    Also, shortcuts `withTime(LocalTime)` and `withTime(Int, Int, Int, Int)`?
  - `ZonedDateTime` + `UnresolvedZonedDateTime`
  - `UnresolvedZonedDateTime.resolve`
  - `UnresolvedZonedDateTime.rawLocalDateTime`
* Deprecate date-based arithmetics on `Instant`?
* If applicable: time zones as context parameters in `Instant` arithmetics?
* Resolvers as context parameters in `resolve()`? In `Instant` arithmetics?


Appendix: the derivation of ZonedDateTime
=========================================

Currently, in `kotlinx-datetime`, we have three forms of datetime arithmetics:

* Interactions between `Duration` and `Instant`:
  - `instant + 5.minutes`
  - `instant.plus(10, DateTimeUnit.SECOND)`
  - `instant2 - instant1`
* Interactions between date units and `LocalDate`:
  - `date.plus(2, DateTimeUnit.MONTH)`
  - `date2 - date1`
* Interactions between `Instant` and date units:
  - `instant.plus(5, DateTimeUnit.DAY, TimeZone.currentSystemDefault())`
  - `instant.plus(DateTimePeriod(days = 5), TimeZone.currentSystemDefault())`
  - `instant1.periodUntil(instant2, TimeZone.currentSystemDefault())`

This model has been surprising for our users, as other libraries usually choose
another set of primitives to represent the available operations.
Typically
(as seen in `java.time`, Python's `datetime`, Go's `time`, and others),
a library exposes an entity combining the moment in time with an optional
time zone and allows performing arbitrary arithmetics on it.
`Instant` is just a moment in time without a time zone attached to it,
and `kotlinx.datetime.LocalDateTime` does not provide any arithmetics at all.
Still, the model we provide is almost equal in power to
`java.time.ZonedDateTime` (the one technical exception is that we don't provide
`withEarlierOffsetAtOverlap`/`withLaterOffsetAtOverlap`),
so it did get adopted, we received comments and questions about it,
and now, before the 1.0 release of the library signifies stronger stability
guarantees, is a good time to reevaluate our approach.

Although resolution of `LocalDateTime` to `Instant` is performed during
arithmetics, we treat it as a separate problem.
In particular, we do not discuss the different strategies one can use to perform
resolution.

Known problems
--------------

### Repetition of the time zone / Excessive computations

```kotlin
// A LocalDateTime 5 calendar days later in our time zone
localDateTime
    .toInstant(timeZone)
    .plus(5, DateTimeUnit.DAY, timeZone)
    .toLocalDateTime(timeZone)
```

Every operation here works with a time zone.

This gives us the flexibility to easily do things like

```kotlin
// Find out what people in Tunis see on their clocks
// at the moment that's five days later in Berlin
// than the moment when the given clock readings are observed in New York.
localDateTimeInNewYork
    .toInstant(TimeZone.of("America/New_York"))
    .plus(5, DateTimeUnit.DAY, TimeZone.of("Europe/Berlin"))
    .toLocalDateTime(TimeZone.of("Africa/Tunis"))
```

That's nice, but seemingly useless.

In exchange for this flexibility, we have to use `timeZone` multiple times,
and also, internally, more computations happen than this pipeline suggests.
What internally happens in the snippet at the top of the section can be
described with this pseudocode:

```kotlin
localDateTime
    .let { timeZone.rulesAt(it).resolveToInstant(DefaultResolver) } // toInstant
    .let { it.toLocalDateTime(timeZone.offsetAt(it)) }
    .let { it.date.plus(5, DateTimeUnit.DAY).atTime(it.time) }
    .let { timeZone.rulesAt(it).resolveToInstant(DefaultResolver) } // plus
    .let { it.toLocalDateTime(timeZone.offsetAt(it)) } // toLocalDateTime
```

In this snippet, `rulesAt(localDateTime)` is an operation that's not exposed
in the library that determines whether the given clock readings happened
once, more than once, or never in the given time zone, and returns the details.
`resolveToInstant` is another non-exposed function that takes these details
and chooses a suitable `Instant` value.

Here's how the same operation could have been implemented more efficiently:

```kotlin
val rules = timeZone.rulesAt(localDateTime)
val adjustedLdt = if (rules is Gap) rules.skipGap(localDateTime)
    else localDateTime
val ldt5DaysLater =
    adjustedLdt.date.plus(5, DateTimeUnit.DAY).atTime(adjustedLdt.time)
val rules5DaysLater = timeZone.rulesAt(ldt5DaysLater)
val result = if (rules5DaysLater is Gap) rules.skipGap(ldt5DaysLater)
    else ldt5DaysLater
```

In the common case where no time gaps occur, we:

* Calculate the rules object.
* Extract a `LocalDate` to perform arithmetics on.
* Find the updated `LocalDate`.
* Allocate a `LocalDateTime` with the old local time and the updated date.
* Calculate the rules object.

If the gap does occur, we also allocate an additional adjusted `LocalDateTime`.

In contrast, in the original code, we unconditionally:

* Calculate the rules object.
* (!) Allocate an `Instant`.
* (!) Calculate the UTC offset.
* (!) Allocate a `LocalDateTime`
  (that's most likely the same as `localDateTime`).
* Extract a `LocalDate` to perform arithmetics on.
* Find the updated `LocalDate`.
* Allocate a `LocalDateTime` with the old local time and the updated date.
* Calculate the rules object.
* (!) Allocate an `Instant`.
* (!) Calculate the UTC offset.
* (!) Allocate a `LocalDateTime`
  (that's most likely the same as the result of addition).

There's clearly noticeable overhead.

### Resolution to an Instant in arithmetic call chains is eager

In the example from the previous section, even the improved more efficient
version is not always exactly what one wants, but the alternatives are
non-obvious.

Imagine you have a start date `2024-01-16` and a time `16:30` and need to
enumerate all `Instant`s that happen 0, 1, 2, ..., 11 months after the start
date at `16:30`.

One (correct) way to implement this would be

```kotlin
val startDate = LocalDate(2024, 1, 16)
val time = LocalTime(16, 30)
repeat(12) {
    startDate.plus(it, DateTimeUnit.MONTH).atTime(time).toInstant(timeZone)
}
```

Another (and incorrect!) way to do that would be

```kotlin
val firstInstant = LocalDate(2024, 1, 16).atTime(16, 30)
    .toInstant(timeZone)
repeat(12) {
    firstInstant.plus(it, DateTimeUnit.MONTH, timeZone)
}
```

If `2024-01-16T16:30` is in a time gap in `timeZone`, then it is impossible for
`firstInstant` to be a moment when `2024-01-16T16:30` is shown on the clocks
in `timeZone`. Instead, it will be an `Instant` when, for example,
`2024-01-16T17:30` is shown.
`Instant` does not remember that we asked it to be a moment of `16:30`, so
now this code will behave as if we've initally chosen the time of `17:30`--
and *every* resulting `Instant` will be the time when clocks show `17:30`.
In contrast, the correct version will attempt working around gaps for each
resulting `Instant` individually and only skip gaps if they happen at the
destination moment.

So, on the one hand, we don't provide `LocalDateTime` arithmetics, meaning
that anyone with a `LocalDateTime` on their hands must convert that
`LocalDateTime` to `Instant` to do anything useful. On the other hand,
this conversion to `Instant` may lose the information that we initially had,
specifically the time of day.

(Note that, for example, Java's `ZonedDateTime` has the same issue:
<https://docs.oracle.com/javase/8/docs/api/java/time/ZonedDateTime.html#of-int-int-int-int-int-int-int-java.time.ZoneId->)

To sum it up, the initial resolution to `Instant` involved in datetime
arithmetics is not only inefficient but also potentially erroneous.

Returning to the example from the previous section:

```kotlin
// A LocalDateTime 5 calendar days later in our time zone
localDateTime
    .toInstant(timeZone)
    .plus(5, DateTimeUnit.DAY, timeZone)
    .toLocalDateTime(timeZone)
```

It's entirely possible that what was meant is actually the slightly different
and much more confusing

```kotlin
localDateTime
    .date.plus(5, DateTimeUnit.DAY)
    .atTime(localDateTime.time)
    // a roundtrip to ensure we have a valid LocalDateTime
    .toInstant(timeZone).toLocalDateTime(timeZone)
```

The difference, again, is that `toInstant` will eagerly adjust the time-of-day
to be correct before the arithmetic operations happen, whereas the second
version will only ensure the end result is valid.

`localDateTime.date.let { ... }.atTime(localDateTime.time)` is also not
a pattern we'd like to promote, though, as the roundtrip necessary to ensure
the `LocalDateTime` is valid in this time zone is very easy to forget.

### Lack of temporal adjusters

A temporal adjuster is a seemingly simple thing:

* `instant.withDayOfMonth(15, tz)`: find the moment it's the 15th of this month.
* `instant.withTime(LocalTime(0, 0), tz)`: find the start of this day.
* `instant.nextMonday(tz)`: find the next time it's Monday.
* `instant.nextTimeItIs(LocalTime(16, 30), tz)`:
  find the next time it's 16:30, possibly on the next day.

If we attempt to take time discontinuities into account, they are anything
but simple.

For example, let's look at `withDayOfMonth` and `nextMonday`.
These operations are nice and well-defined on `LocalDate`, but with our approach
of duplicating date-based operations for `Instant`, they obtain a surprising
behavior.

Imagine there clocks are advanced at `23:30` to `00:30` on the 15th of April.
On the 14th of April at `23:55`, we invoke `withDayOfMonth(15)`.
What should we return?

- Our usual way of dealing with time gaps is to advance
  the time by the size of the gap, which means we'd be returning
  `00:55` on the 16th, but this isn't 15th, like what was requested.
- We could return "the last moment of the 15th" if we detect this situation, but
  there's no such thing conceptually: `23:30` already doesn't exist.
  The closest to that is `23:29.999999999`.
- We could arbitrarily choose to go back the length of the gap instead of going
  forward, which would mean `22:55`, but that's inconsistent with everything
  else we do and is hard to justify.

Similarly, the same can happen when the requested time doesn't exist on a Monday
and we advance to Tuesday.

Yet even this is the simple case, one that we could do something about.
`nextTimeItIs(16:30)` and `withTime(00:00)` are on a different level of
conceptual complexity. Here, the programmer explicitly requests a specific time,
but what if that time doesn't exist?

- Do we naively do `date.atTime(00:00)` and then convert to `Instant`,
  potentially returning some arbitrary other moment, like `01:00`?
- Do we throw an exception, which will crash the application once a year,
  since the programmer didn't encounter this problem in testing?
- Do we find some other date with the time `00:00`?

What about the case when `nextTimeItIs` will return this day's `16:30`
if it's already `16:55` but we are about to move clocks from `17:00` to
`16:00`? Is this expected, or is this error-prone? Depending on the use case,
both answers are possible.

No matter which solution we implement, there's a problem: it's obscure.
To understand what `instant.nextTimeItIs` does, one has to understand its
implementation, there are no simple rules describing what it does, as some
strong guarantees make other ones impossible.

With a `LocalDate` or a `LocalDateTime`, there is no such problem, and
worst of all, the conceptual issues of dealing with non-existent times are
irrelevant if an adjustment is an intermediate step in a chain of operations.

```kotlin
instant
    .withLastDayOfMonth(tz)
    .lastSunday(tz)
```

In this example, we don't want to crash or return a day in the next month
from `withLastDayOfMonth` if the exact time we want doesn't exist on that date:
if the exact time we want exists on the last Sunday of that month, the
intermediate step is not important to us.

#### Related problem: inconvenient + error-prone adjustment of dates

A very common antipattern we've observed in `kotlinx-datetime` practice is
using `Instant` + date-unit arithmetics when `LocalDate` is a better fit.

A simple but very representative example encountered in the wild:

```kotlin
localDateTime
    .toInstant(timeZone)
    .plus(1, DateTimeUnit.DAY, timeZone)
    .toLocalDateTime(timeZone)
    .date
```

This can be easily replaced with

```kotlin
localDateTime.date.plus(1, DateTimeUnit.DAY)
```

One of the reasons for this is that people naturally gravitate to the most
powerful entity in our library (which is `Instant`) for every single operation,
because from their experience, using the most powerful entity (like
`java.time.ZonedDateTime`) is the way to go in datetime libraries.
Using our API like that is cumbersome, but not in a way that indicates a
potential improvement.

This is related to the issue of missing temporal adjusters, because with them,
we could instead suggest API like this:

```kotlin
fun Instant.withDate(timeZone: TimeZone, action: (LocalDate) -> LocalDate): Instant =
    toLocalDateTime(timeZone).let {
        action(it.date).atTime(it.time)
    }.toInstant(timeZone)
```

(In this example, we ignore the potential issue of `toInstant` advancing
the time of day to the next day, so that `withDate` returns a moment with a
date different from what was returned from `action`,
but the problem is still there!)

With that, the code above becomes

```kotlin
localDateTime
    .toInstant(timeZone)
    .withDate(timeZone) { it.plus(1, DateTimeUnit.DAY) }
    .toLocalDateTime(timeZone)
    .date
```

It teaches the programmer that `LocalDate` arithmetic operations exist and
don't require knowing a time zone to use and can hopefully make one question
whether they need an `Instant` at all here.

### Subpar debugging experience

Imagine you want to step through this code:

```kotlin
val originalInstant = Clock.System.now()
val timeZone = TimeZone.currentSystemDefault()
val instantOneDayLater = originalInstant.plus(1, DateTimeUnit.DAY, timeZone)
val instantOneDayAndThreeHoursLater = instantOneDayLater + 3.hours
```

The debugger tells you the values of the variables:

```
originalInstant: 2025-02-04T11:18:25.983057780Z
timeZone: Europe/Berlin
instantOneDayLater: 2025-02-05T11:18:25.983057780Z
instantOneDayAndThreeHoursLater: 2025-02-05T14:18:25.983057780Z
```

Because the code operates in a definite time zone, the programmer is likely to
think in terms of the `LocalDateTime` representation of these instants in the
time zone, but to actually get access to them and see if they match the
assumptions, new variables have to be introduced:

```kotlin
val originalInstant = Clock.System.now()
val timeZone = TimeZone.currentSystemDefault()
val ldtAtOriginalInstant = originalInstant.toLocalDateTime(timeZone)
val instantOneDayLater = originalInstant.plus(1, DateTimeUnit.DAY, timeZone)
val ldtOneDayLater = instantOneDayLater.toLocalDateTime(timeZone)
val instantOneDayAndThreeHoursLater = instantOneDayLater + 3.hours
val ldtOneDayAndThreeHoursLater =
    instantOneDayAndThreeHoursLater.toLocalDateTime(timeZone)
```

The situation is worse in cases when all computations are written in a single
chain:

```kotlin
val timeZone = TimeZone.currentSystemDefault()
Clock.System.now().plus(1, DateTimeUnit.DAY, timeZone) + 3.hours
```

Then, it's not enough to step through the expression chain to see the values,
one has to insert additional code inside:

```kotlin
val timeZone = TimeZone.currentSystemDefault()
Clock.System.now().apply {
    toLocalDateTime(timeZone)
}.plus(1, DateTimeUnit.DAY, timeZone).apply {
    toLocalDateTime(timeZone)
}.plus(3.hours).apply {
    toLocalDateTime(timeZone)
}
```

API derivation
--------------

Taking into account the problems highlighted above, we can formulate the desired
properties of the API for date arithmetics on `Instant`s:

* The resolution of a resulting `LocalDateTime` to `Instant` should be explicit:
  no silent adjustments to accommodate time gaps in intermediate results.
* The resolution of a resulting `LocalDateTime` to `Instant` should be
  mandatory: this means that adding `LocalDateTime` arithmetics along with a
  `fun LocalDateTime.ensureValidIn(timeZone: TimeZone): LocalDateTime`
  is not good enough.
* The API should facilitate the discovery of `LocalDate` arithmetics, which
  is often a better choice than using `Instant`s for arithmetics
  in the first place.
* The new API should provide temporal adjusters that work in a
  time-gap-oblivious manner without introducing additional discontinuities.
* Avoiding repetition of time zones should be a priority over
  the effortless flexibility of mixing several time zones in one chain of
  operations.

### Attempt 1: a special "unresolved" local date-time

The first constraints regarding a separate and mandatory resolution step mean
that neither `Instant` nor `LocalDateTime` can be the return type of date-based
arithmetic operations. Let us attempt to define the simplest possible separate
class:

```kotlin
class UnresolvedLocalDateTime {
    val rawLocalDateTime: LocalDateTime
    fun resolveIn(timeZone: TimeZone, resolver: Resolver): Instant
}

// not an actual implementation: mutually recursive to show relationships
// between operations
fun Instant.plus(
    value: Int, unit: DateTimeUnit.DateBased, timeZone: TimeZone
): UnresolvedLocalDateTime =
    toLocalDateTime(timeZone).plus(value, unit)

fun LocalDateTime.plus(
    value: Int, unit: DateTimeUnit.DateBased
): UnresolvedLocalDateTime =
    toInstant(TimeZone.UTC).plus(value, unit, TimeZone.UTC)

fun UnresolvedLocalDateTime.plus(
    value: Int, unit: DateTimeUnit.DateBased
): UnresolvedLocalDateTime = rawLocalDateTime.plus(value, unit)

fun Instant.withDayOfMonth(
    dayOfMonth: Int, timeZone: TimeZone
): UnresolvedLocalDateTime =
    toLocalDateTime(timeZone).withDayOfMonth(dayOfMonth)

fun LocalDateTime.withDayOfMonth(
    dayOfMonth: Int
): UnresolvedLocalDateTime =
    toInstant(TimeZone.UTC).withDayOfMonth(dayOfMonth, TimeZone.UTC)

fun UnresolvedLocalDateTime.withDayOfMonth(
    dayOfMonth: Int
): UnresolvedLocalDateTime = rawLocalDateTime.withDayOfMonth(dayOfMonth)
```

The resolution step is both separate and mandatory, which means we succeeded.

Immediately, we can see that the need to chain arithmetic operations together
requires that `UnresolvedLocalDateTime` and the main entry points for datetime
arithmetics should have a common interface: maybe they should be one and the
same, maybe they should inherit from the same class, or something similar, but
in any scenario, duplicating all arithmetic operations is a non-orthogonal
solution that complicates the conceptual model.

Another point to consider is the choice between providing `Instant.plus`,
`LocalDateTime.plus`, both, or none of the two.

Semantically, `UnresolvedLocalDateTime` is a weak version of `LocalDateTime`,
so the logical conclusion from having this class looks like this:

```kotlin
interface LocalDateTimeAlike {
    fun plus(
        value: Int, unit: DateTimeUnit.DateBased
    ): UnresolvedLocalDateTime

    fun withDayOfMonth(
        dayOfMonth: Int
    ): UnresolvedLocalDateTime

    fun toInstant(timeZone: TimeZone, resolver: Resolver): Instant
}

class UnresolvedLocalDateTime: LocalDateTimeAlike {
    val rawLocalDateTime: LocalDateTime
}

class LocalDateTime: LocalDateTimeAlike
```

`UnresolvedLocalDateTime` can not inherit from `LocalDateTime`, because this
makes it trivial to forget to resolve an `UnresolvedLocalDateTime` to a proper
`LocalDateTime`: you can simply upcast instead.
`LocalDateTime` does not inherit from `UnresolvedLocalDateTime`, because
accessing `rawLocalDateTime` on a proper `LocalDateTime` would just return
`this`, but this is not set in stone: we have `DatePeriod : DateTimePeriod`,
where using time-based components is meaningless, so if there is enough utility
in passing `LocalDateTime` somewhere an `UnresolvedLocalDateTime` is expected,
we can reevaluate this.

Using this API helps with some of the repetition of time zones, but not
completely if the starting point is an `Instant`:

```kotlin
instant
    .toLocalDateTime(TimeZone.of("Europe/Berlin"))
    .withDayOfMonth(1)
    .plus(3, DateTimeUnit.MONTH)
    .toInstant(TimeZone.of("Europe/Berlin"), resolver)
```

Semantically, this duplication does not make sense.
We define `LocalDateTime` as something existing in an implicit unspecified
`TimeZone`, so all the arithmetic operations on `UnresolvedLocalDateTime` do
actually imply some time zone, and therefore logically, `toInstant` should
not require specifying the time zone again, as it was already provided and
could not have changed in the meantime.

<https://docs.oracle.com/javase/8/docs/api/java/time/ZonedDateTime.html#withZoneSameLocal-java.time.ZoneId->
is a `java.time` operation that attempts to reinterpret a given `LocalDateTime`
using the original and the new time zone, which is roughly equivalent to
passing different time zones to `toLocalDateTime` and `toInstant` above.
<https://grep.app/search?q=withZoneSameLocal> and
<https://stackoverflow.com/search?q=withZoneSameLocal> do not quickly surface
any compelling use cases for this that aren't covered better by other means.

### Attempt 2: LocalDateTime + TimeZone

To solve the problem of the implicit `TimeZone` getting forgotten after the
`LocalDateTime` is obtained from the `Instant`, we can store the `TimeZone`
in the object.

This is similar to the common interpretation of `java.time.ZonedDateTime`,
except that the technical implementation is actually different.

```kotlin
class UnresolvedZonedDateTime {
    val rawLocalDateTime: LocalDateTime
    val timeZone: TimeZone
    fun resolveIn(resolver: Resolver): Instant
}

```

This time, it is clearer that this class is neither the sub- nor the superclass
of `LocalDateTime`, as it's both weaker in that it's unresolved and stronger in
that it contains a `TimeZone`.

Using this API means that `LocalDateTime` arithmetic operations can not be
provided with the same signature as on `UnresolvedLocalDateTime`, as they now
require a `TimeZone` to be well-defined, and so `Instant` and `LocalDateTime`
share the exact same signatures now.

To avoid duplicating functionality, therefore, it is necessary to factorize
date-based arithmetic operations through conversion functions converting
`Instant` and `LocalDateTime` to a `ZonedDateTime`:

```kotlin
fun LocalDateTime.atZone(timeZone: TimeZone): UnresolvedZonedDateTime

fun Instant.atZone(timeZone: TimeZone): ZonedDateTime
```

It's logically incorrect for `Instant.atZone` to return an
`UnresolvedZonedDateTime`, because the local date-time is actually resolved,
meaning we can safely perform elapsed-time-based arithmetics on it.

```kotlin
sealed class UnresolvedZonedDateTime(
    val rawLocalDateTime: LocalDateTime,
    val timeZone: TimeZone,
) {
    fun resolve(resolver: Resolver): ZonedDateTime

    fun plus(
        value: Int, unit: DateTimeUnit.DateBased
    ): UnresolvedZonedDateTime

    fun withDayOfMonth(
        dayOfMonth: Int
    ): UnresolvedZonedDateTime

    fun plus(
        period: DateTimePeriod,
        resolver: Resolver,
    ): ZonedDateTime
}

class ZonedDateTime(
    localDateTime: LocalDateTime,
    val utcOffset: UtcOffset,
    timeZone: TimeZone,
): UnresolvedLocalDateTime(localDateTime, timeZone) {
    fun toInstant(): Instant

    val localDateTime: LocalDateTime get() = rawLocalDateTime

    fun plus(
        value: Int, unit: DateTimeUnit.TimeBased
    ): ZonedDateTime

    fun plus(
        value: Int, unit: DateTimeUnit
    ): UnresolvedZonedDateTime
}
```

Here, we encounter the same problem as with `LocalDateTime` inheriting from
`UnresolvedLocalDateTime`: having `rawLocalDateTime` on `ZonedDateTime` does
not make sense. Still, the alternative of separating a new interface doesn't
seem to be better:


```kotlin
interface ZonedDateTime {
    val timeZone: TimeZone

    fun plus(
        value: Int, unit: DateTimeUnit.DateBased
    ): UnresolvedZonedDateTime

    fun withDayOfMonth(
        dayOfMonth: Int
    ): UnresolvedZonedDateTime

    fun plus(
        period: DateTimePeriod,
        resolver: Resolver,
    ): ResolvedZonedDateTime

    // fun unresolved(): UnresolvedZonedDateTime = plus(0, DateTimeUnit.DAY)
}

class UnresolvedZonedDateTime(
    val rawLocalDateTime: LocalDateTime,
    override val timeZone: TimeZone,
): ZonedDateTime {
    fun resolve(resolver: Resolver): ZonedDateTime
}

class ResolvedZonedDateTime(
    val localDateTime: LocalDateTime,
    val utcOffset: UtcOffset,
    timeZone: TimeZone,
): ZonedDateTime {
    val instant: Instant get() = localDateTime.toInstant(utcOffset)

    fun plus(
        value: Int, unit: DateTimeUnit.TimeBased
    ): ZonedDateTime

    fun plus(
        value: Int, unit: DateTimeUnit
    ): UnresolvedZonedDateTime
}
```

The common interface doesn't seem useful on its own, and also, implementing
`ZonedDateTime` is semantically the same as implementing
`UnresolvedZonedDateTime`, as `zonedDateTime.plus(0, DateTimeUnit.DAY)` is not
supposed to have any effect.
As a result, we haven't solved the problem of `rawLocalDateTime`:
it's a natural thing to add to `ZonedDateTime`, but then,
`ResolvedZonedDateTime` will obtain it, too.

With this, the constraints of explicit and mandatory resolve calls and the
constraint of removing logically extraneous repetitions of timezones have been
solved.

The next consideration is the ease of defining custom extension functions.

```kotlin
fun ZonedDateTime.atEndOfMonth(): UnresolvedZonedDateTime =
    UnresolvedZonedDateTime(
        with(rawLocalDateTime) { date.yearMonth.lastDay.atTime(time) },
        timeZone
    )
```

(With the interface-based approach, because there is no `rawLocalDateTime`, it's
even more difficult)

The most worrying problem here is that people writing custom date manipulations
have to do strictly more work than they would have to if they used
`LocalDateTime`, which doesn't require resolving anything, so the complexity of
the API encourages error-prone methods when something is not provided out of
the box.

### Attempt 3.1: extra combinators for idiomatic and generic date manipulation

```kotlin
sealed class UnresolvedZonedDateTime(
    val rawLocalDateTime: LocalDateTime,
    val timeZone: TimeZone,
) {
    fun resolve(resolver: Resolver): ZonedDateTime

    fun withDate(action: (LocalDate) -> LocalDate): UnresolvedZonedDateTime

    fun withTime(action: (LocalTime) -> LocalTime): UnresolvedZonedDateTime

    fun plus(
        period: DateTimePeriod,
        resolver: Resolver,
    ): ZonedDateTime
}

class ZonedDateTime(
    localDateTime: LocalDateTime,
    val utcOffset: UtcOffset,
    timeZone: TimeZone,
): UnresolvedLocalDateTime(localDateTime, timeZone) {
    fun toInstant(): Instant

    val localDateTime: LocalDateTime get() = rawLocalDateTime

    fun plus(
        value: Int, unit: DateTimeUnit.TimeBased
    ): ZonedDateTime

    fun plus(
        value: Int, unit: DateTimeUnit
    ): UnresolvedZonedDateTime
}

fun UnresolvedZonedDateTime.plus(
    value: Int, unit: DateTimeUnit.DateBased
): UnresolvedZonedDateTime = withDate { it.plus(value, unit) }

fun UnresolvedZonedDateTime.withDayOfMonth(
    dayOfMonth: Int
): UnresolvedZonedDateTime = withDate { it.withDayOfMonth(dayOfMonth) }

fun UnresolvedZonedDateTime.atMidnight(): UnresolvedZonedDateTime =
    withTime { LocalTime(0, 0) }
```

With this, it becomes viable to avoid manual manipulations of `ZonedDateTime`.

Other potential combinators include `withDate(LocalDate)` and
`withTime(LocalTime)`, which can easily be expressed using the existing ones,
and `withInstant`:

```kotlin
fun UnresolvedZonedDateTime.withInstant(instant: Instant): ZonedDateTime =
    instant.atZone(timeZone)

fun ZonedDateTime.withInstant(action: (Instant) -> Instant): ZonedDateTime =
    action(toInstant()).atZone(timeZone)

fun UnresolvedZonedDateTime.withInstant(
    resolver: Resolver, action: (Instant) -> Instant
): ZonedDateTime = resolve(resolver).withInstant(action)
```

All of these functions seem simple to write, and it's not clear that they bring
much utility, given that they replace all the information in the provided object
except the time zone.

Likewise, `withLocalDateTime` is probably useless, as we intentionally avoid
providing any arithmetics on it.

### Attempt 3.2: removing ZonedDateTime

With the new combinators in place, we should revisit whether `ZonedDateTime` is
something we actually need. Given that the date-based operations factorize
through `withDate` now, is it not enough to provide this combinator on `Instant`
and still somehow satisfy our constraints?

The goal of implicit resolutions is in any scenario undermined by the existence
of `DateTimePeriod`: a resolution must happen after adding the date-based
components but before adding the time-based ones, so the only indicator of a
resolution taking place is the need to pass a resolver.
We could embrace this pattern and say that if something receives a `Resolver`,
it means that exactly one resolution takes place internally somewhere.

```kotlin
fun Instant.withDate(
    resolver: Resolver, timeZone: TimeZone, action: (LocalDate) -> LocalDate
): Instant = with(toLocalDateTime()) {
    action(date).atTime(time)
}.toInstant(resolver)

fun Instant.withTime(
    resolver: Resolver, timeZone: TimeZone, action: (LocalTime) -> LocalTime
): Instant = with(toLocalDateTime()) {
    action(time).onDate(date)
}.toInstant(resolver)

// can not be defined using withDate(...).withTime(...),
// as it avoids an extra resolution
fun Instant.withDateAndTime(
    resolver: Resolver, timeZone: TimeZone,
    dateAction: (LocalDate) -> LocalDate,
    timeAction: (LocalTime) -> LocalTime,
): Instant = with(toLocalDateTime()) {
    dateAction(date).atTime(timeAction(time))
}.toInstant(resolver)
```

Realistically, what *can* you do with a `LocalDateTime` without resolving it to
`Instant`? You can set the date, and you can set the time.
`withDateAndTime` has almost the same expressive power as `ZonedDateTime`,
the only omission is that `dateAction` and `timeAction` have to happen
independently, which is not a problem in the usual use cases.

When given a `LocalDateTime`, it is impossible to avoid the initial resolution
with just these functions, so we in fact have to implement these ones as well:

```kotlin
fun LocalDateTime.toInstantWithDate(
    resolver: Resolver, timeZone: TimeZone, action: (LocalDate) -> LocalDate
): Instant = action(date).atTime(time).toInstant(resolver)

fun LocalDateTime.toInstantWithTime(
    resolver: Resolver, timeZone: TimeZone, action: (LocalTime) -> LocalTime
): Instant = action(time).onDate(date).toInstant(resolver)

fun LocalDateTime.toInstantWithDateAndTime(
    resolver: Resolver, timeZone: TimeZone,
    dateAction: (LocalDate) -> LocalDate,
    timeAction: (LocalTime) -> LocalTime,
): Instant = dateAction(date).atTime(timeAction(time)).toInstant(resolver)
```

This approach has the upside of being simple and requiring minimal intervention,
and also satisfies all the constraints we've imposed.

