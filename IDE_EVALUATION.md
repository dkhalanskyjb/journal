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
