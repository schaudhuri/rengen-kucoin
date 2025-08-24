# Order Book Viewer & Validation API

This project provides real-time and validation APIs for viewing and verifying the in-memory order book for cryptocurrency trading pairs. It is designed to help monitor and ensure the accuracy of order book data compared to the official Kucoin exchange snapshots.

---

## Features

- **View In-Memory Order Book (Level 2, Depth 20)**  
  Access the current bids and asks stored in memory for a given trading pair.  
  Example endpoints:  
  - `http://localhost:8080/orderbook/BTC-USDT`  
  - `http://localhost:8080/orderbook/ETH-USDT`

- **Validate In-Memory Order Book Against Kucoin Snapshot**  
  Compares the in-memory order book to the official Kucoin snapshot and reports how closely they match with a percentage score for bids and asks. This helps verify the accuracy and synchronization of the local data.  
  Example endpoints:  
  - `http://localhost:8080/orderbook/validate/BTC-USDT`  
  - `http://localhost:8080/orderbook/validate/ETH-USDT`  

  JSON response will include fields like:  
"bids_match_percentage": 0.0,
"asks_match_percentage": 0.0


A higher percentage (close to 100) indicates a more accurate match. These values may fluctuate during startup or reconnects but typically stabilize above 99% over time.

- **Admin WebSocket Control Endpoints**  
Manage the connection to the Kucoin WebSocket feed for testing and recovery purposes:  
- Start WebSocket connection: `http://localhost:8081/admin/websocket/start`  
- Stop WebSocket connection: `http://localhost:8081/admin/websocket/stop`  
- Restart (auto-reconnect) connection: `http://localhost:8081/admin/websocket/restart`

If the WebSocket connection is stopped, the match percentages gradually decline toward zero. Restarting or starting the connection restores and stabilizes the match percentages toward 99%+.

---

## Usage

1. Access the in-memory order book using the `/orderbook/{pair}` endpoint to view live bids and asks.  
2. Use the `/orderbook/validate/{pair}` endpoint to confirm how closely your data aligns with Kucoin's official snapshot.  
3. Use the admin endpoints to simulate connection disruptions or recoveries and observe the impact on data validity.

---

This API is ideal for developers and traders who want to monitor the fidelity of their local order book cache compared to the exchange's data, aiding in debugging, testing, or building reliable trading systems.
