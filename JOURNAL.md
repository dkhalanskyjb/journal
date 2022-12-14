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
common/main/zh_Hant.xml:                                                <dateFormatItem id="h">Bh???</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hm">Bh:mm</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hms">Bh:mm:ss</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="Ehm">E Bh:mm</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="Ehms">E Bh:mm:ss</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="h">Bh???</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hm">Bh:mm</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="hms">Bh:mm:ss</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="Ehm">E Bh:mm</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="Ehms">E Bh:mm:ss</dateFormatItem>
common/main/zh_Hant.xml:                                                <dateFormatItem id="h">Bh???</dateFormatItem>
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
Style: '??' '+' '??' '??', locales: [ar, ar_001, ar_AE, ar_BH, ar_DJ, ar_EG, ar_ER, ar_IL, ar_IQ, ar_JO, ar_KM, ar_KW, ar_LB, ar_MR, ar_OM, ar_PS, ar_QA, ar_SA, ar_SD, ar_SO, ar_SS, ar_SY, ar_TD, ar_YE, sd, sd_PK]
Style: '0' '+' '???' ',', locales: [ar_DZ, ar_LY, ar_MA, ar_TN]
Style: '0' '+' '???' '.', locales: [ar_EH, iw, iw_IL, ur, ur_PK]
Style: '???' '+' '-' '.', locales: [as, as_IN, bn, bn_BD, bn_IN]
Style: '??' '+' '???' '??', locales: [ckb, ckb_IQ, ckb_IR, ig, ig_NG]
Style: '???' '+' '-' '.', locales: [dz, dz_BT]
Style: '0' '+' '???' ',', locales: [et, et_EE, eu, eu_ES, fi, fi_FI, fo, fo_DK, fo_FO, ksh, ksh_DE, lt, lt_LT, nb, nb_NO, nb_SJ, nn, no_NO_NY, nn_NO, no, no_NO, se, se_FI, se_NO, se_SE, sl, sl_SI, sv, sv_AX, sv_FI, sv_SE]
Style: '??' '+' '???' '??', locales: [fa, fa_AF, fa_IR, ks, ks_IN, lrc, lrc_IQ, lrc_IR, mzn, mzn_IR, pa__#Arab, pa_PK_#Arab, ps, ps_AF, ur_IN, uz__#Arab, uz_AF_#Arab]
Style: '0' '+' '???' '.', locales: [gsw, gsw_CH, gsw_FR, gsw_LI, rm, rm_CH]
Style: '???' '+' '-' '.', locales: [mr, mr_IN, ne, ne_IN, ne_NP]
Style: '???' '+' '-' '.', locales: [my, my_MM]
Style: '???' '+' '-' '.', locales: [th_TH_TH_#u-nu-thai]
Style: '??' '+' '-' '??', locales: [uz__#Cyrl, uz_UZ_#Cyrl]
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
Style: '??' '+' '??' '??', pos_time: ??????????????????????, neg_time: ????????????????????????, locales: [ar, ar_001, ar_AE, ar_BH, ar_DJ, ar_EG, ar_ER, ar_IL, ar_IQ, ar_JO, ar_KM, ar_KW, ar_LB, ar_MR, ar_OM, ar_PS, ar_QA, ar_SA, ar_SD, ar_SO, ar_SS, ar_SY, ar_TD, ar_YE, sd, sd_PK]
Style: '0' '+' '???' ',', pos_time: 2000,057428, neg_time: ???2000,057428, locales: [ar_DZ, ar_LY, ar_MA, ar_TN]
Style: '0' '+' '???' '.', pos_time: 2000.057428, neg_time: ???2000.057428, locales: [ar_EH, iw, iw_IL, ur, ur_PK]
Style: '???' '+' '-' '.', pos_time: ????????????.??????????????????, neg_time: -????????????.??????????????????, locales: [as, as_IN, bn, bn_BD, bn_IN]
Style: '??' '+' '???' '??', pos_time: ??????????????????????, neg_time: ?????????????????????????, locales: [ckb, ckb_IQ, ckb_IR, ig, ig_NG]
Style: '???' '+' '-' '.', pos_time: ????????????.??????????????????, neg_time: -????????????.??????????????????, locales: [dz, dz_BT]
Style: '0' '+' '???' ',', pos_time: 2000,057428, neg_time: ???2000,057428, locales: [et, et_EE, eu, eu_ES, fi, fi_FI, fo, fo_DK, fo_FO, ksh, ksh_DE, lt, lt_LT, nb, nb_NO, nb_SJ, nn, no_NO_NY, nn_NO, no, no_NO, se, se_FI, se_NO, se_SE, sl, sl_SI, sv, sv_AX, sv_FI, sv_SE]
Style: '??' '+' '???' '??', pos_time: ??????????????????????, neg_time: ?????????????????????????, locales: [fa, fa_AF, fa_IR, ks, ks_IN, lrc, lrc_IQ, lrc_IR, mzn, mzn_IR, pa__#Arab, pa_PK_#Arab, ps, ps_AF, ur_IN, uz__#Arab, uz_AF_#Arab]
Style: '0' '+' '???' '.', pos_time: 2000.057428, neg_time: ???2000.057428, locales: [gsw, gsw_CH, gsw_FR, gsw_LI, rm, rm_CH]
Style: '???' '+' '-' '.', pos_time: ????????????.??????????????????, neg_time: -????????????.??????????????????, locales: [mr, mr_IN, ne, ne_IN, ne_NP]
Style: '???' '+' '-' '.', pos_time: ????????????.??????????????????, neg_time: -????????????.??????????????????, locales: [my, my_MM]
Style: '???' '+' '-' '.', pos_time: ????????????.??????????????????, neg_time: -????????????.??????????????????, locales: [th_TH_TH_#u-nu-thai]
Style: '??' '+' '-' '??', pos_time: ??????????????????????, neg_time: -??????????????????????, locales: [uz__#Cyrl, uz_UZ_#Cyrl]
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
AM ???????? 12:12
AM ???????? 1:12
AM ???????? 2:12
AM ???????? 3:12
AM ???????? 4:12
AM ???????? 5:12
AM ???????? 6:12
AM ???????? 7:12
AM ???????? 8:12
AM ???????? 9:12
AM ???????? 10:12
AM ???????? 11:12
PM ?????? 12:12
PM ?????? 1:12
PM ?????? 2:12
PM ?????? 3:12
PM ?????? 4:12
PM ?????? 5:12
PM ???????????? 6:12
PM ???????????? 7:12
PM ???????????? 8:12
PM ???????????? 9:12
PM ???????????? 10:12
PM ???????????? 11:12
```

And for the `my` locale, the AM/PM markers are not always the same as the
period-of-day specification, notice the second half of day:
```
Locale: my
??????????????? ??????????????? 12:12
??????????????? ??????????????? 1:12
??????????????? ??????????????? 2:12
??????????????? ??????????????? 3:12
??????????????? ??????????????? 4:12
??????????????? ??????????????? 5:12
??????????????? ??????????????? 6:12
??????????????? ??????????????? 7:12
??????????????? ??????????????? 8:12
??????????????? ??????????????? 9:12
??????????????? ??????????????? 10:12
??????????????? ??????????????? 11:12
????????? ?????????????????? 12:12
????????? ?????????????????? 1:12
????????? ?????????????????? 2:12
????????? ?????????????????? 3:12
????????? ????????? 4:12
????????? ????????? 5:12
????????? ????????? 6:12
????????? ??? 7:12
????????? ??? 8:12
????????? ??? 9:12
????????? ??? 10:12
????????? ??? 11:12
```

The correct way to print short time, according to the JDK, is this:
```
Locale: my
??????????????? 0:12
??????????????? 1:12
??????????????? 2:12
??????????????? 3:12
??????????????? 4:12
??????????????? 5:12
??????????????? 6:12
??????????????? 7:12
??????????????? 8:12
??????????????? 9:12
??????????????? 10:12
??????????????? 11:12
?????????????????? 12:12
?????????????????? 13:12
?????????????????? 14:12
?????????????????? 15:12
????????? 16:12
????????? 17:12
????????? 18:12
??? 19:12
??? 20:12
??? 21:12
??? 22:12
??? 23:12
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
        <dayPeriod type="midnight">?????????????????????????????????</dayPeriod>
        <dayPeriod type="am">???????????????</dayPeriod>
        <dayPeriod type="noon">???????????????????????????</dayPeriod>
        <dayPeriod type="pm">?????????</dayPeriod>
        <dayPeriod type="morning1">???????????????</dayPeriod>
        <dayPeriod type="afternoon1">??????????????????</dayPeriod>
        <dayPeriod type="evening1">?????????</dayPeriod>
        <dayPeriod type="night1">???</dayPeriod>
</dayPeriodWidth>
```

```xml
<dayPeriodWidth type="abbreviated">
        <dayPeriod type="midnight">????????.</dayPeriod>
        <dayPeriod type="am">AM</dayPeriod>
        <dayPeriod type="noon">????????.</dayPeriod>
        <dayPeriod type="pm">PM</dayPeriod>
        <dayPeriod type="morning1">????????</dayPeriod>
        <dayPeriod type="afternoon1">??????</dayPeriod>
        <dayPeriod type="evening1">????????????</dayPeriod>
        <dayPeriod type="night1">????????</dayPeriod>
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
