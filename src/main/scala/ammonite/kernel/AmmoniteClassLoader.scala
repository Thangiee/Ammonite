package ammonite.kernel

import collection.mutable
import java.net.{URL, URLClassLoader}

/**
  * Classloader used to implement the jar-downloading command-evaluating logic in Ammonite.
  *
  * http://stackoverflow.com/questions/3544614/how-is-the-control-flow-to-findclass-of
  */
private[kernel] final class AmmoniteClassLoader(parent: ClassLoader, parentSignature: Seq[String])
    extends URLClassLoader(Array(), parent) {

  /**
    * Files which have been compiled, stored so that our special
    * classloader can get at them.
    */
  private val newFileDict: mutable.Map[String, Array[Byte]] = mutable.Map.empty

  def addClassFile(name: String, bytes: Array[Byte]): Unit = {
    classpathSignature0 = classpathSignature0 :+ name
    newFileDict(name) = bytes
  }

  override def findClass(name: String): Class[_] = {
    val loadedClass = this.findLoadedClass(name)
    if (loadedClass != null) {
      loadedClass
    } else if (newFileDict.contains(name)) {
      val bytes = newFileDict(name)
      defineClass(name, bytes, 0, bytes.length)
    } else {
      super.findClass(name)
    }
  }

  def add(url: URL): Unit = {
    classpathSignature0 = classpathSignature0 :+ url.toURI().getPath()
    addURL(url)
  }

  override def close() = {
    // DO NOTHING LOLZ

    // Works around
    // https://github.com/scala/scala/commit/6181525f60588228ce99ab3ef2593ecfcfd35066
    // Which for some reason started mysteriously closing these classloaders in 2.12
  }

  private[this] var classpathSignature0 = parentSignature

}

private[kernel] object AmmoniteClassLoader {

  /**
    * Stats all loose class-files in the current classpath that could
    * conceivably be part of some package, i.e. their directory path
    * doesn't contain any non-package-identifier segments, and aggregates
    * their names and mtimes as a "signature" of the current classpath
    */
  def initialClasspathSignature(classloader: ClassLoader): Seq[String] = {
    val allClassloaders: mutable.Buffer[ClassLoader] = {
      val all = mutable.Buffer.empty[ClassLoader]
      var current = classloader
      while (current != null) {
        all.append(current)
        current = current.getParent
      }
      all
    }

    allClassloaders.collect {
      case cl: java.net.URLClassLoader => cl.getURLs
    }.flatten
      .filter(_.getProtocol == "file")
      .map(_.toURI)
      .map(java.nio.file.Paths.get)
      .filter(java.nio.file.Files.isDirectory(_))
      .map(_.toUri().getPath)

  }
}
