package ws.logv.lochkarte.template.local

import com.ibm.icu.text.CharsetDetector
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.charset.Charset
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption

val supportedFileExtensions =
    listOf(
        "gradle",
        "gradle.kts",
        "java",
        "c",
        "h",
        "cc",
        "cpp",
        "cxx",
        "c++",
        "h",
        "H",
        "hh",
        "hpp",
        "hxx",
        "h++",
        "cs",
        "csx",
        "fs",
        "fsx",
        "html",
        "htm",
        "md",
        "markdown",
        "xml"
    )

fun macro(name: String, value: String): Pair<String, String> {
    return Pair("%%${name}%%", value)
}

fun replaceMacros(project: Project) {

    if (project.basePath == null) {
        return
    }

    val root = File(project.basePath!!)
    if (!root.exists()) {
        return
    }

    val macros = mapOf(macro("project-name", project.name))

    root.walk().filter { file -> !file.isDirectory && supportedFileExtensions.any { file.name.endsWith(it) } }.forEach {
        replaceMacrosInFile(it, macros)
    }

}

fun replaceMacrosInFile(it: File, macros: Map<String, String>) {
    val detector = CharsetDetector()
    detector.setText(it.inputStream())
    val charsetMatch = detector.detect()
    val reader = charsetMatch.reader
    val tempFile = File.createTempFile(it.nameWithoutExtension, it.extension)
    val writer = tempFile.printWriter(Charset.forName(charsetMatch.name))
    reader.readLines()
        .forEach { line -> writer.println(macros.entries.fold(line) { l, m -> l.replace(m.key, m.value) }) }
    writer.close()
    reader.close()
    val permissions = Files.getPosixFilePermissions(it.toPath())
    val owner = Files.getOwner(it.toPath())
    Files.move(tempFile.toPath(), it.toPath(), StandardCopyOption.REPLACE_EXISTING)
    Files.setOwner(it.toPath(), owner)
    Files.setPosixFilePermissions(it.toPath(), permissions)
}