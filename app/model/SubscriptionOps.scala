package model

import com.gu.memsub.{OneOffPeriod, Product, Weekly}
import com.gu.memsub.subsv2.{PaidCharge, PaidSubscriptionPlan, Subscription}
import com.gu.memsub.subsv2.SubscriptionPlan.{Paid, PaperPlan, WeeklyPlan}
import com.github.nscala_time.time.OrderingImplicits._
import com.typesafe.scalalogging.LazyLogging
import controllers.ContextLogging
import org.joda.time.LocalDate.now
import model.BillingPeriodOps._
object SubscriptionOps extends LazyLogging {

  type WeeklyPlanOneOff = PaidSubscriptionPlan[Product.Weekly, PaidCharge[Weekly.type, OneOffPeriod]]

  implicit class EnrichedPaidSubscription[P <: Paid](subscription: Subscription[P]) {
    val currency = subscription.plans.head.charges.currencies.head
    val nextPlan = subscription.plans.list.maxBy(_.end)
    val planToManage = {
      lazy val coveringPlans = subscription.plans.list.filter(p => (p.start.isEqual(now) || p.start.isBefore(now)) && p.end.isAfter(now))
      lazy val expiredPlans = subscription.plans.list.filter(p => p.end.isBefore(now) || p.end.isEqual(now))

      // The user's sub might be in many situations:

      if (coveringPlans.nonEmpty) {
        // Has a plan which is currently active (i.e today sits within the plan's start and end dates)
        coveringPlans.maxBy(_.end)
      } else if (expiredPlans.nonEmpty) {
        // Has a plan which has recently expired
        expiredPlans.maxBy(_.end)
      } else {
        // Has a plan(s) starting soon
        if (subscription.plans.size > 1) {
          logger.warn("User has multiple future plans, we are showing them their last ending plan")
        }
        nextPlan
      }
    }
  }

  implicit class EnrichedPaperSubscription[P <: PaperPlan](subscription: Subscription[P]) extends ContextLogging {
    implicit val subContext = subscription
    val renewable = {
      val wontAutoRenew = !subscription.autoRenew
      val startedAlready = !subscription.termStartDate.isAfter(now)
      val isOneOffPlan = !subscription.planToManage.charges.billingPeriod.isRecurring
      info(s"testing if renewable - wontAutoRenew: $wontAutoRenew, startedAlready: $startedAlready, isOneOffPlan: $isOneOffPlan")
      wontAutoRenew && startedAlready && isOneOffPlan
    }
  }

  implicit class EnrichedWeeklySubscription[P <: WeeklyPlan](subscription: Subscription[P]) {
    val asRenewable = if (subscription.renewable) Some(subscription.asInstanceOf[Subscription[WeeklyPlanOneOff]]) else None
  }
}