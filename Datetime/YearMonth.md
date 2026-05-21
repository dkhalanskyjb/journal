YearMonth
=========

Prior art
---------

### Java

There is a `java.time.YearMonth` class for representing a year and a month:
<https://docs.oracle.com/en%2Fjava%2Fjavase%2F21%2Fdocs%2Fapi%2F%2F/java.base/java/time/YearMonth.html>

Stack Overflow discussions related to it:

* Using as data storage. Dozens of questions mentioning `YearMonth` (but not regarding its usage).
* The number of days in a month in a given year:
  - <https://stackoverflow.com/questions/8940438/number-of-days-in-particular-month-of-particular-year/8940484#8940484>
* First and last day of the month:
  - <https://stackoverflow.com/questions/22223786/get-first-and-last-day-of-month-using-threeten-localdate/49895903#49895903>
  - <https://stackoverflow.com/questions/14475489/java-get-the-first-date-and-last-date-of-given-month-and-given-year/32322588#32322588>
  - <https://stackoverflow.com/questions/10828398/how-to-get-the-first-date-and-last-date-of-the-previous-month-java>
  - <https://stackoverflow.com/questions/52561760/get-the-first-day-and-the-last-day-of-month-from-an-instant/52561808#52561808>
* Or just the last day:
  - <https://stackoverflow.com/questions/13624442/getting-last-day-of-the-month-in-a-given-string-date/54080554#54080554>
  - <https://stackoverflow.com/questions/9397203/how-to-calculate-the-last-day-of-the-month/56873525#56873525>
  - <https://stackoverflow.com/questions/26136000/how-to-get-the-last-day-of-a-month-in-a-yearmonth-object/26138372?r=SearchResults&s=83%7C0.0000#26138372>
* The number of months mentioned in a range between two dates:
  - <https://stackoverflow.com/questions/48950145/java-8-calculate-months-between-two-dates/48951547#48951547>
  - <https://stackoverflow.com/questions/1086396/java-date-month-difference/34811261#34811261>
* Parsing years + months:
  - <https://stackoverflow.com/questions/23800477/java-8-time-api-how-to-parse-string-of-format-mm-yyyy-to-localdate/23800649#23800649>
  - <https://stackoverflow.com/questions/34061759/why-my-patternyyyymm-cannot-parse-with-datetimeformatter-java-8/34061936#34061936>
  - <https://stackoverflow.com/questions/35133540/java-8-how-to-parse-expiration-date-of-debit-card/35133637#35133637>
* `date.yearMonth == otherDate.yearMonth`:
  - <https://stackoverflow.com/questions/2937086/how-to-get-the-first-day-of-the-current-week-and-month/33591149#33591149>
* Last working day of the previous month:
  - <https://stackoverflow.com/questions/46528017/last-working-day-of-previous-month-with-localdate/46528547#46528547>
* Checking if a date lies in a specific year-month:
  - <https://stackoverflow.com/questions/44616603/what-is-the-easiest-way-to-find-whether-a-particular-localdate-falls-within-a-ye/44616702#44616702>
* `someYearMonth >= date.yearMonth`
  - <https://stackoverflow.com/questions/63267547/how-to-find-yearmonth-is-equal-or-after-current-month/63267578#63267578>
* Grouping data by year-month:
  - <https://stackoverflow.com/questions/43611552/java-stream-grouping-and-counting-by-multiple-fields/43612353#43612353>

List of interesting methods and their use cases:

* `atDay(int dayOfMonth)`:
  - `atDay(1)` because there is no "at the start of the month".
  - `repeat(lengthOfMonth()) { f(atDay(it)) }` for iterating over date ranges.
* `atEndOfMonth()`:
  - Deadlines.
  - Setting the boundary for date ranges.
* `withYear`/`withMonth`:
  - Updating the components whenever the user updates the fields in a form
    <https://github.com/palexdev/MaterialFX/blob/a7ae81912f00b7cb224392601772b1b7bc6daa09/materialfx/src/main/java/io/github/palexdev/materialfx/skins/MFXDatePickerSkin.java#L300>
