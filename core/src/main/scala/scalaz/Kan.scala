package scalaz

/** The right Kan extension of `H` along `G` */
trait Ran[G[_], H[_], A] { ran =>
  def apply[B](f: A => G[B]): H[B]

  def map[B](f: A => B): Ran[G, H, B] = new Ran[G, H, B] {
    def apply[C](k: B => G[C]): H[C] = ran(f andThen k)
  }

  def toAdjoint[F[_]](implicit A: Adjunction[F, G]): H[F[A]] =
    apply(a => A.unit(a))
}

object Ran {
  import Id._

  implicit def ranFunctor[G[_], H[_]]: Functor[({type λ[α] = Ran[G, H, α]})#λ] =
    new Functor[({type λ[α] = Ran[G, H, α]})#λ] {
      def map[A,B](r: Ran[G, H, A])(f: A => B) = r map f
    }

  /**
   * The universal property of a right Kan extension. The functor `Ran[G,H,_]` and the
   * natural transformation `gran[G,H,_]` are couniversal in the sense that for any
   * functor `K` and a natural transformation `s` from `K[G[_]]` to `H`, a unique
   * natural transformation `toRan` exists from `K` to `Ran[G,H,_]` such that
   * for all `k`, `gran(toRan(k)) = s(k)`.
   */
  def toRan[G[_], H[_], K[_]:Functor, B](k: K[B])(
    s: ({type λ[α] = K[G[α]]})#λ ~> H): Ran[G, H, B] = new Ran[G, H, B] {
      def apply[C](f: B => G[C]) = s(Functor[K].map(k)(f))
    }

  /**
   * `toRan` and `fromRan` witness an adjunction from `Compose[G,_,_]` to `Ran[G,_,_]`.
   */
  def fromRan[G[_], H[_], K[_], B](k: K[G[B]])(s: K ~> ({type λ[α] = Ran[G, H, α]})#λ): H[B] =
    s(k)(x => x)

  def adjointToRan[F[_], G[_], A](f: F[A])(implicit A: Adjunction[F, G]): Ran[G, Id, A] =
    new Ran[G, Id, A] {
      def apply[B](a: A => G[B]) = A.rightAdjunct(f)(a)
    }

  def ranToAdjoint[F[_], G[_], A](r: Ran[G, Id, A])(implicit A: Adjunction[F, G]): F[A] =
    r(a => A.unit(a))

  def composedAdjointToRan[F[_], G[_], H[_], A](h: H[F[A]])(
    implicit A: Adjunction[F, G], H: Functor[H]): Ran[G, H, A] = new Ran[G, H, A] {
      def apply[B](f: A => G[B]) = H.map(h)(A.rightAdjunct(_)(f))
    }

  /** This is the natural transformation that defines a right Kan extension. */
  def gran[G[_], H[_], A](r: Ran[G, H, G[A]]): H[A] =
    r(a => a)
}

/** The left Kan extension of `H` along `G` */
trait Lan[G[_], H[_], A] { lan =>
  type I
  def v: H[I]
  def f(gi: G[I]): A

  /**
   * The universal property of a left Kan extension. The functor `Lan[G,H,_]` and the
   * natural transformation `glan[G,H,_]` are universal in the sense that for any
   * functor `F` and a natural transformation `s` from `H` to `F[G[_]]`, a unique
   * natural transformation `toLan` exists from `Lan[G,H,_]` to `F` such that
   * for all `h`, `glan(h).toLan = s(h)`.
   */
  def toLan[F[_]:Functor](s: H ~> ({type λ[α] = F[G[α]]})#λ): F[A] =
    Functor[F].map(s(v))(f)

  /**
   * If `G` is left adjoint to `F`, there is a natural isomorphism between
   * `Lan[G,H,_]` and `H[F[_]]`
   */
  def toAdjoint[F[_]](implicit H: Functor[H], A: Adjunction[G,F]): H[F[A]] =
    H.map(v)(A.leftAdjunct(_)(f))

  def map[B](g: A => B): Lan[G, H, B] = new Lan[G, H, B] {
    type I = lan.I
    lazy val v = lan.v
    def f(gi: G[I]) = g(lan f gi)
  }

}

object Lan {
  import Id._

  implicit def lanFunctor[F[_], G[_]]: Functor[({type λ[α] = Lan[F,G,α]})#λ] =
    new Functor[({type λ[α] = Lan[F,G,α]})#λ] {
      def map[A,B](lan: Lan[F,G,A])(g: A => B) = lan map g
    }

  implicit def lanApplicative[G[_]:Functor, H[_]:Applicative]: Applicative[({type λ[α]=Lan[G,H,α]})#λ] =
    new Applicative[({type λ[α] = Lan[G,H,α]})#λ] {
      def point[A](a: => A) = new Lan[G,H,A] {
        type I = Unit
        val v = Applicative[H].point(())
        def f(gi: G[I]) = a
      }
      def ap[A,B](x: => Lan[G, H, A])(xf: => Lan[G, H, A => B]):
        Lan[G, H, B] {
          // TODO remove this structural type, needed only for 2.9.3
          val xp: Lan[G, H, A]
          val xfp: Lan[G, H, A => B]
        } = new Lan[G, H, B] {
        // NB: Since the existential type has to be accessed, this applicative cannot be lazy
        val xfp = xf
        val xp = x
        type I = (xfp.I, xp.I)
        lazy val v = Applicative[H].apply2(xfp.v, xp.v)((_, _))
        def f(gi: G[I]) = xfp.f(Functor[G].map(gi)(_._1))(xp.f(Functor[G].map(gi)(_._2)))
      }
    }

  /**
   * `fromLan` and `toLan` witness an adjunction from `Lan[G,_,_]` to `Compose[G,_,_]`:
   */
  def fromLan[F[_], G[_], H[_], B](h: H[B])(s: ({type λ[α] = Lan[G,H,α]})#λ ~> F): F[G[B]] =
    s(glan(h))

  /** The natural transformation that defines a left Kan extension */
  def glan[G[_], H[_], A](h: H[A]): Lan[G, H, G[A]] = new Lan[G, H, G[A]] {
    type I = A
    val v = h
    def f(gi: G[I]) = gi
  }

  def adjointToLan[F[_], G[_], A](ga: G[A])(implicit A: Adjunction[F,G]): Lan[F,Id,A] =
    new Lan[F, Id, A] {
      type I = G[A]
      lazy val v = ga
      def f(gi: F[I]) = A.counit(gi)
    }

  def lanToAdjoint[F[_], G[_], A](lan: Lan[F,Id,A])(implicit A: Adjunction[F,G]): G[A] =
    A.leftAdjunct(lan.v)(lan.f)

  def composedAdjointToLan[F[_], G[_], H[_], A](h: H[G[A]])(
    implicit A: Adjunction[F, G]): Lan[F, H, A] = new Lan[F, H, A] {
      type I = G[A]
      val v = h
      def f(fi: F[I]) = A.counit(fi)
    }
}
