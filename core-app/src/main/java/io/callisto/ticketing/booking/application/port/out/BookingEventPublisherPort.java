package io.callisto.ticketing.booking.application.port.out;

import io.callisto.ticketing.booking.domain.Booking;

// Unlike ApplicationEventPublisher (session 6, used directly in BookingService, no
// port) — that's Spring's own in-process event bus, framework infrastructure the
// same way a method call is. RabbitMQ is a genuine external system crossing a real
// network hop to a different deployable, same category as PaymentPort/
// EventAvailabilityPort, so it gets a port too. The same booking-cancelled moment
// now drives two independent reactions through two different mechanisms: the
// in-JVM ApplicationEvent releases seats within core-app itself; this port tells
// the outside world.
public interface BookingEventPublisherPort {

	void publishCreated(Booking booking);

	void publishCancelled(Booking booking);

}
