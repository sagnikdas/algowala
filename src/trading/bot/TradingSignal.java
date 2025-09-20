package trading.bot;

import java.time.LocalDateTime;

public class TradingSignal {
    public enum SignalType {
        BUY, SELL, HOLD, CLOSE_LONG, CLOSE_SHORT
    }
    
    public enum SignalStrength {
        WEAK(1), MODERATE(2), STRONG(3), VERY_STRONG(4);
        
        private final int value;
        SignalStrength(int value) { this.value = value; }
        public int getValue() { return value; }
    }
    
    public enum TriggerReason {
        PRICE_ABOVE_CPR, PRICE_BELOW_CPR, CPR_RETEST_SUCCESS, CPR_RETEST_FAIL,
        RESISTANCE_BREAK, SUPPORT_BREAK, CONFLUENCE_BULLISH, CONFLUENCE_BEARISH,
        STOP_LOSS_HIT, TARGET_ACHIEVED, TIME_BASED_EXIT
    }
    
    private final String instrumentToken;
    private final SignalType type;
    private final SignalStrength strength;
    private final TriggerReason reason;
    private final double triggerPrice;
    private final double targetPrice;
    private final double stopLossPrice;
    private final int quantity;
    private final LocalDateTime timestamp;
    private final double confidence;
    
    private TradingSignal(Builder builder) {
        this.instrumentToken = builder.instrumentToken;
        this.type = builder.type;
        this.strength = builder.strength;
        this.reason = builder.reason;
        this.triggerPrice = builder.triggerPrice;
        this.targetPrice = builder.targetPrice;
        this.stopLossPrice = builder.stopLossPrice;
        this.quantity = builder.quantity;
        this.timestamp = builder.timestamp;
        this.confidence = builder.confidence;
    }
    
    // Getters
    public String getInstrumentToken() { return instrumentToken; }
    public SignalType getType() { return type; }
    public SignalStrength getStrength() { return strength; }
    public TriggerReason getReason() { return reason; }
    public double getTriggerPrice() { return triggerPrice; }
    public double getTargetPrice() { return targetPrice; }
    public double getStopLossPrice() { return stopLossPrice; }
    public int getQuantity() { return quantity; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public double getConfidence() { return confidence; }
    
    public boolean isValid() {
        return instrumentToken != null && type != null && triggerPrice > 0 && quantity > 0;
    }
    
    public boolean shouldExecute() {
        return isValid() && confidence > 0.6 && strength.getValue() >= 2;
    }
    
    public double getRiskRewardRatio() {
        double risk = Math.abs(triggerPrice - stopLossPrice);
        double reward = Math.abs(targetPrice - triggerPrice);
        return risk > 0 ? reward / risk : 0;
    }
    
    @Override
    public String toString() {
        return String.format("Signal[%s %s@%.2f, Target:%.2f, SL:%.2f, Qty:%d]", 
                           type, instrumentToken, triggerPrice, targetPrice, stopLossPrice, quantity);
    }
    
    // Builder Pattern
    public static class Builder {
        private String instrumentToken;
        private SignalType type;
        private SignalStrength strength = SignalStrength.MODERATE;
        private TriggerReason reason;
        private double triggerPrice;
        private double targetPrice;
        private double stopLossPrice;
        private int quantity;
        private LocalDateTime timestamp = LocalDateTime.now();
        private double confidence = 0.5;
        
        public Builder(String instrumentToken, SignalType type) {
            this.instrumentToken = instrumentToken;
            this.type = type;
        }
        
        public Builder strength(SignalStrength strength) { this.strength = strength; return this; }
        public Builder reason(TriggerReason reason) { this.reason = reason; return this; }
        public Builder triggerPrice(double price) { this.triggerPrice = price; return this; }
        public Builder targetPrice(double price) { this.targetPrice = price; return this; }
        public Builder stopLossPrice(double price) { this.stopLossPrice = price; return this; }
        public Builder quantity(int quantity) { this.quantity = quantity; return this; }
        public Builder confidence(double confidence) { 
            this.confidence = Math.max(0, Math.min(1, confidence)); 
            return this; 
        }
        
        public TradingSignal build() {
            return new TradingSignal(this);
        }
    }
}
