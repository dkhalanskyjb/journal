Parser and formatter chaining
-----------------------------

### Non-chaining parsing and formatting of complex objects

If we introduce `OffsetDateTime`, `ZonedDateTime`, or some other entity that
encompasses all the components that we need parsed, then there’s no immediate
need for flexible parser and formatter chaining, and everything can be specified
in the format strings.

This is the approach that currently seems more plausible.

This documents discusses the API in case we want orthogonal entities that
permit chaining.
For example, we could introduce a separate primitive for `Instant` like
`DateTimeFormatter` does it, but we also could design the abstractions in such a
way that it becomes trivial to express as `LocalDateTime` + `ZoneOffset`.
(see the discussion on the `Instant` entity for some issues that may arise)
Ditto for `LocalDateTime` = `LocalDate` + `LocalTime`.

### Combined parsers

#### Algebra

The list below is for theoretical notes that can be safely skipped, these are
not proposals, but application of the algebraic laws to derive possible useful
operations.

* Functors:

```
fun <S, T> Parser<S>.map(fn: (S) -> T): Parser<T>
fun <S, T> Formatter<T>.comap(fn: (S) -> T): Formatter<S>

fun <S, T> ParserFormatter<S>.map(f: (S) -> T, g: (T) -> S): ParserFormatter<T>
```

* (Monoidal applicative) functors:

```
// parse S followed by T
fun <S, T, U> chain(p1: Parser<S>, p2: Parser<T>, f: (S, T) -> U): Parser<U>

// output S followed by T
fun <S, T, U> cochain(f1: Formatter<S>, p2: Formatter<T>, g: (U, (S, T) -> Unit) -> Unit):
  Formatter<U>

// just return Unit without consuming the string
fun empty(): Parser<Unit>

// output the empty string
fun empty(): Formatter<Unit>
```

* Alternatives:

```
// Assumes the model
//   Parser<S> = String -> Result<Pair<S, String>>
// Either the parser succeeds with S, leaving the specified remainder of the string,
// or it fails.
//   Formatter<S> = S -> String
// There's only one valid string representation.

// parse with `p1`, but on failure, use `p2`, etc.
fun <S> or(p: vararg Parser<S>): Parser<S>
// can be thought of as (String -> Parser<S>) -> String -> S,
// as each string will lead to a specific parser.

// Using coalternative here, Alternative for this is weird
// uses the value to determine which formatter to use.
fun <S> prod(f: (S) -> Formatter<S>): Formatter<S>
// can be thought of as (S -> Formatter<S>) -> S -> String

// on parsing, tries the parsers until something succeeds.
// on formatting, queries the selector functions until one of them returns `true`.
fun <S> or(p: vararg Pair<(S) -> Bool, ParserFormatter<S>>): ParserFormatter<S>
```

Another implementation:

```
// Assumes the model
//   Parser<S> = String -> List<Pair<S, String>>
// Nondeterministic parsing: there may be many ways for parser to finish correctly.
//   Formatter<S> = S -> List<String>
// Nondeterministic printing: there can be many outputs. (could this be useful? doubt it.)

// parse with all the parsers, collecting all the results that succeeded.
fun <S> or(p: vararg Parser<S>): Parser<S>
// can be thought of as [String -> S] -> String -> [S]

// use the already-formatted parts (there can be many) to choose the formatter to use.
fun <S> prod(f: (S, String) -> Formatter<S>): Formatter<S>
// can be thought of as (S -> String) -> S -> [String]
```

* Monads:

```
// parse S, then use `f` to decide how to parse T next
fun <S, T> bind(p: Parser<S>, f: S -> Parser<T>): Parser<T>
// generally useful, but, I think, not here.

// Monads for formatters are useless, try comonads
fun <S, T> cobind(f: (Formatter<S>) -> T, g: Formatter<S>): Formatter<T>
// why?

fun <S> extract(f: Formatter<S>): S
// why and how?
```

#### Proposals

If parsers and formatters are bundled together:

```
fun string(s: String): ParserFormatter<Unit>
fun <T> string(s: String, create: (Unit) -> T): ParserFormatter<T>

fun <S, T> ParserFormatter<S>.map(
    transformParsed: (S) -> T,
    transformBeforeFormatting: (T) -> S
): ParserFormatter<T>

fun <S, T, U> ParserFormatter<S>.chain(
    formatter: ParserFormatter<T>,
    transformParsed: (S, T) -> U,
    transformBeforeFormatting: ((S, T) -> Unit, U) -> Unit
): ParserFormatter<U>

fun <S, T, U> chain(
    formatter: ParserFormatter<S>,
    formatter2: ParserFormatter<T>,
    transformParsed: (S, T) -> U,
    transformBeforeFormatting: ((S, T) -> Unit, U) -> Unit
): ParserFormatter<U>

fun <S, T, U, W> chain3(
    formatter: ParserFormatter<S>,
    formatter2: ParserFormatter<T>,
    formatter2: ParserFormatter<U>,
    transformParsed: (S, T, U) -> W,
    transformBeforeFormatting: ((S, T, U) -> Unit, W) -> Unit
): ParserFormatter<U>

// constructing the pairs is only done once, on creation of the formatter.
// formatters use the fist argument whose predicate returns `true` on the value.
// parsers try everything in turn, until some parser successfully completes.
fun <S> alternatives(
    p: vararg Pair<(S) -> Bool, ParserFormatter<S>>
): ParserFormatter<S>

/*
offsetFormatterWithColonsAndZ =
  Formatters.alternatives(
    (offset -> offset == UtcOffset.ZERO) to Formatters.string("Z") { UtcOffset.ZERO },
    (offset -> true) to UtcOffset.formatter("%+%02h[:%02m[:%02s]]")
  )
*/
```
