Strictness of parsing
---------------------

### Should a string be in a specific format?

What if a string arrives that is not the exact format that's requested but
instead something a bit different? For example, instead of `2022-01-04T23:58`
arrives `2022-01-04t23:58` (wrong character case), or
`2022-1-04T23:58` (wrong field format), or `2022 - 01 - 04 T 23:58`
(extra/missing spaces), or `2022 Jan 04T23:58`
(wrong style of the field).

#### Case sensitivity

Case-insensitive month names are in enormous demand:
* <https://bugs.openjdk.org/browse/JDK-8141245>
* <https://stackoverflow.com/questions/10797808/how-to-parse-case-insensitive-strings-with-jsr310-datetimeformatter>
* <https://stackoverflow.com/questions/36102897/java-time-format-datetimeparseexception-for-dd-mmm-yyyy-format>
* <https://stackoverflow.com/questions/28318119/how-to-handle-upper-or-lower-case-in-jsr-310/28318296#28318296>
* <https://stackoverflow.com/questions/39211077/java-english-date-format-parsing>
* <https://stackoverflow.com/questions/44925840/java-time-format-datetimeparseexception-text-could-not-be-parsed-at-index-3>

Case-insensitive weekday names are also popular:
* <https://stackoverflow.com/questions/68259413/parse-lowercase-short-weekday-to-java-8s-time-dayofweek>
* <https://stackoverflow.com/questions/46486822/localdate-parsing-is-case-sensitive>
* <https://stackoverflow.com/questions/39211077/java-english-date-format-parsing>

Case-insensitive UTC offsets are used in JSR-310:
* <https://github.com/ThreeTen/threetenbp/blob/9924a52af6a8aef2dbfc834cfcaee93d1a7b8faf/src/main/java/org/threeten/bp/format/DateTimeFormatter.java#L154-L156>

Metasearches:
* <https://grep.app/search?q=parseCaseInsensitive>
* <https://stackoverflow.com/search?q=parseCaseInsensitive>

C's `strptime` is case-insensitive when parsing month and weekday names.
Go is likewise: <https://stackoverflow.com/a/55590545>.

By default, Java's date-time parsing is case-sensitive.
This can be changed with the `parseCaseInsensitive()` directive, which affects
all the remaining text until the end or `parseCaseSensitive()`.

#### Field format

If the format expects a string of the form `2022-01-04`, is it okay to pass
`2022-1-04` instead? `2022- 1-04`? `2022-001-04`?

`strptime`:
* A single space in the format denotes an unlimited amount of them.
  - In glibc, despite the man page saying otherwise, there is even more leniency:
    any spaces before (yet not after) a field is okay.
    ```
    $ ./datetime.out '%d-%m-%Y' '01- 1-2022'
    2022-01-01T99:99:99 (day of year 1, Saturday), maybe DST
    $ ./datetime.out '%d-%m-%Y' '01- 1  -2022'
    Could not parse the string
    9999-01-01T99:99:99 (day of year 999, Unknown day of the week), maybe DST
    ```
* If it makes sense to have at most `n` digits in a field, up to `n` digits
  but not more are allowed:
  ```
  $ ./datetime.out '%d-%m-%Y' '01- 01-2022'
  2022-01-01T99:99:99 (day of year 1, Saturday), maybe DST
  $ ./datetime.out '%d-%m-%Y' '01- 1-2022'
  2022-01-01T99:99:99 (day of year 1, Saturday), maybe DST
  $ ./datetime.out '%d-%m-%Y' '01- 001-2022'
  Could not parse the string
  9999-99-01T99:99:99 (day of year 999, Unknown day of the week), maybe DST
  ```

Go's `time` package behaves exactly Python here with directives that imply no
padding (like `3`), allowing either having or not having zero-padding on parsing,
but also has separate directives that imply padding (like `03`), and those do
check the exact width.
Spaces are also expanded to groups of spaces like in Python.

