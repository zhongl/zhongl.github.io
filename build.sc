import $ivy.`com.lihaoyi::scalatags:0.6.7`
import $ivy.`com.lihaoyi::requests:0.1.7`
import $ivy.`org.yaml:snakeyaml:1.24`
import $ivy.`org.jsoup:jsoup:1.11.3`
import $ivy.`com.vladsch.flexmark:flexmark-all:0.40.32`

import scala.collection.JavaConverters._
import ammonite.ops._
import date._, pipe._
import scalatags.Text.all._
import $file.template.layout

import upickle.default.{ReadWriter => RW, macroRW}

object api {

  // https://developer.github.com/v3/issues/#response-1
  final case class Issue(id: Int, title: String, created_at: String, user: User, html_url: String, labels: List[Label], comments: Int, body: String) {
    private lazy val meta: Map[String, String] = {
      import org.jsoup._

      // metadata hidden in body
      Jsoup.parseBodyFragment(body_html)
          .select("input[type=hidden]")
          .asScala
          .map(e => e.attr("name") -> e.attr("value"))
          .toMap
    }

    lazy val body_html: String = {
      import com.vladsch.flexmark.parser._
      import com.vladsch.flexmark.html._
      import com.vladsch.flexmark.util.options._
      import com.vladsch.flexmark.ext.tables._
      import com.vladsch.flexmark.ext.footnotes._
      import com.vladsch.flexmark.ext.toc._
      
      val options = new MutableDataSet()
        .set(Parser.EXTENSIONS, Seq(
          TablesExtension.create(),
          SimTocExtension.create(),
          FootnoteExtension.create()
        ).asJava)

      val parser = Parser.builder(options).build()
      val renderer = HtmlRenderer.builder(options).build()
      renderer.render(parser.parse(body))
    }

    // For permanently human-readable link
    def path: RelPath = RelPath((created | "yyyy/MM/dd/") + meta.get("id").map(_.replace(' ', '-')).getOrElse(id))

    def created: java.util.Date = {
      // For legacy post specified real created date
      meta.get("created").map(_ | "yyyy-MM-dd").getOrElse(created_at | "yyyy-MM-dd'T'HH:mm:ss'Z'")    
    }
  }
  object Issue {
    implicit val rw: RW[Issue] = macroRW
  }
  final case class User(login: String, html_url: String)
  object User {
    implicit val rw: RW[User] = macroRW
  }
  final case class Label(name: String, color: String = "#44555d") {
    def path: RelPath = RelPath("tags") / (name.replace(' ','-').toLowerCase)
  }
  object Label {
    implicit val rw: RW[Label] = macroRW
  }

}

@main
@doc("Generate static site files from template directory.")
def generate(
  owner: String @doc("github account"),
  repo: String @doc("github repository"),
  site: String @doc("base uri or directory of site") = (pwd / "_site").toString, 
  to: String @doc("directory to generate") = "_site" 
) = {
  import api._

  implicit val l = layout.Hyde(
    baseUri = site,
    slogon = "放着, 我来",
    about = blockquote("理想和情怀是有代价的, 但好在它不是自由的枷锁, 而应该是真正的自由本身. ", a(href:="https://weibo.com/1875401263/DDSIEbOJy")("by @dcaoyuan")),
    copyRight = p(RawFrag(s"&copy; ${now | "yyyy"}. All rights reserved"))
  )

  implicit val o: Output = path => {
    val t = pwd / to / path 
    os.makeDir.all(t)
    write.over(t / "index.html", _)
  }

  implicit val dateFrag: java.util.Date => Frag = d => stringFrag(d | "dd MMM yyyy")

  @inline def post(i: Issue): (RelPath, Frag) = {
    println(s"generate post: ${i.path}")
    i.path -> div(cls:="post")(
      h1(cls:="post-title")(i.title),
      p(cls:="post-meta")(i.created, RawFrag(" @"), a(href:=i.user.html_url)(i.user.login)),
      RawFrag(i.body_html),
      div(
        i.labels.map(l => a(href:=s"$site/${l.path}", cls:="post-badge")(l.name)),
        a(href:=i.html_url,cls:="post-comment")(s"Comment(${i.comments})")
      ),
      div()
    )
  } 

  @inline def list(is: List[Issue]): Frag = {
    ul(cls:="related-posts")(is.map { i =>
      li(h2(a(href:=i.path.toString)(i.title, " ", small(i.created))))
    })
  }

  @inline def labels(is: List[Issue]): List[(RelPath, Frag)] = {
    val group = is.foldLeft(Map.empty[Label, List[Issue]]) { (m, i) =>
      i.labels.foldLeft(m) { (g, l) =>
        g + (l -> (i :: m.getOrElse(l, Nil)))
      }
    }
    for ((l, pis) <- group.toList) yield {
      println(s"generate label: ${l.path}")
      l.path -> div(cls:="label")(h1(l.name), list(pis))
    }
  }

  val generate = Blog[List[Issue], Frag]()
    .external("Github", s"https://github.com/$owner")
    .internal("Home", RelPath.rel, is => div(list(is)))
    .posts { is => labels(is) ++ is.map(post) }
  
  val response = requests.get(s"https://api.github.com/repos/$owner/$repo/issues", params = Map("state" -> "closed"))
  val issues = upickle.default.read[List[Issue]](response.text)

  rm! pwd / to
  generate(issues)
  cp.over(pwd / "template" / "public", pwd / to / "public")
  write(pwd / to / "README.md", "**This is generated static blog by Github issues, more information please see [branch iblog](https://github.com/zhongl/zhongl.github.com/tree/iblog)**.")
}

