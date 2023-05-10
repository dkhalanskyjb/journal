Locales
-------

See `patterns_by_popularity.txt`.

Locale-dependent directives like `MMM`, `MMMM`, `a`, `EEE`, or `EEEE`
are quite high in the list of usage.

### Locale-specific format strings

Not just specific directives may be locale-dependent, the whole strings may
be. For example, the order of fields and the separators between them are
locale-dependent.

Usages: <https://grep.app/search?q=DateTimeFormatter.ofLocalized>

### Demand for localized names/markers

Search query: <https://stackoverflow.com/search?q=datetimeformatter+locale>.

Suggestions to use locales:
* <https://stackoverflow.com/questions/32152258/how-can-i-create-a-custom-datetimeformatter-that-respects-the-locale-specific-or>
* <https://stackoverflow.com/questions/6543174/how-can-i-parse-utc-date-time-string-into-something-more-readable>
* <https://stackoverflow.com/questions/4594519/how-do-i-get-localized-date-pattern-string>
* <https://stackoverflow.com/questions/54015963/java-8-datetimeformatter-not-translating-timezone-based-on-locale>
* <https://stackoverflow.com/questions/54230982/get-locale-specific-date-time-format-in-java>
* <https://stackoverflow.com/questions/56802168/how-can-i-format-day-and-month-in-the-locale-correct-order-in-java>

Note: most of the suggestions discourage using `MMM`, `MMMM` and such for
human-readable formatting, and instead recommend using locale-specific
format specifications. Typically, `MMM` and `MMMM` should only be used when
the locale is known beforehand, as otherwise, it's more idiomatic to present
the date and time with the correct locale-specific field order.

Suggestions to hard-code locales are not unique to Java:
* <https://stackoverflow.com/questions/44156831/nsdateformatter-date-short-style-has-different-values>

### Demand for standard names that also happen to be in some locales

However, sometimes, despite these being locale-aware formats, they are needed
for the exact strings contained in them. For example, some people need to
output exactly `Tuesday` on Tuesdays, not `Вторник` or `Dienstag`.
In these cases, it is incorrect to use the format without hard-coding the
locale, as changing the environment in which the computation happens may also
affect the result. Some people are aware of this and do hard-code the locale:
<https://grep.app/search?q=ofPattern.%2Aocale&regexp=true>

With specific patterns:
<https://grep.app/search?q=EEE.%2Aocale&regexp=true>
<https://grep.app/search?q=MMM.%2Aocale&regexp=true>

Recommendations to hard-code the locale:
* <https://stackoverflow.com/questions/4216745/java-string-to-date-conversion/4216767#4216767>
* <https://stackoverflow.com/questions/44925840/java-time-format-datetimeparseexception-text-could-not-be-parsed-at-index-3/44928297#44928297>

### Localized separators

The .NET ecosystem provides additionally locale-specific *separators*:
* `/`, a separator between date components. Example: `2022.01.04`.
* `:`, a separator between time components. No example provided.

The ability to separate fractional seconds with `,` and not `.` depending on
the locale may be needed:
* <https://github.com/golang/go/issues/23134>
* <https://stackoverflow.com/questions/38908226/comma-fails-parsing-by-java-time-instant>
* <https://stackoverflow.com/questions/38886005/datetimeformatter-to-handle-either-a-full-stop-or-comma-in-fractional-second>
* <https://stackoverflow.com/questions/64588798/iso-8601-parser-in-java-spring-with-comma-separator>
* <https://bugs.openjdk.org/browse/JDK-8132536>

To solve this, the .NET ecosystem provides `.` that is always a dot, no matter
what locale, and `;`, that always outputs a dot, but may also parse a comma.

ISO-8601 does actually require that both `.` and `,` are able to separate
fractional parts (see ISO-8601, 3.2.6).

### Getting locale-specific information

No support in Kotlin yet.

We could, however, use platform-specific means of accessing some commonly-needed locale-dependent definitions:

* JVM: `(DateFormat.getDateInstance(DateFormat.SHORT, java.util.Locale.getAvailableLocales().toList().find { it.toString() == "ru_RU" }!!) as java.text.SimpleDateFormat).toPattern() == "dd.MM.y"`
  Notably, this is also the approach taken by the 310bp project:
  <https://github.com/ThreeTen/threetenbp/blob/main/src/main/java/org/threeten/bp/format/SimpleDateTimeFormatStyleProvider.java>
* JS: <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toLocaleTimeString>
  may be abused to infer the representation in the current locale.
* Native: `strftime`, `strptime`, though it has its issues.
  The main problem is not invoking `strptime` (one can simply change
  the environment variables in runtime to affect which locale gets chosen)
  but the fact that the system on which the code runs may simply not have
  the locale info installed.
  The JVM bundles that information with itself, as do web browsers, but on
  Native, it may just be absent.
* Do we want to provide localized variations as format strings or as predefined
  formatters like `LocalDate.Formatters.localized`? Both?

Java itself uses the
[Common Locale Data Repository](https://cldr.unicode.org/index) by default
since Java 9: <https://openjdk.org/jeps/252>.

Maybe we could gather that as well, though this would require special-casing on
Android to use the already-present CLDR data to reduce the APK size:
<https://developer.android.com/guide/topics/resources/internationalization>
