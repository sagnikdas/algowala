package model;

public class CandleData {

    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    //private long oi;
    private long ts;

    public CandleData(double open, double high, double low, double close, double volume, long oi, long ts) {
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.oi = oi;
        this.ts = ts;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public long getOi() {
        return oi;
    }

    public void setOi(long oi) {
        this.oi = oi;
    }
}
