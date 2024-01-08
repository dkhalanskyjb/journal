I automatically renamed `EuropeBerlinTzFile` to `EuropeBerlinTzFile2023c` in
response to
<https://github.com/Kotlin/kotlinx-datetime/pull/286/files/613d49d06a0353cc930b5fb194b20101478639e6#diff-4cb9dd770dc64cf73071ca9c37793d0781b78e2e5d2d9cc37ab2d70d42f8987b>

This could be done via `sed`, but it *is* less streamlined than
`Shift+Shift`, `EuropeBer`, `Enter`, `Shift+F6`, right arrow, `2023c`, `Enter`,
and much less conceptually busy then
`git grep -l EuropeBer | xargs sed -i 's/\(EuropeBeR\W+\)/\1_2023c/g'`.

---

I'm simplifying a class hierarchy.
I want to remove an interface, as it's never actually used, and make all the
methods that constitute its implementation just plain methods: transform

```kotlin
interface X {
  fun a()
}

class Y : X {
  override fun a()
}
```

to

```kotlin
class Y : X {
  fun a()
}

```

Trying to inline the interface via a context menu item or `Ctrl+Shift+N` while
keeping the cursor on its name results in

`Cannot perform refactoring. Caret should be positioned at the name of element to be refactored`

Clearly misleading. Also, why then is the context menu item there at all?

Trying to "Safe delete" (`Alt+Delete`), I get notified that there is a usage and
it's not safe to delete the interface. "Delete anyway" just results in broken
code.

Oh well, let's do this manually.

---

I have a bunch of classes implementing an interface:

```kotlin
sealed interface X {
}

class A : X {
}

class B : X {
}

class C : X {
}
```

I want to move them inside the interface as nested classes:

```kotlin
sealed interface X {
    class A : X {
    }
    class B : X {
    }
    class C : X {
    }
}
```

`F6` does not give me an option to move the classes inside.

Googling how to do this, I found
<https://youtrack.jetbrains.com/issue/KTIJ-9834>. Ok, so no way to do this yet,
and I need to manually move the classes and update the usages.

During the manual fixup, the chain of `F2` to go to the next unresolved symbol
and IdeaVim's `.` to repeat the previous command (in this case, it was
prepending `X.`) worked well this time. Though this is more of a hack and not
a structured solution: when I have to *append* something to the place with an
error, it doesn't work nearly as well.

---

I used the IDE just now to launch a specific test. A neat feature.

---

I want to find the places in a codebase where a `Deprecated.HIDDEN` declaration
is preceded by a docstring. In the console, I can do it roughly this way:

```
git grep -B3 DeprecationLevel.HIDDEN | grep -F '*/' | sed 's/\(.\+.kt\).*/\1/g' | sort -u
```

It finds the mentions of `DeprecationLevel.HIDDEN`, takes the three lines above
that mention (in order to skip some other declarations), finds the
comment-ending sequence `*/`, leaves out the specific matches, keeping only the
filenames, and produces a sorted list of them.

This is very hacky. I'd certainly like to employ a more structured approach.
Something like `comment annotation* @ReplaceWith(DeprecationLevel.HIDDEN)`.

The IDE contains the feature just like that: a structured search (and replace).
Let's try it out, I never did so before.

The window for writing my search expression helpfully autocompletes. So,
"comment"...

* "Comments (Java search template)"
* "Comments containing a given word (Java search template)"
* "Comments containing a given word (Kotlin search template)"

I wonder what Java search templates are doing here, given that I explicitly set
the language to Kotlin. Also, no "Comments (Kotlin search template)"?
Won't Java's search template work?.. No, it won't, it just returns Java files.

Ok, "Comments containing a given word (Kotlin search template)" it is, with the
word being "a":

```
// $before$ a $after$
```

"No modifiers added for `$after$`" What? Alright, doesn't matter, my needs are
fairly simple.

Next, "Annotations".

