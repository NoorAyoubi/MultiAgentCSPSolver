# Distributed N-Queens Constraint Solver

A Java-based Distributed Constraint Satisfaction Problem (DisCSP) solver for the N-Queens problem. Each queen is modeled as an autonomous agent that communicates through a synchronized message-passing system.

## Features

* Multi-agent architecture
* Java multithreading
* Thread-safe message passing
* Forward Checking (FC)
* Conflict-Directed Backjumping (CBJ)
* Distributed constraint propagation
* Conflict detection and explanation sets
* State restoration and backtracking mechanisms

## Architecture

The system consists of:

* **Agent** – Represents a queen and performs local reasoning.
* **Mailer** – Handles synchronized message delivery between agents.
* **SearchContext** – Maintains global search state.
* **CspBoard** – Represents the N-Queens board and constraints.
* **Messages** – Support communication and coordination between agents.

## Technologies

* Java
* Object-Oriented Programming (OOP)
* Multithreading
* Concurrent Programming
* Distributed Systems
* Constraint Satisfaction Problems (CSP)
* Forward Checking (FC)
* Conflict-Directed Backjumping (CBJ)

## Learning Outcomes

This project was developed as part of a Distributed Constraint Satisfaction Algorithms assignment and demonstrates:

* Distributed problem solving
* Agent-based system design
* Concurrent software development
* Search optimization techniques
* Advanced algorithm implementation

## Author

Noor Ayoubi
