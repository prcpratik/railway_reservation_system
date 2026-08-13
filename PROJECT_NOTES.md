# Project Notes — how it works & viva preparation

Read this before presenting. Every teammate should be able to answer everything here.

## 1. Big picture

```
React (5173)  --axios/JSON-->  Spring Boot REST API (8080)  --JPA/Hibernate-->  MySQL
```

- The frontend never talks to the database. It only calls REST APIs.
- The backend is stateless: no HTTP session. Every request carries a JWT token
  that proves who the user is. This is why we disable CSRF and set
  `SessionCreationPolicy.STATELESS`.

## 2. Backend layers (package by package)

| Package | Files | Responsibility |
|---|---|---|
| `entity` | User, Train, TrainClass, TrainStop, Booking, Passenger (+ SeatClass enum) | JPA entities = the 5 database tables |
| `repository` | UserRepository, TrainRepository, BookingRepository | Data access. Spring Data generates the implementation |
| `service` | UserService, TrainService, BookingService, PaymentService, NotificationService | Business rules (availability, seat allotment, fares, refunds, ownership checks) |
| `controller` | Auth-, Train-, Booking-, Payment-, AdminController (+ GlobalExceptionHandler) | REST endpoints, JSON in/out. Thin — they just call services |
| `security` | JwtUtil, JwtFilter, SecurityConfig | Token creation/validation + access rules |
| `aspect` | LoggingAspect | Spring AOP — logs every controller call |
| `config` | DataSeeder | inserts admin + sample trains on first run |
| `dto` | Dtos.java | small records for request/response bodies |

**Tables:** `users`, `trains`, `train_classes`, `train_stops`, `bookings`,
`passengers`. A train has many classes (capacity + fare each) and many stops
(the route); a booking belongs to one user, one train and one class, and has
many passengers.

**Flow of one request** (e.g. booking): JwtFilter validates token → controller
receives JSON → service checks rules → repository saves → JSON response.

## 3. Key design decisions (say these proactively!)

- **Seat availability is calculated, not stored.**
  `available = class capacity − COUNT(CONFIRMED passengers on that train+class+date)`
  (see `BookingRepository.countBookedSeats`, a custom `@Query`).
  Advantage: cancelling — even a single passenger — automatically frees seats;
  no counter to keep in sync; each class and each date is independent.
- **Seats and fares belong to the class, not the train.** A train has many
  `TrainClass` rows (1AC, Sleeper, …), each with its own capacity and fare, so the
  admin lists only the classes that train actually runs.
- **The route is a list of `TrainStop` rows** with a `stopOrder` and kilometres
  from the start. That ordering makes intermediate-station search directional and
  lets a part-journey be priced by distance.
- **One booking → many passengers** (`@OneToMany` with `cascade = ALL`).
  Status lives on the *passenger* (CONFIRMED/WAITLISTED/CANCELLED); the *booking*
  status is always derived from its passengers: any cancelled → PARTIALLY_CANCELLED,
  all cancelled → CANCELLED, any still waiting → WAITLISTED, otherwise CONFIRMED.
  That single rule (`recompute()` in BookingService) makes partial cancellation
  and the waitlist almost free.
- **Fare is locked at booking time** (`farePerSeat` stored on the booking, already
  scaled for the part of the route being travelled). If the admin changes the fare
  later, existing tickets and their refund math are unaffected.
  `totalFare = farePerSeat × non-cancelled passengers`.
- **DTOs for booking responses.** Controllers return `BookingResponse` /
  `PassengerResponse` records instead of entities, so the API shape is explicit
  and we never leak the nested User (or lazy JPA internals) into JSON.
- **JWT instead of sessions.** The token contains the email + role, signed with
  a server-side secret (HS256). The server doesn't store anything per user, so
  it works naturally with a separate React app on another port.
- **Passwords are BCrypt-hashed** — never stored in plain text. `@JsonIgnore`
  on the password field keeps the hash out of every JSON response.
- **Redux only for auth state.** The logged-in user is needed everywhere
  (navbar, route guards, trains page), so it lives in the Redux store.
  Page-local data (train list, form fields) is plain `useState` — putting it in
  Redux would add boilerplate for no benefit.
