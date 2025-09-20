package trading.bot;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CPRTradingBot {
    
    private KiteConnect kiteConnect;
    private CPRCalculator cprCalculator;
    private PositionManager positionManager;
    private InstrumentManager instrumentManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // Configuration
    private static final String NIFTY_INSTRUMENT = "NSE:NIFTY 50";
    private static final double STOP_LOSS_PERCENTAGE = 0.25; // 25%
    private static final LocalTime LOGIN_TIME = LocalTime.of(10, 0); // 10:00 AM
    private static final LocalTime EXIT_TIME = LocalTime.of(15, 0);  // 3:00 PM
    
    // Trading state
    private boolean isLoggedIn = false;
    private boolean positionsOpened = false;
    private CPRLevels cprLevels;
    
    public CPRTradingBot() {
        this.cprCalculator = new CPRCalculator();
        this.positionManager = new PositionManager();
    }
    
    public void start() {
        System.out.println("CPR Trading Bot started at: " + LocalDateTime.now());
        
        // Schedule login at 10:00 AM
        scheduleLogin();
        
        // Schedule position monitoring every 5 minutes after login
        schedulePositionMonitoring();
        
        // Schedule exit at 3:00 PM
        scheduleExit();
        
        // Keep the bot running
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }
    
    private void scheduleLogin() {
        LocalTime now = LocalTime.now();
        long delayMinutes = 0;
        
        if (now.isBefore(LOGIN_TIME)) {
            delayMinutes = java.time.Duration.between(now, LOGIN_TIME).toMinutes();
        }
        
        scheduler.schedule(() -> {
            try {
                performLogin();
                calculateCPRLevels();
            } catch (Exception e) {
                System.err.println("Login failed: " + e.getMessage());
                e.printStackTrace();
            } catch (KiteException e) {
                throw new RuntimeException(e);
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }
    
    private void schedulePositionMonitoring() {
        // Start monitoring 5 minutes after login time
        LocalTime monitoringStart = LOGIN_TIME.plusMinutes(5);
        LocalTime now = LocalTime.now();
        long delayMinutes = 0;
        
        if (now.isBefore(monitoringStart)) {
            delayMinutes = java.time.Duration.between(now, monitoringStart).toMinutes();
        }
        
        scheduler.scheduleAtFixedRate(() -> {
            if (isLoggedIn && LocalTime.now().isBefore(EXIT_TIME)) {
                try {
                    monitorAndTrade();
                } catch (Exception e) {
                    System.err.println("Error in monitoring: " + e.getMessage());
                    e.printStackTrace();
                } catch (KiteException e) {
                    throw new RuntimeException(e);
                }
            }
        }, delayMinutes, 5, TimeUnit.MINUTES); // Every 5 minutes
    }
    
    private void scheduleExit() {
        LocalTime now = LocalTime.now();
        long delayMinutes = 0;
        
        if (now.isBefore(EXIT_TIME)) {
            delayMinutes = java.time.Duration.between(now, EXIT_TIME).toMinutes();
        }
        
        scheduler.schedule(() -> {
            try {
                exitAllPositions();
            } catch (Exception e) {
                System.err.println("Error during exit: " + e.getMessage());
                e.printStackTrace();
            } catch (KiteException e) {
                throw new RuntimeException(e);
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }
    
    private void performLogin() throws Exception {
        System.out.println("Performing login at: " + LocalDateTime.now());
        
        Scanner scanner = new Scanner(System.in);
        
        System.out.print("Enter your Kite API Key: ");
        String apiKey = scanner.nextLine().trim();
        
        System.out.print("Enter your Kite API Secret: ");
        String apiSecret = scanner.nextLine().trim();
        
        System.out.print("Enter your Kite User ID: ");
        String userId = scanner.nextLine().trim();
        
        System.out.print("Enter your Kite Password: ");
        String userPassword = scanner.nextLine().trim();
        
        // Use reflection to call ZerodhaAutoLogin from default package
        try {
            Class<?> loginClass = Class.forName("ZerodhaAutoLogin");
            java.lang.reflect.Method loginMethod = loginClass.getMethod("zerodhaAutoLoginManualOtp", 
                String.class, String.class, String.class, String.class, boolean.class);
            
            this.kiteConnect = (KiteConnect) loginMethod.invoke(null, 
                apiKey, apiSecret, userId, userPassword, true);
            
            // Initialize instrument manager after successful login
            this.instrumentManager = new InstrumentManager(kiteConnect);
            this.instrumentManager.loadInstruments();
            this.instrumentManager.printInstrumentSummary();
            
            this.isLoggedIn = true;
            System.out.println("Login successful!");
        } catch (Exception e) {
            throw new RuntimeException("Failed to login using ZerodhaAutoLogin", e);
        } catch (KiteException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void calculateCPRLevels() throws KiteException, IOException {
        System.out.println("Calculating CPR levels...");
        
        // Get previous day's OHLC data for Nifty
        Date yesterday = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        Date today = new Date();
        
        // Get historical data for previous day
        HistoricalData historicalData = kiteConnect.getHistoricalData(
            yesterday, today, "256265", "day", false, false
        );
        
        if (historicalData.dataArrayList != null && !historicalData.dataArrayList.isEmpty()) {
            HistoricalData lastDay = historicalData.dataArrayList.get(
                historicalData.dataArrayList.size() - 1
            );
            
            this.cprLevels = cprCalculator.calculateCPR(
                lastDay.high, lastDay.low, lastDay.close
            );
            
            System.out.println("CPR Levels calculated:");
            System.out.println("PDH: " + cprLevels.getPDH());
            System.out.println("PDL: " + cprLevels.getPDL());
            System.out.println("R1: " + cprLevels.getR1());
            System.out.println("S1: " + cprLevels.getS1());
            System.out.println("Pivot: " + cprLevels.getPivot());
        }
    }
    
    private void monitorAndTrade() throws KiteException, IOException {
        if (cprLevels == null) {
            System.out.println("CPR levels not calculated yet");
            return;
        }
        
        // Get current Nifty price from 5-minute candle
        double currentPrice = getCurrentNiftyPrice();
        System.out.println("Current Nifty Price: " + currentPrice + " at " + LocalDateTime.now());
        
        // Check trading conditions
        if (!positionsOpened) {
            checkAndExecuteTrades(currentPrice);
        } else {
            // Monitor existing positions for stop loss
            positionManager.monitorStopLoss(kiteConnect, STOP_LOSS_PERCENTAGE);
        }
    }
    
    private double getCurrentNiftyPrice() throws KiteException, IOException {
        // Get current market data for Nifty
        String[] instruments = {"256265"}; // Nifty 50 instrument token
        Map<String, Quote> quotes = kiteConnect.getQuote(instruments);
        
        if (quotes.containsKey("256265")) {
            return quotes.get("256265").lastPrice;
        }
        
        throw new RuntimeException("Unable to fetch Nifty price");
    }
    
    private void checkAndExecuteTrades(double currentPrice) throws KiteException, IOException {
        boolean shouldTrade = false;
        String tradeType = "";
        double strikeLevel = 0;
        
        // Check if price is within PDH and R1 range
        if (currentPrice >= cprLevels.getPDH() && currentPrice <= cprLevels.getR1()) {
            shouldTrade = true;
            tradeType = "SHORT_CE";
            strikeLevel = cprLevels.getPDH();
            System.out.println("Price within PDH-R1 range. Shorting CE at PDH level: " + strikeLevel);
        }
        // Check if price is within PDL and S1 range
        else if (currentPrice >= cprLevels.getS1() && currentPrice <= cprLevels.getPDL()) {
            shouldTrade = true;
            tradeType = "SHORT_PE";
            strikeLevel = cprLevels.getPDL();
            System.out.println("Price within S1-PDL range. Shorting PE at PDL level: " + strikeLevel);
        }
        
        if (shouldTrade) {
            executeOptionTrades(tradeType, strikeLevel);
            positionsOpened = true;
        }
    }
    
    private void executeOptionTrades(String tradeType, double strikeLevel) throws KiteException, IOException {
        // Find the closest strike to the target level
        int closestStrike = findClosestStrike(strikeLevel);
        
        // Get option instruments for the closest strike
        String ceInstrument = getOptionInstrument(closestStrike, "CE");
        String peInstrument = getOptionInstrument(closestStrike, "PE");
        
        if (tradeType.equals("SHORT_CE")) {
            // Short CE and PE
            positionManager.shortOption(kiteConnect, ceInstrument, "CE");
            positionManager.shortOption(kiteConnect, peInstrument, "PE");
        } else if (tradeType.equals("SHORT_PE")) {
            // Short CE and PE
            positionManager.shortOption(kiteConnect, ceInstrument, "CE");
            positionManager.shortOption(kiteConnect, peInstrument, "PE");
        }
        
        System.out.println("Executed trades for strike: " + closestStrike);
    }
    
    private int findClosestStrike(double price) {
        // Round to nearest 50 (Nifty strikes are in multiples of 50)
        return (int) (Math.round(price / 50.0) * 50);
    }
    
    private String getOptionInstrument(int strike, String optionType) {
        // This would need to be implemented based on Zerodha's instrument tokens
        // For now, returning a placeholder - you'll need to map strikes to instrument tokens
        return "NIFTY_" + strike + "_" + optionType;
    }
    
    private void exitAllPositions() throws KiteException, IOException {
        System.out.println("Exiting all positions at 3:00 PM");
        positionManager.exitAllPositions(kiteConnect);
        positionsOpened = false;
    }
    
    private void shutdown() {
        System.out.println("Shutting down CPR Trading Bot...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
    
    public static void main(String[] args) {
        CPRTradingBot bot = new CPRTradingBot();
        bot.start();
        
        // Keep main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Bot interrupted");
        }
    }
}
