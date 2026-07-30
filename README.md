# Payment Event Recovery Engine

A production-style backend application built with **Java 17, Spring Boot 3, and PostgreSQL** that ensures reliable processing of payment events through automatic retries, exponential backoff, and a Dead Letter Queue (DLQ). The project demonstrates backend engineering concepts commonly used in distributed systems.

---

## 🚀 Features

* Receive payment events through REST APIs.
* Automatic retry mechanism using Spring Scheduler.
* Exponential backoff for failed deliveries.
* Dead Letter Queue (DLQ) for permanently failed events.
* Manual retry for FAILED and DEAD payments.
* Complete audit trail for every payment event.
* Payment metrics dashboard API.
* Pagination and status-based filtering.
* Global exception handling.
* Structured logging using SLF4J + Logback.
* Clean layered architecture (Controller → Service → Repository).

---

## 🛠 Tech Stack

* Java 17
* Spring Boot 3
* Spring Data JPA (Hibernate)
* PostgreSQL
* Maven
* Lombok
* SLF4J + Logback
* Spring Scheduler

---

## 📂 Project Structure

```text
src
├── controller
├── service
├── repository
├── entity
├── dto
├── enums
├── exception
├── scheduler
└── resources
```

---

## 📌 Architecture

```text
                 Client
                    │
                    ▼
          Webhook Controller
                    │
                    ▼
             Webhook Service
                    │
                    ▼
          Payment Repository
                    │
                    ▼
           Delivery Service
                    │
       ┌────────────┴────────────┐
       ▼                         ▼
Payment Repository        Audit Service
       │                         │
       ▼                         ▼
 PostgreSQL               Audit Repository

                ▲
                │
        Retry Scheduler
```

---

## 🔄 Payment Lifecycle

```text
             Payment Received
                    │
                    ▼
                PENDING
                    │
          Delivery Attempt
          ┌─────────┴─────────┐
          ▼                   ▼
     DELIVERED             FAILED
                                │
                       Retry Scheduler
                                │
                    Exponential Backoff
                                │
                ┌───────────────┴───────────────┐
                ▼                               ▼
           DELIVERED                         DEAD
                                                 │
                                          Manual Retry
```

---

## 📡 REST API Endpoints

### Webhook

| Method | Endpoint                 | Description           |
| ------ | ------------------------ | --------------------- |
| POST   | `/api/webhooks/payments` | Receive payment event |

### Payments

| Method | Endpoint                          | Description         |
| ------ | --------------------------------- | ------------------- |
| GET    | `/api/payments`                   | Get all payments    |
| GET    | `/api/payments/{paymentId}`       | Get payment details |
| GET    | `/api/payments?status=FAILED`     | Filter by status    |
| GET    | `/api/payments/dead`              | Get dead payments   |
| GET    | `/api/payments/{paymentId}/logs`  | Get audit logs      |
| POST   | `/api/payments/{paymentId}/retry` | Manual retry        |
| GET    | `/api/payments/metrics`           | Payment metrics     |

---

## 📊 Metrics

The Metrics API provides:

* Total payments
* Pending payments
* Failed payments
* Delivered payments
* Dead payments

---

## 📝 Audit Logging

Every important action is recorded, including:

* Payment received
* Delivery attempted
* Delivery successful
* Delivery failed
* Retry scheduled
* Payment moved to Dead Letter Queue
* Manual retry initiated

---

## 📁 Logging

Structured application logging is implemented using **SLF4J + Logback**.

Log levels used:

* INFO
* WARN
* ERROR

Logs are available in both the console and log files.

---

## ▶️ Running the Project

### Clone the repository

```bash
git clone https://github.com/<your-username>/payment-event-recovery-engine.git
```

### Navigate to the project

```bash
cd payment-event-recovery-engine
```

### Configure PostgreSQL

Update your database credentials in:

```text
src/main/resources/application.yml
```

### Build the project

```bash
mvn clean install
```

### Run the application

```bash
mvn spring-boot:run
```

The application starts at:

```text
http://localhost:8080
```

---

## 📈 Future Enhancements

* Swagger/OpenAPI documentation
* Unit and integration testing (JUnit & Mockito)
* Docker and Docker Compose support
* React Admin Dashboard
* Microservice integration with an Order Service

---

## 👨‍💻 Author

**Divyansh Jeena**

Backend Developer | Java | Spring Boot | PostgreSQL

Passionate about building scalable backend systems with clean architecture and production-ready engineering practices.
