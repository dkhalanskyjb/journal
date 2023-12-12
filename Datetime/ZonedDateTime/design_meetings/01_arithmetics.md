Datetime arithmetics in kotlinx-datetime
========================================

Overview
--------

A reminder of how datetime arithmetics in our library works currently.

There are several fundamental operations that we support:

* `LocalDate.plus(DAY/MONTH)` is implemented under the assumption that
  adding a day or a month is not susceptible to timezone transitions.
  It's an operation defined by the calendar, not by physical time.
* `Instant.plus(Duration)` is implemented in terms of physical time.
* `LocalDateTime.toInstant(TimeZone)` finds the moment of physical time
  corresponding to the given calendar representation. (**contaminated**)
* `LocalDate.atStartOfDay(TimeZone)` (irrelevant to the discussion).
* `Instant.toLocalDateTime(TimeZone)`.
* `LocalDate.atTime(LocalTime): LocalDateTime`.

Derivative notions:

* `Instant.plus(n * (DAY/MONTH), TimeZone)` finds the calendar representation of
  the given physical moment via `Instant.toLocalDateTime(TimeZone)`,
  advances the calendar date as needed via `LocalDate.plus(DAY/MONTH)`, and then
  performs `LocalDateTime.toInstant(TimeZone)`. (**contaminated**)
* `Instant.plus(DatePeriod)` adds months via `Instant.plus(n * MONTH, TimeZone)`,
  then adds days via `Instant.plus(n * DAY, TimeZone)`. (**contaminated**)
* `Instant.plus(DateTimePeriod)` first performs `plus(DatePeriod, TimeZone)`
  then `plus(Duration)`. (**contaminated**)
* `Instant.plus(DateTimeUnit.TimeBased)` can be expressed via
  `Instant.plus(Duration)`.
* etc.

The **contaminated** operations are susceptible to lossy conversion from
`LocalDateTime` to `Instant`.

* If the target moment doesn't exist (for example, `15:14` if `14:59` is
  directly followed by `16:00`), the duration of the transition is added
  (and the example becomes `16:14`).
* If the target moment is ambiguous (for example, `15:14` if `15:59` is
  followed by `15:00` again), the moment before the transition is used.
  Sometimes, when we expect that the moment after the transition would be
  preferred (for example, when subtracting one day), we use the moment after the
  transition instead, but this is not reliable.

We plan to support configuring this behavior via user-supplied resolution
strategies.

Problems
--------

### Pattern: roundtrip

A common pattern:

```kotlin
instant.plus(1, DateTimeUnit.DAY, zone1).toLocalDateTime(zone2)
```

Small issues:

* It's tough to imagine any use cases where `zone1` and `zone2` would be
  different. In practice, they are the same expression.
* Often, `zone1` and `zone2` are both `TimeZone.currentSystemDefault()`. This
  introduces a race condition if the system time zone changes between the two
  calls.
* Internally, `plus` performs conversion to `LocalDateTime` +
  a preferred offset, adds a day, resolves `LocalDateTime` and offset to a valid
  pair, *then* converts it to `Instant`, but then converts it back. In effect,
  `Instant` is just the source of data and a useless intermediary.

This pattern is often part of a larger one:

```kotlin
localDateTime.toInstant(zone).plus(1, DateTimeUnit.DAY, zone).toLocalDateTime(zone)
```

Here, the following happens:

* `LocalDateTime` is resolved to a valid value + offset.
* This pair is converted to an `Instant`.
* The `Instant` is converted back to the same `LocalDateTime` and offset.
* A day is added.
* The new `LocalDateTime` is resolved to a valid value + offset.
* The pair is converted to an `Instant`.
* This `Instant` gets converted back to the same `LocalDateTime`.

If we remove the useless intermediate steps, we simply get

* `LocalDateTime` is resolved to a valid value + offset.
* A day is added.
* The new `LocalDateTime` is resolved to a valid value.

Notes:

* The usage of `Instant` here is incidental and obscures the places where
  resolving happens. If we mark all the contaminated API with resolvers, the
  pattern will get even more cumbersome.
* The first `Instant` resolution is often undesirable, but there is no way to
  opt out of it. More on that below.

### Example: special-casing days

Consider this code:

```kotlin
// move the delivery to the same time of the next workday
val tomorrow = deliveryPlannedAt.plus(1, DateTimeUnit.DAY, zone)
return if (tomorrow.dayOfWeek == DayOfWeek.SUNDAY) {
  // skip SUNDAY
  tomorrow.plus(1, DateTimeUnit.DAY, zone)
} else {
  tomorrow
}
```

