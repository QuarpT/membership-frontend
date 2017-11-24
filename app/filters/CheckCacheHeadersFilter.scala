package filters

import javax.inject.Inject

import akka.stream.Materializer
import play.api.mvc._

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.Cached.suitableForCaching

class CheckCacheHeadersFilter @Inject()(implicit  val mat: Materializer) extends Filter {

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).map { result =>
      if (suitableForCaching(result)) {
        val hasCacheControl = result.header.headers.contains("Cache-Control")
        assert(hasCacheControl, s"Cache-Control not set. Ensure controller response has Cache-Control header set for ${requestHeader.path}. Throwing exception... ")
      }
      result
    }
  }
}
