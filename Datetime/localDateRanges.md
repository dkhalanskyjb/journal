LocalDate ranges and progressions
=================================

A range allows querying whether an element is part of it:
`if (0 in 1..10)`.
A progression allows iterating over it:
`for (i in 1..10)`.

`LocalDate` is a class describing dates in the Gregorian calendar,
like `2024-08-24`. Ranges of `LocalDate` are well-defined:

```kotlin
val currentDate = Clock.System.todayAt(TimeZone.currentSystemDefault())
if (currentDate in LocalDate(2024, 1, 15)..<LocalDate(2025, 1, 15)) {
    // ...
}
```

Progressions of `LocalDate` also look simple:

```kotlin
// iterate over all the dates of 2024
for (dateIn2024 in LocalDate(2024, 1, 1)..<LocalDate(2025, 1, 1))) {
    // ...
}
```

This is a requested feature:
<https://github.com/Kotlin/kotlinx-datetime/issues/190>.

Looking for usages of datetime arithmetics shows that many date arithmetics
usages are just homegrown date progressions. Some examples:

* <https://github.com/hi-manshu/Kalendar/blob/2d6a5cfaa77ad97b03fd8ad6a609ba5627cf11eb/kalendar/src/main/java/com/himanshoe/kalendar/ui/oceanic/util/WeekData.kt#L29>
* <https://github.com/joelkanyi/MealTime/blob/e911cc8264c340353814746e45ac2e11defdd551/core/common/src/main/java/com/joelkanyi/common/util/UtilFunctions.kt#L237-L252>

Prior art
---------

### Kotlin's ranges

In Kotlin, we have several implementations for ranges and progressions,
described in <https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.ranges/>:

* Characters
* Integral values
* Floating-point values (ranges only, no progressions)

### Go, Python, C++

No support for date progressions, just use generators or something:

* <https://stackoverflow.com/questions/50982524/how-to-gracefully-iterate-a-date-range-in-go>
* <https://stackoverflow.com/questions/46455168/iterating-through-a-daterange-in-python>,
  <https://stackoverflow.com/questions/22907062/iterating-over-date-in-python>,
  <https://stackoverflow.com/questions/1060279/iterating-through-a-range-of-dates-in-python>
* <https://old.reddit.com/r/cpp_questions/comments/vrktgx/iterating_over_days_dates_in_c_20/>

### Rust

No nice syntax for date progressions, but there are two iterators:

* <https://docs.rs/chrono/0.4.19/chrono/naive/struct.NaiveDate.html#method.iter_days>
* <https://docs.rs/chrono/0.4.19/chrono/naive/struct.NaiveDate.html#method.iter_weeks>

One iterates over every day starting from the given one, and the other iterates over
every 7 days starting from the given one.
Both iterators are infinite.

Grep.app finds some usages: <https://grep.app/search?q=iter_days%28%29>
They are split into three forms:

* `iter_days().take(length)`:
  - <https://github.com/halvnykterist/rating-update/blob/d65291695599c63203371345a4f6b98da0118c7b/src/api.rs>
  - <https://github.com/pop-os/cosmic-applets/blob/master/cosmic-applet-time/src/window.rs>
  - <https://github.com/shouya/malakal/blob/e609eccc6c2e841d7c166bab98c70fdfe0853438/src/widget/schedule_ui.rs>
* `iter_days().take_while(|day| *day <= end)`:
  - <https://github.com/kesyog/crossword/blob/af1b6f58a4147f9dae5d6abfd346e2234ef8291f/src/lib.rs>
  - <https://github.com/larsfroelich/unifi-protect-bulk-download/blob/main/src/main.rs>,
    but the implementation is either strangely written or optimized.
  - <https://github.com/hove-io/transit_model/blob/d197b8c4ad525339b39093726661c0c4c1ecb7db/src/model_builder.rs>
* `iter_days().take_while(|day| *day < end)`:
  - <https://github.com/travisbrown/cancel-culture/blob/88f8fefbc86c64cf8c531ee864d7d8483afd6b6d/src/browser/twitter/search.rs>
  - <https://github.com/open-rust-initiative/freighter/blob/main/src/handler/channel.rs>,
    but the implementation is either strangely written or optimized.

### Java

There is a dedicated `LocalDate.datesUntil(LocalDate, Period = 1 day)` function
starting from Java 9:

