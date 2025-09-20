package trading.bot;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InstrumentManager {
    
    public static class Instrument {
        private final String instrumentToken;
        private final String tradingSymbol;
        private final String name;
        private final String exchange;
        private final String segment;
        private final double tickSize;
        private final double lotSize;
        private final boolean isActive;
        
        public Instrument(String instrumentToken, String tradingSymbol, String name,
                         String exchange, String segment, double tickSize, double lotSize) {
            this.instrumentToken = instrumentToken;
            this.tradingSymbol = tradingSymbol;
            this.name = name;
            this.exchange = exchange;
            this.segment = segment;
            this.tickSize = tickSize;
            this.lotSize = lotSize;
            this.isActive = true;
        }
        
        public String getInstrumentToken() { return instrumentToken; }
        public String getTradingSymbol() { return tradingSymbol; }
        public String getName() { return name; }
        public String getExchange() { return exchange; }
        public String getSegment() { return segment; }
        public double getTickSize() { return tickSize; }
        public double getLotSize() { return lotSize; }
        public boolean isActive() { return isActive; }
    }
    
    public static class LiveQuote {
        private final String instrumentToken;
        private final double lastPrice;
        private final double open;
        private final double high;
        private final double low;
        private final double close;
        private final long volume;
        private final double change;
        private final double changePercent;
        private final LocalDateTime timestamp;
        
        public LiveQuote(String instrumentToken, double lastPrice, double open, 
                        double high, double low, double close, long volume) {
            this.instrumentToken = instrumentToken;
            this.lastPrice = lastPrice;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.change = lastPrice - close;
            this.changePercent = (change / close) * 100;
            this.timestamp = LocalDateTime.now();
        }
        
        public String getInstrumentToken() { return instrumentToken; }
        public double getLastPrice() { return lastPrice; }
        public double getOpen() { return open; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getClose() { return close; }
        public long getVolume() { return volume; }
        public double getChange() { return change; }
        public double getChangePercent() { return changePercent; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    private final Map<String, Instrument> instruments = new ConcurrentHashMap<>();
    private final Map<String, String> symbolToToken = new ConcurrentHashMap<>();
    private final Map<String, LiveQuote> liveQuotes = new ConcurrentHashMap<>();
    private final Set<String> subscribedTokens = ConcurrentHashMap.newKeySet();
    
    public void initializeCommonInstruments() {
        addInstrument("256265", "NIFTY 50", "NIFTY 50", "NSE", "INDICES", 0.05, 25);
        addInstrument("260105", "NIFTY BANK", "NIFTY BANK", "NSE", "INDICES", 0.05, 15);
        addInstrument("8045826", "NIFTY23SEPFUT", "NIFTY 50 SEP FUT", "NFO", "FUT", 0.05, 25);
        addInstrument("8040706", "BANKNIFTY23SEPFUT", "BANK NIFTY SEP FUT", "NFO", "FUT", 0.05, 15);
        
        // Initialize with mock live data
        updateLiveQuote("256265", 19800, 19750, 19850, 19720, 19780, 1000000);
        updateLiveQuote("260105", 44500, 44400, 44600, 44350, 44480, 500000);
        updateLiveQuote("8045826", 19850, 19800, 19900, 19770, 19830, 800000);
        updateLiveQuote("8040706", 44600, 44550, 44700, 44500, 44580, 400000);
    }
    
    public void addInstrument(String token, String symbol, String name, 
                             String exchange, String segment, double tickSize, double lotSize) {
        Instrument instrument = new Instrument(token, symbol, name, exchange, segment, tickSize, lotSize);
        instruments.put(token, instrument);
        symbolToToken.put(symbol.toUpperCase(), token);
    }
    
    public Instrument getInstrumentByToken(String token) {
        return instruments.get(token);
    }
    
    public boolean subscribeToInstrument(String instrumentToken) {
        Instrument instrument = instruments.get(instrumentToken);
        if (instrument != null && instrument.isActive()) {
            subscribedTokens.add(instrumentToken);
            return true;
        }
        return false;
    }
    
    public void updateLiveQuote(String instrumentToken, double lastPrice, double open,
                               double high, double low, double close, long volume) {
        if (subscribedTokens.contains(instrumentToken) || instruments.containsKey(instrumentToken)) {
            LiveQuote quote = new LiveQuote(instrumentToken, lastPrice, open, high, low, close, volume);
            liveQuotes.put(instrumentToken, quote);
        }
    }
    
    public LiveQuote getLiveQuote(String instrumentToken) {
        return liveQuotes.get(instrumentToken);
    }
    
    public Map<String, Double> getCurrentPrices() {
        Map<String, Double> prices = new HashMap<>();
        for (Map.Entry<String, LiveQuote> entry : liveQuotes.entrySet()) {
            // Simulate live price changes
            double basePrice = entry.getValue().getLastPrice();
            double currentPrice = basePrice + (Math.random() - 0.5) * basePrice * 0.001; // 0.1% variation
            prices.put(entry.getKey(), currentPrice);
        }
        return prices;
    }
    
    public int calculateLotAdjustedQuantity(String instrumentToken, int desiredQuantity) {
        Instrument instrument = instruments.get(instrumentToken);
        if (instrument != null) {
            double lotSize = instrument.getLotSize();
            return (int) (Math.floor(desiredQuantity / lotSize) * lotSize);
        }
        return desiredQuantity;
    }
    
    public List<String> getCPRWatchlist() {
        return Arrays.asList("256265", "260105", "8045826", "8040706");
    }
    
    public boolean isMarketOpen() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();
        int dayOfWeek = now.getDayOfWeek().getValue();
        
        boolean isWeekday = dayOfWeek >= 1 && dayOfWeek <= 5;
        boolean isMarketHours = (hour == 9 && minute >= 15) || (hour >= 10 && hour < 15) || (hour == 15 && minute <= 30);
        
        return isWeekday && isMarketHours;
    }
    
    public Map<String, Object> getMarketStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isMarketOpen", isMarketOpen());
        status.put("subscribedInstruments", subscribedTokens.size());
        status.put("liveQuotesAvailable", liveQuotes.size());
        status.put("totalInstruments", instruments.size());
        status.put("lastUpdateTime", LocalDateTime.now());
        return status;
    }
}
