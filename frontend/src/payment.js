import api from "./api";

// Opens the Razorpay checkout popup for a PENDING booking.
// On success it calls our backend to verify the payment, then onDone(message).
export function payForBooking(booking, keyId, user, onDone) {
  const rzp = new window.Razorpay({
    key: keyId,
    order_id: booking.razorpayOrderId, // amount comes from the order created by the backend
    name: "RailSathi",
    description: `PNR ${booking.pnr} — ${booking.trainName} (${booking.source} → ${booking.destination})`,
    prefill: { name: user.name, email: user.email },
    theme: { color: "#1e3a5f" },
    handler: async (response) => {
      try {
        await api.put(`/bookings/${booking.id}/pay`, {
          razorpayPaymentId: response.razorpay_payment_id,
          razorpayOrderId: response.razorpay_order_id,
          razorpaySignature: response.razorpay_signature,
        });
        onDone({ type: "success", text: `Payment successful! PNR ${booking.pnr} is CONFIRMED.` });
      } catch (err) {
        onDone({ type: "error", text: err.response?.data?.message || "Payment verification failed" });
      }
    },
    modal: {
      ondismiss: () =>
        onDone({
          type: "error",
          text: `Payment not completed. PNR ${booking.pnr} is PENDING — pay or cancel it from My Bookings.`,
        }),
    },
  });
  rzp.open();
}