It has a subtle bug: if there is a time gap on Sunday, this will shift the end
result. This is an example of an accidental resolution that turns out harmful.

To properly avoid this, one must do one of the following:

```kotlin
val deliveryLdt = deliveryPlannedAt.toLocalDateTime(zone)
// move the delivery to the same time of the next workday
val tomorrow = deliveryLdt.date.plus(1, DateTimeUnit.DAY)
val newDeliveryDay = if (tomorrow.dayOfWeek == DayOfWeek.SUNDAY) {
  // skip Sunday
  tomorrow.plus(1, DateTimeUnit.DAY)
} else {
  tomorrow
}
// don't forget to do a roundtrip with `Instant` when returning `LocalDateTime` directly!
val deliveryUnresolvedLdt = newDeliveryDay.atTime(deliveryLdt.time)
return deliveryUnresolvedLdt.toInstant(zone)
```

or

```kotlin
// move the delivery to the same time of the next workday
val daysToAdd = if (deliveryPlannedAt.plus(1, DateTimeUnit.DAY, zone).dayOfWeek == DayOfWeek.SUNDAY) {
  // skip Sunday
  2
} else {
  1
}
return deliveryPlannedAt.plus(daysToAdd, DateTimeUnit.DAY, zone)
```

or an in-between option.

The correct implementations are less obvious than the original, and `atTime` is
also error-prone without `toInstant(zone).toLocalDateTime(zone)`.

### Temporal adjusters

#### Simple case

Let's imagine we have this API:

```kotlin
fun Instant.next(dayOfWeek: DayOfWeek, zone: TimeZone): Instant
```

Here's a usage:

```kotlin
date
  .atTime(LocalTime(15, 30))
  .toInstant(zone)
  .next(DayOfWeek.MONDAY, zone)
```

What should happen if on Monday, clocks go from `14:59` to `16:00` directly?

* The user wants to find the next `Instant` at time `15:30` that happens to be
  Monday, and this Monday, there's no such thing.
  We need to skip this week entirely.
* The user wants to find the next Monday, ideally something close to `15:30`.

If we assume that the second option is correct, then `next` is implemented as
follows:

* Take the `LocalDateTime`,
* Set the date to another one,
* Resolve,
* Convert to `Instant`.

This behavior is tricky to explain without defining a resolution phase, and
also, this operation is contaminated, which means it needs a resolver.

#### Challenging (but less likely) case

Now imagine that at some point, we'll also be able to express this:

```kotlin
fun Instant.next(hour: Int, minute: Int, zone: TimeZone): Instant
```

Now let's repeat the earlier situation, but with this code:

```kotlin
date
  .atTime(LocalTime(15, 30))
  .toInstant(zone)
  .next(DayOfWeek.MONDAY, zone)
  .next(hour = 16, minute = 0, zone)
```

During the first `next`, we obtain time `16:30`. During the second `next`, we'll
go directly to Tuesday, as the second operation won't know that the original
`16:30` is an artifact of shifting `15:30`.

#### Bureaucratic matters

ISO 8601 defines a duration `P1M1D1H` this way:

"duration of one calendar month, duration of one calendar day, duration of
(60 minutes = 60 * 60 seconds as defined in the International System of Units)"

A calendar month is defined as a "specific number of calendar days."
It seems like the correct algorithm for adding
`DateTimePeriod(months = m, days = d, nanoseconds = n)` is:

* Calculate the amount of calendar days to add by combining the days contained
  in `m` months and `d`.
* Add the required number of calendar days.
* Resolve.
* Advance physical time by `n` nanoseconds.

The algorithm as we implemented it has another resolution phase between adding
`m` months and `d` days. If `currentDateTime + m.months` is in a time gap,
this gap will get skipped, and
`currentDateTime + m.months + d.days` will end at a different `LocalDateTime`
than it would if `m.months` and `d.days` were added in one step.

Conclusions
-----------

If we stick resolvers to every contaminated operation, chaining calendar-space
operations on `Instant` will become unwieldly, but if we don't, they will go
through more resolution phases than needed, possibly corrupting the results.

Proposal
--------

* Deprecate `DateTimeUnit.DateBased` arithmetic on `Instant`.
* Allow `Instant.plus(DateTimePeriod)`, but tweak it so that months and days
  are added as a single operation, likely using the data structure below.
