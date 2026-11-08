package com.thanh.backend.booking.mapper;

import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.BookingCancellationReason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class BookingMapperTest {
    @ParameterizedTest
    @CsvSource(value = {
        "CANCELLED,NULL,Payment timeout (deadline exceeded),PAYMENT_TIMEOUT",
        "CANCELLED,42,Payment timeout (deadline exceeded),USER_CANCELLED",
        "CANCELLED,42,User cancelled booking,USER_CANCELLED",
        "CANCELLED,NULL,Legacy reason,OTHER",
        "CANCELLED,NULL,NULL,OTHER",
        "PENDING,NULL,Payment timeout (deadline exceeded),NULL",
        "COMPLETED,NULL,NULL,NULL",
        "REFUNDED,NULL,Payment timeout (deadline exceeded),NULL"
    }, nullValues = "NULL")
    void exposesCancellationClassificationWithoutChangingPersistedStatus(
            BookingStatus status, Long actor, String reason, BookingCancellationReason expected) {
        Booking booking = Booking.builder().status(status).cancelledBy(actor).cancelReason(reason).build();
        var response = new BookingMapper(null).toResponse(booking, null, null);
        assertEquals(expected, response.getCancellationReason());
        assertEquals(status, response.getStatus());
        assertEquals(reason, booking.getCancelReason());
    }
}
