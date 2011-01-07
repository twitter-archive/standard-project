package com.twitter.sbt

trait IntegrationSpecs extends StandardProject {
  val integrationTestSuffix = "IntegrationSpec"
  lazy val integrationTestOptions: Seq[TestOption] =
    TestListeners(testListeners) :: TestFilter(_ endsWith integrationTestSuffix) :: Nil
  lazy val integrationTest = defaultTestTask(integrationTestOptions)
  override def testOptions =
    super.testOptions ++ Seq(TestFilter(name => !(name endsWith integrationTestSuffix)))
}