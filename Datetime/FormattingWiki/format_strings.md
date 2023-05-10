Format strings
--------------

We treat parsing and formatting as distinctly dissimilar use cases.
When a format string suits both purposes well, that's splendid, but it seems
like this can realistically be achieved only in the simplest of cases.

### `(` and `)` (regex-like grouping)

Will be useful to limit the scope of other operators.

### `|` (or) pattern

Semantics:
- When parsing, attempt the left pattern. In case parsing
  (of the whole string) fails with it, try the right one.
  If any of the patterns lists some component but the other one does not,
  this component must have a default value. That value will be used when
  the pattern without the component is used.
- When formatting, check the left pattern. In case all the components listed
  there have their default value, try the right pattern.

The semantics of the formatting are, to my knowledge, novel.
With them, `%+%02h(:%02m(:%02s|)|)|Z` would format `+02:03:00` as `+02:03`,
`00:00:00` as `Z`, and `+02:00:00` as `02`.

The other type of format strings that allows something similar is Java's
`[` `]` optional sections. They don't allow one to simulate this behavior.
Instead, the way they affect formatting is that the text inside them will
only be formatted if all the components are present. For example, a format
`yyyy-MM-dd['T'HH:mm:ss]` will not format the time part if the time was not
passed.

The analysis shows that Java's `[` `]` are largely unused, and when they are
used, it's for parsing.
```
grep -F '[' patterns.csv
```
shows some formats that don't even make sense when formatting
(like `[.SSSSSSSSS][.SSSSSSSS][.SSSSSSS][.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]`,
which, when formatting, will just output the fractional part once for each
optional section), and searching for those that could make sense when
formatting, like <https://grep.app/search?q=yyyy-MM-dd%5B%20HH%3Amm%3Ass%5D>,
all of them seem to be only used for parsing.

The probable reason is that the data that needs *formatting* is typically
fairly regular, and one rarely needs to output a date, two date-times, and
then another couple of dates, all for the same purpose.
When it *is* required, the option is always there to just use several
different formatters. Rarely do people actually operate with generic
`TemporalAccessor`s, instead they have very specific `LocalDate`, `LocalTime`,
etc.

See also <https://bugs.openjdk.org/browse/JDK-8132536>, a request for the JDK
to add this functionality.

On the other hand, the need not to output the default values is much more
common:
* <https://github.com/EsotericSoftware/kryo/blob/master/src/com/esotericsoftware/kryo/serializers/TimeSerializers.java#L139>
* <https://github.com/signaflo/java-timeseries/blob/master/timeseries/src/main/java/com/github/signaflo/timeseries/Time.java#L98>
* All the similar code that we ourselves have.

### Specifying the size

#### Problem statement

The directives can be split into the following categories:
1. Have a known limited representation size (e.g., 2 digits), may require padding:
   month numbers, week of year numbers, week of month numbers, day of week
   number, day numbers, hours, minutes, seconds, UTC offset minutes,
   UTC offset hours, UTC offset seconds, quarter-of-year numbers.
2. Don't have a known upper bound on size:
   years, years of era, week-years.
3. Textual fields:
   month names, names of the days of the week, AM/PM markers, time zone names,
   time zone identifiers, quarter-of-year names.
4. Fractions:
   fractions of a second.

These groups have very distinct requirements for specifying the size.
1. When formatting the first group, it's important to be able to specify
   whether padding is required, and if so, whether to use spaces or zeros.
   When parsing, one must be able to configure the minimum allowed size of
   the field, and it doesn't make sense to configure the maximum size.
   For example, this is crucial in formats like `yyyyMMddHHmmss`, which are
   ambiguous if padding is not strictly enforced. The only field among those
   that has a sign is the UTC offset hour.
2. When working with years, there are three distinct requirements:
   - Zero- or space-padding to a given length.
   - Outputting the sign if the given length was exceeded.
     * These two requirements may seem orthogonal, but given how rare it is
       to see non-four-digit years, it seems that nobody at all cares.
   - Ensuring the provided length was at least the given length.
3. Not much choice here: when parsing, only the strings that much the provided
   ones make sense, and when formatting, only outputting the strings themselves
   is the correct behavior. So, configuring the size is meaningless.
4. <https://stackoverflow.com/search?q=appendFraction>
   People seem to need a lot of things from this:
   - When formatting, round to the given precision.
   - When formatting, add trailing zeros until the given precision.
   - When parsing, validate the format to have exactly the given precision.
   - When parsing, allow to vary the precision. 

#### Unicode's approach

