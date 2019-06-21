package akka.persistence.pg.writestrategy

import com.typesafe.config.Config

abstract class MissingWriteStrategySuite(config: Config) extends WriteStrategySuite(config) {

  import driver.api._

  override val expected = 2500

  test("polling stored events should miss events") {
    val ids                = writeEvents()
    val missing: Seq[Long] = missingIds(ids)
    println(missing)
    missing.isEmpty shouldBe false
    ids.size shouldNot equal(expected)
    database.run(journals.size.result).futureValue shouldBe expected
    ()
  }

}
