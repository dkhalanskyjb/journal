Shape of the datetime parsing/formatting API
--------------------------------------------

### Parsing vs formatting

These tasks appear to be distinct.
* `strptime` is mostly similar to `strftime`, but the format strings are not
  compatible between them. Everyone seems fine with that, except in cases where
  the discrepancy is blatantly unjustified:
  - <https://stackoverflow.com/questions/20003993/perl-strptime-format-differs-from-strftime>
  - <https://stackoverflow.com/questions/8302990/why-do-strptime-and-strftime-behave-differently-and-what-do-you-do-about-it>
* Localized representations are useful for two things:
  - presenting information in a user-friendly manner, and
  - parsing information in a format that happens to corresponds to some
    locale's treatment of formatting.
    Example: <https://gist.github.com/jbowes/1065230#file-httprequestlogger-java-L146>

### Builders and string patterns

We 100% need string patterns, and we most likely also want builders.

Why we need string patterns:
* People **love** them. If possible at all, will use them even when a builder
  provides a much more robust solution: <https://stackoverflow.com/a/48300794>.

Why we need builders:
* Some fairly common use cases are not feasible without flexibility that can't
  be achieved with just string formats. Searching for how other libraries that
  don't provide the builders dealt with the problems, usually they are solved
  by hacky string sanitizing. All of this can be accomplished by extending the
  string format and adding a few options to the `parse` and `format` functions,
  but that doesn't seem to be a clean solution. Maybe all of these issues can
  be solved one by one though.
  - Parsing and formatting nonstandard month names.
  - Formatting nonstandard weekday names.
  - Setting the base year for the reduced form.
  - Defining default values for missing fields.
  - Defining the maximum and the minimum size of the fraction of a second both
    when parsing and when formatting.
  - Defining a correspondence between abbreviated timezone names and the
    timezones.
* For longer and more elaborate formats, they are more readable than the usual
  letter soup. This can be partially solved by cleaner syntax of the format
  strings, but only up to a point.
