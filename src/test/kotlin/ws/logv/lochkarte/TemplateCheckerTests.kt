package ws.logv.lochkarte

import org.junit.Test
import ws.logv.lochkarte.helper.checkTemplate
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

class TemplateCheckerTests {
    @Test
    fun `reports nested runtime solution`() {
        val testProjectPath = System.getenv("TESTPROJECT_LOCATION") ?: fail("no test project location")
        val root = File(testProjectPath)
        val messages = checkTemplate(root)
        assertTrue(messages.any { it.contains("runtime solution") })
    }

    @Test
    fun `reports nested sandbox solution`() {
        val testProjectPath = System.getenv("TESTPROJECT_LOCATION") ?: fail("no test project location")
        val root = File(testProjectPath)
        val messages = checkTemplate(root)
        assertTrue(messages.any { it.contains("sandbox solution") })
    }
}