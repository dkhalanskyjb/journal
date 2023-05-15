Datetime string format API implementation overview
==================================================

Requirements
------------

There are two actions on string formats: formatting and parsing.

### Formatting

#### User-visible formatting

Let's say the task is to just format a day, a month, and the local time for the
end user.

Is done mostly incorrectly nowadays.

```kotlin
val formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
```

This is incorrect:
* In the US, for example, the clocks are 12-hour.
* Different cultures think of `03/02` as either `02 Mar` or `03 Feb`.

So, for user-visible formatting, even the order and the set of the fields is
dependent on the locale.

Because there's none locale support in Kotlin at the moment, this is out of
scope of the initial release.

Examples of APIs used in real life to solve this:
* <https://developer.android.com/reference/android/text/format/DateUtils>
* <https://developer.apple.com/documentation/foundation/dateformatter/1417087-setlocalizeddateformatfromtempla>
  or its more modern incarnation
  <https://developer.apple.com/documentation/foundation/date/formatstyle>

See the discussion on Github for more details:
<https://github.com/Kotlin/kotlinx-datetime/discussions/253>

#### Machine-machine communication

Must be locale-independent. Otherwise, the results are unpredictable if we don't
control all the computers on which the program runs, and so can't be parsed
back.

Notable special case: for diagnostic messages (logs etc), people do not perform
user-visible formatting, as it is not greppable, but still want the output to
look nice.

Examples of common formats:
* `2023-01-02T23:42Z` (ISO 8601)
* `Mon, 30 Jun 2008 11:05:30 GMT` (RFC 822, 2822, 1123)
* `20160203163000`

A very common pattern: "if seconds == 0, just hours and minutes, otherwise also
include seconds." Done via several separate formatters.
Mostly to transfer data over the wire, not for diagnostics.

### Parsing

You get something it a string, you want to work with it.

Happy case: you know the format in advance.
E.g. machine-to-machine communication with an established format.
Then you just parse it.

Not-so-happy case: you know it's one of several formats, but don't know which
one. E.g. cleaning up data: parsing logs that were formatted differently at
different points in time. Common solution: regex + normal parser, or
running parsers one after another until one succeeds.

Sad case: you know it's a date, but don't know the format and can tolerate some
mistakes. E.g. web scraping. Common solution:
<https://dateutil.readthedocs.io/en/stable/parser.html>
Out of scope for us: the goal currently is to provide ways to define formats,
not to work around lack of them.

Typical issues:
* Some data is optionally present, like `16:53` vs `16:53:23`.
* Some data doesn't fit into the specified bounds, like a local time of
  `25:31` or the local date of `2023-03-40`.
* Some data doesn't form a complete entity from our library, like
  `2023-02`. It's not a `LocalDate` and should not be parsed as such.

Note: there are never things that go outside the regular languages.
Anything that can be parsed via a specialized mechanism can also be parsed with
very simple regular expressions. However, they are comparatively unreadable:
`(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(|:(\d{2})(|.(\d+)))`,
with the subsequent need to count the indices of match groups and also to
manually assemble the data structures from the read data.

Internal concepts
-----------------

### Fields

Examples: year, month, day, 24-hour hour, 12-hour hour, minute.

Some part of a data structure, independent of its representation.

```kotlin
internal interface FieldSpec<in Target, Type> {
    /**
     * The function with which the field can be accessed.
     */
    val accessor: Accessor<in Target, Type>

    /**
     * The default value of the field, or `null` if the field has none.
     */
    val defaultValue: Type?

    /**
     * The name of the field.
     */
    val name: String

    /**
     * The sign corresponding to the field value, or `null` if the field has none.
     */
    val sign: FieldSign<Target>?
}


internal interface FieldSign<in Target> {
    /**
     * The field that is `true` if the value of the field is known to be negative, and `false` otherwise.
     */
    val isNegative: Accessor<in Target, Boolean>
    fun isZero(obj: Target): Boolean
}

```

