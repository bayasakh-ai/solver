package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The privacy policy shown in the app (PolicyText): it is the same text as the web page
 * (docs/privacy-policy.md), it parses into something readable, and it still tells the
 * truth about the build (the manifest asks for the camera and not for the internet).
 */
class PolicyTextTest {

    // unit tests run in android/app
    private val web = File("../../docs/privacy-policy.md")
    private val inApp = File("src/main/assets/privacy-policy.md")

    private fun String.unix() = replace("\r\n", "\n")

    @Test fun theAppShowsTheSameTextAsTheWebPage() {
        assertTrue("missing ${web.path}", web.exists())
        assertTrue("missing ${inApp.path}: cp docs/privacy-policy.md android/app/src/main/assets/", inApp.exists())
        assertEquals("the two copies differ: cp docs/privacy-policy.md android/app/src/main/assets/",
            web.readText().unix(), inApp.readText().unix())
    }

    @Test fun thePolicyParsesIntoTitleHeadingsParagraphsAndBullets() {
        val blocks = PolicyText.parse(inApp.readText())
        assertEquals(PolicyText.Kind.TITLE, blocks.first().kind)
        assertEquals("Sudofolio privacy policy", blocks.first().text)
        val headings = blocks.filter { it.kind == PolicyText.Kind.HEADING }.map { it.text }
        assertEquals(listOf("The short version", "What the app accesses", "What the app stores on your phone",
            "Backups made by your phone", "What the app does not do", "Children", "Third-party software",
            "Changes to this policy", "Contact"), headings)
        assertTrue(blocks.count { it.kind == PolicyText.Kind.BULLET } >= 10)
        // no Markdown left on screen, and wrapped lines are joined with single spaces
        for (b in blocks) {
            assertFalse(b.text, b.text.contains("**") || b.text.contains('`') || b.text.startsWith("#") || b.text.startsWith("- "))
            assertFalse(b.text, b.text.contains("  ") || b.text.contains('\n'))
        }
        // the header facts are separate lines, not one run-together paragraph
        val head = blocks.takeWhile { it.kind != PolicyText.Kind.HEADING }
        assertEquals(4, head.count { it.kind == PolicyText.Kind.BULLET })
        assertTrue(head.any { it.spans.first() == PolicyText.Span("App:", strong = true) })
    }

    @Test fun spans() {
        assertEquals(listOf(PolicyText.Span("Camera.", strong = true), PolicyText.Span(" The app asks. Choose "),
            PolicyText.Span("Not now", emphasis = true), PolicyText.Span(" or see com.bay.x.")),
            PolicyText.spans("**Camera.** The app asks. Choose *Not now* or see `com.bay.x`."))
        // an unclosed marker stays as typed
        assertEquals(listOf(PolicyText.Span("2 * 3 = 6")), PolicyText.spans("2 * 3 = 6"))
        assertEquals(listOf(PolicyText.Span("plain")), PolicyText.spans("plain"))
        // bullets take their indented continuation lines; a blank line ends them
        val blocks = PolicyText.parse("- first\n  goes on\n- second\n\nthen a\nparagraph")
        assertEquals(listOf("first goes on", "second", "then a paragraph"), blocks.map { it.text })
        assertEquals(listOf(PolicyText.Kind.BULLET, PolicyText.Kind.BULLET, PolicyText.Kind.PARAGRAPH), blocks.map { it.kind })
    }

    /** The policy's central promises must match what the build can do. */
    @Test fun thePolicyStillMatchesTheManifest() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val policy = inApp.readText()
        assertTrue(policy.contains("It has no internet access"))
        assertFalse("the manifest asks for the internet: rewrite the privacy policy, the Play data-safety answers " +
            "and the welcome page's promise first", manifest.contains("android.permission.INTERNET"))
        val asked = Regex("uses-permission android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.permission.CAMERA"), asked)
    }
}