Java's `DateTimeFormatter` requires exactly the width of fields by
default, unless they are variable-length fields (like `H` or `M`):
`DateTimeFormatter.ofPattern("uuuu MM dd").parse("1996 2 24")` will fail, as will
`DateTimeFormatter.ofPattern("uuuu MM dd").parse("1996 002 24")`.
However, both dates would parse successfully if `DateTimeFormatterBuilder`'s
`parseLenient` is used:
```kotlin
val withLenient = DateTimeFormatterBuilder()
    .parseLenient().appendPattern("uuuu MM dd").toFormatter()
println(withLenient.parse("1996 2 24"))
println(withLenient.parse("1996 000002 24"))
```

Interestingly, if one modifies the padding type to be the spaces
(via the `p` directive), the leniency is not fully observed:
```kotlin
val spacesWithLenient = DateTimeFormatterBuilder()
    .parseLenient().appendPattern("uuuu ppMM dd").toFormatter()
println(spacesWithLenient.parse("1996  2 24")) // ok
println(spacesWithLenient.parse("1996 2 24")) // ok
println(spacesWithLenient.parse("1996   2 24")) // fails
```

`parseLenient` is not widely in use:
* <https://grep.app/search?q=parseLenient&words=true&filter[lang][0]=Java>
* <https://stackoverflow.com/search?tab=Relevance&pagesize=50&q=DateTimeFormatterBuilder%20parseLenient>

Note that the usage of `parseLenient` is for leniency not only in field width
but also in field style (see the discussion of field style strictness). 

A way to simulate this leniency without using `parseLenient` is by using
the optional sections. Analysis of the patterns found in the wild uncovered
these usages (pattern/total usages/total unique files/containing repositories):
```
"yyyy-[MM][M]-[dd][d]","9","4","7"
"[Z][VV][x][xx][xxx]","4","4","3"
"yyyy-MM-dd'T'HH:mm:ss[.SSS][xxx][xx][X]","4","4","1"
"yyyy-MM-dd[['T'][ ]HH:mm:ss.SSS[ ][XXXXX][XXXX]]","3","3","2"
"[yyyyMMdd][yyyy-MM-dd][yyyy-DDD]['T'[HHmmss][HHmm][HH:mm:ss][HH:mm][.SSSSSSSSS][.SSSSSS][.SSS][.SS][.S]][OOOO][O][z][XXXXX][XXXX]['['VV']']","3","3","2"
"yyyy-MM-dd[['T'][ ]HH:mm:ss[.SSS][ ][XXX][X]]","3","3","2"
"uuuu[-MM[-dd]]['T'HH[:mm[:ss[.SSSSSSSSS][.SSSSSSSS][.SSSSSSS][.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]]]][XXX]","3","3","2"
"['\t']['    ']['  '][' ']['\t']","2","2","1"
"[yyyy-MM-dd HH:mm:ssxxx][yyyy-MM-dd HH:mm:ssxx][yyyy-MM-dd HH:mm:ssx]","2","2","1"
"[yyyy-MM-dd HH:mm:ss.SSSSSSSSS][yyyy-MM-dd HH:mm:ss.SSSSSSSS][yyyy-MM-dd HH:mm:ss.SSSSSSSS][yyyy-MM-dd HH:mm:ss.SSSSSSS][yyyy-MM-dd HH:mm:ss.SSSSSS][yyyy-MM-dd HH:mm:ss.SSSSS][yyyy-MM-dd HH:mm:ss.SSSS][yyyy-MM-dd HH:mm:ss.SSS][yyyy-MM-dd HH:mm:ss.SS][yyyy-MM-dd HH:mm:ss.S][yyyy-MM-dd HH:mm:ss]","2","2","1"
"H[H]:m[m][:s[s]][.SSS][ zzz]","2","2","2"
"[(XXX) ][O ][(O) ]a h:m:s M/d/y","2","2","2"
"[XXX][XX][X]","2","2","1"
"[H:mm][HH:mm][H:m][HH:m]","2","2","1"
"[HH:mm:ss.SSSSSSSSS][HH:mm:ss.SSSSSSSS][HH:mm:ss.SSSSSSSS][HH:mm:ss.SSSSSSS][HH:mm:ss.SSSSSS][HH:mm:ss.SSSSS][HH:mm:ss.SSSS][HH:mm:ss.SSS][HH:mm:ss.SS][HH:mm:ss.S][HH:mm:ss]","2","2","1"
" [HH][H]:[mm][m]:[ss][s]","2","2","2"
"yyyy-M[M]-d[d][ H[H]:m[m][:s[s]][.SSS][ zzz]]","2","2","2"
"[xxx][xx][X]","4","1","2"
```

