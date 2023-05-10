Analysis of the usage of the Unicode format strings
---------------------------------------------------

This is a discussion of the directives from
<https://unicode-org.github.io/icu/userguide/format_parse/datetime/>,
based on `patterns.csv`.

The usage analytics are those of the JSR-310's implementation of these patterns.
The main results of this section are driven by the
`conflated_patterns_by_popularity.txt` file.

The discussion is only about the nature of the data formatted or parsed.
The *form* is discussed in other files. `locales.md` discusses localized
strings, and `format_strings.md` describes the considerations of the
length of the fields.

The sections are sorted by how widely used the directives in them are.

### Calendar dates

The most popular directives are those for the calendar dates:
* `d`, day-of-month numbers.
* Month:
  - `M`, month numbers or names in the genitive casus.
  - `L`, month numbers or names in the nominative casus.
* Year:
  - `y`, years-of-era + `G`, era.
  - `u`, years.

#### `y` with no era

Using `y` instead of the much less popular `u` is commonly a mistake.
There are 21738 unique files with `y` counted, but only 114 files with `G`.
The problem does not affect most people because, by default,
`DateTimeFormatter` will fill-in the current era if no other era was parsed.
The problem is only surfaced when `STRICT` parsing is enabled.
See the discussion of strictness for more details.

#### Casus of the month

```kotlin
DateTimeFormatter.ofPattern("LLLL", java.util.Locale("ru"))
  .format(java.time.LocalDate.of(2022, 7, 10)) // Июль
DateTimeFormatter.ofPattern("MMMM", java.util.Locale("ru"))
  .format(java.time.LocalDate.of(2022, 7, 10)) // июля
```

### Time of day

* `m`, minutes.
* `s`, seconds.
* `S`, fractions of a second.
* Hour of day:
  - `H`, an hour in 0..23.
  - `h` + `a`/`B`, an hour in 1..12 + an AM/PM marker.
  - `k`, an hour in 1..24.
  - `K` + `a`/`B`, an hour in 0..11 + an AM/PM marker.

#### `h` without `a`/`B`

Roughly half of the usages of `h` don't use the AM/PM marker.
A search of the usage of the most popular pattern of this sort,
`yyyy-MM-dd hh:mm:ss`:
<https://grep.app/search?q=yyyy-MM-dd%20hh%3Amm%3Ass&case=true&filter[lang][0]=Java>

Note: when `SimpleDateFormat` (the old Java API for parsing and formatting)
uses this pattern and is used for parsing, it will parse the same as with `HH`:
```kotlin
SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse("2022-01-24 14:23:58")
// Mon Jan 24 14:23:58 UTC 2022
```
So, we're only interested in the formatting facilities.

This code in Google Cloud Platform sure looks erroneous:
<https://github.com/GoogleCloudPlatform/DataflowTemplates/blob/main/v2/googlecloud-to-googlecloud/src/main/java/com/google/cloud/teleport/v2/utils/JdbcConverters.java#L85>
```kotlin
DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss.SSSSSS")
    .format(LocalDateTime.of(2022, 10, 16, 14, 35, 24, 123456789)))
// 2022-10-16 02:35:24.123456
```

Here's a case where someone obviously fixed the wrong `hh` usage:
<https://github.com/chenssy89/jutils/blob/master/src/main/java/com/JUtils/date/DateFormatUtils.java>

In fact, most of them look erroneous. One could imagine that AM/PM are
formatted separately, or that the times are unambiguous in some cases
given the problem area (e.g. work hours), but here, most usages seems
fairly general.

That said, there *are* valid use cases for using `hh` without an AM/PM marker:
for example, for representing very recent events, where the marker is obvious:
<https://github.com/NationalSecurityAgency/ghidra/blob/master/Ghidra/Framework/Generic/src/main/java/ghidra/util/DateUtils.java#L248>

