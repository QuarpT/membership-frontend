package services

import com.gu.i18n.{Currency, GBP, Country}
import com.gu.salesforce.ContactId
import com.gu.stripe.StripeService
import com.gu.zuora.soap.actions.subscribe._
import model._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

trait PaymentService {
  def stripeService: StripeService

  sealed trait Payment {
    def makeAccount: Account
    def makePaymentMethod: Future[PaymentMethod]
  }

  case class DirectDebitPayment(paymentData: DirectDebitData, personalData: PersonalData, memberId: ContactId) extends Payment {
    override def makeAccount = Account.goCardless(memberId, GBP, autopay = true)

    override def makePaymentMethod =
      Future(BankTransfer(
        accountNumber = paymentData.account,
        sortCode = paymentData.sortCode,
        accountHolderName = paymentData.holder,
        firstName = personalData.first,
        lastName = personalData.last,
        countryCode = Country.UK.alpha2
      ))
  }

  class CreditCardPayment(val paymentData: CreditCardData, val currency: Currency, userIdData: UserIdData, val memberId: ContactId) extends Payment {
    override def makeAccount = Account.stripe(memberId, currency, autopay = true)
    override def makePaymentMethod =
      stripeService.Customer.create(userIdData.id.id, paymentData.stripeToken)
        .map(CreditCardReferenceTransaction)
  }

  def makeDirectDebitPayment(paymentData: DirectDebitData, personalData: PersonalData, memberId: ContactId) = {
    require(personalData.country == Country.UK, "Direct Debit payment only works in the UK right now")
    new DirectDebitPayment(paymentData, personalData, memberId)
  }

  def makeCreditCardPayment(paymentData: CreditCardData, personalData: PersonalData, userIdData: UserIdData, memberId: ContactId) = {
    new CreditCardPayment(paymentData, personalData.currency, userIdData, memberId = memberId)
  }
}