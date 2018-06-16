package controllers

import javax.inject._

import dao.CategoriesDAO
import play.api.i18n.I18nSupport
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents, categoriesDAO: CategoriesDAO) extends AbstractController(cc) with I18nSupport {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action.async { implicit request =>
      val categories = categoriesDAO.list()
      categories.map {
        c => Ok(views.html.index("Welcome to Quizzy Peasy", c))
      }
  }
}
