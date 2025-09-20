package trading.bot;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Instrument;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class InstrumentManager {
    
    private Map<String, Instrument> instrumentMap = new HashMap<>();
    private KiteConnect kiteConnect;
    
    public InstrumentManager(KiteConnect kiteConnect) {
        this.kiteConnect = kiteConnect;
    }
    
    /**
     * Loads all NFO instruments and filters for current week Nifty options
     */
    public void loadInstruments() throws KiteException, IOException {
        System.out.println("Loading NFO instruments...");
        
        // Get all NFO instruments
        Instrument[] instruments = kiteConnect.getInstruments("NFO");
        
        // Filter for Nifty options of current week
        String currentWeekExpiry = getCurrentWeekExpiry();
        
        for (Instrument instrument : instruments) {
            if (instrument.name.equals("NIFTY") && 
                instrument.instrumentType.equals("OPT") &&
                instrument.expiry.toString().equals(currentWeekExpiry)) {
                
                String key = createInstrumentKey(instrument.strike.intValue(), 
                                               instrument.instrumentType.equals("CE") ? "CE" : "PE");
                instrumentMap.put(key, instrument);
            }
        }
        
        System.out.println("Loaded " + instrumentMap.size() + " Nifty option instruments for expiry: " + currentWeekExpiry);
    }
    
    /**
     * Gets the instrument token for a specific strike and option type
     */
    public String getInstrumentToken(int strike, String optionType) {
        String key = createInstrumentKey(strike, optionType);
        Instrument instrument = instrumentMap.get(key);
        
        if (instrument != null) {
            return String.valueOf(instrument.instrumentToken);
        }
        
        throw new RuntimeException("Instrument not found for strike: " + strike + " " + optionType);
    }
    
    /**
     * Gets the trading symbol for a specific strike and option type
     */
    public String getTradingSymbol(int strike, String optionType) {
        String key = createInstrumentKey(strike, optionType);
        Instrument instrument = instrumentMap.get(key);
        
        if (instrument != null) {
            return instrument.tradingsymbol;
        }
        
        throw new RuntimeException("Trading symbol not found for strike: " + strike + " " + optionType);
    }
    
    /**
     * Gets the complete instrument object for a specific strike and option type
     */
    public Instrument getInstrument(int strike, String optionType) {
        String key = createInstrumentKey(strike, optionType);
        Instrument instrument = instrumentMap.get(key);
        
        if (instrument != null) {
            return instrument;
        }
        
        throw new RuntimeException("Instrument not found for strike: " + strike + " " + optionType);
    }
    
    /**
     * Gets all available strikes for the current expiry
     */
    public List<Integer> getAvailableStrikes() {
        return instrumentMap.values().stream()
                .map(instrument -> instrument.strike.intValue())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * Finds the closest available strike to the target price
     */
    public int findClosestStrike(double targetPrice) {
        List<Integer> strikes = getAvailableStrikes();
        
        if (strikes.isEmpty()) {
            throw new RuntimeException("No strikes available");
        }
        
        int closestStrike = strikes.get(0);
        double minDifference = Math.abs(targetPrice - closestStrike);
        
        for (int strike : strikes) {
            double difference = Math.abs(targetPrice - strike);
            if (difference < minDifference) {
                minDifference = difference;
                closestStrike = strike;
            }
        }
        
        return closestStrike;
    }
    
    /**
     * Gets strikes within a specific range of the target price
     */
    public List<Integer> getStrikesInRange(double targetPrice, double range) {
        List<Integer> strikes = getAvailableStrikes();
        
        return strikes.stream()
                .filter(strike -> Math.abs(strike - targetPrice) <= range)
                .sorted((a, b) -> Double.compare(Math.abs(a - targetPrice), Math.abs(b - targetPrice)))
                .collect(Collectors.toList());
    }
    
    /**
     * Checks if instruments are loaded
     */
    public boolean isLoaded() {
        return !instrumentMap.isEmpty();
    }
    
    /**
     * Gets the count of loaded instruments
     */
    public int getInstrumentCount() {
        return instrumentMap.size();
    }
    
    /**
     * Creates a unique key for instrument mapping
     */
    private String createInstrumentKey(int strike, String optionType) {
        return strike + "_" + optionType;
    }
    
    /**
     * Gets the current week's expiry date for Nifty options
     * Nifty weekly options expire on Thursdays
     */
    private String getCurrentWeekExpiry() {
        LocalDate today = LocalDate.now();
        LocalDate thursday = today;
        
        // Find the next Thursday (or today if it's Thursday)
        while (thursday.getDayOfWeek().getValue() != 4) { // 4 = Thursday
            thursday = thursday.plusDays(1);
        }
        
        // If today is Friday or later in the week, get next Thursday
        if (today.getDayOfWeek().getValue() > 4) {
            thursday = thursday.plusDays(7);
        }
        
        return thursday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
    /**
     * Prints summary of loaded instruments
     */
    public void printInstrumentSummary() {
        if (instrumentMap.isEmpty()) {
            System.out.println("No instruments loaded.");
            return;
        }
        
        List<Integer> strikes = getAvailableStrikes();
        System.out.println("\n=== Instrument Summary ===");
        System.out.println("Total instruments loaded: " + instrumentMap.size());
        System.out.println("Available strikes: " + strikes.size());
        System.out.println("Strike range: " + strikes.get(0) + " to " + strikes.get(strikes.size() - 1));
        System.out.println("Expiry date: " + getCurrentWeekExpiry());
        System.out.println("==========================\n");
    }
    
    /**
     * Validates that required strikes are available
     */
    public boolean validateStrikesAvailable(List<Integer> requiredStrikes) {
        List<Integer> availableStrikes = getAvailableStrikes();
        
        for (int strike : requiredStrikes) {
            if (!availableStrikes.contains(strike)) {
                System.err.println("Required strike not available: " + strike);
                return false;
            }
        }
        
        return true;
    }
}