Wait, why does choosing "Annotations (Kotlin search template)" from
autocompletion removes the template I already had? Can I only search either/or?
Probably not, I think I'm just choosing one template among the examples.
Ok, let me read the examples provided on the left.

Looks like the principle is simple: you write the form of the code you want to
find, using `$variables$` to capture unknown input, and it will return you that
code. For example,

```
// $comment$
@$Annotation$
```

should return comments followed by annotations...

```kotlin
    }

    @Test
    fun testAwaitFailure() = runBlocking {
```

Eh? I don't see any comment here. Maybe just comments don't work for Kotlin and
you *do* need it to have a form `// $before$ bug $after$`?


```
// $comment1$ a $comment2$
@$Annotation$
```

No, `@Test` without any surrounding comments is still there for some reason.

What does the "Injected code" checkbox do? Time to read the documentation, I'm
unable to make sense of this any other way, it seems.

<https://www.jetbrains.com/help/idea/structural-search-and-replace.html>

> **Injected code**: if this checkbox is selected, the injected code such as
> JavaScript that is injected in HTML code or SQL injected in Java will be a
> part of the search process.

Yeah, not relevant at all. A small gripe is that it doesn't have a tooltip to
indicate this on mouse hover.

<https://resources.jetbrains.com/help/img/idea/2023.2/Edit_filters.png>

Huh? What does the regex `\b[A-Z].*?\b` even mean? Which dialect is that?
`?` is supposed to mean "optionally", but `.*` already can have the width of
zero... And why are `\b` needed? Isn't the field, surrounded by spaces, already
known to be on a word boundary? No matter, probably a typo. Not to mention that
I don't need any regexes here, my requirements are simple.

Unfortunately, the documentation does not answer my question at all.
Let's try some more manually.

```
/** $comment1$ a $comment2$ */
@$Annotation$
```

also returns the uncommented `@Test`.

I tried setting `comment1` to be the "target" of the search:

> **Target**: in the list of options, select what item to search for.

However, even when explicitly searching `comment1` instances, just the
annotations are returned anyway.

I give up, I don't think this works. And here I was thinking about searching for
public declarations without a KDoc...

---

I want to replace all occurrences of `assertTrue(x is T)` with `assertIs<T>(x)`.
This doesn't require any new imports. A simple, straightforward task. I could
even do it from the command line in a minute if I so desired, but let's give the
IDE a chance to shine.

I open "Search structurally." I input the template:
`assertTrue($Expr$ is $Type$)`. I click "Find." It found 201 results! Not bad.

There's a button: "Create Inspection from Template..." I'm interested: after
all, I want to replace the problematic code.

Hm... I get a window that prompts me to input the inspection name,
a description, and a tooltip, along with "Suppress ID." No, inspection doesn't
seem to be what I want.

Wait, the template is wrong: I also want to capture the overload
"`assertTrue($Expr$ is $Type$, $error_message$)`." I do this using the template
"`assertTrue($Expr$ is $Type$, $Parameter$)`." It automatically recognizes that
I need 0+ parameters. Now I get 214 results. How do I edit them, though?

Oh, it's a separate action: "Replace structurally." Let's try it.

The UI is weak: there deosn't seem to be a way to see the "before" and the
"after" side-by-side. Still, "Replace All" did its job! Amazing!

...

```kotlin
assertTrue(args is Array<Int>) // before
(assertIs<Array>(args)) // after
```

Did I do something wrong? Why the extra parens? They don't appear in any other
replacements. Also, more importantly, why is the type argument missing?

```kotlin
assertEquals(context[CoroutineName.Key]?.name, infoFromJson.name) // before
assertEquals(context[CoroutineName]?.name, infoFromJson.name) // after
```

Wait, what? Why did it do that? I didn't ask for this, I certainly didn't do
this myself.

Let's see if there are any templates that allow me to explicitly capture the
type arguments so that they don't get missing.

