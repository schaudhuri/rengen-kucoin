# Kucoin Market Data Level 2 Order Book Service

## Overview

This project is a Java-based real-time order book viewer and validator designed for cryptocurrency trading pairs on the Kucoin exchange. It maintains an in-memory Level 2 order book with a depth of 20 price levels by connecting to Kucoin’s WebSocket stream and periodically fetching snapshots via REST API.

Key functionalities include:  
- Real-time representation of bids and asks with continuous updates from Kucoin’s WebSocket feed  
- Validation of the in-memory order book against official Kucoin snapshots with percentage accuracy metrics  
- Administrative endpoints to start, stop, and restart the WebSocket connection for testing recovery and resilience  

The system ensures high fidelity data synchronization with Kucoin, with accuracy stabilizing above 99% during normal operation. This makes it valuable for developers and traders requiring reliable and accurate order book data for analysis or trading strategies.

## Prerequisites

- OpenJDK 24 installed on your machine (alternatively, Gradle will download the necessary JDK if configured)
- Gradle 8.14 (or use the Gradle wrapper included in the project)
- IntelliJ IDEA or another Java-compatible IDE (optional)

## Building the Project

To build the application, open a terminal in the project root and run:

./gradlew build

This will compile the source code, run tests, and package the application.

## Running the Application

You can run the application using the Gradle `run` task:

./gradlew run


Alternatively, you can run the `Main.java` class directly from your IDE by running the main class defined in the Gradle configuration.

## Java Version Configuration

The project is configured to use Java 24 explicitly via Gradle’s Java toolchain setting. This configuration is included in the `build.gradle` file, so you don’t need to manually configure your IDE or command line environment to use a specific JDK version.

## Dependencies

The project includes dependencies such as:

- Vert.x (web, web-client, core, config)
- Tyrus standalone WebSocket client
- JSON Processing APIs
- JUnit 5 and Mockito for testing

All dependencies are managed via Maven Central and declared in the `build.gradle` file.

---

For detailed documentation and API usage, please refer to the project wiki or contact the maintainers.
