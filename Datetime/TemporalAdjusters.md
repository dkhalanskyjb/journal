A raw collection of findings regarding the temporal adjusters
=============================================================

Use cases
---------

### Rust

#### Rounding time-based components towards zero

<https://github.com/getdozer/dozer/blob/8b0b4cc021db089722cb3d48dbe915e8e9c532ec/dozer-sql/src/window/operator.rs#L86>
implements <https://learn.microsoft.com/en-us/stream-analytics-query/hopping-window-azure-stream-analytics>.
To that end, it finds out the moment from which the analytics output should
start, *rounds it down* to the nearest multiple of the hop size window, and then
proceeds to generate windows. It doesn't seem like this has anything to do with
correctness: probably any starting value would work just as well. If the goal
was to make this pretty, it will work if both are true:

* The length of the window divides or is divided by the number of seconds in a minute,
* The UTC offset in which this is displayed has zero seconds-of-minute.

<https://github.com/helium/gateway-rs/blob/1d69023c73050e1e7a6cb6c166de7b82124c9117/src/beaconer.rs#L327>
uses truncation to split all time into equally-sized chunks and ensure that a
signal isn't emitted more often than once per chunk.
This looks like an attempt at homegrown rate-limiting.

<https://github.com/rcoh/angle-grinder/blob/2ec0920632645bd69460f82b6e4be7a4a6e110e1/src/operator/timeslice.rs>
also uses truncation to define time slices with a given duration.

<https://github.com/JuanPotato/unicode_bot/blob/7f30e6249d2ffedf5fe2375c508f6d93ef7e7bc1/src/main.rs#L92>
seems to attempt to write some stats to a file at the start of every hour
(in the UTC time zone). Seems like a bug: probably the local-date-time hour was
meant instead. Though this is just some internal diagnostics, so this may not
matter much.

<https://github.com/isucon/isucon11-qualify/blob/1011682c2d5afcc563f4ebf0e4c88a5124f63614/webapp/rust/src/main.rs#L872-L907>
also splits time into hour-long chunks and aggregates data in each chunk.
It hardcodes the Japanese Standard Time timezone.

<https://github.com/pop-os/cosmic-applets/blob/73ae8710885a80e29a2a4044608f86828f6c83d8/cosmic-applet-time/src/window.rs#L422-L426>
calculates how long the program has to sleep before the second-of-minute or
the minute-of-hour becomes zero.

<https://github.com/MaterializeInc/materialize/blob/b23356d1230365179af0154e1950886d65597bc5/src/compute-client/src/controller/instance.rs#L769>
splits time in minute-long chunks and aggregates data per chunk.

<https://github.com/jacob-pro/solar-screen-brightness/blob/6a81ca896b9514ff7ebab209bb6edce4b62d2a92/src/gui/brightness_settings.rs#L181-L201>
splits time in multi-hour-long chunks and aggregates data per chunk.

<https://github.com/dimfeld/ergo/blob/af4aacc4daba37b577824b0a8a4bbbfabd20c8ed/api/tests/tasks/periodic.rs#L51>
rounds down to whole minutes so that the datetime fits the cron format.

#### Rounding time-based components to the nearest

<https://github.com/chirpstack/chirpstack/blob/a5ff416fa277f396ac442008923333abcba79e70/chirpstack/src/backend/roaming.rs#L249-L253>
is probably a mistake. It looks like the idea is to take a datetime, and if
there is a very precise sub-second reading, use that instead.
If so, the correct thing seems to be to round the time towards zero, but here,
it's rounded instead. Tough to say if that's intentional, as there are no tests
for this function.

<https://github.com/sugyan/atrium/blob/d4a3cbb670ef1fe6baa24ad5f614a54b5dc54b04/atrium-api/src/types/string.rs#L191-L195>
ensures that the date is not truncated but rounded when serialized with
microsecond precision.

<https://github.com/blockworks-foundation/mango-v4/blob/d9c4f69e0e0446a6fbf974c45d70474c262c80d9/bin/service-mango-health/src/processors/persister.rs#L158-L161>
splits all time in buckets of fixed length and aggregates data per chunk.
It seems like truncation could also be used here, but then the chunks would be
aligned differently.

<https://github.com/dimfeld/ergo/blob/af4aacc4daba37b577824b0a8a4bbbfabd20c8ed/queues/lib.rs#L750-L753>
ensures that the serialized version, which only has limited precision, is the
same instant as what the system sees internally. The instant is far enough into
the future that it doesn't matter whether it's truncated or rounded.
