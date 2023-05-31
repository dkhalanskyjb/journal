Overview of SimpleDateFormat
============================

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

| Directive   | Meaning                                                             |
| ----------- | ------------------------------------------------------------------- |
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

Should we editorialize?
-----------------------

A format like `yyyy-MM-dd` is problematic in two cases:

* (Java) If at a later point strict resolving needs to be enabled, then
  this format will fail, since the year-of-era was supplied but the era was not.
* (Inherent) Trouble with the years below 1 AD.
  - Years below 1 can't be parsed.
    Example: <https://github.com/StarRocks/starrocks/blob/362528867139fde9f0cfa5300e3872cdb92ee156/fe/fe-core/src/main/java/com/starrocks/common/util/DateUtils.java>
  - Years before 1 BC are just formatted as positive numbers.
  - In Java, the choice of scales for years is documented and sensible,
    defaulting to the ISO chronology. In the Foundation library, if the locale
    of the formatter is explicitly set to something, the Gregorian calendar is
    chosen, but otherwise, the current system calendar will be used.
    This is not documented AFAIK.

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

Another small question: should we parse format strings leniently or strictly?
The pattern `yyyy-MM-ddTHH:mm:ss` will be recognized by Java as correct, with
`T` being treated as a literal, but if at a later point the T directive is
introduced, the code will break. Do we want to accept patterns that are this
malformed? (The Unicode format does occasionally expand to include new and new
pattern letters).

How prominent should this API be?
---------------------------------

General pros of supporting this format in some form:

* Exists for a long time already, and so is visually familiar to many people.
* There is a huge body of existing questions about it on Stack Overflow, many
  of them of the form "how can I obtain this output." Nearly effortless
  copy-pasting of common formats.
  - As seen in the "top formats" list, most formats don't need to be copy-pasted
    if the format string patterns are comprehensible.
* ChatGPT 3 can write these format strings when asked for Java's
  `DateTimeFormatter`.
  - When asked for Kotlin's format strings, it starts hallucinating.

### Option 1: only add a deprecated method

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

### Option 2: add both a proper API and this one with equal prominence

Pros:

* All the usual use cases are supported with the maximum comfort.
* The use case of sharing a format string between several datetime libraries
  is also fulfilled.

Cons:

* Tough to market our own format, there's the risk of people using Java's
  format due to familiarity.

### Option 3: make this the main format string API

Pros:

* ChatGPT knows this format and can explain it.
  - Maybe it makes sense to provide the "explanation string" extension function
    either way to make formats self-documenting.
* We can handwave all the issues with the format with "well, it's a standard"
  and avoid any effort related to format string support.
* Easy migration path to the other options if we call the function verbosely
  enough from the start (e.g. "`fromUnicodePattern`" instead of
  "`fromPattern`").

Cons:

* At best write-only in tough cases, at best read-only in normal cases.
* Can only be written by carefully consulting the reference, there is no system.
* Was never meant for machine-machine communication, and the design choices in
  the formats reflect that.
* Seems suitable for building foundational systems by datetime specialists,
  but not for the rank-and-file programmers.
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
* Comes with the expectations that some random format strings will work, but
  they only will if they are locale-oblivious (which may not be obvious in case
  of common strings like `Jan`, `Feb`, etc) and concern the entities that
  we already have (for example, no week-based dates or quarters of year).
* *Is a poor conceptual fit for the modern understanding of localized
  formatting. Even if we only use it for non-localized formatting, the existing
  body of knowledge may confuse people.*