Search for `h:mm`, a benign pattern using a 1..12 hour without the AM/PM marker:
<https://grep.app/search?q=%22h%3Amm%22&case=true&filter[lang][0]=Java>

#### Fractional seconds

The only proper solution is to use the `S` designator.
However, the majority of uses of the `n` designator (nanoseconds of a second)
and even the `N` designator (nanoseconds since the start of the day) are also
used in this role, certainly because of the confusion about their role.
Of those, the only valid pattern is `.nnnnnnnnn`, which will behave the same
as `.SSSSSSSSS` unless lenient parsing is enabled, in which case it's also
wrong:
```
DateTimeFormatterBuilder()
  .parseLenient()
  .appendPattern("ss.nnnnnnnnn")
  .toFormatter()
  .parse("10.123") // 10 seconds 123 nanoseconds
```

The `S` designator is not particularly good for either formatting or
parsing.

* Formatting: it doesn't allow outputting fewer digits if the rest of them are
  zeros. The number of digits that will be output depends exactly
  on the number of `S` letters. If one writes `.SSSSSSSSS` as the format and
  there are exactly `123` milliseconds, `.123000000` will be the output.
* Parsing: likewise, without actually enabling lenient parsing, there's no
  sensible option to have varying-width fractions, like the ones ISO-8601
  requires. For formatting, this is being fixed in the wild using a nasty
  trick: <https://stackoverflow.com/a/74081972>, writing
  `[.SSSSSSSSS][.SSSSSS][.SSS][.SS][.S]` (or with more options, depending on
  the expected input). Using this format string to *format* a time with, say,
  123456789 nanoseconds, will lead to this broken string:
  `.123456789.123456.123.12.1`.
  * <https://stackoverflow.com/questions/30090710/java-8-datetimeformatter-parsing-for-optional-fractional-seconds-of-varying-sign>

That said, no one seems to complain. Looks like everyone's okay with these
limitations. Possibly because of a convenient alternative way to work with
fractional seconds:
<https://grep.app/search?q=appendFraction%28ChronoField.NANO_OF_SECOND>
When formatting, it will only print trailing zeros if the minimum number of
digits requested requires that, and when parsing, it allows an arbitrary
number of digits in the specified range.

This feature is widely requested when it's not present. For example, in
Python, which for some time didn't have fractional second support:
<https://stackoverflow.com/search?q=strptime+fractional>.

#### AM/PM markers

`B` is a very recent addition and is used exceedingly rarely.

Both `a` and `B` are localized and are often the same. When not,
`B` typically represents the period of time of day: night, morning,
day, evening.

```kotlin
DateTimeFormatter.ofPattern("hh:mm:ss B", Locale("de"))
  .parse("08:32:10 abends") // 20:32:10
DateTimeFormatter.ofPattern("hh:mm:ss B", Locale("de"))
  .parse("08:32:10 morgens") // 08:32:10
DateTimeFormatter.ofPattern("h часов m минут[а][ы] B", Locale("ru"))
  .parse("8 часов 23 минуты вечера") // 20:23
```

When using `a`, it's common to want the specific strings `AM`/`PM` or `am`/`pm`.
This is a common complaint not only in Java.
* <https://stackoverflow.com/questions/38863210/how-to-make-pythons-datetime-object-show-am-and-pm-in-lowercase>

#### `k` and `K`

<https://en.wikipedia.org/wiki/24-hour_clock#Times_after_24:00>:
time of day is in some contexts overflowing to another day.
For example, if a train starts its schedule at 03:36 and ends at 00:36
the following day, this can be neatly represented as `03:36-24:36`.

However, `k` is not fully suitable for this, as the overflow does not
stop at `01:00`.

> For example, bars or clubs may advertise as being open until "30時" (i.e. 6 am).