* `plus`/`minus`:
  - "Next"/"previous" month chosen by the end user
    <https://github.com/mihonapp/mihon/blob/f22767d863a0fa001f93f24092cd5ade87350502/app/src/main/java/mihon/feature/upcoming/components/calendar/Calendar.kt#L52-L53>
    <https://github.com/LGoodDatePicker/LGoodDatePicker/blob/e0d34f66b675e77ff5c79f1c6728cbf44564971e/Project/src/main/java/com/github/lgooddatepicker/components/CalendarPanel.java>
  - Generating random year-months in a range as `lower bound + n months`:
    <https://github.com/kotest/kotest/blob/8d0d77a6b2d053a6bb8913c0bec792e93a0f3bc5/kotest-property/src/jvmMain/kotlin/io/kotest/property/arbitrary/dates.kt>
  - "The year-months for the next 12 months":
    <https://github.com/benoitletondor/EasyBudget/blob/48742d54749ac82658c425044434d6eaa5a25e66/Android/EasyBudget/app/src/main/java/com/benoitletondor/easybudgetapp/helper/DateHelper.kt#L30-L50>
* `isLeapYear`: TODO
* `now`: TODO

### Python

No built-in solutions:

- <https://stackoverflow.com/questions/14425133/date-object-with-year-and-month-only>
- <https://stackoverflow.com/questions/76148971/python-datetime-with-months-and-year-only>