`Accessor<A, T>` is a pair of functions: the getter `(A) -> T?` and the setter
`(A, T) -> Unit`. The getter may return `null` because the values being parsed
and formatted are (potentially) incomplete.

`defaultValue` represents whether there is some value that we don't consider
worth mentioning in the field. If a field has its default value and the format
allows avoiding formatting it, the value won't be formatted. For example,
seconds-of-minute have the `defaultValue` of 0, as do nanoseconds-of-second.

`sign` represents the numeric sign. The same `sign` object can be shared among
several fields. For example, for the UTC offset `-03:30`, both hours (`03`) and
minutes-of-hour (`30`) have the same sign, `-`. This is important in cases like
`-00:30`: here, hours are zero and don't have a sign, but the whole offset is
still negative.

Nitty-gritty details:

Except for the very special case of years, there don't seem to be any cases
where a field could sensibly have a sign in some representations but not others.
As a result, the field itself knows about its sign, even if it violates the
separation of concerns a bit.

The year field is treated as a special non-signed value, despite occasionally
being parsed/formatted with a sign.

### Field representations

Examples: four-digit year, POSIX month name, day of month as a Roman number.

The set of rules to format and parse a field.

```kotlin
internal interface FieldFormatDirective<in Target> {
    /**
     * The field parsed and formatted by this directive.
     */
    val field: FieldSpec<Target, *>

    /**
     * The formatter structure that formats the field.
     */
    fun formatter(): FormatterStructure<Target>

    /**
     * The parser structure that parses the field.
     */
    fun parser(): ParserStructure<Target>

    // ...
}
```

### Formats

Examples: ISO 8601 date format, "4-digit years, then 2-digit months, then
2-digit days, then 2-digit hours, then 2-digit minutes, then, optionally,
2-digit seconds, followed, optionally, by variable-length the fraction-of-second
value."

A sealed interface:
```kotlin
internal sealed interface FormatStructure<in T>

/**
 * Formats from the [formats] list, one after another.
 */
internal class ConcatenatedFormatStructure<in T>(
    val formats: List<NonConcatenatedFormatStructure<T>>
) : FormatStructure<T>

internal sealed interface NonConcatenatedFormatStructure<in T> : FormatStructure<T>

/**
 * The basic building block: the representation of a single field.
 */
internal class BasicFormatStructure<in T>(
    val directive: FieldFormatDirective<T>
) : NonConcatenatedFormatStructure<T>

/**
 * A string literal.
 */
internal class ConstantFormatStructure<in T>(
    val string: String
) : NonConcatenatedFormatStructure<T>

/**
 * The shared sign of a subformat.
 */
internal class SignedFormatStructure<in T>(
    val format: FormatStructure<T>,
    val withPlusSign: Boolean,
) : NonConcatenatedFormatStructure<T>

/**
 * One of the formats from the [formats] list.
 */
internal class AlternativesFormatStructure<in T>(
    val formats: List<ConcatenatedFormatStructure<T>>
) : NonConcatenatedFormatStructure<T>
```

These are probably self-explanatory:
* `ConcatenatedFormatStructure` places the subformats one after another.
* `BasicFormatStructure` is the format of one field directive.
* `ConstantFormatStructure` is a string literal.

Non-obvious ones are `SignedFormatStructure` and `AlternativesFormatStructure`.

When formatting, `SignedFormatStructure` checks the signs of all the fields
in the format that it wraps. If all of them are not plus and at least one of
them is minus, then `-` is output, and subsequently, in the subformat,
everything is output without the `-` sign, since the signs are inverted.
Otherwise, either `+` is output if `withPlusSign` is set, or nothing is.

Example (pseudocode):
```kotlin
SignedFormatStructure([
  "P",
  WholeDays,
  "D",
  "T",
  WholeHours,
  "H"
], withPlusSign = true)

//   10 days + 4 hours -> +P10DT4H
// - 10 days + 4 hours -> +P-10DT4H
//   10 days - 4 hours -> +P10DT-4H
// - 10 days - 4 hours -> -P10DT4H
```

