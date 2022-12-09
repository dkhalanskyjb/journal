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