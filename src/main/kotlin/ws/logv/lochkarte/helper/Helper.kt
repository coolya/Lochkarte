package ws.logv.lochkarte.helper
import java.io.File

fun checkTemplate(root: File): List<String> {

    //see https://regex101.com/r/VSC5fQ/1 explanation of this regex
    val s = "\\${File.separator}"
    val runtimeRegEx = """[\S\s]*${s}languages${s}[\S\s]*${s}runtime${s}[\S\s]*\.msd"""
    val runtimeNested = root.walk().filter { file ->
        file.path.matches(Regex(runtimeRegEx)) }.map {
        "${it.path} appears to be a runtime solution nested into a language folder. This isn't supported at the moment. "
    }.toList()

    val sandboxRegEx = """[\S\s]*${s}languages${s}[\S\s]*${s}sandbox${s}[\S\s]*\.msd"""
    val sandboxNested = root.walk().filter { file ->
        file.path.matches(Regex(sandboxRegEx)) }.map {
        "${it.path} appears to be a sandbox solution nested into a language folder. This isn't supported at the moment. "
    }.toList()

    return runtimeNested + sandboxNested
}