package services.api

import com.gu.i18n.Country
import com.gu.identity.play.IdUser
import com.gu.memsub.Subscriber._
import com.gu.memsub.promo.PromoError
import com.gu.memsub.subsv2._
import com.gu.memsub.{BillingSchedule, Subscription => S}
import com.gu.salesforce.{ContactId, PaidTier, Tier}
import com.gu.zuora.soap.models.Commands.PaymentMethod
import com.gu.zuora.soap.models.Results.{CreateResult, SubscribeResult}
import controllers.IdentityRequest
import forms.MemberForm._
import model.Eventbrite.{EBCode, EBOrder, EBTicketClass}
import model.RichEvent.RichEvent
import model.{GenericSFContact, PlanChoice}
import utils.ReferralData
import views.support.ThankyouSummary

import scala.concurrent.Future
import scalaz.\/

trait MemberService {
  import MemberService._

  type ZuoraSubName = String

  def country(contact: GenericSFContact)(implicit i: IdentityRequest): Future[Country]

  def createMember(user: IdUser,
                   formData: JoinForm,
                   fromEventId: Option[String],
                   tier: Tier,
                   ipCountry: Option[Country],
                   referralData: ReferralData): Future[(ContactId, ZuoraSubName)]


  def createContributor(user: IdUser,
                   formData: ContributorForm): Future[(ContactId, ZuoraSubName)]

  def previewUpgradeSubscription(subscriber: PaidMember, newPlan: PlanChoice)
                                (implicit i: IdentityRequest): Future[MemberError \/ BillingSchedule]

  def upgradeFreeSubscription(sub: FreeMember, newTier: PaidTier, form: FreeMemberChangeForm, referralData: ReferralData)
                             (implicit identity: IdentityRequest): Future[MemberError \/ ContactId]

  def downgradeSubscription(subscriber: PaidMember): Future[MemberError \/ Unit]

  def upgradePaidSubscription(sub: PaidMember, newTier: PaidTier, form: PaidMemberChangeForm, referralData: ReferralData)
                             (implicit id: IdentityRequest): Future[MemberError \/ ContactId]

  def cancelSubscription(subscriber: Member): Future[MemberError \/ Unit]

  def upgradeTierIsValidForCurrency(subscription: Subscription[SubscriptionPlan.Member], newTier: PaidTier): Boolean

  def upgradeTierIsHigher(subscription: Subscription[SubscriptionPlan.Member], newTier: PaidTier): Boolean

  def getMembershipSubscriptionSummary(contact: GenericSFContact): Future[ThankyouSummary]

  /*
   * If the member is entitled to complimentary tickets return its Zuora account's corresponding usage records count.
   * Returns none otherwise
   */
  def getUsageCountWithinTerm(subscription: Subscription[SubscriptionPlan.Member], unitOfMeasure: String): Future[Option[Int]]

  def recordFreeEventUsage(subs: Subscription[SubscriptionPlan.Member],
                           event: RichEvent,
                           order: EBOrder,
                           quantity: Int): Future[CreateResult]

  def retrieveComplimentaryTickets(subscription: Subscription[SubscriptionPlan.Member], event: RichEvent): Future[Seq[EBTicketClass]]

  def createEBCode(subscriber: Member, event: RichEvent): Future[Option[EBCode]]

  def createPaidSubscription(contactId: ContactId,
                             identityId: String,
                             joinData: PaidMemberForm,
                             nameData: NameForm,
                             tier: PaidTier,
                             email: String,
                             paymentMethod: PaymentMethod,
                             ipCountry: Option[Country]): Future[SubscribeResult]

  def createFreeSubscription(contactId: ContactId,
                             identityId: String,
                             joinData: JoinForm,
                             email: String,
                             ipCountry: Option[Country]): Future[SubscribeResult]

  def createContribution(contactId: ContactId,
                         identityId: String,
                         joinData: ContributorForm,
                         nameData: NameForm,
                         email: String,
                         paymentMethod: PaymentMethod): Future[SubscribeResult]
}

object MemberService {
  sealed trait MemberError extends Throwable

  case class MemberPromoError(get: PromoError) extends MemberError {
    override def getMessage = s"Promo error: ${get.msg}"
  }

  case class PaidSubscriptionExpected(name: S.Name) extends MemberError {
    override def getMessage = s"Paid subscription expected. Got a free one instead: ${name.get} "
  }
  case class PendingAmendError(name: S.Name) extends MemberError {
    override def getMessage = s"Subscription ${name.get} already has a pending amend"
  }
  case class NoCardError(name: S.Name) extends MemberError {
    override def getMessage = s"Subscription ${name.get} has no card"
  }
}
