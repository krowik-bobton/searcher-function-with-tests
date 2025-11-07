import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.NoSuchFileException
import kotlin.io.path.createDirectory
import java.nio.file.AccessDeniedException

class FileSearcherConcurrentTest {
    private var tempDir: Path = Files.createTempDirectory("tempTestingDirectory")

    @BeforeEach
    fun init() {
        tempDir = Files.createTempDirectory("tempTestingDirectory")
    }

    @AfterEach
    fun clean() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun test_single_occurrence_in_single_file() = runTest {
        val file1 = tempDir.resolve("file1.txt").toFile()
        val file1Text = "First line \nSecond line with a PATTERN \nThird line"
        file1.writeText(file1Text)
        val flow = searchForTextOccurrences("PATTERN", tempDir)
        val results = flow.toList()
        assertEquals(1, results.size, "Result should contain exactly 1 value")
        assertEquals(file1.toPath(), results[0].file, "Occurrence found in a wrong file")
        assertEquals(2, results[0].line, "Occurrence found in a wrong line")
        assertEquals(19, results[0].offset, "Occurrence found in a wrong offset")
    }

    @Test
    fun test_no_occurrences_in_single_file() = runTest {
        val file1 = tempDir.resolve("file1.txt").toFile()
        val file1Text = "First line \nSecond line \nThird line"
        file1.writeText(file1Text)
        val flow = searchForTextOccurrences("PATTERN", tempDir)
        val results = flow.toList()
        assertEquals(0, results.size, "Result should be empty")
    }

    @Test
    fun test_directory_does_not_exist() = runTest {
        val fakeDirectory = tempDir.resolve("fakeDirectory")
        assertThrows<NoSuchFileException>("Exception should be thrown if directory does not exist") {
            val flow = searchForTextOccurrences("PATTERN", fakeDirectory)
            flow.toList()
        }
    }

    @Test
    fun test_multiple_occurrences_in_one_file() = runTest {
        val file1 = tempDir.resolve("file1.txt").toFile()
        val file1Text = "First line with PATTERN and another PATTERN \nSecond line with a PATTERN \nThird line"
        file1.writeText(file1Text)
        val flow = searchForTextOccurrences("PATTERN", tempDir)
        val results = flow.toList()
        assertEquals(3, results.size, "Result should contain exactly 3 values")
        assertTrue(
            results[0].file == results[1].file && results[1].file == results[2].file,
            "Occurrences found in different files, but only one file was given"
        )
        assertEquals(file1.toPath(), results[0].file, "All occurrences found in a wrong file")

        val sortedResults = results.sortedWith(
            compareBy({ it.line }, { it.offset })
        )
        assertEquals(1, sortedResults[0].line, "Occurrence found in a wrong line")
        assertEquals(16, sortedResults[0].offset, "Occurrence found in a wrong offset")

        assertEquals(1, sortedResults[1].line, "Occurrence found in a wrong line")
        assertEquals(36, sortedResults[1].offset, "Occurrence found in a wrong offset")

        assertEquals(2, sortedResults[2].line, "Occurrence found in a wrong line")
        assertEquals(19, sortedResults[2].offset, "Occurrence found in a wrong offset")
    }

    @Test
    fun test_three_files_in_one_directory() = runTest {
        val file1 = tempDir.resolve("file1.txt").toFile()
        val file1Text = "First line \nPATTERN \nThird line"
        file1.writeText(file1Text)

        val file2 = tempDir.resolve("file2.txt").toFile()
        val file2Text = "Nothing here..."
        file2.writeText(file2Text)

        val file3 = tempDir.resolve("file3.txt").toFile()
        val file3Text = "PATTeRN PATTERN"
        file3.writeText(file3Text)

        val flow = searchForTextOccurrences("PATTERN", tempDir)
        val results = flow.toList()
        assertEquals(2, results.size, "Result should contain exactly 2 values")

        val sortedResults = results.sortedWith(
            compareBy({ it.file.toString() }, { it.line }, { it.offset })
        )

        assertEquals(file1.toPath(), sortedResults[0].file, "Occurrence found in a wrong file")
        assertEquals(2, sortedResults[0].line, "Occurrence found in a wrong line")
        assertEquals(0, sortedResults[0].offset, "Occurrence found in a wrong offset")
        assertEquals(file3.toPath(), sortedResults[1].file, "Occurrence found in a wrong file")
        assertEquals(1, sortedResults[1].line, "Occurrence found in a wrong line")
        assertEquals(8, sortedResults[1].offset, "Occurrence found in a wrong offset")

    }