- **Cancel = status change, not delete.** Keeps booking history (like real IRCTC)
  and the availability query simply ignores CANCELLED rows.

## 4. Where each syllabus topic appears

| Syllabus topic | Where in this project |
|---|---|
| React components & props | all files in `src/pages`, `src/components` |
| State & lifecycle (hooks) | `useState`/`useEffect` in Trains.jsx etc. |
| Handling events, forms | Login.jsx, Register.jsx, AdminTrains.jsx |
| Lists & keys | `trains.map(...)` with `key={train.id}` |
| Conditional rendering | Navbar (login vs logout), Trains ("Login to book") |
| Lifting state up | seat selection state lives in Trains, passed to each row |
| React Router | App.jsx routes + `<Navigate>` route guards |
| Redux (actions/reducers/store) | store.js — authReducer, loginAction/logoutAction |
| Spring Boot + Maven | pom.xml, RailwayApplication |
| Spring Data JPA, CrudRepository/JpaRepository | repository package |
| Query methods | `findBySourceIgnoreCaseAndDestinationIgnoreCase` |
| Custom @Query | `countBookedSeats` in BookingRepository |
| REST with Spring | the 3 controllers |
| Spring AOP | LoggingAspect (`@Around` advice, pointcut on controllers) |
| Spring Security + JWT | security package |
| Unit testing (Mockito) | BookingServiceTest |

## 5. Likely viva questions & answers

**Q: What happens exactly when a user logs in?**
POST /api/auth/login → UserService loads the user by email, checks the password
with `BCryptPasswordEncoder.matches()`, then JwtUtil builds a token with the
email as subject and role as a claim, signed with our secret key. React stores
it in localStorage and Redux, and axios attaches it to every later request.

**Q: How is a protected request authorized?**
JwtFilter runs before every request. It reads the `Authorization: Bearer` header,
verifies the signature and expiry, and puts an Authentication object (email +
`ROLE_USER`/`ROLE_ADMIN`) into the SecurityContext. SecurityConfig rules then
decide access, e.g. `.requestMatchers("/api/trains/**").hasRole("ADMIN")`.

**Q: Why did you disable CSRF?**
CSRF protection matters for cookie/session-based auth, where the browser sends
credentials automatically. We use a token that JavaScript adds manually to a
header, so a cross-site form cannot include it — CSRF doesn't apply.

**Q: What if two users book the last seat at the same time?**
We prevent the double-sell with a **pessimistic write lock** on the train row.
At the start of `BookingService.book()` we call
`TrainService.getByIdForUpdate()`, which runs a `SELECT ... FOR UPDATE`
(`@Lock(PESSIMISTIC_WRITE)` on `TrainRepository.findByIdForUpdate`). The whole
method is `@Transactional`, so the lock is held until the booking commits. A
second booking for the *same train* blocks on that lock until the first one
finishes, then re-counts the seats and sees the seat is already gone — so it is
correctly rejected instead of overselling. `BookingConcurrencyTest` proves this:
12 threads race for 5 seats on an in-memory H2 database and exactly 5 succeed.
- Bookings for *different* trains lock different rows, so they don't block each
  other — only genuine contention on the same train is serialized.
- Alternative we considered: an optimistic `@Version` column (retry on conflict).
  Pessimistic locking is simpler to reason about here since a booking almost
  always writes, so conflicts would be common.

**Q: Pessimistic vs optimistic locking (likely follow-up)?**
Pessimistic = lock the row up front so others wait (`SELECT ... FOR UPDATE`);
best when conflicts are likely. Optimistic = don't lock, but keep a version
number and fail/retry if someone else changed the row first (`@Version`); best
when conflicts are rare. We chose pessimistic because seat booking is
write-heavy and contended.

**Q: What is the AOP aspect doing? Explain the terminology.**
`LoggingAspect` is an *aspect* containing one *advice* (`@Around` method) whose
*pointcut* `execution(* com.eureka.railway.controller..*(..))` matches every
controller method (*join points*). It logs the method name and execution time —
a cross-cutting concern kept out of business code.

**Q: JpaRepository vs CrudRepository?**
CrudRepository gives basic CRUD; JpaRepository extends it and adds JPA extras
(flush, batch delete, paging/sorting via its parent). We extend JpaRepository.