* <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/LocalDate.html#datesUntil(java.time.LocalDate)>
* <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/LocalDate.html#datesUntil(java.time.LocalDate,java.time.Period)>

The end date is excluded.

Grep.app isn't finding a lot:
<https://grep.app/search?q=datesUntil&words=true&filter[lang][0]=Java> returns
only 20 hits as of writing, and the only relevant ones are:

* <https://github.com/opentripplanner/OpenTripPlanner/blob/8164d41b4f9ad7e5c946dfeec4e9dd567b38e6f5/src/main/java/org/opentripplanner/routing/stoptimes/StopTimesHelper.java#L202-L203>
* <https://github.com/opentripplanner/OpenTripPlanner/blob/8164d41b4f9ad7e5c946dfeec4e9dd567b38e6f5/src/main/java/org/opentripplanner/routing/algorithm/raptoradapter/transit/TripPatternForDate.java#L157>
* <https://github.com/apache/incubator-kie-optaplanner-quickstarts/blob/6e98dfd687322bc0b1f7719d5da8d5412e6bf7a8/use-cases/maintenance-scheduling/src/main/java/org/acme/maintenancescheduling/domain/MaintenanceSchedule.java#L46-L54>

That said, on Stack Overflow, these functions are often recommended
(<https://stackoverflow.com/search?tab=votes&q=datesUntil&searchOn=3>).
Use cases:

* Iterate over days from some date to some other date (inclusive):
  - <https://stackoverflow.com/questions/53564494/java-string-to-date-conversions-to-print-1-year-of-dates/53567129#53567129>
  - <https://stackoverflow.com/questions/18489927/a-day-without-midnight/61328380#61328380>
  - <https://stackoverflow.com/questions/60361104/java-get-all-working-days-in-a-year-in-yyyymmdd-format/60366269#60366269>
    (the code is incorrect, as it doesn't add one day to the end date)
  - <https://stackoverflow.com/questions/75589148/java-get-list-date-between-2-dates/75589270#75589270>
  - <https://stackoverflow.com/questions/2689379/how-to-get-a-list-of-dates-between-two-dates-in-java/42344215#42344215>
  - <https://stackoverflow.com/questions/4534924/how-to-iterate-through-range-of-dates-in-java/33060516#33060516>
* Iterate over days from some date to some other date (exclusive):
  - <https://stackoverflow.com/questions/65330169/how-to-get-business-days-between-two-dates-in-java/65330264#65330264>
  - <https://stackoverflow.com/questions/19426807/given-a-range-getting-all-dates-within-that-range-in-scala/52487942#52487942>
  - <https://stackoverflow.com/questions/20089549/how-to-get-all-the-dates-in-a-month-using-calender-class/53649080#53649080>
  - <https://stackoverflow.com/questions/8166390/java-generate-all-dates-between-x-and-y/35907027#35907027>
  - <https://stackoverflow.com/questions/49963037/calculating-workday-difference-between-two-calendar-dates/49963107#49963107>
  - <https://stackoverflow.com/questions/38220543/java-8-localdate-how-do-i-get-all-dates-between-two-dates/52416855#52416855>
  - <https://stackoverflow.com/questions/4600034/calculate-number-of-weekdays-between-two-dates-in-java/51010738#51010738>
* Calculating the number of days between dates:
  - <https://stackoverflow.com/questions/60601606/calculating-days-between-two-dates-in-java-catch-parseexception-error/60601807#60601807>
* Obtain ISO week numbers (why?) for dates between the two given ones:
  - <https://stackoverflow.com/questions/52083103/week-numbers-between-two-dates-in-java/52084683#52084683>
    Here, the period of 7 days is used.

Notably, not a single clear use case that would need a `Period`.
It may be so that there are no such use cases: the overload accepting a `Period`
was added together with the usual one, so it may have been done for the
purposes of generality and flexibility:
<https://docs.oracle.com/javase/9/docs/api/java/time/LocalDate.html#datesUntil-java.time.LocalDate-java.time.Period->

API shape
---------

Listing things from <https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.ranges/>
that we have for the other ranges and progression, we get this API to support:

```kotlin
sealed class LocalDateProgression: Iterable<LocalDate> {
    internal val first: LocalDate
    internal val last: LocalDate
    internal val step: ???

    internal fun isEmpty(): Boolean

    public companion object {
        internal fun fromClosedRange(
            rangeStart: LocalDate,
            rangeEnd: LocalDate,
            step: ???,
        )
    }
}

public class LocalDateRange(val start: LocalDate, val endInclusive: LocalDate) :
    LocalDateProgression, ClosedRange<LocalDate>, OpenEndRange<LocalDate>
{
    public companion object {
        public val EMPTY: LocalDateRange
    }

    @Deprecated(
        "This throws an exception if the exclusive end if not inside " +
        "the platform-specific boundaries for LocalDate. " +
        "The 'endInclusive' property does not throw and should be preferred.",
        level = DeprecationLevel.WARNING
    )
    override val endExclusive: LocalDate
}

public operator fun LocalDate.rangeTo(other: LocalDate): LocalDateRange
public operator fun LocalDate.rangeUntil(other: LocalDate): LocalDateRange

public operator fun LocalDateRange.contains(element: LocalDate?): Boolean

public infix fun LocalDate.downTo(to: LocalDate): LocalDateProgression

public fun LocalDateProgression.first(): LocalDate
public fun LocalDateProgression.firstOrNull(): LocalDate?
public fun LocalDateProgression.last(): LocalDate
public fun LocalDateProgression.lastOrNull(): LocalDate?

// questionable, but it looks like we'll need them: see "Random dates" below
public fun LocalDateRange.random(): LocalDate
public fun LocalDateRange.random(random: Random): LocalDate
public fun LocalDateRange.randomOrNull(): LocalDate?
public fun LocalDateRange.randomOrNull(random: Random): LocalDate?

public fun LocalDateProgression.reversed(): LocalDateProgression

public infix fun LocalDateProgression.step(
    ???
): LocalDateProgression

// in any case, we'll need these overloads
public infix fun LocalDateProgression.step(
    value: Long,
    unit: DateTimeUnit.DayBased,
): LocalDateProgression

public infix fun LocalDateProgression.step(
    value: Int,
    unit: DateTimeUnit.DayBased,
): LocalDateProgression

```

Functions that are present in the other ranges but have unclear utility:

```kotlin
// replaced by `..<`
infix fun LocalDate.until(to: LocalDate): LocalDateRange
```

### What is a `LocalDateProgressionStep`?

#### What can we add to dates at all?

If we look at the `plus` operations, there are the following things that we
could add to a `LocalDate`:

* `DatePeriod`: a pair of the number of months to add and days to add, in that
  order.
* An `Int` or a `Long` number of `DateTimeUnit.DateBased`:
  - `plus(2, DateTimeUnit.YEAR)` is a date 24 months later.
  - `plus(-5, DateTimeUnit.WEEK)` is a date 35 days earlier.

The algorithm for adding some number of month is an optimization of the
following one:

* Take the current date.
* Increment the month number, overflowing into the year on overflow.
* If the resulting date does not exist (for example, `2024-02-31`,
  obtained by adding one month to `2024-01-31`),
  use the last day of this month.

This addition is non-associative: `2024-01-31 + (1 month + 1 month)` is
`2024-03-31`, but `(2024-01-31 + 1 month) + 1 month` is `2024-02-29`.

#### How can we add things to dates in a loop?

A progression is an object representing this generator's state machine:

```kotlin
// State machine 1
var current = first
while (
    step.isPositive() && current <= last ||
    step.isNegative() && current >= last
) {
    yield(current)
    current = try {
        current.plus(step)
    } catch (e: Overflow) {
        break
    }
}
```

An alternative (but, for the existing progressions, equivalent) state machine is:

```kotlin
// State machine 2
var i = 0
while (true) {
    val current = first + i * step
    if (
        step.isPositive() && current <= last ||
        step.isNegative() && current >= last
    ) break
    yield(current)
    ++i
}
```

This means that `(0..10) step n` may either be empty (if `n` is negative)
or contain at least `0`.
Thus, progressions can not be implemented consistently with the existing ones
without knowing the sign on the step.

##### `DatePeriod`

Syntactically, `step` looks nicer when only one value needs to be passed to it,
because then, it can be `infix`, so it's worth considering steps measured in
`DatePeriod` values:

```kotlin
LocalDate(2023, 2, 28)..LocalDate(2024, 2, 29) step DatePeriod(days = 1)
```

`DatePeriod` does not necessarily have a consistent step direction, and in fact,
some non-zero step values have fixed points:

```kotlin
generateSequence(LocalDate(2023, 2, 28)) {
    it.plus(DatePeriod(months = 1, days = -28))
}.take(3).toList()
// [2024-01-31, 2024-01-30, 2024-01-30]
```

The following code determines the maximum and minimum numbers of days in
`n` consecutive months:

```kotlin
val maxResults = mutableListOf<Int>()
val minResults = mutableListOf<Int>()
for (i in 1..60) { maxResults.add(0); minResults.add(Int.MAX_VALUE) }
for (year in 1993..2005) { // TODO: check on a larger year span if needed
    for (startMonth in 1..12) {
        val startDate = LocalDate(year, startMonth, 1)
        for (months in 1..60) {
            val endDate = startDate.plus(months, DateTimeUnit.MONTH)
            maxResults[months-1] = maxOf(maxResults[months-1], startDate.daysUntil(endDate))
            minResults[months-1] = minOf(minResults[months-1], startDate.daysUntil(endDate))
        }
    }
}
```

The rough results show that the difference between the maximum and minimum
numbers of days in consecutive months differs by 0-4 days, mostly by 3-4.
This means that for given number of months, there are probably several
`DatePeriod` values that, depending on the input `LocalDate`, may go forward,
backward in time or stay in place.

Calculating the precise set of `DatePeriod` values that are unambiguously
positive and unambiguously negative should be possible (especially if we give
ourselves a generous range where false failures are allowed), but it's not clear
that it's worth the effort.

##### A multiple of `DateTimeUnit.DateBased`

We probably can't just add `DateTimeUnit.DateBased` values without a multiplier:
they can't be negative, but iterating in the opposite direction is often
performed. Therefore, we have to stick to the full function-calling syntax:

```kotlin
(LocalDate(2023, 2, 28)..LocalDate(2024, 2, 29)).step(1, DateTimeUnit.DAY)
```

Adding a more convenient syntax is a language task that's probably closely
related to <https://youtrack.jetbrains.com/issue/KT-53675/>.

Even if we limit ourselves to just `DateTimeUnit.DateBased` and forbid
mixing together months and days, months on their own are already enough to
cause trouble:

```kotlin
(LocalDate(2023, 1, 31)..LocalDate(2023, 5, 1)).step(1, DateTimeUnit.MONTH)
// What to output?
// Option 1: 2023-01-31, 2023-02-28, 2023-03-31, 2023-04-30
// Option 2: 2023-01-31, 2023-02-28, 2023-03-28, 2023-04-28

(LocalDate(2023, 2, 28)..LocalDate(2023, 5, 1)).step(1, DateTimeUnit.MONTH)
// This is unambiguous: 2023-02-28, 2023-03-28, 2023-04-28
```

Depending on whether we choose state machine 1 or 2 as the definition of what a
progression is, we get different results if we try adding some multiple of
`DateTimeUnit.MONTH` to the end of a month.
As mentioned above, adding months is a non-associative operation, which causes
the issue.

In Java, `datesUntil(LocalDate, Period)` uses the state machine 2: that is,
adding a month repeatedly to `2023-01-31` will output the last day of each
month.

##### A multiple of `DateTimeUnit.DAY`

Given that we failed to find any use cases for adding months repeatedly, there
seems to be no need to provide anything other than
`step(Int/Long, DateTimeUnit.DayBased)`, free from all the conceptual concerns.

**Proposal**: let's stick with that until anyone requests anything specific.
Let's also not add any public API for obtaining the step of a progression,
because it would be a breaking change to modify the type of the step later.

### Random dates

It's initially unclear whether one needs `LocalDateProgression.random`, but a
search through Stack Overflow shows that people do occasionally need it:

* <https://stackoverflow.com/questions/40253332/generating-random-date-in-a-specific-range-in-java>
* <https://stackoverflow.com/questions/3985392/generate-random-date-of-birth>
* <https://stackoverflow.com/questions/42532025/how-do-i-generate-any-random-date-between-01-01-2016-to-01-01-2017-using-java>
* <https://stackoverflow.com/questions/34051291/generate-a-random-localdate-with-java-time>

The solutions alternative to just providing the required `random()` method seem
cumbersome: to correctly implement obtaining a random date, one must either take
leap years into account and generate year-month-day combinations (which gets
ugly when the range to generate dates in is not aligned with year boundaries),
or have enough knowledge of our API to convert dates `toEpochDays` and back.

Looks like the operation, though obscure, is useful, harmless, and difficult to
implement correctly, which makes it a good candidate for addition.

**Proposal**: let's just add it.
