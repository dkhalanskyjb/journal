An overview of prior art
------------------------

### C's [strftime(3)](https://linux.die.net/man/3/strftime), a `printf` lookalike.

The de-facto standard, used in

* C: <https://man7.org/linux/man-pages/man3/strftime.3.html>
* C++: <https://en.cppreference.com/w/cpp/io/manip/put_time>
* Python: <https://docs.python.org/3/library/datetime.html#datetime.date.strftime>
* Rust: <https://docs.rs/chrono/0.3.1/chrono/format/strftime/index.html>
  (ignoring the locale)
* Haskell: <https://hackage.haskell.org/package/time-1.13/docs/Data-Time-Format.html>
  (with quite significant differences)
* ...

Example in Python:

```
`now.strftime("%m/%d/%Y, %H:%M:%S")`
```

Details:

* Several directives for each field for different output forms.
  For example, hours can be output with
  `%H` (00..23), `%I` (00..12), `%k` (0..23), `%l` (0..12).
* Parsing is lenient.
    * Only the parsed components are initialized by `strptime`.
    * The difference between `%H` and `%k` is ignored, both mean the same.
    * Whitespaces are skipped.
    * If the year is parsed with the two-digit format, it is considered to be
      a year in (1950..2049) in glibc 2.0, and a year in (1969..2068)
      in glibc 2.1.
* A lot of locale-dependent functionality:
    * Locale-specified format strings for dates and times.
    * Names of weekdays (abbreviated and full).
    * Strings for `a.m.` and `p.m.`, in lower and upper case.
    * Locale can be optionally queried for whether to use roman numerals.

#### Characters

- `%n`, a newline.
- `%t`, a tab.
- `%%`, a `%`.

#### Locale-dependent directives

Due to their nature, applicable to `LocalTime`, `LocalDate`, `LocalDateTime`.

- `%a`, `%A`, abbreviated and full names of the day.
  *`Mon`, `Monday`.*
- (`%b`/`%h`), `%B`, abbreviated and full names of the month.
  *`Jan`, `January`.*
- `%x`, the locale-appropriate formatting of dates.
- `%X`, the locale-appropriate formatting of times.
- `%X`, the locale-appropriate formatting of times in a.m/p.m. notation.
- `%c`, the locale-appropriate formatting of date-times.
- `%EC`, the name of the current era.
- `%EY`, the current year.
- `%E` is a modifier for `%c`, `%C`, `%x`, `%X`, `%y`, `%Y` that forces the use
  of the locale-aware notion of "era" for calculating which year it is.
  (TODO: unclear what this means for `%X`);
- `%O` is a modifier that forces use of the "alternative numeric symbols" of the
  locale.
- `%p`, `%P`, lower- or upper- case of "AM" and "PM".
- `%r`, the time in a.m./p.m. notation.

#### Locale-independent date directives

Year:
- `%C`, the century number, two digits.
  *`2021-10-23` → `20`.*
- `%y`, the year without the century, two digits.
  *`2021-10-23` → `21`.*
- `%Y`, the year, four digits.
  *`761-10-23` → `0761`.*

Month:
- `%m`, the month, two digits.
  *`2021-07-09` → `07`.*

Day of the month:
- `%d`, day of the months, two digits.
  *`2021-07-09` → `09`.*
- `%e`, day of the months, two characters, with a leading space.
  *`2021-07-09` → ` 9`.*

Day of the week:
- `%u`, day of the week, Monday = 1, Sunday = 7.
- `%w`, day of the week, Sunday = 0, Saturday = 6.

Week numbers (see also "week dates" below):
- `%W`, the week number, in `(00..53)`, two digits.

Other:
- `%j`, the day of the year, three digits, in `(001..366)`.
  *`2021-02-12` → `044`.*

#### Locale-independent time directives

Hour ("AM"/"PM" is locale-dependent: `%p`, `%P`):
- `%H`, the hour, in `(00..23)`, two digits.
  *`09:15` → `09`.*
