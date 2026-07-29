# Railway Reservation System

CDAC PG-DAC team project. A simple full-stack railway ticket reservation app.

**Tech stack** (only what the CDAC syllabus covers):

| Layer | Technology |
|---|---|
| Frontend | React 18 (Vite), React Router, Redux (auth state only), Axios |
| Backend | Spring Boot 3.5, Spring Web (REST), Spring Data JPA, Spring Security + JWT, Spring AOP, Spring Mail |
| Database | MySQL 8/9 |

## Features

- Register / login (JWT based, passwords stored BCrypt-hashed)
- Search trains by source, destination and journey date
- Live seat availability per date (total seats minus confirmed passengers)
- Book one ticket for up to 6 passengers (name, age, gender each), fare calculated automatically
- View my bookings with full passenger list
- **Partial cancellation**: cancel a single passenger (seat freed, fare reduced,
  booking becomes PARTIALLY_CANCELLED) or cancel the whole booking
- **Razorpay payment (test mode)**: booking stays PENDING until paid through the
  Razorpay checkout; the backend verifies the payment signature before confirming
- **Refund on cancellation**: a real Razorpay refund is issued for the cancelled
  seats (full refund) and recorded on the booking
- **Email notifications** (Gmail SMTP): sent on booking confirmation and on cancellation/refund
- **Concurrency-safe booking**: a pessimistic row lock on the train prevents the
  last seat from being sold twice under simultaneous bookings
- **User profile**: edit name & phone, change password, view booking history
  (upcoming vs past)
- Admin: manage trains (add / edit / delete), view **all bookings**, and
  **create another admin**
- Role-based access: only ADMIN can manage trains / create admins; only logged-in
  users can book. Admins are routed to the management view, not the booking page.

## How to run

### 1. Database

MySQL must be running. The database `eureka_railway` is created automatically on
first start.

Personal settings (your MySQL password, Razorpay keys) go in a **git-ignored**
file: copy `backend/src/main/resources/application-local.properties.example` to
`application-local.properties` in the same folder and fill in your values.
Spring loads it automatically on top of `application.properties`
(`spring.profiles.active=local`), so nobody's password ever reaches GitHub.

### 2. Backend (port 8080)

Open the `backend` folder in Eclipse/STS as an *Existing Maven Project* and run
`RailwayApplication.java`, **or** from the command line (needs Maven):

```
cd backend
mvn spring-boot:run
```

On first run it seeds an admin user and 6 sample trains.

### 3. Frontend (port 5173)

```
cd frontend
npm install
npm run dev
```

Open http://localhost:5173

## Default logins

| Role | Email | Password |
|---|---|---|
| Admin | admin@railway.com | admin123 |
| User | register your own from the UI | |

## REST API

| Method | URL | Access | Purpose |
|---|---|---|---|
| POST | /api/auth/register | public | create account |
| POST | /api/auth/login | public | returns JWT token |
| GET | /api/auth/me | logged in | current user's profile |
| PUT | /api/auth/profile | logged in | update name & phone |
| PUT | /api/auth/password | logged in | change password (verifies current) |
| GET | /api/trains?source=&destination=&date= | public | search trains + availability |
| POST | /api/trains | ADMIN | add train |
| PUT | /api/trains/{id} | ADMIN | update train |
| DELETE | /api/trains/{id} | ADMIN | delete train |
| POST | /api/admin/create-admin | ADMIN | create a new admin account |
| POST | /api/bookings | logged in | book a ticket with a passenger list |
| GET | /api/bookings/my | logged in | my bookings (with passengers) |
| GET | /api/bookings/all | ADMIN | every booking in the system |
| PUT | /api/bookings/{id}/pay | logged in | verify Razorpay payment, confirm booking |
| PUT | /api/bookings/{id}/cancel | logged in | cancel entire booking (with refund) |
| PUT | /api/bookings/{id}/passengers/{pid}/cancel | logged in | cancel one passenger (partial, with refund) |
| GET | /api/payments/config | logged in | is payment enabled + public key id |

Booking request body example:

```json
{
  "trainId": 1,
  "journeyDate": "2026-08-01",
  "passengers": [
    { "name": "Amit", "age": 25, "gender": "Male" },
    { "name": "Neha", "age": 23, "gender": "Female" }
  ]
}
```

Protected calls need the header `Authorization: Bearer <token>`.
You can try all of these in Postman: login first, copy the token, then add the header.

## Razorpay setup (test mode — no real money)

1. Create a free account at https://razorpay.com and switch the dashboard to **Test Mode**.
2. Dashboard → Settings → API Keys → **Generate Test Key** (gives a key id `rzp_test_...` and a key secret).
3. In your **git-ignored** `application-local.properties` set:
   ```properties
   razorpay.enabled=true
   razorpay.key-id=rzp_test_yourKeyId
   razorpay.key-secret=yourKeySecret
   ```
4. Restart the backend. Booking now opens the Razorpay checkout popup.
5. Pay with Razorpay's test methods, e.g. card `4111 1111 1111 1111`
   (any future expiry, any CVV) or UPI id `success@razorpay`.

With `razorpay.enabled=false` the payment step is skipped and bookings are
confirmed immediately (useful while developing). Cancelling a paid booking issues
a real Razorpay refund for the cancelled seats.

## Email setup (optional — Gmail SMTP)

Emails are sent on booking confirmation and cancellation. Off by default; when
disabled, the email content is logged to the console instead of sent.

1. Enable 2-Step Verification on your Google account, then generate a Gmail
   **App Password** (Google Account → Security → App passwords).
2. In your **git-ignored** `application-local.properties` set:
   ```properties
   app.mail.enabled=true
   spring.mail.username=youremail@gmail.com
   spring.mail.password=your16charAppPassword
   ```
3. Restart the backend. Emails go to the booking user's registered address.

**Flow**: book → backend creates a PENDING booking + Razorpay *order* → popup
collects payment → frontend sends payment id + signature to `/pay` → backend
recomputes the HMAC-SHA256 signature with the secret key and only then marks the
booking CONFIRMED. An unpaid booking stays PENDING; pay later or cancel it from
My Bookings.

## Tests

```
cd backend
mvn test
```

Runs the booking-service unit tests (Mockito) plus a concurrency test that proves
the pessimistic lock prevents overselling (uses an in-memory H2 database).

See `PROJECT_NOTES.md` for how everything works internally and likely viva
questions, and `DEPLOYMENT.md` for the Docker / Selenium / Jenkins deployment plan.