... You can't make this up: when I tried to look at the template called
"Properties with explicit getter," the IDE hanged instead. Though this doesn't
reproduce. Ok.

I didn't find a template, so I wrote this:
```kotlin
assertTrue($Expr$ is $Type$<$Parameter$>, $Parameter$)
```

For some reason, I got 209 matches. A weird number.

This doesn't seem to work:

```kotlin
assertTrue(cause is TestException1) // before
assertIs<TestException1<>>(cause) // after
```

So it's smart enough to understand that `TestException1` matches the template
`$Type$<$Parameter$>`, but not smart enough to understand that
`TestException1<>` should be formatted as `TestException1`.

Ok, I give up. I've no idea why the IDE thinks it's okay to throw away
information (and make arbitrary extra changes) when all I'm asking is to make a
permutation of some parameters:

```
assertTrue($Expr$ is $Type$, $Parameter$)
assertIs<$Type$>($Expr$, $Parameter$)
```

I'm intimidated to even try to do the thing for which this was a warm-up: to
look for the functions whose name starts with "assert" that accept a literal
string with parameters.

With `grep`, it's...

```sh
git grep -P 'assert.+\(.*\$'
```

I'm sure it misses some cases (notably, ones where the strings is on a separate
line), but at least it gives me something.

Ok... Here goes nothing.

Let's start with

```
$MethodCall$($Parameter$)
```

I "add a modifier" to `$MethodCall$`. It can be a "reference," "match call
semantically," "text," or a "script." "Reference" I understand: some specific
entity in code. Clicking at "Script," I see there's access to the full-fledged
API. It's a good thing. "Match call semantically" is completely opaque for me.
What I need is "Text." The info blob helpfully states that regular expressions
are supported, but doesn't say which dialect. Ok, let's try "`assert.+`.

It looks like it searches for the right thing! It is extremely slow for some
reason, but I'm not going to complain yet. Let's arrive at a somewhat working
solution first.

There are two patterns that I see in the `grep` output:

```
assert.+(blah-blah, "Error")
```

and

```
assert.+(message = "Error") { }
```

Maybe I could achieve this by using the "script" option. Let's look into this.
Googling "GroovyScript IntelliJ API" (a phrase verbatim from the infoblob that
explains what "script" is), I get
<https://intellij-support.jetbrains.com/hc/en-us/community/posts/360000045970-Search-and-Replace-Structurally-Script-Constraints-Documentation->:
some forum post where a user decries lack of documentation, asks for pointers,
and receives an (actually useful) explanation. Maybe I'll use this approach if
I can't actually write the template.

`$MethodCall$($Parameter$, "$String$")` does seem to return vaguely relevant
results. It also includes `AssertionError` (I forgot to tick the "match case"
option) and `assertEquals(channel.receive(), "3")`, but this is expected.
Now, how do I only keep the strings that contain templates?

Oh, well, maybe later. I have a more pressing matter right now. More on that
below.

---

Here's the star hour for the IDE.

I need to move a bunch of definitions to a new package. Basically, I need to
change the package of a whole Gradle module and update all the dependent files.
This would be tough to do by hand: I'd need to find every place where something
is used and add a new import.

Well, if the IDE fails, I know how to do that in this particular case. Still,
it wouldn't be as clean a solution.

I open the file I want to move and press F6. I get the list of all definitions
in this file. Am I supposed to tick every checkbox manually?
Where's "select all"? There's, like, thirty of them! Oh, alright. Arrow down,
space, arrow down, space...

```
The following problems were found:
Class TestBase uses property SHUTDOWN_TIMEOUT which will be inaccessible after move
Class TestBase uses property VERBOSE which will be inaccessible after move
```

Makes sense: I moved `expect` classes and their `actual` instantiations, but
not the things on which these `actual` instantiations depend. I can live with
that and move them manually.

What I can't live with is the rest of the result.

