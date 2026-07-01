package de.tfr.tool.timetrack

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor.stringFlavor
import java.io.File
import java.lang.Thread.sleep
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.io.println

val simulate  = true
val startTime = "9:15"
val endTime   = "18:00"

fun main() {
    // 1. Connect to the already running Chrome instance (Debug Mode)
    val options = ChromeOptions()
    options.setExperimentalOption("debuggerAddress", "127.0.0.1:9222")
    
    
    try {
        // 2. Read the CSV file
        // Assuming arbeitstage.csv is in the project root and contains one date per line
        val csvFile = File("arbeitstage.csv")
        
        val workDays = mutableListOf<String>()

        if (csvFile.exists()) {
            workDays += csvFile.readLines().filter { it.isDate() }
            println("Found ${workDays.size} dates in CSV file")
        }else
        {
            println("arbeitstage.csv not found!")
            println("Trying to read CSV content from clipboard...")
            val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val clipboardContent  : String = clipboard.getData(stringFlavor) as String
            workDays += clipboardContent.lines().filter { it.isDate() }
            println("Found ${workDays.size} dates in clipboard content")
        }

        // 3. Iterate over each date from the CSV
        if(simulate){
            println("Simulating automation...")
            for (day in workDays) {
                println("Processing date: $day")
                sleep(500)
            }
        }else{
            val driver: WebDriver = ChromeDriver(options)
            val wait = WebDriverWait(driver, Duration.ofSeconds(10))
            for (day in workDays) {
                println("Processing date: $day")
                trackDay(day, driver, wait)
            }
        }
    } catch (e: Exception) {
        println("An error occurred during automation: ${e.message}")
        e.printStackTrace()
    } finally {
        // We do not call driver.quit() here, because we want to keep the 
        // Chrome profile open for future manual use or next script runs.
        println("Automation finished.")
    }
}

private fun trackDay(
    day: String,
    driver: WebDriver,
    wait: WebDriverWait
) {
    // Open the specific form
    driver.get("https://employees.exact.com/docs/WflRequest.aspx?BCAction=0&Type=304")

    // Wait for the StartTime element to be visible and interactable
    val startTimeInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("StartTime")))
    startTimeInput.clear()
    startTimeInput.sendKeys(startTime)

    val endTimeInput = driver.findElement(By.id("EndTime"))
    endTimeInput.clear()
    endTimeInput.sendKeys(endTime)

    val description = driver.findElement(By.id("Description"))

    fun WebElement.setDate(date: String) {
        this.clear()
        description.click()
        this.click()
        this.sendKeys(date)
        description.click()
    }

    val startDateInput = driver.findElement(By.id("StartDate"))
    startDateInput.setDate(day)

    val endDateInput = driver.findElement(By.id("EndDate"))
    endDateInput.setDate(day)

    // Click the save button
    val saveButton = driver.findElement(By.cssSelector(".exButtonSave"))
    saveButton.click()

    // Optional: Wait for a confirmation element or page reload to ensure saving is complete
    // wait.until(ExpectedConditions.stalenessOf(saveButton))
    println("Successfully saved record for: $day")
}

fun String.isDate() : Boolean {
    return try {
        if(this.isBlank()) return false
        LocalDate.parse(this, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        true
    } catch (_: DateTimeParseException) {
        false
    }
}