object markdown {
  import scala.collection.mutable.Map
  import org.yaml.snakeyaml._

  sealed trait Phase {
    def next(line: String): Phase
    def finish: Option[Post]    
  }
  final case class Ignore(id: String) extends Phase {
    def next(line: String) = line match {
      case "---" => CollectMeta(id, "")
      case _     => this
    }
    def finish = None
  }
  final case class CollectMeta(id: String, meta: String) extends Phase {
    private val yaml = new Yaml()
    def next(line: String) = line match {
      case "---" => CollectBody(id, yaml.load(meta).asInstanceOf[java.util.Map[String, AnyRef]].asScala, "")
      case _     => CollectMeta(id, s"$meta\n${line.replace('\t',' ')}")
    }
    def finish = None
  }
  final case class CollectBody(id: String, meta: Map[String, AnyRef], body: String) extends Phase {
    def next(line: String) = CollectBody(id, meta, s"$body\n$line")
    def finish = Some {
      @inline def date(a: AnyRef): String = a.asInstanceOf[String] | "yyyy-MM-dd HH:mm:ss Z" | "yyyy-MM-dd"

      val hiddens = List(
        Option(input(`type`:="hidden", name:="id", value:=id)),
        meta.get("date").map(v => input(`type`:="hidden", name:="created", value:=date(v)))
      ).filter(_.isDefined).map(_.get).map(_.render).mkString("\n")
      val labels = meta.get("tags").map(_.asInstanceOf[java.util.List[String]].asScala).getOrElse(Seq.empty[String])
      Post(meta("title").asInstanceOf[String], labels.map(_.toLowerCase), s"$hiddens\n$body")
    }
  }

  final case class Post(title: String, labels: Seq[String], body: String)
  object Post {
    implicit val rw: RW[Post] = macroRW
  }

}

@main
@doc("Migrate legacy markdown files to issues.")
def migrate(
  owner: String @doc("github account"),
  repo: String @doc("github repository"),
  token: String @doc("personal access token of github"),
  path: os.Path @doc("the directory of markdown files") = pwd / "_posts"
) = {
  import api.Label
  import markdown._

  @inline def id(s: String): String = {
    s.substring(11, s.length - 3).replace('-', ' ')
  }

  @inline def post(path: Path): Post = {
    val ph: Phase = Ignore(id(path.last))
    read.lines(path).foldLeft(ph)(_ next _).finish.getOrElse(throw new IllegalStateException(s"Invalid markdown file: $path"))   
  }

  val auth = "Authorization" -> s"token $token"
  val json = "Content-Type" -> "application/json"
  val posts = (ls! path).map(post)

  posts.map(_.labels).flatten.distinct.foreach { n =>
    val r = requests.post(s"https://api.github.com/repos/$owner/$repo/labels", headers = List(auth, json), data = upickle.default.write(Label(n))) 
    r.statusCode match {
      case 201  => println(s"Label $n created")
      case code => println(s"Error: ${r.url} $code ${r.text()}")
    }
  }
  posts.foreach { p =>
    val r = requests.post(s"https://api.github.com/repos/$owner/$repo/issues", headers = List(auth, json), data = upickle.default.write(p)) 
    r.statusCode match {
      case 201  => println(s"Post ${p.title} created")
      case code => println(s"Error: ${r.url} $code ${r.text()}")
    }
  }
}