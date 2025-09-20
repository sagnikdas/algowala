package trading.bot;

public class TradingSignal {
    private final String action;
    private final String reason;
    private final double targetLevel;
    
    public TradingSignal(String action, String reason, double targetLevel) {
        this.action = action;
        this.reason = reason;
        this.targetLevel = targetLevel;
    }
    
    public String getAction() {
        return action;
    }
    
    public String getReason() {
        return reason;
    }
    
    public double getTargetLevel() {
        return targetLevel;
    }
    
    public boolean shouldTrade() {
        return !"NO_TRADE".equals(action);
    }
    
    @Override
    public String toString() {
        return String.format("TradingSignal{action='%s', reason='%s', targetLevel=%.2f}", 
                           action, reason, targetLevel);
    }
}