So, who is this actually for?
A seach through the most common usage patterns of `k` and `K` didn't show
anyone using it with explicit intent:
<https://grep.app/search?current=3&q=kk%3Amm>
I would guess that if someone were to use such an exotic thing intentionally,
they would drop a comment with the rationale. Doesn't seem to be the case.
Maybe people are just using those by mistake?

This is especially likely given that `strftime` uses `%k` to denote
24-hour clocks (0..23).

Searching through Stack Overflow seems to confirm the assumption that no one
needs `k`/`K`:
<https://stackoverflow.com/search?q=DateTimeFormatter+kk>
* <https://stackoverflow.com/questions/50938458/why-does-java-8-zoneddatetime-think-2401-is-a-valid-time-string-representation>
* <https://stackoverflow.com/questions/62049146/cannot-compare-string-dates-in-different-time-zone/62049773#62049773>
* <https://stackoverflow.com/questions/36184352/using-groovy-add-text-to-a-returned-date-based-on-the-time>
* <https://stackoverflow.com/questions/30754259/jdk8-unable-to-parse-localtime>
  something very odd is going on here.
* <https://stackoverflow.com/questions/69119620/simpledateformatdd-mm-yy-kkmm-showing-time-as-2430-instead-of-0030>
* <https://stackoverflow.com/questions/45744185/parsing-a-string-into-a-zoneddatetime-with-a-datetimeformatter>
* <https://stackoverflow.com/questions/17341214/difference-between-java-hhmm-and-hhmm-on-simpledateformat>

It seems like the only valid use case to use `k`/`K` is to work around someone
else having used them:
* <https://stackoverflow.com/questions/72872512/javax-net-ssl-logging-timestamp-format-has-strange-hours-value>
* <https://stackoverflow.com/questions/53477714/java-gson-date-converter-how-to-get-midnight-as-0000-am-instead-of-1200-am>

Still, these formats are not commonly encountered.

### Week-of-year date representation

There's an alternative representation of dates:
* `Y`, week-year.
* `w`, week number.
* `E` or `e`/`c`, day of the week.

#### The `Y` Directive Considered Harmful

`Y` is encountered in 609 unique files, whereas `w` is encountered only in 65
unique files. Also, most usages of `Y` are alongside months and days-of-month,
so people are certainly just confused between `y` and `Y`.

* <https://stackoverflow.com/questions/12423179/nsdateformatter-show-wrong-year>

#### Day of the week names

`E` is widely used even outside of the week-of-year date specifications, both
standalone and as part of a larger datetime description.

`E` is significantly more popular than `e` and `c`, appearing in 10 times more
unique files than `e` and `c` combined. However, the usage of `e` and `c` is
still seemingly non-negligible.

The `DateTimeFormatter` documentation states that `E` is just day of the week
whereas `e`/`c` is localized, but in general, that seems to be misleading,
as `E` is localized as well:
```kotlin
DateTimeFormatter.ofPattern("EEEE")
  .localizedBy(java.util.Locale("ru"))
  .format(java.time.LocalDate.of(2022, 7, 12)))
// вторник
```

The documentation states that `c` is the standalone version of `e`,
the same way `L` is the standalone version of `M`.
In 310bp, the difference between `E`, `e`, and `c` is solely in handling the
meaning of one-character, two-character, and five-character directives:
* `EEE` = `eee` = `ccc`.
* `EEEE` = `eeee` = `cccc`.
* `E` = `EE` = `EEE` = a short name for day of the week.
* `e` = `c` = a numeric representation of the day of the week, no padding.
* `ee` is a zero-padded numeric representation of the day of the week.
* `cc` is forbidden.
* `EEEEE` = `eeeee` != `ccccc`.
  `ccccc` outputs the ISO number, whereas `EEEEE` and `eeeee` output the
  one-letter string.

