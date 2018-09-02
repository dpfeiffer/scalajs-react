package japgolly.scalajs.react.core

import japgolly.scalajs.react._
import japgolly.scalajs.react.test.ReactTestUtils
import japgolly.scalajs.react.test.TestUtil._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.{html, svg}
import utest._

object RefTest extends TestSuite {

  val attr = "data-ah"
  val V = "!"

  def testHtmlTag(): Unit = {
    class Backend {
      val input = Ref[html.Input]
      def addDataAttr = input.foreach(_.setAttribute(attr, V))
      def render = <.div(<.input.text(^.defaultValue := "2").withRef(input))
    }
    val C = ScalaComponent.builder[Unit]("X").renderBackend[Backend].componentDidMount(_.backend.addDataAttr).build
    ReactTestUtils.withNewBodyElement { mountNode =>
      val mounted = C().renderIntoDOM(mountNode)
      assertEq(mounted.getDOMNode.asMounted().asElement().querySelector("input").getAttribute(attr), V)
    }
  }

  def testSvgTag(): Unit = {
    import japgolly.scalajs.react.vdom.svg_<^._
    class Backend {
      val circle = Ref[svg.Circle]
      def addDataAttr = circle.foreach(_.setAttribute(attr, V))
      def render = <.svg(<.circle().withRef(circle))
    }
    val C = ScalaComponent.builder[Unit]("X").renderBackend[Backend].componentDidMount(_.backend.addDataAttr).build
    ReactTestUtils.withNewBodyElement { mountNode =>
      val mounted = C().renderIntoDOM(mountNode)
      assertEq(mounted.getDOMNode.asMounted().asElement().querySelector("circle").getAttribute(attr), V)
    }
  }

  object TestScala {
    object InnerScala {
      class B { def secret = 666 }
      val C = ScalaComponent.builder[Int]("X").backend(_ => new B).render_P(i => <.p(s"Hello $i")).build
    }

    def testRef(): Unit = {
      class Backend {
        val ref = Ref.toScalaComponent(InnerScala.C)
        def render = <.div(ref.component(123))
      }
      val C = ScalaComponent.builder[Unit]("X").renderBackend[Backend].build
      ReactTestUtils.withNewBodyElement { mountNode =>
        val mounted = C().renderIntoDOM(mountNode)
        assertEq(mounted.backend.ref.unsafeGet().backend.secret, 666)
      }
    }

    def testRefAndKey(): Unit = {
      class Backend {
        val ref = Ref.toScalaComponent(InnerScala.C)
        def render = <.div(ref.component.withKey(555555555)(123))
      }
      val C = ScalaComponent.builder[Unit]("X").renderBackend[Backend].build
      ReactTestUtils.withNewBodyElement { mountNode =>
        val mounted = C().renderIntoDOM(mountNode)
        assertEq(mounted.backend.ref.unsafeGet().backend.secret, 666)
      }
    }
  }

  object TestJs {
    val InnerJs = JsComponentEs6STest.Component

    def testRef(): Unit = {
      class Backend {
        val ref = Ref.toJsComponent(InnerJs)
        def render = <.div(ref.component())
      }
      val C = ScalaComponent.builder[Unit]("X").renderBackend[Backend].build
      ReactTestUtils.withNewBodyElement { mountNode =>
        val mounted = C().renderIntoDOM(mountNode)
        mounted.backend.ref.unsafeGet().raw.inc() // compilation and evaluation without error is test enough
      }
    }

    def testRefAndKey(): Unit = {
      class Backend {
        val ref = Ref.toJsComponent(InnerJs)
        def render = <.div(ref.component.withKey(555555555)())
      }
      val C = ScalaComponent.builder[Unit]("X").renderBackend[Backend].build
      ReactTestUtils.withNewBodyElement { mountNode =>
        val mounted = C().renderIntoDOM(mountNode)
        mounted.backend.ref.unsafeGet().raw.inc() // compilation and evaluation without error is test enough
      }
    }
  }

  object TestRefForwarding {

