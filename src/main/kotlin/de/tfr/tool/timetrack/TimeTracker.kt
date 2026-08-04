package de.tfr.tool.timetrack

import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
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

val simulate  = false
val create = true
val verify = true
val startTime = "9:15"
val endTime   = "18:00"
val person = "88888"

fun main() {
    // 1. Connect to the already running Chrome instance (Debug Mode)
    val options = ChromeOptions()
    options.setExperimentalOption("debuggerAddress", "127.0.0.1:9222")
    
    
    try {
        // 2. Read the CSV file
        // Assuming arbeitstage.csv is in the project root and contains one date per line
        val csvFile = File("arbeitstage.csv")
        
        val workDays = mutableListOf<String>()
        var readFromFile = false

        if (csvFile.exists()) {
            // Create a backup of the source file at start
            val backupFile = File(csvFile.absolutePath + ".back")
            csvFile.copyTo(backupFile, overwrite = true)
            println("Created backup: ${backupFile.name}")

            readFromFile = true
            workDays += csvFile.readLines()
                .filter { !it.trimStart().startsWith("#") }
                .filter { it.isDate() }
            println("Found ${workDays.size} dates in CSV file")
        }else
        {
            println("arbeitstage.csv not found!")
            println("Trying to read CSV content from clipboard...")
            val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val clipboardContent  : String = clipboard.getData(stringFlavor) as String
            workDays += clipboardContent.lines()
                .filter { !it.trimStart().startsWith("#") }
                .filter { it.isDate() }
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
                val success = trackDayWithRetry(day, driver, wait)
                if (success && readFromFile) {
                    commentOutDate(csvFile, day)
                }
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

private fun trackDayWithRetry(
    day: String,
    driver: WebDriver,
    wait: WebDriverWait,
    maxAttempts: Int = 3
): Boolean {
    for (attempt in 1..maxAttempts) {
        if (trackDay(day, driver, wait)) {
            return true
        }
        System.err.println("Attempt $attempt/$maxAttempts failed for $day")
        if (attempt < maxAttempts) {
            sleep(2000)
        }
    }
    System.err.println("ERROR: Could not set day $day after $maxAttempts attempts")
    return false
}

private fun commentOutDate(csvFile: File, day: String) {
    if (!csvFile.exists()) return
    val lines = csvFile.readLines().map { line ->
        if (!line.trimStart().startsWith("#") && line.trim() == day.trim()) "#$line" else line
    }
    csvFile.writeText(lines.joinToString(System.lineSeparator()))
    println("Commented out processed date in CSV: $day")
}

private fun trackDay(
    day: String,
    driver: WebDriver,
    wait: WebDriverWait
): Boolean {
    if(create){
        try {
            submitDay(driver, wait, day)
            sleep(3000)
        } catch (e: Exception) {
            System.err.println("ERROR: Failed to submit record for $day: ${e.message}")
            return false
        }
    }
    if(verify){
        return verify(driver, wait, day)
    }
    return true
}

private fun verify(
    driver: WebDriver,
    wait: WebDriverWait,
    day: String
): Boolean {
    try {
        driver.get("https://employees.exact.com/docs/WflRequests.aspx?Key=" + person)
        sleep(2000)

        // 1. Verify that the tracked day appears in the result table
        val tableRows = wait.until(
            ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("tr.DataLight, tr.DataDark"))
        )
        val matchingRow = tableRows.firstOrNull { row ->
            row.findElements(By.tagName("td")).getOrNull(4)?.text?.contains(day) == true
        } ?: throw RuntimeException("Day $day not found in result table after saving!")

        // 2. Check the checkbox for the row matching the tracked date
        val checkbox = matchingRow.findElement(By.cssSelector("input[name='List\$chkTick']"))
        if (!checkbox.isSelected) {
            checkbox.click()
        }
        sleep(1000)

        // 3. Process the checked entry — locate by visible label text, click directly via JS
        val processButton = wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.xpath("//button[.//span[text()='Bulk: Process']]")
            )
        )
        (driver as JavascriptExecutor).executeScript("arguments[0].click();", processButton)
        sleep(1000)
        // 4. Verify the success message "Successful. Request Processed"
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.xpath("//td[text()='Successful. Request Processed']")
            )
        )

        println("Successfully saved record for: $day")
        return true
    } catch (e: Exception) {
        System.err.println("ERROR: Failed to verify/approve record for $day: ${e.message}")
        return false
    }
}

private fun submitDay(
    driver: WebDriver,
    wait: WebDriverWait,
    day: String
) {
    // Open the specific form
    driver.get("https://employees.exact.com/docs/WflRequest.aspx?BCAction=0&Type=304")
    sleep(2000)
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
        sleep(500)
        description.click()
        this.click()
        sleep(500)
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