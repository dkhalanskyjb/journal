Good day to everyone reading!

This is a journal where I'm going to write about my work at my job.
The intention for now is to write in it every day or two and describe briefly
my findings: which links I found useful, what new things I learned, what are
my plans for the following day, etc.

This does *not* have the goal of being well-structured, it's just a stream of
consciousness.
For example, currently, at my job, I'm involved in two projects:
<https://github.com/Kotlin/kotlinx.coroutines>, and
<https://github.com/Kotlin/kotlinx-datetime>.
I'm not going to go out of my way to separate my notes between the two projects!
If my day is dedicated to some coroutine thing and I suddenly decide that I'm in
the mood for juggling some time zones, this means that, suddenly, my notes about
a `kotlinx-coroutines-test` thingamajig will suddenly include a dump of the IANA
TZDB identifiers that map to "Central European Standard Time".
I'll disregard any typos and will not obsess over a nice way to put something.

My aspiration is to often restructure as much as possible from this file to
various public design documents. I would like the results of my work to be as
public as possible, and the common way to do it is to commit often, but my work
is research-heavy and my findings often don't directly result in production code
being written, and just comitting my ramblings to the project repository
directly is also clearly not the way.

The goals of all of this:
* Somehow storing my findings that don't nicely fit into code.
  Time and again have I encountered a situation where I needed to recall or find
  some thing that I clearly encountered before, only to find that my browser
  history has long forgotten about it, as have the search engines.
* Sometimes, I'm unsure about decision that I myself took. Could I have been
  missing something? Sometimes, the details of why some decisions were made slip
  from my mind, and a journal that would allow me to *consistently* find out the
  *why* of my own actions would be helpful.
* More transparency for the Kotlin community. You think we're just playing
  ping-pong or installing IDEA plugins here in the ivory tower of
  hey-look-we-have-our-own-language (which is not as impressive as it may sound
  to some)? Go ahead, feel free to stalk what I do and why your favorite feature
  **still** hasn't landed.
* This can be nicely used to demonstrate my usefulness. With this, collecting
  the 10 screens of the most salient lines of code will be a piece of cake.
  I don't see *my* leadership suddenly changing in a manner that would require
  this, but, ah.
* <https://en.wikipedia.org/wiki/Bus_factor> Yes, Gregory, this is also written
  specifically so that, if I'm not available for some reason, you have in theory
  a chance to retrace the reasoning behind my commits at least, even if everyone
  else has forgotten about it and the institutional knowledge evaporated.
  (I don't in fact work with any Gregory, it's just a hypothetical).
* Practice writing. Here, I can be witty, or I can sound tired, or I can be
  rambly, or anything else that has no place in actual design documents. Just
  express myself more naturally.

2022-12-12
----------

I was thinking regarding the last design meeting regarding date-time formatting.

1. These two requirements are related:

* We need to be able to refuse patterns like `YYYY-MM-dd` even if we do add
  ISO week dates support.
* We need to consider whether `DateTimeValueBag` should just list all of its
  fields in a flat manner (like `NSDateComponents`) or nest them, like
  ```kotlin
  class DateTimeValueBag {
    // val localDateTime: PartialLocalDateTime // stores links to the two fields below
    val localDate: PartialLocalDate
    val localTime: PartialLocalTime
    val offset: UtcOffset?
    val timeZone: TimeZone?
  }
  ```

This is actually the question of the resolution mechanism, though not phrased
as such initially. When there are multiple different representations of the same
thing (24 hours/12 hours + AM marker, year + month + day/week-year + week number
+ week day/year + quarter-of-year + day-of-quarter etc), resolving allows to
obtain one representation from another. For example, if a 24-hour time is
supplied, it's certainly possible to format it as 12-hour time.

Java has a very elaborate resolution mechanism. Resolution is performed
field-by-field, and each field is responsible for its own resolution, and it
can access the other fields. See the implementations of
<https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/temporal/TemporalField.html#resolve(java.util.Map,java.time.temporal.TemporalAccessor,java.time.format.ResolverStyle)>
However, some of the fields are combined explicitly outside of this mechanism:
see `mergeTime` in
<https://github.com/ThreeTen/threetenbp/blob/main/src/main/java/org/threeten/bp/format/DateTimeBuilder.java>

A read regarding ISO week dates: <https://bugs.python.org/issue12006>
This is the change that adds support of parsing week dates to Python.

It would be nice to have transparent resolving rules.
If there are two fields in `DateTimeValueBag`, `val localDate` and
`val isoWeekDate`, there's no confusion at all about why `YYYY-MM-dd`
doesn't output anything when you put a `LocalDate` in: of course,
`MM-dd` is successfully taken from `localDate`, but `YYYY` should be
taken from the (empty) `isoWeekDate`, which signals an error.

If we were to store all the values in one bag with no hierarchy, then not
filling `val isoWeekYear` and `val isoWeekNumber` when supplied with a
`LocalDate` looks very strange, as this information can be inferred.

2. Ilya mentioned that he had a use case for
<https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/find.html>
on a date-time formatter: he was receiving unstructured data from an API that
had dates in one of several formats listed somewhere in the freeform text.

The parser engine that I have currently is based on deterministic stack
machines, but maybe it would be more convenient to emulate an NFA instead.
The change is not very intrusive.


Have been staring at the *specific* numbers behind usage patterns, like
"months are mentioned in 80%+ of all patterns" and
"AM markers are only in 4%- of all patterns, whereas hours-of-am/pm are in about
5% of all patterns". Right now I want to analyze this the other way,
"what is the set of patterns we should support to achieve at least N%", because
looking at it like this, we could get an impression that we can just cut AM/PM
for now, as well as everything else that is not all that popular, but different
patterns use *different* rare directives, so it may well turn out that, by
removing everything that's rare, we're actually removing the ability to write
50% of all the patterns.

Sounds a bit tough though to analyze this properly. Would need to try to
minimize the number of directives somehow, instead of just greedily traversing
the pattern list.

Managed to get fairly close, won't spend any more time on this.

... Or so I thought. Also taking into account which directives are easy to
implement if we already decided that some other ones should be implemented
turned out to affect the results significantly.

Discovery of the day!

For 90% of cases, it is enough to support:
* Years, month numbers, and days;
* Hours-of-day, minutes, seconds, and fractions of seconds;
* Short and long localized month names;
* Hours-of-am/pm, am/pm;
* Offsets of the form +HHMM;
* Short names of weekdays.


2022-12-13
----------

### Morning: datetime

Since I decided to work on the parser now, it's a good idea to also revise the
simplified handling of timezone identifiers.

There are only two kinds of usages of timezone identifiers in format strings:
* Delimited by `[]` or spaces, and
* Appended at the end of the text to whatever number there was last.
  Probably this is used in place of printing or parsing an offset.

If we make an assumption that no identifiers contain `[]` or start with a number,
we can greedily parse an arbitrary string into a timezone ID and not break any
use cases.

On the other hand, 310bp parses timezone identifiers properly, using the
timezone database:
<https://github.com/ThreeTen/threetenbp/blob/973f2b7120d2c173b0181bde39ce416d1e8edfe0/src/main/java/org/threeten/bp/format/DateTimeFormatterBuilder.java#L3516>

However, this is at odds with our plans to support alternative timezone
databases. How would parser know which timezone database to use?

Noda Time requires passing the timezone database if one wants to parse TZDB
identifiers:
<https://nodatime.org/2.4.x/api/NodaTime.Text.ZonedDateTimePattern.html#NodaTime_Text_ZonedDateTimePattern_WithZoneProvider_NodaTime_IDateTimeZoneProvider_>

### Day: coroutines

My musings were interrputed by needing to adjust the Coroutines library for the
new release of Kotlin itself.
Looks like this commit <https://github.com/Kotlin/kotlinx.coroutines/commit/61ba10d9029e4990636f9a41dce24b462e33026e>
has to go, because the feature itself will be postponed a whole compiler
version.

We act as both producers and consumers of reactive publishers, and that commit
adds the `& Any` to both places. What I need to research is, what happens when
the code that has this feature enabled interacts with code that doesn't.
* Obviously, if we add `& Any` to our consumers, nothing will break for anyone.
  The opposite: if someone has the feature enabled, they will never break when
  using our producers, even in the polymorphic case.
* On the other hand, adding `& Any` to the consumers will cause the polymorphic
  case of of an arbitrary reactive publisher to fail.
  This wouldn't be a problem if the language itself prohibited non-`& Any`
  publishers, but that is postponed.

Maybe we could still keep the upper bounds on publishers we create, but not the
ones we consume?

Uh, no, turns out it's not actually the case. `Maybe<T>` is not recognized as
`Maybe<out T>`. That's a bummer. So, adding `& Any` to either producers or
consumers, while being different in principle, will break code due to the lack
of the variance knowledge.

So, there are two possible answers:
* Embrace the breaking change, which the committee approved, and just suppress
  the error, or
* Revert the commit, delaying the breaking change until the language itself is
  ready.

The second option seems more preferable. So, reverting it is.