To check the differences between the formats across every locale, the following
code was used:
```kotlin
internal fun checkOutputForAllLocales(
    value: TemporalAccessor,
    onlyListDifferent: Boolean = false,
    onlyListNotThoseInRoot: Boolean = false,
    vararg formats: String
) {
    fun buildStringForLocale(locale: Locale): Pair<Boolean, String> {
        val strings = formats.map { DateTimeFormatter.ofPattern(it, locale).format(value) }
        return Pair(strings.toSet().size == 1, strings.joinToString(" | "))
    }
    val (_, rootString) = buildStringForLocale(Locale.ROOT)
    if (onlyListNotThoseInRoot)
        println("ROOT: $rootString")
    for (locale in Locale.getAvailableLocales().sortedBy { it.toLanguageTag() }) {
        val str = locale.toString()
        val (isSame, string) = buildStringForLocale(locale)
        if (onlyListNotThoseInRoot && string == rootString || onlyListDifferent && isSame) continue
        println(buildString {
            append(locale)
            repeat(40 - str.length) { append(' ') }
            append(string)
        })
    }
}
```

Example of invocation:
```kotlin
checkOutputForAllLocales(
  java.time.LocalDate.of(2022, 7, 10),
  onlyListDifferent = true,
  onlyListNotThoseInRoot = false,
  "ccccc", "eeeee", "EEEEE"
)
```

Adjusting the popularity by grouping the equal directives together, we get:
* `e` is in 64 unique files,
* `ee` is in 4 unique production files,
* `ccccc` is not used in a single production file,
* `E` is used in 777 unique files,
* `EEEE` is used in 309 unique files,
* `EEEEE` is used in 2 production files.

The numeric representation depends on whether Sunday or Monday is the first
day of the week in the given locale:
```kotlin
DateTimeFormatter.ofPattern("e", java.util.Locale("de"))
  .format(java.time.LocalDate.of(2022, 7, 10)) // 7
DateTimeFormatter.ofPattern("e", java.util.Locale("en"))
  .format(java.time.LocalDate.of(2022, 7, 10)) // 1
```

The code is at <https://github.com/ThreeTen/threetenbp/blob/973f2b7120d2c173b0181bde39ce416d1e8edfe0/src/main/java/org/threeten/bp/format/DateTimeFormatterBuilder.java#L1569-L1632>

For some reason, on <https://play.kotlinlang.org>, the output for every locale
that I've tries is always 1. Not sure what's going on. My first guess is that
the website uses some minimal distribution of the JVM with some assets cut.

People have issues with choosing the first day of the week:
* <https://stackoverflow.com/questions/46341152/datetimeformatter-weekday-seems-off-by-one/46343732#46343732>
* <https://stackoverflow.com/questions/19136226/why-does-my-gregoriancalendar-object-return-the-wrong-day-of-week/68785303#68785303>
* <https://stackoverflow.com/questions/45880644/locale-independent-year-week-date-format>

Search: <https://stackoverflow.com/search?q=DateTimeFormatter+monday+first+day>

### Zone offsets

There is a variety of directives and lengths of directives just for working with
offsets: `X`, `Z`, `x`, and `O`.

```kotlin
for (pattern in listOf(
    "x", "xx", "xxx", "xxxx", "xxxxx",
    "X", "XX", "XXX", "XXXX", "XXXXX",
    "Z", "ZZ", "ZZZ", "ZZZZ", "ZZZZZ",
    "O", "OOOO",
)) {
    println("${pattern}: ${DateTimeFormatter.ofPattern(pattern).format(ZonedDateTime.now().withZoneSameLocal(ZoneOffset.ofHoursMinutesSeconds(1, 30, 15)))}")
}
```

