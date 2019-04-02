type Output = os.RelPath => String => Unit

final case class Navigator(label: String, url: String)

trait Layout[Frag] {
  def render(navigators: List[Navigator])(content: Frag): String
}

final class Blog[Data, Frag] private (
  layout: Layout[Frag],
  output: Output,
  navigators: List[Navigator] = Nil, 
  internals: List[Blog.Internal[Data]] = Nil
) {  
  def external(label: String, url: String): Blog[Data, Frag] = {
    new Blog(layout, output, Navigator(label, url) :: navigators, internals)
  }

  def internal(label: String, path: os.RelPath, render: Data => Frag): Blog[Data, Frag] = {
    val in: Blog.Internal[Data] =  ns => d => output(path)(layout.render(ns)(render(d)))
    new Blog(layout, output, Navigator(label, path.toString) :: navigators, in :: internals)
  }

  def posts(render: Data => List[(os.RelPath, Frag)]): Data => Unit = { d =>
    internals.foreach(_(navigators)(d))
    render(d).foreach { case (path, frag) => output(path)(layout.render(navigators)(frag)) }
  }
}
object Blog {
  type Internal[Data] = List[Navigator] => Data => Unit
  def apply[Data, Frag]()(implicit layout: Layout[Frag], output: Output) = new Blog[Data, Frag](layout, output)
} 

object date {
  import java.util.Date
  import java.text.SimpleDateFormat

  implicit val format: String => Date => String = pattern => {
    new SimpleDateFormat(pattern).format(_)
  }

  implicit val parse: String => String => Date = pattern => {
    new SimpleDateFormat(pattern).parse(_)
  }

  def now = new Date()
}

object pipe {
  implicit class Ops[A, B](val a: A) extends AnyVal {
    def |(f: A => B): B = f(a)
  }
}