Except for the zone offset format,
most of the patterns here don't make any sense, people are clearly just
bruteforcing the format strings. For example, when parsing, `H[H]` is exactly
the same as just `H`. The behavior would be different when formatting: it would
output the hour twice back to back.
```kotlin
val pp = DateTimeFormatterBuilder()
    .appendPattern("[H][HH]").toFormatter()
println(pp.parse(pp.format(java.time.LocalTime.of(2, 15, 0))))
// Text '202' could not be parsed: Invalid value for HourOfDay (valid values 0 - 23): 202
```

Still, the point remains that some number of people clearly does want
variable-width fields when parsing even to the point of trying to put together
whatever seems to work.

The correct solution is clearly to use the designated variable-width fields,
like `H`, `m`, `M`, or `S`. Unfortunately, their behavior is obscured by the
fact that directive lengths have unintuitive special behavior, so people clearly
think that just the pattern `H` means that only a single digit is permitted.

#### Variable amount of spaces

When Java switched from `SimpleDateFormat` to `DateTimeFormatter`,
it stopped supporting the variable amount of spaces.

```kotlin
import java.text.*
import java.time.format.*

fun main() {
    val before = SimpleDateFormat("uuuu MM dd")
    println(before.parse("1996   02 24"))
    val after = DateTimeFormatter.ofPattern("uuuu MM dd")
    println(after.parse("1996 02 24"))
}
```

An uproar seemingly didn't happen.
The complaints about this are actually about zero-padded fixed-length fields:

```
Mon Nov 20 14:40:36 2006
Mon Nov  6 14:40:36 2006
```

* <https://stackoverflow.com/questions/5879546/parsing-dates-with-variable-spaces>
* <https://stackoverflow.com/questions/19439821/eliminating-the-subtle-whitespace-handling-difference-between-datetimeformat-and>
* <https://stackoverflow.com/questions/28136083/how-to-parse-localdatetime-with-leading-spaces>

It looks like there's neither the option for actually variable-width fields.
Even the option for lenient parsing doesn't affect this:

```kotlin
DateTimeFormatterBuilder()
  .parseLenient()
  .appendPattern("uuuu MM dd")
  .toFormatter()
```

#### Field style

When the format says the string should be `Jan 1st`, is it okay to successfully
parse `January 1st`, or vice versa?

##### `strptime`

In C, `Jan` = `January` ≠ `1`.

```
$ ./datetime.out '%d %B %Y' '1 January 1996'
1996-01-01T99:99:99 (day of year 1, Monday), maybe DST
$ ./datetime.out '%d %B %Y' '1 Jan 1996'
1996-01-01T99:99:99 (day of year 1, Monday), maybe DST
$ ./datetime.out '%d %B %Y' '1 1 1996'
Could not parse the string
9999-99-01T99:99:99 (day of year 999, Unknown day of the week), maybe DST
$ ./datetime.out '%d %m %Y' '1 January 1996'
Could not parse the string
9999-99-01T99:99:99 (day of year 999, Unknown day of the week), maybe DST
$ ./datetime.out '%d %m %Y' '1 Jan 1996'
Could not parse the string
9999-99-01T99:99:99 (day of year 999, Unknown day of the week), maybe DST
$ ./datetime.out '%d %m %Y' '1 1 1996'
1996-01-01T99:99:99 (day of year 1, Monday), maybe DST
```

In Python, `Jan` ≠ `January` ≠ `1`.

