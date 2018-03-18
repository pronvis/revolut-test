package com.pronvis.revolut.test

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.pronvis.revolut.test.controllers.AccountsController
import com.pronvis.revolut.test.controllers.requests.CreateAccountRequest
import com.pronvis.revolut.test.exceptions.BusinessException
import com.pronvis.revolut.test.model.{Account, AccountsMiddleware, AccountsModel, TransactionsModel}
import com.pronvis.revolut.test.utils.{ErrorHelper, ErrorMessage}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.mockito.Mockito
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mockito.MockitoSugar
import io.circe.generic.auto._
import org.mockito.invocation.InvocationOnMock

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

class AccountsControllerSpec extends FunSuite with Matchers with ScalatestRouteTest with FailFastCirceSupport with MockitoSugar with ErrorHelper {

  private val queryTimeout = 1 second
  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(2 seconds)
  implicit val actorSystem: ActorSystem = system
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  def route(implicit accountsModel: AccountsModel, transactionsModel: TransactionsModel): Route = {
    val accountsMiddleware = new AccountsMiddleware(accountsModel, transactionsModel)
    new AccountsController(accountsMiddleware, queryTimeout).route
  }

  // ========================== TEST DATA ======================================

  val acccounts = Seq(
    Account(1, "Vasia", 123),
    Account(2, "Petia", 456),
    Account(3, "Masha", 789),
    Account(4, "Praskovia", 555),
    Account(5, "Marfa", 666)
  )

  // ================================== GET ACCOUNTS =========================================
  //no tests for timeout because of https://github.com/akka/akka-http/issues/952

  test("find accounts should return all accounts") {

    //given
    implicit val accountsModel: AccountsModel = mock[AccountsModel]
    implicit val transactionsModel: TransactionsModel = mock[TransactionsModel]
    Mockito.when(accountsModel.all).thenReturn(Future.successful(acccounts))

    //when-then
    Get("/accounts") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      contentType shouldEqual ContentTypes.`application/json`
      headers shouldEqual Seq()
      responseAs[Seq[Account]] should contain theSameElementsAs acccounts
    }
  }

  test("find accounts should return InternalServerError if error") {

    //given
    implicit val accountsModel: AccountsModel = mock[AccountsModel]
    implicit val transactionsModel: TransactionsModel = mock[TransactionsModel]
    Mockito.when(accountsModel.all)
      .thenAnswer((_: InvocationOnMock) => {
        Future.failed(new RuntimeException("some problem"))
      })

    //when-then
    Get(s"/accounts") ~> route ~> check {
      status shouldEqual StatusCodes.InternalServerError
      contentType shouldEqual ContentTypes.`application/json`
      headers shouldEqual Seq()
      response.status should be(StatusCodes.InternalServerError)
      responseAs[ErrorMessage].message shouldBe "Internal error"
    }
  }

  // ================================== ADD ACCOUNT =========================================

  test("add account should return account id") {

    val createAccountRequest = CreateAccountRequest("new one", 872)
    //given
    implicit val accountsModel: AccountsModel = mock[AccountsModel]
    implicit val transactionsModel: TransactionsModel = mock[TransactionsModel]
    Mockito.when(accountsModel.find(createAccountRequest.name)).thenReturn(Future.successful(None))
    Mockito.when(accountsModel.addAccount(createAccountRequest.name, createAccountRequest.balance)).thenReturn(Future.successful(1l))

    //when-then
    Post("/accounts", createAccountRequest) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      contentType shouldEqual ContentTypes.`application/json`
      headers shouldEqual Seq()
      responseAs[Long] shouldBe 1
    }
  }

  test("add account should return understandable message if account already exists") {

    val businessExceptionMsg = "some understandable message"
    //given
    implicit val accountsModel: AccountsModel = mock[AccountsModel]
    implicit val transactionsModel: TransactionsModel = mock[TransactionsModel]
    Mockito.when(accountsModel.find(org.mockito.Matchers.anyString())).thenReturn(Future.successful(None))
    Mockito.when(accountsModel.addAccount(
      org.mockito.Matchers.anyString(),
      org.mockito.Matchers.any[BigDecimal]
    ))
      .thenAnswer((_: InvocationOnMock) => {
        Future.failed(new BusinessException(businessExceptionMsg))
      })

    //when-then
    Post(s"/accounts", CreateAccountRequest("new one", 872)) ~> route ~> check {
      status shouldEqual StatusCodes.InternalServerError
      contentType shouldEqual ContentTypes.`application/json`
      headers shouldEqual Seq()
      response.status should be(StatusCodes.InternalServerError)
      responseAs[ErrorMessage].message shouldBe businessExceptionMsg
    }
  }

  test("add account should return InternalServerError if error") {

    //given
    implicit val accountsModel: AccountsModel = mock[AccountsModel]
    implicit val transactionsModel: TransactionsModel = mock[TransactionsModel]
    Mockito.when(accountsModel.addAccount(
      org.mockito.Matchers.anyString(),
      org.mockito.Matchers.any[BigDecimal]
    ))
      .thenAnswer((_: InvocationOnMock) => {
        Future.failed(new RuntimeException("some problem"))
      })

    //when-then
    Post(s"/accounts", CreateAccountRequest("new one", 872)) ~> route ~> check {
      status shouldEqual StatusCodes.InternalServerError
      contentType shouldEqual ContentTypes.`application/json`
      headers shouldEqual Seq()
      response.status should be(StatusCodes.InternalServerError)
      responseAs[ErrorMessage].message shouldBe "Internal error"
    }
  }

  // ================================== TRANSFER MONEY =========================================

//  test("transfer money, success scenario") {
//    //given
//    implicit val accountsModel: AccountsModel = mock[AccountsModel]
//    implicit val transactionsModel: TransactionsModel = mock[TransactionsModel]
//    Mockito.when(accountsMiddleware.addAccount(org.mockito.Matchers.any[CreateAccountRequest]))
//      .thenAnswer((_: InvocationOnMock) => {
//        Future.failed(new RuntimeException("some problem"))
//      })
//
//    //when-then
//    Post(s"/accounts", CreateAccountRequest("new one", 872)) ~> route ~> check {
//      status shouldEqual StatusCodes.InternalServerError
//      contentType shouldEqual ContentTypes.`application/json`
//      headers shouldEqual Seq()
//      response.status should be(StatusCodes.InternalServerError)
//      responseAs[ErrorMessage].message shouldBe "Internal error"
//    }
//  }
}