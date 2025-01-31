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

Also, my ear aches terribly. A "ligament" they call it in English? in my mouth
is really stressed, so the whole right half of my face is in pain, especially
the ear.

Walking through the PRs in coroutines, but I also must fix my computer.
I found the root cause of all my issues: the new Ubuntu version uses Wayland by
default, but on my work computer, I'm still rocking i3. Quite expected that my
programs would crash on startup. It's a shame that I must migrate, because by
the nature of my work, I have to use IntelliJ IDEA, which doesn't work well on
Wayland: the text is all blurry. Trying to migrate using `apt`, I still
encounter issues, so I might as well try to keep the course of using the nix
package manager, slowly migrating all the software I use to it, leaving behind
only the software that has to run as a system service.


2023-02-13
----------

Fixing <https://github.com/Kotlin/kotlinx.coroutines/pull/3603>.
I agree with Vsevolod's suggestion to extract the non-deprecated code at this
point. I had this idea myself, but thought that this PR was already too large in
scope. However, now that I think about it, there's another consideration:
we *do* want to extract the correct implementation at the time when we stabilize
it: when we do end up extracting it, surely there will be some tough decision
when we inline everything in the end, like "do we really want to check this
here? do we want to throw that there?"

So, I rolled up my figurative sleeves and started separating the legacy code
from non-legacy one.

The result is some very clean logic, much simpler than not only the combined
legacy + current implementation, but also than just the legacy implementation.
Always a good sign!


2023-02-14
----------

External factors suggest that if, in two weeks, a draft of date-time formatting
materializes, some parties would be satisfied. In other words, there's a
deadline now.

The deadline seems really easily achievable. I already have the generic parsing
and formatting capabilities and partial support of format strings. I only need
to finalize the format strings and add a builder interface, and this can be
an acceptable draft already. Then I'll have to make sure the code works and to
provide some conversion functions from other libraries' format strings.

Plans as I see them so far:
* Tuesday, Wednesday: add the proper builder interface.
* Thursday: add support for format strings in our format.
* Friday: add end-to-end tests. At this point, we can consider this a proper
  draft.
* Monday: parse `strftime` and Moment.js format strings.
* Tuesday: parse Unicode format strings (Java, Swift).
* Wednesday: document everything thoroughly.
* Thursday: publish the draft and simultaneously start working on the initial
  implementation of localized parsing and formatting.

There's a proverb in Russian: "Tell the God your plans if you want to lighten
His mood". The meaning is that the God knows better than anyone that your plans
will differ wildly from what's actually going to happen. This is exactly what's
happened to me this time: of course, the IDE stopped working with the datetime
project for me. Even after I invalidated all the and updated all plugins,
navigation in the project still doesn't work. Looks like no declarations from
common code is resolved. The project builds fine though, so it's not me.
I guess I'll have to implement everything the non-Kotlin way, in plain text
editors. At least they're an order of magnitude more performant than IDEA, and
when IDEA is broken, there are literally no downsides.

Also, it's a good opportunity to check out the Kotlin language server.
Unfortunately, in the Helix editor, it simply silently doesn't work, despite
`hx --health kotlin` saying that it did found the language server. Kakoune also
doesn't work, but I don't understand why.

Ok, after following <https://github.com/mawww/kakoune-lsp#troubleshooting>, I
managed to arrive at a configuration that works at least better than IDEA.
I can now go to definition!

Wow, no, I was wrong. It seems this can't find *the standard library*.
```
kotlin language server error: 'Internal error.'
```

Trying to query information about something like "`toList`" leads to this
response:
```
Symbol is declared in unnamed module which is not read by current module
```

Well, to be fair, while it was worth a try, I don't know why I expected that
a third-party language server that interacts with a compiler that does not
provide a stable API would work better than a first-party tool.


2023-02-15
----------

Removing the `.idea` directory from the project resolved the issue.

Spent some time trying to battle variance. In essence, here's the problem I
was trying to solve and its solution, very simplified:

```kotlin
/**
 * A parsing/formatting operation on `T`.
 */
private interface Operation<in T>

/**
 * A format, consisting of a series of operations.
 */
private class Format<in T>(private val list: List<Operation<T>>)

private class FormatBuilder<T>(
    private val list: MutableList<Operation<T>> = mutableListOf()
) {
    fun append(element: Operation<T>) = list.add(element)

    fun build(): Format<T> = Format(list)

    fun <E : T> upcast(): FormatBuilder<E> = FormatBuilder(list.toMutableList())
}
```

The problem: I want the format-building operations to return the format that
works with as many `T` as possible automatically. For example,
if I'm building a `Format<LocalDateTime>` but only date-based fields are used,
`Format<LocalDate>` should be returned instead. To this end, I start out with
`FormatBuilder<Nothing>`, then, each `append` operation checks whether the
new operation still makes sense in that builder, and if not, promotes the
builder to a more all-encompassing one.

For example, after calling `appendYear()` on `FormatBuilder<Nothing>`,
we get `FormatBuilder<LocalDate>`, and after also calling `appendMinute()`, we
get `FormatBuilder<LocalDateTime>`, as this is the smallest available structure
that contains both fields.

Now, my issue was that I tried to avoid an explicit `upcast` step.
How do I set the variance everywhere in a way that `FormatBuilder<LocalDate>` is
also automatically `FormatBuilder<LocalDateTime>`? The answer is, I don't.
The classic trick that shows variance issues is at it again, though it slipped
my mind because this time, the trick would be really convoluted:

```kotlin
val builder: FormatBuilder<LocalDate>
val builder2: FormatBuilder<LocalDateTime> = builder
val builder3: FormatBuilder<ZonedDateTime> = builder
builder3.appendTimeZone()
```

Now `builder2` would contain a non-`LocalDateTime` field.

This would be a good place for Rust-like linearity: we could make an
`upcast` operation totally free if it consumed `FormatBuilder` completely, and
the code would be much more obvious. However, I spared no expenses on format
construction: it is an expensive operation, please do this as rarely as
possible.

**Why do this at all?** Simple: because it's easy to do and, depending on our
choice of the user-facing API, this could be a bit useful.

Consider `DateTimeFormat.fromStrftime("%Y-%m-%d")`. What should it return?
It could be an equivalent of `Format<AllEncompassingThingWithDateTimeAndZone>`,
but actually, we just want `Format<LocalDate>`.

This won't work if we decide to make an API like `DateTimeFormat<LocalDate>`,
since then, there would be no way to programmatically check whether this is
`DateTimeFormat<LocalDate>` or `DateTimeFormat<LocalDateTime>` due to type
erasure, but if we introduce separate types like `DateFormat`, `TimeFormat`,
etc., then this could be done.

**Wait, doesn't type erasure break the whole idea?** Yeah, it's true, we can't
pattern match types due to type erasure in the `appendYear` implementation as
well, which is why I have to implement a mechanism that allows me to know the
type information in both runtime and compile-time:
```kotlin
interface WithReifiedTypeArgument { val builder: FormatBuilder<*> }
class Date(override val builder: FormatBuilder<DateFieldContainer>) : WithReifiedTypeArgument
class Time(override val builder: FormatBuilder<TimeFieldContainer>) : WithReifiedTypeArgument
class Top(override val builder: FormatBuilder<Nothing>) : WithReifiedTypeArgument
class Bot(override val builder: FormatBuilder<Any>) : WithReifiedTypeArgument
```

It's a shame that I can't have `FormatBuilder` subclassing, or I'd be able to
write neat things like `interface DateTime: Date, Time`.
Maybe I should trick the compiler and have my subclassing.


Or maybe not! Here's a thought: make the code simply reflect the conceptual
model. Rarely is this a bad idea. And how *conceptually* do we represent
formats with mixed type containers? Here's a hint:
`ld<yyyy'-'mm'-'dd>'T'lt<hh':'mm':'ss>`. We *split the format* into groups of
different types!

Here, we could do exactly the same. Instead of promoting a formatter into a
bigger thing on each `append`, we could delay this decision until the final
`build` call, and until then, just keep a list like
`[DateFormat(...), TimeFormat(...), DateFormat(...)]`.
This way, we won't be constantly copying lists and performing complex
subclassing checks. Now, `appendYear` will only need to check "is the last group
`DateFormat`? If so, append to that, and otherwise, create a new one".
This also allows for better separation of responsibilities: this way, we won't
have to make the date-only format aware of how it should upgrade itself to the
encompassing formats.

Really a shame we can't write this in Kotlin:
```kotlin
private sealed interface Marker<T>

private object DateMarker: Marker<List<DateFieldContainer>>

private class MarkedValue<T>(val marker: Marker<T>, val value: T)

private fun t(value: MarkedValue<*>) {
    when (value.marker) {
        is DateMarker -> {
            value.value as List<DateFieldContainer> // "unchecked cast"
        }
    }
}
```

2023-02-15
----------

After some thought, I see now that the idea of `DateTimeFormat` subclassing is
doomed from the start.

Consider the API of the parser part of `DateTimeFormat`:
```kotlin
interface DateTimeParser {
  fun parse(string: String): AllEncompassingThingWithDateTimeAndZone
}

interface DateParser {
  fun parse(string: String): LocalDate
}
```

There's no way they could be related via subclassing: a `LocalDate` is certainly
not an `AllEncompassingThingWithDateTimeAndZone`, and it's impossible to
inherit `AllEncompassingThingWithDateTimeAndZone` from both `LocalDate` and
`LocalTime`.

We *could* do this all via interfaces, like
```kotlin
interface DateTimeParser: DateParser {
  fun parse(string: String): SomethingWithDateTimeAndZoneFields
}

interface DateParser {
  fun parse(string: String): SomethingWithDateFields
}
```

But then, in order to get a `LocalDate` from `parse`, one would either have to
do `as` casting (very cumbersome) or have something like `toLocalDate` in
`SomethingWithDateFields`. But then, we wouldn't win anything with the
subclassing: you can just create an `AllEncompassingThingWithDateTimeAndZone`
and call `getLocalDate` on that.

This all is even ignoring the issue that we may not want `Parser` to be a
standalone thing (I sure don't, for example) and instead be part of a `Format`,
with the `Formatter` part having a different variance.

Well, not entirely true. They have the same variance, but that's a
technicality.


Important milestone: ISO-8601 `LocalDate` format is parsed and formatted with my
thing successfully. So, the *basic* end-to-end functionality is there.
Hopefully, I'll manage to repeat this success for `LocalTime`, `LocalDateTime`,
and `UtcOffset` by Monday. Then, only `DateTimePeriod` will remain. Though that
one really is a tough nut to crack.


2023-02-17
----------

One more success: managed to implement `LocalTime` formatting and parsing via
the new

```kotlin
appendHour(2)
appendLiteral(':')
appendMinute(2)
appendOptional {
    appendLiteral(':')
    appendSecond(2)
    appendOptional {
        appendLiteral('.')
        appendSecondFraction(1)
    }
}
```

This ensures that formatting and parsing optional sections at least somewhat
works.

Next up, complex data structures, like `LocalDateTime`.


Turns out, the idea with the complex parsers behaving in terms of
`[DateFormat(...), TimeFormat(...), DateFormat(...)]` is also not as robust as
I thought. Consider the format `ld<yyyy-mm-dd>(|lt<hh:mm:ss>)`, for example.
It can not be used for formatting, since hours don't have a default value, but
this *can* be used to parse something that's either a `LocalDate` or
`LocalDateTime`. In this particular case, `lt<*>` and `(*|*)` commute, but
they are not guaranteed to: `(ld<yyyy-mm-dd>|lt<hh:mm:ss>)` just permits an
arbitrary structure on the input. So, while we could patch up this particular
use case, I think it would be conceptually clearer to just go back to the
original downcasting idea. Oh well.


Success! `LocalDateTime` parsed and printed, with its format defined via the
ones for `LocalDate` and `LocalTime`, though it could have just as easily been
defined on its own.


2023-02-20
----------

The next milestone is to be able to write a `DateTimePeriod` parser/formatter.
There's a problem though: the existence of the global sign, like in
`-P1DT-1H`, meaning minus (one day minus one hour). I've already discussed
this on 2023-01-22. I've had some new thoughts since then as to how specifically
to implement this. I'm not that worried about the deadline now, as I know I can
quickly pretty up what I have now and already call that a prototype.
Maybe I should instead focus on format strings for completeness then, to have a
more full and impressive prototype.

However, this must wait until I've dealt with the showstoppers in coroutines:
<https://github.com/Kotlin/kotlinx.coroutines/pull/3603>.

It's crazy hot in here, even with the open windows! "February", yeah, right.

So, some thoughts about the signs.
We have the following strategies for sign placement that we must support:
* Always output the sign. For example, `UtcOffset`.
* Only output the sign when it's `-`. For example, `DateTimePeriod` components.
* The ISO-8601 year mess of "if the number is longer than the proposed padding,
  write `+`".

The ISO-8601 year strategy is only needed for years
(<https://grep.app/search?q=appendValue.%2AEXCEEDS_PAD&regexp=true>), so we
won't provide means of attaching such behavior to arbitrary fields.
This leaves us with two other strategies. The "always output sign" one is
commonly represented as the `+` modifier, because the strategy of only
outputting `-` is usually the default. However, not many formatting systems
permit formatting `-10, -15` as `-(10, 15)`, and we have to support this.
Noda Time, who do provide the "shared sign" functionality
(<https://nodatime.org/2.4.x/userguide/duration-patterns>), use the `-`
modifier for this behavior.

The plan:
* Due to the special requirements of the ISO-8601 year, by default, use the
  `EXCEEDS_PAD` strategy for it. In the realistic use cases of four-digit
  years, this will be exactly the same strategy as the `-` one.
* For every other field, use the `-` strategy by default.
* If some shared sign was output somewhere up the stack, don't output it again
  for the field itself. This means not outputting `-` for negative fields if
  `-` was already output, but also not outputting `+` for years when
  `EXCEEDS_PAD` rule is triggered if a shared `+` was already output.
* If the entity to the right of `+` and `-` doesn't have a sign, fail.
* When shared signs are nested, `+` should still output `+` when appropriate.

Why is all of this so? Let's consider the use cases.

* Offset:
  - `+HH:mm` reads as "output the sign, then the hours, then the minutes"
  - `HH:mm`, due to established conventions behind signs, reads as
    "output the hours, only outputting the sign if it's `-`, then the minutes".
* Dates:
  - `+yyyy'-'mm'-'dd` reads as "output the years with the leading sign, then
    the rest of the date".
  - `yyyy'-'mm'-'dd` reads as "output the years with whatever signs, it doesn't
    matter, we only have dates in the 2006-2023 range".
  - `dd-mm-yyyy` reads like `dd'-'mm'-'yyyy`, but with a syntactic mistake.
* Periods:
  - `-('('yy' years' mm' months)')` reads as something uncommon, because it is.
    After learning that an explicit `-` is "output `-` if negative" and only
    makes sense if `()` follow it, it reads fine.
  - `-('('+yy' years'+mm' months)')` reads like "I want to output a shared sign
    if it's `-`, but I also always want to output the sign of the thing here".

In order to support conditionally outputting `()`, we can also consider the
introduced sign to be a separate field with the default value `+`, so that code
like `(yy mm|-('['yy mm']'))` works as expected.

Looks like the proposed plan always leads to the most intuitive reading.

We could emphasize the point that `-` only makes sense if `()` follows it and
forbid anything else, but I think this would be a mistake. The reason is
`yyyy-(mm|m)-(dd|d)`.

 A big (and completely unimportant!) question is this format:
`-(yy -(mm dd))`. With `yy = -15`, `mm = -23`, `dd = -56`, what should be
output, `-15 23 56` or `-15 -23 56`? Ah, nevermind, obviously the first one is
correct. How nice to have distributivity here!

2023-02-21
----------

Yeah, I definitely should focus on format strings. It's a good thing that I
thought about all the details for the plus/minus signs, as this will directly
affect the parsing of format strings, but I'm sure the signs are not a priority
in the way the general API shape is.

Working a bit on <https://github.com/Kotlin/kotlinx.coroutines/pull/3603> and
<https://github.com/Kotlin/kotlinx.coroutines/pull/3632>.


A bit tired from work (have almost finished writing the format string parser),
let's try to excercise Kotlin some more. Are there existential types in Kotlin?
There are in the weak sense that, for each `T`, `exists E, E : T` is trivially
available: this is just inheritance. Is there, for each `A<X>` the ability to
define the type `exists E, A<E>`? Well, that's `A<*>`, simple. But!
Is there, for each `A<X>` the ability to define `exists E, A<X> where f(E)`?

A specific use case (pseudocode):
```kotlin
interface Builder<T> {
  fun subBuilderByName(name: String): { Builder<E> where T : E }
}
```

Well, on the surface level, it *is* possible:

```kotlin
internal sealed interface BuilderInheritedWrapper<T>
internal class BuilderInherited<E, T>(val builder: Builder<E>) : BuilderInheritedWrapper<T> where T : E
```

However, clients of this can't be written, it seems.

```kotlin
internal fun <E, T> someFunction(builder: Builder<E>): FormatStructure<T> where T : E {
    return builder.build()
}

internal fun client() {
    val b1 = object : Builder<Number> {
        // ...
    }
    val b: BuilderInheritedWrapper<Int> = BuilderInherited<Number, Int>(b1) as BuilderInheritedWrapper<Int>
    val i: FormatStructure<Int> = when(b) {
        is BuilderInherited<*, Int> -> someFunction(b.builder) // infers FormatStructure<Nothing>
    }
}
```

The constraints are clearly not propagated properly. Shame.


Success! Date, time, and date-time formats can be defined in format strings.


2023-02-22
----------

I need to rethink the semantics of parsing in case when alternatives are used.
Consider the case of searching for `lt<hh:mm(|:ss)>` in some text. `find` will
return `13:45` if text like `13:45:31` is encountered, as this is the first
option when parsing. Surely, regardless of what happens with regular expressions
in general, searching for date-time things in text should be as greedy as
possible, an so the right strategy is to parse using all branches, but return
the first one among the longest ones.

Likewise, `findAll` shouldn't return `[13:45, 13:45:31, 45:31]`, one one of
these is correct. This can be achieved by only taking the longest parsed string
from the given position, and also by skipping the parts of the input that were
already recognized as something.


Who knew I would be implementing stack machines just to support parsing signs
properly.


2023-02-23
----------

Successfully implemented formatting with signs, but parsing still requires work.
The release of coroutines is a moving target, so work is constantly tickling
there. Have a bunch more pull requests to review there.

On Monday (or early Tuesday), I'll publish the draft of datetime formatting, and
for that, a working implementation is neither sufficient nor needed. Instead,
everyone will judge the external interfaces. So, I think I'll focus on them
primarily and only deal with making parsing with signs work *after* the
API is somewhat established.


Here's a pretty damning evidence that I've started losing my sight of the target
and began overengineering: <https://grep.app/search?q=SignStyle.ALWAYS>
Surprise: *nobody* uses the "always output a sign" directive.
Should have checked this before adding `+`. It all just went so smoothly: we
certainly need a "shared minus", so why not have a "shared plus" as well? If we
have a shared minus and a shared plus, why not make them applicable to
individual directives as well? Etc.

Let's reestablish everything from the ground up. *We need the shared `-`*.
We don't need individual `-`, because it's the default. To negate this default,
we could need an individual `+`, however, so far, there's no evidence that we
ever would.

This simplifies everything quite a bit. No more reasoning about what to do if
there's a `+` output up the stack, and no more concerns about the behavior of
the sign of years: screw years, they don't make sense as signed numbers anyway.
ISO year 1 is the beginning of the AD era, and year 0 is the year 1 BC, and if
we treat that literally as numbers, it becomes something like "ISO year `n` is
`n` years later than one year before the start of the current era." Just rolls
from the tongue, doesn't it?

The need that people actually have is to *avoid* the `SignStyle.EXCEEDS_PAD`,
to the point where people even discard the minus sign!
<https://grep.app/search?q=appendValue%5C%28.%2A%5CbYEAR.%2ASignStyle.%5B%5EE%5D&regexp=true>

So, here's the resolution for now: no `+` modifier, it's just never needed.

That was a long rant for such small a resolution, but oh well.


2023-02-24
----------

Wow. Some days, the best you can do for work is to just go home. Yesterday was,
it seems, one of such days for me. Reading what I wrote yesterday, it's really
ridiculous.

"We don't need a `+`" is a faulty premise, because we obviously do need it: the
UTC offset format `+HH:mm` wouldn't work otherwise. Well, it obviously could
work if we special-cased `HH`, but as a programmer, I **hate** with deep passion
such special-casing in the API. You can't really learn and understand the API,
you have to always research every single tiny thing. This is really my main
gripe with Kotlin and C++, as well as Python's and C's standard libraries: you
can't build an intuition, the ground is always shifting under your legs.

Do I entertain the thought that someone would learn the formatting API I'm
working on? Well, I don't think anyone would become an expert in it, but I
absolutely think that the goal of making it possible to just jump into
documentation for a quick refresher and then writing whatever format one desires
is possible... With the big caveat of *localized* parsing and formatting, of
course, which is an entirely different beast.

To return to the question of `+HH:mm`, I'm certain that all signed numbers
should just behave the same, and a special `+` sign shouldn't be special.
It's really not that expensive to support `+` everywhere.

Funny occurrence: I wrote an API like
```kotlin
public fun LocalDate.Companion.parse(formatString: String, input: String): LocalDate
```

When I tried to use it elsewhere, Copilot suggested:
```kotlin
parse(input, isoDateFormat)
```
Clearly it's mistaken!
However, after thinking about it for a bit, I changed the *API*.
Initially, I followed the school of Haskell, with the more unchanged parameters
being the first ones. This way, you can comfortably curry:
```haskell
parseDate :: Format -> String -> Date

isoParseDate :: String -> Date
isoParseDate = parseDate isoDateFormat
```

Very handy, but Kotlin just doesn't have this. So, other considerations come
into play. For example, the consistency among overloads.

```kotlin
parse("1")
parse("2", "3")
```
The first function, parsing with the default format, looks very much like the
second function. To the point where we could write the first one like

```kotlin
fun parse(input: String) = parse(format = isoDateFormat, input)
```

But the defaultable arguments usually go to the end. So, it makes sense that the
first argument is the input string: this way, it stays the same between
overloads.

Also, Java does it this way.


2023-02-26
----------

Interesting find: turns out, testing a system just using itself is error-prone.
When we transferred parsing `Instant` to use `OffsetDateTime` to be able to
parse the offsets other than `Z`, we accidentally also allowed parsing strings
that don't have the minute component. How did this slip in? Well, if we simplify
the issue, the code that checks the correctness of parsing does this (pseudocode)
```kotlin
for (offset in offsets) {
  for (instant in instants) {
    // here, Instant.parse(s) = OffsetDateTime.parse(s).toInstant()
    assertEquals(instant, Instant.parse(OffsetDateTime(instant, offset).toString()))
  }
}
```

So, effectively, the code is checking that `OffsetDateTime` parses what it outputs.
Not a very useful property unless we want to test java.time!


2023-02-27
----------

Finally published a draft of the datetime formatting implementation:
<https://github.com/Kotlin/kotlinx-datetime/pull/251>
Should be smooth sailing from there.

However, there are many things to improve. I have plenty of time before we
arrange design meetings, and so, many opportunities to iron out the issues.

The most glaring ones for now are those:
* Actually, the internal `Builder` interface is not that useful.
  I could replace it with a mapping of subbuilder names to their collections
  of directives and have an even better, more declarative implementation of
  the same, opening up the possibility of more informative error messages in
  case of unknown directives/subbuilders.
* The latest fixes in the parsing facilities led me to a nice refactor that
  suggests that formats are composable. If it turns out to be the case, it's
  one more nice algebraic property, and those are usually very fruitful.
  For example, we could have an API like
  "`LocalDateTimeBuilder.appendDateFormat`" that would allow one to use the
  preset formats we're going to provide.

Taking a breather from the datetime, I noticed that the
`kotlinx-coroutines-test`'s `README.md` needs an update to reflect the
new stuff: <https://github.com/Kotlin/kotlinx.coroutines/pull/3645>.


2023-02-28
----------

Writing a public document about localized date-time formatting. Stay tuned.
Done: <https://github.com/Kotlin/kotlinx-datetime/discussions/253>

Took a bit longer than I expected, because Swift managed to provide a nice API
for localized formatting while I wasn't looking, and it took some time to get
up to speed.


2023-03-01
----------

A nice first day of spring.

Reading a sad tale:
<https://betterprogramming.pub/the-silent-killer-thats-crashing-your-coroutines-9171d1e8f79b>
Really compelling argumentation. I'm not under obligation to write anything
articulate here, so I'm going to ramble. The purpose is not to tell something
but to understand what I think about all this.

So, the Java world is built on exceptions for exceptional conditions. There is
the normal code that runs without any worries or concerns about an error
happening in one of its operations, as the errors simply propagate upwards.

There are several kinds of exceptions in practice:
* Runtime issues: out-of-memory, stack overflow, etc. Sometimes these are
  programmers' mistakes, and sometimes, they are jus the sad reality.
  All bets are off when such exceptions happen: anything can throw them, each
  line of code is a potential culprit.
* Programmer mistakes: `NullPointerException`. These should not happen, so if
  they do, it's best to catch them in a way that makes it possible to inform the
  programmer about their mistake. A top-level handler of sorts is nice for this.
* Expected exceptions: for example, when parsing some user-supplied string
  fails. It's not the happy path, but it *is* expected. These should be caught
  and processed appropriately.
* Abusing the concept. All the exceptions above represent straying from the
  happy path, but the exceptions are also used for their magical control flow
  manipulation properties. Jumping up the stack *is* a powerful ability.
  In theory, when you have exceptions, you don't need `continue`, or `break`,
  or `return`: all of this can be replaced with `try`-`catch`.
  A language with just a `while(true)`, `if`, `try`, and `throw` is already
  not any less powerful than Python.

Interruption exceptions and coroutines' cancellation exceptions are really
abusing the concept: why would one call those "exceptions" if they are very
regularly the *expected* part of execution? This is just using the mechanism
built and refined for one thing and making it mean something else completely,
just because the behavior is the same on the surface level.

Runtime exceptions can not be sensibly handled. Only if every line of code is
written with the assumption that it can throw can we reason about which
invariants would be violated by a stack overflow in this or that function call.
We can't even write defensively against this: no matter how well you keep your
call chain flat, someone could just run your top-level function in some
monstrous multi-layered framework. Let's see what Java thinks about it:
<https://docs.oracle.com/javase/7/docs/api/java/lang/Error.html>.
And sure enough,
> these errors are abnormal conditions that should never occur

Programmer mistakes can only be handled via a top-level handler. Should one
ever try to wrap a block of code in `try`-`catch` *just in case* there's an
error in that code? Well, in theory, this could be sometimes useful, but... are
you not calling that code with some specific purpose? If that purpose was not
fulfilled, how can you continue? By instead calling a safer fallback, one that
is more robust but potentially deficient in some other way that prevents it
from just making it the default? I guess that's a possibility, but I've never
seen this done. Maybe in some more enterprise environments? No, this still does
not make sense: if you admit that your code may be wrong, you can't assume that
it will always throw an exception if something's wrong. Or can you? If you
program really defensively, like below, maybe you can:
```kotlin
doOperation1()
check(operation1Done()) { "fail1" }
doOperation2()
check(operation2Done()) { "fail2" }
```
Still, not something I've seen.

Expected exceptions are a tricky beast. Which exceptions you expect depends on
your problem area. If a function throws `E1`, `E2`, and `E3` depending on the
conditions, and you expect `E1` and `E2` and are sure that `E3` never happens,
then, when `E3` is thrown, it's a programmer mistake. However, we're not
discussing language syntax here, it's just me rambling about the *semantics*,
and *semantically*, this function simply returns `Value | E1 | E2 | E3`, where
`|` is a disjoint union. Even `IllegalArgumentException` is just part of the
function's behavior and its *reaction to the inputs*. It would be irritating to
actually write the code where all non-happy-path cases were returned in a
reified manner: not all assumptions are encoded in types, so you may get
nonsense even if `Value` was returned, and painstakingly enumerating everything
that's can be returned from your functions would be exhausting. However, we
should recognize the use case where exceptions are just the expected result.
Let's split this problem into two: let's imagine that every non-critical
exception is always explicitly returned and programmers must always check the
result and explicitly decide what to promote to a critical problem (something
that's not expected and is a programmer mistake) and what to treat in some
manner.

Someone could notice that we're getting close to Rust here.

A `CancellationException` being thrown means that the coroutine was cancelled
and needs to stop doing what it's doing. This is not an exception semantically,
it's a control flow token that happens to reuse the exception handling
mechanism to make use of the `finally` blocks and such. If coroutine
cancellation didn't rely on exceptions, what would it do? My guess is, it would
take the position in code where the coroutine was suspended, and then go through
the `finally` blocks. Would we want to ever let people explicitly handle
*cancellation*? I don't really see a compelling reason to. If your code stopped
executing for some reason and decided to unwind the stack and prematurely
terminate, do you care whether it was an out-of-memory error, programmer
mistake, or cancellation? Can you meaningfully react to the difference between
these?

Ideally, therefore, it would be impossible to even programmatically react to a
`CancellationException`. It's not an interruption exception: in Java, when you
interrupt a thread, it may decide to keep chugging along, even going into
interruptible methods, by clearing the interrupt flag. With Kotlin coroutines,
this is impossible: to execute some code while disregarding cancellation, you
must either have launched it in `withContext(NonCancellable)` (you can't do
that *after* the cancellation) or to just spawn a new coroutine that finishes
the job for you (the first question is, why do that, and the second one is,
why would it be handled differently from a programmer's mistake or an OOM).

So, `CancellationException` shouldn't even ever surface.

With all these considerations, what should `Deferred.await` do when if the
computation was cancelled? With all the considerations above, I feel that
it should *not* throw an instance of `CancellationException`: it *is* not a
signal for the caller to cancel.

So, in short: I think I agree.

2023-03-02
----------

I wonder in which cases deprecation warnings should not be shown.
For example, consider the following code:

```kotlin
@Deprecated("Don't use this!")
class X

fun f(x: X) = 3
```

Compiling this code leads to a warning: the function is using
the deprecated `X`, but it should not.

Now, a concern: the more warnings there are, the worse
is the incentive to disregard them until it's too late and they become errors,
so if there are too many warnings, we risk making deprecated code
indistinguishable from normal one.

Is the warning for `f` actually doing anything useful? In order to use `f`,
one first has to obtain an instance of `X`, and *that* place is already going
to give a warning. When all such places are eliminated, `f` will warn about
existing despite being unused.

This can be generalized to any use of deprecated things in `in`-positions:
`f(x: X)`, `f(x: () -> X)`, `Consumer<X>`, etc: they are benign, as to use them
in a way that accesses `X`-specific functionality, you have to already have an
instance.

Now, here's a concern that probably invalidates my reasoning: deprecating an
interface/superclass while keeping the implementors/inheritors. If everywhere in
the `in` position you have the deprecated `X`, but everywhere in `out` you have
`Y : X`, you will not notice any issues until `Y` stops being inherited from
`X` (which breaks backward compatibility) or `X` is hidden (which causes a
compilation error). This kind of negates the benefits of graceful migration via
`Deprecated`.

Yeah, on second thought, the idea of not treating `in`-uses as issues seems
flawed in the presence of subclassing.


2023-03-03
----------

There are already some discussions under
<https://github.com/Kotlin/kotlinx-datetime/discussions/253>, in which I
participate.

Today, after a very long week, I'm going to review all the pull requests in
coroutines that I should. I've lost count of how many times I've had to write
this here already.


2023-03-06
----------

Quite a productive morning: fixed a bug in a test
<https://github.com/Kotlin/kotlinx.coroutines/pull/3659>,
reviewed the changelog of the upcoming beta release of coroutines,
discussed the localized date-time formatting a bit.

Now I'm reading <https://abseil.io/docs/cpp/atomic_danger>, and then, I'll need
to work for a bit on the paper we submitted. Everything goes well for now!


2023-03-07
----------

Still working on the paper.


2023-03-08
----------

Let's start small today: <https://github.com/Kotlin/kotlinx-datetime/pull/256>

Or think bigger: let's also *keep it* small.

For example, now I'm going to configure `kotlinx-datetime` so that it supports
the Kotlin compiler continuous integration.


2023-03-09
----------

Noticed that the dates for the last few days were all wrong. Fixed.

When I wanted to keep it small yesterday, I didn't expect configuring
`kotlinx-datetime` for the compiler continuous integration to be so demanding.

Groovy is very dynamic. You can add properties at runtime and then try to query
them. When it works, it works, and when it doesn't work, it compiles.
The existing scripts for continuous integration
(like <https://github.com/Kotlin/kotlinx.coroutines/blob/4116d4a178f32b1241db8247a38cd2823fb8b03e/build.gradle#L17-L43>)
use Groovy.

`kotlinx-datetime` uses `build.gradle.kts`, which is a different beast.
Naively translating Groovy code into `build.gradle.kts` simply does not compile:
we can't query a property that's not there statically. So, instead of a simple
manual translation, this became a serious task of trying to understand what's
going on in the continuous integration scripts.

For now, it seems, the following things happen, though I may be severely
mistaken:
* The build scripts in `buildSrc` check if this is a continuous integration
  build. If so, the plugins *for* the scripts in `buildSrc` use the CI version
  of the Kotlin compiler. Why? The only plugin we use is `kotlin-dsl`.
  Do we actually use *our own* `kotlin-dsl`? I don't think we do: there's not
  even a version of it specified, so there's no way to discern between
  our `kotlin-dsl` (if any) and the official one.
  The Gradle docs seem to agree that this is what's going on there:
  <https://docs.gradle.org/current/userguide/plugins.html#sec:plugin_management>
  The change was introduced in <https://github.com/Kotlin/kotlinx.coroutines/pull/2332>.
* The build scripts in `buildSrc` *depend* on the Kotlin gradle plugin with the
  version that can be specified from the command line. Ok, that makes sense.
* The code in `buildSrc` provides a function to conditionally add one of the
  repositories involved with the CI if the CI mode is enabled.
* `settings.gradle` optionally updates the `kotlinVersion` to the CI one.
  It's then used throughout the build scripts to refer to the various plugins.

I think I got the gist of it here:
<https://github.com/Kotlin/kotlinx-datetime/pull/257>


2023-03-10
----------

A Friday is a nice day to leisurely catch some bugs. I'm going to hunt
<https://teamcity.jetbrains.com/buildConfiguration/KotlinTools_KotlinxCoroutines_BuildAggregated/4078957?hideProblemsFromDependencies=false&hideTestsFromDependencies=false&expandBuildProblemsSection=true&expandBuildChangesSection=true&expandBuildTestsSection=true>

So, this is a very interesting situation. In the test, which now has failed both
on Windows and a Mac once, we see the interplay of three things:
* `limitedParallelism`,
* `newFixedThreadPoolContext` on Native,
* and `coroutineScope`.

We create a `newFixedThreadPoolContext`, wrap it in a `limitedParallelism`,
repeatedly create `coroutineScope` where a bunch of coroutines run, *wait for
the coroutine scope to finish*, and at the end, close the executor.

The test fails, surprisingly, when something submits a new task after
the executor is closed. There shouldn't be anything capable of doing that
though, as all the tasks are supposed to be finished by the time
`coroutineScope` exits.

An additional tricky part is that I don't seem to be able to reproduce the crash
with
```kotlin
fun testLimitedExecutor() = runTest {
    while (true) { // loop the problematic code
        val executor = newFixedThreadPoolContext(targetParallelism, "test")
        val view = executor.limitedParallelism(targetParallelism)
        doStress {
            repeat(iterations) {
                launch(view) {
                    checkParallelism()
                }
            }
        }
        executor.close()
    }
}
```

I've been spinning it on my Linux for good ten minutes now, to no avail.

Notably, all the failures we observed were not on the Nightly CI configuration,
which enables stress testing, but on just the normal runs... Ah, wait, it's the
Native, the `stressTestMultiplier` is always just 1.

Maybe I should wrap the whole `runTest` in a loop instead?

Fifteen minutes (one lunch) later, no failure when I loop the whole test.

Did the state from other tests maybe somehow leak into this one, which is why
just looping one test does not do anything? Or maybe the reason is me being on
Linux and not Mac or Windows, where the issue reproduced, once in each?
Or maybe fifteen minutes is not enough?

Let's look through the history. Mac:
```
  iosX64WorkerWithNewMMTest.kotlinx.coroutines.sync.MutexTest
  iosX64WorkerWithNewMMTest.kotlinx.coroutines.sync.SemaphoreTest
  iosX64WorkerWithNewMMTest.kotlinx.coroutines.AtomicCancellationTest
  iosX64WorkerWithNewMMTest.kotlinx.coroutines.CommonThreadLocalTest
  iosX64WorkerWithNewMMTest.kotlinx.coroutines.ConcurrentExceptionsStressTest
  iosX64WorkerWithNewMMTest.kotlinx.coroutines.DefaultDispatcherConcurrencyTest
  iosX64WorkerWithNewMMTest.kotlinx.coroutines.IoDispatcherConcurrencyTest
  iosX64WorkerWithNewMMTest.kotlinx.coroutines.JobStructuredJoinStressTest
  iosX64WorkerWithNewMMTest.kotlinx.coroutines.LimitedParallelismConcurrentTest
    kotlinx.coroutines.LimitedParallelismConcurrentTest.testLimitedExecutor[iosX64 worker with new MM]
  :kotlinx-coroutines-core:iosX64WorkerWithNewMMTest (Thread[included builds Thread 2,5,main]) completed. Took 19.182 secs.
  :kotlinx-coroutines-core:compileTestKotlinJsIr (Thread[included builds Thread 2,5,main]) started.
  
  1178 tests completed, 1 failed, 1 skipped
  Test running process exited unexpectedly.
  Current test: testLimitedExecutor
  Process output:
   Uncaught Kotlin exception: kotlin.IllegalStateException: Dispatcher test was closed, attempted to schedule: LimitedDispatcher@53c3da80
  Invalid connection: com.apple.coresymbolicationd
      at 0   workerWithNewMM.kexe                0x109b6708d        kfun:kotlinx.coroutines.MultiWorkerDispatcher.dispatch#internal + 1389 (/opt/buildAgent/work/44ec6e850d5c63f0/kotlinx-coroutines-core/native/src/MultithreadedDispatchers.kt:123:23)
      at 1   workerWithNewMM.kexe                0x109b4c9a4        kfun:kotlinx.coroutines.internal.LimitedDispatcher#run(){} + 3236 (/opt/buildAgent/work/44ec6e850d5c63f0/kotlinx-coroutines-core/common/src/internal/LimitedDispatcher.kt:62:32)
  Child process terminated with signal 6: Abort trap
      at 2   workerWithNewMM.kexe                0x109b67fc8        kfun:kotlinx.coroutines.MultiWorkerDispatcher.$workerRunLoop$lambda$2COROUTINE$2988.invokeSuspend#internal + 1000 (/opt/buildAgent/work/44ec6e850d5c63f0/kotlinx-coroutines-core/native/src/MultithreadedDispatchers.kt:107:23)
      at 3   workerWithNewMM.kexe                0x109a1a371        kfun:kotlin.coroutines.native.internal.BaseContinuationImpl#resumeWith(kotlin.Result<kotlin.Any?>){} + 337 (/opt/buildAgent/work/460cf706e11bbdb0/kotlin/kotlin-native/runtime/src/main/kotlin/kotlin/coroutines/ContinuationImpl.kt:27:44)
      at 4   workerWithNewMM.kexe                0x109b4b317        kfun:kotlinx.coroutines.DispatchedTask#run(){} + 999 (/opt/buildAgent/work/44ec6e850d5c63f0/kotlinx-coroutines-core/common/src/internal/DispatchedTask.kt:106:71)
      at 5   workerWithNewMM.kexe                0x109ad61ac        kfun:kotlinx.coroutines.EventLoopImplBase#processNextEvent(){}kotlin.Long + 2204 (/opt/buildAgent/work/44ec6e850d5c63f0/kotlinx-coroutines-core/common/src/EventLoop.common.kt:<unknown>)
      at 6   workerWithNewMM.kexe                0x109b63479        kfun:kotlinx.coroutines#runBlocking(kotlin.coroutines.CoroutineContext;kotlin.coroutines.SuspendFunction1<kotlinx.coroutines.CoroutineScope,0:0>){0§<kotlin.Any?>}0:0 + 3641 (/opt/buildAgent/work/44ec6e850d5c63f0/kotlinx-coroutines-core/native/src/Builders.kt:56:5)
      at 7   workerWithNewMM.kexe                0x109b63876        kfun:kotlinx.coroutines#runBlocking$default(kotlin.coroutines.CoroutineContext?;kotlin.coroutines.SuspendFunction1<kotlinx.coroutines.CoroutineScope,0:0>;kotlin.Int){0§<kotlin.Any?>}0:0 + 118 (/opt/buildAgent/work/44ec6e850d5c63f0/kotlinx-coroutines-core/native/src/Builders.kt:36:15)
      at 8   workerWithNewMM.kexe                0x109b68735        kfun:kotlinx.coroutines.MultiWorkerDispatcher.$<init>$lambda$1$lambda$0$FUNCTION_REFERENCE$1718.$<bridge-UNN>invoke(){}#internal + 197 (/opt/buildAgent/work/44ec6e850d5c63f0/kotlinx-coroutines-core/native/src/MultithreadedDispatchers.kt:80:13)
      at 9   workerWithNewMM.kexe                0x109a20e6f        WorkerLaunchpad + 127 (/opt/buildAgent/work/460cf706e11bbdb0/kotlin/kotlin-native/runtime/src/main/kotlin/kotlin/native/concurrent/Internal.kt:87:54)
      at 10  workerWithNewMM.kexe                0x109fce5f5        _ZN6Worker19processQueueElementEb + 997
      at 11  workerWithNewMM.kexe                0x109fce19c        _ZN12_GLOBAL__N_113workerRoutineEPv + 108
      at 12  libsystem_pthread.dylib             0x7fff6bfee8fb     _pthread_start + 223
      at 13  libsystem_pthread.dylib             0x7fff6bfea442     thread_start + 14
```

Windows:
```
  mingwX64Test.kotlinx.coroutines.DefaultDispatcherConcurrencyTest
  mingwX64Test.kotlinx.coroutines.JobStructuredJoinStressTest
  mingwX64Test.kotlinx.coroutines.LimitedParallelismConcurrentTest
    kotlinx.coroutines.LimitedParallelismConcurrentTest.testLimitedExecutor[mingwX64]
      at 6   ???                                 7ff72e5e1de8       kfun:kotlinx.coroutines#runBlocking(kotlin.coroutines.CoroutineContext;kotlin.coroutines.SuspendFunction1<kotlinx.coroutines.CoroutineScope,0:0>){0§<kotlin.Any?>}0:0 + 3560
  :kotlinx-coroutines-core:mingwX64Test (Thread[Execution worker for ':' Thread 3,5,main]) completed. Took 15.29 secs.
  :kotlinx-coroutines-core:linkWorkerWithNewMMDebugTestMingwX64 (Thread[Execution worker for ':' Thread 3,5,main]) started.
      at 7   ???                                 7ff72e5e220e       kfun:kotlinx.coroutines#runBlocking$default(kotlin.coroutines.CoroutineContext?;kotlin.coroutines.SuspendFunction1<kotlinx.coroutines.CoroutineScope,0:0>;kotlin.Int){0§<kotlin.Any?>}0:0 + 126
      at 8   ???                                 7ff72e5e7090       kfun:kotlinx.coroutines.MultiWorkerDispatcher.$<init>$lambda$1$lambda$0$FUNCTION_REFERENCE$1709.$<bridge-UNN>invoke(){}#internal + 208
      at 9   ???                                 7ff72e49f555       WorkerLaunchpad + 133
      at 10  ???                                 7ff72ea4aea5       _ZN6Worker19processQueueElementEb + 1029
      at 11  ???                                 7ff72ea4aa2a       _ZN12_GLOBAL__N_113workerRoutineEPv + 90
      at 12  ???                                 7ff72ea50792       pthread_create_wrapper + 306
      at 13  ???                                 7ffb0e57b0ea       _ZSt25__throw_bad_function_callv + 16637819914
      at 14  ???                                 7ffb0e57b1bc       _ZSt25__throw_bad_function_callv + 16637820124
      at 15  ???                                 7ffb0c5a7974       _ZSt25__throw_bad_function_callv + 16604447892
      at 16  ???                                 7ffb0e66a2f1       _ZSt25__throw_bad_function_callv + 16638799377
```

Hey, got it! Twenty minutes were enough to reproduce the issue. So, no intertest
interaction magic this time.

Let's try to simplify the test a bit an see if it still reproduces.
First, try to remove the loops, just launch a single coroutine in a coroutine
scope instead.

Hey, no, I don't think it will work. The stack traces are all with the *workers*
trying to schedule another task, so we'll need at least some non-trivial task
interaction. Ok, let's just reduce the number of iterations from `100_000`
to 100. If the bug doesn't depend on the overwhelming amount of coroutines to
surface, it should trigger even faster, since more iterations will have a chance
to happen in the same span of time.

Maybe I should put my computer under more stress to trigger various
interleavings. Play Elden Ring or something. Just kidding, of course: these
business-oriented laptops probably can't even run Portal with their measly
videocard.

It still failed, in just 12 minutes. Good!

Ah, the IDEs.

```kotlin
    @Test
    fun testLimitedExecutor() {
        while (true) {
            runTest {
                val executor = newFixedThreadPoolContext(targetParallelism, "test")
                val view = executor.limitedParallelism(targetParallelism)
                doStress {
                    repeat(100) {
                        launch(view) {
                            checkParallelism()
                        }
                    }
                }
                executor.close()
            }
        }
    }

    private suspend inline fun doStress(crossinline block: suspend CoroutineScope.() -> Unit) {
        repeat(stressTestMultiplier) {
            coroutineScope {
                block()
            }
        }
    }
```

"Inline `doStress`!"

```kotlin
    @Test
    fun testLimitedExecutor() {
        while (true) {
            runTest {
                val executor = newFixedThreadPoolContext(targetParallelism, "test")
                val view = executor.limitedParallelism(targetParallelism)
                repeat(stressTestMultiplier) {
                    coroutineScope {
                        repeat(100) {
                            launch(view) {
                                checkParallelism()
                            }
                        }
                        this.
                    }
                }
                executor.close()
            }
        }
    }
```

Yeah, let's do this by hand instead by just replacing `doStress` with
`coroutineScope`.

So, yeah, the next step is to try to remove the extra code between
`coroutineScope` and `executor.close()`. You never know if it's the compiler
that messed up.

Great, it failed even quicker, in 10 minutes, after about 4k iterations.
When I manage to take it down to 5 minutes, I'll try removing the
`limitedParallelism` part, though it really seems to be the key of the issue:
looking at the code of `LimitedDispatcher`, I get a suspicion that it can
occasionally schedule tasks even when the dispatcher no longer supports it...

Yeah, this seems to be the issue. *Almost* all the dispatches are in response
to a task arriving, but there's one exception: to support fairness, the
procedure used by `LimitedDispatcher` sporadically re-dispatches itself.
Let's try to artificially increase the rate at which it happens by making the
dispatchers more fair...

Yes, this time it took just a thousand iterations to encounter the error
condition.

Well, this is going to be tough to fix.

Or not! The trick is to have a separate object for each thread currently doing
work in `LimitedDispatcher`. This object always stores the current task, so it's
impossible for it to exist unless there's a task ready for it. And the changes
to the source code also look simple.

Of course, there's always the possibility of this:
```
kotlinx.coroutines.CoroutinesInternalError: Fatal exception in coroutines machinery for DispatchedContinuation[LimitedDispatcher@56de5042, Continuation at kotlinx.coroutines.LimitedParallelismConcurrentTest$testLimitedExecutor$1$1$1$1.invokeSuspend(LimitedParallelismConcurrentTest.kt)@5fce8ff7]. Please read KDoc to 'handleFatalException' method and report this incident to maintainers
```

Several tests fail with this. Oh well, let's dig in.

Silly me, I allowed to run the same task several times.

What, that wasn't it? I'm in deep now.


It wasn't easy, but finally, I managed to find the culprit and fix the issue:
<https://github.com/Kotlin/kotlinx.coroutines/pull/3672>


2023-03-13
----------

If something can be depended on, it will be depended on:
<https://github.com/Kotlin/kotlinx.coroutines/issues/3673>.

Thinking about <https://github.com/Kotlin/kotlinx.coroutines/issues/3658>.

Let's imagine a scary function that wraps the pattern:
```kotlin
/**
 * An equivalent to a `catch` block, but for code using coroutines: cancellations of operations will not be passed to
 * the handler, even if it catches any `Throwable`.
 *
 * Usage example:
 * ```
 * suspend {
 *   withTimeout(1.seconds) {
 *     CompletableDeferred<Int>().await()
 *   }
 * }.catchInCoroutines { e: Throwable ->
 *   println("caught $e")
 * }
 * ```
 */
public suspend inline fun<T, reified E : Throwable> (suspend () -> T).catchInCoroutines(handle: (E) -> T): T =
    try {
        this()
    } catch (e: Throwable) {
        currentCoroutineContext().ensureActive()
        if (e is E) {
            handle(e)
        } else {
            throw e
        }
    }
```

Does this function meet the high standard of being suitable as a language
primitive? If not, about which issues should the users of the pattern know?

Let's consider the list `p` of statements performed during a particular
execution. Looks like, if `p` is empty, then
`suspend { p }.catchInCoroutines(handle)` is a no-op. If `p == p1 + p2`, then
```kotlin
suspend { p }.catchInCoroutines(handle)
```
behaves the same as
```
var throwable: Throwable? = null
suspend { p1 }.catchInCoroutines {
  throwable = it
  handle(it)
}
if (throwable == null) {
  suspend { p2 }.catchInCoroutines(handle)
}
```
So far, this is typical behavior for `catch` blocks, which roughly means we have
a homomorphism from plain executions to executions wrapped in
`suspend { ... }.catchInCoroutines(handle)` with premature completion semantics.
The homomorphism means that it's enough to consider the executed operations one
by one.

Which operations can those be?
* Non-throwing non-suspend operations. `catchInCoroutines` exhibits no special
  behavior.
* Suspending. Such operations are exactly the ones for which this is done.
  If cancellation races with suspending, we get exactly the behavior we would
  usually get, only with the correct handling of scenarios where a suspending
  function throws a `CancellationException`.
  However, we get non-linearizability when someone observes the continuation as
  being not cancelled but completed, but the operation as a whole still finishes
  with a `CancellationException`. This shouldn't be an issue with the prompt
  cancellation guarantee used throughout the coroutines though, which leads to
  the same behavior. The pattern also wouldn't be leaky in this case, as the
  prompt cancellation guarantee is the same as what happens here.
* `throw X`. The pattern makes this a suspension point for some reason, but I
  don't see cases where this could harm anything.

Need to think a bit more about this. Maybe change the framework.


2023-03-15
----------

Yesterday, I just went ahead and read the issue trackers, and also played a
little bit with the build of kotlinx-datetime:
<https://github.com/Kotlin/kotlinx-datetime/pull/259>

Today, added the supported Kotlin/Native targets:
* <https://github.com/Kotlin/kotlinx-datetime/pull/259>
* <https://github.com/Kotlin/kotlinx-datetime/pull/260>
* <https://github.com/Kotlin/kotlinx-datetime/pull/261>

I should probably finish
<https://github.com/Kotlin/kotlinx.coroutines/pull/3672>: now that I think about
it, there are at least some cases when this could probably be easily tested.

Yeah, turned out just fine.


2023-03-16
----------

Still dealing with kotlinx-datetime build, but I also came back to work on the
formatting things.

First, my computer crashed due to compiling two Gradle projects at a time.
At that moment, I lost several journal entries. Then, I wrote about it here, and
then, the computer crashed the second time. Now I don't know the reason.

Looked into <https://github.com/Kotlin/kotlinx.coroutines/pull/3678>,
and checked whether the train integration works in the `kotlinx-datetime`, and
it does.


2023-03-17
----------

Going back to datetime formatting. Time to punch the keyboard keys, expressing
my thoughts.


2023-03-18
----------

So, the immediate task right now is to restructure the internal `Parser` API so
that the consumers (in the form of `Format`) don't have to jump through any
hoops. Right now, the `ParserStructure.simplify` function in `Format` actually
just implements the second stage of the monoidal concatenation.
Non-simplified `Parser` instances are just plain illegal.

There's the slight issue when implementing a monoidal `append` for `Parser` that
also performs simplification: it seems to have an inherent worst-case complexity
of `O(size of the left operand)`. If we build a `Format` using this, we get a
`O(size of the format ^ 2)` complexity of building the parser, which is not so
tiny already.

Probably need a custom `mconcat` here.


It turned out to be very involved and error-prone. Unfortunately, I only have
the ISO 8601 serialization tests to rely on so far, and can't be sure about the
fine points of the behavior. So, a better idea for now is to grab the top
most used formats, specify them and see if there are any issues.

Here's the code I'm using to generate test data:

```kotlin
const val DATE = 1
const val TIME = 2
const val DATETIME = 3
const val OFFSET = 4

fun name(tp: Int) = when (tp) {
    DATE -> "LocalDate"
    TIME -> "LocalTime"
    DATETIME -> "LocalDateTime"
    OFFSET -> "UtcOffset"
    else -> throw UnsupportedOperationException("$tp")
}

fun ZonedDateTime.constrString(tp: Int) = when (tp) {
    DATE -> "LocalDate($year, $monthValue, $dayOfMonth)"
    TIME -> "LocalTime($hour, $minute, $second, $nano)"
    DATETIME -> "LocalDateTime($year, $monthValue, $dayOfMonth, $hour, $minute, $second, $nano)"
    OFFSET -> "UtcOffset(${offset.toKotlinUtcOffset().format("hh, mm, ss")})"
    else -> throw UnsupportedOperationException("$tp")
}

fun escapeString(str: String) = '"' + str + '"'

val interestingDates: List<LocalDate> = listOf(
    LocalDate(2008, 7, 5),
    LocalDate(2007, 12, 31),
    LocalDate(999, 12, 31),
    LocalDate(-1, 1, 2),
    LocalDate(9999, 12, 31),
    LocalDate(-9999, 12, 31),
    LocalDate(10000, 1, 1),
    LocalDate(-10000, 1, 1),
    LocalDate(123456, 1, 1),
    LocalDate(-123456, 1, 1),
)

val interestingTimes: List<LocalTime> = listOf(
    LocalTime(0, 0, 0, 0),
    LocalTime(1, 0, 0, 0),
    LocalTime(23, 0, 0, 0),
    LocalTime(0, 1, 0, 0),
    LocalTime(12, 30, 0, 0),
    LocalTime(23, 59, 0, 0),
    LocalTime(0, 0, 1, 0),
    LocalTime(0, 0, 59, 0),
    LocalTime(0, 0, 0, 100000000),
    LocalTime(0, 0, 0, 10000000),
    LocalTime(0, 0, 0, 1000000),
    LocalTime(0, 0, 0, 100000),
    LocalTime(0, 0, 0, 10000),
    LocalTime(0, 0, 0, 1000),
    LocalTime(0, 0, 0, 100),
    LocalTime(0, 0, 0, 10),
    LocalTime(0, 0, 0, 1),
    LocalTime(0, 0, 0, 999999999),
    LocalTime(0, 0, 0, 99999999),
    LocalTime(0, 0, 0, 9999999),
    LocalTime(0, 0, 0, 999999),
    LocalTime(0, 0, 0, 99999),
    LocalTime(0, 0, 0, 9999),
    LocalTime(0, 0, 0, 999),
    LocalTime(0, 0, 0, 99),
    LocalTime(0, 0, 0, 9),
)

val interestingOffsets: List<UtcOffset> = listOf(
    UtcOffset(-18),
    UtcOffset(-17, -59, -58),
    UtcOffset(-4, -3, -2),
    UtcOffset(0, 0, -1),
    UtcOffset(0, -1, 0),
    UtcOffset(0, -1, -1),
    UtcOffset(-1, 0, 0),
    UtcOffset(-1, 0, -1),
    UtcOffset(-1, -1, 0),
    UtcOffset(-1, -1, -1),
    UtcOffset(0, 0, 0),
    UtcOffset(0, 1, 0),
    UtcOffset(0, 1, 1),
    UtcOffset(1, 0, 0),
    UtcOffset(1, 0, 1),
    UtcOffset(1, 1, 0),
    UtcOffset(1, 1, 1),
    UtcOffset(4, 3, 2),
    UtcOffset(17, 59, 58),
    UtcOffset(18),
)

fun generateTestData(tp: Int, pattern: String, secondsPresent: Boolean, nanosPresent: Boolean) {
    val times = if (nanosPresent) {
        interestingTimes
    } else if (secondsPresent) {
        interestingTimes.filter { it.nanosecond == 0 }
    } else {
        interestingTimes.filter { it.second == 0 && it.nanosecond == 0 }
    }
    val format = DateTimeFormatter.ofPattern(pattern)
    println("buildMap<${name(tp)}, Pair<String, Set<String>>> {")
    val interestingZdts: List<ZonedDateTime> = when (tp) {
        DATE -> interestingDates.map {
            it.toJavaLocalDate().atStartOfDay(ZoneId.of("UTC"))
        }
        TIME -> times.map {
            it.toJavaLocalTime().atDate(
                LocalDate(2000, 1, 1).toJavaLocalDate()
            ).atZone(ZoneId.of("UTC"))
        }
        DATETIME -> interestingDates.paddedZip(times, LocalDate(2022, 1, 2), LocalTime(13, 44, 0)) { date, time ->
            time.toJavaLocalTime().atDate(date.toJavaLocalDate()).atZone(ZoneId.of("UTC"))
        }
        OFFSET -> interestingOffsets.map { ZonedDateTime.of(2020, 5, 6, 20, 15, 58, 0, it.toJavaZoneOffset()) }
        else -> throw UnsupportedOperationException("$tp")
    }
    for (zdt in interestingZdts) {
        println("  put(${zdt.constrString(tp)}, (${escapeString(format.format(zdt))} to setOf()))")
    }
    println("}")
}

fun<T, U, A> Iterable<T>.paddedZip(other: Iterable<U>, defaultLeft: T, defaultRight: U, transform: (T, U) -> A): List<A> {
    val first = iterator()
    val second = other.iterator()
    val list = mutableListOf<A>()
    while (first.hasNext() && second.hasNext()) {
        list.add(transform(first.next(), second.next()))
    }
    while (first.hasNext()) {
        list.add(transform(first.next(), defaultRight))
    }
    while (second.hasNext()) {
        list.add(transform(defaultLeft, second.next()))
    }
    return list
}
```

This is one more case where a `repr()`-like functionality in Kotlin would be
welcome: then, I'd be able to avoid `constrString` and would instead just get
the string representation of "interesting" objects.

Trying to understand why the format `"yyyy"` is so popular, via
<https://grep.app/search?q=%28of%7Cappend%29Pattern%5C%28%22yyyy%22%5C%29&regexp=true&filter[lang][0]=Java&filter[lang][1]=Scala&filter[lang][2]=Kotlin>.
* <https://github.com/elastic/logstash/blob/main/tools/benchmark-cli/src/main/java/org/logstash/benchmark/cli/ui/UserOutput.java>
  Here, it's used as part of an (incorrectly, localized) format.
* <https://github.com/buchen/portfolio/blob/master/name.abuchen.portfolio.ui/src/name/abuchen/portfolio/ui/util/chart/TimeGridHelper.java>
  Depending on how far the moment was, we may only be interested in the year.
* <https://github.com/elastic/elasticsearch/blob/main/x-pack/plugin/sql/src/main/java/org/elasticsearch/xpack/sql/expression/function/scalar/datetime/DateFormatter.java#L145>
  Here, it's unclear how the format is localized, but the approach to caching
  the builder objects is notable.
* <https://github.com/201206030/novel/blob/master/src/main/java/io/github/xxyopen/novel/service/impl/ResourceServiceImpl.java#L56>
  Hey, this is interesting. `yyyy/mm/dd`, but separated with the filesystem `/`.

<https://github.com/StarRocks/starrocks/pull/19846> helping out a bit while I'm
at it.


2023-03-21
----------

Reviewing the pull requests in coroutines for a start.
<https://github.com/Kotlin/kotlinx.coroutines/pull/3686/files#r1143242723> looks
like an interesting challenge.

Turned out fairly simple.

Updated <https://github.com/Kotlin/kotlinx-datetime/pull/257>.

Finished reviewing the current coroutine requests, and also started to work on
the "Java datetime format string to Kotlin formatter" conversion function.


2023-03-22
----------

I see that <https://docs.oracle.com/javase/8/docs/api/java/text/DateFormatSymbols.html#getMonths-->
provides the names of formatted months, but where are the standalone month
names? Are they even available from the locale? I can't find any code in 310bp
that would properly build the standalone month names dictionary.


2023-03-23
----------

Yeah, with 310bp, I get a failure in
```
Locale ru = new Locale("ru", "RU");
DateTimeFormatter test = DateTimeFormatter.ofPattern("LLLL", ru);
assertEquals("Январь", test.format(LocalDate.of(2012, 1, 1)));
```
"января" is returned instead.

So, I guess there's just no direct access to the standalone forms in the Java
locale API?

Let's see how much the built-in Java formatters are used, by the matches on
grep.app:
* `BASIC_ISO_DATE` (`20230123`): 123
* `ISO_DATE` (`2023-03-24` or `2023-03-24+01:00`): 363
* `ISO_DATE_TIME` (`2023-03-24T21:35:31`, `2023-03-24T21:35:31+01:00`,
  or `2023-03-24T21:35:31+01:00[Europe/Paris]`): 592
* `ISO_INSTANT` (`2023-03-24T21:35:31Z`): 566
* `ISO_LOCAL_DATE` (`2023-03-24`): 688
* `ISO_LOCAL_DATE_TIME` (`2023-03-24T03:53:21`): 565
* `ISO_LOCAL_TIME` (`03:53:21`): 313
* `ISO_OFFSET_DATE` (`2023-03-24+01:00`): 23
* `ISO_OFFSET_DATE_TIME` (`2023-03-24T23:31:33+01:00`): 783
* `ISO_OFFSET_TIME` (`23:31:33+01:00`): 84
* `ISO_ORDINAL_DATE` (`2021-134`): 23
* `ISO_TIME` (`03:53:21`, `03:53:21+01:00`): 98
* `ISO_WEEK_DATE` (`2012-W48-6`): 27
* `ISO_ZONED_DATE_TIME` (`2023-03-24T21:35:31+01:00[Europe/Paris]`): 248
* `RFC_1123_DATE_TIME` (`Tue, 3 Jun 2008 11:05:35 GMT`): 352

Some very surprising finds here. I'd never guess that `ISO_ZONED_DATE_TIME` was
so popular. That said, the timezone section in that format is optional, in spite
of what the table in the documentation for `DateTimeFormatter` claims, so it can
be used the same way as `ISO_OFFSET_DATE_TIME`.

Oh! <https://dev.epicgames.com/documentation/en-us/uefn/verse-language-reference>
Finally, the Verse language is here.

2023-03-24
----------

Final edits to the paper.

2023-03-27
----------

Looks like I'm all done with coroutines for now.

Setting up a Windows virtual machine on my computer to be able to write the pure
Kotlin implementation for it. I already had success with the
<https://github.com/quickemu-project/quickemu> tool, so hopefully, I'll get it
running.

Alas, nothing is so simple. 

I use the Nix package manager on top of the corporate-issued Ubuntu. Naturally,
most software works fine, but some doesn't, namely, anything that uses OpenGL.
It's a known issue with the Nix package manager: unless you use NixOS, the
package manager has no way of knowing what libraries implementing the OpenGL
interface to link to. The issue is, they are heavily dependent on the kernel
version. These libraries are very tightly coupled with the videocard drivers
and need to be updated in tandem with them, but without knowing which kernel is
used, there's no way to do that.

There's a known fix for that: just call <https://github.com/guibou/nixGL>, and
you're all set. Except it doesn't work for me for some reason.
Even more surprisingly, even plain, non-Nix `glxinfo` doesn't work:
```
$ glxinfo
name of display: :0
Error: couldn't find RGB GLX visual or fbconfig
```

The complicated thing is that I'm using `sway` from Nix to run everything.
Could XWayland be to blame? Could Ubuntu? Could sway? Or Nix?

Looking through the output of `set`, I don't see anything suspicious.
So, likely, `sway` is not to blame: it's not the window manager's responsibility
to configure OpenGL.

While I'm researching this, I managed to find a workaround: run `qemu` with
the Spice display, not the SDL one, and so without OpenGL. Still, OpenGL is
worth fixing.

*Meanwhile*, let's try getting rid of the `infra` plugin in kotlinx-datetime.
Maybe it's the root of my bigger issue: lack of autocompletion of
platform-specific declarations in Kotlin/Native.

2023-03-28
----------

To take a break from all the formatting business, today, I'm rewriting the
Windows implementation of datetime library to pure Kotlin. Unfortunately,
I used this function initially:
<https://learn.microsoft.com/en-us/windows/win32/api/timezoneapi/nf-timezoneapi-enumdynamictimezoneinformation>
Turns out, I shouldn't have used anything that doesn't work on Windows 7:
<https://github.com/Kotlin/kotlinx-datetime/issues/262>

Now, I have to dive deeply into how Windows stores the timezones in the
registry.

That said, it's still a simple, straightforward task with a clear scope.
I'm really enjoying the process, even though the experience is not really
enjoyable: I'm writing WinAPI code in Kotlin with the IDE running in a virtual
machine without hardware acceleration of graphics.


2023-04-17
----------

So, back from a vacation, after which I didn't write the journal for several
more days: they were too hectic, as days immediately following vacations are
known to be, with much correspondence to wade through. After that, I worked a
bit on the datetime formatting, mainly implementing the mechanism of sign
handling.

The final idea as of now is that each field may have a sign associated with it,
and several fields may have the same associated sign. For example, in the UTC
offset, all the fields are linked to the same sign. After much thought,
I reached the simple conclusion that, in `+03:30`, the `30` should never be
allowed to have a separate sign, it's just not how the UTC offset formats work
in real life. This creates a bit of a disparity between the formatting API and
the `UtcOffset(hours, minutes, seconds)` constructor that forces each field to
have the same sign, but still, to have a sign.

Oh yeah, I also finally implemented the conversion function from the Unicode
format strings to the datetime formatter.
Next, I'm going to work on the `repr` for the formatters so that it's easy to
migrate from the formatter received from the migration functions.

A huge irritation for me is the issue of different representations for the same
thing: 24-hour VS 12-hour + AM/PM; a signed number VS an unsigned + a sign;
year + month + day VS week-year + week-of-year + weekday, etc. I've encountered
this often enough already and my code is sufficiently full of workarounds for
this that I'm starting to wonder if I'm missing some approach that would allow
me to express these alternatives clearly.

Ah, how do I miss the lenses.


2023-04-18
----------

So, today, the weather is a bit terrible, so I don't have it in me to do
anything demanding. I'm just slowly trudging through writing tests for the
datetime formatting and, when tired of that, fixing the laptop. Recurring
readers of this blog may remember that I had issues with OpenGL not working.
Well, I found the culprit, though I still don't wholly understand what's going
on.

Turns out, on Wayland, the window manager *does* affect how the normal windows
behave. Even if programs know where to find the OpenGL drivers, Wayland itself
handles the OpenGL context somehow and prevents the programs from reaching it.
So, when running Wayland, OpenGL programs depend on whether the Wayland
compositor works with OpenGL...

... But, if the compositor is from `home-manager` (that is, from the Nix
package manager), then it can't use the OpenGL implementation. The OpenGL
libraries are tightly coupled with the video drivers, and not knowing what the
kernel (or probably even the OS) this is, Nix has no way of accessing the
correct OpenGL implementation.

So, the solution is to install `sway` system-wide and launch that. The config
is still controlled by `home-manager`, so no portability is lost. Maybe there
will be some bugs due to version mismatches, but it still beats having no
OpenGL.

Now, OpenGL works:
```
$ glxinfo | grep Device
    Device: Mesa Intel(R) UHD Graphics 630 (CFL GT2) (0x3e9b)
```

It's interesting though how I could utilize the NVidia GPU. It's fairly
powerful, and I'm sure the Android emulator and the QEMU window, which I
occasionally do have to launch, would appreciate it. The Ubuntu has
Bumblebee preinstalled, but
```
$ optirun glxnifo
[ 2762.920495] [ERROR]Cannot access secondary GPU - error: [XORG] (EE) Failed to load module "mouse" (module does not exist, 0)
```

After installing the mouse package `xserver-xorg-input-mouse` and rebooting,
I get the error
```
[   65.350018] [ERROR]Cannot access secondary GPU - error: [XORG] (EE) NOUVEAU(0): [drm] failed to set drm interface version.
```

...

Hey, wait.

```
$ DRI_PRIME=1 glxinfo | grep "Device"
    Device: NV137 (0x1cbb)
```

I don't need any `optirun`, `primusrun`, or anything like that, it seems.
I wonder how it works, but I'll take the easy win gladly.

Also, I fixed the messed-up boot process. In general, it seems, now I have a
work machine that works very nicely. With OpenGL enabled, I get *immensely*
better performance from QEMU, as expected, but for some reason, the CPU usage
dropped overall.


2023-04-19
----------

A nice rainy day, perfect for staying at home and reading some books. Not an
option though. Everyone's waiting for me to finish the goddamn formatting PR.
I just need to focus and perform the (another) final rush. It's funny that
I've been doing these "final rushes" for a while now, each time thinking,
"This is it! One final rush!" This time, though, it looks like there's just
no obsure places for complexity to hide in anymore. I'm completely exhausted
from all this already, but I don't think I regret the effort I put into this.
We don't grow without being challenged, and this marathon, for sure, was a huge
challenge. I learned a lot about pushing myself to the limit while keeping the
marathon pace, without turning this into a series of sprints. And also, about
Kotlin's type system.

Now's as good a time as any to list the notable omissions that still need to be
implemented before the draft PR of formatting can be considered
feature-complete.

* Yes, I still need to implement pretty-printing of formatters. Otherwise,
  there's no clear migration path to our formatting capabilities.
* I think I should make `-` a non-special character in the format strings.
  `yyyy-mm-dd` is too common a format to write it as `yyyy'-'mm'-'dd`,
  complaining about months being unsigned otherwise.
* Parser needs to recognize the concepts of "inside a word" and "inside a
  number", preventing `find` from parsing strings like `12020-01-02` as
  `2020-01-02`.
* Actually, I am yet to expose `find` through the public API. Need to do that.
* Also, introduce the `DateTimeFormat<T>` interface for the `parse`, `toString`,
  `find`, etc, so that they share the documentation.

There's a week and a half left until May. Let's warm up my deadline angst by
promising to this diary that I'm going to make it until then. My calendar is
open next to me, and crossing out days one after another is sure to boost my
productivity.

For any readers not from JetBrains, I should note that this soul-crushing
experience is completely self-imposed. I guess I'm just the kind of person that
enjoys miserably plowing through insurmountable tasks, listening to the heavy
rock songs from the Sonic the Hedgehog game series OST. Examples:
* <https://soundcloud.com/user-675036643/sonic-frontiers-ost-do-or-die-boss-theme>
* <https://soundcloud.com/csaba-jynx-padar/sonic-adventure-open-your-heart-crush-40>
* <https://soundcloud.com/sonic-the-hedgeog/sonic-adventure-2-live-and-learn>

That's the way I worked through all the difficult tasks I've had, like
a year and a half of formal proofs, or the test framework for the coroutines.

Reviewed the release notes for the new release of coroutines. Working on the
pretty-printing of formatters.


2023-04-25
----------

Fell ill for a couple of days. I've still not recovered fully, but to avoid work
any longer would mean to subject myself to doctors and formally getting a sick
leave. Given that I'm clearly non-infectious, this is not happening.

Meanwhile, I somehow managed to implement the pretty-printers for datetime
formatters.


2023-04-27
----------

So, I finished with the things I listed as the technical preliminaries for
datetime formatters. I also remembered one more important thing: space-padding.
It's not there in any shape or form yet. This is not a huge deal, as there are
almost no formats that use space padding.

There are interesting issues I uncovered with the `find` family of functions
when I implemented them. Nothing major, and certainly not a problem in the happy
case, but still worth deciding on.

If we see a string like `25:12:34` in the text and are searching for `hh:mm`,
should we recognize `25:12`? `12:34`? Both? None? This case is clear-cut: if
searching for a `ValueBag` (which, by design, does not do any input validation),
`25:12` should be found, whereas `12:34` should not, since `25` is clearly the
leading component here. If searching for a `LocalTime`, then not finding
anything is the proper solution.

If we generalize this case to a rule, it will be this:
* Search for strings matching the pattern.
* If a string also forms a valid instance, it's a match, and the part of the
  string that matched should be skipped: we don't want to reinterpret it anew.
* If the string *does not* form a valid instance, it's not a match (though
  whether or not we do input validation depends on the caller), but we skip
  it anyway.

With the case of `hh:mm`, it's clearly the way to go. If we can't consider the
leading component of a series like `A:B:C` to be the hours, then the whole
series is unlikely to be the local time.

However, are we losing anything valuable with such rules by skipping over
matched-but-invalid strings?

Consider `mm/dd`. If we have `2018/07/05`, could we lose `07/05`? We actually
could not, because `2018` does not match the pattern `mm` itself. Each field has
the corresponding maximum length (where applicable). I've seen local times with
more than 24 hours, but they were all less then 48 hours; I've seen 40 as the
day-of-month, but never something larger than 100; I think it is completely
sensible to limit the field length even for non-resolved datetimes.

On the other hand, if we were looking for `mm/yyyy` and got `05/07/2018`, we
also wouldn't lose `07/2018`, because `07` doesn't match the `yyyy` field due to
the mandatory four-character lower bound.

If we were looking for `m/y` and got `05/07/2018`, we would grab `m = 05`,
`y = 07` and leave, even though it's not correct, but with such format, I don't
think there's an obligation for us to deal sensibly with such formats.
Garbage in, garbage out.

I can't really think of a real format + a real input that would lead to
undesirable results. There's the issue that `|` is novel and was not yet
attempted, so we do not know what expectations and hopes people would place on
it. Maybe someone would want to parse a format like `mm/(yyyy|y)` and expect it
to, I don't know, first try searching for `mm/yyyy` everywhere and only attempt
`mm/y` on the portions of the string without matches. No idea. This would be
neat though! Can't think of a reason to implement such behavior, unfortunately,
or even to think it through.


2023-05-02
----------

The week promises to be tricky. I just started out, but already has IDEA spoiled
my day by hanging each time I try to inline something. Can't I rely on *any*
behavior with this IDE? Inlining, as they implemented it, ignoring side effects
and such, is the simplest thing to do. No idea who's to blame, but on this
occasion, I can be *sure* it's not me, and I'm displeased.

What's going on? It hanged even when I just tried to type something.

Everything's fine with my filesystem, I think. I successfully edited the file
in Vim.

Ok, after `pkill -kill java`, I manage to type successfully.


2023-05-03
----------

Freezes become more common for some reason. Just now, the IDE freezed for
ten minutes when I asked it to copy a reference to a thing to my clipboard.


2023-05-04
----------

Once again messed up the dates. Fixed.
Also, today, the IDE freezes every ten minutes of use. One more day of this,
and I'll have to look into it.

So, not much interesting happened this week. After the marathon of implementing
the datetime formatting, I took it slow and mostly reviewed PRs and replied to
user inquiries, thinking about what to do next.

Today, a sign from the above told me what to focus on: fix a couple of the
remaining warts in the test module of coroutines. A user on the Kotlin Slack
channel asked yet another time how to collect all `SharedFlow` emissions
without conflation in the sad case when the emissions themselves happen in
an unconfined dispatcher. The answer is, due to the event loop, this is
difficult.

I strongly suspect that not having the option to disable the event loop is a
mistake. I'll probably gather some evidence to that effect and call upon a
design meeting.

However, with the test module, a **true** unconfined dispatcher is not
actually required in most cases. What is needed is for me to fix
<https://github.com/Kotlin/kotlinx.coroutines/issues/3298>. This way, most
uses of the unconfined dispatcher will not actually happen. There's no one
else to fix this, after all.

So, here's the issue: it's not clear at all what the "correct context" would
be where dispatches are not needed in the case of
`setMain(StandardTestDispatcher)`. If we just say that being in the main test
thread means that dispatches are not needed, all kinds of issues will
surface: for example, mocking `Dispatchers.IO` or `Dispatchers.Default` with the
test dispatcher will cause inline execution of tasks passed to
`Dispatchers.Main.immediate`.

Important points:
* The platform-specific implementations of the event loop.
  Maybe, if I were to replicate the logic of "we're actually in the event loop"
  to mean "we're actually dispatched by the mocked `Dispatchers.Main`", it would
  be an important piece of the equation.
* Maybe <https://github.com/Kotlin/kotlinx.coroutines/issues/3326> is the key.
  In essence, `ThreadContextElement` is make-believe thread-specific state
  spanning across threads: keeping the thread-local state available across
  thread switches in a coroutine. Yeah, well, no, it doesn't seem relevant now
  that I think about it: when switching execution to a different thread, we
  *do* most likely want not to consider ourselves to be executing in the
  `Dispatchers.Main` anymore.
* The event loop can already bite us when mocking `Dispatchers.IO`:
  ```kotlin
  launch(Dispatchers.Unconfined) {
    launch(Dispatchers.IO) {
      launch(Dispatchers.Unconfined) {
        println("Should be printed second")
      }
      println("Should be printed first")
    }
  }
  ```

2023-05-08
----------

So, the 1.7.0 release of the coroutines library was published last Friday, and
there's breakage in the `kotlinx-coroutines-test` module, for whch I'm
responsible.

Initially, we learned about <https://github.com/Kotlin/kotlinx.coroutines/issues/3673>
when we published the "beta" release of 1.7.0. The gist of it is, some library
depended on `runTest`, and some people depended on *that* library transitively.
We introduced a source-compatible but binary-incompatible change in 1.7.0-Beta,
which caused all the users of *that* library to observe build failures when they
ugraded to the new coroutines version.

I thought I fixed the issue in 1.7.0-RC ("release candidate") by introducing a
fake stub for binary compatibility. The fix was flawed and didn't actually fix
anything, but no one complained. So, we released 1.7.0 with the same faulty fix.

Now that the release is out, we should evaluate the impact that the issue has.

First of all, "that library" is a widely-used test artifact for Android's
version of the widely used Compose framework:
<https://androidx.tech/artifacts/compose.ui/ui-test-junit4/1.5.0-alpha03-source/androidx/compose/ui/test/ComposeUiTest.android.kt.html>
This is the source code for the latest release of that library.

Second point:
**many** people use that library: <https://grep.app/search?q=ui-test-junit4>,
<https://github.com/search?q=ui-test-junit4&type=code>.

Third: it doesn't look like we can expect a release of that library that depends
on the latest test framework anytime soon. Why do I think that? Well, looking at
the latest source code, I see use of lots of deprecated API. Even the
non-deprecated API is not used the way it should be. I think it would take them
quite a bit of time to fix their code so that it even compiles.

Of course, given the scope of the breakage, they could concievably just add
`@Suppress` and push out a release, but I don't think they will. After all, they
have no stake in the latest update of the coroutines library being widely
adopted and can take their time. Nobody will migrate from their elaborate
contraption just because it broke because of them using API where we didn't
promise any stability.


Right now, to remind myself that there's life outside the datetime formatting,
I'm going on the Odyssey of reading through the open issues on the coroutines
tracker:
<https://github.com/Kotlin/kotlinx.coroutines/issues?page=10&q=is%3Aissue+is%3Aopen>

I've done it already, but I think I should do it once more with the newfound
insights. Maybe I'll see some low-hanging fruit.

2023-05-09
----------

Spent some time wrapping my head around infinities in the comment section of
<https://github.com/Kotlin/kotlinx-datetime/pull/271>.

Reading through the issues one at a time. I think I'll publish a separate file
in this repository with my takes on those issues. Sometimes, I may publish those
takes to the comments directly under the issues, but most of the time, I don't
think my understanding could help the discussion a lot.

Meanwhile, I'm also browsing through the coroutine code and fixing things here
and there, like
* <https://github.com/Kotlin/kotlinx.coroutines/pull/3747>
* <https://github.com/Kotlin/kotlinx.coroutines/pull/3746>


2023-05-10
----------

A thought.

So, here I have a bunch of documents that are actually useful. However, they are
not in any repository, because they are my personal notes and not some
production-quality docs. I really don't have the capacity to prettify them.
Things change too quickly, the reasoning becomes outdated. But *I* do have all
the context and will be able to tell if the information is outdated. Also, *I*
am the main user of these notes.

Well, why not just publish them here?
It's not like they are some kind of a secret, but they *are* useful to me, and
publishing them here would make everything less disastrous were my SSD to die,
for example.

2023-05-12
----------

Looking at <https://github.com/Kotlin/kotlinx.coroutines/issues/3673> closely.
If I don't find a workaround, we'll have to publish a new release, preferably
today.

<https://github.com/Kotlin/kotlinx.coroutines/issues/3673#issuecomment-1537441738>
this guy helpfully provided their code where the issue reproduces. Let's
try some things.

For example, <https://github.com/Kotlin/kotlinx.coroutines/pull/3742#issuecomment-1544320961>
this approach initially confused me greatly: how would this work? However,
admittedly, I don't know much about how linking happens in Java. What if you
can add a file with `package kotlinx.coroutines.test`, and it will know to link
properly?

Well, it's unlikely. The specific method called is in the `TestBuildersKt`
class, so you'd have to have a class with a conflicting name, which will
certainly not work correctly, even if it is allowed somehow: that
`TestBuildersKt` won't have any of the other methods that it should. What was
I even thinking.

Ok, I published the coroutines patch locally. Let's look if the reproducers
start to pass.
Let's try this one: <https://github.com/sebaslogen/resaca/tree/uptade-coroutines-1.7.0>

Before: `38 tests  37  failures`
After: `38 tests  22  failures`, and none of them is a "method not found."
Is this success?

Not sure.

Let's try another one: <https://github.com/alexvanyo/composelife/pull/838>

I can't even build it.

```
  To build this project, accept the SDK license agreements and install the missing components using the Android Studio SDK Manager.
  All licenses can be accepted using the sdkmanager command line tool:
  sdkmanager.bat --licenses
  Or, to transfer the license agreements from one workstation to another, see https://developer.android.com/studio/intro/update.html#download-with-gradle
```

Thank you very much, but I'm using Linux, `sdkmanager.bat` won't work.
Will `~/Android/Sdk/tools/bin/sdkmanager --licenses` do what I want?

```
Exception in thread "main" java.lang.NoClassDefFoundError: javax/xml/bind/annotation/XmlSchema
	at com.android.repository.api.SchemaModule$SchemaModuleVersion.<init>(SchemaModule.java:156)
	at com.android.repository.api.SchemaModule.<init>(SchemaModule.java:75)
	at com.android.sdklib.repository.AndroidSdkHandler.<clinit>(AndroidSdkHandler.java:81)
	at com.android.sdklib.tool.sdkmanager.SdkManagerCli.main(SdkManagerCli.java:73)
	at com.android.sdklib.tool.sdkmanager.SdkManagerCli.main(SdkManagerCli.java:48)
Caused by: java.lang.ClassNotFoundException: javax.xml.bind.annotation.XmlSchema
	at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:641)
	at java.base/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:188)
	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:520)
	... 5 more
```

Nope. I guess the simplest solution is to launch Android Studio and click
something there. If that fails (and it often does, I encounter "this AGP
is incompatible with this Android Studio" more often than not when opening
random projects), the proper solution is probably to set some class path
somewhere.

By the way, did you know that, in the Java ecosystem, everything **just works**,
you simply download the JAR, run Java, and boom, production, enterprise,
financial industry just spring out from your screen in full 3D with fancy
shading? After you try it once, you can't settle for anything less!

Ok, while `composelife` is being indexed (or whatever), let's do some work.

So, the datetime formatting.

I was thinking a bit about how to properly implement space-padding. Seems fairly
simple, actually...

Oh.

> The project is using an incompatible version (AGP 8.0.0) of the Android Gradle plugin. Latest supported version is AGP 8.0.0-alpha02

Well, okay, I guess. Just as I expected. Hope it'll still allow me to click the
license thing. Except there's no prompt for me to click anything.
Let's hope that while Android Studio is open, the license gets accepted
automatically... it does! Now the tests in my console run successfully.

Well, "successfully" is stretching it, the tests do fail, after all.
7 tests out of 809 (wow, that's a shockingly large number!),
with `NoSuchMethodError`.

Now, let's switch to my locally-published build...

```
Configuration cache state could not be cached: field `left` of
`org.gradle.configurationcache.serialization.codecs.SubtractingFileCollectionSpec`
bean found in field `provider` of
`org.gradle.configurationcache.serialization.codecs.ProviderBackedFileCollectionSpec`
bean found in field `element` of `java.util.Collections$SingletonList` bean
found in field `elements` of
`org.gradle.configurationcache.serialization.codecs.ResolutionBackedFileCollectionSpec`
bean found in field `__classpathSnapshot__` of
`org.jetbrains.kotlin.gradle.tasks.KotlinCompile$ClasspathSnapshotProperties`
bean found in field `__classpathSnapshotProperties__` of task
`:dispatchers:kaptGenerateStubsDebugKotlinAndroid` of type
`org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask`: error writing
value of type
'org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection'
> Could not resolve all files for configuration ':dispatchers:debugCompileClasspath'.
   > Could not find org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.0-SNAPSHOT.
     Required by:
         project :dispatchers
   > Could not find org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-SNAPSHOT.
     Required by:
         project :dispatchers
```

Look at this word soup, just to say that I forgot to add `mavenLocal` to the
list of repositories. Strong C++ template error influences, I see; learning from
the best, as is proper.

Funnily enough, I was not one minute in the Android Studio
when I found some bug: clicking on links doesn't open my browser.

This project has 4 places where `mavenCentral` is listed, by the way, and
adding `mavenLocal` to 3/4 of them does nothing.

Finally, success.


Ok, so, wrote the changelog and proposed a release:
<https://github.com/Kotlin/kotlinx.coroutines/pull/3752>

As I was saying, **space padding**.

In most places I've looked, space-padding was implemented in a way that I don't
approve of (footnote: when writing this, I subvocalized "approve of" and felt
mightily weird writing "of" given that it sounds nothing like the combination of
these letters; the [ghoti](https://en.wikipedia.org/wiki/Ghoti) of English
strikes again). Namely, we have zero padding or space padding.

Java does this right:
> [Pad modifier `p`] modifies the pattern that immediately follows to be padded
> with spaces. The pad width is determined by the number of pattern letters.
> This is the same as calling DateTimeFormatterBuilder.padNext(int).

For example,
```kotlin
DateTimeFormatter.ofPattern("ppppMM");
```
would output today `  05`. Two-digit months, with four-digit padding before.

I don't like *the syntax* though, as it goes against the grain of "when
formatting, the letters in the pattern get replaced by the desired contents".

Like,
```
yyyy-MM-dd, HH:mm
2023-05-12, 12:01
```
See? One-to-one mapping, except when we have to escape some characters, but
then, what can you really do?

So, I think a better choice would be to space-pad to the number of *p* letters
**plus** the length of the directive being padded. For example,
```
yyyy pM/dd, pH:mm
2023  5/12, 12:01
```

Better yet, the syntax could be like in Go:
```
yyyy _M/dd, _H:mm
2023  5/12, 12:01
```
Space is too useful as a proper text literal to give it up for such an uncommon
need.

Now, how would one go about implementing this? The formatting part is clear: you
format the thing to the right, calculate its length, prepend spaces as needed.
Easy. Java decided to bury a trick there, though, by failing
**during formatting** if the length of the result exceeds the padding.
The docs <https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html#padNext(int)>
are a bit misleading here: it's said that the failure happens if "the pad
width is too small," which can be interpreted as, "if the field can sensibly be
bigger, this will be caught," whereas in fact, it means only the widths below 1.
Is padding to one space sensible?.. I don't think it ever is.

So, the way I think this should be done:
* If the value has a defined maximum width larger than the provided padding,
  fail on construction. For example... well, yeah, I can't think of any that
  we have right now. For example, "day of the year, padded to two characters
  with spaces" fits the bill, but we don't support days-of-year.
* If the value doesn't have a maximum defined width, don't fail even if the
  value overflows. Be nice. People don't want their log-formatting procedure to
  crash if the system is in an incorrect state.
  For example, if someone somehow obtained the year value of `15312` and we have
  `___+y` as the format (pad to 5 characters, including the sign),
  we'll just print `+15321`, oveflowing a bit.

So, no runtime failures (unless someone constructs formatters on the fly with
various parameters, but this just means they are on their own).
I think it's a worthy goal.

Regarding the parsing, I think the behavior is also clear: if the padding was
overflown by too large a thing, not a big deal. If it was underflown, fail.
Don't like it? Define your parser separately, without the padding directive.
You most likely already do. If you want to **allow** padding while parsing but
not mandate it, there's always the `(m| m)` pattern.

The question is, how does it fit into the paradigm of parsers as sequences of
commands. Preliminarily, I think it should be solved by paired commands of
"checkpoint" placed before the first directive, with the function of remembering
the current position and skipping any spaces, and "finish", where the "finish"
command queries the position of the earlier checkpoint and checks the resulting
width.

The question is, of course, what to do with things like
```kotlin
padWithSpacesTo(10) {
  padWithSpacesTo(5) {
    appendYear()
  }
  appendLiteral("-")
  appendMonth()
}
```
If we format something with this, we get `   2023-05`. 10 characters total,
5 characters for the year.

The proper way in this case, I believe, is to say screw this, this is not
allowed. I *am* prompted to say that this is nonsensical, but it's not:
consider
```kotlin
padWithSpacesTo(10) {
  padWithSpacesTo(5) {
    appendYear()
  }
  appendLiteral(" ")
  appendTimeZone()
}
```
So, we can have strings like `  2023 UTC`, but we also can have strings like
` 2023 Europe/Berlin`. Here, the padding for years actually has an effect of
its own.

Hey, wait, this seemingly unimportant detail exposes a problem with my initial
plan. If the checkpoints just skip the spaces, then we can get erroneous
results: `      05` would be considered acceptable for `_m`, for example.
Now what do we do?


Fought with IDEA for something like ten minutes to make it show me the bytecode
for `kotlinx-coroutines-test:1.6.4`. That's one of the main reasons I dislike
the "I" in the "IDE": when you want some specific functionality, you can't just
access it, you need to determine how it's "I"ntegrated with everything else.
Also, no idea which attempts to view the JAR failed because of me and which
because of bugs.


Sure enough, my gut feeling that two instances of fixing something shouldn't be
enough paid off, but this time before we published one more embarassing release.
<https://github.com/Kotlin/kotlinx.coroutines/pull/3753> This time, the code is
just the Kotlin version of what was implicitly compiled, there's very little
potential of something going wrong. Frankly, this last edit was not even
strictly needed, so there probably wouldn't be another embarassing release
anyway. I added rationale throughout the PRs so that the wide public doesn't
think we're some clueless buffoons. Not that I'm saying I'm not, especially when
it comes to Kotlin, but we do need some credibility in the public's eyes.

In any case, this is educational. Need to focus better.


In the meantime, I was asked about truncating `LocalDateTime` to the given
`ChronoUnit`. I think I should repost the answer here as well, so that it's not
lost deeply in Slack. Maybe I'll need it someday.

```kotlin
public fun LocalTime.truncateTo(unit: DateTimeUnit.TimeBased): LocalTime =
    LocalTime.fromNanosecondOfDay(toNanosecondOfDay().let { it - it % unit.nanoseconds })

public fun LocalDateTime.truncateTo(unit: DateTimeUnit.TimeBased): LocalDateTime =
    LocalDateTime(date, time.truncateTo(unit))

@Test
fun testTruncation() {
    val localTime = LocalTime(1, 2, 3, 456789123)
    assertEquals(LocalTime(1, 2, 3, 456789123), localTime.truncateTo(DateTimeUnit.NANOSECOND))
    assertEquals(LocalTime(1, 2, 3, 456789000), localTime.truncateTo(DateTimeUnit.MICROSECOND))
    assertEquals(LocalTime(1, 2, 3, 456000000), localTime.truncateTo(DateTimeUnit.MILLISECOND))
    assertEquals(LocalTime(1, 2, 3, 0), localTime.truncateTo(DateTimeUnit.SECOND))
    assertEquals(LocalTime(1, 2, 0, 0), localTime.truncateTo(DateTimeUnit.MINUTE))
    assertEquals(LocalTime(1, 0, 0, 0), localTime.truncateTo(DateTimeUnit.HOUR))
}
```

Finally published the release 1.7.1, and from my testing, it does properly
include the fix.

Here's code that checks whether the artifacts are at Maven already:
```sh
version=1.7.1; while curl https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-test/$version/ | grep -q '404 Not Found'; do sleep 1; done; notify-send "Hey, they published it"
```

2023-05-22
----------

With the datetime formatting being mostly feature-complete, all I have to do
with it now is lead a series of internal design discussions regarding it. This
is not a full-time job (thankfully, we don't have meetings all day), so I have
plenty of time now to work on other things.

Right now, I'm extending the Kotlin/Native implementations for time zones, and
my immediate goal right now is to rewrite the Linux portion of it.
I already tried my hand at rewriting the Windows implementation
(<https://github.com/Kotlin/kotlinx-datetime/commit/cb9b0a91c4a1f148e5fbb7a13bd13ae47733f0d8>),
but to no avail: unfortunately, in the Windows timezone database, the provided
data is a bit broken: the "daylight savings time" in some cases starts before
the standard time does, and this is just one assumption that was discovered by
our tests. I can't be bothered to work around this with elaborate `if-else`
chains.

Of course, this work needs to be done, and there's no one but me to do it, so
I will have to deal with this eventually. However, implementing Linux first will
make this significantly easier. In order to implement Linux, I'll have to
recreate the entire mechanism of how timezone rules are stored. Until now, we
always relied on someone else to be the containers for timezone rules (Darwin's
`NSTimeZone`, Windows's `DynamicTimeZoneInformation`, and the
<https://github.com/HowardHinnant/date> library's `tzdb` on Linux) and queried
them for the answers we needed, but this approach outlived itself: our needs are
no longer fulfilled by just the information these containers provide. Except,
funnily enough, on Linux. If the `date` library worked on iOS and Windows using
the system time zone, we wouldn't even need to do anything except wrap the
library and let it carry us to success.

So, we *could* have left the Linux version intact, but:
* We'll need to learn to parse IANA TZDB files anyway.
* We'll need the mechanism for arbitrary timezone rules anyway in order to be
  able to provide alternative timezone databases:
  <https://github.com/Kotlin/kotlinx-datetime/issues/201>.

... and that is basically all there is to the Linux implementation, actually.

When we have the ability to work with arbitrary rules, it will be much easier to
tame the Windows implementation: we'll only need to convert the obscure format
that Windows uses to our rules, check that everything works, and throw away the
Windows-specific timezone handling altogether, thus being free from the
`if-else` pollution.

For checking that everything works, maybe we could use the test suite for the
`Temporal.ZonedDateTime` type, which is essentially just the test suite for
datetime arithmetics as a whole in disguise:
<https://github.com/tc39/proposal-temporal/blob/503c1edea7c81e11dde033fc94ecbdcdc83d171d/polyfill/test/zoneddatetime.mjs>.
One of the authors
<https://github.com/Kotlin/kotlinx-datetime/issues/163#issuecomment-1181368707>
explicitly encouraged that we do it, and maybe we should.
<https://github.com/tc39/proposal-temporal/blob/503c1edea7c81e11dde033fc94ecbdcdc83d171d/LICENSE>
says, I think, that the code is essentially under the BSD license, which should
be compatible with our Apache 2.0.


To implement the Linux version, I first need to understand what timezone rules
are. Of course, essentially, each timezone is exactly a function
`Instant -> UtcOffset` (or, equivalently, `Instant -> LocalDateTime`), but the
specifics of how to encode it may be tricky. For one, there is the concept of
periods when specific rules of DST switch were in effect, like "at 02:00 on the
last Sunday of March." This is important because the last entry in the timezone
database may be "in 1980, this rule was established," and this rule can be used
for arbitrarily long. We can't just store a transition description for each
year, there could be arbitrarily many of them.

*Sure*, as a first approximation, we *could* do something like "generate and
store all transitions for years 1970-2100." No one will expect the information
about the year 2100 to be accurate, and there are literally no guarantees even
in the timezone database itself about the timezone data accuracy for years
before 1970. Maybe this is actually the way forward: have each timezone be a
list of something like 200 `Instant` + `UtcOffset` values for each transition
and just query them. 200 * (`Long` + `Int` + `Int`) is about 200 * 2 * 8 bytes,
or 3 kB. The whole TZDB would then be less than a megabyte. Even if we take
Java's inefficient memory layout into account, we still get something
reasonable, and the implementation of all operations is completely
straightforward.

I think I saw some suspicious arrays in 310bp's implementation.
Let's look at what they do at all.

The `TzdbZoneRulesProvider.java` file seems to be the entry point I'm interested
in. `org.threeten.bp.zone.TzdbZoneRulesProvider#loadData` is a function that
reads the IANA TZDB file hidden in the resources. Looks like it doesn't parse
the actual rules, it only reads the required number of bytes worth of rules
and stores them as a byte array. Then,
`org.threeten.bp.zone.TzdbZoneRulesProvider.Version#createRule` checks what's
stored: `ZoneRules` or a byte array. If a byte array is stored, `ZoneRules` is
obtained from it, and the byte array is replaced. Smart: this means avoiding the
expensive parsing phase until the data for a specific region is requested.
The way `ZoneRules` is obtained is by calling `org.threeten.bp.zone.Ser#read`,
which, in turn, checks the first byte to see the type of the byte data and calls
one of:
* `StandardZoneRules.readExternal`
* `ZoneOffsetTransition.readExternal`
* `ZoneOffsetTransitionRule.readExternal`

Wait, why? Only `StandardZoneRules` fits the bill here. This is very strange!
If some other branch is successfully taken, then not only will `createRule`
throw an exception, it will also overwrite the byte array with the
newly-deserialized object... though that's okay, now that I think about it:
it will throw the same exception every time anyway, being fully deterministic
and operating on immutable data.

Still, what we *want* is to skip a byte and call
`StandardZoneRules.readExternal`. I don't get it. Oh well, it doesn't matter.
What matters is, which format is this, anyway?

During the compilation, *something* did output
`./target/classes/org/threeten/bp/TZDB.dat` (not stored in git), and there is
`./src/main/resources/org/threeten/bp/TZDB.dat` (stored in git), and they
are the same.

`git grep -F TZDB.dat` reveals:
```
src/main/java/org/threeten/bp/zone/TzdbZoneRulesCompiler.java:        File tzdbFile = new File(dstDir, "TZDB.dat");
src/main/java/org/threeten/bp/zone/TzdbZoneRulesCompiler.java:            jos.putNextEntry(new ZipEntry("org/threeten/bp/TZDB.dat"));
src/main/java/org/threeten/bp/zone/TzdbZoneRulesProvider.java:            Enumeration<URL> en = classLoader.getResources("org/threeten/bp/TZDB.dat");
```

Hello, two classes that are named deviously similar and look like just one
class! (For those who are as clueless as me: yes, there are two files listed,
one is "Compiler" and the other is "Provider").

How is the "compiler" used? `README.md` sheds some light:

```markdown
#### Time-zone data
The time-zone database is stored as a pre-compiled dat file that is included in the built jar.
The version of the time-zone data used is stored within the dat file (near the start).
Updating the time-zone database involves using the `TzdbZoneRulesCompiler` class
and re-compiling the jar file.
An automated CI job should help keep the time-zone data up to date.
```

Ok, so: 310bp has some unnamed format for timezone databases and compiles
something to it. `.github/workflows/tzdbupdate.yml` explains what exactly
gets compiled: <https://github.com/eggert/tz.git>. At some point, we'll need to
recreate this process, most likely.


2023-05-30
----------

Configuring a virtual machine for MacOS to test my code on it. For this, I'm
using <https://github.com/quickemu-project/quickemu>: in theory, you just run
a couple of simple commands and obtain a virtual machine for the given OS.

Why in particular do I want to test something on MacOS right now: I need to
understand how the Foundation library chooses the calendar to format dates
with. For example, how `yyyy-MM-dd` looks when the user's calendar is Buddhist,
where years are on some unfamiliar scale:
<https://en.wikipedia.org/wiki/Buddhist_calendar#Epochal_date>.

In Java, the behavior is documented and sensible, defaulting to the ISO
chronology:

> hronology is determined. The chronology of the result is either the chronology
> that was parsed, or if no chronology was parsed, it is the chronology set on
> this class, or if that is null, it is `IsoChronology`.

I already installed the MacOS virtual machine once, but silly me, I forgot the
user password. I'm sure that, given the filesystem being fully accessible to me,
I could do something with this (like resetting the password by manual byte
manipulation), but screw it, that virtual machine was misconfigured anyway.
I decided to start anew.

Unfortunately, this isn't so simple. Not sure whom to blame. The instructions
that I followed (<https://github.com/quickemu-project/quickemu#macos-guest>,
already a bit too involved for my tastes) didn't work, as the virtual hard drive
that was created for this purpose wasn't the right size: just 2GB.

A benefit of using these involved systems over recreating them manually is that,
most likely, the problem is already documented. Whoop, it is:
<https://github.com/quickemu-project/quickemu/issues/578>.

> "Using "Apple Inc. VirtIO Bloc..." as the disk instead of "QEMU HARDDISK..."
> works just as well.
> That disk has ~103.08GB storage just like the doc mentioned."

Ok.

Boy do I hope that Swift will work on MacOS Catalina. I think there's a very
real risk that this OS is so outdated that everything will just fail.

While MacOS installs, I'll describe another line of inquiry that I'm making
right now. I've been working on porting the Linux implementation of
kotlinx-datetime to pure Kotlin in order to gain access to things like swapping
timezone databases (down the line) or `LocalDateTime.toInstant(someCustomRule)`
conversions (right now). Well, while it won't help with the "down the line"
thing, there may be a quick workaround for "right now": make every platform work
through the C++ API: <https://en.cppreference.com/w/cpp/chrono>
This API is the most powerful of all the platforms and is certainly mighty
enough to accommodate all our needs.

I gave it a try. All I did was took the `master` branch and replaced the code
that uses the `date` library with the C++ `<chrono>`, and also updated the
build scripts to access C++20, where the API was introduced.

I get this error:
```
Exception in thread "main" org.jetbrains.kotlin.konan.KonanExternalToolFailure: The /home/dmitry.khalanskiy/.konan/dependencies/llvm-11.1.0-linux-x64-essentials/bin/clang++ command returned non-zero exit code: 1.
output:
In file included from /home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/native/cinterop/cpp/cdate.cpp:11:
/home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/native/cinterop/public/helper_macros.hpp:14:17: error: use of undeclared identifier 'stderr'
        fprintf(stderr, "Insufficient memory available\n");
                ^
/home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/native/cinterop/cpp/cdate.cpp:15:44: error: unknown type name 'year'
static int64_t first_instant_of_year(const year& yr) {
                                           ^
/home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/native/cinterop/cpp/cdate.cpp:16:12: error: use of undeclared identifier 'sys_seconds'
    return sys_seconds{sys_days{yr/January/1}}.time_since_epoch().count();
           ^
/home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/native/cinterop/cpp/cdate.cpp:16:24: error: use of undeclared identifier 'sys_days'
    return sys_seconds{sys_days{yr/January/1}}.time_since_epoch().count();
                       ^
...
```

Strange. LLVM 11.1.0 is fairly modern, it should already know about C++20's
APIs, but this looks like none of the `<chrono>` is available. However, what I
got out of it was the name of the command that's executed:
`/home/dmitry.khalanskiy/.konan/dependencies/llvm-11.1.0-linux-x64-essentials/bin/clang++`.
I can just run it manually and see what happens:
```
$ /home/dmitry.khalanskiy/.konan/dependencies/llvm-11.1.0-linux-x64-essentials/bin/clang++ -std=c++20 cdate.cpp -I../public/
In file included from cdate.cpp:5:
../public/helper_macros.hpp:14:17: error: use of undeclared identifier 'stderr'
        fprintf(stderr, "Insufficient memory available\n");
                ^
cdate.cpp:46:14: error: unknown type name 'time_zone'
static const time_zone *zone_by_id(TZID id)
             ^
cdate.cpp:56:18: error: use of undeclared identifier 'get_tzdb'
    auto& tzdb = get_tzdb();
                 ^
cdate.cpp:64:30: error: unknown type name 'tzdb'
static TZID id_by_zone(const tzdb& db, const time_zone* tz)
                             ^
cdate.cpp:64:46: error: unknown type name 'time_zone'
static TZID id_by_zone(const tzdb& db, const time_zone* tz)
                                             ^
cdate.cpp:90:22: error: use of undeclared identifier 'get_tzdb'
        auto& tzdb = get_tzdb();
                     ^
cdate.cpp:103:22: error: use of undeclared identifier 'get_tzdb'
        auto& tzdb = get_tzdb();
                     ^
cdate.cpp:127:16: error: use of undeclared identifier 'INT_MAX'
        return INT_MAX;
               ^
cdate.cpp:134:22: error: use of undeclared identifier 'get_tzdb'
        auto& tzdb = get_tzdb();
                     ^
cdate.cpp:149:18: error: use of undeclared identifier 'local_info'
            case local_info::unique:
                 ^
cdate.cpp:152:18: error: use of undeclared identifier 'local_info'
            case local_info::nonexistent: {
                 ^
cdate.cpp:165:35: error: use of undeclared identifier 'INT_MAX'
                        *offset = INT_MAX;
                                  ^
cdate.cpp:169:18: error: use of undeclared identifier 'local_info'
            case local_info::ambiguous:
                 ^
cdate.cpp:175:27: error: use of undeclared identifier 'INT_MAX'
                *offset = INT_MAX;
                          ^
cdate.cpp:179:19: error: use of undeclared identifier 'INT_MAX'
        *offset = INT_MAX;
                  ^
cdate.cpp:195:19: error: use of undeclared identifier 'INT_MAX'
    if (offset == INT_MAX)
                  ^
cdate.cpp:196:16: error: use of undeclared identifier 'LONG_MAX'
        return LONG_MAX;
               ^
17 errors generated.
```
Oh, ok. Two things: first, `year` is clearly available now, as is a lot of other
things from `<chrono>`. What's not available is, surprisingly, some constants
and, unsurprisingly, the timezone-related code. Well, it seems that the timezone
database is not accessible from this.

I wonder if the problem is in the version, in the LLVM, or in the fact that it's
`llvm...essentials`. Maybe in the non-"essentials" version the timezone database
*is* included.

Let's try with my system `clang`:
```sh
nix-shell -p clang
clang++ -std=c++20 cdate.cpp -I../public/
```

Still the exact same 17 errors. So, even the non-"essentials" version doesn't
provide access to time zones. A pity.

Maybe I'm doing something wrong? `g++` also doesn't know about `get_tzdb` and
such...

... and neither does the Internet: <https://grep.app/search?q=get_tzdb%28%29>

> Showing 1 - 10 out of 43 results

even before I limit the search to just C++ and not the documentation.
With the limits <https://grep.app/search?q=get_tzdb%28%29&words=true&filter[lang][0]=C%2B%2B>,
it's just 29 results.

And here I was hoping that the compilers just hide it behind some macro
definition and I had to write some deeply-C thing like
`#define __CLANG_TZDB_ACCESS`. I think I'm out of luck here.

Launching the "MacOS installer" in the meantime...

... and installed successfully.

So, I'll also share my (not really first) impressions about MacOS.

I like it a lot that, like is commonly done in Linux, I can type the name of
a command into the command line and get a suggestion to install it. Right now,
I typed `swift` and was prompted to sign some license agreement (which I didn't
like) in a GUI window (which I also didn't like). I also suppose that I won't
be able to do the same thing with any arbitrary command.

Yep, `zsh: command not found: tmux`, etc. Of course, there's no package
repository with common packages, so `zsh` doesn't even know where to look.
Maybe it'll work once I install a package manager.

Oh, just in case I forget, the password to my virtual machine is `abcd`.
I hope that whichever hacker pulls off a highly-involved series of tricks to
gain access to my work machine will find it rewarding to have read my journal.
For the record, my actual user account password is much more involved.

2023-05-31
----------

A hot summer day. I've been preparing for the next internal design meeting about
datetime formatting API, so for now, I want to do something absent-minded.
For example, porting some tests from
<https://github.com/tc39/proposal-temporal/blob/503c1edea7c81e11dde033fc94ecbdcdc83d171d/polyfill/test/zoneddatetime.mjs#L1>.
I'll need to do that anyway before I merge my implementation of timezone rule
handling.

Looks like the only relevant section is
<https://github.com/tc39/proposal-temporal/blob/503c1edea7c81e11dde033fc94ecbdcdc83d171d/polyfill/test/zoneddatetime.mjs#L2173-L2430>.
The other things are mostly checks of the correctness of specific operations.
Let's see what's useful there...

> Samoa date line change (subtract): 11:00PM 31 Dec 2011 -> 10:00PM 29 Dec 2011

(`Pacific/Apia`) Wow, interesting. That's a huge jump to make!

Another thing that stood out:

```js
    it('year 0 leap day', () => {
      let zdt = ZonedDateTime.from('0000-02-29T00:00-00:01:15[Europe/London]');
      equal(`${zdt.toInstant()}`, '0000-02-29T00:01:15Z');
      zdt = ZonedDateTime.from('+000000-02-29T00:00-00:01:15[Europe/London]');
      equal(`${zdt.toInstant()}`, '0000-02-29T00:01:15Z');
    });
```

I wonder what it tests. So, we parse the same thing twice and both times check
that the `Instant` is the `LocalDateTime` minus the `UtcOffset`, even when
we're on the year 0 leap day... Ok, I don't get why it's a special case.

I think this is it? Didn't find any other interesting edge cases in the test
suite.

Funny: when I wrote

```kotlin
@Test
fun samoaDateJump() {
  val zone = TimeZone.of("Pa
}
```
Copilot suggested

```kotlin
@Test
fun samoaDateJump() {
  val zone = TimeZone.of("Pacific/Apia")
  val initialDateTime = LocalDateTime(2011, 12, 29, 23, 59, 59, 999999999)
}
```

Not quite accurate (I think), but just how knowledgeable is it if it knows even
such obscure dates?

Ok, the test that checks that there was a transition on `2011-12-29` at
`22:00` doesn't pass. This is treated as just a normal date. I guess I'll either
have to read the original test or figure out what is supposed to have happened
that day. I'll choose the latter as the more robust and less legally exhausting
approach.

So: <https://en.wikipedia.org/wiki/International_Date_Line#Samoan_Islands_and_Tokelau_(1892_and_2011)>

> In 2011, Samoa shifted back to the west side of the IDL by removing Friday,
> 30 December 2011 from its calendar.
> This changed the time zone from UTC−11:00 to UTC+13:00 (UTC-10 to UTC+14 Dst).

So, it looks like the jump actually happened on the midnight, and they jumped
24 hours.

This test of mine doesn't pass though for some reason:

```kotlin
    @Test
    fun samoaDateJump() {
        val zone = TimeZone.of("Pacific/Apia")
        // the LocalDateTime skipped from 2011-12-30T24:00 to 2011-12-31T23:00
        val initialDateTime = LocalDateTime(2011, 12, 30, 0, 0)
        val newYear = LocalDateTime(2012, 1, 1, 0, 0)
        val instantJustBeforeTransition = initialDateTime.toInstant(UtcOffset.ZERO)
            .minus(1, DateTimeUnit.NANOSECOND)
            .toLocalDateTime(UtcOffset.ZERO)
            .toInstant(zone)
        assertEquals(LocalDateTime(2011, 12, 31, 0, 0),
            instantJustBeforeTransition.plus(1, DateTimeUnit.NANOSECOND).toLocalDateTime(zone))
        assertEquals(newYear,
            instantJustBeforeTransition.plus(1, DateTimeUnit.DAY, zone).plus(1, DateTimeUnit.NANOSECOND)
                .toLocalDateTime(zone))
        assertEquals(instantJustBeforeTransition.toLocalDateTime(zone),
            newYear.toInstant(zone).minus(1, DateTimeUnit.NANOSECOND).minus(1, DateTimeUnit.DAY, zone)
                .toLocalDateTime(zone))
    }
```

Everything until the last assert is just fine, but the last assert fails with
> `AssertionError: Expected <2011-12-29T23:59:59.999999999>, actual <2011-12-31T23:59:59.999999999>.`

On all the platforms, including Java!

Let's simplify the issue.

```kotlin
assertEquals(LocalDateTime(2011, 12, 29, 23, 0).toInstant(TimeZone.of("Pacific/Apia")),
    LocalDateTime(2011, 12, 31, 23, 0)
        .toInstant(TimeZone.of("Pacific/Apia"))
        .minus(1, DateTimeUnit.DAY, TimeZone.of("Pacific/Apia")))
```

fails with
`expected:<2011-12-30T09:00:00Z> but was:<2011-12-31T09:00:00Z>`

Finally:

```kotlin
val fixedPoint = LocalDateTime(2011, 12, 31, 23, 0).toInstant(TimeZone.of("Pacific/Apia"))
assertEquals(fixedPoint, fixedPoint.minus(1, DateTimeUnit.DAY, TimeZone.of("Pacific/Apia")))
```

So, thanks to the folks from the Temporal JS proposal, at least one bug in our
implementation was uncovered, because this certainly does look like a bug.
I wonder if it's patched in the latest Java though...

Oh wait, Java deals with this via `ZonedDateTime`, which is supposed to be
shielded from this problem. I think?

Essentially, which code gets executed in our JVM version is
```kotlin
atZone(timeZone).plusDays(1).toInstant()
```

This *seems* benign.

```java
import java.time.*;

public class Check {
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Pacific/Apia");
        ZonedDateTime fixedPoint = LocalDateTime.of(2011, 12, 31, 23, 0).atZone(zone);
        ZonedDateTime result = fixedPoint.minusDays(1);
        System.out.println(fixedPoint);
        System.out.println(result);
    }
}
```

What fun! The output is

```
2011-12-31T23:00+14:00[Pacific/Apia]
2011-12-31T23:00+14:00[Pacific/Apia]
```

So, on Java, this problem is present even with `ZonedDateTime`.
I checked on Java 17, and it's still there.

I don't think we're up to fixing this at the moment. After all, nobody cares,
and rewriting the whole JVM and JS implementations not to use the
`ZonedDateTime`, in which the problem lies, means a lot of effort for dubious
gain, not to mention the bloating of the bytecode size on the JVM, a problem
on Android. I think having an issue with doing arithmetic operations on one day
in 2011 is a small price to pay. We could probably fix this on
Native, but then there would be a discrepancy in behavior. So, I think, just let
it be.


I wonder if <https://github.com/Kotlin/kotlinx-datetime/issues/277> can be
solved not only for `watchosDeviceArm64` but for all the native platforms.
Looks like the serialization library supports all of them now.

... oh, `androidNativeArm32` is not considered to be a Linux. Well, I guess that
makes sense, but then there's the question: would the Linux implementation work
there?
* Does the `date` library compile fine to ARM?
* Does the native Android have the timezone database in `/usr/share/zoneinfo`?
  I don't think that's likely!

So, we're yet to figure out how to provide `androidNativeArm32` support.
This target is for <https://developer.android.com/ndk>, maybe there are some
docs there.

I see this: <https://source.android.com/docs/core/permissions/timezone-rules#source-code>
But what does it mean? `bionic`? `libcore`? What's that?
Also, this seems to be the advice for the old Android versions, but where's the
information about how to access the TZDB in newer ones?

Looks like <https://lib.rs/crates/chrono> uses the "`android-tzdata`" crate.
If so, I could probably check out what `chrono` does to access the timezone
database. After all, it's Rust, surely it works with Android through the NDK,
how else would it?


Was on a design meeting regarding the `02_SimpleDateFormat.md` document. We did
not reach any consensus: half of the people were on one side, and another half
were on another.

The stumbling point was this: there's already the `SimpleDateFormat` format for
format strings (yep, that's a mouthful of formats). Half of the people thought
that, despite its deficiencies, the format is so widespread and familiar that
we would be foolish not to include it as the de-facto standard string format.
Half of the people thought that, despite it being so widespread, the format is
deficient and outdated enough that admitting it to our library would cause more
friction than the initial learning curve that comes with introducing a new
format.

2023-06-01
----------

Summer already.

We agreed to start a poll to maybe resolve the disagreement about the general
form of the string format API. I'll just write the initial text here and send it
to the other team members to clean it up so that biases are avoided. So, maybe
the readers of this blog can get an exclusive behind-the-scenes glimpse into how
this text looked before it was censored. Though I struggle to imagine a reader
who would be into such details.

@here Call to action!

When designing the format string API for **non-localized** date/time formats, we
decided on two possible forms:

* A format compatible with most common usages of Java's `DateTimeFormatter` and
  Swift's `DateFormatter`: `yyyy-MM-dd'T'HH:mm:ssxxx`. The localization
  functionality will be removed, as will some of the common warts (for example,
  `yyyy` vs `YYYY` vs `uuuu`).
* A brand new format that splits the format string into date, time, and UTC
  offset sections. The format above would look like
  `ld<yyyy-mm>'T'lt<hh:mm:ss>uo<+hh:mm>`, where `<` and `>` separate
  sections, and `ld`, `lt`, and `uo` are the section names, where `ld` means
  `L`ocal`D`ate, `lt` means `L`ocal`T`ime, and `uo` means `U`tc`O`ffset.
  This helps keep the letters in the format intuitive and not worry about
  `MM` vs `mm`.
  Also, a conversion function from the `DateTimeFormatter` format will be
  provided to simplify migration.

Both approaches have their benefits. Those familiar with Java's
`DateTimeFormatter` syntax may not want to migrate to and learn some new format,
while everyone else may be against remembering what `xxx` means and what is the
difference between `HH` and `hh`.

1: I strongly prefer to see formats like `yyyy-MM-dd'T'HH:mm:ssxxx` in my
   codebase.
2: I somewhat prefer formats like `yyyy-MM-dd'T'HH:mm:ssxxx`.
3: I somewhat prefer that the strings for date formats are rethought from the ground up.
4: I strongly want the strings for date formats to be rethought from the ground up.
5: What format strings? I just want a concise builder API for the formats.

We want as many votes as possible! Please also ask your friends and colleagues.
If they don't want to register in the Kotlin Slack, it's okay if you tell us
their vote in the comments.


Oh, well, looks like nobody wants to censor it. I fixed the grammar a bit and
reworded some things myself, but my colleagues don't seem to have any
objections. So, this is probably the version that will get posted today.


Let's dig into the implementation of time zone rules. Interesting
implementations are 310bp's and
<https://github.com/HowardHinnant/date/tree/master/src>.

First, let's see if anything relevant has changed in the `date` library in the
last three years (and so, should I upgrade it before reading through it and
getting rid of the dependency on it).

I see many changes to the string formats, and also some changes to the `ptz.h`
file. What is that?

```c
// This header allows Posix-style time zones as specified for TZ here:          
// http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap08.html#tag_08_03
```

```c
// Note, Posix-style time zones are not recommended for all of the reasons described here:
// https://stackoverflow.com/tags/timezone/info                                 
```

Oh, neat, a new resource I didn't know about. Well, anyway, this means that
`ptz.h` is irrelevant. I've encountered the POSIX timezones already, and yeah,
it seems like nobody is using them.

A new file, `include/date/solar_hijri.h`. No thanks. We should pick our battles.

Finally, the `tz.cpp` file. I see some new things being initialized that weren't
before, some new error checks for `nullptr`. Less special handling for Apple's
lack of leap second support (before, even the fields related to leap seconds
were not provided, but now they are just empty). There's now no special "tzdb
version" property just on Apple devices: now, on every device, `+VERSION` and
`version` files in the timezone database directory are checked for their version
info.

These are all minor things.

Now, I *could* have just checked out `master` and read the latest
implementation, but this going through the changes is also useful: this way, I
can be sure that the code I'm reading is stable, was not subject to drastic
changes recently, and won't have surprising bugs that just weren't ironed out
yet due to their young age. If there *were* recent changes, then not only would
I be more careful around these parts of the code, but I also would know that
what we had in 2020 was somehow deficient: otherwise, the changes wouldn't be
made. For example, if some new form of caching was added in 2022, I'd know for
sure that someone encountered a performance issue without the caching.
Luckily, there's nothing of this sort and I can relax and read the code.


Just received a report in Kotlin Slack:
```kotlin

    @Test
    fun nestedTimeouts() = runBlocking {
        val disp: CloseableCoroutineDispatcher = newSingleThreadContext("my-test")
        val result = withTimeout(30.seconds) {
            withContext(disp) {
                // Disabling the inner timeout will make it work correctly
                withTimeout(30.seconds) {
                    "Hello"
                }
            }
        }
        println(result) // Print "Hello"
        disp.close()
        println("Dispatcher closed: $disp") // We never reach here if the inner timeout is enabled
    }
```

It still reproduces: this will wait for half a minute before `close` succeeds.

This reminds me of <https://github.com/Kotlin/kotlinx.coroutines/pull/3441/>...
but no, the root of the issue is different. The handle is properly cancelled
this time. What's the problem then?

Oh, I see, it's the `schedule` implementation in `WorkerDispatcher`.
The disposable handler that gets created will not notify the `worker` that the
code no longer needs to be executed.

I think I fixed the issue: <https://github.com/Kotlin/kotlinx.coroutines/pull/3769>
Let's see what the CI thinks about it. For now, let's get back to the timezones.

`tzdb::current_zone` is a very important function: it collects various places
where the currently-used timezone could be defined.


2023-07-12
----------

My work was not that intellectually involved the last month and a half, and also
I had some non-work-related difficulties in real life, so I was completely not
in a mood to write anything.

Now, however, I reached an important realization that may be of historic
interest.

In our datetime formatting design meetings, we decided not to introduce a
general mechanism for space padding and instead to limit it to the fields where
it's actually occasionally used: month and day-of-month numbers, so, to
generalize just a little bit, unsigned fixed-width numbers.

While trying to write a clean, bug-free implementation of space padding for
these things, I managed to think of a general rule with which to implement space
padding in the general case.

The key observation is that typically, a parser can be forced to become
fixed-length (with a few caveats).
For example, when we want to parse a month number without a limited length, we
can instead represent it as "parse either two-digit or one-digit month".
If we want to space-pad a month number to, say, three digits, we can build
this parser:

```
One of:
* Three spaces, then nothing as the month number,
* Two spaces, then one-digit month number,
* One space, then two-digit month number,
* Three or more digits as a month number.
```

The first and the last branches are impossible to, so we can instead just
define the desired parser as ` ( m|mm)`, where `m` is *exactly* one digit long
and `mm` is *exactly* two digits long.

This can be generalized to the full case of parsing, which is, for the subset of
regular languages we're dealing with, just the recursive definition
```
Parser := Operations, followed by
          one of several parsers
```

We can prove that all parsers can return the range in which their lengths lie
and even split into different subparsers depending on their length, again, with
a few caveats.

* A basic operation can always be limited to fit at most a fixed length.
  This can be proved by enumerating all the basic operations we support.
  - A constant string. There's only a single way to parse it.
  - A number span. If there is a flexible-width field, the admissible lengths
    and parses are `[minWidth; maxWidth]`, otherwise the length is precisely
    defined.
  - A numeric sign. It's exactly one char.
  - A trie. We can take its sections, each of which corresponds to a specific
    length. I wonder if tries with fixed-length elements can be implemented more
    efficiently.
  - And that's it!
* A sequence of several operations has the combined length equal to the
  Carthesian product of the sets of lengths of individual operations.
  Therefore, a basic parser that's not followed by any subparser can be split
  into parsers of fixed length.
* By induction, let's assume that the tails of the parser can be split into
  fixed lengths. Then a parser followed by other parsers also can: we can treat
  the whole tail as a single operation whose lengths are the union of lengths
  of the alternatives.

What are the caveats? It's the fact that some fields have unbounded length.
For example, it's not inconcievable that we would allow parsing 10+ digit
fractional parts of a second. Naively employing this proof would lead us to
splitting `second(); char('.'); secondFraction(minLength = 1, maxLength = null)`
into an infinite number of parsers of different lengths.

However, there's only a single reason for us to do this, that is, to implement
padding to a specific length. Once this length is exceeded, we're no longer
interested in what happens. For example, to space-pad `1.1234...` to 11 chars,
we only need to consider the cases
```
 1.12345678
  1.1234567
   1.123456
    1.12345
     1.1234
      1.123
       1.12
        1.1
```

The rest of the cases are just
`second(); char('.'); secondFraction(minLength = 9)`, with no need to split or
space-pad anything.

It may seem to be a crazy expensive operation.
If we want to space-pad something to $n$ characters that uses $m$ operations,
how many branching subparsers we will create in the worst case?

```
For i := 1..n chars of space padding,
  for every assignment of chars to m operations that sums to (n - i),
    there is a single parser.
```

I forgot the name of the combinatoric primitive that explains the number of
tuples $(a_1, a_2, ..., a_m)$ with $0 \le a_j$ that sum to $i$. Something like
Stirling numbers, right?
Stirling numbers of the second kind $S(n, k)$ describe the number of ways to
assign the $n$'th digit to any of the $k$ operations. Not exactly what I want.
Let me try to derive the formula on my own and see what it is.

If there are $k$ characters to fill and $m$ operations, then the number of ways
to do that is $M(m, 0) = 1$,
$M(0, k + 1) = 0$, $M(m + 1, k + 1) = \sum_{i = 0}^{k + 1} M(m, i)$.
If there are no more characters to assign, there's only one way to do it: make
all the remaining operations zero-width.
If there are still characters but no more operations, that's sad, as we won't
be able to fulfill the obligation. If there are both characters and operations
yet, then the next operation can eat zero characters, all of them, or anything
in-between.

This actually looks like some classic dynamic programming task. Maybe there's
no sensible closed-formula solution.


2023-09-08
----------

Am I not a good enough IDE user, or am I not the IDE's target audience? Maybe
I should watch some YouTube videos about, like,
"top 10 tricks for IDE productivity" or something?

Let's consider the situation I'm in now. I have a *very* simple, painfully
mechanical task. There is a bunch of extension functions for `expect` classes,
and I want to move them inside the classes so that they are proper members,
doing some slight renaming in the process.

Example:

```kotlin
// common code

expect class UtcOffset {
    companion object {
    }

    object Format;
}

val UtcOffset.Format.ISO: DateTimeFormat<UtcOffset> = ISO_OFFSET

fun UtcOffset.Format.invoke(
    builder: UtcOffsetFormatBuilder.() -> Unit
): DateTimeFormat<UtcOffset> =
    UtcOffsetFormat.build(builder)

// jvm, js, native

actual class UtcOffset {
    companion object {
    }

    object Format;
}
```

must be translated to

```kotlin
// common code

expect class UtcOffset {
    companion object {
        fun Format(
            builder: UtcOffsetFormatBuilder.() -> Unit
        ): DateTimeFormat<UtcOffset>
    }

    object Formats {
        val UtcOffset.Format.ISO: DateTimeFormat<UtcOffset>
    }
}


// jvm, js, native

actual class UtcOffset {
    companion object {
        fun Format(
            builder: UtcOffsetFormatBuilder.() -> Unit
        ): DateTimeFormat<UtcOffset> = UtcOffsetFormat.build(builder)
    }

    object Formats {
        val UtcOffset.Format.ISO: DateTimeFormat<UtcOffset> = ISO_OFFSET
    }
}
```

What does the IDE allow me to do?

When I ask it for the actions I can perform on `invoke`, it proposes to convert
it to the block body. No mention of moving it to a non-extension method.

Googling "convert extension to member," I get this:
<https://www.jetbrains.com/help/rider/Refactorings__Convert_Extension_Method_to_Plain_Static.html>
It doesn't work, because it's a different IDE for a different language, even if
it is by the same company.

There is also an open issue:
<https://youtrack.jetbrains.com/issue/KTIJ-10351/Refactoring-Convert-to-instance-method-for-companion-object-top-level-functions>
I think this means I have to move `invoke` manually. Whoo, and this is simply
*moving* things, and I need to change it more significantly than that,
converting it from a call of the form `Format { }` where `Format` is an object
to the form `Format { }` where `Format` is a function name.

Ok, I did it manually:

```kotlin
// common code

expect class UtcOffset {
    companion object {
        fun Format(
            builder: UtcOffsetFormatBuilder.() -> Unit
        ): DateTimeFormat<UtcOffset> =
            UtcOffsetFormat.build(builder)
    }

    object Format;
}

val UtcOffset.Format.ISO: DateTimeFormat<UtcOffset> = ISO_OFFSET


// jvm, js, native

actual class UtcOffset {
    companion object {
    }

    object Format;
}
```

Now the IDE complains: "Expected declaration must not have a body." Yes, I know.
Please help me fix this by copy-pasting the body I provided to the actual
source-sets. This is a menial task, I'll be glad to let automation handle this.

Querying the IDE inspections, I get "Convert to block body" once again, but
also "Make internal." None of this is useful.

Googling "Expected declaration must not have a body" verbatim, I only get
discussions like <https://youtrack.jetbrains.com/issue/KT-42628> and
<https://youtrack.jetbrains.com/issue/KT-20427> and
<https://discuss.kotlinlang.org/t/why-it-is-not-possible-to-expect-only-parts-of-a-class-in-a-multiplatform-project/11237>,
but no mentions of letting the IDE help fix this.

So, is the IDE *at all* helping me with this? Yes, there is one place where it
does: after I change the common code, in the platform-specific code, I can ask
it to create `actual` stubs for me to fill.
Let's count how many things I have to do to employ this functionality.

* Open the platform-specific file.
* `<F2>` to jump to the next error.
* (One-time bonus step: oh wait, the whole file is red, re-sync Gradle)
* `<Alt+Shift+Enter>` to implement the missing members.
* (Bonus step: oh wait, the error message window disappeared due to me switching
  windows, and now `<Alt+Shift+Enter>` does something else entirely, drawing
  some arrows on top of my window with
  "Press Enter to open, Ctrl+Enter to duplicate" (I tried pressing both of these
  and didn't notice any effect, by the way); I should have pressed `<Alt+Enter>`
  instead, followed by `Enter`)
* `<F2>` again, to do the same for the companion object, then `<Alt+Enter>`,
  then `<Enter>`.
* Because the new members don't have the `public` visibility explicitly for
  some reason (a mistake on the IDE's part, as I did have these modifiers in the
  common code) and the library uses explicit API mode,
  also do `<F2>`, `<Alt+Enter>`, `<Enter>` several more times to add
  the `public` modifier.
* Scroll around the file and remove the documentation that was accidentally
  copied along with the declarations.

After that, I have to search for `TODO()`, filling the stubs. The IDE also helps
me fill the stubs by providing the autocompletion, where I consistently can find
the definitions I'm actually looking for.

Then, as I'm certainly *not* going to do this for every platform, I'm copying
the resulting piece

```kotlin
        fun Format(
            builder: UtcOffsetFormatBuilder.() -> Unit
        ): DateTimeFormat<UtcOffset> = UtcOffsetFormat.build(builder)
    }

    object Formats {
        val UtcOffset.Format.ISO: DateTimeFormat<UtcOffset> = ISO_OFFSET
    }
```

and pasting it to the other platform files.

You have to paste it just before the `}` of the companion object, not after!
It may seem like the result would be the same--I'd have to delete one curly
brace--but pasting this *after* seriously messes up the formatting of the file.

Then, I just remove
```kotlin
    }

    public actual object Format;
```

I'm sure that for someone out there, refactoring works. Otherwise, why would
anyone use IDEs when a plain text editor does the job in comparable time but is
much more predictable and reliable, with a UI that's not so laggy, without
constant crashes and freezes, with crisp fonts on Linux
(<https://youtrack.jetbrains.com/issue/IDEA-218458/Blurry-fonts-on-Wayland-fractional-scaling>)?

Am I just being too pessimistic, which affects my perception? What if the IDE
*does* help me a lot, but I'm so used to it that only notice the cases when it
fails to do so? Well, in a way, maybe: I do understand that without the IDE,
I wouldn't be able to navigate any code bases, because I'd have no idea which
function gets actually called among the overloads, conflicting names, etc., but
I think that's the result of the Kotlin style of
"hey, we have an IDE, don't worry about being able to navigate manually."
I don't think it takes much effort to make code navigable and readable without
an IDE, but one does have to keep it in mind. So, when your colleagues are using
an IDE and a language does not enforce readability, you're at a disadvantage
if you're not also using an IDE.

But is that really it? Is an IDE just like Apple's ecosystem, where you wake up
several years after buying an iPhone and notice that your whole electronic life
now revolves around Apple to the point where it's uncomfortable to use anything
else even when that something else is not actually worse in any way?

Tell you what: here's my new-work-year resolution. I'll try for a month to
notice (and record) every moment when the IDE helps me, and I'll also try to
actively research whether a thing I did manually could be done automatically
faster. Either I'll gain proficiency and obtain new appreciation for IDEA, or
I'll just drop it for good. In any case, this multi-year cycle of constant
irritation will stop. Maybe I'll start a separate journal in this repository
where I'll record how much the IDE assists me. I'll also record the cases where
it actively hindered me and a plain text editor would do a better job.

2023-09-21
----------

Finally took to reading
<https://schedule.cppnow.org/wp-content/uploads/2023/05/speeding-date.pdf>,
which I saw posted quite a while ago. It describes a way to speed up some
calendar-based computations. An implementation of these ideas is
<https://github.com/dotnet/runtime/pull/72712/files>. Maybe some of this can
be introduced to `kotlinx-datetime`.

The technique of optimizing divisions I see here reminds me of the book I went
through once: <https://www.xorpd.net/pages/xchg_rax/snip_00.html>. I'm surprised
compilers don't optimize things like `n / 5` to `3435973837 * b / 2^34` on their
own. Or maybe they do?.. Looking at <https://godbolt.org/>, I don't think they
do.

Without seeing the actual talk, I don't understand what role do the Euclidean
affine functions and their properties play here... Also, what are they even?
I can't find mentions of them... outside their paper?..

Hey, wait a minute! There's a paper! <https://arxiv.org/abs/2102.06959>
This will be much easier to read. It's peculiar that this warranted a
publication, I didn't think one could optimize some code and publish a paper
about it, but maybe they inroduce a novel approach there, I'm not sure.
Let's read it.

This is certainly worthy of a publication, it seems: they *are* introducing a
novel approach for how to automatically optimize a class of common forms of
computation, and the calendar calculations are simply there as an example of how
even highly irregularly behaving functions can be optimized in this framework.

I have a question to Definition 2.2. Is it actually well-defined? If a function
is a EAF, is its residual function unique?

First, let's check the obvious:
```
(a * r + b) / d  = (ka * r + kb) / (kd)
(5 * r + 3) / 7  = (10 * r + 6 ) / 14
(5 * r + 3) % 7 != (10 * r + 6 ) % 14
```

Let's check the first equality computationally to see that I didn't mess up
due to these being the tricky integral division:

```python
f1 = lambda r: (5 * r + 3) // 7
f2 = lambda r: (10 * r + 6) // 14
for i in range(-100, 100):
  print(f1(i), f2(i), f1(i) == f2(i))
```

First of all, nice to know that Python's `//` is algebraically nice:
`-1 // 7 == -1`. Didn't expect any sensibility out of their bunch. Second, yes,
functions behave equally.

The residual functions don't. For $r = 1$, we get 1 on the left and 2 on the
right. So, unfortunately, it's not "*the*" residual but "*a*" residual, because
different representations of a EAF lead to different residual functions.

To fix this, we have to throw away definition 2.1 and replace it with EAF
being *defined* by the three integral constants. Thus, functional extensionality
doesn't hold and we must compare EAFs as integer triplets.

Ok, I'll get back to this when I'm in the mood.


2023-11-20
----------

After a deep dive into datetime formatting, I'm finally satisfied. I've sent the
code to be reviewed: <https://github.com/Kotlin/kotlinx-datetime/pull/251>.
It marks one of the biggest undertakings I've done so far, and I've spent the
whole weekend celebrating that I'm finally done with it, modulo some
supplementary things like updating `README.md` with examples and publishing the
protocols of the design meetings. For now, I'd like to do something else for a
bit.

For example, I could go through the issues in the test module. Let's do that:
<https://github.com/Kotlin/kotlinx.coroutines/issues?q=is%3Aissue+is%3Aopen+label%3Atest>

Let's do <https://github.com/Kotlin/kotlinx.coroutines/issues/3800>, as it's
causing much woe and should be fixed in some shape or form before the next
release.

The resolution to the problem in the end
<https://github.com/Kotlin/kotlinx.coroutines/issues/3800#issuecomment-1816259708>
is to roll back the timeout to 60 seconds and leave a setting for those who want
to ensure that the tests complete quicker.

Easier said than done, though: on the JVM, the obvious choice would be to use
the system properties mechanism, but it's not available in multiplatform.
In fact, **nothing** is available on all platforms, because Kotlin also supports
JS tests running in web browser
<https://kotlinlang.org/docs/js-project-setup.html#test-task>, and I don't think
there's a way to pass anything that's not directly in the code to the
heavily-sandboxed browser environment.

The current policy seems to be that, if a JS is a show-stopper, we should
sacrifice it rather than walk back on the whole idea altogether. Luckily, here,
the choice is pretty inconsequential: even Node.JS supports reading the
environment variables, so only the browser tests seem to be affected.

So, let's get into the trenches. This time, I'll try to use my text editor
exclusively and see if I feel any discomfort at any point.

Here's the first attempt at documenting the behavior (always nice to start with
that):

```
There's a built-in timeout of 60 seconds for the test body. If the test body doesn't complete within this time, 
then the test fails with an [AssertionError]. The timeout can be changed for each test separately by setting the 
[timeout] parameter. Additionally, setting the `kotlinx.coroutines.test.default_timeout` environment variable to 
any string that can be parsed using [Duration.parse] (like `1m`, `30s` or `1500ms`) will change the default timeout 
to that value for all tests whose [timeout] is not set explicitly.
Note that environment variables and JVM system properties are separate concepts and are configured differently. 
```

I think using the `Duration` parsing facilities is a good choice:
`kotlinx.coroutines.test.default_timeout=15s` is more straightforward than
`kotlinx.coroutines.test.default_timeout_seconds=15`. It's not any more
flexible, as in practice, no one is going to set it to a sub-second value or
"1h"; in practice, this timeout is always going to be in seconds. But
readability would suffer if we didn't mention seconds in any way.
Speaking of readability, `kotlinx.coroutines.test.default_timeout=Infinity`
reads much better than something like
`kotlinx.coroutines.test.default_timeout_seconds=99999999`.

Something irks me: what should happen when parsing fails?

* If we silently ignore it, then we don't have a way to help someone who
  unintentionally set it to the wrong value, like `15 s`:
  <https://play.kotlinlang.org/#eyJ2ZXJzaW9uIjoiMS44LjIxIiwicGxhdGZvcm0iOiJqYXZhIiwiYXJncyI6IiIsIm5vbmVNYXJrZXJzIjp0cnVlLCJ0aGVtZSI6ImlkZWEiLCJjb2RlIjoiaW1wb3J0IGtvdGxpbi50aW1lLipcblxuZnVuIG1haW4oKSB7XG4gICAgcHJpbnRsbihEdXJhdGlvbi5wYXJzZShcIjE1IHNcIikpXG59In0=>
  Sure, our docs specify that the value should parse to a valid `Duration`
  value, *but* it's still rurprising to set your value to something and not have
  it at all reflected in the execution process.
* If we fail on unsuccessfully parsing it, could this open some doors to
  misuse by malicious parties, like DDoS-ing a service?.. No, I doubt it.
  If the attacker can set the system variable to anything they want, they can
  ensure that most tests fail by setting the timeout to `1ms`. Also, what kind
  of a service would run with `kotlinx-coroutines-test` exposed?

Ok, that solves it: we should throw an exception. But when? It's also a tricky
question!

The most natural way to implement this is to store a variable like
`private val DEFAULT_TIMEOUT: Duration` and try to initialize it using the
environment variables. But let's say we're running several tests--what will
happen then? The first attempt to call `runTest` will lead to the file
containing `runTest` being initialized and the failure occurring, and that test
will be marked as failed, *but* I'm pretty sure the following calls won't think
that something's wrong... Let's check this.

```kotlin
package reproduce3800helper

val globla = run {
    var i = 0
    repeat(100) { ++i }
    if (i != 0) {
        throw IllegalStateException("can not be set")
    }
    1000
}
```

```kotlin
package reproduce3800

import kotlin.time.*

fun main() {
    println("running the main function")
    try {
        println("trying to access globla")
        println(reproduce3800helper.globla)
    } catch (e: Throwable) {
        println(e)
    }
    println("trying to access globla again")
    println(reproduce3800helper.globla)
}
```

Here's the first time I felt the abscence of the IDE: I've no idea how to run
`main` without the green triangle on the left.
<https://stackoverflow.com/questions/21358466/gradle-to-execute-java-class-without-modifying-build-gradle>
helped, and now I can do

```sh
./gradlew -PmainClass=reproduce3800.Reproduce3800Kt run
```

from the command line.

The result:

```
running the main function
trying to access globla
java.lang.ExceptionInInitializerError
trying to access globla again
Exception in thread "main" java.lang.NoClassDefFoundError: Could not initialize class reproduce3800helper.Reproduce3800HelperKt
        at reproduce3800.Reproduce3800Kt.main(Reproduce3800.kt:14)
        at reproduce3800.Reproduce3800Kt.main(Reproduce3800.kt)
```

So, everything is somewhat fine after all: we can simply set the variable, fail
to do so, and still have all the following tests fail. I still don't like this:
if a thousand tests fail, you'll have trouble finding the one that failed with
`ExceptionInInitializerError` that explains what went wrong. Running any test
manually will reveal the problem, but I think we can do better with a tiny bit
of effort. So, let's have `DEFAULT_TIMEOUT` fail with a clear message on every
access.

I implemented it, but it seems like my implementation doesn't work for some
reason.

```sh
$ env kotlinx.coroutines.test.default_timeout=1blah env | grep kotlinx.coroutines
kotlinx.coroutines.test.default_timeout=1blah
$ env kotlinx.coroutines.test.default_timeout=1blah ./gradlew :kotlinx-coroutines-test:clean :kotlinx-coroutines-test:jvmTest
```

reports
`System.getenv("kotlinx.coroutines.test.default_timeout") == null`
using this code on the JVM:

```kotlin
"System.getenv(\"$name\") == ${System.getenv(name)}"
```

... Don't tell me Gradle doesn't update the environment variables and just
reuses the environment its daemons were launched in?..

```sh
$ while pkill java; do sleep 1; done
$ env kotlinx.coroutines.test.default_timeout=1blah ./gradlew :kotlinx-coroutines-test:clean :kotlinx-coroutines-test:jvmTest
```

Nope, still `null`. Let's print all the environment variables then.

Here's a small promotion of the text editor I'm using (`kakoune`): given this
text

```sh
[(PATH, /home/dmitry.khalanskiy/.local/share/JDK/11/bin:/home/dmitry.khalanskiy/.local/share/cargo/bin:/home/dmitry.khalanskiy/.local/bin:/home/dmitry.khalanskiy/.nix-profile/bin:/nix/var/nix/profiles/default/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games:/usr/local/games:/snap/bin:/home/dmitry.khalanskiy/.opam/default/bin), (LC_MEASUREMENT, ru_RU.UTF-8), (LC_TELEPHONE, ru_RU.UTF-8), (GDMSESSION, sway), (LC_TIME, ru_RU.UTF-8), (JDK_17_0, /usr/lib/jvm/java-17-openjdk-amd64), (DBUS_SESSION_BUS_ADDRESS, unix:path=/run/user/1001/bus), (COLORTERM, truecolor), (LC_PAPER, ru_RU.UTF-8), (USERNAME, dmitry.khalanskiy), (LOGNAME, dmitry.khalanskiy), (PWD, /home/dmitry.khalanskiy/IdeaProjects/kotlinx.coroutines), (TERM_PROGRAM_VERSION, 1.14.0), (CARGO_HOME, /home/dmitry.khalanskiy/.local/share/cargo), (LOCALE_ARCHIVE_2_27, /nix/store/x7zrg5s4c2nm3x0hhnq71jh3y8iaamf6-glibc-locales-2.37-45/lib/locale/locale-archive), (SHELL, /bin/bash), (LC_ADDRESS, ru_RU.UTF-8), (PAGER, less -iR), (TERMINFO, /nix/store/g2ksis13a2i4adkh2fx1q23x7i2j2q9c-foot-1.14.0-terminfo/share/terminfo), (GOPATH, /home/dmitry.khalanskiy/.local/share/go), (OLDPWD, /home/dmitry.khalanskiy/IdeaProjects/kotlinx.coroutines), (VISUAL, kak), (GTK_MODULES, gail:atk-bridge), (_JAVA_OPTIONS, -Djava.util.prefs.userRoot=/home/dmitry.khalanskiy/.config/java), (LEDGER_FILE, /home/dmitry.khalanskiy/.local/share/hledger.journal), (SYSTEMD_EXEC_PID, 2376), (XDG_SESSION_DESKTOP, sway), (SHLVL, 1), (LC_IDENTIFICATION, ru_RU.UTF-8), (TERMINAL, footclient), (LC_MONETARY, ru_RU.UTF-8), (BROWSER, firefox), (JAVA_HOME, /home/dmitry.khalanskiy/.local/share/JDK/11), (JDK_16_0, /home/dmitry.khalanskiy/.local/share/JDK/16), (I3SOCK, /run/user/1001/sway-ipc.1001.2424.sock), (TERM, foot), (LANG, en_US.UTF-8), (XDG_SESSION_ID, 2), (XDG_SESSION_TYPE, wayland), (NIX_REMOTE, daemon), (DISPLAY, :0), (GRADLE_USER_HOME, /home/dmitry.khalanskiy/.local/share/gradle), (WAYLAND_DISPLAY, wayland-1), (STACK_ROOT, /home/dmitry.khalanskiy/.local/share/stack), (SWAYSOCK, /run/user/1001/sway-ipc.1001.2424.sock), (LC_NAME, ru_RU.UTF-8), (XDG_SESSION_CLASS, user), (_, /usr/bin/env), (XCURSOR_SIZE, 24), (TERM_PROGRAM, foot), (JDK_11, /home/dmitry.khalanskiy/.local/share/JDK/11), (NIX_PATH, nixpkgs=/nix/var/nix/profiles/per-user/dmitry.khalanskiy/channels/nixpkgs:/nix/var/nix/profiles/per-user/dmitry.khalanskiy/channels:/home/dmitry.khalanskiy/.nix-defexpr/channels), (DESKTOP_SESSION, sway), (USER, dmitry.khalanskiy), (QT_ACCESSIBILITY, 1), (LC_NUMERIC, ru_RU.UTF-8), (XDG_SEAT, seat0), (EDITOR, kak), (JDK_8, /home/dmitry.khalanskiy/.local/share/JDK/8), (JDK_16, /home/dmitry.khalanskiy/.local/share/JDK/1.6), (XDG_VTNR, 3), (XDG_RUNTIME_DIR, /run/user/1001), (JDK_17, /home/dmitry.khalanskiy/.local/share/JDK/1.7), (JDK_18, /home/dmitry.khalanskiy/.local/share/JDK/8), (HOME, /home/dmitry.khalanskiy)]
```

I can select the line with `x`, press `s` to start selecting a subpart,
enter `\),` to choose the separators between entries, press `Enter` to confirm,
then press `a` to append some new text, and press the newline, and finally,
`Esc` to exit the insertion mode to get this:


```sh
[(PATH, /home/dmitry.khalanskiy/.local/share/JDK/11/bin:/home/dmitry.khalanskiy/.local/share/cargo/bin:/home/dmitry.khalanskiy/.local/bin:/home/dmitry.khalanskiy/.nix-profile/bin:/nix/var/nix/profiles/default/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games:/usr/local/games:/snap/bin:/home/dmitry.khalanskiy/.opam/default/bin),
(LC_MEASUREMENT, ru_RU.UTF-8),
(LC_TELEPHONE, ru_RU.UTF-8),
(GDMSESSION, sway),
(LC_TIME, ru_RU.UTF-8),
(JDK_17_0, /usr/lib/jvm/java-17-openjdk-amd64),
(DBUS_SESSION_BUS_ADDRESS, unix:path=/run/user/1001/bus),
(COLORTERM, truecolor),
(LC_PAPER, ru_RU.UTF-8),
(USERNAME, dmitry.khalanskiy),
(LOGNAME, dmitry.khalanskiy),
(PWD, /home/dmitry.khalanskiy/IdeaProjects/kotlinx.coroutines),
(TERM_PROGRAM_VERSION, 1.14.0),
(CARGO_HOME, /home/dmitry.khalanskiy/.local/share/cargo),
(LOCALE_ARCHIVE_2_27, /nix/store/x7zrg5s4c2nm3x0hhnq71jh3y8iaamf6-glibc-locales-2.37-45/lib/locale/locale-archive),
(SHELL, /bin/bash),
(LC_ADDRESS, ru_RU.UTF-8),
(PAGER, less -iR),
(TERMINFO, /nix/store/g2ksis13a2i4adkh2fx1q23x7i2j2q9c-foot-1.14.0-terminfo/share/terminfo),
(GOPATH, /home/dmitry.khalanskiy/.local/share/go),
(OLDPWD, /home/dmitry.khalanskiy/IdeaProjects/kotlinx.coroutines),
(VISUAL, kak),
(GTK_MODULES, gail:atk-bridge),
(_JAVA_OPTIONS, -Djava.util.prefs.userRoot=/home/dmitry.khalanskiy/.config/java),
(LEDGER_FILE, /home/dmitry.khalanskiy/.local/share/hledger.journal),
(SYSTEMD_EXEC_PID, 2376),
(XDG_SESSION_DESKTOP, sway),
(SHLVL, 1),
(LC_IDENTIFICATION, ru_RU.UTF-8),
(TERMINAL, footclient),
(LC_MONETARY, ru_RU.UTF-8),
(BROWSER, firefox),
(JAVA_HOME, /home/dmitry.khalanskiy/.local/share/JDK/11),
(JDK_16_0, /home/dmitry.khalanskiy/.local/share/JDK/16),
(I3SOCK, /run/user/1001/sway-ipc.1001.2424.sock),
(TERM, foot),
(LANG, en_US.UTF-8),
(XDG_SESSION_ID, 2),
(XDG_SESSION_TYPE, wayland),
(NIX_REMOTE, daemon),
(DISPLAY, :0),
(GRADLE_USER_HOME, /home/dmitry.khalanskiy/.local/share/gradle),
(WAYLAND_DISPLAY, wayland-1),
(STACK_ROOT, /home/dmitry.khalanskiy/.local/share/stack),
(SWAYSOCK, /run/user/1001/sway-ipc.1001.2424.sock),
(LC_NAME, ru_RU.UTF-8),
(XDG_SESSION_CLASS, user),
(_, /usr/bin/env),
(XCURSOR_SIZE, 24),
(TERM_PROGRAM, foot),
(JDK_11, /home/dmitry.khalanskiy/.local/share/JDK/11),
(NIX_PATH, nixpkgs=/nix/var/nix/profiles/per-user/dmitry.khalanskiy/channels/nixpkgs:/nix/var/nix/profiles/per-user/dmitry.khalanskiy/channels:/home/dmitry.khalanskiy/.nix-defexpr/channels),
(DESKTOP_SESSION, sway),
(USER, dmitry.khalanskiy),
(QT_ACCESSIBILITY, 1),
(LC_NUMERIC, ru_RU.UTF-8),
(XDG_SEAT, seat0),
(EDITOR, kak),
(JDK_8, /home/dmitry.khalanskiy/.local/share/JDK/8),
(JDK_16, /home/dmitry.khalanskiy/.local/share/JDK/1.6),
(XDG_VTNR, 3),
(XDG_RUNTIME_DIR, /run/user/1001),
(JDK_17, /home/dmitry.khalanskiy/.local/share/JDK/1.7),
(JDK_18, /home/dmitry.khalanskiy/.local/share/JDK/8),
(HOME, /home/dmitry.khalanskiy)]
```

Hope I'm not exposing any secrets. At a glance, I think I'm not, but you never
know what people with too much free time can find useful.

`_` being `/usr/bin/env` is strange: does Java call `/usr/bin/env` to get the
environment variables? In any case, it seems like all of my variables except
the newly-set one are there.

Ah, well. I'll have a meeting with my more experienced colleagues soon, maybe
they know what's going on.

Meanwhile: <https://github.com/Kotlin/kotlinx.coroutines/issues/3820#issuecomment-1818846416>
What even is this? Are our issue trackers just places to hang out at now?
I'd say that reading at least the last few messages before replying is not even
*politeness* but the basic social norm of online behavior. What am I even
supposed to do with people who just come and spam the issue tracker as if they
are entitled to personal answers from the maintainers? Should we also come to
their repositories to fix bugs? How about we do some actual work instead?

One could say that I'm overreacting to just a couple of messages, but in the
end, reading these messages and reacting to them do constitute a significant
chunk of my time at work. Maybe if I didn't care what to reply and only thought
about my answers for as long as the posters thought about their questions, it
wouldn't take too long, but given how much I have to answer, I'd quickly bury
the issue tracker under a thick layer of spam.

Coming back to the environment variables: I didn't get the chance to ask this
question, there were more important things on the agenda, so for now, I'll try
making sense of this myself. Luckily, it seems like I've found the answer:
<https://stackoverflow.com/a/59041173>. Somehow, Gradle filters the environment
variables. Interestingly, it knows about `TERM_PROGRAM=foot` and various such
things that are not in my dotfiles, so it's not just sourcing them. Where does
it get the resulting environment from? At this point, this is just curiosity,
not an actual technical question I have to solve: after all, I can just appease
Gradle by giving it the exact incantation it's interested in.

I'll finish writing the JS implementation and then add an integration test.
On Node, accessing the environment variable seems simple enough:

```sh
$ env kotlinx.coroutines.test.default_timeout=1s \
node -e 'console.log(process.env["kotlinx.coroutines.test.default_timeout"])'
1s
```

The bigger question is, how to properly access it from Kotlin/JS.

`kotlinx-coroutines-core/js/src/CoroutineContext.kt` does the following:

```kotlin
private external val navigator: dynamic
private const val UNDEFINED = "undefined"
internal external val process: dynamic

internal fun createDefaultDispatcher(): CoroutineDispatcher = when {
    // Check if we are running under jsdom. WindowDispatcher doesn't work under jsdom because it accesses MessageEvent#source.
    // It is not implemented in jsdom, see https://github.com/jsdom/jsdom/blob/master/Changelog.md
    // "It's missing a few semantics, especially around origins, as well as MessageEvent source."
    isJsdom() -> NodeDispatcher
    // Check if we are in the browser and must use window.postMessage to avoid setTimeout throttling
    jsTypeOf(window) != UNDEFINED && window.asDynamic() != null && jsTypeOf(window.asDynamic().addEventListener) != UNDEFINED ->
        window.asCoroutineDispatcher()
    // If process is undefined (e.g. in NativeScript, #1404), use SetTimeout-based dispatcher
    jsTypeOf(process) == UNDEFINED || jsTypeOf(process.nextTick) == UNDEFINED -> SetTimeoutDispatcher
    // Fallback to NodeDispatcher when browser environment is not detected
    else -> NodeDispatcher
}
```

`internal external` is a nice combination in and of itself.

It looks like that, in Kotlin/JS, that JS line becomes something like

```kotlin
internal external val process: dynamic

internal fun environmentVariableImpl(name: String): String? {
    if (jsTypeOf(process) == "undefined") return null
    if (jsTypeOf(process.env) == "undefined") return null
    return process.env[name] as? String
}
```

The nice thing about being single-threaded is that you don't have to think about
parallel interactions. If `process.env` is not `"undefined"` on line A and you
didn't do anything with it, it won't be `"undefined"` on line B. Such a
refreshing experience!

Ok, let's try setting the environment variable.

```kotlin
tasks.withType(Test::class).all {
    environment("kotlinx.coroutines.test.default_timeout", "1ms")
}
```

This may seem like it will work, but for some reason, `Test::class` only matches
`jvmTest`. This is where I'm launching the IDE today: without it, I don't know
how to browse the class hierarchy of Gradle tasks.

Googling this problem, I get <https://stackoverflow.com/a/65375061>, which is
what I have, but inaccurate.

Using the IDE, I see that `Task` is inherited from `AbstractTestTask`.
Sure enough, `AbstractTestTask` matches everything. The problem is,
`AbstractTestTask` doesn't have the `environment` call; something called
`ProcessForkOptions` does. Unfortunately, only `jvmTest` matches both
`AbstractTestTask` and `ProcessForkOptions`. There's simply no functionality
in the Kotlin Gradle plugin to set the environment variables of all tests, it
would seem.

Alright, I'm asking my colleagues, I'm clearly out of my depth here. It's not
as easy to pass an environment variable to a process as one would think!

Made a PR with what I already have:
<https://github.com/Kotlin/kotlinx.coroutines/pull/3945>

So there's that...

It would be nice to strive to solve an issue per day, but today, I failed to
do that. I only have 15 minutes of work left, but let's make that count.
Let's try this one: <https://github.com/Kotlin/kotlinx.coroutines/issues/3944>

```sh
rm -r ~/.m2
./gradlew :kotlinx-coroutines-debug:clean :kotlinx-coroutines-debug:publishToMavenLocal
jar --describe-module --file ~/.m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-debug/1.7.2-SNAPSHOT/kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar --release 9
```

does confirm the issue.

The `kotlinx-coroutines-debug` jar is special in that it's not produced by the
normal build mechanism, but instead it's made using the "shadow jar" plugin that
allows us to put another library's classes alongside ours.

Googling "shadowjar module-info" suggests
<https://github.com/johnrengelman/shadow/issues/352>: a `module-info.java` from
the shaded library leaks into the main library. Yes, it sounds like what we are
dealing with here!

Unfortunately, this fix seems to have been there since shadow 4.0.0:
<https://github.com/johnrengelman/shadow/commit/275382cef77df1cdce2b2b53568541933fd5d9cd>
And we are using 7.1.2. Likely, that's not it. It also looks like they did
nothing with the Java modules since then:
<https://github.com/johnrengelman/shadow/releases?q=module&expanded=true>
I don't see a point in upgrading to 8.1.1, which is the latest now. This would
mean also upgrading to Gradle 8.0, and it's probably not worth it.

Manually calling `exclude("module-info.java")` also seems to do nothing.
Let's dive in deeper... but not today. I'm done, without having helped anyone.

2023-11-21
----------

Continuing with <https://github.com/Kotlin/kotlinx.coroutines/issues/3944>.

Looking into the jar file, I found one of the culprits:

```
META-INF/versions/9/module-info.class
```

It turns out, the shadow plugin didn't properly exclude all the module-info
files that can be picked up by the JVM, only one of them. If I manually exclude
another one via `exclude('META-INF/versions/9/module-info.class')`, I get closer
to the desired result:

```
No module descriptor found. Derived automatic module.

kotlinx.coroutines.debug@1.7.2-SNAPSHOT automatic
requires java.base mandated
provides reactor.blockhound.integration.BlockHoundIntegration with kotlinx.coroutines.debug.CoroutinesBlockHoundIntegration
contains kotlinx.coroutines.debug
contains kotlinx.coroutines.debug.internal
contains kotlinx.coroutines.debug.junit4
contains kotlinx.coroutines.debug.junit5
contains kotlinx.coroutines.repackaged.net.bytebuddy
...
```

At least we're not claiming to provide bytebuddy. Still, the explicit
module-info that we define is something else, and somehow it doesn't get picked
up.

Looking at

```sh
find kotlinx-coroutines-debug/ | grep class'$'
```

after building the jar, I see that `module-info.class` does get built:

```
kotlinx-coroutines-debug/build/classes/kotlin/java9/module-info.class
```

Clearly, someone has already encountered the problem of needing to include
`module-info.class`. Googling "shadowjar include module-info," I get
<https://github.com/johnrengelman/shadow/issues/710>. Wait a second...

> ShadowJar extends from Gradle's built in Jar task [...]
> which is what provides all the include/exclude capabilities.
> None of that code is aware to the source of the files that are being copied so
> it doesn't appear possible to use the native capabilities to exclude
> module-info.class from dependencies, but include it from the local source set
> output.

So, our `module-info.class` could get excluded by accident! But where *is* it
supposed to be located?

```sh
$ unzip -l ~/.m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.2-SNAPSHOT/kotlinx-coroutines-core-jvm-1.7.2-SNAPSHOT.jar | grep module-info
      882  1980-02-01 00:00   META-INF/versions/9/module-info.class
```

Oh. Nice. So, I *should not* exclude `META-INF/versions/9/module-info.class`.
Instead, I must somehow make sure that *the correct version* of it gets
included. Now I'll have to find a way to specify that some files should not
be overwritten while shading.

Of course, there's an issue about that as well:
<https://github.com/johnrengelman/shadow/issues/304>
The recommended solution for *that specific issue* is
<https://stackoverflow.com/questions/32887966/shadow-plugin-gradle-what-does-mergeservicefiles-do>,
but what I'm encountering is something else.

Truly, the days when I have to remember the word "gradle" are the least pleasant
workdays. If this were my own project, I'd throw away Gradle completely and
replace it with several `Makefile`s. The amount of effort I'm spending to make
Gradle do fairly trivial things just because it uses the OOP-style top-down
approach rather than the composable bottom-up ruleset and I have to somehow jam
my requirements down its throat is just miserable. I've read a book that
explained just how perfect Gradle is compared to Maven, so I can see what they
were trying to achieve, but if we just look at the end result, Gradle seems to
waste more time than it saves compared to a custom set of rules on top of
something like Ninja. Just look at the wording:

> None of that code is aware to the source of the files that are being copied so
> it doesn't appear possible

This just screams that Gradle is the wrong abstraction. When you *have* to peek
into an abstraction, it's bad already, but when you *can't*, it's much worse.

Ok, well, this ranting is useless: clearly, I will have to live with this, no
matter how unpleasant it is.

I fire up my IDE once again to look into how `mergeServiceFiles` is implemented
to see if it's straightforward to just tell the thing to leave my files alone.
Why isn't this even the default? Why do files in the shaded library take
precedence? I can't figure out what benefits that could bring.

The whole `ShadowJar.java` (<https://github.com/johnrengelman/shadow/blob/main/src/main/groovy/com/github/jengelman/gradle/plugins/shadow/tasks/ShadowJar.java>),
along with `Jar` that it inherits from doesn't seem to be all that helpful.
`mergeServiceFiles` is implemented using a general `transform` mechanism, which
is described here:
<https://maven.apache.org/plugins/maven-shade-plugin/examples/resource-transformers.html>

`DontInccludeResourceTransformer` is possibly what I'm looking for. I'm not sure
if it means "don't include the shaded resources" or "don't include the resources
at all," so let's just try it!

If I can.

```groovy
def shadowJarTask = shadowJar {
    classifier null
    // Shadow only byte buddy, do not package kotlin stdlib
    configurations = [project.configurations.shadowDeps]
    relocate('net.bytebuddy', 'kotlinx.coroutines.repackaged.net.bytebuddy')
    try {
        transform(DontIncludeResourceTransformer.class, new Action<DontIncludeResourceTransformer>() {
            @Override
            void execute(DontIncludeResourceTransformer dontIncludeResourceTransformer) {
                dontIncludeResourceTransformer.resource("module-info.class")
            }
        });
    } catch (IllegalAccessException e) {
    } catch (InstantiationException e) {
    }

    manifest {
        attributes "Premain-Class": "kotlinx.coroutines.debug.AgentPremain"
        attributes "Can-Redefine-Classes": "true"
    }
}
```

fails with

> `No signature of method: build_4u6g4d26qfndzikzbvofa8lrc.shadowJar() is applicable for argument types: (build_4u6g4d26qfndzikzbvofa8lrc$_run_closure6) values: [build_4u6g4d26qfndzikzbvofa8lrc$_run_closure6@101c8417]`

I know that, clearly, I did something wrong, but what have I done to deserve the
punishment of such a vague error message?
Maybe I *should* rewrite this into `Makefile`s one subproject at a time. After
all, Gradle prides itself on being able to integrate with existing build tools,
so I'll be able to gradually introduce a more straightforward build system.
I'm wondering how often someone calls `Makefile`s straightforward.

Attempt 2 that I arrived at by diving into the implementation at least builds:

```groovy
def shadowJarTask = shadowJar {
    classifier null
    // Shadow only byte buddy, do not package kotlin stdlib
    configurations = [project.configurations.shadowDeps]
    relocate('net.bytebuddy', 'kotlinx.coroutines.repackaged.net.bytebuddy')
    var transformation = new DontIncludeResourceTransformer()
    transformation.resource = "module-info.class"
    transform(transformation)

    manifest {
        attributes "Premain-Class": "kotlinx.coroutines.debug.AgentPremain"
        attributes "Can-Redefine-Classes": "true"
    }
}
```

But alas, it doesn't work: the `module-info.class` from the shaded library is
included. Maybe `DontIncludeResourceTransformer` is for the project files and
I need `IncludeResourceTransformer` (yes, the names are not of much help).

```
  - In plugin 'com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin' type 'com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar' property 'transformers.IncludeResourceTransformer$0.file' doesn't have a configured value.
    
    Reason: This property isn't marked as optional and no value has been configured.
    
    Possible solutions:
      1. Assign a value to 'transformers.IncludeResourceTransformer$0.file'.
      2. Mark property 'transformers.IncludeResourceTransformer$0.file' as optional.
```

Ok, let's pay attention...

```groovy
    var transformation = new IncludeResourceTransformer()
    transformation.file = new File("build/classes/kotlin/java9/module-info.class")
    transformation.resource = "META-INF/versions/9/module-info.class"
    transform(transformation)
```

This, too, builds, but doesn't affect the result: the wrong `module-info.class`
still gets included. This is surely getting ridiculous. Should I go to Stack
Overflow and ask a question there?

<https://stackoverflow.com/questions/69431812/maven-shade-plugin-override-a-file>
has a useful advice: maybe we could explicitly exclude the `module-info.class`
file and then include the one that we want.

Maybe ChatGPT (3, I don't have access to 4) knows?

```
Hey ChatGPT, please help me.
I'm trying to use the Shadow plugin for Gradle. There's a file with the same path in both my project and the jar I'm trying to shade, and I want my file to replace the one from the shaded jar. How do I configure the Gradle task?
ChatGPT

---

Certainly! When using the Shadow plugin for Gradle, you can configure it to handle resource merging and file replacement through the merge strategy. In your case, you want to ensure that a specific file from your project replaces the one from the shaded JAR. Here's an example configuration:

gradle

plugins {
    id 'com.github.johnrengelman.shadow' version '7.0.0'
}

shadowJar {
    // other shadowJar configurations

    merge service.provider
}

In the example above, service.provider is the name of the strategy for merging resources. This strategy merges resources from the shadowed JAR with the resources from the project, allowing you to specify how conflicts should be resolved.

Now, to replace a specific file, you can use the exclude and from methods within the merge block. Here's an example:

gradle

shadowJar {
    // other shadowJar configurations

    merge service.provider

    merge {
        exclude 'path/to/conflicting/file/in/shaded/jar'
        from 'path/to/replacement/file/in/project'
    }
}

In this example, replace 'path/to/conflicting/file/in/shaded/jar' with the path of the file you want to exclude from the shaded JAR, and 'path/to/replacement/file/in/project' with the path of the file from your project that should replace the one in the shaded JAR.

This configuration ensures that the specified file from your project will replace the corresponding file in the shaded JAR during the shadowJar task execution.

Make sure to adapt the paths according to your project's structure.
```

It hallucinated a response. Now's the time to go to the Shadow's issue tracker
and complain that the solution I obtained from ChatGPT doesn't work.

```groovy
def shadowJarTask = shadowJar {
    classifier null
    // Shadow only byte buddy, do not package kotlin stdlib
    configurations = [project.configurations.shadowDeps]
    relocate('net.bytebuddy', 'kotlinx.coroutines.repackaged.net.bytebuddy')
    var transformation1 = new DontIncludeResourceTransformer()
    transformation1.resource = "META-INF/versions/9/module-info.class"
    transform(transformation1)
    var transformation2 = new IncludeResourceTransformer()
    transformation2.file = new File("build/classes/kotlin/java9/module-info.class")
    transformation2.resource = "META-INF/versions/9/module-info.class"
    transform(transformation2)

    manifest {
        attributes "Premain-Class": "kotlinx.coroutines.debug.AgentPremain"
        attributes "Can-Redefine-Classes": "true"
    }
}
```
?

No, no result. The file is the correct one: if I specify something non-existent,
the build fails. I'm trying to exclude the "bad" file and replace it with the
"good" file, but to no avail.

Maybe `grep.app` will show me the way?
<https://grep.app/search?q=IncludeResourceTransformer>.

<https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/UseDependencyReducedPom.java>
Hey, that's what I just tried!

Maybe the `module-info.class` I'm trying to put into the jar is somehow the
wrong one?

```sh
$ strings ./kotlinx-coroutines-debug/build/classes/kotlin/java9/module-info.class
SourceFile
module-info.java
Module
5.7.0
1.7.0
module-info
kotlinx.coroutines.debug
        java.base
java.management
java.instrument
kotlin.stdlib
kotlinx.coroutines.core
net.bytebuddy
net.bytebuddy.agent
org.junit.jupiter.api
org.junit.platform.commons
kotlinx/coroutines/debug/junit4
kotlinx/coroutines/debug/junit5
```

This seems to corellate exactly to `module-info.java`, so no, it's not that.

I'm starting to lose patience. I've been at it for more than an hour at this
point only today. I'd be happy to understand how to do this properly, but I
don't even know where to look.

Maybe the code that already puts the `module-info.class` into other jars would
work if I explicitly add it to the shadow jar task?
<https://github.com/Kotlin/kotlinx.coroutines/blob/9f9cb872ba7118cf2caf43d65815dc86fd0edb21/buildSrc/src/main/kotlin/Java9Modularity.kt#L145-L149>

```groovy
def shadowJarTask = shadowJar {
    classifier null
    // Shadow only byte buddy, do not package kotlin stdlib
    configurations = [project.configurations.shadowDeps]
    relocate('net.bytebuddy', 'kotlinx.coroutines.repackaged.net.bytebuddy')

    manifest {
        attributes "Premain-Class": "kotlinx.coroutines.debug.AgentPremain"
        attributes "Can-Redefine-Classes": "true"
    }

    manifest {
        attributes("Multi-Release" to true)
    }
    def compileJavaModuleInfo = tasks.named("compileModuleInfoJava")
    from(compileJavaModuleInfo) {
        // Include **only** file we are interested in as JavaCompile output also contains some tmp files
        include("module-info.class")
        into("META-INF/versions/9/")
    }
}
```

Of course not.

```
> Could not get unknown property 'to' for object of type com.github.jengelman.gradle.plugins.shadow.tasks.DefaultInheritManifest.
```

I'm clearly trying something exotic: according to Google,

> `No results found for "Could not get unknown property 'to' for object of type".`

Screw this, I'll read the entirety of
<https://imperceptiblethoughts.com/shadow/>.

* <https://imperceptiblethoughts.com/shadow/getting-started/#default-java-groovy-tasks>
  is not complete: `module-info.class` is also excluded.

After carefully reading the documentation, I think I gained some understanding.

* Once we write `exclude`, the file passed as the argument won't be in the
  resulting jar.
* `Transformer` also isn't of much help: it's invoked on the entries of the
  resulting jar before being written, possibly rewriting it several times when
  new information arrives. This is why all the transformers provided out of the
  box don't take the nature of where the entries come from into account.

With this newfound understanding, I decided that there's just no better way than
creating a new zip file and copying everything there, along with the new
`module-info.class`. Luckily, someone provided some code under the "shadow
throws away our module-info" issue that only needs to be slightly adapted:
<https://github.com/johnrengelman/shadow/issues/710#issuecomment-1280585784>.

One trick we must keep in mind is that the new jar file will have its own
manifest, so we need to move the manifest-specification code to the new place.

With that, I have my first PR for the week:
<https://github.com/Kotlin/kotlinx.coroutines/pull/3948>.

Of course, such endeavors don't stop at that. Fixing the issue does not mean it
won't happen again. To ensure the problem gets caught, we need to write tests.

I don't know quite how to test this. I have no idea how `module-info.class` is
structured internally, and writing my own parser for `module-info.java` also
seems a bit excessive. Perhaps I could utilize the existing tools?

```sh
#!/bin/sh
# Usage: `clean=true sh check_module_info.sh`

modules_with_module_info="
:kotlinx-coroutines-core:jvm
ui:kotlinx-coroutines-javafx:
ui:kotlinx-coroutines-android:
ui:kotlinx-coroutines-swing:
:kotlinx-coroutines-test:jvm
:kotlinx-coroutines-debug:
reactive:kotlinx-coroutines-reactive:
reactive:kotlinx-coroutines-jdk9:
reactive:kotlinx-coroutines-reactor:
reactive:kotlinx-coroutines-rx3:
reactive:kotlinx-coroutines-rx2:
"

m2_path="${m2_path:-$HOME/.m2}"

if [ "${clean:+x}" = "x" ]; then
    rm -fr "$m2_path"
    ./gradlew clean publishToMavenLocal
fi

for entry in $modules_with_module_info; do
    prefix=$(printf "%s" "$entry" | awk -F: '{ print $1 }')
    module=$(printf "%s" "$entry" | awk -F: '{ print $2 }')
    platform=$(printf "%s" "$entry" | awk -F: '{ print $3 }')
    path="${prefix:-.}/$module${platform:+/}$platform/src/module-info.java"
    artifact_name=$module${platform:+-}$platform
    artifact=$(
        find $m2_path/repository/org/jetbrains/kotlinx/$artifact_name \
            -name "$artifact_name-*.jar" \
            -not -name '*sources.jar' \
            -not -name '*javadoc.jar' \
            -not -name '*all.jar' \
    )
    jar --describe-module --file "$artifact" --release 9 | while read line; do
        if [ "$line" != "requires java.base mandated" -a "$line" != "releases: 9" ] && ! printf "%s" "$line" | grep -q 'jar:file://'; then
            if ! grep -q "$line" "$path"; then
                printf "Extra line '$line' not found in $path\n"
            fi
        fi
    done
done
```

A big problem I noticed during writing this is that I also published the
`kotlinx-coroutines-debug-VERSION-all.jar` artifact. I wonder why it happened.
Let's keep this in mind.

An issue with this script is that if some entries are missing from
`module-info.java`, we won't know this from this test. For example, if an
empty `module-info.class` gets published somehow. Still, as a smoke test, this
could be fine. Sure, it's Linux-specific, but we only need to test this somehow,
not to provide a cross-platform library for testing `module-info`
implementations. Alas, it doesn't work, even with these restrictions.

The output is

```
Extra line 'requires java.instrument static' not found in ./kotlinx-coroutines-core/jvm/src/module-info.java
Extra line 'requires jdk.unsupported static' not found in ./kotlinx-coroutines-core/jvm/src/module-info.java
Extra line 'requires kotlin.stdlib transitive' not found in ./kotlinx-coroutines-core/jvm/src/module-info.java
Extra line 'uses kotlinx.coroutines.CoroutineExceptionHandler' not found in ./kotlinx-coroutines-core/jvm/src/module-info.java
Extra line 'uses kotlinx.coroutines.internal.MainDispatcherFactory' not found in ./kotlinx-coroutines-core/jvm/src/module-info.java
Extra line 'provides kotlinx.coroutines.internal.MainDispatcherFactory with kotlinx.coroutines.javafx.JavaFxDispatcherFactory' not found in ui/kotlinx-coroutines-javafx/src/module-info.java
Extra line 'provides kotlinx.coroutines.internal.MainDispatcherFactory with kotlinx.coroutines.android.AndroidDispatcherFactory' not found in ui/kotlinx-coroutines-android/src/module-info.java
Extra line 'provides kotlinx.coroutines.internal.MainDispatcherFactory with kotlinx.coroutines.swing.SwingDispatcherFactory' not found in ui/kotlinx-coroutines-swing/src/module-info.java
Extra line 'provides kotlinx.coroutines.CoroutineExceptionHandler with kotlinx.coroutines.test.internal.ExceptionCollectorAsService' not found in ./kotlinx-coroutines-test/jvm/src/module-info.java
Extra line 'provides kotlinx.coroutines.internal.MainDispatcherFactory with kotlinx.coroutines.test.internal.TestMainDispatcherFactory' not found in ./kotlinx-coroutines-test/jvm/src/module-info.java
Extra line 'contains kotlinx.coroutines.test.internal' not found in ./kotlinx-coroutines-test/jvm/src/module-info.java
Extra line 'provides kotlinx.coroutines.reactive.ContextInjector with kotlinx.coroutines.reactor.ReactorContextInjector' not found in reactive/kotlinx-coroutines-reactor/src/module-info.java
```

Let's go through these as groups.

* `requires java.instrument static` is missing because the corresponding like is
  actually `requires static java.instrument;`. I don't know why the utility
  provides its output with a syntax different from the one used in
  `module-info.java`. I tried swapping the name with the modifier in the
  Java file, and the syntax highlighting was no longer active.
* `uses kotlinx.coroutines.CoroutineExceptionHandler` and other places use
  fully qualified names, whereas the `module-info.java` files are written using
  imports, so this looks like `uses CoroutineExceptionHandler` with an
  `import kotlinx.coroutines.CoroutineExceptionHandler` on top.
* `contains kotlinx.coroutines.test.internal` is something that's simply not in
  the original file. I don't know where it comes from.

It's *possible* to work around these individually, but this looks like the limit
of applicability of shell scripts. Maybe I'll have to look into libraries that
the `jar` utility uses to parse `module-info.class`.

But first, `kotlinx-coroutines-debug-VERSION-all`. What's it doing here?

```sh
$ ls ~/.m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-debug/1.7.2-SNAPSHOT/
kotlinx-coroutines-debug-1.7.2-SNAPSHOT-all.jar      kotlinx-coroutines-debug-1.7.2-SNAPSHOT.module       maven-metadata-local.xml
kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar          kotlinx-coroutines-debug-1.7.2-SNAPSHOT.pom          
kotlinx-coroutines-debug-1.7.2-SNAPSHOT-javadoc.jar  kotlinx-coroutines-debug-1.7.2-SNAPSHOT-sources.jar  
$ cd ~/.m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-debug/1.7.2-SNAPSHOT/
$ diff kotlinx-coroutines-debug-1.7.2-SNAPSHOT-all.jar kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar
Binary files kotlinx-coroutines-debug-1.7.2-SNAPSHOT-all.jar and kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar differ
```

I think the `-all` artifact is the output from the shadow jar task, right?

```sh
$ comm -3 <(unzip -l kotlinx-coroutines-debug-1.7.2-SNAPSHOT-all.jar  | awk '{print $4}' | sort -u) <(unzip -l kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar  | awk '{print $4}' | sort -u)
        META-INF/versions/
        META-INF/versions/9/
        META-INF/versions/9/module-info.class
```

Yep, on-point. The only thing left is to discover how to exclude it from
publication.

```sh
$ git grep -l publish | grep -v '^reactive' | grep -v '^kotlinx-coroutines-core'
CHANGES_UP_TO_1.7.md
README.md
benchmarks/src/jmh/kotlin/benchmarks/flow/scrabble/ReactorPlaysScrabble.kt
build.gradle
buildSrc/src/main/kotlin/CommunityProjectsBuild.kt
buildSrc/src/main/kotlin/Projects.kt
buildSrc/src/main/kotlin/Publishing.kt
buildSrc/src/main/kotlin/animalsniffer-conventions.gradle.kts
buildSrc/src/main/kotlin/bom-conventions.gradle.kts
buildSrc/src/main/kotlin/java-modularity-conventions.gradle.kts
buildSrc/src/main/kotlin/kover-conventions.gradle.kts
gradle/publish-mpp-root-module-in-platform.gradle
gradle/publish.gradle
integration-testing/README.md
integration-testing/build.gradle
integration-testing/smokeTest/build.gradle
kotlinx-coroutines-bom/build.gradle
```

From these, `gradle/publish.gradle` seems the most relevant.

`maven-publish` is used, and
<https://imperceptiblethoughts.com/shadow/publishing/> says that Shadow will
automatically configure publishing as needed.
Ok, but it's not needed. What do I do now?

Googling "gradle exclude jar from publication," I find this:
<https://github.com/gradle/gradle/issues/11150> It looks scary:

> In order to exclude files one can create a custom JAR artifact, but the
> problem is that the generated POM will be lacking all of the dependencies.
> This is especially noticeable in multi-module projects.

I'll need to check the resulting POM as well, it seems!

... The POM seems fine.

2023-11-22
----------

Yesterday, I had no deadlines. Today, I have five, and they are all quite near!

* The next release for coroutines needs to happen soon, so we'll need to somehow
  roll out <https://github.com/Kotlin/kotlinx.coroutines/pull/3945>;
* For the same reason, I'll need to finish
  <https://github.com/Kotlin/kotlinx.coroutines/pull/3948> until then;
* I have to review the WASM implementation for the datetime library:
  <https://github.com/Kotlin/kotlinx-datetime/pull/315>;
* I need to review a chapter about testing coroutines for a book;
* I have to provide a roadmap for the datetime project.

All of this is somewhere until the 1st of December, which is only a bit more
than a week away.

I'll start by providing actionable points to
<https://github.com/Kotlin/kotlinx-datetime/pull/315>. Right now, the changes
are so intrusive that it's even hard to properly review them. I've sent a couple
of comments about this.

With regards to
<https://github.com/Kotlin/kotlinx-datetime/pull/315>, everything's worse than
I suspected. <https://github.com/joffrey-bion> shared some code that solves the
same problem:
<https://github.com/joffrey-bion/krossbow/blob/d2dce0e32e28fdbe7754b87df9b1ca111c133911/autobahn-tests/build.gradle.kts#L175-L237>

An interesting detail is that there is an `environment` function for
native tests. Maybe I missed something, or maybe it's somehow in a different
interface. I'll need to take a look.

A very unpleasant discovery is that the native tests in the iOS simulator do
accept environment variables, but need the specific prefix `SIMCTL_CHILD_` for
them to be passed to the test process.

I just don't think it's realistic to expect the people who want to set a timeout
globally to jump through all these hoops. At the same time, it doesn't seem fair
to only provide the configuration to the JVM, configuring which was implemented
properly: the same reasoning is applicable to all tests.

The good solution would be to fix this at the root: provide a cross-platform
mechanism for exposing properties for code execution. Then we'd triumphantly
add the property and leave it at that. Of course, we don't have the time to do
quite that.

I'm afraid we don't have a better choice than to implement it on the JVM first
and only support the other platforms once we provide a proper cross-platform
mechanism to do so. The big question then becomes: when we do introduce that
mechanism, how will it work? If would be nice if it could be compatible with the
solution we present right now for the JVM.

So, I'll have to right now find the pros and cons of environment variables and
system properties for the JVM to understand which of them will likely be chosen
in the end.

* System properties can be edited in runtime;
  on the JVM, the environment variables can not.
* System properties stay in the same JVM process; the environment variables get
  naturally inherited.

On Node JS:

```sh
$ x=y node -e 'console.log(process.env.x); process.env.x = "z"; console.log(process.env.x)'
y
z
```

On the POSIX-compatible Native, naturally, reassignment of environment variables
is also possible.

So, if we want to, we probably can assure that the configurable parameters can
also be set during runtime. Is this a desirable thing? I'd say yes. This way,
when you don't control how your program will be started, you can still place the
configurable parameter assignments at the top of `main()` and have it work.

Do we want the configuration parameters to get inherited in separate processes?
I think we don't.

It looks like, on the JVM, a system property is the way to go, whereas for the
other platforms, we will need some creative thinking--but not today.


Right now, let's finish at least the `kotlinx-coroutines-debug` fix.
I'm fighting not only Gradle but also our own Gradle configs this time.

I'm trying to untangle the tricky interplay of task creation. I see that
`publish.gradle` contains a special case: for `kotlinx-coroutines-debug`,
before `maven-publish` is activated, the shadow plugin is. I think it shouldn't
be like this, so I remove that special case.

Now, `build.gradle` fails, as there is special handling of the `shadowJar`
task there. It's done so that it's known into which file to put the version file
(I have no idea why it's needed, though).

I could remove the `kotlinx-coroutines-debug` handling from there and just
copy-paste it to `kotlinx-coroutines-debug/build.gradle`, but being a good
citizen, instead I split that logic into the reusable part (which I place in
`buildSrc`) and the dirty part (which I leave as is). The end result is that

```groovy

    def thisProject = it
    if (thisProject.name in sourceless) {
        return
    }

    def versionFileTask = thisProject.tasks.register("versionFileTask") {
        def name = thisProject.name.replace("-", "_")
        def versionFile = thisProject.layout.buildDirectory.file("${name}.version")
        it.outputs.file(versionFile)

        it.doLast {
            versionFile.get().asFile.text = version.toString()
        }
    }

    List<String> jarTasks
    if (isMultiplatform(it)) {
        jarTasks = ["jvmJar"]
    } else if (it.name == "kotlinx-coroutines-debug") {
        // We shadow debug module instead of just packaging it
        jarTasks = ["shadowJar"]
    } else {
        jarTasks = ["jar"]
    }

    for (name in jarTasks) {
        thisProject.tasks.named(name, Jar) {
            it.dependsOn versionFileTask
            it.from(versionFileTask) {
                into("META-INF")
            }
        }
    }
```

becomes

```groovy
    if (it.name !in sourceless) {
        def jarTask
        if (isMultiplatform(it)) {
            jarTask = "jvmJar"
        } else if (it.name == "kotlinx-coroutines-debug") {
            return // we configure it in that module when the proper task is created
        } else {
            jarTask = "jar"
        }
        VersionFile.configure(it, it.tasks.named(jarTask, Jar).get())
    }
```

with the help of

```kotlin
import org.gradle.api.*
import org.gradle.api.tasks.bundling.*
import org.gradle.kotlin.dsl.*

object VersionFile {
    fun configure(project: Project, jarTask: Jar) = with(project) {
        val versionFileTask by tasks.register("versionFileTask") {
            val name = project.name.replace('-', '_')
            val versionFile = project.layout.buildDirectory.file("$name.version")
            outputs.file(versionFile)
            doLast {
                versionFile.get().asFile.writeText(project.version.toString())
            }
        }
        jarTask.dependsOn(versionFileTask)
        jarTask.from(versionFileTask) {
            into("META-INF")
        }
    }
}
```

How nice it would be if this were so simple.

Unfortunately...

```
No signature of method: static VersionFile.configure() is applicable for argument types: (org.gradle.api.internal.project.DefaultProject_Decorated, org.gradle.api.tasks.bundling.Jar_Decorated) values: [project ':kotlinx-coroutines-android', task ':kotlinx-coroutines-android:jar']
  Possible solutions: configure(org.gradle.api.Project, org.gradle.api.tasks.bundling.Jar)
```

Ok, `org.gradle.api.tasks.bundling.Jar` inherits from `org.gradle.jvm.tasks.Jar`.
Maybe I'm somehow requiring too specific a version in my script...

Nope:

```
> No signature of method: static VersionFile.configure() is applicable for argument types: (org.gradle.api.internal.project.DefaultProject_Decorated, org.gradle.api.tasks.bundling.Jar_Decorated) values: [project ':kotlinx-coroutines-android', task ':kotlinx-coroutines-android:jar']
  Possible solutions: configure(org.gradle.api.Project, org.gradle.jvm.tasks.Jar)
```

Ok. Screw `Jar`. I only need the copying facilities and this being a task.

```
> No signature of method: static VersionFile.configure() is applicable for argument types: (org.gradle.api.internal.project.DefaultProject_Decorated, org.gradle.api.tasks.bundling.Jar_Decorated) values: [project ':kotlinx-coroutines-android', task ':kotlinx-coroutines-android:jar']
  Possible solutions: configure(org.gradle.api.Project, org.gradle.api.tasks.AbstractCopyTask)
```

Just what **is** being passed to me?

Looking at how `Java9Modularity` is used, I see that there's a separate
`gradle.kts` script that calls its functions. Maybe that's a solution.

Looks like it is. Now, `build.gradle` only has
`apply plugin: "version-file-conventions"`, but there's an extra `gradle.kts`
file:

```kotlin
/* `kotlinx-coroutines-debug` configures `VersionFile` on its own when the corresponding task is created. */
val invalidModules = listOf("kotlinx-coroutines-debug")

configure(subprojects.filter {
    !unpublished.contains(it.name) && !invalidModules.contains(it.name) && it.name !in sourceless
}) {
    val jarTask = tasks.withType(Jar::class.java).findByName(if (isMultiplatform) { "jvmJar" } else { "jar" })!!
    VersionFile.configure(this, jarTask)
}
```

Alas:

```
> No signature of method: static VersionFile.configure() is applicable for argument types: (org.gradle.api.internal.project.DefaultProject_Decorated, com.github.jengelman.gradle.plugins.shadow.tasks.Sh
adowJar_Decorated) values: [project ':kotlinx-coroutines-debug', task ':kotlinx-coroutines-debug:shadowJar']
  Possible solutions: configure(org.gradle.api.Project, org.gradle.api.tasks.AbstractCopyTask)
```

What now? Rewrite `kotlinx-coroutine-debug`'s `build.gradle` to `gradle.kts`?
Ok, I'll try.

How do we apply plugins in `gradle.kts`?

```sh
$ find . -name '*.kts' | xargs grep apply
./buildSrc/src/main/kotlin/animalsniffer-conventions.gradle.kts:    apply(plugin = "ru.vyarus.animalsniffer")
# and other output
```

...

Ok, I think I migrated this. Let's give it a go...

```
* Where:                                                                                                                                                                                                 
Build file '/home/dmitry.khalanskiy/IdeaProjects/kotlinx.coroutines/kotlinx-coroutines-bom/build.gradle' line: 25                                                                                        
                                                                                                                                                                                                         
* What went wrong:                                                                                                                                                                                       
A problem occurred evaluating project ':kotlinx-coroutines-bom'.                                                                                                                                         
> Could not create task ':kotlinx-coroutines-debug:shadowJarWithCorrectModuleInfo'.                                                                                                                      
   > DefaultTaskContainer#register(String, Action) on task set cannot be executed in the current context.                                                                                                
```

WOW! What a terrible error message! My intuition suggested the cause, but sadly,
even after fixing this issue, I still have no idea why it happened and why
the issue arose in a completely different module in an arbitrary place.

The reason was that I used effectively `otherTask.dependsOn(this)`. I fixed
this by calling `otherTask.dependsOn(thisTask.get())` from outside the
definition of `thisTask`. If not for my experience, this could have taken
days!

Nevermind, this can still take days.

```
Execution failed for task ':kotlinx-coroutines-debug:publishMavenPublicationToMavenLocal'.
> Failed to publish publication 'maven' to repository 'mavenLocal'
   > Artifact kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar wasn't produced by this build.
```

Oh, okay, but *what is this then*?

```sh
$ find kotlinx-coroutines-debug/ -name '*.jar'
kotlinx-coroutines-debug/build/libs/kotlinx-coroutines-debug-1.7.2-SNAPSHOT-all.jar
kotlinx-coroutines-debug/build/libs/kotlinx-coroutines-debug-1.7.2-SNAPSHOT-sources.jar
kotlinx-coroutines-debug/build/libs/kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar
kotlinx-coroutines-debug/build/libs/kotlinx-coroutines-debug-1.7.2-SNAPSHOT-javadoc.jar
```

Unless I'm sorely mistaken, **there is** a file with that name!

```kotlin
tasks.getByName("publishMavenPublicationToMavenLocal") {
    dependsOn(shadowJarWithCorrectModuleInfo)
}
```

also doesn't help. So, the file probably *does* get created, but for some
reason that Gradle refuses to tell me, that's not enough.

With `--info`, I don't see any new relevant information.

The query `maven custom jar "wasn't produced by this build"` produces this
seemingly relevant information:
<https://github.com/spring-projects/spring-boot/issues/23797>.

With this, the build passes:
```kotlin

configurations.configureEach {
    outgoing.artifacts.removeIf { it.buildDependencies.getDependencies(null).contains(jar) }
    outgoing.artifact(shadowJarWithCorrectModuleInfo)
}
```

but I'm back at square one, only now with `build.gradle.kts`:

```sh
$ ls ~/.m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-debug/1.7.2-SNAPSHOT/
kotlinx-coroutines-debug-1.7.2-SNAPSHOT-all.jar
kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar
kotlinx-coroutines-debug-1.7.2-SNAPSHOT-javadoc.jar
kotlinx-coroutines-debug-1.7.2-SNAPSHOT.module
kotlinx-coroutines-debug-1.7.2-SNAPSHOT.pom
kotlinx-coroutines-debug-1.7.2-SNAPSHOT-sources.jar
maven-metadata-local.xml
```

But this time, I think I know what to do... roughly.

```sh
configurations.all {
    outgoing.artifacts.removeIf {
        val dependencies = it.buildDependencies.getDependencies(null)
        dependencies.contains(jar) || dependencies.contains(shadowJar)
    }
    if (name == "apiElements" || name == "runtimeElements") {
        outgoing.artifact(shadowJarWithCorrectModuleInfo)
    }
}
```

Yes! This fixes my immediate problem:

```sh
$ ls ~/.m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-debug/1.7.2-SNAPSHOT/
kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar
kotlinx-coroutines-debug-1.7.2-SNAPSHOT-javadoc.jar
kotlinx-coroutines-debug-1.7.2-SNAPSHOT.module
kotlinx-coroutines-debug-1.7.2-SNAPSHOT.pom
kotlinx-coroutines-debug-1.7.2-SNAPSHOT-sources.jar
maven-metadata-local.xml
```

Now, the problem is, I have no idea what I've just done and how will it affect
the project down the line. Let's push this to the CI and see if there are
some obvious failures.

Let's try to play with the system in the meantime to discover what I've done.

```kotlin
configurations.all {
    if (outgoing.artifacts.isNotEmpty()) {
        println("CONFIG $this (1): ${outgoing.artifacts.toList()}")
        outgoing.artifacts.removeIf {
            val dependencies = it.buildDependencies.getDependencies(null)
            dependencies.contains(jar) || dependencies.contains(shadowJar)
        }
        if (name == "apiElements" || name == "runtimeElements") {
            outgoing.artifact(shadowJarWithCorrectModuleInfo)
        }
        println("CONFIG $this (2): ${outgoing.artifacts.toList()}")
    }
}
```

```sh
$ ./gradlew --info :kotlinx-coroutines-debug:clean :kotlinx-coroutines-debug:publishToMavenLocal | grep CONFIG
CONFIG configuration ':kotlinx-coroutines-debug:apiElements' (1): [org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact@28ef8fa4]
CONFIG configuration ':kotlinx-coroutines-debug:apiElements' (2): [DecoratingPublishArtifact_Decorated kotlinx-coroutines-debug:jar:jar:]
CONFIG configuration ':kotlinx-coroutines-debug:archives' (1): [org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact@28ef8fa4]
CONFIG configuration ':kotlinx-coroutines-debug:archives' (2): []
CONFIG configuration ':kotlinx-coroutines-debug:mainSourceElements' (1): [DecoratingPublishArtifact_Decorated resources:directory::null, DecoratingPublishArtifact_Decorated java:directory::null, DecoratingPublishArtifact_Decorated src:directory::null]
CONFIG configuration ':kotlinx-coroutines-debug:mainSourceElements' (2): [DecoratingPublishArtifact_Decorated resources:directory::null, DecoratingPublishArtifact_Decorated java:directory::null, DecoratingPublishArtifact_Decorated src:directory::null]
CONFIG configuration ':kotlinx-coroutines-debug:runtimeElements' (1): [org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact@28ef8fa4]
CONFIG configuration ':kotlinx-coroutines-debug:runtimeElements' (2): [DecoratingPublishArtifact_Decorated kotlinx-coroutines-debug:jar:jar:]
CONFIG configuration ':kotlinx-coroutines-debug:shadow' (1): [DecoratingPublishArtifact_Decorated kotlinx-coroutines-debug:jar:jar:all]
CONFIG configuration ':kotlinx-coroutines-debug:shadow' (2): []
CONFIG configuration ':kotlinx-coroutines-debug:shadowRuntimeElements' (1): [DecoratingPublishArtifact_Decorated kotlinx-coroutines-debug:jar:jar:all]
CONFIG configuration ':kotlinx-coroutines-debug:shadowRuntimeElements' (2): []
CONFIG configuration ':kotlinx-coroutines-debug:testResultsElementsForTest' (1): [DecoratingPublishArtifact_Decorated binary:directory::null]
CONFIG configuration ':kotlinx-coroutines-debug:testResultsElementsForTest' (2): [DecoratingPublishArtifact_Decorated binary:directory::null]
```

Oh, this is actually pretty easy to wrap my head around.

* For `apiElements` and `runtimeElements`, I replaced the `jar` with
  my own archive.
* For `archives`, I removed the `jar` without a suitable replacement.
  Maybe I shouldn't.
* `mainSourceElements` and `testResultsElementsForTest` are intact.
* I removed the only output for the `shadow` and `shadowRuntimeElements`
  configurations. Seems appropriate.

I think I can formulate this in a clearer manner...

But how? My idea is: remove all dependencies on the `shadowJar`, and replace all
dependencies on `jar` with dependencies on `shadowJarWithCorrectModuleInfo`.
How do I express this? If this were a mutable list, I'd be done immediately.
Another option is something like this:

```kotlin
    val newArtifacts: List<PublishArtifact> = outgoing.artifacts.filter {
        // we're not interested at all in the output of the shadowJar task; it's just an intermediate stage
        !it.buildDependencies.getDependencies(null).contains(shadowJar)
    }.map {
        // if something wants a `jar` to be published, we want to publish the `shadowJarWithCorrectModuleInfo` instead
        if (it.buildDependencies.getDependencies(null).contains(jar)) {
            shadowJarWithCorrectModuleInfo as PublishArtifact
        } else {
            it
        }
    }
    outgoing.artifacts.clear()
    outgoing.artifacts.addAll(newArtifacts)

```

But as `shadowJarWithCorrectModuleInfo` is not a `PublishArtifact`, this won't
work. It's not, right?

```
class org.gradle.api.internal.tasks.DefaultTaskContainer$TaskCreatingProvider_Decorated cannot be cast to class org.gradle.api.artifacts.PublishArtifact (org.gradle.api.internal.tasks.DefaultTaskContainer$TaskCreatingProvider_Decorated and org.gradle.api.artifacts.PublishArtifact are in unnamed module of loader org.gradle.internal.classloader.VisitableURLClassLoader @91161c7)
```

Yep.

Attempt 2:

```kotlin
configurations.all {
    if (outgoing.artifacts.isNotEmpty()) {
        println("CONFIG $this (1): ${outgoing.artifacts.toList()}")
        // we're not interested at all in the output of the shadowJar task; it's just an intermediate stage
        outgoing.artifacts.removeIf { it.buildDependencies.getDependencies(null).contains(shadowJar) }
        // if something wants a `jar` to be published, we want to publish the `shadowJarWithCorrectModuleInfo` instead
        if (outgoing.artifacts.buildDependencies.getDependencies(null).contains(jar)) {
            outgoing.artifacts.removeIf { it.buildDependencies.getDependencies(null).contains(jar) }
            outgoing.artifact(shadowJarWithCorrectModuleInfo)
        }
        println("CONFIG $this (2): ${outgoing.artifacts.toList()}")
    }
}
```

produces

```sh
$  ./gradlew --info :kotlinx-coroutines-debug:clean :kotlinx-coroutines-debug:publishToMavenLocal | grep CONFIG
CONFIG configuration ':kotlinx-coroutines-debug:apiElements' (1): [org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact@72ea3f0]
CONFIG configuration ':kotlinx-coroutines-debug:apiElements' (2): [DecoratingPublishArtifact_Decorated kotlinx-coroutines-debug:jar:jar:]
CONFIG configuration ':kotlinx-coroutines-debug:archives' (1): [org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact@72ea3f0]
CONFIG configuration ':kotlinx-coroutines-debug:archives' (2): [DecoratingPublishArtifact_Decorated kotlinx-coroutines-debug:jar:jar:]
CONFIG configuration ':kotlinx-coroutines-debug:mainSourceElements' (1): [DecoratingPublishArtifact_Decorated resources:directory::null, DecoratingPublishArtifact_Decorated java:directory::null, DecoratingPublishArtifact_Decorated src:directory::null]
CONFIG configuration ':kotlinx-coroutines-debug:mainSourceElements' (2): [DecoratingPublishArtifact_Decorated resources:directory::null, DecoratingPublishArtifact_Decorated java:directory::null, DecoratingPublishArtifact_Decorated src:directory::null]
CONFIG configuration ':kotlinx-coroutines-debug:runtimeElements' (1): [org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact@72ea3f0]
CONFIG configuration ':kotlinx-coroutines-debug:runtimeElements' (2): [DecoratingPublishArtifact_Decorated kotlinx-coroutines-debug:jar:jar:]
CONFIG configuration ':kotlinx-coroutines-debug:shadow' (1): [DecoratingPublishArtifact_Decorated kotlinx-coroutines-debug:jar:jar:all]
CONFIG configuration ':kotlinx-coroutines-debug:shadow' (2): []
CONFIG configuration ':kotlinx-coroutines-debug:shadowRuntimeElements' (1): [DecoratingPublishArtifact_Decorated kotlinx-coroutines-debug:jar:jar:all]
CONFIG configuration ':kotlinx-coroutines-debug:shadowRuntimeElements' (2): []
CONFIG configuration ':kotlinx-coroutines-debug:testResultsElementsForTest' (1): [DecoratingPublishArtifact_Decorated binary:directory::null]
CONFIG configuration ':kotlinx-coroutines-debug:testResultsElementsForTest' (2): [DecoratingPublishArtifact_Decorated binary:directory::null]
```

Yes, I think this is it. Now I'm fairly confident in this solution.


2023-11-23
----------

I'm doing meditative work today: transforming
<https://github.com/Kotlin/kotlinx.coroutines/compare/develop...kotlin-community/k2/dev>
into
<https://github.com/Kotlin/kotlinx.coroutines/compare/develop...kotlin-community/k2/dev3>

Throwing away the extra fluff, outdated workarounds, and generally, just keeping
the required things with a clear reason to have them.

2023-11-24
----------

Some magic of command-line interfaces. Would I be able to do this in the IDE?
I don't think so.

I looked at the commit
<https://github.com/Kotlin/kotlinx.coroutines/pull/3953/commits/91df241e7fd57c5595ca8ba2123311b96a606442>
and thought: hey, we're adding a suppression even before the compiler has
started complaining about this code. This will show up as a warning, so a
comment is really in order so that we know not to remove the suppression
accidentally.

There are 38 lines here, and going through each one, looking in different files,
is boring.

So, here's what I did instead:

```
git checkout kotlin-community/k2/dev3
git rebase -i origin/develop # choose `edit` for the commits that add suppressions
git status | grep 'modified: ' | awk '{ print $2 }' |
    xargs -n1 sed -i 's;\("INVISIBLE_MEMBER".*)\);\1 // do not remove the INVISIBLE_REFERENCE suppression: required in K2;'
```

Voila, done. Every mention of `INVISIBLE_MEMBER` is accompanied by the prompt
not to remove the corresponding `INVISIBLE_REFERENCE`.


The archangel of XBox 360 (I guess?) visited my pull request and blessed me with
some knowledge of Gradle!
<https://github.com/Kotlin/kotlinx.coroutines/pull/3948> What a nice event.

This was quite a demanding week. I just looked at the book called
"Code that fits in your head," and my tired brain transformed it at a glance
to "Cool thief that stole candy."
If we take the Levenstein distance between what is written and what I'm reading
as the measure of mental tiredness, then clearly, I'm tired.

Though, of course, the Levenstein difference is a very bad predictor of which
words get mixed up, especially in English.

2023-11-27
----------

Today, I have two goals:

* Finish a PR <https://github.com/Kotlin/kotlinx.coroutines/pull/3945> that
  restores the 60-second timeout to `runTest`.
* See if we can cheaply implement direct access to the timezone databases on
  Apple's devices.

Let's mix the two together. The 60-second timeout one seems like simple work:
after thinking about it for a while, I think that the cross-platform mechanism
of environment configuration will have to rely on system properties on the JVM,
so I'll just introduce this parameter as a JVM-only system property for now due
to time constraints and leave a big TODO to drive the introduction of system
properties. So, I know exactly what to implement, I just need to do it during
my downtime.

The cheap access to Apple's timezone databases is much trickier. The lead we
have is
<https://github.com/HowardHinnant/date/blob/cc4685a21e4a4fdae707ad1233c61bbaff241f93/src/tz.cpp#L475-L481>
By the way, to get this link, I pressed the `y` button on this page:
<https://github.com/HowardHinnant/date/blob/master/src/tz.cpp#L475-L481>.
A nice feature of Github!

If it's possible to avoid using the Foundation API, it would be great. That
API doesn't provide any convenient access to the timezone database.
Maybe iterating over every `nextDaylightSavingTimeTransition` from 1 AD and
calling `secondsFromGMT` for each moment would work, but

* It doesn't give us any way to learn the recurring rules.
* It's very inefficient.

Another scary option is to use
<https://developer.apple.com/documentation/foundation/nstimezone/1387213-data>.
It's only present for `NSTimeZone` and not `TimeZone`. Internally, it turns out,
this is just a normal timezone database file, like the ones you'd encounter in
`/usr/share/zoneinfo/`.

Relying on unspecified behavior on Apple's devices is even more unreliable than
relying on the specified behavior, and probably any iOS developer will confirm
that even the specified behavior is not reliable. I myself encountered this a
lot when researching the datetime formatting. Accessing and parsing `data` may
work for a while, but nothing says that it will continue to do so, especially
given that the new API doesn't even include it.

Hoping that the timezone database on the filesystem is always there and works
properly seems like our best bet. Maybe with a fallback to the suboptimal
procedure of `NSTimeZone` querying.

I received a MacBook and and iPhone from the IT department to test all these
approaches. The reason I need a real iPhone is that the iOS simulator seemingly
has access to the parts of the filesystem that are not present on an actual
iPhone: not so much qemu-style emulation as Wine-style non-emulation, I think.

And the reason I need a MacBook is that, naturally, not just anyone can build
and an application for an iPhone and upload it. You even need to sign a bunch
of agreements to get access to the tooling to do so on a MacBook.

In any case, I've already uploaded my first iOS application to the iPhone and
am ready to experiment.

I rewrote the Apple implementation to use the `/var/db/timezone` path to obtain
the timezone database, but the tests started to fail even on MacOS, without
involving the iPhone.

Of course, diagnozing this is not so easy.
The stacktraces from Kotlin/Native during testing don't include the exceptions
that are the cause, so I have no way to know what fails if the failure gets
wrapped in a `try`-`catch`, and we utilize this approach constantly.
Without the stacktrace, just printing the error message doesn't get me anywhere,
really.

```
Failed requirement.
```

I guess I need to go through the codebase and replace all `require` with ones
that have a more helpful error message. It would be very unpleasant to receive
a bug report with `Failed requirement` as the only lead.

The good news is, only two timezones are affected!
* `Africa/Casablanca`,
* and `Africa/El_Aaiun`.

The other ones work fine on MacOS.

I separate initialization for `Africa/Casablanca` to a separate test...

Eh?

The problem is during parsing the POSIX rulestring `XXX-2<+01>-1,0/0,J365/23`:
`0/0` is parsed as the date "day 0, hour 0." I *thought* this was illegal and
only days 1-366 are permitted. Apparently not?

Yep, I was wrong:

> The zero-based Julian day (0 <= n <= 365). Leap days shall be counted, and it
> is possible to refer to February 29.

Will need to fix this, for sure, but what does this rule even mean?

Let's take something we understand well: `Europe/Berlin`. The rule is
`CET-1CEST,M3.5.0,M10.5.0/3`. It means that the usual offset is `+1`, and in
summer, it's `+2`. The change from `+1` to `+2` happens in late March; the
switch back is in late October.

```
$ zdump -v | grep 2023
/usr/share/zoneinfo/Europe/Berlin  Sun Mar 26 00:59:59 2023 UT = Sun Mar 26 01:59:59 2023 CET isdst=0 gmtoff=3600
/usr/share/zoneinfo/Europe/Berlin  Sun Mar 26 01:00:00 2023 UT = Sun Mar 26 03:00:00 2023 CEST isdst=1 gmtoff=7200
/usr/share/zoneinfo/Europe/Berlin  Sun Oct 29 00:59:59 2023 UT = Sun Oct 29 02:59:59 2023 CEST isdst=1 gmtoff=7200
/usr/share/zoneinfo/Europe/Berlin  Sun Oct 29 01:00:00 2023 UT = Sun Oct 29 02:00:00 2023 CET isdst=0 gmtoff=3600
```

The moment `2023-03-26T02:00+01` equals `2023-03-26T03:00+02`.

Now for the strange rule.

The daylight saving time (offset `01`) starts at midnight from Dec 31st to
Jan 1st and continues until Dec 31st, 23:00, at which point it goes back to the
usual offset `02`.

`2023-12-31T23:00+01` equals `2023-01-01T00:00+02`; however, according to the
rules of the following year, this equals `2023-12-31T23:00+01` once again, after
which, however, no further transitions happen.

In effect, this simply means "`+01`, always," written in a really strange way.

Do we even properly handle such a rule? Won't we hang infinitely when trying to
figure out what's going on?

Very funny: even the `zdump -v` command fails with such an input!
Though for all finite years, it does recognize properly that no transition
actually happens.

```
$ zdump -v /var/db/timezone/zoneinfo/Africa/Casablanca | tail -n5
/var/db/timezone/zoneinfo/Africa/Casablanca  Wed Dec 31 22:59:59 2147485547 UT = Wed Dec 31 23:59:59 2147485547 +01 isdst=1 gmtoff=3600
/var/db/timezone/zoneinfo/Africa/Casablanca  Wed Dec 31 23:00:00 2147485547 UT = 67768036191673200 (localtime failed)
/var/db/timezone/zoneinfo/Africa/Casablanca  Wed Dec 31 23:59:59 2147485547 UT = 67768036191676799 (localtime failed)
/var/db/timezone/zoneinfo/Africa/Casablanca  67768036191676800 (gmtime failed) = 67768036191676800 (localtime failed)
/var/db/timezone/zoneinfo/Africa/Casablanca  9223372036854775807 (gmtime failed) = 9223372036854775807 (localtime failed)
```

No, I don't think we'll hang, but it's worth encoding into a test anyway.
In any case, when queried about whether Dec 31st, 23:30 is in a gap, overlap,
or a regular offset, our system will return nonsense: likely that it's in a gap.
I'm not sure it's worth investing time into, as this rule is clearly malformed.

Luckily, that was it: now the `TimeZoneRulesCompleteTest` passes on MacOS as
well, which means that of the transitions that `zdump` recognizes, we handle
each one correctly.

Now for the iOS. I'm publishing my changes to the datetime library locally
via `publishToMavenLocal` and link against them in the XCode project.

Hey, wait, the tests *do* fail, though not for MacOS but for the
iPhone Simulator. It's a `NullPointerException` when constructing an
`NSTimeZone` from a `TimeZone`. Interesting. The conversion passes for several
time zones before failing for `America/Ciudad_Juarez`. This means that somehow,
there is a file for this timezone on the iOS simulator but no such timezone in
the Foundation framework.

I'm wondering if the `date` library using `/usr/share/zoneinfo/` instead of
`/var/db/timezone/zoneinfo` on the iOS Simulator is intentional and fixes this
somehow.

My code passes in the simulator but fails on the real device because it can't
find the system timezone...

Well, of course it can't: it's not going through the normal channels, it's
directly accessing the filesystem to learn the name of the current time zone,
but even the `date` library doesn't do it this way.

Hey, wow, after fixing this, it worked!

Testing this further, I see that on the iOS Simulator, the only timezone that
is available in `TimeZone.availableZoneIds` but can't be converted to
`NSTimeZone` is `America/Ciudad_Juarez`; on the real device, there are no such
timezones. Likewise, there are no such timezones on MacOS.

I'll probably just need to reorganize the code so that it uses exactly the
timezones available on the system. On Darwin, it would be the ones provided by
<https://developer.apple.com/documentation/foundation/nstimezone/1387223-knowntimezonenames>,
and on Linux, we need to traverse the `/usr/share/zoneinfo/` directory as usual.

2023-11-28
----------

Made another review round on the Wasm support PR in datetime:
<https://github.com/Kotlin/kotlinx-datetime/pull/315>.
Sadly, the architectural approach I was rooting for was not met with support, so
now unrelated code has to change because of this PR. Well, you can't always have
what you want, so I manually checked every `try` block in the shared module, and
it's fine. Here's how I did it:

```sh
git grep -A3 try core/jsAndWasmShared/
```

I've adapted the coroutines codebase a bit to the new compiler: for some reason,
tests started failing on Kotlin 2.0 with the error that a call to `println` was
detected. Turns out, the reason was that in the JVM source-set, we had a special
`println` function in the base class that all tests inherit from, and this
`println` is exempt from our wildcard check of "no `println` calls happen in
the code." Makes sense: `println` in a test is more or less harmless. However,
now, the rules of resolution changed: first, the common source-set without the
overloaded `println` was compiled, and only later, when the `println` is already
resolved to `kotlin.io.println`, does the JVM-specific code gets compiled.

The fix is easy: <https://github.com/Kotlin/kotlinx.coroutines/pull/3955>

Now I'm looking into how to improve
<https://github.com/Kotlin/kotlinx.coroutines/pull/3948>. I was told by
@shanshin that I'm full of sin and I must never do `configurations.all`.
He also advised me to try and manually edit the shadow jar and add a file to
it after the `shadowJar` task is done with it.

Let's try doing that.

The root of the fix is going to be this:

```kotlin
configurations {
    // shadowJar is already part of the `shadowRuntimeElements` and `shadowApiElements`, but it's not enough.
    artifacts {
        add("apiElements", shadowJar)
        add("runtimeElements", shadowJar)
    }
}
```

Naively, this causes this error:

```
   > Artifact kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar wasn't produced by this build.
```

The `jar` task is still set to publish its files, and Gradle complains that
`jar` produced no files.

However, by setting some extra variable called `archiveClassifier.convention`
to `null`, I seemingly trick Gradle into thinking that the jar file that does
get published is suitable for every purpose. I won't question this, I'm just
glad it works.

Ok, how do I add a new file to an existing zip-file?
<https://stackoverflow.com/questions/2223434/appending-files-to-a-zip-file-with-java>
looks promising. I only need to find out what my archive is called.

After a while, I think I got it.

Now, the only thing missing from my side for the release of coroutines,
it seems, is <https://github.com/Kotlin/kotlinx.coroutines/pull/3924>. Aside
from that, I also have to provide a roadmap for the datetime project by the end
of the week and keep reviewing the wasm pull request in datetime as needed.
Seems fairly straightforward. I think I'd rather finish PR #3924 now.

The big problem I'm having with this PR is that on Android, the tests that work
just fine for the `Main` dispatchers of other platforms tend to hang. Luckily,
being an author of a virtual time mplementation, I can make a pretty educated
guess as to what's going on: in the test environment, the clock is substituted
with a controllable one and I need to manipulate it somehow.

A very telling example:

```kotlin
@Test
fun hangingTest() = runBlocking {
    withContext(Dispatchers.Default) {
        withContext(Dispatchers.Main) {
        }
    }
}
```

This just hangs. Why? Well, clearly, the main dispatcher enqueues its task, but
there is no one to run it.

Looking at the surrounding tests, I cook up this solution:

```kotlin
@Test
fun nonHangingTest() = runBlocking {
    val testBody = launch {
        withContext(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
            }
        }
    }
    val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
    while (testBody.isActive) {
        delay(maxOf(mainLooper.nextScheduledTaskTime.toKotlinDuration(), 1.milliseconds))
        mainLooper.runOneTask()
    }
}
```

This works! Though it has a bug: if we are waiting for 10 seconds and a new task
arrives that's supposed to execute in 5 seconds, we'll wait for the whole 10
seconds first. This is a no-go for tests like
`withTimeout(long_time) { delay(short_time) }`: we'll first notice that there's
a task scheduled for `long_time` and wait for that long.

No, a better approach is to do something like this:

```kotlin
while (testBody.isActive) {
    delay(10.milliseconds)
    mainLooper.idleFor(10, TimeUnit.MILLISECONDS)
}
```

This way, every 10 milliseconds (a small amount of time), the real clock and
the virtual clock will get synchronized. This essentially gets rid of the
virtual time altogether, but there doesn't seem to be a downside to it.

I edited the tests, and now they work for essentially every implementation
except Apple. I don't know if if works on Apple, as I usually don't touch the
MacOS that was issued to me unless I really have to. I guess I have to this
time, as the CI on Mac is not enabled for day-to-day builds of coroutines.

Cloning the coroutines repo and trying to run it, I get...

`Unsupported class file major version 63`.

Cool.

You know what they say about the JVM ecosystem? "You just install Java, and it
will work." Not for me. Maybe compared to JS, this is true, but even on Haskell,
notorious for its constant breakage, the tooling is not so ridiculous as to
have something new errors in store every day.

Setting `JAVA_HOME` to JDK 17 (because the internet said so)
as opposed to 19 helped. Gradle started successfully and began to download the
Kotlin compiler. Though I'm sure it has more tricks up its sleeve.

The final nail in the coffin for today: running tests on a Mac floods the
console with messages like

```
Process 'Gradle Test Executor 78' finished with non-zero exit value 1
org.gradle.process.internal.ExecException: Process 'Gradle Test Executor 78' finished with non-zero exit value 1
        at org.gradle.process.internal.DefaultExecHandle$ExecResultImpl.assertNormalExitValue(DefaultExecHandle.java:414)
        at org.gradle.process.internal.worker.DefaultWorkerProcess.onProcessStop(DefaultWorkerProcess.java:141)
        at org.gradle.process.internal.worker.DefaultWorkerProcess.access$000(DefaultWorkerProcess.java:42)
        at org.gradle.process.internal.worker.DefaultWorkerProcess$1.executionFinished(DefaultWorkerProcess.java:94)
        at jdk.internal.reflect.GeneratedMethodAccessor1553.invoke(Unknown Source)
        at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.base/java.lang.reflect.Method.invoke(Method.java:568)
        at org.gradle.internal.dispatch.ReflectionDispatch.dispatch(ReflectionDispatch.java:36)
        at org.gradle.internal.dispatch.ReflectionDispatch.dispatch(ReflectionDispatch.java:24)
        at org.gradle.internal.event.AbstractBroadcastDispatch.dispatch(AbstractBroadcastDispatch.java:43)
        at org.gradle.internal.event.BroadcastDispatch$SingletonDispatch.dispatch(BroadcastDispatch.java:245)
        at org.gradle.internal.event.BroadcastDispatch$SingletonDispatch.dispatch(BroadcastDispatch.java:157)
        at org.gradle.internal.event.ListenerBroadcast.dispatch(ListenerBroadcast.java:141)
        at org.gradle.internal.event.ListenerBroadcast.dispatch(ListenerBroadcast.java:37)
        at org.gradle.internal.dispatch.ProxyDispatchAdapter$DispatchingInvocationHandler.invoke(ProxyDispatchAdapter.java:94)
        at jdk.proxy1/jdk.proxy1.$Proxy117.executionFinished(Unknown Source)
        at org.gradle.process.internal.DefaultExecHandle.setEndStateInfo(DefaultExecHandle.java:221)
        at org.gradle.process.internal.DefaultExecHandle.finished(DefaultExecHandle.java:354)
        at org.gradle.process.internal.ExecHandleRunner.completed(ExecHandleRunner.java:110)
        at org.gradle.process.internal.ExecHandleRunner.run(ExecHandleRunner.java:84)
        at org.gradle.internal.operations.CurrentBuildOperationPreservingRunnable.run(CurrentBuildOperationPreservingRunnable.java:42)
        at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
        at org.gradle.internal.concurrent.ManagedExecutorImpl$1.run(ManagedExecutorImpl.java:48)
        at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
        at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
        at java.base/java.lang.Thread.run(Thread.java:840)
WARNING: A command line option has enabled the Security Manager
WARNING: The Security Manager is deprecated and will be removed in a future release
Error occurred during initialization of VM
java.lang.NoClassDefFoundError: kotlin/text/StringsKt
        at kotlinx.coroutines.TestSecurityManager.checkPropertyAccess(TestSecurityManager.kt:12)
        at java.lang.System.getProperty(java.base@17.0.9/System.java:915)
        at java.lang.ClassLoader.initSystemClassLoader(java.base@17.0.9/ClassLoader.java:1982)
        at java.lang.System.initPhase3(java.base@17.0.9/System.java:2246)
Caused by: java.lang.ClassNotFoundException: kotlin.text.StringsKt
        at kotlinx.coroutines.TestSecurityManager.checkPropertyAccess(TestSecurityManager.kt:12)
        at java.lang.System.getProperty(java.base@17.0.9/System.java:915)
        at java.lang.ClassLoader.initSystemClassLoader(java.base@17.0.9/ClassLoader.java:1982)
        at java.lang.System.initPhase3(java.base@17.0.9/System.java:2246)
```

2023-11-29
----------

A workaround for the wrong Maven publication metadata being created turned out
not to be needed: for some reason, the metadata was is already correct. I won't
question it. But when I removed the workaround, the build started failing in
another subproject that relied on `kotlinx-coroutines-debug`:
<https://github.com/Kotlin/kotlinx.coroutines/pull/3948/commits/0234c2923a93f5e797ff2f857051ce59e8861e4d>

Just doing `./gradlew clean :benchmarks:compileJmhKotlin` did not reproduce the
issue. These last two weeks were full of Gradle causing me a headache.

Just looking at the `benchmarks` module, I only see a presumably harmless
```kotlin
implementation(project(":kotlinx-coroutines-debug"))
```

Doing a `./gradlew clean build publishToMavenLocal`, the way it's done on
TeamCity, I *do* get the error during the `:benchmarks:compileJmhKotlin` task.
Magic!

```
Execution failed for task ':benchmarks:compileJmhKotlin'.
> Could not resolve all files for configuration ':benchmarks:detachedConfiguration3'.
   > Failed to transform kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar to match attributes {artifactType=classpath-entry-snapshot, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}.
      > Execution failed for ClasspathEntrySnapshotTransform: /home/dmitry.khalanskiy/IdeaProjects/kotlinx.coroutines/kotlinx-coroutines-debug/build/libs/kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar.
         > Check failed.
```

`Failed to transform` + `to match attributes` seems like a common query on
Google, I'll be able to find more information at the very least.

If I don't do a `clean` now and instead just `build publishToMavenLocal`, what
will happen?.. It fails, good. This means I'll be able at least to avoid
recompilation while I diagnose the problem.

Here's the monstrous stacktrace:

```
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':benchmarks:compileJmhKotlin'.
        at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:38)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
        at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
        at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:74)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:333)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:320)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:313)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:299)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.lambda$run$0(DefaultPlanExecutor.java:143)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:227)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.executeNextNode(DefaultPlanExecutor.java:218)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:140)
        at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
        at org.gradle.internal.concurrent.ManagedExecutorImpl$1.run(ManagedExecutorImpl.java:48)
Caused by: org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration$ArtifactResolveException: Could not resolve all files for configuration ':benchmarks:detachedConfiguration3'.
        at org.gradle.api.internal.artifacts.configurations.DefaultConfiguration.rethrowFailure(DefaultConfiguration.java:1503)
        at org.gradle.api.internal.artifacts.configurations.DefaultConfiguration.access$3700(DefaultConfiguration.java:159)
        at org.gradle.api.internal.artifacts.configurations.DefaultConfiguration$DefaultResolutionHost.rethrowFailure(DefaultConfiguration.java:2139)
        at org.gradle.api.internal.artifacts.configurations.DefaultConfiguration$ConfigurationFileCollection.visitContents(DefaultConfiguration.java:1475)
        at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:351)
        at org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection.lambda$calculateFinalizedValue$0(DefaultConfigurableFileCollection.java:241)
        at org.gradle.api.internal.file.collections.UnpackingVisitor.add(UnpackingVisitor.java:64)
        at org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection$UnresolvedItemsCollector.visitContents(DefaultConfigurableFileCollection.java:372)
        at org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection.calculateFinalizedValue(DefaultConfigurableFileCollection.java:241)
        at org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection.visitChildren(DefaultConfigurableFileCollection.java:277)
        at org.gradle.api.internal.file.CompositeFileCollection.visitContents(CompositeFileCollection.java:119)
        at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:351)
        at org.gradle.api.internal.file.CompositeFileCollection.lambda$visitContents$0(CompositeFileCollection.java:119)
        at org.gradle.api.internal.file.collections.UnpackingVisitor.add(UnpackingVisitor.java:64)
        at org.gradle.api.internal.file.collections.UnpackingVisitor.add(UnpackingVisitor.java:89)
        at org.gradle.api.internal.file.DefaultFileCollectionFactory$ResolvingFileCollection.visitChildren(DefaultFileCollectionFactory.java:333)
        at org.gradle.api.internal.file.CompositeFileCollection.visitContents(CompositeFileCollection.java:119)
        at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:351)
        at org.gradle.api.internal.file.CompositeFileCollection.lambda$visitContents$0(CompositeFileCollection.java:119)
        at org.gradle.api.internal.tasks.PropertyFileCollection.visitChildren(PropertyFileCollection.java:48)
        at org.gradle.api.internal.file.CompositeFileCollection.visitContents(CompositeFileCollection.java:119)
        at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:351)
        at org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter.snapshot(DefaultFileCollectionSnapshotter.java:51)
        at org.gradle.internal.execution.fingerprint.impl.DefaultInputFingerprinter$InputCollectingVisitor.visitInputFileProperty(DefaultInputFingerprinter.java:131)
        at org.gradle.api.internal.tasks.execution.TaskExecution.visitRegularInputs(TaskExecution.java:328)
        at org.gradle.internal.execution.fingerprint.impl.DefaultInputFingerprinter.fingerprintInputProperties(DefaultInputFingerprinter.java:61)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.captureExecutionStateWithOutputs(CaptureStateBeforeExecutionStep.java:193)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.lambda$captureExecutionState$1(CaptureStateBeforeExecutionStep.java:141)
        at org.gradle.internal.execution.steps.BuildOperationStep$1.call(BuildOperationStep.java:37)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
        at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
        at org.gradle.internal.execution.steps.BuildOperationStep.operation(BuildOperationStep.java:34)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.captureExecutionState(CaptureStateBeforeExecutionStep.java:130)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.lambda$execute$0(CaptureStateBeforeExecutionStep.java:75)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.execute(CaptureStateBeforeExecutionStep.java:75)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.execute(CaptureStateBeforeExecutionStep.java:50)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.executeWithNoEmptySources(SkipEmptyWorkStep.java:249)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.executeWithNoEmptySources(SkipEmptyWorkStep.java:204)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.execute(SkipEmptyWorkStep.java:83)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.execute(SkipEmptyWorkStep.java:54)
        at org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep.execute(RemoveUntrackedExecutionStateStep.java:32)
        at org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep.execute(RemoveUntrackedExecutionStateStep.java:21)
        at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
        at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:43)
        at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:31)
        at org.gradle.internal.execution.steps.AssignWorkspaceStep.lambda$execute$0(AssignWorkspaceStep.java:40)
        at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:287)
        at org.gradle.internal.execution.steps.AssignWorkspaceStep.execute(AssignWorkspaceStep.java:40)
        at org.gradle.internal.execution.steps.AssignWorkspaceStep.execute(AssignWorkspaceStep.java:30)
        at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:37)
        at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:27)
        at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:44)
        at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:33)
        at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:76)
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:144)
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:133)
        at org.gradle.api.internal.tasks.execution.CleanupStaleOutputsExecuter.execute(CleanupStaleOutputsExecuter.java:77)
        at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
        at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
        at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
        at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:56)
        at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
        at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
        at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:74)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:333)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:320)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:313)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:299)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.lambda$run$0(DefaultPlanExecutor.java:143)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:227)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.executeNextNode(DefaultPlanExecutor.java:218)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:140)
        at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
        at org.gradle.internal.concurrent.ManagedExecutorImpl$1.run(ManagedExecutorImpl.java:48)
Caused by: org.gradle.api.internal.artifacts.transform.TransformException: Failed to transform kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar to match attributes {artifactType=classpath-entry-snapshot, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}.
        at org.gradle.api.internal.artifacts.transform.TransformingAsyncArtifactListener$TransformedArtifact.lambda$visit$2(TransformingAsyncArtifactListener.java:232)
        at org.gradle.internal.Try$Failure.ifSuccessfulOrElse(Try.java:282)
        at org.gradle.api.internal.artifacts.transform.TransformingAsyncArtifactListener$TransformedArtifact.visit(TransformingAsyncArtifactListener.java:224)
        at org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ParallelResolveArtifactSet$VisitingSet$StartVisitAction.visitResults(ParallelResolveArtifactSet.java:100)
        at org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ParallelResolveArtifactSet$VisitingSet.visit(ParallelResolveArtifactSet.java:69)
        at org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration.visitArtifacts(DefaultLenientConfiguration.java:296)
        at org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration.access$500(DefaultLenientConfiguration.java:74)
        at org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration$3.run(DefaultLenientConfiguration.java:244)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:29)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:26)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:47)
        at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:68)
        at org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration.visitArtifactsWithBuildOperation(DefaultLenientConfiguration.java:241)
        at org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration.access$200(DefaultLenientConfiguration.java:74)
        at org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration$2.visitArtifacts(DefaultLenientConfiguration.java:144)
        at org.gradle.api.internal.artifacts.configurations.DefaultConfiguration$ConfigurationFileCollection.visitContents(DefaultConfiguration.java:1473)
        at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:351)
        at org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection.lambda$calculateFinalizedValue$0(DefaultConfigurableFileCollection.java:241)
        at org.gradle.api.internal.file.collections.UnpackingVisitor.add(UnpackingVisitor.java:64)
        at org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection$UnresolvedItemsCollector.visitContents(DefaultConfigurableFileCollection.java:372)
        at org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection.calculateFinalizedValue(DefaultConfigurableFileCollection.java:241)
        at org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection.visitChildren(DefaultConfigurableFileCollection.java:277)
        at org.gradle.api.internal.file.CompositeFileCollection.visitContents(CompositeFileCollection.java:119)
        at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:351)
        at org.gradle.api.internal.file.CompositeFileCollection.lambda$visitContents$0(CompositeFileCollection.java:119)
        at org.gradle.api.internal.file.collections.UnpackingVisitor.add(UnpackingVisitor.java:64)
        at org.gradle.api.internal.file.collections.UnpackingVisitor.add(UnpackingVisitor.java:89)
        at org.gradle.api.internal.file.DefaultFileCollectionFactory$ResolvingFileCollection.visitChildren(DefaultFileCollectionFactory.java:333)
        at org.gradle.api.internal.file.CompositeFileCollection.visitContents(CompositeFileCollection.java:119)
        at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:351)
        at org.gradle.api.internal.file.CompositeFileCollection.lambda$visitContents$0(CompositeFileCollection.java:119)
        at org.gradle.api.internal.tasks.PropertyFileCollection.visitChildren(PropertyFileCollection.java:48)
        at org.gradle.api.internal.file.CompositeFileCollection.visitContents(CompositeFileCollection.java:119)
        at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:351)
        at org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter.snapshot(DefaultFileCollectionSnapshotter.java:51)
        at org.gradle.internal.execution.fingerprint.impl.DefaultInputFingerprinter$InputCollectingVisitor.visitInputFileProperty(DefaultInputFingerprinter.java:131)
        at org.gradle.api.internal.tasks.execution.TaskExecution.visitRegularInputs(TaskExecution.java:328)
        at org.gradle.internal.execution.fingerprint.impl.DefaultInputFingerprinter.fingerprintInputProperties(DefaultInputFingerprinter.java:61)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.captureExecutionStateWithOutputs(CaptureStateBeforeExecutionStep.java:193)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.lambda$captureExecutionState$1(CaptureStateBeforeExecutionStep.java:141)
        at org.gradle.internal.execution.steps.BuildOperationStep$1.call(BuildOperationStep.java:37)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
        at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
        at org.gradle.internal.execution.steps.BuildOperationStep.operation(BuildOperationStep.java:34)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.captureExecutionState(CaptureStateBeforeExecutionStep.java:130)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.lambda$execute$0(CaptureStateBeforeExecutionStep.java:75)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.execute(CaptureStateBeforeExecutionStep.java:75)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.execute(CaptureStateBeforeExecutionStep.java:50)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.executeWithNoEmptySources(SkipEmptyWorkStep.java:249)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.executeWithNoEmptySources(SkipEmptyWorkStep.java:204)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.execute(SkipEmptyWorkStep.java:83)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.execute(SkipEmptyWorkStep.java:54)
        at org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep.execute(RemoveUntrackedExecutionStateStep.java:32)
        at org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep.execute(RemoveUntrackedExecutionStateStep.java:21)
        at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
        at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:43)
        at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:31)
        at org.gradle.internal.execution.steps.AssignWorkspaceStep.lambda$execute$0(AssignWorkspaceStep.java:40)
        at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:287)
        at org.gradle.internal.execution.steps.AssignWorkspaceStep.execute(AssignWorkspaceStep.java:40)
        at org.gradle.internal.execution.steps.AssignWorkspaceStep.execute(AssignWorkspaceStep.java:30)
        at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:37)
        at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:27)
        at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:44)
        at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:33)
        at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:76)
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:144)
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:133)
        at org.gradle.api.internal.tasks.execution.CleanupStaleOutputsExecuter.execute(CleanupStaleOutputsExecuter.java:77)
        at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
        at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
        at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
        at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:56)
        at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
        at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
        at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
        at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:74)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:333)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:320)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:313)
        at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:299)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.lambda$run$0(DefaultPlanExecutor.java:143)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:227)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.executeNextNode(DefaultPlanExecutor.java:218)
        at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:140)
        at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
        at org.gradle.internal.concurrent.ManagedExecutorImpl$1.run(ManagedExecutorImpl.java:48)
Caused by: org.gradle.api.internal.artifacts.transform.TransformException: Execution failed for ClasspathEntrySnapshotTransform: /home/dmitry.khalanskiy/IdeaProjects/kotlinx.coroutines/kotlinx-coroutines-debug/build/libs/kotlinx-coroutines-debug-1.7.2-SNAPSHOT.jar.
        at org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory$1.lambda$mapResult$3(DefaultTransformerInvocationFactory.java:160)
        at org.gradle.internal.Try$Failure.mapFailure(Try.java:273)
        at org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory$1.mapResult(DefaultTransformerInvocationFactory.java:160)
        at org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory$1.lambda$processDeferredOutput$0(DefaultTransformerInvocationFactory.java:153)
        at org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory.fireTransformListeners(DefaultTransformerInvocationFactory.java:185)
        at org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory.access$000(DefaultTransformerInvocationFactory.java:70)
        at org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory$1.lambda$processDeferredOutput$1(DefaultTransformerInvocationFactory.java:152)
        at org.gradle.api.internal.artifacts.transform.CacheableInvocation$3.invoke(CacheableInvocation.java:110)
        at org.gradle.api.internal.artifacts.transform.CacheableInvocation$1.invoke(CacheableInvocation.java:58)
        at org.gradle.api.internal.artifacts.transform.TransformingAsyncArtifactListener$TransformedArtifact.finalizeValue(TransformingAsyncArtifactListener.java:203)
        at org.gradle.api.internal.artifacts.transform.TransformingAsyncArtifactListener$TransformedArtifact.run(TransformingAsyncArtifactListener.java:136)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationExecutor$QueueWorker.execute(DefaultBuildOperationExecutor.java:238)
        at org.gradle.internal.operations.DefaultBuildOperationQueue$WorkerRunnable.runOperation(DefaultBuildOperationQueue.java:266)
        at org.gradle.internal.operations.DefaultBuildOperationQueue$WorkerRunnable.doRunBatch(DefaultBuildOperationQueue.java:247)
        at org.gradle.internal.operations.DefaultBuildOperationQueue$WorkerRunnable.lambda$runBatch$0(DefaultBuildOperationQueue.java:237)
        at org.gradle.internal.resources.AbstractResourceLockRegistry.whileDisallowingLockChanges(AbstractResourceLockRegistry.java:69)
        at org.gradle.internal.work.DefaultWorkerLeaseService.whileDisallowingProjectLockChanges(DefaultWorkerLeaseService.java:242)
        at org.gradle.internal.operations.DefaultBuildOperationQueue$WorkerRunnable.lambda$runBatch$1(DefaultBuildOperationQueue.java:237)
        at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:270)
        at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:119)
        at org.gradle.internal.operations.DefaultBuildOperationQueue$WorkerRunnable.runBatch(DefaultBuildOperationQueue.java:223)
        at org.gradle.internal.operations.DefaultBuildOperationQueue$WorkerRunnable.run(DefaultBuildOperationQueue.java:191)
        ... 2 more
Caused by: java.lang.IllegalStateException: Check failed.
        at org.jetbrains.kotlin.incremental.classpathDiff.DirectoryOrJarReader$Companion.create(ClasspathSnapshotter.kt:234)
        at org.jetbrains.kotlin.incremental.classpathDiff.ClasspathEntrySnapshotter.snapshot(ClasspathSnapshotter.kt:47)
        at org.jetbrains.kotlin.gradle.internal.transforms.ClasspathEntrySnapshotTransform.doTransform(ClasspathEntrySnapshotTransform.kt:90)
        at org.jetbrains.kotlin.gradle.internal.transforms.ClasspathEntrySnapshotTransform.transform(ClasspathEntrySnapshotTransform.kt:53)
        at org.gradle.api.internal.artifacts.transform.DefaultTransformer.transform(DefaultTransformer.java:264)
        at org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory$AbstractTransformerExecution$1.call(DefaultTransformerInvocationFactory.java:296)
        at org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory$AbstractTransformerExecution$1.call(DefaultTransformerInvocationFactory.java:291)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
        at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
        at org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory$AbstractTransformerExecution.execute(DefaultTransformerInvocationFactory.java:291)
        at org.gradle.internal.execution.steps.ExecuteStep.executeInternal(ExecuteStep.java:89)
        at org.gradle.internal.execution.steps.ExecuteStep.access$000(ExecuteStep.java:40)
        at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:53)
        at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:50)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
        at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
        at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
        at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
        at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:50)
        at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:40)
        at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:68)
        at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:38)
        at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:48)
        at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:36)
        at org.gradle.internal.execution.steps.CancelExecutionStep.execute(CancelExecutionStep.java:41)
        at org.gradle.internal.execution.steps.TimeoutStep.executeWithoutTimeout(TimeoutStep.java:74)
        at org.gradle.internal.execution.steps.TimeoutStep.execute(TimeoutStep.java:55)
        at org.gradle.internal.execution.steps.CreateOutputsStep.execute(CreateOutputsStep.java:51)
        at org.gradle.internal.execution.steps.CreateOutputsStep.execute(CreateOutputsStep.java:29)
        at org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep.execute(CaptureStateAfterExecutionStep.java:61)
        at org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep.execute(CaptureStateAfterExecutionStep.java:42)
        at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:60)
        at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:27)
        at org.gradle.internal.execution.steps.BuildCacheStep.executeWithoutCache(BuildCacheStep.java:180)
        at org.gradle.internal.execution.steps.BuildCacheStep.lambda$execute$1(BuildCacheStep.java:75)
        at org.gradle.internal.Either$Right.fold(Either.java:175)
        at org.gradle.internal.execution.caching.CachingState.fold(CachingState.java:59)
        at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:73)
        at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:48)
        at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:36)
        at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:25)
        at org.gradle.internal.execution.steps.RecordOutputsStep.execute(RecordOutputsStep.java:36)
        at org.gradle.internal.execution.steps.RecordOutputsStep.execute(RecordOutputsStep.java:22)
        at org.gradle.internal.execution.steps.SkipUpToDateStep.executeBecause(SkipUpToDateStep.java:110)
        at org.gradle.internal.execution.steps.SkipUpToDateStep.lambda$execute$2(SkipUpToDateStep.java:56)
        at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:56)
        at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:38)
        at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:73)
        at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:44)
        at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:37)
        at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:27)
        at org.gradle.internal.execution.steps.ResolveCachingStateStep.execute(ResolveCachingStateStep.java:89)
        at org.gradle.internal.execution.steps.ResolveCachingStateStep.execute(ResolveCachingStateStep.java:50)
        at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:114)
        at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:57)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.execute(CaptureStateBeforeExecutionStep.java:76)
        at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.execute(CaptureStateBeforeExecutionStep.java:50)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.executeWithNoEmptySources(SkipEmptyWorkStep.java:249)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.execute(SkipEmptyWorkStep.java:86)
        at org.gradle.internal.execution.steps.SkipEmptyWorkStep.execute(SkipEmptyWorkStep.java:54)
        at org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep.execute(RemoveUntrackedExecutionStateStep.java:32)
        at org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep.execute(RemoveUntrackedExecutionStateStep.java:21)
        at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
        at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:43)
        at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:31)
        at org.gradle.internal.execution.steps.AssignWorkspaceStep.lambda$execute$0(AssignWorkspaceStep.java:40)
        at org.gradle.internal.execution.workspace.impl.DefaultImmutableWorkspaceProvider.lambda$withWorkspace$3(DefaultImmutableWorkspaceProvider.java:124)
        at org.gradle.cache.internal.LockOnDemandCrossProcessCacheAccess.withFileLock(LockOnDemandCrossProcessCacheAccess.java:90)
        at org.gradle.cache.internal.DefaultCacheAccess.withFileLock(DefaultCacheAccess.java:191)
        at org.gradle.cache.internal.DefaultPersistentDirectoryStore.withFileLock(DefaultPersistentDirectoryStore.java:188)
        at org.gradle.cache.internal.DefaultCacheFactory$ReferenceTrackingCache.withFileLock(DefaultCacheFactory.java:209)
        at org.gradle.internal.execution.workspace.impl.DefaultImmutableWorkspaceProvider.withWorkspace(DefaultImmutableWorkspaceProvider.java:121)
        at org.gradle.internal.execution.steps.AssignWorkspaceStep.execute(AssignWorkspaceStep.java:40)
        at org.gradle.internal.execution.steps.AssignWorkspaceStep.execute(AssignWorkspaceStep.java:30)
        at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:37)
        at org.gradle.internal.execution.steps.IdentityCacheStep.lambda$executeDeferred$0(IdentityCacheStep.java:49)
        at org.gradle.cache.Cache.lambda$get$0(Cache.java:31)
        at org.gradle.cache.ManualEvictionInMemoryCache.get(ManualEvictionInMemoryCache.java:30)
        at org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory$CrossBuildCacheRetainingDataFromPreviousBuild.get(DefaultCrossBuildInMemoryCacheFactory.java:255)
        at org.gradle.cache.Cache.get(Cache.java:31)
        at org.gradle.internal.execution.steps.IdentityCacheStep.lambda$executeDeferred$1(IdentityCacheStep.java:47)
        ... 25 more
```

Unfortunately, I don't think many people encounter *this* particular problem.
See, `Check failed.` is an indication that there are some broken assumptions on
the Gradle's side and not just some thing I did wrong. Usually, people do funky
stuff with their jar files, which is why some plugins can't understand them,
but here, without an error message, what can I even rely on?

The closest I find is
<https://youtrack.jetbrains.com/issue/KT-44893/Kapt-Crash-if-some-dependencies-are-not-exists.>,
where the root cause is the same:
`org.jetbrains.kotlin.incremental.classpathDiff.ClasspathEntrySnapshotter.snapshot`.
JetBrains is where I work, so maybe I can get priority support from my
colleagues. Or maybe I can upgrade something to a new version (and get a
different error message).

Searching for the name of this function, I get the advice to add this line to
`gradle.properties`:
```
kotlin.incremental.useClasspathSnapshot=false
```
The advice is coming from
<https://youtrack.jetbrains.com/issue/KT-62101/IC-Execution-failed-for-ClasspathEntrySnapshotTransform-when-using-tools.jar-as-dependency>

Well... It does seem to help, but let's consider other options.

Where is this `DirectoryOrJarReader` class even? According to the Github Search,
it's in the main `kotlin` repository, but in `master`, I don't see such a thing.
I do see the file that contains it: or used to contain it.

Hey, maybe I just need to update the Kotlin version? It does look like my branch
didn't get rebased in a long time... In particular, it doesn't contain the
change to 1.9.21.

The work involving Gradle is just *so* **meanial**! When I touch Gradle, I feel
like a student tasked with setting up Oracle databases. You read instructions,
they are inaccurate, you read some blog posts by paid contractors, their advice
works better but still not always, you mash together various combinations of
config options without rhyme or reason, because there's no rhyme or reason in
the underlying systems: they are just *incomprehensible*, the mental model
behind them is exactly the code that comprises them. It's so antithetical to
what I consider good and proper that I'm losing any sparks of joy that
occasionally surface.

Of course, in this case, the culprit is not Gradle but my own colleagues. Let's
be fair: Gradle does not have a monopoly on giving you headaches.

Why am I getting a bunch of warnings as I test this, even though we supposedly
enabled "warnings are treated as errors" in the coroutines project?

Ah: warnings are only checked for the JVM and the common source sets, it seems.
Let me fix this quickly:
<https://github.com/Kotlin/kotlinx.coroutines/pull/3957>

Ok, back to work. No, 1.9.21 didn't fix the problem: the build still fails
<https://github.com/Kotlin/kotlinx.coroutines/pull/3948/commits/d5e8cbb773a5b570c3ee7b1b5bad9529cecbd933>,
though with a slightly different stacktrace.

An interesting Gradle warning went past me:

```
Execution optimizations have been disabled for task ':kotlinx-coroutines-core:dokkaHtmlPartial' to ensure correctness due to the following reasons:
  - Gradle detected a problem with the following location: '/home/dmitry.khalanskiy/IdeaProjects/kotlinx.coroutines/kotlinx-coroutines-core/build/libs/kotlinx-coroutines-core-jvm-1.7.2-SNAPSHOT.jar'. Reason: Task ':kotlinx-coroutines-core:dokkaHtmlPartial' uses this output of task ':kotlinx-coroutines-core:jvmJar' without declaring an explicit or implicit dependency. This can lead to incorrect results being produced, depending on what order the tasks are executed. Please refer to https://docs.gradle.org/7.4.2/userguide/validation_problems.html#implicit_dependency for more details about this problem.
```

Fun, but maybe `dokkaHtmlPartial` actually behaves correctly here and depends on
the result of the `shadowJar` task, except Gradle is confused about it.

If only the shadow plugin provided a way to access its shadowing functionality
piecewise, like this:

```kotlin
val shadowing by tasks.register<Shadow> {
    // configure how to shade the classes
}

jar {
    from(shadowing)
    // do something else with the classpath and metadata
}
```

Maybe I could try to invert the situation? For example, re-enable the `jar` task
but only tear out parts of `shadowJar`?

I don't think I'm good enough at Gradle for that. Hopefully, my colleagues who
put this `Check failed` in their code will help me.

Meanwhile, let's do the final task that has a deadline for this week: the
project board for `kotlinx-datetime`.

How do I just add every *open* issue/PR to the Github project? So far, I can't
see a way to filter the issues, only add all of them 25 items at a time and
then cross out the ones we don't need.

<https://docs.github.com/en/issues/planning-and-tracking-with-projects/managing-items-in-your-project/adding-items-to-your-project#adding-multiple-issues-or-pull-requests-from-a-repository>

Oh, you're supposed to do this from inside the repo.

2023-11-30
----------

<https://github.com/Kotlin/kotlinx-datetime/pull/315> finally has the full list
of the JS API endpoints that can throw, and I'm going through each API endpoint
one after another to make sure it won't throw an exception.

```kotlin
public actual open class JodaTimeTemporalUnit(open val value: TemporalUnit)
public actual open class JodaTimeTemporalAmount
public actual open class JodaTimeChronoLocalDate(override val value: ChronoLocalDate) : JodaTimeTemporal(value)
public actual open class JodaTimeTemporalAccessor(open val value: TemporalAccessor)
public actual open class JodaTimeChronoLocalDateTime(override val value: ChronoLocalDateTime) : JodaTimeTemporal(value) {
    actual open fun toInstant(offset: JodaTimeZoneOffset): JodaTimeInstant = JodaTimeInstant(value.toInstant(offset.value))
}
public actual open class JodaTimeChronoZonedDateTime(override val value: ChronoZonedDateTime) : JodaTimeTemporal(value) {
    actual fun toInstant(): JodaTimeInstant = JodaTimeInstant(value.toInstant())
}
```

The range of `Instant` is one year wider than that of `LocalDateTime`, so it's
a valid transformation.

```kotlin
public actual open class JodaTimeTemporal(override val value: Temporal) : JodaTimeTemporalAccessor(value) {
    actual open fun until(endTemporal: JodaTimeTemporal, unit: JodaTimeTemporalUnit): Double =
        value.until(endTemporal.value, unit.value)
}
```

`until` is an abstract method, used in `LocalDate` and `ZonedDateTime`.
Let's look at <https://github.com/js-joda/js-joda/tree/c635147087af60f35b964ba3f327715436e4ba05/packages/core/src>,
`LocalDate.js` and `ZonedDateTime.js`.

```javascript
daysUntil(end) {
    return end.toEpochDay() - this.toEpochDay();  // no overflow
}
```

Ok, no overflow it is then.

In `ZonedDateTime`, though...

```javascript
until(endExclusive, unit) {
    let end = ZonedDateTime.from(endExclusive);
    if (unit instanceof ChronoUnit) {
        end = end.withZoneSameInstant(this._zone);
        if (unit.isDateBased()) {
            return this._dateTime.until(end._dateTime, unit);
        } else {
            const difference = this._offset.totalSeconds() - end._offset.totalSeconds();
            const adjustedEnd = end._dateTime.plusSeconds(difference);
            return this._dateTime.until(adjustedEnd, unit);
        }
    }
    return unit.between(this, end);
}
```

There's a tiny problem: `plusSeconds`. If one `ZonedDateTime` is at the limits
of what `LocalDateTime` can represent, adjusting it to have the same offset as
the original `ZonedDateTime` will lead to an overflow.

Luckily, this doesn't happen in our codebase, so I just drop a comment.

Then, `ZonedDateTime` delegates to `LocalDateTime`'s `until` implementation.
Let's look at it...

There are calls to `endDate.minusDays` and `endDate.plusDays`, but only in the
case when it's known that another date is even further from the reference date,
so this can't overflow. There's a lot of `safeMultiply` and `safeAdd`, but they
only receive non-zero values when the `LocalDateTime` values are far apart and
time-based difference is queried, whereas we always make sure that the
time-based difference is only calculated when there isn't a whole day between
the date-times. So, we're all clear.

```kotlin
public actual open class JodaTimeChronoUnit(override val value: ChronoUnit) : JodaTimeTemporalUnit(value) {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeChronoUnit && value === other.value)
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()

    actual companion object {
        actual var NANOS: JodaTimeChronoUnit = JodaTimeChronoUnit(ChronoUnit.NANOS)
        actual var DAYS: JodaTimeChronoUnit = JodaTimeChronoUnit(ChronoUnit.DAYS)
        actual var MONTHS: JodaTimeChronoUnit = JodaTimeChronoUnit(ChronoUnit.MONTHS)
        actual var YEARS: JodaTimeChronoUnit = JodaTimeChronoUnit(ChronoUnit.YEARS)
    }
}
```

Nothing can throw here, I hope.

```kotlin
public actual open class JodaTimeClock(val value: Clock)  {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeClock && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun instant(): JodaTimeInstant = JodaTimeInstant(value.instant())

    actual companion object {
        actual fun systemUTC(): JodaTimeClock = JodaTimeClock(Clock.systemUTC())
    }
}
```

Looking at the code, constructing `Clock.systemUTC()` is trivial, and
`instant()`... Well, it can throw if the value of `Date().getTime()` is
ridiculously large. I won't risk setting my computer to such a large time to
experiment, though, as I'm not sure I'll be able to turn it back on without
some very heavy tinkering. Let's just assume that whoever has their clock so
far off is ready for the consequences.

```kotlin
public actual open class JodaTimeDuration(val value: Duration) : JodaTimeTemporalAmount() {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeDuration && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun nano(): Double = value.nano()
    actual fun seconds(): Double = value.seconds()

    actual companion object {
        actual fun between(startInclusive: JodaTimeTemporal, endExclusive: JodaTimeTemporal): JodaTimeDuration =
            JodaTimeDuration(Duration.between(startInclusive.value, endExclusive.value))
    }
}
```

This `between` bothers me. It's only used in
```kotlin
public actual operator fun minus(other: Instant): Duration {
    val diff = jtDuration.between(other.value, this.value)
    return diff.seconds().seconds + diff.nano().nanoseconds
}
```

Its implementation is:

```kotlin
static between(startInclusive, endExclusive) {
    requireNonNull(startInclusive, 'startInclusive');
    requireNonNull(endExclusive, 'endExclusive');
    let secs = startInclusive.until(endExclusive, ChronoUnit.SECONDS);
    let nanos = 0;
        if (startInclusive.isSupported(ChronoField.NANO_OF_SECOND) && endExclusive.isSupported(ChronoField.NANO_OF_SECOND)) {
        try {
                const startNos = startInclusive.getLong(ChronoField.NANO_OF_SECOND);
                nanos = endExclusive.getLong(ChronoField.NANO_OF_SECOND) - startNos;
            if (secs > 0 && nanos < 0) {
                nanos += LocalTime.NANOS_PER_SECOND;
            } else if (secs < 0 && nanos > 0) {
                nanos -= LocalTime.NANOS_PER_SECOND;
            } else if (secs === 0 && nanos !== 0) {
                // two possible meanings for result, so recalculate secs
                    const adjustedEnd = endExclusive.with(ChronoField.NANO_OF_SECOND, startNos);
                    secs = startInclusive.until(adjustedEnd, ChronoUnit.SECONDS);
            }
        } catch (e) {
            // ignore and only use seconds
        }
    }
    return this.ofSeconds(secs, nanos);
}
```

If something fails when equiring nanoseconds, they are ignored. I won't bother
to check what *can* happen: I'm only interested in exceptions flying out.

`ofSeconds` is:

```kotlin
static ofSeconds(seconds, nanoAdjustment = 0) {
    const secs = MathUtil.safeAdd(seconds, MathUtil.floorDiv(nanoAdjustment, LocalTime.NANOS_PER_SECOND));
    const nos = MathUtil.floorMod(nanoAdjustment, LocalTime.NANOS_PER_SECOND);
    return Duration._create(secs, nos);
}
```

and crucially,

```kotlin
constructor(seconds, nanos) {
    super();
    this._seconds = MathUtil.safeToInt(seconds);
    this._nanos = MathUtil.safeToInt(nanos);
}
```

I'm copying so much code that I'm starting to worry about licensing. The license
is BSD. Well, rules are rules, I have to include it:

```
BSD License

For js-joda software

Copyright (c) 2016, Philipp Thürwächter & Pattrick Hüper
 
All rights reserved.
 
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
 
 * Redistributions of source code must retain the above copyright notice,
   this list of conditions and the following disclaimer.
 
 * Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
 
 * Neither the name of js-joda nor the names of its contributors
   may be used to endorse or promote products derived from this software
   without specific prior written permission.
 
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

You got all this? Good. Let's proceed.

Turns out, at most `Int.MAX_VALUE` seconds can be stored in this `Duration`, or
a crash will occur. This is clearly achievable, right?

```javascript
    Instant.MIN_SECONDS = -31619119219200; // -1000000-01-01T00:00:00Z          
    Instant.MAX_SECONDS = 31494816403199; // +1000000-12-31T23:59:59.999999999Z 
```

Even just `Instant.MAX_SECONDS` is larger than an `Int`! Clearly a bug on our
hands, right? Let's double-check.

```kotlin
@Test
fun subtractInstants() {
    val max = Instant.fromEpochSeconds(31494816403199L)
    val min = Instant.fromEpochSeconds(-31619119219200L)
    assertEquals(max.epochSeconds - min.epochSeconds, (max - min).inWholeSeconds)
}
```

No, this passes just fine, and I checked, the `assertEquals` *does* execute.

What if... Oh.

```javascript
static verifyInt(value){
    if (value == null) {
        throw new ArithmeticException(`Invalid value: '${value}', using null or undefined as argument`);
    }
    if (isNaN(value)) {
        throw new ArithmeticException('Invalid int value, using NaN as argument');
    }
    if ((value % 1) !== 0) {
        throw new ArithmeticException(`Invalid value: '${value}' is a float`);
    }
    if (value > MAX_SAFE_INTEGER || value < MIN_SAFE_INTEGER) {
        throw new ArithmeticException(`Calculation overflows an int: ${value}`);
    }
}

export const MAX_SAFE_INTEGER = 9007199254740991;
export const MIN_SAFE_INTEGER = -9007199254740991;
```

So, it not an `Int` but a JS-`Int`.

```
31494816403199
9007199254740991
```

Look at these two numbers. The one above is so tiny that I could subtract and
add them all day, and the bottom one would never be reached.

Clearly, this is safe. Moving on.

```kotlin
public actual open class JodaTimeInstant(override val value: Instant) : JodaTimeTemporal(value) {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeInstant && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun atZone(zone: JodaTimeZoneId): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(jsTry { value.atZone(zone.value) })
    actual fun compareTo(otherInstant: JodaTimeInstant): Int = value.compareTo(otherInstant.value)
    actual fun epochSecond(): Double = value.epochSecond()
    actual fun nano(): Double = value.nano()

    actual companion object {
        actual var MIN: JodaTimeInstant = JodaTimeInstant(Instant.MIN)
        actual var MAX: JodaTimeInstant = JodaTimeInstant(Instant.MAX)
        actual fun ofEpochSecond(epochSecond: Double, nanoAdjustment: Int): JodaTimeInstant =
            JodaTimeInstant(jsTry { Instant.ofEpochSecond(epochSecond, nanoAdjustment) })
    }
}
```

Everything suspicious is properly guarded.

```kotlin
public actual open class JodaTimeLocalDate(override val value: LocalDate) : JodaTimeChronoLocalDate(value) {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeLocalDate && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun atStartOfDay(zone: JodaTimeZoneId): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(value.atStartOfDay(zone.value))
    actual fun compareTo(other: JodaTimeLocalDate): Int = value.compareTo(other.value)
    actual fun dayOfMonth(): Int = value.dayOfMonth()
    actual fun dayOfWeek(): JodaTimeDayOfWeek = JodaTimeDayOfWeek(value.dayOfWeek())
    actual fun dayOfYear(): Int = value.dayOfYear()
    actual fun month(): JodaTimeMonth = JodaTimeMonth(value.month())
    actual fun monthValue(): Int = value.monthValue()
    actual fun plusDays(daysToAdd: Int): JodaTimeLocalDate =
        JodaTimeLocalDate(jsTry { value.plusDays(daysToAdd) })
    actual fun plusMonths(monthsToAdd: Int): JodaTimeLocalDate =
        JodaTimeLocalDate(jsTry { value.plusMonths(monthsToAdd) })
    actual fun toEpochDay(): Double = value.toEpochDay()
    actual fun year(): Int = value.year()

    actual companion object {
        actual var MIN: JodaTimeLocalDate = JodaTimeLocalDate(LocalDate.MIN)
        actual var MAX: JodaTimeLocalDate = JodaTimeLocalDate(LocalDate.MAX)
        actual fun of(year: Int, month: Int, dayOfMonth: Int): JodaTimeLocalDate =
            JodaTimeLocalDate(jsTry { LocalDate.of(year, month, dayOfMonth) })
        actual fun ofEpochDay(epochDay: Int):  JodaTimeLocalDate =
            JodaTimeLocalDate(jsTry { LocalDate.ofEpochDay(epochDay) })
        actual fun parse(text: String): JodaTimeLocalDate =
            JodaTimeLocalDate(jsTry { LocalDate.parse(text) })
    }
}
```

Can `atStartOfDay` fail?.. Not realistically, no. Looking at the JsJoda code,
I also don't find anything suspicious. Except maybe if the timezone handling in
JsJoda is broken and passing too huge a moment causes it to fail. But we have
tests for that.

Other than that, everything's properly guarded.

```kotlin
public actual open class JodaTimeLocalDateTime(override val value: LocalDateTime) : JodaTimeChronoLocalDateTime(value) {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeLocalDateTime && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()

    actual fun atZone(zone: JodaTimeZoneId): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(value.atZone(zone.value))
    actual fun compareTo(other: JodaTimeLocalDateTime): Int = value.compareTo(other.value)
    actual fun dayOfMonth(): Int = value.dayOfMonth()
    actual fun dayOfWeek(): JodaTimeDayOfWeek = JodaTimeDayOfWeek(value.dayOfWeek())
    actual fun dayOfYear(): Int = value.dayOfYear()
    actual fun hour(): Int = value.hour()
    actual fun minute(): Int = value.minute()
    actual fun month(): JodaTimeMonth = JodaTimeMonth(value.month())
    actual fun monthValue(): Int = value.monthValue()
    actual fun nano(): Double = value.nano()
    actual fun second(): Int = value.second()
    actual fun toLocalDate(): JodaTimeLocalDate = JodaTimeLocalDate(value.toLocalDate())
    actual fun toLocalTime(): JodaTimeLocalTime = JodaTimeLocalTime(value.toLocalTime())
    actual fun year(): Int = value.year()

    actual companion object {
        actual var MIN: JodaTimeLocalDateTime = JodaTimeLocalDateTime(LocalDateTime.MIN)
        actual var MAX: JodaTimeLocalDateTime = JodaTimeLocalDateTime(LocalDateTime.MAX)
        actual fun of(date: JodaTimeLocalDate, time: JodaTimeLocalTime): JodaTimeLocalDateTime =
            JodaTimeLocalDateTime(jsTry { LocalDateTime.of(date.value, time.value) })
        actual fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int, nanoSecond: Int): JodaTimeLocalDateTime =
            JodaTimeLocalDateTime(jsTry { LocalDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoSecond) })
        actual fun ofInstant(instant: JodaTimeInstant, zoneId: JodaTimeZoneId): JodaTimeLocalDateTime =
            JodaTimeLocalDateTime(jsTry { LocalDateTime.ofInstant(instant.value, zoneId.value) })
        actual fun parse(text: String): JodaTimeLocalDateTime =
            JodaTimeLocalDateTime(jsTry { LocalDateTime.parse(text) })
    }
}
```

Oh, this one's huge. Everythin in the companion object properly has the
guardrails (and for each case, there doesn't seem to be a way around it).

For accessors, no special actions are needed.

`atZone` looks suspicious. Can `ZonedDateTime` construction fail?..
In theory, it can. If given a `LocalDateTime.MAX` and `LocalDateTime.MAX` is in
a timezone gap, it will be adjusted to something farther away, throwing an
exception. Is `+999999999-12-31T23:59:59.999999999` ever in a gap?

**Well**, if you're on Apple's hardware and access the `Africa/Casablanca`
timezone with the default zone rules, then yes, as our regular readers know!

Though it looks like JsJoda provides a fixed set of transitions, if we browse
the `MomentZoneRules.js` file. So, even theoretically, it doesn't seem like
this could cause an issue.

... Ok, let's check it.

```kotlin
    @Test
    fun xxx() {
        val zone = TimeZone.of("Europe/Berlin")
        for (year in 2020..100_000) {
            val standardTime = LocalDateTime(year, 1, 1, 0, 0, 0, 0)
            val summerTime = LocalDateTime(year, 7, 1, 0, 0, 0, 0)
            val standardOffset = zone.offsetAt(standardTime.toInstant(zone))
            val summerOffset = zone.offsetAt(summerTime.toInstant(zone))
            assertNotEquals(standardOffset, summerOffset, "same offsets for year $year")
        }
    }
```

This passes on JVM, but on JS, it fails for the year 2038! Not a lot of leeway!
At least I'm confident that there are no timezone transitions after that, so
that's a relief.

```kotlin
public actual open class JodaTimeLocalTime(override val value: LocalTime) : JodaTimeTemporal(value) {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeLocalTime && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun compareTo(other: JodaTimeLocalTime): Int = value.compareTo(other.value)
    actual fun hour(): Int = value.hour()
    actual fun minute(): Int = value.minute()
    actual fun nano(): Double = value.nano()
    actual fun second(): Int = value.second()
    actual fun toNanoOfDay(): Double = value.toNanoOfDay()
    actual fun toSecondOfDay(): Int = value.toSecondOfDay()

    actual companion object {
        actual var MIN: JodaTimeLocalTime = JodaTimeLocalTime(LocalTime.MIN)
        actual var MAX: JodaTimeLocalTime = JodaTimeLocalTime(LocalTime.MAX)
        actual fun of(hour: Int, minute: Int, second: Int, nanoOfSecond: Int): JodaTimeLocalTime =
            JodaTimeLocalTime(jsTry { LocalTime.of(hour, minute, second, nanoOfSecond) })
        actual fun ofNanoOfDay(nanoOfDay: Double): JodaTimeLocalTime =
            JodaTimeLocalTime(jsTry { LocalTime.ofNanoOfDay(nanoOfDay) })
        actual fun ofSecondOfDay(secondOfDay: Int, nanoOfSecond: Int): JodaTimeLocalTime =
            JodaTimeLocalTime(jsTry { LocalTime.ofSecondOfDay(secondOfDay, nanoOfSecond) })
        actual fun parse(text: String): JodaTimeLocalTime =
            JodaTimeLocalTime(jsTry { LocalTime.parse(text) })
    }
}
```

Completely straightforward.

```kotlin
public actual open class JodaTimeOffsetDateTime(override val value: OffsetDateTime) : JodaTimeTemporal(value) {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeOffsetDateTime && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun toInstant(): JodaTimeInstant = JodaTimeInstant(value.toInstant())

    actual companion object {
        actual fun ofInstant(instant: JodaTimeInstant, zone: JodaTimeZoneId): JodaTimeOffsetDateTime =
            JodaTimeOffsetDateTime(OffsetDateTime.ofInstant(instant.value, zone.value))
        actual fun parse(text: String): JodaTimeOffsetDateTime =
            JodaTimeOffsetDateTime(jsTry { OffsetDateTime.parse(text) })
    }
}
```

Not straightforward. Why do we even need this?.. Ah, `toString`. Sure. Well,
`toString` works properly for `Instant.MAX` and `Instant.MIN`, so everything's
alright.

```kotlin
public actual open class JodaTimeZonedDateTime(override val value: ZonedDateTime) : JodaTimeChronoZonedDateTime(value) {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeZonedDateTime && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun plusDays(days: Int): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(jsTry { value.plusDays(days) })
    actual fun plusDays(days: Double): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(jsTry { value.plusDays(days) })
    actual fun plusHours(hours: Int): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(jsTry { value.plusHours(hours) })
    actual fun plusMinutes(minutes: Int): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(jsTry { value.plusMinutes(minutes) })
    actual fun plusMonths(months: Int): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(jsTry { value.plusMonths(months) })
    actual fun plusMonths(months: Double): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(jsTry { value.plusMonths(months) })
    actual fun plusNanos(nanos: Double): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(jsTry { value.plusNanos(nanos) })
    actual fun plusSeconds(seconds: Int): JodaTimeZonedDateTime =
        JodaTimeZonedDateTime(jsTry { value.plusSeconds(seconds) })
}
```

Ok.

```kotlin
public actual open class JodaTimeZoneId(open val value: ZoneId)  {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeZoneId && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun id(): String = value.id()
    actual fun normalized(): JodaTimeZoneId = JodaTimeZoneId(value.normalized())
    actual fun rules(): JodaTimeZoneRules = JodaTimeZoneRules(value.rules())

    actual companion object {
        actual fun systemDefault(): JodaTimeZoneId = JodaTimeZoneId(ZoneId.systemDefault())
        actual fun of(zoneId: String): JodaTimeZoneId = JodaTimeZoneId(jsTry { ZoneId.of(zoneId) })
    }
}
```

`of` is guarded, but `systemDefault` is questionable. Effectively, it's doing

```javascript
ZoneId.systemDefault = new SystemDefaultZoneId();
try {
    const resolvedTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    ZoneId.systemDefault = function() {
        ZoneId.of(resolvedTimeZone);
    }
} catch (err) {
    return;
}
```

Ok, so if it's *not* resolved, this won't throw be will return a generic result.

```kotlin
public actual open class JodaTimeZoneOffset(override val value: ZoneOffset) : JodaTimeZoneId(value) {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeZoneOffset && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun totalSeconds(): Int = value.totalSeconds()

    actual companion object {
        actual var UTC: JodaTimeZoneOffset = JodaTimeZoneOffset(ZoneOffset.UTC)
        actual fun of(offsetId: String): JodaTimeZoneOffset =
            JodaTimeZoneOffset(jsTry { ZoneOffset.of(offsetId) })
        actual fun ofHoursMinutesSeconds(hours: Int, minutes: Int, seconds: Int): JodaTimeZoneOffset =
            JodaTimeZoneOffset(jsTry { ZoneOffset.ofHoursMinutesSeconds(hours, minutes, seconds) })
        actual fun ofTotalSeconds(totalSeconds: Int): JodaTimeZoneOffset =
            JodaTimeZoneOffset(jsTry { ZoneOffset.ofTotalSeconds(totalSeconds) })
    }
}

public actual open class JodaTimeDayOfWeek(private val value: DayOfWeek) {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeDayOfWeek && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun value(): Int = value.value()
}

public actual open class JodaTimeMonth(private val value: Month) {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeMonth && value.equals(other.value))
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun value(): Int = value.value()
}
```

These are all completely trivial.

```kotlin
public actual open class JodaTimeZoneRules(private val value: ZoneRules)  {
    actual override fun equals(other: Any?): Boolean = this === other || (other is JodaTimeZoneRules && value === other.value)
    actual override fun hashCode(): Int = value.hashCode()
    actual override fun toString(): String = value.toString()
    actual fun isFixedOffset(): Boolean = value.isFixedOffset()
    actual fun offsetOfInstant(instant: JodaTimeInstant): JodaTimeZoneOffset =
        JodaTimeZoneOffset(value.offsetOfInstant(instant.value))
}
```

Oh, I actually remember a case when `isFixedOffset` threw an exception on
Android. However, on JS, with `this._tzdbInfo.offsets.length === 1;`, there's
nothing to throw (except the property accesses, but as far as I can see, in the
JsJoda codebase, the culture of property initialization is strong).

As for `offsetOfInstant`... Hm.

Let's look at this again:

```
31494816403199
9007199254740991
```

If we multiply the top number by 1000, we get

```
31494816403199000
 9007199254740991
```

That's how many milliseconds there are in an `Instant` at most. Clearly an
unsafe number.

```javascript
offsetOfInstant(instant){
    const epochMilli = instant.toEpochMilli();
    return this.offsetOfEpochMilli(epochMilli);
}
```

```javascript
toEpochMilli() {
    const millis = MathUtil.safeMultiply(this._seconds, 1000);
    return millis + MathUtil.intDiv(this._nanos, NANOS_PER_MILLI);
}
```

This will throw on `Instant.MAX_VALUE`!

Yep, `Instant.MAX.offsetIn(TimeZone.of("Europe/Berlin")` crashes.
Disappointigly, it also crashes on the JVM.

But it passes on Linux!

How do I write a test for this?..

```kotlin
Instant.MAX.minus(3, DateTimeUnit.YEAR, zone).offsetIn(zone)
```

No, this also fails, except everywhere, as `minus` converts to `LocalDateTime`
first.

Here:

```
// without the minus, this test fails on JVM
(Instant.MAX - (2 * 365).days).offsetIn(zone)
```

Why did I write this, though? It still won't work on JS.

In any case, I suggested incorporating the fix into the Wasm development branch
<https://github.com/Kotlin/kotlinx-datetime/pull/321>.

2023-12-01
----------

I was busy doing the Friday things and finishing the planning for the datetime
library, when suddenly, it turned out we could publish a release of the
coroutines library today. So, I drop everything, Google "a new hope soundtrack"
and concentrate on following instructions to the letter.

The `RELEASE.md` file at the root of our repository specifies what exactly we
need to do.

... Step 3, `git merge origin/master` fails. Leia's ship appears on the screen.

It seems like the merge conflict is fairly simple.

```
        both modified:   README.md
        both modified:   gradle.properties
        both modified:   gradle/dokka.gradle.kts
        both modified:   integration-testing/gradle.properties
```

In both of `gradle.properties`, the changes are independent version updates on
the lines that happened to be nearby. In `README.md`, also something similar.
In `gradle/dokka.gradle.kts` is the only non-trivial decision: the Wasm PR added
publication of Wasm documentation, and in `master`, there's already a PR that
essentially does a `foreach` on the list of all platforms instead. Well, I can
simply use the version from `master`.

Moving on.

By the time I've finished writing the changelog, Obi-Wan already made his
entrance.

By the time I've double-checked everything... What's going on in the movie?
I'm not sure about this moment. I think it's them entering the Death Star... No,
why would the Force theme be placed directly after that? Also, I think I
would've heard the moment when Alderaan was destroyed. And I'm hearing clearly
the Tatooine themes. We must be near the burning homestead, I think. This makes
more sense: otherwise, it would mean that one third of the movie went by as I
double-checked the release PR.

By the time the build finished after all the small fixes, the end credits are
rolling for episode V. I guess I'll have to stay a bit late to actually see this
published today. Not the best way to spend the Friday evening, but someone has
to release the library. It's a zero-sum game: if I leave and ask someone else to
release it, then they will have to spend the Friday evening waiting for the
build to pass...

What?

Before my very own eyes, the build just *stopped* and restarted. Ok, add another
hour to my time at work today.

Sadly, publishing is not even an involved process. For the next 45 minutes
(at least) I'm just doing some other work.

Ok, why not. Let's spend the Friday evening typing away on the (less than
mediocre) Apple laptop to support the (less than mediocre) Darwin platform in
the kotlinx-datetime library.

Splendid: after wasting another 40 minutes, the build failed with
<https://github.com/Kotlin/kotlinx.coroutines/issues/3936>. Restarting and
getting ready to waste even more of the evening on this.

2023-04-12
----------

Staying at home today, as the trains refused to deliver me to the office.
Not a problem: I'd like to look into `ZonedDateTime`, and I don't need my work
laptop to read through other people's code.

My hypothesis is that `Instant`, a moment in time, is not always the right
abstraction for datetime arithmetics. `ZonedDateTime`, a combination of
the calendar date/time and the corresponding time zone, just feels like the
more appropriate mental model.

For example, our API is flexible enough to allow things like this:
```kotlin
Clock.System.now()
  .plus(1, DateTimeUnit.DAY, TimeZone.of("Europe/Berlin"))
  .plus(1, DateTimeUnit.DAY, TimeZone.of("America/New_York"))
  .toLocalDateTime(TimeZone.of("Europe/Moscow"))
```

But this is just nonsense!

On the other hand, our API requires meaningless repetition in cases like

```kotlin
localDateTime
  .toInstant(timeZone)
  .plus(1, DateTimeUnit.DAY, timeZone)
  .toLocalDateTime(timeZone)
```

It could be much more straightforward:

```kotlin
localDateTime
  .atZone(timeZone)
  .plus(1, DateTimeUnit.DAY)
  .localDateTime
```

Yet that's a hypothesis. First, I'll need to validate that it does happen.

I'll be searching for code that uses `DateTimeUnit`, trying to understand if
`ZonedDateTime` would meaningfully simplify it. Maybe I'll find more than I
bargained for on the way. We'll see.

---

<https://github.com/hi-manshu/Kalendar/blob/2d6a5cfaa77ad97b03fd8ad6a609ba5627cf11eb/kalendar/src/main/java/com/himanshoe/kalendar/ui/oceanic/util/WeekData.kt#L29>

This specimen implements a date range, which is a long-standing feature request.

`isLeapYear` at the bottom of the file also caught my interest, but it's just
used to calculate the number of days in a month:
<https://github.com/hi-manshu/Kalendar/blob/2d6a5cfaa77ad97b03fd8ad6a609ba5627cf11eb/kalendar/src/main/java/com/himanshoe/kalendar/ui/firey/KalendarFirey.kt#L101>

---

<https://github.com/data2viz/data2viz/blob/5640d3e8f1ce4cd4e5d651431726869e329520fc/scale/src/commonMain/kotlin/io/data2viz/scale/intervals/intervals.kt#L16>

This is a way to implement iterating over `Instant` values with a given step.
Ok, makes sense.

<https://github.com/data2viz/data2viz/blob/5640d3e8f1ce4cd4e5d651431726869e329520fc/scale/src/commonMain/kotlin/io/data2viz/scale/intervals/intervals.kt#L62-L73>
looks like something that temporal accessors <https://github.com/Kotlin/kotlinx-datetime/issues/325>
could help a lot with.

---

<https://github.com/JuulLabs/krayon/blob/d9fab622e79312c274c7fd859da3eeed129bf984/time/src/commonMain/kotlin/WeekInterval.kt#L16>

```kotlin
    // TODO: Optimize this via math
    override fun floor(input: LocalDateTime): LocalDateTime =
        generateSequence(input.date) { it.minus(1, DateTimeUnit.DAY) }
            .first { it.dayOfWeek == dayOfWeek }
            .atTime(0, 0)
```

Another use case for temporal accessors.

The best arithmetics on dates and times is the one someone else does for you!

By the way, this, too, is in the service of intervals with steps. Something to
keep in mind.

---

<https://github.com/dcxp/opentelemetry-kotlin/blob/43967d97f58592ed808b2d04d26bc80c53f4d670/api/all/src/commonMain/kotlin/io/opentelemetry/kotlin/api/common/DateTimeUnit.ext.kt#L5>

```kotlin
fun DateTimeUnit.getNanos(): Long {
    return when (this) {
        is DateTimeUnit.TimeBased -> nanoseconds
        is DateTimeUnit.DayBased -> DateTimeUnit.HOUR.nanoseconds * 24 * days
        is DateTimeUnit.MonthBased ->
            throw IllegalStateException("Month based DateTimeUnits can not be converted to nanos")
    }
}
```

Wow, thanks, I hate it!

Essentially, someone confused `DateTimeUnit` with `Duration`.

---

<https://github.com/batoulapps/adhan-kotlin/blob/748ecd751eca5db2f34561f630096ad2b36a7921/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/PrayerTimes.kt#56>

```kotlin
    val tomorrowDate: LocalDateTime = add(prayerDate, 1, DateTimeUnit.DAY)
```

Heh, someone somewhere is praying at the incorrect time because they are using
`LocalDateTime` arithmetics. I hope the divine punishment for this honest
error is not too severe in that religion... This is Islamic library. I don't
know enough about Islam to judge the degree of the sin.

This whole repo is seemingly a treasure trove of misusing our API.

```kotlin

  private fun LocalDateTime.before(other: LocalDateTime): Boolean {
    return toInstant(TimeZone.UTC).toEpochMilliseconds() <
        other.toInstant(TimeZone.UTC).toEpochMilliseconds()
  }

  private fun LocalDateTime.after(other: LocalDateTime): Boolean {
    return toInstant(TimeZone.UTC).toEpochMilliseconds() >
        other.toInstant(TimeZone.UTC).toEpochMilliseconds()
  }
```

This could be rewritten as

```kotlin
fun LocalDateTime.before(other: LocalDateTime) = this < other

fun LocalDateTime.after(other: LocalDateTime) = this > other
```

But this is a good reminder to introduce
<https://github.com/Kotlin/kotlinx-datetime/issues/137> so that people who
copy-paste their Java code naturally arrive to the simple implementation.

They are reimplementing `LocalDate`:
<https://github.com/batoulapps/adhan-kotlin/blob/748ecd751eca5db2f34561f630096ad2b36a7921/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/data/DateComponents.kt>

and `LocalTime`, though with the unique twist of allowing negative hours, or
so it would seem:
<https://github.com/batoulapps/adhan-kotlin/blob/748ecd751eca5db2f34561f630096ad2b36a7921/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/data/TimeComponents.kt>
Though for negative hours, it seems buggy: `-23.5` hours is actually
`-23.hours + 30.minutes`, no? Ah, well.

The meat of this repository for me is
<https://github.com/batoulapps/adhan-kotlin/blob/748ecd751eca5db2f34561f630096ad2b36a7921/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/data/CalendarUtil.kt>

The utility methods the author needed but didn't find in our library.

```kotlin
  fun LocalDateTime.toUtcInstant(): Instant = toInstant(TimeZone.UTC)
```

The existence of this method looks like a bug in and of itself.

```kotlin
  private fun resolveTime(year: Int, month: Int, day: Int): LocalDateTime {
    return LocalDateTime(year, month, day, 0, 0, 0)
  }
```

This is essentally `localDate.atStartOfDay().toLocalDateTime(TimeZone.UTC)`,
except the latter is much more error-proof.

The comment explains why it's needed:

```kotlin
   * Gets a date for the particular date
```

Maybe I'm wrong and all this person wants to do is actually to manipulate
exactly the UTC time, so everything's fine? If so, manipulating
`ZonedDateTime` with the UTC time zone would express that intent more clearly.

```kotlin
  fun add(localDateTime: LocalDateTime, amount: Int, dateTimeUnit: DateTimeUnit): LocalDateTime {
    val timezone = TimeZone.UTC
    val instant = localDateTime.toInstant(timezone)
    return add(instant, amount, dateTimeUnit)
  }
```

The same story: only valid when there are no timezone transitions.

Looking at <https://github.com/batoulapps/adhan-kotlin/blob/main/README.md>, I
get the impression that sticking to UTC is entirely justified and intentional:
it is performing astronomical calculations, always returning the result in UTC.
Maybe the methods for calculating this are all in terms of UTC as well.
This does suggest that the user of the library is actually correct, the problem
is only that we don't provide a data structure that's comfortable enough to use
for this use case. They need both the access to the raw fields like `day` and
the arithmetics.

So, I'm sure this could be written better in small places, but overall, I was
too quick to jump to conclusions. I don't see any obvious bugs, except maybe the
strange `TimeComponents`.

---

<https://github.com/GerardPaligot/conferences4hall/blob/472258a2383d15232d7c205529bc0688667fbd93/shared/core/src/androidMain/kotlin/org/gdglille/devfest/AlarmScheduler.kt#L42-L48>

```kotlin
val time =
    talkItem.startTime.toLocalDateTime().toInstant(TimeZone.currentSystemDefault())
        .minus(ReminderInMinutes, DateTimeUnit.MINUTE).toEpochMilliseconds()
alarmManager.set(
    AlarmManager.ELAPSED_REALTIME_WAKEUP,
    SystemClock.elapsedRealtime() + (time - Clock.System.now().toEpochMilliseconds()),
    pendingIntent
)
```

This can be rewritten to

```kotlin
val time = talkItem.startTime.toLocalDateTime().toInstant(TimeZone.currentSystemDefault())
val remindIn = time - Clock.System.now() - ReminderInMinutes.minutes
alarmManager.set(
    AlarmManager.ELAPSED_REALTIME_WAKEUP,
    SystemClock.elapsedRealtime() + remindIn.inWholeMilliseconds(),
    pendingIntent
)
```

Looks pretty straightforward to me, nothing to improve API-wise.

---

<https://github.com/joelkanyi/MealTime/blob/e911cc8264c340353814746e45ac2e11defdd551/core/common/src/main/java/com/joelkanyi/common/util/UtilFunctions.kt#L237-L252>

```kotlin
fun calendarLocalDates(): List<LocalDate> {
    val thisYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    val lastYear = thisYear - 1
    val nextYear = thisYear + 1
    val dates = mutableListOf<LocalDate>()
    for (i in 0..365) {
        dates += LocalDate(thisYear, 1, 1).plus(i, DateTimeUnit.DAY)
    }
    for (i in 0..365) {
        dates += LocalDate(lastYear, 1, 1).plus(i, DateTimeUnit.DAY)
    }
    for (i in 0..365) {
        dates += LocalDate(nextYear, 1, 1).plus(i, DateTimeUnit.DAY)
    }
    return dates
}
```

Another implementation of a range of dates, and an incorrect one at that, as it
doesn't take leap years into account.

---

Alright, as I'm mostly interested in time zones and arithmetics that involves
them, let's narrow our search.

Instead of
<https://github.com/search?q=DateTimeUnit.+language%253AKotlin&type=code&l=Kotlin>,
we'll have
<https://github.com/search?q=DateTimeUnit.DAY%2C&type=code>.

Yes, this immediately yielded some better results.

---

<https://github.com/streetcomplete/StreetComplete/blob/4f287e194b30ad73a075fe689ce7db1be8dd12f9/app/src/main/java/de/westnordost/streetcomplete/screens/user/profile/DatesActiveDrawable.kt#L90>

```kotlin
            time = time.minus(1, DateTimeUnit.DAY, TimeZone.UTC)
```

Here, I'm confident this is a mistake: why does the Github-style
activity-for-dates grid only support the UTC time zone?

---

<https://github.com/hyperskill/mobile-app/blob/08417af603b77c5db4e81733d3fcecea1225472b/shared/src/commonMain/kotlin/org/hyperskill/app/core/utils/DateTimeUtils.kt#L22>

```kotlin
val tzNewYork = TimeZone.NYC
val nowInNewYork = Clock.System.now().toLocalDateTime(tzNewYork).toInstant(tzNewYork)
val tomorrowInNewYork = nowInNewYork.plus(1, DateTimeUnit.DAY, tzNewYork).toLocalDateTime(tzNewYork)
val startOfTomorrow = LocalDateTime(
    year = tomorrowInNewYork.year,
    month = tomorrowInNewYork.month,
    dayOfMonth = tomorrowInNewYork.dayOfMonth,
    hour = 0,
    minute = 0,
    second = 0,
    nanosecond = 0
)
return (startOfTomorrow.toInstant(tzNewYork) - nowInNewYork).inWholeSeconds
```

First of all, `toLocalDateTime(tzNewYork).toInstant(tzNewYork)` is just a no-op
in most cases, and where it does something, it's just harmful.

Second, zeroing out the local time can be done easier:

```kotlin
val tzNewYork = TimeZone.NYC
val now = Clock.System.now()
val tomorrowInNewYork = now.plus(1, DateTimeUnit.DAY, tzNewYork).toLocalDateTime(tzNewYork)
val startOfTomorrow = tomorrowInNewYork.date.atStartOfDay(tzNewYork)
return (startOfTomorrow - now).inWholeSeconds
```

If we had temporal adjusters... this would actually be error-prone!

```kotlin
val tzNewYork = TimeZone.NYC
val now = Clock.System.now()
val startOfTomorrow = now.nextWithTime(time = LocalTime(hour = 0, minute = 0), tzNewYork)
return (startOfTomorrow - now).inWholeSeconds
```

This hides a subtle error: if the following day doesn't have the midnight
(for example, if 23:30 was directly followed by 00:30), this will skip more than
24 hours, which is not what's expected.

... Or we could implement `nextWithTime` by first adjusting the time blindly and
only then resolving the time gaps. Except that's not semantically clear:
who would expect `nextWithTime` not to set exactly the time it's asked to find?

... Except in `ZonedDateTime`! There, the whole semantics of "this is all done
on `LocalDateTime` and the resolution only happens at the end" will naturally
map to this assumption.


Let's look at the second function in that file:

```kotlin
val tzNewYork = TimeZone.NYC
val nowInNewYork = Clock.System.now().toLocalDateTime(tzNewYork)
val nowInNewYorkInstant = nowInNewYork.toInstant(tzNewYork)

// Calculate the number of days until the next Sunday and add that number of days to the current date.
val nextSundayInNewYorkInstant = if (nowInNewYork.dayOfWeek == DayOfWeek.SUNDAY) {
    nowInNewYorkInstant.plus(1, DateTimeUnit.WEEK, tzNewYork)
} else {
    val daysUntilSunday = (DayOfWeek.SUNDAY.ordinal - nowInNewYork.dayOfWeek.ordinal + 7) % 7
    nowInNewYorkInstant.plus(daysUntilSunday.toLong(), DateTimeUnit.DAY, tzNewYork)
}

val nextSundayInNewYork = nextSundayInNewYorkInstant.toLocalDateTime(tzNewYork)
val startOfNextSunday = LocalDateTime(
    year = nextSundayInNewYork.year,
    month = nextSundayInNewYork.month,
    dayOfMonth = nextSundayInNewYork.dayOfMonth,
    hour = 0,
    minute = 0,
    second = 0,
    nanosecond = 0
)

return (startOfNextSunday.toInstant(tzNewYork) - nowInNewYorkInstant).inWholeSeconds
```

Let's simplify this first.

```kotlin
val tzNewYork = TimeZone.NYC
val now = Clock.System.now()
val dayOfWeekInNewYork = now().toLocalDateTime(tzNewYork).dayOfWeek

// Calculate the number of days until the next Sunday and add that number of days to the current date.
val nextSunday = if (dayOfWeekInNewYork == DayOfWeek.SUNDAY) {
    now.plus(1, DateTimeUnit.WEEK, tzNewYork)
} else {
    val daysUntilSunday = (DayOfWeek.SUNDAY.ordinal - dayOfWeekInNewYork.ordinal + 7) % 7
    now.plus(daysUntilSunday.toLong(), DateTimeUnit.DAY, tzNewYork)
}

val nextSundayInNewYork = nextSunday.toLocalDateTime(tzNewYork)
val startOfNextSunday = nextSundayInNewYork.date.atStartOfDay(tzNewYork)

return (startOfNextSunday - now).inWholeSeconds
```

Now, imagine we had `ZonedDateTime`.

```kotlin
val tzNewYork = TimeZone.NYC
val nowInNewYork = Clock.System.now().atZone(tzNewYork)

// Calculate the number of days until the next Sunday and add that number of days to the current date.
val nextSundayInNewYork = if (nowInNewYork.dayOfWeek == DayOfWeek.SUNDAY) {
    nowInNewYork.plus(1, DateTimeUnit.WEEK)
} else {
    val daysUntilSunday = (DayOfWeek.SUNDAY.ordinal - nowInNewYork.dayOfWeek.ordinal + 7) % 7
    nowInNewYork.plus(daysUntilSunday, DateTimeUnit.DAY)
}

val startOfNextSunday = nextSundayInNewYork.localDateTime.date.atStartOfDay(tzNewYork)

return (startOfNextSunday - now).inWholeSeconds
```

... and some style...

```kotlin
val tzNewYork = TimeZone.NYC
val nowInNewYork = Clock.System.now().atZone(tzNewYork)

// Calculate the number of days until the next Sunday and add that number of days to the current date.
val nextSundayInNewYork = with(nowInNewYork) {
    if (dayOfWeek == DayOfWeek.SUNDAY) {
        plus(1, DateTimeUnit.WEEK)
    } else {
        val daysUntilSunday = (DayOfWeek.SUNDAY.ordinal - dayOfWeek.ordinal + 7) % 7
        plus(daysUntilSunday, DateTimeUnit.DAY)
    }
}

val startOfNextSunday = nextSundayInNewYork.localDateTime.date.atStartOfDay(tzNewYork)

return (startOfNextSunday - now).inWholeSeconds
```

Now that I think about it, `ZonedDateTime` would not be useful here, were this
code written more idiomatically. Here's a viable alternative:

```kotlin
val tzNewYork = TimeZone.NYC
val now = Clock.System.now()
val todayInNewYork = now.toLocalDateTime(tzNewYork).date

// Calculate the number of days until the next Sunday and add that number of days to the current date.
val nextSundayInNewYork = with(todayInNewYork) {
    val nextSunday = if (dayOfWeek == DayOfWeek.SUNDAY) {
        plus(1, DateTimeUnit.WEEK)
    } else {
        val daysUntilSunday = (DayOfWeek.SUNDAY.ordinal - dayOfWeek.ordinal + 7) % 7
        plus(daysUntilSunday.toLong(), DateTimeUnit.DAY)
    }
}

val startOfNextSunday = nextSundayInNewYork.atStartOfDay(tzNewYork)

return (startOfNextSunday - now).inWholeSeconds
```

Without temporal adjusters, this is on par with `ZonedDateTime`.

---

This seems to be a theme: people are just unaware that `LocalDate` has its own
arithmetics.

<https://github.com/Scogun/kcron-common/blob/56a32c9f1e119191e939cab275766e184f98e677/src/commonMain/kotlin/com/ucasoft/kcron/builders/Builder.kt#L199>

```kotlin
private fun nearestWorkDayTo(year: Int, month: Int, lastMonthDay: Int, day: Int) : Int {
    var checkingDay = LocalDateTime(year, month, day, 0, 0)
    if (checkingDay.dayOfWeek.isoDayNumber >= 6) {
        checkingDay = if (checkingDay.dayOfWeek.isoDayNumber == 6) {
            if (checkingDay.dayOfMonth > 1) {
                checkingDay.minusDays(1)
            } else {
                checkingDay.plusDays(2)
            }
        } else if (checkingDay.dayOfMonth < lastMonthDay) {
            checkingDay.plusDays(1)
        } else {
            checkingDay.minusDays(2)
        }
    }
    return checkingDay.dayOfMonth
}

fun LocalDateTime.plusDays(days: Int, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
    return plus(this, days, DateTimeUnit.DAY, timeZone)
}

fun LocalDateTime.minusDays(days: Int, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
    return plus(this, if (days > 0) { days * -1 } else { days }, DateTimeUnit.DAY, timeZone)
}

private fun plus(self: LocalDateTime, value: Int, unit: DateTimeUnit, timeZone: TimeZone) : LocalDateTime {
    return self.toInstant(timeZone).plus(value, unit, timeZone).toLocalDateTime(timeZone)
}
```

I don't even want to imagine the edge cases where this will fail (but there are
some!). Here's what won't fail:

```kotlin
private fun nearestWorkDayTo(year: Int, month: Int, lastMonthDay: Int, day: Int) : Int {
    var checkingDay = LocalDate(year, month, day)
    if (checkingDay.dayOfWeek.isoDayNumber >= 6) {
        checkingDay = if (checkingDay.dayOfWeek.isoDayNumber == 6) {
            if (checkingDay.dayOfMonth > 1) {
                checkingDay.minus(1, DateTimeUnit.DAY)
            } else {
                checkingDay.plus(2, DateTimeUnit.DAY)
            }
        } else if (checkingDay.dayOfMonth < lastMonthDay) {
            checkingDay.plus(1, DateTimeUnit.DAY)
        } else {
            checkingDay.minus(2, DateTimeUnit.DAY)
        }
    }
    return checkingDay.dayOfMonth
}
```

This function is an extra WTF:

```kotlin
fun LocalDateTime.minusDays(days: Int, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
    return plus(this, if (days > 0) { days * -1 } else { days }, DateTimeUnit.DAY, timeZone)
}
```

With this, `x - (-3) == x - 3`. I can *sort of* see the logic: `minusDays(-3)`
must be some subvocalization: "I want to subtract days. How many? -3 days."

Nevermind.

---

<https://github.com/oianmol/ComposeAnimationsPlayground/blob/3497b46538cc6aaefffb7e41acc714268964efd4/common/src/commonMain/kotlin/dev/baseio/composeplayground/ui/animations/anmolverma/IOSSleepSchedule.kt#L222>

```kotlin
var newStartTime =
    sTime.toInstant(TimeZone.currentSystemDefault()).plus(elapsedTime)
        .toLocalDateTime(TimeZone.currentSystemDefault())
var newEndTime = enTime.toInstant(TimeZone.currentSystemDefault()).plus(elapsedTime)
    .toLocalDateTime(TimeZone.currentSystemDefault())

if (newEndTime.date.dayOfMonth > newStartTime.date.dayOfMonth) {
    newStartTime = newStartTime.toInstant(TimeZone.currentSystemDefault()).minus(
        1,
        DateTimeUnit.DAY, TimeZone.currentSystemDefault()
    ).toLocalDateTime(TimeZone.currentSystemDefault())
    newEndTime = newEndTime.toInstant(TimeZone.currentSystemDefault()).minus(
        1,
        DateTimeUnit.DAY, TimeZone.currentSystemDefault()
    ).toLocalDateTime(TimeZone.currentSystemDefault())
}
```

What a mess!

This is a statement of fact, not a moral judgement. Before diving in deeper, I
don't even know who made the mess: the developer, us, or both.

Eh... Let's refactor this, I guess?

```kotlin
val zone = TimeZone.currentSystemDefault()
var newStartTime = sTime.toInstant(zone).plus(elapsedTime).toLocalDateTime(zone)
var newEndTime = enTime.toInstant(zone).plus(elapsedTime).toLocalDateTime(zone)

if (newEndTime.dayOfMonth > newStartTime.dayOfMonth) {
    newStartTime = newStartTime.toInstant(zone).minus(1, DateTimeUnit.DAY, zone).toLocalDateTime(zone)
    newEndTime = newEndTime.toInstant(zone).minus(1, DateTimeUnit.DAY, zone).toLocalDateTime(zone)
}
```

Oh, it's actually okay if we remove the race condition. Though the logic of this
code eludes me completely. If, after adding a duration to both the end and the
start they end up at different dates, then we subtract a day from both. Why?..

Ok, doesn't matter. We're here to see if `ZonedDateTime` would express this
better.

```kotlin
val zone = TimeZone.currentSystemDefault()
val sTime = sTime.atZone(zone)
val enTime = enTime.atZone(zone)
var newStartTime = sTime.plus(elapsedTime) // likely won't be added
var newEndTime = enTime.plus(elapsedTime) // likely won't be added

if (newEndTime.dayOfMonth > newStartTime.dayOfMonth) {
    newStartTime = newStartTime.minus(1, DateTimeUnit.DAY)
    newEndTime = newEndTime.minus(1, DateTimeUnit.DAY)
}
```

Did this help us? To answer this, let's look at how `elapsedTime` is obtained.

```kotlin
private fun elapsedTime(
    timeAtUsersFinger: LocalDateTime,
    sTime: LocalDateTime
): Duration {
    val timeAtUsersFingerInstant = timeAtUsersFinger.toInstant(TimeZone.currentSystemDefault())
    val sTimeInstant = sTime.toInstant(TimeZone.currentSystemDefault())
    val millisUntil = sTimeInstant.until(timeAtUsersFingerInstant, DateTimeUnit.MILLISECOND)
    return millisUntil.milliseconds
}
```

Simplifying:

```kotlin
private fun elapsedTime(
    timeAtUsersFinger: LocalDateTime,
    sTime: LocalDateTime
): Duration {
    val zone = TimeZone.currentSystemDefault()
    return timeAtUsersFinger.toInstant(zone) - sTime.toInstant(zone)
}
```

(Maybe also truncating to whole milliseconds at the end).

This function calculates how many milliseconds passed between the two instants.
Let's assume that's intentional. Then `elapsedTime` is how long it takes in
real time, and `plus(elapsedTime)` on a `ZonedDateTime` must perform the
resolution to calculate the time, effectively going back and forth between
`LocalDateTime` and `Instant` anyway.

Therefore, `plus(elapsedTime)` semantically doesn't fit into `ZonedDateTime`,
and this use case is no exception.

```kotlin
val zone = TimeZone.currentSystemDefault()
var newStartTime = sTime.toInstant(zone).plus(elapsedTime).atZone(zone)
var newEndTime = enTime.toInstant(zone).plus(elapsedTime).atZone(zone)

if (newEndTime.dayOfMonth > newStartTime.dayOfMonth) {
    newStartTime = newStartTime.minus(1, DateTimeUnit.DAY)
    newEndTime = newEndTime.minus(1, DateTimeUnit.DAY)
}
```

This does help out a bit compared to no `ZonedDateTime`, but it's no silver
bullet. Still, the behavior probably changed for the better: the double
conversion to `LocalDateTime` is lossy, and here there will only be one at the
end.

Without knowing what the author meant and what the code is supposed to do,
tough to say what would improve the code.

---

<https://github.com/HuixingWong/Food2Fork-KMM/blob/85dda1d0ae789874e2473717473b32f43a0b961b/shared/src/commonMain/kotlin/com/codingwithmitch/food2forkkmm/domain/util/DatetimeUtil.kt#L33>

Another piece of code where the author only needed the date but ended up with
`Instant` arithmetics.

---

<https://github.com/d4l-data4life/hc-data-donation-sdk-kmp/blob/d1f997a7d3aa35a178186bd640e70bdb47a2b799/data-donation-sdk/src/commonMain/kotlin/care/data4life/datadonation/donation/fhir/anonymization/DateTimeConcealer.kt#L50>

Here are some home-grown temporal adjusters.

---

Maybe months would give us more signal?

<https://github.com/search?q=DateTimeUnit.MONTH%2C+language%3AKotlin&type=code&l=Kotlin&p=2>

No, I just don't see any valuable leads at all. People almost never use this.

<https://grep.app/search?q=DateTimeUnit.MONTH%2C&filter[lang][0]=Kotlin>
is a more telling representation of the same result.

For years, it's even worse:

<https://grep.app/search?q=DateTimeUnit.YEAR%2C&filter[lang][0]=Kotlin>

So, days it is then.

---

<https://github.com/Scogun/kcron-common/blob/56a32c9f1e119191e939cab275766e184f98e677/src/commonMain/kotlin/com/ucasoft/kcron/builders/Builder.kt#L218-L227>

```kotlin
private fun lastMonthDay(year: Int, month: Int): LocalDateTime {
    var nextMonth = month + 1
    var nextYear = year
    if (nextMonth > 12) {
        nextYear += 1
        nextMonth = 1
    }
    val firstDayNextMonth = LocalDateTime(nextYear, nextMonth, 1, 0, 0).toInstant(TimeZone.currentSystemDefault())
    return firstDayNextMonth.plus(-1, DateTimeUnit.DAY, TimeZone.currentSystemDefault()).toLocalDateTime(TimeZone.currentSystemDefault())
}
```

First: yes, this is the case for temporal adjusters. But how it was achieved
here is also representative.

Second: easily achieved with `LocalDate` with even better reliability:

```kotlin
private fun lastMonthDay(year: Int, month: Int): LocalDateTime {
    var nextMonth = month + 1
    var nextYear = year
    if (nextMonth > 12) {
        nextYear += 1
        nextMonth = 1
    }
    val lastDay = LocalDate(nextYear, nextMonth, 1).plus(-1, DateTimeUnit.DAY)
    return lastDay.atStartOfDay(TimeZone.currentSystemDefault())
}
```

or better yet,

```kotlin
private fun lastMonthDay(year: Int, month: Int): LocalDateTime {
    val thisDay = LocalDate(year, month, 1)
    val lastDay = thisDay.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    return lastDay.atStartOfDay(TimeZone.currentSystemDefault())
}
```

Not to mention that you can just return `LocalDate`: the surrounding code
doesn't seem to be using the time part.

In any case, we see the pattern:

```kotlin
ldt.toInstant(zone).plus(x, zone).toLocalDateTime(zone)
```

2023-12-05
----------

Let's double-check <https://github.com/Kotlin/kotlinx.coroutines/pull/3969/>.

```sh
mkdir oldHtml
./gradlew clean dokkaHtml
# check that the general form yields the results I need
find . -path '*build/dokka*' -exec sh -c 'echo "$0"' '{}' ';'
find . -path '*build/dokka*' -exec sh -c '[ -d "$0" ] || (mkdir -p oldHtml/${0%/*}; cp "$0" oldHtml/"$0")' '{}' ';'
```

Whoops, it copied some of the files from `oldHtml` to `oldHtml`. Not a big
problem.

```sh
mv oldHtml ..
./gradlew clean dokkaHtml
mkdir ../newHtml
find . -path '*build/dokka*' -exec sh -c '[ -d "$0" ] || (mkdir -p ../newHtml/${0%/*}; cp "$0" ../newHtml/"$0")' '{}' ';'
```

Could probably have this `find` as a much simpler `rsync` command, but this is
in my CLI only, it doesn't go to any production.

```sh
diff -f oldHtml/ newHtml/
```

Hey, there *are* some differences. Now... how do I read them?
`difft` doesn't support `-r`, or `--recursive`... What if...

```sh
difft oldHtml/ newHtml/
```

Splendid. This way, the differences are extremely readable. Difftastic
completely changed how I approach diffing: now, I don't have to metodically
browse through everything, which was especially painful with long lines and/or
"words."

<https://github.com/Kotlin/kotlinx-datetime/commit/841b0a895e8f8b23bdb0ba4f397273e71743e10f>


A quick way to remove the deprecated `SharedImmutable` annotation:
```sh
git grep -l SharedImmutable | xargs -n1 sed -i '/SharedImmutable/d'
```

After trying to upgrade the datetime formatting code I have to the new compiler
version, I'm getting

```kotlin
e: file:///home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/common/src/format/DateTimeFormat.kt:112:25 Unresolved reference: Parser
e: file:///home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/common/src/format/DateTimeFormat.kt:112:38 Property delegate must have a 'getValue(AbstractDateTimeFormat<T, U>, KProperty<*>)' method. None of the following functions is suitable:
public inline operator fun <T> Lazy<Unit>.getValue(thisRef: Any?, property: KProperty<*>): Unit defined in kotlin
e: file:///home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/common/src/format/DateTimeFormat.kt:112:45 Unresolved reference: Parser
e: file:///home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/common/src/format/DateTimeFormat.kt:128:89 Unresolved reference: it
```

The IDE doesn't highlight the code as red. The code itself seems *completely*
benign:

```kotlin
    private val parser: Parser<U> by lazy { Parser(actualFormat.parser) }
```

I think it's very strange that it's come to this, but I'll just remove this
line altogether, it was not all that important.

```kotlin
e: file:///home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/common/src/format/DateTimeFormat.kt:114:13 Unresolved reference: Parser
e: file:///home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/common/src/format/DateTimeFormat.kt:126:9 Unresolved reference: Parser
e: file:///home/dmitry.khalanskiy/IdeaProjects/kotlinx-datetime/core/common/src/format/DateTimeFormat.kt:126:110 Unresolved reference: it
```

Eh? Just what is going on?

Using the fully-qualified name seems to have helped.

Oh, I found the culprit: during the rebase, `Parser.kt` in Native un-deleted
itself somehow, must be because I made a mistake somewhere. So, the ambiguity
surfaced in this surprising manner. Removing `Parser.kt` fixed this.

Going through the codebase and adding diagnostic messages:

```sh
git grep 'require(' | grep -v '{'
```

2023-12-11
----------

The last two days of the previous week went by in somewhat of a haze. Quite
spontaneously, we and @qwwdfsad started cleaning out the mess that are the
Gradle configs for the coroutines library:
<https://github.com/Kotlin/kotlinx.coroutines/pull/3966>.

This part of the library was a constant low-key source of problems: no one
really understood what was going on there. If the project managed to build and
publish successfully, we called it a success, but any change to the Gradle
configs required feeling a little bit of sadness each time.

After our sporadic changes, the library is a bit better. Looking at the diff
right now, we see `+1196 -1315` lines, so seemingly, we didn't save much, but
notably, a lot of the new code are comments that weren't there before. That's
a win in my book. There is still much to fix, many workaround of questionable
usefulness to remove, but that's a story for some other time.

Right now, I'm working on <https://github.com/Kotlin/kotlinx-datetime/pull/327>.
The problem still is that we don't know how to access the correct timezone
database in the iOS Simulator, and so there can be discrepancies.

Definitely, it's not a major issue. When we introduce custom timezone databases,
we'll need to rework `TimeZone.toNSTimeZone` anyway: there *will* be expected
discrepancies. Still, I'll try to find a cleaner solution for the problem at
hand today.

My immediate instinct is to use `strace` to capture the filesystem accesses and
check which files are touched by the iOS Simulator. As far as I understand from
cursory reading, iOS Simulator is not an emulator and gets its functionality
from *somewhere* in the filesystem. I'll need to try touching some arbitrary
file from iOS Simulator and double-check this.

On MacOS, the equivalent to `strace` is `dtrace`. I have two problems:

* I assume the iOS Simulator is not a simple command-line program but some
  daemon. I will probably need to dynamically attach to it somehow.
* `dtrace` doesn't seem to be working on modern MacOS versions even under `sudo`
  due to strict security.

The first problem is straightforward enough to solve: after some searching, I
see that there's an `opensnoop` command that attaches to everything at once and
dumps all that's happening. If I time this correctly, I can probably create a
dump of fairly reasonable size that I could examine.

The second problem is not solved by `opensnoop`, as the security protection
forbids `opensnoop` as well.

One can disable the protection system-wide:
<https://developer.apple.com/documentation/security/disabling_and_enabling_system_integrity_protection>
This should work, and I'm certain this option suits me, as I'm barely ever using
MacOS, there's just not enough attack surface on it to get infected, and even if
it does get infected, the stakes are extremely low.

Still, I can't believe there's no better way. Tracing what's going on in the
system should be a fairly common requirement. Am I to believe that every Apple
developer disables the system-wide protection?

After some searching, I found the `fs_usage` utility that does seem to work
even despite the system protection.

```swift
import Cocoa
let zone = TimeZone.init(identifier: "Europe/Berlin")
print(zone.secondsFromGMT())
```

In one terminal:

```sh
fs_usage -w -f filesys > usage.txt
```

In another:

```sh
swift x.swift
```

Voila, there's a `Berlin` in the resulting file:
```
(R___)    /usr/share/zoneinfo.default/Europe/Berlin
```

Fun fact observable in `swiftlang-5.9.0.128.108 clang-1500.0.40.1`, the REPL
mode:

```swift
import Cocoa
TimeZone.init(identifier: "Europe/Berlin")
```

produces the output

```
$R0: Foundation.TimeZone? = some {
  _kind = fixed
  _timeZone = 0x00006000022d4740 {
    offset = nil
    lock = {
      _buffer = {
        Swift.ManagedBuffer<Foundation._TimeZone.State, Darwin.os_unfair_lock_s> = {
          Swift.ManagedBuffer<Foundation._TimeZone.State, Darwin.os_unfair_lock_s> = {
            Swift.ManagedBuffer<Foundation._TimeZone.State, Darwin.os_unfair_lock_s> = {
```
and so on, the last line just gets repeated with bigger and bigger offset.

Now I have to force the iOS Simulator to access a time zone and then to observe
it doing that. Luckily, I already have an iOS project that accesses time zones,
I can just tweak it a bit.

Ok, so...

```
(R___)    /usr/share/zoneinfo.default/Europe/Berlin
```

But... `/usr/share/zoneinfo.default` **does** have `America/Ciudad_Juarez`.
I didn't solve anything.

It looks like, the thing is, the Darwin framework doesn't simply go to the
filesystem and check the present files, but instead uses some internal list of
timezones that didn't get updated in time when a new time zone was introduced.

This sort of solves it by leaving us with no real alternatives: we can either
limit the set of timezones to the ones available on Darwin or just inform the
users that `TimeZone.toNSTimeZone` can fail.

2023-12-13
----------

Let's look at this Kotlin code:

```kotlin
try {
  f()
} catch (e: Throwable) {
  println("Caught a throwable")
  throw e
} finally {
  println("Exiting somehow")
}
println("Exited successfully")
```

In which cases will this code only print `Exiting somehow` and nothing else?
This is a language question, not a trick question, so
"if the process terminates between the two lines" doesn't count.

The answer (as far as I know, the complete answer):

```kotlin
inline fun call(f: () -> Unit) {
  try {
    f()
  } catch (e: Throwable) {
    println("Caught a throwable")
    throw e
  } finally {
    println("Exiting somehow")
  }
  println("Exited successfully")
}

call {
  return
}
```

I got an amazing opportunity to learn about this third way to exit a block of
code due to an issue in the code I approved:
<https://github.com/Kotlin/kotlinx.coroutines/issues/3985>

Not that I didn't know about this behavior, I just never internalized until now
that `inline` functions are more like macros than functions, breaking
referential transparency somewhat. Intuitively, I'd expect this non-local return
to manifest in something like a virtual `throw` call, like kotlinx-coroutines
does with `CancellationException`. The situation is exactly the same: we want
to exit the code non-locally due to a non-error event; what's different is the
solution.

Now, I'll to go through the entire code base, look for other `inline` functions
that use `finally`, and ensure that they do properly unsubscribe from whatever
they are using.

After careful testing, I arrived at <https://github.com/Kotlin/kotlinx.coroutines/pull/3986>
I didn't test the implementations in the reactive integrations that also have
`inline` functions that call some `inline` functions for which I did add tests,
as I think it's far too unlikely we'll have to rewrite them.

2024-01-18
----------

A happy day today: after consulting with the legal team, I received confirmation
that we no longer have to include copyright notices in each individual file.

```sh
git grep -l JetBrains | xargs -n1 perl -pi -0 -e 's/\/\*[^\/]*JetBrains s.r.o[^\/]*\/\s+//igs'
```

2024-07-23
----------

Today, I'm woking on <https://github.com/Kotlin/kotlinx-datetime/issues/381>.
For that, I'm classifying all the instances of `java.time.Period.plusDays` I can find.

grep.app only surfaced a single one:

* <https://github.com/rallyhealth/scalacheck-ops/blob/41357feedccf64ec411a4caaf6136d67493617f5/joda/src/test/scala/org/scalacheck/ops/time/joda/JodaLocalDateGeneratorsSpec.scala#L14>

GitHub search for `eriod.plusDays` yielded a bit more usages
(and a lot of false positives I've had to sift through):

* <https://github.com/nightscape/simpletask-android/blob/c8e65dcad02212640c27a911213e76381ee94cf8/src/main/java/nl/mpcjanssen/simpletask/util/Util.java#L375>
  here, `Period(0).plusDays(amount)` is just a substitute for constructing the
  required period.
* <https://github.com/kovacseni/training-solutions/blob/834e4f86fc8d403249913256a64918250d3434ed/src/main/java/dateperiod/PensionCalculator.java#L28>
  just something weird. Thankfully, this looks like dead code.
* <https://github.com/trinhvantu/cmdbuild-3.3.2-src/blob/a2992f24b3401ba7d25d9af2625999c9b903ceb9/utils/lang/src/main/java/org/cmdbuild/utils/date/Interval.java#L25>
  `Period` and `Duration` are normalized together.
* <https://github.com/WebDataConsulting/billing/blob/cd0c64e933705f8cbec3bbe7d467512a5e61edea/src/java/com/sapienter/jbilling/server/util/CalendarUtils.java#L171>
  deprecated and dead code.

2024-11-25
----------

I feel dumb today, so I've beeing doing the menial work I've stored exactly for
the state of not being able to do anything more important.

Now that I've gone into groove a bit, let's research
<https://github.com/Kotlin/kotlinx-datetime/issues/461>. It's an unpleasant
issue: it's clearly difficult or impossible to implement using the today's API;
it's clearly possible for us to provide an API to do that (after
https://github.com/Kotlin/kotlinx-datetime/pull/453 is merged, ensuring it's
possible for us to access the list of DST transitions on JS as well, which,
it turns out, we can't do currently!); lastly, the task seems very narrow and
doesn't lend itself well to providing an orthogonal API.

So, we need
`fun instantsBetween(start: LocalDateTime, endExclusive: LocalDateTime): List<InstantRange>`.
Let's start with the fact we have no `InstantRange`.
Ok, sure; `Pair<Instant, Instant>`.
How would this function be implemented if not via the direct access to the
sequence of all transitions?

The Foundation library has a function that could help with that:
`TimeZone.nextDaylightSavingTimeTransition`.

... but then a prolonged meeting cut the day short.

2024-11-26
----------

<https://youtrack.jetbrains.com/issue/IJPL-172305> was reported for me to
investigate.
For some reason, the issue is private, but after looking at the provided logs,
I've arrived at this problem statement
(<https://github.com/Kotlin/kotlinx.coroutines/issues/4280>): in out-of-memory
scenarios, the internal invariants of coroutines can be violated.

Just what can we do about this? Kotlin does not reify allocations:
the list of all allocating places would be easy to acquire in something like
Zig, where allocators need to be passed around manually, but in Kotlin,
an allocation can happen anywhere and look just like a function call.

I wonder if we can supply a custom allocator to the Kotlin/JVM implementation,
and there, say something like: "If this allocation happens in the coroutine
internals, fail the whole program after printing a stacktrace, and otherwise
delegate to the proper allocator".

A quick search for "jvm replace default allocator" and AI summarization on
<https://kagi.com/> shows (possibly incorrectly) that the only way to use a
custom allocator on the JVM is to rebuild the JVM implementation.

If there is no option to use a Java class as an allocator, I doubt I could
easily get access to the stacktrace of the place calling the allocator from
inside the allocator, even if I did rebuild the JVM. Let's consider some other
approaches first.

Maybe a profiler could help? I didn't check whether it could demonstrate
allocations performed inside each function, but if it could, this would solve
the issue of finding allocations in our internals.

<https://www.jetbrains.com/help/idea/cpu-and-allocation-profiling-basic-concepts.html>
oh no, profiling is only available for IDEA Ultimate.
I'll try to figure out how to run a profiler from the command line, but if
that fails, I guess I'll try to install Ultimate. As a JetBrains employee,
I get access to it automatically.

<https://github.com/async-profiler/async-profiler?tab=readme-ov-file#quick-start>
describes how to attach to a running Java process using its PID.
There's also a first-party `gradle-profiler` tool by Gradle
<https://github.com/gradle/gradle-profiler> that automates using profilers,
but it's for benchmarking of Gradle builds... does this mean the management of
building gets profiled? What is this regarding: the code my Gradle build
is running (the actual build) or the code that constitutes my Gradle config?

I need a dumb and straightforward answer, but I don't find one.
I guess that dumb answer is using an IDE. Though I'd have to move off
Community Edition sooner or later...

Haha. I've opened JetBrains Toolbox, clicked "Install" on IDEA Ultimate, pressed
"activate" and chose "license server" as the activation method.
I don't remember the server URL, but there's a helpful "Discover server" button.
I've pressed it and immediately, the server was found. I've imported my
settings from the Community Edition and literally started typing in my journal:
"Wow, it went surprisingly smoothly". Well, not so fast. Now the IDE wants me to
activate it again, but this time, there is no "Detect server" button.
I should have written down the server it had found earlier.
Things like this are why I avoid using IDEs when I can help it: there is too
much functionality for anything to work reliably.

Ok, after a couple more minutes, I did find the activation server URL in our
internal docs.
Let me try running the usual `kotlinx-coroutines-core` JVM test suite with the
profiler...

Oh, cool, tests are failing. Let's stop the tests... I get an error message:

```
Profiler error: Invalid file format: org.openjdk.jmc.flightrecorder.internal.InvalidJfrFileException: No readable chunks in recording
```

There's an inviting "open" button below the message,
but all it seems to do is open this same message, but in a bigger window.
I guess it would be useful if the message were to long to fit in the small
notification popup?

Ok, I've restarted the `kotlinx-coroutines-core:jvmTest` tests, and now I'm
going to cancel the execution almost immediately, for the tests not to have
any time to fail (I guess there is some conflict between our tests and the
profiler?)...

I've accidentally clicked "run with coverage" instead of "run with profiling",
and coverage also doesn't work: for some reason, it only shows me the coverage
of `kotlinx.coroutines.debug.junit5`, which is in a whole separate Gradle
subproject and has nothing to do with what I'm doing.

Alright, once again, run with *profiling*.

The same error.

Well, I guess if the profiler did work, it would help us with this issue, but
as is, let's just put a lid on it. It's a datetime week for me anyway,
I shouldn't waste too much time on detours. Next time, I could run a profiler
on the company-issued MacBook instead of the Linux PC I'm using as the daily
driver. Because of how homogenous MacOS is, it's easier for the IDE authors to
test their code, so in my experience, IDEA works more reliably there.
Though I still wouldn't bet on it: `kotlinx.coroutines` is a much more complex
project that abuses the Kotlin compiler in various ways, so it's expected that
the tooling is going to run into some failure modes that normal user projects
never have to encounter.

By the way, my issues are not with some nightly version, it's simply
`IntelliJ IDEA 2024.3 (Ultimate Edition)`.

As for my actual task for today, it's to further research what the migration
path from `kotlinx.datetime.Instant` to `kotlin.time.Instant` would look like.
Some findings:

```kotlin
package kotlinx.datetime

@Deprecated("Use kotlin.time.Instant")
public typealias Instant = kotlin.time.Instant

// for binary compatibility
@PublishedApi
internal class Instant
```

This doesn't work, no matter how many tricks I try to use to make the compiler
accept my code: the compiler fails with "Redeclaration" in any case.

```kotlin
@JvmName("Instant")
internal class OldInstant
```

also doesn't work, because `JvmName` can't be applied to classes.

This still wouldn't be a dealbreaker if not for the fact that we can't even
keep the existing `kotlinx.datetime.Instant`-returning functions while also
adding `kotlin.time.Instant`-returning functions with the same name. This
would work on the JVM and JS, but not on Native, where defining two functions
with equivalent signatures save for their return type is forbidden and
results in a declaration clash.

We *could* add a function with a default `Unit` parameter, though...

```kotlin
fun LocalDateTime.toInstant(timeZone: TimeZone, unused: Unit = Unit):
    kotlin.time.Instant
```

This would look terrible, but it would preserve binary compatibility.
The only affected functions are these ones:

```kotlin
fun String.toInstant(): Instant

class TimeZone {
    fun LocalDateTime.toInstant(): Instant
}

fun LocalDateTime.toInstant(timeZone: TimeZone): Instant
fun LocalDateTime.toInstant(offset: UtcOffset): Instant
fun LocalDate.atStartOfDayIn(timeZone: TimeZone): Instant

// Darwin
fun NSDate.toKotlinInstant(): Instant

// JS
fun Date.toKotlinInstant(): Instant

// Java.Time
fun java.time.Instant.toKotlinInstant(): Instant
```

2024-11-27
----------

Getting back to the question of how/whether to expose the historical data
from our timezone database.

Just `List<UtcOffset>` + `List<Instant>` (the way historical tzdb data is stored
internally) is likely to be hostile to the users, as this requires doing a
binary search, an advanced technique stumping a CS freshman and a seasoned
CRUD developer alike.

Let's look through `nextDaylightSavingTimeTransition` usages.

First, Stack Overflow.

* <https://stackoverflow.com/questions/27053135/how-to-get-a-users-time-zone/27053592#27053592>
  <https://stackoverflow.com/questions/36152453/notifying-timezone-change-to-an-ios-app/36152689#36152689>
  the mention is irrelevant to the posed question.
* <https://stackoverflow.com/questions/15473801/find-dst-daylight-savings-time-timestamp-using-objective-c-c/15474051#15474051>
  asks for the day DST begins/ends.
* <https://stackoverflow.com/questions/8923383/checking-whether-the-daylight-savings-time-is-in-effect/8925488#8925488>
  wants to know if DST changes ever happen in a given timezone.
* <https://stackoverflow.com/questions/39250516/how-to-create-nsdate-that-will-be-in-dst-mode>
  wants to determine the closest date that's in DST (which could be today).
* <https://stackoverflow.com/questions/28438156/incorrect-time-string-with-nsdateformatter-with-daylight-saving-time/28448535#28448535>
  finally, a specific use case!
  The user wants to test that their code works properly when a DST transition
  happens, so they ask for the subsequent transitions to check if their code is
  future-proof.
* <https://stackoverflow.com/questions/15567706/gmt-offset-for-date-in-objective-c/15596400#15596400>
  wants to know the UTC offset at a given date, without a time component.
  This question betrays weak understanding of how DST transitions work.
* <https://stackoverflow.com/questions/35623045/equivalent-to-msdn-time-zone-information-struct-in-osx/35624104#35624104>
  the horror: the user needs to serialize timezone information as the Windows
  registry timezone structure.
  Funnily enough, `kotlinx-datetime` has enough data for this almost everywhere
  (JS and Wasm/JS being the exceptions, as they don't expose the timezone
  transition rules).
* <https://stackoverflow.com/questions/77562978/posix-timezone-string-from-swift-timezone>
  <https://stackoverflow.com/questions/69304093/how-can-i-get-gnu-lib-c-tz-format-output-from-nstimezone/69322356#69322356>
  is similar to the last one: it attempts to recreate a POSIX time zone string
  from `nextDaylightSavingTimeTransition`.
* <https://stackoverflow.com/questions/68334259/how-can-i-find-all-the-historical-offset-transition-dates-for-a-timezone/68334260#68334260>
  this is about an attempt to make a timezone browser.
  In my opinion, when writing such a thing, you're best off completely
  ignoring the platform you have an vendoring the tzdb.
  Probably even parsing the raw data on your own, as some things are lost in
  translation.
* <https://stackoverflow.com/questions/56752467/ios-get-time-zone-string-without-dst-offset>
  wants to obtain the standard offset for a timezone, I guess.

Now for <https://grep.app/>... Well, not much to say: as of writing,
<https://grep.app/search?q=.nextDaylightSavingTimeTransition> only surfaces some
test code. Looks like this functionality is deeply niche.

Hopefully, Java.Time API analysis will be more conclusive.
But this will have to wait: a design meeting about moving
`kotlinx.datetime.Instant` to `kotlin.time.Instant` is about to start.

2025-01-07
----------

Back from the New-Year vacation.

Let's start with going through my e-mails.

<https://github.com/Kotlin/kotlinx-datetime/discussions/237#discussioncomment-11698546>
seems like a pure case of tech support.

<https://youtrack.jetbrains.com/issue/KT-74111> this is trickier.
My initial impulse is to unassign myself from the issue, as
`kotlin.coroutines.intrinsics.CoroutineSingletons`, the source of the crash, has
nothing to do with our library. The original poster also believes the library is
not at fault. Let's simplify the work of the compiler people,
though, and provide a reproducer independent of the library.

```kotlin
import kotlinx.coroutines.delay

suspend fun main() {
    crash(item = Test())
}


class Test {
    suspend operator fun invoke(): Int {
        delay(10)
        return 1
    }
}

private suspend inline fun crash(item: Test) {
    println(item())
}
```

This only has one `kotlinx.coroutines` method, and a fairly simple on at that.

Stage two: replacing `delay` with its definition and simplifying it to use as
little of the coroutines machinery as possible.

```kotlin
suspend fun main() {
    crash(item = Test())
}


class Test {
    suspend operator fun invoke(): Int {
        suspendCancellableCoroutine sc@ { cont: CancellableContinuation<Unit> ->
            thread {
                cont.resumeWith(Result.success(Unit))
            }
        }
        return 1
    }
}

private suspend inline fun crash(item: Test) {
    println(item())
}
```

Now, there is still only one method, and it's `suspendCancellableCoroutine`.
Let's replace it with its definition as well.

Ok, done.

<https://github.com/Kotlin/kotlinx.coroutines/issues/2455#issuecomment-2569770713>
should be an easy "bug"fix.

<https://youtrack.jetbrains.com/issue/KT-73919> no idea what to do about this,
the issue is too fuzzy. I've tried to answer to the best interpretation of the
question, but you never know.

I got a spam report!
<https://github.com/Kotlin/kotlinx.coroutines/discussions/4315>
Sure does look non-actionable.

<https://github.com/Kotlin/kotlinx.coroutines/pull/4320> looks like it can be
reviewed quickly. I'll keep the tab open and go back to it when I'm done
processing the correspondence.

Alright, now I need to go through the things that don't end up in my e-mail:
GitHub notifications about newly opened issues or comments for
`kotlinx-datetime` and `kotlinx.coroutines` issues I haven't subscribed to.

<https://github.com/Kotlin/kotlinx-datetime/issues/468> is a feature request
that doesn't explain how the feature would help with expressing the business
requirements.

<https://github.com/Kotlin/kotlinx.coroutines/issues/4322> is a very tricky
problem that is a restatement of the fact that `SharedFlow` and `StateFlow` do
not meaningfully react to their scope being cancelled.
After much research and deliberation, I did find an answer I can stand behind:
<https://github.com/Kotlin/kotlinx.coroutines/issues/4322#issuecomment-2574897582>

Then there are requests like this
<https://github.com/Kotlin/kotlinx.coroutines/issues/4319> that don't provide
enough information about a "bug" and leave you guessing what went wrong.
Each such request takes 20+ minutes to process: you have to clean up the code
(make it runnable), try out inserting various missing pieces to reproduce the
issue, and if you think there's a real bug hiding there, you also spend some
time guessing what could have gone wrong.

<https://github.com/Kotlin/kotlinx.coroutines/pull/4314> this looks like the
same kind of nonsense initially: ok, we compile a module-info file twice,
so what?
After looking at
<https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-debug/1.10.1/kotlinx-coroutines-debug-1.10.1.jar>
to make sure, I notice that there are *two files with the same name* in the jar:
```
2024-12-20 15:12:38 .....          542          305  META-INF/versions/9/module-info.class
2024-12-20 15:12:38 .....          542          305  META-INF/versions/9/module-info.class
```
This *is* nonsense, but the poster seems entirely correct:
there is some sort of conflict involved here.
After the proposed fix, the zip file indeed only contains a single entry.

<https://github.com/Kotlin/kotlinx.coroutines/pull/4312> it's always painful to
close PRs that clearly took some effort, so it's always preferred to file an
issue first. This PR is good, but the set of compromises it proposes seems like
it could be improved on after a discussion.

<https://github.com/Kotlin/kotlinx.coroutines/issues/4316> it's not painful to
close issues related to the IDE problems, as I'm pretty certain opening a new
issue on YouTrack is the optimal course of action. I could open an issue on the
user's behalf (and sometimes I do when the full information is provided, but
people who go to https://github.com/Kotlin/kotlinx.coroutines/issues to complain
about IDE issues are often novice programmers and don't do that), but the user
still has to register on YouTrack to subscribe to that issue.

... modulo small things, I think I'm done reading through the two-week backlog
of notifications. Only kotlinlang.slack.com is remaining.
For now, I'll review <https://github.com/Kotlin/kotlinx.coroutines/pull/4320>.

2025-01-08
----------

Ok, I've read through all of the correspondence, am done with the tech support
chores, and am ready to do some productive work.

Starting small, with some documentation improvements:
<https://github.com/Kotlin/kotlinx-datetime/pull/470>

Then, almost as small, with error message improvements:
<https://github.com/Kotlin/kotlinx-datetime/issues/471>
Well, this is a problem as big or small as we want it to be.

The issue is: when formatting something, we optimistically assume that the
required fields are all there, and if not, throw an exception with just the
name of the field as seen in our Kotlin code. For trivial things like
`hour`, this works well. You try to format a `DateTimeComponents` object, but
get "Field `hour` is not set"? Well, you know what you should do.

An obvious thing to do is to rename all fields in our code to match the
user-visible field names, and also to check for this.
This is a good first step and is well worth taking:
<https://github.com/Kotlin/kotlinx-datetime/pull/472>
There is an architectural problem hiding underneath, though.
For no reason at all, formatting `LocalTime` with our formatting API has to
copy all fields into another data structure with nullable fields, and for every
field access, to branch on whether it's actually `null`. While `null` checks are
*maybe* not a huge issue (everything is nullable on the JVM, so it has to check
for `null` on the field access in any scenario), copying is entirely
unnecessary.

I get an outline of a better solution: make nullability of fields a
responsibility of the entity that's being formatted; for normal domain objects
like `LocalTime`, just expose a non-nullable field, whereas for
`DateTimeComponents`, expose a view of it that promises non-nullable values and
throws a readable, meaningful error if the field actually is `null`. This would
give us more flexibility: we would be able to say things like
`The field 'hourOfAmPm' is not set. Note that setting the field 'hour' does not set 'hourOfAmPm' automatically.`
without breaking the separation of concerns.

Oh, cool, the CI broke on Windows.

```
kotlin.AssertionError: Expected 2029-03-31T22:59:59.998, got 2029-03-31T21:59:59.998 in zone Greenland Standard Time at 2029-04-01T00:59:59.998Z (our guess at the offset is -03:00).
The rules are -03:00 until 2004-03-28T01:00:00Z, -02:00 until 2004-10-31T01:00:00Z, -03:00 until 2005-03-27T01:00:00Z, -02:00 until 2005-10-30T01:00:00Z, -03:00 until 2006-03-26T01:00:00Z, -02:00 until 2006-10-29T01:00:00Z, -03:00 until 2007-03-25T01:00:00Z, -02:00 until 2007-10-28T01:00:00Z, -03:00 until 2008-03-30T01:00:00Z, -02:00 until 2008-10-26T01:00:00Z, -03:00 until 2009-03-29T01:00:00Z, -02:00 until 2009-10-25T01:00:00Z, -03:00 until 2010-03-28T01:00:00Z, -02:00 until 2010-10-31T01:00:00Z, -03:00 until 2011-03-27T01:00:00Z, -02:00 until 2011-10-30T01:00:00Z, -03:00 until 2012-03-25T01:00:00Z, -02:00 until 2012-10-28T01:00:00Z, -03:00 until 2013-03-31T01:00:00Z, -02:00 until 2013-10-27T01:00:00Z, -03:00 until 2014-03-30T01:00:00Z, -02:00 until 2014-10-26T01:00:00Z, -03:00 until 2015-03-29T01:00:00Z, -02:00 until 2015-10-25T01:00:00Z, -03:00 until 2016-03-27T01:00:00Z, -02:00 until 2016-10-30T01:00:00Z, -03:00 until 2017-03-26T01:00:00Z, -02:00 until 2017-10-29T01:00:00Z, -03:00 until 2018-03-25T01:00:00Z, -02:00 until 2018-10-28T01:00:00Z, -03:00 until 2019-03-31T01:00:00Z, -02:00 until 2019-10-27T01:00:00Z, -03:00 until 2020-03-29T01:00:00Z, -02:00 until 2020-10-25T01:00:00Z, -03:00 until 2021-03-28T01:00:00Z, -02:00 until 2021-10-31T01:00:00Z, -03:00 until 2022-03-27T01:00:00Z, -02:00 until 2022-10-30T01:00:00Z, -03:00 until 2023-03-26T01:00:00Z, -02:00 until 2023-10-29T01:00:00Z, -03:00 until 2024-03-31T01:00:00Z, -02:00 until 2024-10-27T01:00:00Z, -03:00 until 2025-03-30T01:00:00Z, -02:00 until 2025-10-26T01:00:00Z, -03:00 until 2026-03-29T01:00:00Z, -02:00 until 2026-10-25T01:00:00Z, -03:00 until 2027-01-01T03:00:00Z, then -03:00, after that transitioning from -03:00 to -02:00 on MARCH, the last SATURDAY, 22:00, wall clock offset, transitioning from -02:00 to -03:00 on OCTOBER, the last SATURDAY, 23:00, wall clock offset, and the offsets throughout the year according to Windows are: [(2029-01-01, -10800), (2029-01-02, -10800), (2029-01-03, -10800), (2029-01-04, -10800), (2029-01-05, -10800), (2029-01-06, -10800), (2029-01-07, -10800), (2029-01-08, -10800), (2029-01-09, -10800), (2029-01-10, -10800), (2029-01-11, -10800), (2029-01-12, -10800), (2029-01-13, -10800), (2029-01-14, -10800), (2029-01-15, -10800), (2029-01-16, -10800), (2029-01-17, -10800), (2029-01-18, -10800), (2029-01-19, -10800), (2029-01-20, -10800), (2029-01-21, -10800), (2029-01-22, -10800), (2029-01-23, -10800), (2029-01-24, -10800), (2029-01-25, -10800), (2029-01-26, -10800), (2029-01-27, -10800), (2029-01-28, -10800), (2029-01-29, -10800), (2029-01-30, -10800), (2029-01-31, -10800), (2029-02-01, -10800), (2029-02-02, -10800), (2029-02-03, -10800), (2029-02-04, -10800), (2029-02-05, -10800), (2029-02-06, -10800), (2029-02-07, -10800), (2029-02-08, -10800), (2029-02-09, -10800), (2029-02-10, -10800), (2029-02-11, -10800), (2029-02-12, -10800), (2029-02-13, -10800), (2029-02-14, -10800), (2029-02-15, -10800), (2029-02-16, -10800), (2029-02-17, -10800), (2029-02-18, -10800), (2029-02-19, -10800), (2029-02-20, -10800), (2029-02-21, -10800), (2029-02-22, -10800), (2029-02-23, -10800), (2029-02-24, -10800), (2029-02-25, -10800), (2029-02-26, -10800), (2029-02-27, -10800), (2029-02-28, -10800), (2029-03-01, -10800), (2029-03-02, -10800), (2029-03-03, -10800), (2029-03-04, -10800), (2029-03-05, -10800), (2029-03-06, -10800), (2029-03-07, -10800), (2029-03-08, -10800), (2029-03-09, -10800), (2029-03-10, -10800), (2029-03-11, -10800), (2029-03-12, -10800), (2029-03-13, -10800), (2029-03-14, -10800), (2029-03-15, -10800), (2029-03-16, -10800), (2029-03-17, -10800), (2029-03-18, -10800), (2029-03-19, -10800), (2029-03-20, -10800), (2029-03-21, -10800), (2029-03-22, -10800), (2029-03-23, -10800), (2029-03-24, -10800), (2029-03-25, -10800), (2029-03-26, -7200), (2029-03-27, -7200), (2029-03-28, -7200), (2029-03-29, -7200), (2029-03-30, -7200), (2029-03-31, -7200), (2029-04-01, -7200), (2029-04-02, -7200), (2029-04-03, -7200), (2029-04-04, -7200), (2029-04-05, -7200), (2029-04-06, -7200), (2029-04-07, -7200), (2029-04-08, -7200), (2029-04-09, -7200), (2029-04-10, -7200), (2029-04-11, -7200), (2029-04-12, -7200), (2029-04-13, -7200), (2029-04-14, -7200), (2029-04-15, -7200), (2029-04-16, -7200), (2029-04-17, -7200), (2029-04-18, -7200), (2029-04-19, -7200), (2029-04-20, -7200), (2029-04-21, -7200), (2029-04-22, -7200), (2029-04-23, -7200), (2029-04-24, -7200), (2029-04-25, -7200), (2029-04-26, -7200), (2029-04-27, -7200), (2029-04-28, -7200), (2029-04-29, -7200), (2029-04-30, -7200), (2029-05-01, -7200), (2029-05-02, -7200), (2029-05-03, -7200), (2029-05-04, -7200), (2029-05-05, -7200), (2029-05-06, -7200), (2029-05-07, -7200), (2029-05-08, -7200), (2029-05-09, -7200), (2029-05-10, -7200), (2029-05-11, -7200), (2029-05-12, -7200), (2029-05-13, -7200), (2029-05-14, -7200), (2029-05-15, -7200), (2029-05-16, -7200), (2029-05-17, -7200), (2029-05-18, -7200), (2029-05-19, -7200), (2029-05-20, -7200), (2029-05-21, -7200), (2029-05-22, -7200), (2029-05-23, -7200), (2029-05-24, -7200), (2029-05-25, -7200), (2029-05-26, -7200), (2029-05-27, -7200), (2029-05-28, -7200), (2029-05-29, -7200), (2029-05-30, -7200), (2029-05-31, -7200), (2029-06-01, -7200), (2029-06-02, -7200), (2029-06-03, -7200), (2029-06-04, -7200), (2029-06-05, -7200), (2029-06-06, -7200), (2029-06-07, -7200), (2029-06-08, -7200), (2029-06-09, -7200), (2029-06-10, -7200), (2029-06-11, -7200), (2029-06-12, -7200), (2029-06-13, -7200), (2029-06-14, -7200), (2029-06-15, -7200), (2029-06-16, -7200), (2029-06-17, -7200), (2029-06-18, -7200), (2029-06-19, -7200), (2029-06-20, -7200), (2029-06-21, -7200), (2029-06-22, -7200), (2029-06-23, -7200), (2029-06-24, -7200), (2029-06-25, -7200), (2029-06-26, -7200), (2029-06-27, -7200), (2029-06-28, -7200), (2029-06-29, -7200), (2029-06-30, -7200), (2029-07-01, -7200), (2029-07-02, -7200), (2029-07-03, -7200), (2029-07-04, -7200), (2029-07-05, -7200), (2029-07-06, -7200), (2029-07-07, -7200), (2029-07-08, -7200), (2029-07-09, -7200), (2029-07-10, -7200), (2029-07-11, -7200), (2029-07-12, -7200), (2029-07-13, -7200), (2029-07-14, -7200), (2029-07-15, -7200), (2029-07-16, -7200), (2029-07-17, -7200), (2029-07-18, -7200), (2029-07-19, -7200), (2029-07-20, -7200), (2029-07-21, -7200), (2029-07-22, -7200), (2029-07-23, -7200), (2029-07-24, -7200), (2029-07-25, -7200), (2029-07-26, -7200), (2029-07-27, -7200), (2029-07-28, -7200), (2029-07-29, -7200), (2029-07-30, -7200), (2029-07-31, -7200), (2029-08-01, -7200), (2029-08-02, -7200), (2029-08-03, -7200), (2029-08-04, -7200), (2029-08-05, -7200), (2029-08-06, -7200), (2029-08-07, -7200), (2029-08-08, -7200), (2029-08-09, -7200), (2029-08-10, -7200), (2029-08-11, -7200), (2029-08-12, -7200), (2029-08-13, -7200), (2029-08-14, -7200), (2029-08-15, -7200), (2029-08-16, -7200), (2029-08-17, -7200), (2029-08-18, -7200), (2029-08-19, -7200), (2029-08-20, -7200), (2029-08-21, -7200), (2029-08-22, -7200), (2029-08-23, -7200), (2029-08-24, -7200), (2029-08-25, -7200), (2029-08-26, -7200), (2029-08-27, -7200), (2029-08-28, -7200), (2029-08-29, -7200), (2029-08-30, -7200), (2029-08-31, -7200), (2029-09-01, -7200), (2029-09-02, -7200), (2029-09-03, -7200), (2029-09-04, -7200), (2029-09-05, -7200), (2029-09-06, -7200), (2029-09-07, -7200), (2029-09-08, -7200), (2029-09-09, -7200), (2029-09-10, -7200), (2029-09-11, -7200), (2029-09-12, -7200), (2029-09-13, -7200), (2029-09-14, -7200), (2029-09-15, -7200), (2029-09-16, -7200), (2029-09-17, -7200), (2029-09-18, -7200), (2029-09-19, -7200), (2029-09-20, -7200), (2029-09-21, -7200), (2029-09-22, -7200), (2029-09-23, -7200), (2029-09-24, -7200), (2029-09-25, -7200), (2029-09-26, -7200), (2029-09-27, -7200), (2029-09-28, -7200), (2029-09-29, -7200), (2029-09-30, -7200), (2029-10-01, -7200), (2029-10-02, -7200), (2029-10-03, -7200), (2029-10-04, -7200), (2029-10-05, -7200), (2029-10-06, -7200), (2029-10-07, -7200), (2029-10-08, -7200), (2029-10-09, -7200), (2029-10-10, -7200), (2029-10-11, -7200), (2029-10-12, -7200), (2029-10-13, -7200), (2029-10-14, -7200), (2029-10-15, -7200), (2029-10-16, -7200), (2029-10-17, -7200), (2029-10-18, -7200), (2029-10-19, -7200), (2029-10-20, -7200), (2029-10-21, -7200), (2029-10-22, -7200), (2029-10-23, -7200), (2029-10-24, -7200), (2029-10-25, -7200), (2029-10-26, -7200), (2029-10-27, -7200), (2029-10-28, -7200), (2029-10-29, -10800), (2029-10-30, -10800), (2029-10-31, -10800), (2029-11-01, -10800), (2029-11-02, -10800), (2029-11-03, -10800), (2029-11-04, -10800), (2029-11-05, -10800), (2029-11-06, -10800), (2029-11-07, -10800), (2029-11-08, -10800), (2029-11-09, -10800), (2029-11-10, -10800), (2029-11-11, -10800), (2029-11-12, -10800), (2029-11-13, -10800), (2029-11-14, -10800), (2029-11-15, -10800), (2029-11-16, -10800), (2029-11-17, -10800), (2029-11-18, -10800), (2029-11-19, -10800), (2029-11-20, -10800), (2029-11-21, -10800), (2029-11-22, -10800), (2029-11-23, -10800), (2029-11-24, -10800), (2029-11-25, -10800), (2029-11-26, -10800), (2029-11-27, -10800), (2029-11-28, -10800), (2029-11-29, -10800), (2029-11-30, -10800), (2029-12-01, -10800), (2029-12-02, -10800), (2029-12-03, -10800), (2029-12-04, -10800), (2029-12-05, -10800), (2029-12-06, -10800), (2029-12-07, -10800), (2029-12-08, -10800), (2029-12-09, -10800), (2029-12-10, -10800), (2029-12-11, -10800), (2029-12-12, -10800), (2029-12-13, -10800), (2029-12-14, -10800), (2029-12-15, -10800), (2029-12-16, -10800), (2029-12-17, -10800), (2029-12-18, -10800), (2029-12-19, -10800), (2029-12-20, -10800), (2029-12-21, -10800), (2029-12-22, -10800), (2029-12-23, -10800), (2029-12-24, -10800), (2029-12-25, -10800), (2029-12-26, -10800), (2029-12-27, -10800), (2029-12-28, -10800), (2029-12-29, -10800), (2029-12-30, -10800), (2029-12-31, -10800)]; the raw data for the recurring rules is b400000000000000c4ffffff00000a0006000500170000000000000000000300060005001600000000000000
```

I'll have to look into this. So far, Windows support was the most problematic
among all Native targets for both `kotlinx-datetime` (where it's exposing an
unrealiable timezone database that also seems to have some hidden rules not
fully expressed in the registry contents and not accessible by any other means)
and `kotlinx.coroutines` (where its thread scheduling is so unreliable that our
tests that check the relationship between our time-based functions like
`withTimeout` and real time will often fail even if we do add generous leeway).

Ok, so the transition according to Windows has happened somewhere around
2029-03-25; the rules state (I'll double-check what the registry says exactly)
that it's the last Saturday of March that has the transition... so, the
31st of March in 2029. It also looks like our algorithm worked fine for 2028.
Is this yet another magical special case?

Ok, let's read the string from the registry and double-check it with the docs.
`b400000000000000c4ffffff00000a0006000500170000000000000000000300060005001600000000000000`
should be interpreted as `REG_TZI_FORMAT`
(see <https://learn.microsoft.com/en-us/windows/win32/api/timezoneapi/ns-timezoneapi-time_zone_information>)...
You know, screw this, I'm doing this a couple of times per month at this point,
I could automate this.

```haskell
{-# LANGUAGE RecordWildCards #-}
import Control.Monad.State
import Data.Word
import Data.Int

data SystemTime = SystemTime
                { year :: Word16
                , month :: Word16
                , dayOfWeek :: Word16
                , day :: Word16
                , hour :: Word16
                , minute :: Word16
                , second :: Word16
                , millisecond :: Word16
                } deriving Show

data RegTziFormat = RegTziFormat
                  { bias :: Int32
                  , standardBias :: Int32
                  , daylightBias :: Int32
                  , standardDate :: SystemTime
                  , daylightDate :: SystemTime
                  } deriving Show

parseRegTziFormat :: String -> RegTziFormat
parseRegTziFormat = evalState $ do
                       bias <- parseLong
                       standardBias <- parseLong
                       daylightBias <- parseLong
                       standardDate <- parseSystemTime
                       daylightDate <- parseSystemTime
                       pure $ RegTziFormat { .. }
  where parseNBytes n = read . ("0x" ++) . fixEndianness <$> state (splitAt (2 * n))
        fixEndianness = concat . reverse . groupBy2
        groupBy2 [] = []
        groupBy2 (a:b:xs) = [a, b] : groupBy2 xs
        parseLong :: State String Int32 
        parseLong = parseNBytes 4
        parseWord :: State String Word16
        parseWord = parseNBytes 2
        parseSystemTime = do year <- parseWord
                             month <- parseWord
                             dayOfWeek <- parseWord
                             day <- parseWord
                             hour <- parseWord
                             minute <- parseWord
                             second <- parseWord
                             millisecond <- parseWord
                             pure $ SystemTime { .. }
```

The output:

```
parseRegTziFormat "b400000000000000c4ffffff00000a0006000500170000000000000000000300060005001600000000000000"
RegTziFormat {bias = 180, standardBias = 0, daylightBias = -60, standardDate = SystemTime {year = 0, month = 10, dayOfWeek = 6, day = 5, hour = 23, minute = 0, second = 0, millisecond = 0}, daylightDate = SystemTime {year = 0, month = 3, dayOfWeek = 6, day = 5, hour = 22, minute = 0, second = 0, millisecond = 0}}
```

:shrug:

2025-01-09
----------

Have spent most of the morning writing this:
<https://github.com/Kotlin/kotlinx-datetime/issues/325#issuecomment-2579629809>

After rereading the original attempt to translate Joda code to
`kotlinx-datetime`, I've decided to return to a pet peeve of mine and finally
file
<https://youtrack.jetbrains.com/issue/KT-74317/Deprecate-Duration.Companion.days-and-Duration.inWholeDays>

After that, I rewrote the test for the Windows registry data so that it emits
the information about all timezones and years that don't match instead of
stopping at the first mismatch; I've also compacted the error output format
so that there is less to parse with your eyes:
<https://github.com/Kotlin/kotlinx-datetime/commit/1e339e7f155d0451203721efb62a4ab77db4583d>.
This should allow us to see the bigger picture of the incompatibility.
The code is very sloppy, but so far, the reason this test has failed was not
the code quality but always some strange Windows behavior.

<https://github.com/Kotlin/kotlinx-datetime/commit/fefe3e7c898a44f9027e3d54341ebf9ba02e0d45>
is an attempt to implement Ilya's suggestion at
<https://github.com/Kotlin/kotlinx-datetime/issues/440#issuecomment-2445694279>.
The last time I tried this, I observed a failure. Let's dig in.

Only one timezone is broken!

```kotlin
IncompatibilityWithWindowsRegistry(
  timeZoneName=Central Brazilian Standard Time,
  dataOnAffectedYears=[Overlap(start=1971-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1971-11-02T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1972-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1972-11-07T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1973-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1973-11-06T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1974-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1974-11-05T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1975-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1975-11-04T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1977-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1977-11-01T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1978-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1978-11-07T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1979-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1979-11-06T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1980-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1980-11-04T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1982-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1982-11-02T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1983-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1983-11-01T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1984-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1984-11-06T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1985-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1985-11-05T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1986-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1986-11-04T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1988-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1988-11-01T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1989-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1989-11-07T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1990-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1990-11-06T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1991-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1991-11-05T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1992-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1992-11-03T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1993-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1993-11-02T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1994-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1994-11-01T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1995-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1995-11-07T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1996-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1996-11-05T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1997-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1997-11-04T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=1999-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=1999-11-02T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=2000-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=2000-11-07T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=2001-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=2001-11-06T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=2002-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=2002-11-05T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00), Overlap(start=2003-01-01T03:00:00.000712539Z, offsetBefore=-03:00, offsetAfter=-04:00), Gap(start=2003-11-04T04:00:00.000859268Z, offsetBefore=-04:00, offsetAfter=-03:00)],
  registryData=f000000000000000c4ffffff0000000000000000000000000000000000000000000000000000000000000000,
  mismatches=[Mismatch(ourGuess=1971-01-06T23:59:59.998, windowsGuess=1971-01-06T22:59:59.998, instant=1971-01-07T02:59:59.998Z), Mismatch(ourGuess=1972-01-05T23:59:59.998, windowsGuess=1972-01-05T22:59:59.998, instant=1972-01-06T02:59:59.998Z), Mismatch(ourGuess=1973-01-03T23:59:59.998, windowsGuess=1973-01-03T22:59:59.998, instant=1973-01-04T02:59:59.998Z), Mismatch(ourGuess=1974-01-02T23:59:59.998, windowsGuess=1974-01-02T22:59:59.998, instant=1974-01-03T02:59:59.998Z), Mismatch(ourGuess=1975-01-01T23:59:59.998, windowsGuess=1975-01-01T22:59:59.998, instant=1975-01-02T02:59:59.998Z), Mismatch(ourGuess=1977-01-05T23:59:59.998, windowsGuess=1977-01-05T22:59:59.998, instant=1977-01-06T02:59:59.998Z), Mismatch(ourGuess=1978-01-04T23:59:59.998, windowsGuess=1978-01-04T22:59:59.998, instant=1978-01-05T02:59:59.998Z), Mismatch(ourGuess=1979-01-03T23:59:59.998, windowsGuess=1979-01-03T22:59:59.998, instant=1979-01-04T02:59:59.998Z), Mismatch(ourGuess=1980-01-02T23:59:59.998, windowsGuess=1980-01-02T22:59:59.998, instant=1980-01-03T02:59:59.998Z), Mismatch(ourGuess=1982-01-06T23:59:59.998, windowsGuess=1982-01-06T22:59:59.998, instant=1982-01-07T02:59:59.998Z), Mismatch(ourGuess=1983-01-05T23:59:59.998, windowsGuess=1983-01-05T22:59:59.998, instant=1983-01-06T02:59:59.998Z), Mismatch(ourGuess=1984-01-04T23:59:59.998, windowsGuess=1984-01-04T22:59:59.998, instant=1984-01-05T02:59:59.998Z), Mismatch(ourGuess=1985-01-02T23:59:59.998, windowsGuess=1985-01-02T22:59:59.998, instant=1985-01-03T02:59:59.998Z), Mismatch(ourGuess=1986-01-01T23:59:59.998, windowsGuess=1986-01-01T22:59:59.998, instant=1986-01-02T02:59:59.998Z), Mismatch(ourGuess=1988-01-06T23:59:59.998, windowsGuess=1988-01-06T22:59:59.998, instant=1988-01-07T02:59:59.998Z), Mismatch(ourGuess=1989-01-04T23:59:59.998, windowsGuess=1989-01-04T22:59:59.998, instant=1989-01-05T02:59:59.998Z), Mismatch(ourGuess=1990-01-03T23:59:59.998, windowsGuess=1990-01-03T22:59:59.998, instant=1990-01-04T02:59:59.998Z), Mismatch(ourGuess=1991-01-02T23:59:59.998, windowsGuess=1991-01-02T22:59:59.998, instant=1991-01-03T02:59:59.998Z), Mismatch(ourGuess=1992-01-01T23:59:59.998, windowsGuess=1992-01-01T22:59:59.998, instant=1992-01-02T02:59:59.998Z), Mismatch(ourGuess=1993-01-06T23:59:59.998, windowsGuess=1993-01-06T22:59:59.998, instant=1993-01-07T02:59:59.998Z), Mismatch(ourGuess=1994-01-05T23:59:59.998, windowsGuess=1994-01-05T22:59:59.998, instant=1994-01-06T02:59:59.998Z), Mismatch(ourGuess=1995-01-04T23:59:59.998, windowsGuess=1995-01-04T22:59:59.998, instant=1995-01-05T02:59:59.998Z), Mismatch(ourGuess=1996-01-03T23:59:59.998, windowsGuess=1996-01-03T22:59:59.998, instant=1996-01-04T02:59:59.998Z), Mismatch(ourGuess=1997-01-01T23:59:59.998, windowsGuess=1997-01-01T22:59:59.998, instant=1997-01-02T02:59:59.998Z), Mismatch(ourGuess=1999-01-06T23:59:59.998, windowsGuess=1999-01-06T22:59:59.998, instant=1999-01-07T02:59:59.998Z), Mismatch(ourGuess=2000-01-05T23:59:59.998, windowsGuess=2000-01-05T22:59:59.998, instant=2000-01-06T02:59:59.998Z), Mismatch(ourGuess=2001-01-03T23:59:59.998, windowsGuess=2001-01-03T22:59:59.998, instant=2001-01-04T02:59:59.998Z), Mismatch(ourGuess=2002-01-02T23:59:59.998, windowsGuess=2002-01-02T22:59:59.998, instant=2002-01-03T02:59:59.998Z), Mismatch(ourGuess=2003-01-01T23:59:59.998, windowsGuess=2003-01-01T22:59:59.998, instant=2003-01-02T02:59:59.998Z)]
)
```

What this means:

```
Year | Windows transition | Our transition
1971 | 1971-01-01T00:00   | 1971-01-07T00:00
1972 | 1972-01-01T00:00   | 1972-01-06T00:00
1973 | 1973-01-01T00:00   | 1973-01-04T00:00
1974 | 1974-01-01T00:00   | 1974-01-03T00:00
1975 | 1975-01-01T00:00   | 1975-01-02T00:00
1976 | no errors          | no errors
1977 | 1977-01-01T00:00   | 1977-01-06T00:00
```

So, the error is that we assume the transition happens at midnight on the
first Thursday of January, but Windows claims the transition is on the
1st of January, and in 1976, this happens to be the same day?..

Let's see what the registry holds in the first entry for this time zone.

```haskell
parseRegTziFormat "f000000000000000c4ffffff0000010004000100000000000000000000000b00020001000000000000000000"
RegTziFormat {bias = 240, standardBias = 0, daylightBias = -60
  , standardDate = SystemTime {year = 0, month = 1, dayOfWeek = 4, day = 1, hour = 0, minute = 0, second = 0, millisecond = 0}
  , daylightDate = SystemTime {year = 0, month = 11, dayOfWeek = 2, day = 1, hour = 0, minute = 0, second = 0, millisecond = 0}}
```

These do say that we're interested in the first Thursday, according to the
interpretation of `StandardDate` at
<https://learn.microsoft.com/en-us/windows/win32/api/timezoneapi/ns-timezoneapi-dynamic_time_zone_information>.

What about the recurring rules?

```haskell
parseRegTziFormat "f000000000000000c4ffffff0000000000000000000000000000000000000000000000000000000000000000"
RegTziFormat {bias = 240, standardBias = 0, daylightBias = -60
             , standardDate = SystemTime {year = 0, month = 0, dayOfWeek = 0, day = 0, hour = 0, minute = 0, second = 0, millisecond = 0}
             , daylightDate = SystemTime {year = 0, month = 0, dayOfWeek = 0, day = 0, hour = 0, minute = 0, second = 0, millisecond = 0}}
```

No, these don't match what's actually happening in Windows, either.

It's unpleasant to have to deal with this nonsense, especially when it doesn't
match the actual reality: if we are to trust
<https://en.wikipedia.org/wiki/Daylight_saving_time_in_Brazil>,
then neither the Windows registry nor the actual behavior of Windows reflects
the true state of how daylight savings time worked in Brazil.
So, I'm just going to suppress the error for this time zone in particular.

2025-01-13
----------

Felt sick on Friday and did basically no work.

Today, it's a week of coroutines. I divide my work time into "datetime weeks"
and "coroutine weeks", and on a week dedicated to a library, I prefer working on
it. This way, starvation doesn't happen. Before I introduced this scheme, it was
occasionally the case that I was so engrossed into some big, demanding project
in one library that the other library got neglected for a couple of months at a
time, giving the impression that all work has stopped.

Let's start by reviewing
<https://github.com/Kotlin/kotlinx.coroutines/pull/4327>, which turned out much
trickier to do correctly than I've assumed.

Next, I'm going to try implementing a Kotlin/Native version of
`kotlinx.coroutines` that uses
<https://github.com/Kotlin/kotlinx-atomicfu/pull/498>.
This could allow `Dispatchers.Default` and `Dispatchers.IO` on Native to use the
same scheduler that our JVM implementation uses. I'm still not sure if this is
going to end up in the actual implementation--at least for iOS, which has its
own threading specifics that are unknown to me--but in any case, this will allow
us to evaluate the new parking implementation in a more informed way.

2025-01-14
----------

I received a task to look through the issues relating to IDEA lints
("inspections") for `kotlinx.coroutines`
(<https://youtrack.jetbrains.com/issues?q=project:%20KTIJ%20tag:%20coroutine-inspections%20%23Unresolved%20>)
and decide which of them are useful.

1. <https://youtrack.jetbrains.com/issue/KTIJ-842/Inappropriate-blocking-method-call-with-Reader.ready-method>
   Did you know that `Reader.ready()` can block? I didn't, but looking at
   <https://stackoverflow.com/a/8342308>, I got a bad feeling when I saw
   "I see that the read methods use synchronize and an internal lock object."
   If the approach taken to internal consistency in `BufferedReader` is locking,
   then, to avoid parallel `read` calls from accidentally overwriting each
   other, then they must be mutually exclusive; if they stick everything under
   one lock, then `ready()` should also be under that lock.
   And sure enough: <https://pl.kotl.in/WCyUw4a_2>
   Of course, I can't imagine anyone reading from one `Reader` in multiple
   threads, so this is not actually an argument for keeping the inspection.
2. <https://youtrack.jetbrains.com/issue/KTIJ-17625/Inspection-when-a-Flow-from-kotlin.coroutines-is-not-used>
   sad but true: side effects in lazy sequences don't run unless you collect
   the sequence.
3. <https://youtrack.jetbrains.com/issue/KTIJ-17367/Inspect-to-suggest-MutableSharedFlow.asSharedFlow-instead-of-SharedFlow-upcast>
   I think this proposal is objectively good, but non-Kotlin-idiomatic.
   As of writing, the `listOf(1, 2, 3) as MutableList<Int>`  cast succeeds and
   allows modifying the contents.
   My impression is that it's not Kotlin-idiomatic to worry about the invariants
   that can be broken when client code downcasts your entity,
   especially when it costs some characters to type.
   Though now that I think about this, this can save some characters:
   ```kotlin
   // What you have to type in the explicit API mode
   val state: StateFlow<Int> = _state.asStateFlow()
   // Before the inspection
   val state: StateFlow<Int> = _state
   // After the inspection
   val state = _state.asStateFlow()
   ```
4. <https://youtrack.jetbrains.com/issue/KTIJ-30864/Missed-Possibly-blocking-call-in-non-blocking-context-when-calling-property-setter-annotated-with-Blocking>
   Uh... what's `@Blocking`?
   After an Internet search, I see this:
   <https://discuss.kotlinlang.org/t/mark-function-as-blocking/13768>
   What? Blocking calls are highlighted in the IDE?
   <https://discuss.kotlinlang.org/t/mark-function-as-blocking/13768/2>
   But when I write `runBlocking { Thread.sleep(1000) }`, I get nothing!
   After some experiments, I did manage to trigger this inspection in the old
   (non-K2) IDE mode, but only outside of suspend lambdas.
   Filed <https://youtrack.jetbrains.com/issue/KTIJ-32716/Blocking-calls-not-highlighted-in-suspend-lambdas-K2-or-anywhere>.
   I wonder how much functionality there really is in the IDE but doesn't work.
   After traversing some links, I see
   <https://youtrack.jetbrains.com/issue/KTIJ-27409/False-negative-Possibly-blocking-call-in-non-blocking-context-could-lead-to-thread-starvation-warning-not-applied-to-blocking>
   Alright, I'm digging too deep. If we just look at the issue I need to sort
   into the useful/useless buckets, I think this one, as stated, is useless.
   Setters shouldn't contain blocking code. I don't think any part of our
   style guide says so, but <https://kotlinlang.org/docs/coding-conventions.html#functions-vs-properties>
   has things about getters that are similar in spirit.
5. <https://youtrack.jetbrains.com/issue/KTIJ-19072/Redundant-Async-Call-inspection-should-insert-yield-immediately-after-withContext>
   I'm not sure what this code is trying to accomplish, but it sure does look
   scary. In any case, I'm against the proposal: if you write two `suspend`
   functions, `launch` them separately, and they stop working well together if
   a couple of suspensions are added or removed, then either you're a
   `kotlinx.coroutines` author writing a test of the form
   "unnecessary suspensions don't happen here", or the data flow in your code
   is fuzzy and ill-defined.
   Maybe we should have added slight randomization to the order in which
   `runBlocking` executes its tasks. Everyone would have to learn not to rely
   on the order in which task interleaving happens.
   The cost, of course, is that the code would be flaky.
   What's worse: badly written code that happens to work or badly written code
   that fails loudly enough that you have no choice but to improve it?
6. <https://youtrack.jetbrains.com/issue/KTIJ-17464/Warn-on-unstructured-no-CoroutineScope-protection-gap-between-getting-a-future-and-await-call-on-it>
   From reading this issue, even though it's from Roman, I didn't obtain an
   understanding of what the fix was supposed to do, so I prepared this
   text to post as a comment:
   > I don't understand the proposal. Maybe someone who starred/upvoted the
   > issue could explain it to me? I get the problem: let's say we have some
   > asynchronous process that happens outside structured concurrency, and when
   > structured concurrency asks the process to fail, the process keeps working
   > (though for ListenableFuture mentioned in the issue, the process would
   > actually get cancelled: see
   > <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-guava/kotlinx.coroutines.guava/await.html>).
   >
   > How does the proposed solution fix this, though?
   > * Before the change, the code created by foo() continues executing,
   >   even though await() throws a CancellationException,
   >   because no one cancels the future itself,
   >   only the process of awaiting it is interrupted.
   > * After the change, the code created by foo() continues executing,
   >   even though await() throws a CancellationException and the coroutine
   >   created by async gets cancelled, because, again,
   >   no one cancels the future itself.
   > Looking at the proposed solution,
   > there is one case I can see where the replacement helps:
   > * fooBar is called in a multithreaded dispatcher,
   >   so the fooBar body is executed in parallel with the async coroutines
   >   created there.
   > * f.await() is actively polling the future, blocking the thread in a
   >   non-cancellable manner. This is not what happens in the await()
   >   implementations that I know of: instead,
   >   they are cancellable and non-blocking.
   > In this scenario, yes, even though nothing can cancel code that doesn't
   > participate in cancellation cooperatively
   > (in this case, the code that's going to put the Int into the Future),
   > at least the process of waiting for it won't hang.
   > However, either it's await()'s responsibility to be susceptible to
   > cancellation (if it's a suspend fun),
   > or await() is a blocking call and shouldn't be freely called
   > in suspending code anyway.

   Before posting this, though, I asked `@qwwdfsad` if he understood what the
   problem and the solution were, and it turns out that yes, there is an obvious
   thing that I'm missing: if `foo()` creates a `Future`, then
   code `val v = foo(); something(); v.await()` is incorrect *because* `await()`
   is the thing that cancels `v`. If `something()` throws an exception, awaiting
   doesn't happen, and neither does cancellation.

   I'm not convinced this is an IDE inspection problem.
   I've filed an issue where I explain this:
   <https://github.com/Kotlin/kotlinx.coroutines/issues/4329>.

Alright, I got tired of sorting through those issues, so I'll take a break and
go back to <https://github.com/Kotlin/kotlinx.coroutines/pull/4277>.
The last thing I did before going to the vacation was to finally make that
PR build. The reason it failed to build was:

* Currently, the thread responsible for processing timers is also responsible
  for finishing tasks that other dispatchers don't want to do
  (see https://github.com/Kotlin/kotlinx.coroutines/issues/4262).
  After my changes, `Dispatchers.IO` will be responsible for that.
* `Dispatchers.IO` is implemented as `limitedParallelism(64)` over a thread
  pool shared with `Dispatchers.Default`.
* Our internal tests close and recreate that shared pool.
  If new tasks attempt to enter the thread pool while it's closed, an
  exception is thrown from `fun dispatch`.
* `limitedParallelism` never considers the possibility that a dispatch can
  fail. It keeps a counter of workers running on the wrapped dispatcher to
  ensure that no more extra workers are allocated than there needs to be.

The last piece is problematic.
I've extracted this idea into a separate PR:
<https://github.com/Kotlin/kotlinx.coroutines/pull/4330>.

Ok, back to IDE inspections.

7. <https://youtrack.jetbrains.com/issue/KTIJ-14557/Inspection-for-Android-when-using-Dispatchers.Main-without-kotlinx-coroutines-android-dependency>
   This is somewhat straightforward.
8. <https://youtrack.jetbrains.com/issue/KTIJ-13441/Suggest-coroutineScope-or-runBlocking-when-trying-to-invoke-coroutine-scope-extension>
   This is much less straightforward, as the concern is real, but an
   implementation that suggests automatically introducing `runBlocking` leaves
   me wary.

2025-01-15
----------

Ok, back to work.

Another inspection: <https://youtrack.jetbrains.com/issue/KTIJ-20974/Inspection-Possibly-blocking-call-in-non-blocking-context-could-lead-to-thread-starvation-suggests-to-use-.flowOnDispatchers.IO>
This one is very tricky to get right, but somehow, the IDE managed to do it!
I didn't really try to break it, but the code I wrote naturally was highlighted
like I would ideally expect it to.

Looks like I've already looked through the easy ones? Each one is harder to
judge than the last. <https://youtrack.jetbrains.com/issue/KTIJ-12751/Dont-extract-suspend-functions-with-CoroutineScope-receiver>

Or maybe not, this one was easy for a change:
<https://youtrack.jetbrains.com/issue/KTIJ-12956/Replace-with-GlobalScope.launch-generates-invalid-call-to-deprecated-method>

Will go back to trying port `kotlinx.coroutines` to the new parking API.

2025-01-16
----------

Still working on the `kotlinx.coroutines` with the new parking API. I've managed
to port the scheduler itself, but not the code that would actually test the
scheduler, as that code relies heavily on what the JVM provides. I'll have to
reimplement a couple of basic things like latches and barriers.

2025-01-22
----------

Was busy looking into how `kotlinx.coroutines` can be tested with the new
LinCheck functionality for the past couple of days.

Now, back to datetime.

I'm wondering if it makes sense to add a combinator:

```kotlin
/**
 * Finds the [Instant] that has the updated [LocalDate] and the same [LocalTime] as `this` in [timeZone].
 *
 * [action] is invoked with the [LocalDate] of this [Instant] in the given [timeZone], and
 * its return value is used as the new date.
 *
 * Note that the conversion back to [Instant] is not always well-defined.
 * There can be the following possible situations:
 * - Only one instant has this datetime value in the [timeZone]. In this case, the conversion is unambiguous.
 * - No instant has this datetime value in the [timeZone].
 *   Such a situation appears when the time zone experiences a transition from a lesser to a greater offset.
 *   In this case, the conversion is performed with the lesser (earlier) offset,
 *   as if the time gap didn't occur yet.
 * - Two possible instants can have these datetime components in the [timeZone].
 *   In this case, the earlier instant is returned.
 *
 * This function behaves differently from using date-based operations on [Instant] directly if there are
 * several operations happening in a row.
 * For example, `instant.withDate(zone) { it.plus(1, DateTimeUnit.MONTH).plus(1, DateTimeUnit.DAY) }`
 * can return a different result from `instant.plus(1, DateTimeUnit.MONTH, zone).plus(1, DateTimeUnit.DAY, zone)`.
 * The reason is that for [withDate], the conversion back to [Instant] as described above happens only once,
 * whereas for successive modifications, each of them can lose some information when converting back to [Instant].
 * If the result of `instant.plus(1, DateTimeUnit.MONTH, zone)` happens to be in a time gap, the [LocalTime] of
 * the result will be different from the [LocalTime] of the original `instant` in `zone`.
 */
inline fun Instant.withDate(timeZone: TimeZone, action: (LocalDate) -> LocalDate): Instant =
    with(toLocalDateTime(timeZone)) { action(date).atTime(time) }.toInstant(timeZone)
```

In general, I'm bothered by the non-associativity of datetime arithmetics, but
in some cases, that's clearly unavoidable. If we have
`LocalDate(year, month, day)` and add a month to it, and then one more month,
this is going to be different from adding two months at once.
The reason for this is that when adding $n$ months, we likely want
the `yearMonth` of the result to be exactly $n$ months later, even when this
means losing some information about the precise day-of-month.
`2025-01-31 + 1 month = 2025-02-28`,
`(2025-01-31 + 1 month) + 1 month = 2025-03-28`, but
`2025-01-31 + 2 months = 2025-03-31`.
Does this reasoning also translate well to associativity in the presence of time
gaps?
"When adding $n$ days, we want the date of the result to be exactly $n$ days
later, even when this means losing the information about the precise
time-of-day" doesn't work, as we freely move to the next day if we land into a
time gap at 23:50.

The more I think of it, the less sense the operation of adding a month to a
`LocalDate` makes. How about this:

```kotlin
import kotlinx.datetime.*

fun f(date: LocalDate) = date.daysUntil(date.plus(1, DateTimeUnit.MONTH))

fun main() {
    println(f(LocalDate(2025, 1, 25))) // 31
    println(f(LocalDate(2025, 1, 26))) // 31
    println(f(LocalDate(2025, 1, 27))) // 31
    println(f(LocalDate(2025, 1, 28))) // 31
    println(f(LocalDate(2025, 1, 29))) // 30
    println(f(LocalDate(2025, 1, 30))) // 29
    println(f(LocalDate(2025, 1, 31))) // 28
    println(f(LocalDate(2025, 2, 25))) // 28
    println(f(LocalDate(2025, 2, 26))) // 28
    println(f(LocalDate(2025, 2, 27))) // 28
    println(f(LocalDate(2025, 2, 28))) // 28
}
```

Colloquially, I would expect people to use "a month later" at the end of the
month to indicate the last day of the next month, but encoding this logic into
computers feels unreliable to me.

Regardless, I'm sure there's nothing to be done. I'll be eaten alive both by our
users and my own colleagues if I suggest making `LocalDate.plus` an
exception-throwing function in cases where the day-of-month can't be preserved,
so there's no reason to even entertain this thought.

Back to the issue of time gaps during arithmetics:

| Target     | Adding months                                                                                                                       | Adding days                                                                       |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| Dates      | Preserve the target month in case of a calendar gap                                                                                 | No gaps                                                                           |
| Instants   | Preserve the target month in case of a calendar gap, but move into the next hour (potentially, into the next month) on time overlap | Move into the next hour (potentially, into the next day or month) on time overlap |

There's no consistency at all, even in the scope of a single operation of adding
a month to an `Instant`: if the target `LocalDateTime` doesn't exist, then the
day-of-month will be clamped to the last day of the target month, unless there's
a time gap at that time on that day, in which case the result will be the first
day of the month after that.

I wonder if there are realistic examples of this.

```kotlin
import kotlinx.datetime.*

fun main() {
    for (id in TimeZone.availableZoneIds) {
        val zone = TimeZone.of(id)
        val start = LocalDate(2024, 1, 31).atTime(23, 59).toInstant(zone)
        for (monthsToAdd in 0..30) {
            val end = start.plus(monthsToAdd, DateTimeUnit.MONTH, zone)
            val endMonth = end.toLocalDateTime(zone).date.month.number
            check(endMonth - 1 == monthsToAdd % 12) {
                "$zone, adding $monthsToAdd months to 2024-01-31T23:59"
            }
        }
    }
}
```
doesn't find anything. That's reassuring.

Let's run a wider search:

```kotlin
import kotlinx.datetime.*

fun main() {
    val strangeAddition = mutableListOf<Triple<String, Int, LocalDateTime>>()
    for (id in TimeZone.availableZoneIds) {
        val zone = TimeZone.of(id)
        val start = LocalDate(1970, 1, 31).atTime(23, 59).toInstant(zone)
        for (monthsToAdd in 0..700) {
            val end = start.plus(monthsToAdd, DateTimeUnit.MONTH, zone)
            val endMonth = end.toLocalDateTime(zone).date.month.number
            if (endMonth - 1 != monthsToAdd % 12) {
                strangeAddition.add(Triple(id, monthsToAdd, end.toLocalDateTime(zone)))
            }
        }
    }
    println(strangeAddition)
}
```

This one does find a few outliers:

```
[(Pacific/Enderbury, 299, 1995-01-01T23:59),
 (Europe/Sofia, 110, 1979-04-01T00:59),
 (Pacific/Kiritimati, 299, 1995-01-01T23:59),
 (Pacific/Kanton, 299, 1995-01-01T23:59),
 (Singapore, 143, 1982-01-01T00:29),
 (Asia/Kuala_Lumpur, 143, 1982-01-01T00:29),
 (Asia/Singapore, 143, 1982-01-01T00:29)]
```

A full reproducer of the gotcha:

```kotlin
val zone = TimeZone.of("Asia/Singapore")
val start = LocalDate(1981, 10, 31).atTime(23, 59).toInstant(zone)
// Undershooting 1982-02-31
assertEquals(
    LocalDate(1982, 2, 28).atTime(23, 59),
    start.plus(4, DateTimeUnit.MONTH, zone).toLocalDateTime(zone),
)
// Overshooting 1981-12-31
assertEquals(
    LocalDate(1982, 1, 1).atTime(0, 29),
    start.plus(2, DateTimeUnit.MONTH, zone).toLocalDateTime(zone)
)
```

The irritating aspect of datetimes is that we can't be sure what happens in the
future, which timezone anomalies still await us.
It would be easy to swipe all of these issues under the rug and say that this is
never going to happen, and even if it does, it's not going to be a significant
problem.
We could just say in our documentation that when adding months, the month is
preserved. But that's not true. So the documentation has to be polluted with
these details that won't interest anyone,
or else the contract is not stated in full.

An alternative would be to change the implementation to always preserve the
month, but then, all functions that convert `LocalDateTime` to `Instant`
internally have to document that.
