import $ivy.`com.lihaoyi::scalatags:0.6.7`

import scalatags.Text.all._

/**  A brazen two-column theme inspire from https://github.com/poole/hyde */
object Hyde {

  def apply(
    baseUri: String,
    slogon: String, 
    about: Frag, 
    copyRight: Frag,
    colorTheme: Option[Base16ColorScheme] = None, 
    stickySidebar: Boolean = true, 
    reverseLayout: Boolean = false
  ) = new Hyde(baseUri, slogon, about, copyRight, colorTheme, stickySidebar, reverseLayout)

  sealed abstract class Base16ColorScheme(code: String) {
    def name = s"theme-base-$code"
  }
  final case object Base08 extends Base16ColorScheme("08")
  final case object Base09 extends Base16ColorScheme("09")
  final case object Base0a extends Base16ColorScheme("0a")
  final case object Base0b extends Base16ColorScheme("0b")
  final case object Base0c extends Base16ColorScheme("0c")
  final case object Base0d extends Base16ColorScheme("0d")
  final case object Base0e extends Base16ColorScheme("0e")
  final case object Base0f extends Base16ColorScheme("0f")

}

class Hyde private (
  baseUri: String,
  slogon: String, 
  about: Frag, 
  copyRight: Frag,
  colorTheme: Option[Hyde.Base16ColorScheme], 
  stickySidebar: Boolean, 
  reverseLayout: Boolean
) extends Layout[Frag] {

  import scalatags.Text.tags
  import scalatags.Text.tags2

  def render(navigators: List[Navigator])(content: Frag): String = {
        "<!DOCTYPE html>" + html(lang:="en-us")(
      head,
      body(cls:=s"${colorTheme.map(_.name).getOrElse("")} ${if (reverseLayout) "layout-reverse" else ""}")(
        sidebar(navigators),
        div(cls:="content container")(content)
      )
    ).render
  }

  protected def head = tags.head(
    link(href:="http://gmpg.org/xfn/11", rel:="profile"),
    meta(httpEquiv:="X-UA-Compatible", attr("content"):="IE=edge"),
    meta(httpEquiv:="content-type", attr("content"):="text/html; charset=utf-8"),
    meta(name:="viewport", attr("content"):="width=device-width, initial-scale=1.0, maximum-scale=1"),
    tags2.title(slogon),
    // CSS
    link(rel:="stylesheet", href:=s"$baseUri/public/css/poole.css"),
    link(rel:="stylesheet", href:=s"$baseUri/public/css/syntax.css"),
    link(rel:="stylesheet", href:=s"$baseUri/public/css/hyde.css"),
    link(rel:="stylesheet", href:="https://fonts.googleapis.com/css?family=Noto+Sans+SC:400"),
    // Icons
    link(rel:="apple-touch-icon-precomposed", attr("sizes"):="144x144", href:=s"$baseUri/public/apple-touch-icon-144-precomposed.png"),
    link(rel:="shortcut icon", href:=s"$baseUri/public/favicon.ico"),
  )

  protected def sidebar(navigators: List[Navigator]): Modifier = {    
    div(cls:="sidebar")(
      div(cls:=s"container ${if (stickySidebar) "sidebar-sticky" else ""}")(
        div(cls:="sidebar-about")(
          h1(slogon),
          about
        ),
        tags2.nav(cls:="sidebar-nav")(navigators.map(n => a(cls:="sidebar-nav-item", href:=n.url)(n.label))),
        copyRight
      )
    )
  }
}