- `%I`, the hour, in `(00..12)`, two digits.
  *`21:15` → `09`.*
- `%k`, the hour, in `(0..23)`, with a leading space.
  *`09:15` → ` 9`.*
- `%l`, the hour, in `(0..12)`, with a leading space.
  *`21:15` → ` 9`.*

Minute:
- `%M`, the minute, in `(00..59)`, two digits.
  *`09:07` → `07`.*

Second:
- `%S`, the second, in `(00..60)`, two digits.
  N.B.: leap seconds are supported.

#### Timezone-aware directives

- `%s`, Unix time.
- `%z`, the time zone offset as `+hhmm`/`-hhmm`.
- `%Z`, the time zone name.

#### Compound formats

- `%D`, an American format equivalent to `%m/%d/%y`.
- `%F`, an ISO date format equivalent to `%Y-%m-%d`.
- `%R`, hours and minutes: `%H:%M`.
- `%T`, hours, minutes, and seconds: `%H:%M:%S`

#### Week dates

"Week dates" are an alternative approach to date enumeration. Its components are:
- `%g`, a week-based year, without a century, two digits.
- `%G`, a week-based year, with a century, four digits.
- `%V`, the week number.

"Week dates" split each year into exactly 52 or 53 weeks.
If more than a half of a week is in the conventional year, this whole week
is considered a part of that year.

### [strptime(3)](https://linux.die.net/man/3/strptime), a `scanf` lookalike.

Uses the same directives as `strftime(3)`, but more leniently.

The man page claims that there should be some characters between any two
directives, but experiments show that this isn't needed, behavior is the same
as if a space was between the directives.
*In effect, the whitespaces are skipped*

These signifiers start meaning the same:
- `%a` and `%A`
- `%b`, `%B`, and `%h`
- `%d` and `%e` are the day of month, with or without a leading zero.

In general, the directives that output leading zeros don't actually require
them to be present.

Also,
- `%t` is an arbitrary whitespace character.
- Specifying `%y` but not `%Y` or `%C` means the year in the range 1969-2068.
  In earlier glibc versions, it meant a year in 1950-2049.

### Python

#### ISO-8601 support

Separate `fromisoformat`, `isoformat`.
`isoformat` for types with time components optionally accepts the name of
the last component to print.

`fromisoformat` used to not actually support parsing the ISO-8601 instant
values with `Z` at the end, causing an uproar:
<https://stackoverflow.com/questions/127803/how-do-i-parse-an-iso-8601-formatted-date/49784038#49784038>
Currently, parsing `Z` is also supported.

#### `strftime`

`strftime`, a catch-all function for formatting everything date-time and
their dog, with the same directives as `strftime(3)`.

`%u`, `%G`, and `%V` were recently added.

`%f` parse microseconds.

Using Unicode code-points that can not be represented in the current locale is
not defined.

Additional directives may be supported, as Python just calls `strftime`.

<https://docs.python.org/3/library/datetime.html#strftime-strptime-behavior>

#### `__format__`

`__format__` allows specifying the `strftime` format strings in the
`{value:FORMAT}` constructs:
```python
>>> dt = datetime.strptime("21/11/06 16:30", "%d/%m/%y %H:%M")
>>> 'The {1} is {0:%d}, the {2} is {0:%B}, the {3} is {0:%I:%M%p}.'.format(dt, "day", "month", "time")
'The day is 21, the month is November, the time is 04:30PM.'
```

#### The `dateutil` library

There's a fairly popular library that adds the ability to parse loosely-defined
datetime strings: <https://dateutil.readthedocs.io/en/stable/parser.html>.
It even spawned an imitator for Rust: <https://github.com/bspeice/dtparse>

They are not locale-aware at all:
* <https://stackoverflow.com/questions/19927654/using-dateutil-parser-to-parse-a-date-in-another-language>

### Go