When parsing, the inverse happens: if `-` is parsed, the fields in the subformat
are expected not to have a sign; if `withPlusSign` is set, `+` is parsed, but
then, everything proceeds as usual.

`AlternativesFormatStructure` works differently for formatting and parsing, but
in a way that everything that can be formatted by a format can be parsed back
with the same format. When parsing, formats in the list are tried one after
another, and that's it.

When formatting, only some forms of `AlternativesFormatStructure` are allowed,
and that is, those where later alternatives have all the fields from the earlier
ones plus, possibly, some fields that define a default value.

When formatting, the alternative that is taken is the one such that the later
ones don't provide any new information about this object.

Examples:
* "hour, colon, minute, (either nothing or (colon, second))".
  Here, seconds have a default value, so "colon, second" is a valid extension
  of "nothing". When formatting `16:45:30`, we'll see `16:45:30`, as
  "30 seconds" is extra information, but when formatting `16:45:30`, we'll
  just see `16:45`.
* "`Z` or (offset sign, offset hour, offset minute)".
  Everything in the UTC offset has the default value of 0.
  When formatting the UTC offset 0, we'll see `Z`.

Why:

This seems to be the most useful semantics to pair with parsing of alternatives.
For formatting, this doesn't make sense in general: for example, how does one
format "either (24-hour hour, colon, minute) or (12-hour hour, colon, minute,
space, AM/PM marker)"? If we just always choose the first one, the second one
becomes just useless extra information.

Also, in most places, as soon as parsing and formatting become non-trivial, they
are handled by different formats. This leads to formats that are nonsensical
when used for formatting, like (in Java's
https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatter.html
syntax) `"[[yyyy/]M/dd][[ ]h:m[:s][.SSS] a][[ ]H:m[:s][.SSS]]"`.
Here, `[]` denotes an optional section, which means, for formatting, that it
should be output if the field is present in the passed object.

### Field containers

`FieldSpec` requires that the accessors for fields are read-write. However,
the values we normally operate on are immutable, like `LocalDate`.

To support mutability, we introduce separate internal interfaces...

```kotlin
internal interface DateFieldContainer {
    var year: Int?
    var monthNumber: Int?
    var dayOfMonth: Int?
    var isoDayOfWeek: Int?
}

// internal interface DateTimeFieldContainer: DateFieldContainer, TimeFieldContainer
```

... and their implementations:

```kotlin
internal class IncompleteLocalDate(
    override var year: Int? = null,
    override var monthNumber: Int? = null,
    override var dayOfMonth: Int? = null,
    override var isoDayOfWeek: Int? = null
) : DateFieldContainer
```

This way, we can define a `month: FieldSpec<DateFieldContainer, Int>` field that
works with anything that can store a date.

### Parsers and formatters

The engines for parsing and formatting are separate and independent.

Formatters have the following API:
```kotlin
internal sealed interface FormatterStructure<in T> {
    fun format(obj: T, builder: StringBuilder, minusNotRequired: Boolean = false)
}
```

Here, the result of formatting is appended to the `builder`. `minusNotRequired`
controls whether the minus sign should be output for values that are negative.
For example, when formatting `-2 days -1 hour` as `-P2DT1H`, `minusNotRequired`
will be set to `true` for `P2D1H`.

Parsers expose this API:
```kotlin
internal class Parser<Output : Copyable<Output>> {
    fun match(input: CharSequence, startIndex: Int = 0): Output

    fun<T> find(input: CharSequence, startIndex: Int = 0, transform: (Output) -> T?): T?

    fun<T> findAll(input: CharSequence, startIndex: Int = 0, transform: (Output) -> T?): List<T>
}
```

* `match` checks if the itput matches the parser rules fully.
* `findAll` goes through all indices, and for each index, attempts to parse
  the longest possible string such that it satisfies the parser rules and
  `transform` returns a valid value.
* `find` is an optimized version of `findAll` composed with `firstOrNull`.

