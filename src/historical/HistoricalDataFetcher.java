package historical;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public class HistoricalDataFetcher {

    private static final String API_BASE_URL = "https://api.kite.trade";
    private static final String API_KEY = "kbn0ca43nbzamzga"; // Replace with your actual API key
    private String accessToken;

    // Load access token from JSON file
    public void loadAccessToken() throws IOException {
        String jsonContent = Files.readString(Paths.get("login/access_token.json"));
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(jsonContent, JsonObject.class);
        this.accessToken = jsonObject.get("access_token").getAsString();
    }

    // Fetch historical data for an instrument
    public void fetchHistoricalData(String instrumentToken, String interval,
                                   LocalDateTime fromDate, LocalDateTime toDate,
                                   boolean includeContinuous, boolean includeOI) throws IOException, InterruptedException {

        // Load access token
        loadAccessToken();

        // Format dates
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String fromDateStr = fromDate.format(formatter);
        String toDateStr = toDate.format(formatter);

        // Build URL
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(API_BASE_URL)
                  .append("/instruments/historical/")
                  .append(instrumentToken)
                  .append("/")
                  .append(interval)
                  .append("?from=").append(fromDateStr.replace(" ", "+"))
                  .append("&to=").append(toDateStr.replace(" ", "+"));

        if (includeContinuous) {
            urlBuilder.append("&continuous=1");
        }

        if (includeOI) {
            urlBuilder.append("&oi=1");
        }

        // Create HTTP client and request
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlBuilder.toString()))
                .header("X-Kite-Version", "3")
                .header("Authorization", "token " + API_KEY + ":" + accessToken)
                .GET()
                .build();

        // Send request and get response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Parse and display response
        if (response.statusCode() == 200) {
            Gson gson = new Gson();
            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);

            if (responseJson.get("status").getAsString().equals("success")) {
                JsonObject data = responseJson.getAsJsonObject("data");
                JsonArray candles = data.getAsJsonArray("candles");

                System.out.println("Historical Data for Instrument Token: " + instrumentToken);
                System.out.println("Interval: " + interval);
                System.out.println("From: " + fromDateStr + " To: " + toDateStr);
                System.out.println("Total Records: " + candles.size());
                System.out.println();

                // Print headers
                if (includeOI) {
                    System.out.println("Timestamp\t\tOpen\tHigh\tLow\tClose\tVolume\tOI");
                } else {
                    System.out.println("Timestamp\t\tOpen\tHigh\tLow\tClose\tVolume");
                }
                System.out.println("**********************************************************************************************************");

                // Print candle data
                for (int i = 0; i < candles.size(); i++) {
                    JsonArray candle = candles.get(i).getAsJsonArray();

                    String timestamp = candle.get(0).getAsString();
                    double open = candle.get(1).getAsDouble();

                    double high = candle.get(2).getAsDouble();
                    double low = candle.get(3).getAsDouble();
                    double close = candle.get(4).getAsDouble();
                    long volume = candle.get(5).getAsLong();

                    if (includeOI && candle.size() > 6) {
                        long oi = candle.get(6).getAsLong();
                        System.out.printf("%s\t%.2f\t%.2f\t%.2f\t%.2f\t%d\t%d%n",
                                        timestamp, open, high, low, close, volume, oi);
                    } else {
                        System.out.printf("%s\t%.2f\t%.2f\t%.2f\t%.2f\t%d%n",
                                        timestamp, open, high, low, close, volume);
                    }
                }
            } else {
                System.err.println("API Error: " + responseJson.get("message").getAsString());
            }
        } else {
            System.err.println("HTTP Error: " + response.statusCode());
            System.err.println("Response: " + response.body());
        }
    }

    // Main method with example usage
    public static void main(String[] args) {
        HistoricalDataFetcher fetcher = new HistoricalDataFetcher();

        try {
            // Example: Fetch minute data for a NIFTY futures contract
            String instrumentToken = "12517890"; // Replace with actual instrument token
            String interval = "5minute"; // minute, 3minute, 5minute, 10minute, 15minute, 30minute, 60minute, day

            LocalDateTime fromDate = LocalDateTime.of(2025, 8, 20, 9, 15, 0);
            LocalDateTime toDate = LocalDateTime.of(2025, 9, 11, 15, 30, 0);

            // Fetch data with OI (for futures/options)
            fetcher.fetchHistoricalData(instrumentToken, interval, fromDate, toDate, false,false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
