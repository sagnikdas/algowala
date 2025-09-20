package trading.bot;

public class CPRCalculator {
    
    /**
     * Calculates CPR (Central Pivot Range) levels based on previous day's OHLC data
     * 
     * @param high Previous day's high
     * @param low Previous day's low
     * @param close Previous day's close
     * @return CPRLevels object containing all calculated levels
     */
    public CPRLevels calculateCPR(double high, double low, double close) {
        // Calculate Pivot Point
        double pivot = (high + low + close) / 3.0;
        
        // Calculate Support and Resistance levels
        double r1 = (2 * pivot) - low;  // First Resistance
        double s1 = (2 * pivot) - high; // First Support
        double r2 = pivot + (high - low); // Second Resistance
        double s2 = pivot - (high - low); // Second Support
        double r3 = high + 2 * (pivot - low); // Third Resistance
        double s3 = low - 2 * (high - pivot); // Third Support
        
        // Calculate CPR levels
        double tc = (pivot - s1) + pivot; // Top Central (same as R1)
        double bc = pivot - (r1 - pivot); // Bottom Central (same as S1)
        
        // PDH and PDL are simply the previous day's high and low
        double pdh = high;
        double pdl = low;
        
        return new CPRLevels(pivot, r1, r2, r3, s1, s2, s3, tc, bc, pdh, pdl);
    }
    
    /**
     * Determines the market sentiment based on current price and CPR levels
     */
    public String getMarketSentiment(double currentPrice, CPRLevels cprLevels) {
        if (currentPrice > cprLevels.getR1()) {
            return "BULLISH";
        } else if (currentPrice < cprLevels.getS1()) {
            return "BEARISH";
        } else if (currentPrice >= cprLevels.getBC() && currentPrice <= cprLevels.getTC()) {
            return "SIDEWAYS";
        } else if (currentPrice > cprLevels.getTC()) {
            return "BULLISH_BIAS";
        } else if (currentPrice < cprLevels.getBC()) {
            return "BEARISH_BIAS";
        }
        return "NEUTRAL";
    }
    
    /**
     * Checks if current price is within the specified range for trading
     */
    public boolean isPriceInTradingRange(double currentPrice, CPRLevels cprLevels) {
        // Check if price is within PDH-R1 range (for shorting CE)
        boolean inPDHR1Range = currentPrice >= cprLevels.getPDH() && currentPrice <= cprLevels.getR1();
        
        // Check if price is within S1-PDL range (for shorting PE)
        boolean inS1PDLRange = currentPrice >= cprLevels.getS1() && currentPrice <= cprLevels.getPDL();
        
        return inPDHR1Range || inS1PDLRange;
    }
    
    /**
     * Gets the trading signal based on current price and CPR levels
     */
    public TradingSignal getTradingSignal(double currentPrice, CPRLevels cprLevels) {
        if (currentPrice >= cprLevels.getPDH() && currentPrice <= cprLevels.getR1()) {
            return new TradingSignal("SHORT_STRADDLE", "PDH_R1_RANGE", cprLevels.getPDH());
        } else if (currentPrice >= cprLevels.getS1() && currentPrice <= cprLevels.getPDL()) {
            return new TradingSignal("SHORT_STRADDLE", "S1_PDL_RANGE", cprLevels.getPDL());
        }
        return new TradingSignal("NO_TRADE", "OUT_OF_RANGE", 0);
    }
}
