package model.exactTarget

import java.lang.Math.min
import java.util.UUID

import com.gu.exacttarget._
import com.gu.i18n.Currency
import com.gu.memsub.subsv2.{Subscription, SubscriptionPlan => Plan}
import com.gu.memsub.{Subscription => _, _}
import com.typesafe.scalalogging.LazyLogging
import model.exactTarget.DataExtensionRowHelpers._
import model.{PaperData, PersonalData}
import org.joda.time.Days.daysBetween
import org.joda.time.{Days, LocalDate}
import views.support.Dates.prettyDate

import scala.math.BigDecimal.decimal
import scalaz.NonEmptyList
import scalaz.syntax.nel._
import scalaz.syntax.std.list._

trait DataExtensionRow {
  def email: String
  def fields: Seq[(String, String)]
  def forExtension: DataExtension
}

object DataExtensionRowHelpers {
  val dd = "Direct Debit"
  val card = "Credit/Debit Card"

  def formatDate(dateTime: LocalDate) = {
    val day = dateTime.dayOfMonth.get

    val month = dateTime.monthOfYear.getAsText

    val year = dateTime.year.getAsString

    s"$day $month $year"
  }

  def formatPrice(price: Float): String = {
    decimal(price).bigDecimal.stripTrailingZeros.toPlainString
  }

  def formatAccountNumber(AccountNumber: String): String = {
    val lastFour = AccountNumber takeRight 4
    s"****$lastFour"
  }

  def formatSortCode(sortCode: String): String =
    sortCode.filter(_.isDigit).grouped(2).mkString("-")

  def trimPromotionDescription(p: String): String = p.substring(0, min(p.length, 255))
}

object DigipackWelcome1DataExtensionRow extends LazyLogging {

  def apply(
             personalData: PersonalData,
             subscription: Subscription[Plan.Paid],
             paymentMethod: PaymentMethod,
             gracePeriod: Days,
             subscriptionDetails: String,
             promotionDescription: Option[String] = None,
             currency:Currency
           ): DigipackWelcome1DataExtensionRow = {

    val paymentDelay = daysBetween(subscription.startDate, subscription.firstPaymentDate).minus(gracePeriod)

    val paymentFields = paymentMethod match {
      case GoCardless(mandateId, accName, accNumber, sortCode) => Seq(
        "Account number" -> formatAccountNumber(accNumber),
        "Sort Code" -> formatSortCode(sortCode),
        "Account Name" -> accName,
        "Default payment method" -> dd,
        "MandateID" -> mandateId
      )
      case _: PaymentCard => Seq("Default payment method" -> card)
    }

    val promotionFields = promotionDescription.map(d => "Promotion description" -> trimPromotionDescription(d))

    DigipackWelcome1DataExtensionRow(
      personalData.email,
      Seq(
        "ZuoraSubscriberId" -> subscription.name.get,
        "SubscriberKey" -> personalData.email,
        "EmailAddress" -> personalData.email,
        "Subscription term" -> subscription.plan.charges.billingPeriod.noun,
        "Payment amount" -> formatPrice(subscription.plan.charges.price.prices.head.amount),
        "First Name" -> personalData.first,
        "Last Name" -> personalData.last,
        "Address 1" -> personalData.address.lineOne,
        "Address 2" -> personalData.address.lineTwo,
        "City" -> personalData.address.town,
        "Post Code" -> personalData.address.postCode,
        "Country" -> personalData.address.country.fold(personalData.address.countryName)(_.name),
        "Date of first payment" -> formatDate(subscription.firstPaymentDate),
        "Currency" -> currency.glyph,
        "Trial period" -> paymentDelay.getDays.toString,
        "Subscription details" -> subscriptionDetails
      ) ++ paymentFields ++ promotionFields
    )
  }


}