<https://pkg.go.dev/time#pkg-constants>
Has human-readable templates: `Mon Jan 2 15:_4:05 MST 2006` is considered
to be a valid template.
Is never ambiguous, because only the predefined numbers are recognized, so in
effect, this is conceptually the same as `strftime`, with set directives.

* `04 05`: output minutes, then a space, then seconds, both with leading zeroes.
* `07 05`: output `07 `, then seconds in two digits with a leading zero.

Looks like this is a popular gotcha:
* <https://stackoverflow.com/questions/14106541/parsing-date-time-strings-which-are-not-standard-formats>
* <https://stackoverflow.com/questions/25845172/parsing-rfc-3339-iso-8601-date-time-string-in-go>
* <https://stackoverflow.com/questions/42876621/hour-out-of-range-on-time-parse-in-golang>
* <https://stackoverflow.com/questions/39589594/golang-time-error-month-out-of-range>

Also, human readability is not always achieved, which can be seen with the
offset specifications:
```
"-0700"  ±hhmm
"-07:00" ±hh:mm
"-07"    ±hh
"Z0700"  Z or ±hhmm
"Z07:00" Z or ±hh:mm
"Z07"    Z or ±hh
```

* Locales are not supported.
  This is a huge point of contention, and the community provided its own library
  to deal with this: <https://github.com/goodsign/monday/>
* Very lenient:
    * All format strings can be used. If a component is missing, it is 0 or 1.
      For example, `3:04pm` = `Jan 1, year 0, 15:04:00 UTC`.
    * Fractional seconds are recognized even if not specified in the format.
    * A two-digit year is parsed as (1969..2068).
    * If the time zone is not specified, it’s UTC.
    * If the offset only is specified and it corresponds to the offset
      at that moment in the current system time zone, the datetime is considered
      to be in that zone.
    * If an unknown time zone abbreviation is used, it’s considered to be UTC,
      but the name is preserved.
* Different forms of output are typically predictable: `4` is hours in `(0..12)`,
  `04` is hours in `(00..12)`, `_4` is hours in `( 0..12)`.
  `15` is hours in `(00..23)`.

### Unicode Date Format Patterns

The standard for format strings incorporated by Excel, Java-like languages, and Darwin
http://unicode.org/reports/tr35/tr35-6.html#Date_Format_Patterns
Also relevant is http://unicode.org/reports/tr35/tr35-6.html#Lenient_Parsing

* Supports era-aware formatting.
  A year can be formatted as either `-1` or  `2` + `BC`.
* Supports alternative forms of dates: year-week-day, where the year is
  sometimes different from the usual one, though most of the times they match.
* Supports short and long names of time zones.
* Seconds can be used to specify the time-of-day via milliseconds-of-day.
* A separate directive for fractional seconds. The trailing zeros are omitted.
* Four forms of formatting hours: 1-24, 0-23, 1-12, 0-11.
* Short and long forms of localized day-of-week names.

### Excel

<https://support.microsoft.com/en-us/office/format-a-date-the-way-you-want-8e10019e-d5d8-47a1-ba95-db95123d273e>

```
`M    - month (1)
MM   - month (01)
MMM  - month (Jan)
MMMM - month (January)
MMMMM- month (J)
D    - day (2)
DD   - day (02)
DDD  - day (Mon)
DDDD - day (Monday)
YY   - year (06)
YYYY - year (2006)
hh   - hours (15)
mm   - minutes (04)
ss   - seconds (05)
`
```

### .NET

An extension of Excel:
<https://docs.microsoft.com/en-us/dotnet/standard/base-types/custom-date-and-time-format-strings>.

* Locale-specific things:
    * AM/PM
    * Date separators, time separators (like `/` and `:`)
    * Day-of-week and month names, full and short
* Fractional seconds are supported by using the required number of `f` or `F`
  (the latter will be omitted for datetimes with whole seconds)
* The UTC zone is just always formatted as `Z`, whereas the `+00:00` offset is
  formatted as `+00:00`.
* Olson names of time zones are not supported at all.

### Noda time

