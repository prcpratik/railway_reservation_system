package com.eureka.railway.service;

import com.eureka.railway.entity.Booking;
import com.eureka.railway.entity.Passenger;
import com.eureka.railway.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// Sends email notifications. When app.mail.enabled=false the message is only
// logged (so the app runs without SMTP set up); when true it is emailed via Gmail.
@Service
@Slf4j
public class NotificationService {

    private final boolean enabled;
    private final String from;
    private final String frontendUrl;
    private final JavaMailSender mailSender;

    public NotificationService(@Value("${app.mail.enabled}") boolean enabled,
                               @Value("${spring.mail.username:noreply@railsathi.com}") String from,
                               @Value("${app.frontend-url}") String frontendUrl,
                               ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.enabled = enabled;
        this.from = from;
        this.frontendUrl = frontendUrl;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    // password reset link (step 1 of "forgot password")
    public void sendPasswordReset(User user, String token, int validMinutes) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String body = "Dear " + user.getName() + ",\n\n"
                + "We received a request to reset your RailSathi password.\n"
                + "Open the link below to choose a new password:\n\n"
                + link + "\n\n"
                + "This link is valid for " + validMinutes + " minutes and can be used only once.\n"
                + "If you did not request this, you can ignore this email - your password stays unchanged.\n\n"
                + "- RailSathi\n";
        send(user.getEmail(), "Reset your RailSathi password", body);
    }

    public void sendBookingConfirmation(Booking booking) {
        String subject = "Booking Confirmed - PNR " + booking.getPnr();
        send(booking.getUser().getEmail(), subject, buildConfirmationBody(booking));
    }

    // sent when a waitlisted passenger moves up into a free seat
    public void sendWaitlistPromoted(Booking booking) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(booking.getUser().getName()).append(",\n\n");
        sb.append("Good news - a seat has become available and your waitlisted ticket has moved up.\n\n");
        sb.append("PNR         : ").append(booking.getPnr()).append("\n");
        sb.append("Train       : ").append(booking.getTrain().getName()).append("\n");
        sb.append("Class       : ").append(booking.getSeatClass().getLabel()).append("\n");
        sb.append("Journey Date: ").append(booking.getJourneyDate()).append("\n\n");
        sb.append("Current passenger status:\n").append(passengerLines(booking)).append("\n");
        sb.append("- RailSathi\n");
        send(booking.getUser().getEmail(), "Waitlist update - PNR " + booking.getPnr(), sb.toString());
    }

    // refundAmount is what was refunded for this cancellation (0 if nothing was paid online)
    public void sendCancellation(Booking booking, double refundAmount) {
        String subject = "Booking Cancelled - PNR " + booking.getPnr();
        send(booking.getUser().getEmail(), subject, buildCancellationBody(booking, refundAmount));
    }

    private String buildConfirmationBody(Booking booking) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(booking.getUser().getName()).append(",\n\n");
        sb.append("Your booking is confirmed. Details:\n\n");
        sb.append("PNR         : ").append(booking.getPnr()).append("\n");
        sb.append("Train       : ").append(booking.getTrain().getName())
                .append(" (#").append(booking.getTrain().getTrainNumber()).append(")\n");
        String from = booking.getFromStation() != null
                ? booking.getFromStation() : booking.getTrain().getSource();
        String to = booking.getToStation() != null
                ? booking.getToStation() : booking.getTrain().getDestination();
        sb.append("Journey     : ").append(from).append(" to ").append(to).append("\n");
        sb.append("Journey Date: ").append(booking.getJourneyDate()).append("\n");
        sb.append("Departure   : ").append(booking.getTrain().getDepartureTime()).append("\n\n");
        sb.append("Class       : ").append(booking.getSeatClass().getLabel()).append("\n\n");
        sb.append("Passengers:\n").append(passengerLines(booking));
        sb.append("\nTotal Fare  : Rs. ").append(booking.getTotalFare()).append("\n\n");
        sb.append("Thank you for booking with RailSathi. Happy journey!\n");
        return sb.toString();
    }

    private String buildCancellationBody(Booking booking, double refundAmount) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(booking.getUser().getName()).append(",\n\n");
        sb.append("A cancellation has been processed on your booking (PNR ")
                .append(booking.getPnr()).append(").\n\n");
        if (refundAmount > 0) {
            sb.append("Refund of Rs. ").append(refundAmount)
                    .append(" has been initiated to your original payment method.\n");
        } else {
            sb.append("No online payment was made for this booking, so no refund applies.\n");
        }
        sb.append("Current status      : ").append(booking.getStatus()).append("\n");
        sb.append("Remaining payable   : Rs. ").append(booking.getTotalFare()).append("\n\n");
        sb.append("- RailSathi\n");
        return sb.toString();
    }

    // one line per passenger showing the seat number, or the waitlist position
    private String passengerLines(Booking booking) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Passenger p : booking.getPassengers()) {
            sb.append("  ").append(i++).append(". ").append(p.getName())
                    .append(" (").append(p.getAge()).append(", ").append(p.getGender()).append(") - ");
            if ("WAITLISTED".equals(p.getStatus())) {
                sb.append("WL").append(p.getWaitlistNumber());
            } else if ("CANCELLED".equals(p.getStatus())) {
                sb.append("CANCELLED");
            } else {
                sb.append("Seat ").append(p.getSeatNumber());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // best-effort: a mail failure is logged but never breaks booking/cancellation
    private void send(String to, String subject, String body) {
        if (!enabled || mailSender == null) {
            log.info("[email disabled] would send to {} | {}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} ({})", to, subject);
        } catch (Exception e) {
            log.warn("Could not send email to {}: {}", to, e.getMessage());
        }
    }
}