object PaperFieldsGenerator {
  def fieldsFor(
                 paperData: PaperData,
                 personalData: PersonalData,
                 subscription: Subscription[Plan.Paid],
                 paymentMethod: PaymentMethod,
                 subscriptionDetails: String,
                 promotionDescription: Option[String] = None
               ): Seq[(String, String)] = {
    val paymentFields = paymentMethod match {
      case GoCardless(mandateId, accName, accNumber, sortCode) => Seq(
        "bank_account_no" -> formatAccountNumber(accNumber),
        "bank_sort_code" -> formatSortCode(sortCode),
        "account_holder" -> accName,
        "payment_method" -> dd,
        "mandate_id" -> mandateId
      )
      case _: PaymentCard => Seq("payment_method" -> card)
    }

    val promotionFields = promotionDescription.map(d => "promotion_details" -> trimPromotionDescription(d))

    val billingAddressFields =
      if (!personalData.address.equals(paperData.deliveryAddress)) Seq(
        "billing_address_line_1" -> personalData.address.lineOne,
        "billing_address_line_2" -> personalData.address.lineTwo,
        "billing_address_town" -> personalData.address.town,
        "billing_county" -> personalData.address.countyOrState,
        "billing_postcode" -> personalData.address.postCode,
        "billing_country" -> personalData.address.country.fold(personalData.address.countryName)(_.name)
      )
      else Seq.empty


    Seq(
      "ZuoraSubscriberId" -> subscription.name.get,
      "SubscriberKey" -> personalData.email,
      "EmailAddress" -> personalData.email,
      "subscriber_id" -> subscription.name.get,
      "IncludesDigipack" -> paperData.plan.charges.benefits.list.contains(Digipack).toString,
      "title" -> personalData.title.fold("")(_.title),
      "first_name" -> personalData.first,
      "last_name" -> personalData.last,
      "delivery_address_line_1" -> paperData.deliveryAddress.lineOne,
      "delivery_address_line_2" -> paperData.deliveryAddress.lineTwo,
      "delivery_address_town" -> paperData.deliveryAddress.town,
      "delivery_county" -> paperData.deliveryAddress.countyOrState,
      "delivery_postcode" -> paperData.deliveryAddress.postCode,
      "delivery_country" -> paperData.deliveryAddress.country.fold(paperData.deliveryAddress.countryName)(_.name),
      "date_of_first_paper" -> formatDate(paperData.startDate),
      "date_of_first_payment" -> formatDate(subscription.firstPaymentDate),
      "package" -> paperData.plan.name,
      "subscription_rate" -> subscriptionDetails
    ) ++ billingAddressFields ++ paymentFields ++ promotionFields

  }
}

object PaperHomeDeliveryWelcome1DataExtensionRow {
  def apply(
             paperData: PaperData,
             personalData: PersonalData,
             subscription: Subscription[Plan.Paid],
             paymentMethod: PaymentMethod,
             subscriptionDetails: String,
             promotionDescription: Option[String] = None
           ): PaperHomeDeliveryWelcome1DataExtensionRow = {

    val commonFields = PaperFieldsGenerator.fieldsFor(paperData, personalData, subscription, paymentMethod, subscriptionDetails, promotionDescription)
    val additionalFields = Seq("delivery_instructions" -> paperData.deliveryInstructions.getOrElse(""))

    PaperHomeDeliveryWelcome1DataExtensionRow(personalData.email, commonFields ++ additionalFields)

  }
}

object PaperVoucherWelcome1DataExtensionRow {
  def apply(
             paperData: PaperData,
             personalData: PersonalData,
             subscription: Subscription[Plan.Paid],
             paymentMethod: PaymentMethod,
             subscriptionDetails: String,
             promotionDescription: Option[String] = None
           ): PaperVoucherWelcome1DataExtensionRow = {

    val fields = PaperFieldsGenerator.fieldsFor(paperData, personalData, subscription, paymentMethod, subscriptionDetails, promotionDescription)
    PaperVoucherWelcome1DataExtensionRow(personalData.email, fields)
  }
}