`expect` class was moved correctly to `kotlinx.coroutines.testing` from
`kotlinx.coroutines`. The `actual` class was instead moved to
`kotlinx.coroutines.kotlinx.coroutines.testing`. The newly-moved files have no
`import` statements. I can fix all of this by `git checkout`-ing the old files
and manually tweaking the package name.

The most important thing to me is that the dependent files do properly contain
the imports of the new package that I introduced.

Except they don't. Yes, it was all in vain. I'll resort to CLI magic.

First, find every file that mentions the most common definition from the new
package, excluding the directory that contains it:

```sh
git grep -l TestBase | grep -v test-utils
```

Next, let's try automatically adapting a single file the way I want. And I want
to find the first `import` statement and add my `import` before it. Can I do
that?

First, let's check that every file does have an `import` statement, at least
one:

```sh
$ git grep -l TestBase | grep -v test-utils | xargs -n1 sh -c 'cat "$0" | grep -q "^import" || echo "$0"'
kotlinx-coroutines-core/jvm/test-resources/stacktraces/channels/testSendToChannel.txt
```

Yeah, I should exclude the `.txt` file as well. Other than that, good to go.

```sh
sed '0,/import/{/import/s/^/import kotlinx.coroutines.testing.*\n/}' kotlinx-coroutines-core/common/test/AbstractCoroutineTest.kt
```

Basically, "from the 0th line to the first mention of `import`" (so, until the
first `import`), "if you see `import`, replace the beginning of the line with
`import kotlinx.coroutines.testing.*`, followed by a newline." Looking at the
result, it does seem to work.

And now run this in parallel:

```sh
git grep -l TestBase | grep -v test-utils | xargs -n1 sed -i '0,/import/{/import/s/^/import kotlinx.coroutines.testing.*\n/}'
```

Et voila!

Oh, wait, I need to fix the tests that do mention `TestBase` by its fully
qualified name:

```sh
$ git grep -F .TestBase
kotlinx-coroutines-core/jvm/test-resources/stacktraces/channels/testSendToChannel.txt:  at kotlinx.coroutines.TestBase.runTest(TestBase.kt)
kotlinx-coroutines-core/jvm/test/internal/LockFreeLinkedListLongStressTest.kt:import kotlinx.coroutines.TestBase
kotlinx-coroutines-core/jvm/test/jdk8/time/FlowDebounceTest.kt:import kotlinx.coroutines.TestBase
kotlinx-coroutines-core/jvm/test/jdk8/time/FlowSampleTest.kt:import kotlinx.coroutines.TestBase
kotlinx-coroutines-debug/test/DebugProbesTest.kt:                        "\tat kotlinx.coroutines.TestBase.runTest(TestBase.kt)\n" +
kotlinx-coroutines-debug/test/DebugProbesTest.kt:                        "\tat kotlinx.coroutines.TestBase.runTest\$default(TestBase.kt)\n" +
kotlinx-coroutines-debug/test/SanitizedProbesTest.kt:                    "\tat kotlinx.coroutines.TestBase.runTest\$default(TestBase.kt:141)\n" +
kotlinx-coroutines-debug/test/SanitizedProbesTest.kt:                "\tat kotlinx.coroutines.TestBase.runTest\$default(TestBase.kt:141)\n" +
kotlinx-coroutines-debug/test/SanitizedProbesTest.kt:                    "\tat kotlinx.coroutines.TestBase.runTest\$default(TestBase.kt:141)\n" +
kotlinx-coroutines-debug/test/SanitizedProbesTest.kt:                "\tat kotlinx.coroutines.TestBase.runTest\$default(TestBase.kt:154)\n" +
ui/kotlinx-coroutines-javafx/test/JavaFxObservableAsFlowTest.kt:import kotlinx.coroutines.TestBase
```

Also easy:

```sh
git grep -lF .TestBase | xargs -n1 sed -i 's/kotlinx.coroutines.TestBase/kotlinx.coroutines.testing.TestBase/'
```

Aside from a couple more files, everything seems to work correctly.