```python
import datetime
datetime.datetime.strptime("01 01 2022", "%d %m %Y")
# datetime.datetime(2022, 1, 1, 0, 0)
datetime.datetime.strptime("01 Jan 2022", "%d %M %Y")
# ValueError: time data '01 Jan 2022' does not match format '%d %M %Y'
datetime.datetime.strptime("01 Jan 2022", "%d %b %Y")
# datetime.datetime(2022, 1, 1, 0, 0)
datetime.datetime.strptime("01 January 2022", "%d %b %Y")
# ValueError: time data '01 January 2022' does not match format '%d %b %Y'
datetime.datetime.strptime("01 January 2022", "%d %B %Y")
# datetime.datetime(2022, 1, 1, 0, 0)
datetime.datetime.strptime("01 Jan 2022", "%d %B %Y")
# ValueError: time data '01 Jan 2022' does not match format '%d %B %Y'
datetime.datetime.strptime("01 02 2022", "%d %B %Y")
# ValueError: time data '01 02 2022' does not match format '%d %B %Y'
```

To fight the inability to parse unstructured data, there's the `dateutil`
Python library:
<https://stackoverflow.com/questions/65766710/using-datetime-strptime-when-data-is-a-little-messy-extra-spaces-jan-or-janua>

#### Go

Doesn't provide any ability to be lenient in its field styles.
If the format says `Jan`, only `Jan`, `Feb`, etc will be accepted, no
`March` or `01`.

The typical way to deal with this is to try several patterns until one of them
succeeds: <https://github.com/grafana/grafana/blob/main/pkg/tsdb/mysql/mysql.go#L236-L245>
This is immensely common: searching through
<https://grep.app/search?q=time.Parse%28&filter[lang][0]=Go>, one sees this
pattern constantly.

#### JSR-310

By default, the style is fixed. With
`DateTimeFormatterBuilder#parseLenient`, it's possible to instruct the parser to
accept more formats. The docs are not awfully clear about what this does:
<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html#parseLenient()>
Experiments show that leniency doesn't always work:

```
Input \ Format | M | MM | MMM | MMMM | MMMMM |
1              | + | +  |  +  |  +   |   +   |
01             | + | +  |  +  |  +   |   +   |
Jan            | - | -  |  +  |  +   |   +   |
January        | - | -  |  +  |  +   |   +   |
J              | - | -  |  +  |  +   |   +   |
```

### Should the values make sense?

An obvious behavior for a parser is to fail to parse a date like `2020-01-40`.
However, alternatively, one could try to auto-correct the date.

#### `strptime` (Python, C, etc)

`strptime` straight-out refuses to parse invalid values, and that's it.

#### Go

`time.parse` will result in an error in case of an impossible value:

```
const longForm = "Jan 2, 2006 at 3:04pm (MST)"
t, e := time.Parse(longForm, "Feb 29, 2013 at 7:54pm (PST)")
fmt.Println(t) // 0001-01-01 00:00:00 +0000 UTC
fmt.Println(e) // parsing time "Feb 29, 2013 at 7:54pm (PST)": day out of range
```

#### JSR-310, `ResolverStyle`

Dictates how invalid date-times are handled.

Can be
* `STRICT`, checks for full consistency.
* `SMART`, default, will clamp out-of-bounds to the sensible ones.
  For example, `2020-01-40` will become `2020-01-31`.
  `SMART` is the default.
* `LENIENT`, if a value is out of bounds, it will overflow to other values.
  For example, `2020-01-40` will become `2020-02-09`.

Searching for the resolver styles, we find the following:

The need to check a value for being fully valid is very common:
<https://stackoverflow.com/questions/226910/how-to-sanity-check-a-date-in-java>
It arises in the areas of user input validation, preparing the data for
crossing the serialization boundaries, etc.
Here, `ResolverStyle.STRICT` shines.
The one major issue people have with `ResolverStyle.STRICT` is the problem of
`yyyy` vs `uuuu`. Search this document for these tokens for details.