(While waiting for an answer about the future of the feature,
I looked at `ReadonlySharedFlow` in order to answer
<https://github.com/Kotlin/kotlinx.coroutines/issues/3552>).


So, it turns out, I misunderstood what the changes to the compiler are.
Denis clarified all these things, and together we managed to arrive at the fix.

So, it *will* be impossible to construct `MaybeSource<T>` and such instead of
`MaybeSource<T & Any>`, and the only thing that actually broke is some small
implementation detail. It is possible to adapt that implementation in a manner
that tricks the compiler into thinking that everything's okay on both the old
and the new compiler. Most importantly, the whole commit is okay and shouldn't
be reverted.

After some cleanup that even allowed me to remove a suppressed warning, I
pushed the result to `develop`:
<https://github.com/Kotlin/kotlinx.coroutines/commit/ccde0c7d777f2b36a5f770533893b1cd5acedafc>


2022-12-14
----------

Yesterday, when I tried to have a call, it turned out that my mic is not
working. I went down the rabbithole of trying to fix it, and it turns out that's
purely a software issue: for some reason, my headphones are stuck in the "music
sounds good" mode and don't switch to "sound is bad, but the mic is working".
Manually switching profiles in `pavucontrol` works just fine. Maybe, before, I
just always had the "music sounds bad" mode without noticing?

After a while, I give up. Maybe a system restart will help. Else, I'll just have
to remember to switch my mic on in three different places.
Not much worse than just two.

No such luck, the system restart doesn't help.

Today, I'm a bit sleepy, not sure I could sensibly start writing code
immediately, so I'll look at the pending reviews requested from me in
<https://github.com/Kotlin/kotlinx.coroutines/pulls?q=is%3Apr+is%3Aopen+user-review-requested%3A%40me+>
Fun fact: I tried to edit this link so that anyone who reads this sees the PRs
pending a review from me-me, not them-"me", but apparently this doesn't work:
<https://github.com/Kotlin/kotlinx.coroutines/pulls?q=is%3Apr+is%3Aopen+user-review-requested%3A%40dkhalanskyjb+>
Also, apparently `@me` is actually someone: <https://github.com/me/>.


On my lunch break, I've read about SPJ's new language for Epic Games:
<https://simon.peytonjones.org/assets/pdfs/haskell-exchange-22.pdf>
Throughout the first half, I was bored: it's just a list monad, but with the
support of narrowing. However, the later section dedicated to logic programming
is really inspiring and got me thinking. Can't wait to see how they managed to
have a sensible type checker with types being arbitrary predicates.


### Datetime

Making some final touches on the parser machinery. Mostly making sure that error
messages make sense, instead of being like Java's not-very-helpful "could not
parse at index 2".

TODO tomorrow:
* Make the state of the parser commands linked to the commands via types.
* Rework the data structure representing the composite commands, so that the
  code dealing with the next command is more straightforward.


2022-12-15
----------

What a deeply unpleasant day. It snowed the whole night, and on 5 AM, a
bulldozer showed up and started to clear the roads very loudly for a whole
hour. This reminded me of living near an airport for a while.
Needless to say, I barely slept since then. Everything's fuzzy.

Still, Curry is said to have invented the Y combinator when he had his
anesthesia for tooth removal, so this is not really an excuse for not working.

Some thoughts I had: Ilya made a point that we probably need to have a
`DateTimeFormatter.ofJavaPattern` or something that would allow creation
of our `DateTimeFormatter` from Java's (and possibly other
[Unicode](https://unicode-org.github.io/icu/userguide/format_parse/datetime/))
pattern strings (in addition to)/replacing
`kotlinFormatPatternFromJavaFormatPattern: (String) -> String`.
The use case that he provided was this: let's say someone already has format
strings in their codebase, but then, adding *another* format string just to
support our patterns is some added friction. As an example, he provided an
internal JetBrains codebase that has an object with all the time format
constants listed: would our API require that they duplicate the object when
migrating gradually?

Turns out, the point is moot, as that codebase *doesn't* use Java's API,
it uses Moment.js: <https://momentjs.com/>. An important difference is that
Moment.js doesn't provide a `DateTimeFormatter` at all, it uses only the
format strings, the same way `strptime`/`strftime`, Go's formats etc do.
In Java, it's not very common to see date-time format strings that are not
just directly passed into a `DateTimeFormatter.ofPattern` invocation, and
the reason is, `DateTimeFormatter` is quite expensive to construct, so it
makes sense to have it as a singleton.

*However*, having `DateTimeFormatter.ofJavaPattern` *would* be useful if we
(and that's most likely) keep the current course of defining
a `DateTimeFormatterBuilder` instead of making string-based API unwieldly with
various options. In that case, some format strings from Java etc. would not
translate into a *format string* in our format but would translate just fine
into a `DateTimeFormatter`. With the facilities to print the `DateTimeFormatter`
in a form of code to reproduce it, we'd recreate the behavior of
`kotlinFormatPatternFromJavaFormatPattern`, but more generally.


Looking through some issues in the coroutines repository. Can't do anything
more structured in this state. Answered a couple of people. Looking at
<https://github.com/Kotlin/kotlinx.coroutines/issues/3551>, it seems like
the suggestion has merit.
<https://docs.oracle.com/javase/8/docs/api/java/io/InterruptedIOException.html>
does mention that this exception implies the thread being interrupted.
I think this exception exists as a separate thing is that a class can't be
inherited from two classes at the same time, and `IOException` and
`InterruptedException` are both classes. It's more likely that people have
mechanisms in place for handling an `IOException` anyway around all I/O
operations, so it makes sense that `IOException` was chosen to represent this
state. What can you do, restart the operation?

So, semantically, `InterruptedIOException` seems to be
an `InterruptedException`.

While researching this, I encountered
<https://github.com/square/okhttp/issues/3945>. From there, I wrote my thoughts
under the issue itself.


Worked some more on the parser.


Looked into <https://momentjs.com/docs/#/parsing/>, which has some interesting
details about parsing strictness.

2022-12-16
----------

Wow, this feels like an elaborate joke. Today, the bulldozer woke me up at
5 AM again.

Also, made a new GitHub avatar yesterday via
<https://stablediffusionweb.com/#demo>. I asked it for a cartoony spider that
had a thread in one hand and a clock in another (get it? because spiders weave
threads, and I work on the coroutines library, so, threads?.. fibers?.. and
the datetime, so the clock?.. nevermind). The result was wrong, but somehow
even better than I intended, because it conveys the *actual* feeling of dealing
with the broken concept of a datetime.

![My new avatar](/images/avatar-2022-12-15.jpeg)

Since this is another slow day, I'm going to sort through my 87 browser tabs
(all work-related, there are no things like "A Comprehensive Analysis of
Gothic II's Quest Structure, Part 16: The Wording in the Journal is Genious").
The <https://addons.mozilla.org/en-US/firefox/addon/tree-style-tab/> extension
certainly helps immensely--I can confidently navigate this number of tabs only
thanks to this extension--but there are some articles that I opened for their
insights and never had time to read.

The first in line is <https://www.zacsweers.dev/ticktock-desugaring-timezones/>,
dedicated to separate time zone databases on Android, a thing we're planning to
have in the datetime library (<https://github.com/Kotlin/kotlinx-datetime/issues/201>).

While looking for the link to the issue for the previous paragraph, I
accidentally answered a couple of issues.


While looking through the issues, I decided to refactor some code. I don't
trust myself to *write* code now, but refactoring is fine.
So, I overhauled my implementation of the mechanism that would allow us to catch
more exceptions during tests:
<https://github.com/Kotlin/kotlinx.coroutines/pull/3449/commits/e5051ba57b9ff1b76c5bf2ad19d62eb59f93f049>

Staring more closely at the machinery that handles uncaught exceptions, I see
that there are many places where we try to handle uncaught exceptions, but defer
to the global machinery in case something is wrong. I think `handle` should
be replaced with `tryHandle`. I wrote a prototype to see how it would work, but,
unfortunately, it seems like the change is not binary-compatible. I'll push it
to the repo anyway when all tests pass, so that we could start a discussion, but
my hopes are not high: <https://github.com/Kotlin/kotlinx.coroutines/pull/3554/>


Some notes from the Tick Tock article linked above:
* Often, loading a timezone has to happen on the application startup, which
  incurs a delay. <https://github.com/gabrielittner/lazythreetenbp> is one
  solution for this. We should support laziness from the ground-up in order to
  be viable.
* We should research whether we should provide alternative timezone databases
  in both the Java resource format and as an Android asset.


Also, I'll be on a trip for the next week. I'll keep working on the datetime
formatting implementation, but I don't know whether I'll have Internet
connection. So, to anyone reading this, don't be surprised if this repo doesn't
get updated throughout the next week!


2022-12-18
----------

Some fascinating discoveries I've made when going to sleep.

First, an update to the model of the "or" operator in the parsers/formatters.
Initially, I thought of the following model:
* Given a pattern `A(B|C)D`, a parser will attempt to parse `ABD` and `ACD`
  (and, typically, just return the first successful match).
* Given a pattern `A(B|C)D`, a *formatter* will check if all of the fields
  mentioned in `B` have their default values.
  If so, `ACD` is formatted instead of `ABD`. For example, with a pattern
  `hh:mm(:ss|)`, a time would format to `hh:mm` instead of `hh:mm:ss` if the
  seconds are zero, and with a pattern `(+hh(:mm(:ss|)|)|Z)`, an offset would
  format as `+hh:mm:ss` if hours, minutes, and seconds were all included,
  as `+hh:mm` if seconds were zero, `+hh` if the minutes were also zero, and
  finally, as `Z` if the offset as a whole is zero.

The parsing rule is completely straightforward and easy to internalize, but
I was dissatisfied with formatting: it just didn't seem right to me: why is
the correct format in case of ambiguity the last?

I understood the issue I was having better when trying to write a conversion
from `strptime`/`strftime` to the format. With the proposed semantics for `|`,
the translation would be:
- `%m` (the month) would be translated to `d<(mm|m)>`
- `%S` (the second) would be translated to `t<(ss|s|00)>`

Why so complicated?
- When parsed, `%m` and `%S` have optional zero-padding.
  So, `m` and `s` patterns need to be included for parsing.
- When formatted, both `%m` and `%S` are zero-padded.
  * The month field can't have a default value. So, `mm` will always get chosen.
  * The seconds field has the default value of 0.
    If the number of seconds is non-zero, the first pattern, `ss`, is chosen.
    However, if the number of seconds is *zero*, the first pattern has only
    default values, so the second, `s`, would be chosen. But then, `0` would be
    output. So, `|00` needs to be added so that `s`, being at its default value,
    defers to `00` being printed.

This is ridiculous. I ruminated over the alternatives.

One alternative could be to have a rule like this: in `A(B|C)D`, `B` is chosen
if either it has non-default values or `C`, too, has only default values.
This, too, doesn't sit well with me: this rule feels too full of magic.
However, the translation does become simple:
- `%m` still becomes `d(mm|m)`,
- `%S` now becomes `t<(ss|s)>`.

Feels too much like plugging a leak though. There must be a more natural rule.

And I think I found it.

Semantically, `A(B_1|B_2|...|B_n)C` builds for each `B_i` the set of fields that
it mentions that have non-default values, and then finds the earliest `i` such
that `B_i` is a maximal element when comparing the sets by inclusion.
I need to thoroughly check whether this definition is associative, but I'm sure
that it is.

The full explanation for programmers could be something like this:

> When formatting `A|B`, `A` will typically be used, unless `B` is more precise
> than `A` and precision is required. For example, `hh:mm(|:ss)` will be
> formatted as `hh:mm` if the seconds are zero, but as `hh:mm:ss` if the seconds
> are non-zero (and so `:ss` has more information than just the empty string).
>
> More accurately, when formatting `A|B`, if `B` contains all the fields from
> `A` whose value is not equal to the default one, but also some additional
> non-default values, `B` will be used instead of `A`.


2022-12-19
----------

With all this travel, I didn't have much chance to whip out my laptop and churn
out any code, but I did manage to think a lot. This turned out to be very
productive. And here I was thinking that the implementation was already good
enough and all that's left is to write some tests!

I'm sure somebody did discover this already, but I managed to imagine a thing
that is the same as zippers while also having better data locality (a good thing
but not something to worry about *now* when we just need to ship this) and, much
more importantly, easier to read for non-FP people. If any of the lurkers here
happen to know what I'm talking about, please open an issue.

So, the idea is very simple.

Let's take a linked list as an example.

```haskell
data List a = Nil | Cons a (List a)

data ListZipper a = ListZipper (List a) a (List a)

listToZipper :: List a -> Maybe (ListZipper a)
listToZipper Nil = Nothing
listToZipper (Cons a (List a)) = Just $ ListZipper Nil a
```

A zipper is equivalent to the original data structure, but "highlighting" one of
the elements, providing O(1) access to it for both reading and modification.

A lilst `[1, 2, 3, 4, 5, 6]` with `4` highlighted is equivalent to the zipper
`[3, 2, 1] 4 [5, 6]`.

```haskell
-- Focus on the next element
-- O(1)
-- [3, 2, 1] 4 [5, 6] -> [4, 3, 2, 1] 5 [6]
zipperNext :: ListZipper a -> Maybe (ListZipper a)
zipperNext (ListZipper prv cur []) = Nothing
zipperNext (ListZipper prv cur (newCur:nxt)) =
  Just $ ListZipper (cur:prv) newCur nxt

-- Focus on the previous element
-- O(1)
-- [3, 2, 1] 4 [5, 6] -> [2, 1] 3 [5, 6]
zipperPrev :: ListZipper a -> Maybe (ListZipper a)
zipperPrev (ListZipper [] cur nxt) = Nothing
zipperPrev (ListZipper (newCur:prv) cur nxt) =
  Just $ ListZipper prv newCur (cur:nxt)

-- Replace the current zipper element
-- O(1)
-- [3, 2, 1] 4 [5, 6], 10 -> [3, 2, 1] 10 [5, 6]
replaceZipperElement :: ListZipper a -> a -> ListZipper a
replaceZipperElement (ListZipper prv a nxt) b = ListZipper prv b nxt

-- Replaces the current zipper element with a zipper
-- [3, 2, 1] 4 [5, 6], [3.5, 3.2] 4.1 [4.3, 4.8] ->
-- [3.5, 3.2, 3, 2, 1] 4.1 [4.3, 4.8, 5, 6]
-- Can be generalized to the monadic bind
-- O(size of the left and rigth sides of the replacement zipper)
injectZipper :: ListZipper a -> (a -> ListZipper a) -> ListZipper a
injectZipper (ListZipper prv a nxt) f =
  let (ListZipper prv' a' nxt') = f a
   in ListZipper (prv' ++ prv) a' (nxt' ++ nxt)
```

All of these operations except the (structure-changing) injection can be done
very easily with just an array index if we put all the list elements in the
array in order.
When working with arrays, I will not go the zipper way, risking losing
data locality and making the code far less idiomatic. Any performance gains from
injecting very quickly are very situational and are negated by the lack of data
locality for small lists, which is a common argument against linked lists in
production in general.

I for one don't need *any* mutation. The reason I used zippers was to represent
the position in a complex list of commands. So, if I could use indices instead
of zippers, I would do so in a heartbeat.

And here's the thing: for any ADT, I can! If we do the naive trick of storing
all the nodes of the same type in an array, we can represent an accessor as just
an array of indices:
`[type_1, index_1, type_2, index_2, ..., type_n, index_n]`

```
parent z = {throw away the last index in the array}
next z = {try to increment the last element in the array}
prev z = {try to decrement the last element in the array}
```

I won't even be implementing the type markers, because they are always known for
my use case, but it's very nice to know that it's possible.


Reading the ICU4J source code to determine how they choose the best pattern
with the given set of fields, supposedly the backbone of
<https://developer.apple.com/documentation/foundation/nsdateformatter/1408112-dateformatfromtemplate>,
since Darwin's datetime, notably, relies on ICU in its implementation.
This method is provided in addition to
<https://developer.apple.com/documentation/foundation/nsdateformatter/1413514-dateformat>,
which allows typical [Unicode](https://unicode-org.github.io/icu/userguide/format_parse/datetime/)
strings.

Oh boy, is this a can of worms.
Required reading: <https://unicode-org.github.io/icu/userguide/format_parse/datetime/#datetimepatterngenerator>
and its implementation.

The simplest way to start understanding this is to look through the
`dateTimeFormats` tag in <https://raw.githubusercontent.com/unicode-org/cldr/main/common/main/ru.xml>
and the neighboring files (don't try to do this via GitHub's interface, like
<https://github.com/unicode-org/cldr/blob/main/common/main/ru.xml>, open the
raw versions: the files are too big).


2022-12-20
----------

Oh no.

There's the `B` Unicode directive that I found absolutely awful:

```
    B       1      appendDayPeriodText(TextStyle.SHORT)
    BBBB    4      appendDayPeriodText(TextStyle.FULL)
    BBBBB   5      appendDayPeriodText(TextStyle.NARROW)
```

Example: `in the morning`.

I wanted so badly to avoid ever dealing with this, but it doesn't look like we
can help it. If we use the CLDR, we'll *have* to know how to parse the letter
"B" and how to format dates according to it.

In the CLDR repository:
```sh
# When the user queries for a pattern without explicitly requesting that the
# period of day is included, but the pattern includes the period of day anyway.
$ git grep '<dateFormatItem id="[^B]*">.*B' common/main/
common/main/my.xml:                                             <dateFormatItem id="Ehm">E B h:mm</dateFormatItem>
common/main/my.xml:                                             <dateFormatItem id="Ehms">E B h:mm:ss</dateFormatItem>
common/main/my.xml:                                             <dateFormatItem id="h">B h</dateFormatItem>
common/main/my.xml:                                             <dateFormatItem id="hm">B h:mm</dateFormatItem>
common/main/my.xml:                                             <dateFormatItem id="hms">B h:mm:ss</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="h">Bh時</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hm">Bh:mm</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hms">Bh:mm:ss</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="Ehm">E Bh:mm</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="Ehms">E Bh:mm:ss</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="h">Bh時</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hm">Bh:mm</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hms">Bh:mm:ss</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="Ehm">E Bh:mm</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="Ehms">E Bh:mm:ss</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="h">Bh時</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hm">Bh:mm</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hms">Bh:mm:ss</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hmsv">Bh:mm:ss [v]</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hmv">Bh:mm [v]</dateFormatItem>
$ grep 'language type="my"' common/main/en.xml
                        <language type="my">Burmese</language>
$ grep 'language type="zh_Hant"' common/main/en.xml
                        <language type="zh_Hant">Traditional Chinese</language>
```

So, in traditional Chinese (and something called "Burmese"), time is formatted
without AM/PM markers, but with the "period of day" marker.

Let's take a look at `com.ibm.icu.text.DateTimePatternGenerator#types` to see if
there are any more surprising directives that we'll have to be able to represent
internally at least.

* Quarters. Unless a user requests that they are formatted, they won't be
  present.
* Week of year. ^
* Week of month. ^
* Any of the variations of the concept of timezone names. ^
* ...

Screw this, I'll be at this all day at this tempo.

```sh
$ for l in {a..z} {A..Z}; do if git grep -q '<dateFormatItem id="[^'$l']*">.*'$l'.*<' common/main/; then echo -n $l; fi; done; echo
abcdefghijklmnoprstuvwyzABGIJKLNUWY
```

These are: AM/PM markers, periods of day, weekday... no, that's not right.
Too many letters. And this is understandable: for example, when someone
requests an `M` (month), they can instead get an `L` (a standalone month), but
can they really get `L` when neither `M` nor `L` were requested?

```
$ git grep '<dateFormatItem id="[^ML]*">.*L.*<' common/main/
$
```

They can not. Ok, so I'll need to actually programmatically implement this
query.


I had a feeling I had to write down why I have to represent `A(B|C)D(E|F)` as

```
    _ B _        _ E _
   /     \      /     \
A --- C ---- D --- F ----

```

and not

```

A --- B --- D --- E
  \          \___ F
   \_ C --- D --- E
             \___ F
```

I was completely sure about this, but now I just don't see any reason in the
slightest.

Relying on the usual quality of one's memory when you know you won't be getting
nearly enough sleep is not efficient.


In general, there's really not much interesting to write. These days I'm just
putting the final touches on the locale-invariant parsing and formatting
implementation. Mostly documentation and tests at this point. The 310bp test
suite is proving useful as usual, though it doesn't help with testing the
desired properties that Java doesn't have, like the ambiguity detector in the
parser.


2022-12-22
----------

Had surgery yesterday, didn't have the strength to work, took a sick leave.

Looking at
<https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/find-all.html>

Here's an interesting example:
```kotlin
val text = "Hello Alice. Hello Bob. Hello Eve."
val regex = Regex("Hello ([^.]*)")
val matches = regex.findAll(text)
val names = matches.map { it.groupValues[1] }.joinToString()
println(names) // Alice, Bob, Eve
```

What's interesting about it is that there are more possible matches than there
are listed: `A`, `Al`, `Ali`, `Alic`, `B`, `Bo`, `E`, and `Ev` also fit.

Let's try the other way:
```kotlin
val text = "Alice, hello! Bob, hello! Eve, hello!"
val regex = Regex("([^ ]*), hello!")
val matches = regex.findAll(text)
val names = matches.map { it.groupValues[1] }.joinToString()
println(names) // Alice, Bob, Eve
```

In fact, `lice`, `ice`, `ce`, `e`, `ob`, `b`, `ve`, and `e` also match.

So, the text "Returns a sequence of **all** occurrences of a regular expression"
is not really true.

This is all an interesting question when implementing `find` and `findAll` for
datetime parsers. We probably want some consistency with the behavior of
`findAll` and `find`, and for that, we should:
* Implement eager matching. For example, parsing `12.1356` as seconds with
  fractions, we should only match `12.1356`, not `12.135`, `12.13`, or `12.1`.
  - What to do with patterns like `hh:mm:ss(|.fff)`?
    Moment.js provides a nice answer: parse as much as you can; in other words,
    parse with all the patterns and choose the longest match.
* If `n..m` is successfully parsed, don't try to also parse `n+k..m`.
  Now, this is a bit tricky semantically, so let's consider this issue
  separately.

Some problematic cases:
* Given a string `Today is 11-02` and a pattern "one- or two-digit month, then
  the day", there are two valid substrings that match: `11-02` and `1-02`.
  The second one is extraneous.
* Given a string `In 10000 years, it will be 12023-09-17` and a pattern
  "four-digit year, two-digit month, and two-digit day", we will only be able to
  match `2023-09-17`, but that's not the correct date.

These issues are basically one and the same: it's an error to start parsing in
the middle of a number.

We could hard-code this, but I don't like special-casing. For example, when
searching for a timezone ID, we, too, don't want to successfully parse the text
"MGMT" as the "GMT" time zone, or "OUTCOME" as "UTC".

So, let's see if we can realistically generalize the rule. Maybe "not when the
characters are in the same Unicode character category"? I don't know much about
character categories, so let's see what they look like for ASCII.

```kotlin
fun main() {
    for (i in 1 until 128) {
        val c = i.toChar()
        val s = when (c) {
            '\n' -> "\\n"
            '\b' -> "\\b"
            '\t' -> "\\t"
            '\r' -> "\\r"
            else -> " $c"
        }
        val n = if (i < 10) "00$i" else if (i < 100) "0$i" else "$i"
        val ss = "$n\t$s\t${c.category}"
        if (i % 2 == 0) {
            println(ss)
        } else {
            print(ss + (40 - ss.length).let { " ".repeat(it) })
        }
    }
}
```

```
001	 	CONTROL                          002	 	CONTROL
003	 	CONTROL                          004	 	CONTROL
005	 	CONTROL                          006	 	CONTROL
007	 	CONTROL                          008	\b	CONTROL
009	\t	CONTROL                          010	\n	CONTROL
011	 	CONTROL                          012	 	CONTROL
013	\r	CONTROL                          014	 	CONTROL
015	 	CONTROL                          016	 	CONTROL
017	 	CONTROL                          018	 	CONTROL
019	 	CONTROL                          020	 	CONTROL
021	 	CONTROL                          022	 	CONTROL
023	 	CONTROL                          024	 	CONTROL
025	 	CONTROL                          026	 	CONTROL
027	 	CONTROL                          028	 	CONTROL
029	 	CONTROL                          030	 	CONTROL
031	 	CONTROL                          032	  	SPACE_SEPARATOR
033	 !	OTHER_PUNCTUATION                034	 "	OTHER_PUNCTUATION
035	 #	OTHER_PUNCTUATION                036	 $	CURRENCY_SYMBOL
037	 %	OTHER_PUNCTUATION                038	 &	OTHER_PUNCTUATION
039	 '	OTHER_PUNCTUATION                040	 (	START_PUNCTUATION
041	 )	END_PUNCTUATION                  042	 *	OTHER_PUNCTUATION
043	 +	MATH_SYMBOL                      044	 ,	OTHER_PUNCTUATION
045	 -	DASH_PUNCTUATION                 046	 .	OTHER_PUNCTUATION
047	 /	OTHER_PUNCTUATION                048	 0	DECIMAL_DIGIT_NUMBER
049	 1	DECIMAL_DIGIT_NUMBER             050	 2	DECIMAL_DIGIT_NUMBER
051	 3	DECIMAL_DIGIT_NUMBER             052	 4	DECIMAL_DIGIT_NUMBER
053	 5	DECIMAL_DIGIT_NUMBER             054	 6	DECIMAL_DIGIT_NUMBER
055	 7	DECIMAL_DIGIT_NUMBER             056	 8	DECIMAL_DIGIT_NUMBER
057	 9	DECIMAL_DIGIT_NUMBER             058	 :	OTHER_PUNCTUATION
059	 ;	OTHER_PUNCTUATION                060	 <	MATH_SYMBOL
061	 =	MATH_SYMBOL                      062	 >	MATH_SYMBOL
063	 ?	OTHER_PUNCTUATION                064	 @	OTHER_PUNCTUATION
065	 A	UPPERCASE_LETTER                 066	 B	UPPERCASE_LETTER
067	 C	UPPERCASE_LETTER                 068	 D	UPPERCASE_LETTER
069	 E	UPPERCASE_LETTER                 070	 F	UPPERCASE_LETTER
071	 G	UPPERCASE_LETTER                 072	 H	UPPERCASE_LETTER
073	 I	UPPERCASE_LETTER                 074	 J	UPPERCASE_LETTER
075	 K	UPPERCASE_LETTER                 076	 L	UPPERCASE_LETTER
077	 M	UPPERCASE_LETTER                 078	 N	UPPERCASE_LETTER
079	 O	UPPERCASE_LETTER                 080	 P	UPPERCASE_LETTER
081	 Q	UPPERCASE_LETTER                 082	 R	UPPERCASE_LETTER
083	 S	UPPERCASE_LETTER                 084	 T	UPPERCASE_LETTER
085	 U	UPPERCASE_LETTER                 086	 V	UPPERCASE_LETTER
087	 W	UPPERCASE_LETTER                 088	 X	UPPERCASE_LETTER
089	 Y	UPPERCASE_LETTER                 090	 Z	UPPERCASE_LETTER
091	 [	START_PUNCTUATION                092	 \	OTHER_PUNCTUATION
093	 ]	END_PUNCTUATION                  094	 ^	MODIFIER_SYMBOL
095	 _	CONNECTOR_PUNCTUATION            096	 `	MODIFIER_SYMBOL
097	 a	LOWERCASE_LETTER                 098	 b	LOWERCASE_LETTER
099	 c	LOWERCASE_LETTER                 100	 d	LOWERCASE_LETTER
101	 e	LOWERCASE_LETTER                 102	 f	LOWERCASE_LETTER
103	 g	LOWERCASE_LETTER                 104	 h	LOWERCASE_LETTER
105	 i	LOWERCASE_LETTER                 106	 j	LOWERCASE_LETTER
107	 k	LOWERCASE_LETTER                 108	 l	LOWERCASE_LETTER
109	 m	LOWERCASE_LETTER                 110	 n	LOWERCASE_LETTER
111	 o	LOWERCASE_LETTER                 112	 p	LOWERCASE_LETTER
113	 q	LOWERCASE_LETTER                 114	 r	LOWERCASE_LETTER
115	 s	LOWERCASE_LETTER                 116	 t	LOWERCASE_LETTER
117	 u	LOWERCASE_LETTER                 118	 v	LOWERCASE_LETTER
119	 w	LOWERCASE_LETTER                 120	 x	LOWERCASE_LETTER
121	 y	LOWERCASE_LETTER                 122	 z	LOWERCASE_LETTER
123	 {	START_PUNCTUATION                124	 |	MATH_SYMBOL
125	 }	END_PUNCTUATION                  126	 ~	MATH_SYMBOL
127	 	CONTROL
```

Unfortunately, this doesn't look like something that suits us.
For example, `UPPERCASE_LETTER` and `LOWERCASE_LETTER` are different categories,
though for our purposes they are the same, whereas both `<` and `+` are
considered to be math symbols, even though a string `<+03:30>` looks fairly
natural. And, to think of it, if the pattern is something like
`(mm| m)-dd`, failing to parse `  2-16` of all things because the space is in
the middle of a span of spaces is, well, really odd.

So, it looks like special casing for letters and numbers can't be avoided.
Luckily, I can't think of any other naturally occurring spans of characters
other than spaces.


Another important thing:

```kotlin
val text = "Oh, hi!"
val regex = Regex(pattern = "(?<=Oh, )hi!")
println(regex.matchAt(text, 4)?.value)
val matches = regex.findAll(text, 3)
val names = matches.map { it.groupValues[0] }.joinToString()
println(names) // hi!
```

Lookbehind can see the part of the string before the `startIndex` position.


2022-12-23
----------

Now, about formatters. Time to clean up their implementation as well, maybe
uncovering something questionable in the process.

Formatting is very easy: just slap together a bunch of strings. The only
question is, what to format, and deciding *that* is less trivial.

Let's see how the formatter is compiled to the more efficient form.

Let's consider three formatters, `A`, `B`, and `C`:
* `N`: has field A,
* `M`: has field B,
* `O`: has both fields A and B.

Turns out that, sadly, the definition of `|` for formatting that I cooked up
(search "maximal element" in this file) is, in fact, non-associative:
* `(N|(M|O)) = O`, since `M|O = O` and `N|O = O`.
* `((N|M)|O) = N`, since `(N|M)|O = N|M = N`.

The latter clearly seems to be against the spirit of the abstraction,
though, in retrospect, that's not surprising, as finding the maximal element
of a partial order, obviously, *is* a non-associative operation.
So, we'll need to look at this globally and require that the "or" chaining
is always normalized to a list of alternatives and the question of
associativity never arises.

On the other hand, there is an issue when choosing the candidate globally:
let's say we have
* `N` with the field `A`,
* `M` with fields `B` and `C`, and
* `O` with fields `A` and `D`.

All fields don't have a default value.

This will have an unpleasant property: we will discard `N` in favor of `O`,
but then we'll choose... `M`. So, the sole existence of `O` will change the
relationship between `N` and `M`.

I should highlight that these are all just concerns about a mental model.
From what I've seen, realistically, people don't have anything so fancy in their
formats. It will always be something in the vein of "don't format seconds if
they and the nanoseconds are zero". The use case for formatters with
programmer-defined patterns is just formatting in a *specific* format for
serialization, and user-visible strings should be obtained from a locale-aware
system like `DateTimeFormatter.ofLocalizedDateTime`, and realistic patterns to
need to format are fairly simple.

Maybe, for formatting, we could simply require that the alternatives must be
listed in the order from most specific to least specific, like
`Z|+hh:mm|+hh:mm:ss`. If someone *does* have issues because of this, we'll hear
them out and will be in the better position to judge what to do next.

So, total order, here we go!

Since, in total orders, the `max` operation is associative, we do obtain that
property as well.


Wow, just saw the wildest thing:

```kotlin
val styles = mutableMapOf<DecimalStyle, MutableSet<Locale>>()
for (locale in Locale.getAvailableLocales().sortedBy { it.toLanguageTag() }) {
    styles.getOrPut(DecimalStyle.of(locale)) { mutableSetOf() }.add(locale)
}
for ((style, locales) in styles) {
    println("Style: '${style.zeroDigit}' '${style.positiveSign}' '${style.negativeSign}' '${style.decimalSeparator}', locales: $locales")
}
```

```
Style: '0' '+' '-' ',', locales: [af, af_NA, af_ZA, agq, agq_CM, ast, ast_ES, az, az__#Cyrl, az_AZ_#Cyrl, az__#Latn, az_AZ_#Latn, bas, bas_CM, be, be_BY, bg, bg_BG, br, br_FR, bs, bs__#Cyrl, bs_BA_#Cyrl, bs__#Latn, bs_BA_#Latn, ca, ca_AD, ca_ES, ca_ES_VALENCIA, ca_FR, ca_IT, cs, cs_CZ, da, da_DK, da_GL, de, de_AT, de_BE, de_DE, de_IT, de_LU, dsb, dsb_DE, dua, dua_CM, dyo, dyo_SN, el, el_CY, el_GR, en_150, en_AT, en_BE, en_CH, en_DE, en_DK, en_FI, en_NL, en_SE, en_SI, en_ZA, eo, eo_001, es, es_AR, es_BO, es_CL, es_CO, es_CR, es_EA, es_EC, es_ES, es_GQ, es_IC, es_PH, es_PY, es_UY, es_VE, ewo, ewo_CM, ff, ff_CM, ff_GN, ff_MR, ff_SN, fr, fr_BE, fr_BF, fr_BI, fr_BJ, fr_BL, fr_CA, fr_CD, fr_CF, fr_CG, fr_CH, fr_CI, fr_CM, fr_DJ, fr_DZ, fr_FR, fr_GA, fr_GF, fr_GN, fr_GP, fr_GQ, fr_HT, fr_KM, fr_LU, fr_MA, fr_MC, fr_MF, fr_MG, fr_ML, fr_MQ, fr_MR, fr_MU, fr_NC, fr_NE, fr_PF, fr_PM, fr_RE, fr_RW, fr_SC, fr_SN, fr_SY, fr_TD, fr_TG, fr_TN, fr_VU, fr_WF, fr_YT, fur, fur_IT, fy, fy_NL, gl, gl_ES, hr, hr_BA, hr_HR, hsb, hsb_DE, hu, hu_HU, hy, hy_AM, in, in_ID, is, is_IS, it, it_IT, it_SM, it_VA, jgo, jgo_CM, ka, ka_GE, kab, kab_DZ, kea, kea_CV, kk, kk_KZ, kkj, kkj_CM, kl, kl_GL, km, km_KH, ksf, ksf_CM, ky, ky_KG, lb, lb_LU, ln, ln_AO, ln_CD, ln_CF, ln_CG, lo, lo_LA, lu, lu_CD, lv, lv_LV, mgh, mgh_MZ, mk, mk_MK, ms_BN, mua, mua_CM, nl, nl_AW, nl_BE, nl_BQ, nl_CW, nl_NL, nl_SR, nl_SX, nmg, nmg_CM, nnh, nnh_CM, os, os_GE, os_RU, pl, pl_PL, pt, pt_AO, pt_BR, pt_CH, pt_CV, pt_GQ, pt_GW, pt_LU, pt_MO, pt_MZ, pt_PT, pt_ST, pt_TL, qu_BO, rn, rn_BI, ro, ro_MD, ro_RO, ru, ru_BY, ru_KG, ru_KZ, ru_MD, ru_RU, ru_UA, rw, rw_RW, sah, sah_RU, seh, seh_MZ, sg, sg_CF, shi, shi__#Latn, shi_MA_#Latn, shi__#Tfng, shi_MA_#Tfng, sk, sk_SK, smn, smn_FI, sq, sq_AL, sq_MK, sq_XK, sr, sr_BA, sr_CS, sr__#Cyrl, sr_BA_#Cyrl, sr_ME_#Cyrl, sr_RS_#Cyrl, sr_XK_#Cyrl, sr__#Latn, sr_BA_#Latn, sr_ME_#Latn, sr_RS_#Latn, sr_XK_#Latn, sr_ME, sr_RS, sw_CD, tg, tg_TJ, tk, tk_TM, tr, tr_CY, tr_TR, tt, tt_RU, tzm, tzm_MA, uk, uk_UA, uz, uz__#Latn, uz_UZ_#Latn, vi, vi_VN, wae, wae_CH, wo, wo_SN, yav, yav_CM, zgh, zgh_MA]
Style: '0' '+' '-' '.', locales: [ak, ak_GH, am, am_ET, asa, asa_TZ, bem, bem_ZM, bez, bez_TZ, bm, bm_ML, bo, bo_CN, bo_IN, brx, brx_IN, ccp, ccp_BD, ccp_IN, ce, ce_RU, cgg, cgg_UG, chr, chr_US, cu, cu_RU, cy, cy_GB, dav, dav_KE, de_CH, de_LI, dje, dje_NE, ebu, ebu_KE, ee, ee_GH, ee_TG, en, en_001, en_AG, en_AI, en_AS, en_AU, en_BB, en_BI, en_BM, en_BS, en_BW, en_BZ, en_CA, en_CC, en_CK, en_CM, en_CX, en_CY, en_DG, en_DM, en_ER, en_FJ, en_FK, en_FM, en_GB, en_GD, en_GG, en_GH, en_GI, en_GM, en_GU, en_GY, en_HK, en_IE, en_IL, en_IM, en_IN, en_IO, en_JE, en_JM, en_KE, en_KI, en_KN, en_KY, en_LC, en_LR, en_LS, en_MG, en_MH, en_MO, en_MP, en_MS, en_MT, en_MU, en_MW, en_MY, en_NA, en_NF, en_NG, en_NR, en_NU, en_NZ, en_PG, en_PH, en_PK, en_PN, en_PR, en_PW, en_RW, en_SB, en_SC, en_SD, en_SG, en_SH, en_SL, en_SS, en_SX, en_SZ, en_TC, en_TK, en_TO, en_TT, en_TV, en_TZ, en_UG, en_UM, en_US, en_US_POSIX, en_VC, en_VG, en_VI, en_VU, en_WS, en_ZM, en_ZW, es_419, es_BR, es_BZ, es_CU, es_DO, es_GT, es_HN, es_MX, es_NI, es_PA, es_PE, es_PR, es_SV, es_US, fil, fil_PH, ga, ga_IE, gd, gd_GB, gu, gu_IN, guz, guz_KE, gv, gv_IM, ha, ha_GH, ha_NE, ha_NG, haw, haw_US, hi, hi_IN, ii, ii_CN, it_CH, ja, ja_JP, ja_JP_JP_#u-ca-japanese, jmc, jmc_TZ, kam, kam_KE, kde, kde_TZ, khq, khq_ML, ki, ki_KE, kln, kln_KE, kn, kn_IN, ko, ko_KP, ko_KR, kok, kok_IN, ksb, ksb_TZ, kw, kw_GB, lag, lag_TZ, lg, lg_UG, lkt, lkt_US, luo, luo_KE, luy, luy_KE, mas, mas_KE, mas_TZ, mer, mer_KE, mfe, mfe_MU, mg, mg_MG, mgo, mgo_CM, ml, ml_IN, mn, mn_MN, ms, ms_MY, ms_SG, mt, mt_MT, naq, naq_NA, nd, nd_ZW, nds, nds_DE, nds_NL, nus, nus_SS, nyn, nyn_UG, om, om_ET, om_KE, or, or_IN, pa, pa__#Guru, pa_IN_#Guru, prg, prg_001, qu, qu_EC, qu_PE, rof, rof_TZ, rwk, rwk_TZ, saq, saq_KE, sbp, sbp_TZ, ses, ses_ML, si, si_LK, sn, sn_ZW, so, so_DJ, so_ET, so_KE, so_SO, sw, sw_KE, sw_TZ, sw_UG, ta, ta_IN, ta_LK, ta_MY, ta_SG, te, te_IN, teo, teo_KE, teo_UG, th, th_TH, ti, ti_ER, ti_ET, to, to_TO, twq, twq_NE, ug, ug_CN, , vai, vai__#Latn, vai_LR_#Latn, vai__#Vaii, vai_LR_#Vaii, vo, vo_001, vun, vun_TZ, xog, xog_UG, ji, ji_001, yo, yo_BJ, yo_NG, yue, yue__#Hans, yue_CN_#Hans, yue__#Hant, yue_HK_#Hant, zh, zh_CN, zh_HK, zh__#Hans, zh_CN_#Hans, zh_HK_#Hans, zh_MO_#Hans, zh_SG_#Hans, zh__#Hant, zh_HK_#Hant, zh_MO_#Hant, zh_TW_#Hant, zh_SG, zh_TW, zu, zu_ZA]
Style: '٠' '+' '؜' '٫', locales: [ar, ar_001, ar_AE, ar_BH, ar_DJ, ar_EG, ar_ER, ar_IL, ar_IQ, ar_JO, ar_KM, ar_KW, ar_LB, ar_MR, ar_OM, ar_PS, ar_QA, ar_SA, ar_SD, ar_SO, ar_SS, ar_SY, ar_TD, ar_YE, sd, sd_PK]
Style: '0' '+' '‎' ',', locales: [ar_DZ, ar_LY, ar_MA, ar_TN]
Style: '0' '+' '‎' '.', locales: [ar_EH, iw, iw_IL, ur, ur_PK]
Style: '০' '+' '-' '.', locales: [as, as_IN, bn, bn_BD, bn_IN]
Style: '٠' '+' '‏' '٫', locales: [ckb, ckb_IQ, ckb_IR, ig, ig_NG]
Style: '༠' '+' '-' '.', locales: [dz, dz_BT]
Style: '0' '+' '−' ',', locales: [et, et_EE, eu, eu_ES, fi, fi_FI, fo, fo_DK, fo_FO, ksh, ksh_DE, lt, lt_LT, nb, nb_NO, nb_SJ, nn, no_NO_NY, nn_NO, no, no_NO, se, se_FI, se_NO, se_SE, sl, sl_SI, sv, sv_AX, sv_FI, sv_SE]
Style: '۰' '+' '‎' '٫', locales: [fa, fa_AF, fa_IR, ks, ks_IN, lrc, lrc_IQ, lrc_IR, mzn, mzn_IR, pa__#Arab, pa_PK_#Arab, ps, ps_AF, ur_IN, uz__#Arab, uz_AF_#Arab]
Style: '0' '+' '−' '.', locales: [gsw, gsw_CH, gsw_FR, gsw_LI, rm, rm_CH]
Style: '०' '+' '-' '.', locales: [mr, mr_IN, ne, ne_IN, ne_NP]
Style: '၀' '+' '-' '.', locales: [my, my_MM]
Style: '๐' '+' '-' '.', locales: [th_TH_TH_#u-nu-thai]
Style: '۰' '+' '-' '٫', locales: [uz__#Cyrl, uz_UZ_#Cyrl]
```

I don't see the negative sign in a couple of places here. Are my fonts lacking
or is there just no negative sign?

I've also asked the code to output the Unicode number of the negative signs it
outputs: `'${style.negativeSign} (${style.negativeSign.code})'`. The missing
signs had codes 8206, 8207, and 1564. These are 200E, 200F, and 061C in hex,
respectively, and their meaning is...
* <https://en.wikipedia.org/wiki/Left-to-right_mark>
* <https://en.wikipedia.org/wiki/Right-to-left_mark>
* <https://en.wikipedia.org/wiki/Arabic_letter_mark>

So, the minus sign for fractions is actually a "reverse the text" sign in some
places?
<https://en.wikipedia.org/wiki/Modern_Arabic_mathematical_notation> doesn't seem
to mention anything to this effect.

I have a sneaking suspicion though... that it's just broken.

```kotlin
val dateTime = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("Europe/Berlin")).withYear(-2000)
val formatter = DateTimeFormatterBuilder().appendValue(ChronoField.YEAR, 4, 4, SignStyle.EXCEEDS_PAD).appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).toFormatter()
val styles = mutableMapOf<DecimalStyle, MutableSet<Locale>>()
for (locale in Locale.getAvailableLocales().sortedBy { it.toLanguageTag() }) {
    styles.getOrPut(DecimalStyle.of(locale)) { mutableSetOf() }.add(locale)
}
for ((style, locales) in styles) {
    println("Style: '${style.zeroDigit}' '${style.positiveSign}' '${style.negativeSign}' '${style.decimalSeparator}', pos_time: ${formatter.withDecimalStyle(style).format(dateTime.withYear(2000))}, neg_time: ${formatter.withDecimalStyle(style).format(dateTime)}, locales: $locales")
}
```

Both here and in the browser, which supports right-to-left, positive and
negative numbers just look the same to me:

```
Style: '0' '+' '-' ',', pos_time: 2000,057428, neg_time: -2000,057428, locales: [af, af_NA, af_ZA, agq, agq_CM, ast, ast_ES, az, az__#Cyrl, az_AZ_#Cyrl, az__#Latn, az_AZ_#Latn, bas, bas_CM, be, be_BY, bg, bg_BG, br, br_FR, bs, bs__#Cyrl, bs_BA_#Cyrl, bs__#Latn, bs_BA_#Latn, ca, ca_AD, ca_ES, ca_ES_VALENCIA, ca_FR, ca_IT, cs, cs_CZ, da, da_DK, da_GL, de, de_AT, de_BE, de_DE, de_IT, de_LU, dsb, dsb_DE, dua, dua_CM, dyo, dyo_SN, el, el_CY, el_GR, en_150, en_AT, en_BE, en_CH, en_DE, en_DK, en_FI, en_NL, en_SE, en_SI, en_ZA, eo, eo_001, es, es_AR, es_BO, es_CL, es_CO, es_CR, es_EA, es_EC, es_ES, es_GQ, es_IC, es_PH, es_PY, es_UY, es_VE, ewo, ewo_CM, ff, ff_CM, ff_GN, ff_MR, ff_SN, fr, fr_BE, fr_BF, fr_BI, fr_BJ, fr_BL, fr_CA, fr_CD, fr_CF, fr_CG, fr_CH, fr_CI, fr_CM, fr_DJ, fr_DZ, fr_FR, fr_GA, fr_GF, fr_GN, fr_GP, fr_GQ, fr_HT, fr_KM, fr_LU, fr_MA, fr_MC, fr_MF, fr_MG, fr_ML, fr_MQ, fr_MR, fr_MU, fr_NC, fr_NE, fr_PF, fr_PM, fr_RE, fr_RW, fr_SC, fr_SN, fr_SY, fr_TD, fr_TG, fr_TN, fr_VU, fr_WF, fr_YT, fur, fur_IT, fy, fy_NL, gl, gl_ES, hr, hr_BA, hr_HR, hsb, hsb_DE, hu, hu_HU, hy, hy_AM, in, in_ID, is, is_IS, it, it_IT, it_SM, it_VA, jgo, jgo_CM, ka, ka_GE, kab, kab_DZ, kea, kea_CV, kk, kk_KZ, kkj, kkj_CM, kl, kl_GL, km, km_KH, ksf, ksf_CM, ky, ky_KG, lb, lb_LU, ln, ln_AO, ln_CD, ln_CF, ln_CG, lo, lo_LA, lu, lu_CD, lv, lv_LV, mgh, mgh_MZ, mk, mk_MK, ms_BN, mua, mua_CM, nl, nl_AW, nl_BE, nl_BQ, nl_CW, nl_NL, nl_SR, nl_SX, nmg, nmg_CM, nnh, nnh_CM, os, os_GE, os_RU, pl, pl_PL, pt, pt_AO, pt_BR, pt_CH, pt_CV, pt_GQ, pt_GW, pt_LU, pt_MO, pt_MZ, pt_PT, pt_ST, pt_TL, qu_BO, rn, rn_BI, ro, ro_MD, ro_RO, ru, ru_BY, ru_KG, ru_KZ, ru_MD, ru_RU, ru_UA, rw, rw_RW, sah, sah_RU, seh, seh_MZ, sg, sg_CF, shi, shi__#Latn, shi_MA_#Latn, shi__#Tfng, shi_MA_#Tfng, sk, sk_SK, smn, smn_FI, sq, sq_AL, sq_MK, sq_XK, sr, sr_BA, sr_CS, sr__#Cyrl, sr_BA_#Cyrl, sr_ME_#Cyrl, sr_RS_#Cyrl, sr_XK_#Cyrl, sr__#Latn, sr_BA_#Latn, sr_ME_#Latn, sr_RS_#Latn, sr_XK_#Latn, sr_ME, sr_RS, sw_CD, tg, tg_TJ, tk, tk_TM, tr, tr_CY, tr_TR, tt, tt_RU, tzm, tzm_MA, uk, uk_UA, uz, uz__#Latn, uz_UZ_#Latn, vi, vi_VN, wae, wae_CH, wo, wo_SN, yav, yav_CM, zgh, zgh_MA]
Style: '0' '+' '-' '.', pos_time: 2000.057428, neg_time: -2000.057428, locales: [ak, ak_GH, am, am_ET, asa, asa_TZ, bem, bem_ZM, bez, bez_TZ, bm, bm_ML, bo, bo_CN, bo_IN, brx, brx_IN, ccp, ccp_BD, ccp_IN, ce, ce_RU, cgg, cgg_UG, chr, chr_US, cu, cu_RU, cy, cy_GB, dav, dav_KE, de_CH, de_LI, dje, dje_NE, ebu, ebu_KE, ee, ee_GH, ee_TG, en, en_001, en_AG, en_AI, en_AS, en_AU, en_BB, en_BI, en_BM, en_BS, en_BW, en_BZ, en_CA, en_CC, en_CK, en_CM, en_CX, en_CY, en_DG, en_DM, en_ER, en_FJ, en_FK, en_FM, en_GB, en_GD, en_GG, en_GH, en_GI, en_GM, en_GU, en_GY, en_HK, en_IE, en_IL, en_IM, en_IN, en_IO, en_JE, en_JM, en_KE, en_KI, en_KN, en_KY, en_LC, en_LR, en_LS, en_MG, en_MH, en_MO, en_MP, en_MS, en_MT, en_MU, en_MW, en_MY, en_NA, en_NF, en_NG, en_NR, en_NU, en_NZ, en_PG, en_PH, en_PK, en_PN, en_PR, en_PW, en_RW, en_SB, en_SC, en_SD, en_SG, en_SH, en_SL, en_SS, en_SX, en_SZ, en_TC, en_TK, en_TO, en_TT, en_TV, en_TZ, en_UG, en_UM, en_US, en_US_POSIX, en_VC, en_VG, en_VI, en_VU, en_WS, en_ZM, en_ZW, es_419, es_BR, es_BZ, es_CU, es_DO, es_GT, es_HN, es_MX, es_NI, es_PA, es_PE, es_PR, es_SV, es_US, fil, fil_PH, ga, ga_IE, gd, gd_GB, gu, gu_IN, guz, guz_KE, gv, gv_IM, ha, ha_GH, ha_NE, ha_NG, haw, haw_US, hi, hi_IN, ii, ii_CN, it_CH, ja, ja_JP, ja_JP_JP_#u-ca-japanese, jmc, jmc_TZ, kam, kam_KE, kde, kde_TZ, khq, khq_ML, ki, ki_KE, kln, kln_KE, kn, kn_IN, ko, ko_KP, ko_KR, kok, kok_IN, ksb, ksb_TZ, kw, kw_GB, lag, lag_TZ, lg, lg_UG, lkt, lkt_US, luo, luo_KE, luy, luy_KE, mas, mas_KE, mas_TZ, mer, mer_KE, mfe, mfe_MU, mg, mg_MG, mgo, mgo_CM, ml, ml_IN, mn, mn_MN, ms, ms_MY, ms_SG, mt, mt_MT, naq, naq_NA, nd, nd_ZW, nds, nds_DE, nds_NL, nus, nus_SS, nyn, nyn_UG, om, om_ET, om_KE, or, or_IN, pa, pa__#Guru, pa_IN_#Guru, prg, prg_001, qu, qu_EC, qu_PE, rof, rof_TZ, rwk, rwk_TZ, saq, saq_KE, sbp, sbp_TZ, ses, ses_ML, si, si_LK, sn, sn_ZW, so, so_DJ, so_ET, so_KE, so_SO, sw, sw_KE, sw_TZ, sw_UG, ta, ta_IN, ta_LK, ta_MY, ta_SG, te, te_IN, teo, teo_KE, teo_UG, th, th_TH, ti, ti_ER, ti_ET, to, to_TO, twq, twq_NE, ug, ug_CN, , vai, vai__#Latn, vai_LR_#Latn, vai__#Vaii, vai_LR_#Vaii, vo, vo_001, vun, vun_TZ, xog, xog_UG, ji, ji_001, yo, yo_BJ, yo_NG, yue, yue__#Hans, yue_CN_#Hans, yue__#Hant, yue_HK_#Hant, zh, zh_CN, zh_HK, zh__#Hans, zh_CN_#Hans, zh_HK_#Hans, zh_MO_#Hans, zh_SG_#Hans, zh__#Hant, zh_HK_#Hant, zh_MO_#Hant, zh_TW_#Hant, zh_SG, zh_TW, zu, zu_ZA]
Style: '٠' '+' '؜' '٫', pos_time: ٢٠٠٠٫٠٥٧٤٢٨, neg_time: ؜٢٠٠٠٫٠٥٧٤٢٨, locales: [ar, ar_001, ar_AE, ar_BH, ar_DJ, ar_EG, ar_ER, ar_IL, ar_IQ, ar_JO, ar_KM, ar_KW, ar_LB, ar_MR, ar_OM, ar_PS, ar_QA, ar_SA, ar_SD, ar_SO, ar_SS, ar_SY, ar_TD, ar_YE, sd, sd_PK]
Style: '0' '+' '‎' ',', pos_time: 2000,057428, neg_time: ‎2000,057428, locales: [ar_DZ, ar_LY, ar_MA, ar_TN]
Style: '0' '+' '‎' '.', pos_time: 2000.057428, neg_time: ‎2000.057428, locales: [ar_EH, iw, iw_IL, ur, ur_PK]
Style: '০' '+' '-' '.', pos_time: ২০০০.০৫৭৪২৮, neg_time: -২০০০.০৫৭৪২৮, locales: [as, as_IN, bn, bn_BD, bn_IN]
Style: '٠' '+' '‏' '٫', pos_time: ٢٠٠٠٫٠٥٧٤٢٨, neg_time: ‏٢٠٠٠٫٠٥٧٤٢٨, locales: [ckb, ckb_IQ, ckb_IR, ig, ig_NG]
Style: '༠' '+' '-' '.', pos_time: ༢༠༠༠.༠༥༧༤༢༨, neg_time: -༢༠༠༠.༠༥༧༤༢༨, locales: [dz, dz_BT]
Style: '0' '+' '−' ',', pos_time: 2000,057428, neg_time: −2000,057428, locales: [et, et_EE, eu, eu_ES, fi, fi_FI, fo, fo_DK, fo_FO, ksh, ksh_DE, lt, lt_LT, nb, nb_NO, nb_SJ, nn, no_NO_NY, nn_NO, no, no_NO, se, se_FI, se_NO, se_SE, sl, sl_SI, sv, sv_AX, sv_FI, sv_SE]
Style: '۰' '+' '‎' '٫', pos_time: ۲۰۰۰٫۰۵۷۴۲۸, neg_time: ‎۲۰۰۰٫۰۵۷۴۲۸, locales: [fa, fa_AF, fa_IR, ks, ks_IN, lrc, lrc_IQ, lrc_IR, mzn, mzn_IR, pa__#Arab, pa_PK_#Arab, ps, ps_AF, ur_IN, uz__#Arab, uz_AF_#Arab]
Style: '0' '+' '−' '.', pos_time: 2000.057428, neg_time: −2000.057428, locales: [gsw, gsw_CH, gsw_FR, gsw_LI, rm, rm_CH]
Style: '०' '+' '-' '.', pos_time: २०००.०५७४२८, neg_time: -२०००.०५७४२८, locales: [mr, mr_IN, ne, ne_IN, ne_NP]
Style: '၀' '+' '-' '.', pos_time: ၂၀၀၀.၀၅၇၄၂၈, neg_time: -၂၀၀၀.၀၅၇၄၂၈, locales: [my, my_MM]
Style: '๐' '+' '-' '.', pos_time: ๒๐๐๐.๐๕๗๔๒๘, neg_time: -๒๐๐๐.๐๕๗๔๒๘, locales: [th_TH_TH_#u-nu-thai]
Style: '۰' '+' '-' '٫', pos_time: ۲۰۰۰٫۰۵۷۴۲۸, neg_time: -۲۰۰۰٫۰۵۷۴۲۸, locales: [uz__#Cyrl, uz_UZ_#Cyrl]
```

I think it's an unfortunate combination of nobody using `DecimalStyle` and
there being virtually no negative numbers in practice. The only negative
numbers are in the UTC offsets, and those are formatted ignoring the decimal
style.

2022-12-27
----------

Back from the trip and ready to work! For the three days that's left before I go
to an actual vacation, that is.

As a nice distraction from the formatting, I decided to write some nice
templates for issues on the coroutines GitHub. We have too many people coming to
us with Stack Overflow-type questions, which is bad for everyone: we answer more
slowly than Stack Overflow does, we are burdened with the additional task of
answering questions instead of fixing issues, and sometimes, we don't even have
the best answers, the community does: for example, with something
Android-specific.


Here's the result: <https://github.com/Kotlin/kotlinx.coroutines/pull/3560>
Tried to keep the text on-point.

Reviewed a PR needed for Kotlin's impending release
<https://github.com/Kotlin/kotlinx.coroutines/pull/3553>


Reading/answering some issues in the coroutines repository that accumulated
while I was unavailable.


All this time, I played fast and loose with the terminology of format strings.
Is `yyyy` a pattern? A directive? A format? Designator?
Nobody's what I'm saying anyway, this is all just research, not a final product.
Well, now that we're close to the final product, I need to clean up my act.

So, reading about how these things are *actually* called.
* "Year" is a field.
* `y` is a pattern letter or a pattern character.
* `yyyy-MM-dd` is a *pattern* that defines a *format*.
* `yyyy` seems to also be called a pattern that defines a format, but I don't
  like that there's no difference between composite patterns and patterns
  equivalent to a single field.

(See `printf(3)`)
`printf`'s parts of the format string are called *directives*.
* A plain character, say, "t" is also an "ordinary character" directive.
* `%.03f` is a conversion specification. Here, `f` is the conversion specifier.
  Aside from the specifier, a specification also allows defining flags, the
  minimum field width, an optional precision, and an optional length modifier.

I certainly don't like the "conversion specification" and "conversion specifier"
terminology here, it seems too unwieldly, but calling an inseparable unit of a
pattern string a "directive" does sound nice.


2022-12-28
----------

The issue templates for the coroutines repo are in. Let's see how this affects
the stream of issues that we have. I don't think it's time already to make such
a template for the datetime library, we don't have nearly as many issue reports
there. No surprise though, as the library is still experimental. We do need to
publish the formatting capabilities.


Looking into how `InterruptedIOException` is used,
for <https://github.com/Kotlin/kotlinx.coroutines/issues/3551>:
* <https://github.com/search?q=InterruptedIOException+language%3AKotlin&type=code&l=Kotlin>
* <https://grep.app/search?q=InterruptedIOException&filter[lang][0]=Kotlin>

So, findings so far:

* People do use `InterruptedIOException` interchangeably with
  `InterruptedException`, like in
  <https://github.com/Goooler/MaterialFiles/blob/5cfa925af1c6dcb337f921973a1f3e3c873f6d8c/app/src/main/java/me/zhanghai/android/files/filejob/FileJobs.kt#L217-L221>
* Some people do separately process `InterruptedIOException` when it signifies a
  timeout, like in
  <https://github.com/pebble-dev/mobile-app/blob/f167a1abb853246be557234335fb933009f828ff/android/app/src/main/kotlin/io/rebble/cobble/bluetooth/BlueGATTServer.kt#L414>
* Some people do process `InterruptedIOException` similarly to
  `InterruptedException`, like in
  <https://github.com/bootstraponline/mirror-goog-studio-main/blob/7ff4a35e41005631dff968bc954ae1cdb756552f/adblib/src/com/android/adblib/AdbPipedInputChannelImpl.kt#L110>

Additional information:

> By convention, any method that exits by throwing an InterruptedException clears interrupt status when it does so.

(from <https://docs.oracle.com/javase/tutorial/essential/concurrency/interrupt.html>)

However, this is *not* the case for `InterruptedIOException`:

```kotlin
package r3551

import java.io.InterruptedIOException
import java.net.*

fun main() {
    val url = URL("https://www.google.com")
    Thread.currentThread().interrupt()
    val conn: HttpURLConnection = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 1
    try {
        println(conn.responseCode)
    } catch (e: InterruptedIOException) {
        println(Thread.currentThread().isInterrupted)
        // true
    }
}
```

In fact, `conn` here will ignore thread interruptions:

```kotlin
package r3551

import java.io.InputStream
import java.io.InterruptedIOException
import java.net.*

fun main() {
    val url = URL("https://www.google.com")
    Thread.currentThread().interrupt()
    val conn: HttpURLConnection = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 1000
    try {
        println((conn.content as InputStream).readAllBytes().toString(Charsets.UTF_8))
    } catch (e: InterruptedIOException) {
        println(Thread.currentThread().isInterrupted)
    }
}
```

This will successfully print some HTML, nothing is aware of the thread being
interrupted.

All of this is enough to provide a response:
<https://github.com/Kotlin/kotlinx.coroutines/issues/3551#issuecomment-1366665603>


Now, for my last two days before a vacation, I have a task of reviewing
<https://github.com/Kotlin/kotlinx.coroutines/pull/3537>. Wish me luck, that's a
portion of the library that I'm completely unfamiliar with.


Reading `WorkQueue.kt`. Some questions:

<https://github.com/Kotlin/kotlinx.coroutines/blob/41a2e30da45bbc70de4c7aafe23777f01b006c8a/kotlinx-coroutines-core/jvm/src/scheduling/WorkQueue.kt#L44-L47>
Wouldn't it be better to say this?
```
     * The only harmful race is:
     * [T1] readProducerIndex (1) preemption(2) readConsumerIndex(4)
     * [T2] changeProducerIndex (3)
```
After all, increasing the consumer index means that the size becomes smaller,
so, to construct a harmful race, we need the opposite.

<https://github.com/Kotlin/kotlinx.coroutines/blob/41a2e30da45bbc70de4c7aafe23777f01b006c8a/kotlinx-coroutines-core/jvm/src/scheduling/WorkQueue.kt#L84>
I guess this is free of the race because only the producer could have caused it,
and it's busy executing this method.

Ah, screw it, I'll just make a separate PR where I add/edit comments according
to my understanding.

For now, I understood enough to judge the PR.


2022-12-29
----------

The last workday of the year. I'll probably take it slow. I still need to read
<https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/src/scheduling/CoroutineScheduler.kt>

If I still have some time left after that, I'll probably skim
<https://unixism.net/loti/index.html>, inspired by an issue that was filed:
<https://github.com/Kotlin/kotlinx.coroutines/issues/3563>.

Or not. There are many small things like answering questions on various trackers.


At the end of the day, no uring for me. Just reading the scheduler and doing
small things here and there.

Happy New Year to everyone reading! I'll be on a vacation for the next week,
don't expect any news.

2022-01-09
----------

Back from the holidays. There's a bunch of code reviews in coroutines that I
should do.

The most challenging one is <https://github.com/Kotlin/kotlinx.coroutines/issues/3578>.
Currently I'm trying and failing to understand what exactly is going on there.

Throughout the day, I answered a couple of issues and reviewed several pull
requests.

2022-01-10
----------

Filed <https://github.com/square/okhttp/issues/7647>. From my library-writer
perspective, storing some resource (like a thread pool, or a clock, or something
like this) instead of providing a way of supplying it is a big no-go, except in
cases of all-encompassing frameworks, which I also really dislike.
It will be interesting to see if, in some cases, this is okay.

Continuing my experiments with
<https://github.com/Kotlin/kotlinx.coroutines/issues/3578>.

After that, I am busy implementing the draft of the datetime formatting.
Mostly some plumbing.

2022-01-11
----------

Finalizing the parsing of local times, I finally have to encounter my foe, which
is the `B` "period-of-day" designator. It won't be part of the initial release,
due to us not having any notion of locales and this being wildly
locale-dependent, but what can you do, I still must research it so that later,
there are no unpleasant surprises that will force us to change something
radically.

So, the language where this is majorly needed is Chinese.

I'm irritated that I can't/(don't know how to) force the IDE to run a test in
JDK 16, which is the one that supports `'B'`. My `JAVA_HOME` points to 1.8, so
that's what attempts to launch my code, which is
```kotlin
println(System.getProperty("java.version"))
val formatter = DateTimeFormatter.ofPattern("Bh:mm", Locale("zh_Hant"))
println(formatter.format(java.time.LocalTime.of(23, 12)))
```

Looks like though that it's Gradle that's preventing the use of a non-standard
JDK, as this also fails:
```
JAVA_HOME=$JDK_16_0 ./gradlew jvmTest --tests kotlinx.datetime.test.X
```

I guess it does the right thing and I just have to accept it and define a
separate project that would use JDK 16.

Let's do that!

Here's a snippet from my gradle config:

```kotlin
kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "16"
        }

```

Oh. With this, I can't run my test at all, "the wrong bytecode version".
So, it actually chooses the wrong JDK to run my tests.
Let's see what JDK it chooses (left-click on the project name, then press F4;
frankly, I'm always entertained by people that claim that they don't want to
learn CLI commands because that's too much work and they want to get straight
to programming, but are fine with the insanely arbitrary things you have to do
in an IDE).

Hey, it's using JDK 11, from my `$HOME`!

Let's read how to change this from the IDE:
<https://www.jetbrains.com/help/idea/gradle-jvm-selection.html#jvm_settings>

So, Shift+Shift, "gradle JVM", aha, found the option. Set it to JDK 16.

```
Could not open init generic class cache for initialization script '/tmp/wrapper_init3.gradle' (/home/dmitry.khalanskiy/.local/share/gradle/caches/6.6.1/scripts/e3247asfie2qgl1q2ghywy5r5).
> BUG! exception in phase 'semantic analysis' in source unit '_BuildScript_' Unsupported class file major version 60

* Try:
Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output. Run with --scan to get full insights.
```

Simply amazing.
```sh
rm -rf ~/.local/share/gradle
rm -r ~/.cache/
```

Restart the IDE, because surely it's not prepared for this:
```sh
pkill java
```

Ok, the whole IDE has hanged after I restarted it. Time to go grab some lunch.

Half an hour later, I'm back from lunch, and I may have murdered the IDE,
it seems. Everything's still stuck.

... It's unstuck, but now it's decided to use JDK 16 for the datetime library.

I wonder how people actually get any work done in the Java ecosystem.
I've read a whole book on Gradle, went through a very intensive Java course, am
busy interacting with fairly infrastructural things in that same ecosystem, and
still I'm easily dismayed by the state of all Java.

... Oh, no, in fact, it's fine, it's just the IDE that thinks I'm using JDK 16,
in fact, 1.8.0 is still used to run that test. The opposite of what I want.

Let's look at the project that I created specifically for using JDK 16.

```
A problem occurred configuring root project 'datetimeExampleProject'.
> Could not resolve all artifacts for configuration ':classpath'.
   > Could not resolve org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.0.
     Required by:
         project : > org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.7.0
      > The consumer was configured to find a runtime of a component compatible with Java 11, packaged as a jar, and its dependencies declared externally. However we cannot choose between the following variants of org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.0:
```

Ah, the "Gradle JVM" setting reset itself. Let's set it to JDK 16 again.
```
Could not open init generic class cache for initialization script '/tmp/wrapper_init5.gradle' (/home/dmitry.khalanskiy/.local/share/gradle/caches/6.6.1/scripts/c2pgg9tmxpusa0cr9f8rpksc1).
> BUG! exception in phase 'semantic analysis' in source unit '_BuildScript_' Unsupported class file major version 60
```

Ok. What about running it in the console?

```
$ JAVA_HOME=$JDK_16_0 ./gradlew jvmTest --tests Check --info
...
* What went wrong:
A problem occurred configuring root project 'datetimeExampleProject'.
> Could not resolve all artifacts for configuration ':classpath'.
   > Could not resolve org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.0.
     Required by:
         project : > org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.7.0
      > The consumer was configured to find a runtime of a component compatible with Java 16, packaged as a jar, and its dependencies declared externally. However we cannot choose between the following variants of org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.0:
```

Usually, I would say that I give up, but I don't really have that option.
Also, to be defeated by something so dumb would be really stupid.
Maybe I'm just not cut out for this.

Oh, I just needed to specify a more recent Gradle wrapper, I think.
Thanks, Stack Overflow! <https://stackoverflow.com/a/73742298/20886821>

So, that wasn't fun.

In the end, let's see what is the output of this:
```kotlin
val chinese = Locale("zh_Hant")
val formatter = DateTimeFormatter.ofPattern("Bh:mm", chinese)
for (i in 0..23) {
    val time = java.time.LocalTime.of(i, 12)
    println(formatter.format(time))
}
```

```
AM12:12
AM1:12
AM2:12
AM3:12
AM4:12
AM5:12
AM6:12
AM7:12
AM8:12
AM9:12
AM10:12
AM11:12
PM12:12
PM1:12
PM2:12
PM3:12
PM4:12
PM5:12
PM6:12
PM7:12
PM8:12
PM9:12
PM10:12
PM11:12
```

Wow. Really underwhelming. It's just AM/PM. This can't be right, or can it?..
Maybe my build of JDK just lacks the required locale information?

```
for (locale in Locale.getAvailableLocales()) {
    println("\nLocale: ${locale}")
    val formatter = DateTimeFormatter.ofPattern("a B h:mm", locale)
    for (i in 0..23) {
        val time = java.time.LocalTime.of(i, 12)
        println(formatter.format(time))
    }
}
```

Maybe not:
```
Locale: ru_RU
AM ночи 12:12
AM ночи 1:12
AM ночи 2:12
AM ночи 3:12
AM утра 4:12
AM утра 5:12
AM утра 6:12
AM утра 7:12
AM утра 8:12
AM утра 9:12
AM утра 10:12
AM утра 11:12
PM дня 12:12
PM дня 1:12
PM дня 2:12
PM дня 3:12
PM дня 4:12
PM дня 5:12
PM вечера 6:12
PM вечера 7:12
PM вечера 8:12
PM вечера 9:12
PM вечера 10:12
PM вечера 11:12
```

And for the `my` locale, the AM/PM markers are not always the same as the
period-of-day specification, notice the second half of day:
```
Locale: my
နံနက် နံနက် 12:12
နံနက် နံနက် 1:12
နံနက် နံနက် 2:12
နံနက် နံနက် 3:12
နံနက် နံနက် 4:12
နံနက် နံနက် 5:12
နံနက် နံနက် 6:12
နံနက် နံနက် 7:12
နံနက် နံနက် 8:12
နံနက် နံနက် 9:12
နံနက် နံနက် 10:12
နံနက် နံနက် 11:12
ညနေ နေ့လယ် 12:12
ညနေ နေ့လယ် 1:12
ညနေ နေ့လယ် 2:12
ညနေ နေ့လယ် 3:12
ညနေ ညနေ 4:12
ညနေ ညနေ 5:12
ညနေ ညနေ 6:12
ညနေ ည 7:12
ညနေ ည 8:12
ညနေ ည 9:12
ညနေ ည 10:12
ညနေ ည 11:12
```

The correct way to print short time, according to the JDK, is this:
```
Locale: my
နံနက် 0:12
နံနက် 1:12
နံနက် 2:12
နံနက် 3:12
နံနက် 4:12
နံနက် 5:12
နံနက် 6:12
နံနက် 7:12
နံနက် 8:12
နံနက် 9:12
နံနက် 10:12
နံနက် 11:12
နေ့လယ် 12:12
နေ့လယ် 13:12
နေ့လယ် 14:12
နေ့လယ် 15:12
ညနေ 16:12
ညနေ 17:12
ညနေ 18:12
ည 19:12
ည 20:12
ည 21:12
ည 22:12
ည 23:12
```

This is in agreement with CLDR's data which states that the period of day should
be used.

The next step is to find out how these symbols are translated into factual
times. Does the table above tell the whole story? In the Russian locale, the
cutoffs seem to be at 00, 03, 12, and 18 hours, but for `my`, these are
00, 12, 16, and 19. Clearly not a off-by-one error.

Let's compare the entries between `my.xml` and `ru.xml`:

```xml
<dayPeriodWidth type="abbreviated">
        <dayPeriod type="midnight">သန်းခေါင်ယံ</dayPeriod>
        <dayPeriod type="am">နံနက်</dayPeriod>
        <dayPeriod type="noon">မွန်းတည့်</dayPeriod>
        <dayPeriod type="pm">ညနေ</dayPeriod>
        <dayPeriod type="morning1">နံနက်</dayPeriod>
        <dayPeriod type="afternoon1">နေ့လယ်</dayPeriod>
        <dayPeriod type="evening1">ညနေ</dayPeriod>
        <dayPeriod type="night1">ည</dayPeriod>
</dayPeriodWidth>
```

```xml
<dayPeriodWidth type="abbreviated">
        <dayPeriod type="midnight">полн.</dayPeriod>
        <dayPeriod type="am">AM</dayPeriod>
        <dayPeriod type="noon">полд.</dayPeriod>
        <dayPeriod type="pm">PM</dayPeriod>
        <dayPeriod type="morning1">утра</dayPeriod>
        <dayPeriod type="afternoon1">дня</dayPeriod>
        <dayPeriod type="evening1">вечера</dayPeriod>
        <dayPeriod type="night1">ночи</dayPeriod>
</dayPeriodWidth>
```

Nothing stands out, really.

Googling `"evening1" cldr`, I found this helpful list that answers the question
exactly:
<https://unicode-org.github.io/cldr-staging/charts/38/supplemental/day_periods.html>

Sure enough, `common/supplemental/dayPeriods.xml` does contain the table.

**So**, the main finding: the correspondence between local times and day periods
is *itself* subject to localization, not just in the textual representation.


With this out of the way, let's work on coroutines.
Vsevolod proposed a fix <https://github.com/Kotlin/kotlinx.coroutines/pull/3584>
for <https://github.com/Kotlin/kotlinx.coroutines/issues/3578>, but, with what
I've written in that issue, I think a more structured approach is possible.
I'll try that.

However, not being able to test our changes bothers me.
Surely there is a way to pinpoint this issue.
I have an idea, let's prototype it.

But also, the proposed fix failed the build. It's irrelevant, just a flaky test,
but why is it flaky? Looking at it, it looks like some thread did nothing for
half a second. Is this sensibly possible? Let's try running the test in a loop
and see if the issue reproduces.

After 14 hours, it didn't reproduce.


2022-01-12
----------

Looking into the debug infrastructure in the coroutines library.
Unfortunately, it seems like only the JVM implementation supports assertions
that can be dynamically enabled.

Meanwhile, without testing the *actual* problem, I wrote a fix that, to me,
seems robust enough: <https://github.com/Kotlin/kotlinx.coroutines/pull/3585>.

Since I'm already looking into the various types of clocks, let's see the other
surprises down the road.
<https://en.wikipedia.org/wiki/24-hour_clock> leads to this:
<https://en.wikipedia.org/wiki/Thai_six-hour_clock>
The other pages that explain different clock systems are all about systems that
are no longer used.

> The six-hour clock is a traditional timekeeping system used in the Thai and
> formerly the Lao language and the Khmer language, alongside the official
> 24-hour clock.

The JVM just uses the 24-hour clock, which is nice.
The 6-hour clock is not accessible via the "period of day" designator either:
```
กลางคืน 12:12
กลางคืน 1:12
กลางคืน 2:12
กลางคืน 3:12
กลางคืน 4:12
กลางคืน 5:12
ในตอนเช้า 6:12
ในตอนเช้า 7:12
ในตอนเช้า 8:12
ในตอนเช้า 9:12
ในตอนเช้า 10:12
ในตอนเช้า 11:12
ในตอนบ่าย 12:12
บ่าย 1:12
บ่าย 2:12
บ่าย 3:12
ในตอนเย็น 4:12
ในตอนเย็น 5:12
ค่ำ 6:12
ค่ำ 7:12
ค่ำ 8:12
กลางคืน 9:12
กลางคืน 10:12
กลางคืน 11:12
```

This is different from the markers Wikipedia lists:
* `โมงเช้า`
* `บ่าย...โมง`
* `...ทุ่ม`
* `ตี...`

Let's search CLDR for that last hieroglyph, "a fish above a heart".
Nope, too many matches. But not a single one for `โมงเช้า`.
So, I guess nobody implements this in practice.

Therefore, the conclusion: we only need to support 12- and 24-hour time.


Not really in the mood today to write any code, so I'll look into the remaining
reviews.

Have read through the docs in `DebugProbes.kt`, going through
`DebugProbesImpl.kt`.


2022-01-13
----------

Asking the legal team about
<https://daniel.haxx.se/blog/2023/01/08/copyright-without-years/>.
Would be relieved to drop the copyright year from our notices.

Crunching the draft of datetime formatting.
Huge chance of it coming out next week. The only thing's left is to actually
churn out the code.


I wonder if I was right when I implemented "find" on a `DateFormat` to only
consider the positions that don't start inside numbers **or words**.
In Chinese or Japanese, for example, there are no spaces, right?
So, the format "年yyyy" would never be found in a text like
"時は年2023。". The example is synthetic, since in Japanese and Chinese,
the year number, it seems, goes before the "年", but there could well be some
cases where this would be a problem, even if I, not knowing either of the
languages, did not find a natural example immediately.

Oh, the troubles of doing true internationalization.

I guess we'll have to allow patterns that start in the middle of a word then.
How do we do that without permitting `OUTCOME` to be parsed as `UTC`?

I think the following scheme is in order: for each parsing operation, define
from which characters it's okay to start it. For example, a "string set"
parsing operation would disallow parsing from the middle of the word, while
"numeric span" can not be started in the middle of a number.
I don't see any issues with trying to parse a string constant from the middle of
a word though.

TODO:
* Remove `Accessor` from `FieldSpec`
* Merge `FieldSpec` with `TemporalField`
  - Two-dimensional hierarchy:
    logical type of the field (date vs time vs period etc), and
    Kotlin-type of the field (int vs long).
* Define collections of the form `TemporalField -> Accessor`


2022-01-16
----------

Funny observation: even though our datetime formatting API has some fairly
specific requirements compared to Java's, during the cleanup, I still am
noticing that our *internal* API becomes more and more similar to Java's public
one. And who knows, maybe, eventually, requirements will force us to open up
that API as well.

Meanwhile, I am happy/disappointed to remove
```kotlin
internal class Accessor<Object, Field>(
    // no idea how much use in these "inline val" and "@JvmField" directives are,
    // but the idea is for `Accessor` to be as lightweight as possible.
    // TODO: someone more competent in Kotlin should look at this.
    @JvmField inline val getter: Object.() -> Field?,
    @JvmField inline val setter: Object.(Field) -> Unit,
    @JvmField inline val name: String,
) {
    inline fun get(obj: Object): Field = getter(obj) ?: throw IllegalArgumentException("Field '$name' is not set")
    inline fun set(obj: Object, value: Field) = setter(obj, value)
}
```

as well as all the code that uses it.

I initially introduced it as just a `getter`/`setter` pair. Then, I added a way
to compose them, lens-like. Then, I decided *not* to use lens composition, but
I noticed that I needed to know the name of the field. *Then*, I noticed that I
had tons of code like
```kotlin
Accessor(IncompleteLocalDate::year, { year = it }, "year")
```

But I'm duplicating lots of information! I could instead just do this:
```kotlin
internal fun<Object, Field> KMutableProperty1<Object?, Field>.toAccessor(): Accessor<Object, Field> =
    Accessor({ this@toAccessor.get(this) }, { this@toAccessor.set(this, it) }, name)
```

But then... Why am I having an `Accessor` in the first place?
Essentially, it performs two functions:
* Point to a mutable nullable field,
* And complain when trying to get the field when it's null.

There is also some code that defines non-field accessors:
```kotlin
Accessor(
    { nanosecond?.let { DecimalFraction(it, 9) } },
    { nanosecond = it.fractionalPartWithNDigits(9) },
    "nanosecond"
)
```

However, it can be extracted to a property no problem:
```kotlin
internal var IncompleteLocalTime.fractionOfSecond: DecimalFraction?
    get() = nanosecond?.let { DecimalFraction(it, 9) }
    set(value) {
        nanosecond = value?.fractionalPartWithNDigits(9)
    }
```

Then the accessor becomes just `IncompleteLocalTime::fractionOfSecond`.


2022-01-17
----------

>  13 files changed, 128 insertions(+), 223 deletions(-)

My favourite kind of changes.

Meanwhile, learning about
<https://kotlinlang.org/docs/delegated-properties.html> in Kotlin, as
I think this could very much help with the fact that I have dozens of properties
exactly like this one:
```kotlin
    override var totalHours: Int? = totalHours
        set(value) {
            forbidReassignment(field, value, "totalHours")
            field = value
        }
```
where
```kotlin
internal fun <T> forbidReassignment(oldValue: T?, newValue: T?, name: String) {
    require(oldValue != null || oldValue == newValue) {
        "Attempting to assign conflicting values '$oldValue' and '$newValue' to field '$name'"
    }
}
```

Sure enough!
>  14 files changed, 142 insertions(+), 271 deletions(-)

This change added 14 lines, but took away 48 lines of boilerplate.

My limit for today is this:
>  15 files changed, 142 insertions(+), 317 deletions(-)

Alas, at some point, you do have to write code.


... Or not!

Separating responsibilities is always good. Why are the *fields* concerned with
not rewriting anything? It's not their business at all. The idea behind this was
that the parsed values have to be in agreement--but why should we forbid the
user from rewriting the field in the result they obtain (in case it's mutable)?
It's our problem to ensure this, not something for the user to be concerned
with.

This insight improved the API, but also...

>  15 files changed, 135 insertions(+), 332 deletions(-)

I am content with this.


2022-01-18
----------

Tough day. Feeling a bit sick. The progress is slow, but I'm trudging through
the datetime formatting draft. A bit sick of that as well. I'll likely switch to
some coroutine work for the rest of the week. Unless I actually fall ill.
Good thing that there's no one but me in my office room today, or I'd have to be
wary of potentially transmitting something.

2022-01-19
----------

Still feel a bit ill, but to a leser degree. Now I'm just exhausted.
I'm going to read the DebugProbes pull requests.
No strength to actually write code now.

Also, I need to fix my computer. Something's messed up in my Ubuntu
installation, so packages look up the wrong libraries. It's come so far that
Firefox silently refuses to open the file picker. Perhaps something's wrong with
my package sources, maybe some of them point to the wrong Ubuntu version?

In `/etc/apt/sources.list`, I see that I'm using "jammy", which, it seems,
is 22.04. No harm in upgrading to 22.10. Maybe 22.04 is no longer supported.
No idea about anything regarding the Ubuntu stability guarantees these days.
Who does still use it, anyway?
<https://www.phoronix.com/news/Steam-Survey-May-2022>
apparently, the majority.

So, couldn't fix the system after all. Moving my configuration to user-level
`nix` installation.


2022-01-20
----------

Didn't quite manage to keep my journal going, as there are some things that I
failed to list.

Right now I'm working on
<https://github.com/Kotlin/kotlinx.coroutines/pull/3576>, basically utilizing
the suggestion <https://github.com/Kotlin/kotlinx.coroutines/pull/3576#issuecomment-1396817076>
I made.
I'm basically repeating the pattern of SegmentQueueSynchronizer:
<https://github.com/Kotlin/kotlinx.coroutines/pull/2045>

There are two queues, one for workers that have nothing to do, and one for
tasks that couldn't find a worker to execute them. There's also a counter that
stores either `+ number of pending tasks` or `- number of parked workers`.
Both values can't be non-zero at the same time, so it's okay to use only one
number to represent both. Also, this counter uses one bit to represent whether
or not new tasks are allowed to enter the queue.

When someone modifies the counter, they promise that they will restore the
state of the queues to what the counter says. Luckily, this can be done in a
single atomic operation!

When a task arrives, it atomically adds 1 if the dispatcher is not already
closed. If the counter contained `- n`, this means that now one less worker
must be waiting for a task. So, the task wakes up a worker from the queue and
tells it specifically to execute it. Otherwise, the counter contained `+ n`,
which means that there were `n` tasks waiting already, and the number just
increased, so the right thing to do is to also enter the queue. Before that,
we try allocating the worker to let the dispatcher know that it must do
something about the tasks piling up. This may fail if workers can't be
allocated anymore due to the limit being reached.

When a worker is done with the current work, or is created initially, it
subtracts 1 unless the dispatcher is already closed *and there are no tasks
left to process in the global queue*. The latter condition is necessary to
uphold the contract that the thread pool successfully executes all the work
before terminating: we can't allow the workers to die off when there's still
a number of tasks to process. Then, if the counter contained `- n`, this means
we're just another worker going to sleep (and so we fall asleep), but if the
counter contained `+ n`, this means there are some tasks to do, so we grab one.

The result of the work is
<https://github.com/Kotlin/kotlinx.coroutines/pull/3595>.

After pushing it to the CI, I was greeted with the surprising
```kotlin
(new) MultithreadedDispatcherStressTest.testClosingNotDroppingTasks

java.lang.AssertionError: 1 threads expected:<10000> but was:<1097>
```

This means that the dispatcher may well drop some tasks when `close()` is
called. But how? I've made sure it didn't happen, the test didn't fail locally,
so what's going on? Hey, wait...

> `java.lang`

So, this is not an issue with my implementation at all!
This is broken in the JVM code!

Maybe a bit out of scope for my PR. I'll take a look at what's going on though.

Oh, maybe it's not *broken* in the JVM code, I just misread the docs. They state
that, when calling `close()`, the tasks that were already successfully submitted
will still run. They do! But *in parallel*, `close()` doesn't wait for them.
It's simply calling `ExecutorService.shutdown()` without `awaitTermination`.
I changed this in order to learn the thoughts of colleagues about this, but
don't actually feel one way or the other about this.

Just noticed that all my commits are `2022-XX-YY`, even though it's 2023
already. I'm having troubles like this since the childhood. Won't be amending
the git commit messages yet. Will do that once there's some damage done by this.


2023-01-22
----------

Just had a very elegant idea about handling numeric signs when formatting
date/time.

Problem statement:
```
d<+yyyy-mm-dd>
```
means "output the year with the leading sign always".
This sign isn't related to months or days, only to years.
```
o<+hh:mm:ss>
```
means "output the hour-minute-second zone offset, with the sign prepended".
The sign relates to the whole offset, but `mm` and `ss` here signify not
actual numbers, but fractions of the hour, written in a fancy manner.

However, let's look at how we format `DateTimePeriod`:
```
P1DT-2H
```
means "a period of one day minus two hours".
```
-P-1DT2H
```
means the same.

So, we have two types of numeric signs: those that affect the number directly
next to them, and those that affect groups of numbers following them.
How do we represent this in formats? In fact, how do we represent this
*anywhere at all*? We could even not give a way to do this outside of builders,
but *how* would one represent this?

When I raised this question during our internal design discussions, we decided
to do the following: have a separate directive that would have a global effect.
For example, `-'P'(|dp<d'D'>)(|'T'tp<h'H'>)`. Here, the minus sign indicates
"only output the sign here if it's negative"/"the minus sign can be parsed here"
and will affect all the fields: if there's a sign, all the numeric values are
negated.

This is a good solution: it covers all the use cases I've found in the wild, and
it's a rare enough corner case that you never really encounter it unless you
absolutely have to, in which case you won't have issues with reading a bit of
documentation. This semantics also works well with the other formats that I
provided as examples above: as the matter of fact, both dates and offsets only
have a single field that has a sign, so the fact that the sign works globally is
not an issue there as well.

Still, this irked me a bit, I never got to implementing this piece of
functionality, and in fact, forgot about it when writing the cleaned-up draft
of the PR. Now that the whole architecture is clean and shiny and in place,
I suddenly remembered about this problem on a nice snowy Sunday morning.
How do I retrofit this kludge into the codebase?

Well, there's always the solution that can be done in an hour:
* Forbid all the signs that are not the first directive in the given format.
  Otherwise, when we parse/output the sign, we'd have to go back to the fields
  we already set and negate them sometimes: remember, the sign is global, so
  it affects the things on the left as well!. When we *format* the sign, we'd
  have to consider what happens in case the sign is in an OR branch, like
  `B(A|-A)C`. This would mean that `B` and `C` have their signs negated if the
  `-A` branch is taken. I'd feel stupid wasting a day implementing this
  properly, given that no one is ever going to use it, just for the sake of
  completeness.
* Add a global flag to the parser and the formatter so that, when they encounter
  the flag, they remember to switch the signs of all the subsequent operations
  if the sign was negative.

That's a really intrusive measure for such a small feature! It *is* easy to
implement, understand and work with, but that's exactly it: this is some
functionality *exactly* for one feature, without rhythm or rhyme. Maybe there's
a better way.

The "forbid all the signs that are not the first directive" gave me a clue:
actually, the sign shouldn't be global, it should only affect the things to the
right of it. This way, the requirement immediately becomes meaningless: no need
to backtract and reassign values, no need to bother with `B(A|-A)C`: if the `-A`
branch is taken, we know to output `A` and `C` negated.

This is nice, but still a bit unstructured. Having global state just for the
"should I negate the things coming after" is also irksome. And also, the mental
model behind the signs still doesn't feel intuitive. In particular, what should
happen when there are *several* signs in the format? Logically, "minus by minus
is plus" etc, but looking at a pattern like `+h 'h' +m 'm' +s 's'`, I certainly
don't expect `-5 h -4 m +3 s` to mean `-(5 h - (4 m + 3 s))` or whatever. So,
maybe it's not the right call after all. Could someone concievably make this
mistake? I think so, yes. Should we just forbid multiple signs per format?
But there's a nothing fundamental that requires us to. In fact, it's nice to be
able to write multiple signs per format: it has not only the function of
describing the expression following them, but also the function of being
explicit about which signs should be output. For example, `+h` is sometimes
preferred to just `h` when we want to explicitly output the `+` sign.

So, spanning all the way to the right is also a problem. But there's a nice fix!
Make the sign directive affect only the one directive that follows it--
*including if that directive is a complex expression in parens*.

`d<+yyyy-mm-dd>` now means "(+years), then months, then days".
`o<+hh:mm:ss>` now means "(+total amount of hours), then the minute portion of
the hour, then the second portion". Or one could write `o<+(hh:mm:ss)>`.
And `-('P'(|dp<+d'D'>)(|'T'tp<h'H'>))` means
"output `-` before the period if every component is negative, and also have an
explicit sign before the days field".
The best of all worlds! This approach even nicely prevents a bug like
`+'P'(|dp<+d'D'>)(|'T'tp<h'H'>)` (notice the missing parens) only affecting
the days directive: we can just complain with something like "the directive
`'P'` does not have a sign; if you meant the literal `+`, write it in quotes,
like `'+'`, or wrap the expression whose sign is implied in parentheses".

Incidentally, this is exactly how the signs behave in math in general, so this
is just the most obvious solution as well. Embarassingly so! This nicely
indicates that it's correct. Also, it, too, is not difficult to implement.
I still think I won't put this in the initial draft, it's already time to
produce some output. Which is partially why I decided to put this into writing
instead of just implementing it immediately. Who knows when I have the time to
deal with the signs. 


2023-01-23
----------

We'd like to publish a new release of coroutines soon, so my strength is needed
there.

Things to do (the list is non-exhaustive, maybe there's something else):
* Finish reviewing the `DebugProbes` pull requests:
  <https://github.com/Kotlin/kotlinx.coroutines/pulls?q=is%3Apr+is%3Aopen+debugprobes>
* Remove the "experimental" mark from the parts of the test framework that we're
  confident in.
* Fix <https://github.com/Kotlin/kotlinx.coroutines/issues/3179> by providing
  some nice API.
* Review <https://github.com/Kotlin/kotlinx.coroutines/pull/3593>.
* Finish work on <https://github.com/Kotlin/kotlinx.coroutines/pull/3595>.
  It seems like the changes to the JVM implementation are undesired:
  @qwwdfsad noted that this would lead to a deadlock if `close` was called from
  inside the thread pool itself, which is a very unpleasant breaking change.

Let's start the week with the busywork first: read the PRs, make small fixes.
The difficult part of implementing a new API is for Tuesdays.

... Or so I thought. I went from down to up in my list of tasks and got stuck on
the task -3, fixing #3179, which involved writing a lot of code.

For the very simple cases stated in the issue, very simple measures are enough.
However, implementing broadly applicable features as kludges for a couple of
specific use cases always leads to someone hopeful using the feature as
advertised, not as intended by the creators. Such warts are what I dislike about
Kotlin (the language) the most, and so I strive to avoid this at least in my
own work.

The main problem is that we want the virtual time to be in sync with the real
time when waiting for `NoDelaySkipping` tasks.

Some issues that complicate things:
* A no-delay-skipping event is scheduled at `X`.
  Then, immediately, a normal event is scheduled at `X/2`.
  The scheduler executes the event at `X/2`.
  Now, if we naively look at the time to determine the length of the pause,
  we'll think that we must sleep for `X/2`, while in fact, we must sleep for
  the whole `X`.
* If `advanceUntilIdle` is waiting for some non-delay-skipping event scheduled
  at some time `X`, but then an event scheduled at time `X/2` arrives, we *must*
  stop waiting for the original event. The reason is simple: `withTimeout`
  won't work otherwise.

2023-01-24
----------

Establishing communication with the copywriting team so that they proofread the
web pages for the coroutines library:
<https://github.com/Kotlin/kotlinx.coroutines/tree/master/docs/topics>

Maybe at some point, with their help, the docs will stop being a source of
frustration and the PRs of the form "this sentence in the docs is inane".


In order to understand what to do with the problems of `NoDelaySkipping`,
I guess there's no way around reading the code for `EventLoop` and such.
It would be nice to have an option to just call
```kotlin
runBlocking {
  select {
    // if the time has come to execute the next non-delay-skipping task, do that
    // if a new task has arrived, look at it
  }
}
```

in the "grab the next task" code. However, there's no such thing on JS.
It looks like scheduled execution is performed via platform-specific things.

I see that the `EventLoop` is implemented via `park`/`unpark`, with `unpark`
being called if something happened in parallel that needs to interrupt the
thread sleeping.

I now remember the reason there's no such thing as `runBlocking` on JS: in
essence, when you call `fun f()`, there's no way for something to happen in
parallel to the code in `f`, since there's only a single thread.

So, is there a way, at least in theory, for code like the following to work?

```kotlin
runTest {
  val job1 = launch {
    withContext(NoDelaySkipping) {
      delay(1000)
    }
  }
  launch(Dispatchers.Default) {
    job1.cancel()
  }
}
```

`launch(Dispatchers.Default)` puts a task into the global queue. This queue
doesn't get queried until the scheduler is idle.

Looks like the only way to implement this is to make the scheduler return the
next non-delay-skipping task to execute, so that the Outer Loop of `runTest`
waits for either new events, timing out, or this task being ready.

This might just work! But what should we do about non-suspending
`advanceUntilIdle`? On JS, this question is easy, for a change:
`advanceUntilIdle` is blocking, nothing can happen while it's called.
On the JVM and Native, it's a bit more demanding, but they can just contain
smaller versions of the Outer Loop of `runTest`, with `runBlocking`, using the
idea from above.

This is probably going to be a mess from the expect/actual perspective.


2022-01-25
----------

I've had very good sleep, my head is as clear as it can be.

A very good time to finally internalize the whole debug probes.


What a chaotic day. I'm flying between various tasks, unable to ground myself
on one of them. I guess I'm a bit anxious about providing the datetime
formatting, which is long overdue. I bought smart watches, they arrive tomorrow,
let's see which stress level they detect!

With all the flailing, I'm still productive. This goes to show that good sleep
trumps in usefulness any attempts to structure work nicely when it comes to
actually doing things.

I've gone quite far in reading `DebugProbesImpl` etc, but also finished writing
the parser compiler for datetime formatting, have read
<https://plv.mpi-sws.org/later-credits/paper-later-credits.pdf> (one does have
to stay on top of new proof techniques when working in a library dedicated to
concurrency), answered a few people, and all of this after a morningful of a
German language course.

Let me dump here some of the links that I have open to free up the space in the
tab list. Even though I use Tree Style Tabs, it's still messy to have so many
links open.

* <https://firebase.google.com/docs/auth/android/anonymous-auth>
  <https://github.com/GitLiveApp/firebase-kotlin-sdk/blob/master/firebase-auth/src/androidMain/kotlin/dev/gitlive/firebase/auth/auth.kt>
  I'm trying to understand whether there's a guarantee that, in the latter link,
  it is guaranteed that the emission won't happen in an unconfined dispatcher.
  <https://github.com/Kotlin/kotlinx.coroutines/issues/3506> was the issue where
  this was raised. If it's not guaranteed, collecting in
  `Dispatchers.Unconfined` is an error.
* <https://bugs.openjdk.org/browse/JDK-8066982>
  I have it open until it's the right moment to send it to
  <https://github.com/Kotlin/kotlinx-datetime/discussions/237>.
  The link is a discussion of Java Time's decision to prioritize zone offsets
  over time zone rules when resolving a `ZonedDateTime`.
  The gist of it is that they decided to do this because the *immutable*,
  guaranteed, part of the sent data is the `LocalDateTime` + the offset +
  the time zone. If the time zone rules change, the implied moment is still
  defined by the offset. I disagree with this reasoning, because it neglects to
  consider the *intent* of sending a `ZonedDateTime` over: yes, if someone
  wanted to send an `Instant` and also a time zone to interpret the instant in,
  this tradeoff is correct, but is it really more common than wanting to send a
  `LocalDateTime` along with a time zone in which to interpret it, using an
  offset as a suggestion in case of resolution ambiguity in case of a time
  overlap? I'm not at all convinced that it is, and the second use case is just
  as valid, so it should be at least considered as well.
* <https://grep.app/search?q=withLaterOffsetAtOverlap>
  <https://grep.app/search?q=withEarlierOffsetAtOverlap>
  People *do* use different strategies for resolving time overlaps!
  The one thing left is to understand why and how.
* <https://github.com/evansiroky/timezone-boundary-builder>
  A pain point in datetime API is representing a simple thing of the form
  "local time in some place". `LocalDateTime` + `TimeZone` is ambiguous in case
  of a time overlap; time zone rules can change; *and* a time zone in some
  location can change. You want to say "2024-03-05, 18:00 in Munich", but
  instead, you say "2024-03-05T18:00+01:00[Europe/Berlin]". If Munich gets its
  own time zone in 2024, this becomes incorrect. If the offset becomes +02:00
  due to time zone rules change, this becomes incorrect.
  *Maybe* we could at some point, certainly outside the datetime library,
  write something robust by reifying locations.
* <https://news.ycombinator.com/item?id=32975173>
  an amusing overview of the datetime insanity.


2023-01-26
----------

Regarding the stabilization of the test module:

* `TestScope` is just the way I like it. It's useful and simple. Stable!
* `runTest` is ok, but a bit messy. The problem is `dispatchTimeoutMs`:
  looks like nobody understands what it does and it's just an implementation
  detail that *looks* like a timeout, but doesn't behave like one.
  Also, barely anybody actually uses it:
  <https://grep.app/search?q=dispatchTimeoutMs>
  We can use the fact that we never actually published `runTest` with
  `dispatchTimeout: Duration` to our advantage: we can deprecate
  `dispatchTimeoutMs` in favor of `timeout: Duration`, where this `timeout` is
  the proper whole-test timeout.
* I don't like the time control mechanism. Test readability really suffers
  because of it. The majority of tests I've seen that use `runCurrent`, or
  `advanceUntilIdle`, etc. are either just a soup of difficult-to-comprehend
  interactions or just of the form like
  ```kotlin
  launch {
    // blah blah
  }
  advanceUntilIdle()
  ```
  This is just a strange way of saying
  ```kotlin
  launch {
    // blah blah
  }.join()
  ```
  There are tons of options to wait for some operation to finish that are
  production-ready and don't require time manipulation. Why play god and
  force the coroutines to run to completion when you can instead reflect
  directly what happens in your production code? *That said*, just removing
  the time controls is also not the right call, it seems: some tests use it
  in a genuinely useful manner. So, I think we should mark
  `TestCoroutineScheduler` as stable, along with its time controls, but not
  `TestScope.runCurrent` et al, where they are easily reachable but are more
  often harmful than not.
* `setMain` is something we definitely don't want to stabilize as is, as it is
  problematic: <https://github.com/Kotlin/kotlinx.coroutines/issues/3298>.
  We will likely have to deprecate `setMain`.
* For the same reason, while `StandardTestDispatcher` is ok,
  `UnconfinedTestDispatcher` is often used to emulate
  `Dispatchers.Main.immediate` due to the reason above.
  `UnconfinedTestDispatcher` does have its uses, but they are few and far
  between. So, I think it would be the wrong call to stabilise the uses of
  `UnconfinedTestDispatcher` at this point. Maybe later, when everyone migrates
  from `setMain` to something else that is not broken and only the legitimate
  use cases remain.


2023-01-27
----------

One (hopefully) final push to understand `DebugProbes`. When I'm exhausted by
that, I write the PR that I described here yesterday. Overall, a nice and quiet
day of straightforward work.


2023-01-30
----------

Guess I caught a cold. I'll try to work, but if I don't manage to produce
anything useful today, I'll take a sick leave retroactively.

So far, managed to use Pandoc to upload a tutorial
<https://kotlinlang.org/docs/coroutines-and-channels.html> to the Google Doc
for the copyediting team.

Wrote a few lines.

Yeah, not my day, clearly.


2023-01-31
----------

Feeling a bit better today, even if still not ideal. Will keep it light for
today.

Worked the whole day on <https://github.com/Kotlin/kotlinx.coroutines/pull/3603>,
managed to iron out the problems I found.
The code is very ugly due to the need to support `runBlockingTest` and
`dispatchTimeoutMs` for now. When they're gone, everything will be simplified
and, I hope, a few bugs will surface and be corrected.


2023-02-01
----------

Lol, just understood that, in my weakened state, I've used the wrong dates in
the journal. See this commit.

I feel much better. I'll take it slow today as well, but in general, I think
I'll be fine by tomorrow. Answering some issues in the coroutines issue tracker,
lazily reviewing pull requests, etc.


2023-02-02
----------

I finished everything I wanted to do before the next coroutine release, so we
can publish it when Vsevolod gets around to it and reviews all my changes
(and possibly creates some new ones for me to review).

So, I can finally go back to working on datetime formatting.

But first, I want to finish going through the tabs I have. I want nothing to
distract me from the datetime, so it's better to finish the remnants of the
other pending work.

<https://github.com/Kotlin/kotlinx.coroutines/issues/3326> is a proposal that
I think I understand, and I don't see any issues with it, but the use case
provided is like French to me: no idea about what Jetpack Compose is.
I think it would be good in general for me to learn what's going on with
Android development. Quick googling revealed
<https://dev.to/zachklipp/a-historical-introduction-to-the-compose-reactive-state-model-19j8>
and <https://dev.to/zachklipp/introduction-to-the-compose-snapshot-system-19cn>.
The guy who wrote this <https://dev.to/zachklipp> seems fairly deeply into this.
The name also seems familiar...

Hey, it's this guy: <https://github.com/Kotlin/kotlinx.coroutines/issues/3493>!


>     this.text = “Counter: ${counter.value}”

I find this so much more funny than I probably should.

After reading through the articles, I understand the use case.
I'll still need to think about the "failing from `restoreThreadContext`"
proposal.

I don't have the time to dig deeper into this, as I need to go to the
standard library design meeting.


2023-02-03
----------

I'm a confident user of the coroutines library, to the point where I can work
on some derivative libraries, like `kotlinx-coroutines-test` or the
reactive integrations, but the implementation of the coroutines themselves is
not something I'm proficient in. If `JobSupport` is involved, I'm more or less
out. I *do* understand continuation-passing style, I've even written a Scheme
interpreter, and I do understand the concept of state machines that `suspend`
functions compile into, and even the `ContinuationInterceptor` stuff, but what
is a coroutine but some thing that somehow vaguely chains `suspend` calls into
a linear execution? I don't really have to dig into this.

Which is why it took me half a day to write
<https://github.com/spring-projects/spring-framework/pull/27308#discussion_r1095746663>.
The use case of intercepting continuations really made me excercise my
understanding: what's the actual role of the `Job` in the context? When code
completes, does it check its context for a `Job` to complete? Are these things
like `Future`, just attached to a coroutine in a context, queried on every
suspension and when the code finishes, and able to form a hierarchy?

*A bit* like that, but there's some wild inheritance going on in the coroutines
library. A coroutine *is* a `Job` (that much I've already actually noticed),
but also it's *itself* a `Continuation`. So, actually, no, the `Job` is not
attached to a coroutine, and a coroutine does not just orchestrate
continuations, these are all the same thing. When a continuation that is a
coroutine resumes with some value, that value goes through the `Job`
machinery of that same object, with no dynamic dispatch or context querying.
So, it looks like it should be possible to do some very nasty unstructured
things in the coroutines library, like, for example, setting a `Job` of
some coroutine to something other than it itself is.

However, I see that, it looks like, we protect against this by carefully
choosing what to expose. The mechanism to affect the coroutine context is
`withContext`, and yes, one can pass arbitrary jobs there, including
`NonCancellable` (which is essentially lack of a job), but `withContext`
is scoped and has the clear behavior of restoring everything when that scope is
exited.

So, it looks like removing `Job` will affect the behavior of `ensureActive()`
but not the resumption. Thus, its job there is actually to serve the purpose of
cancellation.

However, not passing a `Job` when creating a coroutine decides whether or not
the new coroutine will be structurally considered a subservient of the old one:
whether cancelling the old one also cancel the new one, whether any failure in
the new one lead to the old one also cancelling, and whether the old one should
wait for the new one on `join`.

So, how do these concerns work out for Spring? We should note that Spring does
something the purpose of which I don't understand (which is irrelevant to the
discussion): they intercept a coroutine, put it in a Reactor `Mono` (a thing
I've worked on and am familiar with), and then run
`theNewMono.awaitSingleOrNull()` with the old job from the original coroutine.
It seems fairly natural to suggest removing a middleman and just calling the
code directly, but maybe they also inject diagnostics, or some other additional
code, etc. In any case, they have their reasons, I don't doubt that.

So, in essence, the question is the following: are the following two snippets
equivalent?


```kotlin
mono(currentCoroutineContext().minusKey(Job)) {
  A
}.awaitSingleOrNull()
```
and

```kotlin
A
```

Let's go through the responsibilities of the job hierarchies, one by one:
* If the outer scope is cancelled, the inner one will also be in both cases:
  `awaitSingleOrNull` cancels the code it's executing if the await is cancelled.
  After all, `Mono` is a cold stream.
* If nothing is cancelled and `A` completes successfully, the same value will be
  returned in both cases.
* If nothing is cancelled and `A` fails with an exception, that exception will
  be rethrown from `awaitSingleOrNull`, so again, the same result.

... unless `A` changes its behavior depending on the `Job` or affects the
behavior of its `Job`.


Back to reading
<https://dev.to/zachklipp/introduction-to-the-compose-snapshot-system-19cn>
It seems this could be done easily in Haskell. I wonder if it was already done.
This would look really nice in a monadic interface, except for the `remember`
function, which is too magical for my taste. I don't understand its behavior
fully anyway: what will happen if you call a function that calls `remember`
with different parameters? What if it's a method in a class from which you
inherit, will calling the function remember separate results then?


2023-02-06
----------

A paper review arrived. Not sure if I'm allowed to disclose which paper and
which conference it is at this point, sorry. Preparing a rebuttal.


2023-02-10
----------

Done. I think I've exhausted my mental capacity for the week.
Looking forward to the weekend.
Had the main coroutine guy explain obvious things to me:
<https://github.com/Kotlin/kotlinx.coroutines/pull/3595/files#r1102542930>
At this point, I'm more of a plant than a man.
