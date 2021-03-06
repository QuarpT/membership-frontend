package views.support

import abtests.{ABTest, BaseVariant}
import com.gu.i18n.{Country, CountryGroup, Currency}
import com.gu.memsub.BillingPeriod
import configuration.{Config, CopyConfig}
import model.Nav.NavItem
import model.{EventSchema, Nav}
import play.api.libs.json._

case class PageInfo(title: String = CopyConfig.copyTitleDefault,
                    url: String = "/",
                    description: Option[String] = Some(CopyConfig.copyDescriptionDefault),
                    image: Option[String] = Some(Config.membershipUrl + Asset.at("images/common/mem600.jpg")),
                    schemaOpt: Option[EventSchema] = None,
                    customSignInUrl: Option[String] = None,
                    stripeUKMembershipPublicKey: Option[String] = None,
                    stripeAUMembershipPublicKey: Option[String] = None,
                    payPalEnvironment: Option[String] = None,
                    initialCheckoutForm: CheckoutForm =
                      CheckoutForm(CountryGroup.UK.defaultCountry, CountryGroup.UK.currency, BillingPeriod.Year),
                    navigation: Seq[NavItem] = Nav.primaryNavigation,
                    abTests: Map[ABTest, BaseVariant] = Map.empty
                   )

object PageInfo {

  implicit val bpWrites = new Writes[BillingPeriod] {
    override def writes(bp: BillingPeriod): JsValue = JsString(bp.adjective)
  }

  implicit val countryWrites = new Writes[Country] {
    override def writes(c: Country): JsValue = JsString(c.alpha2)
  }

  implicit val currencyWrites = new Writes[Currency] {
    override def writes(c: Currency): JsValue = JsString(c.toString)
  }

  implicit val checkoutFormWrites = Json.writes[CheckoutForm]

  implicit val abTestWrites = new Writes[Map[ABTest, BaseVariant]] {
    override def writes(tests: Map[ABTest, BaseVariant]): JsValue = JsObject(tests.map{case (t,v) => (t.slug, JsString(v.slug)) })
  }

}
