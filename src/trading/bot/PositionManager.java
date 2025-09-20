package trading.bot;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PositionManager {
    
    private final Map<String, Position> activePositions = new ConcurrentHashMap<>();
    private final Map<String, Double> entryPrices = new ConcurrentHashMap<>();
    private final Map<String, String> orderIds = new ConcurrentHashMap<>();
    
    // Default quantity for options trading
    private static final int DEFAULT_QUANTITY = 50; // Nifty lot size
    
    /**
     * Shorts an option (CE or PE)
     */
    public void shortOption(KiteConnect kiteConnect, String instrumentToken, String optionType) 
            throws KiteException, IOException {
        
        try {
            // Get current market price for the option
            String[] instruments = {instrumentToken};
            Map<String, Quote> quotes = kiteConnect.getQuote(instruments);
            
            if (!quotes.containsKey(instrumentToken)) {
                throw new RuntimeException("Unable to get quote for instrument: " + instrumentToken);
            }
            
            Quote quote = quotes.get(instrumentToken);
            double currentPrice = quote.lastPrice;
            
            // Place sell order (short)
            OrderParams orderParams = new OrderParams();
            orderParams.quantity = DEFAULT_QUANTITY;
            orderParams.price = 0.0; // Market order
            orderParams.exchange = Constants.EXCHANGE_NFO;
            orderParams.tradingsymbol = getTradingSymbol(instrumentToken, optionType);
            orderParams.transactionType = Constants.TRANSACTION_TYPE_SELL;
            orderParams.orderType = Constants.ORDER_TYPE_MARKET;
            orderParams.product = Constants.PRODUCT_MIS; // Intraday
            orderParams.validity = Constants.VALIDITY_DAY;
            
            Order order = kiteConnect.placeOrder(orderParams, Constants.VARIETY_REGULAR);
            
            // Store position details
            String positionKey = instrumentToken + "_" + optionType;
            Position position = new Position();
            position.tradingSymbol = orderParams.tradingsymbol;
            position.quantity = -DEFAULT_QUANTITY; // Negative for short position
            position.product = Constants.PRODUCT_MIS;
            
            activePositions.put(positionKey, position);
            entryPrices.put(positionKey, currentPrice);
            orderIds.put(positionKey, order.orderId);
            
            System.out.println(String.format("Shorted %s %s at price %.2f, Order ID: %s", 
                             optionType, orderParams.tradingsymbol, currentPrice, order.orderId));
            
        } catch (Exception e) {
            System.err.println("Error shorting option: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Monitors positions for stop loss
     */
    public void monitorStopLoss(KiteConnect kiteConnect, double stopLossPercentage) 
            throws KiteException, IOException {
        
        if (activePositions.isEmpty()) {
            return;
        }
        
        System.out.println("Monitoring " + activePositions.size() + " positions for stop loss...");
        
        for (Map.Entry<String, Position> entry : activePositions.entrySet()) {
            String positionKey = entry.getKey();
            Position position = entry.getValue();
            
            try {
                // Get current market price
                String instrumentToken = positionKey.split("_")[0];
                String[] instruments = {instrumentToken};
                Map<String, Quote> quotes = kiteConnect.getQuote(instruments);
                
                if (quotes.containsKey(instrumentToken)) {
                    Quote quote = quotes.get(instrumentToken);
                    double currentPrice = quote.lastPrice;
                    double entryPrice = entryPrices.get(positionKey);
                    
                    // Calculate P&L percentage for short position
                    double pnlPercentage = (entryPrice - currentPrice) / entryPrice;
                    
                    System.out.println(String.format("Position %s: Entry=%.2f, Current=%.2f, P&L=%.2f%%", 
                                     position.tradingSymbol, entryPrice, currentPrice, pnlPercentage * 100));
                    
                    // Check if stop loss is hit (loss > stopLossPercentage)
                    if (pnlPercentage < -stopLossPercentage) {
                        System.out.println("Stop loss hit for " + position.tradingSymbol + 
                                         ". Closing position...");
                        closePosition(kiteConnect, positionKey);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error monitoring position " + positionKey + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Closes a specific position
     */
    public void closePosition(KiteConnect kiteConnect, String positionKey) 
            throws KiteException, IOException {
        
        Position position = activePositions.get(positionKey);
        if (position == null) {
            return;
        }
        
        try {
            // Place buy order to close short position
            OrderParams orderParams = new OrderParams();
            orderParams.quantity = Math.abs(position.quantity);
            orderParams.price = 0.0; // Market order
            orderParams.exchange = Constants.EXCHANGE_NFO;
            orderParams.tradingsymbol = position.tradingSymbol;
            orderParams.transactionType = Constants.TRANSACTION_TYPE_BUY;
            orderParams.orderType = Constants.ORDER_TYPE_MARKET;
            orderParams.product = Constants.PRODUCT_MIS;
            orderParams.validity = Constants.VALIDITY_DAY;
            
            Order order = kiteConnect.placeOrder(orderParams, Constants.VARIETY_REGULAR);
            
            System.out.println("Closed position " + position.tradingSymbol + 
                             ", Order ID: " + order.orderId);
            
            // Remove from active positions
            activePositions.remove(positionKey);
            entryPrices.remove(positionKey);
            orderIds.remove(positionKey);
            
        } catch (Exception e) {
            System.err.println("Error closing position " + positionKey + ": " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Exits all active positions
     */
    public void exitAllPositions(KiteConnect kiteConnect) throws KiteException, IOException {
        System.out.println("Exiting all " + activePositions.size() + " positions...");
        
        // Create a copy of keys to avoid concurrent modification
        Set<String> positionKeys = new HashSet<>(activePositions.keySet());
        
        for (String positionKey : positionKeys) {
            try {
                closePosition(kiteConnect, positionKey);
                Thread.sleep(1000); // Small delay between orders
            } catch (Exception e) {
                System.err.println("Error exiting position " + positionKey + ": " + e.getMessage());
            }
        }
        
        System.out.println("All positions exited.");
    }
    
    /**
     * Gets the current P&L for all positions
     */
    public double getTotalPnL(KiteConnect kiteConnect) throws KiteException, IOException {
        double totalPnL = 0.0;
        
        for (Map.Entry<String, Position> entry : activePositions.entrySet()) {
            String positionKey = entry.getKey();
            Position position = entry.getValue();
            
            try {
                String instrumentToken = positionKey.split("_")[0];
                String[] instruments = {instrumentToken};
                Map<String, Quote> quotes = kiteConnect.getQuote(instruments);
                
                if (quotes.containsKey(instrumentToken)) {
                    Quote quote = quotes.get(instrumentToken);
                    double currentPrice = quote.lastPrice;
                    double entryPrice = entryPrices.get(positionKey);
                    
                    // P&L for short position: (entry_price - current_price) * quantity
                    double positionPnL = (entryPrice - currentPrice) * Math.abs(position.quantity);
                    totalPnL += positionPnL;
                }
            } catch (Exception e) {
                System.err.println("Error calculating P&L for " + positionKey + ": " + e.getMessage());
            }
        }
        
        return totalPnL;
    }
    
    /**
     * Gets trading symbol from instrument token and option type
     */
    private String getTradingSymbol(String instrumentToken, String optionType) {
        // This is a placeholder - you'll need to implement proper mapping
        // based on Zerodha's instrument master or use instrument lookup
        return instrumentToken + "_" + optionType;
    }
    
    /**
     * Gets the number of active positions
     */
    public int getActivePositionCount() {
        return activePositions.size();
    }
    
    /**
     * Checks if there are any active positions
     */
    public boolean hasActivePositions() {
        return !activePositions.isEmpty();
    }
    
    /**
     * Gets details of all active positions
     */
    public void printPositionSummary(KiteConnect kiteConnect) {
        if (activePositions.isEmpty()) {
            System.out.println("No active positions.");
            return;
        }
        
        System.out.println("\n=== Position Summary ===");
        try {
            double totalPnL = getTotalPnL(kiteConnect);
            System.out.println("Total P&L: ₹" + String.format("%.2f", totalPnL));
            System.out.println("Active Positions: " + activePositions.size());
            
            for (Map.Entry<String, Position> entry : activePositions.entrySet()) {
                String positionKey = entry.getKey();
                Position position = entry.getValue();
                double entryPrice = entryPrices.get(positionKey);
                
                System.out.println(String.format("- %s: Qty=%d, Entry=%.2f", 
                                 position.tradingSymbol, position.quantity, entryPrice));
            }
        } catch (Exception e) {
            System.err.println("Error printing position summary: " + e.getMessage());
        }
        System.out.println("========================\n");
    }
}
