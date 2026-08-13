



const features = [
  "Search trains by source, destination and journey date",
  "Live seat availability that updates per journey date",
  "Book one ticket for multiple passengers",
  "Secure online payment with Razorpay",
  "Full and partial ticket cancellation",
  "Personal profile with booking history",
];

export default function About() {
  return (
    <div className="about">
      <section className="about-hero card">
        <h2>About RailSathi</h2>
        <p>
          RailSathi is a railway ticket reservation system that lets travellers search
          trains, check live seat availability, book tickets for multiple passengers,
          pay online and manage their bookings — all from one simple web application.
        </p>
        <p className="muted">
          Built as a team project for the CDAC PG-DAC (Advanced Computing) course,
          using Spring Boot, React and MySQL.
        </p>
      </section>

      <section>
        <h3 className="section-title">What You Can Do</h3>
        <ul className="feature-list">
          {features.map((f) => (
            <li key={f}>{f}</li>
          ))}
        </ul>
      </section>

      <section>
        <h3 className="section-title">Technology Used</h3>
        <div className="tech-grid">
          <div className="tech-item"><strong>Frontend</strong><span>React, React Router, Redux, Axios</span></div>
          <div className="tech-item"><strong>Backend</strong><span>Spring Boot, Spring Data JPA, Spring Security (JWT), Spring AOP</span></div>
          <div className="tech-item"><strong>Database</strong><span>MySQL</span></div>
          <div className="tech-item"><strong>Payment</strong><span>Razorpay</span></div>
        </div>
      </section>

      
    </div>
  );
}