object GuardianWeeklyWelcome1DataExtensionRow {
  def apply(
             paperData: PaperData,
             personalData: PersonalData,
             subscription: Subscription[Plan.Paid],
             paymentMethod: PaymentMethod,
             subscriptionDetails: String,
             promotionDescription: Option[String] = None
           ): GuardianWeeklyWelcome1DataExtensionRow = {

    val commonFields = PaperFieldsGenerator.fieldsFor(paperData, personalData, subscription, paymentMethod, subscriptionDetails, promotionDescription)
    val secondPaymentDate = subscription.firstPaymentDate plusMonths subscription.plan.charges.billingPeriod.monthsInPeriod
    val additionalFields = Seq("date_of_second_payment" -> formatDate(secondPaymentDate))
    GuardianWeeklyWelcome1DataExtensionRow(personalData.email, commonFields ++ additionalFields)
  }
}

object HolidaySuspensionBillingScheduleDataExtensionRow {

  def apply(
           email: Option[String],
           saltuation: String,
           subscriptionName: String,
           subscriptionCurrency: Currency,
           packageName: String,
           billingSchedule: BillingSchedule,
           numberOfSuspensionsLinedUp: Int,
           daysUsed: Int,
           daysAllowed: Int
         ): HolidaySuspensionBillingScheduleDataExtensionRow = {

    val (thereafterBill, trimmedSchedule) = BillingSchedule.rolledUp(billingSchedule)
    val schedule = trimmedSchedule.toList.toNel.getOrElse(thereafterBill.wrapNel)
    val futureBills = schedule.zipWithIndex.flatMap { case (bill, number) => NonEmptyList(
        s"future_bill_${number + 1}_date" -> prettyDate(bill.date),
        s"future_bill_${number + 1}_amount" -> Price(bill.amount, subscriptionCurrency).pretty
      )
    }

    val emailAddress = email.getOrElse("holiday-suspension-bounce@guardian.co.uk")

    HolidaySuspensionBillingScheduleDataExtensionRow(
      emailAddress,
      Seq(
        "ZuoraSubscriberId" -> subscriptionName,
        "SubscriberKey" -> emailAddress,
        "EmailAddress" -> emailAddress,
        "uuid" -> UUID.randomUUID().toString,
        "subscriber_id" -> subscriptionName,
        "customer_salutation" -> saltuation,
        "package_name" -> packageName,
        "days_allowed" -> daysAllowed.toString,
        "days_used" -> daysUsed.toString,
        "days_remaining" -> (daysAllowed - daysUsed).toString,
        "number_of_suspensions_lined_up" -> numberOfSuspensionsLinedUp.toString,
        "normal_price" -> Price(thereafterBill.amount, subscriptionCurrency).pretty
      ) ++ futureBills.list
    )
  }

  def constructSalutation(titleOpt: Option[String], firstNameOpt: Option[String], lastNameOpt: Option[String]): String =
    titleOpt.flatMap(title => lastNameOpt.map(lastName => s"$title $lastName")).getOrElse(firstNameOpt.getOrElse("Subscriber"))
}

case class DigipackWelcome1DataExtensionRow(email: String, fields: Seq[(String, String)]) extends DataExtensionRow {
  override def forExtension = DigipackDataExtension
}

case class PaperHomeDeliveryWelcome1DataExtensionRow(email: String, fields: Seq[(String, String)]) extends DataExtensionRow {
  override def forExtension = PaperDeliveryDataExtension
}

case class PaperVoucherWelcome1DataExtensionRow(email: String, fields: Seq[(String, String)]) extends DataExtensionRow {
  override def forExtension = PaperVoucherDataExtension
}
case class GuardianWeeklyWelcome1DataExtensionRow(email: String, fields: Seq[(String, String)]) extends DataExtensionRow {
  override def forExtension = GuardianWeeklyWelcome1DataExtension
}

case class HolidaySuspensionBillingScheduleDataExtensionRow(email: String, fields: Seq[(String, String)]) extends DataExtensionRow {
  override def forExtension = HolidaySuspensionBillingScheduleExtension
}