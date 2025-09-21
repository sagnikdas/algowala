package trading.bot;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import historical.HistoricalDataFetcher;
import login.ZerodhaAutoLogin;
import model.CandleData;

public class CPRTradingBot {
    private static final Logger logger = Logger.getLogger(CPRTradingBot.class.getName());
    
    // Core components
    private final HistoricalDataFetcher historicalFetcher;
    private final CPRCalculator cprCalculator;
    private final InstrumentManager instrumentManager;
    private final PositionManager positionManager;
    
    // Configuration
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, CPRLevels> dailyCPRCache = new ConcurrentHashMap<>();
    private volatile boolean isRunning = false;
    private volatile boolean isLoggedIn = false;
    
    // Trading parameters
    private static final double INITIAL_CAPITAL = 500000; // 5 lakhs
    private static final double MAX_DAILY_LOSS_PERCENT = 2.0; // 2% max daily loss
    private static final double RISK_PER_TRADE_PERCENT = 1.0; // 1% risk per trade
    
    public CPRTradingBot(HistoricalDataFetcher historicalFetcher) {
        this.historicalFetcher = historicalFetcher;
        this.cprCalculator = new CPRCalculator();
        this.instrumentManager = new InstrumentManager();
        
        // Initialize risk parameters
        PositionManager.RiskParameters riskParams = new PositionManager.RiskParameters(
            INITIAL_CAPITAL * (MAX_DAILY_LOSS_PERCENT / 100), // Max daily loss
            10.0, // Max position size as % of capital
            RISK_PER_TRADE_PERCENT, // Risk per trade
            5, // Max positions
            80.0 // Max portfolio exposure %
        );
        
        this.positionManager = new PositionManager(INITIAL_CAPITAL, riskParams);
        
        // Initialize common instruments
        instrumentManager.initializeCommonInstruments();
    }
    
    /**
     * Start the trading bot
     */
    public void startTrading() {
        logger.info("Starting CPR Trading Bot...");
        
        try {
            // Step 1: Login and authenticate
            if (!performDailyLogin()) {
                logger.severe("Failed to login. Cannot start trading.");
                return;
            }
            
            // Step 2: Subscribe to instruments
            subscribeToInstruments();
            
            // Step 3: Calculate initial CPR levels
            calculateDailyCPRLevels();
            
            // Step 4: Start trading loops
            startTradingLoops();
            
            isRunning = true;
            logger.info("CPR Trading Bot started successfully!");
            
        } catch (Exception e) {
            logger.severe("Error starting trading bot: " + e.getMessage());
            stopTrading();
        }
    }
    
