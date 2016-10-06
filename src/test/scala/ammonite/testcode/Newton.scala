package ammonite.kernel.testcode

object Newton {

  // return the square root of c, computed using Newton's method
  // shamelessly copied from http://introcs.cs.princeton.edu/java/21function/Newton.java.html
  def sqrt(c: Double): Double = {
    if (c < 0) {
      Double.NaN
    } else {
      val EPSILON = 1E-15
      var t = c
      while (math.abs(t - c / t) > EPSILON * t) {
        t = (c / t + t) / 2.0
      }
      t
    }
  }
}
