Kotlin function that searches for occurrences of a given text pattern in files within given directory.

## Details

The project provides some Unit tests and a function:

```kotlin
fun searchForTextOccurrences(
    stringToSearch: String,
    directory: Path,
    searchHidden: Boolean = false
): Flow<Occurrence>
```