`ResolverStyle.SMART`: people are unhappy with the lack of validation by default
and ask whether validation can be enforced:
* <https://stackoverflow.com/questions/55165168/java-localdatetime-parsing>
* <https://stackoverflow.com/questions/53034182/prevent-24-value-of-hour-in-a-time-without-seconds>
* <https://stackoverflow.com/questions/63367295/is-this-additional-check-for-parsing-a-string-to-localdate-object-necessary>
* <https://stackoverflow.com/questions/41103603/issue-with-datetimeparseexception-when-using-strict-resolver-style>

It's not clear though whether the people *not* asking about
`ResolverStyle.SMART` just don't know about its gotchas or are actually happy
with them. Given the lack of requests for an equivalent of `ResolverStyle.SMART`
in the other languages, the former is more likely.

`ResolverStyle.LENIENT` is just confusing to everyone, not actually needed,
and leads to bad code:
* <https://github.com/apache/hive/pull/2445>, here a project moves away from
  `LENIENT` even at the cost of backward compatibility.
* <https://github.com/mongodb/mongo-kafka/pull/77#discussion_r665665738>
* <https://stackoverflow.com/questions/66134849/how-to-change-the-current-date>
  someone uses `LENIENT` to perform *parsing-time arithmetic*.
* <https://stackoverflow.com/questions/62054264/check-invalid-date-by-localdate>
* <https://stackoverflow.com/questions/55582146/shifting-month-day-automatically-when-constructing-localdate>
  someone has a need to perform arithmetic, and is advised to convert to
  a string and then parse with lenient resolving.
* <https://stackoverflow.com/a/70234078> `ResolverStyle.LENIENT` is recommended
  here, but does nothing related to the question.

Some use cases when someone *actually* wants this are just people wanting
to parse a `DateTimePeriod`:
* <https://stackoverflow.com/questions/52494526/parse-time-duration-with-leap-second-like-000060/52499196#comment91943960_52499196>
* <https://stackoverflow.com/questions/64005792/value-24-for-hourofday-must-be-in-the-range-0-23>

