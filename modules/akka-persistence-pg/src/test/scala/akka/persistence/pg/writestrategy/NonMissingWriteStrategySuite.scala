package akka.persistence.pg.writestrategy

import com.typesafe.config.Config

abstract class NonMissingWriteStrategySuite(config: Config) extends WriteStrategySuite(config) {

  import driver.api._

  test("polling stored events should not miss events") {
    val ids = writeEvents()
    missingIds(ids) shouldEqual Seq.empty
    ids.size shouldEqual expected
    database.run(journals.size.result).futureValue shouldBe expected
    ()
  }

}
