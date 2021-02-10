package ws.logv.lochkarte

import org.junit.Test
import ws.logv.lochkarte.template.extractArchive
import ws.logv.lochkarte.template.github.CheckResult
import ws.logv.lochkarte.template.github.checkUrl
import ws.logv.lochkarte.template.github.download
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GithubTests {

    @Test
    fun `correct url without branch`() {
        assertEquals(
            CheckResult.Success("https://api.github.com/repos/coolya/Durchblick/tarball/"),
            checkUrl("https://github.com/coolya/Durchblick")
        )
    }

    @Test
    fun `correct url with branch`() {
        assertEquals(
            CheckResult.Success("https://api.github.com/repos/coolya/heavymeta.tv/tarball/gh-pages"),
            checkUrl("https://github.com/coolya/heavymeta.tv/tree/gh-pages")
        )
    }

    @Test
    fun `some other 4 elements url on github`() {
        assertEquals(
            CheckResult.Error("Malformed URL."),
            checkUrl("https://github.com/coolya/heavymeta.tv/settings/branches")
        )
    }

    @Test
    fun `not pointing to a repository`() {
        assertEquals(CheckResult.Error("Url seems not to point to a repository."), checkUrl("https://github.com/"))
    }

    @Test
    fun `pointing to a user`() {
        assertEquals(
            CheckResult.Error("Url seems not to point to a repository."),
            checkUrl("https://github.com/coolya")
        )
    }

    @Test
    fun `invalid url`() {
        assertEquals(CheckResult.Error("Invalid Url"), checkUrl("htt://github"))
    }

    @Test
    fun `download works`() {
        val url = (checkUrl("https://github.com/coolya/Durchblick") as CheckResult.Success).url
        val download = download("test", url)
        assertTrue(download.exists())
    }

    @Test
    fun `download works on branch`() {
        val url = (checkUrl("https://github.com/coolya/heavymeta.tv/tree/gh-pages") as CheckResult.Success).url
        val download = download("test", url)
        assertTrue(download.exists())
    }

    @Test
    fun `extract works`() {
        val download = File(this.javaClass.classLoader.getResource("ws/logv/lochkarte/coolya-Durchblick-v-0.2-60-gaaa2902.tar.gz").file)
        val tempDirectory = Files.createTempDirectory("test").toFile()
        extractArchive(download, tempDirectory)
        //archive contains the repo and commit name as a root directory
        val releaseNotes = File(tempDirectory.listFiles().first(), "RELEASE_NOTES.md")
        assertTrue(releaseNotes.exists())
        assertTrue(releaseNotes.length() > 0)

    }
}