package reactivemongo.api.bson

import scala.util.{ Failure, Try }

trait BSONDocumentReader[T] extends BSONReader[T] { self =>
  final def readTry(bson: BSONValue): Try[T] = bson match {
    case doc: BSONDocument => readDocument(doc)

    case _ => Failure(exceptions.TypeDoesNotMatchException(
      "BSONDocument", bson.getClass.getSimpleName))
  }

  def readDocument(doc: BSONDocument): Try[T]

  final override def afterRead[U](f: T => U): BSONDocumentReader[U] =
    new BSONDocumentReader.MappedReader[T, U](self, f)

  final def beforeRead(f: PartialFunction[BSONDocument, BSONDocument]): BSONDocumentReader[T] = new BSONDocumentReader[T] {
    val underlying = BSONDocumentReader.collect(f)

    def readDocument(doc: BSONDocument): Try[T] =
      underlying.readDocument(doc).flatMap(self.readDocument)
  }
}

object BSONDocumentReader {
  /** Creates a [[BSONDocumentReader]] based on the given `read` function. */
  def apply[T](read: BSONDocument => T): BSONDocumentReader[T] =
    new FunctionalReader[T](read)

  /** Creates a [[BSONDocumentReader]] based on the given `read` function. */
  def from[T](read: BSONDocument => Try[T]): BSONDocumentReader[T] =
    new DefaultReader[T](read)

  /** Creates a [[BSONDocumentReader]] based on the given partial function. */
  def collect[T](read: PartialFunction[BSONDocument, T]): BSONDocumentReader[T] = new FunctionalReader[T]({ doc =>
    read.lift(doc) getOrElse {
      throw exceptions.ValueDoesNotMatchException(BSONDocument pretty doc)
    }
  })

  // ---

  private class DefaultReader[T](
    read: BSONDocument => Try[T]) extends BSONDocumentReader[T] {

    def readDocument(doc: BSONDocument): Try[T] = read(doc)
  }

  private class FunctionalReader[T](
    read: BSONDocument => T) extends BSONDocumentReader[T] {

    def readDocument(doc: BSONDocument): Try[T] = Try(read(doc))
  }

  private[bson] class MappedReader[T, U](
    parent: BSONDocumentReader[T],
    to: T => U) extends BSONDocumentReader[U] {
    def readDocument(doc: BSONDocument): Try[U] =
      parent.readDocument(doc).map(to)
  }
}