<https://nodatime.org/2.4.x/userguide/text>

* Looks fairly well thought-out and compact.
* Each entity gets its own format directives, no one-size-fits-all patterns here.
* Patterns are the Unicode ones.
* For composite things (like `OffsetDateTime`), custom patterns for the components
  (like, in this example, `LocalDate` and `LocalTime`, and `Offset`)
  are specified in `<` `>` brackets.

### Darwin

Playground that can be used as a testbed: <https://nsdateformatter.com/>

API reference:
<https://developer.apple.com/documentation/foundation/nsdateformatter>
Documentation:
<https://developer.apple.com/library/archive/documentation/Cocoa/Conceptual/DataFormatting/Articles/dfDateFormatting10_4.html#//apple_ref/doc/uid/TP40002369-SW1>

* Provides predefined formatters for ISO-8601.
* Implements exactly the Unicode format strings.
  Probably just calls the ICU library underneath, they typically do that.
* By default, formatters use the current system locale to choose
  the format for dates and times.
* Parsing is locale-dependent, and every parser knows its own locale.

**Extremely** leniet, which causes tons of problems:
* <https://stackoverflow.com/questions/26840499/nsdateformatter-still-parsing-instead-having-incorrect-format>

### Java

#### Datetimes

The old formatting facilities:
<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/text/SimpleDateFormat.html>
<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/text/DateFormat.html>

Any `DateFormat` supports such configuration:
* Setting the locale;
* Setting the calendar;
* Setting the number format;
* Setting the time zone for converting between `Instant` and `LocalDateTime`
  values.

The new formatting facilities:
<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatter.html>
<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html>

`SimpleDateFormat` and `DateTimeFormatter` generally follow the Unicode,
but introduce some things of their own.

* Optional sections in format strings, surrounded by `[` and `]`.
    * Can be skipped on parsing.
    * When formatting a datetime object that doesn’t support a given field,
      if it’s in an optional section, the section is not formatted.
    * Sections can be nested.
* “The next directive should be padded with spaces, not zeros” directive.
* Very flexible specifications of how offsets should be outputted: the number
  of components to round to, whether `:` should be present, whether to
  use `Z` as the string for `00:00:00`, and whether to add a localized `GMT`.
* Specifying just the hour is enough for `LocalTime`.
* <https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/chrono/AbstractChronology.html#resolveDate(java.util.Map,java.time.format.ResolverStyle)>
  is a description of how fields are combined into values to return.
* Locale-aware, number format-aware.
  When parsing `Instant` via the `LocalDateTime` fields, timezone-aware.
* When parsing a year whose format is two digits long, treat it as being
  in the interval
  `time when the parser was created - 80 years .. time when the parser was created + 20 years`.

Additional highlights about `DateTimeFormatter`:

* It remembers whether it parsed a leap second.
* It remembers if it got a string `24:00` and added a day as a result.
* (`parseBest`) Can attempt to parse several types of object,
  before parsing succeeds for one of the types.
  Doesn't seem to be widely used.

Differences from `SimpleDateFormat`:

* Years can be specified in two ways: `u` is for proleptic years, whereas `y` is
  for years-of-era.
  For example, year `2 BC` is `-1` when shown/parsed as `y`, but as `2` when
  `u` is used.
* Months are no longer context-sensitive.
* `w` is defined to be a the week in a week-based-year.
* `Q/q`
* `g`
* `e/c`
* `SimpleDateFormat` only supported milliseconds; now, arbitrary fractions
  are supported.
* `A`, `N` are components for the millisecond or the nanosecond since
  the beginning of the day.
* `n` is the nanosecond of the second.
* `[`, `]` denote optional sections, which can be omitted when parsing
  and which only get printed if all components inside them are valid for the
  given entity.
