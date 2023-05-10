Various
=======

Hurdles
-------

The following is from a random search in the various repositories.

If a date is in one of several formats, the format needs to be chosen before
parsing:
* <https://github.com/isergey/school_tatar/blob/2b4f66064b14c1895b86914fefcef364cc002474/libcms/apps/ssearch/common.py>
* <https://github.com/sj741231/DownloadVideo/blob/f62e346a714e4c4ef90c40009ec3f62f956a1b66/utils/util_re.py>
* <https://github.com/nautatva/migrate-away-from-google-photos/blob/198010126f073b7fbd7f18e6967b31096dd793c0/parsers/file_name_parser.py>

Some formats allow for ambigous parsing:
* <https://stackoverflow.com/questions/49358893/datetimeformatter-parsing-string-with-optional-time-part-fails-if-space-removed>
  In `yyyyMMdd(HHmmss)?`, the string `20220102030405` can be parsed as either
  `2022-01-02T03:04:05` or as `2022010203-04-05`.

Serializability of `DateTimeFormatter`: <https://github.com/JodaOrg/joda-time/issues/358>
