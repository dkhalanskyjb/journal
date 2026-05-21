Temporal resolvers
==================

Overview
--------

See <https://stackoverflow.com/tags/dst/info>.

A time zone is a mapping from `Instant` to `LocalDateTime`.
The mapping is, generally, not invertible:

* A time overlap (moving clocks backwards) can make several `Instant` values
  map to the same `LocalDateTime`.
* A time gap (moving clocks forward) can skip some `LocalDateTime` values,
  so that no `Instant` corresponds to them.

There is often a need to *some* guess as to what a `LocalDateTime` means.
"At 2:30 AM tomorrow" can mean different things depending on the use case and
whether the clocks were moved, and a *temporal resolver* describes the logic in
unclear cases.

Resolver specifics
------------------

We'll call a "temporal resolver" a `(LocalDateTime) -> Instant` function with
the following properties:

- If only a single `Instant` maps to the given `LocalDateTime`,
  that `Instant` is returned.
- If many `Instant` values map to the given `LocalDateTime`,
  one of them is returned.

Prior art
---------

### Java

(From <https://docs.oracle.com/javase/8/docs/api/java/time/ZonedDateTime.html>):

```
For Gaps, the general strategy is that if the local date-time falls in the
middle of a Gap, then the resulting zoned date-time will have a local date-time
shifted forwards by the length of the Gap, resulting in a date-time in the later
offset, typically "summer" time.

For Overlaps, the general strategy is that if the local date-time falls in the
middle of an Overlap, then the previous offset will be retained.
If there is no previous offset, or the previous offset is invalid,
then the earlier offset is used, typically "summer" time.
Two additional methods, withEarlierOffsetAtOverlap() and
withLaterOffsetAtOverlap(), help manage the case of an overlap.
```

* <https://grep.app/search?q=withEarlierOffsetAtOverlap>
* <https://grep.app/search?q=withLaterOffsetAtOverlap>

One can also query the timezone rules and obtain the transition:
<https://docs.oracle.com/javase/8/docs/api/java/time/zone/ZoneRules.html#getTransition-java.time.LocalDateTime->
If there is no transition,
<https://docs.oracle.com/javase/8/docs/api/java/time/zone/ZoneRules.html#getOffset-java.time.LocalDateTime->
is safe to use.

An alternative is to use
<https://docs.oracle.com/javase/8/docs/api/java/time/zone/ZoneRules.html#getValidOffsets-java.time.LocalDateTime->.
This makes it easy to deal with overlaps and regular times, but doesn't
provide any useful information for non-existent clock readings.
Easily searchable: <https://grep.app/search?f.lang=Java&case=true&regexp=true&q=%5C.getValidOffsets>

### Temporal.JS

(From <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Temporal/ZonedDateTime#ambiguity_and_gaps_from_local_time_to_utc_time>)

```
When constructing a ZonedDateTime from a local time, the behavior for ambiguity
and gaps is configurable via the disambiguation option:

* `earlier`: If there are two possible instants, choose the earlier one.
  If there is a gap, go back by the gap duration.
* `later` If there are two possible instants, choose the later one.
  If there is a gap, go forward by the gap duration.
* `compatible` (default):
  Same behavior as `Date`: use later for gaps and earlier for ambiguities.
* `reject`: Throw a RangeError whenever there is an ambiguity or a gap.
```

(Note: `compatible` is also the same as Java's default).

(From
<https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Temporal/ZonedDateTime/withPlainTime#plaintime>,
<https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Temporal/ZonedDateTime/round#ambiguity_after_rounding>,
<https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Temporal/ZonedDateTime/add#description>)

Datetime arithmetic almost always uses the `compatible` mode.

The behavior for overlaps is overridden by sticking to the "preferred" UTC
offset whenever one is known:
<https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Temporal/ZonedDateTime#offset_ambiguity>

<https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Temporal/ZonedDateTime/with#disambiguation>
is the exception: this direct manipulation of `ZonedDateTime` does allow
overriding the mode.

### C++ std::chrono

<https://www.en.cppreference.com/w/cpp/chrono/choose.html> allows choosing one
of three strategies for *ambiguity*: in Temporal.JS terms, those are
`earlier`, `later`, and `reject`.
For non-existent clock readings, an exception is always thrown.

* (`later`) <https://grep.app/search?q=choose%3A%3Alatest>: 49 hits
* (`earlier`) <https://grep.app/search?q=choose%3A%3Aearliest>: 56 hits
* Not passing a `choose` object leads to an exception in the uncommon scenarios.
  Tough to look for.
* <https://grep.app/search?q=nonexistent_local_time>: no one's catching the
  non-existent-time exception.

In addition, manual control is available via
<https://www.en.cppreference.com/w/cpp/chrono/time_zone/get_info.html>,
which returns a <https://www.en.cppreference.com/w/cpp/chrono/local_info.html>
for the given clock reading:

* A tag saying whether the time is ambiguous, non-existent, or regular.
* The offset information before the transition.
* The offset information after the transition.

The offset information includes
(<https://www.en.cppreference.com/w/cpp/chrono/sys_info.html>):

* The times the given UTC offset comes into and from effect.
* The UTC offset.
* The deviation from the standard time
  (usually, 1 hour during DST, 0 otherwise).
* The string abbreviation (like `CEST`).

### Rust's `chrono` crate

<https://docs.rs/chrono/latest/chrono/offset/enum.LocalResult.html> is returned
from ambiguous timezone operations
(<https://docs.rs/chrono/latest/chrono/offset/trait.TimeZone.html>, note:
`MappedLocalTime = LocalResult`).

No helpful information is returned for gaps.

### Rust's `jiff` crate

<https://docs.rs/jiff/latest/jiff/tz/enum.Disambiguation.html#variants> are the
same as for Temporal.JS.

Alternatively, it's possible to request the description of
the offsets before and after the transition:
<https://docs.rs/jiff/latest/jiff/tz/enum.AmbiguousOffset.html>

### Python

Stores a `fold` attribute in the `datetime`. It's either 0 or 1, depending on
whether the later date is chosen.
Detect overlaps and gaps by setting `fold` to 0 and 1 and checking the
`timestamp()`.

### C#

<https://learn.microsoft.com/en-us/dotnet/api/system.timezoneinfo.converttimetoutc>

> If dateTime corresponds to an ambiguous local time,
> this method assumes that it is **standard local time**.
> If dateTime corresponds to an invalid local time,
> the method throws an ArgumentException.

### Go

<https://pkg.go.dev/time#Date>

> `Date` returns a time that is correct in one of the two zones involved in
> the transition, but it does not guarantee which.

Scenarios
---------

Based on Rust `chrono`'s mandatory manual choice of strategies.

* (29) choose the earliest moment on overlap, raise an error for a gap.
  The use case: parse a date from a config,
  notify the user about a misconfiguration.
* (16) raise or return errors for non-regular times.
* (3) a homegrown `compatible` strategy.
* (3) define custom `earliest`/`latest`/`error` strategies for overlaps,
  `error` for gaps.
* (3) set `earliest` for overlaps, and for gaps, find the first valid moment
  after the gap. Possible use case: running the scheduled task as early as
  possible.
* (2) a homegrown `later` strategy.
* (2) for overlaps, save both; skip gaps.
  The use case: iterate over all moments with the given local time.
* (1) Python interpreter, recreating the Python approach.
* (1) for overlaps, works with both; on gaps, raises an error.
  The use case: formatting the full knowledge about the date.
* (1) chooses the latest moment on overlap, raises an erros for a gap.
* (1) uses the original UTC offset on overlaps, `later` strategy for gaps.
* (1) on an overlap, take the earliest, but on a gap, take the current moment.
* (1) on an overlap, take the earliest, but on a gap,
  treat the date as if it was in UTC time zone.
* (1) on an overlap, work with the earliest, but on a gap, do nothing.
* (1) on an overlap, work with the earliest, but on a gap, stick to
  non-timezone-aware objects.
* (1)
  a homegrown `later` strategy, with the preference for the original UTC offset
  on overlaps.

### Takeaways

1. Given the freedom, people prefer to use the simplest resolution
   strategy available to them.
2. The long tail of thought-out use cases does not fit neatly into a set of
   predefined resolvers.
3. On overlaps, the most preferred strategy by far is to take the earlier
   occurrence.
4. On gaps, the most preferred strategy by far is to raise an error about
   broken data
   (note: no arithmetics involved, so dates are mostly arriving externally!).

Aside: internal resolution
--------------------------

Some `kotlinx-datetime` functions call the resolvers (for now, implicitly) in
their bodies:

```kotlin
public fun Instant.periodUntil(other: Instant, timeZone: TimeZone): DateTimePeriod {
    val initialOffset = offsetIn(timeZone)
    val initialLdt = toLocalDateTimeFailing(initialOffset)
    val otherLdt = other.toLocalDateTimeFailing(timeZone)
    // THIS IS A RESOLVER CALL:
    val timeAfterAddingDate = localDateTimeToInstant(otherLdt.date.atTime(initialLdt.time), timeZone, preferred = initialOffset)
    val delta = when {
        other > this && timeAfterAddingDate > other -> -1
        other < this && timeAfterAddingDate < other -> 1
        else -> 0
    }
    val endDate = otherLdt.date.plus(delta, DateTimeUnit.DAY)
    val unresolvedLdtWithDays = endDate.atTime(initialLdt.time)
    // THIS IS A RESOLVER CALL:
    val newInstant = localDateTimeToInstant(unresolvedLdtWithDays, timeZone, preferred = initialOffset)
    val nanoseconds = newInstant.until(other, DateTimeUnit.NANOSECOND) // |otherLdt - thisLdt| < 24h
    val datePeriod = endDate - initialLdt.date
    return buildDateTimePeriod(datePeriod.totalMonths, datePeriod.days, nanoseconds)
}

public fun Instant.until(other: Instant, unit: DateTimeUnit, timeZone: TimeZone): Long =
    when (unit) {
        is DateTimeUnit.DateBased -> {
            val start = toLocalDateTimeFailing(timeZone)
            val end = other.toLocalDateTimeFailing(timeZone)
            // THIS IS A RESOLVER CALL:
            val timeAfterAddingDate =
                localDateTimeToInstant(end.date.atTime(start.time), timeZone, preferred = this.offsetIn(timeZone))
            val delta = when {
                other > this && timeAfterAddingDate > other -> -1
                other < this && timeAfterAddingDate < other -> 1
                else -> 0
            }
            start.date.until(end.date.plus(delta, DateTimeUnit.DAY), unit)
        }
        is DateTimeUnit.TimeBased -> {
            check(timeZone); other.check(timeZone)
            until(other, unit)
        }
    }

public fun Instant.plus(period: DateTimePeriod, timeZone: TimeZone): Instant = try {
    with(period) {
        val initialOffset = offsetIn(timeZone)
        val ldtPlusDate = toLocalDateTimeFailing(initialOffset)
            .run { if (totalMonths != 0L) { plus(totalMonths, DateTimeUnit.MONTH) } else { this } }
            .run { if (days != 0) { this.plus(days, DateTimeUnit.DAY) } else { this } }
        // THIS IS A RESOLVER CALL:
        localDateTimeToInstant(ldtPlusDate, timeZone, preferred = initialOffset)
            .run { if (totalNanoseconds != 0L) plus(totalNanoseconds.nanoseconds).check(timeZone) else this }
    }.check(timeZone)
} catch (e: ArithmeticException) {
    throw DateTimeArithmeticException("Arithmetic overflow when adding CalendarPeriod to an Instant", e)
} catch (e: IllegalArgumentException) {
    throw DateTimeArithmeticException("Boundaries of Instant exceeded when adding CalendarPeriod", e)
}
```

API proposal
------------

To handle the long tail of the use cases:

```kotlin
sealed interface LocalDateTimeOffsetInfo {
    class Regular(val offset: UtcOffset): LocalDateTimeOffsetInfo

    sealed interface Irregular: LocalDateTimeOffsetInfo {
        val transitionStart: Instant,
        val offsetBefore: UtcOffset
        val offsetAfter: UtcOffset
    }

    class Gap(
        override val transitionStart: Instant,
        override val offsetBefore: UtcOffset,
        override val offsetAfter: UtcOffset
    ): Irregular

    class Overlap(
        override val transitionStart: Instant,
        override val offsetBefore: UtcOffset,
        override val offsetAfter: UtcOffset
    ): Irregular
}

fun TimeZone.infoAt(dateTime: LocalDateTime): LocalDateTimeOffsetInfo
```

Unfortunately, because of the internal resolution, this *isn't enough*.

```kotlin
interface InstantResolver {
    fun resolve(
        dateTime: LocalDateTime, // for pretty error diagnostics
        offsetInfo: LocalDateTimeOffsetInfo.Irregular,
        preferredOffset: UtcOffset?,
    ): UtcOffset

    object Compatibility: InstantResolver // used in arithmetics
    object EarliestValid: InstantResolver // used in atStartOfDay
    object Failing: InstantResolver // used in atStartOfDay
}
```

## Preferred offsets

There's an extra piece of information we can often provide to the resolver:

```kotlin
fun resolve(
    dateTime: LocalDateTime,
    offsetInfo: LocalDateTimeOffsetInfo.Irregular,
    originalOffset: UtcOffset?,
    originalDateTime: LocalDateTime?,
): UtcOffset
```

Use case: when landing in an overlap, prefer the original offset.
This allows avoiding the discontinuity on the offset boundary for as long as
possible.

Example:
- On `03:00`, the clocks are shifted back one hour, to `02:00`.
- `01:00` + `90.minutes` = `02:30`,
  interpreted as the *first* `02:30`. (inherent to `Instant`)
- `04:00` - `90.minutes` = `02:30`,
  interpreted as the *second* `02:30`. (inherent to `Instant`)
- `02:30` one day earlier `+ 1 day` = `02:30`.
  We want to resolve it to the *first* `02:30`.
- `02:30` one day later `- 1 day` = `02:30`.
  We want to resolve it to the *second* `02:30`.

There are two equivalent ways to formulate this:

1. I want to keep the original offset when adding or subtracting one day.
2. When moving forward in time, I want to use the earlier offset,
   and when moving backward, I want to use the later offset.

The second approach generalizes better to cases of long jumps.
For example, `02:30` 9 months before an overlap + `9 months`
will resolve to the earlier offset with the approach (2),
but to the later offset with the approach (1).

This boils down to this choice for `ZonedDateTime` arithmetics:

```kotlin
class UnresolvedZonedDateTime(
    val rawLocalDateTime: LocalDateTime,
    val timeZone: TimeZone,
    val preferredOffset: UtcOffset? = null,
)

// vs.

class UnresolvedZonedDateTime(
    val rawLocalDateTime: LocalDateTime,
    val timeZone: TimeZone,
    val lastResolved: ZonedDateTime? = null,
)
```

Unfortunately, `enum Direction` is not enough,
`preferredOffset` and `originalDateTime` are both needed anyway.
The reason is the arithmetics happening while *inside* the overlap.
For example, getting from `02:30` to `02:45` is a forward movement both before
and after the clock shift, so to resolve the `02:45`,
the offset comes into play.

The `Compatibility` resolver could be modified to take the direction into
account.

# Appendix B: classification of use cases with links to code

* <https://github.com/pola-rs/polars/blob/79a5333624a25b09be68e07de75a082a99ac8ed1/crates/polars-arrow/src/legacy/kernels/time.rs>
  <https://github.com/cot-rs/cot/blob/8a0eba997ba2908cea70f05063f23584ddf35a1a/cot/src/form/fields/chrono.rs>
  <https://github.com/shunsock/timezone_translator/blob/154a00b29bf56f56da0f5df3d33116413ec5c6ad/src/translator.rs>
  defines `earliest`/`latest`/`error` strategies for overlaps,
  `error` for gaps.
* <https://github.com/nushell/nushell/blob/b36b7eea26aed9e7a4b1b77cb721a03f3ca10359/crates/nu-command/src/date/from_human.rs>
  <https://github.com/aws/aws-lambda-rust-runtime/blob/6f305bb00f3cc613fd82f88d2f2200e8089e4385/lambda-events/src/custom_serde/float_unix_epoch.rs>
  <https://github.com/nearai/ironclaw/blob/2784cef4d797cc8a36791010829c178b768a32b1/src/tools/builtin/time.rs>
  <https://github.com/XAMPPRocky/octocrab/blob/f33fbcf701c824aaf43fd0f439cab30865897364/src/models/date_serde.rs>
  <https://github.com/fmeringdal/rust-rrule/blob/1c3420ede20e6190040804121e1b5e1eddac6493/rrule/src/parser/datetime.rs>
  <https://github.com/cortex/ripasso/blob/d5e2567a8806ea219725f7e629660fb3e9d3ab98/crates/ripasso/src/error.rs>
  <https://github.com/getdozer/dozer/blob/8b0b4cc021db089722cb3d48dbe915e8e9c532ec/dozer-ingestion/postgres/src/helper.rs>
  <https://github.com/jasonish/evebox/blob/ba55c6ca51c1477e3e17147bfc92e39b32b9bd7e/src/elastic/retention.rs>
  <https://github.com/dbt-labs/dbt-fusion/blob/9ad9330160a5c314afd34cfb6bc4fa8d2a7af4ee/crates/dbt-jinja/minijinja-contrib/src/modules/pytz.rs>
  <https://github.com/dashpay/platform-tui/blob/3a3521e60ca562f4b02a00e0a832eea9726e8fbd/src/backend/platform_info.rs>
  <https://github.com/KumoCorp/kumomta/blob/44273ed1d151048bd527e56afe1775c67be70f13/crates/mod-time/src/lib.rs>
  <https://github.com/getdozer/dozer/blob/8b0b4cc021db089722cb3d48dbe915e8e9c532ec/dozer-types/src/types/field.rs>
  <https://github.com/danielschemmel/build-info/blob/36b0eeaf0f7a7c8e06dcaa940e6fce86cd638c52/build-info-build/src/build_script_options/timestamp.rs>
  <https://github.com/polyphony-chat/chorus/blob/eecfb4fd4574e6675d53eed703c6239176caee33/src/types/utils/serde.rs>
  <https://github.com/amazon-ion/ion-rust/blob/d18d2bcd39a3eb37ed2c049cd389e907c85aaba6/src/types/timestamp.rs>
  <https://github.com/JettChenT/timeblok/blob/db3a99866b0d51ab44834bea30a3e661247ca191/timeblok-compiler/src/converter.rs>
  <https://github.com/georust/gdal/blob/500753686b27c982cfd20ad4ab459b0cfafdba79/src/vector/feature.rs>
  raises or returns errors for non-regular times.
* <https://github.com/cube-js/cube/blob/eec59a2e4736d4b3a973abe98028fbfd3af54262/rust/cubesqlplanner/cubesqlplanner/src/planner/time_dimension/date_time_helper.rs>
  <https://github.com/Hexagon/croner-rust/blob/612c65f4280c5c7aed36af0f83db8dd157c53cdd/src/lib.rs> (for fixed-time jobs)
  <https://github.com/nbari/cron-parser/blob/e55de652bec8646e9479b94de954d9cba0ee5f1d/src/lib.rs>
  sets `earliest` for overlaps, and for gaps, finds the first valid moment
  after the gap. Possible use case: running the scheduled task as early as
  possible.
* <https://github.com/Dicklesworthstone/beads_rust/blob/0e805c45636fafab8efcd892ff46bb4228d8f9ca/src/util/time.rs>
  <https://github.com/BurnOutTrader/fund-forge/blob/e9bb54178f94a42abba44e2e85f713d063cbe0f5/ff_standard_lib/src/helpers/converters.rs>
  <https://github.com/mag123c/toktrack/blob/6242952436bdf8c22c506651881286f44605c69e/src/services/data_loader.rs>
  a homegrown `compatible` strategy.
* <https://github.com/PyO3/pyo3/blob/7c4574eedc1b7180d6bd7e3dcc4c6a9b77a6bdc7/src/conversions/chrono.rs>
  Python interpreter, recreating the Python approach.
* <https://github.com/mandiant/macos-UnifiedLogs/blob/6603050e4660a0449ea132662aed95f77eaa0398/src/decoders/time.rs>
  for overlaps, works with both; on gaps, raises an error.
  The use case: formatting the full knowledge about the date.
* <https://github.com/zslayton/cron/blob/e3dfb1a4e0140bff7d175f01e4d5c38aa5ff79aa/src/schedule.rs>
  <https://github.com/Hexagon/croner-rust/blob/612c65f4280c5c7aed36af0f83db8dd157c53cdd/src/lib.rs> (for periodic tasks)
  for overlaps, saves both; skips gaps.
  The use case: iterate over all moments with the given local time.
* <https://github.com/nushell/nushell/blob/b36b7eea26aed9e7a4b1b77cb721a03f3ca10359/crates/nu-command/src/date/utils.rs>
  <https://github.com/tensorbase/tensorbase/blob/7b071a88a175da64e5c8cca7f1710940fe9b75ab/crates/arrow/src/compute/kernels/cast_utils.rs>
  <https://github.com/nushell/nushell/blob/b36b7eea26aed9e7a4b1b77cb721a03f3ca10359/crates/nu-command/src/strings/detect_type.rs>
  <https://github.com/afadil/wealthfolio/blob/6584402d0389a643653b6b7f3cd3bb3624c6258d/crates/core/src/utils/time_utils.rs>
  <https://github.com/nearai/ironclaw/blob/2784cef4d797cc8a36791010829c178b768a32b1/src/tools/builtin/time.rs>
  <https://github.com/Nukesor/pueue/blob/8b9d6fef81996a48db8087a7e92db1cce6cbc0ba/pueue/src/client/commands/state/mod.rs> (note: start of day)
  <https://github.com/gorules/zen/blob/9c69d22018eb612afec548adf588a4d31c1aeece/core/expression/src/vm/date/mod.rs>
  <https://github.com/nbari/cron-parser/blob/e55de652bec8646e9479b94de954d9cba0ee5f1d/src/lib.rs>
  <https://github.com/rmqtt/rmqtt/blob/c249a01446477c957e3c0bf92df6fc007253dd4f/rmqtt-utils/src/lib.rs>
  <https://github.com/GreptimeTeam/greptimedb/blob/dc98e0215bd19312f136dfecd5f3d64fc26023b7/src/common/time/src/timestamp.rs>
  <https://github.com/gendx/rust-interning/blob/e3fdd55c485c177e225b30acbcce2408d816b167/src/schema/optimized.rs>
  <https://github.com/trypsynth/fedra/blob/693ea11f09512fc35c5f682a84f4227328ae2ac9/src/ui/dialogs.rs>
  <https://github.com/gulbanana/gg/blob/bd92b11cebf52529696d93b1f57d238e463618fa/src/messages/mod.rs>
  <https://github.com/Dicklesworthstone/xf/blob/715318ef3ec4955bd278326c05fe2024c67c612f/src/date_parser.rs>
  <https://github.com/abdolence/slack-morphism-rust/blob/6e30d0e05bc0fa6be4c594d3bee64e8d7f87d453/src/models/common/mod.rs>
  <https://github.com/calavera/aws-lambda-events/blob/89ce6f3e59c7a88874e3583b026b9be4d547930b/src/custom_serde/float_unix_epoch.rs>
  <https://github.com/apache/arrow-rs/blob/a8fe8b32045f32bc59794b9ad919ba08d22ef514/arrow-array/src/types.rs>
  <https://github.com/munew/cloudflare-turnstile-solver/blob/ba6cbd2faa8471394ab7e016a12ef52e6ebcc52e/src/solver/utils.rs>
  <https://github.com/egrieco/statical/blob/0db3c3196393da547b60b772b1fde4588f8c77f8/src/model/event.rs>
  <https://github.com/egrieco/statical/blob/0db3c3196393da547b60b772b1fde4588f8c77f8/src/views/month_view.rs>
  <https://github.com/terhechte/postsack/blob/1dd907f720c4c6ad494a12926268b312f546cfbc/ps-importer/src/formats/shared/parse.rs>
  <https://github.com/always-further/nono/blob/2735685fea29ebc3e69a55c99cd1643e287fa39f/crates/nono-cli/src/rollback_commands.rs>
  <https://github.com/ArkForgeLabs/Astra/blob/f2ba68a321b3d8d0e1e50487403714e0287cac7f/src/components/datetime.rs>
  <https://github.com/sjtug/mirror-clone/blob/0cd44507d9c9d49f80456137cf4008a5b40c2ec7/src/rsync.rs>
  <https://github.com/zaari/nmea-parser/blob/bc0a89f01747fd7bf734a9f240755a08b4b76722/src/util.rs>
  <https://github.com/axumrs/axum-rs/blob/98fd2f241c69d42afed30ebcb8d3b54b6801c2b1/src/utils/dt.rs>
  <https://github.com/ellenhp/solari/blob/33faa2f434f6c1cb75fa630845bce0d16c291f0d/solari/src/timetable/in_memory.rs> (note: the choice is documented to be arbitrary)
  <https://github.com/drasi-project/drasi-core/blob/8ee788ca256659b5cf8c10e9c45d856de47be3dd/core/src/evaluation/variable_value/zoned_datetime.rs>
  <https://github.com/euzu/tuliprox/blob/2bb74f2c2bf22dd94c77c71a19262a216ce08bd1/backend/src/api/panel_api.rs>
  chooses the earliest moment on overlap, raises an error for a gap.
  The use case: parse a date from a config,
  notify the user about a misconfiguration.
* <https://github.com/SARDONYX-sard/bluetooth-battery-monitor/blob/a93d80d0ba459872472bf129aabf3fa1dfde5034/crates/bluetooth/src/device/windows/device_info/buffer/mod.rs>
  chooses the latest moment on overlap, raises an erros for a gap.
* <https://github.com/pathwaycom/pathway/blob/75be06300d664c6e17d577e1af9b0dd814db39b0/src/engine/time.rs>
  <https://github.com/risingwavelabs/risingwave/blob/046bd30eb3c40872387392fc188aa0556b055f41/src/expr/impl/src/scalar/timestamptz.rs>
  a homegrown `later` strategy.
* <https://github.com/pola-rs/polars/blob/79a5333624a25b09be68e07de75a082a99ac8ed1/crates/polars-time/src/windows/duration.rs>
  following RFC 5545 (TODO).
* <https://github.com/qxcnm/Codex-Manager/blob/d25ac1d9bbe79cb6889c3364bbf695125a5f5119/crates/service/src/requestlog/requestlog_today_summary.rs>
  on an overlap, take the earliest, but on a gap, take the current moment.
* <https://github.com/mcthesw/game-save-manager/blob/e3342048617cb742296c9408b07c2809ef657b09/src-tauri/src/backup/archive/timestamp.rs>
  on an overlap, take the earliest, but on a gap,
  treat the date as if it was in UTC time zone.
* <https://github.com/stalwartlabs/mail-parser/blob/ebd2b179df0c6adf309abc7d743cad8435a07623/src/parsers/fields/date.rs>
  on an overlap, work with the earliest, but on a gap, do nothing.
* <https://github.com/CSML-by-Clevy/csml-engine/blob/d0f80bbcef96aa53a49dce5fbd130d43a82360d6/csml_interpreter/src/data/primitive/object.rs>
  on an overlap, work with the earliest, but on a gap, stick to
  non-timezone-aware objects.
* <https://github.com/apache/datafusion/blob/84a79e1be5db1736d9bda3e5db7bcca6ed948f26/datafusion/functions/src/datetime/date_trunc.rs>
  a homegrown `later` strategy, with the preference for the original UTC offset
  on overlaps.