* Support for padding with some number of spaces via a separate modifier.
* More clearly specified rules regarding zero-padding.
* A *lot* of ways to specify time zones: `v`, `V`, `O`, `X`, `x`, `Z`:
  full name? offset? output `Z` on zero? Also, some letters have special
  meanings when repeated, so actually, the available *different* directives
  are:
  - `v`
  - `vvvv`
  - `V`
  - `VV`
  - `z`, `zz`, or `zzz` (these are the same)
  - `Z`, `ZZ`, or `ZZZ`
  - `zzzz`
  - `ZZZZ`
  - `x`
  - `xx`
  - `xxx`
  - `xxxx`
  - `xxxxx`
  - `X`
  - `XX`
  - `XXX`
  - `XXXX`
  - `XXXXX`
  - `O`

It’s probably worth it to look through the whole list of methods in
<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html>
for an overview of what `DateFormatter` supports parsing.
Notable is `appendInstant`: ISO-8601 `Instant` is difficult to parse correctly,
so it’s not represented in terms of any other parsers.

#### Duration/Period

Printing/parsing `Duration` and `Period` doesn’t seem to be supported: https://stackoverflow.com/questions/266825/how-to-format-a-duration-in-java-e-g-format-hmmss/44343699 https://stackoverflow.com/questions/53749850/how-to-format-a-period-in-java-8-jsr310

### Rust

#### `strftime`

<https://docs.rs/chrono/0.4.7/chrono/format/strftime/index.html>

It is able to optionally parse fractional seconds:
<https://docs.rs/chrono/0.4.19/chrono/format/strftime/index.html#fn6>

Locales are seemingly not queried, the corresponding values are just hardcoded
to the POSIX defaults. There is a dead project for locale-dependent formatting:
<https://github.com/Alex-PK/chrono-locale>. It simply hardcodes the common
locales.

There's an option not to remember separate letters for space-padded variations.
Instead, use an underscore: `%_d` is the same as `%d`, but space-padded, for
example.

Other notable differences from C:
* `%:z` is `+hh:mm`/`-hh:mm` instead of `+hhmm`/`-hhmm`.
* `%+` is the ISO-8601 representation.
* `%#z` is the same as `%z`, but allows parsing just `+hh`/`-hh`.

### Haskell

#### `Data.Time.Format`

<https://hackage.haskell.org/package/time-1.13/docs/Data-Time-Format.html>

##### Modifiers

Modifiers:
- `%-z` is `%z`, but with no padding.
- `%0z` is `%z`, padding with zeros.
- `%_z` is `%z`, padding with spaces.
- `%^z` is `%z`, converted to upper case.
- `%#z` is `%z`, converted to lower case "consistently, unlike glibc".

`%0` and `%_z` accept the number to denote width:
- `%4z` is `%z`, padded to 4 characters.

`%E` is a modifier, but it is used not to denote the "era", but to have an
"alternate" format. This is only defined for `%z` and `%Z`, so that `%Ez` and
`%EZ` use `±HH:MM` instead of `±HHMM`.

##### Locales

Locales are supported. Their configuration includes:
* Full and abbreviated names of the day of the week;
* Full and abbreviated names of months;
* "AM" and "PM" strings;
* Formats for dates, times, dates + times, 12-hour-based times;
* Known time zone names.

##### Duration support

Both SI time and nominal time are supported via different data structures.

Lower-case directives mean "total whole number of", whereas upper-case
directives mean "the whole number of, that doesn't form a whole instance of the
larger unit". `%d` is the total number of days, but `%D` is the whole number of
days in [0; 7) not forming a whole week.

`%ES` and `%Es` include picoseconds with automatic precision.

##### Other differences from `strftime`

- `%q` represents picoseconds, 12 characters.
- `%Q` represents picoseconds, includes the fractional point,
  and can be 0-13 characters.
- Some things, like `%C`, don't use padding by default.

##### ISO-8601 support

Provided separately from the other things:
<https://hackage.haskell.org/package/time-1.13/docs/Data-Time-Format-ISO8601.html>
The format is not defined as a format string; instead, it's a whole set of
functions.

Also, all formats described by the ISO-8601 are implemented as functions.
