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
