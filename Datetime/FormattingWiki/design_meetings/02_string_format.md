Format Patterns for Datetime Formats
====================================

SimpleDateFormat format
-----------------------

Actually, what's now regulated by the Unicode standard for
[Locale Data Markup Language](https://unicode.org/reports/tr35/tr35-dates.html#Date_Format_Patterns)
started as the project to bring *internationalization* into JDK 1.1:
<https://en.wikipedia.org/wiki/International_Components_for_Unicode#Origin_and_development>
The initial standard for Locale Data Markup Language
(<http://web.archive.org/web/20040705191155/https://www.unicode.org/reports/tr35/tr35-2.html#%3ClocalizedPatternChars%3E>),
published in 2004, even references
[`SimpleDateFormat` the way it was at the time](http://web.archive.org/web/20040707061113/http://java.sun.com/j2se/1.4.2/docs/api/java/text/SimpleDateFormat.html).

In modern terms, each `SimpleDateFormat` pattern defines a correspondence
between `Calendar`, `Locale`, and a temporal object on one side, and a pointed
set of strings on the other.

The current meaning of symbols in a pattern can be found in the table:
<https://unicode.org/reports/tr35/tr35-dates.html#Date_Field_Symbol_Table>
- Does not define space padding (probably since it's meaningless for
  internationalization). Java extended the syntax to support it.

Top formats by popularity:

| `SimpleDateFormat` format      | Format as currently implemented              |
| ------------------------------ | -------------------------------------------- |
| `yyyy-MM-dd`                   | `ld<yyyy-mm-dd>`                             |
| `yyyy-MM-dd HH:mm:ss`          | `ld<yyyy-mm-dd> lt<hh:mm:ss>`                |
| `HH:mm`                        | `lt<hh:mm>`                                  |
| `HH:mm:ss`                     | `lt<hh:mm:ss>`                               |
| `dd/MM/yyyy`                   | `lt<dd/mm/yyyy>`                             |
| `yyyyMMdd`                     | `ld<yyyymmdd>`                               |
| `dd.MM.yyyy`                   | `ld<dd.mm.yyyy>`                             |
| `yyyy-MM-dd HH:mm`             | `ld<yyyy-mm-dd> lt<hh:mm>`                   |
| `yyyy`                         | `ld<yyyy>`                                   |
| `dd-MM-yyyy`                   | `ld<dd-mm-yyyy>`                             |
| `yyyyMMddHHmmss`               | `ld<yyyymmdd>lt<hhmmss>`                     |
| `yyyy-MM-dd HH:mm:ss.SSS`      | `ld<yyyy-mm-dd> lt<hh:mm:ss.fff>`            |
| `yyyy/MM/dd`                   | `ld<yyyy/mm/dd>`                             |
| `yyyy-MM-dd'T'HH:mm:ss`        | `ld<yyyy-mm-dd>'T'lt<hh:mm:ss>`              |
| `MM/dd/yyyy`                   | `ld<mm/dd/yyyy>`                             |
| `yyyy/MM/dd HH:mm:ss`          | `ld<yyyy/mm/dd> lt<hh:mm:ss>`                |
| `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` | `ld<yyyy-mm-dd>'T'lt<hh:mm:ss.fff>'Z'`       |
| `HH:mm:ss.SSS`                 | `lt<hh:mm:ss.fff>`                           |
| `MMMM`                         | Has localized fields                         |
| `dd.MM.yyyy HH:mm`             | `ld<dd.mm.yyyy> lt<hh:mm>`                   |
| `yyyy-MM-dd'T'HH:mm:ss'Z'`     | `ld<yyyy-mm-dd>'T'lt<hh:mm:ss>'Z'`           |
| `yyyy-MM`                      | `ld<yyyy-mm>`                                |
| `hh:mm a`                      | Has localized fields                         |
| `dd/MM/yyyy HH:mm:ss`          | `ld<dd/mm/yyyy> lt<hh:mm:ss>`                |
| `M/d/yyyy`                     | `ld<m/d/yyyy>`                               |
| `h:mm a`                       | Has localized fields                         |
| `yyyy-MM-dd HH:mm:ss.S`        | `ld<yyyy-mm-dd> lt<hh:mm:ss.f>`              |
| `HH`                           | `lt<hh>`                                     |
| `dd-MM-yyyy HH:mm:ss`          | `ld<dd-mm-yyyy> lt<hh:mm:ss>`                |
| `dd/MM/yyyy HH:mm`             | `ld<dd/mm/yyyy> lt<hh:mm>`                   |
| `dd`                           | `ld<dd>`                                     |
| `yyyy-MM-dd'T'HH:mm:ssZ`       | `ld<yyyy-mm-dd>'T'lt<hh:mm:ss>uo<+hhmm>`     |
| `yyyy.MM.dd`                   | `ld<yyyy.mm.dd>`                             |
| `HHmmss`                       | `lt<hhmmss>`                                 |
| `dd MMMM yyyy`                 | Has localized fields                         |
| `dd.MM.yyyy HH:mm:ss`          | `ld<dd.mm.yyyy> lt<hh:mm:ss>`                |
| `MM`                           | `ld<mm>`                                     |
| `dd MMM yyyy`                  | Has localized fields                         |
| `yyyy-MM-dd HH:mm:ss.SSSSSS`   | `ld<yyyy-mm-dd> lt<hh:mm:ss.ffffff>`         |
| `yyyy-MM-dd'T'HH:mm:ss.SSS`    | `ld<yyyy-mm-dd>'T'lt<hh:mm:ss.fff>`          |
| `yyyy-MM-dd'T'HH:mm:ss.SSSZ`   | `ld<yyyy-mm-dd>'T'lt<hh:mm:ss.fff>uo<+hhmm>` |
| `yyyy-MM-dd hh:mm:ss`          | `ld<yyyy-mm-dd> lt<hh:mm:ss>`                |
| `yyMMddHHmmss`                 | Year resolution must be configured           |
| `MM/dd/yyyy HH:mm:ss`          | `ld<mm/dd/yyyy> lt<hh:mm:ss>`                |
| `yyyy-MM-dd-HH-mm-ss`          | `ld<yyyy-mm-dd>-lt<hh-mm-ss>`                |
| `d MMMM yyyy`                  | Has localized fields                         |
| `yyyyMMddHHmmssSSS`            | `ld<yyyymmdd>lt<hhmmssfff>`                  |
| `yyyyMM`                       | `ld<yyyymm>`                                 |
| `yyyyMMddHHmm`                 | `ld<yyyymmdd>lt<hhmm>`                       |
| `H:mm`                         | `lt<h:mm>`                                   |

Followed by a **long** tail of various custom things.

The list of directives that, in total, cover 95% of all the patterns:

| `dd`, `d`   | day of year                                                         |
| `yyyy`      | 4-digit year of era (in theory, calendar-dependent, may bite)       |
| `MM`, `M`   | month number                                                        |
| `mm`, `m`   | minute                                                              |
| `HH`, `H`   | 24-hour hour                                                        |
| `ss`, `s`   | second                                                              |
| `S`, etc    | fractional second portion of the given length                       |
| `MMM`       | short month name (localized)                                        |
| `MMMM`      | long month name (localized)                                         |
| `a`         | AM/PM marker (localized)                                            |
| `hh`, `h`   | 12-hour hour                                                        |
| `yy`        | the last two digits of the year                                     |
| `uuuu`      | 4-digit ISO year                                                    |
| `Z`         | an attempt to print `Z`, or the UTC offset `+0000`, `+0130`         |
| `YYYY`      | an attempt to write `yyyy`, or the week-based year                  |
| `EEE`       | short name of the day of the week (localized)                       |
| `z`         | localized time zone name (localized)                                |
| `EEEE`      | full name of the day of the week (localized)                        |
| `XXX`       | UTC offset in the form `Z`, `+01:30`                                |
| `X`         | UTC offset in the form `Z`, `+0130` (mostly used with TimeZone.UTC) |
| `y`         | arbitrary-length year of era                                        |
| `yyy`       | three-digit year, probably a typo                                   |
| `VV`        | timezone identifier                                                 |

People clearly don't test their code well:
* Usages of `DateTimeFormatter` with `yyyy-mm-dd` (year, minute, day) were found
  in > 100 repositories.
* An estimate: about half of the usages of `Z` (`+0000`, `-0330`) is incorrect
  and just `'Z'` (a literal) was meant instead.
  - A huge pitfall is that the `Z` format doesn't work with `'Z'`, `X` does.
  - About a quarter of *all* UTC offset usages.
  - Leads to beautiful hacks like `yyyy-MM-dd'T'HH:mm:ss[Z]['Z']`.

A typical usage looks like this (<https://github.com/StarRocks/starrocks/blob/362528867139fde9f0cfa5300e3872cdb92ee156/fe/fe-core/src/main/java/com/starrocks/common/util/DateUtils.java>):
```
public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
```
No separate constants, just immediately caching the formatter.

Two questions:

### Should we editorialize?

A format like `yyyy-MM-dd` is problematic in two cases:

* (Java) If at a later point strict resolving needs to be enabled, then
  this format will fail, since the year-of-era was supplied but the era was not.
* (Inherent) Trouble with the years below 1 AD.
  - Years below 1 can't be parsed.
  - Years before 1 BC are just formatted as positive numbers.
  - TODO: check on some Apple hardware what happens when trying to format/parse
    a date when the system calendar is Buddhist. Maybe this is broken as well.
    In Java, the behavior is documented and sensible, defaulting to the
    ISO chronology.

A more tricky example is the infamous `YYYY`, which almost always gives correct
results, but at the start or the end of the year, contains an off-by-one error.

With pitfalls like this one, we have a choice:

* Try to infer what the user actually meant and do that.
* Throw an error, explaining to the user that their format is not what they
  think it is.
* Both, via a flag.
  - Is the flag optional? Does it default to the lenient interpretation or to
    the accurate interpretation?

Against throwing errors:

* Migration in existing code bases has more friction.
  Common formats that could work automatically for almost all cases, and do so
  more reliably than in the source in case of inference, will require fixing
  some errors in the format first.

Against intent inference:

* There are now several ways to do the same thing: the right way (`uuuu`) and
  the wrong ways (`yyyy`, `YYYY`).
* If the format gets extracted to a constant that is used in two types of
  formatters (ours and someone else's), there may be difference in behavior.
  - Even if *our* behavior is right, we could have at least thrown an error to
    explain the possible mistake so that all the formatters benefit from it.
* There is a huge body of existing questions about it on Stack Overflow.
  If we adopt this format but change it, these questions and answers may add to
  the confusion.

Against a flag:

* If someone wants inference and is going to learn about a flag and set it, they
  could just as well replace their `yyyy` with `uuuu` under our guidance.

### How prominent should this API be?

Pros of this format:

* Exists for a long time already, and so is visually familiar to many people.
* There is a huge body of existing questions about it on Stack Overflow, many
  of them of the form "how can I obtain this output." Nearly effortless
  copy-pasting of common formats.
  - As seen in the "top formats" list, most formats don't need to be copy-pasted
    if the format string patterns are comprehensible.
* Widely documented.
  - Will only add confusion if we adapt some of the behavior.
* ChatGPT 3 can write and explain these format strings when asked for Java's
  `DateTimeFormatter`.
  - When asked for Kotlin's format strings, it starts hallucinating.

Cons of this format:

* At best write-only in tough cases, at best read-only in normal cases.
* Can only be written by carefully consulting the reference, there is no system.
* Was never meant for machine-machine communication, and the design choices in
  the formats reflect that.
* Seems suitable for building foundational systems by datetime specialists,
  but not for the rank-and-file programmers.
* Surprising behavior in corner cases.
  Example: <https://github.com/StarRocks/starrocks/blob/362528867139fde9f0cfa5300e3872cdb92ee156/fe/fe-core/src/main/java/com/starrocks/common/util/DateUtils.java>
  Here, `yyyy` was used for parsing, but because it's year-of-era and not
  the ISO year, the formats were deprecated as they "don't support year 0000
  parsing." Instead, `uuuu` should have been used.
* Specific implementations vary a lot in their behavior when it comes to
  parsing. Translating behavior one-to-one is impossible unless we parameterize
  the format builder heavily.
  So, *which semantics* should we use when adopting the format strings?
  - ICU (and Objective-C, which uses it) implements extremely lenient parsing
    that attempts to fix any kind of broken data:
    <https://unicode.org/reports/tr35/tr35.html#Lenient_Parsing>
  - Java provides *lenient* and *strict* parsing and *lenient*, *strict*, and
    *smart* resolving mechanisms, all of which have their downsides and are
    generally not understood well.

#### Option 1: only add a deprecated method

```kotlin
@Deprecated(
  "Provided for ease of migration. " +
  "Please call `toString` on the resulting `Format` and copy the format " +
  "to your code directly.",
  level = DeprecationLevel.WARNING)
fun FormatBuilder<T>.appendUnicodeFormatString("uuuuMMdd")
```

Pros:

* Immediately teaches how to idiomatically use our format API without much
  effort.

Cons:

* If someone want to parse/format platform-native objects as well as
  the kotlinx-datetime objects *and* had already an existing format defined,
  they'll have to duplicate the logic, making code evolution more challenging.

#### Option 2: add both a proper API and this one with equal prominence

#### Option 3: make this the main format string API


printf-style format strings
---------------------------

### In general

#### Formatting

Example: `%02d:%02d:%02.3f`

```c
int hours = 23;
int minutes = 54;
float seconds = 21.5213;
printf("%02d:%02d:%02.3f\n", hours, minutes, seconds);
// 23:54:21.521
```

Consists of literal characters and directives.
* Literal characters are output as usual.
* Directives (called "conversion specifications") have the general form
  `%[$][flags][width][.precision][length modifier]conversion`

`\n` is handled by the C compiler, `printf` only sees the actual newline
character.

Directly after `%`, one may write `n$` to denote that the value should be
taken from the `n`'th argument.

Flags are single characters that provide
* padding with zeros,
* padding with spaces on the left,
* padding with spaces on the right,
* characters that show the type of the field (`0` for octal,
  `0x` for hexadecimal, terminal `.` for floating-point numbers, etc)
* initial sign,
* grouping of digits (like `1'000'000`).

`width` is the total width of the output.

`.precision` is the length after the floating point. For strings, this is the
max length to output.

Width and length can be either numbers or `*n$` to denote that the `n`'th
argument is the desired width.

`length modifier` defines how big a type to expect (e.g., `Int` vs `Long`).

Some examples of directives:
* `%o`, output as unsigned octal number.
* `%# 3o`, output as unsigned octal number, always prepending 0, space-padded on
  the left to at least three characters.
* `%-6.8s`, output at least 6 and at most 8 characters of the given string,
  space-padded on the right.
* `%+08lld`, output at least 8 characters of the given `Long` value,
  zero-padded, always outputting the sign.
* `%2$d`, print the 2nd argument as a decimal number.
* `%2$0*2$d`, print the 2nd argument as a decimal number, with the width equal
  to that same number, space-padded.

#### Parsing

A bit similar to formatting, but actually different.

Format strings consist of:
* Whitespace characters. They match `[ \t\n]*`.
* Directives, starting with `%`.
* Ordinary characters. They match themselves.

Directives are of the form `%[$][flags][width][length modifier]conversion`.

`n$`, like with formatting, can be used to specify the argument to interact
with.

Flags:
* "Do not assign to anything, just skip".
* "Digits may be grouped" (like `1'000'000`).
* "I won't provide the buffer to hold the result, allocate it on your own".

The width now defines the maximum width to read. For example, `%5s` means
"parse at most 5 non-white-space characters into a string".

Parsing is lenient and doesn't care about the intricacies of formatting:
* Numeric signs can always be present.
* `0xABCD` and `0Xabcd` are parsed with the same directives.
* Floating-point number directives read all the notations they know of.
* etc.

Some new directives or behaviors are there:
* `%[a-z]`, `%[^0-9]`, etc: groups of characters. Stored in a string.
* `%c` is used in `printf` to format chars, but `%5c` will read exactly
  5 characters, perfect for fixed-width input.
* `%n` doesn't parse anything, instead outputting the number of characters
  already read.

Python format strings
---------------------

### In general

#### Beginning: the modulo operator on strings

See <https://docs.python.org/3/library/stdtypes.html#printf-style-string-formatting>

```python
"Hi, %s! %s?" % ("me", "What's up") # "Hi, me! What's up?"
"Hi, %(name)s! %(question)s?" % { "name": "me", "question": "What's up" }
```

Everything is like in C's `printf`, except that, instead of the `n$`
position-selecting syntax, we have `(name)` map-querying syntax, and the
type length need not be specified.

Everything else is as before:

```python
"%(birthYear)+08d" % { "birthYear" : 21333 } # '+0021333'
```

#### 2002: template strings

<https://peps.python.org/pep-0292/>

Very simple wrapper 

```python
from string import Template
Template('$who likes $what').safe_substitute({"who": "tim"}) # 'tim likes $what'
Template('$who likes $what').substitute({"who": "tim"}) # KeyError: 'what'
Template('$who likes $what').substitute({"who": "tim", "what": "$100"}) # 'tim likes $100'
Template('$who likes $what').substitute(who = "tim", what = "$100") # 'tim likes $100'
```

Seems like that's the full API.

#### 2006: the `.format` function on strings

<https://peps.python.org/pep-3101/>

The form of the format is specified in
<https://docs.python.org/3/library/string.html#format-specification-mini-language>:
`[[fill]align][sign]["z"]["#"]["0"][width][grouping_option]["." precision][type]`

We should go through this group by group.
* `[[fill]align]` is for padding. `fill` (space by default) is the character to
  pad with, and `align` is `<` for left alignment, `>` for right alignment,
  `^` for centering, and `=` is only valid for numeric types and means
  justifying by putting the padding *inside* the field, like `+000000120`.
* `sign` is `+`, `-`, or ` `, to denote that the sign must be output always,
  only when it's minus (the default), or that space should be used instead of
  the `+`.
* `z` means that if the number becomes zero after rounding, it should forget its
  sign. For example, `-0.001` with a single digit after the decimal point is
  usually `-0.0`, but with the `z` flag, it's `0.0`.
  Motivation: <https://peps.python.org/pep-0682/>
* `#` is still the "make the type of the field explicit" directive (prepend
  `0x` for hex, `0` for octal, etc).
* The `0` flag is equivalent to the `0=` as `[[fill]align]`.
* `width` is the total width of the output.
* `grouping_option` is either `,` or `_`, and that character is used as a
  separator for thousands. <https://peps.python.org/pep-0378/>,
  <https://peps.python.org/pep-0515/>.
  It is not locale-aware, for that one should use the `n` type.
* `precision` is as usual, the width after the decimal point.

If `type` is specified, it explains how to format the field. It defaults to the
most typical format. For example, if a number is passed, it will attempt to
format as a decimal number: `"{}".format(-100) # '-100'`.
If `x` is passed, it will format in hex (signed):
`"{:#x}".format(-100) # '-0x64'`.

A notable format is `n`, which will output the number with locale-specific
separator between thousands.

Many examples are available at
<https://docs.python.org/3/library/string.html#format-examples>

#### Modern times: the f-strings

A special syntax that allows calculating arbitrary expressions when
interpolating strings and to describe the format parameters as in `.format()`,
separated by the colon.

For example:
```python
f'{2 + 2 + 119 :=+8}' # '+    123'
```
Here, after `:`, there's the format `=+8`, which means "insert padding between
signs and the number, always output the sign, pad to 8 characters."
