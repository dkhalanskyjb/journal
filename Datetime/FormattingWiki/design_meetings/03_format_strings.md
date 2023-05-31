Format Patterns for Datetime Formats
====================================

printf-style format strings
---------------------------

### Formatting

Example: `%02d:%02d:%02.3f`

```c
int hours = 23;
int minutes = 54;
float seconds = 21.5213;
printf("%02d:%02d:%02.3f\n", hours, minutes, seconds);
// 23:54:21.521
```

Consists of literal characters and directives.
* Literal characters are output as usual.
* Directives (called "conversion specifications") have the general form
  `%[$][flags][width][.precision][length modifier]conversion`

`\n` is handled by the C compiler, `printf` only sees the actual newline
character.

Directly after `%`, one may write `n$` to denote that the value should be
taken from the `n`'th argument.

Flags are single characters that provide
* padding with zeros,
* padding with spaces on the left,
* padding with spaces on the right,
* characters that show the type of the field (`0` for octal,
  `0x` for hexadecimal, terminal `.` for floating-point numbers, etc)
* initial sign,
* grouping of digits (like `1'000'000`).

`width` is the total width of the output.

`.precision` is the length after the floating point. For strings, this is the
max length to output.

Width and length can be either numbers or `*n$` to denote that the `n`'th
argument is the desired width.

`length modifier` defines how big a type to expect (e.g., `Int` vs `Long`).

Some examples of directives:
* `%o`, output as unsigned octal number.
* `%# 3o`, output as unsigned octal number, always prepending 0, space-padded on
  the left to at least three characters.
* `%-6.8s`, output at least 6 and at most 8 characters of the given string,
  space-padded on the right.
* `%+08lld`, output at least 8 characters of the given `Long` value,
  zero-padded, always outputting the sign.
* `%2$d`, print the 2nd argument as a decimal number.
* `%2$0*2$d`, print the 2nd argument as a decimal number, with the width equal
  to that same number, space-padded.

### Parsing

A bit similar to formatting, but actually different.

Format strings consist of:
* Whitespace characters. They match `[ \t\n]*`.
* Directives, starting with `%`.
* Ordinary characters. They match themselves.

Directives are of the form `%[$][flags][width][length modifier]conversion`.

`n$`, like with formatting, can be used to specify the argument to interact
with.

Flags:
* "Do not assign to anything, just skip".
* "Digits may be grouped" (like `1'000'000`).
* "I won't provide the buffer to hold the result, allocate it on your own".

The width now defines the maximum width to read. For example, `%5s` means
"parse at most 5 non-white-space characters into a string".

Parsing is lenient and doesn't care about the intricacies of formatting:
* Numeric signs can always be present.
* `0xABCD` and `0Xabcd` are parsed with the same directives.
* Floating-point number directives read all the notations they know of.
* etc.

Some new directives or behaviors are there:
* `%[a-z]`, `%[^0-9]`, etc: groups of characters. Stored in a string.
* `%c` is used in `printf` to format chars, but `%5c` will read exactly
  5 characters, perfect for fixed-width input.
* `%n` doesn't parse anything, instead outputting the number of characters
  already read.

Swift format strings
--------------------

<https://stackoverflow.com/a/52332748>

Disappointingly enough, it's just the C format strings with an occasional
incompatibility like "integers with serapated digit groups" not working or there
being a new directive `%@` that formats an Objective-C object by calling its
`descriptionWithLocale` method.

Python format strings
---------------------

### Beginning: the modulo operator on strings

See <https://docs.python.org/3/library/stdtypes.html#printf-style-string-formatting>

```python
"Hi, %s! %s?" % ("me", "What's up") # "Hi, me! What's up?"
"Hi, %(name)s! %(question)s?" % { "name": "me", "question": "What's up" }
```

Everything is like in C's `printf`, except that, instead of the `n$`
position-selecting syntax, we have `(name)` map-querying syntax, and the
type length need not be specified.

Everything else is as before:

```python
"%(birthYear)+08d" % { "birthYear" : 21333 } # '+0021333'
```

### 2002: template strings

<https://peps.python.org/pep-0292/>

Very simple wrapper 

```python
from string import Template
Template('$who likes $what').safe_substitute({"who": "tim"}) # 'tim likes $what'
Template('$who likes $what').substitute({"who": "tim"}) # KeyError: 'what'
Template('$who likes $what').substitute({"who": "tim", "what": "$100"}) # 'tim likes $100'
Template('$who likes $what').substitute(who = "tim", what = "$100") # 'tim likes $100'
```

Seems like that's the full API.

### 2006: the `.format` function on strings

<https://peps.python.org/pep-3101/>

The form of the format is specified in
<https://docs.python.org/3/library/string.html#format-specification-mini-language>:
`[[fill]align][sign]["z"]["#"]["0"][width][grouping_option]["." precision][type]`

We should go through this group by group.
* `[[fill]align]` is for padding. `fill` (space by default) is the character to
  pad with, and `align` is `<` for left alignment, `>` for right alignment,
  `^` for centering, and `=` is only valid for numeric types and means
  justifying by putting the padding *inside* the field, like `+000000120`.
* `sign` is `+`, `-`, or ` `, to denote that the sign must be output always,
  only when it's minus (the default), or that space should be used instead of
  the `+`.
* `z` means that if the number becomes zero after rounding, it should forget its
  sign. For example, `-0.001` with a single digit after the decimal point is
  usually `-0.0`, but with the `z` flag, it's `0.0`.
  Motivation: <https://peps.python.org/pep-0682/>
* `#` is still the "make the type of the field explicit" directive (prepend
  `0x` for hex, `0` for octal, etc).
* The `0` flag is equivalent to the `0=` as `[[fill]align]`.
* `width` is the total width of the output.
* `grouping_option` is either `,` or `_`, and that character is used as a
  separator for thousands. <https://peps.python.org/pep-0378/>,
  <https://peps.python.org/pep-0515/>.
  It is not locale-aware, for that one should use the `n` type.
* `precision` is as usual, the width after the decimal point.

If `type` is specified, it explains how to format the field. It defaults to the
most typical format. For example, if a number is passed, it will attempt to
format as a decimal number: `"{}".format(-100) # '-100'`.
If `x` is passed, it will format in hex (signed):
`"{:#x}".format(-100) # '-0x64'`.

A notable format is `n`, which will output the number with locale-specific
separator between thousands.

Many examples are available at
<https://docs.python.org/3/library/string.html#format-examples>

### Modern times: the f-strings

A special syntax that allows calculating arbitrary expressions when
interpolating strings and to describe the format parameters as in `.format()`,
separated by the colon.

For example:
```python
f'{2 + 2 + 119 :=+8}' # '+    123'
```
Here, after `:`, there's the format `=+8`, which means "insert padding between
signs and the number, always output the sign, pad to 8 characters."

Rust
----

<https://doc.rust-lang.org/std/fmt/index.html>.