outputs
```
x: +0130
xx: +0130
xxx: +01:30
xxxx: +013015
xxxxx: +01:30:15
X: +0130
XX: +0130
XXX: +01:30
XXXX: +013015
XXXXX: +01:30:15
Z: +0130
ZZ: +0130
ZZZ: +0130
ZZZZ: GMT+01:30:15
ZZZZZ: +01:30:15
O: GMT+1:30:15
OOOO: GMT+01:30:15
```
whereas with the offset of 0 it outputs
```
x: +00
xx: +0000
xxx: +00:00
xxxx: +0000
xxxxx: +00:00
X: Z
XX: Z
XXX: Z
XXXX: Z
XXXXX: Z
Z: +0000
ZZ: +0000
ZZZ: +0000
ZZZZ: GMT
ZZZZZ: Z
O: GMT
OOOO: GMT
```

Additionally, `O`, `OOOO`, and `ZZZZ` are all subject to localization.
For the offset 0, they all output `GMT` in all locales (which may be an
oversight, given that the word `GMT` itself is localized with non-zero
offsets), but for offsets other than zero, they produce something like
`Гринуич+1:30:15` (Belorussian) or `گرینویچ+1:30:15` (Arabic).

Essentially, there are three dimensions by which the directive can differ:
* how many of the components need to be output,
* whether or not `:` is used,
* whether the offset 0 is formatted as `Z` or completely regularly,
* and whether the offset requires a localized `GMT` prefix/suffix.

To my knowledge, no other ecosystem actually provides the localized
`GMT` prefixes/suffixes.

The relative popularity in the number of unique files that reference them:
* `Z`, `ZZ`, `ZZZ`: 551
* `X`: 262
* `XXX`: 262
* `xxx`: 79
* `O`: 39
* `XX`: 34
* `x`: 31
* `xx`: 26
* `ZZZZZ`: 24
* `XXXXX`: 23
* `OOOO`, `ZZZZ`: 28
* `xxxx`: 14
* `XXXX`: 12
* `xxxxx`: 5

People get confused by the basic/extended ISO-8601 w.r.t. offsets:
<https://stackoverflow.com/questions/42763103/convert-string-yyyy-mm-dd-to-localdatetime/42763206#42763206>

### Time zones

* `z`, localized time zone name.
* `v`, localized time zone name, disregarding the daylight saving time.
* `V`, the timezone identifier.

`z` is more widespread than `V`.
`v` is a recent addition and almost isn't used at all.

`z`, `zz`, and `zzz` are the same short form, whereas `zzzz` is the full form.
`v` is the short form, `vvvv` is the full form.

* The short forms are sometimes localized as well. For example,
  almost everywhere, `America/Guatemala` is `CST` when formatted via `z`
  in winter and `CT` when formatted via `v`, whereas in `fr_CA`, it's
  `HNC` via `z` and `HC` via `v`. Check:
  ```kotlin
  for (zone in ZoneId.getAvailableZoneIds()) {
    println("============ $zone ============")
    checkOutputForAllLocales(
        ZonedDateTime.now().withZoneSameLocal(ZoneId.of(zone)),
        onlyListDifferent = true,
        onlyListNotThoseInRoot = true,
        "z",
        "v"
    )
  }
  ```
* As shown above, the short names between `z` and `v` can also be different
  from each other.

### Miscallaneous

* `D`, the day of the year.
* `n`, the nanosecond part of a second. Almost always misused for `S`, as
  discussed above.

#### Quarters

There are directives `q` and `Q` for quarters of the year. Looking through all
the locales, these formats are completely consistent:

`q`     | 3
`qq`    | 03
`qqq`   | 3
`qqqq`  | 3
`qqqqq` | 3
`Q`     | 3
`QQ`    | 03

On the other hand,
* `QQQ` is a locale-defined name of the quarter. Serbian: `K3`.
* `QQQQ` is a longer locale-defined name. Serbian: `Треће тромесечје`.
* `QQQQQ` is a shortened locale-defined name. Serbian: `3.`.

Across the repositories, `Q` and `q` are only in in 16 unique files.

#### The rest of the directives

All the following directives are so unpopular that we won't bother even
researching them.
* `g`, the "modified julian day"
* `A`, the millisecond of day.
* `N`, the nanosecond of day.
* `W`, the week of month.
