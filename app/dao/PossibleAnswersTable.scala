package dao

import javax.inject.{Inject, Singleton}
import models.PossibleAnswer
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

trait PossibleAnswerComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class PossibleAnswersTable(tag: Tag) extends Table[PossibleAnswer](tag, "possible_answers") {
    def id = column[Long]("id_possible_answer", O.PrimaryKey, O.AutoInc) // Primary key, auto-incremented
    def value = column[String]("value")

    // Map the attributes with the model; the ID is optional.
    def * = (id.?, value) <> (PossibleAnswer.tupled, PossibleAnswer.unapply)
  }

}

@Singleton
class PossibleAnswersDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] with PossibleAnswerComponent with QuestionsComponent {
  import profile.api._

  val users = TableQuery[PossibleAnswersTable]
  val quizzes = TableQuery[QuestionsTable]
}