    @Test
    fun test_find_occurrences_in_nested_directories() = runTest {
        val rootFile = tempDir.resolve("rootFile.txt").toFile()
        rootFile.writeText("This is the file in our root, contains PATTERN")

        val nestedDirectory = tempDir.resolve("nestedDir").createDirectory()

        val nestedFile = nestedDirectory.resolve("nestedFile.txt").toFile()
        nestedFile.writeText("This is the file in our nested, contains PATTERN")

        val flow = searchForTextOccurrences("PATTERN", tempDir)
        val results = flow.toList()
        assertEquals(2, results.size, "Result should contain exactly 2 values")

        val sortedResults = results.sortedByDescending { it.file.toString() }
        assertEquals(rootFile.toPath(), sortedResults[0].file, "Occurrence found in a wrong file")
        assertEquals(1, sortedResults[0].line, "rootFile occurrence found in a wrong line")
        assertEquals(39, sortedResults[0].offset, "rootFile occurrence found in a wrong offset")

        assertEquals(nestedFile.toPath(), sortedResults[1].file, "Occurrence found in a wrong file")
        assertEquals(1, sortedResults[1].line, "nestedFile occurrence found in a wrong line")
        assertEquals(41, sortedResults[1].offset, "nestedFile occurrence found in a wrong offset")
    }

    @Test
    fun test_main_directory_with_no_access_throws_exception() = runTest {
        val lockedDir = tempDir.resolve("lockedDir").createDirectory()
        val file1 = lockedDir.resolve("file1.txt").toFile()
        file1.writeText("PATTERN")
        try {
            lockedDir.toFile().setReadable(false)
            assertThrows<AccessDeniedException>("Should throw AccessDeniedException") {
                val flow = searchForTextOccurrences("PATTERN", lockedDir)
                flow.toList()
            }
        } finally {
            lockedDir.toFile().setReadable(true)
        }
    }

    @Test
    fun test_file_with_no_access_skips_and_continues() = runTest {
        val readableFile = tempDir.resolve("readable.txt").toFile()
        readableFile.writeText("first PATTERN")

        val lockedFile = tempDir.resolve("locked.txt").toFile()
        lockedFile.writeText("second PATTERN")

        try {
            lockedFile.setReadable(false)
            val flow = searchForTextOccurrences("PATTERN", tempDir)
            val results = flow.toList() // this should not throw an exception
            assertEquals(1, results.size, "Result should contain exactly 1 value")
            assertEquals(readableFile.toPath(), results[0].file)

        } finally {
            lockedFile.setReadable(true)
        }
    }

    @Test
    fun test_pattern_is_an_empty_string() = runTest {
        val pattern = ""
        val file1 = tempDir.resolve("file1.txt").toFile()
        val file1Text = "Nothing special..."
        file1.writeText(file1Text)
        assertThrows<IllegalArgumentException>("Empty pattern string should throw an exception") {
            val flow = searchForTextOccurrences(pattern, tempDir)
            flow.toList()
        }
    }

    @Test
    fun test_pattern_with_newline_throws_exception() = runTest {
        val pattern = "pattern\nwith newline"
        assertThrows<IllegalArgumentException>("Pattern with a newline should throw an exception") {
            val flow = searchForTextOccurrences(pattern, tempDir)
            flow.toList()
        }
    }

}