package login;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Profile;
import com.zerodhatech.models.User;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

/**
 * Handles Zerodha login automation and access token retrieval.
 */
public class ZerodhaAutoLogin {
    /**
     * Performs Zerodha login using manual OTP and returns KiteConnect instance.
     * @param apiKey Zerodha API key
     * @param apiSecret Zerodha API secret
     * @param userId Zerodha user ID
     * @param userPassword Zerodha password
     * @param headless Whether to run browser in headless mode
     * @return KiteConnect instance
     * @throws Exception on login failure
     */
    public static KiteConnect zerodhaAutoLoginManualOtp(String apiKey, String apiSecret,
                                                        String userId, String userPassword,
                                                        boolean headless) throws Exception {
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless");
        }

        WebDriver driver = new ChromeDriver(options);
        String kiteLoginUrl = String.format("https://kite.zerodha.com/connect/login?v=3&api_key=%s", apiKey);
        driver.get(kiteLoginUrl);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        try {
            // Enter user id and password
            WebElement useridInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("userid")));
            useridInput.clear();
            useridInput.sendKeys(userId);

            WebElement passwordInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("password")));
            passwordInput.clear();
            passwordInput.sendKeys(userPassword);

            driver.findElement(By.xpath("//button[@type='submit']")).click();

            // Wait for TOTP input stage
            WebElement totpInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("userid")));
            totpInput.clear();

            // Prompt user to enter OTP manually in terminal
            Console console = System.console();
            String otpInput;
            if (console != null) {
                otpInput = console.readLine("Please enter your TOTP from your authenticator app: ");
            } else {
                Scanner scanner = new Scanner(System.in);
                System.out.print("Please enter your TOTP from your authenticator app: ");
                otpInput = scanner.nextLine();
            }

            totpInput.sendKeys(otpInput);
            driver.findElement(By.xpath("//button[@type='submit']")).click();

            // Wait for redirect after login
            Thread.sleep(5000);
            String redirectUrl = driver.getCurrentUrl();

            // Extract request_token from redirect URL
            String requestToken = extractRequestToken(redirectUrl);

            if (requestToken == null) {
                throw new Exception("Request token not found in redirect URL: " + redirectUrl);
            }

            // This matches the Python logic exactly
            KiteConnect kite = new KiteConnect(apiKey);
            User sessionData = kite.generateSession(requestToken, apiSecret);
            String accessToken = sessionData.accessToken;

            // THIS IS THE MISSING LINE - Set the access token!
            kite.setAccessToken(accessToken);

            // Save access_token for later use in the same day/session
            saveAccessToken(accessToken);

            System.out.println("Logged in and session created successfully.");
            return kite;

        } catch (KiteException e) {
            throw new RuntimeException(e);
        } finally {
            driver.quit();
        }
    }

    private static String extractRequestToken(String url) throws Exception {
        URL urlObj = new URL(url);
        String query = urlObj.getQuery();

        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2 && "request_token".equals(keyValue[0])) {
                    return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private static void saveAccessToken(String accessToken) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> tokenData = new HashMap<>();
        tokenData.put("access_token", accessToken);

        // Create directory if it doesn't exist
        Files.createDirectories(Paths.get("login"));

        mapper.writeValue(new File("login/access_token.json"), tokenData);
    }

    private static String readPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] password = console.readPassword(prompt);
            return new String(password);
        } else {
            Scanner scanner = new Scanner(System.in);
            System.out.print(prompt);
            return scanner.nextLine();
        }
    }

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter your Kite API Key: ");
            String apiKey = scanner.nextLine().trim();

            String apiSecret = readPassword("Enter your Kite API Secret: ");

            System.out.print("Enter your Kite User ID: ");
            String userId = scanner.nextLine().trim();

            String userPassword = readPassword("Enter your Kite Password: ");

            KiteConnect kiteClient = zerodhaAutoLoginManualOtp(apiKey, apiSecret, userId, userPassword, true);

            Profile profile = kiteClient.getProfile();

            System.out.println("User Type: " + profile.userType);
            System.out.println("Email: " + profile.email);
            System.out.println("User Name: " + profile.userName);
            System.out.println("User Shortname: " + profile.userShortname);
            System.out.println("Broker: " + profile.broker);
            System.out.println("Exchanges: " + Arrays.toString(profile.exchanges));
            System.out.println("Products: " + Arrays.toString(profile.products));
            System.out.println("Order Types: " + Arrays.toString(profile.orderTypes));
            System.out.println("Avatar URL: " + profile.avatarURL);

        } catch (Exception | KiteException e) {
            e.printStackTrace();
        }
    }
}