The need for `transform` is explained directly below.

### Parsing and resolution

Given the rules `yyyy-mm-dd` and a string `2023-02-30`, should we parse it?
The value is clearly erroneous, but it also clearly satisfies the required
pattern.

*Sometimes we do want to parse it, sometimes we don't*.

When we only want to work with well-formed values and fail eagerly in case we
encounter something strange, `2023-02-30` is a reason to crash the program.
However, when trying to leniently parse the output from a system that is known
to produce wrong values in some consistent manner, we still want to access the
parsed values and do something with them.

The solution for this issue is to avoid value validity checks at the parsing
time and delegate them to a separate resolution stage.

`LocalDate.find("yyyy-mm-dd")` can only return valid `LocalDate` values.
For this reason, it will call `find` on an `Parser<IncompleteLocalDate>` with
`transform` set to the `(IncompleteLocalDate) -> LocalDate` fallible conversion
function. Thus, in the text `"2023-02-30" is an invalid date, "2023-02-28" is
probably what was meant`, `find` will return `2023-02-28`.

However, we also have `ValueBag`, which is a formless bunch of datetime values
without validity checks. So, `ValueBag.find("yyyy-mm-dd")` on that text will
find `2023-02-30`, leaving the decisions about how to perform value resolution
to the end user.

### Building formats

There are some shared internal concepts that help with format construction.

First, a simplified version without any format string support:

```kotlin
internal class AppendableFormatStructure<T>(
    spec: BuilderSpec<T>
) {
    fun add(format: FormatStructure<T>)

    fun build(): ConcatenatedFormatStructure<T>

    fun createSibling(): AppendableFormatStructure<T>
}
```

Here, `FormatStructure` has the `<in T>` type argument. This means that, if
`T` implements `DateFieldContainer`, any `FormatStructure<DateFieldContainer>`
is also a `FormatStructure<T>`. `AppendableFormatStructure` does some tricks
with variance to make this idea work.
* `add` appends any `FormatStructure<T>` to the end of the format,
* `build` returns the accumulated format,
* `createSibling` creates a new, empty `AppendableFormatStructure<T>`.

Format string support adds some new challenges. As implemented, format strings
allow treating sequences of letters as field format directives: for example,
treating the string `yyyy-mm-dd` as a sequence of directives "4-digit year,
a dash, 2-digit month, a dash, 2-digit day." Additionally, they allow separating
parts of a format into sections depending on the part of the data structure that
they work with: for example, the string `ld<yyyy-mm-dd> lt<hh:mm:ss>` describes
a format "4-digit year, a dash, 2-digit month, a dash, 2-digit day, a space,
and 2-digit hour, minute, and second, separated by colons." Here, `ld` and
`lt` describe that what's described inside is a local date format and a local
time format, correspondingly. Because of this, the same letter `m` can be used
to mean months in one context, minutes-of-hour in another, minutes of UTC offset
in yet another, not shown here, and also months and minutes in date-time
periods.

```kotlin
internal abstract class BuilderSpec<in T>(
    val subBuilders: Map<String, BuilderSpec<T>>,
    val formats: Map<Char, (Int) -> FormatStructure<T>>,
)

internal class AppendableFormatStructure<T>(
    spec: BuilderSpec<T>
) {
    // New functions

    fun formatFromSubBuilder(
        name: String,
        block: AppendableFormatStructure<*>.() -> Unit
    ): FormatStructure<T>

    fun formatFromDirective(letter: Char, length: Int): FormatStructure<T>

    // Already covered

    fun add(format: FormatStructure<T>)

    fun build(): ConcatenatedFormatStructure<T>

    fun createSibling(): AppendableFormatStructure<T>
}
```

`BuilderSpec` specifies the behavior of format strings.
`subBuilders` describes how strings like `ld` and `lt` map to the sub-builders,
and `formats` explain which formats `yyyy` and `mm` should be treated.
`formatFromSubBuilder` and `formatFromDirective` implement the corresponding
logic.
