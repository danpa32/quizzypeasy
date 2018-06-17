package controllers

import java.time.LocalDateTime

import dao.{AnswersDAO, QuestionsDAO, QuizzesDAO, UsersDAO}
import javax.inject._
import models._
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.I18nSupport
import play.api.libs.typedmap.TypedKey
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.matching.Regex


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's login page.
 */
@Singleton
class QuizController @Inject()(cc: ControllerComponents, authenticatedAction: AuthenticatedAction, usersDAO: UsersDAO, quizzesDAO: QuizzesDAO, questionsDAO: QuestionsDAO, answersDAO: AnswersDAO) extends AbstractController(cc) with I18nSupport {
  val quizAnswerForm = Form(
    mapping(
      "id" -> longNumber,
      "answer" -> text,
    )(QuizAnswerData.apply)(QuizAnswerData.unapply)
  )

  def quizQuestion(id: Long, q: Long) = authenticatedAction.andThen(authenticatedAction.PermissionCheckAction).async { implicit request =>
    if(request.userInfo.get._4.contains(id))
      for {
        curQuestionOpt <- answersDAO.getQuestionAndAnswer(id, q)
        possibleAnswers <- if (curQuestionOpt.isDefined) questionsDAO.getPossibleAnswers(curQuestionOpt.get._3.id.get) else Future.successful(Seq.empty)
        allAnswers <- answersDAO.getQuizAnswers(id)
      } yield {
        curQuestionOpt match {
          case Some((cat, quiz, quest, ans)) =>
            val answerForm = quizAnswerForm.fill(QuizAnswerData(ans.id.get, ans.userAnswer))
            Ok(views.html.quiz(answerForm, FullQuizzQuestion(cat, quiz, quest, ans, possibleAnswers.map(t => (t._1, t._2.correctAnswer))), allAnswers))
          case None => BadRequest("There is no question matching the ids")
        }
      }
    else
      Future{Redirect(routes.QuizController.listQuizzes()).flashing("info" -> "The quiz you tried to acces is not yours.")}
  }

  def skipToQuizQuestion(id: Long, q: Long) = authenticatedAction.andThen(authenticatedAction.PermissionCheckAction).async { implicit request =>
    if(request.userInfo.get._4.contains(id)) {
      val redirect = Redirect(routes.QuizController.quizQuestion(id, q))
      quizAnswerForm.bindFromRequest.fold(
        formWithErrors => {
          Future {
            redirect.flashing("info" -> "The answer to the last question has not been saved because there was an error")
          }
        },
        aData => {
          answersDAO.getQuizAnswer(id, aData.id).flatMap{
            case Some(a) =>
              if(a.isFinal){
                Future {
                  redirect.flashing("info" -> "The answer to the last question can't be modified")
                }
              }
              else{
                answersDAO.update(Answer(a.id, aData.answer, false, a.questionId, a.quizId)).map{
                  case 0 => redirect.flashing("info" -> "Could not update last question")
                  case _ => redirect
                }
              }
            case None => Future{
              redirect.flashing("info" -> "The answer to the last question does not match the quizz")
            }
          }
        }
      )
    }
    else
      Future{Redirect(routes.QuizController.listQuizzes()).flashing("info" -> "The quiz you tried to acces is not yours.")}

  }

  def submitQuizQuestion(id: Long, q: Long) = authenticatedAction.andThen(authenticatedAction.PermissionCheckAction).async { implicit request =>
    if(request.userInfo.get._4.contains(id)) {
      val redirect = Redirect(routes.QuizController.quizQuestion(id, q))
      quizAnswerForm.bindFromRequest.fold(
        formWithErrors => {
          Future {
            redirect.flashing("info" -> "The answer to the question has not been saved because there was an error")
          }
        },
        aData => {
          answersDAO.getQuizAnswer(id, aData.id).flatMap{
            case Some(a) =>
              if(a.isFinal){
                Future {
                  redirect.flashing("info" -> "The answer to the last question can't be modified")
                }
              }
              else{
                answersDAO.update(Answer(a.id, aData.answer, true, a.questionId, a.quizId)).map{
                  case 0 => redirect.flashing("info" -> "Could not update the question")
                  case _ => redirect
                }
              }
            case None => Future{
              redirect.flashing("info" -> "The answer to the question does not match the quizz")
            }
          }
        }
      )
    }
    else
      Future{Redirect(routes.QuizController.listQuizzes()).flashing("info" -> "The quiz you tried to acces is not yours.")}
  }

