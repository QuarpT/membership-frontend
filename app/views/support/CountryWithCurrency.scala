package views.support

import com.gu.i18n
import com.gu.i18n.{Country, CountryGroup, Currency}

import scala.language.implicitConversions

sealed abstract class StripeServiceName
case object UKStripeService extends StripeServiceName
case object AUStripeService extends StripeServiceName

case class CountryWithCurrency(country: i18n.Country, currency: i18n.Currency, stripeServiceName: StripeServiceName)

object CountryWithCurrency {
  val all = i18n.CountryGroup.allGroups.flatMap(fromCountryGroup).sortBy(_.country.name)

  def withCurrency(c: Currency) = all.map(_.copy(currency = c))

  def pickStripeService(country: Country) = if(country == Country.Australia) AUStripeService else UKStripeService

  def fromCountryGroup(countryGroup: CountryGroup): List[CountryWithCurrency] = countryGroup.countries.map(c => CountryWithCurrency(c, countryGroup.currency, pickStripeService(c)))

  def whitelisted(availableCurrencies: Set[Currency], default: Currency, availableCountryGroups: List[CountryGroup] = CountryGroup.allGroups): List[CountryWithCurrency] = {
    def ensureValidCurrency(group: CountryGroup) = if (availableCurrencies.contains(group.currency)) group else group.copy(currency = default)
    availableCountryGroups.map(ensureValidCurrency).flatMap(fromCountryGroup).sortBy(_.country.name)
  }

  implicit def toCountry(countryWithCurrency: CountryWithCurrency): Country = countryWithCurrency.country
}

case class CountryAndCurrencySettings(availableDeliveryCountries: Option[List[CountryWithCurrency]], availableBillingCountries: List[CountryWithCurrency], defaultCountry: Option[Country], defaultCurrency: Currency)

object DetermineCountryGroup {

  /***
    * This method takes a hint String and returns a CountryGroup if it can map to it. If the hint also implies a Country
    * which exists within the CountryGroup, then it sets the defaultCountry within that CountryGroup to that Country.
    * @param hint a CountryCode ID or a Country.alpha2 value.
    * @return a potentially modified CountryGroup
    */
  def fromHint(hint: String): Option[CountryGroup] = {
    val possibleCountryGroup = CountryGroup.byId(hint) orElse CountryGroup.byCountryCode(hint.toUpperCase)
    possibleCountryGroup.map { foundCountryGroup =>
      val determinedCountry = CountryGroup.countryByCode(hint.toUpperCase) orElse foundCountryGroup.defaultCountry
      foundCountryGroup.copy(defaultCountry = determinedCountry)
    }
  }
}