* Provide a new data entity:

```kotlin
class DateTimeInZone(
  /**
  * The ground truth.
  */
  val nonAdjustedDateTime: LocalDateTime,
  /**
  * The implied time zone of [nonAdjustedDateTime]
  */
  val timeZone: TimeZone,
  /**
  * The mechanism of adjusting a [LocalDateTime] in cases of time gaps and overlaps.
  * By default, it may have the same behavior as `ZonedDateTime`:
  * on gap, move the time forward, on overlap, choose the earlier offset.
  */
  val resolver: LocalDateTimeResolver = LocalDateTimeResolver.DEFAULT
) {
  /**
  * The corrected value of [LocalDateTime].
  */
  val localDateTime: LocalDateTime
    get() = resolver.adjustLocalDateTime(nonAdjustedDateTime, timeZone)

  val instant: Instant
    get() = localDateTime.toInstant(nonAdjustedDateTime, timeZone, resolver)

  /**
  * Add the given amount of [unit] to this [DateTimeInZone].
  * Only date-based units are supported.
  * The operation will be performed directly on [nonAdjustedDateTime], ignoring the time zone.
  */
  fun plus(value: Int, unit: DateTimeUnit.DateBased): DateTimeInZone =
    DateTimeInZone(
      nonAdjustedDateTime.date.plus(value, unit).atTime(nonAdjustedDateTime.time),
      timeZone,
      resolver
    )
}
```

### Why this fixes our problems

#### Pattern: roundtrip

```kotlin
instant.plus(1, DateTimeUnit.DAY, zone1).toLocalDateTime(zone2)
```

becomes

```kotlin
instant.atZone(zone).plus(1, DateTimeUnit.DAY).localDateTime
```

* No repetition,
* No race conditions,
* No extraneous conversions.

```kotlin
localDateTime.toInstant(zone).plus(1, DateTimeUnit.DAY, zone).toLocalDateTime(zone)
```

becomes

```kotlin
localDateTime.atZone(zone).plus(1, DateTimeUnit.DAY).localDateTime
```

* The only resolution that happens is at the end
* The concept of physical time is not used in this purely calendar-space
  calculation.

#### Example: special-casing days

```kotlin
// move the delivery to the same time of the next workday
val tomorrow = deliveryPlannedAt.plus(1, DateTimeUnit.DAY, zone)
return if (tomorrow.dayOfWeek == DayOfWeek.SUNDAY) {
  // skip SUNDAY
  tomorrow.plus(1, DateTimeUnit.DAY, zone)
} else {
  tomorrow
}
```

becomes

```kotlin
// move the delivery to the same time of the next workday
val tomorrow = deliveryPlannedAt.atZone(zone).plus(1, DateTimeUnit.DAY)
return if (tomorrow.nonAdjustedDateTime.dayOfWeek == DayOfWeek.SUNDAY) {
  // skip SUNDAY
  tomorrow.plus(1, DateTimeUnit.DAY)
} else {
  tomorrow
}.instant
```

The end result is not shifted. Also, by using `nonAdjustedDateTime`, we can even
guard against dates changing due to DST transitions near midnights.

#### Temporal adjusters

```kotlin
date
  .atTime(LocalTime(15, 30))
  .toInstant(zone)
  .next(DayOfWeek.MONDAY, zone)
```

becomes

```kotlin
date
  .atTime(LocalTime(15, 30))
  .atZone(zone)
  .next(DayOfWeek.MONDAY)
  .instant
```

The resolution phase has its clear place, and it's easy to internalize that
`easy` does no resolution on its own, ensuring predictable behavior.

```kotlin
date
  .atTime(LocalTime(15, 30))
  .toInstant(zone)
  .next(DayOfWeek.MONDAY, zone)
  .next(hour = 16, minute = 0, zone)
```

becomes

```kotlin
date
  .atTime(LocalTime(15, 30))
  .atZone(zone)
  .next(DayOfWeek.MONDAY)
  .next(hour = 16, minute = 0)
  .instant
```

Since `next` won't do any resolution on its own, the adjustments will be applied
to the user intent, not to the actual clock behavior, and the resolver will only
ensure that the final result is sensible.

#### Bureaucratic matters

ISO 8601 treats all date-based operations uniformly as some number of calendar
days. Any sequence of operations on `DateTimeInZone` ensures this uniformity.
