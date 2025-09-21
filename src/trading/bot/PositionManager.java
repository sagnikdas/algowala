package trading.bot;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicDouble;

/**
 * Manages trading positions and risk parameters.
 */
public class PositionManager {
    /**
     * Represents a trading position.
     */
    public static class Position {
        private final String instrumentToken;
        private final String transactionType;
        private final int quantity;
        private final double entryPrice;
        private final double stopLoss;
        private final double target;
        private final LocalDateTime entryTime;
        private double currentPrice;
        private boolean isOpen = true;
        private double realizedPnL = 0;
        private LocalDateTime exitTime;

        /**
         * Constructs a Position object.
         */
        public Position(String instrumentToken, String transactionType, int quantity,
                       double entryPrice, double stopLoss, double target) {
            this.instrumentToken = instrumentToken;
            this.transactionType = transactionType;
            this.quantity = quantity;
            this.entryPrice = entryPrice;
            this.stopLoss = stopLoss;
            this.target = target;
            this.entryTime = LocalDateTime.now();
            this.currentPrice = entryPrice;
        }

        // Getters and setters
        public void updateCurrentPrice(double price) { this.currentPrice = price; }
        public double getUnrealizedPnL() {
            if (!isOpen) return realizedPnL;
            return "BUY".equals(transactionType) ?
                (currentPrice - entryPrice) * quantity : (entryPrice - currentPrice) * quantity;
        }
        public void closePosition(double exitPrice) {
            this.currentPrice = exitPrice;
            this.realizedPnL = getUnrealizedPnL();
            this.isOpen = false;
            this.exitTime = LocalDateTime.now();
        }
        public boolean shouldCloseOnStopLoss() {
            return isOpen && (("BUY".equals(transactionType) && currentPrice <= stopLoss) ||
                             ("SELL".equals(transactionType) && currentPrice >= stopLoss));
        }
        public boolean shouldCloseOnTarget() {
            return isOpen && (("BUY".equals(transactionType) && currentPrice >= target) ||
                             ("SELL".equals(transactionType) && currentPrice <= target));
        }
        public boolean isOpen() { return isOpen; }
        public String getInstrumentToken() { return instrumentToken; }
        public String getTransactionType() { return transactionType; }
        public int getQuantity() { return quantity; }
        public double getEntryPrice() { return entryPrice; }
        public double getStopLoss() { return stopLoss; }
        public double getTarget() { return target; }
        public LocalDateTime getEntryTime() { return entryTime; }
        public LocalDateTime getExitTime() { return exitTime; }
        public double getCurrentPrice() { return currentPrice; }
        public double getRealizedPnL() { return realizedPnL; }
    }

    public static class RiskParameters {
        private final double maxDailyLoss;
        private final double maxPositionSize;
        private final double riskPerTrade;
        private final int maxPositions;
        private final double maxPortfolioExposure;

        /**
         * Constructs RiskParameters object.
         */
        public RiskParameters(double maxDailyLoss, double maxPositionSize,
                            double riskPerTrade, int maxPositions, double maxPortfolioExposure) {
            this.maxDailyLoss = maxDailyLoss;
            this.maxPositionSize = maxPositionSize;
            this.riskPerTrade = riskPerTrade;
            this.maxPositions = maxPositions;
            this.maxPortfolioExposure = maxPortfolioExposure;
        }

        public double getMaxDailyLoss() { return maxDailyLoss; }
        public double getMaxPositionSize() { return maxPositionSize; }
        public double getRiskPerTrade() { return riskPerTrade; }
        public int getMaxPositions() { return maxPositions; }
        public double getMaxPortfolioExposure() { return maxPortfolioExposure; }
    }

    private final Map<String, Position> openPositions = new ConcurrentHashMap<>();
    private final List<Position> closedPositions = Collections.synchronizedList(new ArrayList<>());
    private final AtomicDouble totalCapital;
    private final RiskParameters riskParams;
    private final AtomicDouble dailyPnL = new AtomicDouble(0);

    /**
     * Constructs a PositionManager object.
     */
    public PositionManager(double initialCapital, RiskParameters riskParams) {
        this.totalCapital = new AtomicDouble(initialCapital);
        this.riskParams = riskParams;
    }

    public int calculatePositionSize(double entryPrice, double stopLoss, String instrumentToken) {
        double riskAmount = totalCapital.get() * (riskParams.getRiskPerTrade() / 100.0);
        double riskPerShare = Math.abs(entryPrice - stopLoss);

        if (riskPerShare <= 0) return 0;

        int calculatedSize = (int) (riskAmount / riskPerShare);
        double maxPositionValue = totalCapital.get() * (riskParams.getMaxPositionSize() / 100.0);
        int maxAllowedSize = (int) (maxPositionValue / entryPrice);

        return Math.min(calculatedSize, maxAllowedSize);
    }

    public boolean canOpenPosition(TradingSignal signal) {
        return openPositions.size() < riskParams.getMaxPositions() &&
               dailyPnL.get() > -riskParams.getMaxDailyLoss() &&
               !openPositions.containsKey(signal.getInstrumentToken());
    }

    public boolean openPosition(TradingSignal signal) {
        if (!canOpenPosition(signal)) return false;

        String transactionType = (signal.getType() == TradingSignal.SignalType.BUY) ? "BUY" : "SELL";
        Position position = new Position(signal.getInstrumentToken(), transactionType,
                signal.getQuantity(), signal.getTriggerPrice(), signal.getStopLossPrice(), signal.getTargetPrice());

        openPositions.put(signal.getInstrumentToken(), position);
        return true;
    }

    public List<Position> updatePositions(Map<String, Double> currentPrices) {
        List<Position> positionsToClose = new ArrayList<>();

        for (Position position : openPositions.values()) {
            Double currentPrice = currentPrices.get(position.getInstrumentToken());
            if (currentPrice != null) {
                position.updateCurrentPrice(currentPrice);
                if (position.shouldCloseOnStopLoss() || position.shouldCloseOnTarget()) {
                    positionsToClose.add(position);
                }
            }
        }
        return positionsToClose;
    }

    public void closePosition(String instrumentToken, double exitPrice, String reason) {
        Position position = openPositions.remove(instrumentToken);
        if (position != null) {
            position.closePosition(exitPrice);
            closedPositions.add(position);
            dailyPnL.addAndGet(position.getRealizedPnL());
            totalCapital.addAndGet(position.getRealizedPnL());
        }
    }

    public Map<String, Object> getDailyStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCapital", totalCapital.get());
        stats.put("dailyPnL", dailyPnL.get());
        stats.put("openPositions", openPositions.size());
        stats.put("totalUnrealizedPnL", openPositions.values().stream().mapToDouble(Position::getUnrealizedPnL).sum());
        stats.put("portfolioExposure", openPositions.values().stream().mapToDouble(p -> p.getEntryPrice() * p.getQuantity()).sum());
        stats.put("closedTradesToday", closedPositions.size());
        return stats;
    }

    public void resetDailyCounters() {
        dailyPnL.set(0);
        closedPositions.clear();
    }

    public Map<String, Position> getOpenPositions() { return new HashMap<>(openPositions); }
    public List<Position> getClosedPositions() { return new ArrayList<>(closedPositions); }
    public double getTotalCapital() { return totalCapital.get(); }
    public double getDailyPnL() { return dailyPnL.get(); }
}