Actually, if `DateTimePeriod` can be parsed with a given format string
(in JSR-310, it can't), `ResolverStyle.LENIENT` can be emulated by
```kotlin
LocalDateTime(0, 0, 0, 0, 0).toInstant(TimeZone.UTC).plus(
  DateTimePeriod.parse(...), TimeZone.UTC
)
```
This approach has the added benefit of being parameterized with a time zone,
which additionally allows one to prevent acquiring an invalid `LocalDateTime`
as a result.

In general, `ResolverStyle` has unintuitive and unclear behavior:
* <https://stackoverflow.com/questions/33234245/jackson-jsr-310-module-fails-to-deserialize-simplest-offsetdatetime-format>
  `ResolverStyle` is confused with strict/lenient parsing.
* <https://stackoverflow.com/questions/45839399/why-does-java-8-datetimeformatter-allows-an-incorrect-month-value-in-resolversty>
  `ResolverStyle.STRICT` only happens in the resolution phase, so parsing
  the month `50` is fine, but acquiring it is not.

### Should the values be internally consistent?

Sometimes, we want to parse dates like `2022-01-04, Tue`.
Should we perform validation of the day of the week in this case?

In general, some formats permit several sources of truth that can be out of sync.
For example, in theory, it's possible to try to parse a string that contains
both years, months, and days and week-years, week numbers, and weekdays, or
a string with both the local time and the number of nanoseconds since
the start of the day.

The realistic concerns:
* Weekdays + full dates.
* Having both 24-hour times and AM/PM markers.
* Zone offsets + date and time + time zone may be incompatible.

#### `strptime`

`strptime` just allows one to access all the fields independently:
day of the week is separate from the rest of the date.
When there are separate sources of truth, the last one to appear in
the string is silently chosen:
```python
import datetime
# %H -- hours 0..23
# %I -- hours 1..12
# %M -- minutes
datetime.datetime.strptime("13:45 10", "%H:%M %I")
# datetime.datetime(1900, 1, 1, 10, 45)
datetime.datetime.strptime("10 13:45", "%I %H:%M")
# datetime.datetime(1900, 1, 1, 13, 45)
```

No access to the timezone database, so no conflict possible there.

#### Go

Go ignores the day of the week when parsing.
When there are separate sources of truth, the last one to appear in
the format string is chosen:
```go
// 15 -- hours 0..23
// 3 -- hours 1..12
// 04 -- minutes
// PM -- am/pm marker
t, _ = time.Parse("15, 3:04, PM", "23, 10:36, AM")
fmt.Println(t) // 0000-01-01 10:36:00 +0000 UTC
t, _ = time.Parse("3:04, 15, PM", "10:36, 23, AM")
fmt.Println(t) // 0000-01-01 23:36:00 +0000 UTC
```

If the specified offset and/or time zone name matches the time zone specified
separately, outside of the format string, that time zone is used,
keeping the provided offset, if any.

#### JSR-310

By default, Java's `DateTimeFormatter` checks the whole date for internal
consistency. This can be changed by manually choosing which fields
participate in resolution, via
[withResolverFields](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#withResolverFields-java.time.temporal.TemporalField...->).

`withResolverFields` is barely ever used:
* <https://stackoverflow.com/search?q=withResolverFields> only gives
  two usages:
  - <https://stackoverflow.com/questions/26393594/using-new-java-8-datetimeformatter-to-do-strict-date-parsing/63920190#63920190>
    Here, `withResolverFields` does nothing.
  - <https://stackoverflow.com/questions/48825725/different-date-formatting-options-should-be-formatted-into-a-common-format-in-ja/48830764#48830764>
    Here, `withResolverFields` is actually used correctly.
* <https://grep.app/search?q=withResolverFields&filter[lang][0]=Java>
  only shows a single usage:
  - <https://github.com/apache/james-mime4j/blob/master/dom/src/main/java/org/apache/james/mime4j/field/DateTimeFieldLenientImpl.java>
    this claims to parse a date in an RFC-5322 format.
    The [standard](https://www.rfc-editor.org/rfc/rfc5322#section-3.3) says:
    "the day-of-week (if included) MUST be the day implied by the date".
    mime4j is a big library with fairly specific needs, so it may make sense
    for them to neglect this check.

In the old version of Java, parsing facilities did not check the offset for
validity by default, throwing it away in case of a conflict:
```
val zonedDateTimeParser =
  DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX'['VV']'")

// On JVM 1.8
ZonedDateTime.parse(
    "2020-01-04T15:33:53+09:00[Europe/Berlin]",
    zonedDateTimeParser
)
// 2020-01-04T15:33:53+01:00[Europe/Berlin]

ZonedDateTime.parse(
    "2020-01-04T15:33:53+09:00[Europe/Berlin]",
    zonedDateTimeParser.withResolverStyle(ResolverStyle.STRICT)
)
// fails
```

The same code on a new version will attempt to preserve the offset, while
still not checking for validity by default:
```
// On JVM 16
ZonedDateTime.parse(
    "2020-01-04T15:33:53+09:00[Europe/Berlin]",
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX'['VV']'")
)
// 2020-01-04T07:33:53+01:00[Europe/Berlin]
```

The same behavior can be observed by just parsing `ZonedDateTime` without any
specific parser.

#### Analysis

Quite many people use Java's datetime parsing capabilities, and if this few
people managed to find a use for `withResolverFields`, it must be an incredibly
niche requirement. In any case, there doesn't seem to ever be a need to
access all pieces of inconsistent data.

That said, inconsistent offset/timezone combinations are common, so
we shouldn't fail parsing immediately when encountering errors of this kind.

### Should all the required values be unambiguous?

Sometimes, not all the information needed to construct the object is available. 
For example, sometimes people want to parse strings like `34-02-12`,
where `34` means `1934`. Or does it actually mean `2034`?
Similarly, what is the `Instant` corresponding to the string
`2022-07-14T16:48:02.26`? Without knowing the time zone in which the
date is recorded, there's no way of knowing for sure.

#### Year-of-era without an era

A giant problem when people try to use strict resolving is that the directive
`yyyy` is actually ambiguous, denoting the year of era and requiring that the
era is also specified. This is fixed by replacing `yyyy` with `uuuu`:
* <https://stackoverflow.com/questions/63706705/java-datetimeformatter-is-not-parsing-as-expected>
* <https://stackoverflow.com/questions/41103603/issue-with-datetimeparseexception-when-using-strict-resolver-style>
* <https://stackoverflow.com/questions/32823368/java-8-localdatetime-is-parsing-invalid-date>
* <https://stackoverflow.com/questions/27454025/unable-to-obtain-localdatetime-from-temporalaccessor-when-parsing-localdatetime/43675230#43675230>
* <https://stackoverflow.com/questions/26393594/using-new-java-8-datetimeformatter-to-do-strict-date-parsing/30478777#30478777>

When parsing a year with two digits, by default, JSR-310 will assume that the
year is in `2000..2099`, but this is occasionally configured:
* <https://grep.app/search?current=2&q=appendValueReduced%5C%28%5B%5E%29%5D%2A%2C%5Cs%2A%5Cd%2B%5C%29&regexp=true>
* <https://stackoverflow.com/questions/47391985/datetimeformatterbuilder-parsing-days-below-31-smart-not-strict-if-appendvaluere>
* <https://stackoverflow.com/questions/56989006/is-there-any-way-to-convert-the-date-in-java-prior-to-1970/56994184#56994184>
* <https://stackoverflow.com/questions/32782645/parsing-2-digit-years-setting-the-pivot-date-with-an-unknown-date-pattern>
* <https://stackoverflow.com/questions/33238082/last-digit-of-year-with-datetimeformatter/33238493#33238493>
  Here, a user wants to output and parse *one* digit of the year.

#### 12-hour time without the AM/PM marker

Java:
```kotlin
// fails
DateTimeFormatter.ofPattern("hh:mm:ss").parse("13:12:59")

// ok
DateTimeFormatter.ofPattern("hh:mm:ss").parse("02:12:59")

// however, resolving fails anyway:
LocalTime.parse("02:12:59", DateTimeFormatter.ofPattern("hh:mm:ss"))
```

Go:

#### Inferring the locale

Java will use the current system locale by default:
```kotlin
Locale.setDefault(Locale.CHINA)
println(
    DateTimeFormatter.ofPattern("MMMM").format(
        ZonedDateTime.now().withZoneSameLocal(ZoneId.of("Europe/Berlin"))
    )
)
// Output: 十一月
```

Python's and C's `strptime` as well:
* <https://stackoverflow.com/questions/38303217/datetime-strptime-unexpected-behavior-locale-issue>
* <https://stackoverflow.com/questions/57354666/python-datetime-strptime-with-korean-locale-on-windows>
* <https://stackoverflow.com/questions/50016614/overriding-default-locale-in-strptime>

Go only supports the English locale, so there's nothing to infer.
The library for locale-aware datetime parsing and formatting in Go
(<https://github.com/goodsign/monday/>) doesn't infer the locale and expects
one to pass it explicitly.

See the separate file about locales, but in short, it seems there are real
problems caused by Java's locale-inferring mechanism due to changing the
parsing behavior when running code on different machines.

```kotlin
// ok
DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ROOT).parse("02:12:59 PM")

// fails
DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.CHINA).parse("02:12:59 PM")
```

#### Inferring the timezone from incomplete data

This includes both omitting the timezone altogether and supplying
abbreviations, which may be ambiguous. For example,
`IST` is
* Indian Standard Time,
* Irish Standard Time,
* Israel Standard Time,
* Iceland Standard Time.

`GST` is
* Gulf Standard Time,
* South Georgia Time.

* Java:
  - Does not infer the timezone by default, but it's possible to specify one
    for use by the formatter. When a formatter with a specified time zone is
    used for parsing or formatting and only an `Instant` is supplied and a
    `LocalDateTime` is required (or vice versa, or just the timezone itself is
    requested), the values are obtained using the provided timezone.
  - There are several variants of timezone names, and all of them attempt to
    parse anything that comes their way, even in cases of ambiguity.
    For example, with the pattern "short localized time zone names" in the
    English locale, `IST` will be parsed as `Atlantic/Reykjavik`, and `GST`
    as `Asia/Dubai`.
  - The timezone names are localized, both when parsing and when formatting,
    including the short versions.
* Python doesn't have an equivalent of an `Instant`. Instead, its
  `LocalDateTime` objects may optionally contain the timezone information.
  This information is populated when parsing, and when formatting,
  the timezone-aware information just outputs empty strings. However, when
  parsing, empty strings are not accepted. Parsing is always unambiguous,
  because the actual time zones are not supported by default, but it's
  possible to supply your own name/timezone table:
  <https://stackoverflow.com/questions/1703546/parsing-date-time-string-with-timezone-abbreviated-name-in-python>
  Only fixed-offset timezones can be used.
* Go assumes the current time zone when possible. When not, it preserves the
  offset. When an abbreviation doesn't match the assumed time zone, it parses
  into UTC. More specifically:
  - If an offset is supplied and it matches the one for the specified system
    time zone (by default, the current system one), that time zone is used.
    Otherwise, just a fixed-offset time zone is stored.
  - If the abbreviation makes sense in the specified time zone (by default,
    the current system one), that time zone is used.
    For example, if the time zone is `Europe/Moscow` and `MSK`
    (a known abbreviation for `Europe/Moscow`) is supplied,
    `Europe/Moscow` is used.
    If the current timezone is `Europe/Berlin` and `MSK` is supplied,
    the UTC timezone is used, though a synthetic timezone with the name
    `MSK` is used.
* Noda Time (explaining the formatting of the time zones here to illustrate the
  role of the described parts of the parsing/formatting system):
  - It just assumes the UTC time zone when in doubt:
    <https://nodatime.org/2.4.x/userguide/instant-patterns>
    <https://nodatime.org/2.4.x/userguide/zoneddatetime-patterns>
  - Time zones store a "time zone provider", a mechanism which matches
    time zone names with their rules:
    <https://nodatime.org/2.4.x/api/NodaTime.IDateTimeZoneProvider.html>
    Essentially, this is a `Map<String, TimeZone>`.
    There are two providers of time zone names:
    <https://nodatime.org/2.4.x/api/NodaTime.DateTimeZoneProviders.html>
    * .NET Base Class Library (BCL)
    * TZDB
  - When parsing time zones, the provider must be specified.
    When formatting them, it's not taken into account, and instead, just
    the identifier is output.
  - It only allows formatting abbreviations, not parsing them:
    <https://nodatime.org/2.4.x/userguide/zoneddatetime-patterns>
    The abbreviations depend on the provider of the time zone.
    The request to parse abbreviations is not popular:
    <https://stackoverflow.com/questions/66400962/parsing-date-and-time-strings-containing-bst-and-gmt-with-nodatime>

#### Some other things

A composite problem:
* Some people want to parse `Instant` from a `LocalDateTime`:
  - <https://stackoverflow.com/questions/37672012/how-to-create-java-time-instant-from-pattern/37676370#37676370>
* Some people want to parse `LocalDateTime` from a `LocalDate`:
  - <https://stackoverflow.com/questions/42763103/convert-string-yyyy-mm-dd-to-localdatetime/42763206#42763206>

### Resolutions

The analysis of the current struggles tells us that we want:
* An ability to perform case-insensitive parsing of month names,
* An ability to perform case-insensitive parsing of weekday names,
* An ability to perform case-insensitive parsing of the zone offset `Z`,
* (Potentially) Parsing `DateTimePeriod` from a format string.
* Variable-width fields for parsing.
* (Potentially) Ability to specify alternative things to parse.
  This is commonly emulated by parsing in various formats until
  one of them succeeds.

We **don't** want:
* Any mechanisms of dealing with invalid dates and times other than failure.
* Any mechanisms other than failing for handling internally inconsistent readings.