  def quizReview(id: Long) = authenticatedAction.andThen(authenticatedAction.PermissionCheckAction).async { implicit request =>
    if(request.userInfo.get._4.contains(id))
      for {
        qs <- answersDAO.getQuestionsAndAnswers(id)
        as <- questionsDAO.getQuizPossibleAnswers(id)
      } yield {
        val posAnsMap = as.groupBy(_._2.questionId)
        Ok(views.html.review(qs.map({
          case (cat, quiz, quest, ans) => FullQuizzQuestion(cat, quiz, quest, ans, posAnsMap(quest.id.get).map(t => (t._1, t._2.correctAnswer)))
        })))
      }
    else
      Future{Redirect(routes.QuizController.listQuizzes()).flashing("info" -> "The quiz you tried to acces is not yours.")}
  }

  def quizScore(id: Long) = authenticatedAction.andThen(authenticatedAction.PermissionCheckAction).async { implicit request =>
    def calculateScore(qs: Seq[(Category, Quiz, Question, Answer)], as: Seq[(PossibleAnswer, AnswersQuestion)]) : Int = {
      val posAnsMap = as.groupBy(_._2.questionId)
      val allQuestions = qs.map({
        case (cat, quiz, quest, ans) => FullQuizzQuestion(cat, quiz, quest, ans, posAnsMap(quest.id.get).map(t => (t._1, t._2.correctAnswer)))
      })
      allQuestions.count(_.isCorrect)
    }
    if(request.userInfo.get._4.contains(id))
      for {
        qs <- answersDAO.getQuestionsAndAnswers(id)
        as <- questionsDAO.getQuizPossibleAnswers(id)
        qz <- quizzesDAO.update(Quiz(qs(0)._2.id, calculateScore(qs, as), qs(0)._2.categoryId, qs(0)._2.userId))
        qa <- answersDAO.lockAll(id)
      } yield Redirect(routes.QuizController.quizReview(id))
    else
      Future{Redirect(routes.QuizController.listQuizzes()).flashing("info" -> "The quiz you tried to acces is not yours.")}

  }

  def listQuizzes() = authenticatedAction.andThen(authenticatedAction.PermissionCheckAction).async { implicit request =>
    for {
      s <- quizzesDAO.listFromUser(request.userInfo.get._1)
    } yield {
      Ok(views.html.userquizzes(s))
    }
  }
/*
  def quizAnswer(id: Long, q: Long) = Action.async { implicit request =>
    quizAnswerForm.bindFromRequest.fold(
      formWithErrors => {
        Future {
          BadRequest(views.html.signup(formWithErrors))
        }
      },
      uData => {
        val optU = usersDAO.insert(models.User(None, uData.username, uData.password, LocalDateTime.now(), false))
        optU map {
          case u => Redirect(routes.HomeController.index()).withSession("connected" -> u.name)
          case _ => BadRequest(views.html.signup(signUpForm.fill(uData)))
        }
      }
    )
  }*/

  def create(categoryId: Long) = authenticatedAction.andThen(authenticatedAction.PermissionCheckAction).async { implicit request =>
    val answerFor = for {
      quiz <- quizzesDAO.insert(Quiz(None, -1, categoryId, request.userInfo.get._1))
      questions <- questionsDAO.getQuestions(categoryId)
      a <- answersDAO.insertAll(for (q <- questions) yield Answer(None, "", false, q.id.get, quiz.id.get))
    } yield a

    answerFor map {
      as => Redirect(routes.QuizController.quizQuestion(as.head.quizId, as.head.id.get))
    }
  }
}