**Q: Why records for DTOs?**
A record is a concise immutable data carrier — perfect for request/response
bodies where we only need fields, constructor and accessors.

**Q: How does partial cancellation work?**
`PUT /api/bookings/{bookingId}/passengers/{passengerId}/cancel`. The service
loads the booking, checks it belongs to the logged-in user, marks that one
passenger CANCELLED, then recomputes the booking: payable fare = confirmed
passengers × locked fare, and status becomes PARTIALLY_CANCELLED (or CANCELLED
if nobody is left). The availability query only counts CONFIRMED passengers, so
the seat is instantly bookable by someone else — no extra code needed.

**Q: Why DTOs instead of returning entities?**
Entities are the database shape; DTOs are the API contract. Returning entities
couples clients to the schema, risks leaking fields (User inside Booking), and
can trigger lazy-loading/recursion issues in JSON. Our `BookingResponse` record
flattens exactly what the UI needs. (Train is simple and safe, so we return it
directly — a pragmatic middle ground you can defend.)

**Q: Explain the payment flow. How do you know the payment is genuine?**
Booking creates a PENDING booking and a Razorpay *order* (server-to-server call
with RestTemplate — a syllabus topic!). The checkout popup collects payment in
the browser. But we never trust the browser: Razorpay signs
`order_id|payment_id` with our secret key (HMAC-SHA256), and our `/pay` endpoint
recomputes that signature server-side. Only if it matches is the booking marked
CONFIRMED. A tampered frontend cannot fake it because it doesn't have the secret.

**Q: What happens if the user closes the payment popup?**
The booking stays PENDING and its seats stay held (passengers are CONFIRMED, so
the availability query counts them). From My Bookings the user can Pay Now to
retry with the same order, or Cancel All to release the seats. Partial
cancellation is blocked while payment is pending.

**Q: How do you keep secrets out of GitHub?**
`application.properties` (committed) holds only shared defaults. Real secrets —
MySQL password, JWT signing secret, Razorpay key secret — live in
`application-local.properties`, which is git-ignored and loaded on top via
`spring.profiles.active=local`. The JWT secret especially must stay private:
anyone who has it can sign their own tokens and impersonate any user, even an
admin. In production these would come from environment variables or a secrets
manager instead of any file.

**Q: Why is the Razorpay key id sent to the frontend but not the secret?**
The key id is public — the checkout popup needs it and anyone can see it in the
browser. The secret signs and verifies payments and exists only in
application.properties on the server.

**Q: How does "forgot password" work?**
`POST /api/auth/forgot-password` with an email generates a random `UUID` token,
stores it on the user with a 30-minute expiry, and emails a link
`<frontend-url>/reset-password?token=...`. The user opens that page, types a new
password, and the frontend calls `POST /api/auth/reset-password` with the token.
The server checks the token exists and has not expired, encodes the new password
with BCrypt, and then **clears the token** so the link cannot be used twice.
Both endpoints must be public — a user who has forgotten their password cannot
send a JWT — so all the security lives in the token itself: it is random,
time-limited and single-use.

**Q: Why does forgot-password return the same message even for an unregistered
email?**
To prevent **email enumeration**. If we replied "no account with that email" for
unknown addresses and "reset link sent" for known ones, an attacker could script
thousands of addresses and build a list of confirmed users. That list makes
credential stuffing (trying passwords leaked from other sites), brute force and
targeted phishing far cheaper. So both cases return the identical message: "If
that email is registered, a password reset link has been sent to it." The
service uses `.ifPresent(...)` and silently does nothing for an unknown email.
The same reasoning is why **login** returns one generic "Invalid email or
password" instead of distinguishing "user not found" from "wrong password".

*Known limitations (good to volunteer):* registration still reveals existing
emails ("Email is already registered") because the user must be told why signup
failed; and the registered path is slightly slower since it actually sends an
email, which in theory allows a timing attack. Sending the mail asynchronously
would remove that difference.

