# Railway Reservation System

CDAC PG-DAC team project. A simple full-stack railway ticket reservation app.

**Tech stack** (only what the CDAC syllabus covers):

| Layer | Technology |
|---|---|
| Frontend | React 18 (Vite), React Router, Redux (auth state only), Axios |
| Backend | Spring Boot 3.5, Spring Web (REST), Spring Data JPA, Spring Security + JWT, Spring AOP, Spring Mail, Lombok |
| Database | MySQL 8/9 |

## Features

- Register / login (JWT based, passwords stored BCrypt-hashed)
- Search trains by source, destination and journey date
- **Intermediate stations**: a train carries its full route (station, timings and
  km from the start). Passengers can search using *any two stations on the route*
  in travel order, not just the end points
- **Part-journey fares**: booking a section of the route is charged in proportion
  to the distance travelled, and the ticket shows the booked stations with the
  boarding and arrival times at those stops
- **Travel classes**: each train offers its own set of classes (1AC, 2AC, 3AC,
  Sleeper, General), each with its own capacity and fare
- Live seat availability per **class** per journey date (class capacity minus
  confirmed passengers)
- **Seat allotment**: every confirmed passenger gets a seat number, unique per
  train + class + date; cancelling releases the number for reuse
- **Waitlist**: when a class is full a passenger can join the queue (WL1, WL2, …)
  and is promoted automatically into the first seat that is freed
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
- **Forgot password**: emailed reset link with a random, single-use token that
  expires in 30 minutes
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

> **Lombok — one-time IDE setup (every team member must do this).**
> The entities use Lombok's `@Getter`/`@Setter`, which generate code at compile
> time. Maven builds fine without any setup, but **Eclipse/STS will show red
> errors** like *"method getName() is undefined"* until Lombok is installed:
>
> 1. Find `lombok.jar` in your local Maven repo:
>    `C:\Users\<you>\.m2\repository\org\projectlombok\lombok\<version>\lombok-<version>.jar`
> 2. Double-click it (or run `java -jar lombok-<version>.jar`)
> 3. In the installer, select your Eclipse/STS installation → **Install / Update**
> 4. **Restart Eclipse**, then Project → Clean
>
> IntelliJ IDEA: install the *Lombok* plugin and enable
> Settings → Build → Compiler → Annotation Processors → *Enable annotation processing*.
>
> If you see errors on the entity classes, this step was skipped.

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
| POST | /api/auth/forgot-password | public | email a password-reset link |
| POST | /api/auth/reset-password | public | set a new password using the emailed token |
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
  "seatClass": "AC3",
  "journeyDate": "2026-08-01",
  "allowWaitlist": false,
  "fromStation": "Bhopal",
  "toStation": "Nagpur",
  "passengers": [
    { "name": "Amit", "age": 25, "gender": "Male" },
    { "name": "Neha", "age": 23, "gender": "Female" }
  ]
}
```

`seatClass` is one of `AC1`, `AC2`, `AC3`, `SLEEPER`, `GENERAL` and must be a
class that train actually offers. Set `allowWaitlist` to `true` only when the
user has explicitly chosen to join the queue — otherwise a full class is
rejected instead of silently waitlisting them.

`fromStation` / `toStation` are optional. Leave them out (or null) to book the
train's whole route; give two stations from the train's route, in travel order,
to book and pay for that part only. The fare is the class fare scaled by
`km travelled / total km` of the route.

Protected calls need the header `Authorization: Bearer <token>`.
You can try all of these in Postman: login first, copy the token, then add the header.

### Booking and passenger status values

| Booking status | Meaning |
|---|---|
| `PENDING` | seats held, awaiting online payment |
| `CONFIRMED` | every passenger has a seat |
| `WAITLISTED` | at least one passenger is still in the queue |
| `PARTIALLY_CANCELLED` | some passengers cancelled, others still travelling |
| `CANCELLED` | all passengers cancelled |

| Passenger status | Meaning |
|---|---|
| `CONFIRMED` | has a seat number |
| `WAITLISTED` | has a waitlist position (WL1, WL2, …), no seat yet |
| `CANCELLED` | seat/position released |

The booking status is always **derived** from its passengers, so the two can
never contradict each other.

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
