package trading.bot;

public class CPRLevels {
    private final double pivot;
    private final double r1, r2, r3;
    private final double s1, s2, s3;
    private final double tc, bc; // Top Central, Bottom Central
    private final double pdh, pdl; // Previous Day High, Previous Day Low
    
    public CPRLevels(double pivot, double r1, double r2, double r3, 
                     double s1, double s2, double s3, 
                     double tc, double bc, double pdh, double pdl) {
        this.pivot = pivot;
        this.r1 = r1;
        this.r2 = r2;
        this.r3 = r3;
        this.s1 = s1;
        this.s2 = s2;
        this.s3 = s3;
        this.tc = tc;
        this.bc = bc;
        this.pdh = pdh;
        this.pdl = pdl;
    }
    
    // Getters
    public double getPivot() { return pivot; }
    public double getR1() { return r1; }
    public double getR2() { return r2; }
    public double getR3() { return r3; }
    public double getS1() { return s1; }
    public double getS2() { return s2; }
    public double getS3() { return s3; }
    public double getTC() { return tc; }
    public double getBC() { return bc; }
    public double getPDH() { return pdh; }
    public double getPDL() { return pdl; }
    
    /**
     * Gets the CPR width (difference between TC and BC)
     */
    public double getCPRWidth() {
        return tc - bc;
    }
    
    /**
     * Determines if CPR is narrow (width < 12 points for Nifty)
     */
    public boolean isNarrowCPR() {
        return getCPRWidth() < 12.0;
    }
    
    /**
     * Determines if CPR is wide (width > 25 points for Nifty)
     */
    public boolean isWideCPR() {
        return getCPRWidth() > 25.0;
    }
    
    /**
     * Gets the range between PDH and PDL
     */
    public double getPDRange() {
        return pdh - pdl;
    }
    
    @Override
    public String toString() {
        return String.format(
            "CPR Levels:\n" +
            "Pivot: %.2f\n" +
            "R3: %.2f, R2: %.2f, R1: %.2f\n" +
            "S1: %.2f, S2: %.2f, S3: %.2f\n" +
            "TC: %.2f, BC: %.2f\n" +
            "PDH: %.2f, PDL: %.2f\n" +
            "CPR Width: %.2f",
            pivot, r3, r2, r1, s1, s2, s3, tc, bc, pdh, pdl, getCPRWidth()
        );
    }

    public boolean isPriceAboveCPR(double currentPrice) {
        //TODO check if the currentPrice is above CPR level
        return pivot > currentPrice;
    }

    public double getBottomCentral() {
        //TODO get the bottom central cpr
        return 0D;
    }
}