    /**
     * Perform daily login and token generation
     */
    private boolean performDailyLogin() {
        try {
            logger.info("Performing Zerodha login...");

            String jsonContent = Files.readString(Paths.get("login/access_token.json"));
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonContent, JsonObject.class);
            String accessToken = jsonObject.get("access_token").getAsString();


            if (accessToken != null && !accessToken.isEmpty()) {
                isLoggedIn = true;
                logger.info("Login successful. Access token obtained.");

                // Reset daily counters
                positionManager.resetDailyCounters();
                return true;
            }


            logger.warning("Login failed or access token not available.");
            return false;

        } catch (Exception e) {
            logger.severe("Login error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Subscribe to required instruments for live data
     */
    private void subscribeToInstruments() {
        List<String> watchlist = instrumentManager.getCPRWatchlist();
        
        for (String instrumentToken : watchlist) {
            instrumentManager.subscribeToInstrument(instrumentToken);
        }
        
        logger.info("Subscribed to " + watchlist.size() + " instruments for live data.");
    }
    
    /**
     * Calculate CPR levels for all watchlist instruments
     */
    private void calculateDailyCPRLevels() {
        logger.info("Calculating daily CPR levels...");
        
        LocalDate today = LocalDate.now();
        LocalDate previousDay = today.minusDays(1);
        
        List<String> watchlist = instrumentManager.getCPRWatchlist();

        String interval = "day"; // minute, 3minute, 5minute, 10minute, 15minute, 30minute, 60minute, day

        LocalDateTime fromDate = LocalDateTime.of(2025, 9, 19, 9, 15, 0);
        LocalDateTime toDate = LocalDateTime.of(2025, 9, 19, 15, 30, 0);

        for (String instrumentToken : watchlist) {
            try {
                // Get historical data for previous day
                List<CandleData> ohlcData = historicalFetcher.fetchHistoricalData(instrumentToken, interval, fromDate, toDate, false,false);
                
                if (ohlcData != null) {
                    double high = ohlcData.get(0).getHigh();
                    double low = ohlcData.get(0).getLow();
                    double close = ohlcData.get(0).getClose();
                    
                    CPRLevels cprLevels = cprCalculator.calculateCPR(high, low, close);
                    dailyCPRCache.put(instrumentToken, cprLevels);
                    
                    logger.info("CPR calculated for " + instrumentToken + ": " + cprLevels);
                }
                
            } catch (Exception e) {
                logger.warning("Failed to calculate CPR for " + instrumentToken + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Start main trading loops
     */
    private void startTradingLoops() {
        // Main trading loop - runs every 10 seconds
        scheduler.scheduleWithFixedDelay(this::mainTradingLoop, 0, 10, TimeUnit.SECONDS);
        
        // Position monitoring loop - runs every 5 seconds
        scheduler.scheduleWithFixedDelay(this::monitorPositions, 5, 5, TimeUnit.SECONDS);
        
        // Market status check - runs every minute
        scheduler.scheduleWithFixedDelay(this::checkMarketStatus, 0, 60, TimeUnit.SECONDS);
        
        // Daily reset - runs at market open
        scheduler.scheduleWithFixedDelay(this::performDailyReset, 0, 24, TimeUnit.HOURS);
    }
    
    /**
     * Main trading logic loop
     */
    private void mainTradingLoop() {
        if (!isRunning || !isLoggedIn || !instrumentManager.isMarketOpen()) {
            return;
        }
        
        try {
            Map<String, Double> currentPrices = instrumentManager.getCurrentPrices();
            
            for (Map.Entry<String, Double> entry : currentPrices.entrySet()) {
                String instrumentToken = entry.getKey();
                double currentPrice = entry.getValue();
                
                // Generate trading signals
                List<TradingSignal> signals = generateTradingSignals(instrumentToken, currentPrice);
                
                // Execute valid signals
                for (TradingSignal signal : signals) {
                    if (signal.shouldExecute()) {
                        executeSignal(signal);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warning("Error in main trading loop: " + e.getMessage());
        }
    }
    
    /**
     * Generate trading signals based on CPR logic
     */
    private List<TradingSignal> generateTradingSignals(String instrumentToken, double currentPrice) {
        List<TradingSignal> signals = new ArrayList<>();
        
        CPRLevels cprLevels = dailyCPRCache.get(instrumentToken);
        if (cprLevels == null) return signals;
        
        // Check if position already exists
        if (positionManager.getOpenPositions().containsKey(instrumentToken)) {
            return signals; // Already have position
        }
        
        // CPR Trading Strategy Logic
        
        // 1. Price above CPR - Bullish signal
        if (cprLevels.isPriceAboveCPR(currentPrice)) {
            double stopLoss = cprLevels.getBottomCentral();
            double target = cprLevels.getR1();
            
            // Ensure good risk-reward ratio (minimum 1:1.5)
            double riskReward = Math.abs(target - currentPrice) / Math.abs(currentPrice - stopLoss);
            
            if (riskReward >= 1.5) {
                int quantity = positionManager.calculatePositionSize(currentPrice, stopLoss, instrumentToken);
                quantity = instrumentManager.calculateLotAdjustedQuantity(instrumentToken, quantity);
                
                TradingSignal signal = new TradingSignal.Builder(instrumentToken, TradingSignal.SignalType.BUY)
                    .triggerPrice(currentPrice)
                    .targetPrice(target)
                    .stopLossPrice(stopLoss)
                    .quantity(quantity)
                    .strength(getSignalStrength(cprLevels, currentPrice))
                    .reason(TradingSignal.TriggerReason.PRICE_ABOVE_CPR)
                    .confidence(calculateConfidence(cprLevels, currentPrice, true))
                    .build();
                
                signals.add(signal);
            }
        }
        
        // 2. Price below CPR - Bearish signal
        else if (cprLevels.isPriceBelowCPR(currentPrice)) {
            double stopLoss = cprLevels.getTopCentral();
            double target = cprLevels.getS1();
            
            double riskReward = Math.abs(currentPrice - target) / Math.abs(stopLoss - currentPrice);
            
            if (riskReward >= 1.5) {
                int quantity = positionManager.calculatePositionSize(currentPrice, stopLoss, instrumentToken);
                quantity = instrumentManager.calculateLotAdjustedQuantity(instrumentToken, quantity);
                
                TradingSignal signal = new TradingSignal.Builder(instrumentToken, TradingSignal.SignalType.SELL)
                    .triggerPrice(currentPrice)
                    .targetPrice(target)
                    .stopLossPrice(stopLoss)
                    .quantity(quantity)
                    .strength(getSignalStrength(cprLevels, currentPrice))
                    .reason(TradingSignal.TriggerReason.PRICE_BELOW_CPR)
                    .confidence(calculateConfidence(cprLevels, currentPrice, false))
                    .build();
                
                signals.add(signal);
            }
        }
        
        return signals;
    }
    
    /**
     * Calculate signal strength based on CPR characteristics
     */
    private TradingSignal.SignalStrength getSignalStrength(CPRLevels cprLevels, double currentPrice) {
        if (cprLevels.getCprType() == CPRLevels.CPRType.NARROW_CPR) {
            return TradingSignal.SignalStrength.STRONG; // Narrow CPR indicates strong breakout potential
        } else if (cprLevels.getCprType() == CPRLevels.CPRType.WIDE_CPR) {
            return TradingSignal.SignalStrength.WEAK; // Wide CPR indicates sideways movement
        } else {
            return TradingSignal.SignalStrength.MODERATE;
        }
    }
    
    /**
     * Calculate signal confidence
     */
    private double calculateConfidence(CPRLevels cprLevels, double currentPrice, boolean isBullish) {
        double confidence = 0.5; // Base confidence
        
        // Higher confidence for narrow CPR
        if (cprLevels.getCprType() == CPRLevels.CPRType.NARROW_CPR) {
            confidence += 0.2;
        }
        
        // Higher confidence when price is significantly away from CPR
        double distanceFromCPR;
        if (isBullish) {
            distanceFromCPR = (currentPrice - cprLevels.getTopCentral()) / cprLevels.getTopCentral();
        } else {
            distanceFromCPR = (cprLevels.getBottomCentral() - currentPrice) / cprLevels.getBottomCentral();
        }
        
        if (distanceFromCPR > 0.005) { // More than 0.5% away
            confidence += 0.15;
        }
        
        return Math.min(1.0, confidence);
    }
    
    /**
     * Execute trading signal
     */
    private void executeSignal(TradingSignal signal) {
        try {
            if (positionManager.openPosition(signal)) {
                // Place actual order through Zerodha API
                boolean orderSuccess = placeOrder(signal);
                
                if (orderSuccess) {
                    logger.info("Order executed successfully: " + signal);
                } else {
                    logger.warning("Failed to place order: " + signal);
                }
            } else {
                logger.info("Position rejected by risk management: " + signal.getInstrumentToken());
            }
            
        } catch (Exception e) {
            logger.severe("Error executing signal: " + e.getMessage());
        }
    }
    
    /**
     * Place order through Zerodha API
     */
    private boolean placeOrder(TradingSignal signal) {
        try {
            // This would integrate with your login.ZerodhaAutoLogin class
            Map<String, Object> orderParams = new HashMap<>();
            orderParams.put("tradingsymbol", instrumentManager.getInstrumentByToken(signal.getInstrumentToken()).getTradingSymbol());
            orderParams.put("exchange", "NSE");
            orderParams.put("transaction_type", signal.getType() == TradingSignal.SignalType.BUY ? "BUY" : "SELL");
            orderParams.put("quantity", signal.getQuantity());
            orderParams.put("price", signal.getTriggerPrice());
            orderParams.put("product", "MIS"); // Intraday
            orderParams.put("order_type", "LIMIT");
            
        } catch (Exception e) {
            logger.severe("Order placement error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Monitor open positions for exits
     */
    private void monitorPositions() {
        if (!isRunning || !isLoggedIn) return;
        
        try {
            Map<String, Double> currentPrices = instrumentManager.getCurrentPrices();
            List<PositionManager.Position> positionsToClose = positionManager.updatePositions(currentPrices);
            
            for (PositionManager.Position position : positionsToClose) {
                String reason = position.shouldCloseOnTarget() ? "Target Hit" : "Stop Loss Hit";
                positionManager.closePosition(position.getInstrumentToken(), position.getCurrentPrice(), reason);
                
                // Place exit order
                closePosition(position, reason);
            }
            
        } catch (Exception e) {
            logger.warning("Error monitoring positions: " + e.getMessage());
        }
    }
    
    /**
     * Close position through API
     */
    private void closePosition(PositionManager.Position position, String reason) {
        try {
            Map<String, Object> orderParams = new HashMap<>();
            orderParams.put("tradingsymbol", instrumentManager.getInstrumentByToken(position.getInstrumentToken()).getTradingSymbol());
            orderParams.put("exchange", "NSE");
            orderParams.put("transaction_type", position.getTransactionType().equals("BUY") ? "SELL" : "BUY");
            orderParams.put("quantity", position.getQuantity());
            orderParams.put("product", "MIS");
            orderParams.put("order_type", "MARKET");
            
            boolean orderSuccess = zerodhaLogin.placeOrder(orderParams);
            logger.info(String.format("Position closed: %s, Reason: %s, Success: %s", 
                       position.getInstrumentToken(), reason, orderSuccess));
            
        } catch (Exception e) {
            logger.severe("Error closing position: " + e.getMessage());
        }
    }
    
    /**
     * Check market status and handle market close
     */
    private void checkMarketStatus() {
        if (!instrumentManager.isMarketOpen() && isRunning) {
            logger.info("Market closed. Closing all positions...");
            closeAllPositions("Market Close");
        }
        
        // Print daily statistics
        if (isRunning) {
            printDailyStats();
        }
    }
    
    /**
     * Close all open positions
     */
    private void closeAllPositions(String reason) {
        Map<String, PositionManager.Position> openPositions = positionManager.getOpenPositions();
        
        for (PositionManager.Position position : openPositions.values()) {
            closePosition(position, reason);
            positionManager.closePosition(position.getInstrumentToken(), position.getCurrentPrice(), reason);
        }
    }
    
    /**
     * Print daily trading statistics
     */
    private void printDailyStats() {
        Map<String, Object> stats = positionManager.getDailyStats();
        Map<String, Object> marketStatus = instrumentManager.getMarketStatus();
        
        logger.info("=== Daily Trading Stats ===");
        logger.info("Total Capital: " + stats.get("totalCapital"));
        logger.info("Daily PnL: " + stats.get("dailyPnL"));
        logger.info("Open Positions: " + stats.get("openPositions"));
        logger.info("Unrealized PnL: " + stats.get("totalUnrealizedPnL"));
        logger.info("Portfolio Exposure: " + stats.get("portfolioExposure"));
        logger.info("Market Open: " + marketStatus.get("isMarketOpen"));
        logger.info("==========================");
    }
    
    /**
     * Perform daily reset at market open
     */
    private void performDailyReset() {
        if (instrumentManager.isMarketOpen()) {
            logger.info("Performing daily reset...");
            
            // Recalculate CPR levels
            calculateDailyCPRLevels();
            
            // Reset position manager counters
            positionManager.resetDailyCounters();
            
            logger.info("Daily reset completed.");
        }
    }
    
    /**
     * Stop trading bot
     */
    public void stopTrading() {
        logger.info("Stopping CPR Trading Bot...");
        
        isRunning = false;
        
        // Close all open positions
        closeAllPositions("Bot Shutdown");
        
        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("CPR Trading Bot stopped.");
    }
    
    // Main method to run the bot
    public static void main(String[] args) {
        try {
            // Initialize components
            HistoricalDataFetcher historicalFetcher = new HistoricalDataFetcher();
            
            // Create and start bot
            CPRTradingBot bot = new CPRTradingBot(historicalFetcher);
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(bot::stopTrading));
            
            // Start trading
            bot.startTrading();
            
            // Keep main thread alive
            while (bot.isRunning) {
                Thread.sleep(10000); // Check every 10 seconds
            }
            
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Getter for status check
    public boolean isRunning() {
        return isRunning;
    }
    
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isRunning", isRunning);
        status.put("isLoggedIn", isLoggedIn);
        status.put("dailyStats", positionManager.getDailyStats());
        status.put("marketStatus", instrumentManager.getMarketStatus());
        return status;
    }
}