    private class InnerScalaBackend($: BackendScope[Int, Unit]) {
      def gimmeHtmlNow() = $.getDOMNode.runNow().asMounted().asHtml().outerHTML
      def render(p: Int) = <.h1(s"Scala$p")
    }
    private lazy val InnerScala = ScalaComponent.builder[Int]("Scala").renderBackend[InnerScalaBackend].build

    object WithPropsToVdom {
      private lazy val Forwarder = React.forwardRef.toVdom[String, html.Button]((label, ref) =>
        <.div(<.button.withRef(ref)(label)))

      def withoutRef() = assertRender(Forwarder("hehe"), "<div><button>hehe</button></div>")

      def withRef() = {
        class Backend {
          val ref = Ref[html.Button]
          def render = Forwarder.withRef(ref)("ok")
        }
        val C = ScalaComponent.builder[Unit]("X").renderBackend[Backend].build
        ReactTestUtils.withNewBodyElement { mountNode =>
          val mounted = C().renderIntoDOM(mountNode)
          assertRendered(mounted.getDOMNode.asMounted().asHtml(), "<div><button>ok</button></div>")
          assertEq(mounted.backend.ref.get.asCallback.runNow().map(_.outerHTML), Some("<button>ok</button>"))
        }
      }

      def wideRef() = assertCompiles(Forwarder.withRef(Ref[html.Element])("ok"))

      def narrowRef() = {
        def X = React.forwardRef.toVdom[String, html.Element]((label, ref) => <.div(<.button.withRef(ref)(label)))
        compileError(""" X.withRef(Ref[html.Button])("ok") """)
      }

      def scalaRef() = {
        def ref = Ref.toScalaComponent(InnerScala)
        compileError(""" Forwarder.withRef(ref)("ok") """)
      }
    }

    object WithPropsToScala {
      // TODO ref should be Option
      // TODO Components need .withRef(ref) and .withRef(Option(ref))
      private lazy val Forwarder = React.forwardRef.toScalaComponent(InnerScala)[String]((label, ref) =>
        <.div(label, InnerScala.withRef(ref)(123)))

      def withoutRef() = assertRender(Forwarder("hey"), "<div>hey<h1>Scala123</h1></div>")

      def withRef() = {
        class Backend {
          val ref = Ref.toScalaComponent(InnerScala)
          def render = Forwarder.withRef(ref)("noice")
        }
        val C = ScalaComponent.builder[Unit]("X").renderBackend[Backend].build
        ReactTestUtils.withNewBodyElement { mountNode =>
          val mounted = C().renderIntoDOM(mountNode)
          assertRendered(mounted.getDOMNode.asMounted().asHtml(), "<div>noice<h1>Scala123</h1></div>")
          assertEq(mounted.backend.ref.get.asCallback.runNow().map(_.gimmeHtmlNow()), Some("<h1>Scala123</h1>"))
        }
      }

      def wrongScala() = {
        def Scala2 = ScalaComponent.builder[Int]("Scala2").renderStatic(<.div).build
        def ref = Ref.toScalaComponent(Scala2)
        compileError(""" Forwarder.withRef(ref)("nah mate") """)
      }

      def vdomRef() = compileError(""" Forwarder.withRef(Ref[html.Button])("nah mate") """)
    }
  }

  override def tests = Tests {
    'htmlTag - testHtmlTag()
    'svgTag - testSvgTag()
    'scalaComponent - {
      'ref - TestScala.testRef()
      'refAndKey - TestScala.testRefAndKey()
    }
    'jsComponent - {
      'ref - TestJs.testRef()
      'refAndKey - TestJs.testRefAndKey()
    }
    'forwardRefs - {
      'vdom {
        import TestRefForwarding.WithPropsToVdom._
        'withoutRef - withoutRef()
        'withRef    - withRef()
        'wideRef    - wideRef()
        'narrowRef  - narrowRef()
        'scalaRef   - scalaRef()
      }
      'scala {
        import TestRefForwarding.WithPropsToScala._
        'withoutRef - withoutRef()
        'withRef    - withRef()
        'wrongScala - wrongScala()
        'vdomRef    - vdomRef()
      }
    }
  }
}