Unicode's format strings (including Java, .NET, Darwin, etc) deal with
field length thus:
* When formatting numeric fields (categories 1 and 2), the length of the
  output is not less than the length of the directive.
  For example, `yyyy` will ensure that the year is output with at least
  four numbers, even if it means zero-padding, and `MM` will output the
  months with at least two digits.
* When formatting textual fields, the length of the output is not related to
  the length of the directive. Instead, the different numbers of letters just
  mean different sets of strings. Thus, `MMM` is the short month name,
  `MMMM` is the full month name, but `MMMMM` is a one-letter abbreviation of
  the name of the month, useful, for example, to sign the axes of charts.
  Typically, the five-letter designator, the longest one, means the shortest.
  Note also that textual fields share their pattern letters with the numeric
  fields (`M` is the month number, `MMMM` is the month name, `e` is the
  numeric day of the week, `eee` is the name of the day of the week).
* Sometimes, different numbers of pattern letters in a directive just mean
  the same. It doesn't look like there's any consistency there. For example,
  the `O` pattern only permits two directives: `O` and `OOOO`, but `Z` permits
  distinct directives `Z`, `ZZZZ`, and `ZZZZZ`--but also directives `ZZ` and
  `ZZZ` that are exactly the same as `Z`.

#### Go's approach

TODO 

### Numeric signs

The following entities need their sign specified:
* Years of all varieties,
* Offsets as a whole,
* Durations as a whole,
* Individual duration components.

#### Years of all varieties

This is probably the easiest. There don't seem to be any downsides to the
approach of Java where, if the format assumes at least a fixed number of
digits, having a year with more than that number of digits results in the
sign always being output, even if it's `+`. Most people never ever deal
with years outside of the range `0..9999`, which is evident from how Go
managed to limit the years to this range and everyone's okay with that:
I only managed to find this question:
<https://stackoverflow.com/questions/51578482/parsing-dates-with-negative-year-in-go>

Noda Time also has years in `-9999..9999`
<https://nodatime.org/2.0.x/userguide/range>.

Looking at `patterns_by_popularity.md`, we can see the distribution of
popularity in the `DateTimeFormatter` usage (unique files / the designator):
```
20491 yyyy
850   yy
802   uuuu
503   YYYY
269   y
123   yyy
94    u
52    YY
38    Y
36    uu
16    YYY
4     yyyyy
2     uuu
1     yyyyyy
```

Here,
* one-letter patterns mean "whatever number of digits",
* two-letter patterns mean "only the last two digits interest us",
* three-letter patterns mean "at least three digits",
* and four- or more-letter patterns mean "at least this number of digits, and
  if the number is exceeded, also always output the sign".

See <https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html#appendPattern(java.lang.String)>

This design is highly irregular.
* All patterns except two-letter ones output and parse the whole year,
  not just a portion of it.
  Two-letter patterns are not rare, but due to resolving ambiguously,
  they likely deserve special treatment. Let's not consider them here.
* Three-letter patterns *are* rare, and of the usages that do exist, most can
  be used interchangeably with single- or four-letter ones in the
  overwhelming majority of use cases (that is, with four-digit years).

With all this in mind, it looks like the vastly more regular approach would be:
* If the field length is unspecified, never output the `+` sign.
* If the field length is specified, always output the `+` sign on overflow.

Most people will never encounter this behavior, but it would be nice to
make the year designator be able to cover the ISO-8601 expanded representation
(see ISO-8601, 5.2.2.3) by default, like JSR 310 does.

This approach is future-proof: all the existing behavior can be emulated through
it, except the three-letter patterns. If we receive requests for exactly
outputting years with exactly three digits but no `+` sign when a four-digit
year is supplied, we could deal with this separately from the format strings,
by adding configurability to the formatter builder in this regard.

#### Offsets

TODO

#### `DateTimePeriod` and its components

This is a really tricky issue.

We allow both the individual components to have signs and the whole
duration, in a distributive manner.
```
-P1Y-2M3DT-4H2M-5S == P-1Y2M-3DT4H-2M5S
```

ISO-8601 itself does not allow the components to have different signs, so it
seems to be impossible to describe something like "a month later, and then
two minutes earlier". However, extensions to ISO-8601 do allow negative
durations, so it is possible to specify "a month earlier" as the duration
`-P1M`.

JSR 310 supports different signs per components of `Period`. However, it seems
not completely thought through.
For example, a `Period` is considered negative if at least one component
is: <https://docs.oracle.com/javase/8/docs/api/java/time/Period.html#isNegative-->
In any case, JSR 310 does not allow formatting a `Period`.

Noda time permits parsing and formatting of a pure time-based version, like
Java's `Duration`. The signs there are unambiguous, as the fields are not
orthogonal and it's always possible to normalize all fields into some number of
nanoseconds.

### Specifying the style

TODO
