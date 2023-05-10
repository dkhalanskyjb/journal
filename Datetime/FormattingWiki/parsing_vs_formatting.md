An interface for parsing and formatting
---------------------------------------

#### Parsers and formatters as a single entity or different ones?

Considerations for them being a single entity:

* `List<Pair<A, B>>` can be represented as `Pair<List<A>, List<B>>`, but this
  would be hiding the information that the two lists have the same length.
  Likewise, if a parser is paired with a formatter, it can be assumed that this
  relationship is somehow reified.
* More compact API, with orthogonal features. No two different `chain` functions,
  where one is for parsers, and the other one for formatters.
* Can we expect that most formats people use are fairly trivial, easily parseable
  and easily formattable? Sacrificing the common use case for the complex one is
  undesirable. Needs further research.

Considerations against them being a single entity:

* In complex cases when parsers are chained, if someone wants just the parsing
  or just the formatting capabilities, it may be wrong to ask them to also
  provide code for formatting.
* Inapplicable format strings can be discovered on construction and not on
  parsing in more cases.

Compromise in the form of the marker interface
`interface ParserFormatter: Parser, Formatter` with its own chaining API:

* + Parsers are parsers, formatters are formatters, but one can have both at
    the same time.
* - A lot of entities.
* - Looks like we wouldn’t be able to discover the inapplicable format strings
    on constructions: what if this `Parser` is also a `Formatter` and is
    required for the formatting capabilities?

#### Format strings

##### Not all format strings permit parsing with them fully

* Parsing uses the same formats as formatting but default-initializes the missing values.
    * + Should not really be error-prone. Though format strings are frustrating to write, once written and checked to be working, they just work.
    * + A single entity can then straightforwardly represent both operations.
    * - Not in the spirit with the rest of the library.
* Parsing doesn’t allow for format strings that don’t provide all the information, throwing instead.
    * Throwing on construction if parsers and formatters are separate entities.
    * Throwing on attempts to parse if they are the same.
    * + Less suspicious than default-initialization.
    * - Some use cases can’t be represented, like parsing `YYYY-MM`.
* Parsing `null`-initializes and does *not* actually construct an object on its own, but only provides its components:

```
public fun LocalDateTime.Companion.parser(formatString: String): Parser<LocalDateTime> =
  Parsers.dateTime(formatString) { year, month, day, hour, minute, second, nanosecond ->
    require(year != null) { "The year was not supplied" }
    require(month != null) { "The month was not supplied" }
    require(day != null) { "The day was not supplied" }
    require(hour != null) { "The hour was not supplied" }
    require(minute != null) { "The minute was not supplied" }
    require(second != null) { "The second was not supplied" }
    require(nanosecond != null) { "The nanosecond was not supplied" }
     LocalDateTime(year, month, day, hour, minute, second, nanosecond)
  }
```

    * An alternative that assumes that formatters and parsers are a single entity:

```
public fun LocalDateTime.Companion.formatter(formatString: String): Formatter<LocalDateTime> =
  Formatters.dateTime(formatString)({ init, ldt ->
     init(ldt.year, ldt.monthNumber, ldt.dayOfMonth, ldt.hour, ldt.minute, ldt.second, ldt.nanosecond)
  }, { year, month, day, hour, minute, second, nanosecond ->
     // require(... != null) { "The ... was not supplied" }
     LocalDateTime(year, month, day, hour, minute, second, nanosecond)
  })

public fun <T> Formatters.Companion.dateTime(
   formatting: ((Int?, Int?, Int?, Int?, Int?, Int?, Int?) -> Unit, T) -> Unit,
  parsing: (Int?, Int?, Int?, Int?, Int?, Int?, Int?) -> T
): Formatter<T>
```

    * (Maybe the `formatting` argument shouldn’t accept `null` values?)
    * + Very flexible
    * + Intuitive
    * + Sidesteps the problem of `Instant` being wider than `LocalDateTime`
    * - The types are not very pleasing
        * May not be an issue: the incorrect uses will fail on the very first parsing/formatting.
    * - Seven `Int` values as arguments, like we’re back in 1970
        * These are higher-order functions, not a common sight in 1970
        * These primitive parsers are not expected to be used often.

##### Syntax and functionality

Syntax is not that important at this stage, because it can be a last-minute decision. Everyone else is using the same thing in essence: templates with directives representing the components, and when we decide on the syntax, only the directives will need to be established.

An important addition to the standard syntax can be *optional sections*: a pair of brackets such that

* If everything specified between the pair of brackets is missing during parsing, the values will be default-initialized;
* If everything in the section has the default value, nothing is output;
* Default sections can be nested.

Java, too, has optional sections https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html#optionalStart(), but with different semantics: broadly-applicable formats can be reused between structures, and if a structure doesn’t provide any of the fields required for the optional section, the section will be skipped when formatting. Given how we don’t have a `Temporal` or a huge zoo of classes in general, our needs are different: we can reasonably require different formats for different structures.


TODO
