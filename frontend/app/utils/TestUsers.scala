package utils

import java.time.Duration.ofDays

import com.gu.identity.play.IdMinimalUser
import com.gu.identity.testing.usernames.TestUsernames
import com.gu.salesforce._
import configuration.Config

object TestUsers {

  val ValidityPeriod = ofDays(2)

  lazy val testUsers = TestUsernames(
    com.gu.identity.testing.usernames.Encoder.withSecret(Config.config.getString("identity.test.users.secret")),
    recency = ValidityPeriod
  )

  def isTestUser(user: IdMinimalUser): Boolean =
    user.displayName.flatMap(_.split(' ').headOption).exists(TestUsers.testUsers.isValid)

  def isTestUser(firstName: String): Boolean =
    TestUsers.testUsers.isValid(firstName)

  // Convenience method for SalesForce users.
  // Here the assumption is that the user first name matches the test user key generated by the application
  def isTestUser(member: Contact): Boolean =
    member.firstName.exists(TestUsers.testUsers.isValid)
}
