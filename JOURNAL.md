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
that `B_i` is a maximum when comparing the sets by inclusion.
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
