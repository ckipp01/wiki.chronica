import $file.head, head._
import $file.data, data._
import $file.modifiers, modifiers._

import java.io.File
import java.nio.file.Paths
import java.io.PrintWriter

// Pulling this in from mdoc
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser

import scala.io.Source

println("""|
           |      generating chronica
           |===============================
           |""".stripMargin)

implicit val parser = Parser.builder().build()
implicit val renderer = HtmlRenderer.builder().build()

def getListOfFiles(dir: String): List[String] = {
  val file = new File(dir)
  file.listFiles
    .filter(_.isFile)
    .map(_.getPath)
    .filter(_.endsWith(".md"))
    .toList
}

def getLogs(fileLoc: String): List[Log] = {
  val bufferedLogs = Source.fromFile(fileLoc)
  val rawLogs = bufferedLogs.getLines.mkString
  bufferedLogs.close()

  ujson
    .read(rawLogs)
    .arr
    .map(Log.fromJson)
    .toList
}

def createPage(
    fileLoc: String
)(implicit parser: Parser, renderer: HtmlRenderer): Page = {
  val topic = fileLoc.split("/").last.takeWhile(_ != '.')
  val bufferedMarkdown = Source.fromFile(fileLoc)

  val markdown = bufferedMarkdown.getLines
    .mkString("\n")

  bufferedMarkdown.close()

  val parsed = parser.parse(markdown)
  val head = createHead(topic)
  val htmlBody = renderer.render(parsed)
  val fullHtml = putTogetherHtml(head, htmlBody)
  val fileName = topic + ".html"

  println(s"---- created $fileName ----")

  Page(fileName, fullHtml)
}

def writeToOut(page: Page): Unit = {
  val out = new File("out")
  if (!out.isDirectory) {
    out.mkdir()
  }
  val pw = new PrintWriter(new File(s"out/${page.name}"))
  pw.write(page.content)
  pw.close
}

val logs = getLogs("./logs.json")
val percentageGenerator = new PercentageGenerator(logs)

val settings = mdoc
  .MainSettings()
  .withIn(Paths.get("pages"))
  .withStringModifiers(List(percentageGenerator))
  .withNoLinkHygiene(true)

val exitCode = mdoc.Main.process(settings)
if (exitCode != 0) sys.exit(exitCode)

val files = getListOfFiles("out")

val _ = files
  .map(createPage)
  .foreach(writeToOut)

println(s"""|
            |         finished creating ${files.length} pages
            |============================================
            |""".stripMargin)
