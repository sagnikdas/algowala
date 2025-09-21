# Algowala: Zerodha CPR Trading Bot

## Overview
Algowala is an automated trading bot for Zerodha, built around the Central Pivot Range (CPR) strategy. It fetches historical and live market data, calculates CPR levels, generates trading signals, and manages positions with robust risk controls. The bot is modular, extensible, and designed for real-world trading automation.

## Features
- Automated Zerodha login and access token management
- Historical OHLCV data fetching via Kite API
- CPR level calculation and technical analysis
- Real-time signal generation and order execution
- Position and risk management
- Configurable trading parameters
- Modular architecture for easy extension

## Architecture
### Main Components
- **CPRTradingBot**: Orchestrates the trading logic, manages lifecycle, and coordinates all modules.
- **HistoricalDataFetcher**: Fetches historical OHLCV data from Zerodha Kite API.
- **CPRCalculator & CPRLevels**: Calculates CPR levels and market sentiment.
- **InstrumentManager**: Manages tradable instruments and live quotes.
- **PositionManager**: Handles open/closed positions and risk management.
- **TradingSignal**: Encapsulates trading signals and their metadata.
- **ZerodhaAutoLogin**: Automates Zerodha login using Selenium and manages access tokens.
- **CandleData**: Represents a single OHLCV candle.

### Flow
1. **Login**: Automated via Selenium, access token saved for API use.
2. **Data Fetching**: Historical data fetched for CPR calculation.
3. **CPR Calculation**: CPR levels computed for each instrument.
4. **Signal Generation**: Trading signals generated based on CPR logic and live prices.
5. **Order Execution**: Signals executed via Zerodha API, positions managed with risk controls.
6. **Monitoring**: Positions and market status monitored in real time.

## Setup & Installation
1. **Clone the repository:**
   ```bash
   git clone https://github.com/sagnikdas/algowala.git
   cd algowala
   ```
2. **Install dependencies:**
   - Java 11+
   - Maven (for building)
   - ChromeDriver (for Selenium)
   - Zerodha KiteConnect Java SDK
   - Selenium Java bindings
   - Gson, Jackson (for JSON)

3. **Configure API keys:**
   - Set your Zerodha API key and secret in the login prompt or environment variables.

4. **Build the project:**
   ```bash
   mvn clean install
   ```

## Usage
- **Login and generate access token:**
  Run the main method in `ZerodhaAutoLogin.java` and follow prompts for credentials and OTP.
- **Start the bot:**
  Run the main method in `CPRTradingBot.java`. The bot will log in, subscribe to instruments, calculate CPR levels, and begin trading.

## File Structure
```
algowala/
├── src/
│   ├── historical/
│   │   └── HistoricalDataFetcher.java
│   ├── login/
│   │   └── ZerodhaAutoLogin.java
│   ├── model/
│   │   └── CandleData.java
│   └── trading/
│       └── bot/
│           ├── CPRCalculator.java
│           ├── CPRLevels.java
│           ├── CPRTradingBot.java
│           ├── InstrumentManager.java
│           ├── PositionManager.java
│           └── TradingSignal.java
├── login/
│   └── access_token.json
├── pom.xml
└── README.md
```

## Contribution
Pull requests and issues are welcome! Please follow standard Java coding conventions and document your changes.

## License
This project is licensed under the MIT License.

## Disclaimer
This bot is for educational purposes only. Use at your own risk. Trading in financial markets involves risk; past performance is not indicative of future results.