**Q: How does searching by an intermediate station work?**
Each train has a `TrainStop` list — station, `stopOrder` (position along the
route), timings and kilometres from the start. The search query joins the stops
table to itself twice and keeps trains where the boarding stop's `stopOrder` is
**less than** the destination stop's. That single comparison is what makes
direction matter: Bhopal → Nagpur matches, Nagpur → Bhopal does not. Routes are
optional, so the search is a union of this stop-based match and the older
source/destination match, letting trains without a listed route still be found.

**Q: How is the fare for a part-journey calculated?**
`TrainClass.fare` is the fare for the whole route. Every stop records
`distanceFromSource` in km, so a segment costs
`classFare × (toKm − fromKm) / (lastKm − firstKm)`, rounded to whole rupees.
For Karnataka Express (Delhi 0 → … → Bengaluru 2444 km) a Sleeper ticket from
Bhopal (702) to Nagpur (1092) costs `720 × 390 / 2444 ≈ 115` instead of 720.
If a route has no distances recorded, it falls back to counting stops so the
feature still works. The method lives on the `Train` entity (`fareShare`) rather
than in a service, because both the search screen and the booking logic need the
same number — one source of truth instead of the arithmetic written twice.

**Q: Why does the booking store `fromStation`/`toStation` at all?**
Because a ticket must show the journey that was bought, not the train's whole
run. The booking records both stations and their stop positions, and the API
returns them as `source`/`destination` (with `trainSource`/`trainDestination`
alongside for context), so My Bookings, the admin view and the email all show
"Bhopal → Nagpur" with the boarding and arrival times taken from those two stops.
Older bookings have these fields null and are treated as the full journey.

**Q: Known limitation — what is not implemented here?**
Seats are still held for the **whole** run. If someone books Bhopal → Nagpur, that
berth is blocked Delhi → Bengaluru even though only a part-fare was paid, so a
seat freed at Nagpur cannot be resold onward. Doing that properly means making
availability an interval-overlap problem — counting only passengers whose segment
overlaps the requested one — and allowing the same seat number to be reused on
disjoint segments. We scoped that out deliberately and list it as the main future
enhancement. (Being able to state this clearly is better than pretending it works.)

**Q: Why is `TrainClass` a separate entity instead of columns on `Train`?**
A train does not have one capacity and one fare — it has 24 seats in 1AC, 48 in
2AC, 72 in 3AC, each at a different price. Putting that on `Train` would mean ten
columns (`ac1Seats`, `ac1Fare`, `ac2Seats`, …), which is unnormalised, wastes
space on classes a train does not run, and needs a schema change to add a class.
`Train 1 —— N TrainClass` models it properly: each row is one class on one train
with its own `totalSeats` and `fare`, and the admin adds only the classes that
train actually offers. Availability is then computed per **(train, class, date)**.

**Q: How are seat numbers allotted?**
At booking time. The service asks the database which seat numbers are already
taken for that train + class + journey date (confirmed passengers only), then
gives each new passenger the lowest free number from 1 to the class capacity.
Cancelling needs no special handling: a cancelled passenger is no longer
CONFIRMED, so their number stops being "taken" and is handed out again.

**Q: Where does the waitlist fit, and how does promotion work?**
If a class is full the user can choose "Join Waitlist". Those passengers are
saved with status WAITLISTED and a queue position (WL1, WL2, …) instead of a
seat number. When a seat is freed by a cancellation, `promoteFromWaitlist()`
takes the queue in order, gives the earliest waiting passengers the freed seat
numbers, marks them CONFIRMED, and renumbers whoever is still waiting (WL3
becomes WL2). Promoted users get an email. If only some seats are free, only
that many are promoted.

**Q: Why does cancellation now take the pessimistic lock too?**
Because cancellation can promote someone off the waitlist, and promotion
allocates seat numbers. Without the lock, two cancellations running at the same
time could read the same "free seats" and hand the same seat number to two
people — the identical race we fixed for booking. So `cancel()` and
`cancelPassenger()` lock the train row before freeing seats, exactly like
`book()` does. Good example of a safety mechanism generalising to a new feature.

**Q: Are waitlisted passengers charged?**
Yes, up front, the same as on IRCTC — the payment flow is unchanged, only the
resulting status differs (WAITLISTED instead of CONFIRMED). If the ticket never
confirms and the user cancels, the existing refund logic returns the full fare.
That is also why `recompute()` counts *non-cancelled* passengers (confirmed +
waitlisted) when working out the payable fare, not just the confirmed ones.