For data analyzing purposes, Pandas provides
[`pandas.Period`](https://pandas.pydata.org/docs/reference/api/pandas.Period.html)
that can just be `year`-`month`. Specifying `freq = 'M'` means the precision is
has the month granularity.

Example: <https://stackoverflow.com/questions/66755723/string-to-date-but-only-month-and-year>

Dozens of questions mention `yearmonth` in Pandas frames.

API
---

### Derivative boilerplate

```kotlin
package kotlinx.datetime

class YearMonth(val year: Int, val monthNumber: Int) : Comparable<YearMonth> {

    constructor(year: Int, month: Month): YearMonth(year, month.number)

    val month: Month get() = Month(monthNumber)

    companion object {
        fun parse(
            input: CharSequence,
            format: DateTimeFormat<YearMonth> = Formats.ISO
        ) : YearMonth

        fun Format(block: DateTimeFormatBuilder.WithYearMonth.() -> Unit)
            : DateTimeFormat<YearMonth>
    }

    object Formats {
        val ISO: DateTimeFormat<YearMonth>
    }
}


package kotlinx.datetime.format

interface DateTimeFormatBuilder {

    interface WithYearMonth : DateTimeFormatBuilder {
        fun year(padding: Padding = Padding.ZERO)

        fun yearTwoDigits(baseYear: Int)

        fun monthNumber(padding: Padding = Padding.ZERO)

        fun monthName(names: MonthNames)

        fun yearMonth(format: DateTimeFormat<YearMonth>)
    }

    interface WithDate : WithYearMonth {
        fun dayOfMonth(padding: Padding = Padding.ZERO)

        fun dayOfWeek(names: DayOfWeekNames)

        fun date(format: DateTimeFormat<LocalDate>)
    }
}

class DateTimeComponents {

    fun setYearMonth(yearMonth: YearMonth)

    fun toYearMonth(): YearMonth
}
```

### Interesting part

#### Conversions and construction

```kotlin
package kotlinx.datetime

val LocalDate.yearMonth: YearMonth = YearMonth(year, monthNumber)

fun YearMonth.onDay(dayOfMonth: Int): LocalDate = LocalDate(year, monthNumber)

val Month.inYear(year: Int): YearMonth = YearMonth(year, this)
```

#### `YearMonth` is a range of dates

Obtaining the first and last day of the month, iterating over all the days in
a day, and checking if the date lies in a given month, is all accomplished by
making `YearMonth` a date range:

```kotlin
class YearMonth: LocalDateRange(
    LocalDate(year, monthNumber, 1),
    LocalDate(year, monthNumber, /* last day of the month */),
)
```

Usages:

```kotlin
if (date in currentYearMonth) {
    calendarView.dayCells[date.dayOfMonth] += events[date]
}

println("Start date: ${currentYearMonth.start}")
println("End date: ${currentYearMonth.endInclusive}")
println("Start of the next month: ${currentYearMonth.endExclusive}")

for (date in currentYearMonth) {
    println(date)
}
```

#### Ranges of year-month

`YearMonthRange` is just as well-defined as `LocalDateRange`, and most usages
of `plus`/`minus` aside from `+/- 1 (month/year)` were in service of ranges.

#### `next`/`previous`, `plus`/`minus`, both?

Should be solved together with the previous point.

```kotlin
fun YearMonth.nextYear(): YearMonth
fun YearMonth.nextMonth(): YearMonth
fun YearMonth.previousYear(): YearMonth
fun YearMonth.previousMonth(): YearMonth

// no usages found with `value != +/- 1 or 12` that aren't better with ranges
fun YearMonth.plus(value: Int, unit: DateTimeUnit.MonthBased): YearMonth
fun YearMonth.minus(value: Int, unit: DateTimeUnit.MonthBased): YearMonth

// On the JVM, MAX_YEAR is +999_999_999,
// so the number of months between MIN and MAX doesn't fit into an `Int`
fun YearMonth.plus(value: Long, unit: DateTimeUnit.MonthBased): YearMonth
fun YearMonth.minus(value: Long, unit: DateTimeUnit.MonthBased): YearMonth
```

* We don't have functions like `nextDay` for `LocalDate`.
* But the number of `plusMonths(1)` is just overwhelming.

#### Number of months/years between two dates

```kotlin
// LocalDate has this. Not the full range!
operator fun YearMonth.minus(other: YearMonth): DatePeriod

// LocalDate has the equivalent operations
operator fun YearMonth.yearsUntil(other: YearMonth): Int
operator fun YearMonth.monthsUntil(other: YearMonth): Int

```

`YearQuarter` and `FixedLengthRange`
------------------------------------

Looking through Stack Overflow and grep.app, we can see that there are the same
use cases for `YearQuarter` as there are for `YearMonth`,
though far less popular:

* <https://grep.app/search?q=YearQuarter>
* <https://stackoverflow.com/questions/36493649/how-to-get-the-first-date-and-last-date-of-current-quarter-in-java-util-date>
* <https://stackoverflow.com/questions/59946969/find-next-quarter-end-date-given-previous-quarter-end-date-using-java>
* Etc.

If they are the same, what is the generalization that covers both of them?

```kotlin
// generalization of LocalDateRange represented by YearMonth
class AbstractProgression<Value>(
    val first: Value,
    val last: Value,
    val stepMultiplier: Int,
    val addStep: Value.(Int) -> Value?,
)

open class FixedLengthClosedRange<Value>(
    val start: Value,
    val tryAddFixedLengthMinusStep: Value.(Int) -> Value?,
    val tryAddStep: Value.(Int) -> Value?,
) : OpenEndRange<Value>, ClosedRange<Value>

class YearMonth(val year: Int, val monthNumber: Int) :
    FixedLengthClosedRange(
        start = LocalDate(year, monthNumber, 1),
        tryAddFixedLengthMinusStep = {
            plus(it, month.length(isLeapYear(year)))
        },
        tryAddStep = {
            plus(it, DateTimeUnit.DAY)
        }
    )

class YearQuarter(val year: Int, val quarterNumber: Int) :
    FixedLengthClosedRange(
        start = LocalDate(year, 3 * quarterNumber, 1),
        tryAddFixedLengthMinusStep = {
            with(plus(2, DateTimeUnit.MONTH)) {
                plus(it, month.length(isLeapYear(year)))
            }
        },
        tryAddStep = {
            plus(it, DateTimeUnit.DAY)
        }
    )
```
