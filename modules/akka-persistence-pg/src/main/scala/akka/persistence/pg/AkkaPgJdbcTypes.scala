package akka.persistence.pg

import java.sql._
import java.time.OffsetDateTime
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField

import org.postgresql.util.HStoreConverter
import slick.ast.Library.SqlOperator
import slick.ast.{FieldSymbol, TypedType}
import slick.jdbc.{JdbcType, JdbcTypesComponent, PostgresProfile}
import slick.lifted.ExtensionMethods

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.reflect.ClassTag

trait AkkaPgJdbcTypes extends JdbcTypesComponent { driver: PostgresProfile =>

  def pgjson: String

  import driver.api._

  private[this] class GenericJdbcType[T](
      val sqlTypeName: String,
      fnFromString: String => T,
      fnToString: T => String = (r: T) => r.toString,
      val sqlType: Int = java.sql.Types.OTHER,
      zero: T = null.asInstanceOf[T],
      override val hasLiteralForm: Boolean = false
  )(implicit override val classTag: ClassTag[T])
      extends DriverJdbcType[T] {

    override def sqlTypeName(sym: Option[FieldSymbol]): String = sqlTypeName

    override def getValue(r: ResultSet, idx: Int): T = {
      val value = r.getString(idx)
      if (r.wasNull) zero else fnFromString(value)
    }

    override def setValue(v: T, p: PreparedStatement, idx: Int): Unit = p.setObject(idx, toStr(v), java.sql.Types.OTHER)

    override def updateValue(v: T, r: ResultSet, idx: Int): Unit = r.updateObject(idx, toStr(v), java.sql.Types.OTHER)

    override def valueToSQLLiteral(v: T): String = if (v == null) "NULL" else s"'${fnToString(v)}'"

    private def toStr(v: T) = if (v == null) null else fnToString(v)
  }

  protected def fromInfinitable[T](max: T, min: T, parse: String => T): String => T = {
    case "infinity"  => max
    case "-infinity" => min
    case finite      => parse(finite)
  }

  protected def toInfinitable[T](max: T, min: T, format: T => String): T => String = {
    case `max`  => "infinity"
    case `min`  => "-infinity"
    case finite => format(finite)
  }

  private val date2TzDateTimeFormatter =
    new DateTimeFormatterBuilder()
      .append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
      .optionalStart()
      .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
      .optionalEnd()
      .appendOffset("+HH:mm", "+00")
      .toFormatter()

  protected val fromOffsetDateTimeOrInfinity: String => OffsetDateTime =
    fromInfinitable(OffsetDateTime.MAX, OffsetDateTime.MIN, OffsetDateTime.parse(_, date2TzDateTimeFormatter))
  protected val toOffsetDateTimeOrInfinity: OffsetDateTime => String =
    toInfinitable[OffsetDateTime](OffsetDateTime.MAX, OffsetDateTime.MIN, _.format(date2TzDateTimeFormatter))

  trait AkkaPgImplicits {

    implicit val date2TzTimestampTypeMapper: JdbcType[OffsetDateTime] = new GenericJdbcType[OffsetDateTime](
      "timestamptz",
      fromOffsetDateTimeOrInfinity,
      toOffsetDateTimeOrInfinity,
      hasLiteralForm = false
    )

    implicit val simpleHStoreTypeMapper: JdbcType[Map[String, String]] =
      new GenericJdbcType[Map[String, String]](
        "hstore",
        v => HStoreConverter.fromString(v).asScala.toMap,
        v => HStoreConverter.toString(v.asJava),
        hasLiteralForm = false
      )

    implicit def simpleHStoreColumnExtensionMethods(
        c: Rep[Map[String, String]]
    ): HStoreColumnExtensionMethods[Map[String, String]] =
      new HStoreColumnExtensionMethods[Map[String, String]](c)

    implicit val jsonStringTypeMapper: JdbcType[JsonString] =
      new GenericJdbcType[JsonString](
        pgjson,
        v => JsonString(v),
        v => v.value,
        hasLiteralForm = false
      )

  }

  /** Extension methods for hstore Columns */
  class HStoreColumnExtensionMethods[P1](val c: Rep[P1])(implicit tm: JdbcType[Map[String, String]])
      extends ExtensionMethods[Map[String, String], P1] {

    val Contains = new SqlOperator("@>")

    protected implicit def b1Type: TypedType[Map[String, String]] = implicitly[TypedType[Map[String, String]]]

    def @>[P2, R](c2: Rep[P2])(implicit om: o#arg[Map[String, String], P2]#to[Boolean, R]): Rep[R] =
      om.column(Contains, n, c2.toNode)
  }

}
