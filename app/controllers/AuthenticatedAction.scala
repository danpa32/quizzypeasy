package controllers

import dao.UsersDAO
import javax.inject.Inject
import models.User
import play.api.Logger
import play.api.libs.typedmap.TypedKey
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class AuthenticatedRequest[A](val userInfo: Option[(Long, String, Boolean)], request: Request[A]) extends WrappedRequest[A](request)

class AuthenticatedAction @Inject()(val parser: BodyParsers.Default, usersDAO: UsersDAO)(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[AuthenticatedRequest, AnyContent] with ActionTransformer[Request, AuthenticatedRequest] {


  def transform[A](request: Request[A]): Future[AuthenticatedRequest[A]] = {
    request.session.get("connected") match {
      case None => {
        Logger.info("Hello transform none")
        Future.successful(new AuthenticatedRequest(None, request))
      }
      case Some(u) => {
        Logger.info("Hello transform some")
        val user = for {
          u <- usersDAO.findByName(u)
          if u.isDefined
        } yield u
        user.map[AuthenticatedRequest[A]](_ match {
          case Some(User(Some(id), name, _, _, isAdmin)) => {
            val userInfo = (id, name, isAdmin)
            new AuthenticatedRequest(Some(userInfo), request)
          }
          case None => new AuthenticatedRequest(None, request)
        })
      }
    }
  }

  def PermissionCheckAction(implicit ec: ExecutionContext) = new ActionFilter[AuthenticatedRequest] {
    def executionContext = ec

    def filter[A](input: AuthenticatedRequest[A]) = Future.successful {
      if (input.userInfo.isEmpty)
        Some(Forbidden("hello world"))
      else
        None
    }
  }
}

