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