**Q: How is the booking status decided when passengers are in different states?**
It is derived, never set directly, in this priority order: all cancelled →
CANCELLED; some cancelled → PARTIALLY_CANCELLED; any still waitlisted →
WAITLISTED; otherwise CONFIRMED. A booking awaiting payment stays PENDING until
the payment succeeds. So the booking reads as "not fully confirmed yet" while the
passenger table shows exactly who has a seat and who is on WL.

**Q: What is Lombok doing in this project?**
Lombok generates boilerplate at **compile time** through annotation processing.
We use `@Getter` and `@Setter` on the four entities (removing ~290 lines of
hand-written accessors), `@NoArgsConstructor` for the no-arg constructor JPA
requires, and `@Slf4j` in `LoggingAspect` and `NotificationService` to generate
the `private static final Logger log`. The generated methods are real bytecode —
you can see them in the compiled `.class` file. It is marked `optional` in the
POM because it is only needed to compile, not at runtime.

**Q: Why `@Getter`/`@Setter` on the entities instead of `@Data`?**
`@Data` also generates `toString()`, `equals()` and `hashCode()`, and all three
are dangerous on a JPA entity with a bidirectional relation. `Booking` holds a
list of `Passenger`, and each `Passenger` points back to its `Booking` — a
generated `toString()` would recurse between them until the stack overflows, and
the same applies to `equals()`/`hashCode()`. Generated `equals()` also compares
every field, whereas entity identity should be based on the primary key. So we
deliberately generate only the accessors. (We still wrote `addPassenger()` by
hand, because it keeps both sides of the relation in sync — Lombok's setter
cannot do that.)

**Q: Why is `availableSeats` marked `@Transient`?**
It's derived data, not a column. The service computes it per journey date and
sets it on the entity so it appears in the JSON response.

**Q: Props vs State vs Context/Redux?**
Props = data passed down from parent, read-only. State = component's own data
that changes over time (`useState`). Redux/Context = shared/global state needed
by many components — here, the logged-in user.

**Q: Why does axios use an interceptor?**
So the JWT header is added in one place instead of repeating it in every call.

**Q: What does `ddl-auto=update` do? Would you use it in production?**
Hibernate compares entities with the DB schema on startup and creates/alters
tables. Fine for a college project; production uses migration tools (Flyway).

**Q: How is the refund handled on cancellation?**
For an online-paid booking, cancelling a seat calls Razorpay's refund API
(`PaymentService.refund`) for that seat's fare (full refund) and records it in
`booking.refundedAmount`. The refund runs *before* the DB is saved inside the
`@Transactional` method, so if the refund fails the whole cancellation rolls
back — you never get "cancelled but not refunded". Bookings made when payment was
disabled have no `razorpayPaymentId`, so nothing is refunded.

**Q: How does the app stay email-safe if SMTP isn't configured?**
`NotificationService` checks `app.mail.enabled`. When false (default) it just
logs the message; when true it sends via Gmail SMTP. Sending is wrapped in
try/catch so a mail failure only logs a warning and never breaks a booking or
cancellation. Emails go to the booking user's registered address.

**Q: Only an admin can create an admin — how is that enforced?**
`POST /api/admin/create-admin` sits under `/api/admin/**`, which SecurityConfig
restricts to `hasRole("ADMIN")`. It's enforced server-side from the JWT role, not
just by hiding the UI link — a normal user's token (ROLE_USER) gets 403 even if
they craft the request by hand.

**Q: Why can't an admin see the booking page?**
Admins are routed to Manage Trains on login, and the `/` route redirects any
admin to `/admin`. Booking is a passenger action; separating the two removes the
"why can an admin book?" confusion. It's a UX guard — the API still enforces roles.

## 6. Suggested work split for 5 members (for "who did what")

1. Database design + entities + repositories
2. Services + business logic + unit tests
3. Security (JWT) + AOP + exception handling
4. React pages: auth + trains/booking
5. React: Redux store, routing, admin page, CSS

Everyone should still be able to explain the whole flow end-to-end.
