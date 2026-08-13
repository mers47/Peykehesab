package ir.peykhesab.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.peykhesab.app.domain.Customer
import ir.peykhesab.app.domain.Driver
import ir.peykhesab.app.domain.Neighborhood
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStartsAndCoreScreensRender() {
        composeRule.onNodeWithText("پیک‌حساب").assertIsDisplayed()

        composeRule.onNodeWithTag("nav-orders").performClick()
        composeRule.onAllNodesWithText("سفارش‌ها")[0].assertIsDisplayed()

        composeRule.onNodeWithTag("nav-drivers").performClick()
        composeRule.onAllNodesWithText("راننده‌ها")[0].assertIsDisplayed()

        composeRule.onNodeWithTag("nav-customers").performClick()
        composeRule.onAllNodesWithText("مشتریان")[0].assertIsDisplayed()

        composeRule.onNodeWithTag("nav-reports").performClick()
        composeRule.onAllNodesWithText("گزارش‌ها")[0].assertIsDisplayed()
        composeRule.onNodeWithText("امروز").assertIsDisplayed()

        composeRule.onNodeWithTag("nav-dashboard").performClick()
        composeRule.onNodeWithText("ثبت سفارش جدید").assertIsDisplayed()
        composeRule.onNodeWithText("پشتیبان‌گیری و بازیابی").performClick()
        composeRule.onNodeWithText("حفاظت از تمام اطلاعات").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("بازگشت").performClick()
        composeRule.onNodeWithText("ثبت سفارش جدید").assertIsDisplayed()
    }
    @Test
    fun newOrderDraftSurvivesActivityRecreation() {
        val app = composeRule.activity.application as PeykHesabApplication
        val suffix = UUID.randomUUID().toString().take(8)
        val neighborhood = Neighborhood(name = "محله چرخش $suffix")
        val customer = Customer(name = "مشتری چرخش $suffix", neighborhoodId = neighborhood.id)
        val driver = Driver(name = "راننده چرخش $suffix")
        runBlocking {
            app.repository.saveNeighborhood(neighborhood)
            app.repository.saveCustomer(customer)
            app.repository.saveDriver(driver)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ثبت سفارش جدید").performClick()

        composeRule.onNodeWithTag("customer-picker").performClick()
        composeRule.onNodeWithText(customer.name).performClick()
        composeRule.onNodeWithTag("driver-picker").performClick()
        composeRule.onNodeWithText(driver.name).performClick()
        composeRule.onNodeWithTag("order-amount").performTextInput("۱۲۳۴۵")
        composeRule.onNodeWithTag("order-notes").performTextInput("یادداشت چرخش")

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("customer-picker").assertTextContains(customer.name)
        composeRule.onNodeWithTag("driver-picker").assertTextContains(driver.name)
        composeRule.onNodeWithTag("neighborhood-picker").assertTextContains(neighborhood.name)
        composeRule.onNodeWithTag("order-amount").assertTextContains("۱۲۳۴۵")
        composeRule.onNodeWithTag("order-notes").assertTextContains("یادداشت چرخش")
        composeRule.onNodeWithTag("order-submit").performScrollTo().assertIsDisplayed()
    }

}
