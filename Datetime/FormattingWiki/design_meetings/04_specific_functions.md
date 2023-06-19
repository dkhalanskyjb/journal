Specific behavior of datetime formatting facilities
===================================================

Out-of-bounds unstructured data
-------------------------------

```kotlin
val packedFormat = LocalTime.Format.build {
  hour(minDigits = 2)
  minute(minDigits = 2)
  second(minDigits = 2)
  fractionOfSecond(minDigits = 3)
}
```

Given a string `020356123456789`, we understand that it's
`02:03:56.123456789`, since every field has an implicit max width. It could not
have been `020:356:123.456789`, for example.

However, when working with unstructured data, the values *can* be so grossly out
of bounds (like the minute-of-hour 356).

Unstructured data with the same order of magnitude is common: `24` as the hour,
`60` as the second, <https://en.wikipedia.org/wiki/24-hour_clock#Times_after_24:00>,
31th of February. There's also often misuse of `LocalDate` to mean
`DateTimePeriod` in Java, in which case 300 days is not out of the question.

What to do?

* Have different parsing mechanisms, where packed formats don't work for
  unstructured data, but in return, `12-03-300` is a valid unstructured
  "year-month-day".
* Define the maximum order of magnitude for each value and don't allow it to
  exceed that, even when the data is unstructured.
* Introduce a `maxDigits` field to each directive specifically for the sake of
  packed formats.
* For most fields, instead of `minDigits`, have `digits`, defining both the
  lower and the upper bounds explicitly. Though for most fields, the only
  valid values would be `2` and `null`.

For any case but the first one, there is a question: what to do when the
maximum digits bound is exceeded during *formatting*?

* Silently produce a normally unparseable value.
* Throw during formatting when the passed values are out of bounds.

Functions on a format
---------------------

### Formatting

#### Which functions to provide

* Bare minimum: `Format<T>.format(value: T): String`.
* Additional option:
  `Format<T>.formatTo(appendable: kotlin.text.Appendable, value: T)`
  Could make sense, as fields are appended one after another, the string is not
  manipulated as a whole.

#### Throwing behavior

* On any attempt to format, this will fail if the format makes sense for
  parsing but not for formatting.
* Depending on how we implement `reducedYear`, it may throw for some values.
* See "Out-of-bounds unstructured data".

### Parsing

#### Which functions to provide

* Bare minimum: `Format<T>.parse(input: CharSequence): T`.
* If we don't have a separate `ValueBag`, then
  `Format<T>.parseToMap(input: CharSequence): Map<DateTimeField, Any>`.
  (The bare minimum can then be factored through this, but doesn't seem like
  a realistic option in practice).
* Option: `Format<T>.parseOrNull(input: CharSequence): T?`.
* Option: also add parameters to grab a piece of string, like
  `Format<T>.parse(input: CharSequence, start: Int, end: Int)`.

  - If `start` is specified, it doesn't make sense not to specify `end` as well,
    so this probably should be a separate overload.
  - May be useful if we don't introduce `find`, for the use case of
    "find a format with a regex, then parse it."
  - In theory, could be useful for parsing some complex string where fields come
    one after another in some structured manner. It doesn't seem though like we
    have such functions for `Int`, etc.
  - When needed, can be less efficiently emulated by stripping out a substring.

#### Throwing behavior

* When a string that doesn't fit the format is passed, an exception is thrown.
* When parsing structured data (`LocalDate`, `LocalTime`, etc), boundaries are
  checked (otherwise, we couldn't construct `T`).
* When parsing unstructured data (`Map<Field, Any>` or `ValueBag`), boundaries
  are not checked, *but* the order of magnitude is checked.
  - See "Out-of-bounds unstructured data".

### Search

See <https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/find.html>,
<https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/find-all.html>.

* Bare minimum: regex + parse the substring. Problems: requires writing the
  format in both the parser and the regex, it's easy to have a discrepancy
  between them, and the behavior of `find` and `findAll` is not always best
  suited for the goal.
* `Format<T>.find(input: CharSequence, startIndex: Int = 0): T?`,
  `Format<T>.findAll(input: CharSequence, startIndex: Int = 0): List<T>`.


What to return if there are several intersecting strings with the given format?
For example, we want to parse `minute:second` and receive the string
`Today at 23:50:39`.

* Return all matches: `23:50`, `50:39`.
* **Current behavior** Return just the first one: `23:50`.
  If you want the correct result, write `(|hour:)minute:second`.
  Combined with the longest match being taken, this will get parsed as
  `(23:)50:39`.

What to return when there are several ways to parse a string? For example,
let's say we want to parse `hour:minute(|:second)` and are given the string
`Today at 23:50:39`.

* Return all matches: `listOf(23:50, 23:50:39)` (+ maybe `listOf(50:39)`).
* **Current behavior** Return the longest match: `listOf(23:50:39)`.
  Otherwise, the semantics of which directives are greedy and which aren't would
  need to be specified, like it's done in regular expressions, and we don't want
  that.

What to return if we're given the string `After 102:56:16` and want to parse
`hour:minute`?

* `02:56`.
* **Current behavior**
  Nothing, it's illegal to start parsing in the middle of a number.

When parsing `96:96:96` as a `LocalTime`, we can't do anything meaningful, but
as unstructured data, this would be parsed into
`96 hours, 96 minutes, 96 seconds`.

* **Current behavior** `find` and `findAll` also work this way:
  for structured data, no match would be found, but for unstructured data,
  `find` should return this, consistently with `parse`.
  Filtering invalid values manually later is easy.
* Invalid data when parsing may be common, but searching for it in text is
  simply too fuzzy and shouldn't be supported.

Commonly used specific directives
---------------------------------

### Years

* A zero-padded four-digit number, with a leading sign if negative.
  This is what most people are doing manually with builders: see
  <https://grep.app/search?q=appendValue%28YEAR> +
  <https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html#appendValue(java.time.temporal.TemporalField)>
* A zero-padded four-digit number, with a leading sign if negative *or*
  the number is larger than 4 digits. This is the way JSR 310 interprets
  ISO 8601.
* The last two digits of the year.
  <https://github.com/search?q=%22yy-MM+lang%3AJava+&type=code>

### Months

* Zero-padded two-digit month numbers.
* Month numbers, padded with spaces to two characters.
* Short POSIX month names: `Jan`, `Feb`, `Mar`, etc.
* Full POSIX month names: `January`, `February`, `March`, etc.
* Upper-case short POSIX month names: `JAN`, `FEB`, `MAR`, etc.
