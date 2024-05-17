## `TimeZone.currentSystemDefault()` providers

Issue: https://github.com/Kotlin/kotlinx-datetime/issues/17

Constraints:

* Not available on WASI.
* On the JVM, Linux, and Apple, a time zone is defined as a IANA identifier.
* On Windows, the current system timezone is not only its identifier, but also
  the specific behavior, including the flag "DST transitions enabled."
  People rely on the flag: ﻿https://learn.microsoft.com/en-us/answers/questions/1193198/how-to-disable-adjust-for-daylight-saving-time-aut
* On Linux, the symlink may be broken and not point to anything.
* On Windows, this operation may fail with a nondescript error.

If we introduce custom timezone databases, the logic of obtaining the current
timezone database will not change anyway.
