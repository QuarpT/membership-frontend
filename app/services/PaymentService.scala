package services
import com.gu.i18n.Country.UK
import com.gu.i18n.{CountryGroup, Currency}
import com.gu.i18n.Currency.GBP
import com.gu.memsub.subsv2.CatalogPlan
import com.gu.salesforce.ContactId
import com.gu.stripe.StripeService
import com.gu.zuora.soap.models.Commands.{Account, BankTransfer, CreditCardReferenceTransaction, PaymentMethod}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import model._

class PaymentService(val stripeService: StripeService) {

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
        countryCode = UK.alpha2
      ))
  }

  class CreditCardPayment(val paymentData: CreditCardData, val currency: Currency, val purchaserIds: PurchaserIdentifiers) extends Payment {
    override def makeAccount = Account.stripe(purchaserIds.memberId, currency, autopay = true)
    override def makePaymentMethod = {
      stripeService.Customer.create(description = purchaserIds.description, card = paymentData.stripeToken)
        .map(a => CreditCardReferenceTransaction(
          cardId = a.card.id,
          customerId = a.id,
          last4 = a.card.last4,
          cardCountry = CountryGroup.countryByCode(a.card.country),
          expirationMonth = a.card.exp_month,
          expirationYear = a.card.exp_year,
          cardType = a.card.`type`
        ))
    }
  }

  def makeDirectDebitPayment(paymentData: DirectDebitData, personalData: PersonalData, memberId: ContactId) = {
    require(personalData.address.country.contains(UK), "Direct Debit payment only works in the UK right now")
    DirectDebitPayment(paymentData, personalData, memberId)
  }

  def makeCreditCardPayment(
     paymentData: CreditCardData,
     desiredCurrency: Currency,
     purchaserIds: PurchaserIdentifiers,
     plan: CatalogPlan.Paid) = {
    //TODO maybe remove the plan as a parameter and assume we get a correct currency instead of a desired currency and do this outside before calling this
    val currency = if (plan.charges.price.currencies.contains(desiredCurrency)) desiredCurrency else GBP

    new CreditCardPayment(paymentData, currency, purchaserIds)
  }
}
